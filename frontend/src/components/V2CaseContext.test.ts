import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';

import V2CaseContext from './V2CaseContext.vue';

const workspace = {
  caseHeader: {
    caseId: 'C-1',
    pathologyNo: 'P001',
    businessTypeCode: 'HISTOLOGY',
    businessTypeName: '常规组织病理',
    lifecycle: 'ACTIVE',
    applicationItemCode: 'SYNTH-HISTOLOGY',
    sourceSystemCode: 'MANUAL',
    applicationNo: 'APP-1',
    patientReference: 'SYNTH-PATIENT',
    visitReference: 'SYNTH-VISIT',
    createdAt: '2026-08-10T08:00:00Z',
  },
  materialTree: {
    caseId: 'C-1',
    pathologyNo: 'P001',
    businessTypeCode: 'HISTOLOGY',
    specimens: [],
  },
  grossings: [],
  responsibilities: [],
  technicalOrders: [],
  digitalSlides: [],
  reports: [],
  timeline: [],
  refreshedAt: '2026-08-10T08:00:00Z',
};

describe('V2CaseContext', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('shows authorized pathology-number correction and case cancellation actions', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        if (url.includes('/api/v2/case-workspaces/C-1'))
          return new Response(JSON.stringify(workspace));
        if (url.includes('/api/v2/registration/cases/C-1/pathology-number-history')) {
          return new Response('[]');
        }
        if (url.includes('/api/v2/registration/cases/C-1')) {
          return new Response(
            JSON.stringify({
              caseId: 'C-1',
              caseNo: 'P001',
              businessTypeCode: 'HISTOLOGY',
              patientReference: 'SYNTH-PATIENT',
              visitReference: 'SYNTH-VISIT',
              applicationNo: 'APP-1',
              lifecycleStateCode: 'ACTIVE',
              numberBindingActive: true,
              concurrencyVersion: 0,
              cancelledAt: null,
              cancelledByRef: null,
              cancellationReason: null,
              duplicate: false,
              eventTypeCode: 'PIS-V2-CASE-READ',
            }),
          );
        }
        if (url.includes('/api/v2/cases/C-1/progress')) {
          return new Response(JSON.stringify({ currentStageLabel: '待取材' }));
        }
        throw new Error(`unexpected request ${url}`);
      }),
    );
    const wrapper = mount(V2CaseContext, {
      props: {
        caseId: 'C-1',
        authUser: {
          userId: 'U-REG',
          username: 'registrar-a',
          displayName: '登记员甲',
          roleCode: 'REGISTRAR',
          permissions: ['P14-PERM-006', 'P14-PERM-007', 'P14-PERM-048'],
        },
      },
    });
    await flushPromises();

    expect(wrapper.text()).toContain('P001');
    expect(wrapper.text()).toContain('SYNTH-PATIENT');
    expect(wrapper.text()).toContain('更多');
    expect(wrapper.text()).toContain('更正病理号');
    expect(wrapper.text()).toContain('取消病例');
    expect(wrapper.text()).not.toContain('C-1');
  });

  it('hides lifecycle actions when authoritative permissions are absent', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        if (url.includes('/api/v2/case-workspaces/C-1'))
          return new Response(JSON.stringify(workspace));
        if (url.includes('/pathology-number-history')) return new Response('[]');
        if (url.includes('/api/v2/registration/cases/C-1')) {
          return new Response(
            JSON.stringify({
              caseId: 'C-1',
              caseNo: 'P001',
              lifecycleStateCode: 'ACTIVE',
              numberBindingActive: true,
              concurrencyVersion: 0,
            }),
          );
        }
        return new Response(JSON.stringify({ currentStageLabel: '待取材' }));
      }),
    );
    const wrapper = mount(V2CaseContext, {
      props: {
        caseId: 'C-1',
        authUser: {
          userId: 'U-DOCTOR',
          username: 'doctor-c',
          displayName: '医生丙',
          roleCode: 'DOCTOR',
          permissions: ['P14-PERM-034'],
        },
      },
    });
    await flushPromises();

    expect(wrapper.text()).not.toContain('更正病理号');
    expect(wrapper.text()).not.toContain('取消病例');
  });
});
