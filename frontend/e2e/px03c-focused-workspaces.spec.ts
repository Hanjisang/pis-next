import { expect, test, type Page, type TestInfo } from '@playwright/test';

import { expectNoPageOverflow, login } from './helpers';

test.setTimeout(45_000);

async function logout(page: Page) {
  await page.getByRole('button', { name: '退出' }).click();
  await expect(page.getByRole('region', { name: 'PIS V2 登录' })).toBeVisible();
}

async function registerCase(page: Page, testInfo: TestInfo, businessType: '常规组织病理' | '冰冻') {
  const suffix = `${Date.now()}-${testInfo.project.name}`;
  await login(page, 'registrar');
  await page.getByRole('button', { name: '登记', exact: true }).click();
  await page.getByRole('button', { name: '新增手工病例' }).click();
  await page.getByLabel('患者编号').fill(`李四-${suffix.slice(-8)}`);
  await page.getByLabel('就诊号').fill(`VISIT-${suffix}`);
  await page.getByLabel('申请号').fill(`APPLICATION-${suffix}`);
  await page.getByLabel('业务类型', { exact: true }).selectOption({ label: businessType });
  await page
    .getByLabel('取材部位')
    .first()
    .fill(businessType === '冰冻' ? '甲状腺结节' : '胃窦活检');
  await page.getByRole('button', { name: '确认登记' }).click();
  await expect(
    page.getByRole('status').filter({ hasText: '登记完成，病理号已生成' }),
  ).toBeVisible();
  await page.getByRole('button', { name: '病例概览' }).click();
  return new URL(page.url()).pathname.split('/').filter(Boolean).at(-1)!;
}

async function prepareRoutineTask(page: Page, testInfo: TestInfo) {
  const caseId = await registerCase(page, testInfo, '常规组织病理');
  await logout(page);
  await login(page, 'technician');
  await page.goto(`/v2/grossing/${caseId}`);
  await page.getByRole('button', { name: '开始取材' }).click();
  await page.getByLabel('新蜡块编号').fill('A1');
  await page.getByRole('button', { name: '+ 蜡块' }).click();
  await page.getByLabel('新蜡块编号').fill('A2');
  await page.getByRole('button', { name: '+ 蜡块' }).click();
  await page.getByRole('button', { name: '完成取材' }).click();
  await expect(page.getByRole('status').filter({ hasText: '取材已完成' })).toBeVisible();
  await page.goto('/v2/workbench');
  return caseId;
}

async function bindLocalWsi(page: Page, caseId: string) {
  await page.goto(`/v2/digital-slides/${caseId}`);
  const block = page.getByLabel('关联蜡块');
  const slide = page.getByLabel('关联玻片');
  for (const code of ['A1', 'A2']) {
    await block.selectOption({ label: code });
    await slide.selectOption({ label: `${code}-HE` });
    await page.getByLabel('来源平台').fill('PX03C Local WSI');
    await page.getByLabel('阅片器引用').fill('/fixtures/px02a-wsi/slide.dzi');
    await page.getByRole('button', { name: '绑定数字切片' }).click();
    await expect(page.getByRole('status').filter({ hasText: '数字切片已绑定' })).toBeVisible();
  }
}

async function completeRoutineSlides(page: Page, caseId: string) {
  await page.goto(`/v2/production/${caseId}`);
  const scan = page.getByPlaceholder('扫描或输入玻片号');
  for (const code of ['A1-HE', 'A2-HE']) {
    await scan.fill(code);
    await scan.press('Enter');
    await expect(page.getByRole('status').filter({ hasText: `玻片 ${code} 已完成` })).toBeVisible();
  }
}

async function prepareTechnicalOrder(page: Page) {
  await login(page, 'doctor-a');
  await page.getByRole('tab', { name: /待初诊/ }).click();
  await page.locator('.workbench-dense-row').first().click();
  await page.getByRole('button', { name: '技术医嘱', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: '开立技术医嘱' });
  await dialog.getByLabel('项目').selectOption({ label: '结构化检测结果' });
  await dialog.getByLabel('材料类型').selectOption('CASE');
  await dialog.getByRole('button', { name: '确认开立' }).click();
  await expect(page.getByRole('status').filter({ hasText: '技术医嘱已开立' })).toBeVisible();
  await logout(page);
  await login(page, 'technician');
}

async function prepareFrozenTask(page: Page, testInfo: TestInfo) {
  const caseId = await registerCase(page, testInfo, '冰冻');
  await logout(page);
  await login(page, 'technician');
  await page.goto(`/v2/frozen/${caseId}`);
  await page.getByRole('button', { name: '开始第 1 轮' }).click();
  await page.goto('/v2/workbench');
}

async function openProductionQueue(page: Page, queueLabel: string) {
  const queueTab = page.getByRole('tab', { name: new RegExp(queueLabel) });
  await expect(queueTab).toBeVisible();
  await queueTab.click();

  const taskRow = page.locator('.workbench-dense-row').first();
  await expect(taskRow).toBeVisible();
  await taskRow.click();
  return queueTab;
}

test('PX03C：常规制片进入 Block／Slide 聚焦工作区', async ({ page }, testInfo) => {
  await prepareRoutineTask(page, testInfo);
  await openProductionQueue(page, '常规制片');

  await expect(page).toHaveURL(/\/v2\/production\/[^?]+\?origin=workbench/);
  await expect(page.getByRole('region', { name: '病例中心', exact: true })).toHaveCount(0);
  await expect(page.getByRole('region', { name: 'Block / Slide 工作表' })).toBeVisible();
  await expect(page.getByRole('heading', { name: '常规制片', exact: true })).toBeVisible();
  await expect(page.getByText('更多：技术记录', { exact: true })).toBeVisible();
  await expect(page.getByText('脱水开始', { exact: true })).toHaveCount(0);
  await expect(page.getByText('包埋开始', { exact: true })).toHaveCount(0);
  await expect(page.getByText('Physical Trace', { exact: true })).toHaveCount(0);
  await expectNoPageOverflow(page);
});

test('PX03C-R1：完成后从原常规制片队列进入下一项', async ({ page }, testInfo) => {
  const caseId = await prepareRoutineTask(page, testInfo);
  await logout(page);
  await prepareRoutineTask(page, testInfo);
  await page.goto(
    `/v2/production/${caseId}?origin=workbench&queue=ROUTINE_PRODUCTION&returnTo=${encodeURIComponent('/v2/workbench?queue=ROUTINE_PRODUCTION')}`,
  );
  const scan = page.getByPlaceholder('扫描或输入玻片号');
  for (const code of ['A1-HE', 'A2-HE']) {
    await scan.fill(code);
    await scan.press('Enter');
    await expect(page.getByRole('status').filter({ hasText: `玻片 ${code} 已完成` })).toBeVisible();
  }

  const next = page.getByRole('button', { name: '完成并下一项', exact: true });
  await expect(next).toBeVisible();
  await next.click();
  await expect(page).toHaveURL(/\/v2\/production\/[^?]+\?.*queue=ROUTINE_PRODUCTION/);
  expect(new URL(page.url()).pathname).not.toBe(`/v2/production/${caseId}`);
});

test('PX03C：TechnicalOrder 进入单一医嘱执行工作区', async ({ page }) => {
  await prepareTechnicalOrder(page);
  await openProductionQueue(page, '技术医嘱');

  await expect(page).toHaveURL(/\/v2\/technical-orders\/[^?]+\?.*origin=workbench/);
  await expect(page.getByRole('region', { name: '病例中心', exact: true })).toHaveCount(0);
  await expect(page.getByRole('region', { name: '技术医嘱工作区' })).toBeVisible();
  await expect(page.getByRole('heading', { name: '技术医嘱', exact: true })).toBeVisible();
  await expect(page.getByText('其他生产能力', { exact: true })).toHaveCount(0);
  await expectNoPageOverflow(page);
});

test('PX03C：FrozenRound 进入时间与快速送诊工作区', async ({ page }, testInfo) => {
  await prepareFrozenTask(page, testInfo);
  await openProductionQueue(page, '冰冻制片');

  await expect(page).toHaveURL(/\/v2\/frozen\/[^?]+\?.*origin=workbench/);
  await expect(page.getByRole('region', { name: '病例中心', exact: true })).toHaveCount(0);
  await expect(page.getByRole('region', { name: '冰冻工作区' })).toBeVisible();
  await expect(page.getByRole('heading', { name: /冰冻第 \d+ 轮/ })).toBeVisible();
  await expect(page.getByText('等待', { exact: false }).first()).toBeVisible();
  await expect(page.getByText('完成并送诊', { exact: true })).toBeVisible();
  await expectNoPageOverflow(page);
});

test('PX03C：无数字切片时 Viewer 区域保持稳定', async ({ page }) => {
  await login(page, 'doctor-a');
  await page.getByRole('tab', { name: /待接诊/ }).click();
  await page.locator('.workbench-dense-row').first().click();

  await expect(page).toHaveURL(/\/v2\/diagnosis\/[^?]+\?origin=workbench/);
  await expect(page.getByText('WSI Viewer', { exact: true })).toBeVisible();
  await expect(page.getByText('当前玻片暂无数字切片', { exact: true })).toBeVisible();
  await expect(page.getByText('仍可从材料列表切换其他玻片。', { exact: true })).toBeVisible();
  await expect(page.getByRole('complementary', { name: '诊断编辑', exact: true })).toBeVisible();
  await expectNoPageOverflow(page);

  const [material, viewer, editor] = await Promise.all([
    page.getByLabel('材料与玻片').boundingBox(),
    page.getByLabel('WSI 阅片主区域').boundingBox(),
    page.getByRole('complementary', { name: '诊断编辑', exact: true }).boundingBox(),
  ]);
  expect(viewer?.width ?? 0).toBeGreaterThan(material?.width ?? 0);
  expect(viewer?.width ?? 0).toBeGreaterThan(editor?.width ?? 0);
});

test('PX03C：Diagnosis 首屏直接阅片并切换数字切片', async ({ page }, testInfo) => {
  const caseId = await prepareRoutineTask(page, testInfo);
  await bindLocalWsi(page, caseId);
  await completeRoutineSlides(page, caseId);
  await logout(page);
  await login(page, 'doctor-c');
  await page.goto(
    `/v2/diagnosis/${caseId}?origin=workbench&queue=INITIAL&returnTo=${encodeURIComponent('/v2/workbench?queue=INITIAL')}`,
  );

  const viewer = page.getByLabel('WSI 阅片主区域');
  await expect(viewer.locator('.openseadragon-container').first()).toBeVisible();
  await expect(viewer.locator('.navigator')).toBeVisible();
  const slides = page.getByLabel('材料与玻片').locator('.diagnosis-slide-button');
  await expect(slides).toHaveCount(2);
  await slides.nth(1).click();
  await expect(slides.nth(1)).toHaveClass(/active/);
  await expect(viewer.locator('.openseadragon-container').first()).toBeVisible();
  const claim = page.getByRole('button', { name: '接诊', exact: true });
  if (await claim.isVisible()) {
    await claim.click();
    await expect(page.getByRole('status').filter({ hasText: '接诊成功' })).toBeVisible();
  }
  await expect(page.getByText('镜下所见', { exact: true })).toBeVisible();
  await expect(page.getByText('病理诊断', { exact: true })).toBeVisible();
  await expectNoPageOverflow(page);
});
