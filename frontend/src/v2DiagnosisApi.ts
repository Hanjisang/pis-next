export type V2ResponsibilityRole = 'INITIAL' | 'REVIEW' | 'AUDIT';

export type V2Responsibility = {
  responsibilityId: string;
  role: V2ResponsibilityRole;
  doctorId: string;
  sequence: number;
  assignmentSource: 'PUBLIC_POOL' | 'MANUAL' | 'SELF_CLAIM' | 'REASSIGN';
  assignmentReason?: string;
  acceptedAt: string;
  completedAt?: string;
  endedAt?: string;
  endReason?: string;
  version: number;
  current: boolean;
};

export type V2CaseFollowUp = {
  followUpId: string;
  caseId: string;
  followUpDate: string;
  plan: string;
  content?: string;
  result?: string;
  operatorRef: string;
  createdAt: string;
  completedAt?: string;
};

export type V2CaseConsultation = {
  consultationId: string;
  caseId: string;
  consultationAt: string;
  initiatorRef: string;
  participantRefs: string;
  reason: string;
  discussion?: string;
  conclusion?: string;
  note?: string;
  attachmentReference?: string;
  recordedByRef: string;
  createdAt: string;
};

export type V2DiagnosisWorkspace = {
  caseSummary: {
    caseId: string;
    pathologyNo: string;
    businessTypeCode: string;
    lifecycle: string;
  };
  application: {
    applicationItemCode: string;
    sourceSystemCode: string;
    externalApplicationId: string;
  };
  patient: { patientReference: string; visitReference?: string };
  materialTree: {
    caseId: string;
    caseNo: string;
    businessTypeCode: string;
    specimens: Array<{
      specimenId: string;
      specimenNo: string;
      specimenCode: string;
      specimenKindCode: string;
      blocks: Array<{
        blockId: string;
        blockCode: string;
        blockType: string;
        slides: V2MaterialSlide[];
      }>;
      directSlides: V2MaterialSlide[];
    }>;
    initialRequiredCount: number;
    initialCompletedCount: number;
    initialProductionComplete: boolean;
  };
  diagnosis?: {
    diagnosisId: string;
    templateVersionId: string;
    structuredData: string;
    microscopicDescription?: string;
    diagnosisText?: string;
    comment?: string;
    version: number;
    updatedAt: string;
  };
  templateVersion?: {
    versionId: string;
    templateId: string;
    versionNo: number;
    schemaDefinition: string;
    status: string;
    publishedAt?: string;
  };
  responsibilityChain: V2Responsibility[];
  currentResponsibility?: V2Responsibility;
  actions: {
    canClaim: boolean;
    canAssign: boolean;
    canCompleteInitial: boolean;
    canCompleteReview: boolean;
    canCompleteAudit: boolean;
    canReassign: boolean;
    readyForSignOut: boolean;
    canCreateTechnicalOrder: boolean;
    canPreview: boolean;
    canSignOut: boolean;
    canWithdraw: boolean;
    canSupplement: boolean;
  };
  molecularResults?: Array<{
    resultId: string;
    resultCode: string;
    resultData: string;
    statusCode: string;
    completedAt: string;
    completedBy: string;
  }>;
  technicalOrders: V2TechnicalOrder[];
  blockingTechnicalOrderCount: number;
  technicalOrder: { kind: string; status: string };
  report: { kind: string; status: string };
  reports: V2Report[];
  blockingReasons: string[];
  digitalSlides: Array<{
    digitalSlideId: string;
    blockId?: string | null;
    slideId?: string | null;
    statusCode: string;
    viewerReference: string;
    sourcePlatform: string;
  }>;
  refreshedAt: string;
};

export type V2Report = {
  reportId: string;
  reportNo: string;
  nature: 'ORIGINAL' | 'SUPPLEMENTAL';
  supplemental: boolean;
  status: 'EFFECTIVE' | 'WITHDRAWN';
  priorReportId?: string;
  templateVersionId: string;
  pdfFileReference: string;
  pdfContentHash: string;
  signedBy: string;
  signedAt: string;
  withdrawnAt?: string;
  withdrawalReason?: string;
};

export type V2TechnicalProject = {
  projectId: string;
  businessTypeId: string;
  projectCode: string;
  projectName: string;
  capabilityCode: string;
  outputTypeCode: string;
  enabled: boolean;
  allowedTargetTypes: string[];
  producesSlide: boolean;
  producesBlock: boolean;
  producesStructuredResult: boolean;
  requiresResult: boolean;
  deviceTypeCode?: string;
  consumableRequired: boolean;
  defaultSlideType?: string;
  parametersSchema?: string;
  resultSchema?: string;
  requiredBeforeSignOutDefault: boolean;
  configurationVersion: number;
};

export type V2TechnicalOrder = {
  orderId: string;
  orderNo: string;
  diagnosisId: string;
  caseId: string;
  caseNo?: string;
  patientReference?: string;
  status: 'PENDING' | 'EXECUTING' | 'COMPLETED' | 'CANCELLED';
  requiredBeforeSignOut: boolean;
  blocking: boolean;
  version: number;
  cancelledAt?: string;
  cancellationReason?: string;
  duplicate: boolean;
  items: V2TechnicalItem[];
};

export type V2TechnicalItem = {
  itemId: string;
  projectId: string;
  projectCode: string;
  projectName: string;
  capabilityCode: string;
  outputTypeCode: string;
  requiresResult: boolean;
  deviceTypeCode?: string;
  consumableRequired: boolean;
  quantity: number;
  status: 'PENDING' | 'EXECUTING' | 'COMPLETED';
  expectedCount: number;
  completedCount: number;
  targets: Array<{
    targetId: string;
    targetType: 'CASE' | 'SPECIMEN' | 'BLOCK' | 'SLIDE';
    targetObjectId: string;
    displayCode: string;
  }>;
  outputs: Array<{
    outputKind: 'GROSSING' | 'BLOCK' | 'SLIDE' | 'RESULT';
    outputId: string;
    occurrenceNo: number;
  }>;
  result?: { resultId: string; resultData: string; version: number; enteredAt: string };
};

export type V2MaterialSlide = {
  slideId: string;
  slideCode: string;
  slideType: string;
  sourceContextType: string;
  completedAt?: string;
  completed: boolean;
  required: boolean;
  concurrencyVersion: number;
};

async function diagnosisRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`/api/v2${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init.headers ?? {}) },
  });
  const body: unknown = await response.json();
  if (!response.ok) {
    const error = body as { error_code?: string; message?: string };
    throw new Error(
      `${error.error_code ?? 'V2-DIAGNOSIS-REQUEST-FAILED'}: ${error.message ?? '请求失败'}`,
    );
  }
  return body as T;
}

export function getV2DiagnosisWorkspace(caseId: string): Promise<V2DiagnosisWorkspace> {
  return diagnosisRequest(`/diagnosis-workspaces/${caseId}`);
}

export function createV2DigitalAnnotation(input: {
  digitalSlideId: string;
  annotationTypeCode: string;
  geometryJson: string;
  label?: string;
  note?: string;
}) {
  const { digitalSlideId, ...body } = input;
  return diagnosisRequest(`/digital-slides/${digitalSlideId}/annotations`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function createV2DigitalMeasurement(input: {
  digitalSlideId: string;
  geometryJson: string;
  value: number;
  unitCode: string;
  measurementModeCode: string;
}) {
  const { digitalSlideId, ...body } = input;
  return diagnosisRequest(`/digital-slides/${digitalSlideId}/measurements`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function saveV2DigitalScreenshot(input: {
  digitalSlideId: string;
  viewportJson: string;
  storageReference: string;
}) {
  const { digitalSlideId, ...body } = input;
  return diagnosisRequest(`/digital-slides/${digitalSlideId}/screenshots`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function getV2FrozenRoundDiagnosisWorkspace(roundId: string): Promise<V2DiagnosisWorkspace> {
  return diagnosisRequest(`/diagnosis-workspaces/frozen-rounds/${roundId}`);
}

export function createV2FrozenRoundDiagnosis(roundId: string, idempotencyKey: string) {
  return diagnosisRequest<{ diagnosisId: string; roundId: string; duplicate: boolean }>(
    `/frozen/rounds/${roundId}/diagnosis`,
    { method: 'POST', body: JSON.stringify({ idempotencyKey }) },
  );
}

export function getV2ReportPreview(diagnosisId: string, templateVersionId?: string) {
  const query = templateVersionId
    ? `?templateVersionId=${encodeURIComponent(templateVersionId)}`
    : '';
  return diagnosisRequest<{
    valid: boolean;
    blockingReasons: string[];
    templateVersionId: string;
    templateVersionNo: number;
    renderedContent: string;
    renderedContentHash: string;
    pdfContentHash: string;
  }>(`/diagnoses/${diagnosisId}/report-preview${query}`);
}

export function signOutV2Report(input: {
  diagnosisId: string;
  templateVersionId?: string;
  idempotencyKey: string;
}) {
  const { diagnosisId, ...body } = input;
  return diagnosisRequest<{ reportId: string; reportNo: string; status: string }>(
    `/diagnoses/${diagnosisId}/sign-out`,
    { method: 'POST', body: JSON.stringify(body) },
  );
}

export function withdrawV2Report(input: {
  reportId: string;
  reason: string;
  idempotencyKey: string;
}) {
  const { reportId, ...body } = input;
  return diagnosisRequest<{ reportId: string; status: string }>(`/reports/${reportId}/withdraw`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function supplementV2Report(input: {
  diagnosisId: string;
  priorReportId?: string;
  templateVersionId?: string;
  content: string;
  idempotencyKey: string;
}) {
  const { diagnosisId, ...body } = input;
  return diagnosisRequest<{ reportId: string; reportNo: string; status: string }>(
    `/diagnoses/${diagnosisId}/supplemental`,
    { method: 'POST', body: JSON.stringify(body) },
  );
}

export function getV2ReportPdfUrl(reportId: string) {
  return `/api/v2/reports/${reportId}/pdf`;
}

export function getV2TechnicalProjects(caseId?: string): Promise<V2TechnicalProject[]> {
  return diagnosisRequest(
    `/technical-projects${caseId ? `?caseId=${encodeURIComponent(caseId)}` : ''}`,
  );
}

export function createV2TechnicalProject(input: {
  businessTypeId: string;
  projectCode: string;
  projectName: string;
  capabilityCode: string;
  outputTypeCode: string;
  enabled: boolean;
  allowedTargetTypes: string;
  producesSlide: boolean;
  producesBlock: boolean;
  producesStructuredResult: boolean;
  requiresResult: boolean;
  deviceTypeCode?: string;
  consumableRequired: boolean;
  defaultSlideType?: string;
  parametersSchema?: string;
  resultSchema?: string;
  feeMapping?: string;
  displayConfiguration?: string;
  requiredBeforeSignOutDefault: boolean;
  configurationVersion: number;
}): Promise<V2TechnicalProject> {
  return diagnosisRequest('/technical-projects', { method: 'POST', body: JSON.stringify(input) });
}

export function createV2TechnicalOrder(input: {
  diagnosisId: string;
  requiredBeforeSignOut?: boolean;
  items: Array<{
    projectId: string;
    quantity: number;
    parameters: string;
    note?: string;
    targets: Array<{ targetType: 'CASE' | 'SPECIMEN' | 'BLOCK' | 'SLIDE'; targetId: string }>;
  }>;
  idempotencyKey: string;
}): Promise<V2TechnicalOrder> {
  return diagnosisRequest('/technical-orders', { method: 'POST', body: JSON.stringify(input) });
}

export function getV2TechnicalWorkbench(): Promise<{ orders: V2TechnicalOrder[] }> {
  return diagnosisRequest('/technical-workbench');
}

export function executeV2TechnicalOrder(
  orderId: string,
  idempotencyKey: string,
): Promise<V2TechnicalOrder> {
  return diagnosisRequest(`/technical-orders/${orderId}/execute`, {
    method: 'POST',
    body: JSON.stringify({ idempotencyKey }),
  });
}

export function cancelV2TechnicalOrder(input: {
  orderId: string;
  expectedVersion: number;
  reason: string;
  idempotencyKey: string;
}): Promise<V2TechnicalOrder> {
  const { orderId, ...body } = input;
  return diagnosisRequest(`/technical-orders/${orderId}/cancel`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function enterV2TechnicalResult(input: {
  itemId: string;
  resultData: string;
  expectedVersion: number;
  idempotencyKey: string;
}): Promise<V2TechnicalOrder> {
  const { itemId, ...body } = input;
  return diagnosisRequest(`/technical-order-items/${itemId}/result`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function acknowledgeV2TechnicalResult(itemId: string) {
  return diagnosisRequest<{
    itemId: string;
    acknowledgedBy: string;
    acknowledgedAt: string;
  }>(`/technical-order-items/${itemId}/acknowledge`, { method: 'POST' });
}

export function evaluateV2TechnicalQuality(input: {
  itemId: string;
  outputId?: string;
  resultCode: 'PASS' | 'WARNING' | 'FAIL';
  score?: number;
  note?: string;
}) {
  const { itemId, ...body } = input;
  return diagnosisRequest(`/technical-order-items/${itemId}/quality`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function updateV2TechnicalFeeStatus(input: {
  itemId: string;
  statusCode: 'NOT_SENT' | 'PENDING' | 'SUCCEEDED' | 'FAILED';
  externalReference?: string;
  failureReason?: string;
}) {
  const { itemId, ...body } = input;
  return diagnosisRequest(`/technical-order-items/${itemId}/fee-status`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function recordV2TechnicalConsumption(input: {
  itemId: string;
  consumableBatchId: string;
  quantity: number;
  unitCode: string;
  reason: string;
}) {
  const { itemId, ...body } = input;
  return diagnosisRequest(`/technical-order-items/${itemId}/consumption`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function printV2TechnicalLabel(input: {
  itemId: string;
  outputId: string;
  reason?: string;
  idempotencyKey: string;
}) {
  const { itemId, ...body } = input;
  return diagnosisRequest(`/technical-order-items/${itemId}/label`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function claimV2Diagnosis(caseId: string, idempotencyKey: string) {
  return diagnosisRequest<{ diagnosisId: string; responsibilityId: string; duplicate: boolean }>(
    '/diagnoses/self-claim',
    { method: 'POST', body: JSON.stringify({ caseId, idempotencyKey }) },
  );
}

export function getV2CaseFavorite(caseId: string) {
  return diagnosisRequest<{ caseId: string; favorite: boolean }>(
    `/case-support/cases/${caseId}/favorite`,
  );
}

export function favoriteV2Case(caseId: string) {
  return diagnosisRequest<{ caseId: string; favorite: boolean }>(
    `/case-support/cases/${caseId}/favorite`,
    { method: 'POST' },
  );
}

export function unfavoriteV2Case(caseId: string) {
  return diagnosisRequest<{ caseId: string; favorite: boolean }>(
    `/case-support/cases/${caseId}/unfavorite`,
    { method: 'POST' },
  );
}

export function getV2CaseFollowUps(caseId: string) {
  return diagnosisRequest<V2CaseFollowUp[]>(`/case-support/cases/${caseId}/follow-ups`);
}

export function createV2CaseFollowUp(input: {
  caseId: string;
  followUpDate: string;
  plan: string;
  idempotencyKey: string;
}) {
  const { caseId, ...body } = input;
  return diagnosisRequest<V2CaseFollowUp>(`/case-support/cases/${caseId}/follow-ups`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function completeV2CaseFollowUp(input: {
  followUpId: string;
  content: string;
  result: string;
  idempotencyKey: string;
}) {
  const { followUpId, ...body } = input;
  return diagnosisRequest<V2CaseFollowUp>(`/case-support/follow-ups/${followUpId}/complete`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function getV2CaseConsultations(caseId: string) {
  return diagnosisRequest<V2CaseConsultation[]>(`/case-support/cases/${caseId}/consultations`);
}

export function createV2CaseConsultation(input: {
  caseId: string;
  consultationAt?: string;
  initiatorRef: string;
  participantRefs: string;
  reason: string;
  discussion?: string;
  conclusion?: string;
  note?: string;
  attachmentReference?: string;
  idempotencyKey: string;
}) {
  const { caseId, ...body } = input;
  return diagnosisRequest<V2CaseConsultation>(`/case-support/cases/${caseId}/consultations`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function assignV2Diagnosis(input: {
  caseId: string;
  doctorId: string;
  reason: string;
  idempotencyKey: string;
}) {
  return diagnosisRequest('/diagnoses/assign', { method: 'POST', body: JSON.stringify(input) });
}

export function reassignV2Diagnosis(input: {
  caseId: string;
  doctorId: string;
  reason: string;
  idempotencyKey: string;
}) {
  return diagnosisRequest('/diagnoses/reassign', { method: 'POST', body: JSON.stringify(input) });
}

export function saveV2Diagnosis(input: {
  diagnosisId: string;
  structuredData: string;
  microscopicDescription: string;
  diagnosisText: string;
  comment: string;
  expectedVersion: number;
  idempotencyKey: string;
}) {
  const { diagnosisId, ...body } = input;
  return diagnosisRequest(`/diagnoses/${diagnosisId}/content`, {
    method: 'PUT',
    body: JSON.stringify(body),
  });
}

export function completeV2Responsibility(input: {
  diagnosisId: string;
  role: V2ResponsibilityRole;
  responsibilityId: string;
  responsibilityExpectedVersion: number;
  structuredData: string;
  microscopicDescription: string;
  diagnosisText: string;
  comment: string;
  diagnosisExpectedVersion: number;
  nextRole?: V2ResponsibilityRole;
  nextDoctorId?: string;
  nextReason?: string;
  idempotencyKey: string;
}) {
  const { diagnosisId, role, ...body } = input;
  const path =
    role === 'INITIAL'
      ? 'complete-initial'
      : role === 'REVIEW'
        ? 'complete-review'
        : 'complete-audit';
  return diagnosisRequest(`/diagnoses/${diagnosisId}/${path}`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}
