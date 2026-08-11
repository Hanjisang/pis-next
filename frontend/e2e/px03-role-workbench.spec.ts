import { expect, test } from '@playwright/test';

import { login } from './helpers';

test('PX03: 登记员追踪细胞病例，技师接收直接制片，医生进入公共池', async ({ page }, testInfo) => {
  const suffix = `${Date.now()}-${testInfo.project.name}`;
  const patientReference = `PX03-PATIENT-${suffix}`;

  await login(page, 'registrar');
  await page.getByRole('button', { name: '登记', exact: true }).click();
  await page.getByRole('button', { name: '新增手工病例' }).click();
  await page.getByRole('textbox', { name: '患者编号' }).fill(patientReference);
  await page.getByRole('textbox', { name: '就诊号' }).fill(`PX03-VISIT-${suffix}`);
  await page.getByRole('combobox', { name: '业务类型' }).selectOption({ label: '细胞病理' });
  await page.getByRole('textbox', { name: '取材部位' }).fill('合成细胞标本');
  await page.getByRole('button', { name: '确认登记' }).click();

  const completed = page.getByRole('status').filter({ hasText: '登记完成，病理号已生成' });
  await expect(completed).toBeVisible();
  const pathologyNo = (await completed.locator('strong').textContent())?.split('：').at(-1)?.trim();
  expect(pathologyNo).toBeTruthy();
  const registrarWorkbenchResponse = page.waitForResponse(
    (response) =>
      response.url().endsWith('/api/v2/my-workbench') && response.request().method() === 'GET',
  );
  await page.goto('/v2/workbench');
  await registrarWorkbenchResponse;
  await page.locator('.segmented-control').getByRole('button', { name: '我登记的病例' }).click();
  const registeredRow = page.getByRole('button', { name: new RegExp(pathologyNo!) });
  await expect(registeredRow).toContainText('待细胞制片');

  await page.getByRole('button', { name: '退出' }).click();
  await page.waitForURL(/\/v2\/workbench/);
  await login(page, 'technician');
  const cytologyQueue = page.getByRole('button', { name: /待细胞制片/ });
  const queueBefore = Number((await cytologyQueue.textContent())?.match(/\d+$/)?.[0] ?? 0);
  expect(queueBefore).toBeGreaterThan(0);
  await cytologyQueue.click();
  await expect(page).toHaveURL(/\/v2\/production$/);
  await page.getByRole('button', { name: new RegExp(pathologyNo!) }).click();
  await expect(page).toHaveURL(new RegExp(`/v2/production/`));
  await expect(page.getByRole('heading', { name: '直接建立玻片' })).toBeVisible();
  await expect(page.getByText('蜡块可选')).toBeVisible();

  await page.getByRole('textbox', { name: '玻片号' }).fill('A-1');
  await page.getByRole('button', { name: '建立直接玻片' }).click();
  await expect(page.getByText('直接玻片 A-1 已建立')).toBeVisible();
  await page.getByRole('button', { name: '完成玻片' }).click();
  await expect(page.getByText('直接玻片 A-1 已完成')).toBeVisible();

  const technicianWorkbenchResponse = page.waitForResponse(
    (response) =>
      response.url().endsWith('/api/v2/my-workbench') && response.request().method() === 'GET',
  );
  await page.goto('/v2/workbench');
  await technicianWorkbenchResponse;
  const queueAfter = Number(
    (await page.getByRole('button', { name: /待细胞制片/ }).textContent())?.match(/\d+$/)?.[0] ?? 0,
  );
  expect(queueAfter).toBe(queueBefore - 1);
  await expect(page.getByRole('button', { name: new RegExp(pathologyNo!) })).toHaveCount(0);

  await page.getByRole('button', { name: '退出' }).click();
  await page.waitForURL(/\/v2\/workbench/);
  await login(page, 'doctor-a');
  await page.getByRole('button', { name: '公共池' }).click();
  await expect(page.getByRole('button', { name: new RegExp(pathologyNo!) })).toBeVisible();
});
