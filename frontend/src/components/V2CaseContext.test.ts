import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';

import V2CaseContext from './V2CaseContext.vue';

describe('V2CaseContext', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('lands search results in a readable case and material context', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        expect(url).toContain('/api/v2/case-workspaces/C-1');
        return new Response(
          JSON.stringify({
            caseHeader: {
              caseId: 'C-1',
              pathologyNo: 'P001',
              businessTypeCode: 'ROUTINE',
              businessTypeName: '常规组织病理',
              lifecycle: 'ACTIVE',
              applicationItemCode: 'SYNTH-ROUTINE',
              sourceSystemCode: 'MANUAL',
              applicationNo: 'APP-1',
              patientReference: 'SYNTH-PATIENT',
              visitReference: 'SYNTH-VISIT',
              createdAt: '2026-08-10T08:00:00Z',
            },
            materialTree: {
              caseId: 'C-1',
              pathologyNo: 'P001',
              businessTypeCode: 'ROUTINE',
              specimens: [],
            },
            grossings: [],
            responsibilities: [],
            technicalOrders: [],
            digitalSlides: [],
            reports: [],
            timeline: [],
            refreshedAt: '2026-08-10T08:00:00Z',
          }),
        );
      }),
    );
    const wrapper = mount(V2CaseContext, {
      props: {
        caseId: 'C-1',
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

    expect(wrapper.text()).toContain('P001');
    expect(wrapper.text()).toContain('SYNTH-PATIENT');
    expect(wrapper.text()).toContain('材料与制片');
    expect(wrapper.text()).toContain('进入诊断');
  });
});
