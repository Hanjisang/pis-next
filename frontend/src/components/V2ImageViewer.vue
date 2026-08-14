<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';

import {
  isRegularImageSource,
  isTiledViewerSource,
  type ImageViewerAdapter,
  type ViewerCapture,
  type ViewerViewport,
} from '../viewer/ImageViewerAdapter';
import { RegularImageViewerAdapter } from '../viewer/RegularImageViewerAdapter';
import { TiledWSIViewerAdapter } from '../viewer/TiledWSIViewerAdapter';

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
const emit = defineEmits<{
  annotation: [
    geometry: {
      x: number;
      y: number;
      coordinateSystem: 'NORMALIZED_IMAGE' | 'NORMALIZED_VIEWPORT';
      viewport: ViewerViewport | null;
    },
  ];
  measurement: [
    geometry: {
      x1: number;
      y1: number;
      x2: number;
      y2: number;
      value: number;
      coordinateSystem: 'NORMALIZED_IMAGE' | 'NORMALIZED_VIEWPORT';
      viewport: ViewerViewport | null;
    },
  ];
}>();

const viewerRoot = ref<HTMLElement | null>(null);
const viewerHost = ref<HTMLElement | null>(null);
const adapter = ref<ImageViewerAdapter | null>(null);
const scale = ref(1);
const imageError = ref(false);
const fullscreenActive = ref(false);
const loading = ref(false);
const reviewMode = ref<'NONE' | 'POINT' | 'MEASURE'>('NONE');
const measurementStart = ref<{ x: number; y: number } | null>(null);

const sourceValue = computed(() => props.source?.trim() ?? '');
const tiled = computed(() => isTiledViewerSource(sourceValue.value));
const regular = computed(() => isRegularImageSource(sourceValue.value));
const supported = computed(() => tiled.value || regular.value);
const modeLabel = computed(() => {
  if (tiled.value) return 'WSI 分层阅片器';
  if (regular.value) return '普通图像阅片器';
  return props.sourcePlatform || '外部阅片平台';
});

watch(
  () => props.source,
  () => void mountViewer(),
);

async function mountViewer() {
  adapter.value?.destroy();
  adapter.value = null;
  imageError.value = false;
  scale.value = 1;
  if (!supported.value) return;
  await nextTick();
  if (!viewerHost.value) return;
  loading.value = true;
  try {
    const nextAdapter = tiled.value ? new TiledWSIViewerAdapter() : new RegularImageViewerAdapter();
    await nextAdapter.mount(viewerHost.value);
    nextAdapter.open({ source: sourceValue.value });
    adapter.value = nextAdapter;
  } catch {
    imageError.value = true;
  } finally {
    loading.value = false;
  }
}

function zoom(delta: number) {
  adapter.value?.zoomBy(delta);
  scale.value = Math.min(8, Math.max(0.25, Number((scale.value + delta).toFixed(2))));
}

function reset() {
  adapter.value?.reset();
  scale.value = 1;
}

function onWheel(event: WheelEvent) {
  if (!supported.value || !adapter.value) return;
  event.preventDefault();
  zoom(event.deltaY > 0 ? -0.1 : 0.1);
}

function onFullscreenChange() {
  // OpenSeadragon may promote its own host element to fullscreen. The browser
  // fullscreen element, rather than the Vue root identity, is the source of truth.
  fullscreenActive.value = Boolean(document.fullscreenElement);
}

function fullscreen() {
  if (fullscreenActive.value || document.fullscreenElement) {
    fullscreenActive.value = false;
    adapter.value?.setFullScreen(false);
    void document.exitFullscreen?.();
    return;
  }
  fullscreenActive.value = true;
  if (tiled.value) {
    adapter.value?.setFullScreen(true);
  } else {
    const request = viewerRoot.value?.requestFullscreen?.();
    void request?.catch(() => {
      fullscreenActive.value = false;
    });
  }
}

function normalizedPoint(event: MouseEvent) {
  const host = viewerHost.value;
  if (!host) return null;
  const image = regular.value ? host.querySelector<HTMLElement>('.viewer-regular-image') : null;
  const imageBounds = image?.getBoundingClientRect();
  const bounds = imageBounds?.width && imageBounds.height ? imageBounds : host.getBoundingClientRect();
  if (!bounds || bounds.width <= 0 || bounds.height <= 0) return null;
  if (
    event.clientX < bounds.left ||
    event.clientX > bounds.right ||
    event.clientY < bounds.top ||
    event.clientY > bounds.bottom
  )
    return null;
  return {
    x: Number(Math.min(1, Math.max(0, (event.clientX - bounds.left) / bounds.width)).toFixed(6)),
    y: Number(Math.min(1, Math.max(0, (event.clientY - bounds.top) / bounds.height)).toFixed(6)),
    coordinateSystem: regular.value ? ('NORMALIZED_IMAGE' as const) : ('NORMALIZED_VIEWPORT' as const),
  };
}

function recordReviewPoint(event: MouseEvent) {
  if (reviewMode.value === 'NONE') return;
  const point = normalizedPoint(event);
  if (!point) return;
  if (reviewMode.value === 'POINT') {
    emit('annotation', { ...point, viewport: adapter.value?.getViewport() ?? null });
    reviewMode.value = 'NONE';
    return;
  }
  if (!measurementStart.value) {
    measurementStart.value = point;
    return;
  }
  const start = measurementStart.value;
  emit('measurement', {
    x1: start.x,
    y1: start.y,
    x2: point.x,
    y2: point.y,
    value: Number(Math.hypot(point.x - start.x, point.y - start.y).toFixed(6)),
    coordinateSystem: point.coordinateSystem,
    viewport: adapter.value?.getViewport() ?? null,
  });
  measurementStart.value = null;
  reviewMode.value = 'NONE';
}

function startAnnotation() {
  if (!supported.value || !adapter.value) return false;
  reviewMode.value = 'POINT';
  measurementStart.value = null;
  return true;
}

function startMeasurement() {
  if (!supported.value || !adapter.value) return false;
  reviewMode.value = 'MEASURE';
  measurementStart.value = null;
  return true;
}

async function captureCurrentView(): Promise<{
  capture: ViewerCapture;
  viewport: ViewerViewport | null;
} | null> {
  const capture = await adapter.value?.captureCurrentView();
  return capture ? { capture, viewport: adapter.value?.getViewport() ?? null } : null;
}

defineExpose({ startAnnotation, startMeasurement, captureCurrentView });

onMounted(() => {
  document.addEventListener('fullscreenchange', onFullscreenChange);
  void mountViewer();
});

onUnmounted(() => {
  document.removeEventListener('fullscreenchange', onFullscreenChange);
  adapter.value?.destroy();
});
</script>

<template>
  <section ref="viewerRoot" class="image-viewer" aria-label="数字切片阅片器">
    <header class="image-viewer-toolbar">
      <div>
        <strong>{{ label || '数字切片' }}</strong>
        <span>{{ modeLabel }}</span>
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
      ref="viewerHost"
      class="image-viewer-viewport image-viewer-host"
      :class="{ 'image-viewer-placeholder': !supported || imageError, 'viewer-loading': loading }"
      tabindex="0"
      @wheel="onWheel"
      @click="recordReviewPoint"
    >
      <div v-if="loading" class="viewer-empty-content" aria-live="polite">
        <span class="loading-spinner" aria-hidden="true"></span>
        <strong>正在打开切片</strong>
      </div>
      <div v-else-if="imageError" class="viewer-empty-content">
        <span class="viewer-placeholder-icon" aria-hidden="true">!</span>
        <strong>阅片器暂时无法打开</strong>
        <p>请检查本地切片资源，或使用外部阅片平台打开。</p>
      </div>
      <div v-else-if="!supported" class="viewer-empty-content">
        <span class="viewer-placeholder-icon" aria-hidden="true">◉</span>
        <strong>当前玻片暂无数字切片</strong>
        <p>仍可从材料列表切换其他玻片。</p>
      </div>
      <div v-if="reviewMode !== 'NONE'" class="viewer-review-prompt" role="status">
        {{
          reviewMode === 'POINT'
            ? '请在图像上点击标注位置'
            : measurementStart
              ? '请点击测量终点'
              : '请点击测量起点'
        }}
      </div>
    </div>
    <footer class="image-viewer-footer">
      <span>滚轮缩放 · 按住拖动平移 · {{ tiled ? '缩略导航器已启用' : '普通图像回退模式' }}</span>
      <a v-if="source" :href="source" target="_blank" rel="noreferrer">在外部阅片器打开 →</a>
    </footer>
  </section>
</template>
