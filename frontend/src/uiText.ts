const businessTypeNames: Record<string, string> = {
  HISTOLOGY: '常规组织病理',
  ROUTINE: '常规组织病理',
  FROZEN: '冰冻',
  CYTOLOGY: '细胞病理',
  CYTOLOGY_NON_GYN: '细胞病理',
  MOLECULAR: '分子病理',
  CONSULTATION: '会诊',
  REFERRAL: '会诊',
};

const responsibilityNames: Record<string, string> = {
  INITIAL: '初诊',
  REVIEW: '复诊',
  AUDIT: '审核',
};

const statusNames: Record<string, string> = {
  ACTIVE: '进行中',
  CANCELLED: '已取消',
  OPEN: '待处理',
  PENDING: '待处理',
  EXECUTING: '处理中',
  IN_PROGRESS: '处理中',
  WAITING_RESULT: '待录结果',
  COMPLETED: '已完成',
  SIGNED: '已签发',
  EFFECTIVE: '已生效',
  WITHDRAWN: '已撤回',
  DRAFT: '草稿',
  ASSIGNED: '待处理',
  CLAIMED: '进行中',
};

const specimenKindNames: Record<string, string> = {
  TISSUE: '组织',
  FLUID: '液体',
  SMEAR: '涂片',
  EXTERNAL_MATERIAL: '外院材料',
};

const blockTypeNames: Record<string, string> = {
  ROUTINE: '常规蜡块',
  FROZEN: '冰冻蜡块',
  CELL_BLOCK: '细胞蜡块',
  EXTERNAL: '外院蜡块',
};

const errorGuidance: Array<[RegExp, string]> = [
  [/CASE_NOT_ACTIVE/i, '病例已取消或不在可签发状态。'],
  [/DIAGNOSIS_NOT_CREATED|尚未建立 Diagnosis/i, '尚未建立诊断，请先接诊并填写诊断。'],
  [/DIAGNOSIS_NOT_FOUND/i, '尚未建立诊断，请先接诊并填写诊断。'],
  [/DIAGNOSIS_NOT_VALID/i, '病理诊断尚未填写，请填写并保存诊断。'],
  [/REPORT_TEMPLATE_NOT_VALID/i, '当前业务类型没有可用的已发布报告模板。'],
  [/AUDIT_RESPONSIBILITY_NOT_FOUND/i, '尚未建立审核责任，请先提交并完成审核。'],
  [/AUDIT_DOCTOR_MISMATCH/i, '当前登录医生不是本病例审核医生，不能签发。'],
  [/BLOCKING_TECHNICAL_ORDER/i, '仍有必须等待的技术医嘱，请完成或取消后再签发。'],
  [/PRODUCTION.*NOT.*COMPLETE|初始材料尚未完成/i, '制片尚未完成，请先完成所有必需玻片。'],
  [
    /TECHNICAL.*(PENDING|BLOCK)|技术医嘱.*未完成/i,
    '仍有必须等待的技术医嘱，请完成或取消后再签发。',
  ],
  [/AUTHENTICATION|请先登录/i, '登录已失效，请重新登录后继续。'],
  [/FORBIDDEN|PERMISSION|无权限/i, '当前身份无权执行此操作，请联系有权限的人员。'],
  [/CONCURRENT|VERSION|并发/i, '记录已被他人更新，请刷新后重试。'],
];

export function businessTypeName(code?: string | null): string {
  if (!code) return '未识别业务类型';
  return businessTypeNames[code] ?? code;
}

export function responsibilityName(code?: string | null): string {
  if (!code) return '待分配';
  return responsibilityNames[code] ?? code;
}

export function statusName(code?: string | null): string {
  if (!code) return '未开始';
  return statusNames[code] ?? code;
}

export function specimenKindName(code?: string | null): string {
  if (!code) return '未标记';
  return specimenKindNames[code] ?? code;
}

export function blockTypeName(code?: string | null): string {
  if (!code) return '蜡块';
  return blockTypeNames[code] ?? code;
}

export function formatDateTime(value?: string | null): string {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date);
}

export function friendlyError(error: unknown, fallback = '操作失败，请稍后重试。'): string {
  const message =
    error instanceof Error ? error.message : typeof error === 'string' ? error : fallback;
  const guidance = errorGuidance.find(([pattern]) => pattern.test(message));
  if (guidance) return guidance[1];
  const withoutCode = message.replace(/^[A-Z0-9-]+:\s*/, '').trim();
  return withoutCode || fallback;
}

export function idempotencyKey(prefix: string): string {
  return `${prefix}-${crypto.randomUUID()}`;
}
