import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';

import V2ConfigurationHub from './V2ConfigurationHub.vue';

const configuration = {
  businessTypes: [],
  applicationItemMappings: [],
  pathologyNumberRules: [],
  technicalProjects: [],
  diagnosisTemplates: [],
  reportTemplates: [],
};

describe('V2ConfigurationHub', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('creates and edits scoped subspecialty assignment rules with a daily limit', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/configuration')) return new Response(JSON.stringify(configuration));
      if (url.endsWith('/custody/locations')) return new Response(JSON.stringify([]));
      if (url.endsWith('/assignment-rules') && !init?.method) {
        return new Response(JSON.stringify([]));
      }
      if (url.endsWith('/assignment-rules') && init?.method === 'POST') {
        const body = JSON.parse(init.body as string);
        return new Response(
          JSON.stringify({ assignmentRuleId: 'RULE-1', version: 0, duplicate: false, ...body }),
        );
      }
      throw new Error(`Unexpected request: ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);

    const wrapper = mount(V2ConfigurationHub);
    await flushPromises();
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '自动分诊规则')
      ?.trigger('click');
    const requiredInputs = wrapper.findAll('input[required]');
    await requiredInputs[4]?.setValue('GI');
    await requiredInputs[5]?.setValue('doctor-a');
    const numericInputs = wrapper.findAll('input[type="number"]');
    await numericInputs[1]?.setValue(12);
    await wrapper.find('form').trigger('submit');
    await flushPromises();

    const createCall = fetchMock.mock.calls.find(
      ([input, init]) => String(input).endsWith('/assignment-rules') && init?.method === 'POST',
    );
    expect(createCall).toBeTruthy();
    expect(JSON.parse(createCall?.[1]?.body as string)).toMatchObject({
      businessTypeCode: 'HISTOLOGY',
      diagnosisGroup: 'GI',
      doctorId: 'doctor-a',
      dailyCaseLimit: 12,
      idempotencyKey: expect.any(String),
    });
    expect(wrapper.text()).toContain('自动分诊规则已创建');
  });
});
