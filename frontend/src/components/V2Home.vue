<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';

import type { V2AuthUser } from '../auth';
import { appendNavigationContext } from '../navigation';
import { businessTypeName, friendlyError } from '../uiText';
import {
  getV2ProductionWorkbench,
  type V2ProductionItem,
  type V2ProductionQueue,
  type V2ProductionWorkbench,
} from '../v2ProductionWorkbenchApi';
import {
  getV2MyWorkbench,
  type V2CaseProgress,
  type V2MyWorkbench,
  type V2WorkbenchItem,
} from '../v2WorkspaceApi';

const props = defineProps<{ authUser: V2AuthUser | null }>();
const emit = defineEmits<{ navigate: [path: string]; openSearch: [] }>();

type QueueItem = {
  key: string;
  caseId: string;
  pathologyNo: string;
  patientReference: string;
  businessType: string;
  task: string;
  detail: string;
  waitingMinutes: number;
  enteredAt: string;
  path: string;
  focused: boolean;
};

type QueueView = { code: string; label: string; items: QueueItem[] };
type SavedWorkbenchState = {
  queue: string;
  filter: string;
  sort: 'oldest' | 'newest';
  page: number;
  scrollY: number;
};

const STATE_KEY = 'pis-v2-my-workbench-state';
const PAGE_SIZE = 20;
const emptyWorkbench: V2MyWorkbench = {
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
};

function readState(): SavedWorkbenchState {
  const fallback: SavedWorkbenchState = {
    queue: new URLSearchParams(window.location.search).get('queue') ?? '',
    filter: '',
    sort: 'oldest',
    page: 1,
    scrollY: 0,
  };
  try {
    const saved = JSON.parse(
      sessionStorage.getItem(STATE_KEY) ?? '',
    ) as Partial<SavedWorkbenchState>;
    return {
      queue: fallback.queue || saved.queue || '',
      filter: saved.filter || '',
      sort: saved.sort === 'newest' ? 'newest' : 'oldest',
      page: Math.max(1, Number(saved.page) || 1),
      scrollY: Math.max(0, Number(saved.scrollY) || 0),
    };
  } catch {
    return fallback;
  }
}

const initialState = readState();
const loading = ref(false);
const error = ref('');
const workbench = ref<V2MyWorkbench>(emptyWorkbench);
const productionWorkbench = ref<V2ProductionWorkbench | null>(null);
const activeQueue = ref(initialState.queue);
const filter = ref(initialState.filter);
const sort = ref<SavedWorkbenchState['sort']>(initialState.sort);
const page = ref(initialState.page);
const permissions = computed(() => new Set(props.authUser?.permissions ?? []));

function can(permission: string) {
  return permissions.value.has(permission);
}

const hasProductionAccess = computed(() => can('P14-PERM-014') || can('P14-PERM-017'));

function diagnosisItem(item: V2WorkbenchItem): QueueItem {
  return {
    key: `${item.workCode}-${item.caseId}`,
    caseId: item.caseId,
    pathologyNo: item.pathologyNo,
    patientReference: item.patientReference,
    businessType: item.businessTypeName || businessTypeName(item.businessTypeCode),
    task: item.workLabel,
    detail: item.responsibilityName || '待处理',
    waitingMinutes: item.waitingMinutes,
    enteredAt: item.enteredAt,
    path: `/v2/${item.workCode === 'WITHDRAWN_REPORT_REQUIRES_ATTENTION' ? 'reports' : 'diagnosis'}/${item.caseId}`,
    focused: true,
  };
}

function productionItem(item: V2ProductionItem): QueueItem {
  const query = new URLSearchParams();
  let route = 'production';
  if (item.productionContext === 'FROZEN_ROUND') {
    route = 'frozen';
    if (item.productionContextId) query.set('roundId', item.productionContextId);
  } else if (item.productionContext === 'TECHNICAL_ORDER') {
    route = 'technical-orders';
    if (item.orderId) query.set('focusId', item.orderId);
  }
  return {
    key: `${item.productionContext}-${item.caseId}-${item.orderId ?? item.slideCode ?? ''}`,
    caseId: item.caseId,
    pathologyNo: item.pathologyNo,
    patientReference: item.patientReference,
    businessType: item.businessTypeName || businessTypeName(item.businessTypeCode ?? ''),
    task: item.taskSummary,
    detail: item.materialSummary,
    waitingMinutes: item.waitingMinutes,
    enteredAt: item.enteredAt,
    path: `/v2/${route}/${item.caseId}${query.size ? `?${query.toString()}` : ''}`,
    focused: true,
  };
}

function registrationItem(item: V2CaseProgress): QueueItem {
  return {
    key: `REGISTERED-${item.caseId}`,
    caseId: item.caseId,
    pathologyNo: item.pathologyNo,
    patientReference: item.patientReference,
    businessType: item.businessTypeName || businessTypeName(item.businessTypeCode),
    task: item.currentStageLabel,
    detail: item.currentResponsible || item.material.status,
    waitingMinutes: item.waitingMinutes,
    enteredAt: item.enteredAt,
    path: `/v2/cases/${item.caseId}`,
    focused: false,
  };
}

function personalQueue(code: string, label: string): QueueView {
  return {
    code,
    label,
    items: workbench.value.myWork.filter((item) => item.workCode === code).map(diagnosisItem),
  };
}

function productionQueue(queue: V2ProductionQueue): QueueView {
  return { code: queue.code, label: queue.label, items: queue.items.map(productionItem) };
}

const queues = computed<QueueView[]>(() => {
  const result: QueueView[] = [];
  if (can('P14-PERM-004')) {
    result.push(
      { code: 'REGISTRATION_PENDING', label: '待登记', items: [] },
      { code: 'REGISTRATION_RETURNED', label: '退回待处理', items: [] },
      {
        code: 'REGISTERED_TODAY',
        label: '我今天登记',
        items: workbench.value.tracking.registeredCases.map(registrationItem),
      },
    );
  }
  if (hasProductionAccess.value && productionWorkbench.value) {
    const production = productionWorkbench.value.queues;
    if (can('P14-PERM-014')) {
      result.push(productionQueue(production.routineProduction));
      result.push(productionQueue(production.cytologyProduction));
      result.push(productionQueue(production.incompleteSlides));
    }
    if (can('P14-PERM-008')) result.push(productionQueue(production.frozenProduction));
    if (can('P14-PERM-017')) result.push(productionQueue(production.technicalOrders));
    result.push(productionQueue(production.exceptions));
  }
  if (can('P14-PERM-034')) {
    result.push(personalQueue('INITIAL', '待初诊'));
    result.push(personalQueue('REVIEW', '待复诊'));
    result.push(personalQueue('TECHNICAL_RESULT_RETURNED_REQUIRES_ATTENTION', '新技术结果'));
    result.push({
      code: 'PUBLIC_POOL',
      label: '待接诊',
      items: workbench.value.publicPool.map(diagnosisItem),
    });
  }
  if (can('P14-PERM-035')) result.push(personalQueue('AUDIT', '待审核'));
  if (can('P14-PERM-036')) {
    result.push(personalQueue('WITHDRAWN_REPORT_REQUIRES_ATTENTION', '撤回待处理'));
  }
  return result;
});

const selectedQueue = computed(() => queues.value.find((item) => item.code === activeQueue.value));
const filteredItems = computed(() => {
  const needle = filter.value.trim().toLocaleLowerCase();
  const items = (selectedQueue.value?.items ?? []).filter((item) =>
    needle
      ? [item.pathologyNo, item.patientReference, item.businessType, item.task, item.detail]
          .join(' ')
          .toLocaleLowerCase()
          .includes(needle)
      : true,
  );
  return [...items].sort((left, right) => {
    const difference = new Date(left.enteredAt).getTime() - new Date(right.enteredAt).getTime();
    return sort.value === 'oldest' ? difference : -difference;
  });
});
const totalPages = computed(() => Math.max(1, Math.ceil(filteredItems.value.length / PAGE_SIZE)));
const visibleItems = computed(() =>
  filteredItems.value.slice((page.value - 1) * PAGE_SIZE, page.value * PAGE_SIZE),
);

function persistState(scrollY = window.scrollY) {
  sessionStorage.setItem(
    STATE_KEY,
    JSON.stringify({
      queue: activeQueue.value,
      filter: filter.value,
      sort: sort.value,
      page: page.value,
      scrollY,
    }),
  );
}

function selectQueue(code: string) {
  activeQueue.value = code;
  page.value = 1;
}

function workbenchReturnPath() {
  const query = new URLSearchParams({ queue: activeQueue.value });
  return `/v2/workbench?${query.toString()}`;
}

function openItem(item: QueueItem) {
  persistState();
  const path = appendNavigationContext(item.path, {
    origin: 'workbench',
    queue: activeQueue.value,
    returnTo: workbenchReturnPath(),
  });
  emit('navigate', path);
}

async function loadWorkbench() {
  loading.value = true;
  error.value = '';
  try {
    const requests: [Promise<V2MyWorkbench>, Promise<V2ProductionWorkbench | null>] = [
      getV2MyWorkbench(),
      hasProductionAccess.value ? getV2ProductionWorkbench() : Promise.resolve(null),
    ];
    [workbench.value, productionWorkbench.value] = await Promise.all(requests);
    if (!queues.value.some((queue) => queue.code === activeQueue.value)) {
      activeQueue.value =
        queues.value.find((queue) => queue.items.length)?.code ?? queues.value[0]?.code ?? '';
    }
    page.value = Math.min(page.value, totalPages.value);
    await nextTick();
    window.scrollTo({ top: initialState.scrollY, behavior: 'auto' });
  } catch (requestError) {
    error.value = friendlyError(requestError, '工作列表暂时无法加载，请刷新后重试。');
  } finally {
    loading.value = false;
  }
}

watch([activeQueue, filter, sort, page], () => persistState());
watch([filter, sort], () => (page.value = 1));
onMounted(() => void loadWorkbench());
onUnmounted(() => persistState());
</script>

<template>
  <section class="workbench-home" aria-label="我的工作台">
    <h1 class="visually-hidden">我的工作</h1>
    <div class="workbench-command-bar">
      <div class="workbench-filter-controls">
        <label>
          <span class="visually-hidden">筛选当前队列</span>
          <input v-model="filter" type="search" placeholder="筛选病理号、患者或当前事项" />
        </label>
        <label>
          <span class="visually-hidden">排序</span>
          <select v-model="sort" aria-label="工作列表排序">
            <option value="oldest">等待最久优先</option>
            <option value="newest">最新进入优先</option>
          </select>
        </label>
      </div>
      <div class="heading-actions">
        <button
          v-if="can('P14-PERM-004')"
          class="secondary-button"
          type="button"
          @click="emit('navigate', '/v2/registration')"
        >
          登记
        </button>
        <button class="secondary-button" type="button" @click="emit('openSearch')">查找病例</button>
        <button class="secondary-button" type="button" :disabled="loading" @click="loadWorkbench">
          {{ loading ? '刷新中…' : '刷新' }}
        </button>
      </div>
    </div>

    <p v-if="error" class="feedback warning" role="alert">{{ error }}</p>

    <nav class="workbench-queue-tabs" role="tablist" aria-label="我的工作队列">
      <button
        v-for="queue in queues"
        :key="queue.code"
        type="button"
        role="tab"
        :aria-selected="activeQueue === queue.code"
        :class="{ active: activeQueue === queue.code }"
        @click="selectQueue(queue.code)"
      >
        <span>{{ queue.label }}</span
        ><strong>{{ queue.items.length }}</strong>
      </button>
    </nav>

    <section class="workbench-dense-list" aria-label="工作列表">
      <header class="workbench-list-header">
        <span>病理号 / 患者</span><span>当前事项</span><span>等待</span><span>操作</span>
      </header>
      <div v-if="loading" class="list-skeleton" aria-label="正在加载工作列表">
        <span></span><span></span><span></span>
      </div>
      <button
        v-for="item in visibleItems"
        v-else
        :key="item.key"
        type="button"
        class="workbench-dense-row"
        @click="openItem(item)"
      >
        <span
          ><strong>{{ item.pathologyNo }}</strong
          ><small>{{ item.patientReference }} · {{ item.businessType }}</small></span
        >
        <span
          ><strong>{{ item.task }}</strong
          ><small>{{ item.detail }}</small></span
        >
        <span
          ><strong>{{ item.waitingMinutes }} 分钟</strong><small>进入当前队列</small></span
        >
        <span class="workbench-row-action">{{ item.focused ? '开始处理' : '查看病例' }} →</span>
      </button>
      <div v-if="!loading && !visibleItems.length" class="empty-state compact">
        <strong>{{ filter ? '没有符合筛选条件的工作' : '当前队列没有待处理项' }}</strong>
        <span>{{ filter ? '请调整筛选条件。' : '新任务进入后会显示在这里。' }}</span>
      </div>
    </section>

    <footer v-if="totalPages > 1" class="workbench-pagination" aria-label="工作列表分页">
      <button type="button" :disabled="page <= 1" @click="page--">上一页</button>
      <span>第 {{ page }} / {{ totalPages }} 页</span>
      <button type="button" :disabled="page >= totalPages" @click="page++">下一页</button>
    </footer>
  </section>
</template>
