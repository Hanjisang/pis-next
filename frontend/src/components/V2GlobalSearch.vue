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

const props = defineProps<{ open: boolean }>();
const emit = defineEmits<{
  close: [];
  navigate: [path: string];
}>();

const query = ref('');
const results = ref<SearchResult[]>([]);
const loading = ref(false);
const error = ref('');
const searchInput = ref<HTMLInputElement | null>(null);

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

async function search() {
  if (!query.value.trim()) {
    results.value = [];
    return;
  }
  loading.value = true;
  error.value = '';
  try {
    const response = await fetch(`/api/v2/search?q=${encodeURIComponent(query.value.trim())}`);
    const body = (await response.json()) as SearchResult[] | { message?: string };
    if (!response.ok) throw new Error((body as { message?: string }).message ?? '查询失败');
    results.value = body as SearchResult[];
  } catch (requestError) {
    error.value = friendlyError(requestError, '查询失败，请检查关键词后重试。');
  } finally {
    loading.value = false;
  }
}

function openResult(result: SearchResult) {
  const target = ['DIAGNOSIS', 'REPORT'].includes(result.resultKind)
    ? `/v2/diagnosis/${encodeURIComponent(result.caseId)}`
    : `/v2/search?caseId=${encodeURIComponent(result.caseId)}`;
  emit('navigate', target);
  emit('close');
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && props.open) emit('close');
}

onMounted(() => window.addEventListener('keydown', handleKeydown));
onUnmounted(() => window.removeEventListener('keydown', handleKeydown));
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
      </form>

      <p v-if="error" class="feedback error" role="alert">{{ error }}</p>
      <div v-else-if="loading" class="search-skeleton" aria-label="正在查询">
        <span></span><span></span><span></span>
      </div>
      <div v-else-if="query.trim() && !results.length" class="empty-state compact">
        <strong>没有找到“{{ query.trim() }}”</strong>
        <span>请检查病理号、患者姓名或材料编号。</span>
      </div>
      <div v-else-if="groupedResults.length" class="search-result-groups">
        <section v-for="[group, items] in groupedResults" :key="group" class="search-result-group">
          <h3>
            {{ group }} <span>{{ items.length }}</span>
          </h3>
          <button
            v-for="result in items"
            :key="`${result.resultKind}-${result.id}`"
            type="button"
            class="search-result-row"
            @click="openResult(result)"
          >
            <span>
              <strong>{{ result.displayCode }}</strong>
              <small>{{ result.summary }}</small>
            </span>
            <span class="result-meta"
              >{{ result.resultKind === 'REPORT' ? '查看报告' : '打开病例' }} →</span
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
