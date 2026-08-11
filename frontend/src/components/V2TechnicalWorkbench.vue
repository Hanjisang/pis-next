<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';

import { getV2Case, type V2CaseResult } from '../v2Api';
import {
  appendNavigationContext,
  safeLocalPath,
  workspaceBackLabel,
  workspaceBackTarget,
  type V2Route,
} from '../navigation';
import { completeV2MolecularResult, type V2MolecularResult } from '../v2BusinessApi';
import { friendlyError, idempotencyKey, statusName } from '../uiText';
import {
  cancelV2TechnicalOrder,
  enterV2TechnicalResult,
  executeV2TechnicalOrder,
  getV2TechnicalWorkbench,
  type V2TechnicalItem,
  type V2TechnicalOrder,
} from '../v2DiagnosisApi';
import { completeV2Slides, getV2MaterialTree } from '../v2MaterialApi';
import V2CaseHeader from './V2CaseHeader.vue';
import V2HistoryDrawer from './V2HistoryDrawer.vue';

type QueueTab = 'PENDING' | 'EXECUTING' | 'RESULT' | 'COMPLETED';
type ResultDraft = { conclusion: string; value: string };

const emit = defineEmits<{ navigate: [path: string] }>();
const caseId = defineModel<string>('caseId', { default: '' });
const props = withDefaults(
  defineProps<{
    focusKind?: string;
    focusId?: string;
    origin?: V2Route['origin'];
    queue?: string;
    returnTo?: string;
  }>(),
  {
    focusKind: '',
    focusId: '',
    origin: 'direct',
    queue: '',
    returnTo: '',
  },
);
const orders = ref<V2TechnicalOrder[]>([]);
const activeTab = ref<QueueTab>('PENDING');
const loading = ref(false);
const submitting = ref(false);
const error = ref('');
const notice = ref('');
const resultDrafts = reactive<Record<string, ResultDraft>>({});
const cancellationReasons = reactive<Record<string, string>>({});
let refreshSequence = 0;
const molecularCase = ref<V2CaseResult | null>(null);
const molecularSpecimenId = ref('');
const molecularProject = ref('常用分子检测');
const molecularConclusion = ref('');
const molecularResult = ref<V2MolecularResult | null>(null);
const molecularLoading = ref(false);
const historyDrawerOpen = ref(false);

const tabCounts = computed(() => ({
  PENDING: orders.value.filter((order) => order.status === 'PENDING').length,
  EXECUTING: orders.value.filter((order) => order.status === 'EXECUTING' && !requiresResult(order))
    .length,
  RESULT: orders.value.filter((order) => order.status === 'EXECUTING' && requiresResult(order))
    .length,
  COMPLETED: orders.value.filter((order) => order.status === 'COMPLETED').length,
}));
const scopedOrders = computed(() =>
  caseId.value ? orders.value.filter((order) => order.caseId === caseId.value) : orders.value,
);
const visibleOrders = computed(() =>
  scopedOrders.value.filter((order) => {
    if (activeTab.value === 'PENDING') return order.status === 'PENDING';
    if (activeTab.value === 'COMPLETED') return order.status === 'COMPLETED';
    if (activeTab.value === 'RESULT') return order.status === 'EXECUTING' && requiresResult(order);
    return order.status === 'EXECUTING' && !requiresResult(order);
  }),
);
const focusedOrder = computed(() => {
  if (!caseId.value) return null;
  return (
    scopedOrders.value.find((order) => order.orderId === props.focusId) ??
    scopedOrders.value[0] ??
    null
  );
});
const nextFocusedOrder = computed(() => {
  if (!focusedOrder.value) return null;
  const queueOrders = orders.value.filter(
    (order) => order.status !== 'COMPLETED' || order.orderId === focusedOrder.value?.orderId,
  );
  const index = queueOrders.findIndex((order) => order.orderId === focusedOrder.value?.orderId);
  return index >= 0 ? (queueOrders[index + 1] ?? null) : null;
});
const backLabel = computed(() => workspaceBackLabel(props.origin));
const backTarget = computed(() => workspaceBackTarget(props, caseId.value));
const caseOverviewTarget = computed(() => {
  if (props.origin === 'case' && safeLocalPath(props.returnTo)) return props.returnTo;
  const path = `/v2/cases/${encodeURIComponent(caseId.value)}`;
  return props.origin === 'workbench'
    ? appendNavigationContext(path, {
        origin: 'workbench',
        queue: props.queue,
        returnTo: props.returnTo,
      })
    : path;
});

function nextOrderPath(order: V2TechnicalOrder) {
  return appendNavigationContext(
    `/v2/technical-orders/${order.caseId}?focusId=${encodeURIComponent(order.orderId)}`,
    { origin: props.origin, queue: props.queue, returnTo: props.returnTo },
  );
}

function returnToWorkbench() {
  emit('navigate', safeLocalPath(props.returnTo) || '/v2/workbench');
}

watch(caseId, () => void loadMolecularCase(), { immediate: true });

function requiresResult(order: V2TechnicalOrder) {
  return order.items.some((item) => item.projectCode.includes('MOLECULAR') && !item.result);
}

function progress(order: V2TechnicalOrder) {
  const expected = order.items.reduce((sum, item) => sum + item.expectedCount, 0);
  const completed = order.items.reduce((sum, item) => sum + item.completedCount, 0);
  return { expected, completed, percent: expected ? Math.round((completed / expected) * 100) : 0 };
}

async function refresh() {
  const sequence = ++refreshSequence;
  loading.value = true;
  error.value = '';
  try {
    const result = await getV2TechnicalWorkbench();
    if (sequence !== refreshSequence) return;
    orders.value = result.orders;
    for (const order of result.orders) {
      for (const item of order.items) {
        resultDrafts[item.itemId] ??= { conclusion: '', value: '' };
      }
    }
    const focused =
      props.focusKind === 'technical-order' && props.focusId
        ? result.orders.find((order) => order.orderId === props.focusId)
        : undefined;
    if (focused) {
      activeTab.value =
        focused.status === 'PENDING'
          ? 'PENDING'
          : focused.status === 'COMPLETED'
            ? 'COMPLETED'
            : requiresResult(focused)
              ? 'RESULT'
              : 'EXECUTING';
    }
  } catch (requestError) {
    if (sequence === refreshSequence) {
      error.value = friendlyError(requestError, '技术医嘱队列暂时无法加载，请稍后重试。');
    }
  } finally {
    if (sequence === refreshSequence) loading.value = false;
  }
}

async function loadMolecularCase() {
  molecularCase.value = null;
  molecularSpecimenId.value = '';
  molecularResult.value = null;
  if (!caseId.value) return;
  molecularLoading.value = true;
  error.value = '';
  try {
    const [pathologyCase, materials] = await Promise.all([
      getV2Case(caseId.value),
      getV2MaterialTree(caseId.value),
    ]);
    if (pathologyCase.businessTypeCode !== 'MOLECULAR') return;
    molecularCase.value = pathologyCase;
    molecularSpecimenId.value = materials.specimens[0]?.specimenId ?? '';
  } catch (requestError) {
    error.value = friendlyError(requestError, '独立分子病例暂时无法加载，请从全局查询重新打开。');
  } finally {
    molecularLoading.value = false;
  }
}

function completeIndependentMolecularResult() {
  if (!molecularCase.value || !molecularProject.value.trim() || !molecularConclusion.value.trim())
    return;
  void submit(async () => {
    molecularResult.value = await completeV2MolecularResult({
      caseId: molecularCase.value!.caseId,
      specimenId: molecularSpecimenId.value || undefined,
      resultCode: molecularProject.value.trim(),
      resultData: JSON.stringify({ conclusion: molecularConclusion.value.trim() }),
      idempotencyKey: idempotencyKey('ux01-independent-molecular-result'),
    });
    notice.value = `${molecularCase.value!.caseNo} 的分子结果已完成，病例已进入待诊池。`;
  });
}

async function submit(operation: () => Promise<void>) {
  submitting.value = true;
  error.value = '';
  notice.value = '';
  try {
    await operation();
  } catch (requestError) {
    error.value = friendlyError(requestError, '技术医嘱操作未完成，请刷新后重试。');
  } finally {
    submitting.value = false;
  }
}

function execute(order: V2TechnicalOrder) {
  void submit(async () => {
    await executeV2TechnicalOrder(order.orderId, idempotencyKey('ux01-technical-execute'));
    await refresh();
    notice.value = `${order.orderNo} 已开始处理，所需玻片或结果记录已生成。`;
  });
}

function cancel(order: V2TechnicalOrder) {
  const reason = cancellationReasons[order.orderId]?.trim();
  if (!reason) return;
  void submit(async () => {
    await cancelV2TechnicalOrder({
      orderId: order.orderId,
      expectedVersion: order.version,
      reason,
      idempotencyKey: idempotencyKey('ux01-technical-cancel'),
    });
    await refresh();
    notice.value = `${order.orderNo} 已取消；已经产生的材料和结果记录仍保留。`;
  });
}

function enterResult(item: V2TechnicalItem) {
  const draft = resultDrafts[item.itemId];
  if (!draft?.conclusion.trim()) return;
  void submit(async () => {
    await enterV2TechnicalResult({
      itemId: item.itemId,
      resultData: JSON.stringify({
        conclusion: draft.conclusion.trim(),
        value: draft.value.trim(),
      }),
      expectedVersion: item.result?.version ?? 0,
      idempotencyKey: idempotencyKey('ux01-technical-result'),
    });
    await refresh();
    notice.value = `${item.projectName} 结果已返回诊断工作区。`;
  });
}

function completeProducedSlides(order: V2TechnicalOrder, item: V2TechnicalItem) {
  const slides = item.outputs
    .filter((output) => output.outputKind === 'SLIDE')
    .map((output) => ({ slideId: output.outputId, expectedVersion: 0 }));
  if (!slides.length) return;
  void submit(async () => {
    await completeV2Slides({
      slides,
      idempotencyKey: idempotencyKey('ux01-technical-slides-complete'),
    });
    await refresh();
    notice.value = `${order.orderNo} 的 ${slides.length} 张技术玻片已完成，诊断医生现在可以查看。`;
  });
}

function targetLabel(item: V2TechnicalItem) {
  return item.targets.map((target) => target.displayCode).join(' / ') || '病例';
}

function displayResult(item: V2TechnicalItem) {
  if (!item.result) return '';
  try {
    const parsed = JSON.parse(item.result.resultData) as { conclusion?: string; value?: string };
    return [parsed.conclusion, parsed.value].filter(Boolean).join(' · ');
  } catch {
    return item.result.resultData;
  }
}

onMounted(() => void refresh());
</script>

<template>
  <!-- Legacy layout retained as a reference for the focused redesign.
    <section class="technical-workbench-page" aria-label="技术医嘱工作台">
      <header class="page-heading compact-heading">
        <div>
          <p class="section-kicker">技术医嘱</p>
          <h2>技术执行工作台</h2>
          <p>按待处理、处理中、待录结果和已完成组织，不展示配置和内部数据结构。</p>
        </div>
        <button class="secondary-button" type="button" :disabled="loading" @click="refresh">
          {{ loading ? '刷新中…' : '刷新队列' }}
        </button>
        <button
          v-if="caseId"
          class="secondary-button"
          type="button"
          @click="historyDrawerOpen = true"
        >
          历史记录
        </button>
      </header>

      <p v-if="error" class="feedback error" role="alert">{{ error }}</p>
      <p v-if="notice" class="feedback success" role="status">{{ notice }}</p>

      <V2CaseHeader
        v-if="molecularCase"
        :case-id="molecularCase.caseId"
        :pathology-no="molecularCase.caseNo"
        :patient-reference="molecularCase.patientReference"
        :visit-reference="molecularCase.visitReference"
        :business-type-code="molecularCase.businessTypeCode"
        current-responsibility="技术结果录入"
        :report-status="molecularResult ? '结果已完成' : '待录结果'"
        progress="独立分子病例"
        @open-case="emit('navigate', `/v2/cases/${molecularCase.caseId}`)"
      />

      <section
        v-if="molecularLoading || molecularCase"
        class="workspace-panel independent-molecular-panel"
        aria-labelledby="independent-molecular-heading"
      >
        <div v-if="molecularLoading" class="list-skeleton" aria-label="正在读取分子病例">
          <span></span><span></span>
        </div>
        <template v-else-if="molecularCase">
          <header class="panel-title-row">
            <div>
              <p class="section-kicker">独立分子病例</p>
              <h3 id="independent-molecular-heading">{{ molecularCase.caseNo }} · 录入结果</h3>
              <p>{{ molecularCase.patientReference }} · 不经过虚构的取材、蜡块或玻片。</p>
            </div>
            <span class="status-pill" :class="{ success: molecularResult }">{{
              molecularResult ? '结果已完成' : '待录结果'
            }}</span>
          </header>
          <div class="field-grid">
            <label>
              检测项目
              <input v-model="molecularProject" :readonly="Boolean(molecularResult)" />
            </label>
            <label class="span-two">
              结果结论
              <textarea
                v-model="molecularConclusion"
                rows="3"
                :readonly="Boolean(molecularResult)"
                placeholder="录入可供诊断医生查看的结构化结论"
              ></textarea>
            </label>
          </div>
          <div class="panel-footer-actions">
            <button
              v-if="!molecularResult"
              class="primary-button"
              type="button"
              :disabled="submitting || !molecularProject.trim() || !molecularConclusion.trim()"
              @click="completeIndependentMolecularResult"
            >
              {{ submitting ? '正在保存…' : '完成分子结果' }}
            </button>
            <button
              v-else
              class="primary-button"
              type="button"
              @click="emit('navigate', `/v2/diagnosis/${molecularCase.caseId}`)"
            >
              查看待诊病例
            </button>
          </div>
        </template>
      </section>

      <div class="workspace-tabs technical-queue-tabs" role="tablist" aria-label="技术医嘱状态">
        <button
          v-for="tab in ['PENDING', 'EXECUTING', 'RESULT', 'COMPLETED'] as const"
          :key="tab"
          type="button"
          role="tab"
          :aria-selected="activeTab === tab"
          :class="{ active: activeTab === tab }"
          @click="activeTab = tab"
        >
          {{
            tab === 'PENDING'
              ? '待处理'
              : tab === 'EXECUTING'
                ? '处理中'
                : tab === 'RESULT'
                  ? '待录结果'
                  : '已完成'
          }}
          <span class="count-pill">{{ tabCounts[tab] }}</span>
        </button>
      </div>

      <div v-if="loading" class="list-skeleton"><span></span><span></span><span></span></div>
      <div v-else-if="!visibleOrders.length" class="empty-state workspace-panel">
        <strong>{{
          activeTab === 'PENDING'
            ? '当前没有待处理技术医嘱'
            : activeTab === 'RESULT'
              ? '当前没有待录结果项目'
              : '当前状态下没有技术医嘱'
        }}</strong>
        <span>新医嘱或状态变化会显示在对应标签中。</span>
      </div>
      <div v-else class="technical-order-queue">
        <article
          v-for="order in visibleOrders"
          :key="order.orderId"
          class="workspace-panel technical-order-card"
          :class="{ 'technical-order-focused': props.focusId === order.orderId }"
        >
          <header class="technical-order-heading">
            <span
              ><strong>{{ order.caseNo ?? order.orderNo }}</strong
              ><small>{{ order.patientReference ?? order.orderNo }}</small></span
            >
            <span
              class="status-pill"
              :class="{ warning: order.blocking, success: order.status === 'COMPLETED' }"
              >{{ statusName(order.status) }}{{ order.blocking ? ' · 签发前需完成' : '' }}</span
            >
          </header>
          <div class="technical-order-summary">
            <span
              ><small>项目</small
              ><strong>{{ order.items.map((item) => item.projectName).join('、') }}</strong></span
            >
            <span
              ><small>目标</small
              ><strong>{{ order.items.map(targetLabel).join('、') }}</strong></span
            >
            <span
              ><small>进度</small
              ><span class="technical-progress"
                ><span class="progress-track"
                  ><span :style="{ width: `${progress(order).percent}%` }"></span></span
                ><strong
                  >{{ progress(order).completed }}/{{ progress(order).expected }}</strong
                ></span
              ></span
            >
          </div>

          <div class="technical-item-list">
            <section v-for="item in order.items" :key="item.itemId" class="technical-item-row">
              <header>
                <span
                  ><strong>{{ item.projectName }}</strong
                  ><small
                    >{{ targetLabel(item) }} · {{ item.completedCount }}/{{
                      item.expectedCount
                    }}</small
                  ></span
                ><span class="status-pill">{{ statusName(item.status) }}</span>
              </header>
              <p v-if="item.result" class="feedback success compact-feedback">
                结果：{{ displayResult(item) }}
              </p>
              <div
                v-else-if="item.projectCode.includes('MOLECULAR') && order.status === 'EXECUTING'"
                class="result-entry-form"
              >
                <label
                  >结论
                  <input
                    v-model="resultDrafts[item.itemId].conclusion"
                    placeholder="输入结构化结论"
                /></label>
                <label
                  >结果值 <input v-model="resultDrafts[item.itemId].value" placeholder="可选"
                /></label>
                <button
                  class="primary-button"
                  type="button"
                  :disabled="submitting || !resultDrafts[item.itemId].conclusion.trim()"
                  @click="enterResult(item)"
                >
                  保存并返回诊断
                </button>
              </div>
              <button
                v-if="
                  item.outputs.some((output) => output.outputKind === 'SLIDE') &&
                  item.status !== 'COMPLETED'
                "
                class="secondary-button"
                type="button"
                :disabled="submitting"
                @click="completeProducedSlides(order, item)"
              >
                完成
                {{
                  item.outputs.filter((output) => output.outputKind === 'SLIDE').length
                }}
                张技术玻片
              </button>
  </section>

          <footer class="technical-order-actions">
            <button
              class="text-button"
              type="button"
              @click="emit('navigate', `/v2/diagnosis/${order.caseId}`)"
            >
              打开诊断工作区
            </button>
            <button
              class="text-button"
              type="button"
              @click="emit('navigate', `/v2/cases/${order.caseId}`)"
            >
              查看病例历史
            </button>
            <div class="action-group">
              <button
                v-if="order.status === 'PENDING'"
                class="primary-button"
                type="button"
                :disabled="submitting"
                @click="execute(order)"
              >
                开始处理
              </button>
              <template v-if="order.status !== 'COMPLETED'">
                <input
                  v-model="cancellationReasons[order.orderId]"
                  aria-label="取消原因"
                  placeholder="取消原因"
                />
                <button
                  class="secondary-button"
                  type="button"
                  :disabled="submitting || !cancellationReasons[order.orderId]?.trim()"
                  @click="cancel(order)"
                >
                  取消医嘱
                </button>
              </template>
            </div>
          </footer>
        </article>
      </div>
      <V2HistoryDrawer
        :open="historyDrawerOpen"
        :case-id="caseId"
        title="技术医嘱历史"
        target-label="技术工作台"
        @close="historyDrawerOpen = false"
      />
    </section>
  </div>

  -->

  <section class="focused-technical-page" aria-label="技术医嘱工作区">
    <template v-if="!caseId">
      <header class="page-heading compact-heading">
        <div>
          <p class="section-kicker">技术医嘱</p>
          <h2>待处理技术医嘱</h2>
          <p>从队列选择一项医嘱，进入只包含本次执行所需信息的工作区。</p>
        </div>
        <button class="secondary-button" type="button" :disabled="loading" @click="refresh">
          {{ loading ? '刷新中…' : '刷新队列' }}
        </button>
      </header>
      <div class="workspace-tabs technical-queue-tabs" role="tablist" aria-label="技术医嘱状态">
        <button
          v-for="tab in ['PENDING', 'EXECUTING', 'RESULT', 'COMPLETED'] as const"
          :key="tab"
          type="button"
          role="tab"
          :aria-selected="activeTab === tab"
          :class="{ active: activeTab === tab }"
          @click="activeTab = tab"
        >
          {{
            tab === 'PENDING'
              ? '待处理'
              : tab === 'EXECUTING'
                ? '处理中'
                : tab === 'RESULT'
                  ? '待录结果'
                  : '已完成'
          }}
          <span class="count-pill">{{ tabCounts[tab] }}</span>
        </button>
      </div>
      <div v-if="loading" class="list-skeleton"><span></span><span></span><span></span></div>
      <div v-else-if="!visibleOrders.length" class="empty-state workspace-panel">
        <strong>当前没有{{ activeTab === 'RESULT' ? '待录结果' : '待处理技术医嘱' }}</strong>
      </div>
      <button
        v-for="order in visibleOrders"
        :key="order.orderId"
        type="button"
        class="technical-queue-row"
        @click="
          emit(
            'navigate',
            '/v2/cases/' + order.caseId + '?focus=technical-order&focusId=' + order.orderId,
          )
        "
      >
        <span
          ><strong>{{ order.caseNo ?? order.orderNo }}</strong
          ><small>{{ order.patientReference ?? '病例' }}</small></span
        >
        <span
          ><strong>{{ order.items.map((item) => item.projectName).join('、') }}</strong
          ><small
            >{{ order.items.map(targetLabel).join('、') }} · {{ progress(order).completed }}/{{
              progress(order).expected
            }}</small
          ></span
        >
        <span class="status-pill" :class="{ success: order.status === 'COMPLETED' }">{{
          statusName(order.status)
        }}</span>
        <span class="queue-row-arrow" aria-hidden="true">→</span>
      </button>
    </template>

    <template v-else>
      <V2CaseHeader
        v-if="molecularCase || focusedOrder"
        :case-id="caseId"
        :pathology-no="molecularCase?.caseNo ?? focusedOrder?.caseNo ?? caseId"
        :patient-reference="
          molecularCase?.patientReference ?? focusedOrder?.patientReference ?? '当前病例'
        "
        :visit-reference="molecularCase?.visitReference"
        :business-type-code="molecularCase?.businessTypeCode ?? 'TECHNICAL_ORDER'"
        current-work="技术医嘱"
        :report-status="
          focusedOrder ? statusName(focusedOrder.status) : molecularResult ? '结果已完成' : '处理中'
        "
        :progress="
          focusedOrder
            ? progress(focusedOrder).completed + '/' + progress(focusedOrder).expected
            : '独立结果'
        "
        :back-label="backLabel"
        @open-case="emit('navigate', backTarget)"
        @open-overview="emit('navigate', caseOverviewTarget)"
      />

      <p v-if="error" class="feedback error" role="alert">{{ error }}</p>
      <p v-if="notice" class="feedback success" role="status">{{ notice }}</p>
      <div v-if="loading && !focusedOrder" class="list-skeleton"><span></span><span></span></div>

      <section v-if="molecularCase" class="workspace-panel technical-focused-panel">
        <header class="focused-task-heading">
          <div>
            <p class="section-kicker">当前任务</p>
            <h2>独立结果录入</h2>
            <p>只填写本次技术结果，不展开病例生产流程。</p>
          </div>
          <span class="status-pill" :class="{ success: molecularResult }">{{
            molecularResult ? '已完成' : '待录结果'
          }}</span>
        </header>
        <div class="field-grid">
          <label
            >检测项目<input v-model="molecularProject" :readonly="Boolean(molecularResult)"
          /></label>
          <label class="span-two"
            >结果结论<textarea
              v-model="molecularConclusion"
              rows="3"
              :readonly="Boolean(molecularResult)"
            />
          </label>
        </div>
        <button
          v-if="!molecularResult"
          class="primary-button"
          type="button"
          :disabled="submitting || !molecularProject.trim() || !molecularConclusion.trim()"
          @click="completeIndependentMolecularResult"
        >
          完成
        </button>
      </section>

      <section
        v-if="focusedOrder"
        class="workspace-panel technical-focused-panel"
        aria-label="技术医嘱执行"
      >
        <header class="focused-task-heading">
          <div>
            <p class="section-kicker">当前任务</p>
            <h2>技术医嘱</h2>
            <p>申请医生：未记录 · 目标材料：{{ focusedOrder.items.map(targetLabel).join('、') }}</p>
          </div>
          <span class="status-pill" :class="{ success: focusedOrder.status === 'COMPLETED' }">{{
            statusName(focusedOrder.status)
          }}</span>
        </header>
        <div class="technical-order-focus-summary">
          <span
            ><small>医嘱编号</small><strong>{{ focusedOrder.orderNo }}</strong></span
          >
          <span
            ><small>进度</small
            ><strong
              >{{ progress(focusedOrder).completed }}/{{ progress(focusedOrder).expected }}</strong
            ></span
          >
          <span
            ><small>项目</small
            ><strong>{{
              focusedOrder.items.map((item) => item.projectName).join('、')
            }}</strong></span
          >
        </div>
        <div class="technical-item-list">
          <section v-for="item in focusedOrder.items" :key="item.itemId" class="technical-item-row">
            <header>
              <span
                ><strong>{{ item.projectName }}</strong
                ><small>{{ targetLabel(item) }}</small></span
              ><span class="status-pill">{{ statusName(item.status) }}</span>
            </header>
            <p v-if="item.result" class="feedback success compact-feedback">
              结果：{{ displayResult(item) }}
            </p>
            <div
              v-else-if="
                item.projectCode.includes('MOLECULAR') && focusedOrder.status === 'EXECUTING'
              "
              class="result-entry-form"
            >
              <label
                >结论<input
                  v-model="resultDrafts[item.itemId].conclusion"
                  placeholder="输入结果结论"
              /></label>
              <label
                >结果值<input v-model="resultDrafts[item.itemId].value" placeholder="可选"
              /></label>
              <button
                class="primary-button"
                type="button"
                :disabled="submitting || !resultDrafts[item.itemId].conclusion.trim()"
                @click="enterResult(item)"
              >
                保存结果
              </button>
            </div>
            <button
              v-if="
                item.outputs.some((output) => output.outputKind === 'SLIDE') &&
                item.status !== 'COMPLETED'
              "
              class="secondary-button"
              type="button"
              :disabled="submitting"
              @click="completeProducedSlides(focusedOrder, item)"
            >
              完成玻片
            </button>
          </section>
        </div>
        <div class="focused-bottom-actions">
          <button
            v-if="focusedOrder.status === 'PENDING'"
            class="primary-button"
            type="button"
            :disabled="submitting"
            @click="execute(focusedOrder)"
          >
            开始处理
          </button>
          <button
            v-if="focusedOrder.status !== 'COMPLETED'"
            class="secondary-button"
            type="button"
            :disabled="submitting || !cancellationReasons[focusedOrder.orderId]?.trim()"
            @click="cancel(focusedOrder)"
          >
            取消医嘱
          </button>
          <button
            v-if="focusedOrder.status === 'COMPLETED' && nextFocusedOrder"
            class="primary-button"
            type="button"
            @click="emit('navigate', nextOrderPath(nextFocusedOrder))"
          >
            完成并下一项
          </button>
          <button
            v-if="focusedOrder.status === 'COMPLETED'"
            class="secondary-button"
            type="button"
            @click="returnToWorkbench"
          >
            完成并返回工作台
          </button>
        </div>
      </section>
      <div v-if="focusedOrder && focusedOrder.status !== 'COMPLETED'" class="focused-form-actions">
        <label
          >取消原因<input
            v-model="cancellationReasons[focusedOrder.orderId]"
            placeholder="需要取消时填写原因"
        /></label>
      </div>
    </template>

    <V2HistoryDrawer
      :open="historyDrawerOpen"
      :case-id="caseId"
      title="技术医嘱历史"
      target-label="当前技术医嘱"
      @close="historyDrawerOpen = false"
    />
  </section>
</template>
