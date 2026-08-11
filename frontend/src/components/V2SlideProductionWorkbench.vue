<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';

import { friendlyError, formatDateTime, idempotencyKey } from '../uiText';
import {
  completeV2Slide,
  createV2DirectCytologySlide,
  getV2MaterialTree,
  printV2Slide,
  type V2MaterialTree,
} from '../v2MaterialApi';
import V2CaseHeader from './V2CaseHeader.vue';
import V2HistoryDrawer from './V2HistoryDrawer.vue';
import { getV2MyWorkbench, type V2WorkbenchItem } from '../v2WorkspaceApi';
import {
  completeV2HistologyPhase,
  getV2HistologyWorkbench,
  histologyPhases,
  recordV2HistologyException,
  startV2HistologyPhase,
  startV2HistologyPhaseBatch,
  completeV2HistologyPhaseBatch,
  type HistologyPhaseCode,
  type V2HistologyPhase,
  type V2HistologyQueues,
  type V2HistologySlide,
} from '../v2HistologyApi';

type HistologyQueueView =
  | 'DEHYDRATION'
  | 'EMBEDDING'
  | 'CUTTING'
  | 'STAINING'
  | 'COVERSLIPPING'
  | 'COMPLETED'
  | 'EXCEPTIONS';

const caseId = defineModel<string>('caseId', { default: '' });
const emit = defineEmits<{ navigate: [path: string] }>();
const materialTree = ref<V2MaterialTree | null>(null);
const selectedSlideIds = ref<string[]>([]);
const scanCode = ref('');
const scanInput = ref<HTMLInputElement | null>(null);
const scanFeedback = ref<{ tone: 'success' | 'warning' | 'error' | 'neutral'; message: string }>({
  tone: 'neutral',
  message: '扫描玻片条码后按 Enter，系统会完成玻片并自动等待下一张。',
});
const directSpecimenId = ref('');
const directSlideCode = ref('');
const directSlideType = ref('CYTOLOGY');
const loading = ref(false);
const busy = ref(false);
const error = ref('');
const notice = ref('');
const histologySlides = ref<V2HistologySlide[]>([]);
const histologyQueues = ref<V2HistologyQueues>({
  dehydration: 0,
  embedding: 0,
  cutting: 0,
  staining: 0,
  coverslipping: 0,
  completed: 0,
  exceptions: 0,
});
const histologyError = ref('');
const activeHistologySlideId = ref('');
const activeHistologyPhase = ref<HistologyPhaseCode>('DEHYDRATION');
const activeHistologyQueue = ref<HistologyQueueView>('DEHYDRATION');
const exceptionFormOpen = ref(false);
const exceptionCode = ref('');
const exceptionNote = ref('');
const historyDrawerOpen = ref(false);
const cytologyPreparationCases = ref<V2WorkbenchItem[]>([]);

const caseSlides = computed(() =>
  caseId.value
    ? histologySlides.value.filter((slide) => slide.caseId === caseId.value)
    : histologySlides.value,
);
const caseHeaderSlide = computed(() => caseSlides.value[0] ?? null);
const caseMaterialProgress = computed(() => {
  if (!caseSlides.value.length) return '0/0';
  return `${caseSlides.value.filter((slide) => slide.slideCompletedAt).length}/${caseSlides.value.length}`;
});
const supportsDirectSlide = computed(
  () => materialTree.value?.capability?.supportsDirectSlides ?? false,
);
const directSlides = computed(
  () => materialTree.value?.specimens.flatMap((specimen) => specimen.directSlides) ?? [],
);
const selectedHistologySlide = computed(
  () =>
    histologySlides.value.find((slide) => slide.slideId === activeHistologySlideId.value) ??
    histologySlides.value[0] ??
    null,
);
const activeHistologyPhaseFact = computed<V2HistologyPhase | null>(
  () =>
    selectedHistologySlide.value?.phases?.find(
      (phase) => phase.phaseCode === activeHistologyPhase.value,
    ) ?? null,
);
const visibleHistologySlides = computed(() =>
  histologySlides.value.filter((slide) => {
    return queueOf(slide) === activeHistologyQueue.value;
  }),
);

function queueOf(slide: V2HistologySlide): HistologyQueueView {
  const queue = slide.derivedQueue;
  const phases = slide.phases ?? [];
  if (queue === 'SECTIONING') return 'CUTTING';
  if (queue === 'MOUNTING') return 'COVERSLIPPING';
  if (
    queue === 'DEHYDRATION' ||
    queue === 'EMBEDDING' ||
    queue === 'CUTTING' ||
    queue === 'STAINING' ||
    queue === 'COVERSLIPPING' ||
    queue === 'COMPLETED' ||
    queue === 'EXCEPTIONS'
  ) {
    return queue;
  }
  if (slide.slideCompletedAt) return 'COMPLETED';
  const nextPhase = histologyPhases.find(
    (phase) => !phases.find((item) => item.phaseCode === phase.code)?.completedAt,
  );
  return nextPhase?.code === 'SECTIONING'
    ? 'CUTTING'
    : nextPhase?.code === 'MOUNTING'
      ? 'COVERSLIPPING'
      : (nextPhase?.code ?? 'COVERSLIPPING');
}

function currentPhase(slide: V2HistologySlide) {
  const queue = queueOf(slide);
  if (queue === 'CUTTING') return 'SECTIONING';
  if (queue === 'COVERSLIPPING') return 'MOUNTING';
  if (histologyPhases.some((phase) => phase.code === queue)) return queue as HistologyPhaseCode;
  const phases = slide.phases ?? [];
  return (
    histologyPhases.find((phase) => {
      const fact = phases.find((item) => item.phaseCode === phase.code);
      return !fact?.completedAt;
    })?.code ?? 'MOUNTING'
  );
}

function currentPhaseFact(slide: V2HistologySlide) {
  return slide.phases?.find((phase) => phase.phaseCode === currentPhase(slide)) ?? null;
}

watch(
  () => caseId.value,
  () => {
    void loadMaterialTree();
    void loadHistology();
    void loadCytologyQueue();
  },
);

async function run(action: () => Promise<void>) {
  busy.value = true;
  error.value = '';
  notice.value = '';
  try {
    await action();
  } catch (requestError) {
    error.value = friendlyError(requestError, '制片操作未完成，请刷新后重试。');
  } finally {
    busy.value = false;
  }
}

async function loadMaterialTree() {
  if (!caseId.value) {
    materialTree.value = null;
    return;
  }
  try {
    materialTree.value = await getV2MaterialTree(caseId.value);
    directSpecimenId.value = materialTree.value.specimens[0]?.specimenId ?? '';
    directSlideCode.value ||= `${materialTree.value.specimens[0]?.specimenCode ?? 'A'}-1`;
  } catch {
    materialTree.value = null;
  }
}

async function loadHistology() {
  loading.value = true;
  histologyError.value = '';
  try {
    const response = await getV2HistologyWorkbench(caseId.value || undefined);
    histologySlides.value = response.slides;
    histologyQueues.value = response.queues;
    if (!histologySlides.value.some((slide) => slide.slideId === activeHistologySlideId.value)) {
      activeHistologySlideId.value = histologySlides.value[0]?.slideId ?? '';
    }
  } catch (requestError) {
    histologySlides.value = [];
    histologyError.value = friendlyError(requestError, '技术过程记录暂时无法加载。');
  } finally {
    loading.value = false;
  }
}

async function loadCytologyQueue() {
  if (caseId.value) {
    cytologyPreparationCases.value = [];
    return;
  }
  try {
    const response = await getV2MyWorkbench();
    cytologyPreparationCases.value = response.queues.cytologyPreparationCases;
  } catch {
    cytologyPreparationCases.value = [];
  }
}

function selectedHistologySlideIds() {
  return selectedSlideIds.value.filter((slideId) =>
    histologySlides.value.some((slide) => slide.slideId === slideId),
  );
}

function toggleSlide(slideId: string) {
  selectedSlideIds.value = selectedSlideIds.value.includes(slideId)
    ? selectedSlideIds.value.filter((id) => id !== slideId)
    : [...selectedSlideIds.value, slideId];
}

function printSlide(slide: V2HistologySlide) {
  void run(async () => {
    await printV2Slide({
      slideId: slide.slideId,
      reason: slide.printCount ? '制片工作台补打' : '制片工作台打印',
      idempotencyKey: idempotencyKey('ux01-slide-print'),
    });
    notice.value = `玻片 ${slide.slideCode} 标签已发送到当前打印机。`;
    await loadHistology();
  });
}

function findCaseSlide(code: string): V2HistologySlide | null {
  const normalized = code.toUpperCase();
  return (
    histologySlides.value.find(
      (item) =>
        item.slideCode.toUpperCase() === normalized &&
        (!caseId.value || item.caseId === caseId.value),
    ) ?? null
  );
}

function submitScan() {
  const rawCode = scanCode.value.trim();
  if (!rawCode) return;
  void run(async () => {
    if (caseId.value && !materialTree.value) await loadMaterialTree();
    const slide = findCaseSlide(rawCode.toUpperCase());
    if (!slide) {
      scanFeedback.value = {
        tone: 'error',
        message: `未找到玻片“${rawCode}”，请检查条码。`,
      };
      scanCode.value = '';
      scanInput.value?.focus();
      return;
    }
    if (slide.slideCompletedAt) {
      scanFeedback.value = {
        tone: 'warning',
        message: `玻片 ${slide.slideCode} 已于 ${formatDateTime(slide.slideCompletedAt)} 完成，无需重复操作。`,
      };
      scanCode.value = '';
      scanInput.value?.focus();
      return;
    }
    await completeV2Slide({
      slideId: slide.slideId,
      expectedVersion: slide.concurrencyVersion ?? 0,
      idempotencyKey: idempotencyKey('ux01-slide-scan'),
    });
    scanFeedback.value = {
      tone: 'success',
      message: `玻片 ${slide.slideCode} 已完成，请扫描下一张。`,
    };
    scanCode.value = '';
    await loadHistology();
    scanInput.value?.focus();
  });
}

function createDirectSlide() {
  if (!caseId.value || !directSpecimenId.value || !directSlideCode.value.trim()) return;
  void run(async () => {
    await createV2DirectCytologySlide({
      caseId: caseId.value,
      specimenId: directSpecimenId.value,
      slideCode: directSlideCode.value.trim(),
      slideType: directSlideType.value,
      idempotencyKey: idempotencyKey('ux01-direct-slide'),
    });
    notice.value = `直接玻片 ${directSlideCode.value.trim()} 已建立，不需要蜡块。`;
    directSlideCode.value = '';
    await Promise.all([loadHistology(), loadMaterialTree()]);
  });
}

function completeDirectSlide(slide: NonNullable<typeof directSlides.value>[number]) {
  if (slide.completed) return;
  void run(async () => {
    await completeV2Slide({
      slideId: slide.slideId,
      expectedVersion: slide.concurrencyVersion,
      idempotencyKey: idempotencyKey('px03-cytology-slide-complete'),
    });
    notice.value = `直接玻片 ${slide.slideCode} 已完成，病例进入待诊断。`;
    await loadMaterialTree();
  });
}

function phaseLabel(phaseCode: HistologyPhaseCode) {
  return histologyPhases.find((phase) => phase.code === phaseCode)?.label ?? phaseCode;
}

function phaseStatus(fact: V2HistologyPhase | null) {
  if (!fact?.startedAt) return '待处理';
  if (fact.exceptionCode && !fact.completedAt) return '异常';
  if (!fact.completedAt) return '处理中';
  return '已完成';
}

function phaseStatusClass(fact: V2HistologyPhase | null) {
  return {
    'status-pill': true,
    success: Boolean(fact?.completedAt),
    warning: Boolean(fact?.exceptionCode && !fact.completedAt),
    current: Boolean(fact?.startedAt && !fact?.completedAt && !fact?.exceptionCode),
  };
}

function startHistologyPhase() {
  const slide = selectedHistologySlide.value;
  if (!slide) return;
  void run(async () => {
    await startV2HistologyPhase({ slideId: slide.slideId, phaseCode: activeHistologyPhase.value });
    notice.value = `${slide.slideCode} 已开始${phaseLabel(activeHistologyPhase.value)}。`;
    await loadHistology();
  });
}

function completeHistologyPhase() {
  const slide = selectedHistologySlide.value;
  if (!slide) return;
  void run(async () => {
    await completeV2HistologyPhase(slide.slideId, activeHistologyPhase.value);
    notice.value = `${slide.slideCode} 的${phaseLabel(activeHistologyPhase.value)}已完成。`;
    await loadHistology();
  });
}

function startHistologyPhaseBatch() {
  const slideIds = selectedHistologySlideIds();
  if (!slideIds.length) return;
  void run(async () => {
    await startV2HistologyPhaseBatch(slideIds, activeHistologyPhase.value);
    notice.value = `已开始 ${slideIds.length} 张玻片的${phaseLabel(activeHistologyPhase.value)}。`;
    await loadHistology();
  });
}

function completeHistologyPhaseBatch() {
  const slideIds = selectedHistologySlideIds();
  if (!slideIds.length) return;
  void run(async () => {
    await completeV2HistologyPhaseBatch(slideIds, activeHistologyPhase.value);
    notice.value = `已完成 ${slideIds.length} 张玻片的${phaseLabel(activeHistologyPhase.value)}。`;
    await loadHistology();
  });
}

function submitHistologyException() {
  const slide = selectedHistologySlide.value;
  if (!slide || !exceptionCode.value.trim() || !exceptionNote.value.trim()) return;
  void run(async () => {
    await recordV2HistologyException({
      slideId: slide.slideId,
      phaseCode: activeHistologyPhase.value,
      exceptionCode: exceptionCode.value,
      note: exceptionNote.value,
    });
    notice.value = `${slide.slideCode} 的${phaseLabel(activeHistologyPhase.value)}异常已记录。`;
    exceptionFormOpen.value = false;
    exceptionCode.value = '';
    exceptionNote.value = '';
    await loadHistology();
  });
}

onMounted(() => {
  void Promise.all([loadMaterialTree(), loadHistology(), loadCytologyQueue()]);
});
</script>

<template>
  <section class="production-workspace" aria-label="病理技术工作台">
    <header class="page-heading compact-heading">
      <div>
        <p class="section-kicker">制片</p>
        <h2>病理技术工作台</h2>
        <p>按技术环节处理材料，打印和扫码是快捷操作。</p>
      </div>
      <div class="heading-actions">
        <button class="secondary-button" type="button" :disabled="loading" @click="loadHistology">
          {{ loading ? '刷新中…' : '刷新队列' }}
        </button>
        <button
          class="primary-button"
          type="button"
          :disabled="!selectedHistologySlideIds().length || busy"
          @click="completeHistologyPhaseBatch"
        >
          完成所选环节（{{ selectedHistologySlideIds().length }}）
        </button>
      </div>
    </header>

    <p v-if="error" class="feedback error" role="alert">{{ error }}</p>
    <p v-if="notice" class="feedback success" role="status">{{ notice }}</p>

    <section
      v-if="!caseId && cytologyPreparationCases.length"
      class="workspace-panel cytology-preparation-queue"
      aria-labelledby="cytology-preparation-heading"
    >
      <header class="panel-title-row">
        <div>
          <p class="section-kicker">CYTOLOGY</p>
          <h3 id="cytology-preparation-heading">待细胞制片</h3>
          <p class="panel-help">已登记并收到标本即可进入队列，建立直接玻片前不需要蜡块。</p>
        </div>
        <span class="status-pill warning">{{ cytologyPreparationCases.length }} 例</span>
      </header>
      <button
        v-for="item in cytologyPreparationCases"
        :key="item.caseId"
        type="button"
        class="personal-queue-row"
        @click="emit('navigate', `/v2/production/${item.caseId}`)"
      >
        <span class="queue-row-main">
          <strong>{{ item.pathologyNo }}</strong>
          <small>{{ item.patientReference }} · {{ item.businessTypeName }}</small>
        </span>
        <span>{{ item.workLabel }}</span>
        <small>{{ formatDateTime(item.enteredAt) }}</small>
        <span class="queue-row-arrow" aria-hidden="true">→</span>
      </button>
    </section>

    <V2CaseHeader
      v-if="caseHeaderSlide"
      :case-id="caseHeaderSlide.caseId"
      :pathology-no="caseHeaderSlide.caseNo"
      :patient-reference="caseHeaderSlide.patientReference"
      :business-type-code="caseHeaderSlide.businessTypeCode ?? ''"
      current-responsibility="制片人员"
      report-status="制片处理中"
      :progress="`${caseMaterialProgress} 张玻片完成`"
      @open-case="emit('navigate', `/v2/cases/${caseHeaderSlide.caseId}`)"
    >
      <template #actions>
        <button
          class="secondary-button"
          type="button"
          @click="emit('navigate', `/v2/grossing/${caseHeaderSlide.caseId}`)"
        >
          查看取材
        </button>
        <button class="secondary-button" type="button" @click="historyDrawerOpen = true">
          历史记录
        </button>
      </template>
    </V2CaseHeader>

    <section class="scan-completion-panel" aria-labelledby="scan-heading">
      <div>
        <label id="scan-heading" for="slide-scan-input">扫码完成玻片</label>
        <div class="input-action-row">
          <input
            id="slide-scan-input"
            ref="scanInput"
            v-model="scanCode"
            autocomplete="off"
            placeholder="扫描或输入玻片号"
            @keydown.enter.prevent="submitScan"
          />
          <button class="primary-button" type="button" :disabled="busy" @click="submitScan">
            完成
          </button>
        </div>
      </div>
      <p class="scan-feedback" :class="scanFeedback.tone" role="status">
        {{ scanFeedback.message }}
      </p>
    </section>

    <section
      class="workspace-panel histology-queue-panel"
      aria-labelledby="histology-queue-heading"
    >
      <header class="panel-title-row">
        <div>
          <p class="section-kicker">Histology</p>
          <h3 id="histology-queue-heading">按技术环节处理</h3>
          <p class="panel-help">
            状态由开始/完成时间事实推导；打印、扫码和异常不会改变技术环节状态。
          </p>
        </div>
        <span class="status-pill">轻量事实</span>
      </header>
      <div class="histology-stage-queues" role="group" aria-label="技术环节队列">
        <button
          type="button"
          :aria-pressed="activeHistologyQueue === 'DEHYDRATION'"
          :class="{ active: activeHistologyQueue === 'DEHYDRATION' }"
          @click="
            activeHistologyQueue = 'DEHYDRATION';
            activeHistologyPhase = 'DEHYDRATION';
          "
        >
          待脱水 <b>{{ histologyQueues.dehydration }}</b>
        </button>
        <button
          type="button"
          :aria-pressed="activeHistologyQueue === 'EMBEDDING'"
          :class="{ active: activeHistologyQueue === 'EMBEDDING' }"
          @click="
            activeHistologyQueue = 'EMBEDDING';
            activeHistologyPhase = 'EMBEDDING';
          "
        >
          待包埋 <b>{{ histologyQueues.embedding }}</b>
        </button>
        <button
          type="button"
          :aria-pressed="activeHistologyQueue === 'CUTTING'"
          :class="{ active: activeHistologyQueue === 'CUTTING' }"
          @click="
            activeHistologyQueue = 'CUTTING';
            activeHistologyPhase = 'SECTIONING';
          "
        >
          待切片 <b>{{ histologyQueues.cutting }}</b>
        </button>
        <button
          type="button"
          :aria-pressed="activeHistologyQueue === 'STAINING'"
          :class="{ active: activeHistologyQueue === 'STAINING' }"
          @click="
            activeHistologyQueue = 'STAINING';
            activeHistologyPhase = 'STAINING';
          "
        >
          待染色 <b>{{ histologyQueues.staining }}</b>
        </button>
        <button
          type="button"
          :aria-pressed="activeHistologyQueue === 'COVERSLIPPING'"
          :class="{ active: activeHistologyQueue === 'COVERSLIPPING' }"
          @click="
            activeHistologyQueue = 'COVERSLIPPING';
            activeHistologyPhase = 'MOUNTING';
          "
        >
          待封片 <b>{{ histologyQueues.coverslipping }}</b>
        </button>
        <button
          type="button"
          :aria-pressed="activeHistologyQueue === 'COMPLETED'"
          :class="{ active: activeHistologyQueue === 'COMPLETED' }"
          @click="activeHistologyQueue = 'COMPLETED'"
        >
          已完成 <b>{{ histologyQueues.completed }}</b>
        </button>
        <button
          type="button"
          :aria-pressed="activeHistologyQueue === 'EXCEPTIONS'"
          :class="{ active: activeHistologyQueue === 'EXCEPTIONS' }"
          @click="activeHistologyQueue = 'EXCEPTIONS'"
        >
          异常 <b>{{ histologyQueues.exceptions }}</b>
        </button>
      </div>
      <div class="inline-actions histology-batch-actions">
        <span class="muted">已选择 {{ selectedHistologySlideIds().length }} 张玻片</span>
        <button
          class="secondary-button"
          type="button"
          :disabled="busy || !selectedHistologySlideIds().length"
          @click="startHistologyPhaseBatch"
        >
          开始所选{{ phaseLabel(activeHistologyPhase) }}
        </button>
        <button
          class="secondary-button"
          type="button"
          :disabled="busy || !selectedHistologySlideIds().length"
          @click="completeHistologyPhaseBatch"
        >
          完成所选{{ phaseLabel(activeHistologyPhase) }}
        </button>
      </div>
    </section>

    <section v-if="supportsDirectSlide && materialTree" class="workspace-panel direct-slide-panel">
      <header class="panel-title-row">
        <div>
          <p class="section-kicker">细胞病理</p>
          <h3>直接建立玻片</h3>
        </div>
        <span class="status-pill success">蜡块可选</span>
      </header>
      <div class="field-grid three-columns direct-slide-fields">
        <label>
          来源标本
          <select v-model="directSpecimenId">
            <option
              v-for="specimen in materialTree.specimens"
              :key="specimen.specimenId"
              :value="specimen.specimenId"
            >
              {{ specimen.specimenCode }} · {{ specimen.specimenNo }}
            </option>
          </select>
        </label>
        <label>玻片号 <input v-model="directSlideCode" placeholder="例如 A-1" /></label>
        <label>
          玻片类型
          <select v-model="directSlideType">
            <option value="CYTOLOGY">细胞涂片</option>
            <option value="HE">HE</option>
          </select>
        </label>
      </div>
      <button class="secondary-button" type="button" :disabled="busy" @click="createDirectSlide">
        建立直接玻片
      </button>
      <div v-if="directSlides.length" class="direct-slide-list">
        <h4>标本直接玻片</h4>
        <div v-for="slide in directSlides" :key="slide.slideId" class="direct-slide-row">
          <span
            ><strong>{{ slide.slideCode }}</strong
            ><small>{{ slide.slideType }}</small></span
          >
          <span :class="slide.completed ? 'status-pill success' : 'status-pill warning'">
            {{ slide.completed ? '已完成' : '待完成' }}
          </span>
          <button
            v-if="!slide.completed"
            class="primary-button"
            type="button"
            :disabled="busy"
            @click="completeDirectSlide(slide)"
          >
            完成玻片
          </button>
        </div>
      </div>
    </section>

    <section class="workspace-panel histology-fact-panel" aria-labelledby="histology-heading">
      <header class="panel-title-row">
        <div>
          <p class="section-kicker">技术过程</p>
          <h3 id="histology-heading">脱水、包埋、切片、染色、封片</h3>
          <p class="panel-help">只记录开始、完成、操作人和异常；当前状态由实际时间事实显示。</p>
        </div>
        <span class="status-pill">轻量记录</span>
      </header>
      <p v-if="histologyError" class="feedback warning" role="status">{{ histologyError }}</p>
      <div v-else-if="!histologySlides.length" class="empty-state compact">
        <strong>当前没有可记录技术过程的玻片</strong>
        <span>先建立玻片，之后可在这里记录每个环节。</span>
      </div>
      <div v-else>
        <div
          v-if="visibleHistologySlides.length"
          class="histology-work-list"
          role="table"
          aria-label="技术环节材料列表"
        >
          <div class="histology-work-row header" role="row">
            <span>选择</span><span>病理号 / 患者</span><span>标本 / 蜡块</span><span>玻片</span
            ><span>当前环节</span><span>开始时间</span><span>操作人</span><span>异常</span
            ><span>操作</span>
          </div>
          <div
            v-for="slide in visibleHistologySlides"
            :key="`work-${slide.slideId}`"
            class="histology-work-row"
            :class="{ selected: selectedHistologySlide?.slideId === slide.slideId }"
            role="row"
            @click="activeHistologySlideId = slide.slideId"
          >
            <span
              ><input
                type="checkbox"
                :aria-label="`选择技术玻片 ${slide.slideCode}`"
                :checked="selectedSlideIds.includes(slide.slideId)"
                @click.stop
                @change="toggleSlide(slide.slideId)"
            /></span>
            <span
              ><strong>{{ slide.caseNo }}</strong
              ><small>{{ slide.patientReference }}</small></span
            >
            <span
              ><strong>{{ slide.specimenCode || '—' }}</strong
              ><small>{{ slide.blockCode || '直接玻片' }}</small></span
            >
            <span
              ><strong>{{ slide.slideCode }}</strong
              ><small>{{ slide.slideType }}</small></span
            >
            <span>{{
              activeHistologyQueue === 'COMPLETED'
                ? '已完成'
                : activeHistologyQueue === 'EXCEPTIONS'
                  ? '异常'
                  : phaseLabel(currentPhase(slide))
            }}</span>
            <span>{{
              currentPhaseFact(slide)?.startedAt
                ? formatDateTime(currentPhaseFact(slide)!.startedAt!)
                : '—'
            }}</span>
            <span>{{ currentPhaseFact(slide)?.operatorRef || '—' }}</span>
            <span>{{ currentPhaseFact(slide)?.exceptionNote || '—' }}</span>
            <span class="inline-actions">
              <button
                class="text-button"
                type="button"
                @click.stop="activeHistologySlideId = slide.slideId"
              >
                处理
              </button>
              <button class="text-button" type="button" @click.stop="printSlide(slide)">
                打印/补打
              </button>
              <button
                class="text-button"
                type="button"
                @click.stop="emit('navigate', `/v2/cases/${slide.caseId}`)"
              >
                病例
              </button>
            </span>
          </div>
        </div>
        <div v-else class="empty-state compact">
          <strong>当前环节没有待处理材料</strong>
          <span>可以切换上方环节，或刷新队列。</span>
        </div>
        <div v-if="selectedHistologySlide" class="histology-fact-content">
          <div class="histology-phase-tabs" role="tablist" aria-label="技术环节">
            <button
              v-for="phase in histologyPhases"
              :key="phase.code"
              type="button"
              role="tab"
              :aria-selected="activeHistologyPhase === phase.code"
              :class="{ active: activeHistologyPhase === phase.code }"
              @click="activeHistologyPhase = phase.code"
            >
              {{ phase.label }}
              <span
                :class="
                  phaseStatusClass(
                    selectedHistologySlide?.phases?.find((item) => item.phaseCode === phase.code) ??
                      null,
                  )
                "
              >
                {{
                  phaseStatus(
                    selectedHistologySlide?.phases?.find((item) => item.phaseCode === phase.code) ??
                      null,
                  )
                }}
              </span>
            </button>
          </div>
          <div class="histology-phase-card">
            <div class="histology-phase-summary">
              <div>
                <span class="field-label">当前环节</span>
                <strong
                  >{{ selectedHistologySlide?.slideCode }} ·
                  {{ phaseLabel(activeHistologyPhase) }}</strong
                >
              </div>
              <span :class="phaseStatusClass(activeHistologyPhaseFact)">{{
                phaseStatus(activeHistologyPhaseFact)
              }}</span>
            </div>
            <p v-if="activeHistologyPhaseFact?.startedAt" class="histology-fact-time">
              {{ formatDateTime(activeHistologyPhaseFact.startedAt) }}
              <span v-if="activeHistologyPhaseFact.completedAt">
                → {{ formatDateTime(activeHistologyPhaseFact.completedAt) }}</span
              >
            </p>
            <p v-if="activeHistologyPhaseFact?.operatorRef" class="muted">操作人已记录</p>
            <p v-if="activeHistologyPhaseFact?.exceptionCode" class="feedback warning">
              {{ activeHistologyPhaseFact.exceptionCode }}：{{
                activeHistologyPhaseFact.exceptionNote
              }}
            </p>
            <div class="histology-phase-actions">
              <button
                class="primary-button"
                type="button"
                :disabled="busy || Boolean(activeHistologyPhaseFact?.startedAt)"
                @click="startHistologyPhase"
              >
                开始{{ phaseLabel(activeHistologyPhase) }}
              </button>
              <button
                class="secondary-button"
                type="button"
                :disabled="
                  busy ||
                  !activeHistologyPhaseFact?.startedAt ||
                  Boolean(activeHistologyPhaseFact?.completedAt)
                "
                @click="completeHistologyPhase"
              >
                完成{{ phaseLabel(activeHistologyPhase) }}
              </button>
              <button
                class="text-button"
                type="button"
                :disabled="busy"
                @click="exceptionFormOpen = !exceptionFormOpen"
              >
                记录异常
              </button>
            </div>
            <form
              v-if="exceptionFormOpen"
              class="histology-exception-form"
              @submit.prevent="submitHistologyException"
            >
              <label>
                异常类型
                <select v-model="exceptionCode" required>
                  <option value="" disabled>请选择</option>
                  <option value="切片皱褶">切片皱褶</option>
                  <option value="玻片破损">玻片破损</option>
                  <option value="染色过浅">染色过浅</option>
                  <option value="脱片">脱片</option>
                  <option value="其他">其他</option>
                </select>
              </label>
              <label>
                说明
                <textarea
                  v-model="exceptionNote"
                  rows="2"
                  placeholder="说明发生了什么以及后续处理"
                ></textarea>
              </label>
              <div class="inline-actions">
                <button
                  class="primary-button"
                  type="submit"
                  :disabled="busy || !exceptionCode || !exceptionNote.trim()"
                >
                  保存异常
                </button>
                <button class="text-button" type="button" @click="exceptionFormOpen = false">
                  取消
                </button>
              </div>
            </form>
          </div>
        </div>
      </div>
    </section>
    <V2HistoryDrawer
      :open="historyDrawerOpen"
      :case-id="caseHeaderSlide?.caseId"
      title="制片历史"
      target-label="技术环节"
      @close="historyDrawerOpen = false"
    />
  </section>
</template>
