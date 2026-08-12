export type V2CaseResult = {
  caseId: string;
  caseNo: string;
  businessTypeCode: string;
  patientReference: string;
  visitReference: string | null;
  applicationNo: string;
  lifecycleStateCode: string;
  numberBindingActive: boolean;
  concurrencyVersion: number;
  cancelledAt: string | null;
  cancelledByRef: string | null;
  cancellationReason: string | null;
  duplicate: boolean;
  eventTypeCode: string;
};

export type V2PathologyNumberHistory = {
  oldPathologyNo: string;
  newPathologyNo: string | null;
  operationCode: 'CORRECTION' | 'CANCELLATION_RELEASE';
  reason: string;
  changedAt: string;
  changedBy: string;
};

export type V2SpecimenResult = {
  specimenId: string;
  caseId: string;
  specimenNo: string;
  specimenCode: string;
  specimenName: string;
  specimenKindCode: string;
  creationSourceCode: string;
  sourceKindCode: string;
  sourceReference: string;
  collectionSite: string;
  collectionMethodCode: string;
  lateralityCode?: string | null;
  quantityValue?: number | null;
  quantityUnitCode?: string | null;
  description?: string | null;
  removedAt?: string | null;
  fixedAt?: string | null;
  receivedAt?: string | null;
  labelCode: string | null;
  deletedAt: string | null;
  deletionReason: string | null;
  concurrencyVersion: number;
  duplicate: boolean;
  eventTypeCode: string;
};

async function v2RegistrationRequest<T>(path: string, init: RequestInit): Promise<T> {
  const response = await fetch(`/api/v2/registration${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init.headers ?? {}) },
  });
  const body: unknown = await response.json();
  if (!response.ok) {
    const error = body as { error_code?: string; message?: string };
    throw new Error(`${error.error_code ?? 'V2-REQUEST-FAILED'}: ${error.message ?? '请求失败'}`);
  }
  return body as T;
}

export function createV2Case(input: {
  sourceSystemCode: string;
  externalApplicationId: string;
  applicationItemCode: string;
  patientReference: string;
  visitReference: string;
  idempotencyKey: string;
}): Promise<V2CaseResult> {
  return v2RegistrationRequest('/cases', { method: 'POST', body: JSON.stringify(input) });
}

export function getV2Case(caseId: string): Promise<V2CaseResult> {
  return v2RegistrationRequest(`/cases/${caseId}`, { method: 'GET' });
}

export function correctV2PathologyNumber(input: {
  caseId: string;
  newPathologyNo: string;
  reason: string;
  expectedVersion: number;
}): Promise<V2CaseResult> {
  return v2RegistrationRequest(`/cases/${encodeURIComponent(input.caseId)}/pathology-number`, {
    method: 'POST',
    body: JSON.stringify({
      newPathologyNo: input.newPathologyNo,
      reason: input.reason,
      expectedVersion: input.expectedVersion,
    }),
  });
}

export function cancelV2Case(input: {
  caseId: string;
  reason: string;
  expectedVersion: number;
}): Promise<V2CaseResult> {
  return v2RegistrationRequest(`/cases/${encodeURIComponent(input.caseId)}/cancel`, {
    method: 'POST',
    body: JSON.stringify({ reason: input.reason, expectedVersion: input.expectedVersion }),
  });
}

export function getV2PathologyNumberHistory(caseId: string): Promise<V2PathologyNumberHistory[]> {
  return v2RegistrationRequest(`/cases/${encodeURIComponent(caseId)}/pathology-number-history`, {
    method: 'GET',
  });
}

export function registerV2Specimen(input: {
  caseId: string;
  specimenCode: string;
  specimenName?: string;
  specimenKindCode: string;
  creationSourceCode?: string;
  sourceKindCode: string;
  sourceReference: string;
  collectionSite: string;
  collectionMethodCode: string;
  lateralityCode?: string | null;
  quantityValue?: number | null;
  quantityUnitCode?: string | null;
  description?: string | null;
  removedAt?: string | null;
  fixedAt?: string | null;
  receivedAt?: string | null;
  labelCode: string;
  creationReason?: string;
  idempotencyKey: string;
}): Promise<V2SpecimenResult> {
  return v2RegistrationRequest('/specimens', { method: 'POST', body: JSON.stringify(input) });
}

export function getV2Specimen(specimenId: string): Promise<V2SpecimenResult> {
  return v2RegistrationRequest(`/specimens/${specimenId}`, { method: 'GET' });
}

export function updateV2Specimen(input: {
  specimenId: string;
  specimenCode: string;
  specimenName?: string;
  specimenKindCode: string;
  sourceKindCode: string;
  sourceReference: string;
  collectionSite: string;
  collectionMethodCode: string;
  lateralityCode?: string | null;
  quantityValue?: number | null;
  quantityUnitCode?: string | null;
  description?: string | null;
  removedAt?: string | null;
  fixedAt?: string | null;
  receivedAt?: string | null;
  labelCode: string;
  expectedVersion: number;
  reason?: string;
}): Promise<V2SpecimenResult> {
  return v2RegistrationRequest(`/specimens/${input.specimenId}`, {
    method: 'PUT',
    body: JSON.stringify({
      specimenCode: input.specimenCode,
      specimenName: input.specimenName,
      specimenKindCode: input.specimenKindCode,
      sourceKindCode: input.sourceKindCode,
      sourceReference: input.sourceReference,
      collectionSite: input.collectionSite,
      collectionMethodCode: input.collectionMethodCode,
      lateralityCode: input.lateralityCode,
      quantityValue: input.quantityValue,
      quantityUnitCode: input.quantityUnitCode,
      description: input.description,
      removedAt: input.removedAt,
      fixedAt: input.fixedAt,
      receivedAt: input.receivedAt,
      labelCode: input.labelCode,
      expectedVersion: input.expectedVersion,
      reason: input.reason,
    }),
  });
}

export function splitV2Specimen(input: {
  specimenId: string;
  childSpecimenCode: string;
  childSpecimenName: string;
  specimenKindCode?: string;
  sourceKindCode?: string;
  collectionSite?: string;
  quantityValue?: number;
  quantityUnitCode?: string;
  description?: string;
  labelCode?: string;
  reason: string;
}): Promise<V2SpecimenResult> {
  const { specimenId, ...body } = input;
  return v2RegistrationRequest(`/specimens/${specimenId}/split`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function softDeleteV2Specimen(input: {
  specimenId: string;
  expectedVersion: number;
  reason: string;
}): Promise<V2SpecimenResult> {
  return v2RegistrationRequest(`/specimens/${input.specimenId}/soft-delete`, {
    method: 'POST',
    body: JSON.stringify({ expectedVersion: input.expectedVersion, reason: input.reason }),
  });
}
