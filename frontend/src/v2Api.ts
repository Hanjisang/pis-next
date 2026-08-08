export type V2CaseResult = {
  caseId: string;
  caseNo: string;
  businessTypeCode: string;
  lifecycleStateCode: string;
  numberBindingActive: boolean;
  concurrencyVersion: number;
  duplicate: boolean;
  eventTypeCode: string;
};

export type V2SpecimenResult = {
  specimenId: string;
  caseId: string;
  specimenNo: string;
  specimenCode: string;
  specimenKindCode: string;
  sourceKindCode: string;
  sourceReference: string;
  collectionSite: string;
  collectionMethodCode: string;
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

export function registerV2Specimen(input: {
  caseId: string;
  specimenCode: string;
  specimenKindCode: string;
  sourceKindCode: string;
  sourceReference: string;
  collectionSite: string;
  collectionMethodCode: string;
  labelCode: string;
  idempotencyKey: string;
}): Promise<V2SpecimenResult> {
  return v2RegistrationRequest('/specimens', { method: 'POST', body: JSON.stringify(input) });
}

export function updateV2Specimen(input: {
  specimenId: string;
  specimenCode: string;
  specimenKindCode: string;
  sourceKindCode: string;
  sourceReference: string;
  collectionSite: string;
  collectionMethodCode: string;
  labelCode: string;
  expectedVersion: number;
}): Promise<V2SpecimenResult> {
  return v2RegistrationRequest(`/specimens/${input.specimenId}`, {
    method: 'PUT',
    body: JSON.stringify({
      specimenCode: input.specimenCode,
      specimenKindCode: input.specimenKindCode,
      sourceKindCode: input.sourceKindCode,
      sourceReference: input.sourceReference,
      collectionSite: input.collectionSite,
      collectionMethodCode: input.collectionMethodCode,
      labelCode: input.labelCode,
      expectedVersion: input.expectedVersion,
    }),
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
