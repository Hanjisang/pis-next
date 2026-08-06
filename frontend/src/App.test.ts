import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';

import App from './App.vue';

describe('PIS Next foundation screen', () => {
  it('renders the P13 module catalog', () => {
    const wrapper = mount(App);

    expect(wrapper.text()).toContain('PIS Next');
    expect(wrapper.text()).toContain('15 个责任模块');
    expect(wrapper.findAll('.module-card')).toHaveLength(15);
  });
});
