<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';

import { businessTypeName, formatDateTime, friendlyError } from '../uiText';
import {
  operationsRequest,
  type QcEvaluation,
  type QcRule,
  type StatisticsSummary,
} from '../v2OperationsApi';

const rules = ref<QcRule[]>([]);
const evaluations = ref<QcEvaluation[]>([]);
const statistics = ref<StatisticsSummary | null>(null);
const evaluationCaseId = ref('');
const loading = ref(false);
const submitting = ref(false);
const error = ref('');
const notice = ref('');

const metricNames: Record<string, string> = {
  ROUTINE_TAT: '常规报告时效',
  FROZEN_TAT: '冰冻诊断时效',
  REPORT_WITHDRAW_RATE: '报告撤回率',
  SLIDE_REPRINT_RATE: '玻片补打率',
};
const countLabels: Record<string, string> = {
  REGISTRATION: '登记病例',
  registrationCount: '登记病例',
  specimenCount: '标本',
  GROSSING: '取材记录',
  grossingCount: '取材记录',
  BLOCK: '蜡块',
  blockCount: '蜡块',
  SLIDE: '玻片',
  slideCount: '玻片',
  DIAGNOSIS_INITIAL: '初诊',
  diagnosisInitialCount: '初诊',
  DIAGNOSIS_REVIEW: '复诊',
  diagnosisReviewCount: '复诊',
  DIAGNOSIS_AUDIT: '审核',
  diagnosisAuditCount: '审核',
  REPORT_SIGN_OUT: '报告签发',
  reportSignOutCount: '报告签发',
  FROZEN: '冰冻病例',
  frozenCount: '冰冻病例',
  TECHNICAL_ORDER: '技术医嘱',
  technicalOrderCount: '技术医嘱',
};
const builtInCounts = computed(() =>
  Object.entries(statistics.value?.counts ?? {}).map(([code, count]) => ({
    code,
    label: countLabels[code] ?? code,
    count,
  })),
);

async function load() {
  loading.value = true;
  error.value = '';
  try {
    [rules.value, evaluations.value, statistics.value] = await Promise.all([
      operationsRequest<QcRule[]>('/qc/rules'),
      operationsRequest<QcEvaluation[]>('/qc/evaluations'),
      operationsRequest<StatisticsSummary>('/statistics/summary'),
    ]);
  } catch (requestError) {
    error.value = friendlyError(requestError, '质控与统计暂时无法加载，请稍后重试。');
  } finally {
    loading.value = false;
  }
}

function evaluate() {
  submitting.value = true;
  error.value = '';
  notice.value = '';
  void operationsRequest<QcEvaluation[]>('/qc/evaluate', {
    method: 'POST',
    body: JSON.stringify({ caseId: evaluationCaseId.value.trim() || null }),
  })
    .then((result) => {
      evaluations.value = result;
      notice.value = '质控评估已更新；提醒用于发现问题，默认不阻塞报告签发。';
    })
    .catch((requestError: unknown) => {
      error.value = friendlyError(requestError, '质控评估未完成，请重试。');
    })
    .finally(() => {
      submitting.value = false;
    });
}

function evaluationFor(rule: QcRule) {
  return evaluations.value.find((item) => item.ruleCode === rule.ruleCode);
}

function evaluationStatus(evaluation?: QcEvaluation) {
  if (!evaluation) return { label: '待评估', tone: '' };
  if (evaluation.statusCode === 'NORMAL') return { label: '正常', tone: 'success' };
  if (evaluation.statusCode === 'WARNING') return { label: '提醒', tone: 'warning' };
  return { label: '异常', tone: 'warning' };
}

onMounted(() => void load());
</script>

<template>
  <section class="quality-page" aria-label="质控统计工作台">
    <header class="page-heading compact-heading">
      <div>
        <p class="section-kicker">事实评估</p>
        <h2>质控与统计</h2>
        <p>质控评价已经发生的事实；提醒默认不控制业务。</p>
      </div>
      <div class="heading-actions">
        <label>评估指定病例（可选）<input v-model="evaluationCaseId" /></label
        ><button class="primary-button" type="button" :disabled="submitting" @click="evaluate">
          {{ submitting ? '评估中…' : '运行评估' }}
        </button>
      </div>
    </header>
    <p v-if="error" class="feedback error" role="alert">{{ error }}</p>
    <p v-if="notice" class="feedback success" role="status">{{ notice }}</p>
    <div v-if="loading" class="list-skeleton"><span></span><span></span><span></span></div>
    <template v-else>
      <section class="quality-rule-grid" aria-label="质控规则">
        <article
          v-for="rule in rules"
          :key="rule.ruleCode"
          class="workspace-panel quality-rule-card"
        >
          <header>
            <span
              ><p class="section-kicker">{{ metricNames[rule.metricCode] ?? rule.ruleName }}</p>
              <h3>{{ rule.ruleName }}</h3></span
            ><span class="status-pill" :class="evaluationStatus(evaluationFor(rule)).tone">{{
              evaluationStatus(evaluationFor(rule)).label
            }}</span>
          </header>
          <strong class="quality-value">{{ evaluationFor(rule)?.value ?? '—' }}</strong>
          <small>提醒阈值 {{ rule.warningThreshold }} · 超时阈值 {{ rule.overdueThreshold }}</small>
          <small v-if="evaluationFor(rule)"
            >最近评估 {{ formatDateTime(evaluationFor(rule)?.evaluatedAt) }}</small
          >
        </article>
      </section>

      <div class="quality-statistics-grid">
        <section class="workspace-panel">
          <header class="panel-title-row">
            <div>
              <p class="section-kicker">内置统计</p>
              <h3>核心业务量</h3>
            </div>
            <span class="status-pill">当前数据范围</span>
          </header>
          <div class="statistics-count-grid">
            <span v-for="item in builtInCounts" :key="item.code"
              ><strong>{{ item.count }}</strong
              ><small>{{ item.label }}</small></span
            >
          </div>
        </section>
        <section class="workspace-panel">
          <header class="panel-title-row">
            <div>
              <p class="section-kicker">业务分布</p>
              <h3>业务类型</h3>
            </div>
          </header>
          <div class="business-distribution-list">
            <span
              v-for="item in statistics?.businessTypeDistribution ?? []"
              :key="item.businessTypeCode"
              ><strong>{{ businessTypeName(item.businessTypeCode) }}</strong
              ><b>{{ item.count }}</b></span
            >
          </div>
        </section>
        <section class="workspace-panel">
          <header class="panel-title-row">
            <div>
              <p class="section-kicker">报表扩展</p>
              <h3>内置与外挂报表</h3>
            </div>
          </header>
          <div class="attention-list">
            <button type="button">
              <span class="semantic-dot success"></span
              ><span><strong>工作量统计</strong><small>按初诊、复诊、审核责任分别统计</small></span
              ><b>打开</b></button
            ><button type="button">
              <span class="semantic-dot neutral"></span
              ><span><strong>自定义报表</strong><small>通过稳定数据源注册扩展</small></span
              ><b>管理</b>
            </button>
          </div>
        </section>
      </div>
    </template>
  </section>
</template>
