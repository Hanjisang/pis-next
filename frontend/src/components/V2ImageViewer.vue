<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue';

type ViewerContext = {
  caseNo?: string;
  specimenCode?: string;
  blockCode?: string;
  slideCode?: string;
  digitalSlideId?: string;
};

const props = defineProps<{
  source?: string | null;
  label?: string;
  sourcePlatform?: string;
  context?: ViewerContext;
}>();

const viewerRoot = ref<HTMLElement | null>(null);
const viewport = ref<HTMLDivElement | null>(null);
const scale = ref(1);
const offsetX = ref(0);
const offsetY = ref(0);
const dragging = ref(false);
const imageError = ref(false);
const fullscreenActive = ref(false);
const dragStart = ref({ x: 0, y: 0, offsetX: 0, offsetY: 0 });

const isImageReference = computed(() => {
  const source = props.source?.trim() ?? '';
  return /^(https?:|data:image|blob:|\/)/i.test(source);
});

const transform = computed(
  () => `translate(${offsetX.value}px, ${offsetY.value}px) scale(${scale.value})`,
);

function zoom(delta: number) {
  scale.value = Math.min(8, Math.max(0.25, Number((scale.value + delta).toFixed(2))));
}

function reset() {
  scale.value = 1;
  offsetX.value = 0;
  offsetY.value = 0;
}

function onPointerDown(event: PointerEvent) {
  if (!isImageReference.value) return;
  dragging.value = true;
  (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
  dragStart.value = {
    x: event.clientX,
    y: event.clientY,
    offsetX: offsetX.value,
    offsetY: offsetY.value,
  };
}

function onPointerMove(event: PointerEvent) {
  if (!dragging.value) return;
  offsetX.value = dragStart.value.offsetX + event.clientX - dragStart.value.x;
  offsetY.value = dragStart.value.offsetY + event.clientY - dragStart.value.y;
}

function onPointerUp() {
  dragging.value = false;
}

function onWheel(event: WheelEvent) {
  if (!isImageReference.value) return;
  event.preventDefault();
  zoom(event.deltaY > 0 ? -0.1 : 0.1);
}

function onFullscreenChange() {
  fullscreenActive.value = document.fullscreenElement === viewerRoot.value;
}

function fullscreen() {
  if (fullscreenActive.value) {
    void document.exitFullscreen?.();
    return;
  }
  void viewerRoot.value?.requestFullscreen?.();
}

function imageFailed() {
  imageError.value = true;
}

onMounted(() => document.addEventListener('fullscreenchange', onFullscreenChange));
onUnmounted(() => document.removeEventListener('fullscreenchange', onFullscreenChange));
</script>

<template>
  <section ref="viewerRoot" class="image-viewer" aria-label="数字切片阅片器">
    <header class="image-viewer-toolbar">
      <div>
        <strong>{{ label || '数字切片' }}</strong>
        <span>{{ sourcePlatform || '通用阅片器' }}</span>
      </div>
      <div class="viewer-controls">
        <button type="button" aria-label="缩小" @click="zoom(-0.25)">−</button>
        <span aria-live="polite">{{ Math.round(scale * 100) }}%</span>
        <button type="button" aria-label="放大" @click="zoom(0.25)">＋</button>
        <button type="button" aria-label="还原视图" @click="reset">还原</button>
        <button
          type="button"
          :aria-label="fullscreenActive ? '退出全屏' : '全屏查看'"
          @click="fullscreen"
        >
          {{ fullscreenActive ? '退出全屏' : '全屏' }}
        </button>
      </div>
    </header>
    <dl v-if="context" class="image-viewer-context" aria-label="阅片上下文">
      <div v-if="context.caseNo">
        <dt>病例</dt>
        <dd>{{ context.caseNo }}</dd>
      </div>
      <div v-if="context.specimenCode">
        <dt>标本</dt>
        <dd>{{ context.specimenCode }}</dd>
      </div>
      <div v-if="context.blockCode">
        <dt>蜡块</dt>
        <dd>{{ context.blockCode }}</dd>
      </div>
      <div v-if="context.slideCode">
        <dt>玻片</dt>
        <dd>{{ context.slideCode }}</dd>
      </div>
      <div v-if="context.digitalSlideId">
        <dt>数字切片</dt>
        <dd>{{ context.digitalSlideId }}</dd>
      </div>
    </dl>
    <div
      ref="viewport"
      class="image-viewer-viewport"
      :class="{ dragging, 'image-viewer-placeholder': !isImageReference || imageError }"
      tabindex="0"
      @pointerdown="onPointerDown"
      @pointermove="onPointerMove"
      @pointerup="onPointerUp"
      @pointercancel="onPointerUp"
      @wheel="onWheel"
    >
      <img
        v-if="isImageReference && !imageError"
        :src="source ?? undefined"
        :alt="label || '数字切片图像'"
        :style="{ transform }"
        draggable="false"
        @error="imageFailed"
      />
      <div v-else class="viewer-empty-content">
        <span class="viewer-placeholder-icon" aria-hidden="true">◎</span>
        <strong>{{ imageError ? '阅片器引用暂时不可访问' : '已准备好阅片入口' }}</strong>
        <p>
          {{
            imageError
              ? '请检查图像平台连接，或从外部阅片器打开。'
              : '当前记录提供了数字切片元数据，可接入医院阅片平台。'
          }}
        </p>
      </div>
      <div v-if="isImageReference && !imageError" class="viewer-minimap" aria-hidden="true">
        <img :src="source ?? undefined" alt="" />
        <span></span>
      </div>
    </div>
    <footer class="image-viewer-footer">
      <span>滚轮缩放 · 按住拖动平移</span>
      <a v-if="source" :href="source" target="_blank" rel="noreferrer">在外部阅片器打开 ↗</a>
    </footer>
  </section>
</template>
