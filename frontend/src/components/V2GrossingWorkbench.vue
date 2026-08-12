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
import {
  blockTypeName,
  friendlyError,
  formatDateTime,
  idempotencyKey,
  specimenKindName,
} from '../uiText';
import { getV2Specimen, updateV2Specimen, type V2SpecimenResult } from '../v2Api';
import {
  associateV2Specimen,
  completeV2Grossing,
  captureV2GrossingImage,
  createV2GrossingAnnotation,
  createV2Block,
  createV2Grossing,
  getV2GrossingImages,
  getV2GrossingWorkspace,
  printV2Block,
  softDeleteV2Block,
  updateV2Block,
  updateV2Grossing,
  type V2GrossingImage,
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
const grossDescription = ref('');
const grossingInstruction = ref('');
const newBlockCode = ref('');
const editingBlockId = ref('');
const editingBlockCode = ref('');
const busy = ref(false);
const loading = ref(false);
const error = ref('');
const notice = ref('');
const doctors = ref<Array<{ id: string; displayName: string; title?: string | null }>>([]);
const selectedDoctorId = ref(props.authUser?.doctor?.id ?? '');
const historyDrawerOpen = ref(false);
const grossingImages = ref<V2GrossingImage[]>([]);
const selectedImageId = ref('');
const imageBusy = ref(false);
const imageAnnotationNote = ref('');
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
  Boolean(workspace.value?.grossing && !workspace.value.grossing.completedAt),
);
const canStart = computed(() =>
  Boolean(workspace.value && !workspace.value.grossing && selectedDoctorId.value),
);
const materialProgress = computed(() => {
  const slides =
    workspace.value?.specimens.flatMap((specimen) => [
      ...specimen.directSlides,
      ...specimen.blocks.flatMap((block) => block.slides),
    ]) ?? [];
  return `${slides.filter((slide) => slide.completed).length}/${slides.length}`;
});

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
    selectedSpecimenId.value ||= workspace.value.specimens[0]?.specimenId ?? '';
    specimenSiteDraft.value = specimenDetails.value[selectedSpecimenId.value]?.collectionSite ?? '';
    if (workspace.value.grossing) {
      selectedDoctorId.value = workspace.value.grossing.grossingDoctorId;
      grossDescription.value = workspace.value.grossing.grossDescription;
      grossingInstruction.value = workspace.value.grossing.grossingInstruction ?? '';
      grossingImages.value = await getV2GrossingImages(workspace.value.grossing.grossingId);
      selectedImageId.value ||= grossingImages.value[0]?.imageId ?? '';
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
      annotationTypeCode: 'NOTE',
      geometryJson: JSON.stringify({ x: 0.5, y: 0.5 }),
      label: '取材标注',
      note: imageAnnotationNote.value.trim(),
    });
    imageAnnotationNote.value = '';
    notice.value = '大体图像标注已保存。';
  } catch (requestError) {
    error.value = friendlyError(requestError, '大体图像标注失败，请稍后重试。');
  } finally {
    imageBusy.value = false;
  }
}

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
  setSuggestedBlockCode();
}

function saveSpecimenDetails() {
  const detail = currentSpecimenDetail.value;
  if (!detail || !specimenSiteDraft.value.trim()) return;
  void run(async () => {
    const updated = await updateV2Specimen({
      ...detail,
      collectionSite: specimenSiteDraft.value.trim(),
      labelCode: detail.labelCode ?? '',
      expectedVersion: detail.concurrencyVersion,
    });
    specimenDetails.value = { ...specimenDetails.value, [updated.specimenId]: updated };
    notice.value = `标本 ${updated.specimenCode} 信息已保存。`;
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
      grossDescription: grossDescription.value.trim() || '待补充大体描述',
      grossingInstruction: grossingInstruction.value.trim(),
      grossingDoctorId: selectedDoctorId.value,
      recorderId: currentRecorder(props.authUser ?? null),
      idempotencyKey: idempotencyKey('ux01-grossing-start'),
    });
    for (const specimen of workspace.value!.specimens) {
      await associateV2Specimen({
        grossingId: created.grossingId,
        specimenId: specimen.specimenId,
        materialDescription: specimen.specimenCode,
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
      idempotencyKey: idempotencyKey('ux01-block-create'),
    });
    notice.value = `蜡块 ${newBlockCode.value.trim()} 已建立。`;
    await loadWorkspace();
  });
}

function addBlocks(count: number) {
  const specimen = currentSpecimen.value;
  const grossing = workspace.value?.grossing;
  if (!specimen || !grossing || count < 1) return;
  void run(async () => {
    const base = specimen.blocks.length;
    for (let index = 0; index < count; index += 1) {
      const code = `${specimen.specimenCode}${base + index + 1}`;
      await createV2Block({
        grossingId: grossing.grossingId,
        specimenId: specimen.specimenId,
        blockCode: code,
        blockType: props.sourceType === 'FROZEN_CONTEXT' ? 'FROZEN' : 'ROUTINE',
        idempotencyKey: idempotencyKey('px02b-block-quick-create'),
      });
    }
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
}

function cancelBlockEdit() {
  editingBlockId.value = '';
  editingBlockCode.value = '';
}

function saveBlock(block: V2GrossingWorkspace['specimens'][number]['blocks'][number]) {
  const nextCode = editingBlockCode.value.trim();
  if (!nextCode || !workspace.value) return;
  void run(async () => {
    await updateV2Block({
      blockId: block.blockId,
      blockCode: nextCode,
      blockType: block.blockType,
      expectedVersion: block.concurrencyVersion,
      idempotencyKey: idempotencyKey('ux01a-block-update'),
    });
    cancelBlockEdit();
    notice.value = `蜡块已修改为 ${nextCode}。`;
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

function completeGrossing() {
  const grossing = workspace.value?.grossing;
  if (!grossing) return;
  void run(async () => {
    await saveDetails(false);
    const currentVersion =
      workspace.value?.grossing?.concurrencyVersion ?? grossing.concurrencyVersion;
    const result = await completeV2Grossing({
      grossingId: grossing.grossingId,
      expectedVersion: currentVersion,
      idempotencyKey: idempotencyKey('ux01-grossing-complete'),
    });
    notice.value = `取材已完成，已生成 ${result.createdSlideCount} 张待制玻片。`;
    await loadWorkspace();
  });
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

    <div class="workspace-toolbar">
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
          </header>
          <div class="specimen-sidebar-list">
            <button
              v-for="specimen in workspace.specimens"
              :key="specimen.specimenId"
              type="button"
              :class="{ active: specimen.specimenId === selectedSpecimenId }"
              @click="selectSpecimen(specimen.specimenId)"
            >
              <strong>{{ specimen.specimenCode }} · {{ specimen.specimenNo }}</strong>
              <small>{{ specimen.blocks.length }} 个蜡块</small>
            </button>
          </div>
        </aside>

        <div class="grossing-editor">
          <section>
            <header class="panel-title-row">
              <div>
                <p class="section-kicker">当前标本</p>
                <h3>{{ currentSpecimen?.specimenCode }} · {{ currentSpecimen?.specimenNo }}</h3>
              </div>
              <span class="status-pill">{{
                specimenKindName(currentSpecimen?.specimenKindCode)
              }}</span>
            </header>
            <div v-if="currentSpecimenDetail" class="field-grid specimen-detail-editor">
              <label>
                取材部位
                <input
                  v-model="specimenSiteDraft"
                  aria-label="当前标本取材部位"
                  :readonly="!canEdit"
                />
              </label>
              <div class="field-action-cell">
                <button
                  class="secondary-button"
                  type="button"
                  :disabled="!canEdit || busy || !specimenSiteDraft.trim()"
                  @click="saveSpecimenDetails"
                >
                  保存标本信息
                </button>
              </div>
            </div>
            <div class="field-grid">
              <label class="span-two">
                大体描述
                <textarea
                  v-model="grossDescription"
                  rows="5"
                  :readonly="Boolean(workspace.grossing?.completedAt)"
                  placeholder="记录大小、形态、颜色、切面等大体所见"
                ></textarea>
              </label>
              <label class="span-two">
                取材说明
                <textarea
                  v-model="grossingInstruction"
                  rows="2"
                  :readonly="Boolean(workspace.grossing?.completedAt)"
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
            <div v-else class="block-quick-grid">
              <article v-for="block in currentBlocks" :key="block.blockId" class="block-chip">
                <header>
                  <template v-if="editingBlockId === block.blockId">
                    <input
                      v-model="editingBlockCode"
                      :aria-label="`修改蜡块 ${block.blockCode}`"
                      class="block-edit-input"
                      @keydown.enter.prevent="saveBlock(block)"
                    />
                  </template>
                  <template v-else>
                    <strong>{{ block.blockCode }}</strong>
                    <span class="status-pill">{{ blockTypeName(block.blockType) }}</span>
                  </template>
                </header>
                <small class="muted">{{ block.slides.length }} 张玻片</small>
                <div class="inline-actions">
                  <template v-if="editingBlockId === block.blockId">
                    <button
                      class="text-button"
                      type="button"
                      :disabled="busy || !editingBlockCode.trim()"
                      @click="saveBlock(block)"
                    >
                      保存修改
                    </button>
                    <button
                      class="text-button"
                      type="button"
                      :disabled="busy"
                      @click="cancelBlockEdit"
                    >
                      取消
                    </button>
                  </template>
                  <button
                    v-else-if="canEdit"
                    class="text-button"
                    type="button"
                    @click="beginBlockEdit(block)"
                  >
                    修改
                  </button>
                  <button
                    class="text-button"
                    type="button"
                    @click="printBlock(block.blockId, block.blockCode)"
                  >
                    {{ block.printCount > 0 || block.slides.length ? '补打' : '打印' }}
                  </button>
                  <button
                    v-if="canEdit"
                    class="text-button danger-text"
                    type="button"
                    @click="removeBlock(block.blockId, block.blockCode, block.concurrencyVersion)"
                  >
                    删除
                  </button>
                </div>
              </article>
            </div>
            <button
              v-if="canEdit && currentBlocks.length"
              class="text-button"
              type="button"
              @click="duplicateLastBlock"
            >
              + 复制上一蜡块
            </button>
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
              <span><strong>{{ image.imageName }}</strong><small>{{ formatDateTime(image.capturedAt) }}</small></span>
            </button>
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
            v-if="canEdit"
            class="primary-button"
            type="button"
            :disabled="busy"
            @click="completeGrossing"
          >
            完成取材
          </button>
          <span v-if="workspace.grossing?.completedAt" class="status-pill success">取材已完成</span>
        </div>
      </div>
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
