<script setup lang="ts">
import { ref, watch } from 'vue';

import { friendlyError, formatDateTime } from '../uiText';
import { operationsRequest, type FrozenRoutineComparison } from '../v2OperationsApi';

const props = defineProps<{ caseId: string; open: boolean }>();
const emit = defineEmits<{ close: [] }>();

const loading = ref(false);
const error = ref('');
const comparison = ref<FrozenRoutineComparison | null>(null);

watch(
  () => [props.caseId, props.open],
  ([caseId, open]) => {
    if (open === true && typeof caseId === 'string' && caseId) void load(caseId);
  },
  { immediate: true },
);

async function load(caseId: string) {
  loading.value = true;
  error.value = '';
  try {
    comparison.value = await operationsRequest<FrozenRoutineComparison>(
      `/frozen/cases/${caseId}/routine-comparison`,
    );
  } catch (requestError) {
    comparison.value = null;
    error.value = friendlyError(requestError, '冰冻/石蜡对照暂时无法加载');
  } finally {
    loading.value = false;
  }
}

function reportLabel(status: string) {
  return status === 'EFFECTIVE'
    ? '正式报告'
    : status === 'WITHDRAWN'
      ? '已撤回'
      : status === 'NOT_DIAGNOSED'
        ? '尚未完成诊断'
        : '尚未签发';
}
</script>

<template>
  <div v-if="open" class="comparison-backdrop" @click.self="emit('close')">
    <section
      class="comparison-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="comparison-title"
    >
      <header class="comparison-header">
        <div>
          <p class="section-kicker">人工查看事实</p>
          <h2 id="comparison-title">冰冻 / 石蜡结果对照</h2>
        </div>
        <button class="text-button" type="button" @click="emit('close')">关闭</button>
      </header>

      <div v-if="loading" class="empty-state compact">正在加载对照结果…</div>
      <p v-else-if="error" class="feedback error" role="alert">{{ error }}</p>
      <div v-else-if="comparison" class="comparison-layout">
        <section class="comparison-side frozen-side" aria-label="冰冻结果">
          <header class="comparison-side-header">
            <span>冰冻</span>
            <strong>{{ comparison.frozenPathologyNo }}</strong>
          </header>
          <article
            v-for="round in comparison.frozenRounds"
            :key="round.roundId"
            class="comparison-round"
          >
            <div class="comparison-round-heading">
              <strong>第 {{ round.roundNo }} 轮</strong>
              <span>{{ reportLabel(round.reportStatus) }}</span>
            </div>
            <p class="muted">{{ round.specimenSummary || '本轮标本待补充' }}</p>
            <p class="comparison-diagnosis">{{ round.diagnosisText }}</p>
            <small>
              {{ round.doctor || '医生待补充' }} ·
              {{ round.signedAt ? formatDateTime(round.signedAt) : '时间待补充' }} · TAT
              {{ round.tatMinutes }} 分钟
            </small>
          </article>
          <p v-if="!comparison.frozenRounds.length" class="empty-state compact">
            暂无冰冻轮次记录。
          </p>
        </section>

        <section class="comparison-side routine-side" aria-label="常规石蜡结果">
          <header class="comparison-side-header">
            <span>常规石蜡</span>
            <strong>{{ comparison.routinePathologyNo }}</strong>
          </header>
          <article class="routine-result">
            <span class="status-pill">{{ reportLabel(comparison.routineReportStatus) }}</span>
            <p class="comparison-diagnosis">{{ comparison.routineDiagnosis }}</p>
            <small>
              {{ comparison.routineDoctor || '医生待补充' }} ·
              {{
                comparison.routineSignedAt
                  ? formatDateTime(comparison.routineSignedAt)
                  : '签发时间待补充'
              }}
            </small>
          </article>
        </section>
      </div>
      <p class="comparison-note">此页面仅并列展示正式业务事实，供医生或质控人员人工查看。</p>
    </section>
  </div>
</template>

<style scoped>
.comparison-backdrop {
  position: fixed;
  inset: 0;
  z-index: 30;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(15, 23, 42, 0.36);
}
.comparison-dialog {
  width: min(1120px, 100%);
  max-height: 90vh;
  overflow: auto;
  display: grid;
  gap: 16px;
  padding: 22px;
  border: 1px solid #dfe5ec;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 20px 60px rgba(15, 23, 42, 0.22);
}
.comparison-header,
.comparison-side-header,
.comparison-round-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.comparison-header h2 {
  margin: 0;
}
.comparison-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(280px, 0.8fr);
  gap: 16px;
  align-items: start;
}
.comparison-side {
  min-width: 0;
  border: 1px solid #dfe5ec;
  border-radius: 6px;
  background: #fbfcfe;
}
.frozen-side {
  max-height: 58vh;
  overflow: auto;
}
.comparison-side-header {
  position: sticky;
  top: 0;
  padding: 12px 14px;
  border-bottom: 1px solid #dfe5ec;
  background: #f4f7fb;
}
.comparison-round,
.routine-result {
  display: grid;
  gap: 8px;
  padding: 14px;
  border-bottom: 1px solid #e7ebf0;
}
.comparison-round:last-child {
  border-bottom: 0;
}
.comparison-round-heading span,
.comparison-round small,
.routine-result small {
  color: #687386;
}
.comparison-round-heading span {
  font-size: 13px;
}
.comparison-diagnosis {
  margin: 0;
  line-height: 1.6;
  white-space: pre-wrap;
}
.comparison-note {
  margin: 0;
  color: #687386;
  font-size: 13px;
}
@media (max-width: 760px) {
  .comparison-layout {
    grid-template-columns: 1fr;
  }
  .frozen-side {
    max-height: none;
  }
}
</style>
