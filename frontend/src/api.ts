export type RegistrationResult = {
  requestId: string;
  applicationNo: string | null;
  lifecycleStateCode: string;
  concurrencyVersion: number;
  duplicate: boolean;
  eventTypeCode: string;
};

export type CaseResult = {
  caseId: string;
  caseNo: string | null;
  snapshotId: string | null;
  duplicate: boolean;
  eventTypeCode: string;
};

export type ExpectedSpecimenResult = {
  specimenId: string;
  specimenNo: string;
  containerBarcode: string;
  lifecycleStateCode: string;
  concurrencyVersion: number;
  duplicate: boolean;
};

export type ReceivingResult = {
  specimenId: string;
  specimenNo: string;
  lifecycleStateCode: string;
  concurrencyVersion: number;
  duplicate: boolean;
  eventTypeCode: string;
};

export type GrossingBatchResult = {
  batchId: string;
  batchNo: string;
  taskStateCode: string;
  stateCode: string;
  assignedActor: string | null;
  actualActor: string | null;
  concurrencyVersion: number;
  duplicate: boolean;
};

export type GrossingSampleResult = {
  sampleId: string;
  sampleNo: string;
  stateCode: string;
  duplicate: boolean;
};

export type GrossingBlockResult = {
  blockId: string;
  blockNo: string;
  tissueBoxNo: string;
  stateCode: string;
  concurrencyVersion: number;
  duplicate: boolean;
};

export type GrossingLabelResult = {
  labelId: string;
  targetObjectId: string;
  labelVersion: number;
  stateCode: string;
  snapshot: string;
  barcodePayload: string;
  duplicate: boolean;
};

export type GrossingPrintResult = {
  requestId: string;
  attemptId: string | null;
  labelId: string;
  stateCode: string;
  outcome: string;
  submitted: boolean;
};

export type ProcessingTaskResult = {
  taskId: string;
  taskNo: string;
  tissueBlockId: string;
  stateCode: string;
  assignedActor: string | null;
  actualActor: string | null;
  concurrencyVersion: number;
  duplicate: boolean;
};

export type ProcessingBatchResult = {
  batchId: string;
  taskId: string;
  batchNo: string;
  stateCode: string;
  executionMode: string;
  deviceIdentity: string | null;
  programVersionSnapshot: string;
  assignedActor: string | null;
  concurrencyVersion: number;
  duplicate: boolean;
};

export type ProcessingMemberResult = {
  memberId: string;
  batchId: string;
  tissueBlockId: string;
  plannedBlockNo: string;
  stateCode: string;
  canEnterEmbedding: boolean;
  concurrencyVersion: number;
  duplicate: boolean;
};

export type ProcessingRunResult = {
  runId: string;
  batchId: string;
  runNo: number;
  stateCode: string;
  executionMode: string;
  externalRunId: string;
  duplicate: boolean;
};

export type ProcessingResult = {
  resultId: string;
  runId: string;
  memberId: string;
  stateCode: string;
  canEnterEmbedding: boolean;
  duplicate: boolean;
};

export type EmbeddingTaskResult = {
  taskId: string;
  taskNo: string;
  tissueBlockId: string;
  processingResultId: string;
  stateCode: string;
  requirementSnapshot: string | null;
  orientationReference: string | null;
  concurrencyVersion: number;
  duplicate: boolean;
};

export type ActualBlockFormationResult = {
  formationId: string;
  tissueBlockId: string;
  inheritedBlockNo: string;
  formationVersion: number;
  stateCode: string;
  currentValid: boolean;
  duplicate: boolean;
};

export type TechnicalPlannedOutput = {
  outputId: string;
  sequenceNo: number;
  outputKindCode: string;
  slidePurposeCode: string;
  plannedQuantity: number;
  plannedStainProjectCode: string | null;
  plannedUsageCode: string;
  plannedLabelQuantity: number;
};

export type TechnicalProjectResult = {
  projectId: string;
  orderId: string;
  projectNo: string;
  projectTypeCode: string;
  taskStateCode: string;
  reviewStateCode: string;
  receivingStateCode: string;
  executionHandoffStateCode: string;
  resultStateCode: string;
  assignedActorRef: string | null;
  actualBlockFormationId: string | null;
  plannedOutputs: TechnicalPlannedOutput[];
  concurrencyVersion: number;
  duplicate: boolean;
};

export type TechnicalOrderResult = {
  orderId: string;
  orderNo: string;
  caseId: string;
  stateCode: string;
  priorityCode: string;
  concurrencyVersion: number;
  duplicate: boolean;
  projects: TechnicalProjectResult[];
};

const apiPrefix = '/api/p15';

async function request<T>(path: string, init: RequestInit): Promise<T> {
  const response = await fetch(`${apiPrefix}${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init.headers ?? {}) },
  });
  const body: unknown = await response.json();
  if (!response.ok) {
    const error = body as { error_code?: string; message?: string };
    throw new Error(`${error.error_code ?? 'P15-REQUEST-FAILED'}: ${error.message ?? '请求失败'}`);
  }
  return body as T;
}

async function p16Request<T>(path: string, init: RequestInit): Promise<T> {
  const response = await fetch(`/api/p16${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init.headers ?? {}) },
  });
  const body: unknown = await response.json();
  if (!response.ok) {
    const error = body as { error_code?: string; message?: string };
    throw new Error(`${error.error_code ?? 'P16-REQUEST-FAILED'}: ${error.message ?? '请求失败'}`);
  }
  return body as T;
}

async function p17Request<T>(path: string, init: RequestInit): Promise<T> {
  const response = await fetch(`/api/p17${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init.headers ?? {}) },
  });
  const body: unknown = await response.json();
  if (!response.ok) {
    const error = body as { error_code?: string; message?: string };
    throw new Error(`${error.error_code ?? 'P17-REQUEST-FAILED'}: ${error.message ?? '请求失败'}`);
  }
  return body as T;
}

async function p18Request<T>(path: string, init: RequestInit): Promise<T> {
  const response = await fetch(`/api/p18${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init.headers ?? {}) },
  });
  const body: unknown = await response.json();
  if (!response.ok) {
    const error = body as { error_code?: string; message?: string };
    throw new Error(`${error.error_code ?? 'P18-REQUEST-FAILED'}: ${error.message ?? '请求失败'}`);
  }
  return body as T;
}

export function registerManual(input: {
  pathologyModalityCode: string;
  requestContent: string;
  reason: string;
}): Promise<RegistrationResult> {
  return request('/registrations/manual', { method: 'POST', body: JSON.stringify(input) });
}

export function establishCase(input: {
  requestId: string;
  patientReference: string;
  visitReference: string;
  pathologyModalityCode: string;
}): Promise<CaseResult> {
  return request('/cases', { method: 'POST', body: JSON.stringify(input) });
}

export function acceptRegistration(
  requestId: string,
  expectedVersion: number,
): Promise<RegistrationResult> {
  return request(`/registrations/${requestId}/accept`, {
    method: 'POST',
    body: JSON.stringify({ expectedVersion }),
  });
}

export function registerExpectedSpecimen(
  caseId: string,
  input: {
    specimenKindCode: string;
    collectionSite: string;
    collectionMethodCode: string;
    expectedQuantity: number;
    containerBarcode: string;
  },
): Promise<ExpectedSpecimenResult> {
  return request(`/cases/${caseId}/expected-specimens`, {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export function receiveSpecimen(input: {
  barcode: string;
  expectedQuantity: number;
  actualQuantity: number;
  expectedVersion: number;
  idempotencyKey: string;
}): Promise<ReceivingResult> {
  return request('/specimens/receive', { method: 'POST', body: JSON.stringify(input) });
}

export function getGrossingQueue(): Promise<Record<string, unknown>[]> {
  return p16Request('/grossing-queue', { method: 'GET' });
}

export function createGrossingBatch(input: {
  specimenId: string;
  specimenNo: string;
  caseNo: string;
  patientIdentityReference: string;
  idempotencyKey: string;
}): Promise<GrossingBatchResult> {
  return p16Request('/grossing-batches', { method: 'POST', body: JSON.stringify(input) });
}

export function takeoverGrossingBatch(
  batchId: string,
  expectedVersion: number,
  idempotencyKey: string,
): Promise<GrossingBatchResult> {
  return p16Request(`/grossing-batches/${batchId}/takeover`, {
    method: 'POST',
    body: JSON.stringify({ expectedVersion, idempotencyKey }),
  });
}

export function startGrossingBatch(
  batchId: string,
  expectedVersion: number,
  idempotencyKey: string,
): Promise<GrossingBatchResult> {
  return p16Request(`/grossing-batches/${batchId}/start`, {
    method: 'POST',
    body: JSON.stringify({ expectedVersion, idempotencyKey }),
  });
}

export function recordGrossing(
  batchId: string,
  input: {
    specimenId: string;
    specimenNo: string;
    caseNo: string;
    patientIdentityReference: string;
    identityVerified: boolean;
    patientIdentityVerified: boolean;
    grossAppearance: string;
    grossDescription: string;
    quantity: number;
    quantityUnitCode: string;
    expectedVersion: number;
    idempotencyKey: string;
  },
): Promise<{ recordId: string; recordVersion: number; duplicate: boolean }> {
  return p16Request(`/grossing-batches/${batchId}/records`, {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export function addTissueSample(
  batchId: string,
  input: {
    specimenId: string;
    sourceSite: string;
    description: string;
    quantity: number;
    unit: string;
    expectedVersion: number;
    idempotencyKey: string;
  },
): Promise<GrossingSampleResult> {
  return p16Request(`/grossing-batches/${batchId}/samples`, {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export function createPlannedBlock(
  batchId: string,
  input: {
    specimenId: string;
    blockKindCode: string;
    sourceMaterialKindCode: string;
    expectedVersion: number;
    idempotencyKey: string;
  },
): Promise<GrossingBlockResult> {
  return p16Request(`/grossing-batches/${batchId}/blocks`, {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export function assignTissueSample(
  blockId: string,
  sampleId: string,
  expectedVersion: number,
  idempotencyKey: string,
): Promise<unknown> {
  return p16Request(`/blocks/${blockId}/samples`, {
    method: 'POST',
    body: JSON.stringify({ sampleId, expectedVersion, idempotencyKey }),
  });
}

export function generateBlockLabel(
  blockId: string,
  idempotencyKey: string,
): Promise<GrossingLabelResult> {
  return p16Request(`/blocks/${blockId}/labels`, {
    method: 'POST',
    body: JSON.stringify({ idempotencyKey }),
  });
}

export function submitBlockLabelPrint(
  labelId: string,
  idempotencyKey: string,
): Promise<GrossingPrintResult> {
  return p16Request(`/labels/${labelId}/print`, {
    method: 'POST',
    body: JSON.stringify({ idempotencyKey }),
  });
}

export function reprintBlockLabel(
  labelId: string,
  idempotencyKey: string,
  reason: string,
): Promise<GrossingPrintResult> {
  return p16Request(`/labels/${labelId}/reprint`, {
    method: 'POST',
    body: JSON.stringify({ idempotencyKey, reason }),
  });
}

export function completeGrossingBatch(
  batchId: string,
  expectedVersion: number,
  idempotencyKey: string,
): Promise<GrossingBatchResult> {
  return p16Request(`/grossing-batches/${batchId}/complete`, {
    method: 'POST',
    body: JSON.stringify({ expectedVersion, idempotencyKey }),
  });
}

export function getProcessingQueue(): Promise<Record<string, unknown>[]> {
  return p17Request('/processing-queue', { method: 'GET' });
}

export function getEmbeddingQueue(): Promise<Record<string, unknown>[]> {
  return p17Request('/embedding-queue', { method: 'GET' });
}

export function createProcessingTask(input: {
  tissueBlockId: string;
  idempotencyKey: string;
}): Promise<ProcessingTaskResult> {
  return p17Request('/processing-tasks', { method: 'POST', body: JSON.stringify(input) });
}

export function takeoverProcessingTask(
  taskId: string,
  expectedVersion: number,
  idempotencyKey: string,
): Promise<ProcessingTaskResult> {
  return p17Request(`/processing-tasks/${taskId}/takeover`, {
    method: 'POST',
    body: JSON.stringify({ expectedVersion, idempotencyKey }),
  });
}

export function createProcessingBatch(input: {
  taskId: string;
  programCode: string;
  versionLabel: string;
  executionMode: string;
  deviceIdentity?: string;
  idempotencyKey: string;
}): Promise<ProcessingBatchResult> {
  const { taskId, ...body } = input;
  return p17Request(`/processing-tasks/${taskId}/batches`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function addProcessingMember(
  batchId: string,
  tissueBlockId: string,
  idempotencyKey: string,
): Promise<ProcessingMemberResult> {
  return p17Request(`/processing-batches/${batchId}/members`, {
    method: 'POST',
    body: JSON.stringify({ tissueBlockId, idempotencyKey }),
  });
}

export function startProcessingBatch(
  batchId: string,
  expectedVersion: number,
  idempotencyKey: string,
): Promise<ProcessingRunResult> {
  return p17Request(`/processing-batches/${batchId}/start`, {
    method: 'POST',
    body: JSON.stringify({ expectedVersion, idempotencyKey }),
  });
}

export function receiveProcessingRawResult(input: {
  runId: string;
  externalMessageId: string;
  payloadDigest: string;
  rawStateCode: string;
  payloadReference?: string;
  idempotencyKey: string;
}): Promise<{ rawResultId: string; runId: string; stateCode: string; duplicate: boolean }> {
  return p17Request('/processing-runs/raw-results', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export function confirmProcessingResult(input: {
  runId: string;
  memberId: string;
  resultStateCode: string;
  canEnterEmbedding: boolean;
  summary: string;
  expectedMemberVersion: number;
  idempotencyKey: string;
}): Promise<ProcessingResult> {
  return p17Request('/processing-runs/results', { method: 'POST', body: JSON.stringify(input) });
}

export function completeProcessingBatch(
  batchId: string,
  expectedVersion: number,
  idempotencyKey: string,
): Promise<ProcessingBatchResult> {
  return p17Request(`/processing-batches/${batchId}/complete`, {
    method: 'POST',
    body: JSON.stringify({ expectedVersion, idempotencyKey }),
  });
}

export function createEmbeddingTask(input: {
  tissueBlockId: string;
  processingResultId: string;
  idempotencyKey: string;
}): Promise<EmbeddingTaskResult> {
  return p17Request('/embedding-tasks', { method: 'POST', body: JSON.stringify(input) });
}

export function takeoverEmbeddingTask(
  taskId: string,
  expectedVersion: number,
  idempotencyKey: string,
): Promise<EmbeddingTaskResult> {
  return p17Request(`/embedding-tasks/${taskId}/takeover`, {
    method: 'POST',
    body: JSON.stringify({ expectedVersion, idempotencyKey }),
  });
}

export function startEmbeddingTask(
  taskId: string,
  expectedVersion: number,
  idempotencyKey: string,
): Promise<EmbeddingTaskResult> {
  return p17Request(`/embedding-tasks/${taskId}/start`, {
    method: 'POST',
    body: JSON.stringify({ expectedVersion, idempotencyKey }),
  });
}

export function recordEmbeddingRequirements(input: {
  taskId: string;
  requirementSnapshot: string;
  orientationReference: string;
  expectedVersion: number;
  idempotencyKey: string;
}): Promise<EmbeddingTaskResult> {
  const { taskId, ...body } = input;
  return p17Request(`/embedding-tasks/${taskId}/requirements`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function completeEmbeddingTask(input: {
  taskId: string;
  expectedTaskVersion: number;
  expectedBlockVersion: number;
  idempotencyKey: string;
}): Promise<ActualBlockFormationResult> {
  const { taskId, ...body } = input;
  return p17Request(`/embedding-tasks/${taskId}/complete`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function getTechnicalOrders(): Promise<Record<string, unknown>[]> {
  return p18Request('/orders', { method: 'GET' });
}

export function createTechnicalOrder(input: {
  caseId: string;
  orderKindCode: string;
  priorityCode: string;
  reasonText: string;
  representedActorRef?: string;
  projects: Array<{
    projectCode: string;
    versionLabel: string;
    projectTypeCode: string;
    actualBlockFormationId: string;
    usageCode: string;
    priorityCode?: string;
    reasonText: string;
    plannedOutputs: Array<{
      sequenceNo: number;
      outputKindCode: string;
      slidePurposeCode: string;
      plannedLayerReference?: string;
      plannedQuantity: number;
      plannedStainProjectCode?: string;
      plannedUsageCode: string;
      plannedLabelQuantity: number;
      executionNote?: string;
    }>;
  }>;
  idempotencyKey: string;
}): Promise<TechnicalOrderResult> {
  return p18Request('/orders', { method: 'POST', body: JSON.stringify(input) });
}

export function submitTechnicalOrder(
  orderId: string,
  expectedVersion: number,
  idempotencyKey: string,
): Promise<TechnicalOrderResult> {
  return p18Request(`/orders/${orderId}/submit`, {
    method: 'POST',
    body: JSON.stringify({ expectedVersion, idempotencyKey }),
  });
}
