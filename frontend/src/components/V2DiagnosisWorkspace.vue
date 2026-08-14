<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';

import type { V2AuthUser } from '../auth';
import {
  appendNavigationContext,
  safeLocalPath,
  workspaceBackLabel,
  workspaceBackTarget,
  type V2Route,
} from '../navigation';
import { businessTypeName, friendlyError, formatDateTime, responsibilityName } from '../uiText';
import {
  acknowledgeV2TechnicalResult,
  assignV2Diagnosis,
  autoAssignV2Diagnosis,
  claimV2Diagnosis,
  completeV2CaseFollowUp,
  completeV2Responsibility,
  createV2CaseConsultation,
  createV2CaseFollowUp,
  createV2DigitalAnnotation,
  createV2DigitalMeasurement,
  createV2FrozenRoundDiagnosis,
  createV2TechnicalOrder,
  downloadV2EncryptedReportPdf,
  getV2DiagnosisWorkspace,
  getV2DigitalAnnotations,
  getV2DigitalMeasurements,
  getV2DigitalScreenshots,
  getV2DigitalScreenshotContentUrl,
  getV2CaseConsultations,
  getV2CaseFavorite,
  getV2CaseFollowUps,
  getV2FrozenRoundDiagnosisWorkspace,
  getV2ReportPdfUrl,
  getV2ReportPreview,
  getV2TechnicalProjects,
  saveV2Diagnosis,
  saveV2DigitalScreenshot,
  signOutV2Report,
  supplementV2Report,
  favoriteV2Case,
  unfavoriteV2Case,
  withdrawV2Report,
  type V2CaseConsultation,
  type V2CaseFollowUp,
  type V2DiagnosisWorkspace as DiagnosisWorkspace,
  type V2DigitalAnnotation,
  type V2DigitalMeasurement,
  type V2DigitalScreenshot,
  type V2ResponsibilityRole,
  type V2TechnicalItem,
  type V2TechnicalProject,
} from '../v2DiagnosisApi';
import V2ImageViewer from './V2ImageViewer.vue';
import V2CaseHeader from './V2CaseHeader.vue';
import {
  getV2CaseWorkspace,
  getV2MyWorkbench,
  getV2PatientHistory,
  type V2PatientHistoryItem,
  type V2WorkspaceTimelineEntry,
} from '../v2WorkspaceApi';
import DiagnosisEditor from './diagnosis/DiagnosisEditor.vue';
import DiagnosisWorkspaceShell from './diagnosis/DiagnosisWorkspaceShell.vue';
import ImageViewerPanel from './diagnosis/ImageViewerPanel.vue';

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
type PoolCase = {
  caseId: string;
  pathologyNo: string;
  businessTypeCode: string;
  workCode?: string;
};
type SupportPanel = 'clinical' | 'technical' | 'caseSupport' | 'history' | 'reports' | 'audit';
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
  defineProps<{
    frozenRoundId?: string;
    authUser?: V2AuthUser | null;
    focusKind?: string;
    focusId?: string;
    origin?: V2Route['origin'];
    queue?: string;
    returnTo?: string;
  }>(),
  {
    frozenRoundId: undefined,
    authUser: null,
    focusKind: '',
    focusId: '',
    origin: 'direct',
    queue: '',
    returnTo: '',
  },
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
const activeSupportPanel = ref<SupportPanel | ''>('');
const selectedSlideId = ref('');
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
const encryptedPdfReport = ref<{ reportId: string; reportNo: string } | null>(null);
const encryptedPdfPassword = ref('');
const encryptedPdfReason = ref('');
const selectedViewer = ref<{
  digitalSlideId: string;
  viewerReference: string;
  sourcePlatform: string;
  slideId?: string | null;
  context: {
    caseNo: string;
    specimenCode?: string;
    blockCode?: string;
    slideCode?: string;
    digitalSlideId: string;
  };
} | null>(null);
const viewerAnnotationNote = ref('');
const viewerReviewBusy = ref(false);
const imageViewer = ref<{
  startAnnotation: () => boolean;
  startMeasurement: () => boolean;
  captureCurrentView: () => Promise<{
    capture: { mediaType: string; dataUrl: string };
    viewport: { zoom: number; centerX: number; centerY: number } | null;
  } | null>;
} | null>(null);
const viewerAnnotations = ref<V2DigitalAnnotation[]>([]);
const viewerMeasurements = ref<V2DigitalMeasurement[]>([]);
const viewerScreenshots = ref<V2DigitalScreenshot[]>([]);
const caseFavorite = ref(false);
const caseFollowUps = ref<V2CaseFollowUp[]>([]);
const caseConsultations = ref<V2CaseConsultation[]>([]);
const followUpDate = ref(new Date().toISOString().slice(0, 10));
const followUpPlan = ref('');
const followUpCompletion = ref<Record<string, { content: string; result: string }>>({});
const consultationDraft = ref({
  participantRefs: '',
  reason: '',
  discussion: '',
  conclusion: '',
  note: '',
});

const currentResponsibility = computed(() => workspace.value?.currentResponsibility);
const currentRole = computed<V2ResponsibilityRole | undefined>(
  () => currentResponsibility.value?.role,
);
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

function templateComponentLabel(component: TemplateComponent) {
  return component.code.toLowerCase() === 'diagnosistext'
    ? '病理诊断'
    : component.label || component.code;
}
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
const timelineEntries = ref<V2WorkspaceTimelineEntry[]>([]);
const patientHistory = ref<V2PatientHistoryItem[]>([]);
const workbenchCases = ref<PoolCase[]>([]);
const canAcknowledgeTechnicalResults = computed(
  () =>
    !props.authUser ||
    !currentResponsibility.value ||
    props.authUser.doctor?.id === currentResponsibility.value.doctorId,
);
const nextCaseId = computed(() => {
  const currentIndex = workbenchCases.value.findIndex((item) => item.caseId === caseId.value);
  return currentIndex >= 0 ? (workbenchCases.value[currentIndex + 1]?.caseId ?? '') : '';
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

function diagnosisPath(nextCaseId: string) {
  return appendNavigationContext(`/v2/diagnosis/${nextCaseId}`, {
    origin: props.origin,
    queue: props.queue,
    returnTo: props.returnTo,
  });
}

function technicalResultAcknowledged(itemId: string) {
  return timelineEntries.value.some(
    (entry) =>
      entry.targetId === itemId && entry.operationCode === 'PIS-V2-PX02B-TECHNICAL-RESULT-ACK',
  );
}

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

function reportLabel(reportId: string) {
  const reports = workspace.value?.reports ?? [];
  const report = reports.find((item) => item.reportId === reportId);
  if (!report) return '报告';
  const chronological = (left: (typeof reports)[number], right: (typeof reports)[number]) => {
    const leftNumber = Number(left.reportNo.match(/\d+/)?.[0] ?? Number.MAX_SAFE_INTEGER);
    const rightNumber = Number(right.reportNo.match(/\d+/)?.[0] ?? Number.MAX_SAFE_INTEGER);
    if (leftNumber !== rightNumber) return leftNumber - rightNumber;
    return left.signedAt.localeCompare(right.signedAt);
  };
  if (report.supplemental) {
    const index =
      [...reports.filter((item) => item.supplemental)]
        .sort(chronological)
        .findIndex((item) => item.reportId === reportId) + 1;
    return `补充报告 ${index}`;
  }
  const index =
    [...reports.filter((item) => !item.supplemental)]
      .sort(chronological)
      .findIndex((item) => item.reportId === reportId) + 1;
  return `报告 ${index}`;
}
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
    timelineEntries.value = [];
    await loadPublicPool();
    return;
  }
  loading.value = true;
  error.value = '';
  try {
    const [loadedWorkspace, caseContext] = await Promise.all([
      props.frozenRoundId
        ? loadFrozenDiagnosisWorkspace(props.frozenRoundId)
        : getV2DiagnosisWorkspace(caseId.value),
      getV2CaseWorkspace(caseId.value),
    ]);
    workspace.value = loadedWorkspace;
    timelineEntries.value = caseContext.timeline;
    try {
      patientHistory.value =
        (await getV2PatientHistory(loadedWorkspace.patient.patientReference, caseId.value)).items ??
        [];
    } catch {
      patientHistory.value = [];
    }
    await loadCaseSupport(loadedWorkspace.caseSummary.caseId);
    void getV2MyWorkbench()
      .then((result) => {
        const queueItems =
          props.queue === 'PUBLIC_POOL'
            ? result.publicPool
            : props.queue
              ? result.myWork.filter((item) => item.workCode === props.queue)
              : [...result.myWork, ...result.publicPool];
        workbenchCases.value = queueItems.map((item) => ({
          caseId: item.caseId,
          pathologyNo: item.pathologyNo,
          businessTypeCode: item.businessTypeCode,
          workCode: item.workCode,
        }));
      })
      .catch(() => {
        workbenchCases.value = [];
      });
    const diagnosis = workspace.value.diagnosis;
    selectedSlideId.value = allSlides.value[0]?.slideId ?? '';
    const firstDigital = viewerDigitalSlides.value[0];
    if (firstDigital) openViewer(firstDigital);
    else selectedViewer.value = null;
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
    applyFocus();
  } catch (requestError) {
    workspace.value = null;
    error.value = friendlyError(requestError, '诊断工作区加载失败，请检查病例后重试。');
  } finally {
    loading.value = false;
  }
}

async function loadCaseSupport(currentCaseId: string) {
  try {
    const [favorite, followUps, consultations] = await Promise.all([
      getV2CaseFavorite(currentCaseId),
      getV2CaseFollowUps(currentCaseId),
      getV2CaseConsultations(currentCaseId),
    ]);
    caseFavorite.value = Boolean(favorite.favorite);
    caseFollowUps.value = Array.isArray(followUps) ? followUps : [];
    caseConsultations.value = Array.isArray(consultations) ? consultations : [];
    followUpCompletion.value = Object.fromEntries(
      caseFollowUps.value
        .filter((item) => !item.completedAt)
        .map((item) => [item.followUpId, { content: '', result: '' }]),
    );
  } catch {
    caseFavorite.value = false;
    caseFollowUps.value = [];
    caseConsultations.value = [];
    followUpCompletion.value = {};
  }
}

async function toggleFavorite() {
  if (!workspace.value) return;
  await submit(async () => {
    const result = caseFavorite.value
      ? await unfavoriteV2Case(workspace.value!.caseSummary.caseId)
      : await favoriteV2Case(workspace.value!.caseSummary.caseId);
    caseFavorite.value = result.favorite;
    notice.value = result.favorite ? '病例已收藏。' : '已取消收藏。';
  });
}

async function createFollowUp() {
  if (!workspace.value || !followUpDate.value || !followUpPlan.value.trim()) return;
  await submit(async () => {
    await createV2CaseFollowUp({
      caseId: workspace.value!.caseSummary.caseId,
      followUpDate: followUpDate.value,
      plan: followUpPlan.value.trim(),
      idempotencyKey: requestKey('dx-follow-up-create'),
    });
    followUpPlan.value = '';
    await loadCaseSupport(workspace.value!.caseSummary.caseId);
    activeSupportPanel.value = 'caseSupport';
    notice.value = '随访计划已保存。';
  });
}

async function completeFollowUp(item: V2CaseFollowUp) {
  const draft = followUpCompletion.value[item.followUpId];
  if (!workspace.value || !draft || (!draft.content.trim() && !draft.result.trim())) return;
  await submit(async () => {
    await completeV2CaseFollowUp({
      followUpId: item.followUpId,
      content: draft.content.trim(),
      result: draft.result.trim(),
      idempotencyKey: requestKey('dx-follow-up-complete'),
    });
    await loadCaseSupport(workspace.value!.caseSummary.caseId);
    activeSupportPanel.value = 'caseSupport';
    notice.value = '随访结果已完成并留痕。';
  });
}

function followUpDraft(followUpId: string) {
  return (followUpCompletion.value[followUpId] ??= { content: '', result: '' });
}

async function createConsultation() {
  if (
    !workspace.value ||
    !consultationDraft.value.participantRefs.trim() ||
    !consultationDraft.value.reason.trim()
  )
    return;
  await submit(async () => {
    await createV2CaseConsultation({
      caseId: workspace.value!.caseSummary.caseId,
      initiatorRef: props.authUser?.doctor?.id ?? props.authUser?.userId ?? '当前医生',
      participantRefs: consultationDraft.value.participantRefs.trim(),
      reason: consultationDraft.value.reason.trim(),
      discussion: consultationDraft.value.discussion.trim(),
      conclusion: consultationDraft.value.conclusion.trim(),
      note: consultationDraft.value.note.trim(),
      idempotencyKey: requestKey('dx-consultation-create'),
    });
    consultationDraft.value = {
      participantRefs: '',
      reason: '',
      discussion: '',
      conclusion: '',
      note: '',
    };
    await loadCaseSupport(workspace.value!.caseSummary.caseId);
    activeSupportPanel.value = 'caseSupport';
    notice.value = '科内会诊记录已保存。';
  });
}

async function loadFrozenDiagnosisWorkspace(roundId: string) {
  try {
    return await getV2FrozenRoundDiagnosisWorkspace(roundId);
  } catch (requestError) {
    if (
      !(requestError instanceof Error) ||
      !requestError.message.startsWith('V2-FROZEN-DIAGNOSIS-NOT-FOUND')
    ) {
      throw requestError;
    }
    // The workbench may project a production-ready, unassigned Frozen round.
    // Creating the unified Diagnosis is the explicit entry action; the POST is
    // idempotent and does not create a second diagnosis when another doctor won
    // the race.
    await createV2FrozenRoundDiagnosis(roundId, `frozen-diagnosis-entry-${roundId}`);
    return getV2FrozenRoundDiagnosisWorkspace(roundId);
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

async function acknowledgeResult(itemId: string) {
  await submit(async () => {
    await acknowledgeV2TechnicalResult(itemId);
    await loadWorkspace();
    notice.value = '技术结果已标记为已查看。';
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

async function autoAssign(targetCaseId = caseId.value) {
  await submit(async () => {
    const result = await autoAssignV2Diagnosis(
      targetCaseId,
      requestKey('ux01-diagnosis-auto-assign'),
    );
    if (targetCaseId === caseId.value && caseId.value) {
      await loadWorkspace();
      notice.value = `已自动分诊至 ${result.diagnosisGroupCode}，责任医生 ${doctorName(result.doctorId)}。`;
      return;
    }
    emit('navigate', `/v2/cases/${targetCaseId}?focus=diagnosis`);
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

function openEncryptedPdf(reportId: string, reportNo: string) {
  encryptedPdfReport.value = { reportId, reportNo };
  encryptedPdfPassword.value = '';
  encryptedPdfReason.value = '';
}

function closeEncryptedPdf() {
  encryptedPdfReport.value = null;
  encryptedPdfPassword.value = '';
  encryptedPdfReason.value = '';
}

async function downloadEncryptedPdf() {
  if (!encryptedPdfReport.value) return;
  await submit(async () => {
    const report = encryptedPdfReport.value!;
    const content = await downloadV2EncryptedReportPdf({
      reportId: report.reportId,
      accessPassword: encryptedPdfPassword.value,
      reason: encryptedPdfReason.value,
    });
    const url = URL.createObjectURL(content);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `${report.reportNo}-encrypted.pdf`;
    anchor.click();
    URL.revokeObjectURL(url);
    closeEncryptedPdf();
    notice.value = '口令加密PDF已生成并下载；系统未保存访问密码。';
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

function openViewer(digital: {
  digitalSlideId: string;
  viewerReference: string;
  sourcePlatform: string;
  slideId?: string | null;
}) {
  selectedSlideId.value = digital.slideId ?? '';
  const material = workspace.value?.materialTree.specimens
    .map((specimen) => ({
      specimen,
      block: specimen.blocks.find((block) =>
        block.slides.some((slide) => slide.slideId === digital.slideId),
      ),
      slide: [...specimen.directSlides, ...specimen.blocks.flatMap((block) => block.slides)].find(
        (slide) => slide.slideId === digital.slideId,
      ),
    }))
    .find((item) => item.slide || item.block);
  selectedViewer.value = {
    ...digital,
    context: {
      caseNo: workspace.value?.caseSummary.pathologyNo ?? '',
      specimenCode: material?.specimen.specimenCode,
      blockCode: material?.block?.blockCode,
      slideCode: material?.slide?.slideCode,
      digitalSlideId: digital.digitalSlideId,
    },
  };
  void loadViewerReview(digital.digitalSlideId);
  activeContext.value = 'digital';
}

const viewerDigitalSlides = computed(() => workspace.value?.digitalSlides ?? []);

function selectSlide(slideId: string) {
  selectedSlideId.value = slideId;
  const digital = viewerDigitalSlides.value.find((item) => item.slideId === slideId);
  if (digital) openViewer(digital);
  else selectedViewer.value = null;
  activeContext.value = 'slides';
}

function technicalResultSlideId(item: V2TechnicalItem) {
  return (
    item.targets.find((target) => target.targetType === 'SLIDE')?.targetObjectId ??
    item.outputs.find((output) => output.outputKind === 'SLIDE')?.outputId ??
    ''
  );
}

function openTechnicalResult(item: V2TechnicalItem) {
  const slideId = technicalResultSlideId(item);
  if (slideId) selectSlide(slideId);
}

function digitalSlideLabel(digital: { slideId?: string | null; digitalSlideId: string }) {
  const slide = allSlides.value.find((item) => item.slideId === digital.slideId);
  return slide?.slideCode ?? digital.digitalSlideId.slice(0, 8);
}

function selectViewerOffset(offset: number) {
  if (!viewerDigitalSlides.value.length) return;
  const current = viewerDigitalSlides.value.findIndex(
    (item) => item.digitalSlideId === selectedViewer.value?.digitalSlideId,
  );
  const nextIndex =
    current < 0
      ? 0
      : (current + offset + viewerDigitalSlides.value.length) % viewerDigitalSlides.value.length;
  const next = viewerDigitalSlides.value[nextIndex];
  if (next) openViewer(next);
}

async function loadViewerReview(digitalSlideId: string) {
  try {
    const [annotations, measurements, screenshots] = await Promise.all([
      getV2DigitalAnnotations(digitalSlideId),
      getV2DigitalMeasurements(digitalSlideId),
      getV2DigitalScreenshots(digitalSlideId),
    ]);
    if (selectedViewer.value?.digitalSlideId !== digitalSlideId) return;
    viewerAnnotations.value = annotations;
    viewerMeasurements.value = measurements;
    viewerScreenshots.value = screenshots;
  } catch {
    if (selectedViewer.value?.digitalSlideId !== digitalSlideId) return;
    viewerAnnotations.value = [];
    viewerMeasurements.value = [];
    viewerScreenshots.value = [];
  }
}

function startViewerAnnotation() {
  if (!viewerAnnotationNote.value.trim()) {
    error.value = '请先填写标注说明，再在图像上选择位置。';
    return;
  }
  error.value = '';
  if (!imageViewer.value?.startAnnotation()) {
    error.value = '当前阅片器不支持页面内标注，请在可交互的数字切片中操作。';
  }
}

function startViewerMeasurement() {
  error.value = '';
  if (!imageViewer.value?.startMeasurement()) {
    error.value = '当前阅片器不支持页面内测量，请在可交互的数字切片中操作。';
  }
}

async function saveViewerAnnotation(geometry: {
  x: number;
  y: number;
  coordinateSystem: 'NORMALIZED_IMAGE' | 'NORMALIZED_VIEWPORT';
  viewport: unknown;
}) {
  const digital = selectedViewer.value;
  if (!digital || !viewerAnnotationNote.value.trim()) return;
  viewerReviewBusy.value = true;
  error.value = '';
  try {
    await createV2DigitalAnnotation({
      digitalSlideId: digital.digitalSlideId,
      annotationTypeCode: 'POINT',
      geometryJson: JSON.stringify(geometry),
      label: '阅片标注',
      note: viewerAnnotationNote.value.trim(),
      idempotencyKey: requestKey('viewer-annotation'),
    });
    viewerAnnotationNote.value = '';
    await loadViewerReview(digital.digitalSlideId);
    notice.value = '阅片标注已保存。';
  } catch (requestError) {
    error.value = friendlyError(requestError, '阅片标注保存失败，请稍后重试。');
  } finally {
    viewerReviewBusy.value = false;
  }
}

async function saveViewerMeasurement(geometry: {
  x1: number;
  y1: number;
  x2: number;
  y2: number;
  value: number;
  coordinateSystem: 'NORMALIZED_IMAGE' | 'NORMALIZED_VIEWPORT';
  viewport: unknown;
}) {
  const digital = selectedViewer.value;
  if (!digital) return;
  viewerReviewBusy.value = true;
  error.value = '';
  try {
    await createV2DigitalMeasurement({
      digitalSlideId: digital.digitalSlideId,
      geometryJson: JSON.stringify(geometry),
      value: geometry.value,
      unitCode: geometry.coordinateSystem === 'NORMALIZED_IMAGE' ? 'IMAGE_RATIO' : 'VIEWPORT_RATIO',
      measurementModeCode: geometry.coordinateSystem + '_COORDINATE',
      idempotencyKey: requestKey('viewer-measurement'),
    });
    await loadViewerReview(digital.digitalSlideId);
    notice.value = '阅片测量已保存（图像比例坐标）。';
  } catch (requestError) {
    error.value = friendlyError(requestError, '阅片测量保存失败，请稍后重试。');
  } finally {
    viewerReviewBusy.value = false;
  }
}

async function saveViewerScreenshot() {
  const digital = selectedViewer.value;
  if (!digital) return;
  viewerReviewBusy.value = true;
  error.value = '';
  try {
    const captured = await imageViewer.value?.captureCurrentView();
    if (!captured) throw new Error('当前阅片器无法导出图像，请检查切片资源的跨域访问设置');
    const imageDataBase64 = captured.capture.dataUrl.split(',', 2)[1];
    if (!imageDataBase64) throw new Error('截图内容为空');
    await saveV2DigitalScreenshot({
      digitalSlideId: digital.digitalSlideId,
      viewportJson: JSON.stringify({
        capturedAt: new Date().toISOString(),
        mode: 'CURRENT_VIEW',
        viewport: captured.viewport,
      }),
      mediaType: captured.capture.mediaType,
      imageDataBase64,
      idempotencyKey: requestKey('viewer-screenshot'),
    });
    await loadViewerReview(digital.digitalSlideId);
    notice.value = '当前阅片视野截图已保存。';
  } catch (requestError) {
    error.value = friendlyError(requestError, '截图保存失败，请稍后重试。');
  } finally {
    viewerReviewBusy.value = false;
  }
}

function applyFocus() {
  if (!workspace.value || !props.focusKind) return;
  if (props.focusKind === 'patient-history') {
    activeContext.value = 'history';
    return;
  }
  if (props.focusKind === 'report') {
    activeSupportPanel.value = 'reports';
    return;
  }
  if (props.focusKind === 'slide' && props.focusId) {
    selectSlide(props.focusId);
  }
}

function toggleSupportPanel(panel: SupportPanel) {
  activeSupportPanel.value = activeSupportPanel.value === panel ? '' : panel;
}

function navigateCase(offset: number) {
  const current = workbenchCases.value.findIndex((item) => item.caseId === caseId.value);
  if (current < 0) return;
  const next = workbenchCases.value[current + offset];
  if (next) emit('navigate', diagnosisPath(next.caseId));
}

async function completeAndNext() {
  const nextId = nextCaseId.value;
  if (!nextId || !completionAllowed.value) return;
  await complete();
  if (!error.value) emit('navigate', diagnosisPath(nextId));
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
  if (event.altKey && event.key === 'ArrowLeft') {
    event.preventDefault();
    selectViewerOffset(-1);
  }
  if (event.altKey && event.key === 'ArrowRight') {
    event.preventDefault();
    selectViewerOffset(1);
  }
  if (event.altKey && event.key === 'ArrowUp') {
    event.preventDefault();
    navigateCase(-1);
  }
  if (event.altKey && event.key === 'ArrowDown') {
    event.preventDefault();
    navigateCase(1);
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleShortcut);
  void loadDoctors().then(() => setNextResponsibilityDefaults());
});
onUnmounted(() => window.removeEventListener('keydown', handleShortcut));
</script>

<template>
  <!-- Legacy layout retained as a reference for the focused redesign.
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
        <V2CaseHeader
          :case-id="workspace.caseSummary.caseId"
          :pathology-no="workspace.caseSummary.pathologyNo"
          :patient-reference="workspace.patient.patientReference"
          :visit-reference="workspace.patient.visitReference"
          :business-type-code="workspace.caseSummary.businessTypeCode"
          :current-responsibility="responsibilitySummary"
          :report-status="reportStatus"
          :progress="productionSummary"
          @open-case="emit('navigate', `/v2/cases/${workspace.caseSummary.caseId}`)"
        >
          <template #actions>
            <button class="secondary-button" type="button" @click="historyDrawerOpen = true">
              历史记录
            </button>
          </template>
        </V2CaseHeader>

        <p v-if="error" class="feedback error diagnosis-feedback" role="alert">{{ error }}</p>
        <p v-if="notice" class="feedback success diagnosis-feedback" role="status">{{ notice }}</p>

        <DiagnosisWorkspaceShell>
          <div class="diagnosis-layout">
            <CaseEvidencePanel>
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
                      <dt>业务类型</dt>
                      <dd>{{ businessTypeName(workspace.caseSummary.businessTypeCode) }}</dd>
                    </div>
                  </dl>
                  <ul v-else-if="activeContext === 'specimens'" class="context-material-list">
                    <li
                      v-for="specimen in workspace.materialTree.specimens"
                      :key="specimen.specimenId"
                    >
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
                      @click="openViewer(digital)"
                    >
                      <span
                        ><strong>数字切片</strong><small>{{ digital.sourcePlatform }}</small></span
                      ><span>打开 →</span>
                    </button>
                    <p v-if="!(workspace.digitalSlides ?? []).length" class="muted">
                      当前没有数字切片。
                    </p>
                  </div>
                  <div v-else-if="activeContext === 'history'" class="context-history-list">
                    <article
                      v-for="item in patientHistory"
                      :key="item.caseId"
                      class="patient-history-item"
                    >
                      <time>{{ formatDateTime(item.occurredAt) }}</time>
                      <div>
                        <strong>{{ item.pathologyNo }}</strong>
                        <span>{{ item.businessTypeName }}</span>
                        <small
                          >{{ item.diagnosisSummary || '暂无诊断摘要' }} ·
                          {{ item.reportStatus === 'EFFECTIVE' ? '已签发' : '未签发' }}</small
                        >
                      </div>
                      <div class="inline-actions">
                        <button
                          v-if="item.reportId"
                          class="text-button"
                          type="button"
                          @click="
                            emit('navigate', `/v2/reports/${item.caseId}?reportId=${item.reportId}`)
                          "
                        >
                          打开报告
                        </button>
                        <button
                          v-if="item.digitalSlideId"
                          class="text-button"
                          type="button"
                          @click="
                            emit(
                              'navigate',
                              `/v2/digital-slides/${item.caseId}?slideId=${item.digitalSlideId}`,
                            )
                          "
                        >
                          查看旧切片
                        </button>
                      </div>
                    </article>
                    <p v-if="!patientHistory.length" class="muted">
                      当前患者还没有其他历史病理记录。
                    </p>
                    <button
                      class="secondary-button"
                      type="button"
                      @click="historyDrawerOpen = true"
                    >
                      查看当前病例完整历史
                    </button>
                  </div>
                  <div v-else class="empty-state compact">
                    <strong>暂无历史病理记录</strong><span>患者历史接入后显示在这里。</span>
                  </div>
                </section>
              </aside>
            </CaseEvidencePanel>

            <main class="diagnosis-editor-stage">
              <ImageViewerPanel
                v-if="selectedViewer"
                class="diagnosis-viewer-panel workspace-panel"
                aria-label="数字切片查看"
              >
                <header class="panel-title-row">
                  <div>
                    <p class="section-kicker">材料证据</p>
                    <h2>数字切片</h2>
                  </div>
                  <button class="text-button" type="button" @click="selectedViewer = null">
                    收起阅片
                  </button>
                </header>
                <div class="diagnosis-slide-strip" aria-label="数字切片列表">
                  <button
                    v-for="digital in viewerDigitalSlides"
                    :key="digital.digitalSlideId"
                    type="button"
                    :class="{ active: selectedViewer.digitalSlideId === digital.digitalSlideId }"
                    @click="openViewer(digital)"
                  >
                    <strong>{{ digitalSlideLabel(digital) }}</strong>
                    <small>{{ digital.sourcePlatform }}</small>
                  </button>
                  <button class="text-button" type="button" @click="selectViewerOffset(-1)">
                    上一张
                  </button>
                  <button class="text-button" type="button" @click="selectViewerOffset(1)">
                    下一张
                  </button>
                </div>
                <V2ImageViewer
                  ref="imageViewer"
                  :source="selectedViewer.viewerReference"
                  :label="selectedViewer.slideId ? `玻片 ${selectedViewer.slideId}` : '数字切片'"
                  :source-platform="selectedViewer.sourcePlatform"
                  :context="selectedViewer.context"
                  @annotation="saveViewerAnnotation"
                  @measurement="saveViewerMeasurement"
                />
              </ImageViewerPanel>
              <DiagnosisEditor>
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
                      {{ templateComponentLabel(component) }}
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
                      {{ templateComponentLabel(component) }}
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
                    <label
                      v-else-if="componentType(component) === 'BOOLEAN'"
                      class="checkbox-label"
                    >
                      <input
                        type="checkbox"
                        :checked="Boolean(structuredValue(component))"
                        :disabled="component.readOnly"
                        @change="updateStructuredValue(component.code, eventCheckedValue($event))"
                      />
                      {{ templateComponentLabel(component) }}
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
              </DiagnosisEditor>
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
                        workspace.responsibilityChain.some(
                          (item) => item.role === role && item.current,
                        )
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
                          workspace.responsibilityChain.find((item) => item.role === role)
                            ?.doctorId,
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
                  <span v-if="technicalAttentionCount" class="status-pill warning"
                    >{{ technicalAttentionCount }} 项新结果</span
                  ><span v-else-if="technicalReturnedCount" class="status-pill success"
                    >结果已返回</span
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
                <div
                  v-for="order in workspace.technicalOrders"
                  :key="`results-${order.orderId}`"
                  class="technical-item-attention-list"
                >
                  <div
                    v-for="item in order.items.filter((candidate) => candidate.result)"
                    :key="item.itemId"
                    class="technical-attention-row"
                  >
                    <span
                      ><strong>{{ item.projectName }}</strong
                      ><small>{{
                        item.result ? molecularResultSummary(item.result.resultData) : ''
                      }}</small></span
                    >
                    <span
                      v-if="technicalResultAcknowledged(item.itemId)"
                      class="status-pill success"
                      >已查看</span
                    >
                    <button
                      v-else-if="canAcknowledgeTechnicalResults"
                      class="secondary-button"
                      type="button"
                      :disabled="submitting"
                      @click="acknowledgeResult(item.itemId)"
                    >
                      标记已查看
                    </button>
                    <span v-else class="muted">当前责任医生查看</span>
                  </div>
                </div>
                <p v-if="!workspace.technicalOrders.length" class="muted">当前没有技术医嘱。</p>
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
                      ><strong>{{ reportLabel(report.reportId) }} · {{ report.reportNo }}</strong
                      ><small
                        >{{ report.supplemental ? '补充报告' : '正式报告' }} ·
                        {{ report.status === 'EFFECTIVE' ? '生效' : '已撤回' }} ·
                        {{ report.signedBy }} · {{ formatDateTime(report.signedAt) }}</small
                      ><small v-if="report.withdrawalReason"
                        >撤回原因：{{ report.withdrawalReason }}</small
                      ></span
                    >
                    <a :href="getV2ReportPdfUrl(report.reportId)" target="_blank" rel="noreferrer"
                      >PDF</a
                    >
                    <button
                      v-if="report.status === 'EFFECTIVE'"
                      class="text-button"
                      type="button"
                      @click="openEncryptedPdf(report.reportId, report.reportNo)"
                    >
                      加密下载
                    </button>
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
                    v-for="report in workspace.reports.filter(
                      (item) => item.status === 'EFFECTIVE',
                    )"
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
                  <label
                    >补充内容 <textarea v-model="supplementalContent" rows="3"></textarea>
                  </label>
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
        </DiagnosisWorkspaceShell>

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

      <div
        v-if="technicalPanelOpen"
        class="drawer-backdrop"
        @click.self="technicalPanelOpen = false"
      >
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
      <V2HistoryDrawer
        :open="historyDrawerOpen"
        :case-id="workspace?.caseSummary.caseId"
        :entries="timelineEntries"
        title="病例历史"
        target-label="当前病例"
        @close="historyDrawerOpen = false"
      />
  </section>
  -->

  <section class="diagnosis-page diagnosis-focused-page" aria-label="诊断工作区">
    <div v-if="loading" class="diagnosis-loading list-skeleton" aria-label="正在加载诊断工作区">
      <span></span><span></span><span></span>
    </div>

    <section v-else-if="!workspace" class="diagnosis-pool-page">
      <header class="page-heading compact-heading">
        <div>
          <p class="section-kicker">诊断</p>
          <h2>待接诊</h2>
          <p>选择病例后直接进入阅片和诊断。</p>
        </div>
      </header>
      <p v-if="error" class="feedback error" role="alert">{{ error }}</p>
      <div v-if="!publicPool.length" class="empty-state workspace-panel">
        <strong>当前没有待接诊病例</strong><span>制片完成的病例会自动进入这里。</span>
      </div>
      <div v-else class="workspace-panel compact-table" role="table" aria-label="待接诊病例">
        <div
          v-for="item in publicPool"
          :key="item.caseId"
          class="table-row diagnosis-pool-row"
          role="row"
        >
          <strong>{{ item.pathologyNo }}</strong>
          <span>{{ businessTypeName(item.businessTypeCode) }}</span>
          <button
            class="text-button"
            type="button"
            @click="emit('navigate', '/v2/cases/' + item.caseId + '?focus=diagnosis')"
          >
            进入诊断
          </button>
          <button
            class="secondary-button"
            type="button"
            :disabled="submitting"
            @click="autoAssign(item.caseId)"
          >
            自动分诊
          </button>
        </div>
      </div>
    </section>

    <template v-else>
      <V2CaseHeader
        :case-id="workspace.caseSummary.caseId"
        :pathology-no="workspace.caseSummary.pathologyNo"
        :patient-reference="workspace.patient.patientReference"
        :visit-reference="workspace.patient.visitReference"
        :business-type-code="workspace.caseSummary.businessTypeCode"
        :current-work="currentRole ? responsibilityName(currentRole) + '诊断' : '诊断与阅片'"
        :report-status="reportStatus"
        :progress="productionSummary"
        :back-label="backLabel"
        @open-case="emit('navigate', backTarget)"
        @open-overview="emit('navigate', caseOverviewTarget)"
      >
        <template #actions>
          <div class="diagnosis-header-actions" aria-label="诊断主要操作">
            <button
              class="secondary-button"
              type="button"
              :disabled="submitting"
              @click="toggleFavorite"
            >
              {{ caseFavorite ? '取消收藏' : '收藏病例' }}
            </button>
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
              <button
                v-if="workspace.actions.canAssign"
                class="secondary-button"
                type="button"
                :disabled="!assignmentDoctor || submitting"
                @click="assign"
              >
                分配
              </button>
              <button
                v-if="workspace.actions.canAssign"
                class="secondary-button"
                type="button"
                :disabled="submitting"
                @click="autoAssign()"
              >
                自动分诊
              </button>
            </template>
            <template v-else>
              <button
                v-if="canEdit"
                class="secondary-button"
                type="button"
                :disabled="submitting"
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
                v-if="workspace.actions.canSignOut"
                class="primary-button"
                type="button"
                :disabled="submitting"
                @click="signOutReport"
              >
                签发
              </button>
              <button
                v-if="workspace.actions.canPreview"
                class="secondary-button"
                type="button"
                :disabled="submitting"
                @click="previewReport"
              >
                报告预览
              </button>
              <button
                v-if="nextCaseId && completionAllowed"
                class="secondary-button"
                type="button"
                :disabled="submitting"
                @click="completeAndNext"
              >
                完成并下一例
              </button>
            </template>
          </div>
        </template>
      </V2CaseHeader>

      <p v-if="error" class="feedback error diagnosis-feedback" role="alert">{{ error }}</p>
      <p v-if="notice" class="feedback success diagnosis-feedback" role="status">{{ notice }}</p>

      <DiagnosisWorkspaceShell>
        <div class="diagnosis-focused-layout">
          <aside class="diagnosis-material-panel" aria-label="材料与玻片">
            <header class="diagnosis-column-heading">
              <p class="section-kicker">材料 / 玻片</p>
              <h2>{{ allSlides.length }} 张玻片</h2>
            </header>
            <div class="diagnosis-material-list">
              <section
                v-for="specimen in workspace.materialTree.specimens"
                :key="specimen.specimenId"
                class="diagnosis-specimen-group"
              >
                <strong>{{ specimen.specimenCode }}</strong>
                <div
                  v-for="block in specimen.blocks"
                  :key="block.blockId"
                  class="diagnosis-block-group"
                >
                  <span class="material-parent-label">{{ block.blockCode }}</span>
                  <button
                    v-for="slide in block.slides"
                    :key="slide.slideId"
                    type="button"
                    class="diagnosis-slide-button"
                    :class="{ active: selectedSlideId === slide.slideId }"
                    @click="selectSlide(slide.slideId)"
                  >
                    <span
                      ><strong>{{ slide.slideCode }}</strong
                      ><small>{{ slide.slideType }}</small></span
                    >
                    <span class="slide-state">{{
                      viewerDigitalSlides.some((digital) => digital.slideId === slide.slideId)
                        ? 'WSI'
                        : '暂无数字切片'
                    }}</span>
                  </button>
                </div>
                <div
                  v-for="slide in specimen.directSlides"
                  :key="slide.slideId"
                  class="diagnosis-block-group"
                >
                  <button
                    type="button"
                    class="diagnosis-slide-button"
                    :class="{ active: selectedSlideId === slide.slideId }"
                    @click="selectSlide(slide.slideId)"
                  >
                    <span
                      ><strong>{{ slide.slideCode }}</strong
                      ><small>{{ slide.slideType }}</small></span
                    >
                    <span class="slide-state">{{
                      viewerDigitalSlides.some((digital) => digital.slideId === slide.slideId)
                        ? 'WSI'
                        : '暂无数字切片'
                    }}</span>
                  </button>
                </div>
              </section>
              <p v-if="!allSlides.length" class="empty-state compact">当前病例暂无玻片。</p>
            </div>
          </aside>

          <main class="diagnosis-viewer-column" aria-label="WSI 阅片主区域">
            <ImageViewerPanel>
              <header class="diagnosis-viewer-heading">
                <div>
                  <p class="section-kicker">阅片</p>
                  <h2>WSI Viewer</h2>
                </div>
                <span class="muted">{{
                  selectedViewer ? digitalSlideLabel(selectedViewer) : '当前玻片'
                }}</span>
              </header>
              <div
                v-if="viewerDigitalSlides.length"
                class="diagnosis-slide-strip"
                aria-label="数字切片列表"
              >
                <button
                  v-for="digital in viewerDigitalSlides"
                  :key="digital.digitalSlideId"
                  type="button"
                  :class="{ active: selectedViewer?.digitalSlideId === digital.digitalSlideId }"
                  @click="openViewer(digital)"
                >
                  <strong>{{ digitalSlideLabel(digital) }}</strong
                  ><small>{{ digital.sourcePlatform }}</small>
                </button>
              </div>
              <div v-if="selectedViewer" class="diagnosis-viewer-host">
                <V2ImageViewer
                  ref="imageViewer"
                  :source="selectedViewer.viewerReference"
                  :label="selectedViewer.slideId ? '玻片 ' + selectedViewer.slideId : '数字切片'"
                  :source-platform="selectedViewer.sourcePlatform"
                  :context="selectedViewer.context"
                  @annotation="saveViewerAnnotation"
                  @measurement="saveViewerMeasurement"
                />
              </div>
              <div v-if="selectedViewer" class="viewer-review-tools" aria-label="阅片记录">
                <input
                  v-model="viewerAnnotationNote"
                  type="text"
                  placeholder="标注说明"
                  aria-label="标注说明"
                  @keyup.enter="startViewerAnnotation"
                />
                <button type="button" :disabled="viewerReviewBusy" @click="startViewerAnnotation">
                  在图像上标注
                </button>
                <button type="button" :disabled="viewerReviewBusy" @click="startViewerMeasurement">
                  在图像上测量
                </button>
                <button type="button" :disabled="viewerReviewBusy" @click="saveViewerScreenshot">
                  保存当前截图
                </button>
              </div>
              <div
                v-if="
                  selectedViewer &&
                  (viewerAnnotations.length ||
                    viewerMeasurements.length ||
                    viewerScreenshots.length)
                "
                class="viewer-review-history"
                aria-label="阅片记录历史"
              >
                <span v-for="item in viewerAnnotations" :key="item.annotationId">
                  标注 · {{ item.note || item.label || '未命名' }}
                </span>
                <span v-for="item in viewerMeasurements" :key="item.measurementId">
                  测量 · {{ (item.value * 100).toFixed(1) }}% 归一化视距
                </span>
                <a
                  v-for="item in viewerScreenshots"
                  :key="item.screenshotId"
                  :href="getV2DigitalScreenshotContentUrl(item.screenshotId)"
                  target="_blank"
                  rel="noreferrer"
                >
                  查看截图 · {{ formatDateTime(item.createdAt) }}
                </a>
              </div>
              <div v-if="!selectedViewer" class="diagnosis-no-viewer" aria-live="polite">
                <strong>{{
                  allSlides.length ? '当前玻片暂无数字切片' : '当前病例暂无玻片'
                }}</strong>
                <p>仍可从材料列表切换其他玻片。</p>
              </div>
              <footer class="diagnosis-viewer-footer">
                <button
                  class="text-button"
                  type="button"
                  :disabled="!viewerDigitalSlides.length"
                  @click="selectViewerOffset(-1)"
                >
                  上一张
                </button>
                <span>缩放 · 平移 · 全屏 · 缩略导航器</span>
                <button
                  class="text-button"
                  type="button"
                  :disabled="!viewerDigitalSlides.length"
                  @click="selectViewerOffset(1)"
                >
                  下一张
                </button>
              </footer>
            </ImageViewerPanel>
          </main>

          <aside class="diagnosis-form-column" aria-label="诊断编辑">
            <DiagnosisEditor>
              <header class="diagnosis-column-heading">
                <p class="section-kicker">诊断</p>
                <h2>{{ editorTitle }}</h2>
              </header>
              <div v-if="!workspace.diagnosis" class="empty-state compact">
                <strong>病例尚未接诊</strong><span>接诊后可以开始填写。</span>
              </div>
              <fieldset v-else :disabled="!canEdit || submitting" class="diagnosis-fields">
                <legend class="visually-hidden">诊断内容</legend>
                <template v-for="component in templateComponents" :key="component.code">
                  <label v-if="['TEXT', 'TEXTAREA'].includes(componentType(component))">
                    {{ templateComponentLabel(component) }}
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
                    {{ templateComponentLabel(component) }}
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
                  <label v-else-if="componentType(component) === 'BOOLEAN'" class="checkbox-label"
                    ><input
                      type="checkbox"
                      :checked="Boolean(structuredValue(component))"
                      :disabled="component.readOnly"
                      @change="updateStructuredValue(component.code, eventCheckedValue($event))"
                    />{{ templateComponentLabel(component) }}</label
                  >
                </template>
                <label v-if="!hasTemplateComponent('microscopicDescription')"
                  >镜下所见<textarea
                    v-model="microscopicDescription"
                    class="microscopic-text"
                    placeholder="记录镜下形态、结构及必要的阴性所见"
                  ></textarea>
                </label>
                <label v-if="!hasTemplateComponent('diagnosisText')"
                  >病理诊断<textarea
                    v-model="diagnosisText"
                    class="diagnosis-text"
                    placeholder="输入正式病理诊断"
                  ></textarea>
                </label>
                <label v-if="!hasTemplateComponent('comment')"
                  >备注<textarea v-model="comment" rows="2" placeholder="可选说明"></textarea>
                </label>
              </fieldset>
            </DiagnosisEditor>
          </aside>
        </div>

        <section class="diagnosis-support-panel" aria-label="辅助信息">
          <nav class="diagnosis-support-tabs" role="tablist" aria-label="辅助信息">
            <button
              type="button"
              role="tab"
              :aria-selected="activeSupportPanel === 'clinical'"
              :class="{ active: activeSupportPanel === 'clinical' }"
              @click="toggleSupportPanel('clinical')"
            >
              临床信息
            </button>
            <button
              type="button"
              role="tab"
              :aria-selected="activeSupportPanel === 'technical'"
              :class="{ active: activeSupportPanel === 'technical' }"
              @click="toggleSupportPanel('technical')"
            >
              技术结果<span v-if="technicalReturnedCount" class="count-pill">{{
                technicalReturnedCount
              }}</span>
            </button>
            <button
              type="button"
              role="tab"
              :aria-selected="activeSupportPanel === 'caseSupport'"
              :class="{ active: activeSupportPanel === 'caseSupport' }"
              @click="toggleSupportPanel('caseSupport')"
            >
              会诊与随访<span
                v-if="caseConsultations.length + caseFollowUps.length"
                class="count-pill"
                >{{ caseConsultations.length + caseFollowUps.length }}</span
              >
            </button>
            <button
              type="button"
              role="tab"
              :aria-selected="activeSupportPanel === 'history'"
              :class="{ active: activeSupportPanel === 'history' }"
              @click="toggleSupportPanel('history')"
            >
              历史病例
            </button>
            <button
              type="button"
              role="tab"
              :aria-selected="activeSupportPanel === 'reports'"
              :class="{ active: activeSupportPanel === 'reports' }"
              @click="toggleSupportPanel('reports')"
            >
              历史报告
            </button>
            <button
              type="button"
              role="tab"
              :aria-selected="activeSupportPanel === 'audit'"
              :class="{ active: activeSupportPanel === 'audit' }"
              @click="toggleSupportPanel('audit')"
            >
              签审记录
            </button>
          </nav>
          <div v-if="activeSupportPanel === 'clinical'" class="support-panel-content">
            <dl class="support-facts">
              <div>
                <dt>申请号</dt>
                <dd>{{ workspace.application.externalApplicationId }}</dd>
              </div>
              <div>
                <dt>业务类型</dt>
                <dd>{{ businessTypeName(workspace.caseSummary.businessTypeCode) }}</dd>
              </div>
              <div>
                <dt>历史病例</dt>
                <dd>{{ patientHistory.length }} 条</dd>
              </div>
            </dl>
          </div>
          <div v-else-if="activeSupportPanel === 'technical'" class="support-panel-content">
            <div
              v-if="molecularResults.length || workspace.technicalOrders.length"
              class="support-result-list"
            >
              <div
                v-for="result in molecularResults"
                :key="result.resultId"
                class="support-result-row"
              >
                <strong>{{ result.resultCode }}</strong
                ><span>{{ molecularResultSummary(result.resultData) }}</span
                ><span>已完成</span>
              </div>
              <template v-for="order in workspace.technicalOrders" :key="order.orderId">
                <div
                  v-for="item in order.items.filter((candidate) => candidate.result)"
                  :key="item.itemId"
                  class="support-result-row"
                >
                  <strong>{{ item.projectName }}</strong
                  ><span>{{
                    item.result ? molecularResultSummary(item.result.resultData) : ''
                  }}</span
                  ><button
                    v-if="technicalResultSlideId(item)"
                    class="text-button"
                    type="button"
                    @click="openTechnicalResult(item)"
                  >
                    定位玻片</button
                  ><span v-else>已完成</span
                  ><button
                    v-if="
                      !technicalResultAcknowledged(item.itemId) && canAcknowledgeTechnicalResults
                    "
                    class="text-button"
                    type="button"
                    @click="acknowledgeResult(item.itemId)"
                  >
                    标记已查看</button
                  ><span v-else>已查看</span>
                </div>
              </template>
            </div>
            <p v-else class="muted">当前没有技术结果。</p>
          </div>
          <div
            v-else-if="activeSupportPanel === 'caseSupport'"
            class="support-panel-content diagnosis-case-support"
          >
            <section class="case-support-section" aria-label="随访">
              <header>
                <div><strong>病例随访</strong><small>计划和完成结果均独立留痕</small></div>
              </header>
              <div class="report-inline-form case-support-create-form">
                <label>随访日期<input v-model="followUpDate" type="date" /></label>
                <label
                  >随访计划<input v-model="followUpPlan" placeholder="填写随访目的和计划"
                /></label>
                <button
                  class="primary-button"
                  type="button"
                  :disabled="!followUpDate || !followUpPlan.trim() || submitting"
                  @click="createFollowUp"
                >
                  新增随访
                </button>
              </div>
              <div class="support-history-list">
                <article v-for="item in caseFollowUps" :key="item.followUpId">
                  <strong>{{ item.followUpDate }} · {{ item.plan }}</strong>
                  <span v-if="item.completedAt">{{ item.result || item.content || '已完成' }}</span>
                  <small>{{
                    item.completedAt ? '已完成 ' + formatDateTime(item.completedAt) : '待随访'
                  }}</small>
                  <div
                    v-if="!item.completedAt && followUpCompletion[item.followUpId]"
                    class="report-inline-form"
                  >
                    <label
                      >随访内容<input
                        v-model="followUpDraft(item.followUpId).content"
                        placeholder="沟通或复查内容"
                    /></label>
                    <label
                      >随访结果<input
                        v-model="followUpDraft(item.followUpId).result"
                        placeholder="结果或后续安排"
                    /></label>
                    <button
                      class="secondary-button"
                      type="button"
                      :disabled="
                        submitting ||
                        (!followUpDraft(item.followUpId).content.trim() &&
                          !followUpDraft(item.followUpId).result.trim())
                      "
                      @click="completeFollowUp(item)"
                    >
                      完成随访
                    </button>
                  </div>
                </article>
                <p v-if="!caseFollowUps.length" class="muted">当前没有随访计划。</p>
              </div>
            </section>
            <section class="case-support-section" aria-label="科内会诊">
              <header>
                <div><strong>科内会诊</strong><small>记录参与人、讨论和结论</small></div>
              </header>
              <div class="report-inline-form case-support-create-form">
                <label
                  >参与医生<input
                    v-model="consultationDraft.participantRefs"
                    placeholder="多个医生可用逗号分隔"
                /></label>
                <label
                  >会诊原因<input v-model="consultationDraft.reason" placeholder="填写发起原因"
                /></label>
                <label
                  >讨论记录<textarea v-model="consultationDraft.discussion" rows="2"></textarea>
                </label>
                <label
                  >会诊结论<textarea v-model="consultationDraft.conclusion" rows="2"></textarea>
                </label>
                <label>备注<input v-model="consultationDraft.note" /></label>
                <button
                  class="primary-button"
                  type="button"
                  :disabled="
                    !consultationDraft.participantRefs.trim() ||
                    !consultationDraft.reason.trim() ||
                    submitting
                  "
                  @click="createConsultation"
                >
                  保存会诊记录
                </button>
              </div>
              <div class="support-history-list">
                <article v-for="item in caseConsultations" :key="item.consultationId">
                  <strong>{{ item.reason }}</strong>
                  <span
                    >{{ item.participantRefs }} ·
                    {{ item.conclusion || item.discussion || '待补充结论' }}</span
                  >
                  <small
                    >{{ formatDateTime(item.consultationAt) }} · 发起 {{ item.initiatorRef }}</small
                  >
                </article>
                <p v-if="!caseConsultations.length" class="muted">当前没有科内会诊记录。</p>
              </div>
            </section>
          </div>
          <div
            v-else-if="activeSupportPanel === 'history'"
            class="support-panel-content support-history-list"
          >
            <article v-for="item in patientHistory" :key="item.caseId">
              <strong>{{ item.pathologyNo }}</strong
              ><span
                >{{ item.businessTypeName }} · {{ item.diagnosisSummary || '暂无诊断摘要' }}</span
              ><small>{{ formatDateTime(item.occurredAt) }}</small>
            </article>
            <p v-if="!patientHistory.length" class="muted">当前患者还没有其他历史病理记录。</p>
          </div>
          <div
            v-else-if="activeSupportPanel === 'reports'"
            class="support-panel-content support-report-list"
          >
            <div class="support-facts">
              <span
                ><small>当前状态</small><strong>{{ reportStatus }}</strong></span
              ><span
                ><small>制片</small><strong>{{ productionSummary }}</strong></span
              >
            </div>
            <article v-for="report in workspace.reports" :key="report.reportId">
              <strong>{{ reportLabel(report.reportId) }} · {{ report.reportNo }}</strong
              ><span
                >{{ report.supplemental ? '补充报告' : '正式报告' }} ·
                {{ report.status === 'EFFECTIVE' ? '生效' : '已撤回' }}</span
              ><a :href="getV2ReportPdfUrl(report.reportId)" target="_blank" rel="noreferrer"
                >PDF</a
              >
              <button
                v-if="report.status === 'EFFECTIVE'"
                class="text-button"
                type="button"
                @click="openEncryptedPdf(report.reportId, report.reportNo)"
              >
                加密下载
              </button>
            </article>
            <div class="inline-actions">
              <button
                v-if="workspace.actions.canWithdraw"
                class="text-button"
                type="button"
                @click="withdrawalOpen = !withdrawalOpen"
              >
                撤回</button
              ><button
                v-if="workspace.actions.canSupplement"
                class="text-button"
                type="button"
                @click="supplementalOpen = !supplementalOpen"
              >
                补充报告
              </button>
            </div>
            <div v-if="withdrawalOpen" class="report-inline-form">
              <label>撤回原因<input v-model="withdrawalReason" /></label
              ><button
                v-for="report in workspace.reports.filter((item) => item.status === 'EFFECTIVE')"
                :key="report.reportId"
                class="danger-button"
                type="button"
                :disabled="!withdrawalReason.trim() || submitting"
                @click="withdrawReport(report.reportId)"
              >
                确认撤回
              </button>
            </div>
            <div v-if="supplementalOpen" class="report-inline-form">
              <label>补充内容<textarea v-model="supplementalContent" rows="2"></textarea></label
              ><button
                class="primary-button"
                type="button"
                :disabled="!supplementalContent.trim() || submitting"
                @click="supplementReport"
              >
                签发补充报告
              </button>
            </div>
          </div>
          <div
            v-else-if="activeSupportPanel === 'audit'"
            class="support-panel-content support-audit-list"
          >
            <article v-for="item in workspace.responsibilityChain" :key="item.responsibilityId">
              <strong>{{ responsibilityName(item.role) }}</strong
              ><span>{{ doctorName(item.doctorId) }}</span
              ><small>{{ item.completedAt ? formatDateTime(item.completedAt) : '进行中' }}</small>
            </article>
          </div>
        </section>
      </DiagnosisWorkspaceShell>
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
          />结果返回前暂不签发</label
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
            >项目<select v-model="draft.projectId" @change="syncDraftProject(index)">
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
              >材料类型<select v-model="draft.targetType" @change="syncDraftTarget(index)">
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
            ><label
              >目标材料<select v-model="draft.targetId">
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
            <label>数量<input v-model.number="draft.quantity" min="1" type="number" /></label
            ><label>备注<input v-model="draft.note" /></label>
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
            ><span
              >就诊：{{
                previewDocument.case?.visitReference ?? workspace?.patient.visitReference
              }}</span
            >
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
      </article>
      <aside class="report-preview-actions">
        <header>
          <p class="section-kicker">报告预览</p>
          <h2>签发前确认</h2>
        </header>
        <p v-if="reportPreview.valid" class="feedback success">预览有效，可以签发。</p>
        <div v-else class="feedback warning">
          <strong>暂不能签发：</strong
          ><span v-for="reason in reportPreview.blockingReasons" :key="reason"
            >{{ blockerText(reason) }}
          </span>
        </div>
        <button class="secondary-button" type="button" @click="previewOpen = false">返回诊断</button
        ><button
          class="primary-button"
          type="button"
          :disabled="!workspace?.actions.canSignOut || submitting"
          @click="signOutReport"
        >
          确认签发
        </button>
      </aside>
    </div>

    <div
      v-if="encryptedPdfReport"
      class="drawer-backdrop"
      role="dialog"
      aria-modal="true"
      aria-label="加密下载报告"
      @click.self="closeEncryptedPdf"
    >
      <aside class="technical-order-drawer encrypted-pdf-drawer">
        <header class="drawer-header">
          <div>
            <p class="section-kicker">对外安全副本</p>
            <h2>加密下载 {{ encryptedPdfReport.reportNo }}</h2>
          </div>
          <button
            class="icon-button"
            type="button"
            aria-label="关闭加密下载"
            @click="closeEncryptedPdf"
          >
            ×
          </button>
        </header>
        <p class="muted">访问密码仅用于本次 AES-256 加密，不会写入报告、日志或审计记录。</p>
        <label
          >访问密码（8–64字符）
          <input v-model="encryptedPdfPassword" type="password" autocomplete="new-password" />
        </label>
        <label
          >下载用途
          <textarea
            v-model="encryptedPdfReason"
            rows="3"
            placeholder="填写对外提供或归档用途"
          ></textarea>
        </label>
        <div class="sticky-form-actions">
          <button class="secondary-button" type="button" @click="closeEncryptedPdf">取消</button>
          <button
            class="primary-button"
            type="button"
            :disabled="
              encryptedPdfPassword.length < 8 ||
              encryptedPdfPassword.length > 64 ||
              !encryptedPdfReason.trim() ||
              submitting
            "
            @click="downloadEncryptedPdf"
          >
            生成并下载
          </button>
        </div>
      </aside>
    </div>
  </section>
</template>
