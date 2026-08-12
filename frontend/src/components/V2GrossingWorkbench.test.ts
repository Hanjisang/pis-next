import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';

import V2GrossingWorkbench from './V2GrossingWorkbench.vue';

const caseId = '11111111-1111-1111-1111-111111111111';
const specimenOne = '22222222-2222-2222-2222-222222222221';
const specimenTwo = '22222222-2222-2222-2222-222222222222';

function workspace(actions: string[]) {
  return {
    caseId,
    caseNo: 'P20260001',
    businessTypeCode: 'HISTOLOGY',
    patientReference: '合成患者甲',
    visitReference: 'ZY10001',
    applicationNo: 'APP-001',
    specimens: [
      {
        specimenId: specimenOne,
        specimenNo: 'SP-001',
        specimenCode: '1',
        specimenName: '胃体',
        specimenKindCode: 'TISSUE',
        creationSourceCode: 'REGISTRATION',
        collectionSite: '胃体',
        specimenDescription: '胃体组织',
        sourceSpecimenCode: null,
        grossMaterialDescription: '灰白组织一块',
        grossSpecimenVersion: 0,
        blocks: [
          {
            blockId: '33333333-3333-3333-3333-333333333333',
            blockCode: 'A1',
            blockType: 'ROUTINE',
            samplingDescription: '胃体全取',
            note: null,
            concurrencyVersion: 0,
            printCount: 1,
            verificationStatus: 'PASSED',
            slides: [],
          },
        ],
        directSlides: [],
      },
      {
        specimenId: specimenTwo,
        specimenNo: 'SP-002',
        specimenCode: '2',
        specimenName: '胃窦',
        specimenKindCode: 'TISSUE',
        creationSourceCode: 'GROSSING_SPLIT',
        collectionSite: '胃窦',
        specimenDescription: '胃窦组织',
        sourceSpecimenCode: '1',
        grossMaterialDescription: '灰红组织一块',
        grossSpecimenVersion: 0,
        blocks: [],
        directSlides: [],
      },
    ],
    grossing: {
      grossingId: '44444444-4444-4444-4444-444444444444',
      grossingNo: 'G001',
      sourceType: 'INITIAL',
      sourceReferenceId: null,
      grossDescription: '胃组织取材',
      grossingInstruction: null,
      grossingDoctorId: 'DOCTOR-1',
      recorderId: 'USER-1',
      startedAt: '2026-08-13T00:00:00Z',
      completedAt: null,
      concurrencyVersion: 0,
    },
    availableActions: actions,
    verificationPolicy: {
      verificationRequired: true,
      dualCheckRequired: false,
      sameUserAllowed: true,
    },
  };
}

function specimen(id: string, name: string, site: string) {
  return {
    specimenId: id,
    caseId,
    specimenNo: id === specimenOne ? 'SP-001' : 'SP-002',
    specimenCode: id === specimenOne ? '1' : '2',
    specimenName: name,
    specimenKindCode: 'TISSUE',
    creationSourceCode: id === specimenOne ? 'REGISTRATION' : 'GROSSING_SPLIT',
    sourceKindCode: 'LOCAL',
    sourceReference: 'APP-001',
    collectionSite: site,
    collectionMethodCode: 'SURGICAL',
    description: `${name}组织`,
    labelCode: null,
    deletedAt: null,
    deletionReason: null,
    concurrencyVersion: 0,
    duplicate: false,
    eventTypeCode: 'READ',
  };
}

function stubBackend(actions: string[]) {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes('/grossing-workspace'))
        return new Response(JSON.stringify(workspace(actions)));
      if (url.endsWith(`/specimens/${specimenOne}`)) {
        return new Response(JSON.stringify(specimen(specimenOne, '胃体', '胃体')));
      }
      if (url.endsWith(`/specimens/${specimenTwo}`)) {
        return new Response(JSON.stringify(specimen(specimenTwo, '胃窦', '胃窦')));
      }
      if (url.includes('/material/grossings/44444444-4444-4444-4444-444444444444/images')) {
        return new Response('[]');
      }
      if (url.includes('/api/v2/auth/doctors')) return new Response('[]');
      throw new Error(`unexpected request ${url}`);
    }),
  );
}

describe('V2GrossingWorkbench', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('renders a compact multi-specimen material workspace from backend actions', async () => {
    stubBackend([
      'SPECIMEN_ADD',
      'SPECIMEN_UPDATE',
      'SPECIMEN_SPLIT',
      'SPECIMEN_CANCEL',
      'GROSSING_UPDATE',
      'GROSSING_COMPLETE',
      'GROSS_IMAGE_CAPTURE',
      'GROSS_IMAGE_ANNOTATE',
      'GROSS_IMAGE_MEASURE',
      'BLOCK_CREATE',
      'BLOCK_UPDATE',
      'BLOCK_CANCEL',
      'BLOCK_PRINT',
      'BLOCK_VERIFY',
    ]);
    const wrapper = mount(V2GrossingWorkbench, {
      props: {
        caseId,
        authUser: {
          userId: 'USER-1',
          username: 'grosser',
          displayName: '取材员甲',
          roleCode: 'GROSSER',
          permissions: [],
          doctor: { id: 'DOCTOR-1', doctorCode: 'D001', displayName: '取材医生甲' },
        },
      },
    });
    await flushPromises();

    expect(wrapper.text()).toContain('P20260001');
    expect(wrapper.text()).toContain('1 · 胃体');
    expect(wrapper.text()).toContain('2 · 胃窦');
    expect(wrapper.text()).toContain('由标本 1 拆分');
    expect(wrapper.find('table.compact-material-table').exists()).toBe(true);
    expect(wrapper.text()).toContain('A1');
    expect(wrapper.text()).toContain('已核对');
    expect(wrapper.text()).toContain('新增标本');
    expect(wrapper.text()).toContain('拆分');
    expect(wrapper.text()).not.toContain(caseId);
    expect(wrapper.text()).not.toContain(specimenOne);

    await wrapper.findAll('.specimen-sidebar-list button')[1].trigger('click');
    expect(wrapper.get('input[aria-label="当前标本取材部位"]').element).toHaveProperty(
      'value',
      '胃窦',
    );
  });

  it('does not render write actions omitted by the authoritative backend', async () => {
    stubBackend([]);
    const wrapper = mount(V2GrossingWorkbench, { props: { caseId } });
    await flushPromises();

    expect(wrapper.text()).not.toContain('新增标本');
    expect(wrapper.text()).not.toContain('取消误录');
    expect(wrapper.text()).not.toContain('+ 蜡块');
    expect(wrapper.text()).not.toContain('批量打印');
  });
});
