export type V2AuthUser = {
  userId: string;
  username: string;
  displayName: string;
  roleCode: string;
  department?: string | null;
  permissions: string[];
  doctor?: {
    id: string;
    doctorCode: string;
    displayName: string;
    title?: string | null;
    department?: string | null;
  } | null;
  organization?: {
    hospitalProfileId?: string | null;
    hospitalProfileCode?: string | null;
    campusId?: string | null;
    campusCode?: string | null;
    departmentId?: string | null;
    departmentCode?: string | null;
    departmentName?: string | null;
  } | null;
};

export function roleName(roleCode: string): string {
  return (
    {
      ADMIN: '系统管理员',
      REGISTRAR: '登记员',
      TECHNICIAN: '技术员',
      DOCTOR: '病理医生',
      AUDITOR: '审核医生',
    }[roleCode] ?? roleCode
  );
}

export function departmentName(user: V2AuthUser | null): string {
  if (user?.organization?.departmentName) return user.organization.departmentName;
  const code = user?.organization?.departmentCode ?? user?.department ?? '';
  return (
    {
      ADMINISTRATION: '系统管理',
      PATHOLOGY: '病理科',
      REGISTRATION: '登记组',
      TECHNICAL: '技术组',
    }[code] ??
    code ??
    '病理科'
  );
}

export function currentMedicalActor(user: V2AuthUser | null): string {
  return user?.doctor?.id ?? user?.userId ?? '';
}

export function currentRecorder(user: V2AuthUser | null): string {
  return user?.userId ?? '';
}

async function passwordRequest(path: string, body: unknown): Promise<void> {
  const response = await fetch(`/api/v2/auth${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (response.ok) return;
  const error = (await response.json().catch(() => ({}))) as { message?: string };
  throw new Error(error.message ?? '密码操作失败');
}

export function changeOwnPassword(currentPassword: string, newPassword: string) {
  return passwordRequest('/password', { currentPassword, newPassword });
}

export function resetUserPassword(userId: string, newPassword: string) {
  return passwordRequest(`/users/${encodeURIComponent(userId)}/password-reset`, { newPassword });
}
