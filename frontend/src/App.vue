<script setup lang="ts">
import P15RegistrationWorkbench from './components/P15RegistrationWorkbench.vue';
import P16GrossingWorkbench from './components/P16GrossingWorkbench.vue';
import P17TechnicalProcessingWorkbench from './components/P17TechnicalProcessingWorkbench.vue';
import P18TechnicalOrderWorkbench from './components/P18TechnicalOrderWorkbench.vue';
import P19DiagnosisReportWorkbench from './components/P19DiagnosisReportWorkbench.vue';
import V2DiagnosisWorkspace from './components/V2DiagnosisWorkspace.vue';
import V2MaterialProductionWorkbench from './components/V2MaterialProductionWorkbench.vue';

const workspaceQuery = new URLSearchParams(window.location.search);
const showV2Material = workspaceQuery.get('workspace') === 'v2';
const showV2Diagnosis = workspaceQuery.get('workspace') === 'v2-diagnosis';
const showLegacyWorkbenches = !showV2Material && !showV2Diagnosis;
let v2CaseId = workspaceQuery.get('caseId') ?? '';
</script>

<template>
  <main class="shell">
    <section class="hero">
      <div>
        <p class="eyebrow">PATHOLOGY INFORMATION SYSTEM</p>
        <h1>PIS Next</h1>
        <p class="lede">
          {{
            showV2Diagnosis
              ? 'V2 Diagnosis 连续诊断与责任工作区。'
              : 'P15 登记与标本接收纵向工作台。'
          }}
        </p>
      </div>
      <div class="phase-badge" aria-label="当前阶段">
        {{ showV2Diagnosis ? 'V2 · DIAGNOSIS WORKSPACE' : 'P19 · DIAGNOSIS &amp; REPORT' }}
      </div>
    </section>

    <section class="summary-grid" aria-label="P15 摘要">
      <article class="summary-card accent">
        <span>当前切片</span>
        <strong>登记 → 接收</strong>
        <small>申请、病例、预计标本和扫码核对</small>
      </article>
      <article class="summary-card">
        <span>追溯</span>
        <strong>追加事实</strong>
        <small>状态历史、交接、审计和发件箱同事务</small>
      </article>
      <article class="summary-card">
        <span>数据</span>
        <strong>合成数据</strong>
        <small>不显示真实患者正文或诊断报告</small>
      </article>
    </section>

    <template v-if="showLegacyWorkbenches">
      <P15RegistrationWorkbench />
      <P16GrossingWorkbench />
      <P17TechnicalProcessingWorkbench />
      <P18TechnicalOrderWorkbench />
      <P19DiagnosisReportWorkbench />
    </template>
    <V2MaterialProductionWorkbench v-if="showV2Material" />
    <V2DiagnosisWorkspace v-if="showV2Diagnosis" v-model:case-id="v2CaseId" />

    <footer>
      <span>PIS Next · Clean-room design</span>
      <span>{{
        showV2Diagnosis
          ? 'V2-I03 · Diagnosis 与责任链'
          : 'P19 已实现 · 报告签发、修订和撤回保持版本链'
      }}</span>
    </footer>
  </main>
</template>
