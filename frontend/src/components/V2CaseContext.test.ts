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
        if (url.includes('/cases/C-1/materials')) {
          return new Response(
            JSON.stringify({
              caseId: 'C-1',
              caseNo: 'P001',
              businessTypeCode: 'ROUTINE',
              specimens: [],
              initialRequiredCount: 0,
              initialCompletedCount: 0,
              initialProductionComplete: false,
            }),
          );
        }
        return new Response(
          JSON.stringify({
            caseId: 'C-1',
            caseNo: 'P001',
            businessTypeCode: 'ROUTINE',
            patientReference: 'SYNTH-PATIENT',
            visitReference: 'SYNTH-VISIT',
            applicationNo: 'APP-1',
            lifecycleStateCode: 'ACTIVE',
            numberBindingActive: true,
            concurrencyVersion: 0,
            duplicate: false,
            eventTypeCode: 'READ',
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
    expect(wrapper.text()).toContain('材料树');
    expect(wrapper.text()).toContain('进入诊断');
  });
});
