<script setup lang="ts">
import { computed, ref } from 'vue';

import {
  createTechnicalOrder,
  getTechnicalOrders,
  submitTechnicalOrder,
  type TechnicalOrderResult,
} from '../api';

const caseId = ref('');
const actualBlockFormationId = ref('');
const projectTypeCode = ref('DEEP_SECTION');
const priorityCode = ref('ROUTINE');
const usageCode = ref('DIAGNOSTIC_SUPPORT');
const reasonText = ref('合成技术医嘱演示：为后续诊断提供技术材料');
const plannedQuantity = ref(1);
const plannedLabelQuantity = ref(1);
const orders = ref<Record<string, unknown>[]>([]);
const order = ref<TechnicalOrderResult | null>(null);
const errorMessage = ref('');
const notice = ref('');
const busy = ref(false);

const projectConfig = computed(() => `P18-SYNTHETIC-${projectTypeCode.value.replace('_', '-')}`);

function commandKey(action: string) {
  return `p18-ui-${action}-${caseId.value || 'draft'}-${actualBlockFormationId.value || 'target'}`;
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

function refresh() {
  void runCommand(async () => {
    orders.value = await getTechnicalOrders();
    notice.value = `已加载 ${orders.value.length} 条技术医嘱`;
  });
}

function createOrder() {
  void runCommand(async () => {
    order.value = await createTechnicalOrder({
      caseId: caseId.value,
      orderKindCode: 'TECHNICAL_ORDER',
      priorityCode: priorityCode.value,
      reasonText: reasonText.value,
      projects: [
        {
          projectCode: projectConfig.value,
          versionLabel: 'SYNTHETIC-1',
          projectTypeCode: projectTypeCode.value,
          actualBlockFormationId: actualBlockFormationId.value,
          usageCode: usageCode.value,
          reasonText: reasonText.value,
          plannedOutputs: [
            {
              sequenceNo: 1,
              outputKindCode: 'PLANNED_SLIDE',
              slidePurposeCode: projectTypeCode.value,
              plannedQuantity: plannedQuantity.value,
              plannedUsageCode: usageCode.value,
              plannedLabelQuantity: plannedLabelQuantity.value,
              executionNote: '仅计划产物；不代表实际玻片已经形成',
            },
          ],
        },
      ],
      idempotencyKey: commandKey('create-order'),
    });
    notice.value = '技术医嘱已建立；尚未提交，也未形成实际玻片或染色事实';
  });
}

function submitOrder() {
  if (!order.value) return;
  void runCommand(async () => {
    order.value = await submitTechnicalOrder(
      order.value?.orderId ?? '',
      order.value?.concurrencyVersion ?? 0,
      commandKey('submit-order'),
    );
    notice.value = '技术医嘱已提交；等待审核、受理和执行责任交接';
  });
}
</script>

<template>
  <!-- eslint-disable vue/html-closing-bracket-newline, vue/html-indent, vue/max-attributes-per-line -->
  <section class="p18-workbench" aria-label="P18 技术医嘱工作台">
    <div class="section-heading">
      <div>
        <p class="eyebrow">P18 TECHNICAL ORDER</p>
        <h2>技术医嘱工作台</h2>
      </div>
      <span class="status-dot">目标 · 配置快照 · 计划产物 · 责任链</span>
    </div>

    <p v-if="errorMessage" class="error-banner" role="alert">{{ errorMessage }}</p>
    <p v-if="notice" class="notice-banner" role="status">{{ notice }}</p>

    <div class="p18-toolbar">
      <label
        >病例内部ID<input v-model="caseId" required autocomplete="off" @keyup.enter="refresh"
      /></label>
      <label
        >实际蜡块形成事实ID<input
          v-model="actualBlockFormationId"
          required
          autocomplete="off"
          @keyup.enter="createOrder"
      /></label>
      <button :disabled="busy" type="button" @click="refresh">刷新医嘱队列</button>
    </div>

    <div class="p18-grid">
      <section class="p18-card">
        <span class="step-label">01 · 开立</span>
        <h3>建立技术医嘱项目</h3>
        <p>目标必须是当前范围内已形成且有效的 P17 实际蜡块；病例级医嘱不能直接执行。</p>
        <label
          >技术项目
          <select v-model="projectTypeCode">
            <option value="DEEP_SECTION">深切</option>
            <option value="RECUT">重切</option>
            <option value="WHITE_SLIDE">白片</option>
            <option value="IHC">免疫组化</option>
            <option value="SPECIAL_STAIN">特殊染色</option>
          </select>
        </label>
        <label>优先级<input v-model="priorityCode" /></label>
        <label>用途<input v-model="usageCode" /></label>
        <label>原因<textarea v-model="reasonText" required /></label>
        <label>计划数量<input v-model.number="plannedQuantity" min="1" type="number" /></label>
        <label
          >计划标签数量<input v-model.number="plannedLabelQuantity" min="0" type="number"
        /></label>
        <button
          :disabled="busy || !caseId || !actualBlockFormationId"
          type="button"
          @click="createOrder"
        >
          建立合成技术医嘱
        </button>
        <output v-if="order"
          >{{ order.orderNo }} · {{ order.stateCode }} · v{{ order.concurrencyVersion }}</output
        >
      </section>

      <section class="p18-card">
        <span class="step-label">02 · 提交</span>
        <h3>提交并进入审核</h3>
        <p>提交只改变医嘱工作流事实；不表示实际切片、IHC、特殊染色或技术结果已经形成。</p>
        <button
          :disabled="busy || !order || order.stateCode !== 'DRAFT'"
          type="button"
          @click="submitOrder"
        >
          正式提交医嘱
        </button>
        <output v-if="order"
          >项目 {{ order.projects.length }} 个 · {{ order.projects[0]?.reviewStateCode }}</output
        >
        <output>配置快照：{{ projectConfig }}</output>
      </section>

      <section class="p18-card">
        <span class="step-label">03 · 追溯边界</span>
        <h3>计划与实际分离</h3>
        <p>
          计划玻片和计划染色只描述需求。实际玻片、设备运行、染色、封片、技术质控和医学判读属于后续阶段。
        </p>
        <ul>
          <li>目标：{{ order?.projects[0]?.actualBlockFormationId ?? '待绑定' }}</li>
          <li>任务状态：{{ order?.projects[0]?.taskStateCode ?? '待提出' }}</li>
          <li>结果引用：{{ order?.projects[0]?.resultStateCode ?? '未关联' }}</li>
        </ul>
      </section>
    </div>

    <div v-if="orders.length" class="p18-queue" aria-label="技术医嘱列表">
      <h3>技术医嘱列表</h3>
      <table>
        <thead>
          <tr>
            <th>医嘱编号</th>
            <th>病例</th>
            <th>状态</th>
            <th>项目数</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in orders" :key="String(item.id)">
            <td>{{ item.technicalOrderNo }}</td>
            <td>{{ item.caseId }}</td>
            <td>{{ item.stateCode }}</td>
            <td>{{ item.projectCount }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
  <!-- eslint-enable vue/html-closing-bracket-newline, vue/html-indent, vue/max-attributes-per-line -->
</template>
