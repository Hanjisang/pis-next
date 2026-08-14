import { expect, test } from '@playwright/test';

import { expectNoPageOverflow } from './helpers';

const tbsSchema = JSON.stringify({
  version: 2,
  standardCode: 'TBS-2014',
  components: [
    {
      code: 'specimenAdequacy',
      label: '标本满意度',
      type: 'SINGLE_SELECT',
      required: true,
      options: [
        { value: 'SATISFACTORY', label: '满意' },
        { value: 'UNSATISFACTORY', label: '不满意' },
      ],
    },
    {
      code: 'generalCategory',
      label: '总体分类',
      type: 'SINGLE_SELECT',
      required: true,
      options: [
        { value: 'NILM', label: '未见上皮内病变或恶性病变' },
        { value: 'EPITHELIAL_CELL_ABNORMALITY', label: '上皮细胞异常' },
      ],
    },
    {
      code: 'interpretationResult',
      label: '解释/结果',
      type: 'TEXTAREA',
      required: true,
    },
    {
      code: 'diagnosisText',
      label: '细胞学诊断',
      type: 'TEXTAREA',
      required: true,
    },
  ],
});

const workspace = {
  caseSummary: {
    caseId: 'CASE-CYTOLOGY',
    pathologyNo: 'CY-SYNTH-001',
    businessTypeCode: 'CYTOLOGY_GYN',
    lifecycle: 'ACTIVE',
  },
  application: {
    applicationItemCode: 'SYNTH-CYTOLOGY-GYN',
    sourceSystemCode: 'SYNTH-HIS',
    externalApplicationId: 'SYNTH-CYTOLOGY-APP',
  },
  patient: { patientReference: 'SYNTH-PATIENT', visitReference: 'SYNTH-VISIT' },
  materialTree: {
    caseId: 'CASE-CYTOLOGY',
    caseNo: 'CY-SYNTH-001',
    businessTypeCode: 'CYTOLOGY_GYN',
    specimens: [],
    initialRequiredCount: 1,
    initialCompletedCount: 1,
    initialProductionComplete: true,
  },
  digitalSlides: [],
  diagnosis: {
    diagnosisId: 'DIAGNOSIS-CYTOLOGY',
    templateVersionId: 'DTV-CYTOLOGY-V2',
    structuredData: '{}',
    microscopicDescription: '',
    diagnosisText: '',
    comment: '',
    version: 0,
    updatedAt: '2026-08-14T00:00:00Z',
  },
  templateVersion: {
    versionId: 'DTV-CYTOLOGY-V2',
    templateId: 'DT-CYTOLOGY',
    versionNo: 2,
    schemaDefinition: tbsSchema,
    status: 'PUBLISHED',
  },
  responsibilityChain: [
    {
      responsibilityId: 'RESP-CYTOLOGY',
      role: 'INITIAL',
      doctorId: 'doctor-a',
      sequence: 1,
      assignmentSource: 'SELF_CLAIM',
      acceptedAt: '2026-08-14T00:00:00Z',
      version: 0,
      current: true,
    },
  ],
  currentResponsibility: {
    responsibilityId: 'RESP-CYTOLOGY',
    role: 'INITIAL',
    doctorId: 'doctor-a',
    sequence: 1,
    assignmentSource: 'SELF_CLAIM',
    acceptedAt: '2026-08-14T00:00:00Z',
    version: 0,
    current: true,
  },
  actions: {
    canClaim: false,
    canAssign: false,
    canCompleteInitial: true,
    canCompleteReview: false,
    canCompleteAudit: false,
    canReassign: false,
    readyForSignOut: false,
    canCreateTechnicalOrder: false,
    canPreview: true,
    canSignOut: false,
    canWithdraw: false,
    canSupplement: false,
  },
  molecularResults: [],
  technicalOrders: [],
  blockingTechnicalOrderCount: 0,
  reports: [],
  availableReportTemplates: [
    {
      templateId: 'RT-CYTOLOGY',
      versionId: 'RTV-CYTOLOGY-V2',
      versionNo: 2,
      code: 'DEFAULT-REPORT-CYTOLOGY-GYN',
      name: '妇科细胞学报告',
    },
  ],
  blockingReasons: [],
  refreshedAt: '2026-08-14T00:00:00Z',
};

test('妇科细胞学使用版本化TBS结构录入并预览专科报告', async ({ page }) => {
  let savedStructuredData = '';
  await page.route('**/api/v2/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path.endsWith('/auth/config')) return route.fulfill({ json: { required: true } });
    if (path.endsWith('/auth/me')) {
      return route.fulfill({
        json: {
          userId: 'doctor-a',
          username: 'doctor-a',
          displayName: '合成医生',
          roleCode: 'DOCTOR',
          permissions: [],
          doctor: { id: 'doctor-a', doctorCode: 'D-A', displayName: '合成医生' },
        },
      });
    }
    if (path.endsWith('/operations/notifications')) return route.fulfill({ json: [] });
    if (path.endsWith('/auth/doctors')) return route.fulfill({ json: [] });
    if (path.endsWith('/diagnosis-workspaces/CASE-CYTOLOGY')) {
      return route.fulfill({ json: workspace });
    }
    if (path.endsWith('/case-workspaces/CASE-CYTOLOGY')) {
      return route.fulfill({ json: { timeline: [] } });
    }
    if (path.endsWith('/diagnoses/DIAGNOSIS-CYTOLOGY/content')) {
      savedStructuredData = request.postDataJSON().structuredData;
      return route.fulfill({ json: { ...workspace.diagnosis, version: 1 } });
    }
    if (path.endsWith('/diagnoses/DIAGNOSIS-CYTOLOGY/report-preview')) {
      return route.fulfill({
        json: {
          valid: true,
          blockingReasons: [],
          renderedContent: JSON.stringify({
            presentation: {
              title: '妇科细胞学报告',
              sections: [
                { code: 'CYTOLOGY', label: '细胞学结构化结果' },
                { code: 'DIAGNOSIS', label: '细胞学诊断' },
              ],
            },
            case: {
              pathologyNo: 'CY-SYNTH-001',
              patientReference: 'SYNTH-PATIENT',
              visitReference: 'SYNTH-VISIT',
              businessTypeCode: 'CYTOLOGY_GYN',
            },
            diagnosis: {
              structuredData: savedStructuredData,
              diagnosisText: '合成妇科细胞学诊断',
            },
            material: [],
            responsibility: [],
            technicalResults: [],
          }),
        },
      });
    }
    if (path.endsWith('/favorite')) {
      return route.fulfill({ json: { caseId: 'CASE-CYTOLOGY', favorite: false } });
    }
    if (
      path.includes('/case-support') ||
      path.includes('/patient-history') ||
      path.endsWith('/technical-projects')
    ) {
      return route.fulfill({ json: [] });
    }
    if (path.endsWith('/my-workbench')) {
      return route.fulfill({ json: { myWork: [], publicPool: [] } });
    }
    return route.fulfill({ json: {} });
  });

  await page.goto('/v2/diagnosis/CASE-CYTOLOGY');
  const editor = page.getByRole('complementary', { name: '诊断编辑', exact: true });
  const adequacy = editor.getByLabel('标本满意度');
  const category = editor.getByLabel('总体分类');
  await expect(adequacy).toBeVisible();
  await expect(category).toBeVisible();
  await adequacy.selectOption('SATISFACTORY');
  await category.selectOption('NILM');
  await editor.getByLabel('解释/结果').fill('合成TBS解释结果');
  await editor.getByLabel('细胞学诊断').fill('合成妇科细胞学诊断');
  await page.getByRole('button', { name: '保存', exact: true }).click();

  expect(JSON.parse(savedStructuredData)).toMatchObject({
    specimenAdequacy: 'SATISFACTORY',
    generalCategory: 'NILM',
    interpretationResult: '合成TBS解释结果',
    diagnosisText: '合成妇科细胞学诊断',
  });
  await page.getByRole('button', { name: '报告预览' }).click();
  const preview = page.getByLabel('报告预览');
  await expect(preview.getByText('妇科细胞学报告', { exact: true })).toBeVisible();
  await expect(preview.getByText('细胞学结构化结果', { exact: true })).toBeVisible();
  await expectNoPageOverflow(page);
});
