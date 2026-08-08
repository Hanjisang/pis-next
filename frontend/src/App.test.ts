import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';

import App from './App.vue';

describe('PIS Next V2 workbench', () => {
  it('renders the V2 primary entry and role navigation', () => {
    const wrapper = mount(App);

    expect(wrapper.text()).toContain('PIS Next');
    expect(wrapper.text()).toContain('今天，从待办开始');
    expect(wrapper.text()).toContain('诊断');
    expect(wrapper.text()).toContain('归档借阅');
    expect(wrapper.findAll('.v2-nav-item')).toHaveLength(12);
  });
});
