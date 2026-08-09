<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';

import { businessTypeName, friendlyError, formatDateTime, idempotencyKey } from '../uiText';
import {
  completeV2Slide,
  completeV2Slides,
  createV2DirectCytologySlide,
  getV2MaterialTree,
  getV2ProductionWorkbench,
  printV2Slide,
  type V2MaterialTree,
  type V2ProductionSlide,
} from '../v2MaterialApi';

type ProductionTab = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED';

const caseId = defineModel<string>('caseId', { default: '' });
const slides = ref<V2ProductionSlide[]>([]);
const materialTree = ref<V2MaterialTree | null>(null);
const activeTab = ref<ProductionTab>('PENDING');
const selectedSlideIds = ref<string[]>([]);
const scanCode = ref('');
const scanInput = ref<HTMLInputElement | null>(null);
const scanFeedback = ref<{ tone: 'success' | 'warning' | 'error' | 'neutral'; message: string }>({
  tone: 'neutral',
  message: '扫描玻片条码后按 Enter，系统会完成玻片并自动等待下一张。',
});
const directSpecimenId = ref('');
const directSlideCode = ref('');
const directSlideType = ref('CYTOLOGY');
const loading = ref(false);
const busy = ref(false);
const error = ref('');
const notice = ref('');

const caseSlides = computed(() =>
  caseId.value ? slides.value.filter((slide) => slide.caseId === caseId.value) : slides.value,
);
function completedToday(slide: V2ProductionSlide) {
  if (!slide.completedAt) return false;
  const completed = new Date(slide.completedAt);
  const today = new Date();
  return (
    completed.getFullYear() === today.getFullYear() &&
    completed.getMonth() === today.getMonth() &&
    completed.getDate() === today.getDate()
  );
}
const visibleSlides = computed(() =>
  caseSlides.value.filter((slide) => {
    if (activeTab.value === 'COMPLETED') return completedToday(slide);
    if (activeTab.value === 'IN_PROGRESS') return !slide.completedAt && slide.printCount > 0;
    return !slide.completedAt && slide.printCount === 0;
  }),
);
const tabCounts = computed(() => ({
  PENDING: caseSlides.value.filter((slide) => !slide.completedAt && slide.printCount === 0).length,
  IN_PROGRESS: caseSlides.value.filter((slide) => !slide.completedAt && slide.printCount > 0)
    .length,
  COMPLETED: caseSlides.value.filter(completedToday).length,
}));
const allVisibleSelected = computed(
  () =>
    visibleSlides.value.length > 0 &&
    visibleSlides.value.every((slide) => selectedSlideIds.value.includes(slide.slideId)),
);
const supportsDirectSlide = computed(() =>
  ['CYTOLOGY_NON_GYN', 'CYTOLOGY'].includes(materialTree.value?.businessTypeCode ?? ''),
);

watch(
  () => caseId.value,
  () => void loadMaterialTree(),
);

async function run(action: () => Promise<void>) {
  busy.value = true;
  error.value = '';
  notice.value = '';
  try {
    await action();
  } catch (requestError) {
    error.value = friendlyError(requestError, '制片操作未完成，请刷新后重试。');
  } finally {
    busy.value = false;
  }
}

async function loadQueue() {
  loading.value = true;
  error.value = '';
  try {
    const response = await getV2ProductionWorkbench();
    slides.value = response.slides;
    selectedSlideIds.value = [];
  } catch (requestError) {
    error.value = friendlyError(requestError, '制片队列暂时无法加载，请稍后重试。');
  } finally {
    loading.value = false;
  }
}

async function loadMaterialTree() {
  if (!caseId.value) {
    materialTree.value = null;
    return;
  }
  try {
    materialTree.value = await getV2MaterialTree(caseId.value);
    directSpecimenId.value = materialTree.value.specimens[0]?.specimenId ?? '';
    directSlideCode.value ||= `${materialTree.value.specimens[0]?.specimenCode ?? 'A'}-1`;
  } catch {
    materialTree.value = null;
  }
}

function toggleSlide(slideId: string) {
  selectedSlideIds.value = selectedSlideIds.value.includes(slideId)
    ? selectedSlideIds.value.filter((id) => id !== slideId)
    : [...selectedSlideIds.value, slideId];
}

function toggleAllVisible() {
  if (allVisibleSelected.value) {
    const visible = new Set(visibleSlides.value.map((slide) => slide.slideId));
    selectedSlideIds.value = selectedSlideIds.value.filter((id) => !visible.has(id));
  } else {
    selectedSlideIds.value = [
      ...new Set([...selectedSlideIds.value, ...visibleSlides.value.map((slide) => slide.slideId)]),
    ];
  }
}

function printSlide(slide: V2ProductionSlide) {
  void run(async () => {
    await printV2Slide({
      slideId: slide.slideId,
      reason: slide.printCount ? '制片工作台补打' : '制片工作台打印',
      idempotencyKey: idempotencyKey('ux01-slide-print'),
    });
    notice.value = `玻片 ${slide.slideCode} 标签已发送到当前打印机。`;
    await loadQueue();
  });
}

function completeSlide(slide: V2ProductionSlide) {
  if (slide.completedAt) return;
  void run(async () => {
    await completeV2Slide({
      slideId: slide.slideId,
      expectedVersion: slide.concurrencyVersion,
      idempotencyKey: idempotencyKey('ux01-slide-complete'),
    });
    notice.value = `玻片 ${slide.slideCode} 已完成。`;
    await loadQueue();
  });
}

function completeSelected() {
  const selected = slides.value.filter(
    (slide) => selectedSlideIds.value.includes(slide.slideId) && !slide.completedAt,
  );
  if (!selected.length) return;
  void run(async () => {
    const result = await completeV2Slides({
      slides: selected.map((slide) => ({
        slideId: slide.slideId,
        expectedVersion: slide.concurrencyVersion,
      })),
      idempotencyKey: idempotencyKey('ux01-slide-complete-batch'),
    });
    notice.value = `已完成 ${result.changedCount} 张玻片。`;
    await loadQueue();
  });
}

function submitScan() {
  const code = scanCode.value.trim().toUpperCase();
  if (!code) return;
  const slide = slides.value.find((item) => item.slideCode.toUpperCase() === code);
  if (!slide) {
    scanFeedback.value = {
      tone: 'error',
      message: `未找到玻片“${scanCode.value.trim()}”，请检查条码。`,
    };
    scanCode.value = '';
    scanInput.value?.focus();
    return;
  }
  if (slide.completedAt) {
    scanFeedback.value = {
      tone: 'warning',
      message: `玻片 ${slide.slideCode} 已于 ${formatDateTime(slide.completedAt)} 完成，无需重复操作。`,
    };
    scanCode.value = '';
    scanInput.value?.focus();
    return;
  }
  void run(async () => {
    await completeV2Slide({
      slideId: slide.slideId,
      expectedVersion: slide.concurrencyVersion,
      idempotencyKey: idempotencyKey('ux01-slide-scan'),
    });
    scanFeedback.value = {
      tone: 'success',
      message: `玻片 ${slide.slideCode} 已完成，请扫描下一张。`,
    };
    scanCode.value = '';
    await loadQueue();
    scanInput.value?.focus();
  });
}

function createDirectSlide() {
  if (!caseId.value || !directSpecimenId.value || !directSlideCode.value.trim()) return;
  void run(async () => {
    await createV2DirectCytologySlide({
      caseId: caseId.value,
      specimenId: directSpecimenId.value,
      slideCode: directSlideCode.value.trim(),
      slideType: directSlideType.value,
      idempotencyKey: idempotencyKey('ux01-direct-slide'),
    });
    notice.value = `直接玻片 ${directSlideCode.value.trim()} 已建立，不需要蜡块。`;
    directSlideCode.value = '';
    await Promise.all([loadQueue(), loadMaterialTree()]);
  });
}

onMounted(() => {
  void Promise.all([loadQueue(), loadMaterialTree()]);
});
</script>

<template>
  <section class="production-workspace" aria-label="玻片制片工作台">
    <header class="page-heading compact-heading">
      <div>
        <p class="section-kicker">制片</p>
        <h2>今天有哪些玻片要完成</h2>
        <p>围绕玻片处理打印、扫码完成和补打；物理流程记录不占据主工作台。</p>
      </div>
      <div class="heading-actions">
        <button class="secondary-button" type="button" :disabled="loading" @click="loadQueue">
          {{ loading ? '刷新中…' : '刷新队列' }}
        </button>
        <button
          class="primary-button"
          type="button"
          :disabled="!selectedSlideIds.length || busy"
          @click="completeSelected"
        >
          批量完成（{{ selectedSlideIds.length }}）
        </button>
      </div>
    </header>

    <p v-if="error" class="feedback error" role="alert">{{ error }}</p>
    <p v-if="notice" class="feedback success" role="status">{{ notice }}</p>

    <section class="scan-completion-panel" aria-labelledby="scan-heading">
      <div>
        <label id="scan-heading" for="slide-scan-input">扫码完成玻片</label>
        <div class="input-action-row">
          <input
            id="slide-scan-input"
            ref="scanInput"
            v-model="scanCode"
            autocomplete="off"
            placeholder="扫描或输入玻片号"
            @keydown.enter.prevent="submitScan"
          />
          <button class="primary-button" type="button" :disabled="busy" @click="submitScan">
            完成
          </button>
        </div>
      </div>
      <p class="scan-feedback" :class="scanFeedback.tone" role="status">
        {{ scanFeedback.message }}
      </p>
    </section>

    <section v-if="supportsDirectSlide && materialTree" class="workspace-panel direct-slide-panel">
      <header class="panel-title-row">
        <div>
          <p class="section-kicker">细胞病理</p>
          <h3>直接建立玻片</h3>
        </div>
        <span class="status-pill success">蜡块可选</span>
      </header>
      <div class="field-grid three-columns direct-slide-fields">
        <label>
          来源标本
          <select v-model="directSpecimenId">
            <option
              v-for="specimen in materialTree.specimens"
              :key="specimen.specimenId"
              :value="specimen.specimenId"
            >
              {{ specimen.specimenCode }} · {{ specimen.specimenNo }}
            </option>
          </select>
        </label>
        <label>玻片号 <input v-model="directSlideCode" placeholder="例如 A-1" /></label>
        <label>
          玻片类型
          <select v-model="directSlideType">
            <option value="CYTOLOGY">细胞涂片</option>
            <option value="HE">HE</option>
          </select>
        </label>
      </div>
      <button class="secondary-button" type="button" :disabled="busy" @click="createDirectSlide">
        建立直接玻片
      </button>
    </section>

    <div class="production-tabs" role="tablist" aria-label="制片队列状态">
      <button
        v-for="tab in ['PENDING', 'IN_PROGRESS', 'COMPLETED'] as const"
        :key="tab"
        type="button"
        role="tab"
        :aria-selected="activeTab === tab"
        :class="{ active: activeTab === tab }"
        @click="activeTab = tab"
      >
        {{ tab === 'PENDING' ? '待制片' : tab === 'IN_PROGRESS' ? '进行中' : '今日完成' }}
        <span class="count-pill">{{ tabCounts[tab] }}</span>
      </button>
    </div>

    <div class="production-list" role="table" aria-label="玻片生产列表">
      <div class="production-row header" role="row">
        <span role="columnheader">
          <input
            type="checkbox"
            aria-label="选择当前列表全部玻片"
            :checked="allVisibleSelected"
            @change="toggleAllVisible"
          />
        </span>
        <span role="columnheader">病理号 / 患者</span>
        <span role="columnheader">玻片号</span>
        <span role="columnheader">染色 / 项目</span>
        <span role="columnheader">来源</span>
        <span role="columnheader">状态</span>
        <span role="columnheader">操作</span>
      </div>
      <div v-if="loading" class="list-skeleton"><span></span><span></span><span></span></div>
      <div v-else-if="!visibleSlides.length" class="empty-state">
        <strong>
          {{
            activeTab === 'PENDING'
              ? '当前没有待制片玻片'
              : activeTab === 'IN_PROGRESS'
                ? '当前没有进行中的玻片'
                : '今天还没有完成记录'
          }}
        </strong>
        <span>切换状态可查看其他玻片。</span>
      </div>
      <div v-for="slide in visibleSlides" :key="slide.slideId" class="production-row" role="row">
        <span role="cell">
          <input
            type="checkbox"
            :aria-label="`选择玻片 ${slide.slideCode}`"
            :checked="selectedSlideIds.includes(slide.slideId)"
            @change="toggleSlide(slide.slideId)"
          />
        </span>
        <span role="cell"
          ><strong>{{ slide.caseNo }}</strong
          ><small>{{ slide.patientReference }}</small></span
        >
        <span role="cell"
          ><strong>{{ slide.slideCode }}</strong
          ><small>{{ businessTypeName(slide.businessTypeCode) }}</small></span
        >
        <span role="cell">{{ slide.slideType }}</span>
        <span role="cell">{{ slide.blockCode ?? slide.specimenCode ?? '直接材料' }}</span>
        <span role="cell">
          <span v-if="slide.completedAt" class="status-pill success">已完成</span>
          <span v-else-if="slide.printCount" class="status-pill current">已打印</span>
          <span v-else class="status-pill">待打印</span>
        </span>
        <span class="inline-actions" role="cell">
          <button class="text-button" type="button" @click="printSlide(slide)">
            {{ slide.printCount ? '补打' : '打印' }}
          </button>
          <button
            v-if="!slide.completedAt"
            class="text-button"
            type="button"
            @click="completeSlide(slide)"
          >
            完成
          </button>
          <small v-else class="muted">{{ formatDateTime(slide.completedAt) }}</small>
        </span>
      </div>
    </div>
  </section>
</template>
