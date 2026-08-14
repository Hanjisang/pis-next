import { expect, test } from '@playwright/test';
import { expectNoPageOverflow } from './helpers';

test('分子工作台完成设备启动、结果录入与支持材料追溯', async ({ page }) => {
  let statusCode = 'REQUESTED';
  let completed = false;
  await page.route('**/api/v2/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path.endsWith('/auth/config')) return route.fulfill({ json: { required: true } });
    if (path.endsWith('/auth/me'))
      return route.fulfill({
        json: {
          userId: 'SYNTH-MOL',
          username: 'synthetic-mol',
          displayName: '合成分子技师',
          roleCode: 'TECHNICIAN',
          permissions: ['P14-PERM-014'],
        },
      });
    if (path.endsWith('/operations/notifications')) return route.fulfill({ json: [] });
    if (path.endsWith('/molecular/tests/test-1/start')) {
      statusCode = 'RUNNING';
      return route.fulfill({ json: { id: 'test-1', statusCode } });
    }
    if (path.endsWith('/molecular/tests/test-1/complete')) {
      statusCode = 'COMPLETED';
      completed = true;
      return route.fulfill({ json: { id: 'test-1', resultId: 'result-1' } });
    }
    if (path.endsWith('/molecular/tests/test-1/attachments'))
      return route.fulfill({ json: { id: 'attachment-1' } });
    if (path.endsWith('/molecular/workbench'))
      return route.fulfill({
        json: {
          refreshedAt: '2026-08-14T00:00:00Z',
          projects: [
            {
              id: 'project-1',
              projectCode: 'PCR',
              projectName: '合成PCR',
              projectTypeCode: 'PCR',
              enabled: true,
            },
          ],
          instruments: [
            {
              id: 'instrument-1',
              instrumentCode: 'SIM-PCR',
              name: '合成PCR仪',
              adapterCode: 'SIMULATOR',
              enabled: true,
            },
          ],
          reagents: [
            {
              id: 'reagent-1',
              kitCode: 'PCR-KIT',
              manufacturer: 'SYNTH',
              batchNo: 'B-001',
              expiryDate: '2027-12-31',
              enabled: true,
            },
          ],
          tests: [
            {
              id: 'test-1',
              caseId: 'case-1',
              specimenId: 'specimen-1',
              projectId: 'project-1',
              projectCode: 'PCR',
              detectionNo: 'M-001',
              instrumentId: 'instrument-1',
              instrumentCode: 'SIM-PCR',
              adapterCode: 'SIMULATOR',
              reagentKitId: 'reagent-1',
              rawDataReference: 'fixture://raw',
              structuredResult: completed ? '合成结构化结果' : null,
              analysisResult: completed ? '合成分析结果' : null,
              statusCode,
              resultId: completed ? 'result-1' : null,
              createdAt: '2026-08-14T00:00:00Z',
              startedAt: statusCode === 'REQUESTED' ? null : '2026-08-14T00:01:00Z',
              completedAt: completed ? '2026-08-14T00:02:00Z' : null,
              concurrencyVersion: completed ? 2 : statusCode === 'RUNNING' ? 1 : 0,
            },
          ],
          attempts:
            statusCode === 'REQUESTED'
              ? []
              : [
                  {
                    id: 'attempt-1',
                    testId: 'test-1',
                    instrumentId: 'instrument-1',
                    adapterCode: 'SIMULATOR',
                    attemptNo: 1,
                    requestReference: 'request-1',
                    statusCode: 'ACCEPTED',
                    responseReference: 'SIM-RUN-test-1',
                    errorCode: null,
                    errorMessage: null,
                    requestedAt: '2026-08-14T00:01:00Z',
                    completedAt: '2026-08-14T00:01:00Z',
                    requestedBy: 'SYNTH-MOL',
                  },
                ],
          attachments: [
            {
              id: 'attachment-1',
              testId: 'test-1',
              digitalSlideId: null,
              attachmentReference: 'fixture://support.pdf',
              description: '合成附件',
              createdAt: '2026-08-14T00:00:00Z',
              createdBy: 'SYNTH-MOL',
            },
          ],
        },
      });
    return route.fulfill({ json: {} });
  });
  await page.goto('/v2/molecular');
  await expect(page.getByRole('heading', { name: '分子病理工作台' })).toBeVisible();
  await expect(page.getByText('M-001 · PCR')).toBeVisible();
  await page.getByRole('button', { name: '启动' }).click();
  await expect(page.getByText('SIM-RUN-test-1')).toBeVisible();
  const completion = page.locator('section.workspace-panel').filter({ hasText: '完成检测' });
  await completion.getByRole('combobox').selectOption('test-1');
  await completion.getByPlaceholder('结构化结果').fill('合成结构化结果');
  await completion.getByPlaceholder('分析结果').fill('合成分析结果');
  await completion.getByRole('button', { name: '完成并生成结果' }).click();
  await expect(page.getByText('结果已保存并进入诊断与报告链。')).toBeVisible();
  await expect(page.getByText(/合成结构化结果 · 合成分析结果/)).toBeVisible();
  await expect(page.getByText(/fixture:\/\/support.pdf/)).toBeVisible();
  await expectNoPageOverflow(page);
});
