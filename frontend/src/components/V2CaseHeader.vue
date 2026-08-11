<script setup lang="ts">
import { businessTypeName } from '../uiText';

defineProps<{
  caseId: string;
  pathologyNo: string;
  patientReference: string;
  visitReference?: string | null;
  sex?: string | null;
  age?: string | number | null;
  businessTypeCode: string;
  currentResponsibility?: string;
  currentWork?: string;
  reportStatus?: string;
  progress?: string;
  notice?: string;
}>();

const emit = defineEmits<{ openCase: [] }>();
</script>

<template>
  <header class="case-header case-header-compact" aria-label="病例固定上下文">
    <div class="case-header-main">
      <button class="case-back-link" type="button" @click="emit('openCase')">← 返回</button>
      <div class="case-title-line">
        <h2>{{ pathologyNo }}</h2>
        <span class="case-header-type">{{ businessTypeName(businessTypeCode) }}</span>
      </div>
      <p class="case-patient-line">
        <strong>{{ patientReference }}</strong>
        <span v-if="visitReference">就诊 {{ visitReference }}</span>
        <span>性别 {{ sex || '待补充' }}</span>
        <span>年龄 {{ age || '待补充' }}</span>
        <span>当前：{{ currentWork || currentResponsibility || '处理中' }}</span>
      </p>
    </div>
    <div class="case-header-actions">
      <span v-if="progress" class="case-header-progress">材料 {{ progress }}</span>
      <span v-if="reportStatus" class="status-pill">{{ reportStatus }}</span>
      <span v-if="notice" class="case-header-notice">{{ notice }}</span>
      <slot name="actions"></slot>
    </div>
  </header>
</template>
