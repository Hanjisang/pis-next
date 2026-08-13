import { expect, test, type APIResponse, type Page, type TestInfo } from '@playwright/test';

import { login } from './helpers';

test.setTimeout(180_000);
test.use({ actionTimeout: 15_000, navigationTimeout: 30_000 });

type CaseRef = { caseId: string; pathologyNo: string };

async function logout(page: Page) {
  await page.getByRole('button', { name: '退出' }).click();
  await expect(page.getByRole('region', { name: 'PIS V2 登录' })).toBeVisible();
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

async function registerFrozenCase(page: Page, testInfo: TestInfo): Promise<CaseRef> {
  const suffix = `${Date.now()}-${testInfo.project.name.replace(/[^a-z0-9]/gi, '')}`;
  const created = await jsonOk<{ caseId: string; caseNo: string }>(
    await page.request.post('/api/v2/registration/cases', {
      data: {
        sourceSystemCode: 'PIS-FC03C1-PLAYWRIGHT',
        externalApplicationId: `FC03C1-${suffix}`,
        applicationItemCode: 'SYNTH-FROZEN',
        patientReference: `FC03C1-PATIENT-${suffix}`,
        visitReference: `FC03C1-VISIT-${suffix}`,
        idempotencyKey: `fc03c1-case-${suffix}`,
      },
    }),
  );
  await jsonOk(
    await page.request.post('/api/v2/registration/specimens', {
      data: {
        caseId: created.caseId,
        specimenCode: 'FC03C1-FS1',
        specimenName: 'FC03C1-FS1',
        specimenKindCode: 'FROZEN',
        creationSourceCode: 'REGISTRATION',
        sourceKindCode: 'LOCAL',
        sourceReference: `FC03C1-SOURCE-FS1-${suffix}`,
        collectionSite: 'FC03C1-FS1',
        collectionMethodCode: 'SURGICAL',
        quantityValue: 1,
        quantityUnitCode: 'CONTAINER',
        description: 'FC03C1 FC03C1-FS1',
        receivedAt: new Date().toISOString(),
        labelCode: `FC03C1-LABEL-FS1-${suffix}`,
        idempotencyKey: `fc03c1-specimen-fs1-${suffix}`,
      },
    }),
  );
  return {
    caseId: created.caseId,
    pathologyNo: created.caseNo,
  };
}

async function jsonOk<T>(response: APIResponse): Promise<T> {
  const body = await response.text();
  expect(response.ok(), body).toBe(true);
  return JSON.parse(body) as T;
}

async function prepareSignedFrozenCase(page: Page, testInfo: TestInfo): Promise<CaseRef> {
  await ensureUser(page, 'registrar', 'Registrar');
  const caseRef = await registerFrozenCase(page, testInfo);
  await logout(page);
  await ensureUser(page, 'technician', 'Technician');
  await page.goto(`/v2/frozen/${caseRef.caseId}`);
  await page.getByRole('button', { name: '开始第 1 轮', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('第 1 轮已开始');

  const workspace = await jsonOk<{
    rounds: Array<{ roundId: string; specimens: Array<{ specimenId: string }> }>;
  }>(await page.request.get(`/api/v2/frozen/cases/${caseRef.caseId}/workspace`));
  await completeFrozenProduction(page, caseRef, workspace.rounds[0]!, testInfo, 1);
  await signFrozenRound(page, caseRef, 1);
  return caseRef;
}

async function completeFrozenProduction(
  page: Page,
  caseRef: CaseRef,
  round: { roundId: string; specimens: Array<{ specimenId: string }> },
  testInfo: TestInfo,
  roundNo: number,
) {
  const specimenIds = round.specimens.map((specimen) => specimen.specimenId);
  await jsonOk(
    await page.request.post(
      `/api/v2/cases/${caseRef.caseId}/frozen-rounds/${round.roundId}/slides/generate`,
      {
        data: {
          specimenIds,
          idempotencyKey: `fc03c1-generate-${roundNo}-${testInfo.project.name}-${Date.now()}`,
        },
      },
    ),
  );
  const materials = await jsonOk<{
    specimens: Array<{ directSlides: Array<{ slideId: string }> }>;
  }>(
    await page.request.get(
      `/api/v2/cases/${caseRef.caseId}/materials?frozenRoundId=${encodeURIComponent(round.roundId)}`,
    ),
  );
  const slides = materials.specimens.flatMap((specimen) => specimen.directSlides);
  expect(slides).toHaveLength(specimenIds.length);
  for (const slide of slides) {
    await jsonOk(
      await page.request.post(`/api/v2/slides/${slide.slideId}/complete`, {
        data: {
          expectedVersion: 0,
          idempotencyKey: `fc03c1-complete-${slide.slideId}`,
        },
      }),
    );
  }
}

async function addSecondFrozenRound(page: Page, caseRef: CaseRef, testInfo: TestInfo) {
  await ensureUser(page, 'technician', 'Technician');
  await page.goto(`/v2/frozen/${caseRef.caseId}`);
  await page.getByRole('button', { name: '新增一轮', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('新的冰冻轮次已创建');
  await jsonOk(
    await page.request.post(`/api/v2/frozen/cases/${caseRef.caseId}/specimens`, {
      data: {
        specimenCode: 'FC03C1-FS2',
        specimenKindCode: 'FROZEN',
        collectionSite: 'FC03C1-FS2',
        collectionMethodCode: 'SURGICAL',
        labelCode: `FC03C1-LABEL-FS2-${testInfo.project.name}-${Date.now()}`,
        idempotencyKey: `fc03c1-specimen-fs2-${testInfo.project.name}-${Date.now()}`,
      },
    }),
  );
  const workspace = await jsonOk<{
    rounds: Array<{
      roundId: string;
      roundNo: number;
      specimens: Array<{ specimenId: string }>;
    }>;
  }>(await page.request.get(`/api/v2/frozen/cases/${caseRef.caseId}/workspace`));
  const secondRound = workspace.rounds.find((round) => round.roundNo === 2)!;
  expect(secondRound.specimens).toHaveLength(1);
  await completeFrozenProduction(page, caseRef, secondRound, testInfo, 2);
}

async function completeRoutineProduction(
  page: Page,
  routineCaseId: string,
  routineSpecimenId: string,
) {
  await ensureUser(page, 'grossing', 'Grossing Staff');
  const grossing = await jsonOk<{ grossingId: string; concurrencyVersion: number }>(
    await page.request.post(`/api/v2/cases/${routineCaseId}/grossings`, {
      data: {
        sourceType: 'INITIAL',
        grossDescription: 'FC03C1 常规合成首次取材',
        grossingInstruction: 'FC03C1 常规合成取材',
        grossingDoctorId: 'doctor-a',
        recorderId: 'grossing',
        idempotencyKey: `fc03c1-routine-grossing-${routineCaseId}`,
      },
    }),
  );
  await jsonOk(
    await page.request.post(`/api/v2/grossings/${grossing.grossingId}/specimens`, {
      data: {
        specimenId: routineSpecimenId,
        materialDescription: 'FC03C1 常规合成大体所见',
        idempotencyKey: `fc03c1-routine-associate-${routineCaseId}`,
      },
    }),
  );
  await jsonOk(
    await page.request.post(`/api/v2/grossings/${grossing.grossingId}/blocks`, {
      data: {
        specimenId: routineSpecimenId,
        blockCode: 'A1',
        blockType: 'ROUTINE',
        samplingDescription: 'FC03C1 常规合成代表性组织',
        idempotencyKey: `fc03c1-routine-block-${routineCaseId}`,
      },
    }),
  );
  await jsonOk(
    await page.request.post(`/api/v2/grossings/${grossing.grossingId}/complete`, {
      data: {
        expectedVersion: grossing.concurrencyVersion,
        idempotencyKey: `fc03c1-routine-grossing-complete-${routineCaseId}`,
      },
    }),
  );

  await logout(page);
  await ensureUser(page, 'technician', 'Technician');
  const materials = await jsonOk<{
    specimens: Array<{
      blocks: Array<{
        slides: Array<{ slideId: string; concurrencyVersion: number }>;
      }>;
    }>;
  }>(await page.request.get(`/api/v2/cases/${routineCaseId}/materials`));
  const slides = materials.specimens.flatMap((specimen) =>
    specimen.blocks.flatMap((block) => block.slides),
  );
  expect(slides).toHaveLength(1);
  await jsonOk(
    await page.request.post(`/api/v2/slides/${slides[0]!.slideId}/complete`, {
      data: {
        expectedVersion: slides[0]!.concurrencyVersion,
        idempotencyKey: `fc03c1-routine-slide-complete-${routineCaseId}`,
      },
    }),
  );
}

async function signRoutineDiagnosis(page: Page, routineCaseId: string) {
  await logout(page);
  await ensureUser(page, 'doctor-a', 'Doctor A');
  await jsonOk(
    await page.request.post('/api/v2/diagnoses/claim', {
      data: {
        caseId: routineCaseId,
        idempotencyKey: `fc03c1-routine-claim-${routineCaseId}`,
      },
    }),
  );
  const diagnosisUrl = `/v2/diagnosis/${routineCaseId}`;
  await page.goto(diagnosisUrl);
  const editor = page.getByRole('region', { name: '诊断编辑器' });
  await editor.getByLabel('镜下所见').fill('FC03C1 常规病理镜下所见。');
  await editor.getByLabel('病理诊断').fill('FC03C1 常规病理最终诊断。');
  await page.getByRole('button', { name: '提交复诊', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('已提交复诊');

  await logout(page);
  await ensureUser(page, 'doctor-b', 'Doctor B');
  await page.goto(diagnosisUrl);
  await editor.getByLabel('镜下所见').fill('FC03C1 常规病理审核所见。');
  await editor.getByLabel('病理诊断').fill('FC03C1 常规病理审核诊断。');
  await page.getByRole('button', { name: '提交审核', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('已提交审核');

  await logout(page);
  await ensureUser(page, 'doctor-c', 'Doctor C');
  await page.goto(diagnosisUrl);
  await editor.getByRole('textbox', { name: '病理诊断' }).fill('FC03C1 常规病理最终诊断。');
  await page.getByRole('button', { name: '完成审核', exact: true }).click();
  await page.getByRole('button', { name: '报告预览' }).click();
  const preview = page.getByRole('dialog', { name: '报告预览' });
  await expect(preview.getByText('预览有效，可以签发。')).toBeVisible();
  await preview.getByRole('button', { name: '确认签发' }).click();
  await expect(page.getByRole('status')).toContainText('已签发');
}

async function signFrozenRound(page: Page, caseRef: CaseRef, roundNo: number) {
  await logout(page);
  await ensureUser(page, 'doctor-a', 'Doctor A');
  await page.goto(`/v2/frozen/${caseRef.caseId}`);
  await page.getByRole('button', { name: new RegExp(`^第 ${roundNo} 轮`) }).click();
  await page.getByRole('button', { name: '进入冰冻诊断', exact: true }).click();
  await expect(page).toHaveURL(/\/v2\/diagnosis\/.*roundId=/);
  const diagnosisUrl = page.url();
  const editor = page.getByRole('region', { name: '诊断编辑器' });
  await editor.getByLabel('镜下所见').fill(`FC03C1 冰冻第${roundNo}轮镜下所见。`);
  await editor.getByLabel('病理诊断').fill(`FC03C1 冰冻第${roundNo}轮正式诊断。`);
  await page.getByRole('button', { name: '提交复诊', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('已提交复诊');

  await logout(page);
  await ensureUser(page, 'doctor-b', 'Doctor B');
  await page.goto(diagnosisUrl);
  await editor.getByLabel('镜下所见').fill(`FC03C1 冰冻第${roundNo}轮审核所见。`);
  await editor.getByLabel('病理诊断').fill(`FC03C1 冰冻第${roundNo}轮审核诊断。`);
  await page.getByRole('button', { name: '提交审核', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('已提交审核');

  await logout(page);
  await ensureUser(page, 'doctor-c', 'Doctor C');
  await page.goto(diagnosisUrl);
  await editor
    .getByRole('textbox', { name: '病理诊断' })
    .fill(`FC03C1 冰冻第${roundNo}轮审核诊断。`);
  await page.getByRole('button', { name: '完成审核', exact: true }).click();
  await page.getByRole('button', { name: '报告预览' }).click();
  const preview = page.getByRole('dialog', { name: '报告预览' });
  await expect(preview.getByText('预览有效，可以签发。')).toBeVisible();
  await preview.getByRole('button', { name: '确认签发' }).click();
  await expect(page.getByRole('status')).toContainText('已签发');
}

test('FC03C1：通知重试、多轮阻断、选择性转常规和诊断对照闭环', async ({ page }, testInfo) => {
  const caseRef = await prepareSignedFrozenCase(page, testInfo);

  await logout(page);
  await ensureUser(page, 'technician', 'Technician');
  await page.goto(`/v2/frozen/${caseRef.caseId}`);
  await page.getByText('技术记录与轮次操作', { exact: true }).click();
  await expect(page.getByText('术中结果发送失败', { exact: true })).toBeVisible();
  await expect(page.getByText('模拟发送', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '重新发送', exact: true })).toBeVisible();
  const firstRound = await jsonOk<{
    rounds: Array<{ roundId: string }>;
  }>(await page.request.get(`/api/v2/frozen/cases/${caseRef.caseId}/workspace`));
  const reportCountBeforeRetry = (
    await jsonOk<{ reports: Array<{ reportId: string }> }>(
      await page.request.get(
        `/api/v2/diagnosis-workspaces/frozen-rounds/${firstRound.rounds[0]!.roundId}`,
      ),
    )
  ).reports.length;
  await page.getByRole('button', { name: '发送记录', exact: true }).click();
  let history = page.getByRole('dialog', { name: '发送记录' });
  await expect(history).toContainText('第 1 次');
  await expect(history).toContainText('失败');
  await history.getByRole('button', { name: '关闭', exact: true }).click();
  await page.getByRole('button', { name: '重新发送', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('通知已重新发送');
  await page.getByText('技术记录与轮次操作', { exact: true }).click();
  await expect(page.getByText('发送成功', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '发送记录', exact: true }).click();
  history = page.getByRole('dialog', { name: '发送记录' });
  await expect(history).toContainText('第 1 次');
  await expect(history).toContainText('第 2 次');
  await expect(history).toContainText('失败');
  await expect(history).toContainText('成功');
  await history.getByRole('button', { name: '关闭', exact: true }).click();
  await page.reload();
  await page.getByText('技术记录与轮次操作', { exact: true }).click();
  await expect(page.getByText('发送成功', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '重新发送', exact: true })).toHaveCount(0);
  const reportCountAfterRetry = (
    await jsonOk<{ reports: Array<{ reportId: string }> }>(
      await page.request.get(
        `/api/v2/diagnosis-workspaces/frozen-rounds/${firstRound.rounds[0]!.roundId}`,
      ),
    )
  ).reports.length;
  expect(reportCountAfterRetry).toBe(reportCountBeforeRetry);

  await addSecondFrozenRound(page, caseRef, testInfo);
  await logout(page);
  await ensureUser(page, 'admin', 'Admin');
  await page.goto(`/v2/frozen/${caseRef.caseId}`);
  await expect(page.getByRole('button', { name: /^第 1 轮/ })).toContainText('已报告');
  await expect(page.getByRole('button', { name: /^第 2 轮/ })).toBeVisible();

  await page.getByRole('button', { name: '结束冰冻并转常规', exact: true }).click();
  let dialog = page.getByRole('dialog', { name: '结束冰冻并转常规' });
  await expect(dialog).toContainText('第2轮尚未完成冰冻诊断或报告签发');
  await expect(dialog.getByRole('button', { name: '确认结束冰冻', exact: true })).toBeDisabled();
  const beforeSecondRoundSigned = await jsonOk<{ ended: boolean; routineCaseId?: string }>(
    await page.request.get(`/api/v2/frozen/cases/${caseRef.caseId}/workspace`),
  );
  expect(beforeSecondRoundSigned.ended).toBe(false);
  expect(beforeSecondRoundSigned.routineCaseId).toBeFalsy();
  await dialog.getByRole('button', { name: '取消', exact: true }).click();

  await signFrozenRound(page, caseRef, 2);
  await logout(page);
  await ensureUser(page, 'admin', 'Admin');
  await page.goto(`/v2/frozen/${caseRef.caseId}`);

  await page.getByRole('button', { name: '结束冰冻并转常规', exact: true }).click();
  dialog = page.getByRole('dialog', { name: '结束冰冻并转常规' });
  await expect(dialog).toContainText(caseRef.pathologyNo);
  await expect(dialog).toContainText('默认选中 2 个待转常规标本');
  await expect(dialog.getByRole('checkbox')).toHaveCount(2);
  await expect(dialog.getByRole('checkbox').nth(0)).toBeChecked();
  await expect(dialog.getByRole('checkbox').nth(1)).toBeChecked();
  await dialog.getByRole('button', { name: '取消', exact: true }).click();
  await expect(page.getByRole('dialog', { name: '结束冰冻并转常规' })).toHaveCount(0);
  const beforeCancelEnd = await jsonOk<{ ended: boolean; routineCaseId?: string }>(
    await page.request.get(`/api/v2/frozen/cases/${caseRef.caseId}/workspace`),
  );
  expect(beforeCancelEnd.ended).toBe(false);
  expect(beforeCancelEnd.routineCaseId).toBeFalsy();

  await page.getByRole('button', { name: '结束冰冻并转常规', exact: true }).click();
  const reopened = page.getByRole('dialog', { name: '结束冰冻并转常规' });
  const checkboxes = reopened.getByRole('checkbox');
  await checkboxes.nth(1).uncheck();
  await expect(reopened).toContainText('将创建 1 个常规病例');
  await expect(reopened).toContainText('将转入 1 个标本');
  await reopened.getByRole('button', { name: '确认结束冰冻', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('冰冻已结束');
  await expect(page.getByText(/已转常规：/)).toBeVisible();

  const ended = await jsonOk<{
    ended: boolean;
    routineCaseId: string;
    routinePathologyNo: string;
    rounds: Array<{ specimens: Array<{ specimenId: string }> }>;
  }>(await page.request.get(`/api/v2/frozen/cases/${caseRef.caseId}/workspace`));
  expect(ended.ended).toBe(true);
  expect(ended.routineCaseId).toBeTruthy();
  expect(ended.routinePathologyNo).not.toBe(caseRef.pathologyNo);
  expect(ended.rounds).toHaveLength(2);
  const frozenSpecimenIds = ended.rounds.flatMap((round) =>
    round.specimens.map((specimen) => specimen.specimenId),
  );
  const routineWorkspace = await jsonOk<{
    caseHeader: { businessTypeCode: string; pathologyNo: string };
    materialTree: { specimens: Array<{ specimenId: string }> };
  }>(await page.request.get(`/api/v2/case-workspaces/${ended.routineCaseId}`));
  expect(routineWorkspace.caseHeader.businessTypeCode).toBe('HISTOLOGY');
  expect(routineWorkspace.caseHeader.pathologyNo).toBe(ended.routinePathologyNo);
  expect(routineWorkspace.materialTree.specimens).toHaveLength(1);
  expect(frozenSpecimenIds).not.toContain(routineWorkspace.materialTree.specimens[0]!.specimenId);

  await page.reload();
  await expect(page.getByText('冰冻已结束', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '查看常规病例', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '结束冰冻并转常规', exact: true })).toHaveCount(0);

  await page.getByRole('button', { name: '查看常规病例', exact: true }).click();
  await expect(page).toHaveURL(new RegExp(`/v2/cases/${ended.routineCaseId}`));
  await expect(page.getByLabel('病例中心')).toContainText('常规');
  await expect(page.getByText(`来源冰冻：${caseRef.pathologyNo}`, { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '查看冰冻/石蜡对照', exact: true })).toBeVisible();

  await page.getByRole('button', { name: '查看冰冻/石蜡对照', exact: true }).click();
  const comparison = page.getByRole('dialog', { name: '冰冻 / 石蜡结果对照' });
  await expect(comparison).toContainText('第 1 轮');
  await expect(comparison).toContainText('第 2 轮');
  await expect(comparison).toContainText('FC03C1-FS1');
  await expect(comparison).toContainText('FC03C1 冰冻第1轮审核诊断。');
  await expect(comparison).toContainText('FC03C1 冰冻第2轮审核诊断。');
  await expect(comparison).toContainText('常规病理尚未完成诊断');
  await expect(comparison).not.toContainText(/一致|不一致/);
  await comparison.getByRole('button', { name: '关闭' }).click();

  await page.getByRole('button', { name: '来源冰冻：' + caseRef.pathologyNo, exact: true }).click();
  await expect(page).toHaveURL(new RegExp(`/v2/frozen/${caseRef.caseId}`));
  await expect(page.getByRole('button', { name: '查看冰冻/石蜡对照', exact: true })).toBeVisible();

  const replay = await page.request.post(`/api/v2/frozen/cases/${caseRef.caseId}/finish`, {
    data: { idempotencyKey: `fc03c1-repeat-${Date.now()}` },
  });
  expect(replay.ok()).toBe(true);
  expect((await replay.json()).duplicate).toBe(true);
  const afterReplay = await page.request.get(`/api/v2/frozen/cases/${caseRef.caseId}/workspace`);
  const afterReplayBody = await afterReplay.json();
  expect(afterReplayBody.routineCaseId).toBe(ended.routineCaseId);

  await completeRoutineProduction(
    page,
    ended.routineCaseId,
    routineWorkspace.materialTree.specimens[0]!.specimenId,
  );
  await signRoutineDiagnosis(page, ended.routineCaseId);
  await logout(page);
  await ensureUser(page, 'admin', 'Admin');
  await page.goto(`/v2/frozen/${caseRef.caseId}`);
  await page.getByRole('button', { name: '查看冰冻/石蜡对照', exact: true }).click();
  const completedComparison = page.getByRole('dialog', { name: '冰冻 / 石蜡结果对照' });
  await expect(completedComparison).toContainText('第 1 轮');
  await expect(completedComparison).toContainText('第 2 轮');
  await expect(completedComparison).toContainText('FC03C1 冰冻第1轮审核诊断。');
  await expect(completedComparison).toContainText('FC03C1 冰冻第2轮审核诊断。');
  await expect(completedComparison).toContainText('FC03C1 常规病理最终诊断。');
  await expect(completedComparison).not.toContainText(/一致|不一致/);
});
