<script setup lang="ts">
import { computed, ref } from 'vue';

import {
  addTissueSample,
  assignTissueSample,
  completeGrossingBatch,
  createGrossingBatch,
  createPlannedBlock,
  generateBlockLabel,
  getGrossingQueue,
  recordGrossing,
  reprintBlockLabel,
  startGrossingBatch,
  submitBlockLabelPrint,
  takeoverGrossingBatch,
  type GrossingBatchResult,
  type GrossingBlockResult,
  type GrossingLabelResult,
  type GrossingSampleResult,
} from '../api';

const specimenId = ref('');
const specimenNo = ref('DEV-SP-');
const caseNo = ref('DEV-CASE-');
const patientReference = ref('SYNTH-PATIENT-001');
const grossAppearance = ref('synthetic gross appearance');
const grossDescription = ref('synthetic gross description');
const sourceSite = ref('synthetic site');
const sampleDescription = ref('synthetic tissue fragment');
const quantity = ref(1);
const errorMessage = ref('');
const notice = ref('');
const busy = ref(false);
const queue = ref<Record<string, unknown>[]>([]);
const batch = ref<GrossingBatchResult | null>(null);
const sample = ref<GrossingSampleResult | null>(null);
const block = ref<GrossingBlockResult | null>(null);
const label = ref<GrossingLabelResult | null>(null);

const currentVersion = computed(() => batch.value?.concurrencyVersion ?? 0);
const hasBatch = computed(() => batch.value !== null);

async function run(action: () => Promise<void>) {
  errorMessage.value = '';
  notice.value = '';
  busy.value = true;
  try {
    await action();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '请求失败';
  } finally {
    busy.value = false;
  }
}

function loadQueue() {
  void run(async () => {
    queue.value = await getGrossingQueue();
    notice.value = `已加载 ${queue.value.length} 个取材任务`;
  });
}

function establishBatch() {
  void run(async () => {
    batch.value = await createGrossingBatch({
      specimenId: specimenId.value,
      specimenNo: specimenNo.value,
      caseNo: caseNo.value,
      patientIdentityReference: patientReference.value,
      idempotencyKey: `p16-batch-${specimenId.value}`,
    });
  });
}

function takeover() {
  if (!batch.value) return;
  void run(async () => {
    batch.value = await takeoverGrossingBatch(
      batch.value?.batchId ?? '',
      currentVersion.value,
      `p16-takeover-${batch.value?.batchId ?? ''}-${currentVersion.value}`,
    );
  });
}

function start() {
  if (!batch.value) return;
  void run(async () => {
    batch.value = await startGrossingBatch(
      batch.value?.batchId ?? '',
      currentVersion.value,
      `p16-start-${batch.value?.batchId ?? ''}-${currentVersion.value}`,
    );
  });
}

function saveGrossingRecord() {
  if (!batch.value) return;
  void run(async () => {
    await recordGrossing(batch.value?.batchId ?? '', {
      specimenId: specimenId.value,
      specimenNo: specimenNo.value,
      caseNo: caseNo.value,
      patientIdentityReference: patientReference.value,
      identityVerified: true,
      patientIdentityVerified: true,
      grossAppearance: grossAppearance.value,
      grossDescription: grossDescription.value,
      quantity: quantity.value,
      quantityUnitCode: 'PIECE',
      expectedVersion: currentVersion.value,
      idempotencyKey: `p16-record-${batch.value?.batchId ?? ''}-${currentVersion.value}`,
    });
    await refreshBatch();
  });
}

function saveSample() {
  if (!batch.value) return;
  void run(async () => {
    sample.value = await addTissueSample(batch.value?.batchId ?? '', {
      specimenId: specimenId.value,
      sourceSite: sourceSite.value,
      description: sampleDescription.value,
      quantity: quantity.value,
      unit: 'PIECE',
      expectedVersion: currentVersion.value,
      idempotencyKey: `p16-sample-${batch.value?.batchId ?? ''}-${currentVersion.value}`,
    });
    await refreshBatch();
  });
}

function saveBlock() {
  if (!batch.value) return;
  void run(async () => {
    block.value = await createPlannedBlock(batch.value?.batchId ?? '', {
      specimenId: specimenId.value,
      blockKindCode: 'ROUTINE',
      sourceMaterialKindCode: 'TISSUE',
      expectedVersion: currentVersion.value,
      idempotencyKey: `p16-block-${batch.value?.batchId ?? ''}-${currentVersion.value}`,
    });
    await refreshBatch();
  });
}

function assignSample() {
  if (!block.value || !sample.value || !batch.value) return;
  void run(async () => {
    await assignTissueSample(
      block.value?.blockId ?? '',
      sample.value?.sampleId ?? '',
      currentVersion.value,
      `p16-assign-${block.value?.blockId ?? ''}-${sample.value?.sampleId ?? ''}`,
    );
    await refreshBatch();
  });
}

function makeLabel() {
  if (!block.value) return;
  void run(async () => {
    label.value = await generateBlockLabel(
      block.value?.blockId ?? '',
      `p16-label-${block.value?.blockId ?? ''}`,
    );
  });
}

function submitPrint() {
  if (!label.value) return;
  void run(async () => {
    const result = await submitBlockLabelPrint(
      label.value?.labelId ?? '',
      `p16-print-${label.value?.labelId ?? ''}`,
    );
    notice.value = `参考打印已提交：${result.outcome}；尚未确认物理打印成功`;
  });
}

function reprint() {
  if (!label.value) return;
  void run(async () => {
    const result = await reprintBlockLabel(
      label.value?.labelId ?? '',
      `p16-reprint-${Date.now()}`,
      'synthetic label replacement',
    );
    notice.value = `重打已提交：${result.outcome}`;
  });
}

function complete() {
  if (!batch.value) return;
  void run(async () => {
    batch.value = await completeGrossingBatch(
      batch.value?.batchId ?? '',
      currentVersion.value,
      `p16-complete-${batch.value?.batchId ?? ''}-${currentVersion.value}`,
    );
  });
}

async function refreshBatch() {
  // Commands return the authoritative version; the small workbench keeps the latest local version explicitly.
  if (batch.value)
    batch.value = { ...batch.value, concurrencyVersion: batch.value.concurrencyVersion + 1 };
}
</script>

<template>
  <!-- eslint-disable vue/html-closing-bracket-newline, vue/html-indent -->
  <section class="p16-workbench" aria-label="P16 取材与蜡块工作台">
    <div class="section-heading">
      <div>
        <p class="eyebrow">P16 GROSSING &amp; BLOCK LABELING</p>
        <h2>取材与蜡块工作台</h2>
      </div>
      <span class="status-dot">取材事实 · 标签历史 · 后端授权</span>
    </div>

    <p v-if="errorMessage" class="error-banner" role="alert">{{ errorMessage }}</p>
    <p v-if="notice" class="notice-banner" role="status">{{ notice }}</p>

    <div class="p16-toolbar">
      <button :disabled="busy" type="button" @click="loadQueue">加载待取材队列</button>
      <span>队列 {{ queue.length }} · 仅显示当前数据范围</span>
    </div>

    <div class="p16-grid">
      <form class="p16-card" @submit.prevent="establishBatch">
        <span class="step-label">01 · 身份和任务</span>
        <h3>建立/接管取材批次</h3>
        <label>标本内部ID<input v-model="specimenId" required /></label>
        <label>标本号<input v-model="specimenNo" required /></label>
        <label>病例号<input v-model="caseNo" required /></label>
        <label>患者核对引用<input v-model="patientReference" required /></label>
        <button :disabled="busy" type="submit">建立取材批次</button>
        <button :disabled="busy || !hasBatch" type="button" @click="takeover">接管任务</button>
        <button :disabled="busy || !hasBatch" type="button" @click="start">开始取材</button>
        <output v-if="batch"
          >{{ batch.batchNo }} · {{ batch.stateCode }} · v{{ batch.concurrencyVersion }}</output
        >
      </form>

      <form class="p16-card" @submit.prevent="saveGrossingRecord">
        <span class="step-label">02 · 取材事实</span>
        <h3>身份核对与大体描述</h3>
        <label>外观<input v-model="grossAppearance" required /></label>
        <label>大体描述<textarea v-model="grossDescription" required /></label>
        <label>数量<input v-model.number="quantity" min="1" required type="number" /></label>
        <button :disabled="busy || !hasBatch" type="submit">保存取材记录</button>
        <output>身份冲突会阻断并生成审计</output>
      </form>

      <form class="p16-card" @submit.prevent="saveSample">
        <span class="step-label">03 · 组织取样</span>
        <h3>记录来源与去向</h3>
        <label>来源部位<input v-model="sourceSite" required /></label>
        <label>取样描述<input v-model="sampleDescription" required /></label>
        <button :disabled="busy || !hasBatch" type="submit">添加组织取样</button>
        <output v-if="sample">{{ sample.sampleNo }} · {{ sample.stateCode }}</output>
      </form>

      <form class="p16-card" @submit.prevent="saveBlock">
        <span class="step-label">04 · 计划蜡块</span>
        <h3>建立包埋盒/计划蜡块</h3>
        <p>本阶段只建立计划身份，不形成物理石蜡块。</p>
        <button :disabled="busy || !hasBatch" type="submit">建立计划蜡块</button>
        <button :disabled="busy || !block || !sample" type="button" @click="assignSample">
          分配组织取样
        </button>
        <output v-if="block"
          >{{ block.blockNo }} / {{ block.tissueBoxNo }} · {{ block.stateCode }}</output
        >
      </form>

      <section class="p16-card label-card" aria-label="标签预览与打印">
        <span class="step-label">05 · 标签</span>
        <h3>版本化预览与参考打印</h3>
        <button :disabled="busy || !block" type="button" @click="makeLabel">生成标签快照</button>
        <div v-if="label" class="label-preview">
          <small>标签 v{{ label.labelVersion }} · {{ label.stateCode }}</small>
          <pre>{{ label.snapshot }}</pre>
          <code>{{ label.barcodePayload }}</code>
        </div>
        <button :disabled="busy || !label" type="button" @click="submitPrint">提交参考打印</button>
        <button :disabled="busy || !label" type="button" @click="reprint">因故重打标签</button>
        <p>打印提交不等于物理打印确认；尚未确认物理打印成功，浏览器不会伪造成功。</p>
      </section>

      <section class="p16-card completion-card">
        <span class="step-label">06 · 交接</span>
        <h3>完成取材并交接后续技术流程</h3>
        <p>完成前必须有取材记录、组织去向、编号和有效标签。</p>
        <button :disabled="busy || !label || !block || !sample" type="button" @click="complete">
          完成取材
        </button>
        <output v-if="batch">当前状态：{{ batch.stateCode }}</output>
      </section>
    </div>
  </section>
</template>
