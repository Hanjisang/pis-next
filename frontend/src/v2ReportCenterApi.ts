export type V2ReportQueueItem = {
  diagnosisId: string;
  caseId: string;
  pathologyNo: string;
  patientReference: string;
  businessTypeCode: string;
  queueCode: 'WAITING_SIGN' | 'SIGNED' | 'WITHDRAWN' | 'SUPPLEMENTAL' | string;
  reportId: string | null;
  reportNo: string | null;
  statusCode: string | null;
  occurredAt: string | null;
  targetLabel: string;
  tatStatus:
    | 'UNCONFIGURED'
    | 'NOT_APPLICABLE'
    | 'NORMAL'
    | 'WARNING'
    | 'OVERDUE'
    | 'COMPLETED_ON_TIME'
    | 'COMPLETED_OVERDUE'
    | string;
  elapsedMinutes: number;
  warningAt: string | null;
  dueAt: string | null;
  policyVersion: number | null;
  delay: {
    delayId: string;
    reasonCode: string;
    reasonDetail: string;
    expectedSignAt: string;
    declaredAt: string;
  } | null;
};

export type V2ReportCenter = {
  items: V2ReportQueueItem[];
  counts: {
    waitingSign: number;
    signed: number;
    withdrawn: number;
    supplemental: number;
    recentSigned: number;
    warning: number;
    overdue: number;
    delayed: number;
  };
  refreshedAt: string;
};

export type V2ClinicianReportResult = {
  reportId: string;
  reportNo: string;
  caseId: string;
  pathologyNo: string;
  patientReference: string;
  reportNature: 'ORIGINAL' | 'SUPPLEMENTAL' | string;
  signedAt: string;
  pdfContentHash: string;
};

export type V2PatientReportResult = Omit<V2ClinicianReportResult, 'patientReference'>;

export async function getV2ReportCenter(): Promise<V2ReportCenter> {
  const response = await fetch('/api/v2/report-center');
  const body = (await response.json()) as V2ReportCenter | { message?: string };
  if (!response.ok)
    throw new Error((body as { message?: string }).message ?? '报告队列暂时无法加载');
  return body as V2ReportCenter;
}

export async function queryV2ClinicianReports(criteria: {
  reportNo?: string;
  pathologyNo?: string;
  patientReference?: string;
}): Promise<V2ClinicianReportResult[]> {
  const parameters = new URLSearchParams();
  Object.entries(criteria).forEach(([key, value]) => {
    if (value?.trim()) parameters.set(key, value.trim());
  });
  const response = await fetch(`/api/v2/report-center/access/clinician?${parameters}`);
  const body = (await response.json()) as V2ClinicianReportResult[] | { message?: string };
  if (!response.ok) throw new Error((body as { message?: string }).message ?? '临床报告查询失败');
  return body as V2ClinicianReportResult[];
}

export function queryV2PatientReports(input: {
  reportNo: string;
  pathologyNo: string;
  identityReference: string;
  terminalReference: string;
}) {
  return reportCenterRequest<V2PatientReportResult[]>('/access/patient', input);
}

async function reportCenterRequest<T>(path: string, body: unknown): Promise<T> {
  const response = await fetch(`/api/v2/report-center${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  const payload = (await response.json()) as T | { message?: string };
  if (!response.ok)
    throw new Error((payload as { message?: string }).message ?? '报告时效操作失败');
  return payload as T;
}

export function declareV2ReportDelay(input: {
  diagnosisId: string;
  reasonCode: string;
  reasonDetail: string;
  expectedSignAt: string;
  idempotencyKey: string;
}) {
  return reportCenterRequest<{ delayId: string; duplicate: boolean }>('/delays', input);
}

export function resolveV2ReportDelay(
  delayId: string,
  input: { resolutionNote: string; idempotencyKey: string },
) {
  return reportCenterRequest<{ delayId: string; resolvedAt: string; duplicate: boolean }>(
    `/delays/${delayId}/resolve`,
    input,
  );
}
