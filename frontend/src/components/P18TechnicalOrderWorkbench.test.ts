import { mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';

import P18TechnicalOrderWorkbench from './P18TechnicalOrderWorkbench.vue';

describe('P18 technical order workbench', () => {
  it('creates and submits a technical order through the backend boundary', async () => {
    const orderId = 'order-1';
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input);
      const body = path.endsWith('/submit')
        ? {
            orderId,
            orderNo: 'P18-SYNTHETIC-ORDER-1',
            stateCode: 'SUBMITTED',
            concurrencyVersion: 2,
            projects: [],
          }
        : {
            orderId,
            orderNo: 'P18-SYNTHETIC-ORDER-1',
            stateCode: 'DRAFT',
            concurrencyVersion: 1,
            projects: [],
          };
      return new Response(JSON.stringify(body), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    });
    vi.stubGlobal('fetch', fetchMock);

    const wrapper = mount(P18TechnicalOrderWorkbench);
    const inputs = wrapper.findAll('input');
    await inputs[0].setValue('case-1');
    await inputs[1].setValue('formation-1');
    const buttons = wrapper.findAll('button');
    await buttons[1].trigger('click');
    await vi.waitFor(() => expect(wrapper.text()).toContain('P18-SYNTHETIC-ORDER-1'));

    await buttons[2].trigger('click');
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    expect(String(fetchMock.mock.calls[1]?.[0])).toContain('/api/p18/orders/order-1/submit');
  });
});
