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
  technicalOrders: V2TechnicalOrder[];
  blockingTechnicalOrderCount: number;
  technicalOrder: { kind: string; status: string };
  report: { kind: string; status: string };
  reports: V2Report[];
  blockingReasons: string[];
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
  enabled: boolean;
  allowedTargetTypes: string[];
  producesSlide: boolean;
  producesBlock: boolean;
  producesStructuredResult: boolean;
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

export function getV2FrozenRoundDiagnosisWorkspace(roundId: string): Promise<V2DiagnosisWorkspace> {
  return diagnosisRequest(`/diagnosis-workspaces/frozen-rounds/${roundId}`);
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
  enabled: boolean;
  allowedTargetTypes: string;
  producesSlide: boolean;
  producesBlock: boolean;
  producesStructuredResult: boolean;
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

export function claimV2Diagnosis(caseId: string, idempotencyKey: string) {
  return diagnosisRequest<{ diagnosisId: string; responsibilityId: string; duplicate: boolean }>(
    '/diagnoses/self-claim',
    { method: 'POST', body: JSON.stringify({ caseId, idempotencyKey }) },
  );
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
