<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';

import type { V2AuthUser } from '../auth';
import { formatDateTime, friendlyError, idempotencyKey, statusName } from '../uiText';
import { getV2Case, type V2CaseResult } from '../v2Api';
import { getV2MaterialTree, printV2Slide, type V2MaterialTree } from '../v2MaterialApi';
import { operationsRequest, type FrozenWorkspace } from '../v2OperationsApi';
import V2CaseHeader from './V2CaseHeader.vue';
import V2HistoryDrawer from './V2HistoryDrawer.vue';

const props = defineProps<{
  caseId?: string;
  roundId?: string;
  authUser?: V2AuthUser | null;
}>();
const emit = defineEmits<{ navigate: [path: string] }>();

const lookupCaseId = ref(props.caseId ?? '');
const workspace = ref<FrozenWorkspace | null>(null);
const selectedRoundId = ref('');
const frozenCaseSummary = ref<V2CaseResult | null>(null);
const materialTree = ref<V2MaterialTree | null>(null);
const clock = ref(Date.now());
const loading = ref(false);
const submitting = ref(false);
const error = ref('');
const notice = ref('');
const historyDrawerOpen = ref(false);

const selectedRound = computed(
  () => workspace.value?.rounds.find((item) => item.roundId === selectedRoundId.value) ?? null,
);
const canManageRounds = computed(
  () => props.authUser?.permissions.includes('P14-PERM-008') ?? false,
);
const elapsedLabel = computed(() => {
  const round = selectedRound.value;
  if (!round) return '—';
  const start = Date.parse(round.arrivalTime);
  if (!Number.isFinite(start)) return '—';
  const totalSeconds = Math.max(0, Math.floor((clock.value - start) / 1000));
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return minutes + '分' + String(seconds).padStart(2, '0') + '秒';
});
const frozenSlides = computed(
  () =>
    (materialTree.value?.specimens ?? []).flatMap((specimen) => [
      ...specimen.blocks.flatMap((block) => block.slides),
      ...specimen.directSlides,
    ]) ?? [],
);

watch(
  () => [props.caseId, props.roundId],
  ([value]) => {
    lookupCaseId.value = value ?? '';
    if (value) void loadFrozen();
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
    materialTree.value = await getV2MaterialTree(frozenCaseId).catch(() => null);
    selectedRoundId.value =
      workspace.value.rounds.find(
        (item) => item.roundId === (props.roundId || selectedRoundId.value),
      )?.roundId ??
      workspace.value.rounds.at(-1)?.roundId ??
      '';
  } catch (requestError) {
    workspace.value = null;
    frozenCaseSummary.value = null;
    materialTree.value = null;
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

function openMaterials() {
  if (!workspace.value || !selectedRound.value) return;
  const destination = selectedRound.value.totalRequiredSlides
    ? `/v2/cases/${workspace.value.frozenCaseId}?focus=production&roundId=${selectedRound.value.roundId}`
    : `/v2/cases/${workspace.value.frozenCaseId}?focus=grossing&roundId=${selectedRound.value.roundId}`;
  emit('navigate', destination);
}

function openDiagnosis() {
  if (!workspace.value || !selectedRound.value) return;
  emit(
    'navigate',
    `/v2/cases/${workspace.value.frozenCaseId}?focus=diagnosis&roundId=${selectedRound.value.roundId}`,
  );
}

function printFrozenSlides() {
  if (!frozenSlides.value.length) return;
  void submit(async () => {
    await Promise.all(
      frozenSlides.value.map((slide) =>
        printV2Slide({
          slideId: slide.slideId,
          reason: '冰冻工作区打印',
          idempotencyKey: idempotencyKey('px03c-frozen-print') + '-' + slide.slideId,
        }),
      ),
    );
    notice.value = '冰冻玻片标签已发送到当前打印机。';
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
  <!-- Legacy layout retained as a reference for the focused redesign.
    <section class="frozen-page" aria-label="冰冻工作区">
      <header class="page-heading compact-heading">
        <div>
          <p class="section-kicker">术中快速诊断</p>
          <h2>冰冻工作区</h2>
          <p>当前轮次、材料和下一步在同一个页面完成。</p>
        </div>
        <form class="case-lookup" @submit.prevent="loadFrozen">
          <label
            >打开冰冻病例 <input v-model="lookupCaseId" placeholder="输入冰冻病例标识"
          /></label>
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
            <button class="secondary-button" type="button" @click="historyDrawerOpen = true">
              历史记录
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
      <V2HistoryDrawer
        :open="historyDrawerOpen"
        :case-id="frozenCaseSummary?.caseId || props.caseId"
        title="冰冻历史"
        target-label="冰冻工作台"
        @close="historyDrawerOpen = false"
      />
    </section>
  </div>

  -->

  <section class="focused-frozen-page" aria-label="冰冻工作区">
    <template v-if="!caseId">
      <header class="page-heading compact-heading">
        <div>
          <p class="section-kicker">冰冻</p>
          <h2>打开冰冻任务</h2>
          <p>从工作台进入时会自动带入病例和当前轮次。</p>
        </div>
        <form class="case-lookup" @submit.prevent="loadFrozen">
          <label>病理号<input v-model="lookupCaseId" placeholder="输入病例号" /></label>
          <button class="secondary-button" type="submit" :disabled="loading">打开</button>
        </form>
      </header>
      <div v-if="error" class="feedback error" role="alert">{{ error }}</div>
      <div v-else class="empty-state workspace-panel">
        <strong>请选择一个冰冻病例</strong><span>当前任务会在这里直接打开。</span>
      </div>
    </template>

    <template v-else>
      <p v-if="error" class="feedback error" role="alert">{{ error }}</p>
      <p v-if="notice" class="feedback success" role="status">{{ notice }}</p>
      <div v-if="loading" class="list-skeleton"><span></span><span></span><span></span></div>

      <template v-else-if="workspace">
        <V2CaseHeader
          :case-id="workspace.frozenCaseId"
          :pathology-no="workspace.pathologyNo"
          :patient-reference="frozenCaseSummary?.patientReference ?? '当前病例'"
          :visit-reference="frozenCaseSummary?.visitReference"
          :business-type-code="workspace.businessTypeCode"
          :current-work="selectedRound ? '冰冻第 ' + selectedRound.roundNo + ' 轮' : '冰冻'"
          :progress="
            selectedRound
              ? selectedRound.completedRequiredSlides +
                '/' +
                selectedRound.totalRequiredSlides +
                ' 张玻片'
              : '等待开始'
          "
          :report-status="selectedRound ? roundStatus(selectedRound) : '等待轮次'"
          @open-case="emit('navigate', '/v2/cases/' + workspace.frozenCaseId)"
        >
          <template #actions>
            <button class="secondary-button" type="button" @click="historyDrawerOpen = true">
              历史记录
            </button>
          </template>
        </V2CaseHeader>

        <div v-if="!workspace.rounds.length" class="empty-state workspace-panel">
          <strong>冰冻尚未开始</strong>
          <span>标本到达后开始第 1 轮。</span>
          <button
            v-if="canManageRounds"
            class="primary-button"
            type="button"
            :disabled="submitting"
            @click="startFirstRound"
          >
            开始第 1 轮
          </button>
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
              <strong>冰冻第 {{ round.roundNo }} 轮</strong>
              <span>{{ roundStatus(round) }}</span>
              <small>{{ formatDateTime(round.arrivalTime) }}</small>
            </button>
          </nav>

          <section class="workspace-panel frozen-focused-summary" aria-label="当前冰冻轮次">
            <div class="frozen-round-identity">
              <div>
                <p class="section-kicker">当前轮次</p>
                <h2>冰冻第 {{ selectedRound?.roundNo }} 轮</h2>
                <p>{{ selectedRound ? roundStatus(selectedRound) : '等待轮次' }}</p>
              </div>
              <div class="frozen-elapsed">
                <small>等待时间</small><strong>{{ elapsedLabel }}</strong>
              </div>
            </div>
            <div class="frozen-focused-grid">
              <section class="frozen-material-focus">
                <header class="panel-title-row">
                  <div>
                    <p class="section-kicker">标本</p>
                    <h3>{{ selectedRound?.specimens.length ?? 0 }} 个标本</h3>
                  </div>
                  <span class="status-pill"
                    >{{ selectedRound?.completedRequiredSlides ?? 0 }}/{{
                      selectedRound?.totalRequiredSlides ?? 0
                    }}</span
                  >
                </header>
                <ul v-if="selectedRound?.specimens.length" class="round-specimen-list">
                  <li v-for="specimen in selectedRound.specimens" :key="specimen.specimenId">
                    <strong>{{ specimen.specimenCode }}</strong
                    ><span>{{ specimen.collectionSite || '未填写部位' }}</span
                    ><small>{{ specimen.specimenNo }}</small>
                  </li>
                </ul>
                <div v-else class="empty-state compact"><strong>本轮还没有标本</strong></div>
              </section>
              <section class="frozen-slide-focus">
                <header class="panel-title-row">
                  <div>
                    <p class="section-kicker">玻片</p>
                    <h3>快速处理</h3>
                  </div>
                  <span class="muted">{{ frozenSlides.length }} 张</span>
                </header>
                <div v-if="frozenSlides.length" class="frozen-slide-list">
                  <div
                    v-for="slide in frozenSlides"
                    :key="slide.slideId"
                    class="material-slide-row"
                  >
                    <span
                      ><strong>{{ slide.slideCode }}</strong
                      ><small>{{ slide.slideType }}</small></span
                    ><span
                      :class="slide.completed ? 'status-pill success' : 'status-pill warning'"
                      >{{ slide.completed ? '已完成' : '待完成' }}</span
                    ><button
                      v-if="!slide.completed"
                      class="text-button"
                      type="button"
                      @click="
                        emit(
                          'navigate',
                          '/v2/cases/' +
                            workspace.frozenCaseId +
                            '?focus=production&roundId=' +
                            selectedRoundId,
                        )
                      "
                    >
                      处理
                    </button>
                  </div>
                </div>
                <div v-else class="empty-state compact">
                  <strong>当前还没有玻片</strong><span>先新增玻片并打印标签。</span>
                </div>
              </section>
            </div>
            <div class="focused-bottom-actions">
              <button class="secondary-button" type="button" @click="openMaterials">
                新增玻片
              </button>
              <button
                class="secondary-button"
                type="button"
                :disabled="!frozenSlides.length || submitting"
                @click="printFrozenSlides"
              >
                打印
              </button>
              <button
                class="primary-button"
                type="button"
                :disabled="!selectedRound?.productionComplete"
                @click="openDiagnosis"
              >
                完成并送诊
              </button>
            </div>
          </section>
        </template>

        <V2HistoryDrawer
          :open="historyDrawerOpen"
          :case-id="workspace.frozenCaseId"
          title="冰冻历史"
          target-label="当前冰冻轮次"
          @close="historyDrawerOpen = false"
        />
      </template>
    </template>
  </section>
</template>
