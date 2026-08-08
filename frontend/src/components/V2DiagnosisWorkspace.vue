<script setup lang="ts">
import { computed, ref, watch } from 'vue';

import {
  assignV2Diagnosis,
  claimV2Diagnosis,
  completeV2Responsibility,
  getV2DiagnosisWorkspace,
  reassignV2Diagnosis,
  saveV2Diagnosis,
  type V2DiagnosisWorkspace as DiagnosisWorkspace,
  type V2ResponsibilityRole,
} from '../v2DiagnosisApi';

type TemplateOption = { value: string; label: string };
type TemplateComponent = {
  code: string;
  label?: string;
  type: string;
  required?: boolean;
  default?: unknown;
  options?: string[] | TemplateOption[];
  readOnly?: boolean;
  unit?: string;
};

const caseId = defineModel<string>('caseId', {
  default: new URLSearchParams(window.location.search).get('caseId') ?? '',
});

const workspace = ref<DiagnosisWorkspace | null>(null);
const loading = ref(false);
const submitting = ref(false);
const error = ref('');
const notice = ref('');
const structuredData = ref('{}');
const microscopicDescription = ref('');
const diagnosisText = ref('');
const comment = ref('');
const structuredValues = ref<Record<string, unknown>>({});
const assignmentDoctor = ref('');
const assignmentReason = ref('');
const nextRole = ref<V2ResponsibilityRole | ''>('REVIEW');
const nextDoctorId = ref('p15-local-registration-actor');

const currentResponsibility = computed(() => workspace.value?.currentResponsibility);
const templateComponents = computed(() =>
  parseTemplateComponents(workspace.value?.templateVersion?.schemaDefinition),
);
const canEdit = computed(() => Boolean(currentResponsibility.value && workspace.value?.diagnosis));
const currentRole = computed<V2ResponsibilityRole | undefined>(
  () => currentResponsibility.value?.role,
);
const completionAllowed = computed(() => {
  if (!workspace.value || !currentRole.value) return false;
  return currentRole.value === 'INITIAL'
    ? workspace.value.actions.canCompleteInitial
    : currentRole.value === 'REVIEW'
      ? workspace.value.actions.canCompleteReview
      : workspace.value.actions.canCompleteAudit;
});

watch(caseId, () => void loadWorkspace(), { immediate: true });

async function loadWorkspace() {
  if (!caseId.value) {
    workspace.value = null;
    return;
  }
  loading.value = true;
  error.value = '';
  try {
    workspace.value = await getV2DiagnosisWorkspace(caseId.value);
    const diagnosis = workspace.value.diagnosis;
    structuredData.value = diagnosis?.structuredData ?? '{}';
    structuredValues.value = parseStructuredValues(structuredData.value);
    microscopicDescription.value = diagnosis?.microscopicDescription ?? '';
    diagnosisText.value = diagnosis?.diagnosisText ?? '';
    comment.value = diagnosis?.comment ?? '';
    if (workspace.value.currentResponsibility) {
      nextDoctorId.value = workspace.value.currentResponsibility.doctorId;
    }
  } catch (requestError) {
    error.value = requestError instanceof Error ? requestError.message : '工作区加载失败';
  } finally {
    loading.value = false;
  }
}

function parseTemplateComponents(schemaDefinition?: string): TemplateComponent[] {
  if (!schemaDefinition) return [];
  try {
    const schema = JSON.parse(schemaDefinition) as { components?: unknown };
    if (!Array.isArray(schema.components)) return [];
    return schema.components.filter((component): component is TemplateComponent => {
      if (!component || typeof component !== 'object') return false;
      const candidate = component as Record<string, unknown>;
      return typeof candidate.code === 'string' && typeof candidate.type === 'string';
    });
  } catch {
    return [];
  }
}

function parseStructuredValues(value: string): Record<string, unknown> {
  try {
    const parsed = JSON.parse(value) as unknown;
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
      ? (parsed as Record<string, unknown>)
      : {};
  } catch {
    return {};
  }
}

function componentType(component: TemplateComponent) {
  return component.type.toUpperCase();
}

function structuredValue(component: TemplateComponent) {
  return structuredValues.value[component.code] ?? component.default ?? '';
}

function updateStructuredValue(code: string, value: unknown) {
  structuredValues.value = { ...structuredValues.value, [code]: value };
  structuredData.value = JSON.stringify(structuredValues.value, null, 2);
}

function eventValue(event: Event) {
  return (event.target as HTMLInputElement).value;
}

function eventNumberValue(event: Event) {
  const value = eventValue(event);
  return value === '' ? null : Number(value);
}

function eventCheckedValue(event: Event) {
  return (event.target as HTMLInputElement).checked;
}

function eventMultiSelectValue(event: Event) {
  return Array.from((event.target as HTMLSelectElement).selectedOptions).map(
    (option) => option.value,
  );
}

function stringValue(component: TemplateComponent) {
  const value = structuredValue(component);
  return value === null || value === undefined ? '' : String(value);
}

function numberValue(component: TemplateComponent) {
  const value = structuredValue(component);
  return typeof value === 'number' ? String(value) : '';
}

function multiSelectValue(component: TemplateComponent) {
  const value = structuredValue(component);
  return Array.isArray(value) ? value.map(String) : [];
}

function templateOptions(component: TemplateComponent): TemplateOption[] {
  if (!Array.isArray(component.options)) return [];
  return component.options.map((option) =>
    typeof option === 'string' ? { value: option, label: option } : option,
  );
}

function requestKey(prefix: string) {
  const identity = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`;
  return `${prefix}-${identity}`;
}

async function claim() {
  await submit(async () => {
    await claimV2Diagnosis(caseId.value, requestKey('v2-diagnosis-claim'));
    await loadWorkspace();
    notice.value = '已建立初诊责任，可开始编辑。';
  });
}

async function assign() {
  await submit(async () => {
    await assignV2Diagnosis({
      caseId: caseId.value,
      doctorId: assignmentDoctor.value,
      reason: assignmentReason.value,
      idempotencyKey: requestKey('v2-diagnosis-assign'),
    });
    await loadWorkspace();
    notice.value = '已记录手工分配。';
  });
}

async function reassign() {
  await submit(async () => {
    await reassignV2Diagnosis({
      caseId: caseId.value,
      doctorId: assignmentDoctor.value,
      reason: assignmentReason.value,
      idempotencyKey: requestKey('v2-diagnosis-reassign'),
    });
    await loadWorkspace();
    notice.value = '已记录责任重分配，历史节点保持不变。';
  });
}

async function save() {
  if (!workspace.value?.diagnosis) return;
  await submit(async () => {
    await saveV2Diagnosis({
      diagnosisId: workspace.value!.diagnosis!.diagnosisId,
      structuredData: structuredData.value,
      microscopicDescription: microscopicDescription.value,
      diagnosisText: diagnosisText.value,
      comment: comment.value,
      expectedVersion: workspace.value!.diagnosis!.version,
      idempotencyKey: requestKey('v2-diagnosis-save'),
    });
    await loadWorkspace();
    notice.value = '诊断草稿已保存。';
  });
}

async function complete() {
  if (!workspace.value?.diagnosis || !currentResponsibility.value || !currentRole.value) return;
  const followingRole = nextRole.value || undefined;
  await submit(async () => {
    await completeV2Responsibility({
      diagnosisId: workspace.value!.diagnosis!.diagnosisId,
      role: currentRole.value!,
      responsibilityId: currentResponsibility.value!.responsibilityId,
      responsibilityExpectedVersion: currentResponsibility.value!.version,
      structuredData: structuredData.value,
      microscopicDescription: microscopicDescription.value,
      diagnosisText: diagnosisText.value,
      comment: comment.value,
      diagnosisExpectedVersion: workspace.value!.diagnosis!.version,
      nextRole: followingRole,
      nextDoctorId: followingRole ? nextDoctorId.value : undefined,
      nextReason: followingRole ? '由诊断工作区提交后续责任' : undefined,
      idempotencyKey: requestKey(`v2-diagnosis-complete-${currentRole.value!.toLowerCase()}`),
    });
    await loadWorkspace();
    notice.value =
      currentRole.value === 'AUDIT' && !followingRole
        ? '审核责任已完成，当前结果仅形成 READY_FOR_SIGN_OUT 投影。'
        : '当前责任已完成，已生成下一责任节点。';
  });
}

async function submit(operation: () => Promise<void>) {
  submitting.value = true;
  error.value = '';
  notice.value = '';
  try {
    await operation();
  } catch (requestError) {
    error.value = requestError instanceof Error ? requestError.message : '命令执行失败';
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <!-- eslint-disable vue/html-indent, vue/html-closing-bracket-newline, vue/max-attributes-per-line -->
  <section class="v2-diagnosis-workspace" aria-label="V2 Diagnosis 工作区">
    <header class="diagnosis-header">
      <div>
        <p class="workspace-kicker">V2 · DIAGNOSIS RESPONSIBILITY</p>
        <h2>Diagnosis Workspace</h2>
        <p class="workspace-caption">连续 Diagnosis、材料上下文与可审计责任链</p>
      </div>
      <label class="case-entry">
        <span>Case ID</span>
        <input v-model="caseId" aria-label="输入病例内部ID" placeholder="粘贴 Case ID" />
      </label>
    </header>

    <p v-if="loading" class="state-message" role="status" aria-busy="true">正在加载 V2 工作区…</p>
    <p v-else-if="error" class="state-message error" role="alert">{{ error }}</p>
    <p v-if="notice" class="state-message success" role="status">{{ notice }}</p>

    <div v-if="workspace" class="workspace-body">
      <section class="case-context" aria-label="病例与材料上下文">
        <div class="case-summary">
          <span class="label">病理号</span>
          <strong>{{ workspace.caseSummary.pathologyNo }}</strong>
          <span class="summary-meta"
            >{{ workspace.caseSummary.businessTypeCode }} ·
            {{ workspace.caseSummary.lifecycle }}</span
          >
        </div>
        <dl class="snapshot-list">
          <div>
            <dt>患者引用</dt>
            <dd>{{ workspace.patient.patientReference }}</dd>
          </div>
          <div>
            <dt>就诊引用</dt>
            <dd>{{ workspace.patient.visitReference || '未提供' }}</dd>
          </div>
          <div>
            <dt>申请项目</dt>
            <dd>{{ workspace.application.applicationItemCode }}</dd>
          </div>
        </dl>

        <div class="section-heading">
          <h3>Material Tree</h3>
          <span
            >{{ workspace.materialTree.initialCompletedCount }}/{{
              workspace.materialTree.initialRequiredCount
            }}
            初始切片完成</span
          >
        </div>
        <p class="tree-status" :class="{ ready: workspace.materialTree.initialProductionComplete }">
          {{
            workspace.materialTree.initialProductionComplete ? '初始材料已就绪' : '初始材料尚未完成'
          }}
        </p>
        <ul class="material-tree" aria-label="标本蜡块切片树">
          <li v-for="specimen in workspace.materialTree.specimens" :key="specimen.specimenId">
            <button
              type="button"
              class="tree-node specimen-node"
              :aria-label="`标本 ${specimen.specimenCode}`"
            >
              <span class="node-type">S</span>{{ specimen.specimenCode }} ·
              {{ specimen.specimenNo }}
            </button>
            <ul>
              <li v-for="block in specimen.blocks" :key="block.blockId">
                <span class="tree-node"
                  ><span class="node-type">B</span>{{ block.blockCode }} ·
                  {{ block.blockType }}</span
                >
                <ul>
                  <li v-for="slide in block.slides" :key="slide.slideId">
                    <span class="tree-node slide-node" :class="{ complete: slide.completed }">
                      <span class="node-type">L</span>{{ slide.slideCode }} ·
                      {{ slide.completed ? '已完成' : '待完成' }}
                    </span>
                  </li>
                </ul>
              </li>
              <li v-for="slide in specimen.directSlides" :key="slide.slideId">
                <span class="tree-node slide-node" :class="{ complete: slide.completed }">
                  <span class="node-type">L</span>{{ slide.slideCode }} · 直接来源
                </span>
              </li>
            </ul>
          </li>
        </ul>
      </section>

      <section class="diagnosis-editor" aria-label="Diagnosis 编辑区">
        <div class="editor-heading">
          <div>
            <span class="label">当前责任</span>
            <strong>{{
              currentResponsibility
                ? `${currentResponsibility.role} · ${currentResponsibility.doctorId}`
                : '尚未建立责任'
            }}</strong>
          </div>
          <span v-if="workspace.diagnosis" class="version-chip"
            >Diagnosis v{{ workspace.diagnosis.version }}</span
          >
          <span v-if="workspace.actions.readyForSignOut" class="ready-chip"
            >READY_FOR_SIGN_OUT · 待正式签发</span
          >
        </div>
        <div v-if="workspace.diagnosis" class="template-strip">
          <span>DiagnosisTemplateVersion</span>
          <strong>V{{ workspace.templateVersion?.versionNo }}</strong>
          <span>{{
            workspace.templateVersion?.status === 'PUBLISHED' ? '已发布快照' : '草稿'
          }}</span>
        </div>
        <p v-else class="empty-editor">
          病例尚未建立 Diagnosis。材料完成后可从公开池认领或由授权人员分配。
        </p>

        <fieldset v-if="workspace.diagnosis" :disabled="!canEdit || submitting">
          <legend>诊断内容</legend>
          <div v-if="templateComponents.length" class="structured-fields">
            <p class="field-hint">
              字段由当前 DiagnosisTemplateVersion 动态提供，复杂组件保留结构化数据兼容性。
            </p>
            <template v-for="component in templateComponents" :key="component.code">
              <label v-if="['TEXT', 'TEXTAREA'].includes(componentType(component))">
                {{ component.label || component.code }}
                <textarea
                  v-if="componentType(component) === 'TEXTAREA'"
                  :value="stringValue(component)"
                  :required="component.required"
                  :readonly="component.readOnly"
                  rows="3"
                  @input="updateStructuredValue(component.code, eventValue($event))"
                />
                <input
                  v-else
                  :value="stringValue(component)"
                  :required="component.required"
                  :readonly="component.readOnly"
                  type="text"
                  @input="updateStructuredValue(component.code, eventValue($event))"
                />
              </label>
              <label v-else-if="componentType(component) === 'NUMBER'">
                {{ component.label || component.code }}
                <input
                  :value="numberValue(component)"
                  :required="component.required"
                  :readonly="component.readOnly"
                  type="number"
                  @input="updateStructuredValue(component.code, eventNumberValue($event))"
                />
              </label>
              <label v-else-if="['SINGLE_SELECT', 'DICTIONARY'].includes(componentType(component))">
                {{ component.label || component.code }}
                <select
                  :value="stringValue(component)"
                  :required="component.required"
                  :disabled="component.readOnly"
                  @change="updateStructuredValue(component.code, eventValue($event))"
                >
                  <option value="">请选择</option>
                  <option
                    v-for="option in templateOptions(component)"
                    :key="option.value"
                    :value="option.value"
                  >
                    {{ option.label }}
                  </option>
                </select>
              </label>
              <label v-else-if="componentType(component) === 'MULTI_SELECT'">
                {{ component.label || component.code }}
                <select
                  multiple
                  :value="multiSelectValue(component)"
                  :disabled="component.readOnly"
                  @change="updateStructuredValue(component.code, eventMultiSelectValue($event))"
                >
                  <option
                    v-for="option in templateOptions(component)"
                    :key="option.value"
                    :value="option.value"
                  >
                    {{ option.label }}
                  </option>
                </select>
              </label>
              <label v-else-if="componentType(component) === 'BOOLEAN'" class="checkbox-field">
                <input
                  type="checkbox"
                  :checked="Boolean(structuredValue(component))"
                  :disabled="component.readOnly"
                  @change="updateStructuredValue(component.code, eventCheckedValue($event))"
                />
                {{ component.label || component.code }}
              </label>
              <div
                v-else-if="['GROUP', 'TITLE'].includes(componentType(component))"
                class="structured-group"
              >
                <strong>{{ component.label || component.code }}</strong>
              </div>
              <p v-else class="field-hint">
                {{ component.label || component.code }}（{{
                  component.type
                }}）暂使用结构化数据模型保留，专用编辑器待后续增强。
              </p>
            </template>
          </div>
          <label v-else
            >结构化诊断数据（JSON）<textarea v-model="structuredData" rows="4" spellcheck="false" />
          </label>
          <label>镜下所见<textarea v-model="microscopicDescription" rows="5" /></label>
          <label>诊断意见<textarea v-model="diagnosisText" rows="5" /></label>
          <label>备注<textarea v-model="comment" rows="3" /></label>
        </fieldset>
        <button
          v-if="workspace.diagnosis"
          class="primary-action"
          type="button"
          :disabled="!canEdit || submitting"
          @click="save"
        >
          保存 Diagnosis 草稿
        </button>

        <div class="responsibility-history" aria-label="责任链历史">
          <div class="section-heading">
            <h3>Responsibility Chain</h3>
            <span>{{ workspace.responsibilityChain.length }} 个节点</span>
          </div>
          <ol>
            <li
              v-for="item in workspace.responsibilityChain"
              :key="item.responsibilityId"
              :class="{ current: item.current }"
            >
              <strong>{{ item.sequence }} · {{ item.role }}</strong>
              <span>{{ item.doctorId }}</span>
              <em>{{ item.current ? '当前' : item.completedAt ? '已完成' : '已结束' }}</em>
            </li>
          </ol>
        </div>
      </section>
    </div>

    <footer v-if="workspace" class="command-bar" aria-label="诊断命令区">
      <div class="command-group">
        <button
          v-if="workspace.actions.canClaim"
          type="button"
          :disabled="submitting"
          @click="claim"
        >
          公开池自主认领
        </button>
        <button
          v-if="workspace.actions.canAssign"
          type="button"
          :disabled="submitting || !assignmentDoctor || !assignmentReason"
          @click="assign"
        >
          手工分配
        </button>
        <button
          v-if="workspace.actions.canReassign"
          type="button"
          :disabled="submitting || !assignmentDoctor || !assignmentReason"
          @click="reassign"
        >
          重分配
        </button>
        <input v-model="assignmentDoctor" aria-label="目标责任医生" placeholder="目标医生 ID" />
        <input
          v-model="assignmentReason"
          aria-label="分配或重分配原因"
          placeholder="责任变更原因"
        />
      </div>
      <div v-if="currentResponsibility" class="command-group completion-group">
        <label v-if="currentRole !== 'AUDIT'"
          >下一责任
          <select v-model="nextRole" aria-label="选择下一责任">
            <option value="">完成本节点</option>
            <option value="REVIEW">REVIEW</option>
            <option value="AUDIT">AUDIT</option>
          </select>
        </label>
        <input
          v-if="nextRole && currentRole !== 'AUDIT'"
          v-model="nextDoctorId"
          aria-label="下一责任医生"
          placeholder="下一医生 ID"
        />
        <button
          class="primary-action"
          type="button"
          :disabled="!completionAllowed || submitting"
          @click="complete"
        >
          完成 {{ currentRole }} 责任
        </button>
      </div>
      <div class="future-actions">
        <span>TechnicalOrder：{{ workspace.technicalOrder.status }}</span>
        <span>Report：{{ workspace.report.status }}</span>
      </div>
    </footer>
  </section>
  <!-- eslint-enable vue/html-indent, vue/html-closing-bracket-newline, vue/max-attributes-per-line -->
</template>

<style scoped>
.v2-diagnosis-workspace {
  background: #f8faf8;
  border: 1px solid #cbd9d2;
  border-radius: 24px;
  color: #17322b;
  margin-top: 28px;
  overflow: hidden;
}
.diagnosis-header {
  align-items: end;
  background: linear-gradient(120deg, #103e36, #1f6656);
  color: #f5fbf7;
  display: flex;
  justify-content: space-between;
  padding: 30px 34px;
}
.workspace-kicker {
  color: #99d6b5;
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.14em;
  margin: 0 0 9px;
}
h2,
h3,
p {
  margin-top: 0;
}
h2 {
  font-size: clamp(1.8rem, 4vw, 2.8rem);
  letter-spacing: -0.05em;
  margin-bottom: 6px;
}
.workspace-caption {
  color: #d7ece0;
  margin-bottom: 0;
}
.case-entry,
fieldset label {
  display: grid;
  gap: 7px;
}
.case-entry span,
.label {
  color: #a7c6b7;
  font-size: 0.75rem;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
input,
textarea,
select {
  background: #fff;
  border: 1px solid #b9ccc2;
  border-radius: 9px;
  color: #17322b;
  font: inherit;
  padding: 10px 12px;
}
.case-entry input {
  min-width: 280px;
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
.workspace-body {
  display: grid;
  grid-template-columns: minmax(280px, 0.8fr) minmax(0, 1.5fr);
}
.case-context,
.diagnosis-editor {
  min-width: 0;
  padding: 26px;
}
.case-context {
  background: #edf4f0;
  border-right: 1px solid #d2dfd8;
}
.case-summary {
  display: grid;
  gap: 5px;
}
.case-summary strong {
  font-size: 1.5rem;
}
.summary-meta {
  color: #60786d;
  font-size: 0.9rem;
}
.snapshot-list {
  display: grid;
  gap: 10px;
  margin: 22px 0;
}
.snapshot-list div {
  border-bottom: 1px solid #d4e0da;
  display: flex;
  gap: 12px;
  justify-content: space-between;
  padding-bottom: 8px;
}
dt {
  color: #667f73;
  font-size: 0.85rem;
}
dd {
  font-weight: 700;
  margin: 0;
  overflow-wrap: anywhere;
  text-align: right;
}
.section-heading,
.editor-heading {
  align-items: center;
  display: flex;
  justify-content: space-between;
  gap: 12px;
}
.section-heading h3 {
  font-size: 1rem;
  margin-bottom: 0;
}
.section-heading span {
  color: #698276;
  font-size: 0.82rem;
}
.tree-status {
  color: #9b5a1c;
  font-size: 0.85rem;
  margin: 12px 0;
}
.tree-status.ready {
  color: #1e7a4a;
}
.material-tree,
.material-tree ul {
  list-style: none;
  margin: 0;
  padding-left: 0;
}
.material-tree ul {
  border-left: 1px solid #b9ccc2;
  margin-left: 13px;
  padding-left: 12px;
}
.material-tree li {
  margin: 8px 0;
}
.tree-node {
  align-items: center;
  background: transparent;
  border: 0;
  color: #29483c;
  display: inline-flex;
  font-size: 0.9rem;
  gap: 7px;
  padding: 4px 0;
  text-align: left;
}
.specimen-node {
  font-weight: 800;
}
.node-type {
  align-items: center;
  background: #bcd8c9;
  border-radius: 5px;
  color: #215441;
  display: inline-flex;
  font-size: 0.68rem;
  font-weight: 900;
  height: 22px;
  justify-content: center;
  width: 22px;
}
.slide-node.complete {
  color: #24784c;
}
.slide-node.complete .node-type {
  background: #a9dfbc;
}
.editor-heading strong {
  font-size: 1.05rem;
}
.version-chip {
  background: #e1f2e6;
  border-radius: 999px;
  color: #24784c;
  font-size: 0.82rem;
  padding: 6px 10px;
}
.ready-chip {
  background: #fff2cb;
  border-radius: 999px;
  color: #7b5310;
  font-size: 0.75rem;
  font-weight: 800;
  padding: 7px 11px;
}
.template-strip {
  align-items: center;
  background: #f1f6f2;
  border: 1px solid #d5e3db;
  border-radius: 10px;
  display: flex;
  gap: 10px;
  margin: 20px 0;
  padding: 10px 12px;
}
.template-strip span {
  color: #698276;
  font-size: 0.82rem;
}
fieldset {
  border: 0;
  display: grid;
  gap: 14px;
  margin: 0;
  padding: 0;
}
.structured-fields {
  display: grid;
  gap: 12px;
}
.field-hint {
  color: #698276;
  font-size: 0.82rem;
  line-height: 1.5;
  margin: 0;
}
.structured-group {
  border-bottom: 1px solid #d8e5dd;
  color: #1d5b45;
  padding: 6px 0;
}
.checkbox-field {
  align-items: center;
  display: flex;
  gap: 8px;
}
.checkbox-field input {
  accent-color: #24784c;
}
legend {
  font-size: 1.05rem;
  font-weight: 800;
  margin-bottom: 12px;
}
textarea {
  min-height: 76px;
  resize: vertical;
}
fieldset:disabled {
  opacity: 0.62;
}
button {
  background: #fff;
  border: 1px solid #aac3b5;
  border-radius: 9px;
  color: #205440;
  cursor: pointer;
  font-weight: 750;
  min-height: 42px;
  padding: 9px 14px;
}
button:hover:not(:disabled) {
  background: #e9f5ed;
}
button:focus-visible,
input:focus-visible,
textarea:focus-visible,
select:focus-visible {
  outline: 3px solid #f1ad54;
  outline-offset: 2px;
}
button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}
.primary-action {
  background: #1e6a52;
  border-color: #1e6a52;
  color: #fff;
}
.diagnosis-editor > .primary-action {
  margin-top: 16px;
  width: 100%;
}
.empty-editor {
  background: #f3f7f3;
  border: 1px dashed #b8cec0;
  border-radius: 12px;
  color: #60786d;
  padding: 22px;
}
.responsibility-history {
  border-top: 1px solid #d5e3db;
  margin-top: 26px;
  padding-top: 20px;
}
.responsibility-history ol {
  display: grid;
  gap: 8px;
  list-style: none;
  margin: 14px 0 0;
  padding: 0;
}
.responsibility-history li {
  align-items: center;
  border-left: 3px solid #c6d7cd;
  display: grid;
  gap: 2px;
  grid-template-columns: 1fr 1fr auto;
  padding: 9px 12px;
}
.responsibility-history li.current {
  background: #e8f6ec;
  border-left-color: #2f8a57;
}
.responsibility-history span,
.responsibility-history em {
  color: #6b8377;
  font-size: 0.82rem;
  font-style: normal;
}
.command-bar {
  align-items: center;
  background: #e5f0e9;
  border-top: 1px solid #cbd9d2;
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  justify-content: space-between;
  padding: 18px 26px;
}
.command-group {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.command-group input {
  min-width: 150px;
}
.completion-group label {
  align-items: center;
  color: #567166;
  display: flex;
  font-size: 0.82rem;
  gap: 7px;
}
.future-actions {
  color: #6b8377;
  display: flex;
  flex-wrap: wrap;
  font-size: 0.8rem;
  gap: 12px;
}
@media (max-width: 860px) {
  .diagnosis-header {
    align-items: start;
    flex-direction: column;
    gap: 20px;
  }
  .case-entry,
  .case-entry input {
    width: 100%;
  }
  .workspace-body {
    grid-template-columns: 1fr;
  }
  .case-context {
    border-bottom: 1px solid #d2dfd8;
    border-right: 0;
  }
}
@media (max-width: 560px) {
  .diagnosis-header,
  .case-context,
  .diagnosis-editor,
  .command-bar {
    padding: 20px;
  }
  .responsibility-history li {
    align-items: start;
    grid-template-columns: 1fr;
  }
  .future-actions {
    width: 100%;
  }
}
</style>
