import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';

import V2RoutineProductionWorkspace from './V2RoutineProductionWorkspace.vue';

const caseId = '11111111-1111-1111-1111-111111111111';
const blockOne = '22222222-2222-2222-2222-222222222221';
const blockTwo = '22222222-2222-2222-2222-222222222222';
const slideId = '33333333-3333-3333-3333-333333333333';
const allActions = [
  'GENERATE_REQUIRED_SLIDES',
  'CREATE_EXTRA_SLIDE',
  'PRINT_SLIDE',
  'COMPLETE_SLIDE',
  'CORRECT_SLIDE_CODE',
  'CORRECT_SLIDE_COMPLETION',
  'CANCEL_SLIDE',
  'SCAN_MATERIAL',
  'RECORD_TECHNICAL_TRACE',
  'RECORD_PRODUCTION_EXCEPTION',
  'PERFORM_REWORK',
];

function installBackend(actions = allActions) {
  let completed = false;
  const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input);
    if (url.includes(`/registration/cases/${caseId}`)) {
      return new Response(
        JSON.stringify({
          caseId,
          caseNo: 'P20260001',
          businessTypeCode: 'HISTOLOGY',
          patientReference: '合成患者甲',
          visitReference: 'ZY10001',
          applicationNo: 'APP-001',
          lifecycleStateCode: 'ACTIVE',
          numberBindingActive: true,
          concurrencyVersion: 0,
          cancelledAt: null,
          cancelledByRef: null,
          cancellationReason: null,
          duplicate: false,
          eventTypeCode: 'READ',
        }),
      );
    }
    if (url.endsWith(`/cases/${caseId}/materials`)) {
      return new Response(
        JSON.stringify({
          caseId,
          caseNo: 'P20260001',
          businessTypeCode: 'HISTOLOGY',
          specimens: [
            {
              specimenId: '44444444-4444-4444-4444-444444444444',
              specimenNo: 'SP-001',
              specimenCode: '1',
              specimenName: '胃体组织',
              specimenKindCode: 'TISSUE',
              creationSourceCode: 'REGISTRATION',
              collectionSite: '胃体',
              specimenDescription: '胃体活检',
              sourceSpecimenCode: null,
              grossMaterialDescription: '灰白组织',
              grossSpecimenVersion: 0,
              blocks: [
                {
                  blockId: blockOne,
                  blockCode: 'A1',
                  blockType: 'ROUTINE',
                  samplingDescription: '胃体全取',
                  note: null,
                  concurrencyVersion: 0,
                  printCount: 1,
                  verificationStatus: 'PASSED',
                  slides: [],
                },
                {
                  blockId: blockTwo,
                  blockCode: 'A2',
                  blockType: 'ROUTINE',
                  samplingDescription: '胃窦全取',
                  note: null,
                  concurrencyVersion: 0,
                  printCount: 1,
                  verificationStatus: 'PASSED',
                  slides: [
                    {
                      slideId,
                      slideCode: 'A2-HE',
                      slideType: 'HE',
                      sourceContextType: 'INITIAL',
                      completedAt: completed ? '2026-08-13T01:00:00Z' : null,
                      completed,
                      required: true,
                      concurrencyVersion: completed ? 1 : 0,
                      printCount: 1,
                    },
                  ],
                },
              ],
              directSlides: [],
            },
          ],
          initialRequiredCount: 2,
          initialCompletedCount: completed ? 1 : 0,
          initialProductionComplete: false,
          availableActions: actions,
        }),
      );
    }
    if (url.includes('/histology-workbench')) {
      return new Response(
        JSON.stringify({
          refreshedAt: '2026-08-13T00:00:00Z',
          slides: [
            {
              slideId,
              caseId,
              caseNo: 'P20260001',
              patientReference: '合成患者甲',
              businessTypeCode: 'HISTOLOGY',
              specimenCode: '1',
              blockCode: 'A2',
              slideCode: 'A2-HE',
              slideType: 'HE',
              sourceContextType: 'INITIAL',
              slideCompletedAt: completed ? '2026-08-13T01:00:00Z' : null,
              concurrencyVersion: completed ? 1 : 0,
              printCount: 1,
              phases: [],
            },
          ],
        }),
      );
    }
    if (url.endsWith('/slides/complete-batch')) {
      completed = true;
      return new Response(JSON.stringify({ changedCount: 1, duplicate: false }));
    }
    throw new Error(`unexpected request ${url}`);
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

describe('V2RoutineProductionWorkspace', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('renders a dense block-slide table from backend capabilities without exposing UUIDs', async () => {
    installBackend();
    const wrapper = mount(V2RoutineProductionWorkspace, { props: { caseId } });
    await flushPromises();

    expect(wrapper.text()).toContain('P20260001');
    expect(wrapper.text()).toContain('合成患者甲');
    expect(wrapper.text()).toContain('A1');
    expect(wrapper.text()).toContain('待生成');
    expect(wrapper.text()).toContain('A2-HE');
    expect(wrapper.text()).toContain('技术记录（可选，不阻止玻片完成）');
    expect(wrapper.findAll('.material-row')).toHaveLength(3);
    expect(wrapper.text()).not.toContain(caseId);
    expect(wrapper.text()).not.toContain(blockOne);
    expect(wrapper.text()).not.toContain(slideId);
  });

  it('uses backend actions and batch selection to complete a slide without trace prerequisites', async () => {
    const fetchMock = installBackend();
    const wrapper = mount(V2RoutineProductionWorkspace, { props: { caseId } });
    await flushPromises();

    const slideCheckbox = wrapper.findAll('input[type="checkbox"]')[2];
    await slideCheckbox.setValue(true);
    const completeButton = wrapper
      .findAll('button')
      .find((button) => button.text() === '批量完成制片');
    expect(completeButton).toBeDefined();
    await completeButton!.trigger('click');
    await flushPromises();

    const request = fetchMock.mock.calls.find(([input]) =>
      String(input).endsWith('/slides/complete-batch'),
    );
    expect(request).toBeDefined();
    expect(wrapper.text()).toContain('技术记录不作为完成前置条件');
  });

  it('does not render write actions when the backend returns no available actions', async () => {
    installBackend([]);
    const wrapper = mount(V2RoutineProductionWorkspace, { props: { caseId } });
    await flushPromises();

    expect(wrapper.text()).not.toContain('按规则生成玻片');
    expect(wrapper.text()).not.toContain('批量完成制片');
    expect(wrapper.text()).not.toContain('技术记录（可选，不阻止玻片完成）');
    expect(wrapper.text()).not.toContain('执行返工');
  });
});
