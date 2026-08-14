import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';

import V2ConfigurationHub from './V2ConfigurationHub.vue';

const configuration = {
  businessTypes: [],
  applicationItemMappings: [],
  pathologyNumberRules: [],
  technicalProjects: [],
  diagnosisTemplates: [],
  reportTemplates: [],
  reportTatPolicies: [],
};

describe('V2ConfigurationHub', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('creates and edits scoped subspecialty assignment rules with a daily limit', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/configuration')) return new Response(JSON.stringify(configuration));
      if (url.endsWith('/custody/locations')) return new Response(JSON.stringify([]));
      if (url.endsWith('/assignment-rules') && !init?.method) {
        return new Response(JSON.stringify([]));
      }
      if (url.endsWith('/report-template-presets')) return new Response(JSON.stringify([]));
      if (url.endsWith('/report-templates') && !init?.method) {
        return new Response(JSON.stringify([]));
      }
      if (url.endsWith('/assignment-rules') && init?.method === 'POST') {
        const body = JSON.parse(init.body as string);
        return new Response(
          JSON.stringify({ assignmentRuleId: 'RULE-1', version: 0, duplicate: false, ...body }),
        );
      }
      throw new Error(`Unexpected request: ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);

    const wrapper = mount(V2ConfigurationHub);
    await flushPromises();
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '自动分诊规则')
      ?.trigger('click');
    const requiredInputs = wrapper.findAll('input[required]');
    await requiredInputs[4]?.setValue('GI');
    await requiredInputs[5]?.setValue('doctor-a');
    const numericInputs = wrapper.findAll('input[type="number"]');
    await numericInputs[1]?.setValue(12);
    await wrapper.find('form').trigger('submit');
    await flushPromises();

    const createCall = fetchMock.mock.calls.find(
      ([input, init]) => String(input).endsWith('/assignment-rules') && init?.method === 'POST',
    );
    expect(createCall).toBeTruthy();
    expect(JSON.parse(createCall?.[1]?.body as string)).toMatchObject({
      businessTypeCode: 'HISTOLOGY',
      diagnosisGroup: 'GI',
      doctorId: 'doctor-a',
      dailyCaseLimit: 12,
      idempotencyKey: expect.any(String),
    });
    expect(wrapper.text()).toContain('自动分诊规则已创建');
  });

  it('designs, versions and publishes a structured report template', async () => {
    const definition = JSON.stringify({
      schemaVersion: 1,
      title: '合成常规病理报告',
      category: 'GENERAL',
      page: { size: 'A4', showPageNumber: true },
      sections: [
        {
          code: 'DIAGNOSIS',
          label: '病理诊断',
          source: 'DIAGNOSIS',
          fields: ['diagnosisText'],
        },
      ],
    });
    let catalog = [
      {
        templateId: 'REPORT-TEMPLATE-1',
        code: 'SYNTH-REPORT',
        name: '合成报告模板',
        businessTypeId: 'BT-1',
        businessTypeCode: 'HISTOLOGY',
        businessTypeName: '常规组织病理',
        enabled: true,
        configurationVersion: 1,
        versionId: 'REPORT-VERSION-1',
        versionNo: 1,
        definition,
        status: 'PUBLISHED',
      },
    ];
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/configuration')) {
        return new Response(
          JSON.stringify({
            ...configuration,
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
          }),
        );
      }
      if (url.endsWith('/custody/locations') || url.endsWith('/assignment-rules')) {
        return new Response(JSON.stringify([]));
      }
      if (url.endsWith('/report-template-presets')) return new Response(JSON.stringify([]));
      if (url.endsWith('/report-templates') && !init?.method) {
        return new Response(JSON.stringify(catalog));
      }
      if (url.endsWith('/report-templates/REPORT-TEMPLATE-1/versions')) {
        const body = JSON.parse(init?.body as string);
        catalog = [
          {
            ...catalog[0],
            versionId: 'REPORT-VERSION-2',
            versionNo: 2,
            definition: body.definition,
            status: 'DRAFT',
          },
          ...catalog,
        ];
        return new Response(
          JSON.stringify({
            versionId: 'REPORT-VERSION-2',
            templateId: 'REPORT-TEMPLATE-1',
            versionNo: 2,
            definition: body.definition,
            status: 'DRAFT',
          }),
        );
      }
      if (url.endsWith('/report-template-versions/REPORT-VERSION-2/publish')) {
        return new Response(
          JSON.stringify({
            versionId: 'REPORT-VERSION-2',
            status: 'PUBLISHED',
            publishedAt: '2026-08-14T00:00:00Z',
          }),
        );
      }
      throw new Error(`Unexpected request: ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);

    const wrapper = mount(V2ConfigurationHub);
    await flushPromises();
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '报告模板')
      ?.trigger('click');
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('合成报告模板'))
      ?.trigger('click');
    await wrapper.find('input[aria-label="报告标题"]').setValue('合成肿瘤报告');
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '+ 添加版块')
      ?.trigger('click');
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '保存新草稿')
      ?.trigger('click');
    await flushPromises();

    const versionCall = fetchMock.mock.calls.find(([input]) =>
      String(input).endsWith('/report-templates/REPORT-TEMPLATE-1/versions'),
    );
    const savedDefinition = JSON.parse(JSON.parse(versionCall?.[1]?.body as string).definition);
    expect(savedDefinition).toMatchObject({
      schemaVersion: 1,
      title: '合成肿瘤报告',
      page: { size: 'A4', showPageNumber: true },
    });
    expect(savedDefinition.sections).toHaveLength(2);
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '发布当前草稿')
      ?.trigger('click');
    await flushPromises();
    expect(
      fetchMock.mock.calls.some(([input]) =>
        String(input).endsWith('/report-template-versions/REPORT-VERSION-2/publish'),
      ),
    ).toBe(true);
    expect(wrapper.text()).toContain('报告模板版本已发布');
  });

  it('copies a common tumor preset into a hospital-scoped draft', async () => {
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
          fields: ['diagnosisText'],
        },
      ],
    });
    let catalog: unknown[] = [];
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/configuration')) {
        return new Response(
          JSON.stringify({
            ...configuration,
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
          }),
        );
      }
      if (url.endsWith('/custody/locations') || url.endsWith('/assignment-rules')) {
        return new Response(JSON.stringify([]));
      }
      if (url.endsWith('/report-template-presets')) {
        return new Response(
          JSON.stringify([
            {
              presetCode: 'TUMOR-LUNG',
              presetName: '肺肿瘤报告结构',
              tumorSiteCode: 'LUNG',
              definition: presetDefinition,
              presetVersion: 1,
            },
          ]),
        );
      }
      if (url.endsWith('/report-templates') && !init?.method) {
        return new Response(JSON.stringify(catalog));
      }
      if (url.endsWith('/report-template-presets/TUMOR-LUNG/instantiate')) {
        catalog = [
          {
            templateId: 'TUMOR-REPORT-1',
            code: 'LOCAL-LUNG',
            name: '本院肺肿瘤报告',
            businessTypeId: 'BT-1',
            businessTypeCode: 'HISTOLOGY',
            businessTypeName: '常规组织病理',
            enabled: true,
            configurationVersion: 1,
            sourcePresetCode: 'TUMOR-LUNG',
            versionId: 'TUMOR-REPORT-V1',
            versionNo: 1,
            definition: presetDefinition,
            status: 'DRAFT',
          },
        ];
        return new Response(
          JSON.stringify({
            template: { templateId: 'TUMOR-REPORT-1', code: 'LOCAL-LUNG', name: '本院肺肿瘤报告' },
            version: {
              versionId: 'TUMOR-REPORT-V1',
              versionNo: 1,
              definition: presetDefinition,
              status: 'DRAFT',
            },
          }),
        );
      }
      throw new Error(`Unexpected request: ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);

    const wrapper = mount(V2ConfigurationHub);
    await flushPromises();
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '报告模板')
      ?.trigger('click');
    const createPanel = wrapper.get('[aria-label="新建报告模板"]');
    await createPanel.find('input[placeholder="例如 TUMOR-LUNG-LOCAL"]').setValue('LOCAL-LUNG');
    await createPanel.find('input[placeholder="例如 肺肿瘤专科报告"]').setValue('本院肺肿瘤报告');
    await createPanel.find('select[aria-label="常用肿瘤模板"]').setValue('TUMOR-LUNG');
    await createPanel
      .findAll('button')
      .find((button) => button.text() === '从肿瘤结构创建草稿')
      ?.trigger('click');
    await flushPromises();

    const instantiateCall = fetchMock.mock.calls.find(([input]) =>
      String(input).endsWith('/report-template-presets/TUMOR-LUNG/instantiate'),
    );
    expect(JSON.parse(instantiateCall?.[1]?.body as string)).toEqual({
      code: 'LOCAL-LUNG',
      name: '本院肺肿瘤报告',
      businessTypeId: 'BT-1',
    });
    expect(wrapper.get('input[aria-label="报告标题"]').element).toHaveProperty(
      'value',
      '肺肿瘤病理报告',
    );
    expect(wrapper.text()).toContain('完成业务审核后发布');
  });

  it('configures report TAT thresholds only after explicit hospital input', async () => {
    let current = {
      ...configuration,
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
      reportTatPolicies: [
        {
          businessTypeId: 'BT-1',
          businessTypeCode: 'HISTOLOGY',
          businessTypeName: '常规组织病理',
          startAnchorCode: 'CASE_REGISTERED',
          enabled: false,
          configurationVersion: 0,
        },
      ],
    };
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/configuration') && !init?.method) {
        return new Response(JSON.stringify(current));
      }
      if (url.endsWith('/custody/locations') || url.endsWith('/assignment-rules')) {
        return new Response(JSON.stringify([]));
      }
      if (url.endsWith('/report-template-presets') || url.endsWith('/report-templates')) {
        return new Response(JSON.stringify([]));
      }
      if (url.endsWith('/configuration/tat-policies/BT-1') && init?.method === 'PUT') {
        const body = JSON.parse(init.body as string);
        current = {
          ...current,
          reportTatPolicies: [
            {
              ...current.reportTatPolicies[0],
              ...body,
              configurationVersion: 1,
            },
          ],
        };
        return new Response(JSON.stringify(current));
      }
      throw new Error(`Unexpected request: ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);

    const wrapper = mount(V2ConfigurationHub);
    await flushPromises();
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '报告时效策略')
      ?.trigger('click');
    const policy = wrapper.get('[aria-label="报告时效策略配置"]');
    const inputs = policy.findAll('input[type="number"]');
    await inputs[0].setValue(2880);
    await inputs[1].setValue(4320);
    await policy.get('input[type="checkbox"]').setValue(true);
    await policy
      .findAll('button')
      .find((button) => button.text() === '保存策略')
      ?.trigger('click');
    await flushPromises();

    const saveCall = fetchMock.mock.calls.find(([input]) =>
      String(input).endsWith('/configuration/tat-policies/BT-1'),
    );
    expect(JSON.parse(saveCall?.[1]?.body as string)).toEqual({
      warningMinutes: 2880,
      targetMinutes: 4320,
      enabled: true,
      expectedVersion: 0,
    });
    expect(wrapper.text()).toContain('报告时效策略已保存');
  });
});
