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
