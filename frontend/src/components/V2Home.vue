<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';

import type { V2AuthUser } from '../auth';
import { businessTypeName, friendlyError } from '../uiText';

type PoolCase = { caseId: string; pathologyNo: string; businessTypeCode: string };
type TechnicalOrder = { orderId: string; orderNo: string; caseId: string; statusCode: string };
type ProductionSlide = { slideId: string; completedAt: string | null };

const props = defineProps<{ authUser: V2AuthUser | null }>();
const emit = defineEmits<{
  navigate: [path: string];
  openSearch: [];
}>();

const publicPool = ref<PoolCase[]>([]);
const technicalOrders = ref<TechnicalOrder[]>([]);
const productionSlides = ref<ProductionSlide[]>([]);
const loading = ref(false);
const error = ref('');

const roleCode = computed(() => props.authUser?.roleCode ?? 'ADMIN');
const greeting = computed(() => {
  const hour = new Date().getHours();
  const prefix = hour < 11 ? '早上好' : hour < 14 ? '中午好' : hour < 18 ? '下午好' : '晚上好';
  return `${prefix}，${props.authUser?.displayName ?? '同事'}`;
});

const taskGroups = computed(() => {
  if (roleCode.value === 'REGISTRAR') {
    return [
      { label: '待登记申请', count: 0, hint: '当前没有待登记申请', path: '/v2/registration' },
      { label: '今日已登记', count: null, hint: '查看今日登记记录', path: '/v2/registration' },
      { label: '异常 / 退回', count: 0, hint: '当前没有需要处理的异常', path: '/v2/registration' },
    ];
  }
  if (roleCode.value === 'TECHNICIAN') {
    const activeOrders = technicalOrders.value.filter(
      (item) => item.statusCode !== 'COMPLETED',
    ).length;
    return [
      { label: '待取材', count: null, hint: '进入待取材病例', path: '/v2/grossing' },
      {
        label: '待制片',
        count: productionSlides.value.filter((item) => !item.completedAt).length,
        hint: '查看未完成玻片',
        path: '/v2/production',
      },
      {
        label: '技术医嘱',
        count: activeOrders,
        hint: '需要处理或录入结果',
        path: '/v2/technical-orders',
      },
      { label: '冰冻', count: null, hint: '进入冰冻工作台', path: '/v2/frozen' },
    ];
  }
  if (roleCode.value === 'DOCTOR') {
    return [
      {
        label: '公共病例池',
        count: publicPool.value.length,
        hint: '制片完成、等待接诊',
        path: '/v2/diagnosis',
      },
      { label: '我的待诊', count: null, hint: '查看我的初诊病例', path: '/v2/diagnosis' },
      { label: '待复诊', count: null, hint: '需要复诊确认', path: '/v2/diagnosis' },
      { label: '待审核', count: null, hint: '需要审核签发', path: '/v2/diagnosis' },
      {
        label: '技术结果已返回',
        count: technicalOrders.value.filter((item) => item.statusCode === 'COMPLETED').length,
        hint: '结果已回到诊断工作区',
        path: '/v2/diagnosis',
      },
    ];
  }
  return [
    { label: '登记', count: null, hint: '登记新病例与标本', path: '/v2/registration' },
    { label: '取材', count: null, hint: '进入病例取材工作区', path: '/v2/grossing' },
    {
      label: '制片',
      count: productionSlides.value.filter((item) => !item.completedAt).length,
      hint: '查看未完成玻片',
      path: '/v2/production',
    },
    {
      label: '公共病例池',
      count: publicPool.value.length,
      hint: '等待医生接诊',
      path: '/v2/diagnosis',
    },
    {
      label: '技术医嘱',
      count: technicalOrders.value.filter((item) => item.statusCode !== 'COMPLETED').length,
      hint: '查看技术执行队列',
      path: '/v2/technical-orders',
    },
    { label: '冰冻', count: null, hint: '查看术中快速诊断', path: '/v2/frozen' },
  ];
});

const visiblePoolCases = computed(() => publicPool.value.slice(0, 5));

async function loadWorkbench() {
  loading.value = true;
  error.value = '';
  try {
    const requests: Promise<void>[] = [];
    if (['DOCTOR', 'ADMIN'].includes(roleCode.value)) {
      requests.push(
        fetch('/api/v2/diagnosis-workspaces/public-pool').then(async (response) => {
          if (!response.ok) throw new Error('无法读取公共病例池');
          publicPool.value = (await response.json()) as PoolCase[];
        }),
      );
    }
    if (['DOCTOR', 'TECHNICIAN', 'ADMIN'].includes(roleCode.value)) {
      requests.push(
        fetch('/api/v2/technical-workbench').then(async (response) => {
          if (!response.ok) throw new Error('无法读取技术医嘱');
          const body = (await response.json()) as { orders?: TechnicalOrder[] };
          technicalOrders.value = body.orders ?? [];
        }),
      );
    }
    if (['TECHNICIAN', 'ADMIN'].includes(roleCode.value)) {
      requests.push(
        fetch('/api/v2/slides/production-workbench').then(async (response) => {
          if (!response.ok) throw new Error('无法读取待制片玻片');
          const body = (await response.json()) as { slides?: ProductionSlide[] };
          productionSlides.value = body.slides ?? [];
        }),
      );
    }
    await Promise.all(requests);
  } catch (requestError) {
    error.value = friendlyError(requestError, '部分待办暂时无法加载，请刷新重试。');
  } finally {
    loading.value = false;
  }
}

onMounted(() => void loadWorkbench());
</script>

<template>
  <section class="workbench-page" aria-label="我的工作台">
    <header class="page-heading compact-heading">
      <div>
        <p class="section-kicker">今日工作</p>
        <h2>{{ greeting }}</h2>
        <p>从待办开始处理；完成后列表会自动减少。</p>
      </div>
      <div class="heading-actions">
        <button class="secondary-button" type="button" @click="emit('openSearch')">全局查询</button>
        <button class="primary-button" type="button" :disabled="loading" @click="loadWorkbench">
          {{ loading ? '刷新中…' : '刷新待办' }}
        </button>
      </div>
    </header>

    <p v-if="error" class="feedback warning" role="status">{{ error }}</p>

    <div class="task-summary-grid" aria-label="今日待办">
      <button
        v-for="task in taskGroups"
        :key="task.label"
        type="button"
        class="task-summary"
        @click="emit('navigate', task.path)"
      >
        <span>
          <strong>{{ task.label }}</strong>
          <small>{{ task.hint }}</small>
        </span>
        <span class="task-count" :class="{ muted: task.count === null }">
          {{ task.count === null ? '查看' : task.count }}
        </span>
      </button>
    </div>

    <div class="workbench-columns">
      <section class="workspace-panel queue-panel">
        <header class="panel-title-row">
          <div>
            <p class="section-kicker">优先处理</p>
            <h3>{{ roleCode === 'REGISTRAR' ? '待登记申请' : '公共病例池' }}</h3>
          </div>
          <button
            class="text-button"
            type="button"
            @click="
              emit('navigate', roleCode === 'REGISTRAR' ? '/v2/registration' : '/v2/diagnosis')
            "
          >
            查看全部
          </button>
        </header>
        <div v-if="loading" class="list-skeleton" aria-label="正在加载待办">
          <span></span><span></span><span></span>
        </div>
        <div v-else-if="!visiblePoolCases.length" class="empty-state">
          <strong>{{
            roleCode === 'REGISTRAR' ? '当前没有待登记申请' : '当前没有待接诊病例'
          }}</strong>
          <span>新任务到达后会显示在这里。</span>
        </div>
        <div v-else class="compact-table" role="table" aria-label="公共病例池">
          <div class="table-head" role="row">
            <span role="columnheader">病理号</span><span role="columnheader">业务类型</span
            ><span role="columnheader">操作</span>
          </div>
          <div v-for="item in visiblePoolCases" :key="item.caseId" class="table-row" role="row">
            <strong role="cell">{{ item.pathologyNo }}</strong>
            <span role="cell">{{ businessTypeName(item.businessTypeCode) }}</span>
            <span role="cell">
              <button
                class="text-button"
                type="button"
                @click="emit('navigate', `/v2/diagnosis/${item.caseId}`)"
              >
                打开
              </button>
            </span>
          </div>
        </div>
      </section>

      <section class="workspace-panel attention-panel">
        <header class="panel-title-row">
          <div>
            <p class="section-kicker">提醒</p>
            <h3>需要关注</h3>
          </div>
        </header>
        <div class="attention-list">
          <button type="button" @click="emit('navigate', '/v2/technical-orders')">
            <span class="semantic-dot warning" aria-hidden="true"></span>
            <span><strong>技术医嘱</strong><small>查看待处理与已返回结果</small></span>
            <b>{{ technicalOrders.filter((item) => item.statusCode !== 'COMPLETED').length }}</b>
          </button>
          <button type="button" @click="emit('navigate', '/v2/frozen')">
            <span class="semantic-dot current" aria-hidden="true"></span>
            <span><strong>冰冻进行中</strong><small>轮次与时效在冰冻工作区查看</small></span>
            <b>查看</b>
          </button>
          <button type="button" @click="emit('navigate', '/v2/quality')">
            <span class="semantic-dot neutral" aria-hidden="true"></span>
            <span><strong>质控提醒</strong><small>提醒默认不阻塞业务</small></span>
            <b>查看</b>
          </button>
        </div>
      </section>
    </div>
  </section>
</template>
