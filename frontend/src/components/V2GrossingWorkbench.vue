<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';

import { currentRecorder, type V2AuthUser } from '../auth';
import {
  blockTypeName,
  friendlyError,
  formatDateTime,
  idempotencyKey,
  specimenKindName,
} from '../uiText';
import {
  associateV2Specimen,
  completeV2Grossing,
  createV2Block,
  createV2Grossing,
  getV2GrossingWorkspace,
  printV2Block,
  softDeleteV2Block,
  updateV2Grossing,
  type V2GrossingWorkspace,
} from '../v2MaterialApi';
import V2CaseHeader from './V2CaseHeader.vue';

const props = withDefaults(
  defineProps<{
    sourceType?: string;
    sourceReferenceId?: string;
    authUser?: V2AuthUser | null;
  }>(),
  { sourceType: 'INITIAL', sourceReferenceId: undefined, authUser: null },
);

const emit = defineEmits<{ navigate: [path: string] }>();
const caseId = defineModel<string>('caseId', { default: '' });
const lookupCaseId = ref(caseId.value);
const workspace = ref<V2GrossingWorkspace | null>(null);
const selectedSpecimenId = ref('');
const grossDescription = ref('');
const grossingInstruction = ref('');
const newBlockCode = ref('');
const busy = ref(false);
const loading = ref(false);
const error = ref('');
const notice = ref('');
const doctors = ref<Array<{ id: string; displayName: string; title?: string | null }>>([]);
const selectedDoctorId = ref(props.authUser?.doctor?.id ?? '');

const currentSpecimen = computed(
  () =>
    workspace.value?.specimens.find((item) => item.specimenId === selectedSpecimenId.value) ?? null,
);
const currentBlocks = computed(() => currentSpecimen.value?.blocks ?? []);
const currentDoctor = computed(
  () =>
    props.authUser?.doctor ??
    doctors.value.find((doctor) => doctor.id === selectedDoctorId.value) ??
    null,
);
const canEdit = computed(() =>
  Boolean(workspace.value?.grossing && !workspace.value.grossing.completedAt),
);
const canStart = computed(() =>
  Boolean(workspace.value && !workspace.value.grossing && selectedDoctorId.value),
);
const materialProgress = computed(() => {
  const slides =
    workspace.value?.specimens.flatMap((specimen) => [
      ...specimen.directSlides,
      ...specimen.blocks.flatMap((block) => block.slides),
    ]) ?? [];
  return `${slides.filter((slide) => slide.completed).length}/${slides.length}`;
});

watch(
  () => caseId.value,
  (value) => {
    lookupCaseId.value = value;
    if (value) void loadWorkspace();
  },
  { immediate: true },
);

async function run(action: () => Promise<void>) {
  busy.value = true;
  error.value = '';
  notice.value = '';
  try {
    await action();
  } catch (requestError) {
    error.value = friendlyError(requestError, '取材操作未完成，请刷新后重试。');
  } finally {
    busy.value = false;
  }
}

async function loadWorkspace() {
  if (!caseId.value.trim()) {
    workspace.value = null;
    return;
  }
  loading.value = true;
  error.value = '';
  try {
    workspace.value = await getV2GrossingWorkspace(
      caseId.value.trim(),
      props.sourceType,
      props.sourceReferenceId,
    );
    selectedSpecimenId.value ||= workspace.value.specimens[0]?.specimenId ?? '';
    if (workspace.value.grossing) {
      selectedDoctorId.value = workspace.value.grossing.grossingDoctorId;
      grossDescription.value = workspace.value.grossing.grossDescription;
      grossingInstruction.value = workspace.value.grossing.grossingInstruction ?? '';
    }
    setSuggestedBlockCode();
  } catch (requestError) {
    workspace.value = null;
    error.value = friendlyError(requestError, '未找到该病例，请检查病理号或病例标识。');
  } finally {
    loading.value = false;
  }
}

async function loadDoctors() {
  try {
    const response = await fetch('/api/v2/auth/doctors');
    if (!response.ok) return;
    doctors.value = (await response.json()) as typeof doctors.value;
    selectedDoctorId.value ||= props.authUser?.doctor?.id ?? doctors.value[0]?.id ?? '';
  } catch {
    doctors.value = [];
  }
}

function openCase() {
  caseId.value = lookupCaseId.value.trim();
  if (caseId.value) void loadWorkspace();
}

function selectSpecimen(specimenId: string) {
  selectedSpecimenId.value = specimenId;
  setSuggestedBlockCode();
}

function setSuggestedBlockCode() {
  const specimen = currentSpecimen.value;
  if (!specimen) {
    newBlockCode.value = '';
    return;
  }
  newBlockCode.value = `${specimen.specimenCode}${specimen.blocks.length + 1}`;
}

function beginGrossing() {
  if (!canStart.value || !workspace.value) return;
  void run(async () => {
    const created = await createV2Grossing({
      caseId: workspace.value!.caseId,
      sourceType: props.sourceType,
      sourceReferenceId: props.sourceReferenceId,
      grossDescription: grossDescription.value.trim() || '待补充大体描述',
      grossingInstruction: grossingInstruction.value.trim(),
      grossingDoctorId: selectedDoctorId.value,
      recorderId: currentRecorder(props.authUser ?? null),
      idempotencyKey: idempotencyKey('ux01-grossing-start'),
    });
    for (const specimen of workspace.value!.specimens) {
      await associateV2Specimen({
        grossingId: created.grossingId,
        specimenId: specimen.specimenId,
        materialDescription: specimen.specimenCode,
        idempotencyKey: idempotencyKey('ux01-grossing-specimen'),
      });
    }
    notice.value = `已开始取材，${workspace.value!.specimens.length} 个标本已加入本次取材。`;
    await loadWorkspace();
  });
}

async function saveDetails(showNotice = true) {
  const current = workspace.value?.grossing;
  if (!current || current.completedAt) return;
  const updated = await updateV2Grossing({
    grossingId: current.grossingId,
    grossDescription: grossDescription.value.trim(),
    grossingInstruction: grossingInstruction.value.trim(),
    grossingDoctorId: selectedDoctorId.value || current.grossingDoctorId,
    recorderId: currentRecorder(props.authUser ?? null) || current.recorderId,
    expectedVersion: current.concurrencyVersion,
    idempotencyKey: idempotencyKey('ux01-grossing-save'),
  });
  current.concurrencyVersion = updated.concurrencyVersion;
  if (showNotice) notice.value = '取材描述已保存。';
}

function saveGrossing() {
  void run(() => saveDetails());
}

function addBlock() {
  const specimen = currentSpecimen.value;
  const grossing = workspace.value?.grossing;
  if (!specimen || !grossing || !newBlockCode.value.trim()) return;
  void run(async () => {
    await createV2Block({
      grossingId: grossing.grossingId,
      specimenId: specimen.specimenId,
      blockCode: newBlockCode.value.trim(),
      blockType: props.sourceType === 'FROZEN_CONTEXT' ? 'FROZEN' : 'ROUTINE',
      idempotencyKey: idempotencyKey('ux01-block-create'),
    });
    notice.value = `蜡块 ${newBlockCode.value.trim()} 已建立。`;
    await loadWorkspace();
  });
}

function duplicateLastBlock() {
  setSuggestedBlockCode();
  addBlock();
}

function removeBlock(blockId: string, blockCode: string, version: number) {
  void run(async () => {
    await softDeleteV2Block({
      blockId,
      expectedVersion: version,
      reason: '取材工作区删除未完成蜡块',
      idempotencyKey: idempotencyKey('ux01-block-remove'),
    });
    notice.value = `蜡块 ${blockCode} 已作废，原记录仍保留。`;
    await loadWorkspace();
  });
}

function printBlock(blockId: string, blockCode: string) {
  void run(async () => {
    await printV2Block({
      blockId,
      reason: '取材工作区打印',
      idempotencyKey: idempotencyKey('ux01-block-print'),
    });
    notice.value = `蜡块 ${blockCode} 的标签已发送到当前打印机。`;
  });
}

function completeGrossing() {
  const grossing = workspace.value?.grossing;
  if (!grossing) return;
  void run(async () => {
    await saveDetails(false);
    const currentVersion =
      workspace.value?.grossing?.concurrencyVersion ?? grossing.concurrencyVersion;
    const result = await completeV2Grossing({
      grossingId: grossing.grossingId,
      expectedVersion: currentVersion,
      idempotencyKey: idempotencyKey('ux01-grossing-complete'),
    });
    notice.value = `取材已完成，已生成 ${result.createdSlideCount} 张待制玻片。`;
    await loadWorkspace();
  });
}

onMounted(() => void loadDoctors());
</script>

<template>
  <section class="grossing-layout" aria-label="病例取材工作区">
    <header class="page-heading compact-heading">
      <div>
        <p class="section-kicker">取材</p>
        <h2>病例取材工作区</h2>
        <p>在一个页面切换全部标本，快速建立蜡块并完成取材。</p>
      </div>
      <label v-if="!workspace?.grossing" class="compact-select">
        取材医生
        <select v-model="selectedDoctorId" aria-label="取材医生">
          <option value="" disabled>请选择</option>
          <option v-for="doctor in doctors" :key="doctor.id" :value="doctor.id">
            {{ doctor.displayName }}{{ doctor.title ? ` · ${doctor.title}` : '' }}
          </option>
        </select>
      </label>
      <span v-else-if="currentDoctor" class="status-pill success"
        >取材医生：{{ currentDoctor.displayName }}</span
      >
    </header>

    <div class="workspace-toolbar">
      <form class="case-lookup" @submit.prevent="openCase">
        <label>
          打开病例
          <input v-model="lookupCaseId" placeholder="输入病例标识或从待取材列表进入" />
        </label>
        <button class="secondary-button" type="submit" :disabled="loading">
          {{ loading ? '读取中…' : '打开' }}
        </button>
      </form>
    </div>

    <p v-if="error" class="feedback error" role="alert">{{ error }}</p>
    <p v-if="notice" class="feedback success" role="status">{{ notice }}</p>

    <div v-if="loading" class="list-skeleton" aria-label="正在读取病例">
      <span></span><span></span><span></span>
    </div>
    <div v-else-if="!workspace" class="empty-state workspace-panel">
      <strong>请打开一个待取材病例</strong>
      <span>从工作台进入病例时会自动带入，无需记忆内部编号。</span>
    </div>
    <template v-else>
      <V2CaseHeader
        :case-id="workspace.caseId"
        :pathology-no="workspace.caseNo"
        :patient-reference="workspace.patientReference"
        :visit-reference="workspace.visitReference"
        :business-type-code="workspace.businessTypeCode"
        :current-responsibility="
          currentDoctor ? `取材医生：${currentDoctor.displayName}` : '待安排取材'
        "
        :report-status="
          workspace.grossing?.completedAt
            ? '取材已完成'
            : workspace.grossing
              ? '取材进行中'
              : '待取材'
        "
        :progress="`${workspace.specimens.length} 个标本，玻片 ${materialProgress} 完成`"
        @open-case="emit('navigate', `/v2/cases/${workspace.caseId}`)"
      >
        <template #actions>
          <button
            class="secondary-button"
            type="button"
            @click="emit('navigate', `/v2/production/${workspace.caseId}`)"
          >
            查看制片
          </button>
        </template>
      </V2CaseHeader>

      <p v-if="!selectedDoctorId && !workspace.grossing" class="feedback warning">
        开始前请选择本次取材医生；当前登录人将自动记为记录员。
      </p>

      <div class="grossing-workspace-grid">
        <aside class="specimen-sidebar" aria-label="标本列表">
          <header class="panel-title-row">
            <div>
              <p class="section-kicker">标本</p>
              <h3>{{ workspace.specimens.length }} 个</h3>
            </div>
          </header>
          <div class="specimen-sidebar-list">
            <button
              v-for="specimen in workspace.specimens"
              :key="specimen.specimenId"
              type="button"
              :class="{ active: specimen.specimenId === selectedSpecimenId }"
              @click="selectSpecimen(specimen.specimenId)"
            >
              <strong>{{ specimen.specimenCode }} · {{ specimen.specimenNo }}</strong>
              <small>{{ specimen.blocks.length }} 个蜡块</small>
            </button>
          </div>
        </aside>

        <div class="grossing-editor">
          <section>
            <header class="panel-title-row">
              <div>
                <p class="section-kicker">当前标本</p>
                <h3>{{ currentSpecimen?.specimenCode }} · {{ currentSpecimen?.specimenNo }}</h3>
              </div>
              <span class="status-pill">{{
                specimenKindName(currentSpecimen?.specimenKindCode)
              }}</span>
            </header>
            <div class="field-grid">
              <label class="span-two">
                大体描述
                <textarea
                  v-model="grossDescription"
                  rows="5"
                  :readonly="Boolean(workspace.grossing?.completedAt)"
                  placeholder="记录大小、形态、颜色、切面等大体所见"
                ></textarea>
              </label>
              <label class="span-two">
                取材说明
                <textarea
                  v-model="grossingInstruction"
                  rows="2"
                  :readonly="Boolean(workspace.grossing?.completedAt)"
                  placeholder="可选：特殊取材要求或备注"
                ></textarea>
              </label>
            </div>
          </section>

          <section>
            <header class="panel-title-row">
              <div>
                <p class="section-kicker">蜡块</p>
                <h3>{{ currentBlocks.length }} 个蜡块</h3>
              </div>
              <div v-if="canEdit" class="input-action-row block-quick-entry">
                <label>
                  <span class="visually-hidden">新蜡块编号</span>
                  <input
                    v-model="newBlockCode"
                    aria-label="新蜡块编号"
                    placeholder="例如 A1"
                    @keydown.enter.prevent="addBlock"
                  />
                </label>
                <button class="primary-button" type="button" :disabled="busy" @click="addBlock">
                  + 蜡块
                </button>
              </div>
            </header>

            <div v-if="!currentBlocks.length" class="empty-state compact">
              <strong>当前标本还没有蜡块</strong>
              <span>开始取材后，输入编号并按 Enter 可快速新增。</span>
            </div>
            <div v-else class="block-quick-grid">
              <article v-for="block in currentBlocks" :key="block.blockId" class="block-chip">
                <header>
                  <strong>{{ block.blockCode }}</strong
                  ><span class="status-pill">{{ blockTypeName(block.blockType) }}</span>
                </header>
                <small class="muted">{{ block.slides.length }} 张玻片</small>
                <div class="inline-actions">
                  <button
                    class="text-button"
                    type="button"
                    @click="printBlock(block.blockId, block.blockCode)"
                  >
                    {{ block.slides.length ? '补打' : '打印' }}
                  </button>
                  <button
                    v-if="canEdit"
                    class="text-button danger-text"
                    type="button"
                    @click="removeBlock(block.blockId, block.blockCode, block.concurrencyVersion)"
                  >
                    删除
                  </button>
                </div>
              </article>
            </div>
            <button
              v-if="canEdit && currentBlocks.length"
              class="text-button"
              type="button"
              @click="duplicateLastBlock"
            >
              + 复制上一蜡块
            </button>
          </section>
        </div>
      </div>

      <div class="sticky-form-actions" aria-label="取材操作">
        <span class="muted">
          <template v-if="workspace.grossing">
            {{ workspace.grossing.grossingNo }} · 开始于
            {{ formatDateTime(workspace.grossing.startedAt) }}
          </template>
          <template v-else>尚未开始取材</template>
        </span>
        <div class="action-group">
          <button
            v-if="canEdit"
            class="secondary-button"
            type="button"
            :disabled="busy"
            @click="saveGrossing"
          >
            保存
          </button>
          <button
            v-if="canStart"
            class="primary-button"
            type="button"
            :disabled="busy"
            @click="beginGrossing"
          >
            开始取材
          </button>
          <button
            v-if="canEdit"
            class="primary-button"
            type="button"
            :disabled="busy"
            @click="completeGrossing"
          >
            完成取材
          </button>
          <span v-if="workspace.grossing?.completedAt" class="status-pill success">取材已完成</span>
        </div>
      </div>
    </template>
  </section>
</template>
