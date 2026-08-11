import { expect, test } from '@playwright/test';

import { expectNoPageOverflow, login } from './helpers';

test('PX03B：工作台与病例中心形成双轴交互，普通导航不暴露业务模块', async ({ page }) => {
  await login(page, 'doctor-a');

  const primaryNavigation = page.getByRole('navigation', { name: '一级导航' });
  await expect(primaryNavigation.getByRole('button')).toHaveCount(1);
  await expect(
    primaryNavigation.getByRole('button', { name: '工作台', exact: true }),
  ).toBeVisible();
  await expect(primaryNavigation.getByRole('button', { name: /诊断|生产|报告|病例/ })).toHaveCount(
    0,
  );

  await expect(page.getByText('工作台 · 人的工作中心', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: /待接诊病例/ }).click();
  const firstWorkItem = page.locator('.personal-queue-row').first();
  await expect(firstWorkItem).toBeVisible();
  await firstWorkItem.click();

  await expect(page).toHaveURL(/\/v2\/cases\/[^?]+\?focus=/);
  await expect(page.getByLabel('病例中心')).toBeVisible();
  await expect(page.getByRole('button', { name: '← 工作台', exact: true })).toBeVisible();
  await expect(page.getByLabel('病例固定上下文')).toContainText('病理号');
  await expect(page.getByLabel('病例固定上下文')).toContainText('患者姓名');
  await expect(page.getByLabel('病例固定上下文')).toContainText('性别');
  await expect(page.getByLabel('病例固定上下文')).toContainText('年龄');
  await expect(page.getByLabel('病例固定上下文')).toContainText('当前处理人');
  await expect(page.getByLabel('病例固定上下文')).toContainText('报告状态');
  await expect(page.getByRole('navigation', { name: '病例视图' })).toContainText('概览');
  await expect(page.getByRole('navigation', { name: '病例视图' })).toContainText('材料');
  await expect(page.getByRole('navigation', { name: '病例视图' })).toContainText('诊断与阅片');
  await expect(page.getByRole('navigation', { name: '病例视图' })).toContainText('报告');
  await expect(page.getByRole('navigation', { name: '病例视图' })).toContainText('病例记录');
  await expect(page.getByText('当前工作', { exact: true })).toBeVisible();
  await expectNoPageOverflow(page);
});

test('PX03B：Global Search 的病例结果回到病例中心', async ({ page }) => {
  await login(page, 'doctor-a');
  await page.getByRole('button', { name: /待接诊病例/ }).click();
  const firstWorkItem = page.locator('.personal-queue-row').first();
  await expect(firstWorkItem).toBeVisible();
  await firstWorkItem.click();
  const pathologyNo = (await page.locator('.case-title-line h2').innerText()).trim();

  await page.getByRole('button', { name: '← 工作台', exact: true }).click();
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
});
