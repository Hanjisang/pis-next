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
    const slideTab = wrapper
      .findAll('.context-nav-list button')
      .find((button) => button.text().includes('玻片'));
    await slideTab?.trigger('click');
    expect(wrapper.text()).toContain('A1-HE');
    expect(wrapper.text()).toContain('诊断内容');
    expect(wrapper.text()).toContain('责任链');
    expect(wrapper.text()).toContain('技术医嘱');
    expect(wrapper.text()).toContain('报告');
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

    expect(wrapper.text()).toContain('分子结果');
    expect(wrapper.text()).toContain('LUNG-PANEL');
    expect(wrapper.text()).toContain('未检出相关驱动基因变异');
    expect(wrapper.text()).not.toContain('{"conclusion"');
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
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes('/auth/doctors')) return new Response(JSON.stringify(doctors));
      if (url.includes('/report-preview')) {
        return new Response(
          JSON.stringify({
            valid: true,
            blockingReasons: [],
            renderedContent: JSON.stringify({
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
      return new Response(JSON.stringify(workspace));
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
    expect(wrapper.find('[aria-label="报告预览"]').text()).toContain('A1-HE');
    expect(wrapper.find('[aria-label="报告预览"]').text()).not.toContain('DOCTOR-A');
  });
});
