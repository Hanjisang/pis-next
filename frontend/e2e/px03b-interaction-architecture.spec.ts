import { expect, test } from '@playwright/test';

import { expectNoPageOverflow, login } from './helpers';

test('PX03C：工作台与病例中心形成双轴交互，普通用户没有永久侧栏', async ({ page }) => {
  await login(page, 'doctor-a');

  const primaryNavigation = page.getByRole('navigation', { name: '一级导航' });
  await expect(primaryNavigation).toHaveCount(0);
  await expect(page.locator('.app-sidebar')).toHaveCount(0);

  await expect(page.getByRole('region', { name: '我的工作台' })).toBeVisible();
  await page.getByRole('tab', { name: /待接诊/ }).click();
  const firstWorkItem = page.locator('.workbench-dense-row').first();
  await expect(firstWorkItem).toBeVisible();
  await firstWorkItem.click();

  await expect(page).toHaveURL(/\/v2\/diagnosis\/[^?]+\?origin=workbench/);
  await expect(page.getByRole('region', { name: '病例中心', exact: true })).toHaveCount(0);
  await expect(page.locator('.case-focus-route-bar')).toHaveCount(0);
  const pathologyNo = (await page.locator('.case-title-line h2').innerText()).trim();
  await expect(page.getByLabel('病例固定上下文')).toContainText(pathologyNo);
  await expect(page.getByLabel('病例固定上下文')).toContainText('当前：');
  await expect(page.getByLabel('病例固定上下文')).toContainText('性别');
  await expect(page.getByLabel('病例固定上下文')).toContainText('年龄');
  await expect(page.getByText('WSI Viewer', { exact: true })).toBeVisible();
  await expect(page.getByRole('complementary', { name: '诊断编辑', exact: true })).toBeVisible();
  await page.getByRole('button', { name: '← 返回工作台' }).click();
  await expect(page.getByRole('tab', { name: /待接诊/ })).toHaveAttribute('aria-selected', 'true');
  await expectNoPageOverflow(page);
});

test('PX03C：Global Search 的病例结果回到病例中心 Overview', async ({ page }) => {
  await login(page, 'doctor-a');
  await page.getByRole('tab', { name: /待接诊/ }).click();
  const firstWorkItem = page.locator('.workbench-dense-row').first();
  await expect(firstWorkItem).toBeVisible();
  await firstWorkItem.click();
  const pathologyNo = (await page.locator('.case-title-line h2').innerText()).trim();

  await page.getByRole('button', { name: '← 返回工作台' }).click();
  await page.keyboard.press('Control+K');
  const search = page.getByRole('dialog', { name: '全局查询' });
  await expect(search).toBeVisible();
  await search.getByRole('textbox').fill(pathologyNo);
  await search.getByRole('button', { name: '查询', exact: true }).click();
  await search
    .getByRole('button', { name: new RegExp(pathologyNo) })
    .first()
    .click();

  await expect(page).toHaveURL(/\/v2\/cases\//);
  await expect(page.getByLabel('病例中心')).toContainText(pathologyNo);
  await expect(page.getByLabel('病例基本信息')).toBeVisible();
});
