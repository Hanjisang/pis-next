import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';

import V2ImageViewer from './V2ImageViewer.vue';

describe('V2ImageViewer', () => {
  it('shows a clear adapter placeholder when only a viewer reference is available', () => {
    const wrapper = mount(V2ImageViewer, {
      props: { source: 'WSI://SYNTH-001', label: 'A1-HE', sourcePlatform: '合成阅片平台' },
    });

    expect(wrapper.text()).toContain('已准备好阅片入口');
    expect(wrapper.text()).toContain('合成阅片平台');
    expect(wrapper.get('a').attributes('href')).toBe('WSI://SYNTH-001');
  });

  it('keeps zoom and reset controls inside the current workspace', async () => {
    const wrapper = mount(V2ImageViewer, {
      props: { source: 'data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22/%3E' },
    });

    await wrapper.get('[aria-label="放大"]').trigger('click');
    expect(wrapper.get('[aria-live="polite"]').text()).toBe('125%');
    await wrapper.get('[aria-label="还原视图"]').trigger('click');
    expect(wrapper.get('[aria-live="polite"]').text()).toBe('100%');
  });
});
