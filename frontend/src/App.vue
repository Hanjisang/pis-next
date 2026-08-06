<script setup lang="ts">
import P15RegistrationWorkbench from './components/P15RegistrationWorkbench.vue';
import P16GrossingWorkbench from './components/P16GrossingWorkbench.vue';
</script>

<template>
  <main class="shell">
    <section class="hero">
      <div>
        <p class="eyebrow">PATHOLOGY INFORMATION SYSTEM</p>
        <h1>PIS Next</h1>
        <p class="lede">P15 登记与标本接收纵向工作台。</p>
      </div>
      <div class="phase-badge" aria-label="当前阶段">P15 · ACCESSION</div>
    </section>

    <section class="summary-grid" aria-label="P15 摘要">
      <article class="summary-card accent">
        <span>当前切片</span>
        <strong>登记 → 接收</strong>
        <small>申请、病例、预计标本和扫码核对</small>
      </article>
      <article class="summary-card">
        <span>追溯</span>
        <strong>追加事实</strong>
        <small>状态历史、交接、审计和发件箱同事务</small>
      </article>
      <article class="summary-card">
        <span>数据</span>
        <strong>合成数据</strong>
        <small>不显示真实患者正文或诊断报告</small>
      </article>
    </section>

    <P15RegistrationWorkbench />
    <P16GrossingWorkbench />

    <footer>
      <span>PIS Next · Clean-room design</span>
      <span>P15 已实现 · 下游取材、技术和报告尚未实现</span>
    </footer>
  </main>
</template>
