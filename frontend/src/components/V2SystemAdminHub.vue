<script setup lang="ts">
const groups = [
  {
    title: '用户与身份',
    description: '维护登录账号与病理业务身份的对应关系。',
  },
  {
    title: '角色与权限',
    description: '管理业务权限、数据范围和可执行操作。',
  },
  {
    title: '医院与院区',
    description: '查看当前医院、院区、科室和组织范围。',
  },
  {
    title: '审计与运行状态',
    description: '查看关键操作记录、接口状态和运行健康度。',
  },
];
</script>

<template>
  <section class="admin-hub-page" aria-label="系统管理">
    <header class="page-heading compact-heading">
      <div>
        <p class="section-kicker">系统管理</p>
        <h2>系统管理</h2>
        <p>管理用户、角色、数据权限和系统运行边界。</p>
      </div>
    </header>
    <div class="configuration-groups">
      <article v-for="group in groups" :key="group.title" class="admin-hub-card">
        <p class="section-kicker">管理范围</p>
        <h3>{{ group.title }}</h3>
        <p>{{ group.description }}</p>
        <button class="secondary-button" type="button">打开管理</button>
      </article>
    </div>
  </section>
</template>
