<script setup lang="ts">
import { computed, ref } from 'vue';

import {
  createV2Case,
  registerV2Specimen,
  softDeleteV2Specimen,
  updateV2Specimen,
  type V2CaseResult,
  type V2SpecimenResult,
} from '../v2Api';

const sourceSystemCode = ref('SYNTH-HIS');
const externalApplicationId = ref('APP-I01-001');
const applicationItemCode = ref('SYNTH-HISTOLOGY');
const patientReference = ref('SYNTH-PATIENT-001');
const visitReference = ref('SYNTH-VISIT-001');
const specimenCode = ref('A');
const specimenKindCode = ref('TISSUE');
const sourceKindCode = ref('LOCAL');
const sourceReference = ref('SYNTH-SOURCE-001');
const collectionSite = ref('synthetic site');
const collectionMethodCode = ref('SURGICAL');
const labelCode = ref('SYNTH-LABEL-001');
const busy = ref(false);
const errorMessage = ref('');
const notice = ref('');
const pathologyCase = ref<V2CaseResult | null>(null);
const specimen = ref<V2SpecimenResult | null>(null);

const canRegisterSpecimen = computed(() => pathologyCase.value !== null && !busy.value);
const canModifySpecimen = computed(
  () => specimen.value !== null && !specimen.value.deletedAt && !busy.value,
);

async function run(action: () => Promise<void>) {
  busy.value = true;
  errorMessage.value = '';
  notice.value = '';
  try {
    await action();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '请求失败';
  } finally {
    busy.value = false;
  }
}

function submitCase() {
  void run(async () => {
    pathologyCase.value = await createV2Case({
      sourceSystemCode: sourceSystemCode.value,
      externalApplicationId: externalApplicationId.value,
      applicationItemCode: applicationItemCode.value,
      patientReference: patientReference.value,
      visitReference: visitReference.value,
      idempotencyKey: `v2-case-${externalApplicationId.value}`,
    });
    notice.value = `V2病例 ${pathologyCase.value.caseNo} 已建立；Case 生命周期只有 ACTIVE/CANCELLED。`;
  });
}

function submitSpecimen() {
  if (!pathologyCase.value) return;
  void run(async () => {
    specimen.value = await registerV2Specimen({
      caseId: pathologyCase.value!.caseId,
      specimenCode: specimenCode.value,
      specimenKindCode: specimenKindCode.value,
      sourceKindCode: sourceKindCode.value,
      sourceReference: sourceReference.value,
      collectionSite: collectionSite.value,
      collectionMethodCode: collectionMethodCode.value,
      labelCode: labelCode.value,
      idempotencyKey: `v2-specimen-${sourceReference.value}`,
    });
    notice.value = `V2标本 ${specimen.value.specimenNo} 已登记；标本事实可继续修改或软删除。`;
  });
}

function submitUpdate() {
  if (!specimen.value) return;
  void run(async () => {
    specimen.value = await updateV2Specimen({
      specimenId: specimen.value!.specimenId,
      specimenCode: specimenCode.value,
      specimenKindCode: specimenKindCode.value,
      sourceKindCode: sourceKindCode.value,
      sourceReference: sourceReference.value,
      collectionSite: collectionSite.value,
      collectionMethodCode: collectionMethodCode.value,
      labelCode: labelCode.value,
      expectedVersion: specimen.value!.concurrencyVersion,
    });
    notice.value = '标本事实已修改，并发版本已递增。';
  });
}

function submitSoftDelete() {
  if (!specimen.value) return;
  void run(async () => {
    specimen.value = await softDeleteV2Specimen({
      specimenId: specimen.value!.specimenId,
      expectedVersion: specimen.value!.concurrencyVersion,
      reason: 'synthetic correction',
    });
    notice.value = '标本已软删除，原记录和删除原因仍可追溯。';
  });
}
</script>

<template>
  <section class="workbench" aria-label="PIS V2 登记与标本工作台">
    <div class="section-heading">
      <div>
        <p class="eyebrow">PIS V2 · V2-I01A</p>
        <h2>登记与标本事实</h2>
      </div>
      <span class="status-dot">独立 V2 API · 不维护病例/标本流程状态机</span>
    </div>

    <p v-if="errorMessage" class="error-banner" role="alert">{{ errorMessage }}</p>
    <p v-if="notice" class="success-banner" role="status">{{ notice }}</p>

    <div class="workflow-grid">
      <form class="business-card" @submit.prevent="submitCase">
        <span class="step-label">V2-01 · 建案</span>
        <h3>按申请项目建立 ACTIVE 病例</h3>
        <label>来源系统<input v-model="sourceSystemCode" required /></label>
        <label>外部申请标识<input v-model="externalApplicationId" required /></label>
        <label>申请项目<input v-model="applicationItemCode" required /></label>
        <label>患者上下文引用<input v-model="patientReference" required /></label>
        <label>就诊上下文引用<input v-model="visitReference" /></label>
        <button :disabled="busy" type="submit">建立 V2 病例</button>
        <output v-if="pathologyCase">
          {{ pathologyCase.caseNo }} · {{ pathologyCase.lifecycleStateCode }}
        </output>
      </form>

      <form class="business-card" @submit.prevent="submitSpecimen">
        <span class="step-label">V2-02 · 标本事实</span>
        <h3>建立可修改的独立标本</h3>
        <label>同病例标本代码<input v-model="specimenCode" required /></label>
        <label>标本类型<input v-model="specimenKindCode" required /></label>
        <label>来源类型<input v-model="sourceKindCode" required /></label>
        <label>来源引用<input v-model="sourceReference" required /></label>
        <label>来源部位<input v-model="collectionSite" required /></label>
        <label>采集方式<input v-model="collectionMethodCode" required /></label>
        <label>技术标签<input v-model="labelCode" /></label>
        <button :disabled="!canRegisterSpecimen" type="submit">登记 V2 标本</button>
        <output v-if="specimen">
          {{ specimen.specimenNo }} · {{ specimen.specimenCode }} · 版本
          {{ specimen.concurrencyVersion }}
        </output>
      </form>

      <form class="business-card scan-card" @submit.prevent="submitUpdate">
        <span class="step-label">V2-03 · 修改/软删除</span>
        <h3>修改事实或软删除</h3>
        <p class="muted">不使用 RECEIVED、PROCESSING、COMPLETED 等标本流程状态。</p>
        <button :disabled="!canModifySpecimen" type="submit">修改标本事实</button>
        <button :disabled="!canModifySpecimen" type="button" @click="submitSoftDelete">
          软删除标本
        </button>
        <output v-if="specimen">
          {{ specimen.deletedAt ? '已软删除' : '当前记录保留' }} · 版本
          {{ specimen.concurrencyVersion }}
        </output>
      </form>
    </div>
  </section>
</template>
