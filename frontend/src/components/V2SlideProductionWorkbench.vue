<script setup lang="ts">
import { computed, ref } from 'vue';

import {
  completeV2Slide,
  completeV2Slides,
  getV2MaterialTree,
  printV2Slide,
  type V2MaterialTree,
  type V2SlideNode,
} from '../v2MaterialApi';

const caseId = defineModel<string>('caseId', { default: '' });
const tree = ref<V2MaterialTree | null>(null);
const selectedSlideIds = ref<string[]>([]);
const busy = ref(false);
const errorMessage = ref('');
const notice = ref('');

const allSlides = computed<V2SlideNode[]>(() => {
  if (!tree.value) return [];
  return tree.value.specimens.flatMap((specimen) => [
    ...specimen.blocks.flatMap((block) => block.slides),
    ...specimen.directSlides,
  ]);
});
const pendingInitialSlides = computed(() =>
  allSlides.value.filter((slide) => slide.sourceContextType === 'INITIAL' && !slide.completed),
);

async function run(action: () => Promise<void>) {
  busy.value = true;
  errorMessage.value = '';
  notice.value = '';
  try {
    await action();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '请求失败';
  } finally {
    busy.value = false;
  }
}

function toggleSlide(slideId: string) {
  selectedSlideIds.value = selectedSlideIds.value.includes(slideId)
    ? selectedSlideIds.value.filter((id) => id !== slideId)
    : [...selectedSlideIds.value, slideId];
}

async function refreshTree() {
  tree.value = await getV2MaterialTree(caseId.value);
  selectedSlideIds.value = [];
}

function loadTree() {
  if (!caseId.value.trim()) return;
  void run(async () => {
    await refreshTree();
    notice.value = 'Material Tree 已按 Specimen → Block → Slide 真实关系刷新。';
  });
}

function completeOne(slide: V2SlideNode) {
  void run(async () => {
    await completeV2Slide({
      slideId: slide.slideId,
      expectedVersion: slide.concurrencyVersion,
      idempotencyKey: `v2-slide-complete-${slide.slideId}-${slide.concurrencyVersion}`,
    });
    await refreshTree();
    notice.value = 'Slide 已完成，Material Tree 已刷新。';
  });
}

function completeSelected() {
  const selected = allSlides.value.filter((slide) =>
    selectedSlideIds.value.includes(slide.slideId),
  );
  if (!selected.length) return;
  void run(async () => {
    await completeV2Slides({
      slides: selected.map((slide) => ({
        slideId: slide.slideId,
        expectedVersion: slide.concurrencyVersion,
      })),
      idempotencyKey: `v2-slide-batch-${selected.map((slide) => slide.slideId).join('-')}`,
    });
    await refreshTree();
    notice.value = '选中 Slide 已批量完成，Material Tree 已刷新。';
  });
}

function printOne(slide: V2SlideNode) {
  void run(async () => {
    const result = await printV2Slide({
      slideId: slide.slideId,
      reason: 'synthetic material label',
      idempotencyKey: `v2-slide-print-${slide.slideId}-${Date.now()}`,
    });
    notice.value = `${slide.slideCode} 打印结果：${result.resultCode}；材料实体保持不变。`;
  });
}
</script>

<template>
  <section class="workbench" aria-label="V2-I02 Slide 生产工作台">
    <div class="section-heading">
      <div>
        <p class="eyebrow">PIS V2 · MATERIAL TREE</p>
        <h2>Slide 生产与材料树</h2>
      </div>
      <span class="status-dot">INITIAL · 可追溯 · 可重试</span>
    </div>

    <p v-if="errorMessage" class="error-banner" role="alert">{{ errorMessage }}</p>
    <p v-if="notice" class="success-banner" role="status">{{ notice }}</p>

    <div class="business-card">
      <label>Case ID<input v-model="caseId" required /></label>
      <button :disabled="busy || !caseId" type="button" @click="loadTree">
        读取 Material Tree
      </button>
      <p v-if="tree" class="muted">
        {{ tree.caseNo }} · INITIAL required {{ tree.initialRequiredCount }} · completed
        {{ tree.initialCompletedCount }}
      </p>
      <p class="muted">每个 Slide 保留内部 ID、版本号和打印/重打审计记录。</p>
      <div class="slide-actions">
        <button
          :disabled="busy || !selectedSlideIds.length"
          type="button"
          @click="completeSelected"
        >
          批量完成选中 Slide
        </button>
        <span class="muted">待完成 INITIAL：{{ pendingInitialSlides.length }}</span>
      </div>
    </div>

    <div v-if="tree" class="material-tree" aria-label="真实材料关系树">
      <article v-for="specimen in tree.specimens" :key="specimen.specimenId" class="module-card">
        <h3>Specimen {{ specimen.specimenCode }}</h3>
        <p class="muted">{{ specimen.specimenNo }} · {{ specimen.specimenKindCode }}</p>
        <div v-for="block in specimen.blocks" :key="block.blockId" class="tree-block">
          <strong>Block {{ block.blockCode }}</strong>
          <span class="muted">{{ block.blockType }}</span>
          <label v-for="slide in block.slides" :key="slide.slideId" class="tree-slide">
            <input
              type="checkbox"
              :checked="selectedSlideIds.includes(slide.slideId)"
              :disabled="slide.completed"
              @change="toggleSlide(slide.slideId)"
            />
            <span
              >{{ slide.slideCode }} · {{ slide.sourceContextType }} · v{{
                slide.concurrencyVersion
              }}</span
            >
            <span class="tree-slide-actions">
              <button :disabled="busy || slide.completed" type="button" @click="completeOne(slide)">
                完成
              </button>
              <button :disabled="busy" type="button" @click="printOne(slide)">打印/重打</button>
            </span>
          </label>
        </div>
        <p v-if="specimen.directSlides.length" class="muted">Direct Slide</p>
        <span v-for="slide in specimen.directSlides" :key="slide.slideId">{{
          slide.slideCode
        }}</span>
      </article>
    </div>
  </section>
</template>
