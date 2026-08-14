import { expect, test } from '@playwright/test';

const workspace = {
  caseSummary: {
    caseId: 'CASE-1',
    pathologyNo: 'P-SYNTH-001',
    businessTypeCode: 'ROUTINE',
    lifecycle: 'ACTIVE',
  },
  application: {
    applicationItemCode: 'SYNTH-HISTOLOGY',
    sourceSystemCode: 'SYNTH-HIS',
    externalApplicationId: 'SYNTH-APP-1',
  },
  patient: { patientReference: 'SYNTH-PATIENT', visitReference: 'SYNTH-VISIT' },
  materialTree: {
    caseId: 'CASE-1',
    caseNo: 'P-SYNTH-001',
    businessTypeCode: 'ROUTINE',
    specimens: [],
    initialRequiredCount: 0,
    initialCompletedCount: 0,
    initialProductionComplete: true,
  },
  digitalSlides: [],
  diagnosis: {
    diagnosisId: 'DIAGNOSIS-1',
    templateVersionId: 'DTV-1',
    structuredData: '{}',
    microscopicDescription: '合成镜下所见',
    diagnosisText: '合成病理诊断',
    comment: '',
    version: 2,
    updatedAt: '2026-08-14T00:00:00Z',
  },
  templateVersion: {
    versionId: 'DTV-1',
    templateId: 'DT-1',
    versionNo: 1,
    schemaDefinition: '{"components":[]}',
    status: 'PUBLISHED',
  },
  responsibilityChain: [],
  actions: {
    canClaim: false,
    canAssign: false,
    canCompleteInitial: false,
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
  reports: [
    {
      reportId: '00000000-0000-0000-0000-000000000013',
      reportNo: 'R001',
      nature: 'ORIGINAL',
      supplemental: false,
      status: 'EFFECTIVE',
      templateVersionId: 'RTV-1',
      pdfFileReference: 'pis-v2/reports/R001.pdf',
      pdfContentHash: 'synthetic-pdf-hash',
      signedBy: 'doctor-a',
      signedAt: '2026-08-14T00:00:00Z',
    },
  ],
  blockingReasons: [],
  refreshedAt: '2026-08-14T00:00:00Z',
};

test('生效报告可生成口令加密副本且页面不回显密码', async ({ page }) => {
  let encryptedRequest: { accessPassword?: string; reason?: string } | undefined;
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
          permissions: ['P14-PERM-055'],
        },
      });
    }
    if (path.endsWith('/operations/notifications')) return route.fulfill({ json: [] });
    if (path.endsWith('/auth/doctors')) return route.fulfill({ json: [] });
    if (path.endsWith('/diagnosis-workspaces/CASE-1')) return route.fulfill({ json: workspace });
    if (path.endsWith('/pdf-encrypted')) {
      encryptedRequest = request.postDataJSON();
      return route.fulfill({
        status: 200,
        contentType: 'application/pdf',
        body: Buffer.from('%PDF-1.7 synthetic encrypted output'),
      });
    }
    if (path.endsWith('/favorite'))
      return route.fulfill({ json: { caseId: 'CASE-1', favorite: false } });
    if (
      path.includes('/case-support') ||
      path.includes('/patient-history') ||
      path.endsWith('/technical-projects')
    ) {
      return route.fulfill({ json: [] });
    }
    if (path.endsWith('/my-workbench'))
      return route.fulfill({ json: { myWork: [], publicPool: [] } });
    return route.fulfill({ json: {} });
  });

  await page.goto('/v2/diagnosis/CASE-1?focus=report');
  await expect(page.getByText('R001', { exact: false }).first()).toBeVisible();
  await page.getByRole('button', { name: '加密下载' }).click();
  const dialog = page.getByRole('dialog', { name: '加密下载报告' });
  await expect(dialog.getByText('访问密码仅用于本次 AES-256 加密')).toBeVisible();
  const password = dialog.getByLabel('访问密码（8–64字符）');
  await password.fill('synthetic-safe-2026');
  await dialog.getByLabel('下载用途').fill('合成对外提供');
  const download = page.waitForEvent('download');
  await dialog.getByRole('button', { name: '生成并下载' }).click();
  expect((await download).suggestedFilename()).toBe('R001-encrypted.pdf');
  expect(encryptedRequest).toEqual({
    accessPassword: 'synthetic-safe-2026',
    reason: '合成对外提供',
  });
  await expect(dialog).toBeHidden();
  await expect(page.getByText('口令加密PDF已生成并下载')).toBeVisible();
  await expect(page.getByText('synthetic-safe-2026')).toHaveCount(0);
});
