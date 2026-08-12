<script setup lang="ts">
import { ref } from 'vue';
import { friendlyError } from '../uiText';
import {
  archiveOperationsDigitalSlide,
  completeOperationsMolecularTest,
  createOperationsMolecularInstrument,
  createOperationsMolecularProject,
  createOperationsMolecularReagent,
  createOperationsMolecularTest,
  createOperationsRegionalShare,
  recordOperationsIncome,
  recordOperationsRegionalAccess,
  updateOperationsDigitalArchive,
  type OperationsOverview,
} from '../v2BusinessOperationsApi';

defineProps<{ overview: OperationsOverview }>();
const emit = defineEmits<{ changed: [] }>();
type Section = 'molecular' | 'archive' | 'regional' | 'income';
const section = ref<Section>('molecular');
const saving = ref(false);
const error = ref('');
const notice = ref('');
const project = ref({ projectCode: '', projectName: '', projectTypeCode: 'PCR' });
const instrument = ref({ instrumentCode: '', name: '', adapterCode: 'SIMULATOR' });
const reagent = ref({ kitCode: '', manufacturer: '', batchNo: '', expiryDate: '' });
const molecular = ref({ caseId: '', projectId: '', detectionNo: '', structuredResult: '' });
const completion = ref({ id: '', structuredResult: '', analysisResult: '' });
const archive = ref({
  digitalSlideId: '',
  storagePath: '',
  storageTier: 'ONLINE',
  filename: '',
  formatCode: 'SVS',
  pathologyNo: '',
  slideNo: '',
  integrityDigest: '',
});
const share = ref({
  caseId: '',
  receivingOrganization: '',
  receivingDoctor: '',
  expiresAt: '',
  patientAuthorized: false,
  items: [],
});
const access = ref({ id: '', accessorReference: '', actionCode: 'VIEW' });
const income = ref({ caseId: '', projectCode: '', amount: 0, sourceReference: '' });
async function run(action: () => Promise<unknown>, message: string) {
  saving.value = true;
  error.value = '';
  notice.value = '';
  try {
    await action();
    notice.value = message;
    emit('changed');
  } catch (requestError) {
    error.value = friendlyError(requestError, '操作失败，请核对记录。');
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <section class="operations-workspace">
    <p v-if="error" class="feedback error">{{ error }}</p>
    <p v-if="notice" class="feedback success">{{ notice }}</p>
    <nav class="operations-section-tabs">
      <button :class="{ active: section === 'molecular' }" @click="section = 'molecular'">
        分子病理</button
      ><button :class="{ active: section === 'archive' }" @click="section = 'archive'">
        数字归档</button
      ><button :class="{ active: section === 'regional' }" @click="section = 'regional'">
        区域共享</button
      ><button :class="{ active: section === 'income' }" @click="section = 'income'">
        收入事实
      </button>
    </nav>
    <section v-if="section === 'molecular'" class="workspace-panel">
      <h3>分子病理支持</h3>
      <form
        class="operations-form"
        @submit.prevent="run(() => createOperationsMolecularProject(project), '分子项目已保存。')"
      >
        <input v-model="project.projectCode" required placeholder="项目编码" /><input
          v-model="project.projectName"
          required
          placeholder="项目名称"
        /><input v-model="project.projectTypeCode" required placeholder="项目类型" /><button
          :disabled="saving"
        >
          新增项目
        </button>
      </form>
      <form
        class="operations-form"
        @submit.prevent="
          run(() => createOperationsMolecularInstrument(instrument), '分子设备已保存。')
        "
      >
        <input v-model="instrument.instrumentCode" required placeholder="设备编码" /><input
          v-model="instrument.name"
          required
          placeholder="设备名称"
        /><input v-model="instrument.adapterCode" required placeholder="连接方式" /><button
          :disabled="saving"
        >
          新增设备
        </button>
      </form>
      <form
        class="operations-form"
        @submit.prevent="run(() => createOperationsMolecularReagent(reagent), '试剂批次已保存。')"
      >
        <input v-model="reagent.kitCode" required placeholder="试剂盒编码" /><input
          v-model="reagent.manufacturer"
          placeholder="厂家"
        /><input v-model="reagent.batchNo" required placeholder="批号" /><input
          v-model="reagent.expiryDate"
          type="date"
        /><button :disabled="saving">新增试剂</button>
      </form>
      <form
        class="operations-form"
        @submit.prevent="run(() => createOperationsMolecularTest(molecular), '分子检测已登记。')"
      >
        <input v-model="molecular.caseId" required placeholder="病例记录标识" /><select
          v-model="molecular.projectId"
          required
        >
          <option value="">选择项目</option>
          <option
            v-for="item in overview.molecularProjects ?? []"
            :key="String(item.id)"
            :value="String(item.id)"
          >
            {{ item.projectCode }} · {{ item.projectName }}
          </option></select
        ><input v-model="molecular.detectionNo" required placeholder="检测编号" /><button
          :disabled="saving"
        >
          登记检测
        </button>
      </form>
      <form
        class="operations-form"
        @submit.prevent="
          run(() => completeOperationsMolecularTest(completion.id, completion), '分子结果已保存。')
        "
      >
        <select v-model="completion.id" required>
          <option value="">选择待完成检测</option>
          <option
            v-for="item in overview.molecularTests ?? []"
            :key="String(item.id)"
            :value="String(item.id)"
          >
            {{ item.detectionNo }} · {{ item.statusCode }}
          </option></select
        ><input v-model="completion.structuredResult" required placeholder="结构化结果" /><input
          v-model="completion.analysisResult"
          required
          placeholder="分析结论"
        /><button :disabled="saving">完成检测</button>
      </form>
      <div
        v-for="item in overview.molecularTests ?? []"
        :key="String(item.id)"
        class="operations-row"
      >
        <div>
          <strong>{{ item.detectionNo }}</strong>
          <p>{{ item.analysisResult || '待录结果' }}</p>
        </div>
        <span class="status-pill">{{ item.statusCode }}</span>
      </div>
    </section>
    <section v-else-if="section === 'archive'" class="workspace-panel">
      <h3>数字切片归档</h3>
      <form
        class="operations-form"
        @submit.prevent="
          run(() => archiveOperationsDigitalSlide(archive), '数字切片归档记录已保存。')
        "
      >
        <input v-model="archive.digitalSlideId" required placeholder="数字切片记录标识" /><input
          v-model="archive.pathologyNo"
          placeholder="病理号"
        /><input v-model="archive.slideNo" placeholder="玻片号" /><input
          v-model="archive.storagePath"
          required
          placeholder="存储路径"
        /><input v-model="archive.filename" required placeholder="文件名" /><button
          :disabled="saving"
        >
          归档
        </button>
      </form>
      <div
        v-for="item in overview.digitalArchives ?? []"
        :key="String(item.id)"
        class="operations-row"
      >
        <div>
          <strong
            >{{ item.pathologyNo || '未填病理号' }} · {{ item.slideNo || '未填玻片号' }}</strong
          >
          <p>{{ item.storageTier }} · {{ item.importedAt }}</p>
        </div>
        <span class="status-pill">{{ item.statusCode }}</span
        ><button
          v-if="item.statusCode === 'ARCHIVED'"
          class="text-button"
          @click="
            run(
              () => updateOperationsDigitalArchive(String(item.id), 'RESTORED'),
              '恢复记录已保存。',
            )
          "
        >
          恢复
        </button>
      </div>
    </section>
    <section v-else-if="section === 'regional'" class="workspace-panel">
      <h3>区域共享</h3>
      <form
        class="operations-form"
        @submit.prevent="
          run(
            () => createOperationsRegionalShare({ ...share, expiresAt: share.expiresAt || null }),
            '区域共享已创建。',
          )
        "
      >
        <input v-model="share.caseId" required placeholder="病例记录标识" /><input
          v-model="share.receivingOrganization"
          required
          placeholder="接收机构"
        /><input v-model="share.receivingDoctor" placeholder="接收医生" /><input
          v-model="share.expiresAt"
          type="datetime-local"
        /><label class="inline-checkbox"
          ><input v-model="share.patientAuthorized" type="checkbox" />已记录患者授权</label
        ><button :disabled="saving">创建共享</button>
      </form>
      <form
        class="operations-form"
        @submit.prevent="
          run(
            () =>
              recordOperationsRegionalAccess(
                access.id,
                access.accessorReference,
                access.actionCode,
              ),
            '访问审计已保存。',
          )
        "
      >
        <select v-model="access.id" required>
          <option value="">选择共享记录</option>
          <option
            v-for="item in overview.regionalShares ?? []"
            :key="String(item.id)"
            :value="String(item.id)"
          >
            {{ item.receivingOrganization }} · {{ item.statusCode }}
          </option></select
        ><input v-model="access.accessorReference" required placeholder="访问人" /><select
          v-model="access.actionCode"
        >
          <option value="VIEW">查看</option>
          <option value="DOWNLOAD">下载</option></select
        ><button :disabled="saving">记录访问</button>
      </form>
      <div
        v-for="item in overview.regionalShares ?? []"
        :key="String(item.id)"
        class="operations-row"
      >
        <div>
          <strong>{{ item.receivingOrganization }}</strong>
          <p>{{ item.receivingDoctor || '未指定医生' }} · 截止 {{ item.expiresAt || '未设置' }}</p>
        </div>
        <span class="status-pill">{{ item.statusCode }}</span>
      </div>
    </section>
    <section v-else class="workspace-panel">
      <h3>收入事实</h3>
      <p class="muted">仅记录外部收费来源事实，不形成财务总账。</p>
      <form
        class="operations-form"
        @submit.prevent="
          run(
            () => recordOperationsIncome({ ...income, caseId: income.caseId || null }),
            '收入事实已记录。',
          )
        "
      >
        <input v-model="income.caseId" placeholder="病例记录标识（可选）" /><input
          v-model="income.projectCode"
          required
          placeholder="收费项目"
        /><input v-model.number="income.amount" type="number" min="0" step="0.01" /><input
          v-model="income.sourceReference"
          required
          placeholder="收费来源"
        /><button :disabled="saving">记录</button>
      </form>
      <div v-for="item in overview.income ?? []" :key="String(item.id)" class="operations-row">
        <div>
          <strong>{{ item.projectCode }}</strong>
          <p>{{ item.sourceReference }} · {{ item.occurredAt }}</p>
        </div>
        <strong>¥ {{ item.amount }}</strong>
      </div>
    </section>
  </section>
</template>
