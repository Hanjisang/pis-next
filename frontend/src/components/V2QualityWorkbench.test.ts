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
    expect(wrapper.text()).not.toContain('registrationCount');
    expect(wrapper.text()).not.toContain('reportSignOutCount');
  });
});
