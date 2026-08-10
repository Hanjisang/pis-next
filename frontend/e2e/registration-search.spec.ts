import { expect, test } from '@playwright/test';

import { expectAccessibleButtons, expectNoPageOverflow, login } from './helpers';

test('登记员在单页登记两个标本，并从全局查询进入病例上下文', async ({ page }, testInfo) => {
  await login(page, 'registrar');
  await expect(page.getByRole('button', { name: '登记', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '诊断', exact: true })).toHaveCount(0);

  await page.getByRole('button', { name: '登记', exact: true }).click();
  await page.getByRole('button', { name: '新增手工病例' }).click();
  const suffix = `${Date.now()}-${testInfo.project.name}`;
  await page.getByRole('textbox', { name: '患者编号' }).fill(`SYNTH-UX01-${suffix}`);
  await page.getByRole('textbox', { name: '就诊号' }).fill(`SYNTH-VISIT-${suffix}`);
  await page.getByRole('combobox', { name: '业务类型' }).selectOption({ label: '常规组织病理' });
  await page.getByRole('textbox', { name: '取材部位' }).fill('合成胃窦组织');
  await page.getByRole('button', { name: '+ 新增标本' }).click();
  await page.getByRole('textbox', { name: '取材部位' }).nth(1).fill('合成胃体组织');
  await expect(page.getByRole('textbox', { name: '取材部位' }).nth(1)).toHaveValue('合成胃体组织');

  await page.getByRole('button', { name: '确认登记' }).click();
  const completed = page.getByRole('status').filter({ hasText: '登记完成，病理号已生成' });
  await expect(completed).toBeVisible();
  const pathologyNo = (await completed.locator('strong').textContent())?.split('：').at(-1)?.trim();
  expect(pathologyNo).toBeTruthy();
  await expect(completed).toContainText('已登记 2 个标本');

  await page.keyboard.press('Control+K');
  const search = page.getByRole('dialog', { name: '全局查询' });
  await expect(search).toBeVisible();
  await expect(search.getByRole('textbox')).toBeFocused();
  await search.getByRole('textbox').fill(pathologyNo!);
  await search.getByRole('button', { name: '查询', exact: true }).click();
  await search.getByRole('button', { name: new RegExp(pathologyNo!) }).click();
  await expect(page).toHaveURL(/\/v2\/cases\//);
  await expect(page.getByLabel('病例中心')).toContainText(pathologyNo!);
  await expect(page.getByRole('heading', { name: '标本、蜡块与玻片' })).toBeVisible();
  await expectAccessibleButtons(page);
  await expectNoPageOverflow(page);
});
