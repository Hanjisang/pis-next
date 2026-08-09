<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';

import type { V2AuthUser } from '../auth';
import {
  businessTypeName,
  friendlyError,
  formatDateTime,
  responsibilityName,
  statusName,
} from '../uiText';
import {
  assignV2Diagnosis,
  claimV2Diagnosis,
  completeV2Responsibility,
  createV2TechnicalOrder,
  getV2DiagnosisWorkspace,
  getV2FrozenRoundDiagnosisWorkspace,
  getV2ReportPdfUrl,
  getV2ReportPreview,
  getV2TechnicalProjects,
  reassignV2Diagnosis,
  saveV2Diagnosis,
  signOutV2Report,
  supplementV2Report,
  withdrawV2Report,
  type V2DiagnosisWorkspace as DiagnosisWorkspace,
  type V2ResponsibilityRole,
  type V2TechnicalProject,
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
type TechnicalTargetType = 'CASE' | 'SPECIMEN' | 'BLOCK' | 'SLIDE';
type TechnicalDraft = {
  projectId: string;
  quantity: number;
  targetType: TechnicalTargetType;
  targetId: string;
  note: string;
};
type DoctorOption = {
  id: string;
  doctorCode: string;
  displayName: string;
  title?: string | null;
  department?: string | null;
};
type PoolCase = { caseId: string; pathologyNo: string; businessTypeCode: string };
type ContextSection = 'application' | 'specimens' | 'blocks' | 'slides' | 'digital' | 'history';
type ReportPreviewDocument = {
  case?: {
    pathologyNo?: string;
    patientReference?: string;
    visitReference?: string;
    businessTypeCode?: string;
  };
  diagnosis?: { microscopicDescription?: string; diagnosisText?: string; comment?: string };
  material?: Array<{
    specimenCode?: string;
    blockCode?: string;
    slideCode?: string;
    slideType?: string;
  }>;
  responsibility?: Array<{ role?: string; doctorId?: string; completedAt?: string }>;
  technicalResults?: Array<{
    orderNo?: string;
    items?: Array<{
      projectCode?: string;
      completedCount?: number;
      expectedCount?: number;
      status?: string;
    }>;
  }>;
};

const caseId = defineModel<string>('caseId', { default: '' });
const props = withDefaults(
  defineProps<{ frozenRoundId?: string; authUser?: V2AuthUser | null }>(),
  { frozenRoundId: undefined, authUser: null },
);
const emit = defineEmits<{ navigate: [path: string] }>();

const workspace = ref<DiagnosisWorkspace | null>(null);
const publicPool = ref<PoolCase[]>([]);
const doctors = ref<DoctorOption[]>([]);
const loading = ref(false);
const submitting = ref(false);
const error = ref('');
const notice = ref('');
const structuredData = ref('{}');
const microscopicDescription = ref('');
const diagnosisText = ref('');
const comment = ref('');
const structuredValues = ref<Record<string, unknown>>({});
const activeContext = ref<ContextSection>('specimens');
const assignmentDoctor = ref('');
const assignmentReason = ref('');
const nextRole = ref<V2ResponsibilityRole | ''>('REVIEW');
const nextDoctorId = ref('');
const technicalProjects = ref<V2TechnicalProject[]>([]);
const technicalRequiredBeforeSignOut = ref(true);
const technicalPanelOpen = ref(false);
const technicalDrafts = ref<TechnicalDraft[]>([
  { projectId: '', quantity: 1, targetType: 'CASE', targetId: '', note: '' },
]);
const reportPreview = ref<{
  renderedContent: string;
  blockingReasons: string[];
  valid: boolean;
} | null>(null);
const previewOpen = ref(false);
const withdrawalReason = ref('');
const supplementalContent = ref('');
const supplementalOpen = ref(false);
const withdrawalOpen = ref(false);

const currentResponsibility = computed(() => workspace.value?.currentResponsibility);
const currentRole = computed<V2ResponsibilityRole | undefined>(
  () => currentResponsibility.value?.role,
);
const responsibilitySummary = computed(() => {
  if (currentResponsibility.value) {
    return `${responsibilityName(currentResponsibility.value.role)} · ${doctorName(currentResponsibility.value.doctorId)}`;
  }
  return workspace.value?.responsibilityChain.length ? '责任已完成' : '待接诊';
});
const editorTitle = computed(() => {
  if (currentRole.value) return `${responsibilityName(currentRole.value)}诊断`;
  if (workspace.value?.reports.some((report) => report.status === 'EFFECTIVE')) {
    return '已签发诊断';
  }
  return workspace.value?.actions.readyForSignOut ? '待签发诊断' : '等待接诊';
});
const templateComponents = computed(() =>
  parseTemplateComponents(workspace.value?.templateVersion?.schemaDefinition),
);
const canEdit = computed(() => {
  if (!workspace.value?.diagnosis || !currentRole.value) return false;
  if (currentRole.value === 'INITIAL') return workspace.value.actions.canCompleteInitial;
  if (currentRole.value === 'REVIEW') return workspace.value.actions.canCompleteReview;
  return workspace.value.actions.canCompleteAudit;
});
const completionAllowed = computed(() => Boolean(canEdit.value));
const productionSummary = computed(() => {
  const tree = workspace.value?.materialTree;
  if (!tree) return '未读取';
  if (props.frozenRoundId) {
    const completed = allSlides.value.filter((slide) => Boolean(slide.completedAt)).length;
    return `${completed}/${allSlides.value.length} 张完成`;
  }
  return `${tree.initialCompletedCount}/${tree.initialRequiredCount} 张完成`;
});
const technicalReturnedCount = computed(
  () =>
    (workspace.value?.technicalOrders ?? [])
      .flatMap((order) => order.items)
      .filter((item) => item.result).length,
);
const molecularResults = computed(() => workspace.value?.molecularResults ?? []);

function molecularResultSummary(resultData: string) {
  try {
    const result = JSON.parse(resultData) as Record<string, unknown>;
    if (typeof result.conclusion === 'string' && result.conclusion.trim()) {
      return result.conclusion.trim();
    }
    if (typeof result.mutationDetected === 'boolean') {
      return result.mutationDetected ? '检出相关变异' : '未检出相关变异';
    }
    const firstReadableValue = Object.values(result).find(
      (value) => typeof value === 'string' || typeof value === 'number',
    );
    if (firstReadableValue !== undefined) return String(firstReadableValue);
  } catch {
    // Historical adapters may have stored a plain-text conclusion.
    if (resultData.trim() && !resultData.trim().startsWith('{')) return resultData.trim();
  }
  return '结构化结果已完成';
}
const reportStatus = computed(() => {
  const reports = workspace.value?.reports ?? [];
  if (reports.some((report) => report.status === 'EFFECTIVE')) return '已签发';
  if (workspace.value?.actions.readyForSignOut) return '待签发';
  return workspace.value?.diagnosis ? '诊断中' : '未开始';
});
const previewDocument = computed<ReportPreviewDocument | null>(() => {
  if (!reportPreview.value?.renderedContent) return null;
  try {
    return JSON.parse(reportPreview.value.renderedContent) as ReportPreviewDocument;
  } catch {
    return null;
  }
});
const previewSlides = computed(
  () => previewDocument.value?.material?.filter((item) => item.slideCode) ?? [],
);
const responsibilityActionLabel = computed(() => {
  if (currentRole.value === 'INITIAL') return nextRole.value === 'AUDIT' ? '提交审核' : '提交复诊';
  if (currentRole.value === 'REVIEW') return '提交审核';
  if (currentRole.value === 'AUDIT') return '完成审核';
  return '提交下一步';
});
const allBlocks = computed(
  () => workspace.value?.materialTree.specimens.flatMap((item) => item.blocks) ?? [],
);
const allSlides = computed(
  () =>
    workspace.value?.materialTree.specimens.flatMap((specimen) => [
      ...specimen.blocks.flatMap((block) => block.slides),
      ...specimen.directSlides,
    ]) ?? [],
);
const targetOptions = computed(() => {
  if (!workspace.value)
    return { CASE: [], SPECIMEN: [], BLOCK: [], SLIDE: [] } as Record<
      TechnicalTargetType,
      Array<{ id: string; label: string }>
    >;
  return {
    CASE: [
      { id: workspace.value.caseSummary.caseId, label: workspace.value.caseSummary.pathologyNo },
    ],
    SPECIMEN: workspace.value.materialTree.specimens.map((item) => ({
      id: item.specimenId,
      label: `标本 ${item.specimenCode}`,
    })),
    BLOCK: allBlocks.value.map((item) => ({ id: item.blockId, label: `蜡块 ${item.blockCode}` })),
    SLIDE: allSlides.value.map((item) => ({ id: item.slideId, label: `玻片 ${item.slideCode}` })),
  };
});
const contextItems = computed(() => [
  { id: 'application' as const, label: '申请信息', count: '' },
  {
    id: 'specimens' as const,
    label: '标本',
    count: workspace.value?.materialTree.specimens.length ?? 0,
  },
  { id: 'blocks' as const, label: '蜡块', count: allBlocks.value.length },
  { id: 'slides' as const, label: '玻片', count: allSlides.value.length },
  { id: 'digital' as const, label: '数字切片', count: workspace.value?.digitalSlides?.length ?? 0 },
  { id: 'history' as const, label: '历史病理', count: '' },
]);

watch(caseId, () => void loadWorkspace(), { immediate: true });

async function loadDoctors() {
  try {
    const response = await fetch('/api/v2/auth/doctors');
    if (!response.ok) return;
    doctors.value = (await response.json()) as DoctorOption[];
  } catch {
    doctors.value = [];
  }
}

async function loadPublicPool() {
  try {
    const response = await fetch('/api/v2/diagnosis-workspaces/public-pool');
    if (!response.ok) throw new Error('公共病例池暂时无法加载');
    publicPool.value = (await response.json()) as PoolCase[];
  } catch (requestError) {
    error.value = friendlyError(requestError, '公共病例池暂时无法加载。');
  }
}

async function loadWorkspace() {
  reportPreview.value = null;
  previewOpen.value = false;
  if (!caseId.value) {
    workspace.value = null;
    await loadPublicPool();
    return;
  }
  loading.value = true;
  error.value = '';
  try {
    workspace.value = props.frozenRoundId
      ? await getV2FrozenRoundDiagnosisWorkspace(props.frozenRoundId)
      : await getV2DiagnosisWorkspace(caseId.value);
    const diagnosis = workspace.value.diagnosis;
    structuredData.value = diagnosis?.structuredData ?? '{}';
    structuredValues.value = parseStructuredValues(structuredData.value);
    microscopicDescription.value =
      diagnosis?.microscopicDescription?.trim() ||
      stringStructuredValue('microscopicDescription', structuredValues.value);
    diagnosisText.value =
      diagnosis?.diagnosisText?.trim() ||
      stringStructuredValue('diagnosisText', structuredValues.value);
    comment.value =
      diagnosis?.comment?.trim() || stringStructuredValue('comment', structuredValues.value);
    setNextResponsibilityDefaults();
    technicalProjects.value = workspace.value.actions.canCreateTechnicalOrder
      ? await getV2TechnicalProjects(caseId.value)
      : [];
    const firstProject = technicalProjects.value[0];
    if (firstProject && !technicalDrafts.value[0]?.projectId) {
      technicalDrafts.value[0] = createTechnicalDraft(firstProject);
    }
  } catch (requestError) {
    workspace.value = null;
    error.value = friendlyError(requestError, '诊断工作区加载失败，请检查病例后重试。');
  } finally {
    loading.value = false;
  }
}

function setNextResponsibilityDefaults() {
  if (currentRole.value === 'INITIAL') nextRole.value = 'REVIEW';
  else if (currentRole.value === 'REVIEW') nextRole.value = 'AUDIT';
  else nextRole.value = '';
  const candidate =
    (nextRole.value === 'AUDIT'
      ? doctors.value.find(
          (doctor) => doctor.title?.includes('审核') && doctor.id !== props.authUser?.doctor?.id,
        )
      : undefined) ?? doctors.value.find((doctor) => doctor.id !== props.authUser?.doctor?.id);
  nextDoctorId.value = candidate?.id ?? '';
}

function doctorName(doctorId?: string) {
  if (!doctorId) return '待分配';
  return doctors.value.find((doctor) => doctor.id === doctorId)?.displayName ?? '已分配医生';
}

function technicalProjectName(projectCode?: string) {
  if (!projectCode) return '技术项目';
  return (
    workspace.value?.technicalOrders
      .flatMap((order) => order.items)
      .find((item) => item.projectCode === projectCode)?.projectName ?? projectCode
  );
}

function createTechnicalDraft(project?: V2TechnicalProject): TechnicalDraft {
  const targetType = (project?.allowedTargetTypes[0] ?? 'CASE') as TechnicalTargetType;
  return {
    projectId: project?.projectId ?? '',
    quantity: 1,
    targetType,
    targetId: targetOptions.value[targetType][0]?.id ?? '',
    note: '',
  };
}

function addTechnicalDraft() {
  technicalDrafts.value.push(createTechnicalDraft(technicalProjects.value[0]));
}

function removeTechnicalDraft(index: number) {
  if (technicalDrafts.value.length > 1) technicalDrafts.value.splice(index, 1);
}

function projectForDraft(projectId: string) {
  return technicalProjects.value.find((project) => project.projectId === projectId);
}

function syncDraftProject(index: number) {
  const draft = technicalDrafts.value[index];
  if (!draft) return;
  const project = projectForDraft(draft.projectId);
  if (project && !project.allowedTargetTypes.includes(draft.targetType)) {
    draft.targetType = (project.allowedTargetTypes[0] ?? 'CASE') as TechnicalTargetType;
  }
  draft.targetId = targetOptions.value[draft.targetType][0]?.id ?? '';
}

function syncDraftTarget(index: number) {
  const draft = technicalDrafts.value[index];
  if (draft) draft.targetId = targetOptions.value[draft.targetType][0]?.id ?? '';
}

async function createTechnicalOrderCommand() {
  if (!workspace.value?.diagnosis) return;
  await submit(async () => {
    await createV2TechnicalOrder({
      diagnosisId: workspace.value!.diagnosis!.diagnosisId,
      requiredBeforeSignOut: technicalRequiredBeforeSignOut.value,
      items: technicalDrafts.value.map((draft) => ({
        projectId: draft.projectId,
        quantity: draft.quantity,
        parameters: '{}',
        note: draft.note,
        targets: [{ targetType: draft.targetType, targetId: draft.targetId }],
      })),
      idempotencyKey: requestKey('ux01-technical-order-create'),
    });
    technicalPanelOpen.value = false;
    technicalDrafts.value = [createTechnicalDraft(technicalProjects.value[0])];
    await loadWorkspace();
    notice.value = '技术医嘱已开立，技术人员可在工作台处理。';
  });
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
  structuredData.value = JSON.stringify(structuredValues.value);
  const canonical = value === null || value === undefined ? '' : String(value);
  if (code === 'microscopicDescription') microscopicDescription.value = canonical;
  if (code === 'diagnosisText') diagnosisText.value = canonical;
  if (code === 'comment') comment.value = canonical;
}

function stringStructuredValue(code: string, values: Record<string, unknown>) {
  const value = values[code];
  return value === null || value === undefined ? '' : String(value);
}

function hasTemplateComponent(code: string) {
  return templateComponents.value.some((component) => component.code === code);
}

function eventValue(event: Event) {
  return (event.target as HTMLInputElement).value;
}

function eventCheckedValue(event: Event) {
  return (event.target as HTMLInputElement).checked;
}

function stringValue(component: TemplateComponent) {
  const value = structuredValue(component);
  return value === null || value === undefined ? '' : String(value);
}

function templateOptions(component: TemplateComponent): TemplateOption[] {
  if (!Array.isArray(component.options)) return [];
  return component.options.map((option) =>
    typeof option === 'string' ? { value: option, label: option } : option,
  );
}

function requestKey(prefix: string) {
  return `${prefix}-${crypto.randomUUID()}`;
}

async function claim() {
  await submit(async () => {
    await claimV2Diagnosis(caseId.value, requestKey('ux01-diagnosis-claim'));
    await loadWorkspace();
    notice.value = '接诊成功，可以开始填写初诊。';
  });
}

async function assign() {
  await submit(async () => {
    await assignV2Diagnosis({
      caseId: caseId.value,
      doctorId: assignmentDoctor.value,
      reason: assignmentReason.value || '工作区分配',
      idempotencyKey: requestKey('ux01-diagnosis-assign'),
    });
    await loadWorkspace();
    notice.value = `病例已分配给 ${doctorName(assignmentDoctor.value)}。`;
  });
}

async function reassign() {
  await submit(async () => {
    await reassignV2Diagnosis({
      caseId: caseId.value,
      doctorId: assignmentDoctor.value,
      reason: assignmentReason.value,
      idempotencyKey: requestKey('ux01-diagnosis-reassign'),
    });
    await loadWorkspace();
    notice.value = `病例已重新分配给 ${doctorName(assignmentDoctor.value)}，原责任记录已保留。`;
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
      idempotencyKey: requestKey('ux01-diagnosis-save'),
    });
    await loadWorkspace();
    notice.value = '诊断草稿已保存。';
  });
}

async function complete() {
  if (!workspace.value?.diagnosis || !currentResponsibility.value || !currentRole.value) return;
  const role = currentRole.value;
  const followingRole = role === 'AUDIT' ? undefined : nextRole.value || undefined;
  await submit(async () => {
    await completeV2Responsibility({
      diagnosisId: workspace.value!.diagnosis!.diagnosisId,
      role,
      responsibilityId: currentResponsibility.value!.responsibilityId,
      responsibilityExpectedVersion: currentResponsibility.value!.version,
      structuredData: structuredData.value,
      microscopicDescription: microscopicDescription.value,
      diagnosisText: diagnosisText.value,
      comment: comment.value,
      diagnosisExpectedVersion: workspace.value!.diagnosis!.version,
      nextRole: followingRole,
      nextDoctorId: followingRole ? nextDoctorId.value : undefined,
      nextReason: followingRole ? `提交${responsibilityName(followingRole)}` : undefined,
      idempotencyKey: requestKey(`ux01-diagnosis-complete-${role.toLowerCase()}`),
    });
    const message = followingRole
      ? `已提交${responsibilityName(followingRole)}：${doctorName(nextDoctorId.value)}。`
      : '审核已完成，可以预览并签发报告。';
    await loadWorkspace();
    notice.value = message;
  });
}

async function previewReport() {
  if (!workspace.value?.diagnosis) return;
  await submit(async () => {
    reportPreview.value = await getV2ReportPreview(workspace.value!.diagnosis!.diagnosisId);
    previewOpen.value = true;
  });
}

async function signOutReport() {
  if (!workspace.value?.diagnosis) return;
  await submit(async () => {
    const report = await signOutV2Report({
      diagnosisId: workspace.value!.diagnosis!.diagnosisId,
      idempotencyKey: requestKey('ux01-report-sign-out'),
    });
    previewOpen.value = false;
    await loadWorkspace();
    notice.value = `${report.reportNo} 已签发，正式 PDF 已生成。`;
  });
}

async function withdrawReport(reportId: string) {
  if (!withdrawalReason.value.trim()) return;
  await submit(async () => {
    await withdrawV2Report({
      reportId,
      reason: withdrawalReason.value,
      idempotencyKey: requestKey('ux01-report-withdraw'),
    });
    withdrawalReason.value = '';
    withdrawalOpen.value = false;
    await loadWorkspace();
    notice.value = '报告已撤回，可修改诊断后重新签发；原报告仍保留在历史中。';
  });
}

async function supplementReport() {
  if (!workspace.value?.diagnosis || !supplementalContent.value.trim()) return;
  const prior = (workspace.value.reports ?? []).find(
    (report) => report.nature === 'ORIGINAL' && report.status === 'EFFECTIVE',
  );
  await submit(async () => {
    await supplementV2Report({
      diagnosisId: workspace.value!.diagnosis!.diagnosisId,
      priorReportId: prior?.reportId,
      content: supplementalContent.value,
      idempotencyKey: requestKey('ux01-report-supplement'),
    });
    supplementalContent.value = '';
    supplementalOpen.value = false;
    await loadWorkspace();
    notice.value = '补充报告已签发，原报告继续生效。';
  });
}

async function submit(operation: () => Promise<void>) {
  submitting.value = true;
  error.value = '';
  notice.value = '';
  try {
    await operation();
  } catch (requestError) {
    error.value = friendlyError(requestError, '操作未完成，请检查当前责任和病例状态。');
  } finally {
    submitting.value = false;
  }
}

function blockerText(reason: string) {
  return friendlyError(reason, reason);
}

function openViewer(reference: string) {
  window.open(reference, '_blank', 'noopener,noreferrer');
}

function handleShortcut(event: KeyboardEvent) {
  if (
    (event.ctrlKey || event.metaKey) &&
    event.key.toLowerCase() === 's' &&
    workspace.value?.diagnosis
  ) {
    event.preventDefault();
    if (canEdit.value && !submitting.value) void save();
  }
  if (event.key === 'Escape') {
    technicalPanelOpen.value = false;
    previewOpen.value = false;
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleShortcut);
  void loadDoctors().then(() => setNextResponsibilityDefaults());
});
onUnmounted(() => window.removeEventListener('keydown', handleShortcut));
</script>

<template>
  <section class="diagnosis-page" aria-label="诊断工作区">
    <div v-if="loading" class="diagnosis-loading list-skeleton" aria-label="正在加载诊断工作区">
      <span></span><span></span><span></span>
    </div>

    <section v-else-if="!workspace" class="diagnosis-pool-page">
      <header class="page-heading compact-heading">
        <div>
          <p class="section-kicker">诊断</p>
          <h2>病例池</h2>
          <p>接诊后直接进入诊断主工作区。</p>
        </div>
      </header>
      <p v-if="error" class="feedback error" role="alert">{{ error }}</p>
      <div v-if="!publicPool.length" class="empty-state workspace-panel">
        <strong>当前没有待接诊病例</strong><span>制片完成的病例会自动进入公共病例池。</span>
      </div>
      <div v-else class="workspace-panel compact-table" role="table" aria-label="公共病例池">
        <div class="table-head diagnosis-pool-row" role="row">
          <span role="columnheader">病理号</span><span role="columnheader">业务类型</span
          ><span role="columnheader">操作</span>
        </div>
        <div
          v-for="item in publicPool"
          :key="item.caseId"
          class="table-row diagnosis-pool-row"
          role="row"
        >
          <strong role="cell">{{ item.pathologyNo }}</strong>
          <span role="cell">{{ businessTypeName(item.businessTypeCode) }}</span>
          <span role="cell">
            <button
              class="text-button"
              type="button"
              @click="emit('navigate', `/v2/diagnosis/${item.caseId}`)"
            >
              进入诊断
            </button>
          </span>
        </div>
      </div>
    </section>

    <template v-else>
      <header class="diagnosis-context-bar" aria-label="病例固定上下文">
        <span
          ><small>病理号</small><strong>{{ workspace.caseSummary.pathologyNo }}</strong></span
        >
        <span
          ><small>患者</small><strong>{{ workspace.patient.patientReference }}</strong></span
        >
        <span
          ><small>就诊</small
          ><strong>{{ workspace.patient.visitReference || '未提供' }}</strong></span
        >
        <span
          ><small>业务类型</small
          ><strong>{{ businessTypeName(workspace.caseSummary.businessTypeCode) }}</strong></span
        >
        <span
          ><small>申请项目</small
          ><strong>{{ workspace.application.applicationItemCode }}</strong></span
        >
        <span
          ><small>当前责任</small><strong>{{ responsibilitySummary }}</strong></span
        >
        <span
          ><small>报告</small><strong>{{ reportStatus }}</strong></span
        >
      </header>

      <p v-if="error" class="feedback error diagnosis-feedback" role="alert">{{ error }}</p>
      <p v-if="notice" class="feedback success diagnosis-feedback" role="status">{{ notice }}</p>

      <div class="diagnosis-layout">
        <aside class="diagnosis-context-nav" aria-label="病例上下文">
          <p class="section-kicker">病例上下文</p>
          <nav class="context-nav-list" aria-label="病例材料导航">
            <button
              v-for="item in contextItems"
              :key="item.id"
              type="button"
              :class="{ active: activeContext === item.id }"
              @click="activeContext = item.id"
            >
              <span>{{ item.label }}</span
              ><span v-if="item.count !== ''" class="count-pill">{{ item.count }}</span>
            </button>
          </nav>

          <section class="context-detail">
            <dl v-if="activeContext === 'application'" class="context-definition-list">
              <div>
                <dt>申请号</dt>
                <dd>{{ workspace.application.externalApplicationId }}</dd>
              </div>
              <div>
                <dt>来源</dt>
                <dd>
                  {{
                    workspace.application.sourceSystemCode === 'MANUAL'
                      ? '手工登记'
                      : workspace.application.sourceSystemCode
                  }}
                </dd>
              </div>
              <div>
                <dt>申请项目</dt>
                <dd>{{ workspace.application.applicationItemCode }}</dd>
              </div>
            </dl>
            <ul v-else-if="activeContext === 'specimens'" class="context-material-list">
              <li v-for="specimen in workspace.materialTree.specimens" :key="specimen.specimenId">
                <strong>标本 {{ specimen.specimenCode }}</strong
                ><span>{{ specimen.specimenNo }}</span>
              </li>
            </ul>
            <ul v-else-if="activeContext === 'blocks'" class="context-material-list">
              <li v-for="block in allBlocks" :key="block.blockId">
                <strong>{{ block.blockCode }}</strong
                ><span>{{ block.slides.length }} 张玻片</span>
              </li>
            </ul>
            <ul v-else-if="activeContext === 'slides'" class="context-material-list">
              <li v-for="slide in allSlides" :key="slide.slideId">
                <strong>{{ slide.slideCode }}</strong
                ><span :class="slide.completed ? 'success-text' : 'warning-text'">{{
                  slide.completed ? '已完成' : '待完成'
                }}</span>
              </li>
            </ul>
            <div v-else-if="activeContext === 'digital'" class="context-material-list">
              <button
                v-for="digital in workspace.digitalSlides ?? []"
                :key="digital.digitalSlideId"
                class="digital-slide-link"
                type="button"
                @click="openViewer(digital.viewerReference)"
              >
                <span
                  ><strong>数字切片</strong><small>{{ digital.sourcePlatform }}</small></span
                ><span>打开 →</span>
              </button>
              <p v-if="!(workspace.digitalSlides ?? []).length" class="muted">当前没有数字切片。</p>
            </div>
            <div v-else class="empty-state compact">
              <strong>暂无历史病理记录</strong><span>患者历史接入后显示在这里。</span>
            </div>
          </section>
        </aside>

        <main class="diagnosis-editor-stage">
          <section class="diagnosis-editor-card" aria-label="诊断编辑器">
            <header class="panel-title-row">
              <div>
                <p class="section-kicker">诊断内容</p>
                <h2>{{ editorTitle }}</h2>
              </div>
              <span v-if="workspace.diagnosis" class="status-pill"
                >最近保存 {{ formatDateTime(workspace.diagnosis.updatedAt) }}</span
              >
            </header>

            <div v-if="!workspace.diagnosis" class="empty-state">
              <strong>病例尚未接诊</strong><span>接诊后会建立初诊责任并打开诊断编辑器。</span>
            </div>
            <fieldset v-else :disabled="!canEdit || submitting" class="diagnosis-fields">
              <legend class="visually-hidden">诊断内容</legend>
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
                  ></textarea>
                  <input
                    v-else
                    :value="stringValue(component)"
                    :required="component.required"
                    :readonly="component.readOnly"
                    @input="updateStructuredValue(component.code, eventValue($event))"
                  />
                </label>
                <label
                  v-else-if="['SINGLE_SELECT', 'DICTIONARY'].includes(componentType(component))"
                >
                  {{ component.label || component.code }}
                  <select
                    :value="stringValue(component)"
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
                <label v-else-if="componentType(component) === 'BOOLEAN'" class="checkbox-label">
                  <input
                    type="checkbox"
                    :checked="Boolean(structuredValue(component))"
                    :disabled="component.readOnly"
                    @change="updateStructuredValue(component.code, eventCheckedValue($event))"
                  />
                  {{ component.label || component.code }}
                </label>
              </template>
              <label v-if="!hasTemplateComponent('microscopicDescription')">
                镜下所见
                <textarea
                  v-model="microscopicDescription"
                  class="microscopic-text"
                  placeholder="记录镜下形态、结构及必要的阴性所见"
                ></textarea>
              </label>
              <label v-if="!hasTemplateComponent('diagnosisText')">
                病理诊断
                <textarea
                  v-model="diagnosisText"
                  class="diagnosis-text"
                  placeholder="输入正式病理诊断"
                ></textarea>
              </label>
              <label v-if="!hasTemplateComponent('comment')">
                备注
                <textarea
                  v-model="comment"
                  rows="2"
                  placeholder="可选：建议、说明或备注"
                ></textarea>
              </label>
            </fieldset>
          </section>
        </main>

        <aside class="diagnosis-inspector" aria-label="责任、医嘱与报告">
          <section class="inspector-section">
            <h3>责任链</h3>
            <div class="responsibility-timeline">
              <div
                v-for="role in ['INITIAL', 'REVIEW', 'AUDIT'] as const"
                :key="role"
                class="responsibility-step"
              >
                <span
                  class="semantic-dot"
                  :class="
                    workspace.responsibilityChain.some((item) => item.role === role && item.current)
                      ? 'current'
                      : workspace.responsibilityChain.some(
                            (item) => item.role === role && item.completedAt,
                          )
                        ? 'success'
                        : 'neutral'
                  "
                ></span>
                <span>
                  <strong>{{ responsibilityName(role) }}</strong>
                  <small>{{
                    doctorName(
                      workspace.responsibilityChain.find((item) => item.role === role)?.doctorId,
                    )
                  }}</small>
                </span>
                <span>{{
                  workspace.responsibilityChain.some(
                    (item) => item.role === role && item.completedAt,
                  )
                    ? '✓'
                    : workspace.responsibilityChain.some(
                          (item) => item.role === role && item.current,
                        )
                      ? '当前'
                      : '待分配'
                }}</span>
              </div>
            </div>

            <div
              v-if="currentRole && currentRole !== 'AUDIT' && canEdit"
              class="next-responsibility-form"
            >
              <label>
                下一步
                <select v-model="nextRole">
                  <option v-if="currentRole === 'INITIAL'" value="REVIEW">提交复诊</option>
                  <option value="AUDIT">提交审核</option>
                </select>
              </label>
              <label>
                {{ responsibilityName(nextRole) }}医生
                <select v-model="nextDoctorId">
                  <option value="" disabled>请选择医生</option>
                  <option v-for="doctor in doctors" :key="doctor.id" :value="doctor.id">
                    {{ doctor.displayName }} · {{ doctor.title || '医生' }}
                  </option>
                </select>
              </label>
            </div>
            <div v-if="workspace.actions.canReassign" class="reassignment-form">
              <label>
                改派给
                <select v-model="assignmentDoctor">
                  <option value="" disabled>请选择医生</option>
                  <option v-for="doctor in doctors" :key="doctor.id" :value="doctor.id">
                    {{ doctor.displayName }}
                  </option>
                </select>
              </label>
              <label>改派原因 <input v-model="assignmentReason" placeholder="必填" /></label>
              <button
                class="secondary-button"
                type="button"
                :disabled="!assignmentDoctor || !assignmentReason.trim() || submitting"
                @click="reassign"
              >
                确认改派
              </button>
            </div>
          </section>

          <section v-if="molecularResults.length" class="inspector-section">
            <header class="panel-title-row">
              <h3>分子结果</h3>
              <span class="status-pill success">{{ molecularResults.length }} 项已返回</span>
            </header>
            <div class="technical-result-list">
              <div
                v-for="result in molecularResults"
                :key="result.resultId"
                class="technical-result-item"
              >
                <span class="semantic-dot success"></span>
                <span>
                  <strong>{{ result.resultCode }}</strong>
                  <small>{{ molecularResultSummary(result.resultData) }}</small>
                </span>
                <span>{{ result.statusCode === 'COMPLETED' ? '已完成' : '处理中' }}</span>
              </div>
            </div>
          </section>

          <section class="inspector-section">
            <header class="panel-title-row">
              <h3>技术医嘱</h3>
              <span v-if="technicalReturnedCount" class="status-pill success"
                >{{ technicalReturnedCount }} 项结果已返回</span
              >
            </header>
            <div v-if="workspace.technicalOrders.length" class="technical-result-list">
              <div
                v-for="order in workspace.technicalOrders"
                :key="order.orderId"
                class="technical-result-item"
              >
                <span
                  class="semantic-dot"
                  :class="
                    order.status === 'COMPLETED'
                      ? 'success'
                      : order.blocking
                        ? 'warning'
                        : 'neutral'
                  "
                ></span>
                <span
                  ><strong>{{ order.items.map((item) => item.projectName).join('、') }}</strong
                  ><small
                    >{{ order.items.reduce((sum, item) => sum + item.completedCount, 0) }}/{{
                      order.items.reduce((sum, item) => sum + item.expectedCount, 0)
                    }}
                    完成</small
                  ></span
                >
                <span>{{ statusName(order.status) }}</span>
              </div>
            </div>
            <p v-else class="muted">当前没有技术医嘱。</p>
          </section>

          <section class="inspector-section">
            <h3>报告</h3>
            <div class="report-status-list">
              <span
                ><small>当前状态</small><strong>{{ reportStatus }}</strong></span
              >
              <span
                ><small>制片</small><strong>{{ productionSummary }}</strong></span
              >
              <span v-if="workspace.blockingReasons.length"
                ><small>暂不能签发</small
                ><strong>{{ workspace.blockingReasons.length }} 项待处理</strong></span
              >
            </div>
            <ul v-if="workspace.blockingReasons.length" class="plain-warning-list">
              <li v-for="reason in workspace.blockingReasons" :key="reason">
                {{ blockerText(reason) }}
              </li>
            </ul>
          </section>

          <section v-if="workspace.reports.length" class="inspector-section">
            <h3>报告历史</h3>
            <div class="report-history-list">
              <article v-for="report in workspace.reports" :key="report.reportId">
                <span
                  ><strong>{{ report.reportNo }}</strong
                  ><small
                    >{{ report.supplemental ? '补充报告' : '正式报告' }} ·
                    {{ report.status === 'EFFECTIVE' ? '生效' : '已撤回' }}</small
                  ></span
                >
                <a :href="getV2ReportPdfUrl(report.reportId)" target="_blank" rel="noreferrer"
                  >PDF</a
                >
              </article>
            </div>
            <div class="inline-actions">
              <button
                v-if="workspace.actions.canWithdraw"
                class="text-button"
                type="button"
                @click="withdrawalOpen = !withdrawalOpen"
              >
                撤回
              </button>
              <button
                v-if="workspace.actions.canSupplement"
                class="text-button"
                type="button"
                @click="supplementalOpen = !supplementalOpen"
              >
                补充报告
              </button>
            </div>
            <div v-if="withdrawalOpen" class="report-inline-form">
              <label>撤回原因 <input v-model="withdrawalReason" /></label>
              <button
                v-for="report in workspace.reports.filter((item) => item.status === 'EFFECTIVE')"
                :key="report.reportId"
                class="danger-button"
                type="button"
                :disabled="!withdrawalReason.trim() || submitting"
                @click="withdrawReport(report.reportId)"
              >
                确认撤回 {{ report.reportNo }}
              </button>
            </div>
            <div v-if="supplementalOpen" class="report-inline-form">
              <label>补充内容 <textarea v-model="supplementalContent" rows="3"></textarea></label>
              <button
                class="primary-button"
                type="button"
                :disabled="!supplementalContent.trim() || submitting"
                @click="supplementReport"
              >
                签发补充报告
              </button>
            </div>
          </section>
        </aside>
      </div>

      <footer class="workspace-action-bar" aria-label="诊断主要操作">
        <span class="muted"
          >Ctrl + S 保存 · 当前身份：{{ props.authUser?.displayName ?? '当前用户' }}</span
        >
        <div class="action-group">
          <template v-if="!workspace.diagnosis">
            <button
              v-if="workspace.actions.canClaim"
              class="primary-button"
              type="button"
              :disabled="submitting"
              @click="claim"
            >
              接诊
            </button>
            <template v-if="workspace.actions.canAssign">
              <select v-model="assignmentDoctor" aria-label="分配医生">
                <option value="" disabled>选择初诊医生</option>
                <option v-for="doctor in doctors" :key="doctor.id" :value="doctor.id">
                  {{ doctor.displayName }}
                </option>
              </select>
              <button
                class="secondary-button"
                type="button"
                :disabled="!assignmentDoctor || submitting"
                @click="assign"
              >
                分配
              </button>
            </template>
          </template>
          <template v-else>
            <button
              class="secondary-button"
              type="button"
              :disabled="!canEdit || submitting"
              @click="save"
            >
              保存
            </button>
            <button
              v-if="workspace.actions.canCreateTechnicalOrder"
              class="secondary-button"
              type="button"
              @click="technicalPanelOpen = true"
            >
              技术医嘱
            </button>
            <button
              v-if="completionAllowed"
              class="primary-button"
              type="button"
              :disabled="submitting || (currentRole !== 'AUDIT' && !nextDoctorId)"
              @click="complete"
            >
              {{ responsibilityActionLabel }}
            </button>
            <button
              class="secondary-button"
              type="button"
              :disabled="!workspace.actions.canPreview || submitting"
              @click="previewReport"
            >
              报告预览
            </button>
            <button
              class="primary-button"
              type="button"
              :disabled="!workspace.actions.canSignOut || submitting"
              @click="signOutReport"
            >
              签发
            </button>
          </template>
        </div>
      </footer>
    </template>

    <div v-if="technicalPanelOpen" class="drawer-backdrop" @click.self="technicalPanelOpen = false">
      <aside
        class="technical-order-drawer"
        role="dialog"
        aria-modal="true"
        aria-label="开立技术医嘱"
      >
        <header class="drawer-header">
          <div>
            <p class="section-kicker">诊断辅助</p>
            <h2>开立技术医嘱</h2>
          </div>
          <button
            class="icon-button"
            type="button"
            aria-label="关闭技术医嘱"
            @click="technicalPanelOpen = false"
          >
            ×
          </button>
        </header>
        <label class="checkbox-label"
          ><input
            v-model="technicalRequiredBeforeSignOut"
            type="checkbox"
          />这些结果返回前暂不签发</label
        >
        <article
          v-for="(draft, index) in technicalDrafts"
          :key="index"
          class="technical-order-draft"
        >
          <header>
            <strong>项目 {{ index + 1 }}</strong
            ><button
              class="text-button"
              type="button"
              :disabled="technicalDrafts.length === 1"
              @click="removeTechnicalDraft(index)"
            >
              删除
            </button>
          </header>
          <label
            >项目
            <select v-model="draft.projectId" @change="syncDraftProject(index)">
              <option value="" disabled>请选择项目</option>
              <option
                v-for="project in technicalProjects"
                :key="project.projectId"
                :value="project.projectId"
              >
                {{ project.projectName }}
              </option>
            </select></label
          >
          <div class="field-grid">
            <label
              >材料类型
              <select v-model="draft.targetType" @change="syncDraftTarget(index)">
                <option
                  v-for="type in projectForDraft(draft.projectId)?.allowedTargetTypes ?? []"
                  :key="type"
                  :value="type"
                >
                  {{
                    type === 'CASE'
                      ? '病例'
                      : type === 'SPECIMEN'
                        ? '标本'
                        : type === 'BLOCK'
                          ? '蜡块'
                          : '玻片'
                  }}
                </option>
              </select></label
            >
            <label
              >目标材料
              <select v-model="draft.targetId">
                <option value="" disabled>请选择</option>
                <option
                  v-for="target in targetOptions[draft.targetType]"
                  :key="target.id"
                  :value="target.id"
                >
                  {{ target.label }}
                </option>
              </select></label
            >
          </div>
          <div class="field-grid">
            <label>数量 <input v-model.number="draft.quantity" min="1" type="number" /></label
            ><label>备注 <input v-model="draft.note" /></label>
          </div>
        </article>
        <button class="secondary-button" type="button" @click="addTechnicalDraft">
          + 添加项目
        </button>
        <div class="sticky-form-actions">
          <span class="muted">共 {{ technicalDrafts.length }} 个项目</span
          ><button
            class="primary-button"
            type="button"
            :disabled="
              submitting || technicalDrafts.some((item) => !item.projectId || !item.targetId)
            "
            @click="createTechnicalOrderCommand"
          >
            确认开立
          </button>
        </div>
      </aside>
    </div>

    <div
      v-if="previewOpen && reportPreview"
      class="report-preview-overlay"
      role="dialog"
      aria-modal="true"
      aria-label="报告预览"
    >
      <article v-if="previewDocument" class="report-preview-paper" aria-label="报告内容">
        <header class="report-document-header">
          <p>病理诊断报告</p>
          <h2>{{ previewDocument.case?.pathologyNo ?? workspace?.caseSummary.pathologyNo }}</h2>
          <div>
            <span
              >患者：{{
                previewDocument.case?.patientReference ?? workspace?.patient.patientReference
              }}</span
            >
            <span
              >就诊：{{
                previewDocument.case?.visitReference ?? workspace?.patient.visitReference
              }}</span
            >
            <span>业务类型：{{ businessTypeName(previewDocument.case?.businessTypeCode) }}</span>
          </div>
        </header>
        <section>
          <h3>镜下所见</h3>
          <p>{{ previewDocument.diagnosis?.microscopicDescription || '未填写' }}</p>
        </section>
        <section class="report-diagnosis-section">
          <h3>病理诊断</h3>
          <p>{{ previewDocument.diagnosis?.diagnosisText || '未填写' }}</p>
        </section>
        <section v-if="previewSlides.length">
          <h3>材料</h3>
          <p>
            {{
              previewSlides
                .map((item) => [item.slideCode, item.slideType].filter(Boolean).join(' · '))
                .join('、')
            }}
          </p>
        </section>
        <section v-if="previewDocument.technicalResults?.length">
          <h3>技术结果</h3>
          <ul>
            <li v-for="order in previewDocument.technicalResults" :key="order.orderNo">
              {{
                order.items
                  ?.map(
                    (item) =>
                      `${technicalProjectName(item.projectCode)} ${item.completedCount ?? 0}/${item.expectedCount ?? 0}`,
                  )
                  .join('、')
              }}
            </li>
          </ul>
        </section>
        <footer class="report-signature-row">
          <span
            v-for="item in previewDocument.responsibility"
            :key="`${item.role}-${item.doctorId}`"
          >
            <small>{{ responsibilityName(item.role) }}</small>
            <strong>{{ doctorName(item.doctorId) }}</strong>
            <small>{{ formatDateTime(item.completedAt) }}</small>
          </span>
        </footer>
      </article>
      <article v-else class="report-preview-paper report-preview-unavailable">
        <strong>暂时无法显示报告版式</strong>
        <span>请返回诊断后重新生成预览。</span>
      </article>
      <aside class="report-preview-actions">
        <header>
          <p class="section-kicker">报告预览</p>
          <h2>签发前确认</h2>
        </header>
        <p v-if="reportPreview.valid" class="feedback success">预览有效，可以签发。</p>
        <div v-else class="feedback warning">
          <span
            ><strong>暂不能签发：</strong><br /><template
              v-for="reason in reportPreview.blockingReasons"
              :key="reason"
              >{{ blockerText(reason) }}<br /></template
          ></span>
        </div>
        <button class="secondary-button" type="button" @click="previewOpen = false">
          返回诊断
        </button>
        <button
          class="primary-button"
          type="button"
          :disabled="!workspace?.actions.canSignOut || submitting"
          @click="signOutReport"
        >
          确认签发
        </button>
      </aside>
    </div>
  </section>
</template>
