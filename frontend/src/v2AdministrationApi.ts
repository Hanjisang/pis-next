export type V2AdministrationSnapshot = {
  users: Array<{
    id: string;
    username: string;
    displayName: string;
    roleCode: string;
    hospitalScope: string;
    departmentScope: string;
    taskScope: string;
    enabled: boolean;
    doctorId: string | null;
    doctorCode: string | null;
    doctorTitle: string | null;
    doctorDepartment: string | null;
    doctorEnabled: boolean;
    businessPermissions: string[];
    actionPermissions: string[];
  }>;
  roles: string[];
  permissions: Array<{ code: string; label: string; dimension: 'BUSINESS' | 'ACTION' }>;
  organizations: Array<{
    hospitalProfileCode: string;
    campusCode: string | null;
    departmentCode: string | null;
    departmentName: string | null;
  }>;
};

async function administrationRequest<T>(path = '', init: RequestInit = {}) {
  const response = await fetch(`/api/v2/administration${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init.headers ?? {}) },
  });
  const body = (await response.json()) as T | { message?: string };
  if (!response.ok)
    throw new Error((body as { message?: string }).message ?? '系统管理暂时无法加载');
  return body as T;
}

export function getV2Administration() {
  return administrationRequest<V2AdministrationSnapshot>();
}

export function updateV2AdminUser(id: string, body: unknown) {
  return administrationRequest<V2AdministrationSnapshot>(`/users/${id}`, {
    method: 'PUT',
    body: JSON.stringify(body),
  });
}
