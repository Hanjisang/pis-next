<script setup lang="ts">
import { computed, ref, watch } from 'vue';

import type { V2AuthUser } from '../auth';
import { businessTypeName, friendlyError, specimenKindName } from '../uiText';
import { getV2Case, type V2CaseResult } from '../v2Api';
import { getV2MaterialTree, type V2MaterialTree } from '../v2MaterialApi';

const props = defineProps<{ caseId: string; authUser?: V2AuthUser | null }>();
const emit = defineEmits<{ navigate: [path: string] }>();

const pathologyCase = ref<V2CaseResult | null>(null);
const materials = ref<V2MaterialTree | null>(null);
const loading = ref(false);
const error = ref('');

const blocks = computed(() => materials.value?.specimens.flatMap((item) => item.blocks) ?? []);
const slides = computed(
  () =>
    materials.value?.specimens.flatMap((specimen) => [
      ...specimen.directSlides,
      ...specimen.blocks.flatMap((block) => block.slides),
    ]) ?? [],
);
const permissions = computed(() => new Set(props.authUser?.permissions ?? []));
const supportsGrossing = computed(() =>
  ['HISTOLOGY', 'ROUTINE', 'FROZEN'].includes(pathologyCase.value?.businessTypeCode ?? ''),
);

watch(
  () => props.caseId,
  () => void load(),
  { immediate: true },
);

async function load() {
  if (!props.caseId) return;
  loading.value = true;
  error.value = '';
  try {
    [pathologyCase.value, materials.value] = await Promise.all([
      getV2Case(props.caseId),
      getV2MaterialTree(props.caseId),
    ]);
  } catch (requestError) {
    error.value = friendlyError(requestError, '病例上下文暂时无法加载，请返回查询后重试。');
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <section class="case-context-page" aria-label="病例上下文">
    <header class="page-heading compact-heading">
      <div>
        <p class="section-kicker">查询结果</p>
        <h2>{{ pathologyCase?.caseNo ?? '病例上下文' }}</h2>
        <p>查询结果直接落到病例和材料，不需要返回独立查询列表。</p>
      </div>
      <div v-if="pathologyCase" class="page-actions">
        <button
          v-if="
            pathologyCase.businessTypeCode === 'FROZEN' &&
            (permissions.has('P14-PERM-008') || permissions.has('P14-PERM-034'))
          "
          class="secondary-button"
          type="button"
          @click="emit('navigate', `/v2/frozen/${caseId}`)"
        >
          进入冰冻
        </button>
        <button
          v-if="permissions.has('P14-PERM-013') && supportsGrossing"
          class="secondary-button"
          type="button"
          @click="emit('navigate', `/v2/grossing/${caseId}`)"
        >
          进入取材
        </button>
        <button
          v-if="permissions.has('P14-PERM-014') && pathologyCase.businessTypeCode !== 'MOLECULAR'"
          class="secondary-button"
          type="button"
          @click="emit('navigate', `/v2/production/${caseId}`)"
        >
          进入制片
        </button>
        <button
          v-if="pathologyCase.businessTypeCode === 'MOLECULAR' && permissions.has('P14-PERM-014')"
          class="primary-button"
          type="button"
          @click="emit('navigate', `/v2/technical-orders/${caseId}`)"
        >
          录入分子结果
        </button>
        <button
          v-if="permissions.has('P14-PERM-034')"
          class="primary-button"
          type="button"
          @click="emit('navigate', `/v2/diagnosis/${caseId}`)"
        >
          进入诊断
        </button>
      </div>
    </header>

    <p v-if="error" class="feedback error" role="alert">{{ error }}</p>
    <div v-if="loading" class="list-skeleton"><span></span><span></span><span></span></div>
    <template v-else-if="pathologyCase && materials">
      <div class="case-context-bar" aria-label="病例摘要">
        <span
          ><small>病理号</small><strong>{{ pathologyCase.caseNo }}</strong></span
        >
        <span
          ><small>患者编号</small><strong>{{ pathologyCase.patientReference }}</strong></span
        >
        <span
          ><small>业务类型</small
          ><strong>{{ businessTypeName(pathologyCase.businessTypeCode) }}</strong></span
        >
        <span
          ><small>标本</small><strong>{{ materials.specimens.length }} 个</strong></span
        >
        <span
          ><small>蜡块</small><strong>{{ blocks.length }} 个</strong></span
        >
        <span
          ><small>玻片</small><strong>{{ slides.length }} 张</strong></span
        >
      </div>
      <section class="workspace-panel">
        <header class="panel-title-row">
          <div>
            <p class="section-kicker">材料</p>
            <h3>材料树</h3>
          </div>
          <span class="status-pill"
            >{{ materials.initialCompletedCount }}/{{ materials.initialRequiredCount }} 张完成</span
          >
        </header>
        <div v-if="materials.specimens.length" class="case-material-tree">
          <article v-for="specimen in materials.specimens" :key="specimen.specimenId">
            <header>
              <strong>标本 {{ specimen.specimenCode }}</strong
              ><span>{{ specimenKindName(specimen.specimenKindCode) }}</span>
            </header>
            <p v-if="!specimen.blocks.length && !specimen.directSlides.length" class="muted">
              尚未产生蜡块或玻片
            </p>
            <ul>
              <li v-for="block in specimen.blocks" :key="block.blockId">
                <strong>蜡块 {{ block.blockCode }}</strong
                ><span>{{
                  block.slides.map((slide) => slide.slideCode).join('、') || '尚无玻片'
                }}</span>
              </li>
              <li v-for="slide in specimen.directSlides" :key="slide.slideId">
                <strong>玻片 {{ slide.slideCode }}</strong
                ><span>标本直接制片</span>
              </li>
            </ul>
          </article>
        </div>
        <div v-else class="empty-state compact">
          <strong>当前病例没有有效材料</strong><span>请核对登记结果或业务类型。</span>
        </div>
      </section>
    </template>
  </section>
</template>
