<script setup lang="ts">
import { computed, ref } from 'vue';

type SearchResult = {
  id: string;
  caseId: string;
  resultKind: string;
  displayCode: string;
  summary: string;
};

const caseId = ref('');
const searchText = ref('');
const searchResults = ref<SearchResult[]>([]);
const loading = ref(false);
const error = ref('');

const navigation = [
  { label: '工作台', workspace: 'v2-home' },
  { label: '登记', workspace: 'v2-registration' },
  { label: '取材 / 制片', workspace: 'v2' },
  { label: '诊断', workspace: 'v2-diagnosis' },
  { label: '冰冻', workspace: 'frozen' },
  { label: '技术医嘱', workspace: 'v2-technical' },
  { label: '报告', workspace: 'v2-diagnosis' },
  { label: '归档借阅', workspace: 'custody' },
  { label: '数字切片', workspace: 'digital' },
  { label: '查询', anchor: 'search' },
  { label: '质控统计', workspace: 'quality' },
  { label: '配置', anchor: 'configuration' },
  { label: '系统管理', anchor: 'administration' },
];

const roleCards = [
  { role: '登记员', items: ['待登记申请', '今日登记'], tone: 'mint' },
  { role: '取材 / 技术', items: ['待取材', '待制片', '技术医嘱', '冰冻'], tone: 'amber' },
  {
    role: '诊断医生',
    items: ['公共池', '我的待诊', '待复诊', '待审核', '技术结果已返回', '已签发'],
    tone: 'blue',
  },
];

const searchHint = computed(() =>
  searchText.value.trim()
    ? `正在检索“${searchText.value.trim()}”`
    : '支持病理号、患者、标本、蜡块、切片、技术医嘱和报告',
);

function openWorkspace(workspace: string) {
  const query = new URLSearchParams({ workspace });
  if (caseId.value.trim()) query.set('caseId', caseId.value.trim());
  window.location.href = `?${query.toString()}`;
}

function scrollTo(anchor?: string) {
  if (anchor) document.getElementById(anchor)?.scrollIntoView({ behavior: 'smooth' });
}

async function search() {
  if (!searchText.value.trim()) {
    searchResults.value = [];
    return;
  }
  loading.value = true;
  error.value = '';
  try {
    const response = await fetch(`/api/v2/search?q=${encodeURIComponent(searchText.value.trim())}`);
    const body = (await response.json()) as SearchResult[] | { message?: string };
    if (!response.ok) throw new Error((body as { message?: string }).message ?? '查询失败');
    searchResults.value = body as SearchResult[];
  } catch (requestError) {
    error.value = requestError instanceof Error ? requestError.message : '查询失败';
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <section class="v2-home" aria-label="PIS V2 工作台">
    <nav class="v2-nav" aria-label="V2 一级导航">
      <button
        v-for="item in navigation"
        :key="item.label"
        class="v2-nav-item"
        type="button"
        @click="item.workspace ? openWorkspace(item.workspace) : scrollTo(item.anchor)"
      >
        {{ item.label }}
      </button>
    </nav>

    <div class="v2-home-heading">
      <div>
        <p class="eyebrow">PIS V2 · CORE OPERATIONS</p>
        <h2>今天，从待办开始</h2>
        <p class="muted">所有业务入口都进入 V2。病例、材料、诊断和报告在同一个追溯链中协作。</p>
      </div>
      <div class="v2-case-launcher">
        <label for="case-id">病例号 / 病例 ID</label>
        <div>
          <input id="case-id" v-model="caseId" placeholder="输入病例 ID 后打开诊断工作区" /><button
            type="button"
            @click="openWorkspace('v2-diagnosis')"
          >
            打开诊断
          </button>
        </div>
      </div>
    </div>

    <div class="role-grid" aria-label="角色待办">
      <article v-for="card in roleCards" :key="card.role" class="role-card" :class="card.tone">
        <p class="step-label">{{ card.role }}</p>
        <h3>我的工作</h3>
        <button
          v-for="item in card.items"
          :key="item"
          type="button"
          class="task-link"
          @click="
            openWorkspace(
              card.role === '诊断医生'
                ? 'v2-diagnosis'
                : card.role === '取材 / 技术'
                  ? 'v2'
                  : 'v2-registration',
            )
          "
        >
          <span>{{ item }}</span
          ><span aria-hidden="true">→</span>
        </button>
      </article>
    </div>

    <div class="v2-home-panels">
      <section id="search" class="home-panel">
        <div class="panel-heading">
          <div>
            <p class="step-label">Ctrl + K</p>
            <h3>全局查询</h3>
          </div>
          <span class="status-pill">进入病例上下文</span>
        </div>
        <form class="search-row" @submit.prevent="search">
          <input
            v-model="searchText"
            aria-label="全局查询"
            placeholder="病理号、患者、标本、蜡块、切片、报告…"
          /><button type="submit" :disabled="loading">{{ loading ? '查询中…' : '查询' }}</button>
        </form>
        <p class="muted">{{ searchHint }}</p>
        <p v-if="error" class="error-banner" role="alert">{{ error }}</p>
        <div v-if="searchResults.length" class="search-results">
          <button
            v-for="result in searchResults"
            :key="`${result.resultKind}-${result.id}`"
            type="button"
            class="search-result"
            @click="
              caseId = result.caseId;
              openWorkspace(
                result.resultKind === 'DIAGNOSIS' || result.resultKind === 'REPORT'
                  ? 'v2-diagnosis'
                  : 'v2',
              );
            "
          >
            <strong>{{ result.displayCode }}</strong
            ><span>{{ result.resultKind }}</span
            ><small>{{ result.summary }}</small>
          </button>
        </div>
      </section>
      <section id="quality" class="home-panel metric-panel">
        <div class="panel-heading">
          <div>
            <p class="step-label">事实监控</p>
            <h3>质控与统计</h3>
          </div>
          <span class="status-pill">不阻塞签发</span>
        </div>
        <div class="metric-list">
          <span>常规 / 冰冻 TAT</span><strong>进入质控工作区</strong><span>撤回率 / 重打率</span
          ><strong>保留异常事实</strong>
        </div>
      </section>
    </div>

    <div id="custody" class="v2-system-strip">
      <span><strong>冰冻</strong>多轮快速诊断，结束后自动生成新的常规病例</span
      ><span><strong>材料去向</strong>归档位置与借阅状态分别追踪</span
      ><span><strong>数字切片</strong>绑定元数据，不阻塞物理制片</span>
    </div>
  </section>
</template>
