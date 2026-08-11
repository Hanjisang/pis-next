import { expect, test, type Page, type TestInfo } from '@playwright/test';

import { login } from './helpers';

test.setTimeout(180_000);

type CaseRef = { caseId: string; pathologyNo: string };

function suffix(testInfo: TestInfo) {
  return `${Date.now()}-${testInfo.project.name.replace(/[^a-z0-9]/gi, '')}`;
}

async function logout(page: Page) {
  await page.getByRole('button', { name: '退出' }).click();
  await expect(page.getByRole('region', { name: 'PIS V2 登录' })).toBeVisible();
}

async function registerCase(
  page: Page,
  testInfo: TestInfo,
  patientReference = `PX02B-${suffix(testInfo)}`,
): Promise<CaseRef> {
  const id = suffix(testInfo);
  await page.getByRole('button', { name: '登记', exact: true }).click();
  await page.getByRole('button', { name: '新增手工病例' }).click();
  await page.getByLabel('患者编号').fill(patientReference);
  await page.getByLabel('就诊号').fill(`PX02B-VISIT-${id}`);
  await page.getByLabel('申请号').fill(`PX02B-APPLICATION-${id}`);
  await page.getByLabel('业务类型', { exact: true }).selectOption({ label: '常规组织病理' });
  await page.getByLabel('取材部位').fill('PX02B 胃窦');
  await page.getByRole('button', { name: '确认登记' }).click();
  const completion = page.getByRole('status').filter({ hasText: '登记完成，病理号已生成' });
  await expect(completion).toBeVisible();
  const pathologyNo = (await completion.getByRole('strong').innerText()).split('：').at(-1)?.trim();
  expect(pathologyNo).toBeTruthy();
  await page.getByRole('button', { name: '病例中心' }).click();
  return {
    caseId: new URL(page.url()).pathname.split('/').filter(Boolean).at(-1)!,
    pathologyNo: pathologyNo!,
  };
}

async function createBlocks(page: Page, caseRef: CaseRef) {
  await page.goto(`/v2/grossing/${caseRef.caseId}`);
  await expect(page.getByLabel('病例取材工作区')).toBeVisible();
  await page.getByRole('button', { name: '开始取材' }).click();
  for (const code of ['A1', 'A2']) {
    await page.getByLabel('新蜡块编号').fill(code);
    await page.getByRole('button', { name: '+ 蜡块' }).click();
    await expect(page.getByRole('status').filter({ hasText: `蜡块 ${code} 已建立` })).toBeVisible();
  }
  await page.getByRole('button', { name: '完成取材' }).click();
  await expect(page.getByRole('status').filter({ hasText: '取材已完成' })).toBeVisible();
}

async function completePhase(page: Page, label: string) {
  await page.getByRole('tab', { name: new RegExp(`^${label}`) }).click();
  await page.getByRole('button', { name: `开始${label}`, exact: true }).click();
  await page.getByRole('button', { name: `完成${label}`, exact: true }).click();
  await expect(page.getByRole('status').filter({ hasText: `${label}已完成` })).toBeVisible();
}

test('PX02B：Histology 单一阶段队列、异常事实和统一历史', async ({ page }, testInfo) => {
  await login(page, 'registrar');
  const caseRef = await registerCase(page, testInfo);
  await logout(page);
  await login(page, 'technician');
  await createBlocks(page, caseRef);

  await page.goto(`/v2/production/${caseRef.caseId}`);
  await expect(page.getByRole('group', { name: '技术环节队列' })).toBeVisible();
  await expect(page.getByRole('button', { name: /待脱水/ })).toBeVisible();
  await expect(page.getByRole('button', { name: /待包埋/ })).toBeVisible();
  await expect(page.getByRole('button', { name: /待切片/ })).toBeVisible();
  await expect(page.getByText('PENDING', { exact: true })).toHaveCount(0);
  await expect(page.getByText('IN_PROGRESS', { exact: true })).toHaveCount(0);
  await expect(page.getByText('COMPLETED', { exact: true })).toHaveCount(0);

  const workList = page.getByRole('table', { name: '技术环节材料列表' });
  const firstRow = workList.locator('.histology-work-row:not(.header)').first();
  const slideCode = (await firstRow.locator('span').nth(3).locator('strong').innerText()).trim();
  await firstRow.click();
  await completePhase(page, '脱水');
  await completePhase(page, '包埋');

  await page.getByRole('tab', { name: /^切片/ }).click();
  await page.getByRole('button', { name: '开始切片', exact: true }).click();
  await page.getByRole('button', { name: '记录异常', exact: true }).click();
  await page.getByLabel('异常类型').selectOption({ label: '切片皱褶' });
  await page
    .getByPlaceholder('说明发生了什么以及后续处理')
    .fill('PX02B 记录切片皱褶，继续后续处理。');
  await page.getByRole('button', { name: '保存异常', exact: true }).click();
  await expect(page.getByRole('status').filter({ hasText: '切片异常已记录' })).toBeVisible();
  await page.getByRole('button', { name: '完成切片', exact: true }).click();
  await expect(page.getByRole('status').filter({ hasText: '切片已完成' })).toBeVisible();
  await completePhase(page, '染色');
  await completePhase(page, '封片');

  const scan = page.getByRole('textbox', { name: '扫码完成玻片' });
  await scan.fill(slideCode);
  await scan.press('Enter');
  await expect(
    page.getByRole('status').filter({ hasText: `玻片 ${slideCode} 已完成` }),
  ).toBeVisible();

  await page.goto(`/v2/cases/${caseRef.caseId}`);
  await page.getByRole('button', { name: '查看业务历史' }).click();
  const history = page.getByRole('dialog', { name: '历史记录' });
  await expect(history).toBeVisible();
  await expect(history.getByText('新增蜡块', { exact: true }).first()).toBeVisible();
  await expect(history.getByText('完成取材', { exact: true }).first()).toBeVisible();
  await expect(history.getByText('切片记录异常', { exact: true }).first()).toBeVisible();
  await expect(history.getByText('封片完成', { exact: true }).first()).toBeVisible();
});

test('PX02B：登记队列、病例深链和配置库位入口保持业务语言', async ({ page }) => {
  await login(page, 'registrar');
  await page.getByRole('button', { name: '登记', exact: true }).click();
  await expect(page.getByRole('region', { name: '待登记申请队列' })).toBeVisible();
  await expect(page.getByRole('heading', { name: '待登记申请', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: '今日已登记', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '新增手工病例' })).toBeVisible();

  await page.keyboard.press('Control+K');
  await expect(page.getByRole('dialog', { name: '全局查询' })).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog', { name: '全局查询' })).toHaveCount(0);
});

test('PX02B：患者历史从诊断工作区直接可见', async ({ page }, testInfo) => {
  const patientReference = `PX02B-HISTORY-${suffix(testInfo)}`;
  await login(page, 'registrar');
  const firstCase = await registerCase(page, testInfo, patientReference);
  const secondCase = await registerCase(page, testInfo, patientReference);
  await logout(page);

  await login(page, 'doctor-a');
  await page.goto(`/v2/diagnosis/${secondCase.caseId}`);
  await expect(page.getByRole('region', { name: '诊断工作区' }).last()).toBeVisible();
  await page.getByRole('button', { name: '历史病理', exact: true }).click();
  await expect(page.getByText(firstCase.pathologyNo, { exact: true })).toBeVisible();
});
