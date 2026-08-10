<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue';

import { friendlyError, formatDateTime } from '../uiText';
import { getV2CaseWorkspace, type V2WorkspaceTimelineEntry } from '../v2WorkspaceApi';

type HistoryCategory =
  | 'ALL'
  | 'REGISTRATION'
  | 'MATERIAL'
  | 'TECHNICAL'
  | 'DIAGNOSIS'
  | 'REPORT'
  | 'ARCHIVE'
  | 'INTEGRATION'
  | 'SYSTEM';

const props = withDefaults(
  defineProps<{
    open: boolean;
    caseId?: string;
    entries?: V2WorkspaceTimelineEntry[];
    targetId?: string | null;
    title?: string;
    targetLabel?: string;
  }>(),
  { caseId: '', entries: undefined, targetId: null, title: '历史记录', targetLabel: '' },
);

const emit = defineEmits<{ close: [] }>();
const category = ref<HistoryCategory>('ALL');
const loading = ref(false);
const error = ref('');
const loadedEntries = ref<V2WorkspaceTimelineEntry[]>([]);

const categories: Array<{ code: HistoryCategory; label: string }> = [
  { code: 'ALL', label: '全部' },
  { code: 'REGISTRATION', label: '登记' },
  { code: 'MATERIAL', label: '材料' },
  { code: 'TECHNICAL', label: '技术' },
  { code: 'DIAGNOSIS', label: '诊断' },
  { code: 'REPORT', label: '报告' },
  { code: 'ARCHIVE', label: '归档' },
  { code: 'INTEGRATION', label: '接口' },
  { code: 'SYSTEM', label: '系统' },
];

const entries = computed(() => (props.entries?.length ? props.entries : loadedEntries.value));
const filteredEntries = computed(() =>
  entries.value.filter((entry) => {
    if (props.targetId && entry.targetId !== props.targetId) return false;
    if (category.value === 'ALL') return true;
    return classify(entry) === category.value;
  }),
);

function targetKindLabel(kind: string | null) {
  if (!kind) return '';
  const labels: Record<string, string> = {
    'V2-CASE': '病例',
    'V2-APPLICATION': '申请',
    'V2-SPECIMEN': '标本',
    'V2-GROSSING': '取材记录',
    'V2-BLOCK': '蜡块',
    'V2-SLIDE': '玻片',
    'V2-DIGITAL-SLIDE': '数字切片',
    'V2-DIAGNOSIS': '诊断',
    'V2-RESPONSIBILITY': '责任链',
    'V2-TECHNICAL-ORDER': '技术医嘱',
    'V2-TECHNICAL-ORDER-ITEM': '技术医嘱项目',
    'V2-REPORT': '报告',
    'V2-FROZEN-ROUND': '冰冻轮次',
    'V2-ARCHIVE-ITEM': '归档材料',
    'V2-LOAN': '借阅记录',
  };
  return labels[kind] ?? '业务对象';
}

function classify(entry: V2WorkspaceTimelineEntry): HistoryCategory {
  const code = `${entry.operationCode} ${entry.targetKind ?? ''}`.toUpperCase();
  if (code.includes('REGISTRATION') || code.includes('CASE-CREATE') || code.includes('SPECIMEN'))
    return 'REGISTRATION';
  if (
    code.includes('MATERIAL') ||
    code.includes('GROSS') ||
    code.includes('BLOCK') ||
    code.includes('SLIDE') ||
    code.includes('PRINT')
  )
    return 'MATERIAL';
  if (code.includes('TECHNICAL') || code.includes('HISTOLOGY')) return 'TECHNICAL';
  if (code.includes('DIAGNOSIS') || code.includes('RESPONSIBILITY')) return 'DIAGNOSIS';
  if (
    code.includes('REPORT') ||
    code.includes('SIGN') ||
    code.includes('WITHDRAW') ||
    code.includes('SUPPLEMENT')
  )
    return 'REPORT';
  if (
    code.includes('ARCHIVE') ||
    code.includes('LOAN') ||
    code.includes('CUSTODY') ||
    code.includes('DESTRUCTION')
  )
    return 'ARCHIVE';
  if (
    code.includes('INTEGRATION') ||
    code.includes('HIS') ||
    code.includes('LIS') ||
    code.includes('EMR')
  )
    return 'INTEGRATION';
  return 'SYSTEM';
}

async function load() {
  if (!props.open || !props.caseId || props.entries?.length) return;
  loading.value = true;
  error.value = '';
  try {
    const workspace = await getV2CaseWorkspace(props.caseId);
    loadedEntries.value = workspace.timeline ?? [];
  } catch (requestError) {
    error.value = friendlyError(requestError, '历史记录暂时无法加载，请稍后重试。');
  } finally {
    loading.value = false;
  }
}

function closeOnEscape(event: KeyboardEvent) {
  if (event.key === 'Escape' && props.open) emit('close');
}

watch(
  () => props.open,
  (open) => {
    if (open) {
      category.value = 'ALL';
      void load();
      window.addEventListener('keydown', closeOnEscape);
    } else {
      window.removeEventListener('keydown', closeOnEscape);
    }
  },
  { immediate: true },
);

onUnmounted(() => window.removeEventListener('keydown', closeOnEscape));
</script>

<template>
  <div v-if="open" class="history-drawer-backdrop" @click.self="emit('close')">
    <aside class="history-drawer" aria-label="历史记录" role="dialog" aria-modal="true">
      <header class="history-drawer-header">
        <div>
          <p class="section-kicker">{{ targetLabel || '业务追溯' }}</p>
          <h2>{{ title }}</h2>
        </div>
        <button class="icon-button" type="button" aria-label="关闭历史记录" @click="emit('close')">
          ×
        </button>
      </header>
      <nav class="history-category-nav" aria-label="历史分类">
        <button
          v-for="item in categories"
          :key="item.code"
          type="button"
          :class="{ active: category === item.code }"
          @click="category = item.code"
        >
          {{ item.label }}
          <span v-if="item.code === 'ALL'">{{ entries.length }}</span>
        </button>
      </nav>
      <p v-if="error" class="feedback error" role="alert">{{ error }}</p>
      <div v-else-if="loading" class="history-loading" aria-label="历史加载中">
        <span></span><span></span><span></span>
      </div>
      <ol v-else-if="filteredEntries.length" class="history-event-list">
        <li v-for="entry in filteredEntries" :key="entry.eventId" class="history-event-item">
          <time>{{ formatDateTime(entry.occurredAt) }}</time>
          <div class="history-event-body">
            <div class="history-event-title">
              <strong>{{ entry.title }}</strong
              ><span>{{ entry.actorName || entry.actorRef }}</span>
            </div>
            <p v-if="entry.detail">{{ entry.detail }}</p>
            <dl v-if="entry.targetKind || entry.targetId" class="history-event-target">
              <div v-if="entry.targetKind">
                <dt>对象</dt>
                <dd>{{ targetKindLabel(entry.targetKind) }}</dd>
              </div>
              <div v-if="entry.targetId">
                <dt>编号</dt>
                <dd>{{ entry.targetId }}</dd>
              </div>
            </dl>
          </div>
        </li>
      </ol>
      <div v-else class="empty-state compact-empty">
        <strong>暂无历史记录</strong><span>当前筛选条件下没有可显示的业务事实。</span>
      </div>
    </aside>
  </div>
</template>
