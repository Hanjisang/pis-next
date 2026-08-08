<script setup lang="ts">
import { ref } from 'vue';

import V2Home from './components/V2Home.vue';
import V2DiagnosisWorkspace from './components/V2DiagnosisWorkspace.vue';
import V2MaterialProductionWorkbench from './components/V2MaterialProductionWorkbench.vue';
import V2OperationsWorkbench from './components/V2OperationsWorkbench.vue';
import V2RegistrationWorkbench from './components/V2RegistrationWorkbench.vue';
import V2TechnicalWorkbench from './components/V2TechnicalWorkbench.vue';

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
  <main class="shell">
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
    <V2DiagnosisWorkspace v-if="showDiagnosis" v-model:case-id="v2CaseId" />
    <V2TechnicalWorkbench v-if="showTechnical" />
    <V2OperationsWorkbench v-if="operationsMode" :mode="operationsMode" :case-id="v2CaseId" />

    <footer>
      <span>PIS V2 · 病例、材料、诊断、报告全程追溯</span
      ><a href="?workspace=v2-home">返回 V2 工作台</a>
    </footer>
  </main>
</template>
