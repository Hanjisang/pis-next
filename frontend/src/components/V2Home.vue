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
const greeting = computed(() => {
  const hour = new Date().getHours();
  const prefix = hour < 11 ? '早上好' : hour < 14 ? '中午好' : hour < 18 ? '下午好' : '晚上好';
  return `${prefix}，${props.authUser?.displayName ?? '同事'}`;
});

function can(permission: string) {
  return permissions.value.has(permission);
}

const registeredCases = computed(() => workbench.value.tracking.registeredCases);
const activeItems = computed(() => {
  if (activeSection.value === 'PUBLIC_POOL') return workbench.value.publicPool;
  return workbench.value.myWork;
});
const groupedItems = computed(() => {
  const groups = new Map<string, V2WorkbenchItem[]>();
  for (const item of activeItems.value) {
    const current = groups.get(item.workCode) ?? [];
    current.push(item);
    groups.set(item.workCode, current);
  }
  return [...groups.entries()].map(([code, items]) => ({
    code,
    label: items[0]?.workLabel ?? code,
    items,
  }));
});

const summaryCards = computed(() => {
  const cards = [] as Array<{ code: string; label: string; count: number; hint: string }>;
  if (can('P14-PERM-034')) {
    cards.push(
      {
        code: 'INITIAL',
        label: '待初诊',
        count: workbench.value.counts.initial,
        hint: '我的初诊责任',
      },
      {
        code: 'REVIEW',
        label: '待复诊',
        count: workbench.value.counts.review,
        hint: '我的复诊责任',
      },
    );
  }
  if (can('P14-PERM-035')) {
    cards.push({
      code: 'AUDIT',
      label: '待审核',
      count: workbench.value.counts.audit,
      hint: '我的审核责任',
    });
  }
  if (can('P14-PERM-034')) {
    cards.push({
      code: 'TECHNICAL_RESULT_RETURNED_REQUIRES_ATTENTION',
      label: '新技术结果',
      count: workbench.value.counts.technicalResultReturned,
      hint: '结果已返回原病例',
    });
  }
  if (can('P14-PERM-036')) {
    cards.push({
      code: 'WITHDRAWN_REPORT_REQUIRES_ATTENTION',
      label: '撤回待处理',
      count: workbench.value.counts.withdrawnReport,
      hint: '需要重新处理的报告',
    });
  }
  if (can('P14-PERM-034')) {
    cards.push({
      code: 'PUBLIC_POOL',
      label: '待接诊病例',
      count: workbench.value.counts.publicPool,
      hint: '可认领病例',
    });
  }
  if (can('P14-PERM-048')) {
    cards.push({
      code: 'REGISTERED_CASES',
      label: '我登记的病例',
      count: registeredCases.value.length,
      hint: '登记后的全流程追踪',
    });
  }
  return cards;
});

const productionQueueCards = computed(() => {
  const queues = productionWorkbench.value?.queues;
  if (!queues) return [];
  const cards: Array<{ queue: V2ProductionQueue; permission: string }> = [];
  if (can('P14-PERM-014')) {
    cards.push(
      { queue: queues.routineProduction, permission: 'P14-PERM-014' },
      { queue: queues.cytologyProduction, permission: 'P14-PERM-014' },
      { queue: queues.incompleteSlides, permission: 'P14-PERM-014' },
    );
  }
  if (can('P14-PERM-008'))
    cards.push({ queue: queues.frozenProduction, permission: 'P14-PERM-008' });
  if (can('P14-PERM-017'))
    cards.push({ queue: queues.technicalOrders, permission: 'P14-PERM-017' });
  if (can('P14-PERM-014') || can('P14-PERM-008') || can('P14-PERM-017')) {
    cards.push({ queue: queues.exceptions, permission: 'P14-PERM-014' });
  }
  return cards;
});

const selectedProductionQueue = computed(
  () => productionWorkbench.value?.queues[activeProductionQueue.value] ?? null,
);

function selectCard(code: string) {
  if (code === 'PUBLIC_POOL') activeSection.value = 'PUBLIC_POOL';
  else if (code === 'REGISTERED_CASES') activeSection.value = 'REGISTERED_CASES';
  else activeSection.value = 'MY_WORK';
}

function selectProductionQueue(code: string) {
  if (!(code in (productionWorkbench.value?.queues ?? {}))) return;
  activeProductionQueue.value = code as ProductionQueueCode;
  activeSection.value = 'PRODUCTION';
}

function openItem(item: { caseId: string; deepLink: string }) {
  emit('navigate', item.deepLink || `/v2/cases/${item.caseId}`);
}

function openRegistration() {
  emit('navigate', '/v2/registration');
}

function openQueueWorkspace(queue: V2ProductionQueue) {
  if (queue.code === 'TECHNICAL_ORDER') emit('navigate', '/v2/technical-orders');
  else emit('navigate', '/v2/production');
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

async function loadWorkbench() {
  loading.value = true;
  error.value = '';
  try {
    workbench.value = await getV2MyWorkbench();
  } catch (requestError) {
    error.value = friendlyError(requestError, '我的工作台暂时无法加载，请刷新后重试。');
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
  <section class="workbench-page personal-workbench-page" aria-label="我的工作台">
    <header class="page-heading compact-heading personal-workbench-heading">
      <div>
        <p class="section-kicker">工作台 · 人的工作中心</p>
        <h2>{{ greeting }}</h2>
        <p>
          这里回答“我现在要处理什么”；跨病例待办按当前权限和数据范围汇总，点击工作项进入病例中心。
        </p>
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
          全局查询 <kbd>Ctrl K</kbd>
        </button>
        <button class="primary-button" type="button" :disabled="loading" @click="loadWorkbench">
          {{ loading ? '刷新中…' : '刷新待办' }}
        </button>
      </div>
    </header>

    <p v-if="error" class="feedback warning" role="alert">{{ error }}</p>

    <div class="task-summary-grid personal-task-grid" aria-label="我的责任汇总">
      <button
        v-for="card in summaryCards"
        :key="card.code"
        type="button"
        class="task-summary"
        :class="{
          selected:
            (activeSection === 'PUBLIC_POOL' && card.code === 'PUBLIC_POOL') ||
            (activeSection === 'REGISTERED_CASES' && card.code === 'REGISTERED_CASES'),
        }"
        @click="selectCard(card.code)"
      >
        <span
          ><strong>{{ card.label }}</strong
          ><small>{{ card.hint }}</small></span
        >
        <b class="task-count">{{ card.count }}</b>
      </button>
    </div>

    <div class="workbench-columns personal-workbench-columns">
      <section class="workspace-panel queue-panel" aria-label="跨病例待办">
        <header class="panel-title-row">
          <div>
            <p class="section-kicker">WORKBENCH QUEUE</p>
            <h3>{{ activeSection === 'PRODUCTION' ? '生产来源队列' : '我的待办' }}</h3>
          </div>
          <div class="segmented-control" role="tablist" aria-label="工作项范围">
            <button
              type="button"
              :class="{ active: activeSection === 'MY_WORK' }"
              @click="activeSection = 'MY_WORK'"
            >
              我的工作
            </button>
            <button
              type="button"
              :class="{ active: activeSection === 'PRODUCTION' }"
              @click="activeSection = 'PRODUCTION'"
            >
              生产队列
            </button>
            <button
              type="button"
              :class="{ active: activeSection === 'PUBLIC_POOL' }"
              @click="activeSection = 'PUBLIC_POOL'"
            >
              待接诊
            </button>
            <button
              v-if="can('P14-PERM-048')"
              type="button"
              :class="{ active: activeSection === 'REGISTERED_CASES' }"
              @click="activeSection = 'REGISTERED_CASES'"
            >
              我登记的病例
            </button>
          </div>
        </header>

        <template v-if="activeSection === 'PRODUCTION'">
          <div class="business-queue-tabs" role="tablist" aria-label="生产来源">
            <button
              v-for="card in productionQueueCards"
              :key="card.queue.code"
              type="button"
              :class="{ active: activeProductionQueue === queueKey(card.queue) }"
              @click="selectProductionQueue(queueKey(card.queue))"
            >
              {{ card.queue.label }} <b>{{ card.queue.count }}</b>
            </button>
          </div>
          <div v-if="productionLoading" class="list-skeleton" aria-label="正在加载生产队列">
            <span></span><span></span><span></span>
          </div>
          <div v-else-if="selectedProductionQueue?.items.length" class="workbench-work-groups">
            <section class="workbench-work-group">
              <header>
                <h4>{{ selectedProductionQueue.label }}</h4>
                <span>{{ selectedProductionQueue.count }}</span>
              </header>
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
                <span>{{ item.taskSummary }}</span>
                <span
                  ><strong>{{ item.currentOperator || '待处理' }}</strong
                  ><small>等待 {{ item.waitingMinutes }} 分钟</small></span
                >
                <span class="queue-row-arrow" aria-hidden="true">→</span>
              </button>
            </section>
          </div>
          <div v-else class="empty-state">
            <strong>当前来源没有待处理项</strong
            ><span>业务事实发生后，Projection 会重新计算队列。</span>
          </div>
        </template>

        <div v-else-if="activeSection === 'REGISTERED_CASES'" class="registered-case-list">
          <button
            v-for="item in registeredCases"
            :key="item.caseId"
            type="button"
            class="registered-case-row"
            @click="emit('navigate', `/v2/cases/${item.caseId}?focus=overview`)"
          >
            <span class="queue-row-main"
              ><strong>{{ item.pathologyNo }}</strong
              ><small
                >{{ item.patientReference }} · {{ businessTypeName(item.businessTypeCode) }}</small
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
          <div v-if="!registeredCases.length" class="empty-state">
            <strong>还没有我登记的病例</strong><span>登记后病例会持续显示当前进度和处理人。</span>
          </div>
        </div>
        <div v-else-if="loading" class="list-skeleton" aria-label="正在加载待办">
          <span></span><span></span><span></span>
        </div>
        <div v-else-if="!activeItems.length" class="empty-state">
          <strong>{{
            activeSection === 'MY_WORK' ? '当前没有我的待办' : '当前没有可认领病例'
          }}</strong>
          <span>{{
            activeSection === 'MY_WORK'
              ? '新的责任、技术结果或报告异常出现后会显示在这里。'
              : '符合当前权限范围的病例会进入待接诊。'
          }}</span>
          <span v-if="activeSection === 'MY_WORK' && can('P14-PERM-004')" class="empty-state-detail"
            >待登记申请：当前没有已接入的申请待登记项</span
          >
        </div>
        <div v-else class="workbench-work-groups">
          <section v-for="group in groupedItems" :key="group.code" class="workbench-work-group">
            <header>
              <h4>{{ group.label }}</h4>
              <span>{{ group.items.length }}</span>
            </header>
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
          </section>
        </div>
      </section>

      <aside class="workbench-attention-stack">
        <section class="workspace-panel attention-panel" aria-label="生产来源队列">
          <header class="panel-title-row">
            <div>
              <p class="section-kicker">BUSINESS SOURCE</p>
              <h3>生产工作</h3>
            </div>
          </header>
          <p v-if="productionError" class="feedback warning" role="status">{{ productionError }}</p>
          <div v-if="productionLoading" class="list-skeleton" aria-label="正在加载生产任务">
            <span></span><span></span><span></span>
          </div>
          <div class="attention-list">
            <button
              v-for="card in productionQueueCards"
              :key="card.queue.code"
              type="button"
              @click="openQueueWorkspace(card.queue)"
            >
              <span class="semantic-dot current" aria-hidden="true"></span
              ><span
                ><strong>{{ card.queue.label }}</strong
                ><small>按业务来源进入处理列表</small></span
              ><b>{{ card.queue.count }}</b>
            </button>
          </div>
          <div
            v-if="!productionLoading && !productionQueueCards.length"
            class="empty-state compact"
          >
            <strong>当前身份没有生产任务</strong>
          </div>
        </section>
        <section v-if="can('P14-PERM-048')" class="workspace-panel workbench-today-panel">
          <p class="section-kicker">GLOBAL SEARCH</p>
          <h3>主动找病例</h3>
          <p>按病理号、患者或材料打开病例中心，不需要先判断业务模块。</p>
          <button class="secondary-button" type="button" @click="emit('openSearch')">
            搜索病例
          </button>
        </section>
      </aside>
    </div>
  </section>
</template>
