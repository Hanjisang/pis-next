import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';

import V2FrozenWorkspace from './V2FrozenWorkspace.vue';

const workspace = {
  frozenCaseId: 'CASE-F',
  pathologyNo: 'F-000002',
  businessTypeCode: 'FROZEN',
  routineCaseId: null,
  rounds: [
    {
      roundId: 'ROUND-1',
      roundNo: 1,
      status: 'SIGNED',
      specimens: [
        {
          specimenId: 'S-A',
          specimenNo: 'FS-1',
          specimenCode: 'A',
          specimenKindCode: 'TISSUE',
          collectionSite: '合成首轮',
        },
      ],
      totalRequiredSlides: 1,
      completedRequiredSlides: 1,
      productionComplete: true,
      diagnosisId: 'D-1',
      arrivalTime: '2026-08-09T01:00:00Z',
      diagnosisSignedTime: '2026-08-09T01:20:00Z',
    },
    {
      roundId: 'ROUND-2',
      roundNo: 2,
      status: 'PRODUCTION_COMPLETE',
      specimens: [
        {
          specimenId: 'S-B',
          specimenNo: 'FS-2',
          specimenCode: 'B',
          specimenKindCode: 'TISSUE',
          collectionSite: '合成第二轮',
        },
      ],
      totalRequiredSlides: 1,
      completedRequiredSlides: 1,
      productionComplete: true,
      diagnosisId: null,
      arrivalTime: '2026-08-09T01:25:00Z',
      diagnosisSignedTime: null,
    },
  ],
};

describe('V2FrozenWorkspace', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('makes rounds explicit and hides material registration from diagnosis-only users', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((input: RequestInfo | URL) => {
        const url = String(input);
        if (url.includes('/frozen/cases/')) {
          return Promise.resolve(new Response(JSON.stringify(workspace)));
        }
        return Promise.resolve(
          new Response(
            JSON.stringify({
              caseId: 'CASE-F',
              caseNo: 'F-000002',
              businessTypeCode: 'FROZEN',
              patientReference: 'SYNTH-PATIENT-F',
              visitReference: 'SYNTH-VISIT-F',
            }),
          ),
        );
      }),
    );
    const wrapper = mount(V2FrozenWorkspace, {
      props: {
        caseId: 'CASE-F',
        authUser: {
          userId: 'U-DOCTOR',
          username: 'doctor-c',
          displayName: 'Doctor C',
          roleCode: 'DOCTOR',
          permissions: ['P14-PERM-034'],
        },
      },
    });
    await flushPromises();

    expect(wrapper.text()).toContain('第 1 轮');
    expect(wrapper.text()).toContain('第 2 轮');
    expect(wrapper.text()).toContain('合成第二轮');
    expect(wrapper.text()).not.toContain('登记术中新增标本');
    expect(wrapper.text()).not.toContain('SourceContext');
  });
});
