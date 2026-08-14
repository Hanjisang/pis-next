import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';

import V2QualityWorkbench from './V2QualityWorkbench.vue';

describe('V2QualityWorkbench', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('renders statistics in pathology business language', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((input: RequestInfo | URL) => {
        const url = String(input);
        if (url.endsWith('/qc/rules')) return Promise.resolve(new Response('[]'));
        if (url.endsWith('/qc/evaluations')) return Promise.resolve(new Response('[]'));
        return Promise.resolve(
          new Response(
            JSON.stringify({
              counts: {
                registrationCount: 8,
                specimenCount: 15,
                reportSignOutCount: 5,
              },
              businessTypeDistribution: [],
              reportTat: {
                policyCaseCount: 8,
                completedCount: 5,
                completedOnTime: 4,
                completedOverdue: 1,
                activeWarning: 1,
                activeOverdue: 1,
                activeDelayed: 1,
                averageCompletedMinutes: 3210,
                complianceRate: 80.0,
                overdueCases: [
                  {
                    caseId: 'CASE-1',
                    pathologyNo: 'H-2026-001',
                    patientReference: 'SYNTH-PATIENT',
                    businessTypeCode: 'HISTOLOGY',
                    status: 'OVERDUE',
                    elapsedMinutes: 5000,
                    startedAt: '2026-08-10T00:00:00Z',
                    warningAt: '2026-08-12T00:00:00Z',
                    dueAt: '2026-08-13T00:00:00Z',
                    delayed: true,
                  },
                ],
              },
            }),
          ),
        );
      }),
    );

    const wrapper = mount(V2QualityWorkbench);
    await flushPromises();

    expect(wrapper.text()).toContain('登记病例');
    expect(wrapper.text()).toContain('标本');
    expect(wrapper.text()).toContain('报告签发');
    expect(wrapper.text()).toContain('报告时效与超期病例');
    expect(wrapper.text()).toContain('H-2026-001');
    expect(wrapper.text()).toContain('80%');
    expect(wrapper.text()).not.toContain('registrationCount');
    expect(wrapper.text()).not.toContain('reportSignOutCount');
  });
});
