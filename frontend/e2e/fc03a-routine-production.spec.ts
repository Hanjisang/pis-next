import { expect, test, type APIResponse, type Page, type TestInfo } from '@playwright/test';

import { expectNoPageOverflow, login } from './helpers';

test.describe.configure({ timeout: 180_000 });

type CaseFixture = {
  caseId: string;
  caseNo: string;
  concurrencyVersion: number;
  patientReference: string;
  specimenId: string;
};

type MaterialTree = {
  initialRequiredCount: number;
  initialCompletedCount: number;
  initialProductionComplete: boolean;
  specimens: Array<{
    blocks: Array<{
      blockId: string;
      blockCode: string;
      slides: Array<{
        slideId: string;
        slideCode: string;
        required: boolean;
        completed: boolean;
        concurrencyVersion: number;
        printCount: number;
      }>;
    }>;
  }>;
};

async function jsonOk<T>(response: APIResponse): Promise<T> {
  const text = await response.text();
  expect(response.ok(), text).toBe(true);
  return JSON.parse(text) as T;
}

async function switchUser(page: Page, username: string) {
  await page.request.post('/api/v2/auth/logout');
  await page.goto('/v2/workbench');
  await login(page, username);
}

async function createRoutineCase(page: Page, label: string, patientReference: string) {
  const created = await jsonOk<Omit<CaseFixture, 'specimenId'>>(
    await page.request.post('/api/v2/registration/cases', {
      data: {
        sourceSystemCode: 'PIS-E2E',
        externalApplicationId: label,
        applicationItemCode: 'SYNTH-HISTOLOGY',
        patientReference,
        visitReference: `VISIT-${label}`,
        idempotencyKey: `case-${label}`,
      },
    }),
  );
  const specimen = await jsonOk<{ specimenId: string }>(
    await page.request.post('/api/v2/registration/specimens', {
      data: {
        caseId: created.caseId,
        specimenCode: 'SP1',
        specimenName: 'FC03A 合成胃黏膜组织',
        specimenKindCode: 'TISSUE',
        creationSourceCode: 'REGISTRATION',
        sourceKindCode: 'LOCAL',
        sourceReference: `${label}-SP1`,
        collectionSite: '胃体',
        collectionMethodCode: 'SURGICAL',
        quantityValue: 1,
        quantityUnitCode: '件',
        description: 'FC03A 常规制片合成标本',
        receivedAt: new Date().toISOString(),
        labelCode: `LBL-${label}`,
        idempotencyKey: `specimen-${label}`,
      },
    }),
  );
  return { ...created, specimenId: specimen.specimenId };
}

async function completeGrossing(page: Page, fixture: CaseFixture, blockCount: number) {
  const grossing = await jsonOk<{ grossingId: string; concurrencyVersion: number }>(
    await page.request.post(`/api/v2/cases/${fixture.caseId}/grossings`, {
      data: {
        sourceType: 'INITIAL',
        grossDescription: 'FC03A 合成首次取材',
        grossingInstruction: '按常规组织病理规范取材',
        grossingDoctorId: 'DOC-A',
        recorderId: 'grossing',
        idempotencyKey: `grossing-${fixture.caseId}`,
      },
    }),
  );
  await jsonOk(
    await page.request.post(`/api/v2/grossings/${grossing.grossingId}/specimens`, {
      data: {
        specimenId: fixture.specimenId,
        materialDescription: 'FC03A 合成大体所见',
        idempotencyKey: `associate-${grossing.grossingId}`,
      },
    }),
  );
  for (let index = 1; index <= blockCount; index += 1) {
    await jsonOk(
      await page.request.post(`/api/v2/grossings/${grossing.grossingId}/blocks`, {
        data: {
          specimenId: fixture.specimenId,
          blockCode: `A${index}`,
          blockType: 'ROUTINE',
          samplingDescription: `FC03A 第 ${index} 块代表性组织`,
          idempotencyKey: `block-${grossing.grossingId}-${index}`,
        },
      }),
    );
  }
  await jsonOk(
    await page.request.post(`/api/v2/grossings/${grossing.grossingId}/complete`, {
      data: {
        expectedVersion: grossing.concurrencyVersion,
        idempotencyKey: `complete-${grossing.grossingId}`,
      },
    }),
  );
}

async function materials(page: Page, caseId: string) {
  return jsonOk<MaterialTree>(await page.request.get(`/api/v2/cases/${caseId}/materials`));
}

function blocks(tree: MaterialTree) {
  return tree.specimens.flatMap((specimen) => specimen.blocks);
}

async function prepareRoutineFixture(page: Page, testInfo: TestInfo) {
  const suffix = `${testInfo.project.name}-${Date.now()}`;
  await login(page, 'registrar');
  const main = await createRoutineCase(page, `FC03A-R001-${suffix}`, `FC03A-PATIENT-${suffix}`);
  const next = await createRoutineCase(page, `FC03A-R006-${suffix}`, `FC03A-PATIENT-${suffix}`);
  await switchUser(page, 'grossing');
  await completeGrossing(page, main, 10);
  await completeGrossing(page, next, 1);
  await switchUser(page, 'technician');

  const initial = await materials(page, main.caseId);
  const mainBlocks = blocks(initial);
  const a1 = mainBlocks.find((block) => block.blockCode === 'A1')!;
  const a2Slide = mainBlocks.find((block) => block.blockCode === 'A2')!.slides[0]!;
  await jsonOk(
    await page.request.post(`/api/v2/slides/${a2Slide.slideId}/complete`, {
      data: {
        expectedVersion: a2Slide.concurrencyVersion,
        idempotencyKey: `complete-a2-${suffix}`,
      },
    }),
  );
  await jsonOk(
    await page.request.post(`/api/v2/slides/${a1.slides[0]!.slideId}/cancel`, {
      data: {
        expectedVersion: a1.slides[0]!.concurrencyVersion,
        reason: 'FC03A 验证按规则补齐缺失玻片',
        idempotencyKey: `cancel-a1-${suffix}`,
      },
    }),
  );
  return { main, next };
}

async function assertDenseFirstScreen(page: Page, testInfo: TestInfo) {
  await expectNoPageOverflow(page);
  const layout = await page.evaluate(() => {
    const rows = Array.from(document.querySelectorAll('.material-row:not(.table-head)'));
    return {
      viewportHeight: window.innerHeight,
      headerVisible: Boolean(document.querySelector('.case-header')),
      toolbarVisible:
        (document.querySelector('.production-toolbar')?.getBoundingClientRect().top ?? 99_999) <
        window.innerHeight,
      visibleRows: rows.filter((row) => row.getBoundingClientRect().top < window.innerHeight)
        .length,
      maxRowHeight: Math.max(...rows.map((row) => row.getBoundingClientRect().height)),
    };
  });
  expect(layout.headerVisible).toBe(true);
  expect(layout.toolbarVisible).toBe(true);
  expect(layout.visibleRows).toBeGreaterThanOrEqual(testInfo.project.name.includes('1920') ? 8 : 5);
  expect(layout.maxRowHeight).toBeLessThanOrEqual(56);
  await testInfo.attach(`fc03a-routine-${testInfo.project.name}`, {
    body: await page.screenshot(),
    contentType: 'image/png',
  });
}

test('FC03A 常规 Block→Slide、可选技术记录、返工及来源导航闭环', async ({ page }, testInfo) => {
  const { main, next } = await prepareRoutineFixture(page, testInfo);

  await page.goto('/v2/workbench');
  await page.getByRole('button', { name: /^常规制片 \d+$/ }).click();
  const keyword = page.getByRole('searchbox', { name: '关键词' });
  await keyword.fill(main.patientReference);
  await page.locator('button.workbench-dense-row').filter({ hasText: main.caseNo }).click();

  await expect(page).toHaveURL(new RegExp(`/v2/production/${main.caseId}\\?origin=workbench`));
  await expect(page.getByRole('region', { name: '常规制片工作区' })).toBeVisible();
  await expect(page.getByRole('region', { name: '病例中心', exact: true })).toHaveCount(0);
  await expect(page.getByRole('table', { name: '材块与玻片生产表' })).toBeVisible();
  await expect(page.getByText('材块 10 | 玻片 1/10 完成')).toBeVisible();
  await expect(page.locator('.material-row:not(.table-head)')).toHaveCount(10);
  await expect(page.locator('main')).not.toContainText(main.caseId);
  await assertDenseFirstScreen(page, testInfo);

  await page.getByRole('button', { name: '返回工作台', exact: true }).click();
  await expect(page).toHaveURL(/\/v2\/workbench\?queue=ROUTINE_PRODUCTION/);
  await expect(page.getByRole('searchbox', { name: '关键词' })).toHaveValue(main.patientReference);
  await page.locator('button.workbench-dense-row').filter({ hasText: main.caseNo }).click();

  const beforeGenerate = await materials(page, main.caseId);
  const a1Before = blocks(beforeGenerate).find((block) => block.blockCode === 'A1')!;
  expect(a1Before.slides).toHaveLength(0);
  await page.locator(`#material-${a1Before.blockId} input[type="checkbox"]`).check();
  await page.getByRole('button', { name: '按规则生成玻片' }).click();
  await expect(page.getByRole('status')).toContainText('已按规则生成 1 张玻片');

  const generated = await materials(page, main.caseId);
  const activeRequired = blocks(generated)
    .flatMap((block) => block.slides)
    .filter((slide) => slide.required && !slide.completed);
  expect(activeRequired).toHaveLength(9);
  const generatedA1 = blocks(generated).find((block) => block.blockCode === 'A1')!.slides[0]!;

  for (const slide of activeRequired) {
    await page.locator(`#material-${slide.slideId} .slide-identity input[type="checkbox"]`).check();
  }
  await page.getByRole('button', { name: '批量打印标签' }).click();
  await expect(page.getByRole('status')).toContainText('已按材块与玻片顺序发送 9 张标签');
  await page.getByRole('button', { name: '批量打印标签' }).click();
  await expect(page.getByRole('status')).toContainText('已按材块与玻片顺序发送 9 张标签');
  const afterReprint = await materials(page, main.caseId);
  expect(
    blocks(afterReprint)
      .flatMap((block) => block.slides)
      .filter((slide) => activeRequired.some((target) => target.slideId === slide.slideId))
      .every(
        (slide) =>
          slide.printCount ===
          activeRequired.find((target) => target.slideId === slide.slideId)!.printCount + 2,
      ),
  ).toBe(true);

  const scanner = page.getByLabel('扫码定位材块或玻片');
  await scanner.fill(generatedA1.slideCode);
  await scanner.press('Enter');
  await expect(page.getByRole('status')).toContainText(`已定位玻片 ${generatedA1.slideCode}`);
  await expect(page.locator(`#material-${generatedA1.slideId}`)).toHaveClass(/located/);

  await page.getByLabel('新玻片编号').fill('A1-01');
  await page.getByLabel('修正原因').fill('规范玻片显示编号');
  await page.getByRole('button', { name: '更正编号' }).click();
  await expect(page.getByRole('status')).toContainText('玻片编号已更正为 A1-01，玻片身份保持不变');
  const afterCorrection = await materials(page, main.caseId);
  expect(
    blocks(afterCorrection).find((block) => block.blockCode === 'A1')!.slides[0]!.slideId,
  ).toBe(generatedA1.slideId);

  await page.getByRole('button', { name: '批量完成制片' }).click();
  await expect(page.getByRole('status')).toContainText('技术记录不作为完成前置条件');
  await expect(page.getByText('当前病例的常规初始玻片要求已满足。')).toBeVisible();

  await page.getByText('技术记录（可选，不阻止玻片完成）').click();
  await page
    .locator(`#material-${generatedA1.slideId} .material-identity input[type="checkbox"]`)
    .check();
  await page.getByRole('button', { name: '脱水', exact: true }).click();
  await page.getByLabel('技术记录说明').fill('制片完成后补录的可选技术事实');
  await page.getByRole('button', { name: '批量记录完成' }).click();
  await expect(page.getByRole('status')).toContainText('已记录 1 项脱水事实');

  await page.getByText('技术记录（可选，不阻止玻片完成）').click();
  await page.getByRole('button', { name: '染色', exact: true }).click();
  await page.getByText('异常与物理返工').click();
  await page.getByLabel('异常类型').selectOption({ label: '玻片破损' });
  await page.getByLabel('异常说明').fill('FC03A 合成破损异常');
  await page.getByRole('button', { name: '记录异常' }).click();
  await expect(page.getByRole('status')).toContainText('制片异常已记录');
  await page.getByText('异常与物理返工').click();
  await page.getByLabel('返工方式').selectOption('RECUT');
  await page.getByLabel('返工原因').fill('物理玻片破损，需要重新切片');
  await page.getByRole('button', { name: '执行返工' }).click();
  await expect(page.getByRole('status')).toContainText('重新切片已生成新物理玻片，原玻片保留');
  const afterRework = await materials(page, main.caseId);
  const a1Slides = blocks(afterRework).find((block) => block.blockCode === 'A1')!.slides;
  expect(a1Slides.some((slide) => slide.slideId === generatedA1.slideId)).toBe(true);
  expect(a1Slides.some((slide) => slide.slideId !== generatedA1.slideId)).toBe(true);
  const replacement = a1Slides.find((slide) => slide.slideId !== generatedA1.slideId)!;
  await page
    .locator(`#material-${replacement.slideId} .slide-identity input[type="checkbox"]`)
    .check();
  await page.getByRole('button', { name: '批量完成制片' }).click();
  await expect(page.getByRole('status')).toContainText('已完成 1 张玻片');
  const attention = await jsonOk<{
    queues: { exceptions: { items: Array<{ caseId: string }> } };
  }>(await page.request.get('/api/v2/production-workbench'));
  expect(attention.queues.exceptions.items.some((item) => item.caseId === main.caseId)).toBe(false);

  await page.getByRole('button', { name: '完成并下一项' }).click();
  await expect(page).toHaveURL(new RegExp(`/v2/production/${next.caseId}\\?origin=workbench`));

  await page.goto('/v2/workbench?queue=ROUTINE_PRODUCTION');
  await expect(page.getByRole('searchbox', { name: '关键词' })).toHaveValue(main.patientReference);
  await expect(
    page.locator('button.workbench-dense-row').filter({ hasText: main.caseNo }),
  ).toHaveCount(0);
  await expect(
    page.locator('button.workbench-dense-row').filter({ hasText: next.caseNo }),
  ).toHaveCount(1);

  await page.goto(`/v2/cases/${main.caseId}`);
  await page.getByRole('button', { name: '材料与制片', exact: true }).click();
  await expect(page.getByText('A1', { exact: true }).first()).toBeVisible();
  await expect(page.getByText('A1-01', { exact: true }).first()).toBeVisible();
});
