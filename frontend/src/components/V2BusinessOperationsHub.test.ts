import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import V2BusinessOperationsHub from './V2BusinessOperationsHub.vue';

describe('V2BusinessOperationsHub', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('separates department, clinical, specialty and migration capabilities', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(
        async () =>
          new Response(
            JSON.stringify({
              schedules: [],
              procurements: [],
              requisitions: [],
              distributions: [],
              packages: [],
              molecularProjects: [],
              molecularTests: [],
              digitalArchives: [],
              regionalShares: [],
              income: [],
              migrationJobs: [],
            }),
          ),
      ),
    );
    const wrapper = mount(V2BusinessOperationsHub);
    await flushPromises();
    expect(wrapper.text()).toContain('科室管理');
    expect(wrapper.text()).toContain('业务管理');
    expect(wrapper.text()).toContain('专项业务');
    expect(wrapper.text()).toContain('数据迁移');
    const migration = wrapper
      .findAll('button')
      .find((button) => button.text().includes('数据迁移'));
    await migration?.trigger('click');
    expect(wrapper.text()).toContain('迁移任务、映射数量和失败记录');
    expect(wrapper.text()).toContain('不直接绕过应用命令修改核心业务表');
  });
});
