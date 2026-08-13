import { expect, test, type APIResponse, type Page, type TestInfo } from '@playwright/test';

import { login } from './helpers';

test.describe.configure({ timeout: 90_000 });

type CaseFixture = {
  caseId: string;
  caseNo: string;
};

type MaterialTree = {
  specimens: Array<{
    blocks: unknown[];
    directSlides: Array<{
      slideId: string;
      slideCode: string;
      completed: boolean;
    }>;
  }>;
  initialRequiredCount: number;
  initialCompletedCount: number;
};

async function jsonOk<T>(response: APIResponse): Promise<T> {
  const body = await response.text();
  expect(response.ok(), body).toBe(true);
  return JSON.parse(body) as T;
}

async function ensureUser(page: Page, username: string, displayName: string) {
  await page.goto('/v2/workbench');
  const identity = page.getByLabel('当前登录身份');
  if (await identity.isVisible()) {
    if ((await identity.innerText()).includes(displayName)) return;
    await page.getByRole('button', { name: '退出', exact: true }).click();
  }
  await login(page, username);
}

async function createFrozenFixture(page: Page, testInfo: TestInfo): Promise<CaseFixture> {
  const suffix = `${testInfo.project.name}-${Date.now()}`.replace(/[^a-zA-Z0-9-]/g, '-');
  await ensureUser(page, 'registrar', 'Registrar');
  const created = await jsonOk<CaseFixture>(
    await page.request.post('/api/v2/registration/cases', {
      data: {
        sourceSystemCode: 'PIS-FC03C-PLAYWRIGHT',
        externalApplicationId: `FC03C-${suffix}`,
        applicationItemCode: 'SYNTH-FROZEN',
        patientReference: `FC03C-PATIENT-${suffix}`,
        visitReference: `FC03C-VISIT-${suffix}`,
        idempotencyKey: `fc03c-case-${suffix}`,
      },
    }),
  );
  await jsonOk(
    await page.request.post('/api/v2/registration/specimens', {
      data: {
        caseId: created.caseId,
        specimenCode: 'SP1',
        specimenName: '冰冻合成标本',
        specimenKindCode: 'FROZEN',
        creationSourceCode: 'REGISTRATION',
        sourceKindCode: 'LOCAL',
        sourceReference: `FC03C-SOURCE-${suffix}`,
        collectionSite: '甲状腺结节',
        collectionMethodCode: 'SURGICAL',
        quantityValue: 1,
        quantityUnitCode: 'CONTAINER',
        description: 'FC03C 冰冻合成标本',
        receivedAt: new Date().toISOString(),
        labelCode: `FC03C-LABEL-${suffix}`,
        idempotencyKey: `fc03c-specimen-${suffix}`,
      },
    }),
  );
  await ensureUser(page, 'technician', 'Technician');
  await page.goto(`/v2/frozen/${created.caseId}`);
  await page.getByRole('button', { name: '开始第 1 轮', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('第 1 轮已开始');
  return created;
}

test('FC03C：零玻片直接冰冻制片并从原队列退出', async ({ page }, testInfo) => {
  const fixture = await createFrozenFixture(page, testInfo);

  await page.goto('/v2/workbench');
  const queue = page.getByRole('button', { name: /^冰冻制片 \d+$/ });
  await expect(queue).toBeVisible();
  await queue.click();

  const row = page.locator('button.workbench-dense-row').filter({ hasText: fixture.caseNo });
  await expect(row).toBeVisible();
  await row.click();
  await expect(page).toHaveURL(new RegExp(`/v2/frozen/${fixture.caseId}\\?.*origin=workbench`));
  await expect(page.getByRole('region', { name: '冰冻工作区' })).toBeVisible();
  await expect(page.getByText('本轮尚未建立玻片', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '进入冰冻制片', exact: true })).toBeVisible();
  const roundId = new URL(page.url()).searchParams.get('roundId');
  expect(roundId).toBeTruthy();

  await page.getByRole('button', { name: '进入冰冻制片', exact: true }).click();
  await expect(page).toHaveURL(new RegExp(`/v2/production/${fixture.caseId}\\?`));
  await expect(page.getByText('本轮标本 → 玻片；不要求蜡块。')).toBeVisible();
  await expect(page.getByText('本轮尚未建立玻片', { exact: true })).toBeVisible();

  await page.getByRole('button', { name: '按规则生成冰冻玻片', exact: true }).click();
  await expect(page.getByText('冰冻轮次所需玻片已按规则生成。', { exact: true })).toBeVisible();
  const materials = await jsonOk<MaterialTree>(
    await page.request.get(
      `/api/v2/cases/${fixture.caseId}/materials?frozenRoundId=${encodeURIComponent(roundId!)}`,
    ),
  );
  expect(materials.specimens).toHaveLength(1);
  expect(materials.specimens[0]!.blocks).toHaveLength(0);
  expect(materials.specimens[0]!.directSlides).toHaveLength(1);
  expect(materials.initialRequiredCount).toBe(1);
  expect(materials.initialCompletedCount).toBe(0);
  const slide = materials.specimens[0]!.directSlides[0]!;
  await expect(page.getByText(slide.slideCode, { exact: true })).toBeVisible();

  const slideRow = page.locator('.production-sheet-row').filter({ hasText: slide.slideCode });
  await expect(slideRow).toBeVisible();
  await slideRow.getByRole('button', { name: '扫码完成', exact: true }).click();
  await expect(page.getByText(`玻片 ${slide.slideCode} 已完成。`, { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '完成并返回工作台', exact: true })).toBeVisible();
  await page.getByRole('button', { name: '完成并返回工作台', exact: true }).click();

  await expect(page).toHaveURL(/\/v2\/workbench\?queue=FROZEN_PRODUCTION/);
  await page.getByRole('button', { name: /^冰冻制片 \d+$/ }).click();
  await expect(
    page.locator('button.workbench-dense-row').filter({ hasText: fixture.caseNo }),
  ).toHaveCount(0);
});
