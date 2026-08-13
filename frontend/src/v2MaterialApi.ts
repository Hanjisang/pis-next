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
  samplingDescription: string | null;
  note: string | null;
  deletedAt: string | null;
  concurrencyVersion: number;
  duplicate: boolean;
};

export type V2SlideNode = {
  slideId: string;
  slideCode: string;
  slideType: string;
  stainCode?: string | null;
  sourceContextType: string;
  completedAt: string | null;
  completed: boolean;
  required: boolean;
  concurrencyVersion: number;
  printCount: number;
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
    specimenName: string;
    specimenKindCode: string;
    creationSourceCode: string;
    collectionSite: string | null;
    collectionMethodCode?: string | null;
    specimenDescription: string | null;
    preparationMethodCode?: string | null;
    specimenConcurrencyVersion?: number;
    sourceSpecimenCode: string | null;
    grossMaterialDescription: string | null;
    grossSpecimenVersion: number;
    blocks: Array<{
      blockId: string;
      blockCode: string;
      blockType: string;
      samplingDescription: string | null;
      note: string | null;
      concurrencyVersion: number;
      printCount: number;
      verificationStatus: 'UNVERIFIED' | 'PASSED' | 'FAILED' | string;
      slides: V2SlideNode[];
    }>;
    directSlides: V2SlideNode[];
  }>;
  initialRequiredCount: number;
  initialCompletedCount: number;
  initialProductionComplete: boolean;
  availableActions: string[];
};

export type V2SlideResult = {
  slideId: string;
  caseId: string;
  blockId: string | null;
  specimenId?: string | null;
  slideCode: string;
  slideType: string;
  stainCode?: string | null;
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
  availableActions: string[];
  verificationPolicy: {
    verificationRequired: boolean;
    dualCheckRequired: boolean;
    sameUserAllowed: boolean;
  };
};

export type V2GrossingImage = {
  imageId: string;
  caseId: string;
  grossingId: string;
  specimenId: string | null;
  imageName: string;
  mediaType: string;
  storageReference: string;
  metadataJson: string | null;
  capturedAt: string;
  capturedByRef: string;
  deletedAt: string | null;
  deletionReason: string | null;
};

export type V2GrossingAnnotation = {
  annotationId: string;
  imageId: string;
  annotationTypeCode: string;
  geometryJson: string;
  label: string | null;
  note: string | null;
  createdAt: string;
  createdByRef: string;
  deletedAt: string | null;
};

export type V2GrossingMeasurement = {
  measurementId: string;
  imageId: string;
  geometryJson: string;
  value: number;
  unitCode: 'MM' | 'CM' | string;
  measurementModeCode: string;
  createdAt: string;
  createdByRef: string;
};

async function materialRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`/api/v2${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init.headers ?? {}) },
  });
  const responseText = await response.text();
  const body: unknown = responseText ? JSON.parse(responseText) : undefined;
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

export function correctV2Grossing(input: {
  grossingId: string;
  grossDescription: string;
  grossingInstruction?: string;
  grossingDoctorId: string;
  recorderId: string;
  reason: string;
  expectedVersion: number;
}): Promise<V2GrossingResult> {
  const { grossingId, ...body } = input;
  return materialRequest(`/grossings/${grossingId}/correct`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
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

export function updateV2GrossingSpecimen(input: {
  grossingId: string;
  specimenId: string;
  materialDescription: string;
  expectedVersion: number;
  reason?: string;
}): Promise<V2GrossingResult> {
  const { grossingId, specimenId, ...body } = input;
  return materialRequest(`/grossings/${grossingId}/specimens/${specimenId}`, {
    method: 'PUT',
    body: JSON.stringify(body),
  });
}

export function createV2Block(input: {
  grossingId: string;
  specimenId: string;
  blockCode: string;
  blockType: string;
  samplingDescription?: string;
  note?: string;
  idempotencyKey: string;
}): Promise<V2BlockResult> {
  const { grossingId, ...body } = input;
  return materialRequest(`/grossings/${grossingId}/blocks`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function createV2Blocks(input: {
  grossingId: string;
  blocks: Array<{
    specimenId: string;
    blockCode: string;
    blockType: string;
    samplingDescription?: string;
    note?: string;
  }>;
  idempotencyKey: string;
}): Promise<{ blocks: V2BlockResult[] }> {
  const { grossingId, ...body } = input;
  return materialRequest(`/grossings/${grossingId}/blocks/batch`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function createV2DirectCytologySlide(input: {
  caseId: string;
  specimenId: string;
  slideCode: string;
  slideType: string;
  stainCode?: string;
  idempotencyKey: string;
}): Promise<V2SlideResult> {
  const { caseId, specimenId, ...body } = input;
  return materialRequest(`/cases/${caseId}/specimens/${specimenId}/slides`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function generateV2RequiredCytologySlides(input: {
  caseId: string;
  specimenIds?: string[];
  slideType?: string;
  stainCode?: string;
  idempotencyKey: string;
}): Promise<{ createdCount: number; slides: V2SlideResult[]; duplicate: boolean }> {
  const { caseId, ...body } = input;
  return materialRequest(`/cases/${caseId}/cytology-slides/generate`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function createV2ExtraCytologySlide(input: {
  caseId: string;
  specimenId: string;
  slideType?: string;
  stainCode?: string;
  reason: string;
  idempotencyKey: string;
}): Promise<V2SlideResult> {
  const { caseId, specimenId, ...body } = input;
  return materialRequest(`/cases/${caseId}/specimens/${specimenId}/cytology-slides/extra`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function updateV2CytologyPreparation(input: {
  caseId: string;
  specimenId: string;
  preparationMethodCode?: string;
  expectedVersion: number;
}): Promise<{
  specimenId: string;
  preparationMethodCode: string | null;
  concurrencyVersion: number;
}> {
  const { caseId, specimenId, ...body } = input;
  return materialRequest(`/cases/${caseId}/specimens/${specimenId}/cytology-preparation`, {
    method: 'PUT',
    body: JSON.stringify(body),
  });
}

export function createV2ExternalCytologySlide(input: {
  caseId: string;
  specimenId: string;
  slideCode: string;
  slideType: string;
  stainCode?: string;
  idempotencyKey: string;
}): Promise<V2SlideResult> {
  const { caseId, specimenId, ...body } = input;
  return materialRequest(`/cases/${caseId}/specimens/${specimenId}/external-cytology-slides`, {
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
  samplingDescription?: string;
  note?: string;
  reason?: string;
  expectedVersion: number;
  idempotencyKey: string;
}): Promise<V2BlockResult> {
  const { blockId, ...body } = input;
  return materialRequest(`/blocks/${blockId}`, { method: 'PUT', body: JSON.stringify(body) });
}

export function verifyV2Block(input: {
  blockId: string;
  verifiedCode: string;
  verifiedSpecimenId: string;
  verifiedQuantity?: number;
  reason?: string;
}): Promise<{ blockId: string; resultCode: string; verifiedAt: string; verifiedByRef: string }> {
  const { blockId, ...body } = input;
  return materialRequest(`/blocks/${blockId}/verify`, {
    method: 'POST',
    body: JSON.stringify({ verifiedQuantity: 1, ...body }),
  });
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

export function generateV2RequiredRoutineSlides(input: {
  caseId: string;
  blockIds: string[];
  idempotencyKey: string;
}): Promise<{ createdCount: number; slides: V2SlideResult[]; duplicate: boolean }> {
  const { caseId, ...body } = input;
  return materialRequest(`/cases/${caseId}/routine-slides/generate`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function createV2ExtraRoutineSlide(input: {
  blockId: string;
  slideType: string;
  reason: string;
  idempotencyKey: string;
}): Promise<V2SlideResult> {
  const { blockId, ...body } = input;
  return materialRequest(`/blocks/${blockId}/routine-slides/extra`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function correctV2SlideCode(input: {
  slideId: string;
  newSlideCode: string;
  reason: string;
  expectedVersion: number;
}): Promise<V2SlideResult> {
  const { slideId, ...body } = input;
  return materialRequest(`/slides/${slideId}/correct-code`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function cancelV2Slide(input: {
  slideId: string;
  reason: string;
  expectedVersion: number;
  idempotencyKey: string;
}): Promise<V2SlideResult> {
  const { slideId, ...body } = input;
  return materialRequest(`/slides/${slideId}/cancel`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function correctV2SlideCompletion(input: {
  slideId: string;
  reason: string;
  expectedVersion: number;
}): Promise<V2SlideResult> {
  const { slideId, ...body } = input;
  return materialRequest(`/slides/${slideId}/correct-completion`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
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

export function printV2Blocks(input: {
  blockIds: string[];
  reason?: string;
  idempotencyKey: string;
}): Promise<{ results: Array<{ entityId: string; duplicate: boolean; resultCode: string }> }> {
  return materialRequest('/blocks/print-batch', { method: 'POST', body: JSON.stringify(input) });
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

export function printV2Slides(input: {
  slideIds: string[];
  reason?: string;
  idempotencyKey: string;
}): Promise<{ results: Array<{ entityId: string; duplicate: boolean; resultCode: string }> }> {
  return materialRequest('/slides/print-batch', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export function locateV2Material(
  caseId: string,
  barcode: string,
): Promise<{
  materialKind: 'BLOCK' | 'SLIDE';
  materialId: string;
  businessCode: string;
}> {
  return materialRequest(
    `/cases/${caseId}/materials/locate?barcode=${encodeURIComponent(barcode)}`,
  );
}

export function performV2ProductionRework(input: {
  slideId: string;
  reworkTypeCode: 'RECUT' | 'REPREPARATION' | 'RESTAIN' | 'RESCAN';
  reason: string;
  idempotencyKey: string;
}): Promise<{
  reworkId: string;
  originalSlideId: string;
  replacementSlideId: string | null;
  reworkTypeCode: string;
  statusCode: string;
}> {
  const { slideId, ...body } = input;
  return materialRequest(`/slides/${slideId}/rework/perform`, {
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

export function getV2GrossingImages(grossingId: string): Promise<V2GrossingImage[]> {
  return materialRequest(`/material/grossings/${grossingId}/images`);
}

export function captureV2GrossingImage(input: {
  grossingId: string;
  specimenId?: string;
  deviceReference?: string;
}): Promise<V2GrossingImage> {
  const { grossingId, ...body } = input;
  return materialRequest(`/material/grossings/${grossingId}/images/capture`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function uploadV2GrossingImage(input: {
  grossingId: string;
  specimenId?: string;
  imageName: string;
  mediaType: string;
  storageReference: string;
  metadataJson?: string;
}): Promise<V2GrossingImage> {
  const { grossingId, ...body } = input;
  return materialRequest(`/material/grossings/${grossingId}/images`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function createV2GrossingAnnotation(input: {
  imageId: string;
  annotationTypeCode: string;
  geometryJson: string;
  label?: string;
  note?: string;
}): Promise<V2GrossingAnnotation> {
  const { imageId, ...body } = input;
  return materialRequest(`/material/grossings/images/${imageId}/annotations`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function getV2GrossingAnnotations(imageId: string): Promise<V2GrossingAnnotation[]> {
  return materialRequest(`/material/grossings/images/${imageId}/annotations`);
}

export function measureV2GrossingImage(input: {
  imageId: string;
  geometryJson: string;
  value: number;
  unitCode: string;
  measurementModeCode: string;
}): Promise<V2GrossingMeasurement> {
  const { imageId, ...body } = input;
  return materialRequest(`/material/grossings/images/${imageId}/measurements`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function getV2GrossingMeasurements(imageId: string): Promise<V2GrossingMeasurement[]> {
  return materialRequest(`/material/grossings/images/${imageId}/measurements`);
}

export function deleteV2GrossingImage(input: { imageId: string; reason: string }): Promise<void> {
  const { imageId, ...body } = input;
  return materialRequest(`/material/grossings/images/${imageId}/delete`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}
