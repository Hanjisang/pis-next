<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';

import type { V2AuthUser } from '../auth';
import { businessTypeName, friendlyError, statusName } from '../uiText';

type PoolCase = { caseId: string; pathologyNo: string; businessTypeCode: string };
type TechnicalOrder = {
  orderId: string;
  orderNo: string;
  caseId: string;
  statusCode: string;
  createdAt?: string;
};
type ProductionSlide = {
  slideId: string;
  caseId: string;
  caseNo: string;
  patientReference: string;
  businessTypeCode: string;
  slideCode?: string;
  slideType?: string;
  completedAt: string | null;
};

const props = defineProps<{ authUser: V2AuthUser | null }>();
const emit = defineEmits<{ navigate: [path: string]; openSearch: [] }>();

const publicPool = ref<PoolCase[]>([]);
const technicalOrders = ref<TechnicalOrder[]>([]);
const productionSlides = ref<ProductionSlide[]>([]);
const loading = ref(false);
const error = ref('');

const permissions = computed(() => new Set(props.authUser?.permissions ?? []));
const roleCode = computed(() => props.authUser?.roleCode ?? 'ADMIN');
const greeting = computed(() => {
  const hour = new Date().getHours();
  const prefix = hour < 11 ? '早上好' : hour < 14 ? '中午好' : hour < 18 ? '下午好' : '晚上好';
  return `${prefix}，${props.authUser?.displayName ?? '同事'}`;
});

function can(permission: string) {
  return roleCode.value === 'ADMIN' || permissions.value.has(permission);
}

const taskGroups = computed(() => {
  const groups = [] as Array<{ label: string; count: number | null; hint: string; path: string }>;
  if (can('P14-PERM-004'))
    groups.push({
      label: '待登记申请',
      count: null,
      hint: '打开登记工作区处理申请',
      path: '/v2/registration',
    });
  if (can('P14-PERM-013'))
    groups.push({
      label: '待取材',
      count: null,
      hint: '按病例进入取材工作区',
      path: '/v2/grossing',
    });
  if (can('P14-PERM-014')) {
    groups.push({
      label: '待制片',
      count: productionSlides.value.filter((item) => !item.completedAt).length,
      hint: '处理未完成玻片',
      path: '/v2/production',
    });
    groups.push({ label: '冰冻', count: null, hint: '查看当前轮次与术中材料', path: '/v2/frozen' });
  }
  if (can('P14-PERM-017'))
    groups.push({
      label: '技术医嘱',
      count: technicalOrders.value.filter(
        (item) => !['COMPLETED', 'CANCELLED'].includes(item.statusCode),
      ).length,
      hint: '处理医嘱或录入结果',
      path: '/v2/technical-orders',
    });
  if (can('P14-PERM-034')) {
    groups.push({
      label: '待诊病例',
      count: publicPool.value.length,
      hint: '制片完成、等待接诊',
      path: '/v2/diagnosis',
    });
    groups.push({
      label: '技术结果已返回',
      count: technicalOrders.value.filter((item) => item.statusCode === 'COMPLETED').length,
      hint: '回到诊断工作区查看结果',
      path: '/v2/diagnosis',
    });
  }
  return groups;
});

const queueTitle = computed(() => {
  if (can('P14-PERM-034')) return '我的优先病例';
  if (can('P14-PERM-017')) return '技术医嘱';
  if (can('P14-PERM-014')) return '待制片玻片';
  return '今日工作项';
});

const queueItems = computed(() => {
  if (can('P14-PERM-034')) {
    return publicPool.value.slice(0, 8).map((item) => ({
      id: item.caseId,
      title: item.pathologyNo,
      subtitle: businessTypeName(item.businessTypeCode),
      detail: '待接诊',
      path: `/v2/diagnosis/${item.caseId}`,
    }));
  }
  if (can('P14-PERM-017')) {
    return technicalOrders.value.slice(0, 8).map((item) => ({
      id: item.orderId,
      title: item.orderNo,
      subtitle: item.caseId,
      detail: statusName(item.statusCode),
      path: `/v2/technical-orders/${item.caseId}`,
    }));
  }
  return productionSlides.value
    .filter((item) => !item.completedAt)
    .slice(0, 8)
    .map((item) => ({
      id: item.slideId,
      title: item.slideCode || item.slideId,
      subtitle: `${item.caseNo} · ${item.patientReference}`,
      detail: item.slideType || businessTypeName(item.businessTypeCode),
      path: `/v2/production/${item.caseId}`,
    }));
});

async function loadWorkbench() {
  loading.value = true;
  error.value = '';
  try {
    const requests: Promise<void>[] = [];
    if (can('P14-PERM-034')) {
      requests.push(
        fetch('/api/v2/diagnosis-workspaces/public-pool').then(async (response) => {
          if (!response.ok) throw new Error('无法读取待诊病例');
          publicPool.value = (await response.json()) as PoolCase[];
        }),
      );
    }
    if (can('P14-PERM-017') || can('P14-PERM-034')) {
      requests.push(
        fetch('/api/v2/technical-workbench').then(async (response) => {
          if (!response.ok) throw new Error('无法读取技术医嘱');
          const body = (await response.json()) as { orders?: TechnicalOrder[] };
          technicalOrders.value = body.orders ?? [];
        }),
      );
    }
    if (can('P14-PERM-014')) {
      requests.push(
        fetch('/api/v2/slides/production-workbench').then(async (response) => {
          if (!response.ok) throw new Error('无法读取制片队列');
          const body = (await response.json()) as { slides?: ProductionSlide[] };
          productionSlides.value = body.slides ?? [];
        }),
      );
    }
    await Promise.all(requests);
  } catch (requestError) {
    error.value = friendlyError(requestError, '待办暂时无法加载，请刷新后重试。');
  } finally {
    loading.value = false;
  }
}

onMounted(() => void loadWorkbench());
</script>

<template>
  <section class="workbench-page personal-workbench-page" aria-label="我的工作台">
    <header class="page-heading compact-heading personal-workbench-heading">
      <div>
        <p class="section-kicker">我的工作台</p>
        <h2>{{ greeting }}</h2>
        <p>这里只显示当前身份能处理的工作项。点击一行直接进入对应病例或工作区。</p>
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

    <p v-if="error" class="feedback warning" role="status">{{ error }}</p>

    <div
      v-if="taskGroups.length"
      class="task-summary-grid personal-task-grid"
      aria-label="我的待办"
    >
      <button
        v-for="task in taskGroups"
        :key="task.label"
        type="button"
        class="task-summary"
        @click="emit('navigate', task.path)"
      >
        <span
          ><strong>{{ task.label }}</strong
          ><small>{{ task.hint }}</small></span
        >
        <span class="task-count" :class="{ muted: task.count === null }">{{
          task.count === null ? '查看' : task.count
        }}</span>
      </button>
    </div>

    <div class="workbench-columns personal-workbench-columns">
      <section class="workspace-panel queue-panel">
        <header class="panel-title-row">
          <div>
            <p class="section-kicker">优先处理</p>
            <h3>{{ queueTitle }}</h3>
          </div>
          <button class="text-button" type="button" @click="emit('openSearch')">查找病例</button>
        </header>
        <div v-if="loading" class="list-skeleton" aria-label="正在加载待办">
          <span></span><span></span><span></span>
        </div>
        <div v-else-if="!queueItems.length" class="empty-state">
          <strong>当前没有待处理工作</strong><span>新任务到达后会出现在这里。</span>
        </div>
        <div v-else class="personal-queue-list">
          <button
            v-for="item in queueItems"
            :key="item.id"
            type="button"
            class="personal-queue-row"
            @click="emit('navigate', item.path)"
          >
            <span class="queue-row-main"
              ><strong>{{ item.title }}</strong
              ><small>{{ item.subtitle }}</small></span
            >
            <span>{{ item.detail }}</span
            ><span class="queue-row-arrow" aria-hidden="true">→</span>
          </button>
        </div>
      </section>

      <aside class="workbench-attention-stack">
        <section class="workspace-panel attention-panel">
          <header class="panel-title-row">
            <div>
              <p class="section-kicker">关注</p>
              <h3>需要留意</h3>
            </div>
          </header>
          <div class="attention-list">
            <button
              v-if="can('P14-PERM-017')"
              type="button"
              @click="emit('navigate', '/v2/technical-orders')"
            >
              <span class="semantic-dot warning" aria-hidden="true"></span
              ><span><strong>技术医嘱</strong><small>待处理或等待结果</small></span
              ><b>{{
                technicalOrders.filter(
                  (item) => !['COMPLETED', 'CANCELLED'].includes(item.statusCode),
                ).length
              }}</b>
            </button>
            <button
              v-if="can('P14-PERM-014')"
              type="button"
              @click="emit('navigate', '/v2/production')"
            >
              <span class="semantic-dot current" aria-hidden="true"></span
              ><span><strong>制片未完成</strong><small>按玻片继续处理</small></span
              ><b>{{ productionSlides.filter((item) => !item.completedAt).length }}</b>
            </button>
            <button type="button" @click="emit('navigate', '/v2/quality')">
              <span class="semantic-dot neutral" aria-hidden="true"></span
              ><span><strong>质控提醒</strong><small>提醒只提示，不默认阻塞业务</small></span
              ><b>查看</b>
            </button>
          </div>
        </section>
        <section class="workspace-panel workbench-today-panel">
          <p class="section-kicker">今天</p>
          <h3>工作节奏</h3>
          <p>完成一项工作后，回到当前列表继续下一项，不需要重新记住病例编号。</p>
          <button class="secondary-button" type="button" @click="emit('openSearch')">
            搜索病例
          </button>
        </section>
      </aside>
    </div>
  </section>
</template>
