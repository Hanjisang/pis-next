<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';

import type { V2AuthUser } from '../auth';
import { businessTypeName, formatDateTime, friendlyError } from '../uiText';
import { getV2MyWorkbench, type V2MyWorkbench, type V2WorkbenchItem } from '../v2WorkspaceApi';

const props = defineProps<{ authUser: V2AuthUser | null }>();
const emit = defineEmits<{ navigate: [path: string]; openSearch: [] }>();

const loading = ref(false);
const error = ref('');
const activeSection = ref<'MY_WORK' | 'PUBLIC_POOL'>('MY_WORK');
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
  },
});

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
  activeSection.value === 'MY_WORK' ? workbench.value.myWork : workbench.value.publicPool,
);

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
  return cards;
});

const queueCards = computed(() => {
  const queues = workbench.value.queues;
  const cards = [] as Array<{ label: string; count: number; path: string; permission: string }>;
  if (can('P14-PERM-017')) {
    cards.push(
      {
        label: '待脱水',
        count: queues.dehydration,
        path: '/v2/production',
        permission: 'P14-PERM-017',
      },
      {
        label: '待包埋',
        count: queues.embedding,
        path: '/v2/production',
        permission: 'P14-PERM-017',
      },
      {
        label: '待切片',
        count: queues.cutting,
        path: '/v2/production',
        permission: 'P14-PERM-017',
      },
      {
        label: '待染色',
        count: queues.staining,
        path: '/v2/production',
        permission: 'P14-PERM-017',
      },
      {
        label: '待封片',
        count: queues.coverslipping,
        path: '/v2/production',
        permission: 'P14-PERM-017',
      },
      {
        label: '技术医嘱',
        count: queues.technical,
        path: '/v2/technical-orders',
        permission: 'P14-PERM-017',
      },
    );
  }
  if (can('P14-PERM-008') || can('P14-PERM-034'))
    cards.push({
      label: '冰冻病例',
      count: queues.frozen,
      path: '/v2/frozen',
      permission: 'P14-PERM-008',
    });
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

function openItem(item: V2WorkbenchItem) {
  emit('navigate', item.deepLink || `/v2/cases/${item.caseId}`);
}

function selectCard(code: string) {
  if (code === 'PUBLIC_POOL') {
    activeSection.value = 'PUBLIC_POOL';
    return;
  }
  activeSection.value = 'MY_WORK';
}

onMounted(() => void loadWorkbench());
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
        :class="{ selected: activeSection === 'PUBLIC_POOL' && card.code === 'PUBLIC_POOL' }"
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
              {{ activeSection === 'MY_WORK' ? 'MY WORK' : 'PUBLIC POOL' }}
            </p>
            <h3>{{ activeSection === 'MY_WORK' ? '我的责任' : '可认领病例' }}</h3>
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
          </div>
        </header>
        <div v-if="loading" class="list-skeleton" aria-label="正在加载待办">
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
              <h3>今天要处理什么</h3>
            </div>
          </header>
          <div class="attention-list">
            <button
              v-for="queue in queueCards"
              :key="queue.label"
              type="button"
              @click="emit('navigate', queue.path)"
            >
              <span class="semantic-dot current" aria-hidden="true"></span
              ><span
                ><strong>{{ queue.label }}</strong
                ><small>进入对应工作区</small></span
              ><b>{{ queue.count }}</b>
            </button>
          </div>
          <div v-if="!queueCards.length" class="empty-state compact">
            <strong>当前身份没有生产队列</strong>
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
