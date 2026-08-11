import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';

import App from './App.vue';

describe('PIS Next V2 application shell', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    window.history.replaceState({}, '', '/');
  });

  it('shows task navigation allowed for the authenticated role', async () => {
    window.history.replaceState({}, '', '/v2/workbench');
    vi.stubGlobal('scrollTo', vi.fn());
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        if (url.endsWith('/auth/config')) return new Response(JSON.stringify({ required: true }));
        if (url.endsWith('/auth/me')) {
          return new Response(
            JSON.stringify({
              userId: 'registrar-1',
              username: 'registrar',
              displayName: '登记员甲',
              roleCode: 'REGISTRAR',
              department: 'REGISTRATION',
              permissions: ['P14-PERM-004'],
            }),
          );
        }
        return new Response(JSON.stringify({}), { status: 200 });
      }),
    );

    const wrapper = mount(App);
    await flushPromises();

    expect(wrapper.text()).toContain('PIS');
    expect(wrapper.text()).toContain('工作台');
    expect(wrapper.text()).toContain('待登记');
    expect(wrapper.text()).toContain('退回待处理');
    expect(wrapper.text()).toContain('我今天登记');
    expect(wrapper.text()).not.toContain('待接诊');
    expect(wrapper.text()).not.toContain('常规制片');
    expect(wrapper.find('.app-sidebar').exists()).toBe(false);
    expect(wrapper.find('[aria-label="一级导航"]').exists()).toBe(false);
    expect(wrapper.get('.heading-actions').text()).toContain('登记');
    const density = wrapper.get('[aria-label="列表密度"]');
    expect(density.element).toHaveProperty('value', 'compact');
    await density.setValue('comfortable');
    expect(document.documentElement.dataset.tableDensity).toBe('comfortable');
  });

  it('uses the contextual task title for a non-primary route', async () => {
    window.history.replaceState({}, '', '/v2/digital-slides/case-1');
    vi.stubGlobal('scrollTo', vi.fn());
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        if (url.endsWith('/auth/config')) return new Response(JSON.stringify({ required: false }));
        return new Response(JSON.stringify({}), { status: 404 });
      }),
    );

    const wrapper = mount(App);
    await flushPromises();

    expect(wrapper.get('.topbar-page-label').text()).toBe('数字切片');
  });
});
