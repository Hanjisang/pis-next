<script setup lang="ts">
import { computed, ref } from 'vue';

import {
  associateV2Specimen,
  completeV2Grossing,
  createV2Block,
  createV2Grossing,
  reopenV2Grossing,
  type V2BlockResult,
  type V2GrossingResult,
} from '../v2MaterialApi';

const caseId = defineModel<string>('caseId', { default: '' });
const grossDescription = ref('synthetic gross description');
const grossingInstruction = ref('synthetic instruction');
const grossingDoctorId = ref('SYNTH-DOCTOR');
const recorderId = ref('SYNTH-RECORDER');
const specimenIds = ref(['']);
const blockSpecimenId = ref('');
const blockCode = ref('A1');
const blockType = ref('ROUTINE');
const grossing = ref<V2GrossingResult | null>(null);
const blocks = ref<V2BlockResult[]>([]);
const busy = ref(false);
const errorMessage = ref('');
const notice = ref('');

const canSubmit = computed(() => Boolean(caseId.value.trim()) && !busy.value);
const grossingState = computed(() =>
  grossing.value?.completedAt ? 'COMPLETED fact' : 'OPEN fact',
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

function addSpecimenRow() {
  specimenIds.value.push('');
}

function submitGrossing() {
  if (!canSubmit.value) return;
  void run(async () => {
    grossing.value = await createV2Grossing({
      caseId: caseId.value,
      sourceType: 'INITIAL',
      grossDescription: grossDescription.value,
      grossingInstruction: grossingInstruction.value,
      grossingDoctorId: grossingDoctorId.value,
      recorderId: recorderId.value,
      idempotencyKey: `v2-grossing-${caseId.value}`,
    });
    notice.value = `Grossing ${grossing.value.grossingNo} 已建立；多标本关联通过独立命令完成。`;
  });
}

function associateSpecimens() {
  if (!grossing.value) return;
  void run(async () => {
    const validSpecimens = specimenIds.value.map((id) => id.trim()).filter(Boolean);
    for (const [index, specimenId] of validSpecimens.entries()) {
      await associateV2Specimen({
        grossingId: grossing.value!.grossingId,
        specimenId,
        materialDescription: `synthetic material ${index + 1}`,
        idempotencyKey: `v2-grossing-specimen-${grossing.value!.grossingId}-${specimenId}`,
      });
    }
    notice.value = `已关联 ${validSpecimens.length} 个 Specimen。`;
  });
}

function submitBlock() {
  if (!grossing.value || !blockSpecimenId.value.trim()) return;
  void run(async () => {
    const block = await createV2Block({
      grossingId: grossing.value!.grossingId,
      specimenId: blockSpecimenId.value,
      blockCode: blockCode.value,
      blockType: blockType.value,
      idempotencyKey: `v2-block-${grossing.value!.grossingId}-${blockCode.value}`,
    });
    blocks.value.push(block);
    notice.value = `Block ${block.blockCode} 已建立。`;
  });
}

function completeGrossing() {
  if (!grossing.value) return;
  void run(async () => {
    const result = await completeV2Grossing({
      grossingId: grossing.value!.grossingId,
      expectedVersion: grossing.value!.concurrencyVersion,
      idempotencyKey: `v2-grossing-complete-${grossing.value!.grossingId}-${grossing.value!.concurrencyVersion}`,
    });
    grossing.value = { ...grossing.value!, ...result, completedAt: result.completedAt };
    notice.value = `Grossing 已完成，新增 ${result.createdSlideCount} 张 INITIAL Slide。`;
  });
}

function reopenGrossing() {
  if (!grossing.value) return;
  void run(async () => {
    grossing.value = await reopenV2Grossing({
      grossingId: grossing.value!.grossingId,
      expectedVersion: grossing.value!.concurrencyVersion,
      reason: 'synthetic correction',
      idempotencyKey: `v2-grossing-reopen-${grossing.value!.grossingId}-${grossing.value!.concurrencyVersion}`,
    });
    notice.value = 'Grossing 已显式重开，可以继续新增材料。';
  });
}
</script>

<template>
  <section class="workbench" aria-label="V2-I02 Grossing 与 Block 工作台">
    <div class="section-heading">
      <div>
        <p class="eyebrow">PIS V2 · V2-I02</p>
        <h2>Grossing / Block 材料生产</h2>
      </div>
      <span class="status-dot">Case-level Grossing · 多标本</span>
    </div>

    <p v-if="errorMessage" class="error-banner" role="alert">{{ errorMessage }}</p>
    <p v-if="notice" class="success-banner" role="status">{{ notice }}</p>

    <div class="workflow-grid">
      <form class="business-card" @submit.prevent="submitGrossing">
        <span class="step-label">I02-01 · Grossing</span>
        <h3>建立 Case-level 取材记录</h3>
        <label>Case ID<input v-model="caseId" required /></label>
        <label>Gross 描述<textarea v-model="grossDescription" required /></label>
        <label>取材医师<input v-model="grossingDoctorId" required /></label>
        <label>记录人<input v-model="recorderId" required /></label>
        <button :disabled="!canSubmit" type="submit">建立 Grossing</button>
        <output v-if="grossing"
          >{{ grossing.grossingNo }} · {{ grossingState }} · v{{
            grossing.concurrencyVersion
          }}</output
        >
      </form>

      <form class="business-card" @submit.prevent="associateSpecimens">
        <span class="step-label">I02-02 · GrossingSpecimen</span>
        <h3>关联多个 Specimen</h3>
        <label v-for="(_, index) in specimenIds" :key="index">
          Specimen ID {{ index + 1 }}<input v-model="specimenIds[index]" required />
        </label>
        <button type="button" :disabled="busy" @click="addSpecimenRow">增加 Specimen</button>
        <button :disabled="!grossing || busy" type="submit">保存多标本关联</button>
      </form>

      <form class="business-card" @submit.prevent="submitBlock">
        <span class="step-label">I02-03 · Block</span>
        <h3>创建统一 Block</h3>
        <label>来源 Specimen ID<input v-model="blockSpecimenId" required /></label>
        <label>Block code<input v-model="blockCode" required /></label>
        <label>Block type<input v-model="blockType" required /></label>
        <button :disabled="!grossing || busy" type="submit">创建 Block</button>
        <!-- prettier-ignore -->
        <output
          v-for="block in blocks"
          :key="block.blockId"
        >{{ block.blockCode }} · v{{ block.concurrencyVersion }}</output>
      </form>

      <article class="business-card">
        <span class="step-label">I02-04 · Fact transition</span>
        <h3>完成 / 重开 Grossing</h3>
        <p class="muted">完成动作触发 SlideRule；重试只补齐缺失输出，不覆盖既有材料。</p>
        <button
          :disabled="!grossing || busy || Boolean(grossing?.completedAt)"
          type="button"
          @click="completeGrossing"
        >
          完成 Grossing
        </button>
        <button
          :disabled="!grossing || busy || !grossing?.completedAt"
          type="button"
          @click="reopenGrossing"
        >
          显式重开 Grossing
        </button>
      </article>
    </div>
  </section>
</template>
