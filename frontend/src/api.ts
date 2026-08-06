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
