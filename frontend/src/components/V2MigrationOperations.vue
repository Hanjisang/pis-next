<script setup lang="ts">
import { ref } from 'vue';
import { friendlyError } from '../uiText';
import {
  addOperationsMigrationError,
  addOperationsMigrationRecord,
  createOperationsMigrationJob,
  type OperationsOverview,
} from '../v2BusinessOperationsApi';
defineProps<{ overview: OperationsOverview }>();
const emit = defineEmits<{ changed: [] }>();
const saving = ref(false);
const error = ref('');
const notice = ref('');
const job = ref({ sourceCode: '', modeCode: 'READ_ONLY', statusCode: 'READ_ONLY' });
const record = ref({
  jobId: '',
  legacyType: '',
  legacyKey: '',
  localType: '',
  localId: '',
  recordStatus: 'MAPPED',
  rawReference: '',
});
const failure = ref({ jobId: '', recordId: '', errorCode: '', errorMessage: '', retryCount: 0 });
async function run(action: () => Promise<unknown>, message: string) {
  saving.value = true;
  error.value = '';
  notice.value = '';
  try {
    await action();
    notice.value = message;
    emit('changed');
  } catch (requestError) {
    error.value = friendlyError(requestError, '迁移记录操作失败。');
  } finally {
    saving.value = false;
  }
}
</script>
<template>
  <section class="operations-workspace">
    <p v-if="error" class="feedback error">{{ error }}</p>
    <p v-if="notice" class="feedback success">{{ notice }}</p>
    <section class="workspace-panel">
      <header class="panel-title-row">
        <div>
          <p class="section-kicker">系统管理</p>
          <h3>数据迁移任务</h3>
        </div>
      </header>
      <p class="muted">迁移任务只记录来源、映射和错误，不直接绕过应用命令修改核心业务表。</p>
      <form
        class="operations-form"
        @submit.prevent="run(() => createOperationsMigrationJob(job), '迁移任务已创建。')"
      >
        <input v-model="job.sourceCode" required placeholder="来源编码" /><select
          v-model="job.modeCode"
        >
          <option value="READ_ONLY">只读核对</option>
          <option value="IMPORT">导入</option></select
        ><select v-model="job.statusCode">
          <option value="READ_ONLY">只读</option>
          <option value="CREATED">已创建</option>
          <option value="RUNNING">执行中</option></select
        ><button :disabled="saving">创建任务</button>
      </form>
      <form
        class="operations-form"
        @submit.prevent="
          run(
            () => addOperationsMigrationRecord({ ...record, localId: record.localId || null }),
            '映射记录已保存。',
          )
        "
      >
        <select v-model="record.jobId" required>
          <option value="">选择任务</option>
          <option
            v-for="item in overview.migrationJobs ?? []"
            :key="String(item.id)"
            :value="String(item.id)"
          >
            {{ item.sourceCode }} · {{ item.statusCode }}
          </option></select
        ><input v-model="record.legacyType" required placeholder="历史数据类型" /><input
          v-model="record.legacyKey"
          required
          placeholder="历史数据键"
        /><input v-model="record.localType" placeholder="本地对象类型" /><input
          v-model="record.localId"
          placeholder="本地记录标识"
        /><button :disabled="saving">保存映射</button>
      </form>
      <form
        class="operations-form"
        @submit.prevent="
          run(
            () => addOperationsMigrationError({ ...failure, recordId: failure.recordId || null }),
            '失败记录已保存。',
          )
        "
      >
        <select v-model="failure.jobId" required>
          <option value="">选择任务</option>
          <option
            v-for="item in overview.migrationJobs ?? []"
            :key="String(item.id)"
            :value="String(item.id)"
          >
            {{ item.sourceCode }}
          </option></select
        ><input v-model="failure.errorCode" required placeholder="错误编码" /><input
          v-model="failure.errorMessage"
          required
          placeholder="错误说明"
        /><button :disabled="saving">记录失败</button>
      </form>
      <div
        v-for="item in overview.migrationJobs ?? []"
        :key="String(item.id)"
        class="operations-row"
      >
        <div>
          <strong>{{ item.sourceCode }}</strong>
          <p>{{ item.modeCode }} · {{ item.createdAt }}</p>
        </div>
        <span>{{ item.recordCount }} 条映射 / {{ item.errorCount }} 条失败</span
        ><span class="status-pill">{{ item.statusCode }}</span>
      </div>
    </section>
  </section>
</template>
