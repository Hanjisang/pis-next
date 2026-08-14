export type MolecularProject = {
  id: string;
  projectCode: string;
  projectName: string;
  projectTypeCode: string;
  enabled: boolean;
};
export type MolecularInstrument = {
  id: string;
  instrumentCode: string;
  name: string;
  adapterCode: string;
  enabled: boolean;
};
export type MolecularReagent = {
  id: string;
  kitCode: string;
  manufacturer: string | null;
  batchNo: string;
  expiryDate: string | null;
  enabled: boolean;
};
export type MolecularTest = {
  id: string;
  caseId: string;
  specimenId: string;
  projectId: string;
  projectCode: string;
  detectionNo: string;
  instrumentId: string;
  instrumentCode: string;
  adapterCode: string;
  reagentKitId: string;
  rawDataReference: string;
  structuredResult: string | null;
  analysisResult: string | null;
  statusCode: string;
  resultId: string | null;
  createdAt: string;
  startedAt: string | null;
  completedAt: string | null;
  concurrencyVersion: number;
};
export type MolecularAttachment = {
  id: string;
  testId: string;
  digitalSlideId: string | null;
  attachmentReference: string | null;
  description: string | null;
  createdAt: string;
  createdBy: string;
};
export type MolecularAttempt = {
  id: string;
  testId: string;
  instrumentId: string;
  adapterCode: string;
  attemptNo: number;
  requestReference: string;
  statusCode: string;
  responseReference: string | null;
  errorCode: string | null;
  errorMessage: string | null;
  requestedAt: string;
  completedAt: string;
  requestedBy: string;
};
export type MolecularWorkbench = {
  refreshedAt: string;
  projects: MolecularProject[];
  instruments: MolecularInstrument[];
  reagents: MolecularReagent[];
  tests: MolecularTest[];
  attachments: MolecularAttachment[];
  attempts: MolecularAttempt[];
};

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init);
  const body = (await response.json().catch(() => ({}))) as { message?: string };
  if (!response.ok) throw new Error(body.message ?? '分子病理操作失败');
  return body as T;
}
const json = (body: unknown): RequestInit => ({
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body),
});
export const getMolecularWorkbench = () =>
  request<MolecularWorkbench>('/api/v2/molecular/workbench');
export const createMolecularTest = (body: Record<string, unknown>) =>
  request<{ id: string; duplicate: boolean }>('/api/v2/molecular/tests', json(body));
export const startMolecularTest = (id: string, key: string) =>
  request(`/api/v2/molecular/tests/${id}/start`, json({ idempotencyKey: key }));
export const completeMolecularTest = (id: string, body: Record<string, unknown>) =>
  request(`/api/v2/molecular/tests/${id}/complete`, json(body));
export const addMolecularAttachment = (id: string, body: Record<string, unknown>) =>
  request(`/api/v2/molecular/tests/${id}/attachments`, json(body));
