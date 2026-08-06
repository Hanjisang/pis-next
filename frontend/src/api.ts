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
