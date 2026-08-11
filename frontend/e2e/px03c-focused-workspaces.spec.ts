import { expect, test } from '@playwright/test';

import { expectNoPageOverflow, login } from './helpers';

async function openProductionQueue(page: Parameters<typeof login>[0], queueLabel: string) {
  await login(page, 'technician');
  await expect(page.getByRole('tab', { name: '生产队列', exact: true })).toBeVisible();
  await page.getByRole('tab', { name: '生产队列', exact: true }).click();

  const queueTab = page
    .locator('.workbench-production-tabs')
    .getByRole('tab', { name: new RegExp(queueLabel) });
  await expect(queueTab).toBeVisible();
  await queueTab.click();

  const taskRow = page.locator('.production-task-row').first();
  await expect(taskRow).toBeVisible();
  await taskRow.click();
  return queueTab;
}

test('PX03C：常规制片进入 Block／Slide 聚焦工作区', async ({ page }) => {
  await openProductionQueue(page, '常规制片');

  await expect(page).toHaveURL(/\/v2\/cases\/[^?]+\?focus=production/);
  await expect(page.getByRole('region', { name: '病例中心', exact: true })).toBeVisible();
  await expect(page.getByRole('region', { name: 'Block / Slide 工作表' })).toBeVisible();
  await expect(page.getByRole('heading', { name: '常规制片', exact: true })).toBeVisible();
  await expect(page.getByText('更多：技术记录', { exact: true })).toBeVisible();
  await expect(page.getByText('脱水开始', { exact: true })).toHaveCount(0);
  await expect(page.getByText('包埋开始', { exact: true })).toHaveCount(0);
  await expect(page.getByText('Physical Trace', { exact: true })).toHaveCount(0);
  await expectNoPageOverflow(page);
});

test('PX03C：TechnicalOrder 进入单一医嘱执行工作区', async ({ page }) => {
  await openProductionQueue(page, '技术医嘱');

  await expect(page).toHaveURL(/\/v2\/cases\/[^?]+\?focus=technical-order/);
  await expect(page.getByRole('region', { name: '病例中心', exact: true })).toBeVisible();
  await expect(page.getByRole('region', { name: '技术医嘱工作区' })).toBeVisible();
  await expect(page.getByRole('heading', { name: '技术医嘱', exact: true })).toBeVisible();
  await expect(page.getByText('其他生产能力', { exact: true })).toHaveCount(0);
  await expectNoPageOverflow(page);
});

test('PX03C：FrozenRound 进入时间与快速送诊工作区', async ({ page }) => {
  await openProductionQueue(page, '冰冻制片');

  await expect(page).toHaveURL(/\/v2\/cases\/[^?]+\?focus=frozen/);
  await expect(page.getByRole('region', { name: '病例中心', exact: true })).toBeVisible();
  await expect(page.getByRole('region', { name: '冰冻工作区' })).toBeVisible();
  await expect(page.getByRole('heading', { name: /冰冻第 \d+ 轮/ })).toBeVisible();
  await expect(page.getByText('等待', { exact: false }).first()).toBeVisible();
  await expect(page.getByText('完成并送诊', { exact: true })).toBeVisible();
  await expectNoPageOverflow(page);
});

test('PX03C：无数字切片时 Viewer 区域保持稳定', async ({ page }) => {
  await login(page, 'doctor-a');
  await page.getByRole('tab', { name: '待接诊', exact: true }).click();
  await page.locator('.personal-queue-row').first().click();

  await expect(page).toHaveURL(/\/v2\/cases\/[^?]+\?focus=diagnosis/);
  await expect(page.getByText('WSI Viewer', { exact: true })).toBeVisible();
  await expect(page.getByText('当前玻片暂无数字切片', { exact: true })).toBeVisible();
  await expect(page.getByText('仍可从材料列表切换其他玻片。', { exact: true })).toBeVisible();
  await expect(page.getByRole('complementary', { name: '诊断编辑', exact: true })).toBeVisible();
  await expectNoPageOverflow(page);
});
