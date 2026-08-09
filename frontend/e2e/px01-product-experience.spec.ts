import { expect, test, type Page } from '@playwright/test';

import { expectAccessibleButtons, expectNoPageOverflow, login } from './helpers';

async function openCaseFromSearch(page: Page, pathologyNo: string) {
  await page.keyboard.press('Control+K');
  const search = page.getByRole('dialog', { name: '全局查询' });
  await expect(search).toBeVisible();
  await search.getByRole('textbox').fill(pathologyNo);
  await search.getByRole('button', { name: '查询', exact: true }).click();
  await search
    .getByRole('button', { name: new RegExp(pathologyNo) })
    .first()
    .click();
  await expect(page.getByLabel('病例中心')).toBeVisible();
}

test('病例中心统一展示材料关系、责任链和业务历史', async ({ page }) => {
  const pathologyNo = process.env.PIS_E2E_PATHOLOGY_NO;
  if (!pathologyNo) throw new Error('PIS_E2E_PATHOLOGY_NO is required');

  await login(page, 'doctor-c');
  await openCaseFromSearch(page, pathologyNo);

  await expect(page.getByRole('heading', { name: '标本、蜡块与玻片' })).toBeVisible();
  await expect(page.getByRole('button', { name: /进入诊断/ })).toBeVisible();
  await expect(page.getByRole('button', { name: /业务历史/ }).first()).toBeVisible();

  await page
    .getByRole('button', { name: /业务历史/ })
    .first()
    .click();
  await expect(page.getByRole('heading', { name: '病例业务历史' })).toBeVisible();
  await expect(page.getByText('按业务时间查看病例从登记到报告的关键事实。')).toBeVisible();
  await expect(page.locator('.timeline-list')).toBeVisible();
  await expectAccessibleButtons(page);
  await expectNoPageOverflow(page);
});

test('技术人员从病例中心进入制片并看到轻量技术过程工作台', async ({ page }) => {
  const pathologyNo = process.env.PIS_E2E_PATHOLOGY_NO;
  if (!pathologyNo) throw new Error('PIS_E2E_PATHOLOGY_NO is required');

  await login(page, 'technician');
  await openCaseFromSearch(page, pathologyNo);
  await page.getByRole('button', { name: '查看制片', exact: true }).click();

  await expect(page).toHaveURL(/\/v2\/production\//);
  await expect(page.getByRole('region', { name: '玻片制片工作台' })).toBeVisible();
  await expect(page.getByRole('heading', { name: '脱水、包埋、切片、染色、封片' })).toBeVisible();
  await expect(page.getByText('轻量记录', { exact: true })).toBeVisible();
  await expect(page.getByRole('tablist', { name: '制片队列状态' })).toBeVisible();
  await expect(page.getByRole('textbox', { name: '扫码完成玻片' })).toBeVisible();
  await expectAccessibleButtons(page);
  await expectNoPageOverflow(page);
});

test('诊断工作区可以在病例上下文内打开数字切片查看器', async ({ page }) => {
  const pathologyNo = process.env.PIS_E2E_DIGITAL_PATHOLOGY_NO;
  if (!pathologyNo) throw new Error('PIS_E2E_DIGITAL_PATHOLOGY_NO is required');

  await login(page, 'doctor-c');
  await openCaseFromSearch(page, pathologyNo);
  await page.getByRole('button', { name: /进入诊断/ }).click();
  await expect(page).toHaveURL(/\/v2\/diagnosis\//);

  await page.getByRole('button', { name: /数字切片/ }).click();
  await expect(page.getByRole('button', { name: /打开/ }).first()).toBeVisible();
  await page.getByRole('button', { name: /打开/ }).first().click();

  const viewer = page.getByRole('region', { name: '数字切片阅片器' });
  await expect(viewer).toBeVisible();
  await expect(viewer.getByRole('button', { name: '放大' })).toBeVisible();
  await viewer.getByRole('button', { name: '放大' }).click();
  await expect(viewer.getByText('125%')).toBeVisible();
  await viewer.getByRole('button', { name: '还原视图' }).click();
  await expect(viewer.getByText('100%')).toBeVisible();
  await expectAccessibleButtons(page);
  await expectNoPageOverflow(page);
});

test('取材人员从病例中心进入病例级取材工作区', async ({ page }) => {
  const pathologyNo = process.env.PIS_E2E_PATHOLOGY_NO;
  if (!pathologyNo) throw new Error('PIS_E2E_PATHOLOGY_NO is required');

  await login(page, 'technician');
  await openCaseFromSearch(page, pathologyNo);
  await page.getByRole('button', { name: '进入取材', exact: true }).click();

  await expect(page).toHaveURL(/\/v2\/grossing\//);
  await expect(page.getByRole('region', { name: '病例取材工作区' })).toBeVisible();
  await expect(page.getByRole('complementary', { name: '标本列表' })).toBeVisible();
  await expect(page.getByText('蜡块', { exact: true }).first()).toBeVisible();
  await expect(
    page.locator('.sticky-form-actions[aria-label="取材操作"]').getByText('取材已完成', {
      exact: true,
    }),
  ).toBeVisible();
  await expectAccessibleButtons(page);
  await expectNoPageOverflow(page);
});

test('冰冻病例中心清楚显示多轮状态和当前轮次', async ({ page }) => {
  await login(page, 'registrar');
  await openCaseFromSearch(page, 'F-000003');
  await page.getByRole('button', { name: /查看冰冻/ }).click();

  await expect(page).toHaveURL(/\/v2\/frozen\//);
  await expect(page.getByRole('region', { name: '冰冻工作区' })).toBeVisible();
  await expect(page.getByRole('navigation', { name: '冰冻轮次' })).toBeVisible();
  await expect(page.getByText('冰冻第 1 轮', { exact: true })).toBeVisible();
  await expect(page.getByText('冰冻第 2 轮', { exact: true })).toBeVisible();
  await expect(page.getByText('冰冻第 3 轮', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: /进入常规流程/ })).toBeVisible();
  await expectAccessibleButtons(page);
  await expectNoPageOverflow(page);
});

test('病例报告页保留原始、补充和签发历史', async ({ page }) => {
  await login(page, 'doctor-c');
  await openCaseFromSearch(page, 'F-000003');
  await page
    .getByRole('navigation', { name: '病例内容' })
    .getByRole('button', { name: /^报告/ })
    .click();

  await expect(page.getByRole('heading', { name: '报告历史' })).toBeVisible();
  await expect(page.getByText('原始报告', { exact: true }).first()).toBeVisible();
  await expect(page.getByText('R001', { exact: true })).toBeVisible();
  await expect(page.getByText('R002', { exact: true })).toBeVisible();
  await expect(page.getByText('R003', { exact: true })).toBeVisible();
  await expectAccessibleButtons(page);
  await expectNoPageOverflow(page);
});

test('技术人员首页直接进入技术医嘱执行工作台', async ({ page }) => {
  await login(page, 'technician');
  await expect(page.getByText('我的工作台', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '技术医嘱', exact: true }).click();

  await expect(page).toHaveURL(/\/v2\/technical-orders/);
  await expect(page.getByRole('region', { name: '技术医嘱工作台' })).toBeVisible();
  await expect(page.getByRole('heading', { name: '技术执行工作台' })).toBeVisible();
  await expect(page.getByRole('tablist', { name: '技术医嘱状态' })).toBeVisible();
  await expect(page.getByRole('tab', { name: /待处理/ })).toBeVisible();
  await expectAccessibleButtons(page);
  await expectNoPageOverflow(page);
});
