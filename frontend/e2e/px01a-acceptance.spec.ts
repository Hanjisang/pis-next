import { expect, test, type Page, type TestInfo } from '@playwright/test';

import { login } from './helpers';

test.setTimeout(180_000);

type CaseRef = { caseId: string; pathologyNo: string };

function uniqueSuffix(testInfo: TestInfo) {
  return `${Date.now()}-${testInfo.project.name.replace(/[^a-z0-9]/gi, '')}`;
}

async function logout(page: Page) {
  await page.getByRole('button', { name: '退出' }).click();
  await expect(page.getByRole('region', { name: 'PIS V2 登录' })).toBeVisible();
}

async function registerCase(
  page: Page,
  testInfo: TestInfo,
  businessType: '常规组织病理' | '冰冻' = '常规组织病理',
  sites = ['合成胃窦组织', '合成胃体组织'],
): Promise<CaseRef> {
  const suffix = uniqueSuffix(testInfo);
  await page.getByRole('button', { name: '登记', exact: true }).click();
  await page.getByLabel('患者编号').fill(`PX01A-${suffix}`);
  await page.getByLabel('就诊号').fill(`VISIT-${suffix}`);
  await page.getByLabel('申请号').fill(`APPLICATION-${suffix}`);
  await page.getByLabel('业务类型', { exact: true }).selectOption({ label: businessType });
  await page.getByLabel('取材部位').first().fill(sites[0]);
  for (const site of sites.slice(1)) {
    await page.getByRole('button', { name: '+ 新增标本' }).click();
    await page.getByLabel('取材部位').last().fill(site);
  }
  await page.getByRole('button', { name: '确认登记' }).click();
  const completion = page.getByRole('status').filter({ hasText: '登记完成，病理号已生成' });
  await expect(completion).toBeVisible();
  const pathologyNo = (await completion.getByRole('strong').innerText()).split('：').at(-1)?.trim();
  expect(pathologyNo).toBeTruthy();
  await page.getByRole('button', { name: '病例中心' }).click();
  await expect(page).toHaveURL(/\/v2\/cases\//);
  return {
    caseId: new URL(page.url()).pathname.split('/').filter(Boolean).at(-1)!,
    pathologyNo: pathologyNo!,
  };
}

async function addBlock(page: Page, blockCode: string) {
  await page.getByLabel('新蜡块编号').fill(blockCode);
  await page.getByRole('button', { name: '+ 蜡块' }).click();
  await expect(
    page.getByRole('status').filter({ hasText: `蜡块 ${blockCode} 已建立` }),
  ).toBeVisible();
}

async function completeGrossing(
  page: Page,
  caseRef: CaseRef,
  blockCodes: string[],
  sourceQuery = '',
  acceptanceActions = false,
) {
  await page.goto(`/v2/grossing/${caseRef.caseId}${sourceQuery}`);
  await expect(page.getByLabel('病例取材工作区')).toBeVisible();
  await expect(page.getByLabel('当前标本取材部位')).toBeVisible();
  await page.getByRole('button', { name: '开始取材' }).click();

  await page.getByLabel('当前标本取材部位').fill('PX01A 修改后的胃窦部位');
  await page.getByRole('button', { name: '保存标本信息' }).click();
  await expect(page.getByRole('status').filter({ hasText: '信息已保存' })).toBeVisible();
  await page.getByLabel('大体描述').fill('PX01A 大体描述：灰白组织，分段取材。');
  await page.getByLabel('取材说明').fill('PX01A 取材说明：浏览器真实写入。');
  await page.getByRole('button', { name: '保存', exact: true }).click();
  await expect(page.getByRole('status').filter({ hasText: '取材描述已保存' })).toBeVisible();

  if (acceptanceActions) {
    await addBlock(page, blockCodes[0]);
    await addBlock(page, blockCodes[1]);
    await addBlock(page, blockCodes[2]);
    const blockA2 = page.locator('article.block-chip').filter({ hasText: blockCodes[1] }).first();
    await blockA2.getByRole('button', { name: '修改', exact: true }).click();
    await page.getByLabel(`修改蜡块 ${blockCodes[1]}`).fill('A2-EDITED');
    await page.getByRole('button', { name: '保存修改', exact: true }).click();
    await expect(
      page.getByRole('status').filter({ hasText: '蜡块已修改为 A2-EDITED' }),
    ).toBeVisible();
    await addBlock(page, 'BAD');
    const badBlock = page.locator('article.block-chip').filter({ hasText: 'BAD' }).first();
    await badBlock.getByRole('button', { name: '删除', exact: true }).click();
    await expect(page.getByRole('status').filter({ hasText: '蜡块 BAD 已作废' })).toBeVisible();
    await expect(page.locator('article.block-chip').filter({ hasText: 'BAD' })).toHaveCount(0);
    await addBlock(page, 'A4');
    const firstBlock = page
      .locator('article.block-chip')
      .filter({ hasText: blockCodes[0] })
      .first();
    await firstBlock.getByRole('button', { name: /打印|补打/, exact: true }).click();
    await expect(
      page.getByRole('status').filter({ hasText: '标签已发送到当前打印机' }),
    ).toBeVisible();
    await page.getByRole('button', { name: '补打', exact: true }).first().click();
    await expect(
      page.getByRole('status').filter({ hasText: '标签已发送到当前打印机' }),
    ).toBeVisible();
  } else {
    for (const blockCode of blockCodes) await addBlock(page, blockCode);
  }

  await page.getByRole('button', { name: '完成取材' }).click();
  await expect(page.getByRole('status').filter({ hasText: '取材已完成' })).toBeVisible();
}

async function completeProduction(page: Page, caseRef: CaseRef) {
  await page.goto(`/v2/production/${caseRef.caseId}`);
  await expect(page.getByLabel('玻片制片工作台')).toBeVisible();
  const pendingTab = page.getByRole('tab', { name: /待制片/ });
  if ((await pendingTab.innerText()).replace(/\D/g, '') === '0') {
    await page.getByRole('tab', { name: /进行中/ }).click();
  }
  const selectAll = page.getByRole('checkbox', { name: '选择当前列表全部玻片' });
  await expect(selectAll).toBeVisible();
  await selectAll.check();
  const completeBatch = page.getByRole('button', { name: /批量完成/ });
  await expect(completeBatch).toBeEnabled();
  await completeBatch.click();
  await expect(page.getByRole('status').filter({ hasText: '已完成' })).toBeVisible();
}

async function completeHistology(page: Page, caseRef: CaseRef) {
  await page.goto(`/v2/production/${caseRef.caseId}`);
  await expect(page.getByLabel('脱水、包埋、切片、染色、封片')).toBeVisible();
  await page.locator('[aria-label="选择玻片"] button').first().click();

  async function completePhase(label: string) {
    await page.getByRole('tab', { name: new RegExp(`^${label}`) }).click();
    await page.getByRole('button', { name: `开始${label}`, exact: true }).click();
    const complete = page.getByRole('button', { name: `完成${label}`, exact: true });
    await expect(complete).toBeEnabled();
    await complete.click();
    await expect(page.getByRole('status').filter({ hasText: `${label}已完成` })).toBeVisible();
  }

  await completePhase('脱水');
  await completePhase('包埋');
  await page.getByRole('tab', { name: /^切片/ }).click();
  await page.getByRole('button', { name: '开始切片', exact: true }).click();
  await page.getByRole('button', { name: '记录异常', exact: true }).click();
  await page.getByLabel('异常类型').selectOption({ label: '切片皱褶' });
  await page
    .getByPlaceholder('说明发生了什么以及后续处理')
    .fill('PX01A 记录切片皱褶，继续后续处理。');
  await page.getByRole('button', { name: '保存异常', exact: true }).click();
  await expect(page.getByRole('status').filter({ hasText: '切片异常已记录' })).toBeVisible();
  await page.getByRole('button', { name: '完成切片', exact: true }).click();
  await expect(page.getByRole('status').filter({ hasText: '切片已完成' })).toBeVisible();
  await completePhase('染色');
  await completePhase('封片');
  await completeProduction(page, caseRef);
}

async function openHistory(page: Page) {
  await page.getByRole('button', { name: /^业务历史/ }).click();
  await expect(page.getByRole('heading', { name: /病例业务历史|当前对象历史/ })).toBeVisible();
}

async function bindViewerFixtures(page: Page, caseRef: CaseRef) {
  await page.goto(`/v2/digital-slides/${caseRef.caseId}`);
  await expect(page.getByLabel('数字切片工作台')).toBeVisible();
  const block = page.getByLabel('关联蜡块');
  const slide = page.getByLabel('关联玻片');
  await block.selectOption({ label: 'A1' });
  await slide.selectOption({ label: 'A1-HE' });
  await page.getByLabel('来源平台').fill('PX01A Local Viewer Fixture');
  await page.getByLabel('阅片器引用').fill('/fixtures/px01a-slide-1.svg');
  await page.getByRole('button', { name: '绑定数字切片' }).click();
  await expect(page.getByRole('status').filter({ hasText: '数字切片已绑定' })).toBeVisible();

  await block.selectOption({ label: 'A2' });
  await slide.selectOption({ label: 'A2-HE' });
  await page.getByLabel('阅片器引用').fill('/fixtures/px01a-slide-2.svg');
  await page.getByRole('button', { name: '绑定数字切片' }).click();
  await expect(page.getByRole('status').filter({ hasText: '数字切片已绑定' })).toBeVisible();
}

async function prepareRoutineCase(page: Page, testInfo: TestInfo): Promise<CaseRef> {
  await login(page, 'registrar');
  const caseRef = await registerCase(page, testInfo, '常规组织病理', ['合成胃窦组织']);
  await logout(page);
  await login(page, 'technician');
  await completeGrossing(page, caseRef, ['A1', 'A2']);
  await completeProduction(page, caseRef);
  return caseRef;
}

async function runDiagnosisChain(page: Page, caseRef: CaseRef) {
  await logout(page);
  await login(page, 'doctor-a');
  await page.goto(`/v2/diagnosis/${caseRef.caseId}`);
  await expect(page.getByRole('region', { name: '诊断工作区' })).toBeVisible();
  await page.getByRole('button', { name: '接诊' }).click();
  const editor = page.getByRole('region', { name: '诊断编辑器' });
  await editor.getByLabel('镜下所见').fill('PX01A 初诊镜下所见。');
  await editor.getByLabel('诊断意见').fill('PX01A 初诊病理诊断。');
  await editor.getByLabel('备注').fill('PX01A 初诊备注。');
  await page.getByRole('button', { name: '保存', exact: true }).click();
  await expect(page.getByRole('status').filter({ hasText: '诊断草稿已保存' })).toBeVisible();

  await page.getByRole('button', { name: '技术医嘱', exact: true }).click();
  const orderDialog = page.getByRole('dialog', { name: '开立技术医嘱' });
  await orderDialog.getByLabel('项目').selectOption({ label: '结构化检测结果' });
  await orderDialog.getByRole('button', { name: '确认开立' }).click();
  await expect(page.getByRole('status').filter({ hasText: '技术医嘱已开立' })).toBeVisible();

  await logout(page);
  await login(page, 'technician');
  await page.getByRole('button', { name: '技术医嘱', exact: true }).click();
  const pendingOrder = page
    .locator('.technical-order-card')
    .filter({ hasText: caseRef.pathologyNo });
  await pendingOrder.getByRole('button', { name: '开始处理' }).click();
  await page.getByRole('tab', { name: /处理中/ }).click();
  await page.getByRole('tab', { name: /待录结果/ }).click();
  const executingOrder = page
    .locator('.technical-order-card')
    .filter({ hasText: caseRef.pathologyNo });
  await executingOrder.getByLabel('结论').fill('PX01A 技术结果已返回');
  await executingOrder.getByLabel('结果值').fill('阴性');
  await executingOrder.getByRole('button', { name: '保存并返回诊断' }).click();
  await expect(page.getByRole('status').filter({ hasText: '结果已返回诊断工作区' })).toBeVisible();

  await logout(page);
  await login(page, 'doctor-a');
  await page.goto(`/v2/diagnosis/${caseRef.caseId}`);
  await expect(page.getByText('1 项结果已返回')).toBeVisible();
  await page.getByLabel('下一步').selectOption('REVIEW');
  await page.getByLabel('复诊医生').selectOption({ label: 'Doctor B · 主治医师' });
  await page.getByRole('button', { name: '提交复诊' }).click();
  await expect(page.getByRole('status').filter({ hasText: '已提交复诊' })).toBeVisible();

  await logout(page);
  await login(page, 'doctor-b');
  await page.goto(`/v2/diagnosis/${caseRef.caseId}`);
  await page.getByRole('button', { name: '历史病理' }).click();
  await expect(page.getByText('修改诊断', { exact: true })).toBeVisible();
  await editor.getByLabel('镜下所见').fill('PX01A 复诊修改后的镜下所见。');
  await editor.getByLabel('诊断意见').fill('PX01A 复诊修改后的病理诊断。');
  await page.getByLabel('下一步').selectOption('AUDIT');
  await page.getByLabel('审核医生').selectOption({ label: 'Doctor C · 审核医师' });
  await page.getByRole('button', { name: '提交审核' }).click();
  await expect(page.getByRole('status').filter({ hasText: '已提交审核' })).toBeVisible();

  await logout(page);
  await login(page, 'doctor-c');
  await page.goto(`/v2/diagnosis/${caseRef.caseId}`);
  await page.getByRole('button', { name: '历史病理' }).click();
  await expect(page.getByRole('button', { name: '完成审核', exact: true })).toBeVisible();
  await editor.getByLabel('镜下所见').fill('PX01A 审核修改后的镜下所见。');
  await editor.getByLabel('诊断意见').fill('PX01A 审核修改后的正式诊断。');
  await page.getByRole('button', { name: '完成审核', exact: true }).click();
  await expect(page.getByRole('status').filter({ hasText: '审核已完成' })).toBeVisible();
  await page.getByRole('button', { name: '报告预览' }).click();
  const preview = page.getByRole('dialog', { name: '报告预览' });
  await expect(preview.getByText('预览有效，可以签发。')).toBeVisible();
  await preview.getByRole('button', { name: '确认签发' }).click();
  await expect(page.getByRole('status').filter({ hasText: '已签发' })).toBeVisible();
  await expect(page.getByText('R001', { exact: true })).toBeVisible();
  await expect(
    page.getByLabel('责任、医嘱与报告').getByText('Doctor C', { exact: true }).first(),
  ).toBeVisible();

  await page.getByRole('button', { name: '撤回', exact: true }).click();
  await page.getByLabel('撤回原因').fill('PX01A 报告撤回回归验证');
  await page.getByRole('button', { name: '确认撤回 R001', exact: true }).click();
  await expect(page.getByRole('status').filter({ hasText: '报告已撤回' })).toBeVisible();
  await expect(
    page.locator('.report-history-list article').filter({ hasText: 'R001' }),
  ).toContainText('已撤回');

  await page.getByRole('button', { name: '技术医嘱', exact: true }).click();
  const supplementalOrderDialog = page.getByRole('dialog', { name: '开立技术医嘱' });
  const supplementalRequired = supplementalOrderDialog.getByLabel('这些结果返回前暂不签发');
  await supplementalRequired.uncheck();
  await supplementalOrderDialog.getByLabel('项目').selectOption({ label: '结构化检测结果' });
  await supplementalOrderDialog.getByRole('button', { name: '确认开立', exact: true }).click();
  await expect(page.getByRole('status').filter({ hasText: '技术医嘱已开立' })).toBeVisible();

  await editor.getByLabel('镜下所见').fill('PX01A 撤回后重新签发的镜下所见。');
  await editor.getByLabel('诊断意见').fill('PX01A 撤回后重新签发的正式诊断。');
  await page.getByRole('button', { name: '保存', exact: true }).click();
  await page.getByRole('button', { name: '报告预览', exact: true }).click();
  const resignPreview = page.getByRole('dialog', { name: '报告预览' });
  await expect(resignPreview.getByText('预览有效，可以签发。')).toBeVisible();
  await resignPreview.getByRole('button', { name: '确认签发', exact: true }).click();
  await expect(page.getByRole('status').filter({ hasText: '已签发' })).toBeVisible();
  await expect(page.getByText('R002', { exact: true })).toBeVisible();

  await logout(page);
  await login(page, 'technician');
  await page.getByRole('button', { name: '技术医嘱', exact: true }).click();
  const supplementalPending = page
    .locator('.technical-order-card')
    .filter({ hasText: caseRef.pathologyNo })
    .filter({ hasText: '待处理' })
    .first();
  await supplementalPending.getByRole('button', { name: '开始处理', exact: true }).click();
  await page.getByRole('tab', { name: /待录结果/ }).click();
  const supplementalResult = page
    .locator('.technical-order-card')
    .filter({ hasText: caseRef.pathologyNo })
    .filter({ hasText: '结构化检测结果' })
    .first();
  await supplementalResult.getByLabel('结论').fill('PX01A 补充技术结果已返回');
  await supplementalResult.getByLabel('结果值').fill('阴性');
  await supplementalResult.getByRole('button', { name: '保存并返回诊断', exact: true }).click();
  await expect(page.getByRole('status').filter({ hasText: '结果已返回诊断工作区' })).toBeVisible();

  await logout(page);
  await login(page, 'doctor-c');
  await page.goto(`/v2/diagnosis/${caseRef.caseId}`);
  await expect(page.getByText('2 项结果已返回')).toBeVisible();
  await page.getByRole('button', { name: '补充报告', exact: true }).click();
  await page.getByLabel('补充内容').fill('PX01A 补充报告回归验证内容。');
  await page.getByRole('button', { name: '签发补充报告', exact: true }).click();
  await expect(page.getByRole('status').filter({ hasText: '补充报告已签发' })).toBeVisible();
  await expect(page.getByText('S001', { exact: true })).toBeVisible();
}

async function signFrozenRound(page: Page, caseRef: CaseRef, roundNo: number) {
  await logout(page);
  await login(page, 'doctor-a');
  await page.goto(`/v2/frozen/${caseRef.caseId}`);
  const roundButton = page.getByRole('button', { name: new RegExp(`冰冻第 ${roundNo} 轮`) });
  await roundButton.click();
  const diagnosisStage = page.getByRole('button', { name: /快速诊断/ });
  await diagnosisStage.click();
  await expect(
    page.getByRole('status').filter({ hasText: `冰冻第 ${roundNo} 轮快速诊断已建立` }),
  ).toBeVisible();
  await page.getByRole('button', { name: /快速诊断/ }).click();
  await expect(page).toHaveURL(/\/v2\/diagnosis\/.*roundId=/);
  const diagnosisUrl = page.url();
  const editor = page.getByRole('region', { name: '诊断编辑器' });
  await editor.getByLabel('镜下所见').fill(`PX01A 冰冻第${roundNo}轮镜下所见。`);
  await editor.getByLabel('诊断意见').fill(`PX01A 冰冻第${roundNo}轮快速诊断。`);
  await page.getByLabel('下一步').selectOption('AUDIT');
  await page.getByLabel('审核医生').selectOption({ label: 'Doctor C · 审核医师' });
  await page.getByRole('button', { name: '提交审核' }).click();
  await expect(page.getByRole('status').filter({ hasText: '已提交审核' })).toBeVisible();

  await logout(page);
  await login(page, 'doctor-c');
  await page.goto(diagnosisUrl);
  await editor.getByLabel('镜下所见').fill(`PX01A 冰冻第${roundNo}轮审核所见。`);
  await editor.getByLabel('诊断意见').fill(`PX01A 冰冻第${roundNo}轮审核诊断。`);
  await page.getByRole('button', { name: '完成审核' }).click();
  await page.getByRole('button', { name: '报告预览' }).click();
  const preview = page.getByRole('dialog', { name: '报告预览' });
  await expect(preview.getByText('预览有效，可以签发。')).toBeVisible();
  await preview.getByRole('button', { name: '确认签发' }).click();
  await expect(page.getByRole('status').filter({ hasText: '已签发' })).toBeVisible();
}

test('PX01A-GH：取材与制片真实写入，包含蜡块修改/作废、五段技术事实和异常历史', async ({
  page,
}, testInfo) => {
  await login(page, 'registrar');
  const caseRef = await registerCase(page, testInfo);
  await logout(page);
  await login(page, 'technician');
  await completeGrossing(page, caseRef, ['A1', 'A2', 'A3'], '', true);
  await completeHistology(page, caseRef);
  await page.getByRole('button', { name: '病例中心' }).click();
  await openHistory(page);
  for (const title of [
    '修改标本信息',
    '新增蜡块',
    '修改蜡块',
    '作废蜡块',
    '完成标签打印',
    '完成取材',
    '脱水开始',
    '脱水完成',
    '包埋完成',
    '切片记录异常',
    '切片完成',
    '染色完成',
    '封片完成',
  ]) {
    await expect(page.getByText(title, { exact: true }).first()).toBeVisible();
  }
  await page.getByRole('button', { name: '材料与制片' }).click();
  await expect(page.getByText('A2-EDITED', { exact: true })).toBeVisible();
  await expect(page.getByText('BAD', { exact: true })).toHaveCount(0);
});

test('PX01A-IJLMO：诊断责任链、Viewer、TechnicalOrder 和报告签发真实操作', async ({
  page,
}, testInfo) => {
  const caseRef = await prepareRoutineCase(page, testInfo);
  await bindViewerFixtures(page, caseRef);
  await logout(page);
  await login(page, 'doctor-a');
  await page.goto(`/v2/diagnosis/${caseRef.caseId}`);
  await page.getByRole('button', { name: /数字切片 2/ }).click();
  const digitalLinks = page.locator('.digital-slide-link');
  await digitalLinks.first().click();
  const viewer = page.getByLabel('数字切片阅片器');
  await expect(viewer).toBeVisible();
  await expect(viewer.locator('img').first()).toBeVisible();
  await expect(viewer.getByLabel('阅片上下文')).toContainText(caseRef.pathologyNo);
  await expect(viewer.getByLabel('阅片上下文')).toContainText('A1');
  await viewer.getByRole('button', { name: '放大' }).click();
  await expect(viewer.getByText('125%', { exact: true })).toBeVisible();
  const viewport = viewer.locator('.image-viewer-viewport');
  const viewportBox = await viewport.boundingBox();
  if (viewportBox) {
    await page.mouse.move(viewportBox.x + 120, viewportBox.y + 120);
    await page.mouse.down();
    await page.mouse.move(viewportBox.x + 155, viewportBox.y + 145);
    await page.mouse.up();
  }
  await viewer.getByRole('button', { name: '全屏查看' }).click();
  await expect(viewer.getByRole('button', { name: '退出全屏' })).toBeVisible();
  await viewer.getByRole('button', { name: '退出全屏' }).click();
  await digitalLinks.nth(1).click();
  await expect(viewer.locator('img').first()).toHaveAttribute('src', /px01a-slide-2\.svg/);
  await expect(viewer.getByLabel('阅片上下文')).toContainText('A2');
  await runDiagnosisChain(page, caseRef);
});

test('PX01A-K：冰冻第1/2轮、独立签发和冰剩常规病例真实写入', async ({ page }, testInfo) => {
  await login(page, 'registrar');
  const caseRef = await registerCase(page, testInfo, '冰冻', ['冰冻第1轮标本']);
  await logout(page);
  await login(page, 'technician');
  await page.goto(`/v2/frozen/${caseRef.caseId}`);
  await page.getByRole('button', { name: '开始第 1 轮' }).click();
  await expect(page.getByText('冰冻第 1 轮', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: /取材与制片/ }).click();
  const roundOneQuery = new URL(page.url()).search;
  await completeGrossing(page, caseRef, ['A1'], roundOneQuery);
  await completeProduction(page, caseRef);
  await signFrozenRound(page, caseRef, 1);

  await logout(page);
  await login(page, 'technician');
  await page.goto(`/v2/frozen/${caseRef.caseId}`);
  await page.getByLabel('标本编号').fill('B');
  await page.getByLabel('送检部位').fill('冰冻第2轮标本');
  await page.getByRole('button', { name: /创建第 2 轮并登记/ }).click();
  await expect(page.getByText('冰冻第 2 轮', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: /取材与制片/ }).click();
  const roundTwoQuery = new URL(page.url()).search;
  await completeGrossing(page, caseRef, ['B1'], roundTwoQuery);
  await completeProduction(page, caseRef);
  await signFrozenRound(page, caseRef, 2);

  await logout(page);
  await login(page, 'doctor-c');
  await page.goto(`/v2/frozen/${caseRef.caseId}`);
  await page.getByRole('button', { name: '冰冻结束' }).click();
  await expect(page.getByRole('status').filter({ hasText: '已创建冰剩常规病例' })).toBeVisible();
  await page.getByRole('button', { name: '病例中心' }).click();
  await openHistory(page);
  await expect(page.getByText('更新冰冻轮次', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: /^报告 2/ }).click();
  await expect(page.getByText('R001', { exact: true })).toBeVisible();
  await expect(page.getByText('R002', { exact: true })).toBeVisible();
  await expect(page.getByText('冰冻剩余组织', { exact: true })).toHaveCount(0);
});

test('PX01A-EF：个人工作台、全局搜索落点和操作权限边界', async ({ page }, testInfo) => {
  await login(page, 'registrar');
  const caseRef = await registerCase(page, testInfo, '常规组织病理', ['查询验证标本']);
  await page.goto('/v2/workbench');
  await expect(page.getByLabel('我的工作台')).toContainText('Registrar');
  await expect(page.getByRole('button', { name: '登记', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '诊断', exact: true })).toHaveCount(0);
  await page.keyboard.press('Control+K');
  const search = page.getByRole('dialog', { name: '全局查询' });
  await search.getByRole('textbox').fill(caseRef.pathologyNo);
  await search.getByRole('button', { name: '查询', exact: true }).click();
  await search.getByRole('button', { name: new RegExp(caseRef.pathologyNo) }).click();
  await expect(page).toHaveURL(/\/v2\/cases\//);
  await expect(page.getByLabel('病例中心')).toContainText(caseRef.pathologyNo);
  await page.getByRole('button', { name: /^业务历史/ }).click();
  await expect(page.getByText('完成登记', { exact: true })).toBeVisible();
});
