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

export type V2ApplicationItem = {
  itemId: string;
  externalItemCode: string;
  itemName: string | null;
  specimenKindCode: string | null;
  specimenDescription: string | null;
  sequenceNo: number;
  statusCode: string;
};

export type V2ApplicationResult = {
  applicationId: string;
  applicationNo: string;
  sourceTypeCode: string;
  sourceSystemCode: string;
  patientReference: string;
  patientName: string | null;
  patientSexCode: string | null;
  patientBirthDate: string | null;
  visitReference: string | null;
  visitTypeCode: string | null;
  applicationDepartment: string | null;
  applicantReference: string | null;
  appliedAt: string;
  clinicalDiagnosis: string | null;
  medicalHistory: string | null;
  operationFinding: string | null;
  examinationPurpose: string | null;
  specimenDescription: string | null;
  note: string | null;
  statusCode: string;
  concurrencyVersion: number;
  items: V2ApplicationItem[];
};

export type V2ApplicationRegistrationResult = {
  applicationId: string;
  createdCaseCount: number;
  duplicate: boolean;
  cases: Array<{
    caseId: string;
    caseNo: string;
    applicationItemId: string;
    externalItemCode: string;
    businessTypeId: string;
    duplicate: boolean;
  }>;
};

async function applicationRequest<T>(path: string, init: RequestInit): Promise<T> {
  const response = await fetch(`/api/v2/applications${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init.headers ?? {}) },
  });
  const body = (await response.json()) as T & { message?: string; error_code?: string };
  if (!response.ok) {
    throw new Error(
      `${body.error_code ?? 'V2-APPLICATION-FAILED'}: ${body.message ?? '申请操作失败'}`,
    );
  }
  return body;
}

export function createV2Application(input: {
  applicationNo: string;
  sourceTypeCode: string;
  sourceSystemCode: string;
  patientReference: string;
  patientName?: string;
  visitReference: string;
  applicationDepartment?: string;
  applicantReference?: string;
  examinationPurpose?: string;
  specimenDescription?: string;
  note?: string;
  items: Array<{
    externalItemCode: string;
    itemName?: string;
    specimenKindCode?: string;
    specimenDescription?: string;
    sequenceNo: number;
  }>;
}): Promise<V2ApplicationResult> {
  return applicationRequest('', { method: 'POST', body: JSON.stringify(input) });
}

export function registerV2Application(
  applicationId: string,
): Promise<V2ApplicationRegistrationResult> {
  return applicationRequest(`/${encodeURIComponent(applicationId)}/register`, {
    method: 'POST',
    body: JSON.stringify({
      receiptKindCode: 'REGISTRATION',
      printerProfileCode: 'MOCK://SYNTH-PRINTER',
    }),
  });
}
