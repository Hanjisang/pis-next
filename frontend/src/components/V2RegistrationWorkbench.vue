<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';

import {
  createV2Case,
  getV2Case,
  getV2Specimen,
  registerV2Specimen,
  softDeleteV2Specimen,
  updateV2Specimen,
  type V2CaseResult,
  type V2SpecimenResult,
} from '../v2Api';
import {
  completeV2MolecularResult,
  registerV2ConsultationMaterial,
  type V2ConsultationMaterialResult,
  type V2MolecularResult,
} from '../v2BusinessApi';
import { completeV2Slide, createV2DirectCytologySlide, type V2SlideResult } from '../v2MaterialApi';

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
const directSlideCode = ref('C-1-1');
const directSlideType = ref('CYTOLOGY');
const directSlide = ref<V2SlideResult | null>(null);
const molecularResultCode = ref('PANEL-1');
const molecularResultData = ref('{"mutationDetected":false}');
const molecularResult = ref<V2MolecularResult | null>(null);
const externalReference = ref('EXT-SYNTH-HOSPITAL-001');
const externalBlockCode = ref('EXT-B1');
const externalBlockType = ref('EXTERNAL');
const localSlideCode = ref('LOCAL-S1');
const localSlideType = ref('HE');
const consultationMaterial = ref<V2ConsultationMaterialResult | null>(null);

const canRegisterSpecimen = computed(() => pathologyCase.value !== null && !busy.value);
const canModifySpecimen = computed(
  () => specimen.value !== null && !specimen.value.deletedAt && !busy.value,
);
const businessType = computed(() => pathologyCase.value?.businessTypeCode ?? '');
const isCytology = computed(() => businessType.value.startsWith('CYTOLOGY'));
const isMolecular = computed(() => businessType.value === 'MOLECULAR');
const isConsultation = computed(() => businessType.value === 'REFERRAL');

const query = new URLSearchParams(window.location.search);
const initialCaseId = query.get('caseId');
const initialSpecimenId = query.get('specimenId');

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

function openDiagnosis() {
  if (!pathologyCase.value) return;
  window.location.href = `?workspace=v2-diagnosis&caseId=${encodeURIComponent(pathologyCase.value.caseId)}`;
}

function createDirectSlide() {
  if (!pathologyCase.value || !specimen.value) return;
  void run(async () => {
    directSlide.value = await createV2DirectCytologySlide({
      caseId: pathologyCase.value!.caseId,
      specimenId: specimen.value!.specimenId,
      slideCode: directSlideCode.value,
      slideType: directSlideType.value,
      idempotencyKey: `v2-direct-slide-${pathologyCase.value!.caseId}-${directSlideCode.value}`,
    });
    notice.value = `直接切片 ${directSlide.value.slideCode} 已创建；未创建 Block。`;
  });
}

function completeDirectSlide() {
  if (!directSlide.value) return;
  void run(async () => {
    directSlide.value = await completeV2Slide({
      slideId: directSlide.value!.slideId,
      expectedVersion: directSlide.value!.concurrencyVersion,
      idempotencyKey: `v2-direct-slide-complete-${directSlide.value!.slideId}`,
    });
    notice.value = `直接切片 ${directSlide.value.slideCode} 已完成。`;
  });
}

function completeMolecular() {
  if (!pathologyCase.value || !molecularResultCode.value || !molecularResultData.value) return;
  void run(async () => {
    molecularResult.value = await completeV2MolecularResult({
      caseId: pathologyCase.value!.caseId,
      specimenId: specimen.value?.specimenId,
      resultCode: molecularResultCode.value,
      resultData: molecularResultData.value,
      idempotencyKey: `v2-molecular-result-${pathologyCase.value!.caseId}-${molecularResultCode.value}`,
    });
    notice.value = '结构化分子结果已回写当前 Molecular Case。';
  });
}

function registerConsultationMaterial() {
  if (!pathologyCase.value) return;
  void run(async () => {
    consultationMaterial.value = await registerV2ConsultationMaterial({
      caseId: pathologyCase.value!.caseId,
      externalReference: externalReference.value,
      specimenKindCode: 'TISSUE',
      blockCode: externalBlockCode.value,
      blockType: externalBlockType.value,
      operatorId: 'registrar',
      createLocalSlide: true,
      localSlideCode: localSlideCode.value,
      localSlideType: localSlideType.value,
      idempotencyKey: `v2-consultation-material-${pathologyCase.value!.caseId}-${externalReference.value}`,
    });
    notice.value = '外院 Block 已登记，并生成统一 V2 Local Slide。';
  });
}

function completeConsultationSlide() {
  const slideId = consultationMaterial.value?.slideId;
  if (!slideId) return;
  void run(async () => {
    await completeV2Slide({
      slideId,
      expectedVersion: 0,
      idempotencyKey: `v2-consultation-slide-complete-${slideId}`,
    });
    notice.value = '会诊 Local Slide 已完成，可进入诊断。';
  });
}

onMounted(() => {
  if (!initialCaseId) return;
  void run(async () => {
    pathologyCase.value = await getV2Case(initialCaseId);
    if (initialSpecimenId) specimen.value = await getV2Specimen(initialSpecimenId);
    notice.value = '已加载现有 V2 病例；可继续执行当前业务类型动作。';
  });
});
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
          {{ pathologyCase.caseNo }} · {{ pathologyCase.lifecycleStateCode }} · Case ID
          {{ pathologyCase.caseId }}
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
          {{ specimen.concurrencyVersion }} · Specimen ID {{ specimen.specimenId }}
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

    <section
      v-if="pathologyCase"
      class="workflow-grid extended-business-actions"
      aria-label="V2 其他业务类型闭环"
    >
      <article v-if="isCytology && specimen" class="business-card">
        <span class="step-label">CYTOLOGY</span>
        <h3>标本 → 直接切片</h3>
        <p class="muted">细胞病理允许 Specimen 直接产生 Slide，不强制创建 Block。</p>
        <label>切片编号<input v-model="directSlideCode" required /></label>
        <label>切片类型<input v-model="directSlideType" required /></label>
        <button :disabled="busy" type="button" @click="createDirectSlide">创建直接切片</button>
        <button
          v-if="directSlide"
          :disabled="busy || Boolean(directSlide.completedAt)"
          type="button"
          @click="completeDirectSlide"
        >
          完成直接切片
        </button>
        <output v-if="directSlide" role="status">
          {{ directSlide.slideCode }} · {{ directSlide.completedAt ? '已完成' : '待完成' }} · Block
          = none
        </output>
      </article>

      <article v-if="isMolecular" class="business-card">
        <span class="step-label">MOLECULAR</span>
        <h3>独立 Molecular Case → Structured Result</h3>
        <p class="muted">独立分子申请在当前 Case 内完成，不伪造 Grossing / Block / Slide。</p>
        <label>结果编码<input v-model="molecularResultCode" required /></label>
        <label>结果 JSON<textarea v-model="molecularResultData" rows="3" required /></label>
        <button
          :disabled="busy || Boolean(molecularResult)"
          type="button"
          @click="completeMolecular"
        >
          回写结构化结果
        </button>
        <output v-if="molecularResult" role="status">
          {{ molecularResult.resultCode }} · {{ molecularResult.statusCode }} ·
          {{ molecularResult.resultId }}
        </output>
      </article>

      <article v-if="isConsultation" class="business-card">
        <span class="step-label">CONSULTATION</span>
        <h3>外院材料 → 本院 Local Slide</h3>
        <p class="muted">保留外院 Block 来源事实；Local Slide 仍是统一 V2 Slide。</p>
        <label>外院材料引用<input v-model="externalReference" required /></label>
        <label>外院 Block 编号<input v-model="externalBlockCode" required /></label>
        <label>本院切片编号<input v-model="localSlideCode" required /></label>
        <button
          :disabled="busy || Boolean(consultationMaterial)"
          type="button"
          @click="registerConsultationMaterial"
        >
          登记外院材料
        </button>
        <button
          v-if="consultationMaterial?.slideId"
          :disabled="busy"
          type="button"
          @click="completeConsultationSlide"
        >
          完成本院切片
        </button>
        <output v-if="consultationMaterial" role="status">
          External Block {{ consultationMaterial.blockId }} · Local Slide
          {{ consultationMaterial.slideId }}
        </output>
      </article>

      <article class="business-card">
        <span class="step-label">DIAGNOSIS</span>
        <h3>进入诊断工作区</h3>
        <p class="muted">诊断、责任链、预览、签发和 PDF 均在同一个 V2 Workspace 完成。</p>
        <button type="button" @click="openDiagnosis">打开 Diagnosis Workspace</button>
      </article>
    </section>
  </section>
</template>
