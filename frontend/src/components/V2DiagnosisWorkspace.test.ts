import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';

import V2DiagnosisWorkspace from './V2DiagnosisWorkspace.vue';

const workspace = {
  caseSummary: {
    caseId: 'CASE-1',
    pathologyNo: 'P20260001',
    businessTypeCode: 'ROUTINE',
    lifecycle: 'ACTIVE',
  },
  application: {
    applicationItemCode: '胃镜活检',
    sourceSystemCode: 'SYNTH-HIS',
    externalApplicationId: 'APP-1',
  },
  patient: { patientReference: 'SYNTH-PATIENT-1', visitReference: 'SYNTH-VISIT-1' },
  materialTree: {
    caseId: 'CASE-1',
    caseNo: 'P20260001',
    businessTypeCode: 'ROUTINE',
    specimens: [
      {
        specimenId: 'S-1',
        specimenNo: 'PS-000001',
        specimenCode: 'A',
        specimenKindCode: 'TISSUE',
        blocks: [
          {
            blockId: 'B-1',
            blockCode: 'A1',
            blockType: 'ROUTINE',
            concurrencyVersion: 0,
            slides: [
              {
                slideId: 'L-1',
                slideCode: 'A1-HE',
                slideType: 'HE',
                sourceContextType: 'INITIAL',
                completed: true,
                required: true,
                concurrencyVersion: 1,
              },
            ],
          },
        ],
        directSlides: [],
      },
    ],
    initialRequiredCount: 1,
    initialCompletedCount: 1,
    initialProductionComplete: true,
  },
  digitalSlides: [],
  diagnosis: {
    diagnosisId: 'D-1',
    templateVersionId: 'TV-1',
    structuredData: '{}',
    microscopicDescription: '',
    diagnosisText: '',
    comment: '',
    version: 0,
    updatedAt: '2026-08-08T00:00:00Z',
  },
  templateVersion: {
    versionId: 'TV-1',
    templateId: 'T-1',
    versionNo: 1,
    schemaDefinition:
      '{"components":[{"code":"tumorType","label":"肿瘤类型","type":"SINGLE_SELECT","options":[{"value":"A","label":"A型"}]},{"code":"isPositive","label":"阳性","type":"BOOLEAN"}]}',
    status: 'PUBLISHED',
  },
  responsibilityChain: [
    {
      responsibilityId: 'R-1',
      role: 'INITIAL',
      doctorId: 'doctor-a',
      sequence: 1,
      assignmentSource: 'SELF_CLAIM',
      acceptedAt: '2026-08-08T00:00:00Z',
      version: 0,
      current: true,
    },
  ],
  currentResponsibility: {
    responsibilityId: 'R-1',
    role: 'INITIAL',
    doctorId: 'doctor-a',
    sequence: 1,
    assignmentSource: 'SELF_CLAIM',
    acceptedAt: '2026-08-08T00:00:00Z',
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
    canCreateTechnicalOrder: true,
    canPreview: true,
    canSignOut: false,
    canWithdraw: false,
    canSupplement: false,
  },
  molecularResults: [],
  technicalOrders: [],
  blockingTechnicalOrderCount: 0,
  reports: [],
  blockingReasons: [],
  refreshedAt: '2026-08-08T00:00:00Z',
};

const doctors = [
  { id: 'doctor-a', displayName: '张医生', title: '主治医师' },
  { id: 'doctor-b', displayName: '李医生', title: '副主任医师' },
];

function responseForWorkspace(input: RequestInfo | URL): Response {
  const url = String(input);
  if (url.includes('/auth/doctors')) return new Response(JSON.stringify(doctors));
  if (url.includes('/case-support') && url.endsWith('/favorite')) {
    return new Response(JSON.stringify({ caseId: 'CASE-1', favorite: false }));
  }
  if (url.includes('/case-support') && url.endsWith('/follow-ups')) {
    return new Response(JSON.stringify([]));
  }
  if (url.includes('/case-support') && url.endsWith('/consultations')) {
    return new Response(JSON.stringify([]));
  }
  return new Response(JSON.stringify(workspace));
}

describe('V2DiagnosisWorkspace', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('keeps case materials, diagnosis, responsibility and reports in one workspace', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => responseForWorkspace(input)),
    );

    const wrapper = mount(V2DiagnosisWorkspace, {
      props: {
        caseId: 'CASE-1',
        authUser: {
          userId: 'user-a',
          username: 'doctor-a',
          displayName: '张医生',
          roleCode: 'DOCTOR',
          permissions: [],
          doctor: { id: 'doctor-a', doctorCode: 'D-A', displayName: '张医生' },
        },
      },
    });
    await flushPromises();

    expect(wrapper.text()).toContain('P20260001');
    expect(wrapper.text()).toContain('A1-HE');
    expect(wrapper.text()).toContain('诊断内容');
    expect(wrapper.text()).toContain('签审记录');
    expect(wrapper.text()).toContain('技术医嘱');
    expect(wrapper.text()).toContain('历史报告');
    expect(wrapper.find('[aria-label="诊断主要操作"]').exists()).toBe(true);
    expect(wrapper.text()).not.toContain('ResponsibilityUnit');
    expect(wrapper.text()).not.toContain('SourceContext');
  });

  it('shows an independent molecular result in business language without raw JSON', async () => {
    const molecularWorkspace = {
      ...workspace,
      molecularResults: [
        {
          resultId: 'MR-1',
          resultCode: 'LUNG-PANEL',
          resultData: '{"conclusion":"未检出相关驱动基因变异"}',
          statusCode: 'COMPLETED',
          completedAt: '2026-08-09T02:00:00Z',
          completedBy: 'TECHNICIAN-A',
        },
      ],
    };
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        if (String(input).includes('/auth/doctors')) return new Response(JSON.stringify(doctors));
        return new Response(JSON.stringify(molecularWorkspace));
      }),
    );

    const wrapper = mount(V2DiagnosisWorkspace, { props: { caseId: 'CASE-1' } });
    await flushPromises();

    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('技术结果'))
      ?.trigger('click');
    expect(wrapper.text()).toContain('技术结果');
    expect(wrapper.text()).toContain('LUNG-PANEL');
    expect(wrapper.text()).toContain('未检出相关驱动基因变异');
    expect(wrapper.text()).not.toContain('{"conclusion"');
  });

  it('renders the versioned TBS cytology fields and writes their structured values', async () => {
    const cytologyWorkspace = {
      ...workspace,
      caseSummary: { ...workspace.caseSummary, businessTypeCode: 'CYTOLOGY_GYN' },
      templateVersion: {
        ...workspace.templateVersion,
        versionNo: 2,
        schemaDefinition: JSON.stringify({
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
              options: [{ value: 'NILM', label: '未见上皮内病变或恶性病变' }],
            },
            {
              code: 'diagnosisText',
              label: '细胞学诊断',
              type: 'TEXTAREA',
              required: true,
            },
          ],
        }),
      },
    };
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.includes('/auth/doctors')) return new Response(JSON.stringify(doctors));
      if (url.includes('/content') && init?.method === 'PUT') {
        return new Response(JSON.stringify({ ...cytologyWorkspace.diagnosis, version: 1 }));
      }
      if (url.includes('/case-support') && url.endsWith('/favorite')) {
        return new Response(JSON.stringify({ caseId: 'CASE-1', favorite: false }));
      }
      if (url.includes('/case-support')) return new Response(JSON.stringify([]));
      return new Response(JSON.stringify(cytologyWorkspace));
    });
    vi.stubGlobal('fetch', fetchMock);

    const wrapper = mount(V2DiagnosisWorkspace, { props: { caseId: 'CASE-1' } });
    await flushPromises();

    expect(wrapper.text()).toContain('标本满意度');
    expect(wrapper.text()).toContain('总体分类');
    expect(wrapper.text()).toContain('细胞学诊断');
    const adequacyField = wrapper
      .findAll('label')
      .find((label) => label.text().includes('标本满意度'));
    const categoryField = wrapper
      .findAll('label')
      .find((label) => label.text().includes('总体分类'));
    const diagnosisField = wrapper
      .findAll('label')
      .find((label) => label.text().includes('细胞学诊断'));
    await adequacyField?.find('select').setValue('SATISFACTORY');
    await categoryField?.find('select').setValue('NILM');
    await diagnosisField?.find('textarea').setValue('合成妇科细胞学诊断');
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '保存')
      ?.trigger('click');
    await flushPromises();

    const contentCall = fetchMock.mock.calls.find(([input]) => String(input).includes('/content'));
    expect(JSON.parse(contentCall?.[1]?.body as string)).toMatchObject({
      diagnosisText: '合成妇科细胞学诊断',
      expectedVersion: 0,
    });
    expect(JSON.parse(JSON.parse(contentCall?.[1]?.body as string).structuredData)).toMatchObject({
      specimenAdequacy: 'SATISFACTORY',
      generalCategory: 'NILM',
      diagnosisText: '合成妇科细胞学诊断',
    });
  });

  it('saves with optimistic version and explains a conflict in user language', async () => {
    const fetchMock = vi.fn(async (...args: [RequestInfo | URL, RequestInit?]) => {
      const input = args[0];
      const url = String(input);
      if (url.includes('/auth/doctors')) return new Response(JSON.stringify(doctors));
      if (url.includes('/content')) {
        return new Response(
          JSON.stringify({ error_code: 'V2-VERSION-CONFLICT', message: '记录版本冲突' }),
          { status: 409 },
        );
      }
      return new Response(JSON.stringify(workspace));
    });
    vi.stubGlobal('fetch', fetchMock);

    const wrapper = mount(V2DiagnosisWorkspace, { props: { caseId: 'CASE-1' } });
    await flushPromises();
    await wrapper.find('textarea[placeholder="输入正式病理诊断"]').setValue('合成病理诊断');
    const saveButton = wrapper.findAll('button').find((button) => button.text() === '保存');
    await saveButton?.trigger('click');
    await flushPromises();

    const contentCall = fetchMock.mock.calls.find(([input]) => String(input).includes('/content'));
    expect(contentCall).toBeTruthy();
    expect(JSON.parse(contentCall?.[1]?.body as string)).toMatchObject({
      diagnosisText: '合成病理诊断',
      expectedVersion: 0,
    });
    expect(wrapper.text()).toContain('记录已被他人更新，请刷新后重试');
  });

  it('opens a large report preview without leaving the diagnosis route', async () => {
    const templateWorkspace = {
      ...workspace,
      availableReportTemplates: [
        {
          templateId: 'REPORT-TEMPLATE-1',
          versionId: 'REPORT-TEMPLATE-V1',
          versionNo: 1,
          code: 'GENERAL',
          name: '通用报告',
        },
        {
          templateId: 'REPORT-TEMPLATE-2',
          versionId: 'REPORT-TEMPLATE-V2',
          versionNo: 2,
          code: 'TUMOR-LUNG',
          name: '肺肿瘤报告',
          sourcePresetCode: 'TUMOR-LUNG',
        },
      ],
    };
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes('/auth/doctors')) return new Response(JSON.stringify(doctors));
      if (url.includes('/report-preview')) {
        const tumorTemplate = url.includes('REPORT-TEMPLATE-V2');
        return new Response(
          JSON.stringify({
            valid: true,
            blockingReasons: [],
            renderedContent: JSON.stringify({
              presentation: {
                title: tumorTemplate ? '肺肿瘤病理报告' : '通用病理报告',
                sections: [
                  { code: 'MICROSCOPY', label: '镜下描述' },
                  { code: 'DIAGNOSIS', label: tumorTemplate ? '肺肿瘤诊断' : '病理诊断' },
                ],
              },
              case: {
                pathologyNo: 'P-TEST-001',
                patientReference: 'SYNTH-PATIENT',
                visitReference: 'SYNTH-VISIT',
                businessTypeCode: 'ROUTINE',
              },
              diagnosis: {
                microscopicDescription: '合成镜下所见',
                diagnosisText: '合成预览内容',
              },
              material: [{ slideCode: 'A1-HE', slideType: 'HE' }],
              responsibility: [
                { role: 'INITIAL', doctorId: 'DOCTOR-A', completedAt: '2026-08-09T01:00:00Z' },
              ],
              technicalResults: [],
            }),
          }),
        );
      }
      if (url.includes('/case-support') && url.endsWith('/favorite')) {
        return new Response(JSON.stringify({ caseId: 'CASE-1', favorite: false }));
      }
      if (url.includes('/case-support')) return new Response(JSON.stringify([]));
      return new Response(JSON.stringify(templateWorkspace));
    });
    vi.stubGlobal('fetch', fetchMock);

    const wrapper = mount(V2DiagnosisWorkspace, { props: { caseId: 'CASE-1' } });
    await flushPromises();
    const previewButton = wrapper.findAll('button').find((button) => button.text() === '报告预览');
    await previewButton?.trigger('click');
    await flushPromises();

    expect(
      fetchMock.mock.calls.some(([input]) =>
        String(input).includes('/diagnoses/D-1/report-preview'),
      ),
    ).toBe(true);
    expect(wrapper.find('[aria-label="报告预览"]').text()).toContain('合成预览内容');
    expect(wrapper.find('[aria-label="报告预览"]').text()).toContain('通用病理报告');
    expect(wrapper.find('[aria-label="报告预览"]').text()).toContain('A1-HE');
    expect(wrapper.find('[aria-label="报告预览"]').text()).not.toContain('DOCTOR-A');
    await wrapper.find('select[aria-label="报告模板版本"]').setValue('REPORT-TEMPLATE-V2');
    await flushPromises();
    expect(wrapper.find('[aria-label="报告预览"]').text()).toContain('肺肿瘤病理报告');
    expect(wrapper.find('[aria-label="报告预览"]').text()).toContain('肺肿瘤诊断');
    expect(
      fetchMock.mock.calls.some(([input]) =>
        String(input).includes('templateVersionId=REPORT-TEMPLATE-V2'),
      ),
    ).toBe(true);
  });

  it('downloads a password-protected PDF without persisting the password in the workspace', async () => {
    const reportWorkspace = {
      ...workspace,
      reports: [
        {
          reportId: 'REPORT-1',
          reportNo: 'R001',
          nature: 'ORIGINAL',
          supplemental: false,
          status: 'EFFECTIVE',
          templateVersionId: 'RTV-1',
          pdfFileReference: 'pis-v2/reports/REPORT-1.pdf',
          pdfContentHash: 'synthetic-pdf-hash',
          signedBy: 'doctor-a',
          signedAt: '2026-08-14T01:00:00Z',
        },
      ],
    };
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/pdf-encrypted') && init?.method === 'POST') {
        return new Response(new Blob(['synthetic encrypted pdf'], { type: 'application/pdf' }));
      }
      if (url.includes('/auth/doctors')) return new Response(JSON.stringify(doctors));
      if (url.includes('/case-support') && url.endsWith('/favorite')) {
        return new Response(JSON.stringify({ caseId: 'CASE-1', favorite: false }));
      }
      if (url.includes('/case-support')) return new Response(JSON.stringify([]));
      return new Response(JSON.stringify(reportWorkspace));
    });
    const createObjectURL = vi.fn(() => 'blob:synthetic-report');
    const revokeObjectURL = vi.fn();
    const click = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(() => undefined);
    vi.stubGlobal('fetch', fetchMock);
    vi.stubGlobal('URL', { createObjectURL, revokeObjectURL });

    const wrapper = mount(V2DiagnosisWorkspace, {
      props: { caseId: 'CASE-1', focusKind: 'report' },
    });
    await flushPromises();
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '加密下载')
      ?.trigger('click');
    await wrapper.find('input[type="password"]').setValue('synthetic-safe-2026');
    await wrapper.find('textarea[placeholder="填写对外提供或归档用途"]').setValue('合成对外提供');
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '生成并下载')
      ?.trigger('click');
    await flushPromises();

    const request = fetchMock.mock.calls.find(([input]) =>
      String(input).endsWith('/pdf-encrypted'),
    );
    expect(JSON.parse(request?.[1]?.body as string)).toEqual({
      accessPassword: 'synthetic-safe-2026',
      reason: '合成对外提供',
    });
    expect(createObjectURL).toHaveBeenCalledOnce();
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:synthetic-report');
    expect(click).toHaveBeenCalledOnce();
    expect(wrapper.find('[aria-label="加密下载报告"]').exists()).toBe(false);
    click.mockRestore();
  });

  it('keeps favorite, consultation and follow-up actions inside the diagnosis workspace', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.includes('/auth/doctors')) return new Response(JSON.stringify(doctors));
      if (url.includes('/case-support') && init?.method === 'POST') {
        if (url.endsWith('/favorite')) {
          return new Response(JSON.stringify({ caseId: 'CASE-1', favorite: true }));
        }
        if (url.endsWith('/follow-ups')) {
          return new Response(
            JSON.stringify({
              followUpId: 'FU-1',
              caseId: 'CASE-1',
              followUpDate: '2026-09-01',
              plan: '合成随访计划',
              operatorRef: 'doctor-a',
              createdAt: '2026-08-14T00:00:00Z',
            }),
          );
        }
        return new Response(
          JSON.stringify({
            consultationId: 'CONS-1',
            caseId: 'CASE-1',
            consultationAt: '2026-08-14T00:00:00Z',
            initiatorRef: 'doctor-a',
            participantRefs: 'doctor-b',
            reason: '合成疑难病例复核',
            recordedByRef: 'doctor-a',
            createdAt: '2026-08-14T00:00:00Z',
          }),
        );
      }
      return responseForWorkspace(input);
    });
    vi.stubGlobal('fetch', fetchMock);

    const wrapper = mount(V2DiagnosisWorkspace, {
      props: {
        caseId: 'CASE-1',
        authUser: {
          userId: 'doctor-a',
          username: 'doctor-a',
          displayName: '张医生',
          roleCode: 'DOCTOR',
          permissions: [],
          doctor: { id: 'doctor-a', doctorCode: 'D-A', displayName: '张医生' },
        },
      },
    });
    await flushPromises();

    await wrapper
      .findAll('button')
      .find((button) => button.text() === '收藏病例')
      ?.trigger('click');
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('会诊与随访'))
      ?.trigger('click');
    await wrapper.find('input[placeholder="填写随访目的和计划"]').setValue('合成随访计划');
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '新增随访')
      ?.trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('病例随访');
    expect(wrapper.text()).toContain('科内会诊');
    expect(fetchMock.mock.calls.some(([input]) => String(input).endsWith('/favorite'))).toBe(true);
    const followUpCall = fetchMock.mock.calls.find(
      ([input, init]) => String(input).endsWith('/follow-ups') && init?.method === 'POST',
    );
    expect(followUpCall).toBeTruthy();
    expect(JSON.parse(followUpCall?.[1]?.body as string)).toMatchObject({
      plan: '合成随访计划',
      idempotencyKey: expect.any(String),
    });
  });

  it('automatically routes a public-pool case with an idempotent diagnosis command', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.includes('/auth/doctors')) return new Response(JSON.stringify(doctors));
      if (url.endsWith('/diagnosis-workspaces/public-pool')) {
        return new Response(
          JSON.stringify([
            { caseId: 'CASE-AUTO', pathologyNo: 'P20260088', businessTypeCode: 'HISTOLOGY' },
          ]),
        );
      }
      if (url.endsWith('/diagnoses/auto-assign') && init?.method === 'POST') {
        return new Response(
          JSON.stringify({
            diagnosisId: 'D-AUTO',
            caseId: 'CASE-AUTO',
            responsibilityId: 'R-AUTO',
            doctorId: 'doctor-a',
            diagnosisGroupCode: 'GI',
            assignmentRuleId: 'RULE-A',
            dailyAssignedCount: 1,
            dailyCaseLimit: 20,
            duplicate: false,
          }),
        );
      }
      return new Response(JSON.stringify({ myWork: [], publicPool: [] }));
    });
    vi.stubGlobal('fetch', fetchMock);

    const wrapper = mount(V2DiagnosisWorkspace);
    await flushPromises();
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '自动分诊')
      ?.trigger('click');
    await flushPromises();

    const autoCall = fetchMock.mock.calls.find(([input]) =>
      String(input).endsWith('/diagnoses/auto-assign'),
    );
    expect(autoCall).toBeTruthy();
    expect(JSON.parse(autoCall?.[1]?.body as string)).toMatchObject({
      caseId: 'CASE-AUTO',
      idempotencyKey: expect.any(String),
    });
    expect(wrapper.emitted('navigate')?.[0]).toEqual(['/v2/cases/CASE-AUTO?focus=diagnosis']);
  });
});
