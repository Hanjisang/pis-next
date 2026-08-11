<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';

import type { V2AuthUser } from '../auth';
import { businessTypeName, formatDateTime, friendlyError } from '../uiText';
import {
  getV2ProductionWorkbench,
  type V2ProductionQueue,
  type V2ProductionWorkbench,
} from '../v2ProductionWorkbenchApi';
import { getV2MyWorkbench, type V2MyWorkbench, type V2WorkbenchItem } from '../v2WorkspaceApi';

const props = defineProps<{ authUser: V2AuthUser | null }>();
const emit = defineEmits<{ navigate: [path: string]; openSearch: [] }>();

type ActiveSection = 'MY_WORK' | 'PRODUCTION' | 'PUBLIC_POOL' | 'REGISTERED_CASES';
type ProductionQueueCode = keyof V2ProductionWorkbench['queues'];

const loading = ref(false);
const productionLoading = ref(false);
const error = ref('');
const productionError = ref('');
const activeSection = ref<ActiveSection>('MY_WORK');
const activeProductionQueue = ref<ProductionQueueCode>('routineProduction');
const workbench = ref<V2MyWorkbench>({
  refreshedAt: '',
  myWork: [],
  publicPool: [],
  counts: {
    initial: 0,
    review: 0,
    audit: 0,
    technicalResultReturned: 0,
    withdrawnReport: 0,
    publicPool: 0,
  },
  queues: {
    histology: 0,
    dehydration: 0,
    embedding: 0,
    cutting: 0,
    staining: 0,
    coverslipping: 0,
    technical: 0,
    frozen: 0,
    withdrawn: 0,
    cytologyPreparation: 0,
    cytologyPreparationCases: [],
  },
  tracking: { registeredCases: [] },
});
const productionWorkbench = ref<V2ProductionWorkbench | null>(null);

const permissions = computed(() => new Set(props.authUser?.permissions ?? []));
const registeredCases = computed(() => workbench.value.tracking.registeredCases);
const activeItems = computed(() => {
  if (activeSection.value === 'PUBLIC_POOL') return workbench.value.publicPool;
  if (activeSection.value === 'REGISTERED_CASES') return [];
  return workbench.value.myWork;
});
const groupedItems = computed(() => {
  const groups = new Map<string, V2WorkbenchItem[]>();
  for (const item of activeItems.value) {
    groups.set(item.workCode, [...(groups.get(item.workCode) ?? []), item]);
  }
  return [...groups.entries()].map(([code, items]) => ({
    code,
    label: items[0]?.workLabel ?? code,
    items,
  }));
});
const productionQueueCards = computed(() => {
  const queues = productionWorkbench.value?.queues;
  if (!queues) return [];
  const cards: Array<{ queue: V2ProductionQueue; permission: string }> = [];
  if (permissions.value.has('P14-PERM-014')) {
    cards.push(
      { queue: queues.routineProduction, permission: 'P14-PERM-014' },
      { queue: queues.cytologyProduction, permission: 'P14-PERM-014' },
      { queue: queues.incompleteSlides, permission: 'P14-PERM-014' },
    );
  }
  if (permissions.value.has('P14-PERM-008'))
    cards.push({ queue: queues.frozenProduction, permission: 'P14-PERM-008' });
  if (permissions.value.has('P14-PERM-017'))
    cards.push({ queue: queues.technicalOrders, permission: 'P14-PERM-017' });
  if (
    permissions.value.has('P14-PERM-014') ||
    permissions.value.has('P14-PERM-008') ||
    permissions.value.has('P14-PERM-017')
  ) {
    cards.push({ queue: queues.exceptions, permission: 'P14-PERM-014' });
  }
  return cards;
});
const selectedProductionQueue = computed(
  () => productionWorkbench.value?.queues[activeProductionQueue.value] ?? null,
);
const queueMetrics = computed(() => [
  { label: '待初诊', value: workbench.value.counts.initial, section: 'MY_WORK' as const },
  { label: '待复诊', value: workbench.value.counts.review, section: 'MY_WORK' as const },
  { label: '待审核', value: workbench.value.counts.audit, section: 'MY_WORK' as const },
  {
    label: '新技术结果',
    value: workbench.value.counts.technicalResultReturned,
    section: 'MY_WORK' as const,
  },
  { label: '待接诊', value: workbench.value.counts.publicPool, section: 'PUBLIC_POOL' as const },
]);

function can(permission: string) {
  return permissions.value.has(permission);
}

function selectProductionQueue(code: ProductionQueueCode) {
  activeProductionQueue.value = code;
  activeSection.value = 'PRODUCTION';
}

function queueKey(queue: V2ProductionQueue): ProductionQueueCode {
  return (
    (
      {
        ROUTINE_PRODUCTION: 'routineProduction',
        CYTOLOGY_PRODUCTION: 'cytologyProduction',
        FROZEN_PRODUCTION: 'frozenProduction',
        TECHNICAL_ORDER: 'technicalOrders',
        INCOMPLETE_SLIDES: 'incompleteSlides',
        EXCEPTIONS: 'exceptions',
      } as Record<string, ProductionQueueCode>
    )[queue.code] ?? 'routineProduction'
  );
}

function openItem(item: { caseId: string; deepLink: string }) {
  const deepLink = item.deepLink?.trim() ?? '';
  if (deepLink.startsWith('/v2/cases/')) {
    emit('navigate', deepLink);
    return;
  }

  const legacyFocus = deepLink.match(
    /^\/v2\/(diagnosis|reports|production|technical-orders|frozen|grossing)\/([^/?]+)/,
  );
  if (legacyFocus) {
    const focusByRoute: Record<string, string> = {
      diagnosis: 'diagnosis',
      reports: 'report',
      production: 'production',
      'technical-orders': 'technical-order',
      frozen: 'frozen',
      grossing: 'grossing',
    };
    const focus = focusByRoute[legacyFocus[1]] ?? 'overview';
    emit('navigate', `/v2/cases/${legacyFocus[2]}?focus=${focus}`);
    return;
  }

  emit('navigate', `/v2/cases/${item.caseId}`);
}

function openQueueWorkspace(queue: V2ProductionQueue) {
  const firstItem = queue.items[0];
  if (firstItem) {
    openItem(firstItem);
    return;
  }
  emit('navigate', queue.code === 'TECHNICAL_ORDER' ? '/v2/technical-orders' : '/v2/production');
}

function openRegistration() {
  emit('navigate', '/v2/registration');
}

async function loadWorkbench() {
  loading.value = true;
  error.value = '';
  try {
    workbench.value = await getV2MyWorkbench();
  } catch (requestError) {
    error.value = friendlyError(requestError, '工作列表暂时无法加载，请刷新后重试。');
  } finally {
    loading.value = false;
  }
}

async function loadProductionWorkbench() {
  if (!(can('P14-PERM-014') || can('P14-PERM-008') || can('P14-PERM-017'))) return;
  productionLoading.value = true;
  productionError.value = '';
  try {
    productionWorkbench.value = await getV2ProductionWorkbench();
  } catch (requestError) {
    productionError.value = friendlyError(requestError, '生产队列暂时无法加载。');
  } finally {
    productionLoading.value = false;
  }
}

onMounted(() => void Promise.all([loadWorkbench(), loadProductionWorkbench()]));
</script>

<template>
  <section class="workbench-home" aria-label="我的工作台">
    <header class="workbench-toolbar">
      <div>
        <p class="section-kicker">今日队列</p>
        <h1>工作台</h1>
      </div>
      <div class="heading-actions">
        <button
          v-if="can('P14-PERM-004')"
          class="secondary-button"
          type="button"
          @click="openRegistration"
        >
          登记
        </button>
        <button class="secondary-button" type="button" @click="emit('openSearch')">
          查找病例 <kbd>Ctrl K</kbd>
        </button>
        <button class="primary-button" type="button" :disabled="loading" @click="loadWorkbench">
          {{ loading ? '刷新中…' : '刷新' }}
        </button>
      </div>
    </header>

    <p v-if="error" class="feedback warning" role="alert">{{ error }}</p>

    <div class="workbench-metric-row" aria-label="工作数量">
      <button
        v-for="metric in queueMetrics"
        :key="metric.label"
        type="button"
        class="workbench-metric"
        :class="{ active: activeSection === metric.section }"
        @click="activeSection = metric.section"
      >
        <span>{{ metric.label }}</span
        ><strong>{{ metric.value }}</strong>
      </button>
    </div>

    <nav class="workbench-sections" role="tablist" aria-label="工作列表范围">
      <button
        type="button"
        role="tab"
        :aria-selected="activeSection === 'MY_WORK'"
        :class="{ active: activeSection === 'MY_WORK' }"
        @click="activeSection = 'MY_WORK'"
      >
        我的工作
      </button>
      <button
        type="button"
        role="tab"
        :aria-selected="activeSection === 'PRODUCTION'"
        :class="{ active: activeSection === 'PRODUCTION' }"
        @click="activeSection = 'PRODUCTION'"
      >
        生产队列
      </button>
      <button
        type="button"
        role="tab"
        :aria-selected="activeSection === 'PUBLIC_POOL'"
        :class="{ active: activeSection === 'PUBLIC_POOL' }"
        @click="activeSection = 'PUBLIC_POOL'"
      >
        待接诊
      </button>
      <button
        v-if="can('P14-PERM-048')"
        type="button"
        role="tab"
        :aria-selected="activeSection === 'REGISTERED_CASES'"
        :class="{ active: activeSection === 'REGISTERED_CASES' }"
        @click="activeSection = 'REGISTERED_CASES'"
      >
        我登记的病例
      </button>
    </nav>

    <div class="workbench-home-grid">
      <section class="workspace-panel workbench-list-panel" aria-label="工作列表">
        <header class="panel-title-row">
          <div>
            <p class="section-kicker">工作列表</p>
            <h2>
              {{
                activeSection === 'PRODUCTION'
                  ? '生产任务'
                  : activeSection === 'PUBLIC_POOL'
                    ? '待接诊病例'
                    : activeSection === 'REGISTERED_CASES'
                      ? '我登记的病例'
                      : '我的待办'
              }}
            </h2>
          </div>
          <span v-if="workbench.refreshedAt" class="muted"
            >更新 {{ formatDateTime(workbench.refreshedAt) }}</span
          >
        </header>

        <template v-if="activeSection === 'PRODUCTION'">
          <div class="workbench-production-tabs" role="tablist" aria-label="生产来源">
            <button
              v-for="card in productionQueueCards"
              :key="card.queue.code"
              type="button"
              role="tab"
              :aria-selected="activeProductionQueue === queueKey(card.queue)"
              :class="{ active: activeProductionQueue === queueKey(card.queue) }"
              @click="selectProductionQueue(queueKey(card.queue))"
            >
              {{ card.queue.label }} <b>{{ card.queue.count }}</b>
            </button>
          </div>
          <div v-if="productionLoading" class="list-skeleton" aria-label="正在加载生产队列">
            <span></span><span></span><span></span>
          </div>
          <div v-else-if="selectedProductionQueue?.items.length" class="workbench-row-list">
            <button
              v-for="item in selectedProductionQueue.items"
              :key="`${item.caseId}-${item.taskSummary}-${item.slideCode ?? item.orderId ?? ''}`"
              type="button"
              class="personal-queue-row production-task-row"
              @click="openItem(item)"
            >
              <span class="queue-row-main"
                ><strong>{{ item.pathologyNo }}</strong
                ><small>{{ item.patientReference }} · {{ item.businessTypeName }}</small></span
              >
              <span
                ><strong>{{ item.taskSummary }}</strong
                ><small>{{ item.materialSummary }}</small></span
              >
              <span
                ><strong>{{ item.currentOperator || '待处理' }}</strong
                ><small>等待 {{ item.waitingMinutes }} 分钟</small></span
              >
              <span class="queue-row-arrow" aria-hidden="true">→</span>
            </button>
          </div>
          <div v-else class="empty-state compact">
            <strong>当前来源没有待处理项</strong><span>新任务出现后会显示在这里。</span>
          </div>
        </template>

        <template v-else-if="activeSection === 'REGISTERED_CASES'">
          <div v-if="registeredCases.length" class="workbench-row-list">
            <button
              v-for="item in registeredCases"
              :key="item.caseId"
              type="button"
              class="personal-queue-row"
              @click="emit('navigate', `/v2/cases/${item.caseId}`)"
            >
              <span class="queue-row-main"
                ><strong>{{ item.pathologyNo }}</strong
                ><small
                  >{{ item.patientReference }} ·
                  {{ businessTypeName(item.businessTypeCode) }}</small
                ></span
              >
              <span
                ><strong>{{ item.currentStageLabel }}</strong
                ><small>{{ item.currentResponsible || '待分派' }}</small></span
              >
              <span
                ><strong>{{ item.material.status }}</strong
                ><small>材料进度</small></span
              >
              <span
                ><strong>{{ item.reportStatus === 'EFFECTIVE' ? '已签发' : '处理中' }}</strong
                ><small>报告状态</small></span
              >
              <span class="queue-row-arrow" aria-hidden="true">→</span>
            </button>
          </div>
          <div v-else class="empty-state compact">
            <strong>还没有我登记的病例</strong><span>登记完成后会持续显示病例进度。</span>
          </div>
        </template>

        <div v-else-if="loading" class="list-skeleton" aria-label="正在加载工作列表">
          <span></span><span></span><span></span>
        </div>
        <template v-else-if="activeItems.length">
          <div v-for="group in groupedItems" :key="group.code" class="workbench-work-group">
            <header>
              <h3>{{ group.label }}</h3>
              <span>{{ group.items.length }}</span>
            </header>
            <div class="workbench-row-list">
              <button
                v-for="item in group.items"
                :key="`${group.code}-${item.caseId}`"
                type="button"
                class="personal-queue-row"
                @click="openItem(item)"
              >
                <span class="queue-row-main"
                  ><strong>{{ item.pathologyNo }}</strong
                  ><small
                    >{{ item.patientReference }} ·
                    {{ businessTypeName(item.businessTypeCode) }}</small
                  ></span
                >
                <span>{{ item.responsibilityName || item.workLabel }}</span>
                <small>{{ formatDateTime(item.occurredAt) }}</small>
                <span class="queue-row-arrow" aria-hidden="true">→</span>
              </button>
            </div>
          </div>
        </template>
        <div v-else class="empty-state compact">
          <strong>{{
            activeSection === 'PUBLIC_POOL' ? '当前没有待接诊病例' : '当前没有待办'
          }}</strong>
          <span>{{
            activeSection === 'PUBLIC_POOL'
              ? '完成制片的病例会出现在这里。'
              : '新的工作项出现后会显示在这里。'
          }}</span>
        </div>
      </section>

      <aside class="workbench-queue-rail" aria-label="生产队列摘要">
        <section class="workspace-panel">
          <header class="panel-title-row">
            <div>
              <p class="section-kicker">生产入口</p>
              <h2>按业务来源</h2>
            </div>
            <span v-if="productionLoading" class="muted">读取中…</span>
          </header>
          <p v-if="productionError" class="feedback warning" role="status">{{ productionError }}</p>
          <div v-if="productionQueueCards.length" class="workbench-queue-list">
            <button
              v-for="card in productionQueueCards"
              :key="card.queue.code"
              type="button"
              :disabled="!card.queue.items.length"
              @click="openQueueWorkspace(card.queue)"
            >
              <span
                ><strong>{{ card.queue.label }}</strong
                ><small>{{
                  card.queue.items.length ? '进入当前最早任务' : '暂无任务'
                }}</small></span
              >
              <b>{{ card.queue.count }}</b>
            </button>
          </div>
          <div v-else class="empty-state compact"><strong>当前身份没有生产队列</strong></div>
        </section>
        <section class="workspace-panel workbench-search-panel">
          <p class="section-kicker">主动查找</p>
          <h2>病例中心</h2>
          <p>按病理号、患者或玻片号打开病例概览。</p>
          <button class="secondary-button" type="button" @click="emit('openSearch')">
            查找病例
          </button>
        </section>
      </aside>
    </div>
  </section>
</template>
