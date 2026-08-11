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

    expect(wrapper.text()).toContain('PIS Next');
    expect(wrapper.text()).toContain('工作台 · 人的工作中心');
    expect(wrapper.text()).toContain('待登记申请');
    expect(wrapper.find('[aria-label="一级导航"]').text()).toBe('工作台');
    expect(wrapper.get('.heading-actions').text()).toContain('登记');
    expect(wrapper.find('[aria-label="一级导航"]').text()).not.toContain('诊断');
    expect(wrapper.find('[aria-label="一级导航"]').text()).not.toContain('系统管理');
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

    expect(wrapper.get('.topbar-title h1').text()).toBe('数字切片');
  });
});
