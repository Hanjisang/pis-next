export type V2ReportQueueItem = {
  diagnosisId: string;
  caseId: string;
  pathologyNo: string;
  patientReference: string;
  queueCode: 'WAITING_SIGN' | 'SIGNED' | 'WITHDRAWN' | 'SUPPLEMENTAL' | string;
  reportId: string | null;
  reportNo: string | null;
  statusCode: string | null;
  occurredAt: string | null;
  targetLabel: string;
};

export type V2ReportCenter = {
  items: V2ReportQueueItem[];
  counts: {
    waitingSign: number;
    signed: number;
    withdrawn: number;
    supplemental: number;
    recentSigned: number;
  };
  refreshedAt: string;
};

export async function getV2ReportCenter(): Promise<V2ReportCenter> {
  const response = await fetch('/api/v2/report-center');
  const body = (await response.json()) as V2ReportCenter | { message?: string };
  if (!response.ok)
    throw new Error((body as { message?: string }).message ?? '报告队列暂时无法加载');
  return body as V2ReportCenter;
}
