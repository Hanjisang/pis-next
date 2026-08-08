import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';

import V2TechnicalWorkbench from './V2TechnicalWorkbench.vue';

const project = {
  projectId: 'P-1',
  businessTypeId: 'BT-1',
  projectCode: 'IHC-KI67',
  projectName: 'Synthetic Ki67',
  enabled: true,
  allowedTargetTypes: ['BLOCK', 'SLIDE'],
  producesSlide: true,
  producesBlock: false,
  producesStructuredResult: false,
  defaultSlideType: 'IHC',
  parametersSchema: '{}',
  resultSchema: '{}',
  requiredBeforeSignOutDefault: true,
  configurationVersion: 1,
};

describe('V2TechnicalWorkbench', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('renders project configuration and the active technical order queue', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(new Response(JSON.stringify([project]), { status: 200 }))
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            orders: [
              {
                orderId: 'O-1',
                orderNo: 'TO001',
                diagnosisId: 'D-1',
                caseId: 'C-1',
                status: 'EXECUTING',
                requiredBeforeSignOut: true,
                blocking: true,
                version: 0,
                duplicate: false,
                items: [
                  {
                    itemId: 'I-1',
                    projectId: 'P-1',
                    projectCode: 'IHC-KI67',
                    projectName: 'Synthetic Ki67',
                    quantity: 1,
                    status: 'EXECUTING',
                    expectedCount: 1,
                    completedCount: 0,
                    targets: [],
                    outputs: [{ outputKind: 'SLIDE', outputId: 'L-1', occurrenceNo: 1 }],
                  },
                ],
              },
            ],
          }),
          { status: 200 },
        ),
      );
    vi.stubGlobal('fetch', fetchMock);

    const wrapper = mount(V2TechnicalWorkbench);
    await flushPromises();

    expect(wrapper.text()).toContain('Technical Workbench');
    expect(wrapper.text()).toContain('TechnicalProject Configuration');
    expect(wrapper.text()).toContain('TO001');
    expect(wrapper.text()).toContain('IHC-KI67');
    expect(wrapper.text()).toContain('BLOCKING');
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });
});
