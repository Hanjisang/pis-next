<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';

import type { V2AuthUser } from '../auth';
import { getV2Case, registerV2Specimen, type V2CaseResult } from '../v2Api';
import { businessTypeName, friendlyError } from '../uiText';
import {
  createV2Application,
  getV2RegistrationQueue,
  registerV2Application,
  registerV2InboundApplication,
  type V2RegistrationQueue,
} from '../v2RegistrationApi';
import V2CaseHeader from './V2CaseHeader.vue';
import V2HistoryDrawer from './V2HistoryDrawer.vue';

type SpecimenDraft = {
  key: string;
  specimenCode: string;
  specimenKindCode: string;
  collectionSite: string;
  collectionMethodCode: string;
  note: string;
};

type BusinessOption = {
  code: string;
  businessTypeName: string;
  applicationItemCode: string;
  defaultSpecimenKindCode: string;
  modalityCode: string;
};

const props = defineProps<{ authUser?: V2AuthUser | null }>();
const emit = defineEmits<{ navigate: [path: string] }>();

const businessOptions = ref<BusinessOption[]>([
  {
    code: 'HISTOLOGY',
    businessTypeName: '常规组织病理',
    applicationItemCode: '',
    defaultSpecimenKindCode: 'TISSUE',
    modalityCode: 'ROUTINE',
  },
  {
    code: 'FROZEN',
    businessTypeName: '冰冻',
    applicationItemCode: '',
    defaultSpecimenKindCode: 'TISSUE',
    modalityCode: 'FROZEN',
  },
  {
    code: 'CYTOLOGY_NON_GYN',
    businessTypeName: '细胞病理',
    applicationItemCode: '',
    defaultSpecimenKindCode: 'FLUID',
    modalityCode: 'CYTOLOGY',
  },
  {
    code: 'MOLECULAR',
    businessTypeName: '分子病理',
    applicationItemCode: '',
    defaultSpecimenKindCode: 'TISSUE',
    modalityCode: 'MOLECULAR',
  },
  {
    code: 'CONSULTATION',
    businessTypeName: '会诊',
    applicationItemCode: '',
    defaultSpecimenKindCode: 'EXTERNAL_MATERIAL',
    modalityCode: 'CONSULTATION',
  },
]);

const draft = reactive({
  patientReference: '',
  visitReference: '',
  applicationNo: `M-${new Date().toISOString().slice(0, 10).replaceAll('-', '')}-${String(Date.now()).slice(-4)}`,
  businessTypeCode: 'HISTOLOGY',
});

const mappingsLoading = ref(true);
const mappingNotice = ref('');
const submitting = ref(false);
const progress = ref('');
const error = ref('');
const completedCase = ref<V2CaseResult | null>(null);
const registrationRunId = crypto.randomUUID();
const queueMode = ref<'QUEUE' | 'MANUAL'>('QUEUE');
const registrationQueue = ref<V2RegistrationQueue | null>(null);
const queueLoading = ref(false);
const historyDrawerOpen = ref(false);
const inboundApplicationId = ref('');
const selectedInboundApplication = computed(() =>
  registrationQueue.value?.pendingApplications.find(
    (item) => item.applicationId === inboundApplicationId.value,
  ),
);

const selectedBusiness = computed(
  () =>
    businessOptions.value.find((option) => option.code === draft.businessTypeCode) ??
    businessOptions.value[0],
);
const specimens = ref<SpecimenDraft[]>([createSpecimenDraft(0)]);
const canSubmit = computed(() => {
  const patientReady = draft.patientReference.trim() && draft.visitReference.trim();
  const applicationReady =
    draft.applicationNo.trim() && selectedBusiness.value?.applicationItemCode;
  const specimenReady =
    draft.businessTypeCode === 'CONSULTATION' ||
    draft.businessTypeCode === 'REFERRAL' ||
    (specimens.value.length > 0 &&
      specimens.value.every((item) => item.specimenCode.trim() && item.collectionSite.trim()));
  return Boolean(
    patientReady && applicationReady && specimenReady && !submitting.value && !completedCase.value,
  );
});

function createSpecimenDraft(index: number, source?: SpecimenDraft): SpecimenDraft {
  return {
    key: crypto.randomUUID(),
    specimenCode: String.fromCharCode(65 + index),
    specimenKindCode:
      source?.specimenKindCode ?? selectedBusiness.value?.defaultSpecimenKindCode ?? 'TISSUE',
    collectionSite: source?.collectionSite ?? '',
    collectionMethodCode: source?.collectionMethodCode ?? 'SURGICAL',
    note: source?.note ?? '',
  };
}

function resequenceSpecimens() {
  specimens.value.forEach((item, index) => {
    item.specimenCode = String.fromCharCode(65 + index);
  });
}

function addSpecimen(source?: SpecimenDraft) {
  specimens.value.push(createSpecimenDraft(specimens.value.length, source));
}

function duplicateSpecimen(index: number) {
  const source = specimens.value[index];
  if (!source) return;
  specimens.value.splice(index + 1, 0, createSpecimenDraft(index + 1, source));
  resequenceSpecimens();
}

function removeSpecimen(index: number) {
  specimens.value.splice(index, 1);
  resequenceSpecimens();
}

function moveSpecimen(index: number, offset: number) {
  const target = index + offset;
  if (target < 0 || target >= specimens.value.length) return;
  const [item] = specimens.value.splice(index, 1);
  if (!item) return;
  specimens.value.splice(target, 0, item);
  resequenceSpecimens();
}

function changeBusinessType() {
  if (draft.businessTypeCode === 'CONSULTATION' || draft.businessTypeCode === 'REFERRAL') {
    specimens.value = [];
    return;
  }
  if (!specimens.value.length) specimens.value = [createSpecimenDraft(0)];
  for (const specimen of specimens.value) {
    specimen.specimenKindCode = selectedBusiness.value?.defaultSpecimenKindCode ?? 'TISSUE';
  }
}

function openManualRegistration() {
  inboundApplicationId.value = '';
  queueMode.value = 'MANUAL';
}

function selectInboundApplication(applicationId: string) {
  const item = registrationQueue.value?.pendingApplications.find(
    (candidate) => candidate.applicationId === applicationId,
  );
  if (!item) return;
  inboundApplicationId.value = item.applicationId;
  draft.patientReference = item.patientReference;
  draft.visitReference = item.visitReference ?? '';
  draft.applicationNo = item.applicationNo;
  const mappedOption = businessOptions.value.find(
    (option) => option.applicationItemCode === item.applicationItemCode,
  );
  if (mappedOption) draft.businessTypeCode = mappedOption.code;
  if (!specimens.value.length) specimens.value = [createSpecimenDraft(0)];
  queueMode.value = 'MANUAL';
}

async function loadMappings() {
  mappingsLoading.value = true;
  try {
    const response = await fetch('/api/v2/registration/application-item-mappings');
    if (!response.ok) throw new Error('申请项目映射暂时无法加载');
    const values = (await response.json()) as Array<{
      applicationItemCode: string;
      defaultSpecimenKindCode: string;
      businessTypeCode: string;
      businessTypeName: string;
      modalityCode: string;
    }>;
    if (values.length) {
      businessOptions.value = values.map((value) => ({
        code: value.businessTypeCode,
        businessTypeName: businessTypeName(value.businessTypeCode),
        applicationItemCode: value.applicationItemCode,
        defaultSpecimenKindCode: value.defaultSpecimenKindCode,
        modalityCode: value.modalityCode,
      }));
      if (!businessOptions.value.some((value) => value.code === draft.businessTypeCode)) {
        draft.businessTypeCode = businessOptions.value[0]?.code ?? '';
      }
      mappingNotice.value = '申请项目已按当前医院配置映射业务类型。';
    } else {
      mappingNotice.value = '当前没有生效的申请项目映射，请联系配置管理员。';
    }
  } catch (requestError) {
    mappingNotice.value = friendlyError(requestError, '申请项目映射暂时无法加载。');
  } finally {
    mappingsLoading.value = false;
  }
}

async function loadRegistrationQueue() {
  queueLoading.value = true;
  try {
    registrationQueue.value = await getV2RegistrationQueue();
  } catch (requestError) {
    error.value = friendlyError(requestError, '待登记队列暂时无法加载。');
  } finally {
    queueLoading.value = false;
  }
}

async function submitRegistration() {
  if (!canSubmit.value || !selectedBusiness.value?.applicationItemCode) return;
  submitting.value = true;
  error.value = '';
  completedCase.value = null;
  try {
    progress.value = '正在生成病理号…';
    let createdCase: V2CaseResult;
    if (inboundApplicationId.value) {
      createdCase = await registerV2InboundApplication(inboundApplicationId.value);
    } else {
      progress.value = '姝ｅ湪淇濆瓨鐢宠鍜岀櫥璁拌褰?';
      const application = await createV2Application({
        applicationNo: draft.applicationNo.trim(),
        sourceTypeCode: 'MANUAL',
        sourceSystemCode: 'PIS-V2-MANUAL',
        patientReference: draft.patientReference.trim(),
        visitReference: draft.visitReference.trim(),
        applicantReference: props.authUser?.displayName ?? 'CURRENT_USER',
        items: [
          {
            externalItemCode: selectedBusiness.value.applicationItemCode,
            itemName: selectedBusiness.value.businessTypeName,
            specimenKindCode: selectedBusiness.value.defaultSpecimenKindCode,
            sequenceNo: 1,
          },
        ],
      });
      const registration = await registerV2Application(application.applicationId);
      const firstCase = registration.cases[0];
      if (!firstCase) throw new Error('鐢宠鐧昏鏈垱寤虹梾渚?');
      createdCase = await getV2Case(firstCase.caseId);
    }
    for (const [index, specimen] of specimens.value.entries()) {
      progress.value = `正在登记标本 ${index + 1}/${specimens.value.length}…`;
      await registerV2Specimen({
        caseId: createdCase.caseId,
        specimenCode: specimen.specimenCode,
        specimenKindCode: specimen.specimenKindCode,
        sourceKindCode:
          draft.businessTypeCode === 'CONSULTATION' || draft.businessTypeCode === 'REFERRAL'
            ? 'EXTERNAL'
            : 'LOCAL',
        sourceReference: draft.applicationNo.trim(),
        collectionSite: specimen.collectionSite.trim() || '外院材料',
        collectionMethodCode: specimen.collectionMethodCode,
        labelCode: '',
        idempotencyKey: `px02-specimen-${registrationRunId}-${specimen.key}`,
      });
    }
    completedCase.value = createdCase;
    progress.value = '';
    await loadRegistrationQueue();
  } catch (requestError) {
    error.value = friendlyError(requestError, '登记未完成，请核对必填信息后重试。');
  } finally {
    submitting.value = false;
  }
}

function nextWorkspacePath(): string {
  if (!completedCase.value) return '/v2/workbench';
  if (draft.businessTypeCode === 'FROZEN') return `/v2/frozen/${completedCase.value.caseId}`;
  if (draft.businessTypeCode === 'CYTOLOGY_NON_GYN')
    return `/v2/production/${completedCase.value.caseId}`;
  if (
    draft.businessTypeCode === 'MOLECULAR' ||
    draft.businessTypeCode === 'CONSULTATION' ||
    draft.businessTypeCode === 'REFERRAL'
  ) {
    return `/v2/cases/${completedCase.value.caseId}`;
  }
  return `/v2/grossing/${completedCase.value.caseId}`;
}

onMounted(() => void Promise.all([loadMappings(), loadRegistrationQueue()]));
</script>

<template>
  <section class="registration-layout" aria-label="病例登记工作区">
    <header class="page-heading compact-heading">
      <div>
        <p class="section-kicker">病例登记</p>
        <h2>核对申请并登记</h2>
        <p>核对申请信息，建立病例、病理号和初始标本。</p>
      </div>
      <span class="status-pill">登记员：{{ props.authUser?.displayName ?? '当前用户' }}</span>
    </header>

    <p v-if="error" class="feedback error" role="alert">{{ error }}</p>
    <p v-if="progress" class="feedback info" role="status">{{ progress }}</p>
    <p v-if="mappingNotice" class="feedback info" role="status">{{ mappingNotice }}</p>
    <section v-if="completedCase" class="feedback success registration-success" role="status">
      <span
        ><strong>登记完成，病理号已生成：{{ completedCase.caseNo }}</strong
        ><br />已登记 {{ specimens.length }} 个标本，业务类型为{{
          businessTypeName(completedCase.businessTypeCode)
        }}。</span
      >
      <button class="primary-button" type="button" @click="emit('navigate', nextWorkspacePath())">
        进入下一步
      </button>
    </section>
    <V2CaseHeader
      v-if="completedCase"
      :case-id="completedCase.caseId"
      :pathology-no="completedCase.caseNo"
      :patient-reference="completedCase.patientReference"
      :visit-reference="completedCase.visitReference"
      :business-type-code="completedCase.businessTypeCode"
      current-responsibility="登记已完成"
      report-status="已登记"
      :progress="`${specimens.length} 个标本已登记`"
      @open-case="emit('navigate', `/v2/cases/${completedCase.caseId}`)"
      @open-overview="emit('navigate', `/v2/cases/${completedCase.caseId}`)"
    >
      <template #actions>
        <button
          class="secondary-button"
          type="button"
          @click="emit('navigate', `/v2/cases/${completedCase.caseId}`)"
        >
          病例中心
        </button>
        <button class="secondary-button" type="button" @click="historyDrawerOpen = true">
          历史记录
        </button>
      </template>
    </V2CaseHeader>

    <section
      v-if="queueMode === 'QUEUE'"
      class="registration-queue-workspace"
      aria-label="待登记申请队列"
    >
      <header class="workspace-toolbar registration-queue-toolbar">
        <div>
          <p class="section-kicker">登记工作台</p>
          <h2>先处理待登记申请</h2>
          <p class="muted">队列展示当前权限范围内的申请；来源未连接时会明确提示。</p>
        </div>
        <div class="inline-actions">
          <button
            class="secondary-button"
            type="button"
            :disabled="queueLoading"
            @click="loadRegistrationQueue"
          >
            {{ queueLoading ? '刷新中…' : '刷新队列' }}
          </button>
          <button class="primary-button" type="button" @click="openManualRegistration">
            新增手工病例
          </button>
        </div>
      </header>
      <div class="registration-queue-grid">
        <section class="workspace-panel registration-pending-panel">
          <header class="panel-title-row">
            <div>
              <p class="section-kicker">申请</p>
              <h3>待登记申请</h3>
            </div>
            <span class="count-pill">{{ registrationQueue?.pendingApplications.length ?? 0 }}</span>
          </header>
          <div v-if="registrationQueue?.pendingApplications.length" class="registration-queue-list">
            <button
              v-for="item in registrationQueue.pendingApplications"
              :key="item.applicationNo"
              type="button"
              class="registration-queue-row"
              @click="selectInboundApplication(item.applicationId)"
            >
              <strong>{{ item.applicationNo }}</strong
              ><span>{{ item.patientReference }}</span
              ><small
                >{{ item.businessTypeName || '待确认业务类型' }} ·
                {{ item.department || '未填写科室' }}</small
              ><span>登记</span>
            </button>
          </div>
          <div v-else class="empty-state compact">
            <strong>{{
              registrationQueue?.sourceAvailable ? '当前没有待登记申请' : '申请来源未连接'
            }}</strong
            ><span>{{ registrationQueue?.sourceMessage || '新的申请会按配置映射进入这里。' }}</span>
          </div>
          <div
            v-if="registrationQueue?.cancelledApplications.length"
            class="registration-cancelled-list"
          >
            <h4>已取消申请</h4>
            <button
              v-for="item in registrationQueue.cancelledApplications"
              :key="item.applicationId"
              type="button"
              class="registration-queue-row cancelled"
              disabled
              :title="`${item.applicationNo} 已取消，不能登记`"
            >
              <strong>{{ item.applicationNo }}</strong
              ><span>{{ item.patientReference }}</span
              ><small>申请已取消</small><span>不可登记</span>
            </button>
          </div>
        </section>
        <section class="workspace-panel registration-recent-panel">
          <header class="panel-title-row">
            <div>
              <p class="section-kicker">今日</p>
              <h3>今日已登记</h3>
            </div>
            <span class="count-pill">{{ registrationQueue?.recentRegistrations.length ?? 0 }}</span>
          </header>
          <div v-if="registrationQueue?.recentRegistrations.length" class="registration-queue-list">
            <button
              v-for="item in registrationQueue.recentRegistrations"
              :key="item.caseId"
              type="button"
              class="registration-queue-row"
              @click="emit('navigate', `/v2/cases/${item.caseId}`)"
            >
              <strong>{{ item.caseNo }}</strong
              ><span>{{ item.patientReference }}</span
              ><small>{{ item.businessTypeName }} · {{ item.applicationNo }}</small
              ><span>打开</span>
            </button>
          </div>
          <div v-else class="empty-state compact">
            <strong>今天还没有登记记录</strong><span>完成手工登记后，病例会显示在这里。</span>
          </div>
        </section>
      </div>
    </section>

    <template v-else>
      <div class="registration-top-grid">
        <section class="workspace-panel" aria-labelledby="patient-heading">
          <header class="panel-title-row">
            <div>
              <p class="section-kicker">患者 / 就诊</p>
              <h3 id="patient-heading">患者基本信息</h3>
            </div>
          </header>
          <p class="feedback info compact-feedback">
            {{
              selectedInboundApplication
                ? '已从申请带入患者和就诊信息，请核对后继续。'
                : '手工登记请输入患者编号与本次就诊号。'
            }}
          </p>
          <div class="field-grid">
            <label
              >患者编号<input
                v-model="draft.patientReference"
                required
                autocomplete="off"
                placeholder="门诊号或住院号"
            /></label>
            <label
              >就诊号<input
                v-model="draft.visitReference"
                required
                autocomplete="off"
                placeholder="本次就诊标识"
            /></label>
          </div>
        </section>
        <section class="workspace-panel" aria-labelledby="registration-heading">
          <header class="panel-title-row">
            <div>
              <p class="section-kicker">登记信息</p>
              <h3 id="registration-heading">业务类型与编号</h3>
              <p class="muted">申请项目决定默认业务类型和病理号规则。</p>
            </div>
            <span
              class="status-pill"
              :class="{ success: Boolean(selectedBusiness?.applicationItemCode) }"
              >{{ selectedBusiness?.applicationItemCode ? '已映射' : '待映射' }}</span
            >
          </header>
          <div class="field-grid">
            <label
              >业务类型<select
                v-model="draft.businessTypeCode"
                aria-label="业务类型"
                :disabled="mappingsLoading"
                @change="changeBusinessType"
              >
                <option v-for="option in businessOptions" :key="option.code" :value="option.code">
                  {{ option.businessTypeName || businessTypeName(option.code) }}
                </option>
              </select></label
            >
            <label>申请号<input v-model="draft.applicationNo" required /></label>
            <label class="span-two"
              >生效申请项目<input
                :value="
                  selectedBusiness?.applicationItemCode ? '已按医院配置映射' : '未找到生效映射'
                "
                readonly
            /></label>
          </div>
        </section>
      </div>

      <section class="workspace-panel" aria-labelledby="application-heading">
        <header class="panel-title-row">
          <div>
            <p class="section-kicker">申请信息</p>
            <h3 id="application-heading">临床申请</h3>
          </div>
        </header>
        <div class="application-summary-row">
          <span><small>申请来源</small><strong>手工登记</strong></span
          ><span
            ><small>申请项目</small
            ><strong>{{
              selectedBusiness?.applicationItemCode ? '已按配置映射' : '未映射'
            }}</strong></span
          ><span
            ><small>业务类型</small
            ><strong>{{
              selectedBusiness?.businessTypeName || businessTypeName(draft.businessTypeCode)
            }}</strong></span
          >
        </div>
      </section>

      <section class="workspace-panel" aria-labelledby="specimen-heading">
        <header class="panel-title-row">
          <div>
            <p class="section-kicker">标本信息</p>
            <h3 id="specimen-heading">本次送检标本</h3>
            <p class="muted">可复制相近标本并调整部位；会诊病例可在后续登记外院玻片或蜡块。</p>
          </div>
          <button class="secondary-button" type="button" @click="addSpecimen()">+ 新增标本</button>
        </header>
        <div v-if="!specimens.length" class="empty-state compact">
          <strong>本次未登记本院标本</strong
          ><span>当前业务允许无本院标本，登记完成后进入病例中心。</span>
        </div>
        <div v-else class="specimen-list-editor">
          <div
            v-for="(specimen, index) in specimens"
            :key="specimen.key"
            class="specimen-row-editor"
          >
            <span class="specimen-code" :aria-label="`标本 ${specimen.specimenCode}`">{{
              specimen.specimenCode
            }}</span>
            <label
              >取材部位<input
                v-model="specimen.collectionSite"
                required
                placeholder="例如 胃窦活检"
            /></label>
            <label
              >标本类型<select v-model="specimen.specimenKindCode">
                <option value="TISSUE">组织</option>
                <option value="FLUID">液体</option>
                <option value="SMEAR">涂片</option>
                <option value="EXTERNAL_MATERIAL">外院材料</option>
              </select></label
            >
            <label
              >采集方式<select v-model="specimen.collectionMethodCode">
                <option value="SURGICAL">手术切除</option>
                <option value="BIOPSY">活检</option>
                <option value="ASPIRATION">穿刺 / 抽吸</option>
                <option value="FRESH">新鲜送检</option>
                <option value="EXTERNAL">外院送检</option>
              </select></label
            >
            <div class="specimen-row-actions" :aria-label="`标本 ${specimen.specimenCode} 操作`">
              <button class="text-button" type="button" @click="duplicateSpecimen(index)">
                复制</button
              ><button
                class="text-button"
                type="button"
                :disabled="index === 0"
                @click="moveSpecimen(index, -1)"
              >
                上移</button
              ><button
                class="text-button"
                type="button"
                :disabled="index === specimens.length - 1"
                @click="moveSpecimen(index, 1)"
              >
                下移</button
              ><button class="text-button danger-text" type="button" @click="removeSpecimen(index)">
                删除
              </button>
            </div>
          </div>
        </div>
      </section>

      <div class="sticky-form-actions" aria-label="登记操作">
        <span class="muted"
          >{{ specimens.length }} 个标本 ·
          {{ selectedBusiness?.businessTypeName || businessTypeName(draft.businessTypeCode) }}</span
        >
        <div class="action-group">
          <button class="secondary-button" type="button" :disabled="submitting">取消</button
          ><button
            class="primary-button"
            type="button"
            :disabled="!canSubmit"
            @click="submitRegistration"
          >
            {{ submitting ? '正在登记…' : '确认登记' }}
          </button>
        </div>
      </div>
    </template>
    <V2HistoryDrawer
      :open="historyDrawerOpen"
      :case-id="completedCase?.caseId"
      title="登记历史"
      target-label="登记"
      @close="historyDrawerOpen = false"
    />
  </section>
</template>
