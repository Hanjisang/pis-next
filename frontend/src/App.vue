<script setup lang="ts">
import { onMounted, ref } from 'vue';

import V2Home from './components/V2Home.vue';
import V2DiagnosisWorkspace from './components/V2DiagnosisWorkspace.vue';
import V2MaterialProductionWorkbench from './components/V2MaterialProductionWorkbench.vue';
import V2OperationsWorkbench from './components/V2OperationsWorkbench.vue';
import V2RegistrationWorkbench from './components/V2RegistrationWorkbench.vue';
import V2TechnicalWorkbench from './components/V2TechnicalWorkbench.vue';
import V2Login from './components/V2Login.vue';

type AuthUser = {
  displayName: string;
  username: string;
  roleCode: string;
  doctor?: { doctorCode: string; displayName: string } | null;
};

const authLoading = ref(false);
const authRequired = ref(false);
const authUser = ref<AuthUser | null>(null);
const authError = ref('');

async function loadAuthentication() {
  try {
    const configResponse = await fetch('/api/v2/auth/config');
    const config = (await configResponse.json()) as { required?: boolean };
    authRequired.value = Boolean(config.required);
    if (!authRequired.value) return;
    const response = await fetch('/api/v2/auth/me');
    if (response.ok) authUser.value = (await response.json()) as AuthUser;
  } catch (requestError) {
    authError.value = requestError instanceof Error ? requestError.message : '认证服务不可用';
  } finally {
    authLoading.value = false;
  }
}

async function logout() {
  await fetch('/api/v2/auth/logout', { method: 'POST' });
  window.location.reload();
}

function reloadAfterLogin() {
  window.location.reload();
}

onMounted(() => void loadAuthentication());

const workspaceQuery = new URLSearchParams(window.location.search);
const workspace = workspaceQuery.get('workspace') ?? 'v2-home';
const showHome = workspace === 'v2-home';
const showRegistration = workspace === 'v2-registration';
const showMaterial = workspace === 'v2';
const showDiagnosis = workspace === 'v2-diagnosis';
const showTechnical = workspace === 'v2-technical';
const operationsMode = ['frozen', 'digital', 'custody', 'quality'].includes(workspace)
  ? (workspace as 'frozen' | 'digital' | 'custody' | 'quality')
  : null;
const v2CaseId = ref(workspaceQuery.get('caseId') ?? '');
const v2RoundId = workspaceQuery.get('roundId') ?? '';

const title = showRegistration
  ? '登记与标本'
  : showMaterial
    ? '取材与制片'
    : showDiagnosis
      ? '诊断与报告'
      : showTechnical
        ? '技术医嘱'
        : '工作台';
</script>

<template>
  <V2Login v-if="!authLoading && authRequired && !authUser" @authenticated="reloadAfterLogin" />
  <main v-else-if="!authLoading" class="shell">
    <section v-if="authUser" class="auth-bar" aria-label="当前登录身份">
      <span>{{ authUser.displayName }} · {{ authUser.roleCode }}</span>
      <span v-if="authUser.doctor"
        >医疗人员：{{ authUser.doctor.displayName }}（{{ authUser.doctor.doctorCode }}）</span
      >
      <button type="button" @click="logout">退出登录</button>
    </section>
    <section class="hero">
      <div>
        <p class="eyebrow">PATHOLOGY INFORMATION SYSTEM · V2</p>
        <h1>PIS Next</h1>
        <p class="lede">{{ title }} · 面向业务角色的统一入口</p>
      </div>
      <div class="phase-badge" aria-label="当前版本">V2 · 正式业务入口</div>
    </section>

    <V2Home v-if="showHome" />
    <V2RegistrationWorkbench v-if="showRegistration" />
    <V2MaterialProductionWorkbench
      v-if="showMaterial"
      v-model:case-id="v2CaseId"
      :source-type="v2RoundId ? 'FROZEN_CONTEXT' : 'INITIAL'"
      :source-reference-id="v2RoundId || undefined"
    />
    <V2DiagnosisWorkspace
      v-if="showDiagnosis"
      v-model:case-id="v2CaseId"
      :frozen-round-id="v2RoundId || undefined"
    />
    <V2TechnicalWorkbench v-if="showTechnical" />
    <V2OperationsWorkbench v-if="operationsMode" :mode="operationsMode" :case-id="v2CaseId" />

    <footer>
      <span>PIS V2 · 病例、材料、诊断、报告全程追溯</span
      ><a href="?workspace=v2-home">返回 V2 工作台</a>
    </footer>
  </main>
  <p v-else-if="authError" class="error-banner" role="alert">{{ authError }}</p>
</template>
