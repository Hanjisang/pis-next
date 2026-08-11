export type V2GrossingResult = {
  grossingId: string;
  grossingNo: string;
  caseId: string;
  sourceType: string;
  completedAt: string | null;
  concurrencyVersion: number;
  duplicate: boolean;
  affectedCount: number;
  reopened: boolean;
};

export type V2BlockResult = {
  blockId: string;
  caseId: string;
  grossingId: string;
  specimenId: string;
  blockCode: string;
  blockType: string;
  deletedAt: string | null;
  concurrencyVersion: number;
  duplicate: boolean;
};

export type V2SlideNode = {
  slideId: string;
  slideCode: string;
  slideType: string;
  sourceContextType: string;
  completedAt: string | null;
  completed: boolean;
  required: boolean;
  concurrencyVersion: number;
};

export type V2MaterialTree = {
  caseId: string;
  caseNo: string;
  businessTypeCode: string;
  capability?: {
    businessTypeCode: string;
    modalityCode: string;
    requiresGrossing: boolean;
    supportsBlocks: boolean;
    supportsDirectSlides: boolean;
    usesHistologyProcessing: boolean;
    requiresSlideCompletion: boolean;
    diagnosisEnabled: boolean;
    initialSlideRule?: string | null;
    productionCapabilities?: string[];
  };
  specimens: Array<{
    specimenId: string;
    specimenNo: string;
    specimenCode: string;
    specimenKindCode: string;
    blocks: Array<{
      blockId: string;
      blockCode: string;
      blockType: string;
      concurrencyVersion: number;
      printCount: number;
      slides: V2SlideNode[];
    }>;
    directSlides: V2SlideNode[];
  }>;
  initialRequiredCount: number;
  initialCompletedCount: number;
  initialProductionComplete: boolean;
};

export type V2SlideResult = {
  slideId: string;
  caseId: string;
  blockId: string | null;
  slideCode: string;
  slideType: string;
  sourceContextType: string;
  completedAt: string | null;
  concurrencyVersion: number;
  duplicate: boolean;
};

export type V2GrossingWorkspace = {
  caseId: string;
  caseNo: string;
  businessTypeCode: string;
  patientReference: string;
  visitReference: string | null;
  applicationNo: string;
  specimens: V2MaterialTree['specimens'];
  grossing: null | {
    grossingId: string;
    grossingNo: string;
    sourceType: string;
    sourceReferenceId: string | null;
    grossDescription: string;
    grossingInstruction: string | null;
    grossingDoctorId: string;
    recorderId: string;
    startedAt: string;
    completedAt: string | null;
    concurrencyVersion: number;
  };
};

async function materialRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`/api/v2${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init.headers ?? {}) },
  });
  const body: unknown = await response.json();
  if (!response.ok) {
    const error = body as { error_code?: string; message?: string };
    throw new Error(
      `${error.error_code ?? 'V2-MATERIAL-REQUEST-FAILED'}: ${error.message ?? '请求失败'}`,
    );
  }
  return body as T;
}

export function createV2Grossing(input: {
  caseId: string;
  sourceType: string;
  sourceReferenceId?: string;
  grossDescription: string;
  grossingInstruction?: string;
  grossingDoctorId: string;
  recorderId: string;
  idempotencyKey: string;
}): Promise<V2GrossingResult> {
  const { caseId, ...body } = input;
  return materialRequest(`/cases/${caseId}/grossings`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function updateV2Grossing(input: {
  grossingId: string;
  grossDescription: string;
  grossingInstruction?: string;
  grossingDoctorId: string;
  recorderId: string;
  expectedVersion: number;
  idempotencyKey: string;
}): Promise<V2GrossingResult> {
  const { grossingId, ...body } = input;
  return materialRequest(`/grossings/${grossingId}`, { method: 'PUT', body: JSON.stringify(body) });
}

export function associateV2Specimen(input: {
  grossingId: string;
  specimenId: string;
  materialDescription?: string;
  idempotencyKey: string;
}): Promise<V2GrossingResult> {
  const { grossingId, ...body } = input;
  return materialRequest(`/grossings/${grossingId}/specimens`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function createV2Block(input: {
  grossingId: string;
  specimenId: string;
  blockCode: string;
  blockType: string;
  idempotencyKey: string;
}): Promise<V2BlockResult> {
  const { grossingId, ...body } = input;
  return materialRequest(`/grossings/${grossingId}/blocks`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function createV2DirectCytologySlide(input: {
  caseId: string;
  specimenId: string;
  slideCode: string;
  slideType: string;
  idempotencyKey: string;
}): Promise<V2SlideResult> {
  const { caseId, specimenId, ...body } = input;
  return materialRequest(`/cases/${caseId}/specimens/${specimenId}/slides`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function createV2DirectExternalSlide(input: {
  caseId: string;
  blockId: string;
  slideCode: string;
  slideType: string;
  idempotencyKey: string;
}): Promise<V2SlideResult> {
  const { caseId, blockId, ...body } = input;
  return materialRequest(`/cases/${caseId}/external-blocks/${blockId}/slides`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function updateV2Block(input: {
  blockId: string;
  blockCode: string;
  blockType: string;
  expectedVersion: number;
  idempotencyKey: string;
}): Promise<V2BlockResult> {
  const { blockId, ...body } = input;
  return materialRequest(`/blocks/${blockId}`, { method: 'PUT', body: JSON.stringify(body) });
}

export function softDeleteV2Block(input: {
  blockId: string;
  expectedVersion: number;
  reason: string;
  idempotencyKey: string;
}): Promise<V2BlockResult> {
  const { blockId, ...body } = input;
  return materialRequest(`/blocks/${blockId}/soft-delete`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function completeV2Grossing(input: {
  grossingId: string;
  expectedVersion: number;
  idempotencyKey: string;
}): Promise<V2GrossingResult & { createdSlideCount: number }> {
  const { grossingId, ...body } = input;
  return materialRequest(`/grossings/${grossingId}/complete`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function reopenV2Grossing(input: {
  grossingId: string;
  expectedVersion: number;
  reason: string;
  idempotencyKey: string;
}): Promise<V2GrossingResult> {
  const { grossingId, ...body } = input;
  return materialRequest(`/grossings/${grossingId}/reopen`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function completeV2Slide(input: {
  slideId: string;
  expectedVersion: number;
  idempotencyKey: string;
}): Promise<V2SlideResult> {
  const { slideId, ...body } = input;
  return materialRequest(`/slides/${slideId}/complete`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function completeV2Slides(input: {
  slides: Array<{ slideId: string; expectedVersion: number }>;
  idempotencyKey: string;
}): Promise<{ changedCount: number; duplicate: boolean }> {
  return materialRequest('/slides/complete-batch', { method: 'POST', body: JSON.stringify(input) });
}

export function printV2Block(input: {
  blockId: string;
  reason?: string;
  idempotencyKey: string;
}): Promise<{ entityId: string; duplicate: boolean; resultCode: string }> {
  const { blockId, ...body } = input;
  return materialRequest(`/blocks/${blockId}/print`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function printV2Slide(input: {
  slideId: string;
  reason?: string;
  idempotencyKey: string;
}): Promise<{ entityId: string; duplicate: boolean; resultCode: string }> {
  const { slideId, ...body } = input;
  return materialRequest(`/slides/${slideId}/print`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function getV2MaterialTree(caseId: string): Promise<V2MaterialTree> {
  return materialRequest(`/cases/${caseId}/materials`);
}

export function getV2GrossingWorkspace(
  caseId: string,
  sourceType = 'INITIAL',
  sourceReferenceId?: string,
): Promise<V2GrossingWorkspace> {
  const query = new URLSearchParams({ sourceType });
  if (sourceReferenceId) query.set('sourceReferenceId', sourceReferenceId);
  return materialRequest(`/cases/${caseId}/grossing-workspace?${query.toString()}`);
}
