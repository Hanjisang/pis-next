<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';

import {
  cancelV2TechnicalOrder,
  createV2TechnicalProject,
  enterV2TechnicalResult,
  executeV2TechnicalOrder,
  getV2TechnicalProjects,
  getV2TechnicalWorkbench,
  type V2TechnicalOrder,
  type V2TechnicalProject,
} from '../v2DiagnosisApi';

const projects = ref<V2TechnicalProject[]>([]);
const orders = ref<V2TechnicalOrder[]>([]);
const loading = ref(false);
const submitting = ref(false);
const error = ref('');
const notice = ref('');
const resultDrafts = reactive<Record<string, string>>({});
const projectDraft = reactive({
  businessTypeId: '',
  projectCode: '',
  projectName: '',
  enabled: true,
  allowedTargetTypes: 'BLOCK,SLIDE',
  producesSlide: true,
  producesBlock: false,
  producesStructuredResult: false,
  defaultSlideType: 'IHC',
  parametersSchema: '{}',
  resultSchema: '{}',
  feeMapping: '{}',
  displayConfiguration: '{}',
  requiredBeforeSignOutDefault: true,
  configurationVersion: 1,
});

onMounted(() => void refresh());

function key(prefix: string) {
  return `${prefix}-${globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`}`;
}

async function refresh() {
  loading.value = true;
  error.value = '';
  try {
    [projects.value, { orders: orders.value }] = await Promise.all([
      getV2TechnicalProjects(),
      getV2TechnicalWorkbench(),
    ]);
    if (!projectDraft.businessTypeId && projects.value[0]) {
      projectDraft.businessTypeId = projects.value[0].businessTypeId;
    }
  } catch (requestError) {
    error.value =
      requestError instanceof Error ? requestError.message : 'Technical Workbench 加载失败';
  } finally {
    loading.value = false;
  }
}

async function execute(order: V2TechnicalOrder) {
  await submit(async () => {
    await executeV2TechnicalOrder(order.orderId, key(`v2-technical-execute-${order.orderId}`));
    await refresh();
    notice.value = `${order.orderNo} 已触发实际输出。`;
  });
}

async function cancel(order: V2TechnicalOrder) {
  await submit(async () => {
    await cancelV2TechnicalOrder({
      orderId: order.orderId,
      expectedVersion: order.version,
      reason: 'synthetic operator cancellation',
      idempotencyKey: key(`v2-technical-cancel-${order.orderId}`),
    });
    await refresh();
    notice.value = `${order.orderNo} 已取消，已生成事实不会被删除。`;
  });
}

async function enterResult(itemId: string) {
  await submit(async () => {
    await enterV2TechnicalResult({
      itemId,
      resultData: resultDrafts[itemId] || '{}',
      expectedVersion: 0,
      idempotencyKey: key(`v2-technical-result-${itemId}`),
    });
    await refresh();
    notice.value = '结构化结果已保存为可追溯版本。';
  });
}

async function createProject() {
  await submit(async () => {
    await createV2TechnicalProject({ ...projectDraft });
    await refresh();
    notice.value = `${projectDraft.projectCode} 配置已创建。`;
    projectDraft.projectCode = '';
    projectDraft.projectName = '';
  });
}

async function submit(operation: () => Promise<void>) {
  submitting.value = true;
  error.value = '';
  notice.value = '';
  try {
    await operation();
  } catch (requestError) {
    error.value =
      requestError instanceof Error ? requestError.message : 'Technical Workbench 操作失败';
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <section class="v2-technical-workbench" aria-label="V2 Technical Workbench">
    <header class="technical-header">
      <div>
        <p class="eyebrow">V2 · I04 · TECHNICAL LOOP</p>
        <h2>Technical Workbench</h2>
        <p>TechnicalProject 配置、TechnicalOrder 执行、正式材料输出与结构化结果。</p>
      </div>
      <button type="button" :disabled="loading || submitting" @click="refresh">Refresh</button>
    </header>

    <p v-if="loading" class="state-message" role="status">Loading Technical Workbench…</p>
    <p v-if="error" class="state-message error" role="alert">{{ error }}</p>
    <p v-if="notice" class="state-message success" role="status">{{ notice }}</p>

    <div class="technical-grid">
      <section class="technical-card" aria-label="TechnicalProject configuration">
        <div class="card-heading">
          <div>
            <h3>TechnicalProject Configuration</h3>
            <p>配置版本快照进入订单 item，核心输出类型显式声明。</p>
          </div>
        </div>
        <form class="project-form" @submit.prevent="createProject">
          <label>Business type ID<input v-model="projectDraft.businessTypeId" required /></label>
          <label
            >Project code<input v-model="projectDraft.projectCode" required placeholder="IHC-KI67"
          /></label>
          <label>Project name<input v-model="projectDraft.projectName" required /></label>
          <label>Allowed targets<input v-model="projectDraft.allowedTargetTypes" required /></label>
          <label>Default slide type<input v-model="projectDraft.defaultSlideType" /></label>
          <label
            >Parameters schema<textarea v-model="projectDraft.parametersSchema" rows="2" />
          </label>
          <label>Result schema<textarea v-model="projectDraft.resultSchema" rows="2" /></label>
          <div class="check-row">
            <label><input v-model="projectDraft.enabled" type="checkbox" /> Enabled</label>
            <label><input v-model="projectDraft.producesSlide" type="checkbox" /> Slide</label>
            <label><input v-model="projectDraft.producesBlock" type="checkbox" /> Block</label>
            <label
              ><input v-model="projectDraft.producesStructuredResult" type="checkbox" />
              Result</label
            >
            <label
              ><input v-model="projectDraft.requiredBeforeSignOutDefault" type="checkbox" />
              Blocking default</label
            >
          </div>
          <button class="primary-action" type="submit" :disabled="submitting">
            Create project
          </button>
        </form>
        <ul class="project-list">
          <li v-for="project in projects" :key="project.projectId">
            <strong>{{ project.projectCode }}</strong>
            <span>{{ project.projectName }} · {{ project.allowedTargetTypes.join(', ') }}</span>
            <small
              >{{ project.producesSlide ? 'SLIDE ' : '' }}{{ project.producesBlock ? 'BLOCK ' : ''
              }}{{ project.producesStructuredResult ? 'RESULT' : '' }}</small
            >
          </li>
        </ul>
      </section>

      <section class="technical-card orders-card" aria-label="TechnicalOrder execution">
        <div class="card-heading">
          <div>
            <h3>TechnicalOrder Queue</h3>
            <p>状态由实际 output/result fact 投影，取消不删除已产生事实。</p>
          </div>
          <strong>{{ orders.length }} active</strong>
        </div>
        <p v-if="!orders.length" class="empty-state">当前没有待处理 TechnicalOrder。</p>
        <article v-for="order in orders" :key="order.orderId" class="order-card">
          <div class="order-heading">
            <strong>{{ order.orderNo }}</strong>
            <span :class="{ blocking: order.blocking }"
              >{{ order.status }}{{ order.blocking ? ' · BLOCKING' : '' }}</span
            >
          </div>
          <small>case {{ order.caseId }} · {{ order.items.length }} items</small>
          <ul>
            <li v-for="item in order.items" :key="item.itemId">
              <div class="item-heading">
                <span
                  >{{ item.projectCode }} · {{ item.status }} · {{ item.completedCount }}/{{
                    item.expectedCount
                  }}</span
                >
                <span v-if="item.outputs.length">{{
                  item.outputs.map((output) => output.outputKind).join(', ')
                }}</span>
              </div>
              <div v-if="item.result" class="result-chip">
                Result v{{ item.result.version }} · {{ item.result.resultData }}
              </div>
              <div v-else-if="item.projectCode.includes('MOLECULAR')" class="result-entry">
                <input
                  v-model="resultDrafts[item.itemId]"
                  placeholder="{}"
                  aria-label="结构化结果 JSON"
                />
                <button type="button" :disabled="submitting" @click="enterResult(item.itemId)">
                  Enter result
                </button>
              </div>
            </li>
          </ul>
          <div class="order-actions">
            <button
              type="button"
              :disabled="submitting || order.status === 'COMPLETED'"
              @click="execute(order)"
            >
              Execute order
            </button>
            <button
              type="button"
              :disabled="submitting || order.status === 'COMPLETED'"
              @click="cancel(order)"
            >
              Cancel order
            </button>
          </div>
        </article>
      </section>
    </div>
  </section>
</template>

<style scoped>
.v2-technical-workbench {
  background: #f7faf8;
  border: 1px solid #cadbd2;
  border-radius: 24px;
  color: #193a30;
  margin-top: 28px;
  overflow: hidden;
}
.technical-header {
  align-items: end;
  background: linear-gradient(120deg, #172f4c, #2d6570);
  color: #f5fbf7;
  display: flex;
  justify-content: space-between;
  padding: 30px 34px;
}
.technical-header h2 {
  margin: 0 0 8px;
}
.technical-header p {
  color: #d4edf0;
  margin: 0;
}
.eyebrow {
  color: #9be0d1 !important;
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.14em;
  margin-bottom: 9px !important;
}
.technical-header button {
  background: #f3fbf8;
}
.state-message {
  margin: 0;
  padding: 14px 24px;
}
.state-message.error {
  background: #fff0ee;
  color: #a33d35;
}
.state-message.success {
  background: #e9f8ed;
  color: #1c7143;
}
.technical-grid {
  display: grid;
  gap: 18px;
  grid-template-columns: minmax(280px, 0.85fr) minmax(0, 1.35fr);
  padding: 24px;
}
.technical-card {
  background: #fff;
  border: 1px solid #d4e2dc;
  border-radius: 16px;
  padding: 20px;
}
.card-heading,
.order-heading,
.item-heading {
  align-items: center;
  display: flex;
  gap: 12px;
  justify-content: space-between;
}
.card-heading h3 {
  margin: 0;
}
.card-heading p {
  color: #698276;
  font-size: 0.86rem;
  margin: 6px 0 0;
}
.project-form {
  display: grid;
  gap: 10px;
  margin-top: 18px;
}
.project-form label {
  display: grid;
  font-size: 0.8rem;
  font-weight: 750;
  gap: 6px;
}
input,
textarea {
  background: #fff;
  border: 1px solid #b9ccc2;
  border-radius: 9px;
  color: #17322b;
  font: inherit;
  padding: 9px 10px;
}
.check-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.check-row label {
  align-items: center;
  display: flex;
  gap: 5px;
}
button {
  background: #fff;
  border: 1px solid #aac3b5;
  border-radius: 9px;
  color: #205440;
  cursor: pointer;
  font-weight: 750;
  min-height: 40px;
  padding: 8px 12px;
}
button:hover:not(:disabled) {
  background: #e9f5ed;
}
button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}
.primary-action {
  background: #1e6a52;
  border-color: #1e6a52;
  color: #fff;
}
.project-list,
.order-card ul {
  list-style: none;
  margin: 18px 0 0;
  padding: 0;
}
.project-list li {
  border-top: 1px solid #e1ebe5;
  display: grid;
  gap: 3px;
  padding: 10px 0;
}
.project-list span,
.project-list small,
.order-card small {
  color: #698276;
  font-size: 0.8rem;
}
.orders-card {
  min-width: 0;
}
.empty-state {
  color: #698276;
  margin-top: 22px;
}
.order-card {
  border: 1px solid #cbd9d2;
  border-radius: 12px;
  margin-top: 14px;
  padding: 14px;
}
.order-heading span {
  color: #24784c;
  font-size: 0.78rem;
  font-weight: 800;
}
.order-heading span.blocking {
  color: #a36120;
}
.order-card ul {
  display: grid;
  gap: 10px;
}
.order-card li {
  background: #f2f7f3;
  border-radius: 9px;
  padding: 9px;
}
.item-heading {
  color: #31594a;
  font-size: 0.84rem;
}
.item-heading span:last-child {
  color: #24784c;
  font-size: 0.75rem;
  font-weight: 800;
}
.result-chip {
  color: #60786d;
  font-size: 0.78rem;
  margin-top: 6px;
  overflow-wrap: anywhere;
}
.result-entry {
  display: flex;
  gap: 7px;
  margin-top: 7px;
}
.result-entry input {
  flex: 1;
  min-width: 0;
}
.order-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}
@media (max-width: 900px) {
  .technical-grid {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 560px) {
  .technical-header {
    align-items: start;
    flex-direction: column;
    gap: 14px;
  }
  .technical-grid {
    padding: 16px;
  }
}
</style>
