<script setup lang="ts">
import { computed, ref, watch } from 'vue';

import type { V2AuthUser } from '../auth';
import { businessTypeName, formatDateTime, friendlyError, statusName } from '../uiText';
import {
  getV2CaseProgress,
  getV2CaseWorkspace,
  type V2CaseProgress,
  type V2CaseWorkspace,
} from '../v2WorkspaceApi';
import V2DiagnosisWorkspace from './V2DiagnosisWorkspace.vue';
import V2FrozenWorkspace from './V2FrozenWorkspace.vue';
import V2GrossingWorkbench from './V2GrossingWorkbench.vue';
import V2SlideProductionWorkbench from './V2SlideProductionWorkbench.vue';
import V2TechnicalWorkbench from './V2TechnicalWorkbench.vue';

const props = defineProps<{
  caseId: string;
  roundId?: string;
  authUser?: V2AuthUser | null;
  focusKind?: string;
  focusId?: string;
}>();
const emit = defineEmits<{ navigate: [path: string] }>();

const workspace = ref<V2CaseWorkspace | null>(null);
const progress = ref<V2CaseProgress | null>(null);
const loading = ref(false);
const error = ref('');

const focusedKinds = new Set([
  'diagnosis',
  'production',
  'technical-order',
  'frozen',
  'grossing',
  'report',
]);
const isFocused = computed(() => focusedKinds.has(props.focusKind ?? ''));
const header = computed(() => workspace.value?.caseHeader);
const materialCounts = computed(() => {
  const specimens = workspace.value?.materialTree.specimens ?? [];
  const blocks = specimens.flatMap((item) => item.blocks);
  const slides = [
    ...specimens.flatMap((item) => item.directSlides),
    ...blocks.flatMap((item) => item.slides),
  ];
  return {
    specimens: specimens.length,
    blocks: blocks.length,
    slides: slides.length,
    completedSlides: slides.filter((slide) => slide.completed).length,
  };
});
const currentStage = computed(() => progress.value?.currentStageLabel ?? '待确认');
const diagnosisSummary = computed(() => {
  const items = workspace.value?.responsibilities ?? [];
  const current = items.find((item) => !item.completedAt && !item.endedAt);
  if (current) return `${roleLabel(current.roleCode)}进行中`;
  return items.length ? '诊断流程已有记录' : '尚未接诊';
});
const reportSummary = computed(() => {
  const reports = workspace.value?.reports ?? [];
  if (reports.some((report) => report.statusCode === 'EFFECTIVE')) return '已签发';
  if (reports.some((report) => report.statusCode === 'WITHDRAWN')) return '已撤回，待处理';
  return reports.length ? '报告处理中' : '尚未签发';
});
const timeline = computed(() => (workspace.value?.timeline ?? []).slice(0, 6));
const focusTitle = computed(
  () =>
    ({
      diagnosis: '诊断与阅片',
      report: '报告处理',
      production: '制片',
      'technical-order': '技术医嘱',
      frozen: '冰冻',
      grossing: '取材',
    })[props.focusKind ?? ''] ?? '当前工作',
);
const permissions = computed(() => new Set(props.authUser?.permissions ?? []));

function can(permission: string) {
  return permissions.value.has(permission);
}

function roleLabel(role: string) {
  return { INITIAL: '初诊', REVIEW: '复诊', AUDIT: '审核' }[role] ?? role;
}

function lifecycleLabel(lifecycle: string) {
  return lifecycle.includes('CANCEL') ? '已取消' : '进行中';
}

function openFocus(kind: string) {
  if (!props.caseId) return;
  const query = new URLSearchParams({ focus: kind });
  if (props.focusId) query.set('focusId', props.focusId);
  if (props.roundId) query.set('roundId', props.roundId);
  emit('navigate', `/v2/cases/${props.caseId}?${query.toString()}`);
}

function backToWorkbench() {
  emit('navigate', '/v2/workbench');
}

async function loadOverview() {
  if (!props.caseId || isFocused.value) return;
  loading.value = true;
  error.value = '';
  try {
    workspace.value = await getV2CaseWorkspace(props.caseId);
    try {
      progress.value = await getV2CaseProgress(props.caseId);
    } catch {
      progress.value = null;
    }
  } catch (requestError) {
    workspace.value = null;
    progress.value = null;
    error.value = friendlyError(requestError, '病例信息暂时无法加载，请刷新后重试。');
  } finally {
    loading.value = false;
  }
}

watch(
  () => [props.caseId, props.focusKind, props.focusId, props.roundId],
  () => {
    if (isFocused.value) {
      workspace.value = null;
      progress.value = null;
      loading.value = false;
      error.value = '';
      return;
    }
    void loadOverview();
  },
  { immediate: true },
);
</script>

<template>
  <section
    class="case-center-page"
    :class="{ 'case-center-focused': isFocused }"
    aria-label="病例中心"
  >
    <template v-if="isFocused">
      <div class="case-focus-route-bar" aria-label="病例中心当前工作">
        <button class="case-back-link" type="button" @click="backToWorkbench">← 返回</button>
        <span>病例中心</span><span class="breadcrumb-separator">/</span
        ><strong>{{ focusTitle }}</strong>
      </div>
      <V2DiagnosisWorkspace
        v-if="props.focusKind === 'diagnosis' || props.focusKind === 'report'"
        :case-id="props.caseId"
        :auth-user="props.authUser"
        :focus-kind="props.focusKind"
        :focus-id="props.focusId"
        :frozen-round-id="props.roundId"
        @navigate="emit('navigate', $event)"
      />
      <V2SlideProductionWorkbench
        v-else-if="props.focusKind === 'production'"
        :case-id="props.caseId"
        :auth-user="props.authUser"
        :frozen-round-id="props.roundId"
        @navigate="emit('navigate', $event)"
      />
      <V2TechnicalWorkbench
        v-else-if="props.focusKind === 'technical-order'"
        :case-id="props.caseId"
        :focus-kind="props.focusKind"
        :focus-id="props.focusId"
        @navigate="emit('navigate', $event)"
      />
      <V2FrozenWorkspace
        v-else-if="props.focusKind === 'frozen'"
        :case-id="props.caseId"
        :round-id="props.roundId"
        :auth-user="props.authUser"
        @navigate="emit('navigate', $event)"
      />
      <V2GrossingWorkbench
        v-else-if="props.focusKind === 'grossing'"
        :case-id="props.caseId"
        :auth-user="props.authUser"
        @navigate="emit('navigate', $event)"
      />
    </template>

    <template v-else-if="loading">
      <div class="workspace-loading" aria-live="polite">
        <span class="loading-spinner" aria-hidden="true"></span><span>正在加载病例概览…</span>
      </div>
    </template>

    <p v-else-if="error" class="feedback error" role="alert">{{ error }}</p>

    <template v-else-if="workspace && header">
      <header class="case-overview-header" aria-label="病例固定上下文">
        <button class="case-back-link" type="button" @click="backToWorkbench">← 返回</button>
        <div class="case-overview-identity">
          <div class="case-title-line">
            <h1>{{ header.pathologyNo }}</h1>
            <span class="status-pill">{{ lifecycleLabel(header.lifecycle) }}</span>
          </div>
          <p class="case-patient-line">
            <strong>{{ header.patientReference }}</strong>
            <span v-if="header.visitReference">就诊 {{ header.visitReference }}</span>
            <span>{{ businessTypeName(header.businessTypeCode) }}</span>
          </p>
        </div>
        <div class="case-overview-actions">
          <button
            v-if="can('P14-PERM-034')"
            class="primary-button"
            type="button"
            @click="openFocus('diagnosis')"
          >
            进入诊断
          </button>
          <button
            v-if="
              can('P14-PERM-014') &&
              ['HISTOLOGY', 'ROUTINE', 'CYTOLOGY'].includes(header.businessTypeCode)
            "
            class="secondary-button"
            type="button"
            @click="openFocus(header.businessTypeCode === 'CYTOLOGY' ? 'production' : 'grossing')"
          >
            {{ header.businessTypeCode === 'CYTOLOGY' ? '进入细胞制片' : '进入取材' }}
          </button>
        </div>
      </header>

      <div class="case-overview-facts" aria-label="病例基本信息">
        <div>
          <span>病理号</span><strong>{{ header.pathologyNo }}</strong>
        </div>
        <div>
          <span>患者</span><strong>{{ header.patientReference }}</strong>
        </div>
        <div><span>性别</span><strong>待补充</strong></div>
        <div><span>年龄</span><strong>待补充</strong></div>
        <div>
          <span>病理类型</span><strong>{{ businessTypeName(header.businessTypeCode) }}</strong>
        </div>
        <div>
          <span>登记时间</span><strong>{{ formatDateTime(header.createdAt) }}</strong>
        </div>
        <div>
          <span>当前环节</span><strong>{{ currentStage }}</strong>
        </div>
        <div>
          <span>报告状态</span><strong>{{ reportSummary }}</strong>
        </div>
      </div>

      <nav class="case-section-tabs" aria-label="病例视图">
        <button type="button" class="active">概览</button>
        <button type="button" @click="openFocus('production')">材料与制片</button>
        <button type="button" @click="openFocus('diagnosis')">诊断与阅片</button>
        <button type="button" @click="openFocus('report')">报告</button>
        <button type="button">病例记录</button>
      </nav>

      <main class="case-overview-grid">
        <section class="workspace-panel overview-block" aria-label="临床摘要">
          <header class="panel-title-row">
            <div>
              <p class="section-kicker">病例摘要</p>
              <h2>临床信息</h2>
            </div>
          </header>
          <dl class="overview-definition-list">
            <div>
              <dt>申请项目</dt>
              <dd>{{ header.applicationItemCode || '—' }}</dd>
            </div>
            <div>
              <dt>申请号</dt>
              <dd>{{ header.applicationNo || '—' }}</dd>
            </div>
            <div>
              <dt>来源</dt>
              <dd>
                {{ header.sourceSystemCode === 'MANUAL' ? '手工登记' : header.sourceSystemCode }}
              </dd>
            </div>
            <div>
              <dt>就诊号</dt>
              <dd>{{ header.visitReference || '—' }}</dd>
            </div>
          </dl>
        </section>

        <section class="workspace-panel overview-block" aria-label="材料与制片">
          <header class="panel-title-row">
            <div>
              <p class="section-kicker">材料摘要</p>
              <h2>材料与制片</h2>
            </div>
            <span class="status-pill"
              >{{ materialCounts.completedSlides }}/{{ materialCounts.slides }} 张玻片</span
            >
          </header>
          <div class="overview-metric-line">
            <span
              ><strong>{{ materialCounts.specimens }}</strong
              ><small>标本</small></span
            >
            <span
              ><strong>{{ materialCounts.blocks }}</strong
              ><small>蜡块</small></span
            >
            <span
              ><strong>{{ materialCounts.slides }}</strong
              ><small>玻片</small></span
            >
          </div>
          <div v-if="workspace.materialTree.specimens.length" class="overview-material-list">
            <div v-for="specimen in workspace.materialTree.specimens" :key="specimen.specimenId">
              <strong>标本 {{ specimen.specimenCode }}</strong>
              <span>{{
                specimen.blocks.length
                  ? `${specimen.blocks.length} 个蜡块`
                  : `${specimen.directSlides.length} 张直接玻片`
              }}</span>
            </div>
          </div>
          <p v-else class="muted">当前还没有材料记录。</p>
          <button class="text-button" type="button" @click="openFocus('production')">
            打开当前制片 →
          </button>
        </section>

        <section class="workspace-panel overview-block" aria-label="技术结果">
          <header class="panel-title-row">
            <div>
              <p class="section-kicker">技术结果</p>
              <h2>技术医嘱</h2>
            </div>
            <span class="status-pill">{{ workspace.technicalOrders.length }} 项</span>
          </header>
          <div v-if="workspace.technicalOrders.length" class="overview-list">
            <div v-for="order in workspace.technicalOrders" :key="order.orderId">
              <span
                ><strong>{{ order.orderNo }}</strong
                ><small>{{ order.itemCount }} 个项目 · {{ order.resultCount }} 个结果</small></span
              >
              <span>{{ statusName(order.statusCode) }}</span>
            </div>
          </div>
          <p v-else class="muted">当前没有技术医嘱。</p>
          <button
            v-if="workspace.technicalOrders.length"
            class="text-button"
            type="button"
            @click="openFocus('technical-order')"
          >
            打开技术医嘱 →
          </button>
        </section>

        <section class="workspace-panel overview-block" aria-label="诊断摘要">
          <header class="panel-title-row">
            <div>
              <p class="section-kicker">诊断摘要</p>
              <h2>诊断与阅片</h2>
            </div>
            <span class="status-pill">{{ diagnosisSummary }}</span>
          </header>
          <p class="overview-emphasis">{{ progress?.currentResponsible || '尚未分配当前医生' }}</p>
          <p class="muted">
            材料进度：{{ progress?.material.completed ?? materialCounts.completedSlides }}/{{
              progress?.material.required ?? materialCounts.slides
            }}
            张完成
          </p>
          <button class="text-button" type="button" @click="openFocus('diagnosis')">
            进入诊断与阅片 →
          </button>
        </section>

        <section class="workspace-panel overview-block" aria-label="报告摘要">
          <header class="panel-title-row">
            <div>
              <p class="section-kicker">报告摘要</p>
              <h2>报告</h2>
            </div>
            <span class="status-pill">{{ reportSummary }}</span>
          </header>
          <div v-if="workspace.reports.length" class="overview-list">
            <div v-for="report in workspace.reports.slice(0, 3)" :key="report.reportId">
              <span
                ><strong>{{ report.reportNo }}</strong
                ><small>{{
                  report.natureCode === 'SUPPLEMENTAL' ? '补充报告' : '正式报告'
                }}</small></span
              >
              <span>{{
                report.statusCode === 'EFFECTIVE'
                  ? '已签发'
                  : report.statusCode === 'WITHDRAWN'
                    ? '已撤回'
                    : '处理中'
              }}</span>
            </div>
          </div>
          <p v-else class="muted">当前没有报告版本。</p>
          <button
            v-if="workspace.reports.length"
            class="text-button"
            type="button"
            @click="openFocus('report')"
          >
            打开报告 →
          </button>
        </section>

        <section class="workspace-panel overview-block" aria-label="最近动态">
          <header class="panel-title-row">
            <div>
              <p class="section-kicker">最近动态</p>
              <h2>病例记录</h2>
            </div>
            <span class="muted">{{ workspace.timeline.length }} 条</span>
          </header>
          <ol v-if="timeline.length" class="overview-timeline">
            <li v-for="entry in timeline" :key="entry.eventId">
              <time>{{ formatDateTime(entry.occurredAt) }}</time
              ><span
                ><strong>{{ entry.title }}</strong
                ><small>{{ entry.actorName || '系统记录' }} · {{ entry.detail }}</small></span
              >
            </li>
          </ol>
          <p v-else class="muted">当前没有最近动态。</p>
        </section>
      </main>
    </template>
  </section>
</template>
