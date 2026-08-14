import { expect, test } from '@playwright/test';

import { expectNoPageOverflow } from './helpers';

test('临床人员查询生效报告并在患者终端核验后打印', async ({ page }) => {
  await page.route('**/api/v2/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path.endsWith('/auth/config')) return route.fulfill({ json: { required: true } });
    if (path.endsWith('/auth/me')) {
      return route.fulfill({
        json: {
          userId: 'SYNTH-REPORT-ACCESS',
          username: 'synthetic-report-access',
          displayName: '合成报告查询终端',
          roleCode: 'DOCTOR',
          permissions: ['P14-PERM-055'],
        },
      });
    }
    if (path.endsWith('/operations/notifications')) return route.fulfill({ json: [] });
    if (path.endsWith('/report-center') && request.method() === 'GET') {
      return route.fulfill({
        json: {
          items: [],
          counts: {
            waitingSign: 0,
            signed: 0,
            withdrawn: 0,
            supplemental: 0,
            recentSigned: 0,
            warning: 0,
            overdue: 0,
            delayed: 0,
          },
          refreshedAt: '2026-08-14T00:00:00Z',
        },
      });
    }
    if (path.endsWith('/report-center/access/clinician')) {
      return route.fulfill({
        json: [
          {
            reportId: 'REPORT-1',
            reportNo: 'R001',
            caseId: 'CASE-1',
            pathologyNo: 'H-2026-001',
            patientReference: 'SYNTH-PATIENT',
            reportNature: 'ORIGINAL',
            signedAt: '2026-08-14T00:00:00Z',
            pdfContentHash: 'abcdef0123456789abcdef0123456789',
          },
        ],
      });
    }
    if (path.endsWith('/report-center/access/patient')) {
      return route.fulfill({
        json: [
          {
            reportId: 'REPORT-1',
            reportNo: 'R001',
            caseId: 'CASE-1',
            pathologyNo: 'H-2026-001',
            reportNature: 'ORIGINAL',
            signedAt: '2026-08-14T00:00:00Z',
            pdfContentHash: 'abcdef0123456789abcdef0123456789',
          },
        ],
      });
    }
    if (path.endsWith('/operations/reports/REPORT-1/print')) {
      return route.fulfill({ json: { statusCode: 'SUCCESS', duplicate: false } });
    }
    return route.fulfill({ status: 404, json: { message: `Unhandled ${path}` } });
  });

  await page.goto('/v2/reports');
  await page.getByRole('button', { name: '临床查询', exact: true }).click();
  const clinicianForm = page.getByLabel('临床报告查询');
  await clinicianForm.getByLabel('病理号').fill('H-2026-001');
  await clinicianForm.getByRole('button', { name: '查询生效报告' }).click();
  await expect(page.getByText('SYNTH-PATIENT')).toBeVisible();
  await expect(page.getByRole('link', { name: '查看报告' })).toHaveAttribute(
    'href',
    '/api/v2/reports/REPORT-1/pdf',
  );

  await page.getByRole('button', { name: '患者自助终端', exact: true }).click();
  const terminal = page.getByLabel('患者报告自助查询');
  await terminal.getByLabel('报告号').fill('R001');
  await terminal.getByLabel('病理号').fill('H-2026-001');
  await terminal.getByLabel('身份核验凭据').fill('SYNTH-PATIENT');
  await terminal.getByLabel('终端标识').fill('TERMINAL-01');
  await terminal.getByRole('button', { name: '核验并查询' }).click();
  await page.getByRole('button', { name: '打印报告' }).click();
  await expect(page.getByText('报告已发送至打印机')).toBeVisible();
  await expectNoPageOverflow(page);
});
