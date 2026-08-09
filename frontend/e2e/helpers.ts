import { expect, type Page } from '@playwright/test';

export async function login(page: Page, username: string) {
  const password = process.env.PIS_E2E_PASSWORD;
  if (!password) throw new Error('PIS_E2E_PASSWORD is required');
  await page.goto('/v2/workbench');
  await page.getByRole('textbox', { name: '用户名' }).fill(username);
  await page.getByRole('textbox', { name: '密码' }).fill(password);
  await page.getByRole('button', { name: '登录' }).click();
  const expectedDisplayName: Record<string, string> = {
    admin: 'Admin',
    'doctor-a': 'Doctor A',
    'doctor-b': 'Doctor B',
    'doctor-c': 'Doctor C',
    registrar: 'Registrar',
    technician: 'Technician',
  };
  await expect(page.getByLabel('当前登录身份')).toContainText(
    expectedDisplayName[username] ?? username,
  );
}

export async function expectAccessibleButtons(page: Page) {
  const unnamed = await page
    .locator('button:visible')
    .evaluateAll(
      (buttons) =>
        buttons.filter(
          (button) => !(button.getAttribute('aria-label') || button.textContent?.trim()),
        ).length,
    );
  expect(unnamed).toBe(0);
}

export async function expectNoPageOverflow(page: Page) {
  const dimensions = await page.evaluate(() => ({
    clientWidth: document.documentElement.clientWidth,
    scrollWidth: document.documentElement.scrollWidth,
  }));
  expect(dimensions.scrollWidth).toBeLessThanOrEqual(dimensions.clientWidth + 1);
}
