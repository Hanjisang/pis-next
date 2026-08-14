<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { friendlyError, formatDateTime } from '../uiText';
import {
  acknowledgeOperationsCriticalValue,
  addOperationsPackageEvent,
  createOperationsAddress,
  createOperationsCriticalValue,
  createOperationsPackage,
  distributeOperationsReport,
  feedbackOperationsCriticalValue,
  getOperationsAddresses,
  getOperationsCriticalValues,
  getOperationsReportDistributions,
  getOperationsReportPrinterStatus,
  getOperationsReportPrints,
  notifyOperationsCriticalValue,
  printOperationsReport,
  updateOperationsDistribution,
  type OperationsOverview,
  type OperationsReportDistribution,
  type OperationsReportPrint,
  type OperationsRow,
} from '../v2BusinessOperationsApi';

defineProps<{ overview: OperationsOverview }>();
const emit = defineEmits<{ changed: [] }>();
type Section = 'critical' | 'distribution' | 'logistics';
const section = ref<Section>('critical');
const saving = ref(false);
const error = ref('');
const notice = ref('');
const criticalValues = ref<OperationsRow[]>([]);
const addresses = ref<OperationsRow[]>([]);
const critical = ref({ caseId: '', valueTypeCode: '', gradeCode: 'HIGH', triggerReference: '' });
const criticalAction = ref({
  criticalId: '',
  recipientReference: '',
  departmentReference: '',
  methodCode: 'PHONE',
  message: '',
  notificationId: '',
  feedback: '',
});
const distribution = ref({ reportId: '', targetCode: 'SIMULATOR_PATIENT_PORTAL' });
const reportPrint = ref({
  identityReference: '',
  terminalReference: 'SELF-SERVICE-01',
  printerReference: 'MOCK://REPORT-PRINTER',
  copyCount: 1,
});
const reportDistributions = ref<OperationsReportDistribution[]>([]);
const reportPrints = ref<OperationsReportPrint[]>([]);
const printerStatus = ref('');
const address = ref({ addressName: '', recipientName: '', phone: '', addressText: '' });
const parcel = ref({
  caseId: '',
  courierCompany: '',
  trackingNo: '',
  senderReference: '',
  recipientReference: '',
  addressText: '',
  items: [],
});
const parcelEvent = ref({ packageId: '', statusCode: 'SENT', note: '' });
async function load() {
  [criticalValues.value, addresses.value] = await Promise.all([
    getOperationsCriticalValues(),
    getOperationsAddresses(),
  ]);
}
async function run(action: () => Promise<unknown>, message: string) {
  saving.value = true;
  error.value = '';
  notice.value = '';
  try {
    await action();
    notice.value = message;
    await load();
    emit('changed');
  } catch (requestError) {
    error.value = friendlyError(requestError, '操作失败，请核对业务记录。');
  } finally {
    saving.value = false;
  }
}
function saveCritical() {
  return run(
    () => createOperationsCriticalValue(critical.value.caseId, critical.value),
    '危急值已登记。',
  );
}
async function notifyCritical() {
  saving.value = true;
  error.value = '';
  try {
    const result = await notifyOperationsCriticalValue(criticalAction.value.criticalId, {
      ...criticalAction.value,
      businessPath: `/v2/cases/${critical.value.caseId}`,
    });
    criticalAction.value.notificationId = result.id;
    notice.value = '危急值已记录通知时间和接收人。';
    await load();
  } catch (requestError) {
    error.value = friendlyError(requestError, '危急值通知失败。');
  } finally {
    saving.value = false;
  }
}
function acknowledge() {
  return run(
    () => acknowledgeOperationsCriticalValue(criticalAction.value.notificationId),
    '危急值通知已确认。',
  );
}
function feedback() {
  return run(
    () =>
      feedbackOperationsCriticalValue(
        criticalAction.value.criticalId,
        criticalAction.value.feedback,
      ),
    '危急值反馈已记录。',
  );
}
function sendReport() {
  return run(async () => {
    const result = await distributeOperationsReport(
      distribution.value.reportId,
      distribution.value.targetCode,
      outputKey('report-distribution'),
    );
    await loadReportOutput();
    if (result.errorMessage) throw new Error(result.errorMessage);
  }, '报告发放结果已记录。');
}
function printReport() {
  return run(async () => {
    const result = await printOperationsReport(distribution.value.reportId, {
      ...reportPrint.value,
      idempotencyKey: outputKey('report-print'),
    });
    await loadReportOutput();
    if (result.errorMessage) throw new Error(result.errorMessage);
  }, '报告打印结果已记录。');
}
async function loadReportOutput() {
  const reportId = distribution.value.reportId.trim();
  if (!reportId) {
    reportDistributions.value = [];
    reportPrints.value = [];
    return;
  }
  [reportDistributions.value, reportPrints.value] = await Promise.all([
    getOperationsReportDistributions(reportId),
    getOperationsReportPrints(reportId),
  ]);
}
async function checkPrinter() {
  const status = await getOperationsReportPrinterStatus(reportPrint.value.printerReference);
  printerStatus.value = `${status.statusCode} · ${status.detail}`;
}
function outputKey(prefix: string) {
  return `${prefix}-${crypto.randomUUID()}`;
}
function retryDistribution(id: string) {
  return run(() => updateOperationsDistribution(id, 'RETRY_PENDING'), '失败发放已进入重试队列。');
}
function saveAddress() {
  return run(() => createOperationsAddress(address.value), '常用地址已保存。');
}
function saveParcel() {
  return run(() => createOperationsPackage(parcel.value), '外送物流单已创建。');
}
function saveParcelEvent() {
  return run(
    () =>
      addOperationsPackageEvent(
        parcelEvent.value.packageId,
        parcelEvent.value.statusCode,
        parcelEvent.value.note,
      ),
    '物流状态已记录。',
  );
}
onMounted(() => void load());
</script>

<template>
  <section class="operations-workspace">
    <p v-if="error" class="feedback error">{{ error }}</p>
    <p v-if="notice" class="feedback success">{{ notice }}</p>
    <nav class="operations-section-tabs">
      <button :class="{ active: section === 'critical' }" @click="section = 'critical'">
        危急值</button
      ><button :class="{ active: section === 'distribution' }" @click="section = 'distribution'">
        报告发放</button
      ><button :class="{ active: section === 'logistics' }" @click="section = 'logistics'">
        外送物流
      </button>
    </nav>
    <section v-if="section === 'critical'" class="workspace-panel">
      <h3>危急值闭环</h3>
      <form class="operations-form" @submit.prevent="saveCritical">
        <input v-model="critical.caseId" required placeholder="病例记录标识" /><input
          v-model="critical.valueTypeCode"
          required
          placeholder="危急值类型"
        /><select v-model="critical.gradeCode">
          <option value="HIGH">高</option>
          <option value="MEDIUM">中</option></select
        ><button :disabled="saving">登记</button>
      </form>
      <form class="operations-form" @submit.prevent="notifyCritical">
        <select v-model="criticalAction.criticalId" required>
          <option value="">选择危急值</option>
          <option v-for="item in criticalValues" :key="String(item.id)" :value="String(item.id)">
            {{ item.valueTypeCode }} · {{ item.statusCode }}
          </option></select
        ><input
          v-model="criticalAction.departmentReference"
          required
          placeholder="接收科室"
        /><input v-model="criticalAction.recipientReference" required placeholder="接收人" /><select
          v-model="criticalAction.methodCode"
        >
          <option value="PHONE">电话</option>
          <option value="SYSTEM">系统通知</option></select
        ><input v-model="criticalAction.message" placeholder="通知内容" /><button
          :disabled="saving"
        >
          记录通知
        </button>
      </form>
      <form class="operations-form" @submit.prevent="acknowledge">
        <input v-model="criticalAction.notificationId" required placeholder="通知记录标识" /><button
          :disabled="saving"
        >
          确认接收
        </button>
      </form>
      <form class="operations-form" @submit.prevent="feedback">
        <input v-model="criticalAction.feedback" required placeholder="临床反馈" /><button
          :disabled="saving"
        >
          记录反馈
        </button>
      </form>
      <div v-for="item in criticalValues" :key="String(item.id)" class="operations-row">
        <div>
          <strong>{{ item.valueTypeCode }} · {{ item.gradeCode }}</strong>
          <p>病例 {{ item.caseId }}</p>
        </div>
        <span class="status-pill">{{ item.statusCode }}</span>
      </div>
    </section>
    <section v-else-if="section === 'distribution'" class="workspace-panel">
      <h3>报告发放</h3>
      <form class="operations-form" @submit.prevent="sendReport">
        <input
          v-model="distribution.reportId"
          required
          placeholder="已签发报告记录标识"
          @change="loadReportOutput"
        /><select v-model="distribution.targetCode">
          <option value="SIMULATOR_PATIENT_PORTAL">产品内患者服务模拟通道</option>
          <option value="HIS">院内系统（需真实适配器）</option></select
        ><button :disabled="saving">执行发放</button
        ><button
          class="secondary-button"
          type="button"
          :disabled="saving"
          @click="loadReportOutput"
        >
          查询历史
        </button>
      </form>
      <h4>报告自助打印</h4>
      <form class="operations-form" aria-label="报告自助打印" @submit.prevent="printReport">
        <input v-model="reportPrint.identityReference" required placeholder="身份核验凭据引用" />
        <input v-model="reportPrint.terminalReference" required placeholder="自助终端标识" />
        <input v-model="reportPrint.printerReference" required placeholder="打印机配置" />
        <input v-model.number="reportPrint.copyCount" type="number" min="1" max="10" required />
        <button :disabled="saving">打印报告</button>
        <button class="secondary-button" type="button" @click="checkPrinter">检查打印机</button>
      </form>
      <p v-if="printerStatus" class="muted">打印机：{{ printerStatus }}</p>
      <div v-for="item in reportPrints" :key="item.id" class="operations-row">
        <div>
          <strong>打印 · {{ item.resultCode }}</strong>
          <p>
            {{ formatDateTime(item.printedAt) }} · {{ item.terminalReference }} ·
            {{ item.copyCount }} 份
          </p>
          <small v-if="item.failureReason">{{ item.failureReason }}</small>
        </div>
        <span class="status-pill">{{ item.resultCode }}</span>
      </div>
      <div v-for="item in reportDistributions" :key="item.id" class="operations-row">
        <div>
          <strong>发放 · {{ item.targetCode }}</strong>
          <p>{{ formatDateTime(item.requestedAt) }}</p>
          <small v-if="item.lastError">{{ item.lastError }}</small>
        </div>
        <span class="status-pill">{{ item.statusCode }}</span>
      </div>
      <div
        v-for="item in overview.distributions ?? []"
        :key="String(item.id)"
        class="operations-row"
      >
        <div>
          <strong>{{ item.targetCode }}</strong>
          <p>
            {{ item.requestedAt
            }}<span v-if="item.lastError"> · 失败原因：{{ item.lastError }}</span>
          </p>
        </div>
        <span class="status-pill">{{ item.statusCode }}</span
        ><button
          v-if="item.statusCode === 'FAILED'"
          class="text-button"
          @click="retryDistribution(String(item.id))"
        >
          重试
        </button>
      </div>
    </section>
    <section v-else class="workspace-panel">
      <h3>外送物流</h3>
      <form class="operations-form" @submit.prevent="saveAddress">
        <input v-model="address.addressName" required placeholder="地址名称" /><input
          v-model="address.recipientName"
          required
          placeholder="收件人"
        /><input v-model="address.phone" placeholder="电话" /><input
          v-model="address.addressText"
          required
          placeholder="详细地址"
        /><button :disabled="saving">保存地址</button>
      </form>
      <form class="operations-form" @submit.prevent="saveParcel">
        <input v-model="parcel.caseId" required placeholder="病例记录标识" /><input
          v-model="parcel.courierCompany"
          required
          placeholder="物流公司"
        /><input v-model="parcel.trackingNo" placeholder="物流单号" /><input
          v-model="parcel.senderReference"
          required
          placeholder="寄件人"
        /><input v-model="parcel.recipientReference" required placeholder="接收人" /><input
          v-model="parcel.addressText"
          required
          placeholder="收件地址"
        /><button :disabled="saving">创建物流单</button>
      </form>
      <form class="operations-form" @submit.prevent="saveParcelEvent">
        <select v-model="parcelEvent.packageId" required>
          <option value="">选择物流单</option>
          <option
            v-for="item in overview.packages ?? []"
            :key="String(item.id)"
            :value="String(item.id)"
          >
            {{ item.trackingNo || '未填单号' }} · {{ item.courierCompany }}
          </option></select
        ><select v-model="parcelEvent.statusCode">
          <option value="SENT">已寄出</option>
          <option value="IN_TRANSIT">运输中</option>
          <option value="DELIVERED">已送达</option>
          <option value="RETURNED">已归还</option>
          <option value="DELAYED">延误</option></select
        ><input v-model="parcelEvent.note" placeholder="说明" /><button :disabled="saving">
          更新状态
        </button>
      </form>
      <div v-for="item in overview.packages ?? []" :key="String(item.id)" class="operations-row">
        <div>
          <strong>{{ item.courierCompany }} · {{ item.trackingNo || '未填单号' }}</strong>
          <p>{{ item.recipientReference }}</p>
        </div>
        <span class="status-pill">{{ item.statusCode }}</span>
      </div>
    </section>
  </section>
</template>
