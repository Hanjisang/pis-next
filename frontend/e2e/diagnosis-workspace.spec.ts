import { expect, test } from '@playwright/test';

import { expectAccessibleButtons, expectNoPageOverflow, login } from './helpers';

test('医生从查询进入诊断主工作区，关键上下文和固定操作可见', async ({ page }) => {
  const pathologyNo = process.env.PIS_E2E_PATHOLOGY_NO;
  if (!pathologyNo) throw new Error('PIS_E2E_PATHOLOGY_NO is required');
  await login(page, 'doctor-c');

  await page.keyboard.press('Control+K');
  const search = page.getByRole('dialog', { name: '全局查询' });
  await search.getByRole('textbox').fill(pathologyNo);
  await search.getByRole('button', { name: '查询', exact: true }).click();
  await search
    .getByRole('button', { name: new RegExp(pathologyNo) })
    .first()
    .click();
  await expect(page.getByLabel('病例中心')).toBeVisible();
  await page.getByRole('button', { name: '进入诊断' }).click();

  await expect(page).toHaveURL(/\/v2\/diagnosis\//);
  await expect(page.getByLabel('病例固定上下文')).toContainText(pathologyNo);
  await expect(page.getByRole('region', { name: '诊断编辑器' })).toBeVisible();
  await expect(page.getByLabel('责任、医嘱与报告')).toBeVisible();
  await expect(page.getByLabel('诊断主要操作')).toBeVisible();
  await expect(page.getByRole('button', { name: '报告预览' })).toBeVisible();
  await expectAccessibleButtons(page);
  await expectNoPageOverflow(page);
});
