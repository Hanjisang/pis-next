<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';

import type { V2AuthUser } from '../auth';
import {
  appendNavigationContext,
  workspaceBackLabel,
  workspaceBackTarget,
  type V2Route,
} from '../navigation';
import { businessTypeName, friendlyError, idempotencyKey, specimenKindName } from '../uiText';
import { getV2Case, type V2CaseResult } from '../v2Api';
import {
  completeV2Slides,
  correctV2SlideCode,
  createV2ExtraCytologySlide,
  generateV2RequiredCytologySlides,
  getV2MaterialTree,
  locateV2Material,
  performV2ProductionRework,
  printV2Slides,
  updateV2CytologyPreparation,
  type V2MaterialTree,
  type V2SlideNode,
} from '../v2MaterialApi';
import {
  completeV2TechnicalTraceBatch,
  recordV2HistologyException,
  type TechnicalTraceStageCode,
} from '../v2HistologyApi';
import { getV2ProductionWorkbench } from '../v2ProductionWorkbenchApi';

const caseId = defineModel<string>('caseId', { default: '' });
const props = withDefaults(
  defineProps<{
    authUser?: V2AuthUser | null;
    origin?: V2Route['origin'];
    queue?: string;
    returnTo?: string;
  }>(),
  { authUser: null, origin: 'direct', queue: 'CYTOLOGY_PRODUCTION', returnTo: '' },
);
const emit = defineEmits<{ navigate: [path: string] }>();

const loading = ref(true);
const busy = ref(false);
const error = ref('');
const notice = ref('');
const summary = ref<V2CaseResult | null>(null);
const materials = ref<V2MaterialTree | null>(null);
const selectedSpecimenId = ref('');
const selectedSpecimenIds = ref<string[]>([]);
const selectedSlideIds = ref<string[]>([]);
const preparationDraft = ref<Record<string, string>>({});
const extraReason = ref('');
const extraStain = ref('PAP');
const correctionCode = ref('');
const correctionReason = ref('');
const traceStage = ref<TechnicalTraceStageCode>('PREPARATION');
const traceStain = ref('PAP');
const traceNote = ref('');
const exceptionCode = ref('');
const exceptionNote = ref('');
const reworkType = ref<'REPREPARATION' | 'RESTAIN' | 'RESCAN'>('REPREPARATION');
const reworkReason = ref('');
const scanCode = ref('');
const selectedActionSlideId = ref('');

const specimens = computed(() => materials.value?.specimens ?? []);
const activeSpecimen = computed(
  () =>
    specimens.value.find((item) => item.specimenId === selectedSpecimenId.value) ??
    specimens.value[0],
);
const slides = computed<V2SlideNode[]>(() => activeSpecimen.value?.directSlides ?? []);
const allSlides = computed(() => specimens.value.flatMap((item) => item.directSlides));
const activeSlide = computed(
  () => allSlides.value.find((item) => item.slideId === selectedActionSlideId.value) ?? null,
);
const selectedSlides = computed(() =>
  allSlides.value.filter((slide) => selectedSlideIds.value.includes(slide.slideId)),
);
const can = (action: string) => Boolean(materials.value?.availableActions.includes(action));
const canGenerate = computed(() => can('GENERATE_CYTOLOGY_SLIDES') || can('CREATE_SLIDE'));
const canExtra = computed(() => can('CREATE_EXTRA_CYTOLOGY_SLIDE') || can('CREATE_SLIDE'));
const canComplete = computed(() => can('COMPLETE_SLIDE') || can('COMPLETE'));
const productionReady = computed(() => materials.value?.initialProductionComplete === true);
const progressLabel = computed(
  () =>
    `${materials.value?.initialCompletedCount ?? 0}/${materials.value?.initialRequiredCount ?? 0}`,
);
const backTarget = computed(() => workspaceBackTarget(props, caseId.value));
const backLabel = computed(() => workspaceBackLabel(props.origin));

const traceStages: Array<{ code: TechnicalTraceStageCode; label: string }> = [
  { code: 'PREPARATION', label: '制片' },
  { code: 'STAINING', label: '染色' },
  { code: 'COVERSLIPPING', label: '封片' },
];

const collectionMethodNames: Record<string, string> = {
  ASPIRATION: '穿刺/抽吸',
  BRUSHING: '刷取',
  COLLECTED: '采集',
  FRESH: '新鲜送检',
  LAVAGE: '灌洗',
  NATURAL: '自然排出',
  SUBMITTED: '送检',
  SURGERY: '手术取材',
  SURGICAL: '手术取材',
};

function cytologySpecimenKindLabel(code?: string | null) {
  if (!code) return '未标记';
  const label = specimenKindName(code);
  return label === code ? '其他细胞标本' : label;
}

function collectionMethodLabel(code?: string | null) {
  if (!code) return '未记录';
  if (collectionMethodNames[code]) return collectionMethodNames[code];
  return Array.from(code).some((character) => character.charCodeAt(0) > 127) ? code : '已记录';
}

function slideStatus(slide: V2SlideNode) {
  return slide.completed ? '已完成' : '待完成';
}

function slideLabel(slide: V2SlideNode) {
  return slide.printCount > 0 ? '已打印' : '未打印';
}

function selectSpecimen(id: string) {
  selectedSpecimenId.value = id;
  selectedSlideIds.value = [];
}

function toggleSpecimen(id: string, checked: boolean) {
  selectedSpecimenIds.value = checked
    ? [...new Set([...selectedSpecimenIds.value, id])]
    : selectedSpecimenIds.value.filter((item) => item !== id);
  if (checked) {
    selectedSpecimenId.value = id;
  } else if (selectedSpecimenId.value === id) {
    selectedSpecimenId.value = selectedSpecimenIds.value[0] ?? specimens.value[0]?.specimenId ?? '';
  }
}

function selectSlide(slide: V2SlideNode, checked: boolean) {
  selectedActionSlideId.value = slide.slideId;
  selectedSlideIds.value = checked
    ? [...new Set([...selectedSlideIds.value, slide.slideId])]
    : selectedSlideIds.value.filter((id) => id !== slide.slideId);
}

async function load() {
  if (!caseId.value) return;
  loading.value = true;
  error.value = '';
  try {
    const [caseResult, tree] = await Promise.all([
      getV2Case(caseId.value),
      getV2MaterialTree(caseId.value),
    ]);
    summary.value = caseResult;
    materials.value = tree;
    if (!specimens.value.some((item) => item.specimenId === selectedSpecimenId.value)) {
      selectedSpecimenId.value = specimens.value[0]?.specimenId ?? '';
    }
    const specimenIds = new Set(specimens.value.map((item) => item.specimenId));
    selectedSpecimenIds.value = selectedSpecimenIds.value.filter((id) => specimenIds.has(id));
    if (!selectedSpecimenIds.value.length && selectedSpecimenId.value) {
      selectedSpecimenIds.value = [selectedSpecimenId.value];
    }
    const ids = new Set(allSlides.value.map((slide) => slide.slideId));
    selectedSlideIds.value = selectedSlideIds.value.filter((id) => ids.has(id));
    if (!ids.has(selectedActionSlideId.value))
      selectedActionSlideId.value = allSlides.value[0]?.slideId ?? '';
    for (const specimen of specimens.value) {
      preparationDraft.value[specimen.specimenId] = specimen.preparationMethodCode ?? '';
    }
  } catch (requestError) {
    error.value = friendlyError(requestError, '细胞制片工作区暂时无法加载。');
  } finally {
    loading.value = false;
  }
}

async function run(action: () => Promise<void>) {
  busy.value = true;
  error.value = '';
  notice.value = '';
  try {
    await action();
  } catch (requestError) {
    error.value = friendlyError(requestError, '细胞制片操作未完成，请核对后重试。');
  } finally {
    busy.value = false;
  }
}

function generateRequired() {
  void run(async () => {
    const result = await generateV2RequiredCytologySlides({
      caseId: caseId.value,
      specimenIds: selectedSpecimenIds.value.length ? selectedSpecimenIds.value : undefined,
      idempotencyKey: idempotencyKey('fc03b-cytology-generate'),
    });
    notice.value = result.createdCount
      ? `已生成 ${result.createdCount} 张细胞玻片。`
      : '当前标本的按规则产出已经满足，没有重复生成。';
    await load();
  });
}

function createExtra() {
  const specimen = activeSpecimen.value;
  if (!specimen || !extraReason.value.trim()) {
    error.value = '请选择标本并填写额外制片原因。';
    return;
  }
  void run(async () => {
    const result = await createV2ExtraCytologySlide({
      caseId: caseId.value,
      specimenId: specimen.specimenId,
      stainCode: extraStain.value || undefined,
      reason: extraReason.value,
      idempotencyKey: idempotencyKey('fc03b-cytology-extra'),
    });
    notice.value = `已额外建立玻片 ${result.slideCode}。`;
    extraReason.value = '';
    await load();
  });
}

function savePreparation(specimen: V2MaterialTree['specimens'][number]) {
  void run(async () => {
    const result = await updateV2CytologyPreparation({
      caseId: caseId.value,
      specimenId: specimen.specimenId,
      preparationMethodCode: preparationDraft.value[specimen.specimenId] || undefined,
      expectedVersion: specimen.specimenConcurrencyVersion ?? 0,
    });
    notice.value = result.preparationMethodCode ? '制片方式已保存。' : '制片方式已清除。';
    await load();
  });
}

function printSelected() {
  if (!selectedSlides.value.length) return;
  void run(async () => {
    await printV2Slides({
      slideIds: selectedSlides.value.map((slide) => slide.slideId),
      reason: selectedSlides.value.some((slide) => slide.printCount > 0)
        ? '细胞玻片重打'
        : '细胞玻片打印',
      idempotencyKey: idempotencyKey('fc03b-cytology-print'),
    });
    notice.value = `已提交 ${selectedSlides.value.length} 张玻片标签打印。`;
    await load();
  });
}

function completeSelected() {
  const targets = selectedSlides.value.filter((slide) => !slide.completed);
  if (!targets.length) return;
  void run(async () => {
    await completeV2Slides({
      slides: targets.map((slide) => ({
        slideId: slide.slideId,
        expectedVersion: slide.concurrencyVersion,
      })),
      idempotencyKey: idempotencyKey('fc03b-cytology-complete'),
    });
    notice.value = `已完成 ${targets.length} 张玻片；技术记录不是完成前置条件。`;
    await load();
  });
}

function correctCode() {
  if (!activeSlide.value || !correctionCode.value.trim() || !correctionReason.value.trim()) return;
  void run(async () => {
    await correctV2SlideCode({
      slideId: activeSlide.value!.slideId,
      newSlideCode: correctionCode.value,
      reason: correctionReason.value,
      expectedVersion: activeSlide.value!.concurrencyVersion,
    });
    notice.value = '玻片编号已更正，玻片身份保持不变。';
    correctionCode.value = '';
    correctionReason.value = '';
    await load();
  });
}

function recordTrace() {
  if (!selectedSlides.value.length) return;
  void run(async () => {
    await completeV2TechnicalTraceBatch({
      targetKind: 'SLIDE',
      targetIds: selectedSlides.value.map((slide) => slide.slideId),
      stageCode: traceStage.value,
      stainCode: traceStage.value === 'STAINING' ? traceStain.value : undefined,
      note: traceNote.value || undefined,
    });
    notice.value = `已记录 ${selectedSlides.value.length} 项${traceStages.find((item) => item.code === traceStage.value)?.label}事实。`;
    traceNote.value = '';
    await load();
  });
}

function recordException() {
  if (!activeSlide.value || !exceptionCode.value.trim() || !exceptionNote.value.trim()) return;
  void run(async () => {
    await recordV2HistologyException({
      slideId: activeSlide.value!.slideId,
      phaseCode: traceStage.value,
      exceptionCode: exceptionCode.value,
      note: exceptionNote.value,
    });
    notice.value = '制片异常已记录，并进入异常/返工关注。';
    exceptionCode.value = '';
    exceptionNote.value = '';
    await load();
  });
}

function performRework() {
  if (!activeSlide.value || !reworkReason.value.trim()) return;
  void run(async () => {
    const result = await performV2ProductionRework({
      slideId: activeSlide.value!.slideId,
      reworkTypeCode: reworkType.value,
      reason: reworkReason.value,
      idempotencyKey: idempotencyKey('fc03b-cytology-rework'),
    });
    notice.value = result.replacementSlideId
      ? '已建立新的物理玻片，原玻片仍保留。'
      : '已记录同一玻片返工事实。';
    reworkReason.value = '';
    await load();
  });
}

function locate() {
  if (!scanCode.value.trim()) return;
  void run(async () => {
    const result = await locateV2Material(caseId.value, scanCode.value.trim());
    if (result.materialKind !== 'SLIDE') throw new Error('当前细胞工作区只定位标本或玻片。');
    selectedActionSlideId.value = result.materialId;
    const specimen = specimens.value.find((item) =>
      item.directSlides.some((slide) => slide.slideId === result.materialId),
    );
    if (specimen) selectedSpecimenId.value = specimen.specimenId;
    selectedSlideIds.value = [result.materialId];
    scanCode.value = '';
    notice.value = `已定位玻片 ${result.businessCode}。`;
  });
}

function navigateBack() {
  emit('navigate', backTarget.value);
}

async function completeAndNext() {
  if (!productionReady.value) return;
  await run(async () => {
    const workbench = await getV2ProductionWorkbench();
    const queue = workbench.queues.cytologyProduction;
    const index = queue.items.findIndex((item) => item.caseId === caseId.value);
    const next =
      queue.items.slice(index + 1).find((item) => item.caseId !== caseId.value) ??
      queue.items.find((item) => item.caseId !== caseId.value);
    if (!next) {
      emit('navigate', backTarget.value);
      return;
    }
    emit(
      'navigate',
      appendNavigationContext(next.deepLink, {
        origin: props.origin,
        queue: props.queue,
        returnTo: props.returnTo,
      }),
    );
  });
}

onMounted(load);
watch(caseId, load);
</script>

<template>
  <main class="cytology-production-workspace">
    <header class="cytology-workspace-header">
      <button type="button" class="text-button" @click="navigateBack">← {{ backLabel }}</button>
      <div>
        <p class="eyebrow">细胞制片</p>
        <h1>{{ summary?.caseNo || materials?.caseNo || '细胞病例' }}</h1>
        <p class="muted">
          {{ summary?.patientReference || '患者信息加载中' }} ·
          {{ businessTypeName(materials?.businessTypeCode) }}
        </p>
      </div>
      <div class="cytology-progress">
        <span>玻片完成</span>
        <strong>{{ progressLabel }}</strong>
      </div>
    </header>

    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <p v-if="notice" class="form-notice" role="status">{{ notice }}</p>
    <p v-if="loading" class="empty-state">正在加载细胞标本……</p>

    <template v-else>
      <section class="cytology-toolbar" aria-label="细胞制片操作">
        <button
          type="button"
          class="primary-button"
          :disabled="busy || !canGenerate"
          @click="generateRequired"
        >
          按规则生成玻片
        </button>
        <button type="button" :disabled="busy || !selectedSlides.length" @click="printSelected">
          打印/重打标签
        </button>
        <button
          type="button"
          :disabled="busy || !selectedSlides.some((slide) => !slide.completed) || !canComplete"
          @click="completeSelected"
        >
          完成制片
        </button>
        <button type="button" :disabled="busy || !productionReady" @click="completeAndNext">
          完成并下一项
        </button>
        <label class="scan-field"
          >扫码定位 <input v-model="scanCode" placeholder="标本号或玻片号" @keyup.enter="locate"
        /></label>
        <button type="button" class="text-button" :disabled="busy" @click="load">刷新</button>
      </section>

      <section class="cytology-layout">
        <aside class="specimen-panel" aria-label="细胞标本列表">
          <div class="section-heading">
            <h2>标本</h2>
            <span>{{ specimens.length }} 个 · 已选 {{ selectedSpecimenIds.length }} 个</span>
          </div>
          <div
            v-for="specimen in specimens"
            :key="specimen.specimenId"
            class="specimen-selection-row"
          >
            <input
              type="checkbox"
              :checked="selectedSpecimenIds.includes(specimen.specimenId)"
              :aria-label="`选择标本 ${specimen.specimenCode}`"
              @change="
                toggleSpecimen(specimen.specimenId, ($event.target as HTMLInputElement).checked)
              "
            />
            <button
              type="button"
              class="specimen-row"
              :class="{ active: specimen.specimenId === activeSpecimen?.specimenId }"
              @click="selectSpecimen(specimen.specimenId)"
            >
              <span class="specimen-code">{{ specimen.specimenCode }}</span>
              <strong>{{
                specimen.specimenName || cytologySpecimenKindLabel(specimen.specimenKindCode)
              }}</strong>
              <small
                >{{ specimen.collectionSite || '来源未记录' }} ·
                {{ specimen.directSlides.length }} 张玻片</small
              >
              <span class="specimen-progress"
                >{{ specimen.directSlides.filter((slide) => slide.completed).length }}/{{
                  specimen.directSlides.length
                }}</span
              >
            </button>
          </div>
          <p v-if="!specimens.length" class="empty-state">没有可处理的细胞标本。</p>
        </aside>

        <section class="slide-panel" aria-label="细胞玻片列表">
          <div class="section-heading">
            <div>
              <h2>{{ activeSpecimen?.specimenCode || '标本' }} · 玻片</h2>
              <span>{{ cytologySpecimenKindLabel(activeSpecimen?.specimenKindCode) }}</span>
            </div>
            <span>{{ slides.length }} 张</span>
          </div>
          <div v-if="activeSpecimen" class="preparation-line">
            <label
              >采集方式
              <span>{{ collectionMethodLabel(activeSpecimen.collectionMethodCode) }}</span></label
            >
            <label
              >制片方式
              <input
                v-model="preparationDraft[activeSpecimen.specimenId]"
                placeholder="液基 / 直接涂片 / 离心涂片"
              />
            </label>
            <button type="button" :disabled="busy" @click="savePreparation(activeSpecimen)">
              保存
            </button>
          </div>
          <div class="material-table-header" role="row">
            <span>选择</span><span>玻片</span><span>制片方式</span><span>染色项目</span
            ><span>状态</span><span>标签</span><span>操作</span>
          </div>
          <div
            v-for="slide in slides"
            :key="slide.slideId"
            class="material-row"
            :class="{ selected: selectedSlideIds.includes(slide.slideId) }"
          >
            <input
              type="checkbox"
              :checked="selectedSlideIds.includes(slide.slideId)"
              :aria-label="`选择玻片 ${slide.slideCode}`"
              @change="selectSlide(slide, ($event.target as HTMLInputElement).checked)"
            />
            <strong>{{ slide.slideCode }}</strong>
            <span>{{ slide.slideType || '未记录' }}</span>
            <span>{{ slide.stainCode || '未指定' }}</span>
            <span :class="slide.completed ? 'status-done' : 'status-pending'">{{
              slideStatus(slide)
            }}</span>
            <span>{{ slideLabel(slide) }}</span>
            <button type="button" class="text-button" @click="selectSlide(slide, true)">
              选择
            </button>
          </div>
          <p v-if="!slides.length" class="empty-state">
            状态：待生成。该标本尚未生成玻片，可使用“按规则生成玻片”。
          </p>
        </section>
      </section>

      <section class="secondary-operations">
        <details open>
          <summary>额外玻片与技术事实</summary>
          <div class="secondary-grid">
            <div class="operation-group">
              <h3>额外新增玻片</h3>
              <label>染色项目 <input v-model="extraStain" placeholder="PAP / HE" /></label>
              <label>原因 <input v-model="extraReason" placeholder="例如：备用玻片" /></label>
              <button type="button" :disabled="busy || !canExtra" @click="createExtra">
                新增物理玻片
              </button>
            </div>
            <div class="operation-group">
              <h3>技术记录</h3>
              <div class="stage-picker">
                <button
                  v-for="stage in traceStages"
                  :key="stage.code"
                  type="button"
                  :class="{ active: traceStage === stage.code }"
                  @click="traceStage = stage.code"
                >
                  {{ stage.label }}
                </button>
              </div>
              <label v-if="traceStage === 'STAINING'"
                >染色项目 <input v-model="traceStain"
              /></label>
              <label>说明 <input v-model="traceNote" placeholder="可选" /></label>
              <button type="button" :disabled="busy || !selectedSlides.length" @click="recordTrace">
                记录技术事实
              </button>
            </div>
            <div class="operation-group">
              <h3>异常与重新制片</h3>
              <label
                >异常类型
                <input v-model="exceptionCode" placeholder="标本量不足 / 染色异常 / 玻片破损"
              /></label>
              <label>说明 <input v-model="exceptionNote" placeholder="必填" /></label>
              <button type="button" :disabled="busy || !activeSlide" @click="recordException">
                记录异常
              </button>
              <label
                >返工类型
                <select v-model="reworkType">
                  <option value="REPREPARATION">重新制片</option>
                  <option value="RESTAIN">重染（同一玻片）</option>
                  <option value="RESCAN">重扫（数字切片）</option>
                </select>
              </label>
              <label>返工原因 <input v-model="reworkReason" placeholder="必填" /></label>
              <button type="button" :disabled="busy || !activeSlide" @click="performRework">
                执行返工
              </button>
            </div>
            <div class="operation-group">
              <h3>玻片编号更正</h3>
              <label>新编号 <input v-model="correctionCode" placeholder="例如 1-01" /></label>
              <label>原因 <input v-model="correctionReason" placeholder="必填" /></label>
              <button type="button" :disabled="busy || !activeSlide" @click="correctCode">
                保存更正
              </button>
            </div>
          </div>
        </details>
      </section>
    </template>
  </main>
</template>

<style scoped>
.cytology-production-workspace {
  padding: 20px 28px 34px;
  color: #172536;
}
.cytology-workspace-header {
  display: grid;
  grid-template-columns: 150px 1fr auto;
  align-items: center;
  gap: 20px;
  border-bottom: 1px solid #dce4ec;
  padding-bottom: 14px;
}
.cytology-workspace-header h1 {
  margin: 2px 0 4px;
  font-size: 24px;
}
.eyebrow {
  margin: 0;
  color: #387264;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
}
.muted {
  margin: 0;
  color: #6c7a89;
}
.cytology-progress {
  display: flex;
  align-items: baseline;
  gap: 10px;
  color: #6c7a89;
}
.cytology-progress strong {
  color: #172536;
  font-size: 20px;
}
.cytology-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 12px 0;
}
.cytology-toolbar button,
.operation-group button,
.preparation-line button {
  border: 1px solid #becbd7;
  border-radius: 6px;
  background: white;
  color: #24364a;
  padding: 7px 12px;
  cursor: pointer;
}
.cytology-toolbar .primary-button {
  background: #226b5a;
  border-color: #226b5a;
  color: white;
}
button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}
.text-button {
  border: 0 !important;
  background: transparent !important;
  color: #236a8a !important;
  padding: 4px 6px !important;
}
.scan-field {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 7px;
  color: #657384;
  font-size: 13px;
}
.scan-field input,
.preparation-line input,
.operation-group input,
.operation-group select {
  min-width: 120px;
  border: 1px solid #c4d0dc;
  border-radius: 5px;
  padding: 7px 8px;
}
.cytology-layout {
  display: grid;
  grid-template-columns: minmax(230px, 28%) 1fr;
  min-height: 380px;
  border: 1px solid #dce4ec;
  border-radius: 8px;
  overflow: hidden;
}
.specimen-panel {
  background: #f7fafc;
  border-right: 1px solid #dce4ec;
  padding: 12px;
}
.slide-panel {
  padding: 12px 16px;
  overflow-x: auto;
}
.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
}
.section-heading h2 {
  margin: 0;
  font-size: 16px;
}
.section-heading span {
  color: #708093;
  font-size: 12px;
}
.specimen-row {
  position: relative;
  display: grid;
  width: 100%;
  gap: 4px;
  margin: 4px 0;
  padding: 10px 12px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  text-align: left;
  color: inherit;
  cursor: pointer;
}
.specimen-selection-row {
  display: grid;
  grid-template-columns: 20px 1fr;
  align-items: stretch;
  gap: 4px;
}
.specimen-selection-row > input {
  align-self: start;
  margin: 16px 0 0 3px;
}
.specimen-row:hover,
.specimen-row.active {
  background: white;
  border-color: #a6c9bd;
}
.specimen-code {
  color: #246d5b;
  font-size: 12px;
  font-weight: 700;
}
.specimen-row small {
  color: #748395;
}
.specimen-progress {
  position: absolute;
  right: 12px;
  top: 12px;
  color: #657384;
  font-size: 12px;
}
.preparation-line {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0 12px;
  color: #667486;
  font-size: 13px;
}
.preparation-line label {
  display: flex;
  align-items: center;
  gap: 6px;
}
.material-table-header,
.material-row {
  display: grid;
  grid-template-columns: 50px minmax(90px, 1fr) minmax(100px, 1fr) minmax(90px, 1fr) 80px 70px 55px;
  align-items: center;
  gap: 8px;
  min-width: 650px;
}
.material-table-header {
  padding: 8px 6px;
  border-bottom: 1px solid #dce4ec;
  color: #718095;
  font-size: 12px;
}
.material-row {
  min-height: 48px;
  border-bottom: 1px solid #edf1f5;
  color: #334457;
  font-size: 13px;
}
.material-row.selected {
  background: #f0f8f5;
}
.material-row strong {
  color: #172536;
}
.status-done {
  color: #217050;
  font-weight: 600;
}
.status-pending {
  color: #9a6917;
  font-weight: 600;
}
.secondary-operations {
  margin-top: 14px;
  border-top: 1px solid #dce4ec;
  padding-top: 10px;
}
.secondary-operations summary {
  cursor: pointer;
  color: #425568;
  font-weight: 650;
}
.secondary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(180px, 1fr));
  gap: 12px;
  padding-top: 12px;
}
.operation-group {
  display: flex;
  flex-direction: column;
  gap: 7px;
  padding: 10px;
  border: 1px solid #e0e7ed;
  border-radius: 6px;
}
.operation-group h3 {
  margin: 0 0 2px;
  font-size: 13px;
}
.operation-group label {
  display: grid;
  gap: 4px;
  color: #667486;
  font-size: 12px;
}
.stage-picker {
  display: flex;
  gap: 4px;
}
.stage-picker button {
  border: 1px solid #ccd6df;
  background: white;
  border-radius: 4px;
  padding: 5px 7px;
  font-size: 12px;
}
.stage-picker button.active {
  background: #e6f2ee;
  border-color: #86b7a8;
  color: #1e644f;
}
.empty-state {
  color: #77879a;
  padding: 18px 6px;
}
.form-error,
.form-notice {
  padding: 9px 12px;
  border-radius: 5px;
  margin: 8px 0;
}
.form-error {
  background: #fff1ef;
  color: #a33b32;
}
.form-notice {
  background: #eef8f1;
  color: #2a6f4a;
}
@media (max-width: 900px) {
  .cytology-production-workspace {
    padding: 14px;
  }
  .cytology-workspace-header {
    grid-template-columns: 1fr auto;
  }
  .cytology-workspace-header .text-button {
    grid-column: 1 / -1;
    text-align: left;
  }
  .cytology-layout {
    grid-template-columns: 1fr;
  }
  .specimen-panel {
    border-right: 0;
    border-bottom: 1px solid #dce4ec;
  }
  .secondary-grid {
    grid-template-columns: repeat(2, minmax(180px, 1fr));
  }
  .scan-field {
    margin-left: 0;
  }
}
@media (max-width: 560px) {
  .secondary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
