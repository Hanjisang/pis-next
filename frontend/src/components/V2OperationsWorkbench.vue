<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';

type OperationsMode = 'frozen' | 'digital' | 'custody' | 'quality';

const props = defineProps<{ mode: OperationsMode; caseId?: string }>();

const loading = ref(false);
const submitting = ref(false);
const error = ref('');
const notice = ref('');
const caseId = ref(props.caseId ?? '');
const frozenWorkspace = ref<FrozenWorkspace | null>(null);
const selectedRoundId = ref('');
const specimenDraft = ref({
  specimenCode: '',
  specimenKindCode: 'TISSUE',
  collectionSite: '',
  collectionMethodCode: 'FRESH',
  labelCode: '',
});
const digitalSlides = ref<DigitalSlide[]>([]);
const digitalDraft = ref({
  blockId: '',
  slideId: '',
  bindingModeCode: 'MANUAL',
  viewerReference: '',
  sourcePlatform: 'SYNTHETIC-VIEWER',
});
const custodyDraft = ref({
  locationCode: '',
  locationName: '',
  locationKindCode: 'SHELF',
  blockIds: '',
  slideIds: '',
  borrowerReference: '',
  purpose: '',
  reason: '',
  batchReference: '',
  locationId: '',
  loanId: '',
});
const rules = ref<QcRule[]>([]);
const evaluations = ref<QcEvaluation[]>([]);
const statistics = ref<StatisticsSummary | null>(null);

const title = computed(() => {
  if (props.mode === 'frozen') return '冰冻工作区';
  if (props.mode === 'digital') return '数字切片绑定';
  if (props.mode === 'custody') return '归档与借阅';
  return '质控与统计';
});

function idempotencyKey(prefix: string) {
  return `${prefix}-${globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`}`;
}

type RequestOptions = { method?: string; headers?: Record<string, string>; body?: string };

async function request<T>(path: string, init: RequestOptions = {}) {
  const response = await fetch(`/api/v2${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init.headers ?? {}) },
  });
  const body = (await response.json()) as T | { message?: string; error_code?: string };
  if (!response.ok) {
    const failure = body as { message?: string; error_code?: string };
    throw new Error(
      `${failure.error_code ?? 'V2_REQUEST_FAILED'}: ${failure.message ?? '请求失败'}`,
    );
  }
  return body as T;
}

async function load() {
  loading.value = true;
  error.value = '';
  try {
    if (props.mode === 'frozen' && caseId.value.trim()) await loadFrozen();
    if (props.mode === 'digital' && caseId.value.trim()) await loadDigitalSlides();
    if (props.mode === 'quality') await loadQuality();
  } catch (requestError) {
    error.value = requestError instanceof Error ? requestError.message : '加载失败';
  } finally {
    loading.value = false;
  }
}

async function submit(operation: () => Promise<void>) {
  submitting.value = true;
  error.value = '';
  notice.value = '';
  try {
    await operation();
  } catch (requestError) {
    error.value = requestError instanceof Error ? requestError.message : '操作失败';
  } finally {
    submitting.value = false;
  }
}

async function loadFrozen() {
  frozenWorkspace.value = await request<FrozenWorkspace>(
    `/frozen/cases/${caseId.value.trim()}/workspace`,
  );
  selectedRoundId.value ||= frozenWorkspace.value.rounds.at(-1)?.roundId ?? '';
}

async function openRound() {
  await submit(async () => {
    await request(`/frozen/cases/${caseId.value.trim()}/rounds`, {
      method: 'POST',
      body: JSON.stringify({
        arrivalTime: new Date().toISOString(),
        idempotencyKey: idempotencyKey('v2-frozen-round'),
      }),
    });
    await loadFrozen();
    notice.value = '新的冰冻轮次已建立。';
  });
}

async function registerFrozenSpecimen() {
  await submit(async () => {
    await request(`/frozen/cases/${caseId.value.trim()}/specimens`, {
      method: 'POST',
      body: JSON.stringify({
        ...specimenDraft.value,
        idempotencyKey: idempotencyKey('v2-frozen-specimen'),
      }),
    });
    await loadFrozen();
    notice.value = '冰冻标本已登记并关联当前轮次。';
  });
}

async function createFrozenDiagnosis() {
  if (!selectedRoundId.value) return;
  await submit(async () => {
    await request(`/frozen/rounds/${selectedRoundId.value}/diagnosis`, {
      method: 'POST',
      body: JSON.stringify({ idempotencyKey: idempotencyKey('v2-frozen-diagnosis') }),
    });
    await loadFrozen();
    notice.value = '冰冻快速诊断已独立建立。';
  });
}

async function finishFrozen() {
  await submit(async () => {
    const result = await request<{ routineCaseId: string }>(
      `/frozen/cases/${caseId.value.trim()}/finish`,
      {
        method: 'POST',
        body: JSON.stringify({ idempotencyKey: idempotencyKey('v2-frozen-finish') }),
      },
    );
    await loadFrozen();
    notice.value = `冰冻已结束，常规病例已建立：${result.routineCaseId}`;
  });
}

async function loadDigitalSlides() {
  digitalSlides.value = await request<DigitalSlide[]>(
    `/digital-slides/cases/${caseId.value.trim()}`,
  );
}

async function createDigitalSlide() {
  await submit(async () => {
    await request('/digital-slides', {
      method: 'POST',
      body: JSON.stringify({
        ...digitalDraft.value,
        caseId: caseId.value.trim(),
        blockId: digitalDraft.value.blockId || null,
        slideId: digitalDraft.value.slideId || null,
      }),
    });
    await loadDigitalSlides();
    notice.value = '数字切片已绑定到病例。';
  });
}

async function rebindDigitalSlide(digitalSlideId: string) {
  await submit(async () => {
    await request(`/digital-slides/${digitalSlideId}/rebind`, {
      method: 'POST',
      body: JSON.stringify({
        blockId: digitalDraft.value.blockId || null,
        slideId: digitalDraft.value.slideId || null,
      }),
    });
    await loadDigitalSlides();
    notice.value = '数字切片绑定已更新。';
  });
}

async function unbindDigitalSlide(digitalSlideId: string) {
  await submit(async () => {
    await request(`/digital-slides/${digitalSlideId}/unbind`, { method: 'POST' });
    await loadDigitalSlides();
    notice.value = '数字切片已解除绑定。';
  });
}

function uuidList(value: string) {
  return value
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);
}

async function createLocation() {
  await submit(async () => {
    const result = await request<{ locationId: string }>('/custody/locations', {
      method: 'POST',
      body: JSON.stringify({
        parentId: null,
        locationCode: custodyDraft.value.locationCode,
        locationName: custodyDraft.value.locationName,
        locationKindCode: custodyDraft.value.locationKindCode,
      }),
    });
    custodyDraft.value.locationId = result.locationId;
    notice.value = `归档位置已建立：${result.locationId}`;
  });
}

async function archiveMaterials() {
  await submit(async () => {
    await request('/custody/archive', {
      method: 'POST',
      body: JSON.stringify({
        blockIds: uuidList(custodyDraft.value.blockIds),
        slideIds: uuidList(custodyDraft.value.slideIds),
        locationId: custodyDraft.value.locationId,
        reason: custodyDraft.value.reason || '常规归档',
        idempotencyKey: idempotencyKey('v2-custody-archive'),
      }),
    });
    notice.value = '材料已批量归档，归档位置已保留。';
  });
}

async function borrowMaterials() {
  await submit(async () => {
    const result = await request<{ loanId: string }>('/custody/loans', {
      method: 'POST',
      body: JSON.stringify({
        blockIds: uuidList(custodyDraft.value.blockIds),
        slideIds: uuidList(custodyDraft.value.slideIds),
        borrowerReference: custodyDraft.value.borrowerReference,
        purpose: custodyDraft.value.purpose,
      }),
    });
    custodyDraft.value.loanId = result.loanId;
    notice.value = `借阅已登记：${result.loanId}`;
  });
}

async function returnLoan() {
  await submit(async () => {
    await request(`/custody/loans/${custodyDraft.value.loanId}/return`, { method: 'POST' });
    notice.value = '借阅已归还，归档位置仍保留。';
  });
}

async function destroyMaterials() {
  await submit(async () => {
    await request('/custody/destruction', {
      method: 'POST',
      body: JSON.stringify({
        blockIds: uuidList(custodyDraft.value.blockIds),
        slideIds: uuidList(custodyDraft.value.slideIds),
        reason: custodyDraft.value.reason,
        batchReference: custodyDraft.value.batchReference,
      }),
    });
    notice.value = '材料销毁事实已记录，病例、诊断和报告未被删除。';
  });
}

async function loadQuality() {
  [rules.value, evaluations.value, statistics.value] = await Promise.all([
    request<QcRule[]>('/qc/rules'),
    request<QcEvaluation[]>('/qc/evaluations'),
    request<StatisticsSummary>('/statistics/summary'),
  ]);
}

async function evaluateQuality() {
  await submit(async () => {
    evaluations.value = await request<QcEvaluation[]>('/qc/evaluate', {
      method: 'POST',
      body: JSON.stringify({ caseId: caseId.value.trim() || null }),
    });
    notice.value = 'QC 已完成事实评估；提醒不会默认阻断签发。';
  });
}

onMounted(() => void load());

type FrozenWorkspace = {
  frozenCaseId: string;
  pathologyNo: string;
  businessTypeCode: string;
  rounds: Array<{
    roundId: string;
    roundNo: number;
    status: string;
    specimenIds: string[];
    totalRequiredSlides: number;
    completedRequiredSlides: number;
    productionComplete: boolean;
    diagnosisId?: string;
    diagnosisSignedTime?: string;
  }>;
  routineCaseId?: string;
};

type DigitalSlide = {
  digitalSlideId: string;
  caseId: string;
  blockId?: string;
  slideId?: string;
  bindingModeCode: string;
  statusCode: string;
  viewerReference: string;
  sourcePlatform: string;
};

type QcRule = {
  ruleCode: string;
  ruleName: string;
  metricCode: string;
  warningThreshold: number;
  overdueThreshold: number;
};
type QcEvaluation = {
  ruleCode: string;
  metricCode: string;
  value: number;
  statusCode: string;
  evaluatedAt: string;
};
type StatisticsSummary = {
  counts: Record<string, number>;
  businessTypeDistribution: Array<{ businessTypeCode: string; count: number }>;
};
</script>

<template>
  <section class="v2-operations" :aria-label="title">
    <header class="operations-header">
      <div>
        <p class="eyebrow">PIS V2 · CORE OPERATIONS</p>
        <h2>{{ title }}</h2>
        <p>关键事实在 V2 领域边界内完成，操作结果可追溯。</p>
      </div>
      <button type="button" :disabled="loading || submitting" @click="load">刷新</button>
    </header>

    <p v-if="loading" class="state-message" role="status">正在加载工作区…</p>
    <p v-if="error" class="state-message error" role="alert">{{ error }}</p>
    <p v-if="notice" class="state-message success" role="status">{{ notice }}</p>

    <section v-if="mode === 'frozen'" class="operation-card">
      <div class="field-row">
        <label>冰冻病例 ID<input v-model="caseId" placeholder="输入 F 病例内部 ID" /></label>
        <button type="button" @click="loadFrozen">读取冰冻工作区</button>
      </div>
      <div v-if="frozenWorkspace" class="frozen-summary">
        <strong>{{ frozenWorkspace.pathologyNo }}</strong>
        <span>{{ frozenWorkspace.rounds.length }} 个快速诊断轮次</span>
        <span v-if="frozenWorkspace.routineCaseId"
          >已转常规：{{ frozenWorkspace.routineCaseId }}</span
        >
      </div>
      <div class="operation-grid">
        <form class="form-panel" @submit.prevent="openRound">
          <h3>建立快速诊断轮次</h3>
          <p>上一轮已独立签发后，新材料自动进入新轮次。</p>
          <button type="submit" :disabled="submitting || !caseId">开始新轮次</button>
        </form>
        <form class="form-panel" @submit.prevent="registerFrozenSpecimen">
          <h3>登记本轮标本</h3>
          <label
            >标本号<input v-model="specimenDraft.specimenCode" required placeholder="F-A"
          /></label>
          <label>取材部位<input v-model="specimenDraft.collectionSite" /></label>
          <label>标签号<input v-model="specimenDraft.labelCode" /></label>
          <button type="submit" :disabled="submitting || !caseId">登记标本</button>
        </form>
        <div class="form-panel">
          <h3>轮次状态</h3>
          <select v-model="selectedRoundId" aria-label="冰冻轮次">
            <option value="" disabled>选择轮次</option>
            <option
              v-for="round in frozenWorkspace?.rounds ?? []"
              :key="round.roundId"
              :value="round.roundId"
            >
              第 {{ round.roundNo }} 轮 · {{ round.status }}
            </option>
          </select>
          <button
            type="button"
            :disabled="submitting || !selectedRoundId"
            @click="createFrozenDiagnosis"
          >
            建立快速诊断
          </button>
          <button type="button" :disabled="submitting || !caseId" @click="finishFrozen">
            结束冰冻并转常规
          </button>
        </div>
      </div>
      <ul v-if="frozenWorkspace" class="fact-list">
        <li v-for="round in frozenWorkspace.rounds" :key="round.roundId">
          第 {{ round.roundNo }} 轮：{{ round.completedRequiredSlides }}/{{
            round.totalRequiredSlides
          }}
          张切片完成 · {{ round.productionComplete ? '制片完成' : '制片中' }} ·
          {{ round.diagnosisId ? '已诊断' : '待诊断' }}
        </li>
      </ul>
    </section>

    <section v-else-if="mode === 'digital'" class="operation-card">
      <div class="field-row">
        <label>病例 ID<input v-model="caseId" placeholder="病例内部 ID" /></label
        ><button type="button" @click="loadDigitalSlides">读取数字切片</button>
      </div>
      <form class="operation-grid" @submit.prevent="createDigitalSlide">
        <label>Block ID（可选）<input v-model="digitalDraft.blockId" /></label>
        <label>Slide ID（可选）<input v-model="digitalDraft.slideId" /></label>
        <label>阅片器引用<input v-model="digitalDraft.viewerReference" required /></label>
        <label>来源平台<input v-model="digitalDraft.sourcePlatform" required /></label>
        <button type="submit" :disabled="submitting || !caseId">绑定数字切片</button>
      </form>
      <div class="fact-list">
        <article v-for="digital in digitalSlides" :key="digital.digitalSlideId" class="fact-card">
          <strong>{{ digital.digitalSlideId }}</strong>
          <span>{{ digital.statusCode }} · {{ digital.bindingModeCode }}</span>
          <small
            >Block {{ digital.blockId || '未绑定' }} · Slide
            {{ digital.slideId || '未绑定' }}</small
          >
          <div>
            <button type="button" @click="rebindDigitalSlide(digital.digitalSlideId)">
              按当前 ID 改绑</button
            ><button type="button" @click="unbindDigitalSlide(digital.digitalSlideId)">
              解除绑定
            </button>
          </div>
        </article>
      </div>
    </section>

    <section v-else-if="mode === 'custody'" class="operation-card">
      <div class="operation-grid">
        <form class="form-panel" @submit.prevent="createLocation">
          <h3>归档位置</h3>
          <label
            >位置编码<input v-model="custodyDraft.locationCode" required placeholder="ROOM-A-S1"
          /></label>
          <label>位置名称<input v-model="custodyDraft.locationName" required /></label>
          <label>位置层级<input v-model="custodyDraft.locationKindCode" required /></label>
          <button type="submit" :disabled="submitting">建立位置</button>
          <small>当前位置 ID：{{ custodyDraft.locationId || '尚未建立' }}</small>
        </form>
        <form class="form-panel" @submit.prevent="archiveMaterials">
          <h3>批量归档</h3>
          <label>Block IDs<input v-model="custodyDraft.blockIds" placeholder="逗号分隔" /></label>
          <label>Slide IDs<input v-model="custodyDraft.slideIds" placeholder="逗号分隔" /></label>
          <label>归档位置 ID<input v-model="custodyDraft.locationId" required /></label>
          <label>原因<input v-model="custodyDraft.reason" /></label>
          <button type="submit" :disabled="submitting">归档材料</button>
        </form>
        <form class="form-panel" @submit.prevent="borrowMaterials">
          <h3>借阅 / 归还</h3>
          <label>借阅人<input v-model="custodyDraft.borrowerReference" required /></label>
          <label>用途<input v-model="custodyDraft.purpose" required /></label>
          <button type="submit" :disabled="submitting">登记借阅</button>
          <label>Loan ID<input v-model="custodyDraft.loanId" /></label>
          <button type="button" :disabled="submitting || !custodyDraft.loanId" @click="returnLoan">
            登记归还
          </button>
        </form>
        <form class="form-panel" @submit.prevent="destroyMaterials">
          <h3>销毁事实</h3>
          <label>批次引用<input v-model="custodyDraft.batchReference" required /></label>
          <label>原因<input v-model="custodyDraft.reason" required /></label>
          <button type="submit" :disabled="submitting">记录销毁</button>
        </form>
      </div>
    </section>

    <section v-else class="operation-card">
      <div class="quality-actions">
        <button type="button" :disabled="submitting" @click="evaluateQuality">
          执行 QC 事实评估</button
        ><span>QC 提醒默认不阻断签发</span>
      </div>
      <div class="operation-grid">
        <div class="form-panel">
          <h3>基础统计</h3>
          <dl v-if="statistics">
            <template v-for="(value, key) in statistics.counts" :key="key">
              <dt>{{ key }}</dt>
              <dd>{{ value }}</dd>
            </template>
          </dl>
          <p v-else>暂无统计数据</p>
        </div>
        <div class="form-panel">
          <h3>QC 规则</h3>
          <ul class="fact-list">
            <li v-for="rule in rules" :key="rule.ruleCode">
              {{ rule.ruleName }} · warning {{ rule.warningThreshold }} · overdue
              {{ rule.overdueThreshold }}
            </li>
          </ul>
        </div>
      </div>
      <ul class="fact-list">
        <li
          v-for="evaluation in evaluations"
          :key="`${evaluation.ruleCode}-${evaluation.evaluatedAt}`"
        >
          {{ evaluation.metricCode }} · {{ evaluation.value }} · {{ evaluation.statusCode }}
        </li>
      </ul>
    </section>
  </section>
</template>

<style scoped>
.v2-operations {
  background: #f7faf8;
  border: 1px solid #cadbd2;
  border-radius: 24px;
  color: #193a30;
  margin-top: 28px;
  overflow: hidden;
}
.operations-header {
  align-items: end;
  background: linear-gradient(120deg, #172f4c, #2d6570);
  color: #f5fbf7;
  display: flex;
  justify-content: space-between;
  padding: 30px 34px;
}
.operations-header h2 {
  margin: 0 0 8px;
}
.operations-header p {
  color: #d4edf0;
  margin: 0;
}
.eyebrow {
  color: #9be0d1 !important;
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.14em;
  margin-bottom: 9px !important;
}
.operations-header button,
button {
  background: #fff;
  border: 1px solid #aac3b5;
  border-radius: 9px;
  color: #205440;
  cursor: pointer;
  font-weight: 750;
  min-height: 40px;
  padding: 8px 12px;
}
button:hover:not(:disabled) {
  background: #e9f5ed;
}
button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}
.state-message {
  margin: 0;
  padding: 14px 24px;
}
.state-message.error {
  background: #fff0ee;
  color: #a33d35;
}
.state-message.success {
  background: #e9f8ed;
  color: #1c7143;
}
.operation-card {
  background: #fff;
  margin: 24px;
  padding: 22px;
}
.field-row,
.operation-grid {
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
}
.field-row {
  align-items: end;
}
label {
  display: grid;
  font-size: 0.82rem;
  font-weight: 750;
  gap: 6px;
}
input,
select {
  background: #fff;
  border: 1px solid #b9ccc2;
  border-radius: 9px;
  color: #17322b;
  font: inherit;
  min-height: 40px;
  padding: 8px 10px;
}
.frozen-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  margin: 18px 0;
}
.frozen-summary span,
.quality-actions span,
small {
  color: #698276;
  font-size: 0.82rem;
}
.operation-grid {
  margin-top: 20px;
}
.form-panel,
.fact-card {
  border: 1px solid #d4e2dc;
  border-radius: 14px;
  display: grid;
  gap: 10px;
  padding: 16px;
}
.form-panel h3 {
  margin: 0;
}
.form-panel p {
  color: #698276;
  margin: 0;
}
.fact-list {
  display: grid;
  gap: 9px;
  list-style: none;
  margin: 18px 0 0;
  padding: 0;
}
.fact-card div {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.quality-actions {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
}
dl {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px 14px;
  margin: 0;
}
dt {
  color: #698276;
}
dd {
  font-weight: 800;
  margin: 0;
}
@media (max-width: 600px) {
  .operations-header {
    align-items: start;
    flex-direction: column;
    gap: 14px;
    padding: 24px;
  }
  .operation-card {
    margin: 14px;
    padding: 16px;
  }
}
</style>
