import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';

import App from './App.vue';

describe('PIS Next P15 workbench', () => {
  it('renders the registration and receiving workflow', () => {
    const wrapper = mount(App);

    expect(wrapper.text()).toContain('PIS Next');
    expect(wrapper.text()).toContain('登记与标本接收');
    expect(wrapper.findAll('.business-card')).toHaveLength(4);
  });
});
