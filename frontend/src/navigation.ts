import type { V2AuthUser } from './auth';

export type V2RouteName =
  | 'workbench'
  | 'case'
  | 'registration'
  | 'grossing'
  | 'production'
  | 'diagnosis'
  | 'frozen'
  | 'technical-orders'
  | 'reports'
  | 'digital-slides'
  | 'material-custody'
  | 'search'
  | 'quality'
  | 'configuration'
  | 'system';

export type V2Route = {
  name: V2RouteName;
  caseId: string;
  roundId: string;
  slideId: string;
  focusKind: string;
  focusId: string;
};

export type NavigationItem = {
  name: V2RouteName;
  label: string;
  shortLabel: string;
  permissions?: string[];
};

export const primaryNavigation: NavigationItem[] = [
  { name: 'workbench', label: '工作台', shortLabel: '工作台' },
  {
    name: 'registration',
    label: '登记',
    shortLabel: '登记',
    permissions: ['P14-PERM-004'],
  },
  {
    name: 'grossing',
    label: '取材',
    shortLabel: '取材',
    permissions: ['P14-PERM-013'],
  },
  {
    name: 'production',
    label: '制片',
    shortLabel: '制片',
    permissions: ['P14-PERM-014'],
  },
  {
    name: 'diagnosis',
    label: '诊断',
    shortLabel: '诊断',
    permissions: ['P14-PERM-034'],
  },
  {
    name: 'frozen',
    label: '冰冻',
    shortLabel: '冰冻',
    permissions: ['P14-PERM-008', 'P14-PERM-034'],
  },
  {
    name: 'technical-orders',
    label: '技术医嘱',
    shortLabel: '医嘱',
    permissions: ['P14-PERM-017'],
  },
  {
    name: 'reports',
    label: '报告',
    shortLabel: '报告',
    permissions: ['P14-PERM-055'],
  },
  {
    name: 'material-custody',
    label: '归档借阅',
    shortLabel: '归档',
    permissions: ['P14-PERM-014'],
  },
  {
    name: 'search',
    label: '查询',
    shortLabel: '查询',
    permissions: ['P14-PERM-048'],
  },
  {
    name: 'quality',
    label: '质控统计',
    shortLabel: '质控',
    permissions: ['P14-PERM-048'],
  },
  { name: 'configuration', label: '配置', shortLabel: '配置', permissions: ['P14-PERM-001'] },
  { name: 'system', label: '系统管理', shortLabel: '系统', permissions: ['P14-PERM-001'] },
];

const segmentToRoute: Record<string, V2RouteName> = {
  workbench: 'workbench',
  cases: 'case',
  registration: 'registration',
  grossing: 'grossing',
  production: 'production',
  diagnosis: 'diagnosis',
  frozen: 'frozen',
  'technical-orders': 'technical-orders',
  reports: 'reports',
  'digital-slides': 'digital-slides',
  'material-custody': 'material-custody',
  search: 'search',
  quality: 'quality',
  configuration: 'configuration',
  system: 'system',
};

export function parseV2Route(location: Pick<Location, 'pathname' | 'search'>): V2Route {
  const parts = location.pathname.split('/').filter(Boolean);
  const name = parts[0] === 'v2' ? segmentToRoute[parts[1] ?? 'workbench'] : 'workbench';
  const query = new URLSearchParams(location.search);
  return {
    name: name ?? 'workbench',
    caseId: parts[2] ?? query.get('caseId') ?? '',
    roundId: query.get('roundId') ?? '',
    slideId: query.get('slideId') ?? '',
    focusKind: query.get('focus') ?? (query.has('reportId') ? 'report' : ''),
    focusId: query.get('focusId') ?? query.get('reportId') ?? '',
  };
}

export function routePath(
  name: V2RouteName,
  options: { caseId?: string; roundId?: string; slideId?: string } = {},
): string {
  const encodedCaseId = options.caseId ? `/${encodeURIComponent(options.caseId)}` : '';
  const query = new URLSearchParams();
  if (options.roundId) query.set('roundId', options.roundId);
  if (options.slideId) query.set('slideId', options.slideId);
  const suffix = query.size ? `?${query.toString()}` : '';
  return `/v2/${name}${encodedCaseId}${suffix}`;
}

export function navigationForUser(user: V2AuthUser | null): NavigationItem[] {
  if (!user) return primaryNavigation.filter((item) => !item.permissions?.length);
  return primaryNavigation.filter(
    (item) =>
      !item.permissions?.length ||
      item.permissions.some((permission) => user.permissions.includes(permission)),
  );
}
