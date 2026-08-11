<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';

import { friendlyError, formatDateTime, idempotencyKey } from '../uiText';
import type { V2AuthUser } from '../auth';
import { getV2Case, type V2CaseResult } from '../v2Api';
import {
  completeV2Slide,
  createV2DirectCytologySlide,
  getV2MaterialTree,
  printV2Slide,
  type V2MaterialTree,
} from '../v2MaterialApi';
import V2CaseHeader from './V2CaseHeader.vue';
import V2HistoryDrawer from './V2HistoryDrawer.vue';
import {
  getV2ProductionWorkbench,
  type V2ProductionQueue,
  type V2ProductionWorkbench,
} from '../v2ProductionWorkbenchApi';
import {
  getV2HistologyWorkbench,
  histologyPhases,
  startV2HistologyPhaseBatch,
  completeV2HistologyPhaseBatch,
  type HistologyPhaseCode,
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
const props = withDefaults(
  defineProps<{ authUser?: V2AuthUser | null; frozenRoundId?: string }>(),
  { authUser: null, frozenRoundId: '' },
);
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
const activeHistologyPhase = ref<HistologyPhaseCode>('DEHYDRATION');
const activeHistologyQueue = ref<HistologyQueueView>('DEHYDRATION');
const historyDrawerOpen = ref(false);
const productionWorkbench = ref<V2ProductionWorkbench | null>(null);
const productionError = ref('');
const caseSummary = ref<V2CaseResult | null>(null);

const histologyCaseSlides = computed(() =>
  caseId.value
    ? histologySlides.value.filter((slide) => slide.caseId === caseId.value)
    : histologySlides.value,
);
const caseHeaderSlide = computed(() => histologyCaseSlides.value[0] ?? null);
const caseHeader = computed(() => {
  const summary = caseSummary.value;
  if (summary) {
    return {
      caseId: summary.caseId,
      caseNo: summary.caseNo,
      patientReference: summary.patientReference,
      visitReference: summary.visitReference,
      businessTypeCode: summary.businessTypeCode,
    };
  }
  const slide = caseHeaderSlide.value;
  if (slide) {
    return {
      caseId: slide.caseId,
      caseNo: slide.caseNo,
      patientReference: slide.patientReference,
      visitReference: null,
      businessTypeCode: slide.businessTypeCode ?? '',
    };
  }
  return null;
});
const isCytology = computed(
  () =>
    materialTree.value?.capability?.supportsDirectSlides === true ||
    caseSummary.value?.businessTypeCode === 'CYTOLOGY',
);
const isFrozen = computed(() => Boolean(props.frozenRoundId));
const caseBlocks = computed(
  () => materialTree.value?.specimens.flatMap((item) => item.blocks) ?? [],
);
const caseSlides = computed(
  () =>
    materialTree.value?.specimens.flatMap((specimen) => [
      ...specimen.blocks.flatMap((block) => block.slides),
      ...specimen.directSlides,
    ]) ?? [],
);
const completedCaseSlides = computed(
  () => caseSlides.value.filter((slide) => slide.completed).length,
);
const nextProductionItem = computed(() => {
  const currentContext = caseProductionItems.value[0]?.productionContext;
  if (!currentContext || !productionWorkbench.value) return null;
  const queue = Object.values(productionWorkbench.value.queues).find((item) =>
    item.items.some(
      (candidate) =>
        candidate.caseId === caseId.value && candidate.productionContext === currentContext,
    ),
  );
  if (!queue) return null;
  const currentIndex = queue.items.findIndex((item) => item.caseId === caseId.value);
  return currentIndex >= 0 ? (queue.items[currentIndex + 1] ?? null) : null;
});
const caseMaterialProgress = computed(() => {
  if (!caseSlides.value.length) return '0/0';
  return `${completedCaseSlides.value}/${caseSlides.value.length}`;
});
const productionReady = computed(
  () => caseSlides.value.length > 0 && completedCaseSlides.value === caseSlides.value.length,
);
const productionQueues = computed<V2ProductionQueue[]>(() => {
  const queues = productionWorkbench.value?.queues;
  if (!queues) return [];
  const result: V2ProductionQueue[] = [];
  const permissions = new Set(props.authUser?.permissions ?? []);
  if (permissions.has('P14-PERM-014')) {
    result.push(queues.routineProduction, queues.cytologyProduction, queues.incompleteSlides);
  }
  if (permissions.has('P14-PERM-008')) result.push(queues.frozenProduction);
  if (permissions.has('P14-PERM-017')) result.push(queues.technicalOrders);
  if (
    permissions.has('P14-PERM-014') ||
    permissions.has('P14-PERM-008') ||
    permissions.has('P14-PERM-017')
  ) {
    result.push(queues.exceptions);
  }
  return result;
});
const caseProductionItems = computed(() =>
  productionQueues.value
    .flatMap((queue) => queue.items)
    .filter((item) => item.caseId === caseId.value),
);

watch(
  () => [caseId.value, props.frozenRoundId],
  () => {
    void loadCaseSummary();
    void loadMaterialTree();
    void loadHistology();
    void loadProductionWorkbench();
  },
);

async function loadCaseSummary() {
  caseSummary.value = null;
  if (!caseId.value) return;
  try {
    caseSummary.value = await getV2Case(caseId.value);
  } catch {
    caseSummary.value = null;
  }
}

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
    const response = await getV2HistologyWorkbench(
      caseId.value || undefined,
      props.frozenRoundId || undefined,
    );
    histologySlides.value = response.slides;
    histologyQueues.value = response.queues;
  } catch (requestError) {
    histologySlides.value = [];
    histologyError.value = friendlyError(requestError, '技术过程记录暂时无法加载。');
  } finally {
    loading.value = false;
  }
}

async function loadProductionWorkbench() {
  if (
    !(props.authUser?.permissions ?? []).some((permission) =>
      ['P14-PERM-014', 'P14-PERM-008', 'P14-PERM-017'].includes(permission),
    )
  ) {
    return;
  }
  try {
    productionWorkbench.value = await getV2ProductionWorkbench();
    productionError.value = '';
  } catch (requestError) {
    productionWorkbench.value = null;
    productionError.value = friendlyError(requestError, '生产任务暂时无法加载。');
  }
}

function selectedHistologySlideIds() {
  return selectedSlideIds.value.filter((slideId) =>
    histologySlides.value.some((slide) => slide.slideId === slideId),
  );
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
    await loadProductionWorkbench();
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
    await Promise.all([loadHistology(), loadMaterialTree(), loadProductionWorkbench()]);
  });
}

function histologyForSlide(slideId: string) {
  return histologySlides.value.find((slide) => slide.slideId === slideId);
}

function printMaterialSlide(slide: V2MaterialTree['specimens'][number]['directSlides'][number]) {
  const histologySlide = histologyForSlide(slide.slideId);
  if (histologySlide) {
    printSlide(histologySlide);
    return;
  }
  void run(async () => {
    await printV2Slide({
      slideId: slide.slideId,
      reason: '制片工作区打印',
      idempotencyKey: idempotencyKey('px03c-slide-print'),
    });
    notice.value = `玻片 ${slide.slideCode} 标签已发送到当前打印机。`;
    await loadMaterialTree();
  });
}

function printAllCaseSlides() {
  const slides = caseSlides.value;
  if (!slides.length) return;
  void run(async () => {
    await Promise.all(
      slides.map((slide) =>
        printV2Slide({
          slideId: slide.slideId,
          reason: '制片工作区批量打印',
          idempotencyKey: idempotencyKey('px03c-batch-print') + '-' + slide.slideId,
        }),
      ),
    );
    notice.value = '已发送 ' + slides.length + ' 张玻片标签到当前打印机。';
    await loadMaterialTree();
  });
}

function completeMaterialSlide(slide: V2MaterialTree['specimens'][number]['directSlides'][number]) {
  if (slide.completed) return;
  void run(async () => {
    await completeV2Slide({
      slideId: slide.slideId,
      expectedVersion: slide.concurrencyVersion,
      idempotencyKey: idempotencyKey('px03c-slide-complete'),
    });
    notice.value = `玻片 ${slide.slideCode} 已完成。`;
    await Promise.all([loadMaterialTree(), loadHistology(), loadProductionWorkbench()]);
  });
}

function openSlideGenerator() {
  if (!caseId.value) return;
  emit('navigate', `/v2/cases/${caseId.value}?focus=grossing`);
}

function completeAndNext() {
  if (nextProductionItem.value) emit('navigate', nextProductionItem.value.deepLink);
}

function phaseLabel(phaseCode: HistologyPhaseCode) {
  return histologyPhases.find((phase) => phase.code === phaseCode)?.label ?? phaseCode;
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

onMounted(() => {
  void Promise.all([
    loadCaseSummary(),
    loadMaterialTree(),
    loadHistology(),
    loadProductionWorkbench(),
  ]);
});
</script>

<template>
  <!-- Legacy layout retained as a reference for the focused redesign.
    <section class="production-workspace" aria-label="病理技术工作台">
      <header class="page-heading compact-heading">
        <div>
          <p class="section-kicker">制片</p>
          <h2>病理技术工作台</h2>
          <p>按常规制片、细胞制片、冰冻制片、技术医嘱和未完成玻片组织系统工作。</p>
        </div>
        <div class="heading-actions">
          <button
            class="secondary-button"
            type="button"
            :disabled="loading"
            @click="loadProductionWorkbench"
          >
            {{ loading ? '刷新中…' : '刷新生产任务' }}
          </button>
        </div>
      </header>

      <p v-if="error" class="feedback error" role="alert">{{ error }}</p>
      <p v-if="notice" class="feedback success" role="status">{{ notice }}</p>

      <section v-if="!caseId" class="production-task-board" aria-label="生产任务">
        <header class="page-heading compact-heading production-task-heading">
          <div>
            <p class="section-kicker">PRODUCTION WORKBENCH</p>
            <h3>生产任务</h3>
            <p>按业务来源显示系统工作；脱水、包埋、切片、染色、封片只在可选技术记录中出现。</p>
          </div>
        </header>
        <p v-if="productionError" class="feedback warning" role="status">{{ productionError }}</p>
        <div
          v-if="!productionWorkbench && !productionError"
          class="list-skeleton"
          aria-label="正在加载生产任务"
        >
          <span></span><span></span><span></span>
        </div>
        <div v-else class="production-queue-grid">
          <section
            v-for="queue in productionQueues"
            :key="queue.code"
            class="workspace-panel production-source-queue"
            :aria-label="queue.label"
          >
            <header class="panel-title-row">
              <div>
                <p class="section-kicker">{{ queue.code }}</p>
                <h3>{{ queue.label }}</h3>
              </div>
              <span class="status-pill">{{ queue.count }}</span>
            </header>
            <button
              v-for="item in queue.items"
              :key="`${queue.code}-${item.caseId}-${item.slideCode ?? item.orderId ?? ''}`"
              type="button"
              class="production-task-row"
              @click="emit('navigate', item.deepLink)"
            >
              <span class="queue-row-main">
                <strong>{{ item.pathologyNo }}</strong>
                <small>{{ item.patientReference }} · {{ item.materialSummary }}</small>
              </span>
              <span
                ><strong>{{ item.taskSummary }}</strong
                ><small>{{ item.currentOperator || '待分派' }}</small></span
              >
              <small>{{ item.waitingMinutes }} 分钟</small>
              <span class="queue-row-arrow" aria-hidden="true">→</span>
            </button>
            <div v-if="!queue.items.length" class="empty-state compact">
              <strong>当前没有{{ queue.label }}任务</strong>
            </div>
  </section>
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

      <section
        v-if="caseProductionItems.length"
        class="workspace-panel case-production-summary"
        aria-label="当前病例生产摘要"
      >
        <header class="panel-title-row">
          <div>
            <p class="section-kicker">CASE PRODUCTION</p>
            <h3>当前病例生产摘要</h3>
          </div>
          <span class="status-pill">{{ caseProductionItems.length }} 项工作</span>
        </header>
        <div class="production-case-task-list">
          <article
            v-for="item in caseProductionItems"
            :key="`${item.productionContext}-${item.slideCode ?? item.orderId ?? item.taskSummary}`"
            class="production-case-task"
          >
            <span
              ><strong>{{ item.taskSummary }}</strong
              ><small>{{ item.materialSummary }}</small></span
            >
            <span
              ><strong>{{ item.completedCount }}/{{ item.requiredCount || '—' }}</strong
              ><small>完成数量</small></span
            >
            <span
              ><strong>{{ item.currentOperator || '待分派' }}</strong
              ><small>{{ item.waitingMinutes }} 分钟</small></span
            >
          </article>
        </div>
      </section>

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

      <details v-if="!isCytology" class="optional-trace-panel">
        <summary>展开可选技术记录（脱水、包埋、切片、染色、封片）</summary>
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
      </details>

      <section
        v-if="supportsDirectSlide && materialTree"
        class="workspace-panel direct-slide-panel"
      >
        <header class="panel-title-row">
          <div>
            <p class="section-kicker">细胞病理</p>
            <h3>直接建立玻片</h3>
          </div>
          <span class="status-pill success">不需要蜡块</span>
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

      <details class="optional-trace-panel">
        <summary>查看技术过程事实与异常记录</summary>
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
                        selectedHistologySlide?.phases?.find(
                          (item) => item.phaseCode === phase.code,
                        ) ?? null,
                      )
                    "
                  >
                    {{
                      phaseStatus(
                        selectedHistologySlide?.phases?.find(
                          (item) => item.phaseCode === phase.code,
                        ) ?? null,
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
      </details>
      <V2HistoryDrawer
        :open="historyDrawerOpen"
        :case-id="caseHeaderSlide?.caseId"
        title="制片历史"
        target-label="技术环节"
        @close="historyDrawerOpen = false"
      />
    </section>
  </div>

  -->

  <section class="focused-production-page" aria-label="制片工作区">
    <header v-if="!caseId" class="page-heading compact-heading production-task-heading">
      <div>
        <p class="section-kicker">生产队列</p>
        <h2>待处理制片</h2>
        <p>选择一项任务后，直接进入该病例的聚焦工作区。</p>
      </div>
      <button
        class="secondary-button"
        type="button"
        :disabled="loading"
        @click="loadProductionWorkbench"
      >
        {{ loading ? '刷新中…' : '刷新队列' }}
      </button>
    </header>

    <p v-if="error" class="feedback error" role="alert">{{ error }}</p>
    <p v-if="notice" class="feedback success" role="status">{{ notice }}</p>

    <section v-if="!caseId" class="production-task-board" aria-label="生产任务">
      <p v-if="productionError" class="feedback warning" role="status">{{ productionError }}</p>
      <div
        v-if="!productionWorkbench && !productionError"
        class="list-skeleton"
        aria-label="正在加载生产任务"
      >
        <span></span><span></span><span></span>
      </div>
      <div v-else class="production-queue-grid">
        <section
          v-for="queue in productionQueues"
          :key="queue.code"
          class="workspace-panel production-source-queue"
          :aria-label="queue.label"
        >
          <header class="panel-title-row">
            <div>
              <p class="section-kicker">{{ queue.code }}</p>
              <h3>{{ queue.label }}</h3>
            </div>
            <span class="status-pill">{{ queue.count }}</span>
          </header>
          <button
            v-for="item in queue.items"
            :key="item.caseId + (item.slideCode ?? item.orderId ?? '')"
            type="button"
            class="production-task-row"
            @click="emit('navigate', item.deepLink)"
          >
            <span class="queue-row-main">
              <strong>{{ item.pathologyNo }}</strong>
              <small>{{ item.patientReference }} · {{ item.materialSummary }}</small>
            </span>
            <span>
              <strong>{{ item.taskSummary }}</strong>
              <small>{{ item.currentOperator || '待分配' }}</small>
            </span>
            <small>{{ item.waitingMinutes }} 分钟</small>
            <span class="queue-row-arrow" aria-hidden="true">→</span>
          </button>
          <div v-if="!queue.items.length" class="empty-state compact">
            <strong>当前没有{{ queue.label }}任务</strong>
          </div>
        </section>
      </div>
    </section>

    <template v-else>
      <V2CaseHeader
        v-if="caseHeader"
        :case-id="caseHeader.caseId"
        :pathology-no="caseHeader.caseNo"
        :patient-reference="caseHeader.patientReference"
        :visit-reference="caseHeader.visitReference"
        :business-type-code="caseHeader.businessTypeCode"
        :current-work="isFrozen ? '冰冻制片' : isCytology ? '细胞制片' : '常规制片'"
        :progress="caseMaterialProgress + ' 张玻片完成'"
        :report-status="caseProductionItems.length ? '当前病例有待处理工作' : '正在处理'"
        @open-case="emit('navigate', '/v2/cases/' + caseHeader.caseId)"
      >
        <template #actions>
          <button class="secondary-button" type="button" @click="historyDrawerOpen = true">
            历史记录
          </button>
        </template>
      </V2CaseHeader>

      <section
        v-if="isCytology"
        class="workspace-panel cytology-focused-panel"
        aria-label="细胞制片"
      >
        <header class="focused-task-heading">
          <div>
            <p class="section-kicker">当前任务</p>
            <h2>细胞制片</h2>
            <p>标本 → 玻片；不展示蜡块和组织学阶段。</p>
          </div>
          <span class="status-pill success">不需要蜡块</span>
        </header>
        <div class="material-focus-summary">
          <span>
            <small>标本</small>
            <strong>{{ materialTree?.specimens[0]?.specimenCode || '—' }}</strong>
          </span>
          <span>
            <small>玻片</small>
            <strong>{{ completedCaseSlides }}/{{ caseSlides.length }}</strong>
          </span>
        </div>
        <div class="cytology-slide-list">
          <div v-for="slide in caseSlides" :key="slide.slideId" class="material-slide-row">
            <span>
              <strong>{{ slide.slideCode }}</strong>
              <small>{{ slide.slideType }}</small>
            </span>
            <span :class="slide.completed ? 'status-pill success' : 'status-pill warning'">
              {{ slide.completed ? '已完成' : '待完成' }}
            </span>
            <span class="inline-actions">
              <button class="text-button" type="button" @click="printMaterialSlide(slide)">
                打印标签
              </button>
              <button
                v-if="!slide.completed"
                class="primary-button"
                type="button"
                :disabled="busy"
                @click="completeMaterialSlide(slide)"
              >
                扫码完成
              </button>
            </span>
          </div>
          <div v-if="!caseSlides.length" class="empty-state compact">
            <strong>还没有玻片</strong>
            <span>先选择标本并新增玻片。</span>
          </div>
        </div>
        <div class="focused-form-actions">
          <label>
            来源标本
            <select v-model="directSpecimenId">
              <option
                v-for="specimen in materialTree?.specimens ?? []"
                :key="specimen.specimenId"
                :value="specimen.specimenId"
              >
                {{ specimen.specimenCode }} · {{ specimen.specimenNo }}
              </option>
            </select>
          </label>
          <label>玻片号<input v-model="directSlideCode" placeholder="例如 A-1" /></label>
          <button
            class="secondary-button"
            type="button"
            :disabled="busy || !directSpecimenId || !directSlideCode.trim()"
            @click="createDirectSlide"
          >
            新增玻片
          </button>
        </div>
        <div class="focused-bottom-actions">
          <button class="secondary-button" type="button" @click="printAllCaseSlides">
            打印标签
          </button>
          <button
            v-if="nextProductionItem && productionReady"
            class="primary-button"
            type="button"
            @click="completeAndNext"
          >
            完成并下一项
          </button>
        </div>
      </section>

      <section
        v-else
        class="workspace-panel routine-focused-panel"
        aria-label="Block / Slide 工作表"
      >
        <header class="focused-task-heading">
          <div>
            <p class="section-kicker">当前任务</p>
            <h2>{{ isFrozen ? '冰冻制片' : '常规制片' }}</h2>
            <p>从来源蜡块生成需要的玻片，并逐张完成。</p>
          </div>
          <span class="status-pill">{{ completedCaseSlides }}/{{ caseSlides.length }} 张完成</span>
        </header>
        <div class="production-sheet-summary">
          <strong>{{ caseBlocks.length }} 个蜡块</strong>
          <span>
            要求
            {{ caseSlides.filter((slide) => slide.required).length || caseSlides.length }} 张
          </span>
          <span>已完成 {{ completedCaseSlides }} 张</span>
        </div>
        <div
          v-if="caseSlides.length"
          class="production-sheet-table"
          role="table"
          aria-label="蜡块与玻片工作表"
        >
          <div class="production-sheet-row header" role="row">
            <span>蜡块</span><span>玻片</span><span>类型</span><span>标签</span><span>完成</span
            ><span>操作</span>
          </div>
          <template v-for="block in caseBlocks" :key="block.blockId">
            <div
              v-for="slide in block.slides"
              :key="slide.slideId"
              class="production-sheet-row"
              role="row"
            >
              <strong>{{ block.blockCode }}</strong>
              <span>{{ slide.slideCode }}</span>
              <span>{{ slide.slideType }}</span>
              <span>{{ slide.completed ? '已打印' : '待打印' }}</span>
              <span>{{ slide.completed ? '✓' : '○' }}</span>
              <span class="inline-actions">
                <button class="text-button" type="button" @click="printMaterialSlide(slide)">
                  打印标签
                </button>
                <button
                  v-if="!slide.completed"
                  class="primary-button"
                  type="button"
                  :disabled="busy"
                  @click="completeMaterialSlide(slide)"
                >
                  扫码完成
                </button>
              </span>
            </div>
          </template>
          <template
            v-for="specimen in materialTree?.specimens ?? []"
            :key="specimen.specimenId + '-direct'"
          >
            <div
              v-for="slide in specimen.directSlides"
              :key="slide.slideId"
              class="production-sheet-row"
              role="row"
            >
              <strong>{{ specimen.specimenCode }}</strong>
              <span>{{ slide.slideCode }}</span>
              <span>{{ slide.slideType }}</span>
              <span>{{ slide.completed ? '已打印' : '待打印' }}</span>
              <span>{{ slide.completed ? '✓' : '○' }}</span>
              <span class="inline-actions">
                <button class="text-button" type="button" @click="printMaterialSlide(slide)">
                  打印标签
                </button>
                <button
                  v-if="!slide.completed"
                  class="primary-button"
                  type="button"
                  :disabled="busy"
                  @click="completeMaterialSlide(slide)"
                >
                  扫码完成
                </button>
              </span>
            </div>
          </template>
        </div>
        <div v-else class="empty-state compact">
          <strong>当前还没有需要完成的玻片</strong>
          <span>先生成玻片，再回到本工作表完成。</span>
        </div>
        <div class="focused-form-actions">
          <button class="secondary-button" type="button" @click="openSlideGenerator">
            生成玻片
          </button>
          <button
            class="secondary-button"
            type="button"
            :disabled="!caseSlides.length"
            @click="printAllCaseSlides"
          >
            批量打印
          </button>
          <label>
            扫码完成
            <input
              ref="scanInput"
              v-model="scanCode"
              autocomplete="off"
              placeholder="扫描或输入玻片号"
              @keydown.enter.prevent="submitScan"
            />
          </label>
          <button
            class="primary-button"
            type="button"
            :disabled="busy || !scanCode.trim()"
            @click="submitScan"
          >
            扫码完成
          </button>
        </div>
        <p class="scan-feedback" :class="scanFeedback.tone" role="status">
          {{ scanFeedback.message }}
        </p>
        <div class="focused-bottom-actions">
          <button
            v-if="nextProductionItem && productionReady"
            class="primary-button"
            type="button"
            @click="completeAndNext"
          >
            完成并下一项
          </button>
        </div>
      </section>

      <details v-if="!isCytology" class="optional-trace-panel">
        <summary>更多：技术记录</summary>
        <div class="histology-stage-queues" role="group" aria-label="技术阶段记录">
          <button
            type="button"
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
            :class="{ active: activeHistologyQueue === 'EXCEPTIONS' }"
            @click="activeHistologyQueue = 'EXCEPTIONS'"
          >
            异常 <b>{{ histologyQueues.exceptions }}</b>
          </button>
        </div>
        <div class="inline-actions">
          <span class="muted">已选择 {{ selectedHistologySlideIds().length }} 张玻片</span>
          <button
            class="secondary-button"
            type="button"
            :disabled="busy || !selectedHistologySlideIds().length"
            @click="startHistologyPhaseBatch"
          >
            开始{{ phaseLabel(activeHistologyPhase) }}
          </button>
          <button
            class="secondary-button"
            type="button"
            :disabled="busy || !selectedHistologySlideIds().length"
            @click="completeHistologyPhaseBatch"
          >
            完成{{ phaseLabel(activeHistologyPhase) }}
          </button>
        </div>
        <p v-if="histologyError" class="feedback warning" role="status">{{ histologyError }}</p>
      </details>
    </template>

    <V2HistoryDrawer
      :open="historyDrawerOpen"
      :case-id="caseHeader?.caseId || caseId"
      title="制片历史"
      target-label="当前制片任务"
      @close="historyDrawerOpen = false"
    />
  </section>
</template>
