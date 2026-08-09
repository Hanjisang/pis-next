<script setup lang="ts">
const groups = [
  {
    title: '业务配置',
    description: '申请项目、业务类型和病理号规则',
    items: ['业务类型', '申请项目映射', '病理号规则'],
  },
  {
    title: '诊断配置',
    description: '诊断模板、报告模板和技术项目',
    items: ['诊断模板', '报告模板', '技术项目'],
  },
  {
    title: '生产配置',
    description: '玻片规则、标签打印和设备适配',
    items: ['玻片规则', '打印规则', '设备'],
  },
  {
    title: '组织权限',
    description: '用户、角色和数据范围',
    items: ['用户与医疗人员身份', '角色与权限', '院区与科室'],
  },
  {
    title: '质控与报表',
    description: '质控规则、统计口径和报表扩展',
    items: ['质控规则', '内置统计', '外挂报表'],
  },
];
</script>

<template>
  <section class="admin-hub-page" aria-label="配置中心">
    <header class="page-heading compact-heading">
      <div>
        <p class="section-kicker">配置</p>
        <h2>配置中心</h2>
        <p>按业务目的组织配置，医院差异通过配置和适配器实现。</p>
      </div>
    </header>
    <div class="configuration-groups">
      <article v-for="group in groups" :key="group.title" class="admin-hub-card">
        <div>
          <p class="section-kicker">配置分类</p>
          <h3>{{ group.title }}</h3>
          <p>{{ group.description }}</p>
        </div>
        <ul>
          <li v-for="item in group.items" :key="item">{{ item }}</li>
        </ul>
        <button class="secondary-button" type="button">查看配置</button>
      </article>
    </div>
  </section>
</template>
