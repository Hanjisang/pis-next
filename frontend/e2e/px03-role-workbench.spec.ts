import { expect, test } from '@playwright/test';

import { login } from './helpers';

test('PX03C: 登记员追踪细胞病例，技师从工作台直接进入细胞制片，医生进入待接诊', async ({
  page,
}, testInfo) => {
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
  await page.getByRole('tab', { name: '我登记的病例', exact: true }).click();
  const registeredRow = page.getByRole('button', { name: new RegExp(pathologyNo!) });
  await expect(registeredRow).toContainText('待细胞制片');

  await page.getByRole('button', { name: '退出' }).click();
  await page.waitForURL(/\/v2\/workbench/);
  await login(page, 'technician');
  await page.getByRole('tab', { name: '生产队列', exact: true }).click();
  const cytologyQueue = page
    .locator('.workbench-production-tabs')
    .getByRole('tab', { name: /细胞制片/ });
  await expect(cytologyQueue).toBeVisible();
  await cytologyQueue.click();
  const cytologyRow = page.locator('.production-task-row').filter({ hasText: pathologyNo! });
  await expect(page.getByText('待脱水', { exact: true })).toHaveCount(0);
  await expect(cytologyRow).toBeVisible();
  const queueBefore = Number((await cytologyQueue.textContent())?.match(/\d+$/)?.[0] ?? 0);
  expect(queueBefore).toBeGreaterThan(0);
  await cytologyRow.click();
  await expect(page).toHaveURL(/\/v2\/cases\/[^?]+\?focus=production/);
  await expect(page.getByRole('heading', { name: '细胞制片', exact: true })).toBeVisible();
  await expect(page.getByText('不需要蜡块')).toBeVisible();

  await page.getByRole('textbox', { name: '玻片号' }).fill('A-1');
  await page.getByRole('button', { name: '新增玻片' }).click();
  await expect(page.getByText('直接玻片 A-1 已建立')).toBeVisible();
  await page.getByRole('button', { name: '扫码完成' }).first().click();
  await expect(page.getByText('玻片 A-1 已完成')).toBeVisible();

  const technicianWorkbenchResponse = page.waitForResponse(
    (response) =>
      response.url().endsWith('/api/v2/production-workbench') &&
      response.request().method() === 'GET',
  );
  await page.goto('/v2/workbench');
  await technicianWorkbenchResponse;
  const queueAfter = Number(
    (
      await page
        .getByRole('button', { name: /细胞制片/ })
        .first()
        .textContent()
    )?.match(/\d+$/)?.[0] ?? 0,
  );
  expect(queueAfter).toBe(queueBefore - 1);
  await expect(page.getByRole('button', { name: new RegExp(pathologyNo!) })).toHaveCount(0);

  await page.getByRole('button', { name: '退出' }).click();
  await page.waitForURL(/\/v2\/workbench/);
  await login(page, 'doctor-a');
  await page.getByRole('tab', { name: '待接诊', exact: true }).click();
  await expect(page.getByRole('button', { name: new RegExp(pathologyNo!) })).toBeVisible();
});
