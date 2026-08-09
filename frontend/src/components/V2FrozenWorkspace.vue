<script setup lang="ts">
import { computed, ref, watch } from 'vue';

import type { V2AuthUser } from '../auth';
import { formatDateTime, friendlyError, idempotencyKey, statusName } from '../uiText';
import { getV2Case, type V2CaseResult } from '../v2Api';
import { operationsRequest, type FrozenWorkspace } from '../v2OperationsApi';
import V2CaseHeader from './V2CaseHeader.vue';

const props = defineProps<{ caseId?: string; authUser?: V2AuthUser | null }>();
const emit = defineEmits<{ navigate: [path: string] }>();

const lookupCaseId = ref(props.caseId ?? '');
const workspace = ref<FrozenWorkspace | null>(null);
const selectedRoundId = ref('');
const specimenDraft = ref({ specimenCode: '', collectionSite: '', labelCode: '' });
const routineCase = ref<V2CaseResult | null>(null);
const frozenCaseSummary = ref<V2CaseResult | null>(null);
const loading = ref(false);
const submitting = ref(false);
const error = ref('');
const notice = ref('');

const selectedRound = computed(
  () => workspace.value?.rounds.find((item) => item.roundId === selectedRoundId.value) ?? null,
);
const latestRound = computed(() => workspace.value?.rounds.at(-1) ?? null);
const nextSpecimenCreatesRound = computed(() =>
  ['SIGNED', 'ENDED'].includes(latestRound.value?.status ?? ''),
);
const canFinish = computed(() =>
  Boolean(latestRound.value?.diagnosisSignedTime && !workspace.value?.routineCaseId),
);
const canManageRounds = computed(
  () => props.authUser?.permissions.includes('P14-PERM-008') ?? false,
);
const canGross = computed(() => props.authUser?.permissions.includes('P14-PERM-013') ?? false);

watch(
  () => props.caseId,
  (value) => {
    lookupCaseId.value = value ?? '';
    if (value) void loadFrozen();
  },
  { immediate: true },
);

async function submit(operation: () => Promise<void>) {
  submitting.value = true;
  error.value = '';
  notice.value = '';
  try {
    await operation();
  } catch (requestError) {
    error.value = friendlyError(requestError, '冰冻操作未完成，请检查当前轮次后重试。');
  } finally {
    submitting.value = false;
  }
}

async function loadFrozen() {
  if (!lookupCaseId.value.trim()) return;
  loading.value = true;
  error.value = '';
  try {
    const frozenCaseId = lookupCaseId.value.trim();
    const [loadedWorkspace, loadedCase] = await Promise.all([
      operationsRequest<FrozenWorkspace>(`/frozen/cases/${frozenCaseId}/workspace`),
      getV2Case(frozenCaseId),
    ]);
    workspace.value = loadedWorkspace;
    frozenCaseSummary.value = loadedCase;
    selectedRoundId.value =
      workspace.value.rounds.find((item) => item.roundId === selectedRoundId.value)?.roundId ??
      workspace.value.rounds.at(-1)?.roundId ??
      '';
    if (workspace.value.routineCaseId)
      routineCase.value = await getV2Case(workspace.value.routineCaseId);
  } catch (requestError) {
    workspace.value = null;
    frozenCaseSummary.value = null;
    error.value = friendlyError(requestError, '未找到冰冻病例，请从冰冻登记或工作台进入。');
  } finally {
    loading.value = false;
  }
}

function startFirstRound() {
  if (!workspace.value) return;
  void submit(async () => {
    await operationsRequest(`/frozen/cases/${workspace.value!.frozenCaseId}/rounds`, {
      method: 'POST',
      body: JSON.stringify({
        arrivalTime: new Date().toISOString(),
        idempotencyKey: idempotencyKey('ux01-frozen-round'),
      }),
    });
    await loadFrozen();
    notice.value = '冰冻第 1 轮已开始。';
  });
}

function registerSpecimen() {
  if (!workspace.value || !specimenDraft.value.specimenCode.trim()) return;
  const createsRound = nextSpecimenCreatesRound.value;
  void submit(async () => {
    const result = await operationsRequest<{ roundId: string; roundNo: number }>(
      `/frozen/cases/${workspace.value!.frozenCaseId}/specimens`,
      {
        method: 'POST',
        body: JSON.stringify({
          specimenCode: specimenDraft.value.specimenCode.trim(),
          specimenKindCode: 'TISSUE',
          collectionSite: specimenDraft.value.collectionSite.trim(),
          collectionMethodCode: 'FRESH',
          labelCode: specimenDraft.value.labelCode.trim(),
          idempotencyKey: idempotencyKey('ux01-frozen-specimen'),
        }),
      },
    );
    specimenDraft.value = { specimenCode: '', collectionSite: '', labelCode: '' };
    await loadFrozen();
    selectedRoundId.value = result.roundId;
    notice.value = createsRound
      ? `上一轮已签发，已创建冰冻第 ${result.roundNo} 轮并加入新标本。`
      : `新标本已加入冰冻第 ${result.roundNo} 轮。`;
  });
}

function createDiagnosis() {
  if (!selectedRound.value) return;
  void submit(async () => {
    await operationsRequest(`/frozen/rounds/${selectedRound.value!.roundId}/diagnosis`, {
      method: 'POST',
      body: JSON.stringify({ idempotencyKey: idempotencyKey('ux01-frozen-diagnosis') }),
    });
    await loadFrozen();
    notice.value = `冰冻第 ${selectedRound.value?.roundNo ?? ''} 轮快速诊断已建立。`;
  });
}

function openMaterials() {
  if (!workspace.value || !selectedRound.value) return;
  emit(
    'navigate',
    `/v2/grossing/${workspace.value.frozenCaseId}?roundId=${selectedRound.value.roundId}`,
  );
}

function openDiagnosis() {
  if (!workspace.value || !selectedRound.value) return;
  emit(
    'navigate',
    `/v2/diagnosis/${workspace.value.frozenCaseId}?roundId=${selectedRound.value.roundId}`,
  );
}

function finishFrozen() {
  if (!workspace.value) return;
  void submit(async () => {
    const result = await operationsRequest<{ routineCaseId: string }>(
      `/frozen/cases/${workspace.value!.frozenCaseId}/finish`,
      {
        method: 'POST',
        body: JSON.stringify({ idempotencyKey: idempotencyKey('ux01-frozen-finish') }),
      },
    );
    routineCase.value = await getV2Case(result.routineCaseId);
    await loadFrozen();
    notice.value = `冰冻已结束，已创建冰剩常规病例 ${routineCase.value.caseNo}。`;
  });
}

function roundStatus(round: FrozenWorkspace['rounds'][number]) {
  if (round.diagnosisSignedTime) return '报告已签发';
  if (round.diagnosisId) return '诊断中';
  if (round.productionComplete) return '待诊断';
  if (round.totalRequiredSlides) return '制片中';
  if (round.specimens.length) return '待取材';
  return statusName(round.status);
}
</script>

<template>
  <section class="frozen-page" aria-label="冰冻工作区">
    <header class="page-heading compact-heading">
      <div>
        <p class="section-kicker">术中快速诊断</p>
        <h2>冰冻工作区</h2>
        <p>当前轮次、材料和下一步在同一个页面完成。</p>
      </div>
      <form class="case-lookup" @submit.prevent="loadFrozen">
        <label>打开冰冻病例 <input v-model="lookupCaseId" placeholder="输入冰冻病例标识" /></label>
        <button class="secondary-button" type="submit" :disabled="loading">
          {{ loading ? '读取中…' : '打开' }}
        </button>
      </form>
    </header>

    <p v-if="error" class="feedback error" role="alert">{{ error }}</p>
    <p v-if="notice" class="feedback success" role="status">{{ notice }}</p>
    <div v-if="loading" class="list-skeleton"><span></span><span></span><span></span></div>
    <div v-else-if="!workspace" class="empty-state workspace-panel">
      <strong>请打开一个冰冻病例</strong><span>从冰冻登记进入时会自动带入病例。</span>
    </div>

    <template v-else>
      <V2CaseHeader
        :case-id="workspace.frozenCaseId"
        :pathology-no="workspace.pathologyNo"
        :patient-reference="frozenCaseSummary?.patientReference ?? '当前病例'"
        :visit-reference="frozenCaseSummary?.visitReference"
        :business-type-code="workspace.businessTypeCode"
        :current-responsibility="latestRound ? `冰冻第 ${latestRound.roundNo} 轮` : '待开始冰冻'"
        :report-status="latestRound ? roundStatus(latestRound) : '待开始'"
        :progress="`${workspace.rounds.length} 轮${routineCase ? '，已生成冰剩常规' : ''}`"
        @open-case="emit('navigate', `/v2/cases/${workspace.frozenCaseId}`)"
      >
        <template #actions>
          <button
            v-if="routineCase"
            class="secondary-button"
            type="button"
            @click="emit('navigate', `/v2/grossing/${routineCase.caseId}`)"
          >
            查看冰剩常规
          </button>
        </template>
      </V2CaseHeader>

      <div v-if="!workspace.rounds.length" class="empty-state workspace-panel">
        <strong>冰冻尚未开始</strong><span>标本到达后开始第 1 轮。</span>
        <button
          v-if="canManageRounds"
          class="primary-button"
          type="button"
          :disabled="submitting"
          @click="startFirstRound"
        >
          开始第 1 轮
        </button>
        <span v-else class="muted">等待登记或技术人员开始本轮。</span>
      </div>

      <template v-else>
        <nav class="frozen-round-timeline" aria-label="冰冻轮次">
          <button
            v-for="round in workspace.rounds"
            :key="round.roundId"
            type="button"
            :class="{ current: round.roundId === selectedRoundId }"
            @click="selectedRoundId = round.roundId"
          >
            <span
              ><strong>冰冻第 {{ round.roundNo }} 轮</strong
              ><span v-if="round.diagnosisSignedTime" aria-label="已完成">✓</span></span
            >
            <span>{{ roundStatus(round) }}</span>
            <small
              >{{ formatDateTime(round.arrivalTime)
              }}<template v-if="round.diagnosisSignedTime">
                → {{ formatDateTime(round.diagnosisSignedTime) }}</template
              ></small
            >
          </button>
        </nav>

        <div class="frozen-workspace-grid">
          <section class="workspace-panel" aria-labelledby="round-materials-heading">
            <header class="panel-title-row">
              <div>
                <p class="section-kicker">第 {{ selectedRound?.roundNo }} 轮材料</p>
                <h3 id="round-materials-heading">
                  {{ selectedRound?.specimens.length ?? 0 }} 个标本
                </h3>
              </div>
              <span class="status-pill" :class="{ success: selectedRound?.productionComplete }"
                >{{ selectedRound?.completedRequiredSlides }}/{{
                  selectedRound?.totalRequiredSlides
                }}
                张玻片</span
              >
            </header>
            <ul v-if="selectedRound?.specimens.length" class="round-specimen-list">
              <li v-for="specimen in selectedRound.specimens" :key="specimen.specimenId">
                <span class="specimen-code">{{ specimen.specimenCode }}</span
                ><span
                  ><strong>{{ specimen.collectionSite || '未填写部位' }}</strong
                  ><small>{{ specimen.specimenNo }}</small></span
                >
              </li>
            </ul>
            <div v-else class="empty-state compact">
              <strong>本轮还没有标本</strong><span>登记新送检后显示在这里。</span>
            </div>
          </section>

          <section
            class="workspace-panel frozen-current-action"
            aria-labelledby="current-action-heading"
          >
            <header class="panel-title-row">
              <div>
                <p class="section-kicker">当前操作</p>
                <h3 id="current-action-heading">{{ roundStatus(selectedRound!) }}</h3>
              </div>
              <span class="status-pill current">第 {{ selectedRound?.roundNo }} 轮</span>
            </header>
            <div class="frozen-stage-list">
              <button
                type="button"
                :class="{ done: selectedRound?.grossingStartTime }"
                :disabled="!selectedRound?.productionComplete && !canGross"
                @click="openMaterials"
              >
                <span>1</span
                ><span
                  ><strong>取材与制片</strong
                  ><small>{{
                    selectedRound?.productionComplete
                      ? '玻片已全部完成'
                      : canGross
                        ? '进入本轮材料工作区'
                        : '等待技术人员完成'
                  }}</small></span
                ><b>{{ selectedRound?.productionComplete ? '✓' : canGross ? '进入' : '等待' }}</b>
              </button>
              <button
                type="button"
                :class="{ done: selectedRound?.diagnosisId }"
                :disabled="!selectedRound?.productionComplete"
                @click="selectedRound?.diagnosisId ? openDiagnosis() : createDiagnosis()"
              >
                <span>2</span
                ><span
                  ><strong>快速诊断</strong
                  ><small>{{
                    selectedRound?.diagnosisId ? '诊断已建立' : '制片完成后可诊断'
                  }}</small></span
                ><b>{{ selectedRound?.diagnosisId ? '进入' : '建立' }}</b>
              </button>
              <button
                type="button"
                :class="{ done: selectedRound?.diagnosisSignedTime }"
                :disabled="!selectedRound?.diagnosisId"
                @click="openDiagnosis"
              >
                <span>3</span
                ><span
                  ><strong>独立签发</strong
                  ><small>{{
                    selectedRound?.diagnosisSignedTime
                      ? `签发于 ${formatDateTime(selectedRound.diagnosisSignedTime)}`
                      : '进入报告预览与签发'
                  }}</small></span
                ><b>{{ selectedRound?.diagnosisSignedTime ? '✓' : '进入' }}</b>
              </button>
            </div>
          </section>
        </div>

        <section
          v-if="!workspace.routineCaseId && canManageRounds"
          class="workspace-panel frozen-submission-panel"
        >
          <header class="panel-title-row">
            <div>
              <p class="section-kicker">新送检</p>
              <h3>登记术中新送标本</h3>
              <p class="muted">
                {{
                  nextSpecimenCreatesRound
                    ? `第 ${latestRound!.roundNo} 轮已签发，登记后会自动创建第 ${latestRound!.roundNo + 1} 轮。`
                    : `当前轮次尚未签发，新标本会加入第 ${latestRound!.roundNo} 轮。`
                }}
              </p>
            </div>
          </header>
          <form class="field-grid three-columns" @submit.prevent="registerSpecimen">
            <label
              >标本编号 <input v-model="specimenDraft.specimenCode" required placeholder="例如 B"
            /></label>
            <label>送检部位 <input v-model="specimenDraft.collectionSite" required /></label>
            <label>标签号 <input v-model="specimenDraft.labelCode" /></label>
            <button class="primary-button" type="submit" :disabled="submitting">
              {{
                nextSpecimenCreatesRound
                  ? `创建第 ${latestRound!.roundNo + 1} 轮并登记`
                  : '加入当前轮次'
              }}
            </button>
          </form>
        </section>

        <div class="sticky-form-actions">
          <span v-if="routineCase" class="feedback success frozen-routine-link"
            >已创建冰剩常规病例：<strong>{{ routineCase.caseNo }}</strong
            ><button
              class="text-button"
              type="button"
              @click="emit('navigate', `/v2/grossing/${routineCase.caseId}`)"
            >
              进入常规流程 →
            </button></span
          >
          <span v-else class="muted">所有已送检轮次签发后结束冰冻。</span>
          <button
            v-if="!routineCase"
            class="primary-button"
            type="button"
            :disabled="!canFinish || submitting"
            @click="finishFrozen"
          >
            冰冻结束
          </button>
        </div>
      </template>
    </template>
  </section>
</template>
