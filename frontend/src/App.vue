<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';

import type { V2AuthUser } from './auth';
import { departmentName, roleName } from './auth';
import V2DiagnosisWorkspace from './components/V2DiagnosisWorkspace.vue';
import V2GlobalSearch from './components/V2GlobalSearch.vue';
import V2FrozenWorkspace from './components/V2FrozenWorkspace.vue';
import V2CaseContext from './components/V2CaseContext.vue';
import V2ConfigurationHub from './components/V2ConfigurationHub.vue';
import V2GrossingWorkbench from './components/V2GrossingWorkbench.vue';
import V2Home from './components/V2Home.vue';
import V2Login from './components/V2Login.vue';
import V2DigitalSlideWorkbench from './components/V2DigitalSlideWorkbench.vue';
import V2MaterialCustodyWorkbench from './components/V2MaterialCustodyWorkbench.vue';
import V2QualityWorkbench from './components/V2QualityWorkbench.vue';
import V2RegistrationWorkbench from './components/V2RegistrationWorkbench.vue';
import V2ReportCenter from './components/V2ReportCenter.vue';
import V2SlideProductionWorkbench from './components/V2SlideProductionWorkbench.vue';
import V2SystemAdminHub from './components/V2SystemAdminHub.vue';
import V2TechnicalWorkbench from './components/V2TechnicalWorkbench.vue';
import {
  navigationForUser,
  parseV2Route,
  primaryNavigation,
  routePath,
  type V2Route,
  type V2RouteName,
} from './navigation';
import { friendlyError } from './uiText';

const authLoading = ref(true);
const authRequired = ref(false);
const authUser = ref<V2AuthUser | null>(null);
const authError = ref('');
const route = ref<V2Route>(parseV2Route(window.location));
const globalSearchOpen = ref(false);
const sidebarOpen = ref(false);
const tableDensity = ref<'compact' | 'comfortable'>('compact');

const routeTitles: Record<V2RouteName, string> = {
  workbench: '工作台',
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
  system: '系统管理',
};

const navigation = computed(() => navigationForUser(authUser.value));
const currentNavigation = computed(() =>
  primaryNavigation.find((item) => item.name === route.value.name),
);
const pageTitle = computed(() => currentNavigation.value?.label ?? routeTitles[route.value.name]);
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
  }
}

async function logout() {
  await fetch('/api/v2/auth/logout', { method: 'POST' });
  window.location.replace('/v2/workbench');
}

function reloadAfterLogin() {
  window.location.replace('/v2/workbench');
}

function navigate(path: string) {
  window.history.pushState({}, '', path);
  route.value = parseV2Route(window.location);
  sidebarOpen.value = false;
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
  <div v-else-if="!authLoading" class="app-shell">
    <a class="skip-link" href="#workspace-main">跳到主要工作区</a>
    <aside class="app-sidebar" :class="{ open: sidebarOpen }" aria-label="PIS V2 主导航">
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
          <button
            class="menu-button"
            type="button"
            aria-label="打开导航"
            @click="sidebarOpen = !sidebarOpen"
          >
            ☰
          </button>
          <div>
            <p>{{ departmentName(authUser) }}</p>
            <h1>{{ pageTitle }}</h1>
          </div>
        </div>
        <div class="topbar-actions">
          <button class="search-trigger" type="button" @click="globalSearchOpen = true">
            <span>搜索病理号、患者或材料</span><kbd>Ctrl K</kbd>
          </button>
          <label class="density-switch">
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
            <button type="button" @click="logout">退出</button>
          </div>
        </div>
      </header>

      <main id="workspace-main" class="workspace-main" tabindex="-1">
        <V2Home
          v-if="route.name === 'workbench' || route.name === 'search'"
          :auth-user="authUser"
          @navigate="navigate"
          @open-search="globalSearchOpen = true"
        />
        <V2CaseContext
          v-else-if="route.name === 'case'"
          :case-id="route.caseId"
          :auth-user="authUser"
          :focus-kind="route.focusKind"
          :focus-id="route.focusId"
          @navigate="navigate"
        />
        <V2RegistrationWorkbench
          v-else-if="route.name === 'registration'"
          :auth-user="authUser"
          @navigate="navigate"
        />
        <V2GrossingWorkbench
          v-else-if="route.name === 'grossing'"
          v-model:case-id="routeCaseId"
          :auth-user="authUser"
          :source-type="route.roundId ? 'FROZEN_CONTEXT' : 'INITIAL'"
          :source-reference-id="route.roundId || undefined"
          @navigate="navigate"
        />
        <V2SlideProductionWorkbench
          v-else-if="route.name === 'production'"
          v-model:case-id="routeCaseId"
          :auth-user="authUser"
          :frozen-round-id="route.roundId || undefined"
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
          @navigate="navigate"
        />
        <V2TechnicalWorkbench
          v-else-if="route.name === 'technical-orders'"
          v-model:case-id="routeCaseId"
          :focus-kind="route.focusKind"
          :focus-id="route.focusId"
          @navigate="navigate"
        />
        <V2FrozenWorkspace
          v-else-if="route.name === 'frozen'"
          :case-id="route.caseId"
          :auth-user="authUser"
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
        <V2SystemAdminHub v-else-if="route.name === 'system'" />
        <V2Home
          v-else
          :auth-user="authUser"
          @navigate="navigate"
          @open-search="globalSearchOpen = true"
        />
      </main>
    </div>

    <button
      v-if="sidebarOpen"
      class="sidebar-scrim"
      type="button"
      aria-label="关闭导航"
      @click="sidebarOpen = false"
    ></button>
    <V2GlobalSearch
      :open="globalSearchOpen"
      @close="globalSearchOpen = false"
      @navigate="navigate"
    />
  </div>
  <main v-else class="auth-loading" aria-label="正在加载系统">
    <span class="loading-spinner" aria-hidden="true"></span>
    <p>正在进入工作台…</p>
  </main>
  <p v-if="authError" class="feedback error auth-error" role="alert">{{ authError }}</p>
</template>
