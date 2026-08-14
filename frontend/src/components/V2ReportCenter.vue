<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';

import { friendlyError, formatDateTime, statusName } from '../uiText';
import {
  declareV2ReportDelay,
  getV2ReportCenter,
  resolveV2ReportDelay,
  type V2ReportCenter as ReportCenterData,
  type V2ReportQueueItem,
} from '../v2ReportCenterApi';

const emit = defineEmits<{ navigate: [path: string] }>();
const data = ref<ReportCenterData | null>(null);
const activeQueue = ref<
  | 'WAITING_SIGN'
  | 'WARNING'
  | 'OVERDUE'
  | 'DELAYED'
  | 'SIGNED'
  | 'WITHDRAWN'
  | 'SUPPLEMENTAL'
  | 'RECENT'
>('WAITING_SIGN');
const loading = ref(false);
const error = ref('');
const delayItem = ref<V2ReportQueueItem | null>(null);
const resolveItem = ref<V2ReportQueueItem | null>(null);
const resolutionNote = ref('');
const delayDraft = ref({
  reasonCode: 'TECHNICAL_WORK',
  reasonDetail: '',
  expectedSignAt: '',
});

const queueOptions = computed(() => [
  { code: 'WAITING_SIGN' as const, label: '待签发', count: data.value?.counts.waitingSign ?? 0 },
  { code: 'WARNING' as const, label: '临期', count: data.value?.counts.warning ?? 0 },
  { code: 'OVERDUE' as const, label: '超期', count: data.value?.counts.overdue ?? 0 },
  { code: 'DELAYED' as const, label: '已登记延迟', count: data.value?.counts.delayed ?? 0 },
  { code: 'SIGNED' as const, label: '已签发', count: data.value?.counts.signed ?? 0 },
  { code: 'WITHDRAWN' as const, label: '撤回待处理', count: data.value?.counts.withdrawn ?? 0 },
  { code: 'SUPPLEMENTAL' as const, label: '补充报告', count: data.value?.counts.supplemental ?? 0 },
  { code: 'RECENT' as const, label: '最近签发', count: data.value?.counts.recentSigned ?? 0 },
]);
const visibleItems = computed(() => {
  const items = data.value?.items ?? [];
  if (activeQueue.value === 'RECENT')
    return items.filter((item) => item.queueCode === 'SIGNED').slice(0, 20);
  if (activeQueue.value === 'WARNING' || activeQueue.value === 'OVERDUE')
    return items.filter((item) => item.tatStatus === activeQueue.value);
  if (activeQueue.value === 'DELAYED') return items.filter((item) => item.delay);
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

function tatLabel(item: V2ReportQueueItem) {
  if (item.tatStatus === 'NORMAL') return '时效正常';
  if (item.tatStatus === 'WARNING') return '即将超期';
  if (item.tatStatus === 'OVERDUE') return '已超期';
  if (item.tatStatus === 'COMPLETED_ON_TIME') return '按时签发';
  if (item.tatStatus === 'COMPLETED_OVERDUE') return '超期签发';
  if (item.tatStatus === 'NOT_APPLICABLE') return '不计时效';
  return '未配置策略';
}

function openDelayDialog(item: V2ReportQueueItem) {
  delayItem.value = item;
  const dueAt = item.dueAt ? new Date(item.dueAt).getTime() : 0;
  const expectedAt = Math.max(Date.now() + 24 * 60 * 60 * 1000, dueAt + 60 * 60 * 1000);
  const localExpectedAt = new Date(expectedAt);
  localExpectedAt.setMinutes(localExpectedAt.getMinutes() - localExpectedAt.getTimezoneOffset());
  delayDraft.value = {
    reasonCode: 'TECHNICAL_WORK',
    reasonDetail: '',
    expectedSignAt: localExpectedAt.toISOString().slice(0, 16),
  };
}

async function submitDelay() {
  if (!delayItem.value) return;
  loading.value = true;
  error.value = '';
  try {
    await declareV2ReportDelay({
      diagnosisId: delayItem.value.diagnosisId,
      reasonCode: delayDraft.value.reasonCode,
      reasonDetail: delayDraft.value.reasonDetail.trim(),
      expectedSignAt: new Date(delayDraft.value.expectedSignAt).toISOString(),
      idempotencyKey: `report-delay-${crypto.randomUUID()}`,
    });
    delayItem.value = null;
    await load();
    activeQueue.value = 'DELAYED';
  } catch (requestError) {
    error.value = friendlyError(requestError, '延迟登记失败，请检查时效策略和预计签发时间。');
    loading.value = false;
  }
}

function openResolveDialog(item: V2ReportQueueItem) {
  resolveItem.value = item;
  resolutionNote.value = '';
}

async function submitResolution() {
  if (!resolveItem.value?.delay) return;
  loading.value = true;
  error.value = '';
  try {
    await resolveV2ReportDelay(resolveItem.value.delay.delayId, {
      resolutionNote: resolutionNote.value.trim(),
      idempotencyKey: `report-delay-resolve-${crypto.randomUUID()}`,
    });
    resolveItem.value = null;
    await load();
  } catch (requestError) {
    error.value = friendlyError(requestError, '延迟事项关闭失败，请刷新后重试。');
    loading.value = false;
  }
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
        ><span>新的报告事项会根据签审流程和报告状态自动进入这里。</span>
      </div>
      <div v-else class="dense-report-table" role="table" aria-label="报告工作项">
        <div class="dense-report-row header" role="row">
          <span>病理号</span><span>患者</span><span>工作项</span><span>报告</span><span>时效</span
          ><span>时间</span><span></span>
        </div>
        <div
          v-for="item in visibleItems"
          :key="`${item.queueCode}-${item.reportId ?? item.diagnosisId}`"
          class="dense-report-row"
          role="row"
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
          ><span
            ><span
              class="status-pill"
              :class="{
                success: ['NORMAL', 'COMPLETED_ON_TIME'].includes(item.tatStatus),
                warning: ['WARNING', 'OVERDUE', 'COMPLETED_OVERDUE'].includes(item.tatStatus),
              }"
              >{{ tatLabel(item) }}</span
            ><small v-if="item.dueAt">目标 {{ formatDateTime(item.dueAt) }}</small
            ><small v-if="item.delay"
              >预计 {{ formatDateTime(item.delay.expectedSignAt) }}</small
            ></span
          ><time>{{ formatDateTime(item.occurredAt) }}</time
          ><span class="report-row-actions"
            ><button class="text-button" type="button" @click="open(item)">打开 →</button
            ><button
              v-if="
                item.queueCode === 'WAITING_SIGN' &&
                !item.delay &&
                ['WARNING', 'OVERDUE'].includes(item.tatStatus)
              "
              class="text-button"
              type="button"
              @click="openDelayDialog(item)"
            >
              登记延迟</button
            ><button
              v-if="item.delay"
              class="text-button"
              type="button"
              @click="openResolveDialog(item)"
            >
              关闭延迟
            </button></span
          >
        </div>
      </div>
    </section>
    <div v-if="delayItem" class="modal-backdrop" role="presentation" @click.self="delayItem = null">
      <form class="modal-card" aria-label="登记报告延迟" @submit.prevent="submitDelay">
        <header class="panel-title-row">
          <div>
            <p class="section-kicker">报告时效</p>
            <h3>登记报告延迟</h3>
          </div>
          <button class="text-button" type="button" @click="delayItem = null">关闭</button>
        </header>
        <p class="muted">
          {{ delayItem.pathologyNo }} · 当前目标 {{ formatDateTime(delayItem.dueAt) }}
        </p>
        <label
          >延迟原因<select v-model="delayDraft.reasonCode">
            <option value="TECHNICAL_WORK">技术工作待完成</option>
            <option value="CONSULTATION">会诊待完成</option>
            <option value="MATERIAL_PENDING">材料待补充</option>
            <option value="CLINICAL_INFORMATION">临床信息待补充</option>
            <option value="OTHER">其他</option>
          </select></label
        >
        <label
          >原因说明<textarea v-model="delayDraft.reasonDetail" maxlength="1000" required></textarea>
        </label>
        <label
          >预计签发时间<input v-model="delayDraft.expectedSignAt" type="datetime-local" required
        /></label>
        <footer class="dialog-actions">
          <button class="secondary-button" type="button" @click="delayItem = null">取消</button>
          <button
            class="primary-button"
            type="submit"
            :disabled="loading || !delayDraft.reasonDetail.trim() || !delayDraft.expectedSignAt"
          >
            {{ loading ? '提交中…' : '确认登记' }}
          </button>
        </footer>
      </form>
    </div>
    <div
      v-if="resolveItem"
      class="modal-backdrop"
      role="presentation"
      @click.self="resolveItem = null"
    >
      <form class="modal-card" aria-label="关闭报告延迟" @submit.prevent="submitResolution">
        <header class="panel-title-row">
          <div>
            <p class="section-kicker">报告时效</p>
            <h3>关闭延迟登记</h3>
          </div>
          <button class="text-button" type="button" @click="resolveItem = null">关闭</button>
        </header>
        <p class="muted">{{ resolveItem.pathologyNo }} · 关闭后保留原登记和本次说明。</p>
        <label
          >关闭说明<textarea v-model="resolutionNote" maxlength="1000" required></textarea>
        </label>
        <footer class="dialog-actions">
          <button class="secondary-button" type="button" @click="resolveItem = null">取消</button>
          <button
            class="primary-button"
            type="submit"
            :disabled="loading || !resolutionNote.trim()"
          >
            {{ loading ? '提交中…' : '确认关闭' }}
          </button>
        </footer>
      </form>
    </div>
  </section>
</template>
