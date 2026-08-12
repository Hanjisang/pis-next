import { expect, test, type APIResponse, type Page, type TestInfo } from '@playwright/test';

import { expectNoPageOverflow, login } from './helpers';

test.describe.configure({ timeout: 120_000 });

type CaseFixture = {
  caseId: string;
  caseNo: string;
  concurrencyVersion: number;
  patientReference: string;
  specimens: Array<{ specimenId: string; specimenCode: string }>;
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

async function createCaseFixture(
  page: Page,
  label: string,
  specimenCodes: string[],
  applicationItemCode = 'SYNTH-HISTOLOGY',
  patientReference = `PAT-${label}`,
): Promise<CaseFixture> {
  const created = await jsonOk<Omit<CaseFixture, 'specimens'>>(
    await page.request.post('/api/v2/registration/cases', {
      data: {
        sourceSystemCode: 'PIS-E2E',
        externalApplicationId: label,
        applicationItemCode,
        patientReference,
        visitReference: `VISIT-${label}`,
        idempotencyKey: `case-${label}`,
      },
    }),
  );
  const specimens: CaseFixture['specimens'] = [];
  for (const [index, specimenCode] of specimenCodes.entries()) {
    const specimen = await jsonOk<{ specimenId: string; specimenCode: string }>(
      await page.request.post('/api/v2/registration/specimens', {
        data: {
          caseId: created.caseId,
          specimenCode,
          specimenName: `${specimenCode}号合成组织`,
          specimenKindCode: applicationItemCode.includes('CYTOLOGY') ? 'FLUID' : 'TISSUE',
          creationSourceCode: 'REGISTRATION',
          sourceKindCode: 'LOCAL',
          sourceReference: `${label}-${specimenCode}`,
          collectionSite: index === 0 ? '胃体' : '胃窦',
          collectionMethodCode: 'SURGICAL',
          quantityValue: 1,
          quantityUnitCode: '件',
          description: `${specimenCode}号标本大体描述`,
          receivedAt: new Date().toISOString(),
          labelCode: `LBL-${label}-${specimenCode}`,
          idempotencyKey: `specimen-${label}-${specimenCode}`,
        },
      }),
    );
    specimens.push(specimen);
  }
  return { ...created, patientReference, specimens };
}

async function cancelCase(page: Page, fixture: CaseFixture) {
  await jsonOk(
    await page.request.post(`/api/v2/registration/cases/${fixture.caseId}/cancel`, {
      data: { reason: 'FC02B 合成取消病例', expectedVersion: fixture.concurrencyVersion },
    }),
  );
}

async function startGrossing(page: Page, specimenDescription = '灰白组织，切面实性') {
  await page.getByRole('textbox', { name: '当前标本大体所见' }).fill(specimenDescription);
  await page.getByRole('textbox', { name: '本次取材总结' }).fill('合成取材：逐一核对全部标本');
  await page.getByRole('button', { name: '开始取材' }).click();
  await expect(page.getByRole('status')).toContainText('已开始取材');
  await expect(page.getByRole('button', { name: '+ 蜡块' })).toBeVisible();
}

async function createBlock(page: Page, code: string, description = '代表性组织') {
  await page.getByRole('textbox', { name: '新蜡块编号' }).fill(code);
  await page.getByRole('textbox', { name: '新材块取材说明' }).fill(description);
  await page.getByRole('button', { name: '+ 蜡块' }).click();
  await expect(page.locator('tbody tr').filter({ hasText: code })).toHaveCount(1);
}

async function screenshotWorkspace(page: Page, testInfo: TestInfo) {
  await expectNoPageOverflow(page);
  const positions = await page.evaluate(() => ({
    viewportHeight: window.innerHeight,
    specimenTop: document.querySelector('.specimen-sidebar')?.getBoundingClientRect().top ?? 99_999,
    grossTop:
      document.querySelector('textarea[placeholder*="分别记录"]')?.getBoundingClientRect().top ??
      99_999,
    blockTop:
      Array.from(document.querySelectorAll('.section-kicker'))
        .find((element) => element.textContent?.trim() === '蜡块')
        ?.getBoundingClientRect().top ?? 99_999,
    actionTop:
      document.querySelector('.sticky-form-actions')?.getBoundingClientRect().top ?? 99_999,
  }));
  expect(positions.specimenTop).toBeLessThan(positions.viewportHeight);
  expect(positions.grossTop).toBeLessThan(positions.viewportHeight);
  expect(positions.blockTop).toBeLessThan(positions.viewportHeight);
  expect(positions.actionTop).toBeLessThan(positions.viewportHeight);
  await testInfo.attach(`fc02b-grossing-${testInfo.project.name}`, {
    body: await page.screenshot(),
    contentType: 'image/png',
  });
}

async function createInitialGrossingThroughApi(page: Page, fixture: CaseFixture) {
  const grossing = await jsonOk<{ grossingId: string; concurrencyVersion: number }>(
    await page.request.post(`/api/v2/cases/${fixture.caseId}/grossings`, {
      data: {
        sourceType: 'INITIAL',
        grossDescription: '合成首次取材',
        grossingInstruction: '按规范取材',
        grossingDoctorId: 'DOC-A',
        recorderId: 'grossing',
        idempotencyKey: `grossing-${fixture.caseId}`,
      },
    }),
  );
  for (const specimen of fixture.specimens) {
    await jsonOk(
      await page.request.post(`/api/v2/grossings/${grossing.grossingId}/specimens`, {
        data: {
          specimenId: specimen.specimenId,
          materialDescription: `${specimen.specimenCode}号标本合成大体所见`,
          idempotencyKey: `associate-${grossing.grossingId}-${specimen.specimenId}`,
        },
      }),
    );
  }
  const block = await jsonOk<{ blockId: string }>(
    await page.request.post(`/api/v2/grossings/${grossing.grossingId}/blocks`, {
      data: {
        specimenId: fixture.specimens[0]!.specimenId,
        blockCode: 'A1',
        blockType: 'ROUTINE',
        samplingDescription: '合成首次取材材块',
        idempotencyKey: `block-${grossing.grossingId}`,
      },
    }),
  );
  await jsonOk(
    await page.request.post(`/api/v2/grossings/${grossing.grossingId}/complete`, {
      data: {
        expectedVersion: grossing.concurrencyVersion,
        idempotencyKey: `complete-${grossing.grossingId}`,
      },
    }),
  );
  return { ...grossing, blockId: block.blockId };
}

test('FC02B 常规多标本、增加拆分、图像、材块及完成闭环', async ({ page }, testInfo) => {
  await login(page, 'registrar');
  const suffix = `${testInfo.project.name}-${Date.now()}`;
  const routine = await createCaseFixture(page, `FC02B-G001-${suffix}`, ['A', 'B']);
  const cytology = await createCaseFixture(page, `FC02B-G002-${suffix}`, ['A'], 'SYNTH-CYTOLOGY');
  const cancelled = await createCaseFixture(page, `FC02B-G005-${suffix}`, ['A']);
  await cancelCase(page, cancelled);
  await switchUser(page, 'grossing');

  await page.getByRole('button', { name: /^待取材 \d+$/ }).click();
  const keyword = page.getByRole('searchbox', { name: '关键词' });
  await keyword.fill(cytology.caseNo);
  await expect(page.locator('button.workbench-dense-row')).toHaveCount(0);
  await keyword.fill(cancelled.caseNo);
  await expect(page.locator('button.workbench-dense-row')).toHaveCount(0);

  await keyword.fill(routine.caseNo);
  await page.locator('button.workbench-dense-row').filter({ hasText: routine.caseNo }).click();
  await expect(page).toHaveURL(new RegExp(`/v2/grossing/${routine.caseId}`));
  await expect(page.getByRole('textbox', { name: '打开病例' })).toHaveCount(0);
  await expect(page.locator('main')).not.toContainText(routine.caseId);
  await startGrossing(page, 'A号标本灰白，切面实性');

  await page.getByRole('button', { name: /^B ·/ }).click();
  await page.getByRole('textbox', { name: '当前标本大体所见' }).fill('B号标本灰红，质软');
  await page.getByRole('button', { name: '保存当前标本所见' }).click();
  await expect(page.getByRole('status')).toContainText('B 的大体所见已保存');

  await page.getByRole('button', { name: '新增标本' }).click();
  await page.getByRole('textbox', { name: '新标本编号' }).fill('C');
  await page.getByRole('textbox', { name: '新标本名称' }).fill('补充发现组织');
  await page.getByRole('textbox', { name: '新标本部位' }).fill('胃角');
  await page.getByRole('textbox', { name: '新标本描述' }).fill('取材中补充发现');
  await page.getByRole('textbox', { name: '新增标本备注' }).fill('取材时发现独立组织');
  await page.locator('form.compact-editor').getByRole('button', { name: '保存' }).click();
  await expect(page.getByRole('button', { name: /^C ·/ })).toBeVisible();

  await page.getByRole('button', { name: /^B ·/ }).click();
  await page.getByText('修正标本信息', { exact: true }).click();
  await page.getByRole('button', { name: '拆分' }).click();
  await page.getByRole('textbox', { name: '拆分标本 1 编号' }).fill('D');
  await page.getByRole('textbox', { name: '拆分标本 1 名称' }).fill('胃窦拆分组织');
  await page.getByRole('textbox', { name: '拆分标本 1 部位' }).fill('胃窦前壁');
  await page.getByRole('textbox', { name: '拆分标本 2 编号' }).fill('E');
  await page.getByRole('textbox', { name: '拆分标本 2 名称' }).fill('胃窦另一组织');
  await page.getByRole('textbox', { name: '拆分标本 2 部位' }).fill('胃窦后壁');
  await page.getByRole('textbox', { name: '拆分原因' }).fill('两个部位需独立追溯');
  await page.getByRole('button', { name: '确认拆分' }).click();
  await expect(page.getByText('由标本 B 拆分')).toHaveCount(2);

  await page.getByRole('button', { name: /^A ·/ }).click();
  await createBlock(page, 'A1');
  await page.getByRole('button', { name: '+3', exact: true }).click();
  await expect(page.locator('tbody tr')).toHaveCount(4);
  await expect(page.locator('tbody')).toContainText('A2');
  await expect(page.locator('tbody')).toContainText('A3');

  await page.getByRole('textbox', { name: '新蜡块编号' }).fill('A1');
  await page.getByRole('button', { name: '+ 蜡块' }).click();
  await expect(page.getByRole('alert')).toContainText('材块编号 A1 已存在');

  for (let index = 0; index < 4; index += 1) {
    await page.getByRole('button', { name: '核对', exact: true }).first().click();
    await expect(page.locator('tbody')).toContainText('已核对');
  }
  let a1Row = page.locator('tbody tr').filter({ hasText: 'A1' }).first();
  await a1Row.getByRole('button', { name: '打印', exact: true }).click();
  a1Row = page.locator('tbody tr').filter({ hasText: 'A1' }).first();
  await a1Row.getByRole('button', { name: /补打\(1\)/ }).click();
  await expect(page.locator('tbody tr').filter({ hasText: 'A1' }).first()).toContainText('补打(2)');

  await page.getByRole('button', { name: '拍摄台采集' }).click();
  await expect(page.getByAltText(/大体图像/)).toBeVisible();
  await page.getByPlaceholder('为当前图像添加标注说明').fill('胃窦切缘标注');
  await page.getByRole('button', { name: '保存标注' }).click();
  await expect(page.getByText('标注：胃窦切缘标注')).toBeVisible();
  await page.getByRole('spinbutton', { name: '长度测量值' }).fill('12.5');
  await page.getByRole('combobox', { name: '长度测量单位' }).selectOption('MM');
  await page.getByRole('button', { name: '保存测量' }).click();
  await expect(page.getByText('长度：12.5 mm')).toBeVisible();
  await page.reload();
  await expect(page.getByText('标注：胃窦切缘标注')).toBeVisible();
  await expect(page.getByText('长度：12.5 mm')).toBeVisible();

  await screenshotWorkspace(page, testInfo);
  await page.getByRole('button', { name: '取材完成并返回工作台' }).click();
  await expect(page).toHaveURL(/\/v2\/workbench/);
  await expect(
    page.locator('button.workbench-dense-row').filter({ hasText: routine.caseNo }),
  ).toHaveCount(0);
  await page.getByRole('button', { name: /^我今天取材 \d+$/ }).click();
  await expect(
    page.locator('button.workbench-dense-row').filter({ hasText: routine.caseNo }),
  ).toHaveCount(1);

  await switchUser(page, 'technician');
  await page.getByRole('button', { name: /^常规制片 \d+$/ }).click();
  await page.getByRole('searchbox', { name: '关键词' }).fill(routine.caseNo);
  await expect(
    page.locator('button.workbench-dense-row').filter({ hasText: routine.caseNo }),
  ).toHaveCount(1);
});

test('FC02B 完成并下一例沿用原队列筛选', async ({ page }, testInfo) => {
  await login(page, 'registrar');
  const suffix = `${testInfo.project.name}-${Date.now()}`;
  const sharedPatient = `PAT-FC02B-NEXT-${suffix}`;
  const first = await createCaseFixture(
    page,
    `FC02B-NEXT-A-${suffix}`,
    ['A'],
    'SYNTH-HISTOLOGY',
    sharedPatient,
  );
  const second = await createCaseFixture(
    page,
    `FC02B-NEXT-B-${suffix}`,
    ['A'],
    'SYNTH-HISTOLOGY',
    sharedPatient,
  );
  await switchUser(page, 'grossing');
  await page.getByRole('button', { name: /^待取材 \d+$/ }).click();
  await page.getByRole('searchbox', { name: '关键词' }).fill(sharedPatient);
  const filteredRows = page.locator('button.workbench-dense-row');
  await expect(filteredRows).toHaveCount(2);
  await filteredRows.first().click();
  const openedId = new URL(page.url()).pathname.split('/').pop();
  const expectedNext = openedId === first.caseId ? second : first;

  await startGrossing(page);
  await createBlock(page, 'A1');
  await page.getByRole('button', { name: '取材完成并下一例' }).click();
  await expect(page).toHaveURL(new RegExp(`/v2/grossing/${expectedNext.caseId}`));
  await expect(page).toHaveURL(/queue=GROSSING_PENDING/);
  await expect(page.getByRole('heading', { name: expectedNext.caseNo })).toBeVisible();
});

test('FC02B 已完成取材在原记录上修正且不重新入队', async ({ page }, testInfo) => {
  await login(page, 'registrar');
  const fixture = await createCaseFixture(
    page,
    `FC02B-CORRECT-${testInfo.project.name}-${Date.now()}`,
    ['A'],
  );
  await switchUser(page, 'grossing');
  const completed = await createInitialGrossingThroughApi(page, fixture);

  await page.goto(
    `/v2/grossing/${fixture.caseId}?origin=case&returnTo=%2Fv2%2Fcases%2F${fixture.caseId}`,
  );
  await expect(page.getByLabel('取材操作').getByText('取材已完成', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '修正取材记录' }).click();
  await page.getByRole('textbox', { name: '本次取材总结' }).fill('授权修正后的大体总结');
  await page.getByRole('textbox', { name: '修正原因' }).fill('纠正录入文字');
  await page.getByRole('button', { name: '确认修正' }).click();
  await expect(page.getByRole('status')).toContainText('原记录上完成授权修正');

  const workspace = await jsonOk<{ grossing: { grossingId: string; grossDescription: string } }>(
    await page.request.get(`/api/v2/cases/${fixture.caseId}/grossing-workspace?sourceType=INITIAL`),
  );
  expect(workspace.grossing.grossingId).toBe(completed.grossingId);
  expect(workspace.grossing.grossDescription).toBe('授权修正后的大体总结');
  await page.goto('/v2/workbench?queue=GROSSING_PENDING');
  await page.getByRole('button', { name: /^待取材 \d+$/ }).click();
  await page.getByRole('searchbox', { name: '关键词' }).fill(fixture.caseNo);
  await expect(page.locator('button.workbench-dense-row')).toHaveCount(0);
});

test('FC02B 技术医嘱补取创建新取材和材块且不创建技术切片', async ({ page }, testInfo) => {
  await login(page, 'registrar');
  const fixture = await createCaseFixture(
    page,
    `FC02B-SUP-${testInfo.project.name}-${Date.now()}`,
    ['A'],
  );
  await switchUser(page, 'grossing');
  const initial = await createInitialGrossingThroughApi(page, fixture);
  const initialMaterials = await jsonOk<{
    specimens: Array<{
      blocks: Array<{ slides: Array<{ slideId: string; concurrencyVersion: number }> }>;
    }>;
  }>(await page.request.get(`/api/v2/cases/${fixture.caseId}/materials`));
  const initialSlides = initialMaterials.specimens.flatMap((specimen) =>
    specimen.blocks.flatMap((block) => block.slides),
  );
  expect(initialSlides).toHaveLength(1);
  await jsonOk(
    await page.request.post(`/api/v2/slides/${initialSlides[0]!.slideId}/complete`, {
      data: {
        expectedVersion: initialSlides[0]!.concurrencyVersion,
        idempotencyKey: `complete-slide-${initialSlides[0]!.slideId}`,
      },
    }),
  );

  await switchUser(page, 'doctor-a');
  const diagnosis = await jsonOk<{ diagnosisId: string }>(
    await page.request.post('/api/v2/diagnoses/claim', {
      data: { caseId: fixture.caseId, idempotencyKey: `claim-${fixture.caseId}` },
    }),
  );
  const projects = await jsonOk<Array<{ projectId: string; projectCode: string }>>(
    await page.request.get(`/api/v2/technical-projects?caseId=${fixture.caseId}`),
  );
  const project = projects.find((item) => item.projectCode === 'SUPPLEMENTARY-GROSSING');
  expect(project).toBeDefined();
  const order = await jsonOk<{
    orderId: string;
    items: Array<{ itemId: string; outputs: Array<{ outputKind: string; outputId: string }> }>;
  }>(
    await page.request.post('/api/v2/technical-orders', {
      data: {
        diagnosisId: diagnosis.diagnosisId,
        requiredBeforeSignOut: true,
        items: [
          {
            projectId: project!.projectId,
            quantity: 1,
            parameters: '{}',
            note: 'FC02B 合成补取',
            targets: [{ targetType: 'SPECIMEN', targetId: fixture.specimens[0]!.specimenId }],
          },
        ],
        idempotencyKey: `order-${fixture.caseId}`,
      },
    }),
  );
  const itemId = order.items[0]!.itemId;

  await switchUser(page, 'technician');
  await page.getByRole('button', { name: /^技术医嘱 \d+$/ }).click();
  await page.getByRole('searchbox', { name: '关键词' }).fill(fixture.caseNo);
  await page.locator('button.workbench-dense-row').filter({ hasText: fixture.caseNo }).click();
  await expect(page.getByRole('button', { name: '补充取材' })).toBeVisible();
  await page.getByRole('button', { name: '补充取材' }).click();
  await expect(page).toHaveURL(new RegExp(`/v2/grossing/${fixture.caseId}`));
  await expect(page).toHaveURL(/sourceType=TECHNICAL_ORDER/);
  await expect(page).toHaveURL(new RegExp(`sourceReferenceId=${itemId}`));
  await createBlock(page, 'B5', '补取代表性组织');
  await page.getByRole('button', { name: '取材完成并返回工作台' }).click();
  await expect(page).toHaveURL(/\/v2\/workbench/);

  const completedOrder = await jsonOk<{
    items: Array<{ outputs: Array<{ outputKind: string; outputId: string }> }>;
  }>(await page.request.get(`/api/v2/technical-orders/${order.orderId}`));
  const outputKinds = completedOrder.items[0]!.outputs.map((output) => output.outputKind);
  expect(outputKinds).toContain('GROSSING');
  expect(outputKinds).toContain('BLOCK');
  expect(outputKinds).not.toContain('SLIDE');
  const supplementaryGrossing = completedOrder.items[0]!.outputs.find(
    (output) => output.outputKind === 'GROSSING',
  );
  expect(supplementaryGrossing?.outputId).not.toBe(initial.grossingId);

  const finalMaterials = await jsonOk<{
    specimens: Array<{ blocks: Array<{ slides: Array<{ slideId: string }> }> }>;
  }>(await page.request.get(`/api/v2/cases/${fixture.caseId}/materials`));
  expect(
    finalMaterials.specimens.flatMap((specimen) =>
      specimen.blocks.flatMap((block) => block.slides),
    ),
  ).toHaveLength(1);
});
