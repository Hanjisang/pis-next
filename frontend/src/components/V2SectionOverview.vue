<script setup lang="ts">
import { computed } from 'vue';

import type { V2RouteName } from '../navigation';

const props = defineProps<{ section: V2RouteName }>();
const emit = defineEmits<{ navigate: [path: string]; openSearch: [] }>();

const content = computed(() => {
  if (props.section === 'search') {
    return {
      title: '查询',
      description: '查询结果进入病例、材料或报告上下文，不建立独立查询孤岛。',
      action: '打开全局查询',
      groups: ['病例与患者', '标本、蜡块与玻片', '技术医嘱与报告'],
    };
  }
  if (props.section === 'configuration') {
    return {
      title: '配置中心',
      description: '按业务目的组织配置，医院差异通过 Profile、配置和适配器实现。',
      action: '',
      groups: ['业务配置', '诊断配置', '生产配置', '组织权限', '质控与报表'],
    };
  }
  if (props.section === 'system') {
    return {
      title: '系统管理',
      description: '管理用户、角色、数据权限和运行状态。',
      action: '',
      groups: ['用户与医疗人员身份', '角色与权限', '医院与院区', '审计与运行状态'],
    };
  }
  return {
    title: '报告工作台',
    description: '报告预览、签发和历史在诊断工作区中完成。',
    action: '进入诊断工作区',
    groups: ['待预览', '待签发', '已签发', '撤回与补充历史'],
  };
});

function primaryAction() {
  if (props.section === 'search') emit('openSearch');
  else emit('navigate', '/v2/diagnosis');
}
</script>

<template>
  <section class="section-overview" :aria-label="content.title">
    <header class="page-heading compact-heading">
      <div>
        <p class="section-kicker">任务中心</p>
        <h2>{{ content.title }}</h2>
        <p>{{ content.description }}</p>
      </div>
      <button v-if="content.action" class="primary-button" type="button" @click="primaryAction">
        {{ content.action }}
      </button>
    </header>
    <div class="configuration-groups">
      <button v-for="group in content.groups" :key="group" type="button">
        <span
          ><strong>{{ group }}</strong
          ><small>查看与维护</small></span
        ><span aria-hidden="true">→</span>
      </button>
    </div>
  </section>
</template>
