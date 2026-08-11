<script setup lang="ts">
import { computed, ref, watch } from 'vue';

import { friendlyError, idempotencyKey } from '../uiText';
import { getV2Case, type V2CaseResult } from '../v2Api';
import { operationsRequest } from '../v2OperationsApi';
import {
  getV2ArchiveLocations,
  getV2Loans,
  type V2ArchiveLocation,
  type V2Loan,
} from '../v2CustodyApi';
import V2HistoryDrawer from './V2HistoryDrawer.vue';

type CustodyMode = 'ARCHIVE' | 'LOAN' | 'DESTRUCTION';
type CustodyMaterial = {
  materialKind: 'BLOCK' | 'SLIDE';
  materialId: string;
  materialCode: string;
  locationId?: string;
  locationCode?: string;
  locationName?: string;
  loanId?: string;
  borrowerReference?: string;
  destroyedAt?: string;
};

const props = defineProps<{ caseId?: string }>();
const lookupCaseId = ref(props.caseId ?? '');
const pathologyCase = ref<V2CaseResult | null>(null);
const materials = ref<CustodyMaterial[]>([]);
const selectedIds = ref<string[]>([]);
const mode = ref<CustodyMode>('ARCHIVE');
const locations = ref<V2ArchiveLocation[]>([]);
const selectedLocationId = ref('');
const loan = ref({ borrower: '', department: '', purpose: '', expectedReturnAt: '' });
const loans = ref<V2Loan[]>([]);
const loanStatusFilter = ref('BORROWED');
const loanQuery = ref('');
const destruction = ref({ reason: '', batch: '', confirmed: false });
const loading = ref(false);
const submitting = ref(false);
const error = ref('');
const notice = ref('');
const historyDrawerOpen = ref(false);

const selectedMaterials = computed(() =>
  materials.value.filter((item) => selectedIds.value.includes(item.materialId)),
);
const selectedBlocks = computed(() =>
  selectedMaterials.value
    .filter((item) => item.materialKind === 'BLOCK')
    .map((item) => item.materialId),
);
const selectedSlides = computed(() =>
  selectedMaterials.value
    .filter((item) => item.materialKind === 'SLIDE')
    .map((item) => item.materialId),
);

watch(
  () => props.caseId,
  (value) => {
    lookupCaseId.value = value ?? '';
    void load();
  },
  { immediate: true },
);

async function load() {
  loading.value = true;
  error.value = '';
  try {
    locations.value = await getV2ArchiveLocations();
    if (lookupCaseId.value.trim()) {
      [pathologyCase.value, materials.value] = await Promise.all([
        getV2Case(lookupCaseId.value.trim()),
        operationsRequest<CustodyMaterial[]>(
          `/custody/cases/${lookupCaseId.value.trim()}/materials`,
        ),
      ]);
    } else {
      pathologyCase.value = null;
      materials.value = [];
    }
    await loadLoans();
    selectedIds.value = [];
    selectedLocationId.value = locations.value[0]?.locationId ?? '';
  } catch (requestError) {
    pathologyCase.value = null;
    materials.value = [];
    error.value = friendlyError(requestError, '材料保管信息暂时无法加载，请检查病例。');
  } finally {
    loading.value = false;
  }
}

async function loadLoans() {
  loans.value = await getV2Loans({
    status: loanStatusFilter.value,
    query: loanQuery.value.trim(),
  });
}

async function submit(operation: () => Promise<void>) {
  submitting.value = true;
  error.value = '';
  notice.value = '';
  try {
    await operation();
  } catch (requestError) {
    error.value = friendlyError(requestError, '材料保管操作未完成，请刷新后重试。');
  } finally {
    submitting.value = false;
  }
}

function toggleMaterial(id: string) {
  selectedIds.value = selectedIds.value.includes(id)
    ? selectedIds.value.filter((item) => item !== id)
    : [...selectedIds.value, id];
}

function toggleAll() {
  selectedIds.value =
    selectedIds.value.length === materials.value.length
      ? []
      : materials.value.map((item) => item.materialId);
}

function archive() {
  const materialCount = selectedMaterials.value.length;
  void submit(async () => {
    await operationsRequest('/custody/archive', {
      method: 'POST',
      body: JSON.stringify({
        blockIds: selectedBlocks.value,
        slideIds: selectedSlides.value,
        locationId: selectedLocationId.value,
        reason: '常规归档',
        idempotencyKey: idempotencyKey('ux01-custody-archive'),
      }),
    });
    await load();
    const target = locations.value.find((item) => item.locationId === selectedLocationId.value);
    notice.value = `已将 ${materialCount} 件材料归档到“${target?.locationName ?? '所选库位'}”。`;
  });
}

function borrow() {
  if (!selectedMaterials.value.length || !loan.value.borrower.trim() || !loan.value.purpose.trim())
    return;
  void submit(async () => {
    await operationsRequest('/custody/loans', {
      method: 'POST',
      body: JSON.stringify({
        blockIds: selectedBlocks.value,
        slideIds: selectedSlides.value,
        borrowerReference: loan.value.borrower.trim(),
        borrowerDepartment: loan.value.department.trim(),
        purpose: loan.value.purpose.trim(),
        expectedReturnAt: loan.value.expectedReturnAt
          ? new Date(loan.value.expectedReturnAt).toISOString()
          : null,
      }),
    });
    await load();
    notice.value = '借阅已登记；原归档位置继续保留，当前去向已更新。';
  });
}

function returnLoanFromWorkbench(loanId: string) {
  void submit(async () => {
    await operationsRequest(`/custody/loans/${loanId}/return`, { method: 'POST' });
    await loadLoans();
    notice.value = '借阅已归还，材料归档位置未改变。';
  });
}

function loanStatusLabel(status: string) {
  return (
    { BORROWED: '在借', DUE_SOON: '今日到期', OVERDUE: '逾期', RETURNED: '已归还' }[status] ??
    status
  );
}

function returnSelected() {
  const loanIds = [
    ...new Set(selectedMaterials.value.map((item) => item.loanId).filter(Boolean)),
  ] as string[];
  if (!loanIds.length) return;
  void submit(async () => {
    for (const loanId of loanIds)
      await operationsRequest(`/custody/loans/${loanId}/return`, { method: 'POST' });
    await load();
    notice.value = '所选借阅已归还，材料归档位置未改变。';
  });
}

function destroy() {
  if (
    !selectedMaterials.value.length ||
    !destruction.value.reason.trim() ||
    !destruction.value.batch.trim() ||
    !destruction.value.confirmed
  )
    return;
  void submit(async () => {
    await operationsRequest('/custody/destruction', {
      method: 'POST',
      body: JSON.stringify({
        blockIds: selectedBlocks.value,
        slideIds: selectedSlides.value,
        reason: destruction.value.reason.trim(),
        batchReference: destruction.value.batch.trim(),
      }),
    });
    await load();
    destruction.value.confirmed = false;
    notice.value = '材料销毁事实已记录；病例、诊断和报告未被删除。';
  });
}
</script>

<template>
  <section class="custody-page" aria-label="归档借阅工作台">
    <header class="page-heading compact-heading">
      <div>
        <p class="section-kicker">材料后生命周期</p>
        <h2>归档与借阅</h2>
        <p>归档位置和当前去向分别管理；批量选择材料后执行操作。</p>
      </div>
      <form class="case-lookup" @submit.prevent="load">
        <label>打开病例 <input v-model="lookupCaseId" /></label
        ><button class="secondary-button" type="submit" :disabled="loading">打开</button>
      </form>
      <button
        v-if="lookupCaseId"
        class="secondary-button"
        type="button"
        @click="historyDrawerOpen = true"
      >
        历史记录
      </button>
    </header>
    <p v-if="error" class="feedback error" role="alert">{{ error }}</p>
    <p v-if="notice" class="feedback success" role="status">{{ notice }}</p>
    <section v-if="!pathologyCase" class="workspace-panel loan-workbench" aria-label="借阅记录">
      <header class="panel-title-row">
        <div>
          <p class="section-kicker">借阅记录</p>
          <h3>材料借阅</h3>
        </div>
        <form class="inline-actions" @submit.prevent="loadLoans">
          <input v-model="loanQuery" placeholder="病理号、材料号、借阅人" aria-label="借阅搜索" />
          <button class="secondary-button" type="submit">查询</button>
        </form>
      </header>
      <nav class="workspace-tabs" aria-label="借阅状态">
        <button
          v-for="item in [
            ['BORROWED', '当前借出'],
            ['DUE_SOON', '今日到期'],
            ['OVERDUE', '逾期'],
            ['RETURNED', '已归还'],
          ]"
          :key="item[0]"
          type="button"
          :class="{ active: loanStatusFilter === item[0] }"
          @click="
            loanStatusFilter = item[0];
            void loadLoans();
          "
        >
          {{ item[1] }}
        </button>
      </nav>
      <div v-if="loans.length" class="loan-list" role="table" aria-label="借阅记录列表">
        <div class="loan-row header" role="row">
          <span>借阅人 / 科室</span><span>材料</span><span>借出时间</span><span>预计归还</span
          ><span>状态</span><span>操作</span>
        </div>
        <div v-for="item in loans" :key="item.loanId" class="loan-row" role="row">
          <span
            ><strong>{{ item.borrowerReference }}</strong
            ><small>{{ item.borrowerDepartment || '—' }}</small></span
          >
          <span
            ><strong>{{ item.items.length }} 件</strong
            ><small>{{
              item.items.map((material) => material.materialCode).join('、')
            }}</small></span
          >
          <span>{{
            item.borrowedAt ? new Date(item.borrowedAt).toLocaleString('zh-CN') : '—'
          }}</span>
          <span>{{
            item.expectedReturnAt
              ? new Date(item.expectedReturnAt).toLocaleDateString('zh-CN')
              : '未设置'
          }}</span>
          <span
            class="status-pill"
            :class="{
              warning: item.statusCode === 'OVERDUE' || item.statusCode === 'DUE_SOON',
              success: item.statusCode === 'RETURNED',
            }"
            >{{ loanStatusLabel(item.statusCode) }}</span
          >
          <span
            ><button
              v-if="item.statusCode !== 'RETURNED'"
              class="text-button"
              type="button"
              @click="returnLoanFromWorkbench(item.loanId)"
            >
              归还</button
            ><span v-else class="muted">—</span></span
          >
        </div>
      </div>
      <div v-else class="empty-state compact">
        <strong>当前筛选没有借阅记录</strong
        ><span>借出材料后，预计归还和逾期状态会在这里显示。</span>
      </div>
    </section>
    <template v-else>
      <div class="case-context-bar">
        <span
          ><small>病理号</small><strong>{{ pathologyCase.caseNo }}</strong></span
        ><span
          ><small>材料</small><strong>{{ materials.length }} 件</strong></span
        ><span
          ><small>已归档</small
          ><strong>{{ materials.filter((item) => item.locationId).length }} 件</strong></span
        ><span
          ><small>借出</small
          ><strong>{{ materials.filter((item) => item.loanId).length }} 件</strong></span
        >
      </div>
      <div class="workspace-tabs">
        <button
          v-for="tab in ['ARCHIVE', 'LOAN', 'DESTRUCTION'] as const"
          :key="tab"
          type="button"
          :class="{ active: mode === tab }"
          @click="mode = tab"
        >
          {{ tab === 'ARCHIVE' ? '批量归档' : tab === 'LOAN' ? '借出 / 归还' : '材料销毁' }}
        </button>
      </div>
      <div class="custody-layout">
        <section class="workspace-panel custody-material-panel">
          <header class="panel-title-row">
            <div>
              <p class="section-kicker">材料列表</p>
              <h3>已选择 {{ selectedIds.length }} 件</h3>
            </div>
            <button class="text-button" type="button" @click="toggleAll">
              {{ selectedIds.length === materials.length ? '取消全选' : '全选' }}
            </button>
          </header>
          <div class="custody-material-list" role="table" aria-label="病例材料">
            <div class="custody-material-row header" role="row">
              <span></span><span>材料</span><span>归档位置</span><span>当前去向</span
              ><span>状态</span>
            </div>
            <label
              v-for="item in materials"
              :key="item.materialId"
              class="custody-material-row"
              role="row"
            >
              <input
                type="checkbox"
                :checked="selectedIds.includes(item.materialId)"
                :aria-label="`选择${item.materialKind === 'BLOCK' ? '蜡块' : '玻片'} ${item.materialCode}`"
                @change="toggleMaterial(item.materialId)"
              />
              <span
                ><strong>{{ item.materialCode }}</strong
                ><small>{{ item.materialKind === 'BLOCK' ? '蜡块' : '玻片' }}</small></span
              >
              <span
                >{{ item.locationName || '未归档'
                }}<small v-if="item.locationCode">{{ item.locationCode }}</small></span
              >
              <span>{{ item.borrowerReference || '病理科' }}</span>
              <span
                class="status-pill"
                :class="{ warning: item.loanId, success: item.locationId && !item.loanId }"
                >{{
                  item.destroyedAt
                    ? '已销毁'
                    : item.loanId
                      ? '借出'
                      : item.locationId
                        ? '在库'
                        : '待归档'
                }}</span
              >
            </label>
          </div>
        </section>

        <aside class="workspace-panel custody-action-panel">
          <template v-if="mode === 'ARCHIVE'">
            <header class="panel-title-row">
              <div>
                <p class="section-kicker">归档位置</p>
                <h3>选择已配置库位</h3>
              </div>
            </header>
            <label class="archive-location-select"
              >归档库位
              <select v-model="selectedLocationId">
                <option value="" disabled>请选择配置中的库位</option>
                <option v-for="item in locations" :key="item.locationId" :value="item.locationId">
                  {{ item.locationCode }} · {{ item.locationName }}
                </option>
              </select>
            </label>
            <p v-if="!locations.length" class="feedback warning">
              当前没有可用库位，请先在“配置中心 → 归档库位”建立。
            </p>
            <button
              class="primary-button"
              type="button"
              :disabled="!selectedLocationId || !selectedIds.length || submitting"
              @click="archive"
            >
              归档所选 {{ selectedIds.length }} 件
            </button>
          </template>
          <template v-else-if="mode === 'LOAN'">
            <header class="panel-title-row">
              <div>
                <p class="section-kicker">借阅</p>
                <h3>登记当前去向</h3>
              </div>
            </header>
            <label>借阅人 <input v-model="loan.borrower" /></label
            ><label>借阅科室 <input v-model="loan.department" /></label
            ><label>用途 <input v-model="loan.purpose" /></label
            ><label
              >预计归还日期 <input v-model="loan.expectedReturnAt" type="datetime-local"
            /></label>
            <div class="action-group">
              <button
                class="primary-button"
                type="button"
                :disabled="
                  !selectedIds.length ||
                  !loan.borrower.trim() ||
                  !loan.department.trim() ||
                  !loan.purpose.trim() ||
                  !loan.expectedReturnAt ||
                  submitting
                "
                @click="borrow"
              >
                借出所选材料</button
              ><button
                class="secondary-button"
                type="button"
                :disabled="!selectedMaterials.some((item) => item.loanId) || submitting"
                @click="returnSelected"
              >
                归还所选借阅
              </button>
            </div>
          </template>
          <template v-else>
            <header class="panel-title-row">
              <div>
                <p class="section-kicker">销毁事实</p>
                <h3>记录材料销毁</h3>
              </div>
            </header>
            <p class="feedback warning">
              销毁不会删除病例、诊断或报告。此操作完成后材料不可恢复为在库。
            </p>
            <label>销毁原因 <textarea v-model="destruction.reason" rows="3"></textarea></label
            ><label>批次 / 依据 <input v-model="destruction.batch" /></label
            ><label class="checkbox-label"
              ><input
                v-model="destruction.confirmed"
                type="checkbox"
              />我已核对所选材料和销毁依据</label
            >
            <button
              class="danger-button"
              type="button"
              :disabled="
                !selectedIds.length ||
                !destruction.reason.trim() ||
                !destruction.batch.trim() ||
                !destruction.confirmed ||
                submitting
              "
              @click="destroy"
            >
              记录 {{ selectedIds.length }} 件材料销毁
            </button>
          </template>
        </aside>
      </div>
    </template>
    <V2HistoryDrawer
      :open="historyDrawerOpen"
      :case-id="pathologyCase?.caseId"
      title="材料归档历史"
      target-label="归档借阅"
      @close="historyDrawerOpen = false"
    />
  </section>
</template>
