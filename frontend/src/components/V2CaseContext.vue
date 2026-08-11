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
const activeSection = ref<'materials' | 'history' | 'responsibility' | 'reports'>('materials');
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
  if (!current) return '暂无当前责任';
  return `${roleLabel(current.roleCode)} · ${current.doctorName}`;
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
    if (kind) activeSection.value = 'materials';
  },
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
            <span>业务类型 {{ businessTypeName(header.businessTypeCode) }}</span>
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

      <div class="case-facts-strip" aria-label="病例关键信息">
        <div>
          <span>患者</span><strong>{{ header.patientReference }}</strong>
        </div>
        <div>
          <span>业务类型</span><strong>{{ businessTypeName(header.businessTypeCode) }}</strong>
        </div>
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
          <span>建立时间</span><strong>{{ formatDateTime(header.createdAt) }}</strong>
        </div>
        <div>
          <span>当前责任</span
          ><strong>{{ progress?.currentResponsible || currentResponsibilityLabel }}</strong>
        </div>
        <div>
          <span>当前进度</span><strong>{{ progress?.currentStageLabel || derivedProgress }}</strong>
        </div>
        <div>
          <span>报告状态</span
          ><strong>{{
            progress?.reportStatus === 'EFFECTIVE'
              ? '已签发'
              : progress?.reportStatus === 'WITHDRAWN'
                ? '报告已撤回'
                : progress?.reportStatus
                  ? '报告处理中'
                  : reportStatusLabel
          }}</strong>
        </div>
      </div>

      <section
        v-if="progress"
        class="workspace-panel case-progress-panel"
        aria-label="病例进度投影"
      >
        <header class="workspace-panel-header">
          <div>
            <p class="section-kicker">CASE PROGRESS</p>
            <h3>{{ progress.currentStageLabel }}</h3>
            <p class="muted">
              当前责任 {{ progress.currentResponsible || '待分派' }} · 已等待
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
            <button class="text-button" type="button" @click="emit('navigate', item.deepLink)">
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

      <nav class="case-section-tabs" aria-label="病例内容">
        <button
          type="button"
          :class="{ active: activeSection === 'materials' }"
          @click="activeSection = 'materials'"
        >
          材料与制片
        </button>
        <button
          type="button"
          :class="{ active: activeSection === 'history' }"
          @click="activeSection = 'history'"
        >
          业务历史 <span class="tab-count">{{ workspace.timeline.length }}</span>
        </button>
        <button
          type="button"
          :class="{ active: activeSection === 'responsibility' }"
          @click="activeSection = 'responsibility'"
        >
          责任链 <span class="tab-count">{{ workspace.responsibilities.length }}</span>
        </button>
        <button
          type="button"
          :class="{ active: activeSection === 'reports' }"
          @click="activeSection = 'reports'"
        >
          报告 <span class="tab-count">{{ workspace.reports.length }}</span>
        </button>
      </nav>

      <div v-if="activeSection === 'materials'" class="case-workspace-grid">
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
                <span>查看制片</span><small>处理未完成玻片</small>
              </button>
              <button v-if="header.businessTypeCode === 'FROZEN'" type="button" @click="openFrozen">
                <span>查看冰冻</span><small>查看轮次与术中材料</small>
              </button>
              <button type="button" @click="viewHistory()">
                <span>查看业务历史</span><small>登记、取材、制片、诊断和报告</small>
              </button>
            </div>
          </section>
          <section class="workspace-panel timeline-mini-panel">
            <header class="workspace-panel-header">
              <div>
                <p class="section-kicker">最近动态</p>
                <h3>业务历史</h3>
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
              <li v-if="!workspace.timeline.length" class="timeline-empty">尚无业务历史</li>
            </ol>
          </section>
        </aside>
      </div>

      <section v-else-if="activeSection === 'history'" class="workspace-panel timeline-panel-full">
        <header class="workspace-panel-header">
          <div>
            <p class="section-kicker">可追溯记录</p>
            <h3>{{ historyTargetId ? '当前对象历史' : '病例业务历史' }}</h3>
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
            当前对象还没有可展示的业务历史。
          </li>
        </ol>
      </section>

      <section
        v-else-if="activeSection === 'responsibility'"
        class="workspace-panel responsibility-panel-full"
      >
        <header class="workspace-panel-header">
          <div>
            <p class="section-kicker">诊断责任</p>
            <h3>责任链</h3>
            <p class="muted">显示每个诊断环节的医生、时间和完成状态。</p>
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
          <strong>尚未建立诊断责任</strong
          ><span>病例进入诊断后会在这里显示初诊、复诊和审核医生。</span>
        </div>
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
        :title="historyTargetId ? '对象历史' : '病例业务历史'"
        target-label="业务追溯"
        @close="historyDrawerOpen = false"
      />
    </template>
  </section>
</template>
