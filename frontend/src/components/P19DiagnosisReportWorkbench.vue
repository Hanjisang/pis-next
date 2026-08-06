<script setup lang="ts">
import { ref } from 'vue';

import {
  approveP19Report,
  createP19DiagnosisTask,
  createP19Report,
  generateP19ReportContent,
  getP19DiagnosisQueue,
  getP19ReportQueue,
  requestP19Correction,
  requestP19Supplement,
  requestP19Withdrawal,
  saveP19DiagnosisDraft,
  signP19Report,
  submitP19Initial,
  submitP19ReportReview,
  takeoverP19DiagnosisTask,
  type P19CommandResult,
  type P19Report,
  type P19Task,
} from '../api';

const caseId = ref('');
const task = ref<P19Task | null>(null);
const opinionVersionId = ref('');
const report = ref<P19Report | null>(null);
const contentVersionId = ref('');
const microscopicDescription = ref('合成镜下描述：组织结构和细胞形态已完成责任核对。');
const diagnosisConclusion = ref('合成诊断结论：仅用于 local/test 工作流验证。');
const reviewerActorRef = ref('p19-independent-reviewer');
const tasks = ref<P19Task[]>([]);
const reports = ref<P19Report[]>([]);
const notice = ref('');
const errorMessage = ref('');
const busy = ref(false);

function key(action: string) {
  return `p19-ui-${action}-${caseId.value || 'draft'}-${Date.now()}`;
}

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

function refresh() {
  void run(async () => {
    [tasks.value, reports.value] = await Promise.all([getP19DiagnosisQueue(), getP19ReportQueue()]);
    notice.value = `已加载诊断任务 ${tasks.value.length} 条、报告 ${reports.value.length} 条`;
  });
}

function useCommand(result: P19CommandResult) {
  notice.value = `${result.objectKindCode} · ${result.stateCode} · v${result.businessVersion || result.concurrencyVersion}`;
}

function createTask() {
  void run(async () => {
    const result = await createP19DiagnosisTask({
      caseId: caseId.value,
      modalityCode: 'HISTOLOGY',
      categoryCode: 'INITIAL',
      priorityCode: 'ROUTINE',
      dataScopeCode: 'PATHOLOGY',
      idempotencyKey: key('create-task'),
    });
    task.value = {
      id: result.objectId,
      taskNo: result.businessNo ?? result.objectId,
      caseId: caseId.value,
      modalityCode: 'HISTOLOGY',
      categoryCode: 'INITIAL',
      priorityCode: 'ROUTINE',
      stateCode: result.stateCode,
      assignedActor: null,
      responsibleActor: null,
      organizationReference: 'LOCAL_HOSPITAL',
      dataScopeCode: 'PATHOLOGY',
      version: result.concurrencyVersion,
    };
    useCommand(result);
  });
}

function takeover() {
  if (!task.value) return;
  void run(async () => {
    const result = await takeoverP19DiagnosisTask(
      task.value?.id ?? '',
      task.value?.version ?? 0,
      key('takeover'),
    );
    task.value = {
      ...task.value!,
      stateCode: result.stateCode,
      responsibleActor: 'p15-local-registration-actor',
      assignedActor: 'p15-local-registration-actor',
      version: result.concurrencyVersion,
    };
    useCommand(result);
  });
}

function saveDraft() {
  if (!task.value) return;
  void run(async () => {
    const result = await saveP19DiagnosisDraft(task.value?.id ?? '', {
      microscopicDescription: microscopicDescription.value,
      diagnosisConclusion: diagnosisConclusion.value,
      expectedVersion: task.value?.version ?? 0,
      idempotencyKey: key('save-draft'),
    });
    useCommand(result);
  });
}

function submitInitial() {
  if (!task.value) return;
  void run(async () => {
    const result = await submitP19Initial(
      task.value?.id ?? '',
      task.value?.version ?? 0,
      key('submit-initial'),
    );
    opinionVersionId.value = result.relatedObjectId ?? '';
    task.value = {
      ...task.value!,
      stateCode: result.stateCode,
      version: result.concurrencyVersion,
    };
    useCommand(result);
  });
}

function createReportDraft() {
  if (!task.value || !opinionVersionId.value) return;
  void run(async () => {
    const result = await createP19Report(
      task.value?.id ?? '',
      opinionVersionId.value,
      key('create-report'),
    );
    report.value = {
      id: result.objectId,
      reportNo: result.businessNo ?? result.objectId,
      caseId: caseId.value,
      reportType: 'HISTOPATHOLOGY',
      stateCode: result.stateCode,
      currentVersionId: null,
      nextVersionNo: 1,
      version: result.concurrencyVersion,
    };
    useCommand(result);
  });
}

function generateReport() {
  if (!report.value || !opinionVersionId.value) return;
  void run(async () => {
    const result = await generateP19ReportContent(report.value?.id ?? '', {
      diagnosisVersionId: opinionVersionId.value,
      patientSnapshot: '合成患者快照',
      encounterSnapshot: '合成就诊快照',
      caseNoSnapshot: caseId.value,
      specimenMaterialSummary: '合成标本与材料追溯摘要',
      diagnosisConclusion: diagnosisConclusion.value,
      idempotencyKey: key('generate-report'),
    });
    contentVersionId.value = result.relatedObjectId ?? '';
    report.value = {
      ...report.value!,
      stateCode: 'IN-REVIEW',
      currentVersionId: contentVersionId.value,
      version: result.concurrencyVersion,
    };
    useCommand(result);
  });
}

function submitReview() {
  if (!contentVersionId.value) return;
  void run(async () =>
    useCommand(
      await submitP19ReportReview(
        contentVersionId.value,
        reviewerActorRef.value,
        key('submit-review'),
      ),
    ),
  );
}

function approveReview() {
  if (!contentVersionId.value) return;
  void run(async () =>
    useCommand(
      await approveP19Report(contentVersionId.value, reviewerActorRef.value, key('approve-review')),
    ),
  );
}

function signReport() {
  if (!contentVersionId.value || !report.value) return;
  void run(async () => {
    const result = await signP19Report(
      contentVersionId.value,
      reviewerActorRef.value,
      report.value?.version ?? 0,
      key('sign-report'),
    );
    report.value = {
      ...report.value!,
      stateCode: result.stateCode,
      currentVersionId: contentVersionId.value,
      version: result.concurrencyVersion,
    };
    useCommand(result);
  });
}

function requestSupplement() {
  if (!report.value) return;
  void run(async () =>
    useCommand(await requestP19Supplement(report.value!.id, '补充说明请求', key('supplement'))),
  );
}

function requestCorrection() {
  if (!report.value) return;
  void run(async () =>
    useCommand(
      await requestP19Correction(report.value!.id, 'CONTENT_ERROR', '更正原因', key('correction')),
    ),
  );
}

function requestWithdrawal() {
  if (!report.value) return;
  void run(async () =>
    useCommand(await requestP19Withdrawal(report.value!.id, '撤回原因', key('withdrawal'))),
  );
}
</script>

<template>
  <!-- eslint-disable vue/html-closing-bracket-newline, vue/multiline-html-element-content-newline, vue/html-indent -->
  <section class="p19-workbench" aria-label="P19 诊断与报告工作台">
    <div class="section-heading">
      <div>
        <p class="eyebrow">P19 DIAGNOSIS &amp; REPORT</p>
        <h2>诊断与报告工作台</h2>
      </div>
      <span class="status-dot">责任 · 版本 · 独立复核 · 签发事实</span>
    </div>
    <p v-if="errorMessage" class="error-banner" role="alert">{{ errorMessage }}</p>
    <p v-if="notice" class="notice-banner" role="status">{{ notice }}</p>
    <div class="p19-toolbar">
      <label>病例内部ID<input v-model="caseId" required autocomplete="off" /></label>
      <label>独立复核主体<input v-model="reviewerActorRef" required autocomplete="off" /></label>
      <button :disabled="busy" type="button" @click="refresh">刷新诊断与报告队列</button>
    </div>
    <div class="p19-grid">
      <section class="p19-card">
        <span class="step-label">01 · 诊断责任</span>
        <h3>建立、接管与诊断意见</h3>
        <p>草稿不等于医学事实；提交初诊才形成不可变诊断意见版本。</p>
        <button :disabled="busy || !caseId" type="button" @click="createTask">建立诊断任务</button
        ><button :disabled="busy || !task" type="button" @click="takeover">接管诊断任务</button
        ><textarea v-model="microscopicDescription" aria-label="镜下描述" /><textarea
          v-model="diagnosisConclusion"
          aria-label="诊断结论"
        /><button :disabled="busy || !task" type="button" @click="saveDraft">保存诊断草稿</button
        ><button :disabled="busy || !task" type="button" @click="submitInitial">提交初诊</button
        ><output v-if="task">{{ task.taskNo }} · {{ task.stateCode }} · v{{ task.version }}</output>
      </section>
      <section class="p19-card">
        <span class="step-label">02 · 报告版本</span>
        <h3>生成报告内容快照</h3>
        <p>报告版本固定患者、就诊、病例、标本材料、诊断和技术结果引用。</p>
        <button
          :disabled="busy || !task || !opinionVersionId"
          type="button"
          @click="createReportDraft"
        >
          建立报告草稿</button
        ><button :disabled="busy || !report" type="button" @click="generateReport">
          生成报告内容版本</button
        ><button :disabled="busy || !contentVersionId" type="button" @click="submitReview">
          提交独立审核</button
        ><button :disabled="busy || !contentVersionId" type="button" @click="approveReview">
          审核通过</button
        ><output v-if="report"
          >{{ report.reportNo }} · {{ report.stateCode }} · v{{ report.version }}</output
        >
      </section>
      <section class="p19-card">
        <span class="step-label">03 · 高风险签发</span>
        <h3>增强认证与签发事实</h3>
        <p>服务端重新校验版本、技术医嘱阻断、独立复核和增强认证后，才允许签发。</p>
        <button
          class="primary-action"
          :disabled="busy || !contentVersionId || !report"
          type="button"
          @click="signReport"
        >
          签发报告
        </button>
        <div class="p19-secondary-actions">
          <button
            :disabled="busy || !report || report.stateCode !== 'SIGNED'"
            type="button"
            @click="requestSupplement"
          >
            申请补充
          </button>
          <button
            :disabled="busy || !report || report.stateCode !== 'SIGNED'"
            type="button"
            @click="requestCorrection"
          >
            申请更正
          </button>
          <button
            :disabled="busy || !report || report.stateCode !== 'SIGNED'"
            type="button"
            @click="requestWithdrawal"
          >
            申请撤回
          </button>
        </div>
        <ul>
          <li>诊断版本：{{ opinionVersionId || '待形成' }}</li>
          <li>报告内容版本：{{ contentVersionId || '待形成' }}</li>
          <li>签后：原版本只读，修订必须形成新版本</li>
        </ul>
      </section>
    </div>
    <div v-if="tasks.length || reports.length" class="p19-queue">
      <h3>队列摘要</h3>
      <table>
        <thead>
          <tr>
            <th>类型</th>
            <th>业务编号</th>
            <th>病例</th>
            <th>状态</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in tasks" :key="item.id">
            <td>诊断任务</td>
            <td>{{ item.taskNo }}</td>
            <td>{{ item.caseId }}</td>
            <td>{{ item.stateCode }}</td>
          </tr>
          <tr v-for="item in reports" :key="item.id">
            <td>报告</td>
            <td>{{ item.reportNo }}</td>
            <td>{{ item.caseId }}</td>
            <td>{{ item.stateCode }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
  <!-- eslint-enable vue/html-closing-bracket-newline, vue/multiline-html-element-content-newline, vue/html-indent -->
</template>
