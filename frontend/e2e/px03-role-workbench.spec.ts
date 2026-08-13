import { expect, test } from '@playwright/test';

import { login } from './helpers';

test.setTimeout(60_000);

test('PX03C: 登记员追踪细胞病例，技师从工作台直接进入细胞制片，医生进入待接诊', async ({
  page,
}, testInfo) => {
  const suffix = `${Date.now()}-${testInfo.project.name}`;
  const patientReference = `张三-${suffix.slice(-8)}`;

  await login(page, 'registrar');
  await page.goto('/v2/registration');
  await page.getByRole('button', { name: '新建病理申请' }).click();
  await page.getByRole('textbox', { name: '患者姓名' }).fill(patientReference);
  await page.getByRole('combobox', { name: '性别' }).selectOption('MALE');
  await page.getByRole('spinbutton', { name: '年龄（无出生日期时）' }).fill('45');
  await page.getByRole('textbox', { name: '患者标识' }).fill(patientReference);
  await page.getByRole('textbox', { name: '门诊号 / 住院号' }).fill(`PX03-VISIT-${suffix}`);
  await page.getByRole('textbox', { name: '申请号' }).fill(`PX03-APP-${suffix}`);
  await page.getByRole('textbox', { name: '申请科室' }).fill('合成细胞科');
  await page.getByRole('combobox', { name: '申请项目' }).selectOption('SYNTH-CYTOLOGY');
  await page.getByRole('textbox', { name: '标本名称 / 部位' }).fill('合成细胞标本');
  await page.getByRole('button', { name: '保存申请' }).click();
  await expect(page.getByRole('heading', { name: '核对申请并登记' })).toBeVisible();
  await page.locator('.registration-item-line input[type="checkbox"]').check();
  for (const name of [
    '患者一致',
    '申请与标本对应',
    '标本数量一致',
    '类型与部位一致',
    '容器符合要求',
    '固定情况符合要求',
  ]) {
    await page.getByRole('checkbox', { name }).check();
  }
  await page.getByRole('button', { name: '登记', exact: true }).click();

  const completed = page.getByRole('status').filter({ hasText: '登记完成，已创建 1 个独立病例' });
  await expect(completed).toBeVisible();
  const pathologyNo = (
    await page.locator('.registration-complete-actions span').first().innerText()
  ).trim();
  expect(pathologyNo).toBeTruthy();
  const registrarWorkbenchResponse = page.waitForResponse(
    (response) =>
      response.url().endsWith('/api/v2/my-workbench') && response.request().method() === 'GET',
  );
  await page.goto('/v2/workbench');
  await registrarWorkbenchResponse;
  await page.getByRole('button', { name: /我今天登记/ }).click();
  await page.getByRole('searchbox', { name: '关键词' }).fill(pathologyNo!);
  const registeredRow = page.getByRole('button', { name: new RegExp(pathologyNo!) });
  await expect(registeredRow).toContainText('登记完成');

  await page.getByRole('button', { name: '退出' }).click();
  await page.waitForURL(/\/v2\/workbench/);
  await login(page, 'technician');
  const cytologyQueue = page.getByRole('button', { name: /细胞制片/ });
  await expect(cytologyQueue).toBeVisible();
  await cytologyQueue.click();
  await page.getByRole('searchbox', { name: '关键词' }).fill(pathologyNo!);
  const cytologyRow = page.locator('.workbench-dense-row').filter({ hasText: pathologyNo! });
  await expect(page.getByText('待脱水', { exact: true })).toHaveCount(0);
  await expect(cytologyRow).toBeVisible();
  const queueBefore = Number((await cytologyQueue.textContent())?.match(/\d+$/)?.[0] ?? 0);
  expect(queueBefore).toBeGreaterThan(0);
  await cytologyRow.click();
  await expect(page).toHaveURL(/\/v2\/production\/[^?]+\?[^#]*origin=workbench/);
  await expect(page.getByText('细胞制片', { exact: true }).first()).toBeVisible();
  await expect(page.locator('.specimen-panel')).toBeVisible();
  await expect(
    page.getByText('状态：待生成。该标本尚未生成玻片，可使用“按规则生成玻片”。'),
  ).toBeVisible();
  await expect(page.locator('.cytology-production-workspace')).not.toContainText('材块');

  await page.getByRole('button', { name: '按规则生成玻片' }).click();
  await expect(page.getByRole('status')).toContainText('已生成 1 张细胞玻片');
  const slideCheckbox = page.getByRole('checkbox', { name: /选择玻片/ }).first();
  await expect(slideCheckbox).toBeVisible();
  await slideCheckbox.check();
  await page.getByRole('button', { name: '完成制片' }).click();
  await expect(page.getByRole('status')).toContainText('技术记录不是完成前置条件');

  const technicianWorkbenchResponse = page.waitForResponse(
    (response) =>
      response.url().endsWith('/api/v2/my-workbench') && response.request().method() === 'GET',
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
  await page.getByRole('button', { name: /^待接诊 \d+$/ }).click();
  await page.getByRole('searchbox', { name: '关键词' }).fill(pathologyNo!);
  await expect(page.getByRole('button', { name: new RegExp(pathologyNo!) })).toBeVisible();
});
