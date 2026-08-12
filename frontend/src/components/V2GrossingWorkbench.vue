<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';

import { currentRecorder, type V2AuthUser } from '../auth';
import {
  appendNavigationContext,
  safeLocalPath,
  workspaceBackLabel,
  workspaceBackTarget,
  type V2Route,
} from '../navigation';
import { friendlyError, formatDateTime, idempotencyKey, specimenKindName } from '../uiText';
import {
  getV2Specimen,
  registerV2Specimen,
  softDeleteV2Specimen,
  splitV2Specimen,
  updateV2Specimen,
  type V2SpecimenResult,
} from '../v2Api';
import { getV2MyWorkbench, type V2CapabilityQueueItem } from '../v2WorkspaceApi';
import {
  associateV2Specimen,
  completeV2Grossing,
  correctV2Grossing,
  captureV2GrossingImage,
  createV2GrossingAnnotation,
  deleteV2GrossingImage,
  createV2Block,
  createV2Blocks,
  createV2Grossing,
  getV2GrossingAnnotations,
  getV2GrossingImages,
  getV2GrossingMeasurements,
  getV2GrossingWorkspace,
  measureV2GrossingImage,
  printV2Block,
  printV2Blocks,
  softDeleteV2Block,
  updateV2Block,
  updateV2Grossing,
  updateV2GrossingSpecimen,
  verifyV2Block,
  type V2GrossingAnnotation,
  type V2GrossingImage,
  type V2GrossingMeasurement,
  type V2GrossingWorkspace,
} from '../v2MaterialApi';
import V2CaseHeader from './V2CaseHeader.vue';
import V2HistoryDrawer from './V2HistoryDrawer.vue';

const props = withDefaults(
  defineProps<{
    sourceType?: string;
    sourceReferenceId?: string;
    authUser?: V2AuthUser | null;
    origin?: V2Route['origin'];
    queue?: string;
    returnTo?: string;
  }>(),
  {
    sourceType: 'INITIAL',
    sourceReferenceId: undefined,
    authUser: null,
    origin: 'direct',
    queue: '',
    returnTo: '',
  },
);

const emit = defineEmits<{ navigate: [path: string] }>();
const caseId = defineModel<string>('caseId', { default: '' });
const lookupCaseId = ref(caseId.value);
const workspace = ref<V2GrossingWorkspace | null>(null);
const specimenDetails = ref<Record<string, V2SpecimenResult>>({});
const selectedSpecimenId = ref('');
const specimenSiteDraft = ref('');
const specimenNameDraft = ref('');
const specimenDescriptionDraft = ref('');
const specimenCorrectionReason = ref('');
const grossSpecimenDrafts = ref<Record<string, string>>({});
const addSpecimenOpen = ref(false);
const newSpecimen = ref({ code: '', name: '', site: '', description: '', reason: '' });
const splitSpecimenOpen = ref(false);
const splitChildren = ref([
  { code: '', name: '', site: '', description: '' },
  { code: '', name: '', site: '', description: '' },
]);
const splitReason = ref('');
const grossDescription = ref('');
const grossingInstruction = ref('');
const newBlockCode = ref('');
const editingBlockId = ref('');
const editingBlockCode = ref('');
const editingBlockDescription = ref('');
const editingBlockNote = ref('');
const editingBlockReason = ref('');
const newBlockDescription = ref('');
const selectedBlockIds = ref<string[]>([]);
const busy = ref(false);
const loading = ref(false);
const error = ref('');
const notice = ref('');
const doctors = ref<Array<{ id: string; displayName: string; title?: string | null }>>([]);
const selectedDoctorId = ref(props.authUser?.doctor?.id ?? '');
const historyDrawerOpen = ref(false);
const nextWorkbenchItem = ref<V2CapabilityQueueItem | null>(null);
const grossingImages = ref<V2GrossingImage[]>([]);
const selectedImageId = ref('');
const imageBusy = ref(false);
const imageAnnotationNote = ref('');
const imageAnnotations = ref<V2GrossingAnnotation[]>([]);
const imageMeasurements = ref<V2GrossingMeasurement[]>([]);
const imageDeletionReason = ref('');
const measurementValue = ref<number | null>(null);
const measurementUnit = ref<'MM' | 'CM'>('MM');
const selectedImage = computed(
  () => grossingImages.value.find((image) => image.imageId === selectedImageId.value) ?? null,
);
const correctionOpen = ref(false);
const correctionReason = ref('');
const WORKBENCH_STATE_KEY = 'pis-v2-my-workbench-state';
const backLabel = computed(() => workspaceBackLabel(props.origin));
const backTarget = computed(() => workspaceBackTarget(props, caseId.value));
const caseOverviewTarget = computed(() => {
  if (props.origin === 'case' && safeLocalPath(props.returnTo)) return props.returnTo;
  const path = `/v2/cases/${encodeURIComponent(caseId.value)}`;
  return props.origin === 'workbench'
    ? appendNavigationContext(path, {
        origin: 'workbench',
        queue: props.queue,
        returnTo: props.returnTo,
      })
    : path;
});

function contextualPath(path: string) {
  return appendNavigationContext(path, {
    origin: props.origin,
    queue: props.queue,
    returnTo: props.returnTo,
  });
}

const currentSpecimen = computed(
  () =>
    workspace.value?.specimens.find((item) => item.specimenId === selectedSpecimenId.value) ?? null,
);
const currentSpecimenDetail = computed(() =>
  selectedSpecimenId.value ? specimenDetails.value[selectedSpecimenId.value] : undefined,
);
const currentBlocks = computed(() => currentSpecimen.value?.blocks ?? []);
const currentDoctor = computed(
  () =>
    props.authUser?.doctor ??
    doctors.value.find((doctor) => doctor.id === selectedDoctorId.value) ??
    null,
);
const canEdit = computed(() =>
  Boolean(
    workspace.value?.grossing && !workspace.value.grossing.completedAt && can('GROSSING_UPDATE'),
  ),
);
const canStart = computed(() =>
  Boolean(
    workspace.value &&
      !workspace.value.grossing &&
      selectedDoctorId.value &&
      grossDescription.value.trim() &&
      can('GROSSING_START'),
  ),
);
const canEditGrossFields = computed(
  () =>
    canEdit.value ||
    correctionOpen.value ||
    Boolean(!workspace.value?.grossing && can('GROSSING_START')),
);
const materialProgress = computed(() => {
  const slides =
    workspace.value?.specimens.flatMap((specimen) => [
      ...specimen.directSlides,
      ...specimen.blocks.flatMap((block) => block.slides),
    ]) ?? [];
  return `${slides.filter((slide) => slide.completed).length}/${slides.length}`;
});

function can(action: string) {
  return workspace.value?.availableActions.includes(action) ?? false;
}

function nextItemInOriginalQueue(items: V2CapabilityQueueItem[]) {
  let saved: {
    queue?: string;
    filter?: string;
    department?: string;
    businessType?: string;
    from?: string;
    to?: string;
    sort?: 'priority' | 'newest';
  } = {};
  try {
    saved = JSON.parse(sessionStorage.getItem(WORKBENCH_STATE_KEY) ?? '{}') as typeof saved;
  } catch {
    saved = {};
  }
  if (saved.queue && saved.queue !== props.queue) return items[0] ?? null;
  const itemText = (item: V2CapabilityQueueItem) =>
    [
      item.businessDisplayId,
      item.patientDisplay,
      item.patientSummary,
      item.visitReference,
      item.businessType,
      item.task,
      item.detail,
    ]
      .filter(Boolean)
      .join(' ')
      .toLocaleLowerCase();
  const needle = saved.filter?.trim().toLocaleLowerCase() ?? '';
  const department = saved.department?.trim().toLocaleLowerCase() ?? '';
  const start = saved.from ? new Date(`${saved.from}T00:00:00`).getTime() : null;
  const end = saved.to ? new Date(`${saved.to}T23:59:59.999`).getTime() : null;
  return (
    items
      .filter((item) => {
        const text = itemText(item);
        const entered = new Date(item.enteredAt).getTime();
        return (
          (!needle || text.includes(needle)) &&
          (!department || text.includes(department)) &&
          (!saved.businessType || item.businessType === saved.businessType) &&
          (start === null || entered >= start) &&
          (end === null || entered <= end)
        );
      })
      .sort((left, right) => {
        if (saved.sort === 'newest') {
          return new Date(right.enteredAt).getTime() - new Date(left.enteredAt).getTime();
        }
        if (left.urgent !== right.urgent) return left.urgent ? -1 : 1;
        return right.waitingMinutes - left.waitingMinutes;
      })[0] ?? null
  );
}

watch(
  () => caseId.value,
  (value) => {
    lookupCaseId.value = value;
    if (value) void loadWorkspace();
  },
  { immediate: true },
);

async function run(action: () => Promise<void>) {
  busy.value = true;
  error.value = '';
  notice.value = '';
  try {
    await action();
  } catch (requestError) {
    error.value = friendlyError(requestError, '取材操作未完成，请刷新后重试。');
  } finally {
    busy.value = false;
  }
}

async function loadWorkspace() {
  if (!caseId.value.trim()) {
    workspace.value = null;
    return;
  }
  loading.value = true;
  error.value = '';
  try {
    workspace.value = await getV2GrossingWorkspace(
      caseId.value.trim(),
      props.sourceType,
      props.sourceReferenceId,
    );
    const details = await Promise.all(
      workspace.value.specimens.map(async (specimen) => {
        try {
          return await getV2Specimen(specimen.specimenId);
        } catch {
          return null;
        }
      }),
    );
    specimenDetails.value = Object.fromEntries(
      details
        .filter((detail): detail is V2SpecimenResult => Boolean(detail))
        .map((detail) => [detail.specimenId, detail]),
    );
    grossSpecimenDrafts.value = Object.fromEntries(
      workspace.value.specimens.map((specimen) => [
        specimen.specimenId,
        specimen.grossMaterialDescription ?? specimen.specimenDescription ?? '',
      ]),
    );
    selectedSpecimenId.value ||= workspace.value.specimens[0]?.specimenId ?? '';
    specimenSiteDraft.value = specimenDetails.value[selectedSpecimenId.value]?.collectionSite ?? '';
    specimenNameDraft.value = specimenDetails.value[selectedSpecimenId.value]?.specimenName ?? '';
    specimenDescriptionDraft.value =
      specimenDetails.value[selectedSpecimenId.value]?.description ?? '';
    if (workspace.value.grossing) {
      selectedDoctorId.value = workspace.value.grossing.grossingDoctorId;
      grossDescription.value = workspace.value.grossing.grossDescription;
      grossingInstruction.value = workspace.value.grossing.grossingInstruction ?? '';
      grossingImages.value = await getV2GrossingImages(workspace.value.grossing.grossingId);
      if (!grossingImages.value.some((image) => image.imageId === selectedImageId.value)) {
        selectedImageId.value = grossingImages.value[0]?.imageId ?? '';
      }
      await loadImageFacts();
    } else {
      grossingImages.value = [];
      selectedImageId.value = '';
    }
    setSuggestedBlockCode();
  } catch (requestError) {
    workspace.value = null;
    error.value = friendlyError(requestError, '未找到该病例，请检查病理号或病例标识。');
  } finally {
    loading.value = false;
  }
}

async function captureGrossingImage() {
  const grossing = workspace.value?.grossing;
  if (!grossing) return;
  imageBusy.value = true;
  error.value = '';
  try {
    const image = await captureV2GrossingImage({
      grossingId: grossing.grossingId,
      specimenId: selectedSpecimenId.value || undefined,
      deviceReference: 'SIMULATOR-GROSS-IMAGING',
    });
    grossingImages.value = [image, ...grossingImages.value];
    selectedImageId.value = image.imageId;
    notice.value = '大体图像已保存，可继续添加标注和测量。';
  } catch (requestError) {
    error.value = friendlyError(requestError, '大体图像采集失败，请稍后重试。');
  } finally {
    imageBusy.value = false;
  }
}

async function annotateSelectedImage() {
  if (!selectedImageId.value || !imageAnnotationNote.value.trim()) return;
  imageBusy.value = true;
  error.value = '';
  try {
    await createV2GrossingAnnotation({
      imageId: selectedImageId.value,
      annotationTypeCode: 'POINT',
      geometryJson: JSON.stringify({ x: 0.5, y: 0.5 }),
      label: '取材标注',
      note: imageAnnotationNote.value.trim(),
    });
    imageAnnotationNote.value = '';
    await loadImageFacts();
    notice.value = '大体图像标注已保存。';
  } catch (requestError) {
    error.value = friendlyError(requestError, '大体图像标注失败，请稍后重试。');
  } finally {
    imageBusy.value = false;
  }
}

async function loadImageFacts() {
  if (!selectedImageId.value) {
    imageAnnotations.value = [];
    imageMeasurements.value = [];
    return;
  }
  [imageAnnotations.value, imageMeasurements.value] = await Promise.all([
    getV2GrossingAnnotations(selectedImageId.value),
    getV2GrossingMeasurements(selectedImageId.value),
  ]);
}

async function saveMeasurement() {
  if (!selectedImageId.value || measurementValue.value === null || measurementValue.value < 0)
    return;
  imageBusy.value = true;
  error.value = '';
  try {
    await measureV2GrossingImage({
      imageId: selectedImageId.value,
      geometryJson: JSON.stringify({ x1: 0.2, y1: 0.5, x2: 0.8, y2: 0.5 }),
      value: measurementValue.value,
      unitCode: measurementUnit.value,
      measurementModeCode: 'IMAGE_COORDINATE',
    });
    measurementValue.value = null;
    await loadImageFacts();
    notice.value = '长度测量已保存。';
  } catch (requestError) {
    error.value = friendlyError(requestError, '长度测量未保存，请检查输入。');
  } finally {
    imageBusy.value = false;
  }
}

async function deleteSelectedImage() {
  if (!selectedImageId.value || !imageDeletionReason.value.trim()) return;
  imageBusy.value = true;
  error.value = '';
  try {
    await deleteV2GrossingImage({
      imageId: selectedImageId.value,
      reason: imageDeletionReason.value.trim(),
    });
    imageDeletionReason.value = '';
    grossingImages.value = grossingImages.value.filter(
      (image) => image.imageId !== selectedImageId.value,
    );
    selectedImageId.value = grossingImages.value[0]?.imageId ?? '';
    notice.value = '误拍图像已取消显示，操作历史已保留。';
  } catch (requestError) {
    error.value = friendlyError(requestError, '图像取消失败，请刷新后重试。');
  } finally {
    imageBusy.value = false;
  }
}

watch(selectedImageId, () => void loadImageFacts());

async function loadDoctors() {
  try {
    const response = await fetch('/api/v2/auth/doctors');
    if (!response.ok) return;
    doctors.value = (await response.json()) as typeof doctors.value;
    selectedDoctorId.value ||= props.authUser?.doctor?.id ?? doctors.value[0]?.id ?? '';
  } catch {
    doctors.value = [];
  }
}

function openCase() {
  caseId.value = lookupCaseId.value.trim();
  if (caseId.value) void loadWorkspace();
}

function selectSpecimen(specimenId: string) {
  selectedSpecimenId.value = specimenId;
  specimenSiteDraft.value = specimenDetails.value[specimenId]?.collectionSite ?? '';
  specimenNameDraft.value = specimenDetails.value[specimenId]?.specimenName ?? '';
  specimenDescriptionDraft.value = specimenDetails.value[specimenId]?.description ?? '';
  setSuggestedBlockCode();
}

function saveSpecimenDetails() {
  const detail = currentSpecimenDetail.value;
  if (!detail || !specimenNameDraft.value.trim()) return;
  void run(async () => {
    const updated = await updateV2Specimen({
      ...detail,
      specimenName: specimenNameDraft.value.trim(),
      collectionSite: specimenSiteDraft.value.trim(),
      description: specimenDescriptionDraft.value.trim(),
      labelCode: detail.labelCode ?? '',
      expectedVersion: detail.concurrencyVersion,
      reason: specimenCorrectionReason.value.trim() || undefined,
    });
    specimenDetails.value = { ...specimenDetails.value, [updated.specimenId]: updated };
    specimenCorrectionReason.value = '';
    notice.value = `标本 ${updated.specimenCode} 信息已保存。`;
    await loadWorkspace();
  });
}

function openAddSpecimen() {
  const next = (workspace.value?.specimens.length ?? 0) + 1;
  newSpecimen.value = { code: String(next), name: '', site: '', description: '', reason: '' };
  addSpecimenOpen.value = true;
}

function addSpecimen() {
  if (!workspace.value || !newSpecimen.value.code.trim() || !newSpecimen.value.name.trim()) return;
  void run(async () => {
    const created = await registerV2Specimen({
      caseId: workspace.value!.caseId,
      specimenCode: newSpecimen.value.code.trim(),
      specimenName: newSpecimen.value.name.trim(),
      specimenKindCode: currentSpecimen.value?.specimenKindCode ?? 'TISSUE',
      creationSourceCode: 'GROSSING_ADD',
      sourceKindCode: 'LOCAL',
      sourceReference: `GROSSING-ADD:${workspace.value!.caseNo}`,
      collectionSite: newSpecimen.value.site.trim(),
      collectionMethodCode: 'GROSSING_OBSERVATION',
      description: newSpecimen.value.description.trim(),
      labelCode: '',
      creationReason: newSpecimen.value.reason.trim(),
      idempotencyKey: idempotencyKey('fc02b-specimen-add'),
    });
    if (workspace.value!.grossing) {
      await associateV2Specimen({
        grossingId: workspace.value!.grossing!.grossingId,
        specimenId: created.specimenId,
        materialDescription: newSpecimen.value.description.trim() || created.specimenName,
        idempotencyKey: idempotencyKey('fc02b-specimen-add-associate'),
      });
    }
    addSpecimenOpen.value = false;
    notice.value = `标本 ${created.specimenCode} 已新增。`;
    await loadWorkspace();
    selectSpecimen(created.specimenId);
  });
}

function openSplitSpecimen() {
  const source = currentSpecimen.value;
  if (!source) return;
  const base = workspace.value?.specimens.length ?? 0;
  splitChildren.value = [
    { code: String(base + 1), name: '', site: '', description: '' },
    { code: String(base + 2), name: '', site: '', description: '' },
  ];
  splitReason.value = '';
  splitSpecimenOpen.value = true;
}

function splitCurrentSpecimen() {
  const source = currentSpecimen.value;
  if (!source || !splitReason.value.trim()) return;
  const children = splitChildren.value.filter((child) => child.code.trim() && child.name.trim());
  if (!children.length) return;
  void run(async () => {
    for (const child of children) {
      const created = await splitV2Specimen({
        specimenId: source.specimenId,
        childSpecimenCode: child.code.trim(),
        childSpecimenName: child.name.trim(),
        specimenKindCode: source.specimenKindCode,
        collectionSite: child.site.trim(),
        description: child.description.trim(),
        reason: splitReason.value.trim(),
      });
      if (workspace.value?.grossing) {
        await associateV2Specimen({
          grossingId: workspace.value.grossing.grossingId,
          specimenId: created.specimenId,
          materialDescription: child.description.trim() || created.specimenName,
          idempotencyKey: idempotencyKey('fc02b-specimen-split-associate'),
        });
      }
    }
    splitSpecimenOpen.value = false;
    notice.value = `已从标本 ${source.specimenCode} 拆分 ${children.length} 个新标本，原标本及既有材料保持不变。`;
    await loadWorkspace();
  });
}

function cancelCurrentSpecimen() {
  const detail = currentSpecimenDetail.value;
  if (!detail || !specimenCorrectionReason.value.trim()) return;
  void run(async () => {
    await softDeleteV2Specimen({
      specimenId: detail.specimenId,
      expectedVersion: detail.concurrencyVersion,
      reason: specimenCorrectionReason.value.trim(),
    });
    notice.value = `误录标本 ${detail.specimenCode} 已取消，历史记录已保留。`;
    selectedSpecimenId.value = '';
    await loadWorkspace();
  });
}

function saveCurrentGrossDescription() {
  const grossing = workspace.value?.grossing;
  const specimen = currentSpecimen.value;
  if (!grossing || !specimen) return;
  const description = grossSpecimenDrafts.value[specimen.specimenId]?.trim();
  if (!description) return;
  void run(async () => {
    await updateV2GrossingSpecimen({
      grossingId: grossing.grossingId,
      specimenId: specimen.specimenId,
      materialDescription: description,
      expectedVersion: specimen.grossSpecimenVersion,
      reason: workspace.value?.grossing?.completedAt ? correctionReason.value.trim() : undefined,
    });
    notice.value = `标本 ${specimen.specimenCode} 的大体所见已保存。`;
    await loadWorkspace();
  });
}

function setSuggestedBlockCode() {
  const specimen = currentSpecimen.value;
  if (!specimen) {
    newBlockCode.value = '';
    return;
  }
  newBlockCode.value = `${specimen.specimenCode}${specimen.blocks.length + 1}`;
}

function beginGrossing() {
  if (!canStart.value || !workspace.value) return;
  void run(async () => {
    const created = await createV2Grossing({
      caseId: workspace.value!.caseId,
      sourceType: props.sourceType,
      sourceReferenceId: props.sourceReferenceId,
      grossDescription: grossDescription.value.trim(),
      grossingInstruction: grossingInstruction.value.trim(),
      grossingDoctorId: selectedDoctorId.value,
      recorderId: currentRecorder(props.authUser ?? null),
      idempotencyKey: idempotencyKey('ux01-grossing-start'),
    });
    for (const specimen of workspace.value!.specimens) {
      await associateV2Specimen({
        grossingId: created.grossingId,
        specimenId: specimen.specimenId,
        materialDescription: grossSpecimenDrafts.value[specimen.specimenId]?.trim() || '',
        idempotencyKey: idempotencyKey('ux01-grossing-specimen'),
      });
    }
    notice.value = `已开始取材，${workspace.value!.specimens.length} 个标本已加入本次取材。`;
    await loadWorkspace();
  });
}

async function saveDetails(showNotice = true) {
  const current = workspace.value?.grossing;
  if (!current || current.completedAt) return;
  const updated = await updateV2Grossing({
    grossingId: current.grossingId,
    grossDescription: grossDescription.value.trim(),
    grossingInstruction: grossingInstruction.value.trim(),
    grossingDoctorId: selectedDoctorId.value || current.grossingDoctorId,
    recorderId: currentRecorder(props.authUser ?? null) || current.recorderId,
    expectedVersion: current.concurrencyVersion,
    idempotencyKey: idempotencyKey('ux01-grossing-save'),
  });
  current.concurrencyVersion = updated.concurrencyVersion;
  if (showNotice) notice.value = '取材描述已保存。';
}

function saveGrossing() {
  void run(() => saveDetails());
}

function addBlock() {
  const specimen = currentSpecimen.value;
  const grossing = workspace.value?.grossing;
  if (!specimen || !grossing || !newBlockCode.value.trim()) return;
  void run(async () => {
    await createV2Block({
      grossingId: grossing.grossingId,
      specimenId: specimen.specimenId,
      blockCode: newBlockCode.value.trim(),
      blockType: props.sourceType === 'FROZEN_CONTEXT' ? 'FROZEN' : 'ROUTINE',
      samplingDescription: newBlockDescription.value.trim(),
      idempotencyKey: idempotencyKey('ux01-block-create'),
    });
    notice.value = `蜡块 ${newBlockCode.value.trim()} 已建立。`;
    newBlockDescription.value = '';
    await loadWorkspace();
  });
}

function addBlocks(count: number) {
  const specimen = currentSpecimen.value;
  const grossing = workspace.value?.grossing;
  if (!specimen || !grossing || count < 1) return;
  void run(async () => {
    const base = specimen.blocks.length;
    await createV2Blocks({
      grossingId: grossing.grossingId,
      blocks: Array.from({ length: count }, (_, index) => ({
        specimenId: specimen.specimenId,
        blockCode: `${specimen.specimenCode}${base + index + 1}`,
        blockType: props.sourceType === 'FROZEN_CONTEXT' ? 'FROZEN' : 'ROUTINE',
        samplingDescription: newBlockDescription.value.trim(),
      })),
      idempotencyKey: idempotencyKey('fc02b-block-batch-create'),
    });
    notice.value = `已快速建立 ${count} 个蜡块。`;
    await loadWorkspace();
  });
}

function duplicateLastBlock() {
  setSuggestedBlockCode();
  addBlock();
}

function beginBlockEdit(block: V2GrossingWorkspace['specimens'][number]['blocks'][number]) {
  editingBlockId.value = block.blockId;
  editingBlockCode.value = block.blockCode;
  editingBlockDescription.value = block.samplingDescription ?? '';
  editingBlockNote.value = block.note ?? '';
  editingBlockReason.value = '';
}

function cancelBlockEdit() {
  editingBlockId.value = '';
  editingBlockCode.value = '';
  editingBlockDescription.value = '';
  editingBlockNote.value = '';
  editingBlockReason.value = '';
}

function saveBlock(block: V2GrossingWorkspace['specimens'][number]['blocks'][number]) {
  const nextCode = editingBlockCode.value.trim();
  if (!nextCode || !workspace.value) return;
  void run(async () => {
    await updateV2Block({
      blockId: block.blockId,
      blockCode: nextCode,
      blockType: block.blockType,
      samplingDescription: editingBlockDescription.value.trim(),
      note: editingBlockNote.value.trim(),
      reason: editingBlockReason.value.trim() || undefined,
      expectedVersion: block.concurrencyVersion,
      idempotencyKey: idempotencyKey('ux01a-block-update'),
    });
    cancelBlockEdit();
    notice.value = `蜡块已修改为 ${nextCode}。`;
    await loadWorkspace();
  });
}

function verifyBlock(
  block: V2GrossingWorkspace['specimens'][number]['blocks'][number],
  specimenId: string,
) {
  void run(async () => {
    const result = await verifyV2Block({
      blockId: block.blockId,
      verifiedCode: block.blockCode,
      verifiedSpecimenId: specimenId,
    });
    notice.value =
      result.resultCode === 'PASSED' ? `材块 ${block.blockCode} 核对通过。` : '核对未通过。';
    await loadWorkspace();
  });
}

function removeBlock(blockId: string, blockCode: string, version: number) {
  void run(async () => {
    await softDeleteV2Block({
      blockId,
      expectedVersion: version,
      reason: '取材工作区删除未完成蜡块',
      idempotencyKey: idempotencyKey('ux01-block-remove'),
    });
    notice.value = `蜡块 ${blockCode} 已作废，原记录仍保留。`;
    await loadWorkspace();
  });
}

function printBlock(blockId: string, blockCode: string) {
  void run(async () => {
    await printV2Block({
      blockId,
      reason: '取材工作区打印',
      idempotencyKey: idempotencyKey('ux01-block-print'),
    });
    notice.value = `蜡块 ${blockCode} 的标签已发送到当前打印机。`;
    await loadWorkspace();
  });
}

function printSelectedBlocks() {
  if (!selectedBlockIds.value.length) return;
  void run(async () => {
    await printV2Blocks({
      blockIds: selectedBlockIds.value,
      reason: '取材工作区批量打印',
      idempotencyKey: idempotencyKey('fc02b-block-batch-print'),
    });
    notice.value = `已提交 ${selectedBlockIds.value.length} 个材块标签。`;
    selectedBlockIds.value = [];
    await loadWorkspace();
  });
}

function completeGrossing(afterComplete: 'stay' | 'next' | 'return' = 'stay') {
  const grossing = workspace.value?.grossing;
  if (!grossing) return;
  void run(async () => {
    await saveDetails(false);
    for (const specimen of workspace.value?.specimens ?? []) {
      const description = grossSpecimenDrafts.value[specimen.specimenId]?.trim();
      if (description && description !== specimen.grossMaterialDescription) {
        await updateV2GrossingSpecimen({
          grossingId: grossing.grossingId,
          specimenId: specimen.specimenId,
          materialDescription: description,
          expectedVersion: specimen.grossSpecimenVersion,
        });
      }
    }
    const currentVersion =
      workspace.value?.grossing?.concurrencyVersion ?? grossing.concurrencyVersion;
    const result = await completeV2Grossing({
      grossingId: grossing.grossingId,
      expectedVersion: currentVersion,
      idempotencyKey: idempotencyKey('ux01-grossing-complete'),
    });
    notice.value = `取材已完成，已生成 ${result.createdSlideCount} 张待制玻片。`;
    await loadWorkspace();
    if (props.origin === 'workbench' && props.queue) {
      const latest = await getV2MyWorkbench();
      nextWorkbenchItem.value = nextItemInOriginalQueue(
        latest.capabilityQueues.find((queue) => queue.key === props.queue)?.items ?? [],
      );
      if (afterComplete === 'next') {
        if (nextWorkbenchItem.value) {
          openNextWorkbenchItem();
        } else {
          returnToWorkbench();
        }
      } else if (afterComplete === 'return') {
        returnToWorkbench();
      }
    }
  });
}

function correctCompletedGrossing() {
  const grossing = workspace.value?.grossing;
  if (!grossing || !grossing.completedAt || !correctionReason.value.trim()) return;
  void run(async () => {
    for (const specimen of workspace.value?.specimens ?? []) {
      const description = grossSpecimenDrafts.value[specimen.specimenId]?.trim();
      if (description && description !== specimen.grossMaterialDescription) {
        await updateV2GrossingSpecimen({
          grossingId: grossing.grossingId,
          specimenId: specimen.specimenId,
          materialDescription: description,
          expectedVersion: specimen.grossSpecimenVersion,
          reason: correctionReason.value.trim(),
        });
      }
    }
    await correctV2Grossing({
      grossingId: grossing.grossingId,
      grossDescription: grossDescription.value.trim(),
      grossingInstruction: grossingInstruction.value.trim(),
      grossingDoctorId: selectedDoctorId.value || grossing.grossingDoctorId,
      recorderId: currentRecorder(props.authUser ?? null) || grossing.recorderId,
      reason: correctionReason.value.trim(),
      expectedVersion: grossing.concurrencyVersion,
    });
    correctionOpen.value = false;
    correctionReason.value = '';
    notice.value = '取材记录已在原记录上完成授权修正，不会重新进入待取材。';
    await loadWorkspace();
  });
}

function openNextWorkbenchItem() {
  if (!nextWorkbenchItem.value) return;
  emit(
    'navigate',
    appendNavigationContext(nextWorkbenchItem.value.workspaceDestination, {
      origin: 'workbench',
      queue: props.queue,
      returnTo: props.returnTo,
    }),
  );
}

function returnToWorkbench() {
  emit('navigate', safeLocalPath(props.returnTo) || '/v2/workbench');
}

onMounted(() => void loadDoctors());
</script>

<template>
  <section class="grossing-layout" aria-label="病例取材工作区">
    <header class="page-heading compact-heading">
      <div>
        <p class="section-kicker">取材</p>
        <h2>病例取材工作区</h2>
        <p>在一个页面切换全部标本，快速建立蜡块并完成取材。</p>
      </div>
      <label v-if="!workspace?.grossing" class="compact-select">
        取材医生
        <select v-model="selectedDoctorId" aria-label="取材医生">
          <option value="" disabled>请选择</option>
          <option v-for="doctor in doctors" :key="doctor.id" :value="doctor.id">
            {{ doctor.displayName }}{{ doctor.title ? ` · ${doctor.title}` : '' }}
          </option>
        </select>
      </label>
      <span v-else-if="currentDoctor" class="status-pill success"
        >取材医生：{{ currentDoctor.displayName }}</span
      >
    </header>

    <div v-if="!caseId" class="workspace-toolbar">
      <form class="case-lookup" @submit.prevent="openCase">
        <label>
          打开病例
          <input v-model="lookupCaseId" placeholder="输入病例标识或从待取材列表进入" />
        </label>
        <button class="secondary-button" type="submit" :disabled="loading">
          {{ loading ? '读取中…' : '打开' }}
        </button>
      </form>
    </div>

    <p v-if="error" class="feedback error" role="alert">{{ error }}</p>
    <p v-if="notice" class="feedback success" role="status">{{ notice }}</p>

    <div v-if="loading" class="list-skeleton" aria-label="正在读取病例">
      <span></span><span></span><span></span>
    </div>
    <div v-else-if="!workspace" class="empty-state workspace-panel">
      <strong>请打开一个待取材病例</strong>
      <span>从工作台进入病例时会自动带入，无需记忆内部编号。</span>
    </div>
    <template v-else>
      <V2CaseHeader
        :case-id="workspace.caseId"
        :pathology-no="workspace.caseNo"
        :patient-reference="workspace.patientReference"
        :visit-reference="workspace.visitReference"
        :business-type-code="workspace.businessTypeCode"
        :current-responsibility="
          currentDoctor ? `取材医生：${currentDoctor.displayName}` : '待安排取材'
        "
        :report-status="
          workspace.grossing?.completedAt
            ? '取材已完成'
            : workspace.grossing
              ? '取材进行中'
              : '待取材'
        "
        :progress="`${workspace.specimens.length} 个标本，玻片 ${materialProgress} 完成`"
        :back-label="backLabel"
        @open-case="emit('navigate', backTarget)"
        @open-overview="emit('navigate', caseOverviewTarget)"
      >
        <template #actions>
          <button
            class="secondary-button"
            type="button"
            @click="emit('navigate', contextualPath(`/v2/production/${workspace.caseId}`))"
          >
            查看制片
          </button>
          <button class="secondary-button" type="button" @click="historyDrawerOpen = true">
            历史记录
          </button>
        </template>
      </V2CaseHeader>

      <p v-if="!selectedDoctorId && !workspace.grossing" class="feedback warning">
        开始前请选择本次取材医生；当前登录人将自动记为记录员。
      </p>

      <div class="grossing-workspace-grid">
        <aside class="specimen-sidebar" aria-label="标本列表">
          <header class="panel-title-row">
            <div>
              <p class="section-kicker">标本</p>
              <h3>{{ workspace.specimens.length }} 个</h3>
            </div>
            <button
              v-if="can('SPECIMEN_ADD') && !workspace.grossing?.completedAt"
              class="text-button"
              type="button"
              @click="openAddSpecimen"
            >
              新增标本
            </button>
          </header>
          <div class="specimen-sidebar-list">
            <button
              v-for="specimen in workspace.specimens"
              :key="specimen.specimenId"
              type="button"
              :class="{ active: specimen.specimenId === selectedSpecimenId }"
              @click="selectSpecimen(specimen.specimenId)"
            >
              <strong>{{ specimen.specimenCode }} · {{ specimen.specimenName }}</strong>
              <small>
                {{ specimen.collectionSite || '部位待补充' }} · {{ specimen.blocks.length }} 个材块
              </small>
              <small v-if="specimen.sourceSpecimenCode"
                >由标本 {{ specimen.sourceSpecimenCode }} 拆分</small
              >
            </button>
          </div>
          <form v-if="addSpecimenOpen" class="compact-editor" @submit.prevent="addSpecimen">
            <strong>新增标本</strong>
            <input v-model="newSpecimen.code" aria-label="新标本编号" placeholder="编号" required />
            <input v-model="newSpecimen.name" aria-label="新标本名称" placeholder="名称" required />
            <input
              v-model="newSpecimen.site"
              aria-label="新标本部位"
              placeholder="部位（按业务填写）"
            />
            <input v-model="newSpecimen.description" aria-label="新标本描述" placeholder="描述" />
            <input
              v-model="newSpecimen.reason"
              aria-label="新增标本备注"
              placeholder="新增原因/备注"
            />
            <div class="inline-actions">
              <button class="primary-button" type="submit" :disabled="busy">保存</button>
              <button class="text-button" type="button" @click="addSpecimenOpen = false">
                取消
              </button>
            </div>
          </form>
        </aside>

        <div class="grossing-editor">
          <section>
            <header class="panel-title-row">
              <div>
                <p class="section-kicker">当前标本</p>
                <h3>{{ currentSpecimen?.specimenCode }} · {{ currentSpecimen?.specimenName }}</h3>
              </div>
              <span class="status-pill">{{
                specimenKindName(currentSpecimen?.specimenKindCode)
              }}</span>
            </header>
            <details v-if="currentSpecimenDetail" class="specimen-detail-disclosure">
              <summary>修正标本信息</summary>
              <div class="field-grid specimen-detail-editor">
                <label>
                  标本名称
                  <input v-model="specimenNameDraft" :readonly="!can('SPECIMEN_UPDATE')" />
                </label>
                <label>
                  取材部位
                  <input
                    v-model="specimenSiteDraft"
                    aria-label="当前标本取材部位"
                    :readonly="!can('SPECIMEN_UPDATE')"
                  />
                </label>
                <label class="span-two">
                  标本描述
                  <input v-model="specimenDescriptionDraft" :readonly="!can('SPECIMEN_UPDATE')" />
                </label>
                <label class="span-two">
                  修正原因
                  <input
                    v-model="specimenCorrectionReason"
                    placeholder="已有下游材料时必须填写"
                    :readonly="!can('SPECIMEN_UPDATE')"
                  />
                </label>
                <div class="field-action-cell">
                  <button
                    class="secondary-button"
                    type="button"
                    :disabled="!can('SPECIMEN_UPDATE') || busy || !specimenNameDraft.trim()"
                    @click="saveSpecimenDetails"
                  >
                    保存标本信息
                  </button>
                  <button
                    v-if="can('SPECIMEN_SPLIT')"
                    class="text-button"
                    type="button"
                    :disabled="busy"
                    @click="openSplitSpecimen"
                  >
                    拆分
                  </button>
                  <button
                    v-if="can('SPECIMEN_CANCEL')"
                    class="text-button danger-text"
                    type="button"
                    :disabled="busy || !specimenCorrectionReason.trim()"
                    @click="cancelCurrentSpecimen"
                  >
                    取消误录
                  </button>
                </div>
              </div>
            </details>
            <form
              v-if="splitSpecimenOpen"
              class="split-editor"
              @submit.prevent="splitCurrentSpecimen"
            >
              <strong>拆分为新标本（原标本及既有材块保持不变）</strong>
              <div v-for="(child, index) in splitChildren" :key="index" class="split-row">
                <input
                  v-model="child.code"
                  :aria-label="`拆分标本 ${index + 1} 编号`"
                  placeholder="编号"
                />
                <input
                  v-model="child.name"
                  :aria-label="`拆分标本 ${index + 1} 名称`"
                  placeholder="名称"
                />
                <input
                  v-model="child.site"
                  :aria-label="`拆分标本 ${index + 1} 部位`"
                  placeholder="部位"
                />
                <input
                  v-model="child.description"
                  :aria-label="`拆分标本 ${index + 1} 描述`"
                  placeholder="描述"
                />
              </div>
              <input v-model="splitReason" aria-label="拆分原因" placeholder="拆分原因" required />
              <div class="inline-actions">
                <button class="primary-button" type="submit" :disabled="busy">确认拆分</button>
                <button class="text-button" type="button" @click="splitSpecimenOpen = false">
                  取消
                </button>
              </div>
            </form>
            <div class="field-grid gross-description-grid">
              <label>
                当前标本大体所见
                <textarea
                  v-if="currentSpecimen"
                  v-model="grossSpecimenDrafts[currentSpecimen.specimenId]"
                  rows="3"
                  :readonly="!canEditGrossFields"
                  placeholder="分别记录该标本的大小、形态、颜色、切面等"
                ></textarea>
                <button
                  v-if="canEdit || correctionOpen"
                  class="text-button"
                  type="button"
                  :disabled="
                    busy || !grossSpecimenDrafts[currentSpecimen?.specimenId ?? '']?.trim()
                  "
                  @click="saveCurrentGrossDescription"
                >
                  保存当前标本所见
                </button>
              </label>
              <label>
                本次取材总结
                <textarea
                  v-model="grossDescription"
                  rows="3"
                  :readonly="Boolean(workspace.grossing?.completedAt) && !correctionOpen"
                  placeholder="记录本次取材的总体说明；各标本所见请分别填写"
                ></textarea>
              </label>
              <label class="span-two">
                取材说明
                <textarea
                  v-model="grossingInstruction"
                  rows="1"
                  :readonly="Boolean(workspace.grossing?.completedAt) && !correctionOpen"
                  placeholder="可选：特殊取材要求或备注"
                ></textarea>
              </label>
            </div>
          </section>

          <section>
            <header class="panel-title-row">
              <div>
                <p class="section-kicker">蜡块</p>
                <h3>{{ currentBlocks.length }} 个蜡块</h3>
              </div>
              <div v-if="canEdit" class="input-action-row block-quick-entry">
                <label>
                  <span class="visually-hidden">新蜡块编号</span>
                  <input
                    v-model="newBlockCode"
                    aria-label="新蜡块编号"
                    placeholder="例如 A1"
                    @keydown.enter.prevent="addBlock"
                  />
                </label>
                <label>
                  <span class="visually-hidden">取材说明</span>
                  <input
                    v-model="newBlockDescription"
                    aria-label="新材块取材说明"
                    placeholder="取材说明"
                  />
                </label>
                <button class="primary-button" type="button" :disabled="busy" @click="addBlock">
                  + 蜡块
                </button>
                <button class="text-button" type="button" :disabled="busy" @click="addBlocks(3)">
                  +3
                </button>
                <button class="text-button" type="button" :disabled="busy" @click="addBlocks(5)">
                  +5
                </button>
              </div>
            </header>

            <div v-if="!currentBlocks.length" class="empty-state compact">
              <strong>当前标本还没有蜡块</strong>
              <span>开始取材后，输入编号并按 Enter 可快速新增。</span>
            </div>
            <div v-else class="compact-table-wrap">
              <table class="compact-material-table">
                <thead>
                  <tr>
                    <th aria-label="选择"></th>
                    <th>编号</th>
                    <th>标本</th>
                    <th>取材说明</th>
                    <th>核对</th>
                    <th>打印</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="block in currentBlocks" :key="block.blockId">
                    <td>
                      <input
                        v-model="selectedBlockIds"
                        type="checkbox"
                        :value="block.blockId"
                        :aria-label="`选择材块 ${block.blockCode}`"
                      />
                    </td>
                    <td>
                      <input
                        v-if="editingBlockId === block.blockId"
                        v-model="editingBlockCode"
                        class="block-edit-input"
                      />
                      <strong v-else>{{ block.blockCode }}</strong>
                    </td>
                    <td>{{ currentSpecimen?.specimenCode }}</td>
                    <td>
                      <input
                        v-if="editingBlockId === block.blockId"
                        v-model="editingBlockDescription"
                        placeholder="取材说明"
                      />
                      <span v-else>{{ block.samplingDescription || '—' }}</span>
                    </td>
                    <td>
                      <span v-if="block.verificationStatus === 'PASSED'" class="status-pill success"
                        >已核对</span
                      >
                      <button
                        v-else-if="can('BLOCK_VERIFY') && currentSpecimen"
                        class="text-button"
                        type="button"
                        @click="verifyBlock(block, currentSpecimen.specimenId)"
                      >
                        核对
                      </button>
                      <span v-else>未核对</span>
                    </td>
                    <td>
                      <button
                        v-if="can('BLOCK_PRINT')"
                        class="text-button"
                        type="button"
                        @click="printBlock(block.blockId, block.blockCode)"
                      >
                        {{ block.printCount > 0 ? `补打(${block.printCount})` : '打印' }}
                      </button>
                    </td>
                    <td class="inline-actions">
                      <template v-if="editingBlockId === block.blockId">
                        <input
                          v-model="editingBlockReason"
                          aria-label="材块修改原因"
                          placeholder="改编号时填写原因"
                        />
                        <button
                          class="text-button"
                          type="button"
                          :disabled="busy || !editingBlockCode.trim()"
                          @click="saveBlock(block)"
                        >
                          保存
                        </button>
                        <button class="text-button" type="button" @click="cancelBlockEdit">
                          取消
                        </button>
                      </template>
                      <template v-else>
                        <button
                          v-if="canEdit && can('BLOCK_UPDATE')"
                          class="text-button"
                          type="button"
                          @click="beginBlockEdit(block)"
                        >
                          修改
                        </button>
                        <button
                          v-if="canEdit && can('BLOCK_CANCEL')"
                          class="text-button danger-text"
                          type="button"
                          @click="
                            removeBlock(block.blockId, block.blockCode, block.concurrencyVersion)
                          "
                        >
                          取消
                        </button>
                      </template>
                    </td>
                  </tr>
                </tbody>
              </table>
              <div class="inline-actions material-table-actions">
                <button
                  v-if="can('BLOCK_PRINT')"
                  class="secondary-button"
                  type="button"
                  :disabled="!selectedBlockIds.length || busy"
                  @click="printSelectedBlocks"
                >
                  批量打印
                </button>
                <button
                  v-if="canEdit"
                  class="text-button"
                  type="button"
                  @click="duplicateLastBlock"
                >
                  + 复制上一材块
                </button>
              </div>
            </div>
          </section>
        </div>
      </div>

      <section class="grossing-image-panel" aria-label="大体图像">
        <header class="panel-title-row">
          <div>
            <p class="section-kicker">大体图像</p>
            <h3>{{ grossingImages.length }} 张图像</h3>
          </div>
          <button
            v-if="workspace.grossing"
            class="secondary-button"
            type="button"
            :disabled="imageBusy"
            @click="captureGrossingImage"
          >
            {{ imageBusy ? '处理中…' : '拍摄台采集' }}
          </button>
        </header>
        <div v-if="!workspace.grossing" class="empty-state compact">
          <span>开始取材后可采集并保存大体图像。</span>
        </div>
        <div v-else-if="!grossingImages.length" class="empty-state compact">
          <strong>尚未采集大体图像</strong>
          <span>可以使用拍摄台模拟器保存第一张图像。</span>
        </div>
        <template v-else>
          <div class="grossing-image-workspace">
            <div class="grossing-image-list">
              <button
                v-for="image in grossingImages"
                :key="image.imageId"
                type="button"
                class="grossing-image-row"
                :class="{ active: selectedImageId === image.imageId }"
                @click="selectedImageId = image.imageId"
              >
                <span class="image-placeholder" aria-hidden="true">图</span>
                <span
                  ><strong>{{ image.imageName }}</strong
                  ><small>{{ formatDateTime(image.capturedAt) }}</small></span
                >
              </button>
            </div>
            <img
              v-if="selectedImage"
              class="grossing-image-preview"
              :src="selectedImage.storageReference"
              :alt="`${selectedImage.imageName} 大体图像`"
            />
          </div>
          <div class="input-action-row">
            <input v-model="imageAnnotationNote" placeholder="为当前图像添加标注说明" />
            <button
              class="text-button"
              type="button"
              :disabled="imageBusy || !imageAnnotationNote.trim()"
              @click="annotateSelectedImage"
            >
              保存标注
            </button>
            <input
              v-model.number="measurementValue"
              aria-label="长度测量值"
              type="number"
              min="0"
              step="0.1"
              placeholder="长度"
            />
            <select v-model="measurementUnit" aria-label="长度测量单位">
              <option value="MM">mm</option>
              <option value="CM">cm</option>
            </select>
            <button
              class="text-button"
              type="button"
              :disabled="imageBusy || measurementValue == null || measurementValue < 0"
              @click="saveMeasurement"
            >
              保存测量
            </button>
          </div>
          <div class="image-fact-list">
            <span v-for="annotation in imageAnnotations" :key="annotation.annotationId">
              标注：{{ annotation.note || annotation.label }}
            </span>
            <span v-for="measurement in imageMeasurements" :key="measurement.measurementId">
              长度：{{ measurement.value }} {{ measurement.unitCode.toLowerCase() }}
            </span>
          </div>
          <div class="input-action-row image-delete-row">
            <input v-model="imageDeletionReason" placeholder="误拍取消原因" />
            <button
              class="text-button danger-text"
              type="button"
              :disabled="imageBusy || !imageDeletionReason.trim()"
              @click="deleteSelectedImage"
            >
              取消当前图像
            </button>
          </div>
        </template>
      </section>
      <div class="sticky-form-actions" aria-label="取材操作">
        <span class="muted">
          <template v-if="workspace.grossing">
            {{ workspace.grossing.grossingNo }} · 开始于
            {{ formatDateTime(workspace.grossing.startedAt) }}
          </template>
          <template v-else>尚未开始取材</template>
        </span>
        <div class="action-group">
          <button
            v-if="canEdit"
            class="secondary-button"
            type="button"
            :disabled="busy"
            @click="saveGrossing"
          >
            保存
          </button>
          <button
            v-if="canStart"
            class="primary-button"
            type="button"
            :disabled="busy"
            @click="beginGrossing"
          >
            开始取材
          </button>
          <button
            v-if="canEdit && props.origin !== 'workbench'"
            class="primary-button"
            type="button"
            :disabled="busy"
            @click="completeGrossing('stay')"
          >
            完成取材
          </button>
          <template v-if="canEdit && props.origin === 'workbench'">
            <button
              class="secondary-button"
              type="button"
              :disabled="busy"
              @click="completeGrossing('return')"
            >
              取材完成并返回工作台
            </button>
            <button
              class="primary-button"
              type="button"
              :disabled="busy"
              @click="completeGrossing('next')"
            >
              取材完成并下一例
            </button>
          </template>
          <template v-if="workspace.grossing?.completedAt && props.origin === 'workbench'">
            <button
              v-if="nextWorkbenchItem"
              class="primary-button"
              type="button"
              @click="openNextWorkbenchItem"
            >
              取材完成并下一例
            </button>
            <button class="secondary-button" type="button" @click="returnToWorkbench">
              取材完成并返回工作台
            </button>
          </template>
          <template v-else-if="workspace.grossing?.completedAt">
            <span class="status-pill success">取材已完成</span>
            <button
              v-if="can('GROSSING_CORRECT')"
              class="secondary-button"
              type="button"
              @click="correctionOpen = true"
            >
              修正取材记录
            </button>
          </template>
        </div>
      </div>
      <form v-if="correctionOpen" class="correction-bar" @submit.prevent="correctCompletedGrossing">
        <label>
          修正原因
          <input v-model="correctionReason" required placeholder="说明需要修正的内容" />
        </label>
        <span>修正沿用当前取材记录，不会生成新取材或返回待取材。</span>
        <button class="primary-button" type="submit" :disabled="busy">确认修正</button>
        <button class="text-button" type="button" @click="correctionOpen = false">取消</button>
      </form>
    </template>
    <V2HistoryDrawer
      :open="historyDrawerOpen"
      :case-id="workspace?.caseId"
      title="取材历史"
      target-label="取材工作台"
      @close="historyDrawerOpen = false"
    />
  </section>
</template>
