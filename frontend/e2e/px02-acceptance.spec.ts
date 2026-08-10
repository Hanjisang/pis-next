import { expect, test, type Page, type TestInfo } from '@playwright/test';

import { login } from './helpers';

test.setTimeout(180_000);

type CaseRef = { caseId: string; pathologyNo: string };

function uniqueSuffix(testInfo: TestInfo) {
  return `${Date.now()}-${testInfo.project.name.replace(/[^a-z0-9]/gi, '')}`;
}

async function logout(page: Page) {
  await page.getByRole('button', { name: '退出' }).click();
  await expect(page.getByRole('region', { name: 'PIS V2 登录' })).toBeVisible();
}

async function registerRoutineCase(page: Page, testInfo: TestInfo): Promise<CaseRef> {
  const suffix = uniqueSuffix(testInfo);
  await page.getByRole('button', { name: '登记', exact: true }).click();
  await page.getByRole('button', { name: '新增手工病例' }).click();
  await page.getByLabel('患者编号').fill(`PX02-${suffix}`);
  await page.getByLabel('就诊号').fill(`PX02-VISIT-${suffix}`);
  await page.getByLabel('申请号').fill(`PX02-APPLICATION-${suffix}`);
  await page.getByLabel('业务类型', { exact: true }).selectOption({ label: '常规组织病理' });
  await page.getByLabel('取材部位').first().fill('PX02 本地 Viewer 胃窦');
  await page.getByRole('button', { name: '+ 新增标本' }).click();
  await page.getByLabel('取材部位').last().fill('PX02 本地 Viewer 胃体');
  await page.getByRole('button', { name: '确认登记' }).click();
  const completion = page.getByRole('status').filter({ hasText: '登记完成，病理号已生成' });
  await expect(completion).toBeVisible();
  const pathologyNo = (await completion.getByRole('strong').innerText()).split('：').at(-1)?.trim();
  expect(pathologyNo).toBeTruthy();
  await page.getByRole('button', { name: '病例中心' }).click();
  await expect(page).toHaveURL(/\/v2\/cases\//);
  return {
    caseId: new URL(page.url()).pathname.split('/').filter(Boolean).at(-1)!,
    pathologyNo: pathologyNo!,
  };
}

async function createSlides(page: Page, caseRef: CaseRef) {
  await logout(page);
  await login(page, 'technician');
  await page.goto(`/v2/grossing/${caseRef.caseId}`);
  await expect(page.getByLabel('病例取材工作区')).toBeVisible();
  await page.getByRole('button', { name: '开始取材' }).click();
  await expect(page.getByRole('status').filter({ hasText: '已开始取材' })).toBeVisible();

  for (const code of ['A1', 'A2']) {
    await page.getByLabel('新蜡块编号').fill(code);
    await page.getByRole('button', { name: '+ 蜡块' }).click();
    await expect(page.getByRole('status').filter({ hasText: `蜡块 ${code} 已建立` })).toBeVisible();
  }

  await page.getByRole('button', { name: '完成取材' }).click();
  await expect(page.getByRole('status').filter({ hasText: '取材已完成' })).toBeVisible();
}

async function bindLocalTiledSlides(page: Page, caseRef: CaseRef) {
  await page.goto(`/v2/digital-slides/${caseRef.caseId}`);
  await expect(page.getByLabel('数字切片工作台')).toBeVisible();

  const block = page.getByLabel('关联蜡块');
  const slide = page.getByLabel('关联玻片');
  await block.selectOption({ label: 'A1' });
  await slide.selectOption({ label: 'A1-HE' });
  await page.getByLabel('来源平台').fill('PX02 Local Tiled Fixture');
  await page.getByLabel('阅片器引用').fill('/fixtures/px02a-wsi/slide.dzi');
  await page.getByRole('button', { name: '绑定数字切片' }).click();
  await expect(page.getByRole('status').filter({ hasText: '数字切片已绑定' })).toBeVisible();

  await block.selectOption({ label: 'A2' });
  await slide.selectOption({ label: 'A2-HE' });
  await page.getByLabel('阅片器引用').fill('/fixtures/px02a-wsi/slide.dzi');
  await page.getByRole('button', { name: '绑定数字切片' }).click();
  await expect(page.getByRole('status').filter({ hasText: '数字切片已绑定' })).toBeVisible();
}

test('PX02-AFGK：配置、权限和报告队列来自真实后端', async ({ page }) => {
  await login(page, 'admin');

  await page.goto('/v2/configuration');
  await expect(page.getByLabel('配置中心')).toBeVisible();
  await expect(page.getByText('已连接真实配置')).toBeVisible();
  await page.getByRole('button', { name: '申请项目映射' }).click();
  await expect(page.getByRole('heading', { name: '申请项目映射' })).toBeVisible();
  await page.getByRole('button', { name: '保存', exact: true }).first().click();
  await expect(page.getByRole('status').filter({ hasText: '申请项目映射已保存' })).toBeVisible();

  await page.goto('/v2/system');
  await expect(page.getByRole('region', { name: '系统管理' })).toBeVisible();
  await expect(page.getByText('BUSINESS · 业务权限')).toBeVisible();
  await expect(page.getByText('DATA · 数据范围')).toBeVisible();
  await expect(page.getByText('ACTION · 操作权限')).toBeVisible();
  await expect(page.getByText('Doctor Identity · 医疗人员身份')).toBeVisible();
  await page.getByRole('button', { name: '保存设置' }).click();
  await expect(page.getByRole('status').filter({ hasText: '三层权限已保存' })).toBeVisible();

  await page.goto('/v2/reports');
  await expect(page.getByLabel('报告中心')).toBeVisible();
  await expect(page.getByRole('button', { name: /已签发/ })).toBeVisible();
  await page.getByRole('button', { name: /已签发/ }).click();
  const reportRow = page.locator('button.dense-report-row').first();
  await expect(reportRow).toBeVisible();
  await reportRow.click();
  await expect(page).toHaveURL(/\/v2\/reports\/[^?]+\?reportId=/);
  await expect(page.getByRole('region', { name: '诊断工作区' }).last()).toBeVisible();
});

test('PX02-EJ：真实病例绑定本地 DZI，并完成阅片器交互', async ({ page }, testInfo) => {
  await login(page, 'registrar');
  const caseRef = await registerRoutineCase(page, testInfo);
  await createSlides(page, caseRef);
  await bindLocalTiledSlides(page, caseRef);
  await logout(page);
  await login(page, 'doctor-c');
  await page.goto(`/v2/digital-slides/${caseRef.caseId}`);
  await expect(page.getByLabel('数字切片工作台')).toBeVisible();

  const viewer = page.getByRole('region', { name: '数字切片阅片器' });
  await expect(viewer).toBeVisible();
  await expect(page.getByText('WSI 分层阅片器')).toBeVisible();
  await expect(viewer.locator('.openseadragon-container').first()).toBeVisible();
  await expect(viewer.locator('.navigator')).toBeVisible();
  await expect(page.getByRole('heading', { name: caseRef.pathologyNo })).toBeVisible();
  await expect(viewer.getByText('A1-HE', { exact: true }).first()).toBeVisible();

  await page.getByRole('button', { name: '放大' }).click();
  await expect(viewer.getByText('125%')).toBeVisible();
  await page.getByRole('button', { name: '缩小' }).click();
  await page.getByRole('button', { name: '还原视图' }).click();

  await page.getByRole('button', { name: '全屏查看' }).click();
  await expect.poll(() => page.evaluate(() => Boolean(document.fullscreenElement))).toBe(true);
  await page.evaluate(() => document.exitFullscreen());
  await expect.poll(() => page.evaluate(() => Boolean(document.fullscreenElement))).toBe(false);

  const slideRail = page.getByLabel('数字切片列表');
  await expect(slideRail.getByRole('button')).toHaveCount(2);
  await slideRail.getByRole('button').nth(1).click();
  await expect(viewer.getByText('A2-HE', { exact: true }).first()).toBeVisible();
  await expect(page.getByLabel('阅片上下文')).toContainText('病例');
  await expect(page.getByLabel('阅片上下文')).toContainText('标本');
  await expect(page.getByLabel('阅片上下文')).toContainText('蜡块');
  await expect(page.getByLabel('阅片上下文')).toContainText('玻片');
  await expect(page.getByLabel('阅片上下文')).toContainText('数字切片');
});
