import { expect, test } from '@playwright/test';

import { expectNoPageOverflow } from './helpers';

const presetDefinition = JSON.stringify({
  schemaVersion: 1,
  title: '肺肿瘤病理报告',
  category: 'TUMOR',
  tumorSiteCode: 'LUNG',
  page: { size: 'A4', showPageNumber: true },
  sections: [
    {
      code: 'DIAGNOSIS',
      label: '病理诊断',
      source: 'DIAGNOSIS',
      fields: ['diagnosisText', 'structuredData'],
    },
  ],
});

test('管理员从常用肿瘤结构创建、设计并发布报告模板版本', async ({ page }) => {
  let catalog: Record<string, unknown>[] = [];
  let savedDefinition = '';
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
          permissions: ['P14-PERM-042'],
        },
      });
    }
    if (path.endsWith('/operations/notifications')) return route.fulfill({ json: [] });
    if (path.endsWith('/configuration')) {
      return route.fulfill({
        json: {
          businessTypes: [
            {
              id: '00000000-0000-0000-0000-00000000b001',
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
        },
      });
    }
    if (path.endsWith('/custody/locations') || path.endsWith('/diagnoses/assignment-rules')) {
      return route.fulfill({ json: [] });
    }
    if (path.endsWith('/report-template-presets')) {
      return route.fulfill({
        json: [
          {
            presetCode: 'TUMOR-LUNG',
            presetName: '肺肿瘤报告结构',
            tumorSiteCode: 'LUNG',
            definition: presetDefinition,
            presetVersion: 1,
          },
        ],
      });
    }
    if (path.endsWith('/report-templates') && request.method() === 'GET') {
      return route.fulfill({ json: catalog });
    }
    if (path.endsWith('/report-template-presets/TUMOR-LUNG/instantiate')) {
      catalog = [
        {
          templateId: 'TEMPLATE-1',
          code: 'LOCAL-LUNG',
          name: '本院肺肿瘤报告',
          businessTypeId: '00000000-0000-0000-0000-00000000b001',
          businessTypeCode: 'HISTOLOGY',
          businessTypeName: '常规组织病理',
          enabled: true,
          configurationVersion: 1,
          sourcePresetCode: 'TUMOR-LUNG',
          versionId: 'VERSION-1',
          versionNo: 1,
          definition: presetDefinition,
          status: 'DRAFT',
        },
      ];
      return route.fulfill({
        json: {
          template: { templateId: 'TEMPLATE-1', code: 'LOCAL-LUNG', name: '本院肺肿瘤报告' },
          version: {
            versionId: 'VERSION-1',
            versionNo: 1,
            definition: presetDefinition,
            status: 'DRAFT',
          },
        },
      });
    }
    if (path.endsWith('/report-templates/TEMPLATE-1/versions')) {
      savedDefinition = request.postDataJSON().definition;
      catalog = [
        { ...catalog[0], versionId: 'VERSION-2', versionNo: 2, definition: savedDefinition },
      ];
      return route.fulfill({
        json: {
          versionId: 'VERSION-2',
          templateId: 'TEMPLATE-1',
          versionNo: 2,
          definition: savedDefinition,
          status: 'DRAFT',
        },
      });
    }
    if (path.endsWith('/report-template-versions/VERSION-2/publish')) {
      catalog = [{ ...catalog[0], status: 'PUBLISHED' }];
      return route.fulfill({
        json: {
          versionId: 'VERSION-2',
          status: 'PUBLISHED',
          publishedAt: '2026-08-14T00:00:00Z',
        },
      });
    }
    return route.fulfill({ json: {} });
  });

  await page.goto('/v2/configuration');
  await page.getByRole('button', { name: '报告模板', exact: true }).click();
  const createPanel = page.getByLabel('新建报告模板');
  await createPanel.getByLabel('模板编码').fill('LOCAL-LUNG');
  await createPanel.getByLabel('模板名称').fill('本院肺肿瘤报告');
  await createPanel.getByLabel('常用肿瘤模板').selectOption('TUMOR-LUNG');
  await createPanel.getByRole('button', { name: '从肿瘤结构创建草稿' }).click();

  const designer = page.getByLabel('报告模板设计器');
  await expect(designer.getByLabel('报告标题')).toHaveValue('肺肿瘤病理报告');
  await expect(page.getByText('完成业务审核后发布')).toBeVisible();
  await designer.getByLabel('报告标题').fill('本院肺肿瘤专科报告');
  await designer.getByRole('button', { name: '+ 添加版块' }).click();
  await designer.getByRole('button', { name: '保存新草稿' }).click();
  await expect(page.getByText('报告模板草稿 v2 已保存')).toBeVisible();
  expect(JSON.parse(savedDefinition)).toMatchObject({
    schemaVersion: 1,
    title: '本院肺肿瘤专科报告',
    category: 'TUMOR',
    tumorSiteCode: 'LUNG',
  });
  expect(JSON.parse(savedDefinition).sections).toHaveLength(2);
  await designer.getByRole('button', { name: '发布当前草稿' }).click();
  await expect(page.getByText('报告模板版本已发布')).toBeVisible();
  await expect(page.getByText('v2 PUBLISHED')).toBeVisible();
  await expectNoPageOverflow(page);
});
