export type OperationsRow = Record<string, string | number | boolean | null>;
export type OperationsOverview = Record<string, OperationsRow[]>;

export type OperationsNotification = {
  id: string;
  recipientReference: string;
  typeCode: string;
  title: string;
  body: string;
  businessPath?: string | null;
  priorityCode: string;
  createdAt: string;
  readAt?: string | null;
};
export type OperationsEquipment = OperationsRow & {
  id: string;
  equipmentCode: string;
  name: string;
  categoryCode: string;
  statusCode: string;
};
export type OperationsStock = OperationsRow & {
  batchId: string;
  catalogId: string;
  materialCode: string;
  name: string;
  batchNo: string;
  balance: number;
};
export type OperationsQualityDocument = OperationsRow & {
  id: string;
  title: string;
  documentNo: string;
  versionLabel: string;
  statusCode: string;
};
export type OperationsSpace = OperationsRow & {
  id: string;
  spaceCode: string;
  name: string;
  zoneCode: string;
  active: boolean;
};
export type OperationsCriticalValue = OperationsRow & {
  id: string;
  caseId: string;
  valueTypeCode: string;
  gradeCode: string;
  statusCode: string;
  createdAt: string;
};
export type OperationsReportDistribution = {
  id: string;
  reportId: string;
  targetCode: string;
  requestedAt: string;
  sentAt?: string | null;
  statusCode: string;
  retryCount: number;
  lastError?: string | null;
  deliveryReference?: string | null;
  errorCode?: string | null;
};
export type OperationsReportPrint = {
  id: string;
  reportId: string;
  identityReference: string;
  terminalReference: string;
  printerReference: string;
  printedAt: string;
  resultCode: string;
  copyCount: number;
  deviceJobReference?: string | null;
  errorCode?: string | null;
  failureReason?: string | null;
};
export type OperationsReportOutputResult = {
  id: string;
  statusCode: string;
  duplicate: boolean;
  errorMessage?: string | null;
};

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`/api/v2/operations${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init.headers ?? {}) },
  });
  const body = (await response.json().catch(() => ({}))) as T & { message?: string };
  if (!response.ok) throw new Error(body.message ?? '业务数据暂时无法处理');
  return body as T;
}

const post = <T>(path: string, body?: unknown) =>
  request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) });

export const getOperationsOverview = () => request<OperationsOverview>('/overview');
export const getOperationsNotifications = () => request<OperationsNotification[]>('/notifications');
export const readOperationsNotification = (id: string) => post<void>(`/notifications/${id}/read`);
export const getOperationsSchedules = (from = '', to = '') =>
  request<OperationsRow[]>(
    `/staff-schedules?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,
  );
export const createOperationsSchedule = (body: unknown) =>
  post<{ id: string }>('/staff-schedules', body);
export const getOperationsQualityDocuments = () =>
  request<OperationsQualityDocument[]>('/quality-documents');
export const createOperationsQualityDocument = (body: unknown) =>
  post<{ id: string }>('/quality-documents', body);
export const transitionOperationsQualityDocument = (id: string, status: string) =>
  post<OperationsQualityDocument>(`/quality-documents/${id}/${status}`);
export const getOperationsEquipment = () => request<OperationsEquipment[]>('/equipment');
export const createOperationsEquipment = (body: unknown) =>
  post<{ id: string }>('/equipment', body);
export const createOperationsEquipmentEvent = (id: string, body: unknown) =>
  post<{ id: string }>(`/equipment/${id}/events`, body);
export const getOperationsCatalog = () => request<OperationsRow[]>('/consumables/catalog');
export const createOperationsCatalog = (body: unknown) =>
  post<{ id: string }>('/consumables/catalog', body);
export const createOperationsBatch = (catalogId: string, body: unknown) =>
  post<{ id: string }>(`/consumables/catalog/${catalogId}/batches`, body);
export const recordOperationsStock = (batchId: string, body: unknown) =>
  post<{ id: string }>(`/consumables/batches/${batchId}/transactions`, body);
export const getOperationsStock = () => request<OperationsStock[]>('/consumables/stock');
export const createOperationsRequisition = (body: unknown) =>
  post<{ id: string }>('/consumables/requisitions', body);
export const decideOperationsRequisition = (id: string, status: string) =>
  post<void>(`/consumables/requisitions/${id}/decision`, { status });
export const createOperationsProcurement = (body: unknown) =>
  post<{ id: string }>('/procurement/requests', body);
export const approveOperationsProcurement = (id: string, decision: string, comment: string) =>
  post<void>(`/procurement/requests/${id}/approval`, { decision, comment });
export const attachOperationsProcurement = (id: string, kind: string, reference: string) =>
  post<{ id: string }>(`/procurement/requests/${id}/attachments`, { kind, reference });
export const getOperationsSpaces = () => request<OperationsSpace[]>('/spaces');
export const createOperationsSpace = (body: unknown) => post<{ id: string }>('/spaces', body);
export const addOperationsEnvironment = (id: string, body: unknown) =>
  post<{ id: string }>(`/spaces/${id}/environment`, body);
export const addOperationsSafety = (id: string, body: unknown) =>
  post<{ id: string }>(`/spaces/${id}/safety`, body);
export const getOperationsCriticalValues = () =>
  request<OperationsCriticalValue[]>('/critical-values');
export const createOperationsCriticalValue = (caseId: string, body: unknown) =>
  post<{ id: string }>(`/cases/${caseId}/critical-values`, body);
export const notifyOperationsCriticalValue = (id: string, body: unknown) =>
  post<{ id: string }>(`/critical-values/${id}/notify`, body);
export const acknowledgeOperationsCriticalValue = (id: string) =>
  post<void>(`/critical-value-notifications/${id}/acknowledge`);
export const feedbackOperationsCriticalValue = (id: string, content: string) =>
  post<{ id: string }>(`/critical-values/${id}/feedback`, { content });
export const distributeOperationsReport = (
  reportId: string,
  targetCode: string,
  idempotencyKey: string,
) =>
  post<OperationsReportOutputResult>(`/reports/${reportId}/distribution`, {
    targetCode,
    idempotencyKey,
  });
export const updateOperationsDistribution = (id: string, status: string, error?: string) =>
  post<void>(`/report-distributions/${id}/status`, { status, error });
export const printOperationsReport = (
  reportId: string,
  body: {
    identityReference: string;
    terminalReference: string;
    printerReference: string;
    copyCount: number;
    idempotencyKey: string;
  },
) => post<OperationsReportOutputResult>(`/reports/${reportId}/print`, body);
export const getOperationsReportDistributions = (reportId: string) =>
  request<OperationsReportDistribution[]>(`/reports/${reportId}/distributions`);
export const getOperationsReportPrints = (reportId: string) =>
  request<OperationsReportPrint[]>(`/reports/${reportId}/prints`);
export const getOperationsReportPrinterStatus = (printerReference: string) =>
  request<{ printerReference: string; statusCode: string; detail: string }>(
    `/report-printer-status?printerReference=${encodeURIComponent(printerReference)}`,
  );
export const getOperationsAddresses = () => request<OperationsRow[]>('/logistics/addresses');
export const createOperationsAddress = (body: unknown) =>
  post<{ id: string }>('/logistics/addresses', body);
export const createOperationsPackage = (body: unknown) =>
  post<{ id: string }>('/logistics/packages', body);
export const addOperationsPackageEvent = (id: string, statusCode: string, note: string) =>
  post<{ id: string }>(`/logistics/packages/${id}/events`, { statusCode, note });
export const createOperationsMolecularProject = (body: unknown) =>
  post<{ id: string }>('/molecular/projects', body);
export const createOperationsMolecularInstrument = (body: unknown) =>
  post<{ id: string }>('/molecular/instruments', body);
export const createOperationsMolecularReagent = (body: unknown) =>
  post<{ id: string }>('/molecular/reagents', body);
export const createOperationsMolecularTest = (body: unknown) =>
  post<{ id: string }>('/molecular/tests', body);
export const completeOperationsMolecularTest = (id: string, body: unknown) =>
  post<void>(`/molecular/tests/${id}/complete`, body);
export const archiveOperationsDigitalSlide = (body: unknown) =>
  post<{ id: string }>('/digital-archive', body);
export const updateOperationsDigitalArchive = (id: string, status: string) =>
  post<void>(`/digital-archive/${id}/status`, { status });
export const createOperationsRegionalShare = (body: unknown) =>
  post<{ id: string }>('/regional/shares', body);
export const recordOperationsRegionalAccess = (
  id: string,
  accessorReference: string,
  actionCode: string,
) => post<{ id: string }>(`/regional/shares/${id}/access`, { accessorReference, actionCode });
export const recordOperationsIncome = (body: unknown) => post<{ id: string }>('/income', body);
export const createOperationsMigrationJob = (body: unknown) =>
  post<{ id: string }>('/migration/jobs', body);
export const addOperationsMigrationRecord = (body: unknown) =>
  post<{ id: string }>('/migration/records', body);
export const addOperationsMigrationError = (body: unknown) =>
  post<{ id: string }>('/migration/errors', body);
