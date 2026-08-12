<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';

import type { V2AuthUser } from '../auth';
import { appendNavigationContext, safeLocalPath, type V2Route } from '../navigation';
import { businessTypeName, formatDateTime, friendlyError } from '../uiText';
import { getV2MyWorkbench } from '../v2WorkspaceApi';
import {
  cancelV2Application,
  createV2Application,
  getV2Application,
  getV2ApplicationPrintHistory,
  getV2ApplicationQueue,
  lookupV2Patient,
  printV2ApplicationBarcodes,
  printV2OutpatientReceipt,
  printV2SpecimenLabels,
  registerV2Application,
  registerV2ApplicationItem,
  scanV2ApplicationBarcode,
  searchV2ApplicationDeliveries,
  updateV2Application,
  validateV2Application,
  verifyV2IncomingSpecimen,
  v2ApplicationDeliveryExportUrl,
  type V2ApplicationInput,
  type V2ApplicationResult,
  type V2BarcodeScanResult,
  type V2DeliveryRecord,
  type V2ValidationIssue,
} from '../v2RegistrationApi';

type MappingOption = {
  applicationItemCode: string;
  defaultSpecimenKindCode: string;
  businessTypeCode: string;
  businessTypeName: string;
};

type DraftItem = {
  key: string;
  externalItemCode: string;
  itemName: string;
  specimenKindCode: string;
  specimenDescription: string;
};

const props = withDefaults(
  defineProps<{
    authUser?: V2AuthUser | null;
    origin?: V2Route['origin'];
    queue?: string;
    returnTo?: string;
  }>(),
  { authUser: null, origin: 'direct', queue: '', returnTo: '' },
);
const emit = defineEmits<{ navigate: [path: string] }>();

const routeQuery = new URLSearchParams(window.location.search);
const queuedApplicationId = routeQuery.get('applicationId') ?? '';
const queuedApplicationItemId = routeQuery.get('applicationItemId') ?? '';
const queueKey = props.queue || routeQuery.get('queue') || 'REGISTRATION_PENDING';
const returnTo = safeLocalPath(props.returnTo || routeQuery.get('returnTo')) || '/v2/workbench';

const mode = ref<'QUEUE' | 'APPLICATION' | 'REGISTRATION'>('QUEUE');
const mappings = ref<MappingOption[]>([]);
const queueRows = ref<Awaited<ReturnType<typeof getV2ApplicationQueue>>>([]);
const currentApplication = ref<V2ApplicationResult | null>(null);
const selectedItemIds = ref<string[]>([]);
const validationIssues = ref<V2ValidationIssue[]>([]);
const printHistory = ref<Awaited<ReturnType<typeof getV2ApplicationPrintHistory>>>([]);
const completedCases = ref<
  Array<{ caseId: string; caseNo: string; specimenId: string; applicationItemId: string }>
>([]);
const loading = ref(false);
const submitting = ref(false);
const error = ref('');
const notice = ref('');
const editingApplicationId = ref('');
const lookupKey = ref('MZ10001');
const rejectReasonCode = ref('SPECIMEN_MISMATCH');
const rejectReasonText = ref('');
const deliveryBarcode = ref('');
const scannedDelivery = ref<V2BarcodeScanResult | null>(null);
const deliveryRows = ref<V2DeliveryRecord[]>([]);
const deliveryFilters = reactive({
  visitReference: '',
  from: '',
  to: '',
  externalItemCode: '',
});

const draft = reactive({
  applicationNo: newApplicationNo(),
  sourceTypeCode: 'MANUAL',
  sourceSystemCode: 'PIS-MANUAL',
  patientInfoSourceCode: 'MANUAL',
  patientReference: '',
  patientName: '',
  patientSexCode: '',
  patientBirthDate: '',
  patientIdentityNo: '',
  visitCardNo: '',
  contactPhone: '',
  ageValue: '',
  ageUnitCode: 'YEAR',
  visitReference: '',
  visitTypeCode: 'OUTPATIENT',
  wardReference: '',
  bedReference: '',
  applicationDepartment: '',
  applicantReference: '',
  clinicalDiagnosis: '',
  medicalHistory: '',
  surgeryName: '',
  specimenDescription: '',
  note: '',
});
const draftItems = ref<DraftItem[]>([newDraftItem()]);
const verification = reactive({
  patientMatch: false,
  applicationMatch: false,
  quantityMatch: false,
  specimenMatch: false,
  containerMatch: false,
  fixationMatch: false,
});

const pendingItems = computed(
  () => currentApplication.value?.items.filter((item) => item.statusCode === 'PENDING') ?? [],
);
const selectedPendingItems = computed(() =>
  pendingItems.value.filter((item) => selectedItemIds.value.includes(item.itemId)),
);
const allVerified = computed(() => Object.values(verification).every(Boolean));
const permissionSet = computed(() => new Set(props.authUser?.permissions ?? []));
const canRegister = computed(
  () =>
    permissionSet.value.has('P14-PERM-003') &&
    permissionSet.value.has('P14-PERM-004') &&
    permissionSet.value.has('P14-PERM-009') &&
    selectedPendingItems.value.length > 0 &&
    (selectedPendingItems.value.length === 1 ||
      selectedPendingItems.value.length === pendingItems.value.length),
);
const canWriteApplication = computed(() => permissionSet.value.has('P14-PERM-002'));
const canUpdateApplication = computed(() => permissionSet.value.has('P14-PERM-007'));
const canCancelApplication = computed(() => permissionSet.value.has('P14-PERM-006'));
const canReject = computed(() => permissionSet.value.has('P14-PERM-010'));
const canPrint = computed(() => permissionSet.value.has('P14-PERM-008'));
const deliveryExportUrl = computed(() =>
  v2ApplicationDeliveryExportUrl({
    visitReference: deliveryFilters.visitReference,
    from: deliveryFilters.from ? new Date(deliveryFilters.from).toISOString() : undefined,
    to: deliveryFilters.to ? new Date(deliveryFilters.to).toISOString() : undefined,
    externalItemCode: deliveryFilters.externalItemCode,
  }),
);

function newApplicationNo() {
  return `APP-${new Date().toISOString().slice(0, 10).replaceAll('-', '')}-${String(Date.now()).slice(-5)}`;
}

function newDraftItem(source?: Partial<DraftItem>): DraftItem {
  const mapping = mappings.value[0];
  return {
    key: crypto.randomUUID(),
    externalItemCode: source?.externalItemCode ?? mapping?.applicationItemCode ?? 'SYNTH-HISTOLOGY',
    itemName: source?.itemName ?? mapping?.businessTypeName ?? '常规组织病理',
    specimenKindCode: source?.specimenKindCode ?? mapping?.defaultSpecimenKindCode ?? 'TISSUE',
    specimenDescription: source?.specimenDescription ?? '',
  };
}

function fieldIssue(field: string) {
  return validationIssues.value.find((item) => item.field === field && item.severity === 'ERROR')
    ?.message;
}

function mappingFor(code: string) {
  return mappings.value.find((item) => item.applicationItemCode === code);
}

function onMappingChanged(item: DraftItem) {
  const mapping = mappingFor(item.externalItemCode);
  if (!mapping) return;
  item.itemName = mapping.businessTypeName;
  item.specimenKindCode = mapping.defaultSpecimenKindCode;
}

function input(): V2ApplicationInput {
  return {
    ...draft,
    patientBirthDate: draft.patientBirthDate || undefined,
    ageValue: draft.ageValue ? Number(draft.ageValue) : undefined,
    ageUnitCode: draft.ageValue ? draft.ageUnitCode : undefined,
    items: draftItems.value.map((item, index) => ({
      externalItemCode: item.externalItemCode,
      itemName: item.itemName,
      specimenKindCode: item.specimenKindCode,
      specimenDescription: item.specimenDescription,
      sequenceNo: index + 1,
    })),
  };
}

async function loadMappings() {
  const response = await fetch('/api/v2/registration/application-item-mappings');
  if (!response.ok) throw new Error('申请项目映射暂时无法加载');
  mappings.value = (await response.json()) as MappingOption[];
  if (draftItems.value.length === 1 && mappings.value[0]) {
    draftItems.value = [newDraftItem()];
  }
}

async function loadQueue() {
  loading.value = true;
  error.value = '';
  try {
    queueRows.value = await getV2ApplicationQueue();
  } catch (requestError) {
    error.value = friendlyError(requestError, '待登记申请暂时无法加载');
  } finally {
    loading.value = false;
  }
}

async function scanDeliveryBarcode() {
  if (!deliveryBarcode.value.trim()) {
    error.value = '请输入或扫描送检条码';
    return;
  }
  error.value = '';
  try {
    scannedDelivery.value = await scanV2ApplicationBarcode(deliveryBarcode.value.trim());
  } catch (requestError) {
    scannedDelivery.value = null;
    error.value = friendlyError(requestError, '未找到该送检条码');
  }
}

async function loadDeliveryRows() {
  error.value = '';
  try {
    deliveryRows.value = await searchV2ApplicationDeliveries({
      visitReference: deliveryFilters.visitReference,
      from: deliveryFilters.from ? new Date(deliveryFilters.from).toISOString() : undefined,
      to: deliveryFilters.to ? new Date(deliveryFilters.to).toISOString() : undefined,
      externalItemCode: deliveryFilters.externalItemCode,
    });
  } catch (requestError) {
    error.value = friendlyError(requestError, '送检记录暂时无法查询');
  }
}

function startApplication() {
  editingApplicationId.value = '';
  Object.assign(draft, {
    applicationNo: newApplicationNo(),
    sourceTypeCode: 'MANUAL',
    sourceSystemCode: 'PIS-MANUAL',
    patientInfoSourceCode: 'MANUAL',
    patientReference: '',
    patientName: '',
    patientSexCode: '',
    patientBirthDate: '',
    patientIdentityNo: '',
    visitCardNo: '',
    contactPhone: '',
    ageValue: '',
    ageUnitCode: 'YEAR',
    visitReference: '',
    visitTypeCode: 'OUTPATIENT',
    wardReference: '',
    bedReference: '',
    applicationDepartment: '',
    applicantReference: props.authUser?.displayName ?? '',
    clinicalDiagnosis: '',
    medicalHistory: '',
    surgeryName: '',
    specimenDescription: '',
    note: '',
  });
  draftItems.value = [newDraftItem()];
  validationIssues.value = [];
  mode.value = 'APPLICATION';
}

async function lookupPatient() {
  error.value = '';
  notice.value = '';
  try {
    const result = await lookupV2Patient(
      draft.visitTypeCode === 'INPATIENT'
        ? { inpatientNo: lookupKey.value }
        : { outpatientNo: lookupKey.value },
    );
    notice.value = result.message;
    if (!result.found) {
      draft.patientInfoSourceCode = 'MANUAL';
      return;
    }
    Object.assign(draft, {
      patientInfoSourceCode: 'HIS',
      patientReference: result.patientReference ?? '',
      patientName: result.patientName ?? '',
      patientSexCode: result.patientSexCode ?? '',
      patientBirthDate: result.birthDate ?? '',
      patientIdentityNo: result.identityNo ?? '',
      visitCardNo: result.visitCardNo ?? '',
      contactPhone: result.contactPhone ?? '',
      ageValue: result.ageValue == null ? '' : String(result.ageValue),
      ageUnitCode: result.ageUnitCode ?? 'YEAR',
      visitReference: result.visitReference ?? lookupKey.value,
      visitTypeCode: result.visitTypeCode ?? draft.visitTypeCode,
      wardReference: result.wardReference ?? '',
      bedReference: result.bedReference ?? '',
      applicationDepartment: result.departmentReference ?? '',
      clinicalDiagnosis: result.clinicalDiagnosis ?? '',
      medicalHistory: result.medicalHistory ?? '',
    });
  } catch (requestError) {
    error.value = friendlyError(requestError, '患者信息查询失败，可重试或人工补录');
  }
}

async function saveApplication() {
  submitting.value = true;
  error.value = '';
  notice.value = '';
  try {
    const payload = input();
    const validation = await validateV2Application(payload);
    validationIssues.value = validation.issues;
    if (!validation.valid) return;
    const saved = editingApplicationId.value
      ? await updateV2Application(editingApplicationId.value, payload)
      : await createV2Application(payload);
    notice.value = editingApplicationId.value ? '申请修改已保存' : '申请已保存并进入待登记';
    await openApplication(saved.applicationId);
  } catch (requestError) {
    error.value = friendlyError(requestError, '申请保存失败，请核对字段后重试');
  } finally {
    submitting.value = false;
  }
}

function fillDraft(application: V2ApplicationResult) {
  Object.assign(draft, {
    applicationNo: application.applicationNo,
    sourceTypeCode: application.sourceTypeCode,
    sourceSystemCode: application.sourceSystemCode,
    patientInfoSourceCode: application.patientInfoSourceCode,
    patientReference: application.patientReference,
    patientName: application.patientName ?? '',
    patientSexCode: application.patientSexCode ?? '',
    patientBirthDate: application.patientBirthDate ?? '',
    patientIdentityNo: application.patientIdentityNo ?? '',
    visitCardNo: application.visitCardNo ?? '',
    contactPhone: application.contactPhone ?? '',
    ageValue: application.ageValue == null ? '' : String(application.ageValue),
    ageUnitCode: application.ageUnitCode ?? 'YEAR',
    visitReference: application.visitReference ?? '',
    visitTypeCode: application.visitTypeCode ?? 'OTHER',
    wardReference: application.wardReference ?? '',
    bedReference: application.bedReference ?? '',
    applicationDepartment: application.applicationDepartment ?? '',
    applicantReference: application.applicantReference ?? '',
    clinicalDiagnosis: application.clinicalDiagnosis ?? '',
    medicalHistory: application.medicalHistory ?? '',
    surgeryName: application.surgeryName ?? '',
    specimenDescription: application.specimenDescription ?? '',
    note: application.note ?? '',
  });
  draftItems.value = application.items
    .filter((item) => item.statusCode === 'PENDING')
    .map((item) =>
      newDraftItem({
        externalItemCode: item.externalItemCode,
        itemName: item.itemName ?? '',
        specimenKindCode: item.specimenKindCode ?? '',
        specimenDescription: item.specimenDescription ?? '',
      }),
    );
}

async function editApplication() {
  if (!currentApplication.value) return;
  editingApplicationId.value = currentApplication.value.applicationId;
  fillDraft(currentApplication.value);
  mode.value = 'APPLICATION';
}

async function openApplication(applicationId: string, itemId?: string) {
  loading.value = true;
  error.value = '';
  try {
    currentApplication.value = await getV2Application(applicationId);
    const candidate = itemId
      ? currentApplication.value.items.find((item) => item.itemId === itemId)
      : currentApplication.value.items.find((item) => item.statusCode === 'PENDING');
    selectedItemIds.value = candidate ? [candidate.itemId] : [];
    printHistory.value = await getV2ApplicationPrintHistory(applicationId);
    completedCases.value = [];
    Object.assign(verification, {
      patientMatch: false,
      applicationMatch: false,
      quantityMatch: false,
      specimenMatch: false,
      containerMatch: false,
      fixationMatch: false,
    });
    mode.value = 'REGISTRATION';
  } catch (requestError) {
    error.value = friendlyError(requestError, '申请详情暂时无法加载');
  } finally {
    loading.value = false;
  }
}

function toggleItem(itemId: string) {
  selectedItemIds.value = selectedItemIds.value.includes(itemId)
    ? selectedItemIds.value.filter((id) => id !== itemId)
    : [...selectedItemIds.value, itemId];
}

async function printSelectedBarcodes() {
  if (!currentApplication.value || !selectedItemIds.value.length) return;
  submitting.value = true;
  try {
    const result = await printV2ApplicationBarcodes(
      currentApplication.value.applicationId,
      selectedItemIds.value,
    );
    printHistory.value = await getV2ApplicationPrintHistory(currentApplication.value.applicationId);
    notice.value = result.allSucceeded
      ? `已提交 ${result.successCount} 个送检标签打印`
      : `打印完成 ${result.successCount}/${result.requestedCount}，失败记录可重试`;
  } catch (requestError) {
    error.value = friendlyError(requestError, '送检标签打印失败，申请数据未受影响');
  } finally {
    submitting.value = false;
  }
}

async function verifyItems(outcome: 'ACCEPTED' | 'REJECTED') {
  if (!currentApplication.value || !selectedPendingItems.value.length) return;
  if (outcome === 'REJECTED' && selectedPendingItems.value.length !== 1) {
    throw new Error('拒收时请只选择一个申请项目');
  }
  for (const item of selectedPendingItems.value) {
    await verifyV2IncomingSpecimen({
      applicationId: currentApplication.value.applicationId,
      applicationItemId: item.itemId,
      incomingSpecimenReference: `${currentApplication.value.applicationNo}-${item.sequenceNo}`,
      patientReference: currentApplication.value.patientReference,
      actualSpecimenDescription:
        item.specimenDescription || currentApplication.value.specimenDescription || '送检标本',
      outcomeCode: outcome,
      reasonCode: outcome === 'REJECTED' ? rejectReasonCode.value : undefined,
      reasonText: outcome === 'REJECTED' ? rejectReasonText.value : undefined,
      ...verification,
    });
  }
}

async function registerSelected(openNext: boolean) {
  if (!currentApplication.value || !canRegister.value || !allVerified.value) return;
  submitting.value = true;
  error.value = '';
  try {
    await verifyItems('ACCEPTED');
    const result =
      selectedPendingItems.value.length === pendingItems.value.length
        ? await registerV2Application(currentApplication.value.applicationId)
        : await registerV2ApplicationItem(
            currentApplication.value.applicationId,
            selectedPendingItems.value[0]!.itemId,
          );
    completedCases.value = result.cases.map((item) => ({
      caseId: item.caseId,
      caseNo: item.caseNo,
      specimenId: item.specimenId,
      applicationItemId: item.applicationItemId,
    }));
    notice.value = `登记完成，已创建 ${result.createdCaseCount} 个独立病例`;
    await openApplication(currentApplication.value.applicationId);
    completedCases.value = result.cases.map((item) => ({
      caseId: item.caseId,
      caseNo: item.caseNo,
      specimenId: item.specimenId,
      applicationItemId: item.applicationItemId,
    }));
    await loadQueue();
    mode.value = 'REGISTRATION';
    if (openNext) await openNextWorkbenchItem();
  } catch (requestError) {
    error.value = friendlyError(requestError, '登记未完成，未创建半成品病例');
  } finally {
    submitting.value = false;
  }
}

async function rejectSelected() {
  if (!rejectReasonText.value.trim()) {
    error.value = '请填写拒收原因说明';
    return;
  }
  submitting.value = true;
  error.value = '';
  try {
    await verifyItems('REJECTED');
    notice.value = '标本已拒收；未创建病例和病理号';
    await openApplication(currentApplication.value!.applicationId);
  } catch (requestError) {
    error.value = friendlyError(requestError, '拒收未完成');
  } finally {
    submitting.value = false;
  }
}

async function cancelCurrentApplication() {
  if (!currentApplication.value) return;
  const reason = window.prompt('请输入申请取消原因');
  if (!reason?.trim()) return;
  try {
    await cancelV2Application(currentApplication.value.applicationId, reason.trim());
    notice.value = '未登记项目已取消，已建立病例保持不变';
    mode.value = 'QUEUE';
    await loadQueue();
  } catch (requestError) {
    error.value = friendlyError(requestError, '申请取消失败');
  }
}

async function printRegistrationLabels() {
  if (!completedCases.value.length) return;
  const results = await Promise.all(
    completedCases.value.map((item) => printV2SpecimenLabels(item.caseId, [item.specimenId])),
  );
  notice.value = `已提交 ${results.reduce((sum, item) => sum + item.successCount, 0)} 个正式标本标签打印`;
}

async function printReceipt() {
  const first = completedCases.value[0];
  if (!first) return;
  const result = await printV2OutpatientReceipt(first.caseId);
  notice.value = result.allSucceeded ? '门诊回执打印成功' : '门诊回执打印失败，可重试';
}

async function openNextWorkbenchItem() {
  const latest = await getV2MyWorkbench();
  const queue = latest.capabilityQueues.find((item) => item.key === queueKey);
  const next = queue?.items[0];
  if (!next) {
    emit('navigate', returnTo);
    return;
  }
  emit(
    'navigate',
    appendNavigationContext(next.workspaceDestination, {
      origin: 'workbench',
      queue: queueKey,
      returnTo,
    }),
  );
}

onMounted(async () => {
  try {
    await Promise.all([loadMappings(), loadQueue()]);
    if (queuedApplicationId) await openApplication(queuedApplicationId, queuedApplicationItemId);
  } catch (requestError) {
    error.value = friendlyError(requestError, '登记工作区初始化失败');
  }
});
</script>

<template>
  <section class="registration-layout" aria-label="申请与登记工作区">
    <header class="registration-focus-header">
      <button
        v-if="mode !== 'QUEUE'"
        class="case-back-link"
        type="button"
        @click="emit('navigate', returnTo)"
      >
        ← 返回工作台
      </button>
      <div>
        <p class="section-kicker">申请 · 核对 · 登记</p>
        <h1>
          {{
            mode === 'QUEUE' ? '待登记申请' : mode === 'APPLICATION' ? '电子申请' : '核对申请并登记'
          }}
        </h1>
      </div>
      <span class="status-pill">登记员：{{ props.authUser?.displayName ?? '当前用户' }}</span>
    </header>

    <p v-if="error" class="feedback error" role="alert">{{ error }}</p>
    <p v-if="notice" class="feedback success" role="status">{{ notice }}</p>

    <template v-if="mode === 'QUEUE'">
      <div class="registration-queue-toolbar">
        <span>按申请时间优先处理；已登记项目不会重复出现。</span>
        <div class="inline-actions">
          <button class="secondary-button" type="button" @click="loadQueue">刷新</button>
          <button
            v-if="canWriteApplication"
            class="primary-button"
            type="button"
            @click="startApplication"
          >
            新建病理申请
          </button>
        </div>
      </div>
      <details
        v-if="permissionSet.has('P14-PERM-009') || permissionSet.has('P14-PERM-048')"
        class="workspace-panel delivery-console"
      >
        <summary>送检扫码与记录</summary>
        <div v-if="permissionSet.has('P14-PERM-009')" class="delivery-scan-row">
          <input
            v-model="deliveryBarcode"
            aria-label="送检条码"
            placeholder="扫描或输入送检条码"
            @keyup.enter="scanDeliveryBarcode"
          />
          <button class="primary-button" type="button" @click="scanDeliveryBarcode">
            查找申请
          </button>
        </div>
        <div v-if="scannedDelivery" class="delivery-scan-result">
          <span
            ><small>申请号</small><strong>{{ scannedDelivery.applicationNo }}</strong></span
          >
          <span
            ><small>患者</small
            ><strong>{{
              scannedDelivery.patientName || scannedDelivery.patientReference
            }}</strong></span
          >
          <span
            ><small>申请项目</small
            ><strong>{{ scannedDelivery.itemName || '未命名申请项目' }}</strong></span
          >
          <span
            ><small>送检状态</small
            ><strong>{{
              scannedDelivery.delivered
                ? `已于 ${formatDateTime(scannedDelivery.deliveredAt)} 由 ${scannedDelivery.deliveredBy} 确认`
                : '待核对确认'
            }}</strong></span
          >
          <button
            v-if="!scannedDelivery.delivered"
            class="secondary-button"
            type="button"
            @click="
              openApplication(scannedDelivery.applicationId, scannedDelivery.applicationItemId)
            "
          >
            打开标本核对
          </button>
        </div>
        <div v-if="permissionSet.has('P14-PERM-048')" class="delivery-query">
          <div class="delivery-filter-row">
            <input
              v-model="deliveryFilters.visitReference"
              placeholder="门诊号 / 住院号"
              aria-label="按门诊住院号查询送检记录"
            />
            <select v-model="deliveryFilters.externalItemCode" aria-label="按申请项目查询送检记录">
              <option value="">全部申请项目</option>
              <option
                v-for="mapping in mappings"
                :key="mapping.applicationItemCode"
                :value="mapping.applicationItemCode"
              >
                {{ mapping.businessTypeName }}
              </option>
            </select>
            <input v-model="deliveryFilters.from" type="datetime-local" aria-label="送检开始时间" />
            <input v-model="deliveryFilters.to" type="datetime-local" aria-label="送检结束时间" />
            <button class="secondary-button" type="button" @click="loadDeliveryRows">查询</button>
            <a class="secondary-button" :href="deliveryExportUrl">导出 Excel</a>
          </div>
          <div v-if="deliveryRows.length" class="delivery-record-list">
            <div v-for="row in deliveryRows" :key="row.deliveryId">
              <strong>{{ row.applicationNo }}</strong>
              <span>{{ row.patientName || row.patientReference }} · {{ row.visitReference }}</span>
              <span>{{ row.itemName || row.externalItemCode }}</span>
              <span
                >{{ row.incomingSpecimenReference }} · {{ formatDateTime(row.deliveredAt) }}</span
              >
            </div>
          </div>
          <p v-else class="muted">设置条件后查询送检记录；导出使用相同筛选条件。</p>
        </div>
      </details>
      <div class="registration-table" role="table" aria-label="待登记申请列表">
        <div class="registration-table-head" role="row">
          <span>申请号</span><span>患者 / 就诊</span><span>申请项目</span><span>科室 / 医生</span
          ><span>申请时间</span><span>操作</span>
        </div>
        <button
          v-for="row in queueRows.filter((item) => item.itemStatusCode === 'PENDING')"
          :key="row.applicationItemId"
          class="registration-table-row"
          type="button"
          role="row"
          @click="openApplication(row.applicationId, row.applicationItemId)"
        >
          <strong>{{ row.applicationNo }}</strong>
          <span
            >{{ row.patientName || row.patientReference
            }}<small>{{ row.visitReference || '特殊场景' }}</small></span
          >
          <span
            >{{ row.itemName || businessTypeName(row.businessTypeCode || '')
            }}<small>{{ row.specimenDescription || '待核对标本' }}</small></span
          >
          <span
            >{{ row.applicationDepartment || '未填写'
            }}<small>{{ row.applicantReference || '未填写' }}</small></span
          >
          <span>{{ formatDateTime(row.appliedAt) }}</span
          ><span class="row-action">登记</span>
        </button>
        <div
          v-if="!loading && !queueRows.some((item) => item.itemStatusCode === 'PENDING')"
          class="empty-state compact"
        >
          <strong>当前没有待登记项目</strong><span>新申请保存后会按项目进入此处。</span>
        </div>
      </div>
    </template>

    <form
      v-else-if="mode === 'APPLICATION'"
      class="registration-form"
      @submit.prevent="saveApplication"
    >
      <section class="workspace-panel compact-panel">
        <header class="panel-title-row">
          <div>
            <p class="section-kicker">患者与就诊</p>
            <h2>获取或人工补录患者信息</h2>
          </div>
        </header>
        <div class="patient-lookup-row">
          <select v-model="draft.visitTypeCode" aria-label="就诊类型">
            <option value="OUTPATIENT">门诊</option>
            <option value="INPATIENT">住院</option>
            <option value="EMERGENCY">急诊</option>
            <option value="OTHER">其他</option>
          </select>
          <input
            v-model="lookupKey"
            aria-label="患者查询号"
            placeholder="门诊号 / 住院号 / 就诊号"
          />
          <button class="secondary-button" type="button" @click="lookupPatient">从 HIS 查询</button>
          <span class="muted">查询不到时可继续人工补录</span>
        </div>
        <div class="field-grid registration-fields">
          <label
            >患者姓名<input v-model="draft.patientName" required /><small
              v-if="fieldIssue('patientName')"
              >{{ fieldIssue('patientName') }}</small
            ></label
          >
          <label
            >性别<select v-model="draft.patientSexCode">
              <option value="">未提供</option>
              <option value="MALE">男</option>
              <option value="FEMALE">女</option>
              <option value="UNKNOWN">未知</option>
            </select></label
          >
          <label>出生日期<input v-model="draft.patientBirthDate" type="date" /></label>
          <label
            >年龄（无出生日期时）<input v-model="draft.ageValue" type="number" min="0"
          /></label>
          <label
            >年龄单位<select v-model="draft.ageUnitCode">
              <option value="YEAR">岁</option>
              <option value="MONTH">月</option>
              <option value="DAY">天</option>
            </select></label
          >
          <label>身份证号（如有）<input v-model="draft.patientIdentityNo" /></label>
          <label>就诊卡号<input v-model="draft.visitCardNo" /></label>
          <label>联系电话<input v-model="draft.contactPhone" /></label>
          <label>患者标识<input v-model="draft.patientReference" required /></label>
          <label
            >门诊号 / 住院号<input v-model="draft.visitReference" required /><small
              v-if="fieldIssue('visitReference')"
              >{{ fieldIssue('visitReference') }}</small
            ></label
          >
          <label v-if="draft.visitTypeCode === 'INPATIENT'"
            >病区<input v-model="draft.wardReference"
          /></label>
          <label v-if="draft.visitTypeCode === 'INPATIENT'"
            >床号<input v-model="draft.bedReference"
          /></label>
        </div>
      </section>

      <section class="workspace-panel compact-panel">
        <header class="panel-title-row">
          <div>
            <p class="section-kicker">申请信息</p>
            <h2>申请与临床资料</h2>
          </div>
        </header>
        <div class="field-grid registration-fields">
          <label
            >申请号<input v-model="draft.applicationNo" :readonly="Boolean(editingApplicationId)"
          /></label>
          <label
            >申请科室<input v-model="draft.applicationDepartment" required /><small
              v-if="fieldIssue('applicationDepartment')"
              >{{ fieldIssue('applicationDepartment') }}</small
            ></label
          >
          <label>申请医生<input v-model="draft.applicantReference" required /></label>
          <label class="span-two"
            >临床诊断<textarea v-model="draft.clinicalDiagnosis" rows="2"></textarea>
          </label>
          <label class="span-two"
            >病史摘要<textarea v-model="draft.medicalHistory" rows="2"></textarea>
          </label>
          <label>手术名称<input v-model="draft.surgeryName" /></label>
          <label>备注<input v-model="draft.note" /></label>
        </div>
      </section>

      <section class="workspace-panel compact-panel">
        <header class="panel-title-row">
          <div>
            <p class="section-kicker">申请项目</p>
            <h2>项目与送检标本</h2>
          </div>
          <button class="secondary-button" type="button" @click="draftItems.push(newDraftItem())">
            + 新增申请项目
          </button>
        </header>
        <div class="application-item-editor">
          <div v-for="(item, index) in draftItems" :key="item.key" class="application-item-row">
            <strong>{{ index + 1 }}</strong>
            <label
              >申请项目<select v-model="item.externalItemCode" @change="onMappingChanged(item)">
                <option
                  v-for="mapping in mappings"
                  :key="mapping.applicationItemCode"
                  :value="mapping.applicationItemCode"
                >
                  {{ mapping.businessTypeName }} · {{ mapping.applicationItemCode }}
                </option>
              </select></label
            >
            <label
              >映射业务类型<input
                :value="businessTypeName(mappingFor(item.externalItemCode)?.businessTypeCode || '')"
                readonly
            /></label>
            <label
              >标本名称 / 部位<input v-model="item.specimenDescription" placeholder="例如 胃窦活检"
            /></label>
            <button
              class="text-button danger-text"
              type="button"
              :disabled="draftItems.length === 1"
              @click="draftItems.splice(index, 1)"
            >
              移除
            </button>
          </div>
        </div>
      </section>
      <div class="sticky-form-actions">
        <button class="secondary-button" type="button" @click="mode = 'QUEUE'">取消</button>
        <button class="primary-button" type="submit" :disabled="submitting">
          {{ editingApplicationId ? '保存申请修改' : '保存申请' }}
        </button>
      </div>
    </form>

    <template v-else-if="currentApplication">
      <section class="registration-identity-strip" aria-label="登记患者摘要">
        <div>
          <small>申请号</small><strong>{{ currentApplication.applicationNo }}</strong>
        </div>
        <div>
          <small>患者</small
          ><strong>{{
            currentApplication.patientName || currentApplication.patientReference
          }}</strong>
        </div>
        <div>
          <small>性别 / 年龄</small
          ><strong
            >{{ currentApplication.patientSexCode || '未提供' }} ·
            {{ currentApplication.patientBirthDate || '未提供' }}</strong
          >
        </div>
        <div>
          <small>门诊 / 住院号</small
          ><strong>{{ currentApplication.visitReference || '特殊场景' }}</strong>
        </div>
        <div>
          <small>申请科室 / 医生</small
          ><strong
            >{{ currentApplication.applicationDepartment }} ·
            {{ currentApplication.applicantReference }}</strong
          >
        </div>
        <div>
          <small>申请时间</small><strong>{{ formatDateTime(currentApplication.appliedAt) }}</strong>
        </div>
      </section>

      <section class="workspace-panel compact-panel">
        <header class="panel-title-row">
          <div>
            <p class="section-kicker">申请项目</p>
            <h2>选择本次登记项目</h2>
          </div>
          <div class="inline-actions">
            <button
              v-if="canUpdateApplication"
              class="secondary-button"
              type="button"
              @click="editApplication"
            >
              编辑申请
            </button>
            <button
              v-if="canCancelApplication"
              class="text-button danger-text"
              type="button"
              @click="cancelCurrentApplication"
            >
              取消申请
            </button>
          </div>
        </header>
        <div class="registration-item-list">
          <label
            v-for="item in currentApplication.items"
            :key="item.itemId"
            class="registration-item-line"
            :class="item.statusCode.toLowerCase()"
          >
            <input
              v-if="item.statusCode === 'PENDING'"
              type="checkbox"
              :checked="selectedItemIds.includes(item.itemId)"
              @change="toggleItem(item.itemId)"
            />
            <span v-else class="item-state-mark">✓</span>
            <strong>{{ item.itemName || item.externalItemCode }}</strong>
            <span>{{ item.externalItemCode }}</span>
            <span>{{ businessTypeName(item.businessTypeCode || '') }}</span>
            <span>{{ item.specimenDescription || '待核对标本' }}</span>
            <span>{{
              item.statusCode === 'PENDING'
                ? '待登记'
                : item.statusCode === 'REGISTERED'
                  ? `已登记 ${item.pathologyNo}`
                  : item.statusCode === 'REJECTED'
                    ? '已拒收'
                    : '已取消'
            }}</span>
          </label>
        </div>
        <div class="inline-actions print-actions">
          <button
            v-if="canPrint"
            class="secondary-button"
            type="button"
            :disabled="!selectedItemIds.length"
            @click="printSelectedBarcodes"
          >
            打印所选送检标签
          </button>
          <span class="muted">打印记录 {{ printHistory.length }} 条；再次打印自动记为重打</span>
        </div>
      </section>

      <section class="workspace-panel compact-panel">
        <header class="panel-title-row">
          <div>
            <p class="section-kicker">送检标本核对</p>
            <h2>接收前逐项确认</h2>
          </div>
        </header>
        <div class="verification-grid">
          <label><input v-model="verification.patientMatch" type="checkbox" />患者一致</label>
          <label
            ><input v-model="verification.applicationMatch" type="checkbox" />申请与标本对应</label
          >
          <label><input v-model="verification.quantityMatch" type="checkbox" />标本数量一致</label>
          <label
            ><input v-model="verification.specimenMatch" type="checkbox" />类型与部位一致</label
          >
          <label><input v-model="verification.containerMatch" type="checkbox" />容器符合要求</label>
          <label
            ><input v-model="verification.fixationMatch" type="checkbox" />固定情况符合要求</label
          >
        </div>
        <div class="rejection-row">
          <select v-model="rejectReasonCode" aria-label="拒收原因">
            <option value="SPECIMEN_MISMATCH">标本信息不符</option>
            <option value="CONTAINER_DAMAGED">容器破损</option>
            <option value="FIXATION_INVALID">固定不符合要求</option>
            <option value="SPECIMEN_MISSING">标本缺失</option>
            <option value="APPLICATION_INCOMPLETE">申请信息缺失</option>
            <option value="OTHER">其他</option>
          </select>
          <input v-model="rejectReasonText" placeholder="拒收原因说明（必填）" />
        </div>
      </section>

      <section v-if="completedCases.length" class="feedback success registration-complete-actions">
        <div>
          <strong>登记完成</strong
          ><span v-for="item in completedCases" :key="item.caseId">{{ item.caseNo }}</span>
        </div>
        <div class="inline-actions">
          <button
            v-if="canPrint"
            class="secondary-button"
            type="button"
            @click="printRegistrationLabels"
          >
            打印正式标本标签
          </button>
          <button
            v-if="canPrint && currentApplication.visitTypeCode === 'OUTPATIENT'"
            class="secondary-button"
            type="button"
            @click="printReceipt"
          >
            打印门诊回执
          </button>
          <button class="primary-button" type="button" @click="openNextWorkbenchItem">
            登记并下一项
          </button>
          <button class="secondary-button" type="button" @click="emit('navigate', returnTo)">
            登记并返回工作台
          </button>
        </div>
      </section>

      <div v-else class="sticky-form-actions">
        <span class="muted"
          >已选 {{ selectedPendingItems.length }} 项；多选时须选择全部剩余项目以保证事务一致</span
        >
        <div class="action-group">
          <button
            v-if="canReject"
            class="danger-button"
            type="button"
            :disabled="submitting || selectedPendingItems.length !== 1"
            @click="rejectSelected"
          >
            拒收
          </button>
          <button
            class="secondary-button"
            type="button"
            :disabled="!canRegister || !allVerified || submitting"
            @click="registerSelected(false)"
          >
            登记
          </button>
          <button
            class="primary-button"
            type="button"
            :disabled="!canRegister || !allVerified || submitting"
            @click="registerSelected(true)"
          >
            登记并下一项
          </button>
        </div>
      </div>
    </template>
  </section>
</template>

<style scoped>
.registration-layout {
  display: grid;
  gap: 12px;
}
.registration-focus-header,
.registration-queue-toolbar,
.registration-identity-strip,
.sticky-form-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.registration-focus-header h1 {
  margin: 2px 0 0;
  font-size: 24px;
}
.registration-table {
  border: 1px solid var(--border-color, #dfe4ea);
  border-radius: 10px;
  overflow: hidden;
  background: #fff;
}
.registration-table-head,
.registration-table-row {
  display: grid;
  grid-template-columns: 1.05fr 1.2fr 1.5fr 1.1fr 1fr 64px;
  align-items: center;
  gap: 12px;
  min-height: 52px;
  padding: 0 14px;
  text-align: left;
}
.registration-table-head {
  min-height: 38px;
  color: #687385;
  background: #f6f8fa;
  font-size: 12px;
}
.registration-table-row {
  width: 100%;
  border: 0;
  border-top: 1px solid #edf0f3;
  background: #fff;
  color: inherit;
  cursor: pointer;
}
.registration-table-row:hover {
  background: #f7fbff;
}
.delivery-console {
  padding: 10px 14px;
}
.delivery-console > summary {
  cursor: pointer;
  font-weight: 700;
}
.delivery-scan-row,
.delivery-filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}
.delivery-scan-row input {
  min-width: 280px;
}
.delivery-scan-result {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr)) auto;
  align-items: center;
  gap: 12px;
  margin-top: 10px;
  padding: 10px;
  border-radius: 8px;
  background: #f6f8fa;
}
.delivery-scan-result span {
  display: grid;
  gap: 3px;
}
.delivery-scan-result small {
  color: #778291;
}
.delivery-record-list {
  display: grid;
  margin-top: 10px;
  border-top: 1px solid #edf0f3;
}
.delivery-record-list > div {
  display: grid;
  grid-template-columns: 1fr 1.4fr 1.2fr 1.5fr;
  gap: 10px;
  min-height: 42px;
  align-items: center;
  border-bottom: 1px solid #edf0f3;
  font-size: 13px;
}
.registration-table-row span {
  display: grid;
  gap: 2px;
}
.registration-table-row small {
  color: #7b8594;
}
.row-action {
  color: #1264a3;
  font-weight: 700;
}
.compact-panel {
  padding: 14px 16px;
}
.registration-fields {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}
.patient-lookup-row,
.application-item-row,
.registration-item-line,
.rejection-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.patient-lookup-row {
  margin-bottom: 12px;
}
.patient-lookup-row input {
  min-width: 240px;
}
.application-item-editor,
.registration-item-list {
  display: grid;
  gap: 6px;
}
.application-item-row {
  display: grid;
  grid-template-columns: 28px 1.4fr 1fr 1.4fr 56px;
}
.application-item-row label {
  display: grid;
  gap: 4px;
  font-size: 12px;
  color: #687385;
}
.registration-identity-strip {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  padding: 12px 16px;
  border: 1px solid #dfe4ea;
  border-radius: 10px;
  background: #fff;
}
.registration-identity-strip div {
  display: grid;
  gap: 3px;
}
.registration-identity-strip small {
  color: #778291;
}
.registration-item-line {
  min-height: 48px;
  display: grid;
  grid-template-columns: 28px 1.4fr 1fr 1fr 1.4fr 1fr;
  border-top: 1px solid #edf0f3;
}
.registration-item-line.registered,
.registration-item-line.rejected,
.registration-item-line.cancelled {
  color: #778291;
}
.item-state-mark {
  text-align: center;
}
.print-actions {
  margin-top: 12px;
}
.verification-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}
.verification-grid label {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 38px;
}
.rejection-row {
  margin-top: 12px;
}
.rejection-row input {
  flex: 1;
}
.registration-complete-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}
.registration-complete-actions div:first-child {
  display: flex;
  gap: 12px;
}
@media (max-width: 1400px) {
  .registration-fields {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .registration-identity-strip {
    grid-template-columns: repeat(3, 1fr);
  }
  .registration-table-head,
  .registration-table-row {
    grid-template-columns: 1fr 1.2fr 1.4fr 1fr 64px;
  }
  .registration-table-head span:nth-child(5),
  .registration-table-row > span:nth-child(5) {
    display: none;
  }
}
</style>
