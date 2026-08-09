import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';

import V2RegistrationWorkbench from './V2RegistrationWorkbench.vue';

describe('V2RegistrationWorkbench', () => {
  it('keeps registration and multi-specimen maintenance in one workspace', async () => {
    const wrapper = mount(V2RegistrationWorkbench);

    expect(wrapper.text()).toContain('核对申请并登记');
    expect(wrapper.text()).toContain('患者 / 就诊');
    expect(wrapper.text()).toContain('业务类型与编号');
    expect(wrapper.text()).toContain('标本信息');
    expect(wrapper.text()).not.toContain('BusinessType');
    expect(wrapper.text()).not.toContain('Specimen');

    const addButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('新增标本'));
    await addButton?.trigger('click');
    expect(wrapper.findAll('.specimen-row-editor')).toHaveLength(2);

    const copyButton = wrapper.findAll('button').find((button) => button.text() === '复制');
    await copyButton?.trigger('click');
    expect(wrapper.findAll('.specimen-row-editor')).toHaveLength(3);
    expect(wrapper.text()).toContain('3 个标本');

    await wrapper.get('[aria-label="业务类型"]').setValue('CONSULTATION');
    expect(wrapper.findAll('.specimen-row-editor')).toHaveLength(0);
    expect(wrapper.text()).toContain('会诊病例可在后续登记外院玻片或蜡块');
  });
});
