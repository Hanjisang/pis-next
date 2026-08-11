import { expect, test } from '@playwright/test';

import { expectNoPageOverflow, login } from './helpers';

test('PX03C-R1：登记员只看到登记相关队列', async ({ page }) => {
  await login(page, 'registrar');

  await expect(page.getByRole('tab', { name: /待登记/ })).toBeVisible();
  await expect(page.getByRole('tab', { name: /退回待处理/ })).toBeVisible();
  await expect(page.getByRole('tab', { name: /我今天登记/ })).toBeVisible();
  await expect(page.getByRole('tab', { name: /常规制片/ })).toHaveCount(0);
  await expect(page.getByRole('tab', { name: /待初诊/ })).toHaveCount(0);
  await expect(page.getByRole('tab', { name: /待审核/ })).toHaveCount(0);
});

test('PX03C-R1：技术员只看到授权生产队列', async ({ page }) => {
  await login(page, 'technician');

  await expect(page.getByRole('tab', { name: /常规制片/ })).toBeVisible();
  await expect(page.getByRole('tab', { name: /细胞制片/ })).toBeVisible();
  await expect(page.getByRole('tab', { name: /冰冻制片/ })).toBeVisible();
  await expect(page.getByRole('tab', { name: /技术医嘱/ })).toBeVisible();
  await expect(page.getByRole('tab', { name: /待登记/ })).toHaveCount(0);
  await expect(page.getByRole('tab', { name: /待初诊/ })).toHaveCount(0);
});

test('PX03C-R1：医生只看到诊断相关队列', async ({ page }) => {
  await login(page, 'doctor-a');

  await expect(page.getByRole('tab', { name: /待初诊/ })).toBeVisible();
  await expect(page.getByRole('tab', { name: /待复诊/ })).toBeVisible();
  await expect(page.getByRole('tab', { name: /新技术结果/ })).toBeVisible();
  await expect(page.getByRole('tab', { name: /待接诊/ })).toBeVisible();
  await expect(page.getByRole('tab', { name: /细胞制片/ })).toHaveCount(0);
  await expect(page.getByRole('tab', { name: /待登记/ })).toHaveCount(0);
});

test('PX03C-R1：多能力用户在一个工作台看到授权队列并集', async ({ page }) => {
  await login(page, 'admin');

  await expect(page.getByRole('tab', { name: /待登记/ })).toBeVisible();
  await expect(page.getByRole('tab', { name: /细胞制片/ })).toBeVisible();
  await expect(page.getByRole('tab', { name: /待初诊/ })).toBeVisible();
  await expect(page.getByRole('tab', { name: /待审核/ })).toBeVisible();
});

test('PX03C-R1：工作台来源返回原队列并恢复筛选排序', async ({ page }) => {
  await login(page, 'doctor-a');
  const initialQueue = page.getByRole('tab', { name: /待初诊/ });
  await initialQueue.click();
  const firstItem = page.locator('.workbench-dense-row').first();
  await expect(firstItem).toBeVisible();
  const pathologyNo = (await firstItem.locator('strong').first().innerText()).trim();
  await page.getByRole('searchbox', { name: '筛选当前队列' }).fill(pathologyNo);
  await page.getByRole('combobox', { name: '工作列表排序' }).selectOption('newest');
  await firstItem.click();

  await expect(page).toHaveURL(/\/v2\/diagnosis\/[^?]+\?origin=workbench/);
  await page.getByRole('button', { name: '← 返回工作台' }).click();
  await expect(initialQueue).toHaveAttribute('aria-selected', 'true');
  await expect(page.getByRole('searchbox', { name: '筛选当前队列' })).toHaveValue(pathologyNo);
  await expect(page.getByRole('combobox', { name: '工作列表排序' })).toHaveValue('newest');
});

test('PX03C-R1：病例中心来源的诊断返回当前病例', async ({ page }) => {
  await login(page, 'doctor-a');
  await page.getByRole('tab', { name: /待初诊/ }).click();
  await page.locator('.workbench-dense-row').first().click();
  await page.getByRole('button', { name: '病例概览' }).click();
  await expect(page.getByRole('region', { name: '病例中心', exact: true })).toBeVisible();
  await page.getByRole('button', { name: '进入诊断', exact: true }).click();

  await expect(page).toHaveURL(/\/v2\/diagnosis\/[^?]+\?.*origin=case/);
  await page.getByRole('button', { name: '← 返回病例' }).click();
  await expect(page.getByRole('region', { name: '病例中心', exact: true })).toBeVisible();
  await expectNoPageOverflow(page);
});
