import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';

import V2ImageViewer from './V2ImageViewer.vue';

describe('V2ImageViewer', () => {
  it('shows a clear adapter placeholder when only a viewer reference is available', () => {
    const wrapper = mount(V2ImageViewer, {
      props: { source: 'WSI://SYNTH-001', label: 'A1-HE', sourcePlatform: '合成阅片平台' },
    });
    expect(wrapper.text()).toContain('当前玻片暂无数字切片');
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

  it('records real normalized points and two-point measurements from the viewer', async () => {
    const wrapper = mount(V2ImageViewer, {
      props: { source: 'data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22/%3E' },
    });
    await flushPromises();
    const viewport = wrapper.get('.image-viewer-viewport');
    vi.spyOn(viewport.element, 'getBoundingClientRect').mockReturnValue({
      left: 10,
      top: 20,
      width: 200,
      height: 100,
      right: 210,
      bottom: 120,
      x: 10,
      y: 20,
      toJSON: () => ({}),
    });
    const viewer = wrapper.vm as unknown as {
      startAnnotation: () => void;
      startMeasurement: () => void;
    };

    viewer.startAnnotation();
    await viewport.trigger('click', { clientX: 60, clientY: 45 });
    expect(wrapper.emitted('annotation')?.[0]?.[0]).toMatchObject({
      x: 0.25,
      y: 0.25,
      coordinateSystem: 'NORMALIZED_IMAGE',
    });

    viewer.startMeasurement();
    await viewport.trigger('click', { clientX: 30, clientY: 40 });
    await viewport.trigger('click', { clientX: 170, clientY: 40 });
    expect(wrapper.emitted('measurement')?.[0]?.[0]).toMatchObject({
      x1: 0.1,
      y1: 0.2,
      x2: 0.8,
      y2: 0.2,
      value: 0.7,
      coordinateSystem: 'NORMALIZED_IMAGE',
    });
  });
});
