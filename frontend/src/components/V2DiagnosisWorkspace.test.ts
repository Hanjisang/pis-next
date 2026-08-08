import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';

import V2DiagnosisWorkspace from './V2DiagnosisWorkspace.vue';

const workspace = {
  caseSummary: {
    caseId: 'CASE-1',
    pathologyNo: 'H-000001',
    businessTypeCode: 'HISTOLOGY',
    lifecycle: 'ACTIVE',
  },
  application: {
    applicationItemCode: 'SYNTH-HISTOLOGY',
    sourceSystemCode: 'SYNTH-HIS',
    externalApplicationId: 'APP-1',
  },
  patient: { patientReference: 'SYNTH-PATIENT-1', visitReference: 'SYNTH-VISIT-1' },
  materialTree: {
    caseId: 'CASE-1',
    caseNo: 'H-000001',
    businessTypeCode: 'HISTOLOGY',
    specimens: [
      {
        specimenId: 'S-1',
        specimenNo: 'HS-000001',
        specimenCode: 'A',
        specimenKindCode: 'TISSUE',
        blocks: [
          {
            blockId: 'B-1',
            blockCode: 'A1',
            blockType: 'ROUTINE',
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
      doctorId: 'p15-local-registration-actor',
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
    doctorId: 'p15-local-registration-actor',
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
    canCreateTechnicalOrder: false,
  },
  technicalOrders: [],
  blockingTechnicalOrderCount: 0,
  technicalOrder: { kind: 'TECHNICAL_ORDER', status: 'V2-I04已实现' },
  report: { kind: 'REPORT', status: 'V2-I05待实现' },
  refreshedAt: '2026-08-08T00:00:00Z',
};

describe('V2DiagnosisWorkspace', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('renders the independent case context, material tree, diagnosis editor and responsibility chain', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(new Response(JSON.stringify(workspace), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);

    const wrapper = mount(V2DiagnosisWorkspace, { props: { caseId: 'CASE-1' } });
    await flushPromises();

    expect(wrapper.text()).toContain('Diagnosis Workspace');
    expect(wrapper.text()).toContain('Material Tree');
    expect(wrapper.text()).toContain('A1-HE');
    expect(wrapper.text()).toContain('Responsibility Chain');
    expect(wrapper.text()).toContain('肿瘤类型');
    expect(wrapper.find('select').exists()).toBe(true);
    expect(wrapper.find('textarea').exists()).toBe(true);
    expect(wrapper.find('[aria-label="标本蜡块切片树"]').exists()).toBe(true);
  });

  it('sends explicit diagnosis save with the workspace version and shows conflict feedback', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(workspace), { status: 200 }))
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ error_code: 'V2-VERSION-CONFLICT', message: '请重新加载' }), {
          status: 409,
        }),
      );
    vi.stubGlobal('fetch', fetchMock);

    const wrapper = mount(V2DiagnosisWorkspace, { props: { caseId: 'CASE-1' } });
    await flushPromises();
    await wrapper.findAll('textarea')[1].setValue('synthetic diagnosis');
    await wrapper.get('button.primary-action').trigger('click');
    await flushPromises();

    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(JSON.parse(fetchMock.mock.calls[1][1].body as string)).toMatchObject({
      diagnosisText: 'synthetic diagnosis',
      expectedVersion: 0,
    });
    expect(wrapper.text()).toContain('V2-VERSION-CONFLICT');
  });
});
