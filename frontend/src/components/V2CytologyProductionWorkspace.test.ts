import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import V2CytologyProductionWorkspace from './V2CytologyProductionWorkspace.vue';
import { getV2Case } from '../v2Api';
import { generateV2RequiredCytologySlides, getV2MaterialTree } from '../v2MaterialApi';

vi.mock('../v2Api', () => ({
  getV2Case: vi.fn(),
}));

vi.mock('../v2MaterialApi', () => ({
  completeV2Slides: vi.fn().mockResolvedValue({ changedCount: 1, duplicate: false }),
  correctV2SlideCode: vi.fn(),
  createV2ExtraCytologySlide: vi.fn(),
  generateV2RequiredCytologySlides: vi.fn().mockResolvedValue({
    createdCount: 1,
    slides: [],
    duplicate: false,
  }),
  getV2MaterialTree: vi.fn(),
  locateV2Material: vi.fn(),
  performV2ProductionRework: vi.fn(),
  printV2Slides: vi.fn(),
  updateV2CytologyPreparation: vi.fn(),
}));

vi.mock('../v2HistologyApi', () => ({
  completeV2TechnicalTraceBatch: vi.fn(),
  recordV2HistologyException: vi.fn(),
}));

vi.mock('../v2ProductionWorkbenchApi', () => ({
  getV2ProductionWorkbench: vi.fn(),
}));

describe('V2CytologyProductionWorkspace', () => {
  const tree = {
    caseId: 'C-1',
    caseNo: 'CY-001',
    businessTypeCode: 'CYTOLOGY_NON_GYN',
    capability: {
      businessTypeCode: 'CYTOLOGY_NON_GYN',
      modalityCode: 'CYTOLOGY',
      requiresGrossing: false,
      supportsBlocks: false,
      supportsDirectSlides: true,
      usesHistologyProcessing: false,
      requiresSlideCompletion: true,
      diagnosisEnabled: true,
      initialSlideRule: null,
      productionCapabilities: ['DIRECT_SPECIMEN_SLIDE'],
    },
    specimens: [
      {
        specimenId: 'S-1',
        specimenNo: 'SP-1',
        specimenCode: '1',
        specimenName: '宫颈脱落细胞',
        specimenKindCode: 'FLUID',
        creationSourceCode: 'REGISTRATION',
        collectionSite: '宫颈',
        collectionMethodCode: '刷取',
        specimenDescription: null,
        preparationMethodCode: '液基',
        specimenConcurrencyVersion: 0,
        sourceSpecimenCode: null,
        grossMaterialDescription: null,
        grossSpecimenVersion: 0,
        blocks: [],
        directSlides: [],
      },
    ],
    initialRequiredCount: 1,
    initialCompletedCount: 0,
    initialProductionComplete: false,
    availableActions: ['GENERATE_CYTOLOGY_SLIDES', 'CREATE_EXTRA_CYTOLOGY_SLIDE', 'COMPLETE_SLIDE'],
  };

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getV2Case).mockResolvedValue({
      caseId: 'C-1',
      caseNo: 'CY-001',
      patientReference: 'Synthetic patient',
    } as never);
    vi.mocked(getV2MaterialTree).mockResolvedValue(tree as never);
  });

  it('renders zero-slide cytology directly from specimen and sends rule generation to the backend', async () => {
    const wrapper = mount(V2CytologyProductionWorkspace, { props: { caseId: 'C-1' } });
    await flushPromises();

    expect(wrapper.text()).toContain('CY-001');
    expect(wrapper.text()).toContain('宫颈脱落细胞');
    expect(wrapper.text()).toContain('待生成');
    expect(wrapper.text()).not.toContain('材块');
    expect(wrapper.text()).not.toContain('S-1');

    await wrapper.get('button.primary-button').trigger('click');
    await flushPromises();

    expect(generateV2RequiredCytologySlides).toHaveBeenCalledWith(
      expect.objectContaining({ caseId: 'C-1', specimenIds: ['S-1'] }),
    );
  });

  it('allows selecting multiple specimens for one rule-generation command', async () => {
    vi.mocked(getV2MaterialTree).mockResolvedValue({
      ...tree,
      specimens: [
        ...tree.specimens,
        {
          ...tree.specimens[0],
          specimenId: 'S-2',
          specimenNo: 'SP-2',
          specimenCode: '2',
          specimenName: '胸水',
        },
      ],
    } as never);
    const wrapper = mount(V2CytologyProductionWorkspace, { props: { caseId: 'C-1' } });
    await flushPromises();

    const specimenCheckboxes = wrapper.findAll('input[aria-label^="选择标本"]');
    expect(specimenCheckboxes).toHaveLength(2);
    await specimenCheckboxes[1]!.setValue(true);
    await wrapper.get('button.primary-button').trigger('click');
    await flushPromises();

    expect(generateV2RequiredCytologySlides).toHaveBeenCalledWith(
      expect.objectContaining({ caseId: 'C-1', specimenIds: ['S-1', 'S-2'] }),
    );
  });
});
