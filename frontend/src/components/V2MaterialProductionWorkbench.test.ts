import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';

import V2MaterialProductionWorkbench from './V2MaterialProductionWorkbench.vue';

describe('V2MaterialProductionWorkbench', () => {
  it('exposes the independent Grossing, Block, Slide and Material Tree chain', () => {
    const wrapper = mount(V2MaterialProductionWorkbench);
    const text = wrapper.text();

    expect(text).toContain('Grossing / Block 材料生产');
    expect(text).toContain('关联多个 Specimen');
    expect(text).toContain('创建统一 Block');
    expect(text).toContain('Slide 生产与材料树');
    expect(text).toContain('INITIAL');
    expect(text).toContain('打印/重打');
    expect(text).not.toContain('ProcessingTask');
    expect(text).not.toContain('EmbeddingTask');
    expect(text).not.toContain('PlannedBlock');
    expect(text).not.toContain('ActualSlide');
  });
});
