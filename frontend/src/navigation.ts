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
  | 'molecular'
  | 'material-custody'
  | 'search'
  | 'quality'
  | 'configuration'
  | 'business-operations'
  | 'system';

export type V2Route = {
  name: V2RouteName;
  caseId: string;
  roundId: string;
  slideId: string;
  focusKind: string;
  focusId: string;
  sourceType: string;
  sourceReferenceId: string;
  origin: 'workbench' | 'case' | 'search' | 'direct';
  queue: string;
  returnTo: string;
};

export type NavigationItem = {
  name: V2RouteName;
  label: string;
  shortLabel: string;
  permissions?: string[];
};

export const primaryNavigation: NavigationItem[] = [
  { name: 'workbench', label: '工作台', shortLabel: '工作台' },
];

/**
 * Clinical users do not receive a persistent module navigation. Administrators
 * use a separate shell so operational configuration cannot be confused with
 * the clinical work surface.
 */
export const adminNavigation: NavigationItem[] = [
  { name: 'workbench', label: '工作台', shortLabel: '工作台' },
  { name: 'configuration', label: '配置中心', shortLabel: '配置' },
  { name: 'quality', label: '质控与统计', shortLabel: '质控' },
  { name: 'business-operations', label: '科室运行', shortLabel: '运行' },
  { name: 'system', label: '系统管理', shortLabel: '管理' },
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
  molecular: 'molecular',
  'material-custody': 'material-custody',
  search: 'search',
  quality: 'quality',
  configuration: 'configuration',
  'business-operations': 'business-operations',
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
    sourceType: query.get('sourceType') ?? '',
    sourceReferenceId: query.get('sourceReferenceId') ?? '',
    origin: parseOrigin(query.get('origin')),
    queue: query.get('queue') ?? '',
    returnTo: safeLocalPath(query.get('returnTo')),
  };
}

function parseOrigin(value: string | null): V2Route['origin'] {
  return value === 'workbench' || value === 'case' || value === 'search' ? value : 'direct';
}

export function safeLocalPath(value: string | null | undefined): string {
  return value?.startsWith('/v2/') ? value : '';
}

export function appendNavigationContext(
  path: string,
  context: { origin: V2Route['origin']; queue?: string; returnTo?: string },
): string {
  const [pathname, queryString = ''] = path.split('?');
  const query = new URLSearchParams(queryString);
  query.set('origin', context.origin);
  if (context.queue) query.set('queue', context.queue);
  if (context.returnTo) query.set('returnTo', safeLocalPath(context.returnTo));
  return `${pathname}?${query.toString()}`;
}

export function workspaceBackTarget(route: Pick<V2Route, 'origin' | 'returnTo'>, caseId: string) {
  if (route.returnTo) return route.returnTo;
  if (route.origin === 'workbench') return '/v2/workbench';
  return `/v2/cases/${encodeURIComponent(caseId)}`;
}

export function workspaceBackLabel(origin: V2Route['origin']) {
  return origin === 'workbench' ? '返回工作台' : '返回病例';
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
  if (!user) return [];
  const isAdministrator = user.permissions.includes('P14-PERM-001');
  if (!isAdministrator) return [];
  return adminNavigation.filter(
    (item) =>
      !item.permissions?.length ||
      item.permissions.some((permission) => user.permissions.includes(permission)),
  );
}
