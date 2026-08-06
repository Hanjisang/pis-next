<script setup lang="ts">
import { computed, ref } from 'vue';

import {
  acceptRegistration,
  establishCase,
  receiveSpecimen,
  registerExpectedSpecimen,
  registerManual,
  type CaseResult,
  type ExpectedSpecimenResult,
  type RegistrationResult,
} from '../api';

const modality = ref('HISTOLOGY');
const requestContent = ref('synthetic pathology request');
const registrationReason = ref('P15 synthetic manual registration');
const patientReference = ref('SYNTH-PATIENT-001');
const visitReference = ref('SYNTH-VISIT-001');
const collectionSite = ref('synthetic site');
const specimenKind = ref('TISSUE');
const collectionMethod = ref('SURGICAL');
const expectedQuantity = ref(1);
const containerBarcode = ref('');
const scanBarcode = ref('');
const scanQuantity = ref(1);
const errorMessage = ref('');
const busy = ref(false);
const request = ref<RegistrationResult | null>(null);
const pathologyCase = ref<CaseResult | null>(null);
const expected = ref<ExpectedSpecimenResult | null>(null);
const receivedState = ref('');

const canEstablishCase = computed(() => request.value !== null && !busy.value);
const canRegisterSpecimen = computed(() => pathologyCase.value !== null && !busy.value);

async function run(action: () => Promise<void>) {
  errorMessage.value = '';
  busy.value = true;
  try {
    await action();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '请求失败';
  } finally {
    busy.value = false;
  }
}

function submitRegistration() {
  void run(async () => {
    request.value = await registerManual({
      pathologyModalityCode: modality.value,
      requestContent: requestContent.value,
      reason: registrationReason.value,
    });
  });
}

function submitCase() {
  if (!request.value) return;
  void run(async () => {
    await acceptRegistration(
      request.value?.requestId ?? '',
      request.value?.concurrencyVersion ?? 0,
    );
    pathologyCase.value = await establishCase({
      requestId: request.value?.requestId ?? '',
      patientReference: patientReference.value,
      visitReference: visitReference.value,
      pathologyModalityCode: modality.value,
    });
  });
}

function submitExpectedSpecimen() {
  if (!pathologyCase.value) return;
  void run(async () => {
    expected.value = await registerExpectedSpecimen(pathologyCase.value?.caseId ?? '', {
      specimenKindCode: specimenKind.value,
      collectionSite: collectionSite.value,
      collectionMethodCode: collectionMethod.value,
      expectedQuantity: expectedQuantity.value,
      containerBarcode: containerBarcode.value,
    });
    scanBarcode.value = expected.value.containerBarcode;
  });
}

function submitReceive() {
  if (!expected.value) return;
  void run(async () => {
    const received = await receiveSpecimen({
      barcode: scanBarcode.value,
      expectedQuantity: expectedQuantity.value,
      actualQuantity: scanQuantity.value,
      expectedVersion: expected.value?.concurrencyVersion ?? 0,
      idempotencyKey: `${scanBarcode.value}:${scanQuantity.value}`,
    });
    receivedState.value = received.lifecycleStateCode;
  });
}
</script>

<template>
  <section class="workbench" aria-label="P15 登记与标本接收工作台">
    <div class="section-heading">
      <div>
        <p class="eyebrow">P15 REGISTRATION &amp; RECEIVING</p>
        <h2>登记与标本接收</h2>
      </div>
      <span class="status-dot">后端授权 · 追加审计</span>
    </div>

    <p v-if="errorMessage" class="error-banner" role="alert">{{ errorMessage }}</p>

    <div class="workflow-grid">
      <form class="business-card" @submit.prevent="submitRegistration">
        <span class="step-label">01 · 申请登记</span>
        <h3>建立合成申请</h3>
        <label>
          病理类型
          <input v-model="modality" required />
        </label>
        <label>
          申请说明
          <input v-model="requestContent" required />
        </label>
        <label>
          建立原因
          <input v-model="registrationReason" required />
        </label>
        <button :disabled="busy" type="submit">登记手工申请</button>
        <output v-if="request">
          申请 {{ request.applicationNo ?? request.requestId }} ·
          {{ request.lifecycleStateCode }}
        </output>
      </form>

      <form class="business-card" @submit.prevent="submitCase">
        <span class="step-label">02 · 病例建立</span>
        <h3>固定患者/就诊快照</h3>
        <label>
          患者外部引用
          <input v-model="patientReference" required />
        </label>
        <label>
          就诊外部引用
          <input v-model="visitReference" required />
        </label>
        <button :disabled="!canEstablishCase" type="submit">接受申请并建立病例</button>
        <output v-if="pathologyCase">
          病例 {{ pathologyCase.caseNo ?? pathologyCase.caseId }}
        </output>
      </form>

      <form class="business-card" @submit.prevent="submitExpectedSpecimen">
        <span class="step-label">03 · 预计标本</span>
        <h3>登记容器</h3>
        <label>
          标本类型
          <input v-model="specimenKind" required />
        </label>
        <label>
          来源部位
          <input v-model="collectionSite" required />
        </label>
        <label>
          预计数量
          <input v-model.number="expectedQuantity" min="1" required type="number" />
        </label>
        <label>
          容器条码
          <input v-model="containerBarcode" placeholder="留空由后端分配" />
        </label>
        <button :disabled="!canRegisterSpecimen" type="submit">登记预计标本</button>
        <output v-if="expected">
          容器 {{ expected.containerBarcode }} · {{ expected.lifecycleStateCode }}
        </output>
      </form>

      <form class="business-card scan-card" @submit.prevent="submitReceive">
        <span class="step-label">04 · 扫码接收</span>
        <h3>核对并接收单个容器</h3>
        <label>
          扫码条码
          <input v-model="scanBarcode" aria-label="扫码条码" autofocus required />
        </label>
        <label>
          实际数量
          <input v-model.number="scanQuantity" min="1" required type="number" />
        </label>
        <button :disabled="!expected || busy" type="submit">扫码接收</button>
        <output v-if="receivedState">结果 {{ receivedState }}</output>
      </form>
    </div>
  </section>
</template>
