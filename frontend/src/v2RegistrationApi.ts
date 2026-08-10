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
  pendingApplications: Array<{
    applicationNo: string;
    patientReference: string;
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
    pendingApplications: body.pendingApplications ?? [],
    recentRegistrations: body.recentRegistrations ?? [],
    refreshedAt: body.refreshedAt ?? new Date().toISOString(),
  };
}
