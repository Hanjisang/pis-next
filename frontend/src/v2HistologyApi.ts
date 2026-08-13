export const histologyPhases = [
  { code: 'DEHYDRATION', label: '脱水' },
  { code: 'EMBEDDING', label: '包埋' },
  { code: 'SECTIONING', label: '切片' },
  { code: 'STAINING', label: '染色' },
  { code: 'MOUNTING', label: '封片' },
] as const;

export type HistologyPhaseCode = (typeof histologyPhases)[number]['code'];
export type TechnicalTraceStageCode =
  | 'DEHYDRATION'
  | 'EMBEDDING'
  | 'SECTIONING'
  | 'PREPARATION'
  | 'STAINING'
  | 'COVERSLIPPING'
  | 'MOUNTING';

export type V2HistologyPhase = {
  factId?: string | null;
  targetKind?: 'BLOCK' | 'SLIDE' | null;
  targetId?: string | null;
  phaseCode: HistologyPhaseCode;
  startedAt: string | null;
  completedAt: string | null;
  operatorRef: string | null;
  deviceReference: string | null;
  equipmentId?: string | null;
  batchReference: string | null;
  stainCode?: string | null;
  exceptionCode: string | null;
  exceptionNote: string | null;
  exceptionResolvedAt?: string | null;
};

export type V2HistologySlide = {
  slideId: string;
  caseId: string;
  caseNo: string;
  patientReference: string;
  businessTypeCode?: string | null;
  specimenCode?: string | null;
  blockCode?: string | null;
  slideCode: string;
  slideType: string;
  sourceContextType?: string | null;
  slideCompletedAt: string | null;
  concurrencyVersion?: number;
  printCount?: number;
  currentPhase?:
    | 'DEHYDRATION'
    | 'EMBEDDING'
    | 'CUTTING'
    | 'STAINING'
    | 'COVERSLIPPING'
    | 'EXCEPTIONS'
    | 'COMPLETED'
    | string;
  derivedQueue?:
    | 'DEHYDRATION'
    | 'EMBEDDING'
    | 'CUTTING'
    | 'STAINING'
    | 'COVERSLIPPING'
    | 'EXCEPTIONS'
    | 'COMPLETED'
    | string;
  phases: V2HistologyPhase[];
};

export type V2HistologyQueues = {
  dehydration: number;
  embedding: number;
  cutting: number;
  staining: number;
  coverslipping: number;
  completed: number;
  exceptions: number;
};

export async function getV2HistologyWorkbench(caseId?: string, roundId?: string) {
  const params = new URLSearchParams();
  if (caseId) params.set('caseId', caseId);
  if (roundId) params.set('roundId', roundId);
  const query = params.size ? `?${params.toString()}` : '';
  const response = await fetch(`/api/v2/histology-workbench${query}`);
  const body = (await response.json()) as
    | { message?: string }
    | { slides: V2HistologySlide[]; queues?: V2HistologyQueues; refreshedAt: string };
  if (!response.ok)
    throw new Error((body as { message?: string }).message ?? '制片事实暂时无法加载');
  const successBody = body as {
    slides: V2HistologySlide[];
    queues?: V2HistologyQueues;
    refreshedAt: string;
  };
  return {
    slides: successBody.slides ?? [],
    queues: successBody.queues ?? {
      dehydration: 0,
      embedding: 0,
      cutting: 0,
      staining: 0,
      coverslipping: 0,
      completed: 0,
      exceptions: 0,
    },
    refreshedAt: successBody.refreshedAt,
  };
}

async function phaseRequest<T>(path: string, init: RequestInit = {}) {
  const response = await fetch(path, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init.headers ?? {}) },
  });
  const body = (await response.json()) as T | { message?: string };
  if (!response.ok) throw new Error((body as { message?: string }).message ?? '制片事实保存失败');
  return body as T;
}

export function startV2HistologyPhase(input: {
  slideId: string;
  phaseCode: HistologyPhaseCode;
  deviceReference?: string;
  batchReference?: string;
}) {
  return phaseRequest<V2HistologyPhase>(
    `/api/v2/histology/slides/${input.slideId}/phases/${input.phaseCode}/start`,
    {
      method: 'POST',
      body: JSON.stringify({
        deviceReference: input.deviceReference ?? '',
        batchReference: input.batchReference ?? '',
      }),
    },
  );
}

export function completeV2HistologyPhase(slideId: string, phaseCode: HistologyPhaseCode) {
  return phaseRequest<V2HistologyPhase>(
    `/api/v2/histology/slides/${slideId}/phases/${phaseCode}/complete`,
    { method: 'POST', body: '{}' },
  );
}

export function recordV2HistologyException(input: {
  slideId: string;
  phaseCode: TechnicalTraceStageCode;
  exceptionCode: string;
  note: string;
}) {
  return phaseRequest<V2HistologyPhase>(
    `/api/v2/histology/slides/${input.slideId}/phases/${input.phaseCode}/exception`,
    {
      method: 'POST',
      body: JSON.stringify({ exceptionCode: input.exceptionCode, note: input.note }),
    },
  );
}

export function startV2HistologyPhaseBatch(slideIds: string[], phaseCode: HistologyPhaseCode) {
  return phaseRequest<V2HistologyPhase[]>(`/api/v2/histology/phases/${phaseCode}/start-batch`, {
    method: 'POST',
    body: JSON.stringify({ slideIds }),
  });
}

export function completeV2HistologyPhaseBatch(slideIds: string[], phaseCode: HistologyPhaseCode) {
  return phaseRequest<V2HistologyPhase[]>(`/api/v2/histology/phases/${phaseCode}/complete-batch`, {
    method: 'POST',
    body: JSON.stringify({ slideIds }),
  });
}

export function completeV2TechnicalTrace(input: {
  targetKind: 'BLOCK' | 'SLIDE';
  targetId: string;
  stageCode: TechnicalTraceStageCode;
  equipmentReference?: string;
  stainCode?: string;
  note?: string;
}) {
  const { targetKind, targetId, ...body } = input;
  return phaseRequest<V2HistologyPhase>(
    `/api/v2/histology/traces/${targetKind}/${targetId}/complete`,
    { method: 'POST', body: JSON.stringify(body) },
  );
}

export function completeV2TechnicalTraceBatch(input: {
  targetKind: 'BLOCK' | 'SLIDE';
  targetIds: string[];
  stageCode: TechnicalTraceStageCode;
  equipmentReference?: string;
  stainCode?: string;
  note?: string;
}) {
  const { targetKind, ...body } = input;
  return phaseRequest<V2HistologyPhase[]>(`/api/v2/histology/traces/${targetKind}/complete-batch`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function resolveV2ProductionException(factId: string, note: string) {
  return phaseRequest<V2HistologyPhase>(`/api/v2/histology/traces/${factId}/resolve-exception`, {
    method: 'POST',
    body: JSON.stringify({ note }),
  });
}
