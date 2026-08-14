<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';

import { friendlyError } from '../uiText';
import { getOperationsOverview, type OperationsOverview } from '../v2BusinessOperationsApi';
import V2ClinicalOperations from './V2ClinicalOperations.vue';
import V2DepartmentOperations from './V2DepartmentOperations.vue';
import V2MigrationOperations from './V2MigrationOperations.vue';
import V2SpecialtyOperations from './V2SpecialtyOperations.vue';

type Module = 'department' | 'business' | 'specialty' | 'migration';
const module = ref<Module>('department');
const overview = ref<OperationsOverview>({});
const loading = ref(false);
const error = ref('');
const modules: Array<{ key: Module; label: string; description: string }> = [
  { key: 'department', label: '科室管理', description: '排班、质量、设备、耗材、采购和空间' },
  { key: 'business', label: '业务管理', description: '危急值、报告发放和外送物流' },
  { key: 'specialty', label: '专项业务', description: '分子、数字归档、区域共享和收入事实' },
  { key: 'migration', label: '数据迁移', description: '迁移任务、映射数量和失败记录' },
];
const active = computed(() => modules.find((item) => item.key === module.value) ?? modules[0]);

async function load() {
  loading.value = true;
  error.value = '';
  try {
    overview.value = await getOperationsOverview();
  } catch (requestError) {
    error.value = friendlyError(requestError, '管理数据加载失败。');
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  const requested = new URLSearchParams(window.location.search).get('module') as Module | null;
  if (requested && modules.some((item) => item.key === requested)) module.value = requested;
  void load();
});
</script>

<template>
  <section class="admin-hub-page business-operations-page" aria-label="业务运行管理">
    <header class="page-heading compact-heading">
      <div>
        <p class="section-kicker">管理后台</p>
        <h2>{{ active.label }}</h2>
        <p>{{ active.description }}</p>
      </div>
      <button class="secondary-button" type="button" :disabled="loading" @click="load">
        {{ loading ? '刷新中…' : '刷新' }}
      </button>
    </header>
    <p v-if="error" class="feedback error" role="alert">{{ error }}</p>
    <nav class="operations-module-nav" aria-label="运行管理模块">
      <button
        v-for="item in modules"
        :key="item.key"
        type="button"
        :class="{ active: module === item.key }"
        @click="module = item.key"
      >
        <strong>{{ item.label }}</strong
        ><small>{{ item.description }}</small>
      </button>
    </nav>
    <div v-if="loading && !Object.keys(overview).length" class="list-skeleton">
      <span></span><span></span><span></span>
    </div>
    <V2DepartmentOperations
      v-else-if="module === 'department'"
      :overview="overview"
      @changed="load"
    />
    <V2ClinicalOperations v-else-if="module === 'business'" :overview="overview" @changed="load" />
    <V2SpecialtyOperations
      v-else-if="module === 'specialty'"
      :overview="overview"
      @changed="load"
    />
    <V2MigrationOperations v-else :overview="overview" @changed="load" />
  </section>
</template>
