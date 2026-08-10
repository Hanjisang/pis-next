<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';

import { friendlyError, formatDateTime, statusName } from '../uiText';
import {
  getV2ReportCenter,
  type V2ReportCenter as ReportCenterData,
  type V2ReportQueueItem,
} from '../v2ReportCenterApi';

const emit = defineEmits<{ navigate: [path: string] }>();
const data = ref<ReportCenterData | null>(null);
const activeQueue = ref<'WAITING_SIGN' | 'SIGNED' | 'WITHDRAWN' | 'SUPPLEMENTAL' | 'RECENT'>(
  'WAITING_SIGN',
);
const loading = ref(false);
const error = ref('');

const queueOptions = computed(() => [
  { code: 'WAITING_SIGN' as const, label: '待签发', count: data.value?.counts.waitingSign ?? 0 },
  { code: 'SIGNED' as const, label: '已签发', count: data.value?.counts.signed ?? 0 },
  { code: 'WITHDRAWN' as const, label: '撤回待处理', count: data.value?.counts.withdrawn ?? 0 },
  { code: 'SUPPLEMENTAL' as const, label: '补充报告', count: data.value?.counts.supplemental ?? 0 },
  { code: 'RECENT' as const, label: '最近签发', count: data.value?.counts.recentSigned ?? 0 },
]);
const visibleItems = computed(() => {
  const items = data.value?.items ?? [];
  if (activeQueue.value === 'RECENT')
    return items.filter((item) => item.queueCode === 'SIGNED').slice(0, 20);
  return items.filter((item) => item.queueCode === activeQueue.value);
});

async function load() {
  loading.value = true;
  error.value = '';
  try {
    data.value = await getV2ReportCenter();
  } catch (requestError) {
    error.value = friendlyError(requestError, '报告队列暂时无法加载，请稍后重试');
  } finally {
    loading.value = false;
  }
}

function open(item: V2ReportQueueItem) {
  if (item.queueCode === 'WAITING_SIGN')
    emit('navigate', `/v2/diagnosis/${item.caseId}?focus=report`);
  else if (item.reportId) emit('navigate', `/v2/reports/${item.caseId}?reportId=${item.reportId}`);
  else emit('navigate', `/v2/cases/${item.caseId}`);
}

onMounted(() => void load());
</script>

<template>
  <section class="report-center-page" aria-label="报告中心">
    <header class="page-heading compact-heading">
      <div>
        <p class="section-kicker">报告中心</p>
        <h2>报告工作队列</h2>
        <p>报告中心只负责定位工作项；编辑、预览和签发仍在病例诊断工作区完成。</p>
      </div>
      <button class="secondary-button" type="button" :disabled="loading" @click="load">
        {{ loading ? '刷新中…' : '刷新队列' }}
      </button>
    </header>
    <p v-if="error" class="feedback error" role="alert">{{ error }}</p>
    <nav class="report-queue-tabs" aria-label="报告队列">
      <button
        v-for="queue in queueOptions"
        :key="queue.code"
        type="button"
        :class="{ active: activeQueue === queue.code }"
        @click="activeQueue = queue.code"
      >
        <span>{{ queue.label }}</span
        ><strong>{{ queue.count }}</strong>
      </button>
    </nav>
    <section class="workspace-panel report-queue-panel">
      <div v-if="loading" class="list-skeleton"><span></span><span></span><span></span></div>
      <div v-else-if="!visibleItems.length" class="empty-state">
        <strong
          >当前没有{{ queueOptions.find((item) => item.code === activeQueue)?.label }}病例</strong
        ><span>新的报告工作项会根据责任链和报告事实自动进入这里。</span>
      </div>
      <div v-else class="dense-report-table" role="table" aria-label="报告工作项">
        <div class="dense-report-row header" role="row">
          <span>病理号</span><span>患者</span><span>工作项</span><span>报告</span><span>时间</span
          ><span></span>
        </div>
        <button
          v-for="item in visibleItems"
          :key="`${item.queueCode}-${item.reportId ?? item.diagnosisId}`"
          class="dense-report-row"
          type="button"
          role="row"
          @click="open(item)"
        >
          <strong>{{ item.pathologyNo }}</strong
          ><span>{{ item.patientReference }}</span
          ><span>{{
            item.queueCode === 'WAITING_SIGN'
              ? '待签发'
              : item.queueCode === 'WITHDRAWN'
                ? '报告已撤回，待处理'
                : item.queueCode === 'SUPPLEMENTAL'
                  ? '补充报告'
                  : '已签发'
          }}</span
          ><span
            >{{ item.reportNo ?? '—' }}
            <small v-if="item.statusCode">{{ statusName(item.statusCode) }}</small></span
          ><time>{{ formatDateTime(item.occurredAt) }}</time
          ><span class="text-button">打开 →</span>
        </button>
      </div>
    </section>
  </section>
</template>
