<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { friendlyError } from '../uiText';
import {
  addMolecularAttachment,
  completeMolecularTest,
  createMolecularTest,
  getMolecularWorkbench,
  startMolecularTest,
  type MolecularWorkbench,
} from '../v2MolecularApi';

const data = ref<MolecularWorkbench | null>(null);
const loading = ref(false);
const error = ref('');
const notice = ref('');
const application = ref({
  caseId: '',
  specimenId: '',
  projectId: '',
  detectionNo: '',
  instrumentId: '',
  reagentKitId: '',
  rawDataReference: '',
});
const completion = ref({ id: '', structuredResult: '', analysisResult: '' });
const attachment = ref({ id: '', digitalSlideId: '', attachmentReference: '', description: '' });
const pending = computed(
  () => data.value?.tests.filter((item) => item.statusCode !== 'COMPLETED') ?? [],
);
const key = (prefix: string) => `${prefix}-${crypto.randomUUID()}`;
async function load() {
  loading.value = true;
  error.value = '';
  try {
    data.value = await getMolecularWorkbench();
  } catch (reason) {
    error.value = friendlyError(reason, '分子检测队列暂时无法加载。');
  } finally {
    loading.value = false;
  }
}
async function run(action: () => Promise<unknown>, message: string) {
  loading.value = true;
  error.value = '';
  notice.value = '';
  try {
    await action();
    notice.value = message;
    await load();
  } catch (reason) {
    error.value = friendlyError(reason, '分子病理操作失败，请核对状态和绑定信息。');
  } finally {
    loading.value = false;
  }
}
onMounted(load);
</script>

<template>
  <section class="workspace-shell molecular-workbench" aria-label="分子病理工作台">
    <header class="workspace-heading">
      <div>
        <p class="section-kicker">Molecular</p>
        <h1>分子病理工作台</h1>
        <p>申请、设备执行、结果与支持材料使用同一检测链。</p>
      </div>
      <button class="secondary-button" :disabled="loading" @click="load">刷新</button>
    </header>
    <p v-if="error" class="feedback error" role="alert">{{ error }}</p>
    <p v-if="notice" class="feedback success">{{ notice }}</p>
    <section class="workspace-panel">
      <h2>新建分子检测申请</h2>
      <form
        class="operations-form"
        @submit.prevent="
          run(
            () =>
              createMolecularTest({
                ...application,
                detectionNo: application.detectionNo || null,
                idempotencyKey: key('create'),
              }),
            '分子检测申请已登记。',
          )
        "
      >
        <input v-model="application.caseId" required placeholder="病例记录标识" /><input
          v-model="application.specimenId"
          required
          placeholder="标本记录标识"
        />
        <select v-model="application.projectId" required>
          <option value="">选择检测项目</option>
          <option v-for="item in data?.projects ?? []" :key="item.id" :value="item.id">
            {{ item.projectCode }} · {{ item.projectName }}
          </option>
        </select>
        <select v-model="application.instrumentId" required>
          <option value="">选择设备</option>
          <option v-for="item in data?.instruments ?? []" :key="item.id" :value="item.id">
            {{ item.instrumentCode }} · {{ item.name }} · {{ item.adapterCode }}
          </option>
        </select>
        <select v-model="application.reagentKitId" required>
          <option value="">选择试剂批次</option>
          <option v-for="item in data?.reagents ?? []" :key="item.id" :value="item.id">
            {{ item.kitCode }} · {{ item.batchNo }} · {{ item.expiryDate || '无效期' }}
          </option>
        </select>
        <input v-model="application.detectionNo" placeholder="检测号（留空自动生成）" /><input
          v-model="application.rawDataReference"
          required
          placeholder="原始数据引用"
        /><button :disabled="loading">登记</button>
      </form>
    </section>
    <section class="workspace-panel">
      <h2>待处理队列</h2>
      <article v-for="item in pending" :key="item.id" class="operations-row">
        <div>
          <strong>{{ item.detectionNo }} · {{ item.projectCode }}</strong>
          <p>{{ item.instrumentCode }} · {{ item.rawDataReference }}</p>
        </div>
        <span class="status-pill">{{ item.statusCode }}</span
        ><button
          v-if="item.statusCode === 'REQUESTED'"
          :disabled="loading"
          @click="run(() => startMolecularTest(item.id, key('start')), '设备已接收检测。')"
        >
          启动
        </button>
      </article>
      <p v-if="!pending.length" class="empty-state">当前没有待处理分子检测。</p>
    </section>
    <section class="workspace-panel">
      <h2>完成检测</h2>
      <form
        class="operations-form"
        @submit.prevent="
          run(
            () =>
              completeMolecularTest(completion.id, {
                structuredResult: completion.structuredResult,
                analysisResult: completion.analysisResult,
                idempotencyKey: key('complete'),
              }),
            '结果已保存并进入诊断与报告链。',
          )
        "
      >
        <select v-model="completion.id" required>
          <option value="">选择执行中检测</option>
          <option
            v-for="item in data?.tests.filter((row) => row.statusCode === 'RUNNING') ?? []"
            :key="item.id"
            :value="item.id"
          >
            {{ item.detectionNo }}
          </option></select
        ><textarea
          v-model="completion.structuredResult"
          required
          placeholder="结构化结果"
        ></textarea
        ><textarea v-model="completion.analysisResult" required placeholder="分析结果"></textarea
        ><button :disabled="loading">完成并生成结果</button>
      </form>
    </section>
    <section class="workspace-panel">
      <h2>关联数字切片或附件</h2>
      <form
        class="operations-form"
        @submit.prevent="
          run(
            () =>
              addMolecularAttachment(attachment.id, {
                digitalSlideId: attachment.digitalSlideId || null,
                attachmentReference: attachment.attachmentReference || null,
                description: attachment.description,
              }),
            '支持材料已关联。',
          )
        "
      >
        <select v-model="attachment.id" required>
          <option value="">选择检测</option>
          <option v-for="item in data?.tests ?? []" :key="item.id" :value="item.id">
            {{ item.detectionNo }}
          </option></select
        ><input v-model="attachment.digitalSlideId" placeholder="数字切片标识（二选一）" /><input
          v-model="attachment.attachmentReference"
          placeholder="附件引用（二选一）"
        /><input v-model="attachment.description" placeholder="说明" /><button :disabled="loading">
          关联
        </button>
      </form>
    </section>
    <section class="workspace-panel">
      <h2>全部检测</h2>
      <article v-for="item in data?.tests ?? []" :key="item.id" class="operations-row">
        <div>
          <strong>{{ item.detectionNo }}</strong>
          <p>
            {{ item.structuredResult || '待录入结构化结果' }} ·
            {{ item.analysisResult || '待分析' }}
          </p>
        </div>
        <span class="status-pill">{{ item.statusCode }}</span>
      </article>
    </section>
    <section class="workspace-panel">
      <h2>设备执行记录</h2>
      <article v-for="item in data?.attempts ?? []" :key="item.id" class="operations-row">
        <div>
          <strong>{{ item.adapterCode }} · 第 {{ item.attemptNo }} 次</strong>
          <p>{{ item.responseReference || item.errorMessage || item.requestReference }}</p>
        </div>
        <span class="status-pill">{{ item.statusCode }}</span>
      </article>
      <p v-if="!data?.attempts?.length" class="empty-state">暂无设备执行记录。</p>
    </section>
    <section class="workspace-panel">
      <h2>支持材料</h2>
      <article v-for="item in data?.attachments ?? []" :key="item.id" class="operations-row">
        <div>
          <strong>{{ item.digitalSlideId ? '数字切片' : '附件' }}</strong>
          <p>
            {{ item.digitalSlideId || item.attachmentReference }} ·
            {{ item.description || '无说明' }}
          </p>
        </div>
      </article>
      <p v-if="!data?.attachments?.length" class="empty-state">暂无支持材料。</p>
    </section>
  </section>
</template>
