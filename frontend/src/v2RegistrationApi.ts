import type { V2CaseResult } from './v2Api';

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
  cancelledApplications: Array<Record<string, unknown>>;
  recentRegistrations: V2RegistrationCaseRow[];
  refreshedAt: string;
};

export type V2ApplicationItem = {
  itemId: string;
  externalItemCode: string;
  itemName: string | null;
  specimenKindCode: string | null;
  specimenDescription: string | null;
  sequenceNo: number;
  statusCode: 'PENDING' | 'REGISTERED' | 'REJECTED' | 'CANCELLED';
  businessTypeCode: string | null;
  caseId: string | null;
  pathologyNo: string | null;
};

export type V2ApplicationInput = {
  applicationNo: string;
  sourceTypeCode: string;
  sourceSystemCode: string;
  patientReference: string;
  patientName: string;
  patientSexCode?: string;
  patientBirthDate?: string;
  patientInfoSourceCode?: string;
  patientIdentityNo?: string;
  visitCardNo?: string;
  contactPhone?: string;
  ageValue?: number;
  ageUnitCode?: string;
  visitReference: string;
  visitTypeCode: string;
  wardReference?: string;
  bedReference?: string;
  applicationDepartment: string;
  applicantReference: string;
  clinicalDiagnosis?: string;
  medicalHistory?: string;
  operationFinding?: string;
  surgeryName?: string;
  examinationPurpose?: string;
  specimenDescription?: string;
  note?: string;
  items: Array<{
    itemId?: string;
    externalItemCode: string;
    itemName?: string;
    specimenKindCode?: string;
    specimenDescription?: string;
    sequenceNo: number;
  }>;
};

export type V2ApplicationResult = Omit<V2ApplicationInput, 'items'> & {
  applicationId: string;
  appliedAt: string;
  statusCode: string;
  concurrencyVersion: number;
  duplicate: boolean;
  items: V2ApplicationItem[];
};

export type V2ValidationIssue = {
  field: string;
  message: string;
  severity: 'ERROR' | 'WARNING';
  applicationItemId: string | null;
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
    specimenId: string;
    duplicate: boolean;
  }>;
};

async function applicationRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
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

export async function getV2RegistrationQueue(): Promise<V2RegistrationQueue> {
  const response = await fetch('/api/v2/registration/queue');
  const body = (await response.json()) as V2RegistrationQueue & { message?: string };
  if (!response.ok) throw new Error(body.message ?? '登记队列暂时无法加载');
  return body;
}

export async function registerV2InboundApplication(applicationId: string): Promise<V2CaseResult> {
  const response = await fetch(
    `/api/v2/registration/inbox/${encodeURIComponent(applicationId)}/register`,
    { method: 'POST' },
  );
  const body = (await response.json()) as V2CaseResult & { message?: string };
  if (!response.ok) throw new Error(body.message ?? '申请登记未完成');
  return body;
}

export function validateV2Application(input: V2ApplicationInput): Promise<{
  valid: boolean;
  issues: V2ValidationIssue[];
}> {
  return applicationRequest('/validate', { method: 'POST', body: JSON.stringify(input) });
}

export function createV2Application(input: V2ApplicationInput): Promise<V2ApplicationResult> {
  return applicationRequest('', { method: 'POST', body: JSON.stringify(input) });
}

export function updateV2Application(
  applicationId: string,
  input: Partial<V2ApplicationInput>,
): Promise<V2ApplicationResult> {
  return applicationRequest(`/${encodeURIComponent(applicationId)}`, {
    method: 'PUT',
    body: JSON.stringify(input),
  });
}

export function getV2Application(applicationId: string): Promise<V2ApplicationResult> {
  return applicationRequest(`/${encodeURIComponent(applicationId)}`, { method: 'GET' });
}

export function getV2ApplicationQueue() {
  return applicationRequest<
    Array<{
      applicationId: string;
      applicationNo: string;
      patientReference: string;
      patientName: string | null;
      visitReference: string | null;
      patientSexCode: string | null;
      applicationDepartment: string | null;
      applicantReference: string | null;
      appliedAt: string;
      applicationItemId: string;
      externalItemCode: string;
      itemName: string | null;
      specimenDescription: string | null;
      itemStatusCode: string;
      businessTypeCode: string | null;
    }>
  >('/queue', { method: 'GET' });
}

export function cancelV2Application(applicationId: string, reason: string) {
  return applicationRequest<V2ApplicationResult>(`/${encodeURIComponent(applicationId)}/cancel`, {
    method: 'POST',
    body: JSON.stringify({ reason }),
  });
}

export function lookupV2Patient(input: {
  patientId?: string;
  visitId?: string;
  outpatientNo?: string;
  inpatientNo?: string;
}) {
  return applicationRequest<{
    found: boolean;
    adapterCode: string;
    message: string;
    patientReference: string | null;
    patientName: string | null;
    patientSexCode: string | null;
    birthDate: string | null;
    ageValue: number | null;
    ageUnitCode: string | null;
    identityNo: string | null;
    visitReference: string | null;
    visitTypeCode: string | null;
    visitCardNo: string | null;
    contactPhone: string | null;
    departmentReference: string | null;
    wardReference: string | null;
    bedReference: string | null;
    clinicalDiagnosis: string | null;
    medicalHistory: string | null;
  }>('/patient-lookup', { method: 'POST', body: JSON.stringify(input) });
}

export function verifyV2IncomingSpecimen(input: {
  applicationId: string;
  applicationItemId: string;
  incomingSpecimenReference: string;
  patientReference: string;
  actualSpecimenDescription: string;
  outcomeCode: 'ACCEPTED' | 'REJECTED' | 'SUPPLEMENT_REQUIRED';
  reasonCode?: string;
  reasonText?: string;
  patientMatch: boolean;
  applicationMatch: boolean;
  quantityMatch: boolean;
  specimenMatch: boolean;
  containerMatch: boolean;
  fixationMatch: boolean;
}) {
  return applicationRequest<{
    statusCode: string;
    deliveredAt: string;
    duplicate: boolean;
  }>(`/${encodeURIComponent(input.applicationId)}/delivery`, {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export type V2BarcodeScanResult = {
  barcode: string;
  applicationId: string;
  applicationNo: string;
  patientReference: string;
  patientName: string | null;
  applicationItemId: string;
  itemName: string | null;
  specimenDescription: string | null;
  itemStatusCode: string;
  delivered: boolean;
  deliveredAt: string | null;
  deliveredBy: string | null;
};

export type V2DeliveryRecord = {
  deliveryId: string;
  applicationId: string;
  applicationItemId: string;
  applicationNo: string;
  visitReference: string | null;
  patientReference: string;
  patientName: string | null;
  externalItemCode: string | null;
  itemName: string | null;
  incomingSpecimenReference: string | null;
  specimenLabelCode: string | null;
  statusCode: string;
  reason: string | null;
  deliveredBy: string;
  deliveredAt: string;
};

export function scanV2ApplicationBarcode(barcode: string) {
  return applicationRequest<V2BarcodeScanResult>(
    `/barcode-scan?barcode=${encodeURIComponent(barcode)}`,
    { method: 'GET' },
  );
}

export function searchV2ApplicationDeliveries(filters: {
  visitReference?: string;
  from?: string;
  to?: string;
  externalItemCode?: string;
}) {
  const query = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (value) query.set(key, value);
  });
  return applicationRequest<V2DeliveryRecord[]>(`/deliveries?${query.toString()}`, {
    method: 'GET',
  });
}

export function v2ApplicationDeliveryExportUrl(filters: {
  visitReference?: string;
  from?: string;
  to?: string;
  externalItemCode?: string;
}) {
  const query = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (value) query.set(key, value);
  });
  return `/api/v2/applications/deliveries/export?${query.toString()}`;
}

export function printV2ApplicationBarcodes(
  applicationId: string,
  applicationItemIds: string[],
  copies = 1,
) {
  return applicationRequest<{
    successCount: number;
    requestedCount: number;
    allSucceeded: boolean;
  }>(`/${encodeURIComponent(applicationId)}/barcode-print`, {
    method: 'POST',
    body: JSON.stringify({
      applicationItemId: applicationItemIds,
      copies,
      printerProfileCode: 'MOCK://SYNTH-PRINTER',
    }),
  });
}

export function getV2ApplicationPrintHistory(applicationId: string) {
  return applicationRequest<
    Array<{
      printId: string;
      applicationItemId: string;
      barcode: string;
      printVersion: number;
      operationCode: 'PRINT' | 'REPRINT';
      copies: number;
      resultCode: string;
      requestedAt: string;
    }>
  >(`/${encodeURIComponent(applicationId)}/barcode-print-history`, { method: 'GET' });
}

export function registerV2Application(
  applicationId: string,
): Promise<V2ApplicationRegistrationResult> {
  return applicationRequest(`/${encodeURIComponent(applicationId)}/register`, {
    method: 'POST',
    body: '{}',
  });
}

export function registerV2ApplicationItem(
  applicationId: string,
  applicationItemId: string,
): Promise<V2ApplicationRegistrationResult> {
  return applicationRequest(
    `/${encodeURIComponent(applicationId)}/items/${encodeURIComponent(applicationItemId)}/register`,
    { method: 'POST', body: '{}' },
  );
}

async function registrationRequest<T>(path: string, body: unknown): Promise<T> {
  const response = await fetch(`/api/v2/registration${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  const result = (await response.json()) as T & { message?: string; error_code?: string };
  if (!response.ok)
    throw new Error(`${result.error_code ?? 'V2-PRINT-FAILED'}: ${result.message ?? '打印失败'}`);
  return result;
}

export function printV2SpecimenLabels(caseId: string, specimenIds: string[], copies = 1) {
  return registrationRequest<{
    successCount: number;
    requestedCount: number;
    allSucceeded: boolean;
  }>(`/cases/${encodeURIComponent(caseId)}/specimen-labels/print`, {
    specimenIds,
    copies,
    printerProfileCode: 'MOCK://SYNTH-PRINTER',
  });
}

export function printV2OutpatientReceipt(caseId: string, copies = 1) {
  return registrationRequest<{
    successCount: number;
    requestedCount: number;
    allSucceeded: boolean;
  }>(`/cases/${encodeURIComponent(caseId)}/receipt/print`, {
    copies,
    printerProfileCode: 'MOCK://SYNTH-PRINTER',
  });
}
