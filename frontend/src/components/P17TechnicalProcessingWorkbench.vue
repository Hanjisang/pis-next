<script setup lang="ts">
import { computed, ref } from 'vue';

import {
  addProcessingMember,
  completeEmbeddingTask,
  completeProcessingBatch,
  confirmProcessingResult,
  createEmbeddingTask,
  createProcessingBatch,
  createProcessingTask,
  getEmbeddingQueue,
  getProcessingQueue,
  receiveProcessingRawResult,
  recordEmbeddingRequirements,
  startEmbeddingTask,
  startProcessingBatch,
  takeoverEmbeddingTask,
  takeoverProcessingTask,
  type ActualBlockFormationResult,
  type EmbeddingTaskResult,
  type ProcessingBatchResult,
  type ProcessingMemberResult,
  type ProcessingResult,
  type ProcessingRunResult,
  type ProcessingTaskResult,
} from '../api';

const tissueBlockId = ref('');
const task = ref<ProcessingTaskResult | null>(null);
const batch = ref<ProcessingBatchResult | null>(null);
const member = ref<ProcessingMemberResult | null>(null);
const run = ref<ProcessingRunResult | null>(null);
const result = ref<ProcessingResult | null>(null);
const embedding = ref<EmbeddingTaskResult | null>(null);
const formation = ref<ActualBlockFormationResult | null>(null);
const processingQueue = ref<Record<string, unknown>[]>([]);
const embeddingQueue = ref<Record<string, unknown>[]>([]);
const requirements = ref('P17-SYNTHETIC-EMBEDDING-REQUIREMENTS');
const orientation = ref('synthetic orientation reference');
const summary = ref('synthetic processing result confirmed by human operator');
const errorMessage = ref('');
const notice = ref('');
const busy = ref(false);

const taskVersion = computed(() => task.value?.concurrencyVersion ?? 0);
const batchVersion = computed(() => batch.value?.concurrencyVersion ?? 0);
const memberVersion = computed(() => member.value?.concurrencyVersion ?? 0);
const embeddingVersion = computed(() => embedding.value?.concurrencyVersion ?? 0);
const canCreateBatch = computed(() => task.value !== null && task.value.assignedActor !== null);

function commandKey(action: string, id = tissueBlockId.value) {
  return `p17-ui-${action}-${id}`;
}

async function runCommand(action: () => Promise<void>) {
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

async function refreshBatch() {
  if (!batch.value) return;
  const response = await fetch(`/api/p17/processing-batches/${batch.value.batchId}`);
  if (!response.ok) throw new Error('P17-REQUEST-FAILED: 无法刷新处理批次');
  batch.value = (await response.json()) as ProcessingBatchResult;
}

function loadQueues() {
  void runCommand(async () => {
    [processingQueue.value, embeddingQueue.value] = await Promise.all([
      getProcessingQueue(),
      getEmbeddingQueue(),
    ]);
    notice.value = `已加载组织处理 ${processingQueue.value.length} 条、包埋 ${embeddingQueue.value.length} 条队列记录`;
  });
}

function establishTask() {
  void runCommand(async () => {
    task.value = await createProcessingTask({
      tissueBlockId: tissueBlockId.value,
      idempotencyKey: commandKey('create-task'),
    });
  });
}

function takeoverTask() {
  if (!task.value) return;
  void runCommand(async () => {
    task.value = await takeoverProcessingTask(
      task.value?.taskId ?? '',
      taskVersion.value,
      commandKey('takeover-task', task.value?.taskId),
    );
  });
}

function establishBatch() {
  if (!task.value) return;
  void runCommand(async () => {
    batch.value = await createProcessingBatch({
      taskId: task.value?.taskId ?? '',
      programCode: 'P17-SYNTHETIC-REFERENCE',
      versionLabel: 'SYNTHETIC-1',
      executionMode: 'HUMAN',
      idempotencyKey: commandKey('create-batch', task.value?.taskId),
    });
  });
}

function addMember() {
  if (!batch.value) return;
  void runCommand(async () => {
    member.value = await addProcessingMember(
      batch.value?.batchId ?? '',
      tissueBlockId.value,
      commandKey('add-member', batch.value?.batchId),
    );
  });
}

function startBatch() {
  if (!batch.value) return;
  void runCommand(async () => {
    run.value = await startProcessingBatch(
      batch.value?.batchId ?? '',
      batchVersion.value,
      commandKey('start-batch', batch.value?.batchId),
    );
    await refreshBatch();
  });
}

function receiveRaw() {
  if (!run.value) return;
  void runCommand(async () => {
    await receiveProcessingRawResult({
      runId: run.value?.runId ?? '',
      externalMessageId: commandKey('raw-message', run.value?.runId),
      payloadDigest: `P17-SYNTHETIC-DIGEST-${run.value?.runId ?? ''}`,
      rawStateCode: 'P17-RAW-COMPLETE',
      payloadReference: 'synthetic://p17/raw-result',
      idempotencyKey: commandKey('raw-result', run.value?.runId),
    });
    notice.value = '原始执行事实已接收；尚未形成有效业务结果';
  });
}

function confirmResult() {
  if (!run.value || !member.value) return;
  void runCommand(async () => {
    result.value = await confirmProcessingResult({
      runId: run.value?.runId ?? '',
      memberId: member.value?.memberId ?? '',
      resultStateCode: 'P17-RESULT-VALIDATED',
      canEnterEmbedding: true,
      summary: summary.value,
      expectedMemberVersion: memberVersion.value,
      idempotencyKey: commandKey('confirm-result', member.value?.memberId),
    });
    member.value = {
      ...member.value!,
      concurrencyVersion: memberVersion.value + 1,
      stateCode: 'P17-MEMBER-READY-FOR-EMBEDDING',
      canEnterEmbedding: true,
    };
  });
}

function finishBatch() {
  if (!batch.value) return;
  void runCommand(async () => {
    batch.value = await completeProcessingBatch(
      batch.value?.batchId ?? '',
      batchVersion.value,
      commandKey('complete-batch', batch.value?.batchId),
    );
  });
}

function establishEmbedding() {
  if (!result.value) return;
  void runCommand(async () => {
    embedding.value = await createEmbeddingTask({
      tissueBlockId: tissueBlockId.value,
      processingResultId: result.value?.resultId ?? '',
      idempotencyKey: commandKey('create-embedding'),
    });
  });
}

function takeoverEmbedding() {
  if (!embedding.value) return;
  void runCommand(async () => {
    embedding.value = await takeoverEmbeddingTask(
      embedding.value?.taskId ?? '',
      embeddingVersion.value,
      commandKey('takeover-embedding', embedding.value?.taskId),
    );
  });
}

function startEmbedding() {
  if (!embedding.value) return;
  void runCommand(async () => {
    embedding.value = await startEmbeddingTask(
      embedding.value?.taskId ?? '',
      embeddingVersion.value,
      commandKey('start-embedding', embedding.value?.taskId),
    );
  });
}

function saveRequirements() {
  if (!embedding.value) return;
  void runCommand(async () => {
    embedding.value = await recordEmbeddingRequirements({
      taskId: embedding.value?.taskId ?? '',
      requirementSnapshot: requirements.value,
      orientationReference: orientation.value,
      expectedVersion: embeddingVersion.value,
      idempotencyKey: commandKey('embedding-requirements', embedding.value?.taskId),
    });
  });
}

function completeEmbedding() {
  if (!embedding.value) return;
  void runCommand(async () => {
    formation.value = await completeEmbeddingTask({
      taskId: embedding.value?.taskId ?? '',
      expectedTaskVersion: embeddingVersion.value,
      expectedBlockVersion: 0,
      idempotencyKey: commandKey('complete-embedding', embedding.value?.taskId),
    });
    notice.value = '包埋完成并已形成实际蜡块事实；编号沿用计划蜡块业务编号';
  });
}
</script>

<template>
  <section class="p17-workbench" aria-label="P17 组织处理与包埋工作台">
    <div class="section-heading">
      <div>
        <p class="eyebrow">P17 TECHNICAL PROCESSING &amp; EMBEDDING</p>
        <h2>组织处理与包埋工作台</h2>
      </div>
      <span class="status-dot">程序快照 · 原始事实 · 人工确认 · 实际蜡块形成</span>
    </div>

    <p v-if="errorMessage" class="error-banner" role="alert">{{ errorMessage }}</p>
    <p v-if="notice" class="notice-banner" role="status">{{ notice }}</p>

    <div class="p17-toolbar">
      <label>计划蜡块内部ID<input v-model="tissueBlockId" required /></label>
      <button :disabled="busy || !tissueBlockId" type="button" @click="loadQueues">
        加载 P17 队列
      </button>
      <span>组织处理 {{ processingQueue.length }} 条 · 包埋 {{ embeddingQueue.length }} 条</span>
    </div>

    <!-- eslint-disable vue/html-closing-bracket-newline, vue/html-indent -->
    <div class="p17-grid">
      <section class="p17-card">
        <span class="step-label">01 · 责任与计划</span>
        <h3>建立组织处理任务</h3>
        <p>仅接受已完成取材交接、尚未形成物理蜡块的计划蜡块。</p>
        <button :disabled="busy || !tissueBlockId" type="button" @click="establishTask">
          建立处理任务
        </button>
        <button :disabled="busy || !task" type="button" @click="takeoverTask">接管处理责任</button>
        <output v-if="task"
          >{{ task.taskNo }} · {{ task.stateCode }} · v{{ task.concurrencyVersion }}</output
        >
      </section>

      <section class="p17-card">
        <span class="step-label">02 · 批次与程序</span>
        <h3>选择程序版本并建立批次</h3>
        <p>当前演示只允许本地/测试环境的合成程序版本。</p>
        <button :disabled="busy || !canCreateBatch" type="button" @click="establishBatch">
          建立处理批次
        </button>
        <button :disabled="busy || !batch" type="button" @click="addMember">加入批次成员</button>
        <output v-if="batch"
          >{{ batch.batchNo }} · {{ batch.stateCode }} · v{{ batch.concurrencyVersion }}</output
        >
        <output v-if="member">成员 {{ member.plannedBlockNo }} · {{ member.stateCode }}</output>
      </section>

      <section class="p17-card">
        <span class="step-label">03 · 执行事实</span>
        <h3>开始、接收原始结果并确认</h3>
        <p>原始执行结果与有效业务结果分开保存；确认动作需要人工主体。</p>
        <button :disabled="busy || !member || !batch" type="button" @click="startBatch">
          开始组织处理
        </button>
        <button :disabled="busy || !run" type="button" @click="receiveRaw">接收原始执行结果</button>
        <button :disabled="busy || !run || !member" type="button" @click="confirmResult">
          人工确认有效结果
        </button>
        <button :disabled="busy || !result" type="button" @click="finishBatch">完成处理批次</button>
        <textarea v-model="summary" aria-label="有效结果摘要" />
        <output v-if="run">运行 {{ run.externalRunId }} · {{ run.stateCode }}</output>
        <output v-if="result"
          >结果 {{ result.stateCode }} · 可进入包埋
          {{ result.canEnterEmbedding ? '是' : '否' }}</output
        >
      </section>

      <section class="p17-card">
        <span class="step-label">04 · 包埋责任</span>
        <h3>建立包埋任务</h3>
        <p>处理结果确认后，才允许进入包埋任务；此处尚不产生实际蜡块。</p>
        <button :disabled="busy || !result" type="button" @click="establishEmbedding">
          建立包埋任务
        </button>
        <button :disabled="busy || !embedding" type="button" @click="takeoverEmbedding">
          接管包埋责任
        </button>
        <button :disabled="busy || !embedding" type="button" @click="startEmbedding">
          开始包埋
        </button>
        <output v-if="embedding"
          >{{ embedding.taskNo }} · {{ embedding.stateCode }} · v{{
            embedding.concurrencyVersion
          }}</output
        >
      </section>

      <section class="p17-card">
        <span class="step-label">05 · 要求快照</span>
        <h3>记录包埋要求与方向</h3>
        <label>要求快照<textarea v-model="requirements" required /></label>
        <label>方向参考<input v-model="orientation" required /></label>
        <button :disabled="busy || !embedding" type="button" @click="saveRequirements">
          保存包埋要求
        </button>
      </section>

      <section class="p17-card completion-card">
        <span class="step-label">06 · 实际形成</span>
        <h3>完成包埋并形成实际蜡块</h3>
        <p>后端事务同时校验任务版本、计划蜡块版本和生命周期；失败时不会显示成功。</p>
        <button :disabled="busy || !embedding" type="button" @click="completeEmbedding">
          确认包埋完成
        </button>
        <output v-if="formation"
          >{{ formation.inheritedBlockNo }} · {{ formation.stateCode }} · v{{
            formation.formationVersion
          }}</output
        >
      </section>
    </div>
    <!-- eslint-enable vue/html-closing-bracket-newline, vue/html-indent -->
  </section>
</template>
