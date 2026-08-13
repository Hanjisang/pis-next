export type FrozenWorkspace = {
  frozenCaseId: string;
  pathologyNo: string;
  businessTypeCode: string;
  rounds: Array<{
    roundId: string;
    roundNo: number;
    status: string;
    specimens: Array<{
      specimenId: string;
      specimenNo: string;
      specimenCode: string;
      specimenKindCode: string;
      collectionSite: string;
      specimenName?: string;
    }>;
    totalRequiredSlides: number;
    completedRequiredSlides: number;
    productionComplete: boolean;
    diagnosisId?: string;
    arrivalTime: string;
    registeredAt: string;
    grossingStartTime?: string;
    slideCompletedTime?: string;
    diagnosisSignedTime?: string;
    cancelledAt?: string | null;
    cancellationReason?: string | null;
    elapsedMinutes?: number;
    tatStatus?: 'NORMAL' | 'WARNING' | 'OVERDUE' | string;
    tatAlertAcknowledged: boolean;
    notificationStatus?: string | null;
    notificationMessageLogId?: string | null;
    notificationAttempts: NotificationAttempt[];
    reportStatus: string;
  }>;
  routineCaseId?: string;
  routinePathologyNo?: string | null;
  ended?: boolean;
};

export type NotificationAttempt = {
  attemptId: string;
  attemptNo: number;
  attemptedAt: string;
  resultCode: 'SUCCEEDED' | 'FAILED' | string;
  errorCode?: string | null;
  errorMessage?: string | null;
};

export type FrozenNotificationHistory = {
  reportId: string;
  reportNo: string;
  reportStatus: string;
  target: string;
  channel: string;
  statusCode: string;
  lastAttemptAt?: string | null;
  errorCode?: string | null;
  errorMessage?: string | null;
  attempts: NotificationAttempt[];
};

export type FrozenRoutineComparison = {
  frozenCaseId: string;
  frozenPathologyNo: string;
  routineCaseId: string;
  routinePathologyNo: string;
  frozenRounds: Array<{
    roundId: string;
    roundNo: number;
    specimenSummary: string;
    diagnosisText: string;
    reportStatus: string;
    signedAt?: string | null;
    doctor?: string | null;
    tatMinutes: number;
  }>;
  routineDiagnosis: string;
  routineReportStatus: string;
  routineSignedAt?: string | null;
  routineDoctor?: string | null;
};

export type DigitalSlide = {
  digitalSlideId: string;
  caseId: string;
  blockId?: string;
  slideId?: string;
  bindingModeCode: string;
  statusCode: string;
  viewerReference: string;
  sourcePlatform: string;
};

export type QcRule = {
  ruleCode: string;
  ruleName: string;
  metricCode: string;
  warningThreshold: number;
  overdueThreshold: number;
};

export type QcEvaluation = {
  ruleCode: string;
  metricCode: string;
  value: number;
  statusCode: string;
  evaluatedAt: string;
};

export type StatisticsSummary = {
  counts: Record<string, number>;
  businessTypeDistribution: Array<{ businessTypeCode: string; count: number }>;
};

export async function operationsRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`/api/v2${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init.headers ?? {}) },
  });
  const body = (await response.json()) as T | { message?: string; error_code?: string };
  if (!response.ok) {
    const failure = body as { message?: string; error_code?: string };
    throw new Error(
      `${failure.error_code ?? 'V2-REQUEST-FAILED'}: ${failure.message ?? '请求失败'}`,
    );
  }
  return body as T;
}
