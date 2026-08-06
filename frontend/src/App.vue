<script setup lang="ts">
import { computed } from 'vue';

const modules = [
  ['申请与病例', 'MOD-001'],
  ['标本与来源材料', 'MOD-002'],
  ['组织技术与蜡块玻片', 'MOD-003'],
  ['术中冰冻', 'MOD-004'],
  ['细胞病理', 'MOD-005'],
  ['分子病理', 'MOD-006'],
  ['外送检测与外部结果', 'MOD-007'],
  ['诊断与报告', 'MOD-008'],
  ['多模态诊断关联', 'MOD-009'],
  ['数字材料', 'MOD-010'],
  ['出站集成与对账', 'MOD-011'],
  ['质量、异常与受控纠错', 'MOD-012'],
  ['授权、代理与审计', 'MOD-013'],
  ['归档、销毁与恢复', 'MOD-014'],
  ['报告呈现与医院配置', 'MOD-015'],
] as const;

const moduleCount = computed(() => modules.length);
</script>

<template>
  <main class="shell">
    <section class="hero">
      <div>
        <p class="eyebrow">PATHOLOGY INFORMATION SYSTEM</p>
        <h1>PIS Next</h1>
        <p class="lede">生产级病理信息系统的工程基础已经就绪。</p>
      </div>
      <div class="phase-badge" aria-label="当前阶段">P13 · FOUNDATION</div>
    </section>

    <section class="summary-grid" aria-label="工程基础摘要">
      <article class="summary-card accent">
        <span>模块边界</span>
        <strong>{{ moduleCount }}</strong>
        <small>Spring Modulith 逻辑模块</small>
      </article>
      <article class="summary-card">
        <span>追溯原则</span>
        <strong>不可覆盖</strong>
        <small>事实、版本与审计分离</small>
      </article>
      <article class="summary-card">
        <span>运行方式</span>
        <strong>模块化单体</strong>
        <small>为受控协作保留边界</small>
      </article>
    </section>

    <section class="module-panel">
      <div class="section-heading">
        <div>
          <p class="eyebrow">P10 ARCHITECTURE</p>
          <h2>15 个责任模块</h2>
        </div>
        <span class="status-dot">边界已验证</span>
      </div>
      <div class="module-grid">
        <article v-for="([name, code], index) in modules" :key="code" class="module-card">
          <span class="module-index">{{ String(index + 1).padStart(2, '0') }}</span>
          <div>
            <h3>{{ name }}</h3>
            <p>{{ code }}</p>
          </div>
        </article>
      </div>
    </section>

    <footer>
      <span>PIS Next · Clean-room design</span>
      <span>仅使用合成数据 · 当前未实现业务流程</span>
    </footer>
  </main>
</template>
