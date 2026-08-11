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

const loading = ref(false);
const productionLoading = ref(false);
const error = ref('');
const productionError = ref('');
const activeSection = ref<'MY_WORK' | 'PUBLIC_POOL' | 'REGISTERED_CASES'>('MY_WORK');
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

const activeItems = computed(() =>
  activeSection.value === 'PUBLIC_POOL' ? workbench.value.publicPool : workbench.value.myWork,
);
const registeredCases = computed(() => workbench.value.tracking.registeredCases);

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
      label: '技术结果待处理',
      count: workbench.value.counts.technicalResultReturned,
      hint: '结果已返回原病例',
    });
  }
  if (can('P14-PERM-036')) {
    cards.push({
      code: 'WITHDRAWN_REPORT_REQUIRES_ATTENTION',
      label: '撤回报告待处理',
      count: workbench.value.counts.withdrawnReport,
      hint: '需要重新处理的报告',
    });
  }
  if (can('P14-PERM-034')) {
    cards.push({
      code: 'PUBLIC_POOL',
      label: '公共病例池',
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
  const cards: Array<{ queue: V2ProductionQueue; path: string }> = [];
  if (can('P14-PERM-014')) {
    cards.push(
      { queue: queues.routineProduction, path: '/v2/production' },
      { queue: queues.cytologyProduction, path: '/v2/production' },
      { queue: queues.incompleteSlides, path: '/v2/production' },
    );
  }
  if (can('P14-PERM-008')) cards.push({ queue: queues.frozenProduction, path: '/v2/production' });
  if (can('P14-PERM-017')) cards.push({ queue: queues.technicalOrders, path: '/v2/production' });
  if (can('P14-PERM-014') || can('P14-PERM-008') || can('P14-PERM-017')) {
    cards.push({ queue: queues.exceptions, path: '/v2/production' });
  }
  return cards;
});

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
    productionError.value = friendlyError(requestError, '生产任务暂时无法加载。');
  } finally {
    productionLoading.value = false;
  }
}

function openItem(item: V2WorkbenchItem) {
  emit('navigate', item.deepLink || `/v2/cases/${item.caseId}`);
}

function selectCard(code: string) {
  if (code === 'PUBLIC_POOL') {
    activeSection.value = 'PUBLIC_POOL';
    return;
  }
  if (code === 'REGISTERED_CASES') {
    activeSection.value = 'REGISTERED_CASES';
    return;
  }
  activeSection.value = 'MY_WORK';
}

onMounted(() => void Promise.all([loadWorkbench(), loadProductionWorkbench()]));
</script>

<template>
  <section class="workbench-page personal-workbench-page" aria-label="我的工作台">
    <header class="page-heading compact-heading personal-workbench-heading">
      <div>
        <p class="section-kicker">我的工作台</p>
        <h2>{{ greeting }}</h2>
        <p>
          这里按当前账号的责任、权限和数据范围显示真实待办；公共病例池单独列出，不混入我的责任。
        </p>
      </div>
      <div class="heading-actions">
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
      <section class="workspace-panel queue-panel" aria-label="病例工作项">
        <header class="panel-title-row">
          <div>
            <p class="section-kicker">
              {{
                activeSection === 'MY_WORK'
                  ? 'MY WORK'
                  : activeSection === 'PUBLIC_POOL'
                    ? 'PUBLIC POOL'
                    : 'REGISTERED CASES'
              }}
            </p>
            <h3>
              {{
                activeSection === 'MY_WORK'
                  ? '我的责任'
                  : activeSection === 'PUBLIC_POOL'
                    ? '可认领病例'
                    : '我登记的病例'
              }}
            </h3>
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
              :class="{ active: activeSection === 'PUBLIC_POOL' }"
              @click="activeSection = 'PUBLIC_POOL'"
            >
              公共池
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
        <div v-if="activeSection === 'REGISTERED_CASES'" class="registered-case-list">
          <button
            v-for="item in registeredCases"
            :key="item.caseId"
            type="button"
            class="registered-case-row"
            @click="emit('navigate', `/v2/cases/${item.caseId}`)"
          >
            <span class="queue-row-main">
              <strong>{{ item.pathologyNo }}</strong>
              <small
                >{{ item.patientReference }} · {{ businessTypeName(item.businessTypeCode) }}</small
              >
            </span>
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
            <strong>还没有我登记的病例</strong>
            <span>登记病例并建立标本后，当前阶段和责任人会持续显示在这里。</span>
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
              ? '新的责任或技术结果返回后会显示在这里。'
              : '制片完成且符合权限范围的病例会进入公共池。'
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
        <section class="workspace-panel attention-panel" aria-label="生产队列">
          <header class="panel-title-row">
            <div>
              <p class="section-kicker">生产队列</p>
              <h3>生产任务</h3>
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
              @click="emit('navigate', card.path)"
            >
              <span class="semantic-dot current" aria-hidden="true"></span
              ><span
                ><strong>{{ card.queue.label }}</strong
                ><small>按业务来源进入工作区</small></span
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
          <p class="section-kicker">快捷入口</p>
          <h3>病例查询</h3>
          <p>按病理号、患者或材料打开对应上下文，不需要先判断应该进入哪个模块。</p>
          <button class="secondary-button" type="button" @click="emit('openSearch')">
            搜索病例
          </button>
        </section>
      </aside>
    </div>
  </section>
</template>
