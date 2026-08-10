export type V2CaseWorkspace = {
  caseHeader: V2CaseHeader;
  materialTree: V2WorkspaceMaterialTree;
  grossings: V2WorkspaceGrossing[];
  responsibilities: V2WorkspaceResponsibility[];
  technicalOrders: V2WorkspaceTechnicalOrder[];
  digitalSlides: V2WorkspaceDigitalSlide[];
  reports: V2WorkspaceReport[];
  timeline: V2WorkspaceTimelineEntry[];
  refreshedAt: string;
};

export type V2WorkbenchItem = {
  caseId: string;
  pathologyNo: string;
  patientReference: string;
  businessTypeCode: string;
  businessTypeName: string;
  workCode: string;
  workLabel: string;
  responsibilityName: string | null;
  occurredAt: string | null;
  caseCreatedAt: string;
  availableActions: string[];
  deepLink: string;
};

export type V2WorkbenchCounts = {
  initial: number;
  review: number;
  audit: number;
  technicalResultReturned: number;
  withdrawnReport: number;
  publicPool: number;
};

export type V2WorkbenchQueues = {
  histology: number;
  dehydration: number;
  embedding: number;
  cutting: number;
  staining: number;
  coverslipping: number;
  technical: number;
  frozen: number;
  withdrawn: number;
};

export type V2MyWorkbench = {
  refreshedAt: string;
  myWork: V2WorkbenchItem[];
  publicPool: V2WorkbenchItem[];
  counts: V2WorkbenchCounts;
  queues: V2WorkbenchQueues;
};

export async function getV2MyWorkbench(): Promise<V2MyWorkbench> {
  const response = await fetch('/api/v2/my-workbench');
  const body = (await response.json()) as Partial<V2MyWorkbench> & { message?: string };
  if (!response.ok) throw new Error(body.message ?? '我的工作台暂时无法加载');
  return {
    refreshedAt: body.refreshedAt ?? new Date().toISOString(),
    myWork: body.myWork ?? [],
    publicPool: body.publicPool ?? [],
    counts: body.counts ?? {
      initial: 0,
      review: 0,
      audit: 0,
      technicalResultReturned: 0,
      withdrawnReport: 0,
      publicPool: 0,
    },
    queues: body.queues ?? {
      histology: 0,
      dehydration: 0,
      embedding: 0,
      cutting: 0,
      staining: 0,
      coverslipping: 0,
      technical: 0,
      frozen: 0,
      withdrawn: 0,
    },
  };
}

export type V2CaseHeader = {
  caseId: string;
  pathologyNo: string;
  businessTypeCode: string;
  businessTypeName: string;
  lifecycle: string;
  applicationItemCode: string;
  sourceSystemCode: string;
  applicationNo: string;
  patientReference: string;
  visitReference: string | null;
  createdAt: string;
};

export type V2WorkspaceMaterialTree = {
  caseId: string;
  pathologyNo: string;
  businessTypeCode: string;
  specimens: V2WorkspaceSpecimen[];
};

export type V2WorkspaceSpecimen = {
  specimenId: string;
  specimenNo: string;
  specimenCode: string;
  specimenKindCode: string;
  blocks: V2WorkspaceBlock[];
  directSlides: V2WorkspaceSlide[];
};

export type V2WorkspaceBlock = {
  blockId: string;
  blockCode: string;
  blockType: string;
  slides: V2WorkspaceSlide[];
};

export type V2WorkspaceSlide = {
  slideId: string;
  slideCode: string;
  slideType: string;
  sourceContextType: string;
  completedAt: string | null;
  completed: boolean;
  required: boolean;
  completedBy: string | null;
};

export type V2WorkspaceGrossing = {
  grossingId: string;
  grossingNo: string;
  sourceType: string;
  grossDescription: string;
  grossingDoctor: string;
  recorder: string;
  startedAt: string;
  completedAt: string | null;
  completedBy: string | null;
};

export type V2WorkspaceResponsibility = {
  responsibilityId: string;
  diagnosisId: string;
  roleCode: 'INITIAL' | 'REVIEW' | 'AUDIT' | string;
  doctorId: string;
  doctorName: string;
  sequenceNo: number;
  acceptedAt: string;
  completedAt: string | null;
  endedAt: string | null;
  assignmentSource: string;
  assignmentReason: string | null;
};

export type V2WorkspaceTechnicalOrder = {
  orderId: string;
  orderNo: string;
  statusCode: string;
  requiredBeforeSignOut: boolean;
  createdAt: string;
  createdBy: string;
  itemCount: number;
  resultCount: number;
};

export type V2WorkspaceDigitalSlide = {
  digitalSlideId: string;
  blockId: string | null;
  slideId: string | null;
  bindingMode: string;
  statusCode: string;
  viewerReference: string;
  sourcePlatform: string;
  updatedAt: string;
};

export type V2WorkspaceReport = {
  reportId: string;
  reportNo: string;
  natureCode: 'ORIGINAL' | 'SUPPLEMENTAL' | string;
  priorReportId: string | null;
  statusCode: string;
  signedBy: string;
  signedAt: string;
  withdrawnBy: string | null;
  withdrawnAt: string | null;
  withdrawalReason: string | null;
  pdfFileReference: string;
};

export type V2WorkspaceTimelineEntry = {
  eventId: string;
  occurredAt: string;
  actorName: string;
  actorRef: string;
  title: string;
  detail: string;
  operationCode: string;
  targetKind: string | null;
  targetId: string | null;
};

export async function getV2CaseWorkspace(caseId: string): Promise<V2CaseWorkspace> {
  const response = await fetch(`/api/v2/case-workspaces/${encodeURIComponent(caseId)}`);
  const body = (await response.json()) as
    | { message?: string; error_code?: string }
    | V2CaseWorkspace;
  if (!response.ok) {
    const error = body as { message?: string; error_code?: string };
    throw new Error(
      `${error.error_code ?? 'V2-CASE-WORKSPACE-FAILED'}: ${error.message ?? '病例信息暂时无法加载'}`,
    );
  }
  return body as V2CaseWorkspace;
}
