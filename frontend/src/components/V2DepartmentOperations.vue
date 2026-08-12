<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { friendlyError } from '../uiText';
import {
  addOperationsEnvironment,
  addOperationsSafety,
  approveOperationsProcurement,
  createOperationsBatch,
  createOperationsCatalog,
  createOperationsEquipment,
  createOperationsEquipmentEvent,
  createOperationsProcurement,
  createOperationsQualityDocument,
  createOperationsSchedule,
  createOperationsSpace,
  getOperationsCatalog,
  getOperationsEquipment,
  getOperationsQualityDocuments,
  getOperationsSpaces,
  getOperationsStock,
  recordOperationsStock,
  transitionOperationsQualityDocument,
  type OperationsOverview,
  type OperationsRow,
} from '../v2BusinessOperationsApi';

defineProps<{ overview: OperationsOverview }>();
const emit = defineEmits<{ changed: [] }>();
type Section = 'schedule' | 'quality' | 'equipment' | 'consumables' | 'procurement' | 'space';
const section = ref<Section>('schedule');
const saving = ref(false);
const error = ref('');
const notice = ref('');
const documents = ref<OperationsRow[]>([]);
const equipment = ref<OperationsRow[]>([]);
const catalogs = ref<OperationsRow[]>([]);
const stock = ref<OperationsRow[]>([]);
const spaces = ref<OperationsRow[]>([]);
const schedule = ref({
  staffReference: '',
  scheduleDate: '',
  shiftCode: 'DAY',
  workArea: '',
  note: '',
});
const documentDraft = ref({
  title: '',
  documentNo: '',
  categoryCode: 'SOP',
  versionLabel: '1.0',
  ownerReference: '',
  contentReference: '',
});
const equipmentDraft = ref({
  equipmentCode: '',
  name: '',
  categoryCode: 'PATHOLOGY',
  statusCode: 'ACTIVE',
});
const equipmentEvent = ref({ equipmentId: '', eventCode: 'MAINTENANCE', description: '' });
const catalog = ref({
  materialCode: '',
  name: '',
  categoryCode: 'GENERAL',
  unitCode: 'ITEM',
  hazardous: false,
});
const batch = ref({ catalogId: '', batchNo: '', expiryDate: '', storageLocation: '' });
const transaction = ref({
  batchId: '',
  directionCode: 'INBOUND',
  quantity: 1,
  reason: '',
  sourceReference: '',
});
const procurement = ref({
  requestNo: '',
  departmentReference: 'PATHOLOGY',
  reason: '',
  materialReference: '',
  quantity: 1,
  estimatedAmount: 0,
  supplier: '',
});
const space = ref({ spaceCode: '', name: '', zoneCode: 'CLEAN', areaValue: null as number | null });
const spaceRecord = ref({
  spaceId: '',
  kind: 'environment',
  code: 'TEMPERATURE',
  value: 22,
  unit: 'C',
  note: '',
});

async function loadDetails() {
  [documents.value, equipment.value, catalogs.value, stock.value, spaces.value] = await Promise.all(
    [
      getOperationsQualityDocuments(),
      getOperationsEquipment(),
      getOperationsCatalog(),
      getOperationsStock(),
      getOperationsSpaces(),
    ],
  );
}
async function run(action: () => Promise<unknown>, message: string) {
  saving.value = true;
  error.value = '';
  notice.value = '';
  try {
    await action();
    notice.value = message;
    await loadDetails();
    emit('changed');
  } catch (requestError) {
    error.value = friendlyError(requestError, '操作失败，请核对输入。');
  } finally {
    saving.value = false;
  }
}
function saveSchedule() {
  return run(() => createOperationsSchedule(schedule.value), '排班已保存。');
}
function saveDocument() {
  return run(() => createOperationsQualityDocument(documentDraft.value), '质量文件草稿已保存。');
}
function moveDocument(id: string, status: string) {
  return run(() => transitionOperationsQualityDocument(id, status), '质量文件状态已更新。');
}
function saveEquipment() {
  return run(() => createOperationsEquipment(equipmentDraft.value), '设备台账已保存。');
}
function saveEquipmentEvent() {
  return run(
    () => createOperationsEquipmentEvent(equipmentEvent.value.equipmentId, equipmentEvent.value),
    '设备维护或故障记录已保存。',
  );
}
function saveCatalog() {
  return run(() => createOperationsCatalog(catalog.value), '耗材目录已保存。');
}
function saveBatch() {
  return run(() => createOperationsBatch(batch.value.catalogId, batch.value), '耗材批次已保存。');
}
function saveTransaction() {
  return run(
    () => recordOperationsStock(transaction.value.batchId, transaction.value),
    '库存交易已记录。',
  );
}
function saveProcurement() {
  return run(
    () =>
      createOperationsProcurement({
        requestNo: procurement.value.requestNo,
        departmentReference: procurement.value.departmentReference,
        reason: procurement.value.reason,
        items: [
          {
            materialReference: procurement.value.materialReference,
            quantity: procurement.value.quantity,
            estimatedAmount: procurement.value.estimatedAmount,
            supplier: procurement.value.supplier,
          },
        ],
      }),
    '采购申请已保存。',
  );
}
function decideProcurement(id: string, decision: string) {
  return run(() => approveOperationsProcurement(id, decision, '管理端审批'), '采购审批已记录。');
}
function saveSpace() {
  return run(() => createOperationsSpace(space.value), '空间档案已保存。');
}
function saveSpaceRecord() {
  return run(
    () =>
      spaceRecord.value.kind === 'environment'
        ? addOperationsEnvironment(spaceRecord.value.spaceId, {
            metricCode: spaceRecord.value.code,
            measureValue: spaceRecord.value.value,
            unitCode: spaceRecord.value.unit,
          })
        : addOperationsSafety(spaceRecord.value.spaceId, {
            checkCode: spaceRecord.value.code,
            resultCode: 'PASS',
            note: spaceRecord.value.note,
          }),
    '空间环境或安全记录已保存。',
  );
}
onMounted(() => void loadDetails());
</script>

<template>
  <section class="operations-workspace">
    <p v-if="error" class="feedback error" role="alert">{{ error }}</p>
    <p v-if="notice" class="feedback success" role="status">{{ notice }}</p>
    <nav class="operations-section-tabs">
      <button
        v-for="item in [
          { k: 'schedule', l: '人员排班' },
          { k: 'quality', l: '质量文件' },
          { k: 'equipment', l: '设施设备' },
          { k: 'consumables', l: '试剂耗材' },
          { k: 'procurement', l: '采购管理' },
          { k: 'space', l: '空间管理' },
        ]"
        :key="item.k"
        type="button"
        :class="{ active: section === item.k }"
        @click="section = item.k as Section"
      >
        {{ item.l }}
      </button>
    </nav>
    <section v-if="section === 'schedule'" class="workspace-panel">
      <h3>人员排班</h3>
      <form class="operations-form" @submit.prevent="saveSchedule">
        <input v-model="schedule.staffReference" required placeholder="人员编码或姓名" /><input
          v-model="schedule.scheduleDate"
          required
          type="date"
        /><select v-model="schedule.shiftCode">
          <option value="DAY">白班</option>
          <option value="EVENING">晚班</option>
          <option value="ON_CALL">值班</option></select
        ><input v-model="schedule.workArea" required placeholder="工作区域" /><button
          class="primary-button"
          :disabled="saving"
        >
          新增排班
        </button>
      </form>
      <div v-for="item in overview.schedules ?? []" :key="String(item.id)" class="operations-row">
        <div>
          <strong>{{ item.staffReference }}</strong>
          <p>{{ item.scheduleDate }} · {{ item.workArea }}</p>
        </div>
        <span class="status-pill">{{ item.shiftCode }}</span>
      </div>
    </section>
    <section v-else-if="section === 'quality'" class="workspace-panel">
      <h3>质量文件</h3>
      <form class="operations-form" @submit.prevent="saveDocument">
        <input v-model="documentDraft.title" required placeholder="文件标题" /><input
          v-model="documentDraft.documentNo"
          required
          placeholder="文件编号"
        /><input v-model="documentDraft.ownerReference" required placeholder="负责人" /><input
          v-model="documentDraft.contentReference"
          required
          placeholder="文件存储引用"
        /><button class="primary-button" :disabled="saving">保存草稿</button>
      </form>
      <div v-for="item in documents" :key="String(item.id)" class="operations-row">
        <div>
          <strong>{{ item.title }}</strong>
          <p>{{ item.documentNo }} · {{ item.versionLabel }}</p>
        </div>
        <span class="status-pill">{{ item.statusCode }}</span
        ><button
          v-if="item.statusCode === 'DRAFT'"
          class="text-button"
          @click="moveDocument(String(item.id), 'REVIEW')"
        >
          提交审核</button
        ><button
          v-else-if="item.statusCode === 'REVIEW'"
          class="text-button"
          @click="moveDocument(String(item.id), 'PUBLISHED')"
        >
          发布</button
        ><button
          v-else-if="item.statusCode === 'PUBLISHED'"
          class="text-button"
          @click="moveDocument(String(item.id), 'ARCHIVED')"
        >
          归档
        </button>
      </div>
    </section>
    <section v-else-if="section === 'equipment'" class="workspace-panel">
      <h3>设施设备</h3>
      <form class="operations-form" @submit.prevent="saveEquipment">
        <input v-model="equipmentDraft.equipmentCode" required placeholder="设备编码" /><input
          v-model="equipmentDraft.name"
          required
          placeholder="设备名称"
        /><input v-model="equipmentDraft.categoryCode" required placeholder="类别" /><button
          class="primary-button"
          :disabled="saving"
        >
          新增设备
        </button>
      </form>
      <form class="operations-form" @submit.prevent="saveEquipmentEvent">
        <select v-model="equipmentEvent.equipmentId" required>
          <option value="">选择设备</option>
          <option v-for="item in equipment" :key="String(item.id)" :value="String(item.id)">
            {{ item.equipmentCode }} · {{ item.name }}
          </option></select
        ><select v-model="equipmentEvent.eventCode">
          <option value="MAINTENANCE">保养</option>
          <option value="FAULT">故障</option>
          <option value="REPAIR">维修</option>
          <option value="CALIBRATION">校准</option></select
        ><input v-model="equipmentEvent.description" required placeholder="记录内容" /><button
          :disabled="saving"
        >
          保存记录
        </button>
      </form>
      <div v-for="item in equipment" :key="String(item.id)" class="operations-row">
        <div>
          <strong>{{ item.equipmentCode }} · {{ item.name }}</strong>
          <p>{{ item.categoryCode }}</p>
        </div>
        <span class="status-pill success">{{ item.statusCode }}</span>
      </div>
    </section>
    <section v-else-if="section === 'consumables'" class="workspace-panel">
      <h3>试剂耗材</h3>
      <form class="operations-form" @submit.prevent="saveCatalog">
        <input v-model="catalog.materialCode" required placeholder="耗材编码" /><input
          v-model="catalog.name"
          required
          placeholder="名称"
        /><input v-model="catalog.unitCode" required placeholder="单位" /><button
          :disabled="saving"
        >
          新增目录
        </button>
      </form>
      <form class="operations-form" @submit.prevent="saveBatch">
        <select v-model="batch.catalogId" required>
          <option value="">选择耗材</option>
          <option v-for="item in catalogs" :key="String(item.id)" :value="String(item.id)">
            {{ item.materialCode }} · {{ item.name }}
          </option></select
        ><input v-model="batch.batchNo" required placeholder="批号" /><input
          v-model="batch.expiryDate"
          type="date"
        /><input v-model="batch.storageLocation" placeholder="存放位置" /><button
          :disabled="saving"
        >
          新增批次
        </button>
      </form>
      <form class="operations-form" @submit.prevent="saveTransaction">
        <select v-model="transaction.batchId" required>
          <option value="">选择批次</option>
          <option v-for="item in stock" :key="String(item.batchId)" :value="String(item.batchId)">
            {{ item.materialCode }} · {{ item.batchNo }}
          </option></select
        ><select v-model="transaction.directionCode">
          <option value="INBOUND">入库</option>
          <option value="OUTBOUND">出库/领用</option>
          <option value="ADJUSTMENT">盘点调整</option></select
        ><input
          v-model.number="transaction.quantity"
          type="number"
          min="0.001"
          step="0.001"
        /><input v-model="transaction.reason" required placeholder="原因" /><button
          :disabled="saving"
        >
          记录库存
        </button>
      </form>
      <div v-for="item in stock" :key="String(item.batchId)" class="operations-row">
        <div>
          <strong>{{ item.materialCode }} · {{ item.name }}</strong>
          <p>批号 {{ item.batchNo }}</p>
        </div>
        <strong>{{ item.balance }}</strong>
      </div>
    </section>
    <section v-else-if="section === 'procurement'" class="workspace-panel">
      <h3>采购管理</h3>
      <form class="operations-form" @submit.prevent="saveProcurement">
        <input v-model="procurement.requestNo" required placeholder="申请编号" /><input
          v-model="procurement.reason"
          required
          placeholder="采购原因"
        /><input v-model="procurement.materialReference" required placeholder="物料" /><input
          v-model.number="procurement.quantity"
          type="number"
          min="0.001"
          step="0.001"
        /><input
          v-model.number="procurement.estimatedAmount"
          type="number"
          min="0"
          step="0.01"
          placeholder="预计金额"
        /><button :disabled="saving">提交申请</button>
      </form>
      <div
        v-for="item in overview.procurements ?? []"
        :key="String(item.id)"
        class="operations-row"
      >
        <div>
          <strong>{{ item.requestNo }}</strong>
          <p>{{ item.reason }} · {{ item.departmentReference }}</p>
        </div>
        <span class="status-pill">{{ item.statusCode }}</span
        ><span v-if="item.statusCode === 'REQUESTED'"
          ><button class="text-button" @click="decideProcurement(String(item.id), 'APPROVED')">
            批准</button
          ><button class="text-button" @click="decideProcurement(String(item.id), 'REJECTED')">
            拒绝
          </button></span
        >
      </div>
    </section>
    <section v-else class="workspace-panel">
      <h3>空间管理</h3>
      <form class="operations-form" @submit.prevent="saveSpace">
        <input v-model="space.spaceCode" required placeholder="空间编码" /><input
          v-model="space.name"
          required
          placeholder="名称"
        /><select v-model="space.zoneCode">
          <option value="POLLUTED">污染区</option>
          <option value="SEMI_POLLUTED">半污染区</option>
          <option value="BUFFER">缓冲区</option>
          <option value="CLEAN">清洁区</option></select
        ><input v-model.number="space.areaValue" type="number" min="0" placeholder="面积" /><button
          :disabled="saving"
        >
          新增空间
        </button>
      </form>
      <form class="operations-form" @submit.prevent="saveSpaceRecord">
        <select v-model="spaceRecord.spaceId" required>
          <option value="">选择空间</option>
          <option v-for="item in spaces" :key="String(item.id)" :value="String(item.id)">
            {{ item.spaceCode }} · {{ item.name }}
          </option></select
        ><select v-model="spaceRecord.kind">
          <option value="environment">环境记录</option>
          <option value="safety">安全检查</option></select
        ><input v-model="spaceRecord.code" required placeholder="指标或检查项目" /><input
          v-model.number="spaceRecord.value"
          type="number"
          step="0.01"
        /><input v-model="spaceRecord.unit" placeholder="单位" /><button :disabled="saving">
          保存记录
        </button>
      </form>
      <div v-for="item in spaces" :key="String(item.id)" class="operations-row">
        <div>
          <strong>{{ item.spaceCode }} · {{ item.name }}</strong>
          <p>{{ item.zoneCode }} · 面积 {{ item.areaValue ?? '—' }}</p>
        </div>
        <span class="status-pill success">启用</span>
      </div>
    </section>
  </section>
</template>
