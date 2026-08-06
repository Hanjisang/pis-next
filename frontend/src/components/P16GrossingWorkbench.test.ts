import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';

import P16GrossingWorkbench from './P16GrossingWorkbench.vue';

describe('P16 grossing workbench', () => {
  it('renders queue, safety, sampling, planned block and label controls', () => {
    const wrapper = mount(P16GrossingWorkbench);

    expect(wrapper.text()).toContain('加载待取材队列');
    expect(wrapper.text()).toContain('身份核对与大体描述');
    expect(wrapper.text()).toContain('添加组织取样');
    expect(wrapper.text()).toContain('建立计划蜡块');
    expect(wrapper.text()).toContain('版本化预览与参考打印');
    expect(wrapper.text()).toContain('尚未确认物理打印成功');
    expect(wrapper.findAll('button')).toHaveLength(12);
  });
});
