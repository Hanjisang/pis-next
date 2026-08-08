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
  };
  technicalOrder: { kind: string; status: string };
  report: { kind: string; status: string };
  refreshedAt: string;
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
