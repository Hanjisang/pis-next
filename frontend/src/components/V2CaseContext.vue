<script setup lang="ts">
import { computed, ref, watch } from 'vue';

import type { V2AuthUser } from '../auth';
import {
  blockTypeName,
  businessTypeName,
  formatDateTime,
  friendlyError,
  specimenKindName,
  statusName,
} from '../uiText';
import {
  getV2CaseProgress,
  getV2CaseWorkspace,
  type V2CaseProgress,
  type V2CaseWorkspace,
  type V2WorkspaceTimelineEntry,
} from '../v2WorkspaceApi';
import { getV2ProductionWorkbench, type V2ProductionItem } from '../v2ProductionWorkbenchApi';
import V2HistoryDrawer from './V2HistoryDrawer.vue';

const props = defineProps<{
  caseId: string;
  authUser?: V2AuthUser | null;
  focusKind?: string;
  focusId?: string;
}>();
const emit = defineEmits<{ navigate: [path: string] }>();

const workspace = ref<V2CaseWorkspace | null>(null);
const progress = ref<V2CaseProgress | null>(null);
const productionItems = ref<V2ProductionItem[]>([]);
const productionSummaryError = ref('');
const loading = ref(false);
const error = ref('');
const activeSection = ref<'overview' | 'materials' | 'diagnosis' | 'history' | 'reports'>(
  'overview',
);
const historyTargetId = ref<string | null>(null);
const historyDrawerOpen = ref(false);

const permissions = computed(() => new Set(props.authUser?.permissions ?? []));
const header = computed(() => workspace.value?.caseHeader);
const materialCounts = computed(() => {
  const tree = workspace.value?.materialTree;
  const specimens = tree?.specimens ?? [];
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
const currentResponsibilityLabel = computed(() => {
  const current = workspace.value?.responsibilities.find(
    (item) => !item.completedAt && !item.endedAt,
  );
  if (!current) return '暂无当前处理人';
  return `${roleLabel(current.roleCode)} · ${current.doctorName}`;
});
const currentWorkLabel = computed(() => {
  return (
    {
      registration: '登记',
      grossing: '取材',
      production: '制片',
      diagnosis: '诊断与阅片',
      'technical-order': '技术结果',
      report: '报告',
      frozen: '冰冻制片',
    }[props.focusKind ?? ''] ??
    progress.value?.currentStageLabel ??
    '病例概览'
  );
});
const currentWorkDescription = computed(() => {
  if (props.focusKind === 'diagnosis')
    return '从工作台进入的诊断工作项；病例材料、技术结果和历史仍保留在当前病例中心。';
  if (props.focusKind === 'production')
    return '从工作台进入的制片工作项；当前业务来源和材料关系已在病例上下文中定位。';
  if (props.focusKind === 'technical-order')
    return '从工作台进入的技术医嘱或结果关注；结果完成后会回到原病例。';
  if (props.focusKind === 'report')
    return '从工作台进入的报告处理项；报告版本、签审记录和撤回历史均在此病例内查看。';
  return '病例中心根据病例事实、业务类型和当前权限提供下一步操作。';
});
const derivedProgress = computed(() => {
  const total = materialCounts.value.slides;
  if (!total) return workspace.value?.responsibilities.length ? '诊断处理中' : '待建立材料';
  return `${materialCounts.value.completedSlides}/${total} 张玻片完成`;
});
const reportStatusLabel = computed(() => {
  const reports = workspace.value?.reports ?? [];
  if (reports.some((report) => report.statusCode === 'EFFECTIVE')) return '已签发';
  if (reports.some((report) => report.statusCode === 'WITHDRAWN')) return '报告已撤回';
  return reports.length ? '报告处理中' : '尚未签发';
});
const productionSourceLabel = computed(() => {
  if (header.value?.businessTypeCode === 'CYTOLOGY') return '细胞制片';
  if (header.value?.businessTypeCode === 'FROZEN') return '冰冻制片';
  if (header.value?.businessTypeCode === 'MOLECULAR') return '技术医嘱';
  return '常规制片';
});
const productionMaterialLabel = computed(() => {
  if (header.value?.businessTypeCode === 'CYTOLOGY') return '标本直接制片';
  if (header.value?.businessTypeCode === 'FROZEN') return 'FrozenRound 轮次制片';
  return '取材后建立蜡块与初始玻片';
});
const productionTaskLabel = computed(() => {
  if (productionItems.value.length) return productionItems.value[0].taskSummary;
  if (progress.value?.currentStageCode === 'SIGNED') return '制片已完成，病例进入已签发状态';
  if (progress.value?.material.required) {
    return `材料 ${progress.value.material.completed}/${progress.value.material.required} 已完成`;
  }
  return progress.value?.currentStageLabel || '等待业务材料建立';
});
const visibleTimeline = computed(() =>
  historyTargetId.value
    ? (workspace.value?.timeline.filter((entry) => entry.targetId === historyTargetId.value) ?? [])
    : (workspace.value?.timeline ?? []),
);

watch(
  () => props.caseId,
  () => void load(),
  { immediate: true },
);

watch(
  () => [props.focusKind, props.focusId],
  ([kind]) => {
    activeSection.value =
      kind === 'diagnosis' ? 'diagnosis' : kind === 'report' ? 'reports' : 'overview';
  },
  { immediate: true },
);

async function load() {
  if (!props.caseId) return;
  loading.value = true;
  error.value = '';
  try {
    workspace.value = await getV2CaseWorkspace(props.caseId);
    try {
      progress.value = await getV2CaseProgress(props.caseId);
    } catch {
      progress.value = null;
    }
    await loadProductionSummary();
  } catch (requestError) {
    error.value = friendlyError(requestError, '病例信息暂时无法加载，请刷新后重试。');
    workspace.value = null;
    progress.value = null;
    productionItems.value = [];
  } finally {
    loading.value = false;
  }
}

async function loadProductionSummary() {
  productionSummaryError.value = '';
  const canReadProduction = (props.authUser?.permissions ?? []).some((permission) =>
    ['P14-PERM-014', 'P14-PERM-008', 'P14-PERM-017'].includes(permission),
  );
  if (!canReadProduction) {
    productionItems.value = [];
    return;
  }
  try {
    const workbench = await getV2ProductionWorkbench();
    productionItems.value = Object.values(workbench.queues)
      .flatMap((queue) => queue.items)
      .filter((item) => item.caseId === props.caseId);
  } catch (requestError) {
    productionItems.value = [];
    productionSummaryError.value = friendlyError(
      requestError,
      '当前角色暂时无法读取生产任务队列。',
    );
  }
}

function openDiagnosis() {
  if (header.value) emit('navigate', `/v2/diagnosis/${header.value.caseId}`);
}

function openGrossing() {
  if (header.value) emit('navigate', `/v2/grossing/${header.value.caseId}`);
}

function openProduction() {
  if (header.value) emit('navigate', `/v2/production/${header.value.caseId}`);
}

function openFrozen() {
  if (header.value) emit('navigate', `/v2/frozen/${header.value.caseId}`);
}

function openDigitalSlides(slideId?: string) {
  if (!header.value) return;
  const query = slideId ? `?slideId=${encodeURIComponent(slideId)}` : '';
  emit('navigate', `/v2/digital-slides/${header.value.caseId}${query}`);
}

function openReport(reportId: string) {
  if (header.value) emit('navigate', `/v2/reports/${header.value.caseId}?reportId=${reportId}`);
}

function openProductionItem(item: V2ProductionItem) {
  if (item.productionContext === 'FROZEN_ROUND' && item.productionContextId) {
    emit(
      'navigate',
      `/v2/frozen/${item.caseId}?roundId=${encodeURIComponent(item.productionContextId)}`,
    );
    return;
  }
  if (item.productionContext === 'TECHNICAL_ORDER' && item.orderId) {
    emit(
      'navigate',
      `/v2/technical-orders/${item.caseId}?focus=technical-order&focusId=${encodeURIComponent(item.orderId)}`,
    );
    return;
  }
  emit('navigate', `/v2/production/${item.caseId}`);
}

function viewHistory(targetId?: string) {
  historyTargetId.value = targetId ?? null;
  historyDrawerOpen.value = true;
}

function actorLabel(entry: V2WorkspaceTimelineEntry) {
  return entry.actorName || entry.actorRef || '系统记录';
}

function roleLabel(role: string) {
  return { INITIAL: '初诊医生', REVIEW: '复诊医生', AUDIT: '审核医生' }[role] ?? role;
}

function lifecycleLabel(lifecycle: string) {
  return lifecycle.includes('CANCEL') ? '已取消' : '进行中';
}
</script>

<template>
  <section class="case-workspace-page" aria-label="病例中心">
    <div v-if="loading" class="workspace-loading" aria-live="polite">
      <span class="loading-spinner" aria-hidden="true"></span>
      <span>正在加载病例上下文…</span>
    </div>
    <p v-else-if="error" class="feedback error" role="alert">{{ error }}</p>

    <template v-else-if="workspace && header">
      <header class="case-header case-header-primary">
        <div class="case-header-main">
          <button class="case-back-link" type="button" @click="emit('navigate', '/v2/workbench')">
            ← 工作台
          </button>
          <div class="case-header-kicker">
            <span class="status-dot success" aria-hidden="true"></span>
            <span>病例中心</span>
            <span class="breadcrumb-separator">/</span>
            <span>{{ businessTypeName(header.businessTypeCode) }}</span>
          </div>
          <div class="case-title-line">
            <h2>{{ header.pathologyNo }}</h2>
            <span class="status-pill">{{ lifecycleLabel(header.lifecycle) }}</span>
          </div>
          <p class="case-patient-line">
            <strong>{{ header.patientReference }}</strong>
            <span v-if="header.visitReference">就诊 {{ header.visitReference }}</span>
            <span>病理类型 {{ businessTypeName(header.businessTypeCode) }}</span>
          </p>
        </div>
        <div class="case-header-actions">
          <button class="secondary-button" type="button" @click="load">刷新</button>
          <button
            v-if="permissions.has('P14-PERM-034')"
            class="primary-button"
            type="button"
            @click="openDiagnosis"
          >
            打开诊断
          </button>
          <button
            v-if="
              permissions.has('P14-PERM-013') &&
              ['HISTOLOGY', 'ROUTINE', 'FROZEN'].includes(header.businessTypeCode)
            "
            class="secondary-button"
            type="button"
            @click="openGrossing"
          >
            进入取材
          </button>
        </div>
      </header>

      <div class="case-facts-strip case-header-facts" aria-label="病例固定上下文">
        <div>
          <span>病理号</span><strong>{{ header.pathologyNo }}</strong>
        </div>
        <div>
          <span>患者</span><strong>{{ header.patientReference }}</strong>
        </div>
        <div><span>来源科室</span><strong>待补充</strong></div>
        <div><span>送检医生</span><strong>待补充</strong></div>
        <div>
          <span>登记时间</span><strong>{{ formatDateTime(header.createdAt) }}</strong>
        </div>
        <div>
          <span>当前环节</span
          ><strong>{{ progress?.currentStageLabel || currentWorkLabel }}</strong>
        </div>
        <div>
          <span>当前处理人</span
          ><strong>{{ progress?.currentResponsible || currentResponsibilityLabel }}</strong>
        </div>
        <div>
          <span>报告状态</span
          ><strong>{{
            progress?.reportStatus === 'EFFECTIVE'
              ? '已签发'
              : progress?.reportStatus === 'WITHDRAWN'
                ? '报告已撤回'
                : progress?.reportStatus
                  ? '未签发'
                  : reportStatusLabel
          }}</strong>
        </div>
      </div>

      <section class="workspace-panel case-current-work-panel" aria-label="当前工作区">
        <div>
          <p class="section-kicker">当前工作</p>
          <h3>{{ currentWorkLabel }}</h3>
          <p class="muted">{{ currentWorkDescription }}</p>
        </div>
        <div class="current-work-actions">
          <button
            v-if="props.focusKind === 'diagnosis' && permissions.has('P14-PERM-034')"
            class="primary-button"
            type="button"
            @click="openDiagnosis"
          >
            继续诊断与阅片
          </button>
          <button
            v-else-if="props.focusKind === 'production' && permissions.has('P14-PERM-014')"
            class="primary-button"
            type="button"
            @click="openProduction"
          >
            继续制片
          </button>
          <button
            v-else-if="props.focusKind === 'technical-order' && permissions.has('P14-PERM-017')"
            class="primary-button"
            type="button"
            @click="emit('navigate', `/v2/technical-orders/${header.caseId}`)"
          >
            查看技术医嘱
          </button>
          <button
            v-else-if="props.focusKind === 'report' && permissions.has('P14-PERM-036')"
            class="primary-button"
            type="button"
            @click="emit('navigate', `/v2/reports/${header.caseId}`)"
          >
            查看报告
          </button>
          <button class="secondary-button" type="button" @click="activeSection = 'overview'">
            病例概览
          </button>
        </div>
      </section>

      <div class="case-facts-strip case-material-facts" aria-label="病例材料摘要">
        <div>
          <span>标本</span><strong>{{ materialCounts.specimens }}</strong>
        </div>
        <div>
          <span>{{ header.businessTypeCode === 'CYTOLOGY' ? '直接玻片' : '蜡块' }}</span
          ><strong>{{
            header.businessTypeCode === 'CYTOLOGY' ? materialCounts.slides : materialCounts.blocks
          }}</strong>
        </div>
        <div>
          <span>玻片</span
          ><strong>{{ materialCounts.completedSlides }}/{{ materialCounts.slides }} 完成</strong>
        </div>
        <div>
          <span>当前进度</span><strong>{{ progress?.currentStageLabel || derivedProgress }}</strong>
        </div>
      </div>

      <section v-if="progress" class="workspace-panel case-progress-panel" aria-label="当前进度">
        <header class="workspace-panel-header">
          <div>
            <p class="section-kicker">当前进度</p>
            <h3>{{ progress.currentStageLabel }}</h3>
            <p class="muted">
              当前处理人 {{ progress.currentResponsible || '待分派' }} · 已等待
              {{ progress.waitingMinutes }} 分钟
            </p>
          </div>
          <span class="status-pill" :class="{ success: progress.currentStageCode === 'SIGNED' }">
            {{ progress.material.status }}
          </span>
        </header>
        <ol class="case-progress-steps">
          <li v-for="step in progress.steps" :key="step.code" :class="step.status.toLowerCase()">
            <span>{{ step.label }}</span
            ><small>{{
              step.status === 'COMPLETED' ? '已完成' : step.status === 'CURRENT' ? '当前' : '待处理'
            }}</small>
          </li>
        </ol>
      </section>

      <section class="workspace-panel case-business-production-panel" aria-label="业务生产摘要">
        <header class="workspace-panel-header">
          <div>
            <p class="section-kicker">BUSINESS PRODUCTION</p>
            <h3>业务生产摘要</h3>
            <p class="muted">按业务来源展示当前制片任务；技术环节记录不决定病例生命周期。</p>
          </div>
          <span class="status-pill">{{ productionSourceLabel }}</span>
        </header>
        <div class="production-summary-grid">
          <div>
            <span>生产来源</span>
            <strong>{{ productionSourceLabel }}</strong>
            <small>{{ productionMaterialLabel }}</small>
          </div>
          <div>
            <span>材料完成</span>
            <strong
              >{{ progress?.material.completed ?? materialCounts.completedSlides }}/{{
                progress?.material.required ?? materialCounts.slides
              }}</strong
            >
            <small>{{ progress?.material.status || derivedProgress }}</small>
          </div>
          <div>
            <span>当前生产任务</span>
            <strong>{{ productionTaskLabel }}</strong>
            <small>{{
              productionItems.length
                ? `${productionItems.length} 项队列工作`
                : '当前角色暂无待处理队列项'
            }}</small>
          </div>
          <div>
            <span>业务入口</span>
            <strong>{{
              header.businessTypeCode === 'FROZEN' ? 'FrozenRound' : '病例生产工作台'
            }}</strong>
            <small>可从当前病例继续处理</small>
          </div>
        </div>
        <div v-if="productionItems.length" class="production-summary-task-list">
          <article
            v-for="item in productionItems"
            :key="`${item.productionContext}-${item.slideCode ?? item.orderId ?? item.taskSummary}`"
            class="production-summary-task"
          >
            <div>
              <strong>{{ item.taskSummary }}</strong>
              <span>{{ item.materialSummary }} · 等待 {{ item.waitingMinutes }} 分钟</span>
            </div>
            <button class="text-button" type="button" @click="openProductionItem(item)">
              进入处理
            </button>
          </article>
        </div>
        <p v-else-if="productionSummaryError" class="muted">{{ productionSummaryError }}</p>
        <details class="optional-trace-panel case-production-trace">
          <summary>查看可选技术记录入口</summary>
          <p class="muted">
            脱水、包埋、切片、染色、封片仅作为技术事实记录，可在生产工作台中展开查看，不作为默认队列。
          </p>
          <button class="text-button" type="button" @click="openProduction">打开生产工作台</button>
        </details>
      </section>

      <nav class="case-section-tabs" aria-label="病例视图">
        <button
          type="button"
          :class="{ active: activeSection === 'overview' }"
          @click="activeSection = 'overview'"
        >
          概览
        </button>
        <button
          type="button"
          :class="{ active: activeSection === 'materials' }"
          @click="activeSection = 'materials'"
        >
          材料
        </button>
        <button
          type="button"
          :class="{ active: activeSection === 'diagnosis' }"
          @click="activeSection = 'diagnosis'"
        >
          诊断与阅片
        </button>
        <button
          type="button"
          :class="{ active: activeSection === 'reports' }"
          @click="activeSection = 'reports'"
        >
          报告 <span class="tab-count">{{ workspace.reports.length }}</span>
        </button>
        <button
          type="button"
          :class="{ active: activeSection === 'history' }"
          @click="activeSection = 'history'"
        >
          病例记录 <span class="tab-count">{{ workspace.timeline.length }}</span>
        </button>
      </nav>

      <section v-if="activeSection === 'overview'" class="case-overview-grid">
        <main class="case-workspace-main">
          <section class="workspace-panel case-clinical-summary-panel">
            <header class="workspace-panel-header">
              <div>
                <p class="section-kicker">临床与申请</p>
                <h3>病例摘要</h3>
              </div>
              <span class="status-pill">{{ businessTypeName(header.businessTypeCode) }}</span>
            </header>
            <div class="case-clinical-summary-grid">
              <div>
                <span>患者</span><strong>{{ header.patientReference }}</strong>
              </div>
              <div>
                <span>就诊号</span><strong>{{ header.visitReference || '未记录' }}</strong>
              </div>
              <div>
                <span>申请号</span><strong>{{ header.applicationNo }}</strong>
              </div>
              <div>
                <span>申请项目</span><strong>{{ header.applicationItemCode }}</strong>
              </div>
              <div>
                <span>来源系统</span><strong>{{ header.sourceSystemCode }}</strong>
              </div>
              <div><span>送检原因</span><strong>由申请项目与临床资料共同确定</strong></div>
            </div>
          </section>
          <section class="workspace-panel case-material-panel">
            <header class="workspace-panel-header">
              <div>
                <p class="section-kicker">材料与制片</p>
                <h3>标本、蜡块与玻片</h3>
              </div>
              <button class="text-button" type="button" @click="activeSection = 'materials'">
                查看材料关系
              </button>
            </header>
            <div class="overview-material-summary">
              <div>
                <strong>{{ materialCounts.specimens }}</strong
                ><span>标本</span>
              </div>
              <div>
                <strong>{{ materialCounts.blocks }}</strong
                ><span>{{
                  header.businessTypeCode === 'CYTOLOGY' ? '蜡块（不需要）' : '蜡块'
                }}</span>
              </div>
              <div>
                <strong>{{ materialCounts.completedSlides }}/{{ materialCounts.slides }}</strong
                ><span>玻片完成</span>
              </div>
              <div>
                <strong>{{ workspace.digitalSlides.length }}</strong
                ><span>数字切片</span>
              </div>
            </div>
            <p class="muted">
              点击“材料”查看 Specimen → Block → Slide → DigitalSlide
              关系；细胞病例可直接从标本进入玻片。
            </p>
          </section>
          <section class="workspace-panel case-business-production-panel" aria-label="业务生产摘要">
            <header class="workspace-panel-header">
              <div>
                <p class="section-kicker">生产摘要</p>
                <h3>{{ productionSourceLabel }}</h3>
              </div>
              <span class="status-pill">{{ productionTaskLabel }}</span>
            </header>
            <div class="production-summary-grid">
              <div>
                <span>业务来源</span><strong>{{ productionSourceLabel }}</strong
                ><small>{{ productionMaterialLabel }}</small>
              </div>
              <div>
                <span>材料完成</span
                ><strong
                  >{{ progress?.material.completed ?? materialCounts.completedSlides }}/{{
                    progress?.material.required ?? materialCounts.slides
                  }}</strong
                ><small>{{ progress?.material.status || derivedProgress }}</small>
              </div>
              <div>
                <span>技术医嘱</span><strong>{{ workspace.technicalOrders.length }} 项</strong
                ><small
                  >结果返回
                  {{
                    workspace.technicalOrders.reduce((total, item) => total + item.resultCount, 0)
                  }}
                  项</small
                >
              </div>
              <div>
                <span>异常</span><strong>按病例记录查看</strong
                ><small>不把物理阶段变成默认队列</small>
              </div>
            </div>
          </section>
        </main>
        <aside class="case-workspace-rail">
          <section class="workspace-panel case-quick-panel">
            <header class="workspace-panel-header">
              <div>
                <p class="section-kicker">下一步</p>
                <h3>可执行操作</h3>
              </div>
            </header>
            <div class="quick-action-list">
              <button v-if="permissions.has('P14-PERM-034')" type="button" @click="openDiagnosis">
                <span>进入诊断</span><small>材料、阅片、诊断和报告</small>
              </button>
              <button v-if="permissions.has('P14-PERM-014')" type="button" @click="openProduction">
                <span>制片</span><small>处理未完成玻片</small>
              </button>
              <button
                v-if="header.businessTypeCode === 'FROZEN' && permissions.has('P14-PERM-008')"
                type="button"
                @click="openFrozen"
              >
                <span>冰冻制片</span><small>查看 FrozenRound 与术中材料</small>
              </button>
              <button type="button" @click="activeSection = 'reports'">
                <span>报告</span><small>{{ reportStatusLabel }}</small>
              </button>
              <button type="button" @click="activeSection = 'history'">
                <span>病例记录</span><small>查看最近操作与签审记录</small>
              </button>
            </div>
          </section>
          <section class="workspace-panel timeline-mini-panel">
            <header class="workspace-panel-header">
              <div>
                <p class="section-kicker">最近动态</p>
                <h3>病例记录</h3>
              </div>
              <button class="text-button" type="button" @click="activeSection = 'history'">
                查看全部
              </button>
            </header>
            <ol class="timeline-list timeline-list-mini">
              <li v-for="entry in workspace.timeline.slice(0, 5)" :key="entry.eventId">
                <time>{{ formatDateTime(entry.occurredAt) }}</time>
                <div>
                  <strong>{{ entry.title }}</strong
                  ><span>{{ actorLabel(entry) }}</span>
                </div>
              </li>
              <li v-if="!workspace.timeline.length" class="timeline-empty">尚无病例记录</li>
            </ol>
          </section>
        </aside>
      </section>

      <div v-else-if="activeSection === 'materials'" class="case-workspace-grid">
        <main class="case-workspace-main">
          <section class="workspace-panel case-material-panel">
            <header class="workspace-panel-header">
              <div>
                <p class="section-kicker">材料关系</p>
                <h3>标本、蜡块与玻片</h3>
              </div>
              <div class="header-actions-inline">
                <button
                  v-if="permissions.has('P14-PERM-014')"
                  class="text-button"
                  type="button"
                  @click="openProduction"
                >
                  查看制片
                </button>
                <button
                  v-if="workspace.digitalSlides.length"
                  class="text-button"
                  type="button"
                  @click="openDigitalSlides()"
                >
                  数字切片 {{ workspace.digitalSlides.length }} 张
                </button>
              </div>
            </header>

            <div v-if="!workspace.materialTree.specimens.length" class="empty-state compact">
              <strong>当前病例还没有有效材料</strong>
              <span>请核对登记结果或业务类型。</span>
            </div>
            <div v-else class="case-material-tree case-material-tree-rich">
              <article
                v-for="specimen in workspace.materialTree.specimens"
                :key="specimen.specimenId"
                class="material-tree-specimen"
              >
                <header>
                  <div>
                    <span class="material-tree-label">标本 {{ specimen.specimenCode }}</span>
                    <strong>{{ specimenKindName(specimen.specimenKindCode) }}</strong>
                  </div>
                  <button
                    class="history-link"
                    type="button"
                    @click="viewHistory(specimen.specimenId)"
                  >
                    查看历史
                  </button>
                </header>
                <ul>
                  <li
                    v-for="block in specimen.blocks"
                    :key="block.blockId"
                    class="material-tree-block"
                    :class="{
                      'material-focused':
                        props.focusKind === 'block' && props.focusId === block.blockId,
                    }"
                  >
                    <div class="material-node-heading">
                      <span class="node-glyph block-glyph" aria-hidden="true">蜡</span>
                      <strong>{{ block.blockCode }}</strong>
                      <span class="muted">{{ blockTypeName(block.blockType) }}</span>
                      <button
                        class="history-link"
                        type="button"
                        @click="viewHistory(block.blockId)"
                      >
                        历史
                      </button>
                    </div>
                    <ul v-if="block.slides.length" class="material-tree-slides">
                      <li v-for="slide in block.slides" :key="slide.slideId">
                        <button
                          class="material-node-button"
                          :class="{
                            'material-focused':
                              props.focusKind === 'slide' && props.focusId === slide.slideId,
                          }"
                          type="button"
                          @click="openDigitalSlides(slide.slideId)"
                        >
                          <span class="node-glyph slide-glyph" aria-hidden="true">片</span>
                          <strong>{{ slide.slideCode }}</strong>
                          <span class="muted">{{ slide.slideType }}</span>
                          <span v-if="slide.completed" class="status-pill success">已完成</span>
                          <span v-else class="status-pill warning">待完成</span>
                        </button>
                        <button
                          class="history-link"
                          type="button"
                          @click="viewHistory(slide.slideId)"
                        >
                          历史
                        </button>
                      </li>
                    </ul>
                    <p v-else class="material-node-empty">尚无玻片</p>
                  </li>
                  <li
                    v-for="slide in specimen.directSlides"
                    :key="slide.slideId"
                    class="material-tree-direct-slide"
                  >
                    <button
                      class="material-node-button"
                      type="button"
                      @click="openDigitalSlides(slide.slideId)"
                    >
                      <span class="node-glyph slide-glyph" aria-hidden="true">片</span>
                      <strong>{{ slide.slideCode }}</strong>
                      <span class="muted">标本直接制片 · {{ slide.slideType }}</span>
                      <span v-if="slide.completed" class="status-pill success">已完成</span>
                      <span v-else class="status-pill warning">待完成</span>
                    </button>
                    <button class="history-link" type="button" @click="viewHistory(slide.slideId)">
                      历史
                    </button>
                  </li>
                </ul>
              </article>
            </div>
          </section>

          <section class="workspace-panel case-record-grid">
            <div>
              <p class="section-kicker">取材记录</p>
              <h3>大体与取材</h3>
              <div v-if="workspace.grossings.length" class="record-list">
                <article
                  v-for="grossing in workspace.grossings"
                  :key="grossing.grossingId"
                  class="record-row"
                >
                  <div>
                    <strong>{{ grossing.grossingNo }}</strong
                    ><span>{{
                      grossing.sourceType === 'FROZEN_CONTEXT' ? '冰冻材料' : '常规取材'
                    }}</span>
                  </div>
                  <p>{{ grossing.grossDescription || '未填写大体描述' }}</p>
                  <time>{{ formatDateTime(grossing.completedAt || grossing.startedAt) }}</time>
                </article>
              </div>
              <div v-else class="empty-state compact"><span>尚无取材记录</span></div>
            </div>
            <div>
              <p class="section-kicker">技术医嘱</p>
              <h3>检查与结果</h3>
              <div v-if="workspace.technicalOrders.length" class="record-list">
                <article
                  v-for="order in workspace.technicalOrders"
                  :key="order.orderId"
                  class="record-row"
                >
                  <div>
                    <strong>{{ order.orderNo }}</strong
                    ><span>{{ statusName(order.statusCode) }}</span>
                  </div>
                  <p>
                    {{ order.itemCount }} 项检查 · {{ order.resultCount }}/{{ order.itemCount }}
                    项结果已返回
                  </p>
                  <time>{{ formatDateTime(order.createdAt) }}</time>
                </article>
              </div>
              <div v-else class="empty-state compact"><span>尚无技术医嘱</span></div>
            </div>
          </section>
        </main>

        <aside class="case-workspace-rail">
          <section class="workspace-panel case-quick-panel">
            <header class="workspace-panel-header">
              <div>
                <p class="section-kicker">下一步</p>
                <h3>可执行操作</h3>
              </div>
            </header>
            <div class="quick-action-list">
              <button v-if="permissions.has('P14-PERM-034')" type="button" @click="openDiagnosis">
                <span>进入诊断</span><small>查看材料、填写诊断并处理报告</small>
              </button>
              <button v-if="permissions.has('P14-PERM-014')" type="button" @click="openProduction">
                <span>制片</span><small>处理未完成玻片</small>
              </button>
              <button v-if="header.businessTypeCode === 'FROZEN'" type="button" @click="openFrozen">
                <span>查看冰冻</span><small>查看轮次与术中材料</small>
              </button>
              <button type="button" @click="viewHistory()">
                <span>病例记录</span><small>登记、取材、制片、诊断和报告</small>
              </button>
            </div>
          </section>
          <section class="workspace-panel timeline-mini-panel">
            <header class="workspace-panel-header">
              <div>
                <p class="section-kicker">最近动态</p>
                <h3>病例记录</h3>
              </div>
              <button class="text-button" type="button" @click="viewHistory()">查看全部</button>
            </header>
            <ol class="timeline-list timeline-list-mini">
              <li v-for="entry in workspace.timeline.slice(0, 5)" :key="entry.eventId">
                <time>{{ formatDateTime(entry.occurredAt) }}</time>
                <div>
                  <strong>{{ entry.title }}</strong
                  ><span>{{ actorLabel(entry) }}</span>
                </div>
              </li>
              <li v-if="!workspace.timeline.length" class="timeline-empty">尚无病例记录</li>
            </ol>
          </section>
        </aside>
      </div>

      <section v-else-if="activeSection === 'history'" class="workspace-panel timeline-panel-full">
        <header class="workspace-panel-header">
          <div>
            <p class="section-kicker">可追溯记录</p>
            <h3>{{ historyTargetId ? '当前对象历史' : '病例记录' }}</h3>
            <p class="muted">按业务时间查看病例从登记到报告的关键事实。</p>
          </div>
          <button v-if="historyTargetId" class="text-button" type="button" @click="viewHistory()">
            查看全部历史
          </button>
        </header>
        <ol class="timeline-list timeline-list-full">
          <li v-for="entry in visibleTimeline" :key="entry.eventId" class="timeline-entry-rich">
            <time>{{ formatDateTime(entry.occurredAt) }}</time>
            <div class="timeline-entry-content">
              <div>
                <strong>{{ entry.title }}</strong
                ><span class="timeline-actor">{{ actorLabel(entry) }}</span>
              </div>
              <p v-if="entry.detail">{{ entry.detail }}</p>
              <button
                class="history-link"
                type="button"
                @click="viewHistory(entry.targetId ?? undefined)"
              >
                查看记录详情
              </button>
            </div>
          </li>
          <li v-if="!visibleTimeline.length" class="timeline-empty">
            当前对象还没有可展示的病例记录。
          </li>
        </ol>
      </section>

      <section v-else-if="activeSection === 'diagnosis'" class="case-diagnosis-context-grid">
        <main class="workspace-panel diagnosis-context-summary">
          <header class="workspace-panel-header">
            <div>
              <p class="section-kicker">诊断与阅片</p>
              <h3>当前病例诊断工作区</h3>
              <p class="muted">材料、数字切片、临床摘要、技术结果和病例历史保持在当前病例中心。</p>
            </div>
            <button
              v-if="permissions.has('P14-PERM-034')"
              class="primary-button"
              type="button"
              @click="openDiagnosis"
            >
              进入诊断工作区
            </button>
          </header>
          <div class="diagnosis-context-facts">
            <div>
              <span>可阅片玻片</span
              ><strong>{{ materialCounts.completedSlides }}/{{ materialCounts.slides }}</strong>
            </div>
            <div>
              <span>技术医嘱</span><strong>{{ workspace.technicalOrders.length }} 项</strong>
            </div>
            <div>
              <span>当前医生</span
              ><strong>{{ progress?.currentResponsible || currentResponsibilityLabel }}</strong>
            </div>
            <div>
              <span>报告</span><strong>{{ reportStatusLabel }}</strong>
            </div>
          </div>
          <section class="viewer-context-panel">
            <h4>材料与数字切片</h4>
            <p v-if="!workspace.digitalSlides.length" class="muted">
              当前病例尚无数字切片；可先从材料视图查看实体玻片。
            </p>
            <button
              v-for="slide in workspace.digitalSlides"
              :key="slide.digitalSlideId"
              class="material-node-button"
              type="button"
              @click="openDigitalSlides(slide.slideId ?? undefined)"
            >
              <strong>{{ slide.viewerReference }}</strong
              ><span class="muted"
                >{{ slide.sourcePlatform }} · {{ statusName(slide.statusCode) }}</span
              ><span class="queue-row-arrow">→</span>
            </button>
          </section>
          <section class="viewer-context-panel">
            <h4>技术结果</h4>
            <article
              v-for="order in workspace.technicalOrders"
              :key="order.orderId"
              class="record-row"
            >
              <div>
                <strong>{{ order.orderNo }}</strong
                ><span>{{ statusName(order.statusCode) }}</span>
              </div>
              <p>{{ order.resultCount }}/{{ order.itemCount }} 项结果已返回</p>
              <time>{{ formatDateTime(order.createdAt) }}</time>
            </article>
            <p v-if="!workspace.technicalOrders.length" class="muted">尚无技术医嘱或结果。</p>
          </section>
        </main>
        <aside class="case-workspace-rail">
          <section class="workspace-panel responsibility-panel-full">
            <header class="workspace-panel-header">
              <div>
                <p class="section-kicker">诊断记录</p>
                <h3>医生记录</h3>
                <p class="muted">显示初诊、复诊和审核医生，不展示内部责任术语。</p>
              </div>
            </header>
            <header class="workspace-panel-header">
              <div>
                <p class="section-kicker">签审记录</p>
                <h3>当前处理人</h3>
              </div>
            </header>
            <div v-if="workspace.responsibilities.length" class="responsibility-grid">
              <article
                v-for="item in workspace.responsibilities"
                :key="item.responsibilityId"
                class="responsibility-card"
              >
                <span class="responsibility-role">{{ roleLabel(item.roleCode) }}</span>
                <strong>{{ item.doctorName }}</strong>
                <span>{{
                  item.completedAt
                    ? `已完成 · ${formatDateTime(item.completedAt)}`
                    : item.endedAt
                      ? '已结束'
                      : '进行中'
                }}</span>
              </article>
            </div>
            <div v-else class="empty-state">
              <strong>尚未建立诊断记录</strong
              ><span>病例进入诊断后会在这里显示初诊、复诊和审核医生。</span>
            </div>
          </section>
        </aside>
      </section>

      <section v-else class="workspace-panel reports-panel-full">
        <header class="workspace-panel-header">
          <div>
            <p class="section-kicker">正式输出</p>
            <h3>报告历史</h3>
            <p class="muted">原始报告、撤回记录和补充报告均保留。</p>
          </div>
        </header>
        <div v-if="workspace.reports.length" class="report-history-list">
          <article
            v-for="report in workspace.reports"
            :key="report.reportId"
            class="report-history-row"
          >
            <div>
              <strong>{{ report.reportNo }}</strong
              ><span>{{ report.natureCode === 'SUPPLEMENTAL' ? '补充报告' : '原始报告' }}</span>
            </div>
            <span
              class="status-pill"
              :class="report.statusCode === 'EFFECTIVE' ? 'success' : 'warning'"
              >{{ statusName(report.statusCode) }}</span
            >
            <div>
              <span>签发：{{ report.signedBy }}</span
              ><time>{{ formatDateTime(report.signedAt) }}</time>
            </div>
            <p v-if="report.statusCode === 'WITHDRAWN'">
              撤回原因：{{ report.withdrawalReason || '未填写' }}
            </p>
            <button class="text-button" type="button" @click="openReport(report.reportId)">
              查看报告
            </button>
          </article>
        </div>
        <div v-else class="empty-state">
          <strong>尚未签发报告</strong><span>报告签发后会在这里保留完整历史。</span>
        </div>
      </section>

      <V2HistoryDrawer
        :open="historyDrawerOpen"
        :case-id="header.caseId"
        :entries="workspace.timeline"
        :target-id="historyTargetId"
        :title="historyTargetId ? '对象历史' : '病例记录'"
        target-label="业务追溯"
        @close="historyDrawerOpen = false"
      />
    </template>
  </section>
</template>
