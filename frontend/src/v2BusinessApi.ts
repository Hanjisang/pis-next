export type V2MolecularResult = {
  resultId: string;
  caseId: string;
  specimenId: string | null;
  resultCode: string;
  resultData: string;
  statusCode: string;
  completedAt: string;
  completedBy: string;
  duplicate: boolean;
};

export type V2ConsultationMaterialResult = {
  caseId: string;
  specimenId: string;
  grossingId: string;
  blockId: string;
  slideId: string | null;
  externalReference: string;
};

async function businessRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`/api/v2${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init.headers ?? {}) },
  });
  const body: unknown = await response.json();
  if (!response.ok) {
    const error = body as { error_code?: string; message?: string };
    throw new Error(
      `${error.error_code ?? 'V2-BUSINESS-REQUEST-FAILED'}: ${error.message ?? '请求失败'}`,
    );
  }
  return body as T;
}

export function completeV2MolecularResult(input: {
  caseId: string;
  specimenId?: string;
  resultCode: string;
  resultData: string;
  idempotencyKey: string;
}): Promise<V2MolecularResult> {
  const { caseId, ...body } = input;
  return businessRequest(`/molecular/cases/${caseId}/results`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function registerV2ConsultationMaterial(input: {
  caseId: string;
  externalReference: string;
  specimenKindCode: string;
  blockCode: string;
  blockType: string;
  operatorId: string;
  createLocalSlide: boolean;
  localSlideCode?: string;
  localSlideType?: string;
  idempotencyKey: string;
}): Promise<V2ConsultationMaterialResult> {
  const { caseId, ...body } = input;
  return businessRequest(`/consultation/cases/${caseId}/external-material`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}
