import { expect, test, type APIResponse, type Page } from '@playwright/test';

import { expectNoPageOverflow, login } from './helpers';

async function openNewApplication(page: Page) {
  await page.goto('/v2/registration');
  await expect(page.getByRole('heading', { name: '待登记申请' })).toBeVisible();
  await page.getByRole('button', { name: '新建病理申请' }).click();
  await expect(page.getByRole('heading', { name: '电子申请' })).toBeVisible();
}

type CreatedApplication = {
  applicationId: string;
  applicationNo: string;
  items: Array<{ itemId: string; statusCode: string }>;
};

type RegisteredCase = {
  caseId: string;
  caseNo: string;
  specimenId: string;
  applicationItemId: string;
};

async function expectOk(response: APIResponse) {
  expect(response.ok(), await response.text()).toBe(true);
  return response;
}

async function createApplicationFixture(
  page: Page,
  applicationNo: string,
  items: Array<{ code: string; name: string; specimen: string }>,
  visitTypeCode = 'OUTPATIENT',
) {
  const response = await page.request.post('/api/v2/applications', {
    data: {
      applicationNo,
      sourceTypeCode: 'MANUAL',
      sourceSystemCode: 'PIS-E2E',
      patientInfoSourceCode: 'MANUAL',
      patientReference: `PAT-${applicationNo}`,
      patientName: '合成测试患者',
      patientSexCode: 'F',
      ageValue: 35,
      ageUnitCode: 'YEAR',
      visitReference: `${visitTypeCode === 'INPATIENT' ? 'ZY' : 'MZ'}-${applicationNo}`,
      visitTypeCode,
      applicationDepartment: '合成申请科室',
      applicantReference: '合成申请医生',
      clinicalDiagnosis: '合成临床诊断',
      items: items.map((item, index) => ({
        externalItemCode: item.code,
        itemName: item.name,
        specimenKindCode: item.code.includes('CYTOLOGY') ? 'FLUID' : 'TISSUE',
        specimenDescription: item.specimen,
        sequenceNo: index + 1,
      })),
    },
  });
  await expectOk(response);
  return (await response.json()) as CreatedApplication;
}

async function verifyItem(page: Page, application: CreatedApplication, itemIndex: number) {
  const item = application.items[itemIndex]!;
  await expectOk(
    await page.request.post(`/api/v2/applications/${application.applicationId}/delivery`, {
      data: {
        applicationItemId: item.itemId,
        incomingSpecimenReference: `${application.applicationNo}-${itemIndex + 1}`,
        specimenLabelCode: `${application.applicationNo}-${itemIndex + 1}`,
        patientReference: `PAT-${application.applicationNo}`,
        actualSpecimenDescription: `合成标本 ${itemIndex + 1}`,
        outcomeCode: 'ACCEPTED',
        patientMatch: true,
        applicationMatch: true,
        quantityMatch: true,
        specimenMatch: true,
        containerMatch: true,
        fixationMatch: true,
      },
    }),
  );
}

async function registerItem(page: Page, application: CreatedApplication, itemIndex: number) {
  await verifyItem(page, application, itemIndex);
  const item = application.items[itemIndex]!;
  const response = await page.request.post(
    `/api/v2/applications/${application.applicationId}/items/${item.itemId}/register`,
    { data: {} },
  );
  await expectOk(response);
  const body = (await response.json()) as { cases: RegisteredCase[] };
  return body.cases[0]!;
}

async function checkAllVerificationFacts(page: Page) {
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
}

test('FC02A 门诊 HIS 查询、申请创建与待登记闭环', async ({ page }, testInfo) => {
  await login(page, 'registrar');
  await openNewApplication(page);

  await page.getByRole('textbox', { name: '患者查询号' }).fill('MZ10001');
  await page.getByRole('button', { name: '从 HIS 查询' }).click();
  await expect(page.getByRole('status')).toContainText('已获取患者与就诊信息');
  await expect(page.getByRole('textbox', { name: '患者姓名' })).toHaveValue('张某');

  const applicationNo = `OP-FC02A-${testInfo.project.name}-${Date.now()}`;
  await page.getByRole('textbox', { name: '申请号' }).fill(applicationNo);
  await page.getByRole('combobox', { name: '申请项目' }).selectOption('SYNTH-HISTOLOGY');
  await page.getByRole('textbox', { name: '标本名称 / 部位' }).fill('合成胃窦活检');
  const validated = page.waitForResponse(
    (response) =>
      response.url().endsWith('/api/v2/applications/validate') &&
      response.request().method() === 'POST',
  );
  const created = page.waitForResponse(
    (response) =>
      response.url().endsWith('/api/v2/applications') && response.request().method() === 'POST',
  );
  await page.getByRole('button', { name: '保存申请' }).click();
  const validation = await validated;
  const validationBody = (await validation.json()) as { valid: boolean; issues: unknown[] };
  expect(validation.status(), JSON.stringify(validationBody)).toBe(200);
  expect(validationBody.valid, JSON.stringify(validationBody)).toBe(true);
  expect((await created).status()).toBe(200);

  await expect(page.getByRole('heading', { name: '核对申请并登记' })).toBeVisible();
  await expect(page.getByLabel('登记患者摘要')).toContainText(applicationNo);
  await page.getByRole('button', { name: '← 返回工作台' }).click();
  await expect(page).toHaveURL(/\/v2\/workbench/);
  await expectNoPageOverflow(page);
});

test('FC02A HIS 未找到后允许人工补录并显示业务提示', async ({ page }, testInfo) => {
  await login(page, 'registrar');
  await openNewApplication(page);

  await page.getByRole('textbox', { name: '患者查询号' }).fill('UNKNOWN');
  await page.getByRole('button', { name: '从 HIS 查询' }).click();
  await expect(page.getByRole('status')).toContainText('未查询到患者信息，可人工补录');
  await page.getByRole('textbox', { name: '患者姓名' }).fill('合成人工患者');
  await page.getByRole('combobox', { name: '性别' }).selectOption('FEMALE');
  await page.getByRole('spinbutton', { name: '年龄（无出生日期时）' }).fill('6');
  await page.getByRole('combobox', { name: '年龄单位' }).selectOption('MONTH');
  await page.getByRole('textbox', { name: '患者标识' }).fill(`MANUAL-${Date.now()}`);
  await page.getByRole('textbox', { name: '门诊号 / 住院号' }).fill(`MZ-MANUAL-${Date.now()}`);
  await page
    .getByRole('textbox', { name: '申请号' })
    .fill(`MANUAL-FC02A-${testInfo.project.name}-${Date.now()}`);
  await page.getByRole('textbox', { name: '申请科室' }).fill('合成门诊科室');
  await page.getByRole('combobox', { name: '申请项目' }).selectOption('SYNTH-CYTOLOGY');
  await page.getByRole('textbox', { name: '标本名称 / 部位' }).fill('合成细胞标本');
  await page.getByRole('button', { name: '保存申请' }).click();
  await expect(page.getByRole('heading', { name: '核对申请并登记' })).toBeVisible();
});

test('FC02A 住院多项目可部分登记并保留剩余项目', async ({ page }, testInfo) => {
  await login(page, 'registrar');
  await openNewApplication(page);

  const suffix = `${testInfo.project.name}-${Date.now()}`;
  await page.getByRole('combobox', { name: '就诊类型' }).selectOption('INPATIENT');
  await page.getByRole('textbox', { name: '患者姓名' }).fill('合成住院患者');
  await page.getByRole('combobox', { name: '性别' }).selectOption('MALE');
  await page.getByRole('spinbutton', { name: '年龄（无出生日期时）' }).fill('45');
  await page.getByRole('textbox', { name: '患者标识' }).fill(`PAT-IP-${suffix}`);
  await page.getByRole('textbox', { name: '门诊号 / 住院号' }).fill(`ZY-${suffix}`);
  await page.getByRole('textbox', { name: '申请号' }).fill(`IP-FC02A-${suffix}`);
  await page.getByRole('textbox', { name: '申请科室' }).fill('合成住院病区');
  await page.getByRole('combobox', { name: '申请项目' }).selectOption('SYNTH-HISTOLOGY');
  await page.getByRole('textbox', { name: '标本名称 / 部位' }).fill('合成组织标本');
  await page.getByRole('button', { name: '+ 新增申请项目' }).click();
  await page.getByRole('combobox', { name: '申请项目' }).nth(1).selectOption('SYNTH-CYTOLOGY');
  await page.getByRole('textbox', { name: '标本名称 / 部位' }).nth(1).fill('合成细胞标本');
  await page.getByRole('button', { name: '保存申请' }).click();

  await expect(page.getByRole('heading', { name: '核对申请并登记' })).toBeVisible();
  await expect(page.locator('.registration-item-line')).toHaveCount(2);
  await checkAllVerificationFacts(page);
  await page.getByRole('button', { name: '登记', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('已创建 1 个独立病例');
  await expect(page.locator('.registration-item-line.registered')).toHaveCount(1);
  await expect(page.locator('.registration-item-line.pending')).toHaveCount(1);
  const firstCaseNo = await page.locator('.registration-complete-actions span').innerText();
  expect(firstCaseNo).toMatch(/^H-/);

  await page.getByRole('button', { name: '登记并返回工作台' }).click();
  await expect(page).toHaveURL(/\/v2\/workbench/);
  await page.getByRole('searchbox', { name: '关键词' }).fill(`IP-FC02A-${suffix}`);
  const remainingWork = page.locator('.workbench-dense-row', { hasText: `IP-FC02A-${suffix}` });
  await expect(remainingWork).toHaveCount(1);
  await remainingWork.click();
  await expect(page.getByRole('heading', { name: '核对申请并登记' })).toBeVisible();
  await expect(page.locator('.registration-item-line.registered')).toHaveCount(1);
  await expect(page.locator('.registration-item-line.pending')).toHaveCount(1);
  await checkAllVerificationFacts(page);
  await page.getByRole('button', { name: '登记', exact: true }).click();
  await expect(page.locator('.registration-item-line.registered')).toHaveCount(2);
  await expect(page.locator('.registration-item-line.pending')).toHaveCount(0);
  const secondCaseNo = await page.locator('.registration-complete-actions span').innerText();
  expect(secondCaseNo).toMatch(/^C-/);
  expect(secondCaseNo).not.toBe(firstCaseNo);
});

test('FC02A 拒收必须有原因且不会创建病例', async ({ page }, testInfo) => {
  await login(page, 'registrar');
  const application = await createApplicationFixture(
    page,
    `RJ-FC02A-${testInfo.project.name}-${Date.now()}`,
    [{ code: 'SYNTH-HISTOLOGY', name: '常规组织病理', specimen: '合成不合格标本' }],
  );
  await page.goto(
    `/v2/registration?applicationId=${application.applicationId}&applicationItemId=${application.items[0]!.itemId}`,
  );
  await expect(page.getByRole('heading', { name: '核对申请并登记' })).toBeVisible();
  await page.getByRole('button', { name: '拒收' }).click();
  await expect(page.getByRole('alert')).toContainText('请填写拒收原因说明');
  await page.getByPlaceholder('拒收原因说明（必填）').fill('合成容器破损');
  await page.getByRole('combobox', { name: '拒收原因' }).selectOption('CONTAINER_DAMAGED');
  await page.getByRole('button', { name: '拒收' }).click();
  await expect(page.getByRole('status')).toContainText('未创建病例和病理号');
  await expect(page.locator('.registration-item-line.rejected')).toContainText('已拒收');

  const applicationResponse = await expectOk(
    await page.request.get(`/api/v2/applications/${application.applicationId}`),
  );
  const applicationBody = (await applicationResponse.json()) as CreatedApplication;
  expect(applicationBody.items[0]!.statusCode).toBe('REJECTED');
});

test('FC02A 批量送检标签打印与重打记录稳定', async ({ page }, testInfo) => {
  await login(page, 'registrar');
  const application = await createApplicationFixture(
    page,
    `PRINT-FC02A-${testInfo.project.name}-${Date.now()}`,
    [
      { code: 'SYNTH-HISTOLOGY', name: '常规组织病理', specimen: '合成标本 A' },
      { code: 'SYNTH-CYTOLOGY', name: '细胞病理', specimen: '合成标本 B' },
    ],
  );
  await page.goto(
    `/v2/registration?applicationId=${application.applicationId}&applicationItemId=${application.items[0]!.itemId}`,
  );
  await expect(page.getByRole('heading', { name: '核对申请并登记' })).toBeVisible();
  await page.locator('.registration-item-line.pending').nth(1).getByRole('checkbox').check();
  await page.getByRole('button', { name: '打印所选送检标签' }).click();
  await expect(page.getByRole('status')).toContainText('已提交 2 个送检标签打印');
  await expect(page.getByText(/打印记录 2 条/)).toBeVisible();
  await page.getByRole('button', { name: '打印所选送检标签' }).click();
  await expect(page.getByText(/打印记录 4 条/)).toBeVisible();

  const history = await expectOk(
    await page.request.get(
      `/api/v2/applications/${application.applicationId}/barcode-print-history`,
    ),
  );
  const body = (await history.json()) as Array<{ operationCode: string; barcode: string }>;
  expect(body.map((item) => item.operationCode)).toEqual(['PRINT', 'PRINT', 'REPRINT', 'REPRINT']);
  expect(body.map((item) => item.barcode)).toEqual([
    `${application.applicationNo}-1`,
    `${application.applicationNo}-2`,
    `${application.applicationNo}-1`,
    `${application.applicationNo}-2`,
  ]);

  await page.goto('/v2/registration');
  await expect(page.getByRole('heading', { name: '待登记申请' })).toBeVisible();
  await page.getByText('送检扫码与记录').click();
  await page.getByRole('textbox', { name: '送检条码' }).fill(`${application.applicationNo}-1`);
  await page.getByRole('button', { name: '查找申请' }).click();
  await expect(page.locator('.delivery-scan-result')).toContainText(application.applicationNo);
  await expect(page.locator('.delivery-scan-result')).toContainText('待核对确认');
});

test('FC02A 正式标本标签可重打且门诊回执可打印', async ({ page }, testInfo) => {
  await login(page, 'registrar');
  const application = await createApplicationFixture(
    page,
    `RECEIPT-FC02A-${testInfo.project.name}-${Date.now()}`,
    [{ code: 'SYNTH-HISTOLOGY', name: '常规组织病理', specimen: '合成门诊标本' }],
  );
  await page.goto(
    `/v2/registration?applicationId=${application.applicationId}&applicationItemId=${application.items[0]!.itemId}`,
  );
  await checkAllVerificationFacts(page);
  await page.getByRole('button', { name: '登记', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('已创建 1 个独立病例');

  await page.getByRole('button', { name: '打印正式标本标签' }).click();
  await expect(page.getByRole('status')).toContainText('已提交 1 个正式标本标签打印');
  await page.getByRole('button', { name: '打印正式标本标签' }).click();
  await expect(page.getByRole('status')).toContainText('已提交 1 个正式标本标签打印');
  await page.getByRole('button', { name: '打印门诊回执' }).click();
  await expect(page.getByRole('status')).toContainText('门诊回执打印成功');
});

test('FC02A 病理号更正保留历史，病例取消后仍可查询', async ({ page }, testInfo) => {
  await login(page, 'registrar');
  const suffix = `${testInfo.project.name}-${Date.now()}`;
  const application = await createApplicationFixture(page, `CASE-FC02A-${suffix}`, [
    { code: 'SYNTH-HISTOLOGY', name: '常规组织病理', specimen: '合成病例标本' },
  ]);
  const registered = await registerItem(page, application, 0);
  const originalNo = registered.caseNo;
  const correctedNo = `HX-${suffix}`.replaceAll(/[^A-Za-z0-9-]/g, '');

  await page.goto(`/v2/cases/${registered.caseId}`);
  await expect(page.getByLabel('病例中心')).toContainText(originalNo);
  await page.locator('details.case-more-actions').click();
  await page.getByRole('button', { name: '更正病理号' }).click();
  await page.getByLabel('更正病理号').getByRole('textbox', { name: '新病理号' }).fill(correctedNo);
  await page
    .getByLabel('更正病理号')
    .getByRole('textbox', { name: '纠正原因' })
    .fill('合成录入纠正');
  await page.getByLabel('更正病理号').getByRole('button', { name: '确认更正' }).click();
  await expect(page.getByRole('status')).toContainText('病例身份与历史材料保持不变');
  await expect(page.getByLabel('病例中心')).toContainText(correctedNo);
  await expect(page.getByLabel('最近动态')).toContainText(`病理号：${originalNo} → ${correctedNo}`);

  const duplicateApplication = await createApplicationFixture(page, `DUP-FC02A-${suffix}`, [
    { code: 'SYNTH-HISTOLOGY', name: '常规组织病理', specimen: '合成占号标本' },
  ]);
  const duplicateCase = await registerItem(page, duplicateApplication, 0);
  await page.locator('details.case-more-actions').click();
  await page.getByRole('button', { name: '更正病理号' }).click();
  await page
    .getByLabel('更正病理号')
    .getByRole('textbox', { name: '新病理号' })
    .fill(duplicateCase.caseNo);
  await page
    .getByLabel('更正病理号')
    .getByRole('textbox', { name: '纠正原因' })
    .fill('合成重复号冲突');
  await page.getByLabel('更正病理号').getByRole('button', { name: '确认更正' }).click();
  await expect(page.getByRole('alert')).toContainText('已被其他有效病例使用');
  await expect(page.getByLabel('病例中心')).toContainText(correctedNo);
  await page.getByLabel('更正病理号').getByRole('button', { name: '关闭' }).click();

  await page.locator('details.case-more-actions').click();
  await page.getByRole('button', { name: '取消病例' }).click();
  await page.getByLabel('取消病例').getByRole('textbox', { name: '取消原因' }).fill('合成重复病例');
  await page.getByLabel('取消病例').getByRole('button', { name: '确认取消病例' }).click();
  await expect(page.getByRole('status')).toContainText('历史记录仍可查询');
  await expect(page.getByLabel('病例固定上下文')).toContainText('已取消');

  const oldSearch = await expectOk(await page.request.get(`/api/v2/search?q=${originalNo}`));
  const newSearch = await expectOk(await page.request.get(`/api/v2/search?q=${correctedNo}`));
  expect(JSON.stringify(await oldSearch.json())).toContain(registered.caseId);
  expect(JSON.stringify(await newSearch.json())).toContain(registered.caseId);
  const caseResponse = await expectOk(
    await page.request.get(`/api/v2/registration/cases/${registered.caseId}`),
  );
  expect((await caseResponse.json()).lifecycleStateCode).toBe('CANCELLED');
});

test('FC02A 无授权用户不能直接更正病理号或取消病例', async ({ page }, testInfo) => {
  await login(page, 'registrar');
  const application = await createApplicationFixture(
    page,
    `PERM-FC02A-${testInfo.project.name}-${Date.now()}`,
    [{ code: 'SYNTH-HISTOLOGY', name: '常规组织病理', specimen: '合成权限标本' }],
  );
  const registered = await registerItem(page, application, 0);
  await page.request.post('/api/v2/auth/logout');
  await page.goto('/v2/workbench');
  await login(page, 'technician');

  const correction = await page.request.post(
    `/api/v2/registration/cases/${registered.caseId}/pathology-number`,
    { data: { newPathologyNo: `DENIED-${Date.now()}`, reason: '无权操作', expectedVersion: 0 } },
  );
  const cancellation = await page.request.post(
    `/api/v2/registration/cases/${registered.caseId}/cancel`,
    { data: { reason: '无权操作', expectedVersion: 0 } },
  );
  expect(correction.status()).toBe(403);
  expect(cancellation.status()).toBe(403);
});
