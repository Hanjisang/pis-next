import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';

import P17TechnicalProcessingWorkbench from './P17TechnicalProcessingWorkbench.vue';

describe('P17 technical processing workbench', () => {
  it('renders queue, execution facts and actual block controls', () => {
    const wrapper = mount(P17TechnicalProcessingWorkbench);

    expect(wrapper.text()).toContain('组织处理与包埋工作台');
    expect(wrapper.text()).toContain('加载 P17 队列');
    expect(wrapper.text()).toContain('接收原始执行结果');
    expect(wrapper.text()).toContain('人工确认有效结果');
    expect(wrapper.text()).toContain('确认包埋完成');
    expect(wrapper.text()).toContain('尚不产生实际蜡块');
    expect(wrapper.findAll('button')).toHaveLength(14);
  });
});
