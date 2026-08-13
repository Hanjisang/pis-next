<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';

import type { V2AuthUser } from './auth';
import { changeOwnPassword, departmentName, roleName } from './auth';
import V2DiagnosisWorkspace from './components/V2DiagnosisWorkspace.vue';
import V2GlobalSearch from './components/V2GlobalSearch.vue';
import V2FrozenWorkspace from './components/V2FrozenWorkspace.vue';
import V2CaseContext from './components/V2CaseContext.vue';
import V2ConfigurationHub from './components/V2ConfigurationHub.vue';
import V2BusinessOperationsHub from './components/V2BusinessOperationsHub.vue';
import V2GrossingWorkbench from './components/V2GrossingWorkbench.vue';
import V2Home from './components/V2Home.vue';
import V2Login from './components/V2Login.vue';
import V2DigitalSlideWorkbench from './components/V2DigitalSlideWorkbench.vue';
import V2MaterialCustodyWorkbench from './components/V2MaterialCustodyWorkbench.vue';
import V2QualityWorkbench from './components/V2QualityWorkbench.vue';
import V2RegistrationWorkbench from './components/V2RegistrationWorkbench.vue';
import V2ReportCenter from './components/V2ReportCenter.vue';
import V2SlideProductionWorkbench from './components/V2SlideProductionWorkbench.vue';
import V2RoutineProductionWorkspace from './components/V2RoutineProductionWorkspace.vue';
import V2CytologyProductionWorkspace from './components/V2CytologyProductionWorkspace.vue';
import V2SystemAdminHub from './components/V2SystemAdminHub.vue';
import V2TechnicalWorkbench from './components/V2TechnicalWorkbench.vue';
import {
  adminNavigation,
  navigationForUser,
  parseV2Route,
  routePath,
  type V2Route,
  type V2RouteName,
} from './navigation';
import { friendlyError } from './uiText';
import {
  getOperationsNotifications,
  readOperationsNotification,
  type OperationsNotification,
} from './v2BusinessOperationsApi';

const authLoading = ref(true);
const authRequired = ref(false);
const authUser = ref<V2AuthUser | null>(null);
const authError = ref('');
const route = ref<V2Route>(parseV2Route(window.location));
const globalSearchOpen = ref(false);
const tableDensity = ref<'compact' | 'comfortable'>('compact');
const unreadNotificationCount = ref(0);
const notificationsOpen = ref(false);
const notifications = ref<OperationsNotification[]>([]);
const passwordOpen = ref(false);
const passwordSaving = ref(false);
const passwordError = ref('');
const passwordNotice = ref('');
const passwordDraft = ref({ currentPassword: '', newPassword: '', confirmation: '' });

const routeTitles: Record<V2RouteName, string> = {
  workbench: '我的工作',
  case: '病例中心',
  registration: '登记',
  grossing: '取材',
  production: '制片',
  diagnosis: '诊断',
  frozen: '冰冻',
  'technical-orders': '技术医嘱',
  reports: '报告',
  'digital-slides': '数字切片',
  'material-custody': '归档借阅',
  search: '查询',
  quality: '质控统计',
  configuration: '配置',
  'business-operations': '科室运行',
  system: '系统管理',
};

const navigation = computed(() => navigationForUser(authUser.value));
const isAdmin = computed(() => navigation.value.length > 0);
const currentNavigation = computed(() =>
  adminNavigation.find((item) => item.name === route.value.name),
);
const pageTitle = computed(() => currentNavigation.value?.label ?? routeTitles[route.value.name]);
const focusedRouteNames = new Set<V2RouteName>([
  'diagnosis',
  'production',
  'frozen',
  'technical-orders',
  'grossing',
]);
const isFocusedWorkspace = computed(
  () => focusedRouteNames.has(route.value.name) && Boolean(route.value.caseId),
);
const searchReturnPath = computed(() => {
  const query = new URLSearchParams(window.location.search);
  query.set('search', 'open');
  return `${window.location.pathname}?${query.toString()}`;
});
const routeCaseId = computed({
  get: () => route.value.caseId,
  set: (caseId: string) => {
    route.value = { ...route.value, caseId };
  },
});

watch(
  tableDensity,
  (density) => {
    document.documentElement.dataset.tableDensity = density;
  },
  { immediate: true },
);

async function loadAuthentication() {
  authLoading.value = true;
  authError.value = '';
  try {
    const configResponse = await fetch('/api/v2/auth/config');
    const config = (await configResponse.json()) as { required?: boolean };
    authRequired.value = Boolean(config.required);
    if (!authRequired.value) return;
    const response = await fetch('/api/v2/auth/me');
    if (response.ok) authUser.value = (await response.json()) as V2AuthUser;
  } catch (requestError) {
    authError.value = friendlyError(requestError, '认证服务暂时不可用，请稍后刷新页面。');
  } finally {
    authLoading.value = false;
    try {
      notifications.value = await getOperationsNotifications();
      unreadNotificationCount.value = notifications.value.filter((item) => !item.readAt).length;
    } catch {
      unreadNotificationCount.value = 0;
    }
  }
}

async function markNotificationRead(item: OperationsNotification) {
  await readOperationsNotification(item.id);
  item.readAt = new Date().toISOString();
  unreadNotificationCount.value = notifications.value.filter((entry) => !entry.readAt).length;
}

async function logout() {
  await fetch('/api/v2/auth/logout', { method: 'POST' });
  window.location.replace('/v2/workbench');
}

async function submitOwnPassword() {
  passwordError.value = '';
  passwordNotice.value = '';
  if (passwordDraft.value.newPassword !== passwordDraft.value.confirmation) {
    passwordError.value = '两次输入的新密码不一致。';
    return;
  }
  passwordSaving.value = true;
  try {
    await changeOwnPassword(passwordDraft.value.currentPassword, passwordDraft.value.newPassword);
    passwordDraft.value = { currentPassword: '', newPassword: '', confirmation: '' };
    passwordNotice.value = '密码已修改，其他已登录会话已失效。';
  } catch (requestError) {
    passwordError.value = friendlyError(requestError, '密码修改失败。');
  } finally {
    passwordSaving.value = false;
  }
}

function reloadAfterLogin() {
  window.location.replace('/v2/workbench');
}

function navigate(path: string) {
  window.history.pushState({}, '', path);
  route.value = parseV2Route(window.location);
  globalSearchOpen.value = new URLSearchParams(window.location.search).get('search') === 'open';
  window.scrollTo({ top: 0, behavior: 'auto' });
}

function navigateByName(name: V2RouteName) {
  if (name === 'search') {
    globalSearchOpen.value = true;
    return;
  }
  navigate(routePath(name));
}

function handlePopState() {
  route.value = parseV2Route(window.location);
}

function handleGlobalShortcut(event: KeyboardEvent) {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault();
    globalSearchOpen.value = true;
  }
}

onMounted(() => {
  if (!window.location.pathname.startsWith('/v2/')) {
    window.history.replaceState({}, '', '/v2/workbench');
    route.value = parseV2Route(window.location);
  }
  window.addEventListener('popstate', handlePopState);
  window.addEventListener('keydown', handleGlobalShortcut);
  void loadAuthentication();
});

onUnmounted(() => {
  window.removeEventListener('popstate', handlePopState);
  window.removeEventListener('keydown', handleGlobalShortcut);
});
</script>

<template>
  <V2Login v-if="!authLoading && authRequired && !authUser" @authenticated="reloadAfterLogin" />
  <div v-else-if="!authLoading" class="app-shell" :class="{ 'admin-shell': isAdmin }">
    <a class="skip-link" href="#workspace-main">跳到主要工作区</a>
    <aside v-if="isAdmin" class="app-sidebar" aria-label="PIS V2 管理导航">
      <div class="brand-block">
        <span class="brand-mark" aria-hidden="true">P</span>
        <span><strong>PIS Next</strong><small>病理信息系统</small></span>
      </div>
      <nav class="primary-navigation" aria-label="一级导航">
        <button
          v-for="item in navigation"
          :key="item.name"
          type="button"
          :class="{ active: route.name === item.name }"
          :aria-current="route.name === item.name ? 'page' : undefined"
          @click="navigateByName(item.name)"
        >
          <span class="nav-marker" aria-hidden="true"></span>
          <span>{{ item.label }}</span>
        </button>
      </nav>
      <div class="sidebar-footer">
        <span class="environment-dot" aria-hidden="true"></span>
        <span><strong>V2 正式业务入口</strong><small>核心业务服务正常</small></span>
      </div>
    </aside>

    <div class="app-body">
      <header class="app-topbar">
        <div class="topbar-title">
          <span class="topbar-wordmark" aria-label="PIS">PIS</span>
          <span class="topbar-page-label">{{ pageTitle }}</span>
          <span class="topbar-department">{{ departmentName(authUser) }}</span>
        </div>
        <div class="topbar-actions">
          <button class="search-trigger" type="button" @click="globalSearchOpen = true">
            <span>搜索病理号 / 姓名 / 住院号 / 玻片号</span><kbd>Ctrl K</kbd>
          </button>
          <button
            class="notification-trigger"
            type="button"
            aria-label="打开通知中心"
            @click="notificationsOpen = true"
          >
            通知<span v-if="unreadNotificationCount" class="notification-count">{{
              unreadNotificationCount
            }}</span>
          </button>
          <label v-if="!isFocusedWorkspace" class="density-switch">
            <span>列表密度</span>
            <select v-model="tableDensity" aria-label="列表密度">
              <option value="compact">紧凑</option>
              <option value="comfortable">舒适</option>
            </select>
          </label>
          <div v-if="authUser" class="identity-menu" aria-label="当前登录身份">
            <span class="identity-avatar" aria-hidden="true">{{
              authUser.displayName.slice(0, 1)
            }}</span>
            <span>
              <strong>{{ authUser.displayName }}</strong>
              <small>{{ roleName(authUser.roleCode) }}</small>
            </span>
            <button
              v-if="authUser.permissions.includes('P14-PERM-001')"
              type="button"
              @click="navigate('/v2/system')"
            >
              管理后台
            </button>
            <button type="button" @click="passwordOpen = true">修改密码</button>
            <button type="button" @click="logout">退出</button>
          </div>
        </div>
      </header>

      <main
        id="workspace-main"
        class="workspace-main"
        :class="{
          'focused-workspace-main': isFocusedWorkspace,
          'diagnosis-main': route.name === 'diagnosis' && Boolean(route.caseId),
        }"
        tabindex="-1"
      >
        <V2Home
          v-if="route.name === 'workbench' || route.name === 'search'"
          :auth-user="authUser"
          @navigate="navigate"
          @open-search="globalSearchOpen = true"
        />
        <V2CaseContext
          v-else-if="route.name === 'case'"
          :case-id="route.caseId"
          :round-id="route.roundId"
          :auth-user="authUser"
          :focus-kind="route.focusKind"
          :focus-id="route.focusId"
          :origin="route.origin"
          :queue="route.queue"
          :return-to="route.returnTo"
          @navigate="navigate"
        />
        <V2RegistrationWorkbench
          v-else-if="route.name === 'registration'"
          :auth-user="authUser"
          :origin="route.origin"
          :queue="route.queue"
          :return-to="route.returnTo"
          @navigate="navigate"
        />
        <V2GrossingWorkbench
          v-else-if="route.name === 'grossing'"
          v-model:case-id="routeCaseId"
          :auth-user="authUser"
          :source-type="route.roundId ? 'FROZEN_CONTEXT' : route.sourceType || 'INITIAL'"
          :source-reference-id="route.roundId || route.sourceReferenceId || undefined"
          :origin="route.origin"
          :queue="route.queue"
          :return-to="route.returnTo"
          @navigate="navigate"
        />
        <V2RoutineProductionWorkspace
          v-else-if="
            route.name === 'production' && route.queue === 'ROUTINE_PRODUCTION' && route.caseId
          "
          v-model:case-id="routeCaseId"
          :auth-user="authUser"
          :origin="route.origin"
          :queue="route.queue"
          :return-to="route.returnTo"
          @navigate="navigate"
        />
        <V2CytologyProductionWorkspace
          v-else-if="
            route.name === 'production' && route.queue === 'CYTOLOGY_PRODUCTION' && route.caseId
          "
          v-model:case-id="routeCaseId"
          :auth-user="authUser"
          :origin="route.origin"
          :queue="route.queue"
          :return-to="route.returnTo"
          @navigate="navigate"
        />
        <V2SlideProductionWorkbench
          v-else-if="route.name === 'production'"
          v-model:case-id="routeCaseId"
          :auth-user="authUser"
          :frozen-round-id="route.roundId || undefined"
          :origin="route.origin"
          :queue="route.queue"
          :return-to="route.returnTo"
          @navigate="navigate"
        />
        <V2ReportCenter
          v-else-if="route.name === 'reports' && !route.caseId"
          @navigate="navigate"
        />
        <V2DiagnosisWorkspace
          v-else-if="route.name === 'diagnosis' || (route.name === 'reports' && route.caseId)"
          v-model:case-id="routeCaseId"
          :auth-user="authUser"
          :frozen-round-id="route.roundId || undefined"
          :focus-kind="route.focusKind"
          :focus-id="route.focusId"
          :origin="route.origin"
          :queue="route.queue"
          :return-to="route.returnTo"
          @navigate="navigate"
        />
        <V2TechnicalWorkbench
          v-else-if="route.name === 'technical-orders'"
          v-model:case-id="routeCaseId"
          :focus-kind="route.focusKind"
          :focus-id="route.focusId"
          :origin="route.origin"
          :queue="route.queue"
          :return-to="route.returnTo"
          @navigate="navigate"
        />
        <V2FrozenWorkspace
          v-else-if="route.name === 'frozen'"
          :case-id="route.caseId"
          :round-id="route.roundId"
          :auth-user="authUser"
          :origin="route.origin"
          :queue="route.queue"
          :return-to="route.returnTo"
          @navigate="navigate"
        />
        <V2DigitalSlideWorkbench
          v-else-if="route.name === 'digital-slides'"
          :case-id="route.caseId"
          :selected-slide-id="route.slideId"
          @navigate="navigate"
        />
        <V2MaterialCustodyWorkbench
          v-else-if="route.name === 'material-custody'"
          :case-id="route.caseId"
        />
        <V2QualityWorkbench v-else-if="route.name === 'quality'" />
        <V2ConfigurationHub v-else-if="route.name === 'configuration'" />
        <V2BusinessOperationsHub
          v-else-if="
            route.name === 'business-operations' && authUser?.permissions.includes('P14-PERM-001')
          "
        />
        <V2SystemAdminHub
          v-else-if="route.name === 'system' && authUser?.permissions.includes('P14-PERM-001')"
        />
        <V2Home
          v-else
          :auth-user="authUser"
          @navigate="navigate"
          @open-search="globalSearchOpen = true"
        />
      </main>
    </div>

    <V2GlobalSearch
      :open="globalSearchOpen"
      :return-path="searchReturnPath"
      @close="globalSearchOpen = false"
      @navigate="navigate"
    />
    <div v-if="passwordOpen" class="modal-backdrop" @click.self="passwordOpen = false">
      <form
        class="modal-card password-dialog"
        aria-label="修改密码"
        @submit.prevent="submitOwnPassword"
      >
        <header class="panel-title-row">
          <div>
            <p class="section-kicker">账户安全</p>
            <h3>修改密码</h3>
          </div>
          <button class="text-button" type="button" @click="passwordOpen = false">关闭</button>
        </header>
        <p v-if="passwordError" class="feedback error" role="alert">{{ passwordError }}</p>
        <p v-if="passwordNotice" class="feedback success" role="status">{{ passwordNotice }}</p>
        <label
          >当前密码<input
            v-model="passwordDraft.currentPassword"
            type="password"
            required
            autocomplete="current-password"
        /></label>
        <label
          >新密码<input
            v-model="passwordDraft.newPassword"
            type="password"
            required
            minlength="8"
            autocomplete="new-password"
        /></label>
        <label
          >确认新密码<input
            v-model="passwordDraft.confirmation"
            type="password"
            required
            minlength="8"
            autocomplete="new-password"
        /></label>
        <button class="primary-button" type="submit" :disabled="passwordSaving">
          {{ passwordSaving ? '保存中…' : '保存新密码' }}
        </button>
      </form>
    </div>
    <div v-if="notificationsOpen" class="modal-backdrop" @click.self="notificationsOpen = false">
      <section class="modal-card notification-dialog" aria-label="通知中心">
        <header class="panel-title-row">
          <div>
            <p class="section-kicker">业务消息</p>
            <h3>通知中心</h3>
          </div>
          <button class="text-button" type="button" @click="notificationsOpen = false">关闭</button>
        </header>
        <div v-if="!notifications.length" class="empty-state">
          <strong>暂无通知</strong><span>新任务和技术结果会显示在这里。</span>
        </div>
        <div
          v-for="item in notifications"
          :key="item.id"
          class="operations-row"
          :class="{ unread: !item.readAt }"
        >
          <div>
            <strong>{{ item.title }}</strong>
            <p>{{ item.body }}</p>
          </div>
          <button
            v-if="!item.readAt"
            class="text-button"
            type="button"
            @click="markNotificationRead(item)"
          >
            标记已读</button
          ><span v-else class="status-pill success">已读</span>
        </div>
      </section>
    </div>
  </div>
  <main v-else class="auth-loading" aria-label="正在加载系统">
    <span class="loading-spinner" aria-hidden="true"></span>
    <p>正在进入工作台…</p>
  </main>
  <p v-if="authError" class="feedback error auth-error" role="alert">{{ authError }}</p>
</template>
