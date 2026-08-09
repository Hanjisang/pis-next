import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';

import V2TechnicalWorkbench from './V2TechnicalWorkbench.vue';

describe('V2TechnicalWorkbench', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('organizes work by technician tasks without exposing internal configuration', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          orders: [
            {
              orderId: 'O-1',
              orderNo: 'TO001',
              diagnosisId: 'D-1',
              caseId: 'C-1',
              caseNo: 'P20260001',
              patientReference: 'SYNTH-PATIENT-1',
              status: 'PENDING',
              requiredBeforeSignOut: true,
              blocking: true,
              version: 0,
              duplicate: false,
              items: [
                {
                  itemId: 'I-1',
                  projectId: 'P-1',
                  projectCode: 'IHC-KI67',
                  projectName: 'Ki67 免疫组化',
                  quantity: 3,
                  status: 'PENDING',
                  expectedCount: 3,
                  completedCount: 0,
                  targets: [{ targetType: 'BLOCK', targetId: 'B-1', displayCode: 'A1' }],
                  outputs: [],
                },
              ],
            },
          ],
        }),
        { status: 200 },
      ),
    );
    vi.stubGlobal('fetch', fetchMock);

    const wrapper = mount(V2TechnicalWorkbench);
    await flushPromises();

    expect(wrapper.text()).toContain('技术执行工作台');
    expect(wrapper.text()).toContain('P20260001');
    expect(wrapper.text()).toContain('Ki67 免疫组化');
    expect(wrapper.text()).toContain('A1');
    expect(wrapper.text()).toContain('0/3');
    expect(wrapper.text()).not.toContain('TechnicalProject');
    expect(wrapper.text()).not.toContain('JSON');
    expect(fetchMock).toHaveBeenCalledOnce();
  });

  it('opens an independent molecular case as a result task without fake material steps', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        if (url.endsWith('/technical-workbench')) {
          return new Response(JSON.stringify({ orders: [] }));
        }
        if (url.endsWith('/cases/M-1/materials')) {
          return new Response(
            JSON.stringify({
              caseId: 'M-1',
              caseNo: 'M20260001',
              businessTypeCode: 'MOLECULAR',
              specimens: [],
              initialRequiredCount: 0,
              initialCompletedCount: 0,
              initialProductionComplete: true,
            }),
          );
        }
        return new Response(
          JSON.stringify({
            caseId: 'M-1',
            caseNo: 'M20260001',
            businessTypeCode: 'MOLECULAR',
            patientReference: 'SYNTH-MOLECULAR-PATIENT',
          }),
        );
      }),
    );

    const wrapper = mount(V2TechnicalWorkbench, { props: { caseId: 'M-1' } });
    await flushPromises();

    expect(wrapper.text()).toContain('独立分子病例');
    expect(wrapper.text()).toContain('M20260001 · 录入结果');
    expect(wrapper.text()).toContain('不经过虚构的取材、蜡块或玻片');
    expect(wrapper.text()).not.toContain('结果 JSON');
  });
});
