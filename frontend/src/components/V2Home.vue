<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';

import type { V2AuthUser } from '../auth';
import { appendNavigationContext } from '../navigation';
import { friendlyError } from '../uiText';
import {
  getV2MyWorkbench,
  type V2CapabilityQueue,
  type V2CapabilityQueueItem,
  type V2MyWorkbench,
} from '../v2WorkspaceApi';

defineProps<{ authUser: V2AuthUser | null }>();
const emit = defineEmits<{ navigate: [path: string]; openSearch: [] }>();

type SavedWorkbenchState = {
  queue: string;
  filter: string;
  department: string;
  businessType: string;
  from: string;
  to: string;
  sort: 'priority' | 'newest';
  page: number;
  scrollTop: number;
};

const STATE_KEY = 'pis-v2-my-workbench-state';
const PAGE_SIZE = 20;
const loading = ref(false);
const error = ref('');
const workbench = ref<V2MyWorkbench | null>(null);

function readState(): SavedWorkbenchState {
  const fallback: SavedWorkbenchState = {
    queue: new URLSearchParams(window.location.search).get('queue') ?? '',
    filter: '',
    department: '',
    businessType: '',
    from: '',
    to: '',
    sort: 'priority',
    page: 1,
    scrollTop: 0,
  };
  try {
    const saved = JSON.parse(
      sessionStorage.getItem(STATE_KEY) ?? '',
    ) as Partial<SavedWorkbenchState>;
    return {
      ...fallback,
      ...saved,
      queue: fallback.queue || saved.queue || '',
      sort: saved.sort === 'newest' ? 'newest' : 'priority',
      page: Math.max(1, Number(saved.page) || 1),
      scrollTop: Math.max(0, Number(saved.scrollTop) || 0),
    };
  } catch {
    return fallback;
  }
}

const initial = readState();
const activeQueue = ref(initial.queue);
const filter = ref(initial.filter);
const department = ref(initial.department);
const businessType = ref(initial.businessType);
const from = ref(initial.from);
const to = ref(initial.to);
const sort = ref(initial.sort);
const page = ref(initial.page);

const queues = computed(() => workbench.value?.capabilityQueues ?? []);
const pendingQueues = computed(() => queues.value.filter((queue) => queue.kind === 'PENDING'));
const trackingQueues = computed(() => queues.value.filter((queue) => queue.kind === 'TRACKING'));
const selectedQueue = computed(() => queues.value.find((queue) => queue.key === activeQueue.value));
const businessTypes = computed(() =>
  [
    ...new Set(
      (selectedQueue.value?.items ?? [])
        .map((item) => item.businessType)
        .filter((value): value is string => Boolean(value)),
    ),
  ].sort(),
);

function itemText(item: V2CapabilityQueueItem) {
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

const filteredItems = computed(() => {
  const needle = filter.value.trim().toLocaleLowerCase();
  const start = from.value ? new Date(`${from.value}T00:00:00`).getTime() : null;
  const end = to.value ? new Date(`${to.value}T23:59:59.999`).getTime() : null;
  const items = (selectedQueue.value?.items ?? []).filter((item) => {
    const entered = new Date(item.enteredAt).getTime();
    return (
      (!needle || itemText(item).includes(needle)) &&
      (!department.value || itemText(item).includes(department.value.trim().toLocaleLowerCase())) &&
      (!businessType.value || item.businessType === businessType.value) &&
      (start === null || entered >= start) &&
      (end === null || entered <= end)
    );
  });
  return [...items].sort((left, right) => {
    if (sort.value === 'newest') {
      return new Date(right.enteredAt).getTime() - new Date(left.enteredAt).getTime();
    }
    if (left.urgent !== right.urgent) return left.urgent ? -1 : 1;
    return right.waitingMinutes - left.waitingMinutes;
  });
});

const totalPages = computed(() => Math.max(1, Math.ceil(filteredItems.value.length / PAGE_SIZE)));
const visibleItems = computed(() =>
  filteredItems.value.slice((page.value - 1) * PAGE_SIZE, page.value * PAGE_SIZE),
);

function waitingText(minutes: number) {
  if (minutes < 60) return `${minutes}分钟`;
  const days = Math.floor(minutes / 1440);
  const hours = Math.floor((minutes % 1440) / 60);
  const rest = minutes % 60;
  if (days) return `${days}天${hours}小时`;
  return `${hours}小时${rest ? `${rest}分钟` : ''}`;
}

function persistState(scrollTop = window.scrollY) {
  sessionStorage.setItem(
    STATE_KEY,
    JSON.stringify({
      queue: activeQueue.value,
      filter: filter.value,
      department: department.value,
      businessType: businessType.value,
      from: from.value,
      to: to.value,
      sort: sort.value,
      page: page.value,
      scrollTop,
    } satisfies SavedWorkbenchState),
  );
}

function selectQueue(queue: V2CapabilityQueue) {
  activeQueue.value = queue.key;
  page.value = 1;
}

function workbenchReturnPath() {
  const query = new URLSearchParams({ queue: activeQueue.value });
  return `/v2/workbench?${query.toString()}`;
}

function openItem(item: V2CapabilityQueueItem) {
  if (!item.availableActions.length || !item.workspaceDestination) return;
  persistState();
  emit(
    'navigate',
    appendNavigationContext(item.workspaceDestination, {
      origin: 'workbench',
      queue: activeQueue.value,
      returnTo: workbenchReturnPath(),
    }),
  );
}

async function loadWorkbench() {
  loading.value = true;
  error.value = '';
  try {
    workbench.value = await getV2MyWorkbench();
    if (!queues.value.some((queue) => queue.key === activeQueue.value)) {
      activeQueue.value =
        queues.value.find((queue) => queue.count > 0)?.key ?? queues.value[0]?.key ?? '';
    }
    page.value = Math.min(page.value, totalPages.value);
    await nextTick();
    window.scrollTo({ top: initial.scrollTop, behavior: 'auto' });
  } catch (requestError) {
    error.value = friendlyError(requestError, '工作列表暂时无法加载，请刷新后重试。');
  } finally {
    loading.value = false;
  }
}

watch([activeQueue, filter, department, businessType, from, to, sort, page], () => persistState());
watch([filter, department, businessType, from, to, sort], () => (page.value = 1));
onMounted(() => void loadWorkbench());
onUnmounted(() => persistState());
</script>

<template>
  <section class="workbench-home" aria-label="我的工作">
    <header class="workbench-title-row">
      <h1>我的工作</h1>
      <div class="heading-actions">
        <button class="secondary-button" type="button" @click="emit('openSearch')">全局搜索</button>
        <button class="secondary-button" type="button" :disabled="loading" @click="loadWorkbench">
          {{ loading ? '刷新中…' : '刷新' }}
        </button>
      </div>
    </header>

    <p v-if="error" class="feedback warning" role="alert">{{ error }}</p>

    <div class="workbench-queue-groups">
      <nav v-if="pendingQueues.length" class="workbench-queue-tabs" aria-label="待处理队列">
        <span class="queue-group-label">待处理</span>
        <button
          v-for="queue in pendingQueues"
          :key="queue.key"
          type="button"
          :aria-pressed="activeQueue === queue.key"
          :class="{ active: activeQueue === queue.key }"
          @click="selectQueue(queue)"
        >
          <span>{{ queue.label }}</span
          ><strong>{{ queue.count }}</strong>
        </button>
      </nav>
      <nav
        v-if="trackingQueues.length"
        class="workbench-queue-tabs tracking"
        aria-label="我的今日记录"
      >
        <span class="queue-group-label">我的今日记录</span>
        <button
          v-for="queue in trackingQueues"
          :key="queue.key"
          type="button"
          :aria-pressed="activeQueue === queue.key"
          :class="{ active: activeQueue === queue.key }"
          @click="selectQueue(queue)"
        >
          <span>{{ queue.label }}</span
          ><strong>{{ queue.count }}</strong>
        </button>
      </nav>
    </div>

    <div class="workbench-command-bar" aria-label="筛选当前列表">
      <div class="workbench-filter-controls">
        <label
          >关键词<input
            v-model="filter"
            type="search"
            placeholder="患者、申请号、病理号、门诊或住院号"
        /></label>
        <label>申请科室<input v-model="department" type="search" placeholder="全部科室" /></label>
        <label
          >申请类型<select v-model="businessType">
            <option value="">全部类型</option>
            <option v-for="item in businessTypes" :key="item" :value="item">{{ item }}</option>
          </select></label
        >
        <label>开始日期<input v-model="from" type="date" /></label>
        <label>结束日期<input v-model="to" type="date" /></label>
        <label
          >排序<select v-model="sort">
            <option value="priority">加急与等待最久优先</option>
            <option value="newest">最新进入优先</option>
          </select></label
        >
      </div>
    </div>

    <section class="workbench-dense-list" aria-label="实际工作列表">
      <header class="workbench-list-header">
        <span>业务编号 / 患者</span><span>当前事项</span><span>等待</span><span>操作</span>
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
          ><strong>{{ item.businessDisplayId }}</strong
          ><small
            >{{ item.patientDisplay
            }}<template v-if="item.patientSummary"> · {{ item.patientSummary }}</template></small
          ></span
        >
        <span
          ><strong>{{ item.task }}</strong
          ><small
            >{{ item.businessType
            }}<template v-if="item.detail"> · {{ item.detail }}</template></small
          ></span
        >
        <span
          ><strong>{{ waitingText(item.waitingMinutes) }}</strong
          ><small>{{ item.urgent ? '加急' : '进入当前队列' }}</small></span
        >
        <span class="workbench-row-action"
          >{{ selectedQueue?.kind === 'TRACKING' ? '查看病例' : '开始处理' }} →</span
        >
      </button>
      <div v-if="!loading && !visibleItems.length" class="empty-state compact">
        <strong>{{
          filter || department || businessType || from || to
            ? '没有符合筛选条件的工作'
            : `${selectedQueue?.label ?? '当前队列'} 0`
        }}</strong>
      </div>
    </section>

    <footer v-if="totalPages > 1" class="workbench-pagination" aria-label="工作列表分页">
      <button type="button" :disabled="page <= 1" @click="page--">上一页</button>
      <span>第 {{ page }} / {{ totalPages }} 页</span>
      <button type="button" :disabled="page >= totalPages" @click="page++">下一页</button>
    </footer>
  </section>
</template>
