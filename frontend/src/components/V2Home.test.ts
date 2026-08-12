import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';

import V2Home from './V2Home.vue';

const queues = [
  {
    key: 'REGISTRATION_PENDING',
    label: '待登记',
    kind: 'PENDING',
    count: 1,
    items: [
      {
        key: 'REGISTRATION-item-1',
        caseId: null,
        applicationId: 'internal-application-uuid',
        applicationItemId: 'internal-item-uuid',
        businessDisplayId: 'APP-2026-001',
        patientDisplay: '合成患者甲',
        patientSummary: '女 · 46岁 · 住院 VISIT-001 · 外科',
        visitReference: 'VISIT-001',
        businessType: '常规组织病理',
        task: '常规组织病理',
        detail: '胃组织',
        enteredAt: '2026-08-12T08:00:00Z',
        waitingMinutes: 125,
        urgent: false,
        availableActions: ['OPEN', 'REGISTER'],
        workspaceDestination:
          '/v2/registration?applicationId=internal-application-uuid&applicationItemId=internal-item-uuid',
      },
    ],
  },
  { key: 'REGISTERED_TODAY', label: '我今天登记', kind: 'TRACKING', count: 0, items: [] },
];

describe('V2 personal workbench', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    sessionStorage.clear();
    window.history.replaceState({}, '', '/v2/workbench');
  });

  it('renders only backend queues, switches compact zero queue, and hides UUIDs', async () => {
    vi.stubGlobal('scrollTo', vi.fn());
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response(JSON.stringify({ capabilityQueues: queues }))),
    );
    const wrapper = mount(V2Home, { props: { authUser: null } });
    await flushPromises();

    expect(wrapper.text()).toContain('待登记');
    expect(wrapper.text()).toContain('我今天登记');
    expect(wrapper.text()).not.toContain('待初诊');
    expect(wrapper.text()).toContain('APP-2026-001');
    expect(wrapper.text()).toContain('2小时5分钟');
    expect(wrapper.text()).not.toContain('internal-application-uuid');

    await wrapper.get('button[aria-pressed="false"]').trigger('click');
    expect(wrapper.text()).toContain('我今天登记 0');
  });

  it('opens the row primary action with workbench navigation context', async () => {
    vi.stubGlobal('scrollTo', vi.fn());
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response(JSON.stringify({ capabilityQueues: queues }))),
    );
    const wrapper = mount(V2Home, { props: { authUser: null } });
    await flushPromises();
    await wrapper.get('.workbench-dense-row').trigger('click');
    const path = wrapper.emitted('navigate')?.[0]?.[0] as string;
    expect(path).toContain('/v2/registration?');
    expect(path).toContain('origin=workbench');
    expect(path).toContain('queue=REGISTRATION_PENDING');
    expect(path).toContain('returnTo=');
  });
});
