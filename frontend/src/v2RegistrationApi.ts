export type V2RegistrationCaseRow = {
  caseId: string;
  caseNo: string;
  applicationNo: string;
  applicationItemCode: string;
  businessTypeCode: string;
  businessTypeName: string;
  patientReference: string;
  registeredAt: string;
};

export type V2RegistrationQueue = {
  sourceAvailable: boolean;
  sourceMessage: string | null;
  pendingApplications: Array<{
    applicationId: string;
    applicationNo: string;
    patientReference: string;
    visitReference: string | null;
    department: string | null;
    doctor: string | null;
    applicationItemCode: string;
    businessTypeCode: string | null;
    businessTypeName: string | null;
    receivedAt: string;
  }>;
  cancelledApplications: Array<{
    applicationId: string;
    applicationNo: string;
    patientReference: string;
    visitReference: string | null;
    department: string | null;
    doctor: string | null;
    applicationItemCode: string;
    receivedAt: string;
  }>;
  recentRegistrations: V2RegistrationCaseRow[];
  refreshedAt: string;
};

export async function getV2RegistrationQueue(): Promise<V2RegistrationQueue> {
  const response = await fetch('/api/v2/registration/queue');
  const body = (await response.json()) as Partial<V2RegistrationQueue> & { message?: string };
  if (!response.ok) throw new Error(body.message ?? '登记队列暂时无法加载');
  return {
    sourceAvailable: body.sourceAvailable ?? false,
    sourceMessage: body.sourceMessage ?? null,
    pendingApplications: body.pendingApplications ?? [],
    cancelledApplications: body.cancelledApplications ?? [],
    recentRegistrations: body.recentRegistrations ?? [],
    refreshedAt: body.refreshedAt ?? new Date().toISOString(),
  };
}

export async function registerV2InboundApplication(applicationId: string): Promise<V2CaseResult> {
  const response = await fetch(
    `/api/v2/registration/inbox/${encodeURIComponent(applicationId)}/register`,
    {
      method: 'POST',
    },
  );
  const body = (await response.json()) as { message?: string } & Record<string, unknown>;
  if (!response.ok) throw new Error(body.message ?? '申请登记未完成');
  return body as V2CaseResult;
}
import type { V2CaseResult } from './v2Api';
