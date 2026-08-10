<script setup lang="ts">
import { computed, ref, watch } from 'vue';

import { friendlyError, statusName } from '../uiText';
import { getV2Case, type V2CaseResult } from '../v2Api';
import { getV2MaterialTree, type V2MaterialTree } from '../v2MaterialApi';
import { operationsRequest, type DigitalSlide } from '../v2OperationsApi';
import V2CaseHeader from './V2CaseHeader.vue';
import V2HistoryDrawer from './V2HistoryDrawer.vue';
import V2ImageViewer from './V2ImageViewer.vue';

const props = defineProps<{ caseId?: string; selectedSlideId?: string }>();
const emit = defineEmits<{ navigate: [path: string] }>();

const lookupCaseId = ref(props.caseId ?? '');
const slides = ref<DigitalSlide[]>([]);
const materials = ref<V2MaterialTree | null>(null);
const caseSummary = ref<V2CaseResult | null>(null);
const draft = ref({
  blockId: '',
  slideId: '',
  bindingModeCode: 'MANUAL',
  viewerReference: '',
  sourcePlatform: '',
});
const loading = ref(false);
const submitting = ref(false);
const error = ref('');
const notice = ref('');
const historyDrawerOpen = ref(false);

const blocks = computed(
  () => materials.value?.specimens.flatMap((specimen) => specimen.blocks) ?? [],
);
const physicalSlides = computed(
  () =>
    materials.value?.specimens.flatMap((specimen) => [
      ...specimen.blocks.flatMap((block) => block.slides),
      ...specimen.directSlides,
    ]) ?? [],
);
const selectedDigitalSlide = computed(
  () =>
    slides.value.find((item) => item.digitalSlideId === props.selectedSlideId) ??
    slides.value[0] ??
    null,
);
const selectedViewerContext = computed(() => {
  const selected = selectedDigitalSlide.value;
  if (!selected || !materials.value) return undefined;
  const specimen = materials.value.specimens.find((item) =>
    [...item.directSlides, ...item.blocks.flatMap((block) => block.slides)].some(
      (slide) => slide.slideId === selected.slideId,
    ),
  );
  const block = specimen?.blocks.find((item) => item.blockId === selected.blockId);
  const physicalSlide = specimen
    ? [...specimen.directSlides, ...specimen.blocks.flatMap((item) => item.slides)].find(
        (item) => item.slideId === selected.slideId,
      )
    : undefined;
  return {
    caseNo: materials.value.caseNo,
    specimenCode: specimen?.specimenCode,
    blockCode: block?.blockCode,
    slideCode: physicalSlide?.slideCode,
    digitalSlideId: selected.digitalSlideId,
  };
});

watch(
  () => props.caseId,
  (value) => {
    lookupCaseId.value = value ?? '';
    if (value) void load();
  },
  { immediate: true },
);

async function load() {
  if (!lookupCaseId.value.trim()) return;
  loading.value = true;
  error.value = '';
  try {
    [slides.value, materials.value, caseSummary.value] = await Promise.all([
      operationsRequest<DigitalSlide[]>(`/digital-slides/cases/${lookupCaseId.value.trim()}`),
      getV2MaterialTree(lookupCaseId.value.trim()),
      getV2Case(lookupCaseId.value.trim()),
    ]);
  } catch (requestError) {
    error.value = friendlyError(requestError, '数字切片暂时无法加载，请检查病例。');
  } finally {
    loading.value = false;
  }
}

async function submit(operation: () => Promise<void>) {
  submitting.value = true;
  error.value = '';
  notice.value = '';
  try {
    await operation();
  } catch (requestError) {
    error.value = friendlyError(requestError, '数字切片绑定未完成，请重试。');
  } finally {
    submitting.value = false;
  }
}

function create() {
  if (!materials.value || !draft.value.viewerReference.trim()) return;
  void submit(async () => {
    await operationsRequest('/digital-slides', {
      method: 'POST',
      body: JSON.stringify({
        caseId: materials.value!.caseId,
        blockId: draft.value.blockId || null,
        slideId: draft.value.slideId || null,
        bindingModeCode: draft.value.bindingModeCode,
        viewerReference: draft.value.viewerReference.trim(),
        sourcePlatform: draft.value.sourcePlatform.trim() || '通用数字切片平台',
      }),
    });
    await load();
    notice.value = '数字切片已绑定；数字扫描进度不会阻塞物理制片或报告签发。';
  });
}

function rebind(item: DigitalSlide) {
  void submit(async () => {
    await operationsRequest(`/digital-slides/${item.digitalSlideId}/rebind`, {
      method: 'POST',
      body: JSON.stringify({
        blockId: draft.value.blockId || null,
        slideId: draft.value.slideId || null,
      }),
    });
    await load();
    notice.value = '数字切片已改绑到所选材料。';
  });
}

function unbind(item: DigitalSlide) {
  void submit(async () => {
    await operationsRequest(`/digital-slides/${item.digitalSlideId}/unbind`, { method: 'POST' });
    await load();
    notice.value = '数字切片已解除材料绑定，病例关联仍保留。';
  });
}

function blockCode(id?: string) {
  return id
    ? (blocks.value.find((item) => item.blockId === id)?.blockCode ?? '已绑定蜡块')
    : '未绑定蜡块';
}

function slideCode(id?: string) {
  return id
    ? (physicalSlides.value.find((item) => item.slideId === id)?.slideCode ?? '已绑定玻片')
    : '未绑定玻片';
}
</script>

<template>
  <section class="digital-slide-page" aria-label="数字切片工作台">
    <header class="page-heading compact-heading">
      <div>
        <p class="section-kicker">数字切片</p>
        <h2>绑定与阅片入口</h2>
        <p>管理元数据、材料绑定和阅片器引用；不实现扫描仪协议或 WSI 图像服务。</p>
      </div>
      <form class="case-lookup" @submit.prevent="load">
        <label>打开病例 <input v-model="lookupCaseId" /></label
        ><button class="secondary-button" type="submit" :disabled="loading">打开</button>
      </form>
    </header>
    <p v-if="error" class="feedback error" role="alert">{{ error }}</p>
    <p v-if="notice" class="feedback success" role="status">{{ notice }}</p>
    <div v-if="!materials" class="empty-state workspace-panel">
      <strong>请打开一个病例</strong><span>绑定后可从诊断工作区直接打开数字切片。</span>
    </div>
    <template v-else>
      <V2CaseHeader
        :case-id="materials.caseId"
        :pathology-no="caseSummary?.caseNo ?? materials.caseNo"
        :patient-reference="caseSummary?.patientReference ?? '当前病例'"
        :visit-reference="caseSummary?.visitReference"
        :business-type-code="materials.businessTypeCode"
        current-responsibility="数字切片"
        report-status="查看中"
        :progress="`物理 ${physicalSlides.length} 张 · 数字 ${slides.length} 张`"
        notice="数字扫描默认不阻塞物理制片或报告签发"
        @open-case="emit('navigate', `/v2/cases/${materials.caseId}`)"
      >
        <template #actions>
          <button class="secondary-button" type="button" @click="historyDrawerOpen = true">
            历史记录
          </button>
        </template>
      </V2CaseHeader>
      <section v-if="slides.length" class="workspace-panel">
        <header class="panel-title-row">
          <div>
            <p class="section-kicker">诊断阅片</p>
            <h3>数字切片查看器</h3>
          </div>
          <span class="status-pill">数字扫描不阻塞物理制片</span>
        </header>
        <div class="digital-diagnosis-grid">
          <aside class="digital-slide-rail" aria-label="数字切片列表">
            <button
              v-for="item in slides"
              :key="item.digitalSlideId"
              type="button"
              :class="{ active: selectedDigitalSlide?.digitalSlideId === item.digitalSlideId }"
              @click="
                emit(
                  'navigate',
                  `/v2/digital-slides/${materials.caseId}?slideId=${item.digitalSlideId}`,
                )
              "
            >
              <strong>{{ slideCode(item.slideId) }}</strong>
              <small>{{ blockCode(item.blockId) }}</small>
              <small>{{ item.sourcePlatform }}</small>
            </button>
          </aside>
          <V2ImageViewer
            v-if="selectedDigitalSlide"
            :source="selectedDigitalSlide.viewerReference"
            :label="slideCode(selectedDigitalSlide.slideId)"
            :source-platform="selectedDigitalSlide.sourcePlatform"
            :context="selectedViewerContext"
          />
        </div>
      </section>
      <div class="digital-workspace-grid">
        <section class="workspace-panel">
          <header class="panel-title-row">
            <div>
              <p class="section-kicker">新增绑定</p>
              <h3>数字切片来源</h3>
            </div>
            <span class="status-pill">Case 必填，Block / Slide 可选</span>
          </header>
          <form class="field-grid" @submit.prevent="create">
            <label
              >来源平台 <input v-model="draft.sourcePlatform" placeholder="例如 WSI 扫描平台"
            /></label>
            <label
              >绑定方式
              <select v-model="draft.bindingModeCode">
                <option value="MANUAL">人工绑定</option>
                <option value="AUTOMATIC">自动绑定</option>
              </select></label
            >
            <label class="span-two"
              >阅片器引用
              <input v-model="draft.viewerReference" required placeholder="阅片器可访问地址或引用"
            /></label>
            <label
              >关联蜡块
              <select v-model="draft.blockId">
                <option value="">不关联蜡块</option>
                <option v-for="block in blocks" :key="block.blockId" :value="block.blockId">
                  {{ block.blockCode }}
                </option>
              </select></label
            >
            <label
              >关联玻片
              <select v-model="draft.slideId">
                <option value="">不关联玻片</option>
                <option v-for="slide in physicalSlides" :key="slide.slideId" :value="slide.slideId">
                  {{ slide.slideCode }}
                </option>
              </select></label
            >
            <button class="primary-button" type="submit" :disabled="submitting">
              绑定数字切片
            </button>
          </form>
        </section>
        <section class="workspace-panel">
          <header class="panel-title-row">
            <div>
              <p class="section-kicker">已绑定</p>
              <h3>{{ slides.length }} 张数字切片</h3>
            </div>
            <button
              class="text-button"
              type="button"
              @click="emit('navigate', `/v2/diagnosis/${materials.caseId}`)"
            >
              进入诊断
            </button>
          </header>
          <div v-if="!slides.length" class="empty-state compact">
            <strong>当前没有数字切片</strong><span>扫描回调或人工绑定后显示在这里。</span>
          </div>
          <div v-else class="digital-slide-list">
            <article v-for="item in slides" :key="item.digitalSlideId">
              <span
                ><strong>{{ slideCode(item.slideId) }}</strong
                ><small>{{ blockCode(item.blockId) }} · {{ item.sourcePlatform }}</small></span
              >
              <span class="status-pill" :class="{ success: item.statusCode === 'ACTIVE' }">{{
                statusName(item.statusCode)
              }}</span>
              <a :href="item.viewerReference" target="_blank" rel="noreferrer">打开阅片器</a>
              <div class="inline-actions">
                <button class="text-button" type="button" @click="rebind(item)">
                  按左侧选择改绑</button
                ><button class="text-button danger-text" type="button" @click="unbind(item)">
                  解除绑定
                </button>
              </div>
            </article>
          </div>
        </section>
      </div>
      <V2HistoryDrawer
        :open="historyDrawerOpen"
        :case-id="materials.caseId"
        :target-id="selectedDigitalSlide?.digitalSlideId"
        title="数字切片历史"
        target-label="数字切片"
        @close="historyDrawerOpen = false"
      />
    </template>
  </section>
</template>
