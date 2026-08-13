<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';

import type { V2AuthUser } from '../auth';
import {
  appendNavigationContext,
  workspaceBackLabel,
  workspaceBackTarget,
  type V2Route,
} from '../navigation';
import { friendlyError, idempotencyKey } from '../uiText';
import { getV2Case, type V2CaseResult } from '../v2Api';
import {
  getV2MaterialTree,
  printV2Slides,
  type V2MaterialTree,
  type V2SlideNode,
} from '../v2MaterialApi';
import { operationsRequest, type FrozenWorkspace } from '../v2OperationsApi';

const props = defineProps<{
  caseId?: string;
  roundId?: string;
  authUser?: V2AuthUser | null;
  origin?: V2Route['origin'];
  queue?: string;
  returnTo?: string;
}>();

const emit = defineEmits<{ navigate: [path: string] }>();

const workspace = ref<FrozenWorkspace | null>(null);
const caseSummary = ref<V2CaseResult | null>(null);
const materialTree = ref<V2MaterialTree | null>(null);
const selectedRoundId = ref('');
const selectedSpecimenId = ref('');
const specimenCode = ref('');
const collectionSite = ref('');
const collectionMethodCode = ref('FROZEN');
const cancellationReason = ref('');
const loading = ref(false);
const submitting = ref(false);
const error = ref('');
const notice = ref('');
const endDialogOpen = ref(false);
const endSpecimenIds = ref<string[]>([]);
const clock = ref(Date.now());

const canManageRounds = computed(() => hasPermission('P14-PERM-019'));
const canCancelRound = computed(() => hasPermission('P14-PERM-020'));
const canEndFrozen = computed(() => hasPermission('P14-PERM-021'));
const backLabel = computed(() => workspaceBackLabel(props.origin ?? 'direct'));
const backTarget = computed(() =>
  workspaceBackTarget(
    { origin: props.origin ?? 'direct', returnTo: props.returnTo ?? '' },
    props.caseId ?? '',
  ),
);

const selectedRound = computed(
  () => workspace.value?.rounds.find((round) => round.roundId === selectedRoundId.value) ?? null,
);
const activeRounds = computed(() =>
  (workspace.value?.rounds ?? []).filter((round) => round.status !== 'CANCELLED'),
);
const selectedSpecimen = computed(
  () =>
    selectedRound.value?.specimens.find(
      (specimen) => specimen.specimenId === selectedSpecimenId.value,
    ) ??
    selectedRound.value?.specimens[0] ??
    null,
);
const roundSlides = computed<V2SlideNode[]>(() => {
  const specimenIds = new Set(
    (selectedRound.value?.specimens ?? []).map((item) => item.specimenId),
  );
  return (materialTree.value?.specimens ?? [])
    .filter((specimen) => specimenIds.has(specimen.specimenId))
    .flatMap((specimen) => [
      ...specimen.directSlides,
      ...specimen.blocks.flatMap((block) => block.slides),
    ]);
});
const selectedRoundFinished = computed(() =>
  Boolean(selectedRound.value?.diagnosisSignedTime || selectedRound.value?.status === 'SIGNED'),
);
const canCreateNextRound = computed(() =>
  Boolean(selectedRoundFinished.value && !workspace.value?.ended && canManageRounds.value),
);
const canEnd = computed(() =>
  Boolean(
    canEndFrozen.value &&
      workspace.value &&
      !workspace.value.ended &&
      activeRounds.value.length > 0 &&
      activeRounds.value.every((round) => round.status === 'SIGNED'),
  ),
);

const elapsedLabel = computed(() => {
  const round = selectedRound.value;
  if (!round) return '—';
  const start = Date.parse(round.arrivalTime);
  if (!Number.isFinite(start)) return '—';
  const end = round.diagnosisSignedTime ? Date.parse(round.diagnosisSignedTime) : clock.value;
  const seconds = Math.max(0, Math.floor((end - start) / 1000));
  const minutes = Math.floor(seconds / 60);
  return `${minutes}分${String(seconds % 60).padStart(2, '0')}秒`;
});

watch(
  () => [props.caseId, props.roundId],
  ([caseId, roundId]) => {
    selectedRoundId.value = roundId ?? '';
    if (caseId) void load();
  },
  { immediate: true },
);

let timer: number | undefined;
onMounted(() => {
  timer = window.setInterval(() => {
    clock.value = Date.now();
  }, 1000);
});
onUnmounted(() => {
  if (timer) window.clearInterval(timer);
});

function hasPermission(permission: string) {
  return props.authUser?.permissions.includes(permission) ?? false;
}

function contextualPath(path: string) {
  return appendNavigationContext(path, {
    origin: props.origin ?? 'direct',
    queue: props.queue,
    returnTo: props.returnTo,
  });
}

function roundLabel(round: FrozenWorkspace['rounds'][number]) {
  if (round.status === 'CANCELLED') return '已取消';
  if (round.diagnosisSignedTime || round.status === 'SIGNED') return '已报告';
  if (round.diagnosisId) return '待报告';
  if (round.productionComplete) return '待诊断';
  if (round.totalRequiredSlides > 0) return '冰冻制片';
  return '待取材';
}

function tatLabel(status?: string) {
  return status === 'OVERDUE' ? '已超时' : status === 'WARNING' ? '临近时限' : '正常';
}

async function load() {
  if (!props.caseId) return;
  loading.value = true;
  error.value = '';
  try {
    const [loadedWorkspace, loadedCase] = await Promise.all([
      operationsRequest<FrozenWorkspace>(`/frozen/cases/${props.caseId}/workspace`),
      getV2Case(props.caseId),
    ]);
    workspace.value = loadedWorkspace;
    caseSummary.value = loadedCase;
    materialTree.value = await getV2MaterialTree(props.caseId).catch(() => null);
    const nextRound =
      loadedWorkspace.rounds.find(
        (round) => round.roundId === (props.roundId || selectedRoundId.value),
      ) ?? loadedWorkspace.rounds.at(-1);
    selectedRoundId.value = nextRound?.roundId ?? '';
    selectedSpecimenId.value = nextRound?.specimens[0]?.specimenId ?? '';
    if (nextRound) {
      endSpecimenIds.value = nextRound.specimens.map((specimen) => specimen.specimenId);
    }
  } catch (requestError) {
    workspace.value = null;
    caseSummary.value = null;
    materialTree.value = null;
    error.value = friendlyError(requestError, '无法打开冰冻病例，请返回工作台重试');
  } finally {
    loading.value = false;
  }
}

function selectRound(round: FrozenWorkspace['rounds'][number]) {
  selectedRoundId.value = round.roundId;
  selectedSpecimenId.value = round.specimens[0]?.specimenId ?? '';
  endSpecimenIds.value = round.specimens.map((specimen) => specimen.specimenId);
}

async function submit(operation: () => Promise<void>) {
  submitting.value = true;
  error.value = '';
  notice.value = '';
  try {
    await operation();
  } catch (requestError) {
    error.value = friendlyError(requestError, '操作未完成，请检查当前轮次后重试');
  } finally {
    submitting.value = false;
  }
}

function startFirstRound() {
  if (!props.caseId) return;
  void submit(async () => {
    await operationsRequest(`/frozen/cases/${props.caseId}/rounds`, {
      method: 'POST',
      body: JSON.stringify({
        arrivalTime: new Date().toISOString(),
        idempotencyKey: idempotencyKey('frozen-round'),
      }),
    });
    await load();
    notice.value = '第 1 轮已开始';
  });
}

function createNextRound() {
  if (!props.caseId) return;
  void submit(async () => {
    await operationsRequest(`/frozen/cases/${props.caseId}/rounds`, {
      method: 'POST',
      body: JSON.stringify({
        arrivalTime: new Date().toISOString(),
        createNew: true,
        idempotencyKey: idempotencyKey('frozen-round'),
      }),
    });
    await load();
    notice.value = '新的冰冻轮次已创建';
  });
}

function addSpecimen() {
  if (!props.caseId) return;
  void submit(async () => {
    await operationsRequest(`/frozen/cases/${props.caseId}/specimens`, {
      method: 'POST',
      body: JSON.stringify({
        specimenCode: specimenCode.value.trim(),
        specimenKindCode: 'TISSUE',
        collectionSite: collectionSite.value.trim(),
        collectionMethodCode: collectionMethodCode.value.trim() || 'FROZEN',
        idempotencyKey: idempotencyKey('frozen-specimen'),
      }),
    });
    specimenCode.value = '';
    collectionSite.value = '';
    await load();
    notice.value = '冰冻标本已加入当前轮次';
  });
}

function openGrossing() {
  if (!props.caseId || !selectedRound.value) return;
  emit(
    'navigate',
    contextualPath(`/v2/grossing/${props.caseId}?roundId=${selectedRound.value.roundId}`),
  );
}

function openProduction() {
  if (!props.caseId || !selectedRound.value) return;
  emit(
    'navigate',
    contextualPath(`/v2/production/${props.caseId}?roundId=${selectedRound.value.roundId}`),
  );
}

async function openDiagnosis() {
  if (!props.caseId || !selectedRound.value) return;
  const roundId = selectedRound.value.roundId;
  let diagnosisId = selectedRound.value.diagnosisId;
  await submit(async () => {
    if (!diagnosisId) {
      const result = await operationsRequest<{ diagnosisId: string }>(
        `/frozen/rounds/${roundId}/diagnosis`,
        {
          method: 'POST',
          body: JSON.stringify({ idempotencyKey: idempotencyKey('frozen-diagnosis') }),
        },
      );
      diagnosisId = result.diagnosisId;
      await load();
    }
    emit(
      'navigate',
      contextualPath(`/v2/diagnosis/${props.caseId}?roundId=${roundId}&focusId=${diagnosisId}`),
    );
  });
}

function printSlides() {
  if (!roundSlides.value.length) return;
  void submit(async () => {
    await printV2Slides({
      slideIds: roundSlides.value.map((slide) => slide.slideId),
      reason: '冰冻轮次标签打印',
      idempotencyKey: idempotencyKey('frozen-slide-print'),
    });
    notice.value = '冰冻玻片标签打印记录已保存';
  });
}

function cancelRound() {
  if (!selectedRound.value || !cancellationReason.value.trim()) return;
  void submit(async () => {
    await operationsRequest(`/frozen/rounds/${selectedRound.value!.roundId}/cancel`, {
      method: 'POST',
      body: JSON.stringify({
        reason: cancellationReason.value.trim(),
        idempotencyKey: idempotencyKey('frozen-round-cancel'),
      }),
    });
    cancellationReason.value = '';
    await load();
    notice.value = '本轮已取消，历史材料仍保留';
  });
}

function openEndDialog() {
  endSpecimenIds.value = activeRounds.value.flatMap((round) =>
    round.specimens.map((specimen) => specimen.specimenId),
  );
  endDialogOpen.value = true;
}

function finishFrozen() {
  if (!props.caseId) return;
  void submit(async () => {
    const result = await operationsRequest<{ routinePathologyNo: string; duplicate: boolean }>(
      `/frozen/cases/${props.caseId}/finish`,
      {
        method: 'POST',
        body: JSON.stringify({
          specimenIds: endSpecimenIds.value,
          idempotencyKey: idempotencyKey('frozen-end'),
        }),
      },
    );
    endDialogOpen.value = false;
    await load();
    notice.value = result.duplicate
      ? `冰冻病例已结束，常规病理号：${result.routinePathologyNo ?? '—'}`
      : `冰冻已结束，已创建常规病例：${result.routinePathologyNo ?? '—'}`;
  });
}

function retryNotification() {
  if (!selectedRound.value) return;
  void submit(async () => {
    await operationsRequest(`/frozen/rounds/${selectedRound.value!.roundId}/notification/retry`, {
      method: 'POST',
    });
    await load();
    notice.value = '通知已重新发送';
  });
}
</script>

<template>
  <section class="focused-frozen-page" aria-label="冰冻工作区">
    <template v-if="!caseId">
      <div class="empty-state workspace-panel">
        <strong>请从工作台打开一例冰冻病例</strong>
        <span>冰冻轮次、计时和当前材料会在这里集中显示。</span>
      </div>
    </template>
    <template v-else-if="loading">
      <div class="list-skeleton"><span></span><span></span><span></span></div>
    </template>
    <template v-else-if="workspace">
      <header class="frozen-header">
        <div class="header-leading">
          <button class="text-button" type="button" @click="emit('navigate', backTarget)">
            ← {{ backLabel }}
          </button>
          <div>
            <p class="section-kicker">冰冻工作区</p>
            <h1>{{ workspace.pathologyNo }}</h1>
            <p class="muted">
              {{ caseSummary?.patientReference || '当前患者' }} · {{ workspace.businessTypeCode }}
            </p>
          </div>
        </div>
        <div class="frozen-header-meta">
          <span>第 {{ selectedRound?.roundNo ?? '—' }} 轮</span>
          <strong>已用时 {{ elapsedLabel }}</strong>
          <span :class="['tat-status', selectedRound?.tatStatus?.toLowerCase()]">{{
            tatLabel(selectedRound?.tatStatus)
          }}</span>
        </div>
        <button
          class="secondary-button"
          type="button"
          @click="emit('navigate', contextualPath(`/v2/cases/${workspace.frozenCaseId}`))"
        >
          查看病例
        </button>
      </header>

      <p v-if="error" class="feedback error" role="alert">{{ error }}</p>
      <p v-if="notice" class="feedback success" role="status">{{ notice }}</p>

      <nav class="frozen-round-tabs" aria-label="冰冻轮次">
        <button
          v-for="round in workspace.rounds"
          :key="round.roundId"
          type="button"
          :class="{ active: round.roundId === selectedRoundId }"
          @click="selectRound(round)"
        >
          <strong>第 {{ round.roundNo }} 轮</strong>
          <span>{{ roundLabel(round) }}</span>
          <small>{{ round.specimens.length }} 个标本 · {{ round.elapsedMinutes ?? 0 }} 分钟</small>
        </button>
        <span v-if="workspace.ended" class="status-pill success">冰冻已结束</span>
      </nav>

      <section v-if="selectedRound" class="frozen-workspace-panel workspace-panel">
        <div class="panel-toolbar">
          <div>
            <p class="section-kicker">第 {{ selectedRound.roundNo }} 轮</p>
            <h2>{{ roundLabel(selectedRound) }}</h2>
            <p class="muted">
              {{ selectedRound.specimens.length }} 个标本 · 玻片
              {{ selectedRound.completedRequiredSlides }}/{{
                selectedRound.totalRequiredSlides
              }}
              完成 · {{ selectedRound.arrivalTime }}
            </p>
          </div>
          <div class="toolbar-actions">
            <button
              v-if="!selectedRound.productionComplete"
              class="secondary-button"
              type="button"
              @click="openGrossing"
            >
              冰冻取材
            </button>
            <button
              v-if="selectedRound.productionComplete && !selectedRound.diagnosisSignedTime"
              class="primary-button"
              type="button"
              @click="openDiagnosis"
            >
              进入冰冻诊断
            </button>
            <button
              v-if="roundSlides.length"
              class="secondary-button"
              type="button"
              :disabled="submitting"
              @click="printSlides"
            >
              打印标签
            </button>
            <button
              v-if="selectedRound.notificationStatus === 'FAILED'"
              class="secondary-button"
              type="button"
              :disabled="submitting"
              @click="retryNotification"
            >
              重试通知
            </button>
          </div>
        </div>

        <div class="frozen-material-layout">
          <section class="frozen-specimen-column" aria-label="本轮标本">
            <div class="panel-title-row">
              <h3>本轮标本</h3>
              <span class="muted">{{ selectedRound.specimens.length }} 个</span>
            </div>
            <button
              v-for="specimen in selectedRound.specimens"
              :key="specimen.specimenId"
              type="button"
              class="specimen-row"
              :class="{ active: specimen.specimenId === selectedSpecimen?.specimenId }"
              @click="selectedSpecimenId = specimen.specimenId"
            >
              <strong>{{ specimen.specimenCode }}</strong>
              <span>{{ specimen.specimenName || specimen.collectionSite || '未填写部位' }}</span>
              <small>{{ specimen.specimenNo }}</small>
            </button>
            <p v-if="!selectedRound.specimens.length" class="empty-state compact">
              本轮尚未登记标本
            </p>
          </section>

          <section class="frozen-slide-column" aria-label="本轮玻片">
            <div class="panel-title-row">
              <h3>{{ selectedSpecimen?.specimenCode || '本轮' }} 的玻片</h3>
              <span class="muted">{{ roundSlides.length }} 张</span>
            </div>
            <div v-if="roundSlides.length" class="dense-table-wrap">
              <table class="dense-table">
                <thead>
                  <tr>
                    <th>玻片编号</th>
                    <th>项目</th>
                    <th>状态</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="slide in roundSlides" :key="slide.slideId">
                    <td>
                      <strong>{{ slide.slideCode }}</strong>
                    </td>
                    <td>{{ slide.stainCode || slide.slideType || '冰冻制片' }}</td>
                    <td>
                      <span :class="['status-pill', slide.completed ? 'success' : 'warning']">{{
                        slide.completed ? '已完成' : '待完成'
                      }}</span>
                    </td>
                    <td>
                      <button
                        v-if="!slide.completed"
                        class="text-button"
                        type="button"
                        @click="openProduction"
                      >
                        进入制片</button
                      ><span v-else class="muted">已完成</span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div v-else class="empty-state compact">
              <strong>本轮尚未建立玻片</strong>
              <span>从这里直接进入冰冻制片，按当前轮次标本生成玻片。</span>
              <button class="primary-button" type="button" @click="openProduction">
                进入冰冻制片
              </button>
            </div>
          </section>
        </div>

        <details class="frozen-secondary-details">
          <summary>技术记录与轮次操作</summary>
          <div class="secondary-details-grid">
            <form
              v-if="!workspace.ended && canManageRounds"
              class="inline-form"
              @submit.prevent="addSpecimen"
            >
              <strong>新增本轮标本</strong>
              <input v-model="specimenCode" required placeholder="标本编号" aria-label="标本编号" />
              <input
                v-model="collectionSite"
                required
                placeholder="部位/来源"
                aria-label="部位或来源"
              />
              <input v-model="collectionMethodCode" placeholder="采集方式" aria-label="采集方式" />
              <button class="secondary-button" type="submit" :disabled="submitting">
                加入本轮
              </button>
            </form>
            <div
              v-if="
                canCancelRound &&
                !selectedRoundFinished &&
                selectedRound.status !== 'CANCELLED' &&
                !workspace.ended
              "
              class="inline-form"
            >
              <strong>取消本轮</strong>
              <input
                v-model="cancellationReason"
                required
                placeholder="取消原因"
                aria-label="取消原因"
              />
              <button
                class="danger-button"
                type="button"
                :disabled="submitting || !cancellationReason.trim()"
                @click="cancelRound"
              >
                确认取消
              </button>
            </div>
            <p v-if="selectedRound.notificationStatus" class="muted">
              通知状态：{{ selectedRound.notificationStatus
              }}{{
                selectedRound.cancellationReason ? ` · ${selectedRound.cancellationReason}` : ''
              }}
            </p>
          </div>
        </details>
      </section>

      <section v-else class="empty-state workspace-panel">
        <strong>尚未创建冰冻轮次</strong><span>只有确认收到有效标本后才创建轮次。</span
        ><button
          v-if="canManageRounds && !workspace.ended"
          class="primary-button"
          type="button"
          @click="startFirstRound"
        >
          开始第 1 轮
        </button>
      </section>

      <footer class="frozen-footer-actions">
        <button
          v-if="canCreateNextRound"
          class="secondary-button"
          type="button"
          :disabled="submitting"
          @click="createNextRound"
        >
          新增一轮
        </button>
        <span v-if="workspace.routinePathologyNo" class="feedback success"
          >已转常规：{{ workspace.routinePathologyNo }}</span
        >
        <button
          v-if="canEnd"
          class="primary-button"
          type="button"
          :disabled="submitting"
          @click="openEndDialog"
        >
          结束冰冻并转常规
        </button>
      </footer>

      <div
        v-if="endDialogOpen"
        class="modal-backdrop"
        role="presentation"
        @click.self="endDialogOpen = false"
      >
        <section
          class="confirm-dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby="frozen-end-title"
        >
          <h2 id="frozen-end-title">结束冰冻并转常规</h2>
          <p>
            将结束冰冻，创建 1
            个新的常规病例、新的常规病理号和新的常规标本。请选择需要转入常规的有效标本。
          </p>
          <label v-for="round in activeRounds" :key="round.roundId" class="end-round-group">
            <strong>第 {{ round.roundNo }} 轮</strong>
            <span
              v-for="specimen in round.specimens"
              :key="specimen.specimenId"
              class="end-specimen-option"
            >
              <input v-model="endSpecimenIds" type="checkbox" :value="specimen.specimenId" />
              {{ specimen.specimenCode }} ·
              {{ specimen.specimenName || specimen.collectionSite || '未填写部位' }}
            </span>
          </label>
          <div class="dialog-actions">
            <button class="secondary-button" type="button" @click="endDialogOpen = false">
              返回</button
            ><button
              class="primary-button"
              type="button"
              :disabled="submitting || !endSpecimenIds.length"
              @click="finishFrozen"
            >
              确认结束
            </button>
          </div>
        </section>
      </div>
    </template>
  </section>
</template>

<style scoped>
.focused-frozen-page {
  display: grid;
  gap: 12px;
}
.frozen-header,
.panel-toolbar,
.frozen-footer-actions,
.dialog-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.frozen-header {
  min-height: 72px;
  padding: 8px 0;
  border-bottom: 1px solid var(--border-subtle, #dfe5ec);
}
.header-leading,
.frozen-header-meta,
.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.frozen-header h1,
.panel-toolbar h2 {
  margin: 0;
}
.frozen-header-meta {
  flex-wrap: wrap;
  justify-content: flex-end;
  font-size: 13px;
}
.frozen-header-meta strong {
  font-size: 18px;
}
.tat-status {
  border-radius: 999px;
  padding: 3px 8px;
  background: #edf5ef;
  color: #287344;
}
.tat-status.warning {
  background: #fff6df;
  color: #8a5b00;
}
.tat-status.overdue {
  background: #fff0ed;
  color: #a33a27;
}
.frozen-round-tabs {
  display: flex;
  align-items: stretch;
  gap: 8px;
  overflow-x: auto;
}
.frozen-round-tabs button {
  min-width: 150px;
  display: grid;
  gap: 3px;
  padding: 9px 12px;
  border: 1px solid var(--border-subtle, #dfe5ec);
  border-radius: 6px;
  background: #fff;
  text-align: left;
  color: inherit;
}
.frozen-round-tabs button.active {
  border-color: #2563eb;
  box-shadow: inset 0 -2px #2563eb;
}
.frozen-round-tabs small {
  color: #6b7280;
}
.frozen-material-layout {
  display: grid;
  grid-template-columns: minmax(210px, 0.35fr) minmax(420px, 1fr);
  gap: 16px;
}
.frozen-specimen-column,
.frozen-slide-column {
  min-width: 0;
}
.panel-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}
.panel-title-row h3 {
  margin: 0;
}
.specimen-row {
  width: 100%;
  display: grid;
  gap: 2px;
  padding: 10px;
  border: 1px solid transparent;
  border-bottom-color: var(--border-subtle, #e4e8ee);
  background: transparent;
  text-align: left;
  color: inherit;
}
.specimen-row.active {
  border-color: #bfdbfe;
  background: #eff6ff;
}
.specimen-row span,
.specimen-row small {
  color: #687386;
}
.dense-table-wrap {
  overflow-x: auto;
}
.dense-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.dense-table th,
.dense-table td {
  padding: 9px 10px;
  border-bottom: 1px solid var(--border-subtle, #e4e8ee);
  text-align: left;
  white-space: nowrap;
}
.status-pill.warning {
  background: #fff6df;
  color: #8a5b00;
}
.status-pill.success {
  background: #edf5ef;
  color: #287344;
}
.frozen-secondary-details {
  border-top: 1px solid var(--border-subtle, #e4e8ee);
  padding-top: 10px;
}
.frozen-secondary-details summary {
  cursor: pointer;
  font-weight: 600;
}
.secondary-details-grid {
  display: grid;
  gap: 10px;
  padding-top: 10px;
}
.inline-form {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.inline-form strong {
  margin-right: 4px;
}
.inline-form input {
  min-width: 120px;
  padding: 7px 9px;
  border: 1px solid #cbd5e1;
  border-radius: 4px;
}
.danger-button {
  border: 1px solid #d05a4b;
  color: #a33a27;
  background: #fff;
  border-radius: 4px;
  padding: 7px 10px;
}
.modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 20;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(15, 23, 42, 0.35);
}
.confirm-dialog {
  width: min(560px, 100%);
  max-height: 90vh;
  overflow: auto;
  display: grid;
  gap: 14px;
  padding: 22px;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 20px 60px rgba(15, 23, 42, 0.22);
}
.confirm-dialog h2 {
  margin: 0;
}
.end-round-group {
  display: grid;
  gap: 7px;
  padding: 10px;
  border: 1px solid #e4e8ee;
  border-radius: 5px;
}
.end-specimen-option {
  display: block;
  font-weight: 400;
}
@media (max-width: 900px) {
  .frozen-header,
  .panel-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }
  .frozen-material-layout {
    grid-template-columns: 1fr;
  }
  .toolbar-actions {
    flex-wrap: wrap;
  }
}
</style>
