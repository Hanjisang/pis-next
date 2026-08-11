<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';

import { friendlyError } from '../uiText';

type SearchResult = {
  id: string;
  caseId: string;
  resultKind: string;
  displayCode: string;
  summary: string;
};

const props = defineProps<{ open: boolean; returnPath?: string }>();
const emit = defineEmits<{ close: []; navigate: [path: string] }>();

const query = ref('');
const results = ref<SearchResult[]>([]);
const loading = ref(false);
const error = ref('');
const selectedIndex = ref(-1);
const searchInput = ref<HTMLInputElement | null>(null);
let debounceTimer: number | undefined;
let requestSequence = 0;

const groupedResults = computed(() => {
  const groups = new Map<string, SearchResult[]>();
  for (const result of results.value) {
    const label =
      {
        CASE: '病例',
        PATIENT: '患者',
        SPECIMEN: '标本',
        BLOCK: '蜡块',
        SLIDE: '玻片',
        DIAGNOSIS: '诊断',
        REPORT: '报告',
        TECHNICAL_ORDER: '技术医嘱',
      }[result.resultKind] ?? '其他';
    groups.set(label, [...(groups.get(label) ?? []), result]);
  }
  return [...groups.entries()];
});

watch(
  () => props.open,
  async (open) => {
    if (!open) return;
    await nextTick();
    searchInput.value?.focus();
  },
);

watch(query, () => {
  window.clearTimeout(debounceTimer);
  selectedIndex.value = -1;
  debounceTimer = window.setTimeout(() => void search(), 250);
});

async function search() {
  const value = query.value.trim();
  if (!value) {
    results.value = [];
    error.value = '';
    return;
  }
  const sequence = ++requestSequence;
  loading.value = true;
  error.value = '';
  try {
    const response = await fetch(`/api/v2/search?q=${encodeURIComponent(value)}`);
    const body = (await response.json()) as SearchResult[] | { message?: string };
    if (!response.ok) throw new Error((body as { message?: string }).message ?? '查询失败');
    if (sequence === requestSequence) results.value = body as SearchResult[];
  } catch (requestError) {
    if (sequence === requestSequence)
      error.value = friendlyError(requestError, '查询失败，请稍后重试');
  } finally {
    if (sequence === requestSequence) loading.value = false;
  }
}

function targetForResult(result: SearchResult) {
  const caseId = encodeURIComponent(result.caseId);
  const query = new URLSearchParams({ origin: 'search' });
  if (props.returnPath?.startsWith('/v2/')) query.set('returnTo', props.returnPath);
  return `/v2/cases/${caseId}?${query.toString()}`;
}

function openResult(result: SearchResult) {
  emit('navigate', targetForResult(result));
  emit('close');
}

function flattenIndex(groupIndex: number, itemIndex: number) {
  return (
    groupedResults.value
      .slice(0, groupIndex)
      .reduce((total, [, items]) => total + items.length, 0) + itemIndex
  );
}

function handleKeydown(event: KeyboardEvent) {
  if (!props.open) return;
  if (event.key === 'Escape') {
    event.preventDefault();
    emit('close');
    return;
  }
  if (!results.value.length) return;
  if (event.key === 'ArrowDown') {
    event.preventDefault();
    selectedIndex.value = (selectedIndex.value + 1) % results.value.length;
  } else if (event.key === 'ArrowUp') {
    event.preventDefault();
    selectedIndex.value =
      selectedIndex.value <= 0 ? results.value.length - 1 : selectedIndex.value - 1;
  } else if (event.key === 'Enter' && selectedIndex.value >= 0) {
    event.preventDefault();
    openResult(results.value[selectedIndex.value]);
  }
}

onMounted(() => window.addEventListener('keydown', handleKeydown));
onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown);
  window.clearTimeout(debounceTimer);
});
</script>

<template>
  <div v-if="open" class="drawer-backdrop" @click.self="emit('close')">
    <aside class="search-drawer" aria-label="全局查询" role="dialog" aria-modal="true">
      <header class="drawer-header">
        <div>
          <p class="section-kicker">快速定位</p>
          <h2>全局查询</h2>
        </div>
        <button class="icon-button" type="button" aria-label="关闭全局查询" @click="emit('close')">
          ×
        </button>
      </header>
      <form class="global-search-form" @submit.prevent="search">
        <label for="global-search-input">病理号、患者、申请号或材料编号</label>
        <div class="input-action-row">
          <input
            id="global-search-input"
            ref="searchInput"
            v-model="query"
            autocomplete="off"
            placeholder="例如 P20260001、A1-HE"
          />
          <button class="primary-button" type="submit" :disabled="loading">
            {{ loading ? '查询中…' : '查询' }}
          </button>
        </div>
        <small class="search-hint">↑ ↓ 选择 · Enter 打开 · Esc 关闭</small>
      </form>
      <p v-if="error" class="feedback error" role="alert">{{ error }}</p>
      <div v-else-if="loading" class="search-skeleton" aria-label="正在查询">
        <span></span><span></span><span></span>
      </div>
      <div v-else-if="query.trim() && !results.length" class="empty-state compact">
        <strong>没有找到“{{ query.trim() }}”</strong><span>请检查病理号、患者姓名或材料编号。</span>
      </div>
      <div v-else-if="groupedResults.length" class="search-result-groups">
        <section
          v-for="([group, items], groupIndex) in groupedResults"
          :key="group"
          class="search-result-group"
        >
          <h3>
            {{ group }} <span>{{ items.length }}</span>
          </h3>
          <button
            v-for="(result, itemIndex) in items"
            :key="`${result.resultKind}-${result.id}`"
            type="button"
            class="search-result-row"
            :class="{ selected: selectedIndex === flattenIndex(groupIndex, itemIndex) }"
            @mouseenter="selectedIndex = flattenIndex(groupIndex, itemIndex)"
            @click="openResult(result)"
          >
            <span
              ><strong>{{ result.displayCode }}</strong
              ><small>{{ result.summary }}</small></span
            >
            <span class="result-meta"
              >{{
                result.resultKind === 'REPORT'
                  ? '查看报告'
                  : result.resultKind === 'SLIDE'
                    ? '打开阅片'
                    : result.resultKind === 'PATIENT'
                      ? '查看患者历史'
                      : '打开上下文'
              }}
              →</span
            >
          </button>
        </section>
      </div>
      <div v-else class="search-help">
        <p>可查询</p>
        <span>病理号</span><span>患者</span><span>申请号</span><span>标本</span><span>蜡块</span
        ><span>玻片</span><span>技术医嘱</span><span>报告</span>
      </div>
    </aside>
  </div>
</template>
