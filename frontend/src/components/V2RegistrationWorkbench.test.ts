import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';

import V2RegistrationWorkbench from './V2RegistrationWorkbench.vue';

describe('V2RegistrationWorkbench', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('offers application intake and item-level registration in one focused workspace', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        if (url.includes('/application-item-mappings')) {
          return new Response(
            JSON.stringify([
              {
                applicationItemCode: 'SYNTH-HISTOLOGY',
                defaultSpecimenKindCode: 'TISSUE',
                businessTypeCode: 'HISTOLOGY',
                businessTypeName: '常规组织病理',
              },
              {
                applicationItemCode: 'SYNTH-CYTOLOGY',
                defaultSpecimenKindCode: 'CYTOLOGY',
                businessTypeCode: 'CYTOLOGY',
                businessTypeName: '细胞病理',
              },
            ]),
          );
        }
        if (url.includes('/api/v2/applications/queue')) return new Response('[]');
        throw new Error(`unexpected request ${url}`);
      }),
    );

    const wrapper = mount(V2RegistrationWorkbench, {
      props: {
        authUser: {
          userId: 'U-REG',
          username: 'registrar-a',
          displayName: '登记员甲',
          roleCode: 'REGISTRAR',
          permissions: [
            'P14-PERM-002',
            'P14-PERM-003',
            'P14-PERM-004',
            'P14-PERM-008',
            'P14-PERM-009',
            'P14-PERM-010',
            'P14-PERM-048',
          ],
        },
      },
    });
    await flushPromises();

    expect(wrapper.text()).toContain('待登记申请');
    expect(wrapper.text()).toContain('送检扫码与记录');
    await wrapper.get('button.primary-button').trigger('click');

    expect(wrapper.text()).toContain('获取或人工补录患者信息');
    expect(wrapper.text()).toContain('申请与临床资料');
    expect(wrapper.text()).toContain('项目与送检标本');
    expect(wrapper.text()).not.toContain('Application UUID');
    expect(wrapper.text()).not.toContain('Case UUID');

    const addItem = wrapper
      .findAll('button')
      .find((button) => button.text().includes('新增申请项目'));
    await addItem?.trigger('click');
    expect(wrapper.findAll('.application-item-row')).toHaveLength(2);
  });
});
