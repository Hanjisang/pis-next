<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';

import type { V2AuthUser } from '../auth';
import {
  appendNavigationContext,
  safeLocalPath,
  workspaceBackLabel,
  workspaceBackTarget,
  type V2Route,
} from '../navigation';
import { friendlyError, formatDateTime, idempotencyKey } from '../uiText';
import { getV2Case, type V2CaseResult } from '../v2Api';
import {
  cancelV2Slide,
  completeV2Slides,
  correctV2SlideCode,
  correctV2SlideCompletion,
  createV2ExtraRoutineSlide,
  generateV2RequiredRoutineSlides,
  getV2MaterialTree,
  locateV2Material,
  performV2ProductionRework,
  printV2Slides,
  type V2MaterialTree,
} from '../v2MaterialApi';
import {
  completeV2TechnicalTraceBatch,
  getV2HistologyWorkbench,
  recordV2HistologyException,
  type TechnicalTraceStageCode,
  type V2HistologySlide,
} from '../v2HistologyApi';
import { getV2MyWorkbench, type V2CapabilityQueueItem } from '../v2WorkspaceApi';
import V2CaseHeader from './V2CaseHeader.vue';
import V2HistoryDrawer from './V2HistoryDrawer.vue';

const caseId = defineModel<string>('caseId', { default: '' });
const props = withDefaults(
  defineProps<{
    authUser?: V2AuthUser | null;
    origin?: V2Route['origin'];
    queue?: string;
    returnTo?: string;
  }>(),
  { authUser: null, origin: 'direct', queue: 'ROUTINE_PRODUCTION', returnTo: '' },
);
const emit = defineEmits<{ navigate: [path: string] }>();

type BlockNode = V2MaterialTree['specimens'][number]['blocks'][number] & {
  specimenCode: string;
  specimenName: string;
};

const loading = ref(true);
const busy = ref(false);
const error = ref('');
const notice = ref('');
const caseSummary = ref<V2CaseResult | null>(null);
const materials = ref<V2MaterialTree | null>(null);
const histologySlides = ref<V2HistologySlide[]>([]);
const selectedBlockIds = ref<string[]>([]);
const selectedSlideIds = ref<string[]>([]);
const locatedMaterialId = ref('');
const scanCode = ref('');
const scanInput = ref<HTMLInputElement | null>(null);
const extraSlideType = ref('HE');
const extraReason = ref('');
const correctionCode = ref('');
const correctionReason = ref('');
const selectedActionSlideId = ref('');
const cancelReason = ref('');
const exceptionCode = ref('');
const exceptionNote = ref('');
const reworkType = ref<'RECUT' | 'RESTAIN' | 'RESCAN'>('RECUT');
const reworkReason = ref('');
const traceStage = ref<TechnicalTraceStageCode>('DEHYDRATION');
const traceEquipment = ref('');
const traceNote = ref('');
const historyOpen = ref(false);

const blocks = computed<BlockNode[]>(() =>
  (materials.value?.specimens ?? []).flatMap((specimen) =>
    specimen.blocks.map((block) => ({
      ...block,
      specimenCode: specimen.specimenCode,
      specimenName: specimen.specimenName,
    })),
  ),
);
const slides = computed(() => blocks.value.flatMap((block) => block.slides));
const completedSlides = computed(() => slides.value.filter((slide) => slide.completed).length);
const pendingSlides = computed(() => slides.value.filter((slide) => !slide.completed));
const productionReady = computed(() => materials.value?.initialProductionComplete === true);
const availableActions = computed(() => new Set(materials.value?.availableActions ?? []));
const actionSlides = computed(() =>
  slides.value.filter((slide) => selectedSlideIds.value.includes(slide.slideId)),
);
const activeSlide = computed(
  () => slides.value.find((slide) => slide.slideId === selectedActionSlideId.value) ?? null,
);
const selectedTraceIds = computed(() =>
  ['DEHYDRATION', 'EMBEDDING'].includes(traceStage.value)
    ? selectedBlockIds.value
    : selectedSlideIds.value,
);
const traceTargetKind = computed<'BLOCK' | 'SLIDE'>(() =>
  ['DEHYDRATION', 'EMBEDDING'].includes(traceStage.value) ? 'BLOCK' : 'SLIDE',
);
const backLabel = computed(() => workspaceBackLabel(props.origin));
const backTarget = computed(() => workspaceBackTarget(props, caseId.value));

const traceStages: Array<{ code: TechnicalTraceStageCode; label: string }> = [
  { code: 'DEHYDRATION', label: '脱水' },
  { code: 'EMBEDDING', label: '包埋' },
  { code: 'SECTIONING', label: '切片' },
  { code: 'STAINING', label: '染色' },
  { code: 'COVERSLIPPING', label: '封片' },
];

function can(action: string) {
  return availableActions.value.has(action);
}

function selectBlock(blockId: string, checked: boolean) {
  selectedBlockIds.value = checked
    ? [...new Set([...selectedBlockIds.value, blockId])]
    : selectedBlockIds.value.filter((id) => id !== blockId);
}

function selectSlide(slideId: string, checked: boolean) {
  selectedSlideIds.value = checked
    ? [...new Set([...selectedSlideIds.value, slideId])]
    : selectedSlideIds.value.filter((id) => id !== slideId);
  if (checked) selectedActionSlideId.value = slideId;
}

function histologySlide(slideId: string) {
  return histologySlides.value.find((slide) => slide.slideId === slideId);
}

async function load() {
  if (!caseId.value) return;
  loading.value = true;
  error.value = '';
  try {
    const [summary, tree, trace] = await Promise.all([
      getV2Case(caseId.value),
      getV2MaterialTree(caseId.value),
      getV2HistologyWorkbench(caseId.value),
    ]);
    caseSummary.value = summary;
    materials.value = tree;
    histologySlides.value = trace.slides;
    const existing = new Set(
      tree.specimens.flatMap((specimen) =>
        specimen.blocks.flatMap((block) => block.slides.map((slide) => slide.slideId)),
      ),
    );
    selectedSlideIds.value = selectedSlideIds.value.filter((id) => existing.has(id));
    selectedActionSlideId.value = existing.has(selectedActionSlideId.value)
      ? selectedActionSlideId.value
      : (tree.specimens.flatMap((specimen) => specimen.blocks.flatMap((block) => block.slides))[0]
          ?.slideId ?? '');
  } catch (requestError) {
    error.value = friendlyError(requestError, '常规制片工作区暂时无法加载。');
  } finally {
    loading.value = false;
  }
}

async function run(action: () => Promise<void>) {
  busy.value = true;
  error.value = '';
  notice.value = '';
  try {
    await action();
  } catch (requestError) {
    error.value = friendlyError(requestError, '制片操作未完成，请核对后重试。');
  } finally {
    busy.value = false;
  }
}

function generateRequired() {
  void run(async () => {
    const result = await generateV2RequiredRoutineSlides({
      caseId: caseId.value,
      blockIds: selectedBlockIds.value,
      idempotencyKey: idempotencyKey('fc03a-generate'),
    });
    notice.value = result.createdCount
      ? `已按规则生成 ${result.createdCount} 张玻片。`
      : '所选材块的常规玻片要求已满足，没有重复生成。';
    await load();
  });
}

function createExtra() {
  if (selectedBlockIds.value.length !== 1) {
    error.value = '额外新增玻片时请选择一个材块。';
    return;
  }
  void run(async () => {
    const slide = await createV2ExtraRoutineSlide({
      blockId: selectedBlockIds.value[0],
      slideType: extraSlideType.value,
      reason: extraReason.value,
      idempotencyKey: idempotencyKey('fc03a-extra'),
    });
    notice.value = `已额外建立玻片 ${slide.slideCode}。`;
    extraReason.value = '';
    await load();
  });
}

function batchPrint() {
  if (!actionSlides.value.length) return;
  void run(async () => {
    const result = await printV2Slides({
      slideIds: actionSlides.value.map((slide) => slide.slideId),
      reason: actionSlides.value.some((slide) => slide.printCount > 0)
        ? '常规制片标签补打'
        : '常规制片标签打印',
      idempotencyKey: idempotencyKey('fc03a-print'),
    });
    notice.value = `已按材块与玻片顺序发送 ${result.results.length} 张标签。`;
    await load();
  });
}

function batchComplete() {
  const targets = actionSlides.value.filter((slide) => !slide.completed);
  if (!targets.length) return;
  void run(async () => {
    await completeV2Slides({
      slides: targets.map((slide) => ({
        slideId: slide.slideId,
        expectedVersion: slide.concurrencyVersion,
      })),
      idempotencyKey: idempotencyKey('fc03a-complete'),
    });
    notice.value = `已完成 ${targets.length} 张玻片；技术记录不作为完成前置条件。`;
    await load();
  });
}

function correctCode() {
  if (!activeSlide.value) return;
  void run(async () => {
    await correctV2SlideCode({
      slideId: activeSlide.value!.slideId,
      newSlideCode: correctionCode.value,
      reason: correctionReason.value,
      expectedVersion: activeSlide.value!.concurrencyVersion,
    });
    notice.value = `玻片编号已更正为 ${correctionCode.value.trim()}，玻片身份保持不变。`;
    correctionCode.value = '';
    correctionReason.value = '';
    await load();
  });
}

function cancelSlide() {
  if (!activeSlide.value) return;
  void run(async () => {
    await cancelV2Slide({
      slideId: activeSlide.value!.slideId,
      expectedVersion: activeSlide.value!.concurrencyVersion,
      reason: cancelReason.value,
      idempotencyKey: idempotencyKey('fc03a-cancel'),
    });
    notice.value = '误生成玻片已失效，历史与审计记录已保留。';
    cancelReason.value = '';
    await load();
  });
}

function reopenCompletion() {
  if (!activeSlide.value?.completed) return;
  void run(async () => {
    await correctV2SlideCompletion({
      slideId: activeSlide.value!.slideId,
      expectedVersion: activeSlide.value!.concurrencyVersion,
      reason: correctionReason.value,
    });
    notice.value = '完成记录已授权修正，玻片重新进入待完成列表。';
    correctionReason.value = '';
    await load();
  });
}

function recordTrace() {
  if (!selectedTraceIds.value.length) return;
  void run(async () => {
    await completeV2TechnicalTraceBatch({
      targetKind: traceTargetKind.value,
      targetIds: selectedTraceIds.value,
      stageCode: traceStage.value,
      equipmentReference: traceEquipment.value || undefined,
      stainCode: traceStage.value === 'STAINING' ? 'HE' : undefined,
      note: traceNote.value || undefined,
    });
    notice.value = `已记录 ${selectedTraceIds.value.length} 项${traceStages.find((stage) => stage.code === traceStage.value)?.label}事实。`;
    traceNote.value = '';
    await load();
  });
}

function recordException() {
  if (!activeSlide.value) return;
  const phaseCode = traceStage.value === 'COVERSLIPPING' ? 'MOUNTING' : traceStage.value;
  void run(async () => {
    await recordV2HistologyException({
      slideId: activeSlide.value!.slideId,
      phaseCode,
      exceptionCode: exceptionCode.value,
      note: exceptionNote.value,
    });
    notice.value = '制片异常已记录，并进入异常/返工关注队列。';
    exceptionCode.value = '';
    exceptionNote.value = '';
    await load();
  });
}

function performRework() {
  if (!activeSlide.value) return;
  void run(async () => {
    const result = await performV2ProductionRework({
      slideId: activeSlide.value!.slideId,
      reworkTypeCode: reworkType.value,
      reason: reworkReason.value,
      idempotencyKey: idempotencyKey('fc03a-rework'),
    });
    notice.value = result.replacementSlideId
      ? '重新切片已生成新物理玻片，原玻片保留。'
      : `${reworkType.value === 'RESTAIN' ? '重染' : '重扫'}已记录，未创建新物理玻片。`;
    reworkReason.value = '';
    await load();
  });
}

function scanLocate() {
  const barcode = scanCode.value.trim();
  if (!barcode) return;
  void run(async () => {
    const result = await locateV2Material(caseId.value, barcode);
    locatedMaterialId.value = result.materialId;
    if (result.materialKind === 'BLOCK') selectBlock(result.materialId, true);
    else selectSlide(result.materialId, true);
    notice.value = `已定位${result.materialKind === 'BLOCK' ? '材块' : '玻片'} ${result.businessCode}。`;
    scanCode.value = '';
    setTimeout(
      () =>
        document
          .getElementById(`material-${result.materialId}`)
          ?.scrollIntoView({ block: 'center' }),
      0,
    );
    scanInput.value?.focus();
  });
}

async function completeAndNext() {
  if (!productionReady.value) return;
  await run(async () => {
    const workbench = await getV2MyWorkbench();
    const queue = workbench.capabilityQueues.find((candidate) => candidate.key === props.queue);
    const saved = readWorkbenchState();
    const next = filterAndSortQueueItems(queue?.items ?? [], saved).find(
      (item) => item.caseId !== caseId.value,
    );
    if (!next) {
      emit('navigate', safeLocalPath(props.returnTo) || '/v2/workbench');
      return;
    }
    emit(
      'navigate',
      appendNavigationContext(next.workspaceDestination, {
        origin: props.origin,
        queue: props.queue,
        returnTo: props.returnTo,
      }),
    );
  });
}

type SavedWorkbenchState = {
  filter?: string;
  department?: string;
  businessType?: string;
  from?: string;
  to?: string;
  sort?: 'priority' | 'newest';
};

function readWorkbenchState(): SavedWorkbenchState {
  try {
    return JSON.parse(
      sessionStorage.getItem('pis-v2-my-workbench-state') ?? '{}',
    ) as SavedWorkbenchState;
  } catch {
    return {};
  }
}

function queueItemText(item: V2CapabilityQueueItem) {
  return [
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
}

function filterAndSortQueueItems(items: V2CapabilityQueueItem[], state: SavedWorkbenchState) {
  const needle = state.filter?.trim().toLocaleLowerCase() ?? '';
  const department = state.department?.trim().toLocaleLowerCase() ?? '';
  const start = state.from ? new Date(`${state.from}T00:00:00`).getTime() : null;
  const end = state.to ? new Date(`${state.to}T23:59:59.999`).getTime() : null;
  return items
    .filter((item) => {
      const entered = new Date(item.enteredAt).getTime();
      const text = queueItemText(item);
      return (
        (!needle || text.includes(needle)) &&
        (!department || text.includes(department)) &&
        (!state.businessType || item.businessType === state.businessType) &&
        (start === null || entered >= start) &&
        (end === null || entered <= end)
      );
    })
    .sort((left, right) => {
      if (state.sort === 'newest') {
        return new Date(right.enteredAt).getTime() - new Date(left.enteredAt).getTime();
      }
      if (left.urgent !== right.urgent) return left.urgent ? -1 : 1;
      return right.waitingMinutes - left.waitingMinutes;
    });
}

function returnToOrigin() {
  emit('navigate', backTarget.value);
}

watch(caseId, () => void load());
onMounted(() => void load());
</script>

<template>
  <section class="routine-production" aria-label="常规制片工作区">
    <V2CaseHeader
      v-if="caseSummary"
      :case-id="caseSummary.caseId"
      :pathology-no="caseSummary.caseNo"
      :patient-reference="caseSummary.patientReference"
      :visit-reference="caseSummary.visitReference"
      :business-type-code="caseSummary.businessTypeCode"
      current-work="常规制片"
      :progress="`材块 ${blocks.length} | 玻片 ${completedSlides}/${materials?.initialRequiredCount ?? 0} 完成`"
      :report-status="productionReady ? '常规制片要求已满足' : '仍有常规制片工作待完成'"
      :back-label="backLabel"
      @open-case="returnToOrigin"
      @open-overview="emit('navigate', `/v2/cases/${caseId}`)"
    >
      <template #actions>
        <button class="secondary-button" type="button" @click="historyOpen = true">历史记录</button>
      </template>
    </V2CaseHeader>

    <p v-if="error" class="feedback error" role="alert">{{ error }}</p>
    <p v-if="notice" class="feedback success" role="status">{{ notice }}</p>
    <div v-if="loading" class="routine-loading">正在加载材块与玻片…</div>

    <template v-else-if="materials">
      <section class="production-toolbar" aria-label="制片主要操作">
        <div v-if="can('SCAN_MATERIAL')" class="scanner-box">
          <label for="routine-scan">扫码定位材块或玻片</label>
          <input
            id="routine-scan"
            ref="scanInput"
            v-model="scanCode"
            autocomplete="off"
            placeholder="直接扫描后按 Enter"
            @keydown.enter.prevent="scanLocate"
          />
          <button
            type="button"
            class="secondary-button"
            :disabled="busy || !scanCode.trim()"
            @click="scanLocate"
          >
            定位
          </button>
        </div>
        <div class="primary-actions">
          <button
            v-if="can('GENERATE_REQUIRED_SLIDES')"
            class="secondary-button"
            type="button"
            :disabled="busy || !can('GENERATE_REQUIRED_SLIDES')"
            @click="generateRequired"
          >
            按规则生成玻片
          </button>
          <button
            v-if="can('PRINT_SLIDE')"
            class="secondary-button"
            type="button"
            :disabled="busy || !actionSlides.length"
            @click="batchPrint"
          >
            批量打印标签
          </button>
          <button
            v-if="can('COMPLETE_SLIDE')"
            class="primary-button"
            type="button"
            :disabled="
              busy || !pendingSlides.some((slide) => selectedSlideIds.includes(slide.slideId))
            "
            @click="batchComplete"
          >
            批量完成制片
          </button>
        </div>
      </section>

      <section class="material-table" role="table" aria-label="材块与玻片生产表">
        <header class="material-row table-head" role="row">
          <span>材块 / 标本</span><span>玻片</span><span>项目</span><span>创建时间</span
          ><span>状态 / 标签</span><span>操作</span>
        </header>
        <template v-for="block in blocks" :key="block.blockId">
          <div
            v-if="!block.slides.length"
            :id="`material-${block.blockId}`"
            class="material-row"
            :class="{ located: locatedMaterialId === block.blockId }"
            role="row"
          >
            <label class="material-identity">
              <input
                type="checkbox"
                :checked="selectedBlockIds.includes(block.blockId)"
                @change="selectBlock(block.blockId, ($event.target as HTMLInputElement).checked)"
              />
              <span
                ><strong>{{ block.blockCode }}</strong
                ><small>{{ block.specimenCode }} · {{ block.specimenName }}</small></span
              >
            </label>
            <strong>—</strong><span>—</span><span>—</span>
            <span><b class="status-pill warning">待生成</b><small>尚无常规玻片</small></span>
            <button
              v-if="can('GENERATE_REQUIRED_SLIDES')"
              class="text-button"
              type="button"
              @click="
                selectBlock(block.blockId, true);
                generateRequired();
              "
            >
              生成
            </button>
          </div>
          <div
            v-for="slide in block.slides"
            :id="`material-${slide.slideId}`"
            :key="slide.slideId"
            class="material-row"
            :class="{ located: locatedMaterialId === slide.slideId }"
            role="row"
          >
            <label class="material-identity">
              <input
                type="checkbox"
                :checked="selectedBlockIds.includes(block.blockId)"
                @change="selectBlock(block.blockId, ($event.target as HTMLInputElement).checked)"
              />
              <span
                ><strong>{{ block.blockCode }}</strong
                ><small
                  >{{ block.specimenCode }} ·
                  {{ block.samplingDescription || block.specimenName }}</small
                ></span
              >
            </label>
            <label class="slide-identity">
              <input
                type="checkbox"
                :checked="selectedSlideIds.includes(slide.slideId)"
                @change="selectSlide(slide.slideId, ($event.target as HTMLInputElement).checked)"
              />
              <strong>{{ slide.slideCode }}</strong>
            </label>
            <span>{{ slide.slideType }}</span>
            <span>{{
              histologySlide(slide.slideId)?.phases.find((fact) => fact.startedAt)?.startedAt
                ? formatDateTime(
                    histologySlide(slide.slideId)!.phases.find((fact) => fact.startedAt)!
                      .startedAt!,
                  )
                : '已建立身份'
            }}</span>
            <span>
              <b :class="slide.completed ? 'status-pill success' : 'status-pill warning'">{{
                slide.completed ? '已完成' : '待完成'
              }}</b>
              <small>{{ slide.printCount ? `已打印 ${slide.printCount} 次` : '标签未打印' }}</small>
            </span>
            <button class="text-button" type="button" @click="selectSlide(slide.slideId, true)">
              选择操作
            </button>
          </div>
        </template>
      </section>

      <section class="workspace-controls" aria-label="玻片操作">
        <div v-if="can('CREATE_EXTRA_SLIDE')" class="control-group">
          <strong>额外新增玻片</strong>
          <span>仅在真实物理需要时使用；按规则生成不会重复补产。</span>
          <input v-model="extraSlideType" aria-label="额外玻片项目" placeholder="HE" />
          <input
            v-model="extraReason"
            aria-label="额外制片原因"
            placeholder="额外制片原因（必填）"
          />
          <button
            class="secondary-button"
            type="button"
            :disabled="busy || selectedBlockIds.length !== 1 || !extraReason.trim()"
            @click="createExtra"
          >
            新增玻片
          </button>
        </div>
        <div class="control-group">
          <strong>当前玻片</strong>
          <select v-model="selectedActionSlideId" aria-label="当前操作玻片">
            <option value="" disabled>选择玻片</option>
            <option v-for="slide in slides" :key="slide.slideId" :value="slide.slideId">
              {{ slide.slideCode }} · {{ slide.completed ? '已完成' : '待完成' }}
            </option>
          </select>
          <input v-model="correctionCode" aria-label="新玻片编号" placeholder="新玻片编号" />
          <input v-model="correctionReason" aria-label="修正原因" placeholder="修正原因（必填）" />
          <div class="inline-actions">
            <button
              v-if="can('CORRECT_SLIDE_CODE')"
              class="secondary-button"
              type="button"
              :disabled="busy || !activeSlide || !correctionCode.trim() || !correctionReason.trim()"
              @click="correctCode"
            >
              更正编号
            </button>
            <button
              v-if="can('CORRECT_SLIDE_COMPLETION')"
              class="text-button"
              type="button"
              :disabled="busy || !activeSlide?.completed || !correctionReason.trim()"
              @click="reopenCompletion"
            >
              修正完成记录
            </button>
          </div>
          <input
            v-model="cancelReason"
            aria-label="玻片失效原因"
            placeholder="误生成失效原因（必填）"
          />
          <button
            v-if="can('CANCEL_SLIDE')"
            class="danger-button"
            type="button"
            :disabled="busy || !activeSlide || !cancelReason.trim()"
            @click="cancelSlide"
          >
            将误生成玻片设为失效
          </button>
        </div>
      </section>

      <details v-if="can('RECORD_TECHNICAL_TRACE')" class="secondary-panel">
        <summary>技术记录（可选，不阻止玻片完成）</summary>
        <p>脱水、包埋作用于材块；切片、染色、封片作用于玻片。未记录任一环节也可完成制片。</p>
        <div class="trace-stage-picker" role="group" aria-label="技术记录类型">
          <button
            v-for="stage in traceStages"
            :key="stage.code"
            type="button"
            :class="{ active: traceStage === stage.code }"
            @click="traceStage = stage.code"
          >
            {{ stage.label }}
          </button>
        </div>
        <div class="trace-summary">
          <span v-for="stage in traceStages" :key="stage.code"
            >{{ stage.label }}
            {{
              histologySlides
                .flatMap((slide) => slide.phases)
                .filter((fact) => fact.phaseCode === stage.code && fact.completedAt).length
            }}</span
          >
        </div>
        <div class="trace-form">
          <span
            >当前将记录到 {{ traceTargetKind === 'BLOCK' ? '已选材块' : '已选玻片' }}（{{
              selectedTraceIds.length
            }}
            项）</span
          >
          <input v-model="traceEquipment" aria-label="技术设备" placeholder="设备编码（可选）" />
          <input v-model="traceNote" aria-label="技术记录说明" placeholder="说明（可选）" />
          <button
            class="secondary-button"
            type="button"
            :disabled="busy || !selectedTraceIds.length"
            @click="recordTrace"
          >
            批量记录完成
          </button>
        </div>
      </details>

      <details
        v-if="can('RECORD_PRODUCTION_EXCEPTION') || can('PERFORM_REWORK')"
        class="secondary-panel"
      >
        <summary>异常与物理返工</summary>
        <div class="exception-grid">
          <label
            >异常类型
            <select v-model="exceptionCode">
              <option value="" disabled>请选择</option>
              <option>组织脱落</option>
              <option>切片褶皱</option>
              <option>染色异常</option>
              <option>玻片破损</option>
              <option>标签损坏</option>
              <option>其他</option>
            </select>
          </label>
          <label>异常说明<input v-model="exceptionNote" placeholder="说明发生了什么" /></label>
          <button
            v-if="can('RECORD_PRODUCTION_EXCEPTION')"
            class="secondary-button"
            type="button"
            :disabled="busy || !activeSlide || !exceptionCode || !exceptionNote.trim()"
            @click="recordException"
          >
            记录异常
          </button>
          <label
            >返工方式
            <select v-model="reworkType">
              <option value="RECUT">重新切片（生成新玻片）</option>
              <option value="RESTAIN">重染（不生成新玻片）</option>
              <option value="RESCAN">重扫（不生成物理玻片）</option>
            </select>
          </label>
          <label>返工原因<input v-model="reworkReason" placeholder="返工原因（必填）" /></label>
          <button
            v-if="can('PERFORM_REWORK')"
            class="primary-button"
            type="button"
            :disabled="busy || !activeSlide || !reworkReason.trim()"
            @click="performRework"
          >
            执行返工
          </button>
        </div>
      </details>

      <footer class="workspace-footer">
        <span>{{
          productionReady
            ? '当前病例的常规初始玻片要求已满足。'
            : `仍有 ${Math.max(0, (materials.initialRequiredCount ?? 0) - completedSlides)} 张要求未完成。`
        }}</span>
        <button class="secondary-button" type="button" @click="returnToOrigin">
          {{ backLabel }}
        </button>
        <button
          class="secondary-button"
          type="button"
          :disabled="!productionReady"
          @click="emit('navigate', safeLocalPath(props.returnTo) || '/v2/workbench')"
        >
          完成并返回工作台
        </button>
        <button
          class="primary-button"
          type="button"
          :disabled="busy || !productionReady"
          @click="completeAndNext"
        >
          完成并下一项
        </button>
      </footer>
    </template>

    <V2HistoryDrawer
      :open="historyOpen"
      :case-id="caseId"
      title="制片历史"
      target-label="当前常规制片"
      @close="historyOpen = false"
    />
  </section>
</template>

<style scoped>
.routine-production {
  display: grid;
  gap: 12px;
  min-width: 0;
}
.routine-loading {
  padding: 28px;
  color: var(--muted-text, #65707f);
}
.production-toolbar,
.workspace-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  background: #fff;
  border: 1px solid #dfe5eb;
  border-radius: 8px;
}
.scanner-box,
.primary-actions,
.inline-actions,
.trace-form {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.scanner-box label {
  font-weight: 700;
}
.scanner-box input {
  width: 220px;
}
.material-table {
  overflow: auto;
  background: #fff;
  border: 1px solid #dfe5eb;
  border-radius: 8px;
}
.material-row {
  display: grid;
  grid-template-columns:
    minmax(190px, 1.4fr) minmax(130px, 1fr) 90px minmax(130px, 1fr) minmax(130px, 1fr)
    90px;
  align-items: center;
  min-height: 50px;
  padding: 0 12px;
  border-bottom: 1px solid #edf0f3;
  gap: 10px;
}
.material-row:last-child {
  border-bottom: 0;
}
.material-row.located {
  outline: 2px solid #167a65;
  outline-offset: -2px;
  background: #f2fbf8;
}
.table-head {
  position: sticky;
  top: 0;
  z-index: 1;
  min-height: 38px;
  background: #f5f7f9;
  color: #586474;
  font-size: 12px;
  font-weight: 700;
}
.material-identity,
.slide-identity {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.material-identity span,
.material-row > span {
  display: grid;
  gap: 2px;
  min-width: 0;
}
.material-row small {
  color: #6a7482;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.workspace-controls {
  display: grid;
  grid-template-columns: 1fr 1.35fr;
  gap: 12px;
}
.control-group {
  display: grid;
  grid-template-columns: minmax(120px, auto) 1fr;
  align-items: center;
  gap: 8px;
  padding: 12px;
  background: #fff;
  border: 1px solid #dfe5eb;
  border-radius: 8px;
}
.control-group > strong,
.control-group > span {
  grid-column: 1 / -1;
}
.secondary-panel {
  background: #fff;
  border: 1px solid #dfe5eb;
  border-radius: 8px;
  padding: 0 14px 12px;
}
.secondary-panel summary {
  cursor: pointer;
  padding: 12px 0;
  font-weight: 700;
}
.secondary-panel p {
  margin: 0 0 10px;
  color: #65707f;
}
.trace-stage-picker,
.trace-summary {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}
.trace-stage-picker button,
.trace-summary span {
  border: 1px solid #d5dce3;
  border-radius: 999px;
  background: #f7f9fa;
  padding: 5px 10px;
}
.trace-stage-picker button.active {
  border-color: #167a65;
  color: #0d6b58;
  background: #edf8f5;
}
.exception-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(180px, 1fr));
  gap: 10px;
  align-items: end;
}
.exception-grid label {
  display: grid;
  gap: 5px;
}
.workspace-footer {
  position: sticky;
  bottom: 8px;
  box-shadow: 0 8px 24px rgba(20, 35, 50, 0.12);
}
.workspace-footer > span {
  margin-right: auto;
  color: #586474;
}
.danger-button {
  color: #a32222;
  border: 1px solid #dfb3b3;
  background: #fff7f7;
  border-radius: 6px;
  padding: 7px 11px;
}
input,
select {
  min-height: 34px;
  border: 1px solid #cfd7df;
  border-radius: 6px;
  padding: 5px 8px;
  background: #fff;
}
button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
@media (max-width: 1450px) {
  .material-row {
    grid-template-columns: minmax(170px, 1.3fr) 120px 70px 105px 120px 75px;
    min-width: 920px;
    min-height: 46px;
  }
  .workspace-controls {
    grid-template-columns: 1fr;
  }
  .exception-grid {
    grid-template-columns: repeat(2, minmax(180px, 1fr));
  }
  .production-toolbar {
    align-items: flex-start;
  }
}
</style>
