import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';

import V2SlideProductionWorkbench from './V2SlideProductionWorkbench.vue';

describe('V2SlideProductionWorkbench', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('organizes the queue around derived histology phases', async () => {
    const today = new Date().toISOString();
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            slides: [
              {
                slideId: 'S-1',
                caseId: 'C-1',
                caseNo: 'P001',
                patientReference: 'SYNTH-1',
                businessTypeCode: 'ROUTINE',
                specimenCode: 'A',
                blockCode: 'A1',
                slideCode: 'A1-HE',
                slideType: 'HE',
                sourceContextType: 'INITIAL',
                completedAt: null,
                concurrencyVersion: 0,
                printCount: 0,
              },
              {
                slideId: 'S-2',
                caseId: 'C-1',
                caseNo: 'P001',
                patientReference: 'SYNTH-1',
                businessTypeCode: 'ROUTINE',
                specimenCode: 'A',
                blockCode: 'A2',
                slideCode: 'A2-HE',
                slideType: 'HE',
                sourceContextType: 'INITIAL',
                completedAt: null,
                concurrencyVersion: 0,
                printCount: 1,
              },
              {
                slideId: 'S-3',
                caseId: 'C-1',
                caseNo: 'P001',
                patientReference: 'SYNTH-1',
                businessTypeCode: 'ROUTINE',
                specimenCode: 'A',
                blockCode: 'A3',
                slideCode: 'A3-HE',
                slideType: 'HE',
                sourceContextType: 'INITIAL',
                completedAt: today,
                concurrencyVersion: 1,
                printCount: 1,
              },
              {
                slideId: 'S-4',
                caseId: 'C-1',
                caseNo: 'P001',
                patientReference: 'SYNTH-1',
                businessTypeCode: 'ROUTINE',
                specimenCode: 'A',
                blockCode: 'A4',
                slideCode: 'A4-HE',
                slideType: 'HE',
                sourceContextType: 'INITIAL',
                completedAt: '2025-01-01T00:00:00Z',
                concurrencyVersion: 1,
                printCount: 1,
              },
            ],
          }),
        ),
      ),
    );
    const wrapper = mount(V2SlideProductionWorkbench);
    await flushPromises();

    expect(wrapper.text()).toContain('待脱水');
    expect(wrapper.text()).toContain('待包埋');
    expect(wrapper.text()).toContain('待切片');
    expect(wrapper.text()).toContain('待染色');
    expect(wrapper.text()).toContain('待封片');
    expect(wrapper.text()).toContain('异常');
    expect(wrapper.text()).not.toContain('待制片');
    expect(wrapper.text()).not.toContain('今日完成');
    expect(wrapper.text()).not.toContain('脱水 → 包埋');
  });
});
