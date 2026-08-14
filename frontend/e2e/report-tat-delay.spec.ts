import { expect, test } from '@playwright/test';

import { expectNoPageOverflow } from './helpers';

test('管理员配置报告时效并在报告中心登记超期原因', async ({ page }) => {
  let policy = {
    businessTypeId: 'BT-1',
    businessTypeCode: 'HISTOLOGY',
    businessTypeName: '常规组织病理',
    startAnchorCode: 'CASE_REGISTERED',
    enabled: false,
    configurationVersion: 0,
  };
  let delayed = false;
  await page.route('**/api/v2/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path.endsWith('/auth/config')) return route.fulfill({ json: { required: true } });
    if (path.endsWith('/auth/me')) {
      return route.fulfill({
        json: {
          userId: 'SYNTH-ADMIN',
          username: 'synthetic-admin',
          displayName: '合成管理员',
          roleCode: 'ADMIN',
          permissions: ['P14-PERM-001', 'P14-PERM-036', 'P14-PERM-055'],
        },
      });
    }
    if (path.endsWith('/operations/notifications')) return route.fulfill({ json: [] });
    if (path.endsWith('/configuration') && request.method() === 'GET') {
      return route.fulfill({
        json: {
          businessTypes: [
            {
              id: 'BT-1',
              code: 'HISTOLOGY',
              displayName: '常规组织病理',
              modalityCode: 'HISTOLOGY',
              enabled: true,
              configurationVersion: 1,
            },
          ],
          applicationItemMappings: [],
          pathologyNumberRules: [],
          technicalProjects: [],
          diagnosisTemplates: [],
          reportTemplates: [],
          reportTatPolicies: [policy],
        },
      });
    }
    if (path.endsWith('/configuration/tat-policies/BT-1') && request.method() === 'PUT') {
      policy = { ...policy, ...request.postDataJSON(), configurationVersion: 1 };
      return route.fulfill({
        json: {
          businessTypes: [],
          applicationItemMappings: [],
          pathologyNumberRules: [],
          technicalProjects: [],
          diagnosisTemplates: [],
          reportTemplates: [],
          reportTatPolicies: [policy],
        },
      });
    }
    if (
      path.endsWith('/custody/locations') ||
      path.endsWith('/diagnoses/assignment-rules') ||
      path.endsWith('/report-template-presets') ||
      path.endsWith('/report-templates')
    ) {
      return route.fulfill({ json: [] });
    }
    if (path.endsWith('/report-center') && request.method() === 'GET') {
      return route.fulfill({
        json: {
          items: [
            {
              diagnosisId: 'DIAGNOSIS-1',
              caseId: 'CASE-1',
              pathologyNo: 'H-2026-001',
              patientReference: 'SYNTH-PATIENT',
              businessTypeCode: 'HISTOLOGY',
              queueCode: 'WAITING_SIGN',
              reportId: null,
              reportNo: null,
              statusCode: null,
              occurredAt: '2026-08-14T00:00:00Z',
              targetLabel: '待签发',
              tatStatus: 'OVERDUE',
              elapsedMinutes: 5000,
              warningAt: '2026-08-12T00:00:00Z',
              dueAt: '2026-08-13T00:00:00Z',
              policyVersion: 1,
              delay: delayed
                ? {
                    delayId: 'DELAY-1',
                    reasonCode: 'TECHNICAL_WORK',
                    reasonDetail: '等待合成技术结果',
                    expectedSignAt: '2026-08-16T00:00:00Z',
                    declaredAt: '2026-08-14T00:00:00Z',
                  }
                : null,
            },
          ],
          counts: {
            waitingSign: 1,
            signed: 0,
            withdrawn: 0,
            supplemental: 0,
            recentSigned: 0,
            warning: 0,
            overdue: 1,
            delayed: delayed ? 1 : 0,
          },
          refreshedAt: '2026-08-14T00:00:00Z',
        },
      });
    }
    if (path.endsWith('/report-center/delays') && request.method() === 'POST') {
      delayed = true;
      return route.fulfill({ json: { delayId: 'DELAY-1', duplicate: false } });
    }
    return route.fulfill({ json: {} });
  });

  await page.goto('/v2/configuration');
  await page.getByRole('button', { name: '报告时效策略', exact: true }).click();
  const policyPanel = page.getByLabel('报告时效策略配置');
  await policyPanel.getByLabel('常规组织病理提醒分钟数').fill('2880');
  await policyPanel.getByLabel('常规组织病理目标分钟数').fill('4320');
  await policyPanel.getByRole('checkbox').check();
  await policyPanel.getByRole('button', { name: '保存策略' }).click();
  await expect(page.getByText('报告时效策略已保存')).toBeVisible();

  await page.goto('/v2/reports');
  await page.getByRole('button', { name: /超期/ }).click();
  await expect(page.getByText('H-2026-001')).toBeVisible();
  await page.getByRole('button', { name: '登记延迟', exact: true }).click();
  const dialog = page.getByLabel('登记报告延迟');
  await dialog.getByLabel('原因说明').fill('等待合成技术结果');
  await dialog.getByRole('button', { name: '确认登记' }).click();
  await expect(page.getByText('关闭延迟')).toBeVisible();
  await expectNoPageOverflow(page);
});
