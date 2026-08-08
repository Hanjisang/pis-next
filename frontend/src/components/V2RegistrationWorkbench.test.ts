import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';

import V2RegistrationWorkbench from './V2RegistrationWorkbench.vue';

describe('V2RegistrationWorkbench', () => {
  it('exposes mutable specimen facts without workflow-state controls', () => {
    const wrapper = mount(V2RegistrationWorkbench);
    const text = wrapper.text();

    expect(text).toContain('按申请项目建立 ACTIVE 病例');
    expect(text).toContain('建立可修改的独立标本');
    expect(text).toContain('修改/软删除');
    expect(text).toContain('不维护病例/标本流程状态机');
    expect(text).toContain('不使用 RECEIVED、PROCESSING、COMPLETED 等标本流程状态');
    expect(text).not.toContain('记录实物到达');
    expect(text).not.toContain('核对身份、来源并接收');
    expect(wrapper.findAll('input[placeholder*="UUID"]').length).toBe(0);
  });
});
