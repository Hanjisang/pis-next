<script setup lang="ts">
import { businessTypeName } from '../uiText';

defineProps<{
  caseId: string;
  pathologyNo: string;
  patientReference: string;
  visitReference?: string | null;
  businessTypeCode: string;
  currentResponsibility?: string;
  reportStatus?: string;
  progress?: string;
  notice?: string;
}>();

const emit = defineEmits<{ openCase: [] }>();
</script>

<template>
  <header class="case-header case-header-compact" aria-label="病例固定上下文">
    <div class="case-header-main">
      <div class="case-header-kicker">
        <span class="status-dot success" aria-hidden="true"></span><span>当前病例</span
        ><span class="breadcrumb-separator">/</span
        ><span>{{ businessTypeName(businessTypeCode) }}</span>
      </div>
      <div class="case-title-line">
        <h2>{{ pathologyNo }}</h2>
        <span class="status-pill">{{ reportStatus || '处理中' }}</span>
      </div>
      <p class="case-patient-line">
        <strong>{{ patientReference }}</strong
        ><span v-if="visitReference">就诊 {{ visitReference }}</span
        ><span v-if="currentResponsibility">当前：{{ currentResponsibility }}</span
        ><span v-if="progress">材料 {{ progress }}</span>
      </p>
    </div>
    <div class="case-header-actions">
      <span v-if="notice" class="case-header-notice">{{ notice }}</span>
      <button class="secondary-button" type="button" @click="emit('openCase')">病例中心</button>
      <slot name="actions"></slot>
    </div>
  </header>
</template>
