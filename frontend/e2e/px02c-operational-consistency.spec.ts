import { expect, test, type Page, type TestInfo } from '@playwright/test';

import { login } from './helpers';

test.setTimeout(180_000);

type CaseRef = { caseId: string; pathologyNo: string };

function suffix(testInfo: TestInfo) {
  return `${Date.now()}-${testInfo.project.name.replace(/[^a-z0-9]/gi, '')}`;
}

function nextDueDateTime() {
  const date = new Date(Date.now() + 120_000);
  date.setSeconds(0, 0);
  const pad = (value: number) => String(value).padStart(2, '0');
  return {
    value: `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(
      date.getHours(),
    )}:${pad(date.getMinutes())}`,
    dueAt: date.getTime(),
  };
}

async function logout(page: Page) {
  await page.getByRole('button', { name: '退出' }).click();
  await expect(page.getByRole('region', { name: 'PIS V2 登录' })).toBeVisible();
}

async function registerCase(page: Page, testInfo: TestInfo, patientReference?: string) {
  const id = suffix(testInfo);
  const patient = patientReference ?? `PX02C-${id}`;
  await page.getByRole('button', { name: '登记', exact: true }).click();
  await page.getByRole('button', { name: '新增手工病例' }).click();
  await page.getByLabel('患者编号').fill(patient);
  await page.getByLabel('就诊号').fill(`PX02C-VISIT-${id}`);
  await page.getByLabel('申请号').fill(`PX02C-APPLICATION-${id}`);
  await page.getByLabel('业务类型', { exact: true }).selectOption({ label: '常规组织病理' });
  await page.getByLabel('取材部位').fill('PX02C 胃窦');
  await page.getByRole('button', { name: '确认登记' }).click();
  const completion = page.getByRole('status').filter({ hasText: '登记完成，病理号已生成' });
  await expect(completion).toBeVisible();
  const pathologyNo = (await completion.getByRole('strong').innerText()).split('：').at(-1)?.trim();
  expect(pathologyNo).toBeTruthy();
  await page.getByRole('button', { name: '病例中心' }).click();
  return {
    caseId: new URL(page.url()).pathname.split('/').filter(Boolean).at(-1)!,
    pathologyNo: pathologyNo!,
  } satisfies CaseRef;
}

async function createBlocks(page: Page, caseRef: CaseRef, complete = true) {
  await page.goto(`/v2/grossing/${caseRef.caseId}`);
  await expect(page.getByLabel('病例取材工作区')).toBeVisible();
  await page.getByRole('button', { name: '开始取材' }).click();
  for (const code of ['A1', 'A2']) {
    await page.getByLabel('新蜡块编号').fill(code);
    await page.getByRole('button', { name: '+ 蜡块' }).click();
    await expect(page.getByRole('status').filter({ hasText: `蜡块 ${code} 已建立` })).toBeVisible();
  }
  if (complete) {
    await page.getByRole('button', { name: '完成取材' }).click();
    await expect(page.getByRole('status').filter({ hasText: '取材已完成' })).toBeVisible();
  }
}

async function completePhase(page: Page, label: string) {
  await page.getByRole('tab', { name: new RegExp(`^${label}`) }).click();
  await page.getByRole('button', { name: `开始${label}`, exact: true }).click();
  await page.getByRole('button', { name: `完成${label}`, exact: true }).click();
  await expect(page.getByRole('status').filter({ hasText: `${label}已完成` })).toBeVisible();
}

test('PX02C：本地入站申请登记成功，取消申请不可登记', async ({ page }) => {
  await login(page, 'registrar');
  await page.getByRole('button', { name: '登记', exact: true }).click();
  const queue = page.getByRole('region', { name: '待登记申请队列' });
  await expect(queue).toBeVisible();

  const cancelled = queue.locator('button.registration-queue-row.cancelled').filter({
    hasText: 'LOCAL-APP-CANCELLED',
  });
  await expect(cancelled).toBeDisabled();
  await expect(cancelled).toContainText('不可登记');

  const pendingPanel = queue.locator('.registration-pending-panel');
  const pendingRow = pendingPanel.locator('button.registration-queue-row').first();
  await expect(pendingRow).toBeVisible();
  const applicationNo = (await pendingRow.locator('strong').innerText()).trim();
  await pendingRow.click();
  await page.getByLabel('取材部位').fill(`PX02C 入站标本 ${applicationNo}`);
  await page.getByRole('button', { name: '确认登记' }).click();
  await expect(
    page.getByRole('status').filter({ hasText: '登记完成，病理号已生成' }),
  ).toBeVisible();

  await page.goto('/v2/registration');
  const refreshedQueue = page.getByRole('region', { name: '待登记申请队列' });
  await expect(
    refreshedQueue.locator('.registration-pending-panel').getByText(applicationNo, { exact: true }),
  ).toHaveCount(0);
  await expect(
    refreshedQueue.locator('.registration-recent-panel .registration-queue-row').filter({
      hasText: applicationNo,
    }),
  ).toBeVisible();
});

test('PX02C：Histology 只使用单一查询投影并完成全部环节', async ({ page }, testInfo) => {
  await login(page, 'registrar');
  const caseRef = await registerCase(page, testInfo);
  await logout(page);
  await login(page, 'technician');
  await createBlocks(page, caseRef);

  const apiPaths: string[] = [];
  page.on('request', (request) => {
    const path = new URL(request.url()).pathname;
    if (path.startsWith('/api/v2/')) apiPaths.push(path);
  });
  await page.goto(`/v2/production/${caseRef.caseId}`);
  await expect(page.getByLabel('病理技术工作台')).toBeVisible();
  await page.getByText('展开可选技术记录（脱水、包埋、切片、染色、封片）', { exact: true }).click();
  await page.getByText('查看技术过程事实与异常记录', { exact: true }).click();
  await expect(page.getByRole('group', { name: '技术环节队列' })).toBeVisible();
  await expect(page.getByRole('button', { name: /待脱水/ })).toBeVisible();
  await expect(page.getByRole('button', { name: /待包埋/ })).toBeVisible();
  await expect(page.getByRole('button', { name: /待切片/ })).toBeVisible();
  await expect(page.getByRole('button', { name: /待染色/ })).toBeVisible();
  await expect(page.getByRole('button', { name: /待封片/ })).toBeVisible();
  await expect(page.getByText('PENDING', { exact: true })).toHaveCount(0);
  await expect(page.getByText('IN_PROGRESS', { exact: true })).toHaveCount(0);

  const workList = page.getByRole('table', { name: '技术环节材料列表' });
  const firstRow = workList.locator('.histology-work-row:not(.header)').first();
  const slideCode = (await firstRow.locator('span').nth(3).locator('strong').innerText()).trim();
  await firstRow.click();
  await completePhase(page, '脱水');
  await completePhase(page, '包埋');
  await completePhase(page, '切片');
  await completePhase(page, '染色');
  await completePhase(page, '封片');
  await page.getByRole('textbox', { name: '扫码完成玻片' }).fill(slideCode);
  await page.getByRole('textbox', { name: '扫码完成玻片' }).press('Enter');
  await expect(
    page.getByRole('status').filter({ hasText: `玻片 ${slideCode} 已完成` }),
  ).toBeVisible();

  const projectionResponse = await page.request.get(
    `/api/v2/histology-workbench?caseId=${caseRef.caseId}`,
  );
  expect(projectionResponse.ok()).toBeTruthy();
  const projection = (await projectionResponse.json()) as {
    slides: Array<{
      slideCode: string;
      currentPhase: string;
      derivedQueue: string;
      phases: Array<{ startedAt: string | null; completedAt: string | null }>;
    }>;
  };
  const slide = projection.slides.find((item) => item.slideCode === slideCode);
  expect(slide).toBeTruthy();
  expect(slide?.currentPhase).toBe('COMPLETED');
  expect(slide?.derivedQueue).toBe('COMPLETED');
  expect(slide?.phases.every((phase) => phase.startedAt && phase.completedAt)).toBeTruthy();
  expect(apiPaths).toContain('/api/v2/histology-workbench');
  expect(apiPaths.some((path) => path.includes('/slides/production-workbench'))).toBeFalsy();
  expect(apiPaths.some((path) => path === '/api/v2/histology/workbench')).toBeFalsy();
});

test('PX02C：历史显示业务编号和结构化修改前后值，不显示内部 UUID', async ({ page }, testInfo) => {
  await login(page, 'registrar');
  const caseRef = await registerCase(page, testInfo);
  await logout(page);
  await login(page, 'technician');
  await createBlocks(page, caseRef, false);
  await page.goto(`/v2/grossing/${caseRef.caseId}`);

  const specimenSite = page.getByLabel('当前标本取材部位');
  await specimenSite.fill('PX02C 修改后的标本部位');
  await page.getByRole('button', { name: '保存标本信息' }).click();
  await expect(page.getByRole('status').filter({ hasText: '标本 A 信息已保存' })).toBeVisible();

  const block = page.locator('.block-chip').filter({ hasText: 'A2' }).first();
  await block.getByRole('button', { name: '修改' }).click();
  await page.getByRole('textbox', { name: '修改蜡块 A2' }).fill('A2-EDIT');
  await page.getByRole('button', { name: '保存修改' }).click();
  await expect(page.getByRole('status').filter({ hasText: '蜡块已修改为 A2-EDIT' })).toBeVisible();
  await page.getByRole('button', { name: '完成取材' }).click();
  await expect(page.getByRole('status').filter({ hasText: '取材已完成' })).toBeVisible();

  const workspaceResponse = await page.request.get(`/api/v2/case-workspaces/${caseRef.caseId}`);
  expect(workspaceResponse.ok()).toBeTruthy();
  const workspace = (await workspaceResponse.json()) as {
    timeline: Array<{
      targetId: string | null;
      targetDisplayCode: string | null;
      changes?: Array<{
        fieldLabel: string;
        beforeValue: string | null;
        afterValue: string | null;
      }>;
    }>;
  };
  const blockChange = workspace.timeline.find((entry) =>
    entry.changes?.some((change) => change.afterValue === 'A2-EDIT'),
  );
  expect(blockChange).toBeTruthy();
  expect(blockChange?.targetDisplayCode).toBe('A2-EDIT');
  expect(blockChange?.targetId).toMatch(/^[0-9a-f-]{36}$/i);

  await page.goto(`/v2/cases/${caseRef.caseId}`);
  await page.getByRole('button', { name: '查看业务历史' }).click();
  const history = page.getByRole('dialog', { name: '历史记录' });
  await expect(history).toBeVisible();
  await expect(history.getByText('A2-EDIT', { exact: true }).first()).toBeVisible();
  await expect(history.getByText('A2', { exact: true }).first()).toBeVisible();
  const historyText = await history.innerText();
  expect(historyText).not.toMatch(/[0-9a-f]{8}-[0-9a-f-]{27,}/i);
});

test('PX02C：借阅预计归还、逾期推导和归还事实完整回显', async ({ page }, testInfo) => {
  await login(page, 'registrar');
  const caseRef = await registerCase(page, testInfo);
  const loanReference = `PX02C-${suffix(testInfo)}`;
  await logout(page);
  await login(page, 'technician');
  await createBlocks(page, caseRef);

  await page.goto(`/v2/material-custody/${caseRef.caseId}`);
  await expect(page.getByLabel('归档借阅工作台')).toBeVisible();
  await page.getByRole('button', { name: '借出 / 归还' }).click();
  await page
    .getByLabel(/选择蜡块/)
    .first()
    .check();
  await page.getByLabel('借阅人').fill(`${loanReference} 借阅人`);
  await page.getByLabel('借阅科室').fill('PX02C 病理科');
  await page.getByLabel('用途').fill('PX02C 复核');
  const due = nextDueDateTime();
  await page.getByLabel('预计归还日期').fill(due.value);
  await page.getByRole('button', { name: '借出所选材料' }).click();
  await expect(page.getByRole('status').filter({ hasText: '借阅已登记' })).toBeVisible();

  await page.goto('/v2/material-custody');
  await expect(page.getByRole('region', { name: '借阅记录' })).toBeVisible();
  await page.getByRole('button', { name: '今日到期' }).click();
  const dueRow = page
    .locator('.loan-row:not(.header)')
    .filter({ hasText: `${loanReference} 借阅人` })
    .first();
  await expect(dueRow.getByText('PX02C 病理科', { exact: true })).toBeVisible();
  await expect(dueRow.getByText('今日到期', { exact: true })).toBeVisible();

  await page.waitForTimeout(Math.max(1000, due.dueAt - Date.now() + 2000));
  await page.getByRole('button', { name: '逾期' }).click();
  const overdueRow = page
    .locator('.loan-row:not(.header)')
    .filter({ hasText: `${loanReference} 借阅人` })
    .first();
  await expect(overdueRow.getByText('逾期', { exact: true })).toBeVisible();
  await overdueRow.getByRole('button', { name: '归还' }).click();
  await expect(page.getByRole('status').filter({ hasText: '借阅已归还' })).toBeVisible();
  await page.getByRole('button', { name: '已归还' }).click();
  const returnedRow = page
    .locator('.loan-row:not(.header)')
    .filter({ hasText: `${loanReference} 借阅人` })
    .first();
  await expect(returnedRow.getByText('已归还', { exact: true })).toBeVisible();
  await expect(returnedRow.getByText('归还', { exact: true })).toHaveCount(0);
});
