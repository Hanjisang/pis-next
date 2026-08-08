import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';

import V2OperationsWorkbench from './V2OperationsWorkbench.vue';

describe('V2OperationsWorkbench', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('loads QC facts and statistics without turning warnings into workflow blocks', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify([
            {
              ruleCode: 'ROUTINE_TAT',
              ruleName: 'Routine TAT',
              metricCode: 'ROUTINE_TAT_HOURS',
              warningThreshold: 24,
              overdueThreshold: 48,
            },
          ]),
          { status: 200 },
        ),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify([
            {
              ruleCode: 'ROUTINE_TAT',
              metricCode: 'ROUTINE_TAT_HOURS',
              value: 25,
              statusCode: 'WARNING',
              evaluatedAt: '2026-08-09T00:00:00Z',
            },
          ]),
          { status: 200 },
        ),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({ counts: { registrationCount: 1 }, businessTypeDistribution: [] }),
          {
            status: 200,
          },
        ),
      );
    vi.stubGlobal('fetch', fetchMock);

    const wrapper = mount(V2OperationsWorkbench, { props: { mode: 'quality' } });
    await flushPromises();

    expect(wrapper.text()).toContain('质控与统计');
    expect(wrapper.text()).toContain('Routine TAT');
    expect(wrapper.text()).toContain('WARNING');
    expect(wrapper.text()).toContain('QC 提醒默认不阻断签发');
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });
});
