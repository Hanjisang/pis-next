<script setup lang="ts">
import { computed, reactive, ref } from 'vue';

import { currentRecorder, type V2AuthUser } from '../auth';
import { createV2Case, registerV2Specimen, type V2CaseResult } from '../v2Api';
import {
  completeV2MolecularResult,
  registerV2ConsultationMaterial,
  type V2ConsultationMaterialResult,
  type V2MolecularResult,
} from '../v2BusinessApi';
import { completeV2Slide } from '../v2MaterialApi';
import { businessTypeName, friendlyError, idempotencyKey } from '../uiText';

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
  applicationItemCode: string;
  defaultSpecimenKindCode: string;
};

const props = defineProps<{ authUser?: V2AuthUser | null }>();
const emit = defineEmits<{ navigate: [path: string] }>();

const businessOptions: BusinessOption[] = [
  { code: 'HISTOLOGY', applicationItemCode: 'SYNTH-HISTOLOGY', defaultSpecimenKindCode: 'TISSUE' },
  { code: 'FROZEN', applicationItemCode: 'SYNTH-FROZEN', defaultSpecimenKindCode: 'TISSUE' },
  {
    code: 'CYTOLOGY_NON_GYN',
    applicationItemCode: 'SYNTH-CYTOLOGY',
    defaultSpecimenKindCode: 'FLUID',
  },
  { code: 'MOLECULAR', applicationItemCode: 'SYNTH-MOLECULAR', defaultSpecimenKindCode: 'TISSUE' },
  {
    code: 'CONSULTATION',
    applicationItemCode: 'SYNTH-CONSULTATION',
    defaultSpecimenKindCode: 'EXTERNAL_MATERIAL',
  },
];

const draft = reactive({
  patientReference: '',
  visitReference: '',
  applicationNo: `M-${new Date().toISOString().slice(0, 10).replaceAll('-', '')}-${String(Date.now()).slice(-4)}`,
  businessTypeCode: 'HISTOLOGY',
});

const submitting = ref(false);
const progress = ref('');
const error = ref('');
const completedCase = ref<V2CaseResult | null>(null);
const registeredSpecimenIds = ref<string[]>([]);
const followUpBusy = ref(false);
const followUpNotice = ref('');
const molecularProject = ref('常用分子检测');
const molecularConclusion = ref('');
const molecularResult = ref<V2MolecularResult | null>(null);
const externalReference = ref('');
const externalBlockCode = ref('EXT-B1');
const localSlideCode = ref('LOCAL-S1');
const consultationMaterial = ref<V2ConsultationMaterialResult | null>(null);
const consultationSlideCompleted = ref(false);
const registrationRunId = crypto.randomUUID();

const selectedBusiness = computed(
  () =>
    businessOptions.find((option) => option.code === draft.businessTypeCode) ?? businessOptions[0],
);
const canCompleteMolecular = computed(() =>
  Boolean(props.authUser?.permissions.includes('P14-PERM-014')),
);
const specimens = ref<SpecimenDraft[]>([createSpecimenDraft(0)]);
const canSubmit = computed(() => {
  const patientReady = draft.patientReference.trim() && draft.visitReference.trim();
  const applicationReady = draft.applicationNo.trim() && selectedBusiness.value;
  const specimenReady =
    draft.businessTypeCode === 'CONSULTATION' ||
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
  if (draft.businessTypeCode === 'CONSULTATION') {
    specimens.value = [];
    return;
  }
  if (!specimens.value.length) specimens.value = [createSpecimenDraft(0)];
  for (const specimen of specimens.value) {
    specimen.specimenKindCode = selectedBusiness.value?.defaultSpecimenKindCode ?? 'TISSUE';
  }
}

async function submitRegistration() {
  if (!canSubmit.value || !selectedBusiness.value) return;
  submitting.value = true;
  error.value = '';
  completedCase.value = null;
  registeredSpecimenIds.value = [];
  try {
    progress.value = '正在生成病理号…';
    const createdCase = await createV2Case({
      sourceSystemCode: 'MANUAL',
      externalApplicationId: draft.applicationNo.trim(),
      applicationItemCode: selectedBusiness.value.applicationItemCode,
      patientReference: draft.patientReference.trim(),
      visitReference: draft.visitReference.trim(),
      idempotencyKey: `ux01-registration-${registrationRunId}`,
    });
    for (const [index, specimen] of specimens.value.entries()) {
      progress.value = `正在登记标本 ${index + 1}/${specimens.value.length}…`;
      const registered = await registerV2Specimen({
        caseId: createdCase.caseId,
        specimenCode: specimen.specimenCode,
        specimenKindCode: specimen.specimenKindCode,
        sourceKindCode: draft.businessTypeCode === 'CONSULTATION' ? 'EXTERNAL' : 'LOCAL',
        sourceReference: draft.applicationNo.trim(),
        collectionSite: specimen.collectionSite.trim(),
        collectionMethodCode: specimen.collectionMethodCode,
        labelCode: '',
        idempotencyKey: `ux01-specimen-${registrationRunId}-${specimen.key}`,
      });
      registeredSpecimenIds.value.push(registered.specimenId);
    }
    completedCase.value = createdCase;
    progress.value = '';
  } catch (requestError) {
    error.value = friendlyError(requestError, '登记未完成，请核对必填信息后重试。');
  } finally {
    submitting.value = false;
  }
}

async function runFollowUp(action: () => Promise<void>) {
  followUpBusy.value = true;
  error.value = '';
  followUpNotice.value = '';
  try {
    await action();
  } catch (requestError) {
    error.value = friendlyError(requestError, '后续业务未完成，请核对信息或交给有权限的岗位处理。');
  } finally {
    followUpBusy.value = false;
  }
}

function submitMolecularResult() {
  if (!completedCase.value || !molecularProject.value.trim() || !molecularConclusion.value.trim())
    return;
  void runFollowUp(async () => {
    molecularResult.value = await completeV2MolecularResult({
      caseId: completedCase.value!.caseId,
      specimenId: registeredSpecimenIds.value[0],
      resultCode: molecularProject.value.trim(),
      resultData: JSON.stringify({ conclusion: molecularConclusion.value.trim() }),
      idempotencyKey: idempotencyKey('ux01-independent-molecular-result'),
    });
    followUpNotice.value = '分子结果已完成，病例已进入待诊池。';
  });
}

function registerConsultationMaterial() {
  if (!completedCase.value || !externalReference.value.trim()) return;
  void runFollowUp(async () => {
    consultationMaterial.value = await registerV2ConsultationMaterial({
      caseId: completedCase.value!.caseId,
      externalReference: externalReference.value.trim(),
      specimenKindCode: 'TISSUE',
      blockCode: externalBlockCode.value.trim(),
      blockType: 'EXTERNAL',
      operatorId: currentRecorder(props.authUser ?? null),
      createLocalSlide: true,
      localSlideCode: localSlideCode.value.trim(),
      localSlideType: 'HE',
      idempotencyKey: idempotencyKey('ux01-consultation-material'),
    });
    followUpNotice.value = `外院蜡块 ${externalBlockCode.value.trim()} 已登记，并生成本院玻片 ${localSlideCode.value.trim()}。`;
  });
}

function completeConsultationSlide() {
  const slideId = consultationMaterial.value?.slideId;
  if (!slideId) return;
  void runFollowUp(async () => {
    await completeV2Slide({
      slideId,
      expectedVersion: 0,
      idempotencyKey: idempotencyKey('ux01-consultation-slide-complete'),
    });
    consultationSlideCompleted.value = true;
    followUpNotice.value = `本院玻片 ${localSlideCode.value.trim()} 已完成，病例已进入待诊池。`;
  });
}

function nextWorkspacePath(): string {
  if (!completedCase.value) return '/v2/workbench';
  if (draft.businessTypeCode === 'FROZEN') return `/v2/frozen/${completedCase.value.caseId}`;
  if (draft.businessTypeCode === 'CYTOLOGY_NON_GYN') {
    return `/v2/production/${completedCase.value.caseId}`;
  }
  return `/v2/grossing/${completedCase.value.caseId}`;
}
</script>

<template>
  <section class="registration-layout" aria-label="病例登记工作区">
    <header class="page-heading compact-heading">
      <div>
        <p class="section-kicker">病例登记</p>
        <h2>核对申请并登记</h2>
        <p>患者、申请和标本在本页一次确认；提交后自动生成病理号。</p>
      </div>
      <span class="status-pill">登记员：{{ props.authUser?.displayName ?? '当前用户' }}</span>
    </header>

    <p v-if="error" class="feedback error" role="alert">{{ error }}</p>
    <p v-if="progress" class="feedback info" role="status">{{ progress }}</p>
    <section v-if="completedCase" class="feedback success registration-success" role="status">
      <span>
        <strong>登记完成，病理号已生成：{{ completedCase.caseNo }}</strong
        ><br />
        已登记 {{ specimens.length }} 个标本，业务类型为{{
          businessTypeName(completedCase.businessTypeCode)
        }}。
      </span>
      <button
        v-if="!['MOLECULAR', 'CONSULTATION'].includes(draft.businessTypeCode)"
        class="primary-button"
        type="button"
        @click="emit('navigate', nextWorkspacePath())"
      >
        进入下一步
      </button>
    </section>
    <p v-if="followUpNotice" class="feedback success" role="status">{{ followUpNotice }}</p>

    <section
      v-if="completedCase && draft.businessTypeCode === 'MOLECULAR'"
      class="workspace-panel registration-follow-up"
      aria-labelledby="molecular-result-heading"
    >
      <header class="panel-title-row">
        <div>
          <p class="section-kicker">独立分子病例</p>
          <h3 id="molecular-result-heading">录入结构化分子结果</h3>
          <p>独立申请在当前病例完成结果后进入诊断，不需要伪造取材、蜡块或玻片。</p>
        </div>
        <span class="status-pill" :class="{ success: molecularResult }">{{
          molecularResult ? '结果已完成' : '待录结果'
        }}</span>
      </header>
      <div class="field-grid">
        <label>
          检测项目
          <input v-model="molecularProject" :readonly="Boolean(molecularResult)" />
        </label>
        <label class="span-two">
          结果结论
          <textarea
            v-model="molecularConclusion"
            rows="3"
            :readonly="Boolean(molecularResult)"
            placeholder="录入可供诊断医生查看的结构化结论"
          ></textarea>
        </label>
      </div>
      <p v-if="!canCompleteMolecular" class="feedback info compact-feedback">
        病例已登记。分子结果由技术人员从全局查询打开该病例后录入。
      </p>
      <div class="panel-footer-actions">
        <button
          v-if="!molecularResult && canCompleteMolecular"
          class="primary-button"
          type="button"
          :disabled="followUpBusy || !molecularProject.trim() || !molecularConclusion.trim()"
          @click="submitMolecularResult"
        >
          {{ followUpBusy ? '正在保存…' : '完成分子结果' }}
        </button>
        <button
          v-else-if="molecularResult"
          class="primary-button"
          type="button"
          @click="emit('navigate', `/v2/diagnosis/${completedCase.caseId}`)"
        >
          查看待诊病例
        </button>
      </div>
    </section>

    <section
      v-if="completedCase && draft.businessTypeCode === 'CONSULTATION'"
      class="workspace-panel registration-follow-up"
      aria-labelledby="consultation-material-heading"
    >
      <header class="panel-title-row">
        <div>
          <p class="section-kicker">外院会诊材料</p>
          <h3 id="consultation-material-heading">外院蜡块转本院玻片</h3>
          <p>保留外院材料来源，并生成统一的本院玻片；无需伪造本院标本链。</p>
        </div>
        <span class="status-pill" :class="{ success: consultationSlideCompleted }">{{
          consultationSlideCompleted
            ? '玻片已完成'
            : consultationMaterial
              ? canCompleteMolecular
                ? '待完成玻片'
                : '待技术完成'
              : '待登记材料'
        }}</span>
      </header>
      <div class="field-grid">
        <label>
          外院材料编号
          <input
            v-model="externalReference"
            :readonly="Boolean(consultationMaterial)"
            placeholder="外院蜡块或材料编号"
          />
        </label>
        <label>
          本院蜡块号
          <input v-model="externalBlockCode" :readonly="Boolean(consultationMaterial)" />
        </label>
        <label>
          本院玻片号
          <input v-model="localSlideCode" :readonly="Boolean(consultationMaterial)" />
        </label>
      </div>
      <div class="panel-footer-actions">
        <button
          v-if="!consultationMaterial"
          class="primary-button"
          type="button"
          :disabled="
            followUpBusy ||
            !externalReference.trim() ||
            !externalBlockCode.trim() ||
            !localSlideCode.trim()
          "
          @click="registerConsultationMaterial"
        >
          {{ followUpBusy ? '正在登记…' : '登记并生成本院玻片' }}
        </button>
        <button
          v-else-if="!consultationSlideCompleted && canCompleteMolecular"
          class="primary-button"
          type="button"
          :disabled="followUpBusy"
          @click="completeConsultationSlide"
        >
          {{ followUpBusy ? '正在完成…' : '完成玻片' }}
        </button>
        <button
          v-else
          class="primary-button"
          type="button"
          @click="emit('navigate', `/v2/diagnosis/${completedCase.caseId}`)"
        >
          查看待诊病例
        </button>
      </div>
      <p
        v-if="consultationMaterial && !consultationSlideCompleted && !canCompleteMolecular"
        class="feedback info compact-feedback"
      >
        本院玻片已生成。请交由技术人员在制片工作台扫码或勾选完成，完成后病例自动进入待诊池。
      </p>
    </section>

    <div class="registration-top-grid">
      <section class="workspace-panel" aria-labelledby="patient-heading">
        <header class="panel-title-row">
          <div>
            <p class="section-kicker">患者 / 就诊</p>
            <h3 id="patient-heading">患者基本信息</h3>
          </div>
        </header>
        <p class="feedback info compact-feedback">
          患者姓名、性别、年龄和临床申请将在选择医院申请后自动带入；当前测试环境仅录入患者编号与就诊号。
        </p>
        <div class="field-grid">
          <label>
            患者编号
            <input
              v-model="draft.patientReference"
              required
              autocomplete="off"
              placeholder="门诊号或住院号"
            />
          </label>
          <label>
            就诊号
            <input
              v-model="draft.visitReference"
              required
              autocomplete="off"
              placeholder="本次就诊标识"
            />
          </label>
        </div>
      </section>

      <section class="workspace-panel" aria-labelledby="registration-heading">
        <header class="panel-title-row">
          <div>
            <p class="section-kicker">登记信息</p>
            <h3 id="registration-heading">业务类型与编号</h3>
          </div>
          <span class="status-pill success">{{ businessTypeName(draft.businessTypeCode) }}</span>
        </header>
        <div class="field-grid">
          <label>
            业务类型
            <select
              v-model="draft.businessTypeCode"
              aria-label="业务类型"
              @change="changeBusinessType"
            >
              <option v-for="option in businessOptions" :key="option.code" :value="option.code">
                {{ businessTypeName(option.code) }}
              </option>
            </select>
          </label>
          <label>申请号 <input v-model="draft.applicationNo" required /></label>
          <label class="span-two">
            病理号规则
            <input :value="`${businessTypeName(draft.businessTypeCode)}默认编号规则`" readonly />
          </label>
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
        <span><small>申请来源</small><strong>手工登记</strong></span>
        <span
          ><small>申请项目</small
          ><strong>{{ businessTypeName(draft.businessTypeCode) }}</strong></span
        >
        <span><small>匹配结果</small><strong class="success-text">已识别业务类型</strong></span>
      </div>
    </section>

    <section class="workspace-panel" aria-labelledby="specimen-heading">
      <header class="panel-title-row">
        <div>
          <p class="section-kicker">标本信息</p>
          <h3 id="specimen-heading">本次送检标本</h3>
          <p class="muted">
            {{
              draft.businessTypeCode === 'CONSULTATION'
                ? '会诊可直接登记外院材料，标本可留空。'
                : '可复制相近标本并调整部位。'
            }}
          </p>
        </div>
        <button class="secondary-button" type="button" @click="addSpecimen()">+ 新增标本</button>
      </header>

      <div v-if="!specimens.length" class="empty-state compact">
        <strong>本次未登记本院标本</strong><span>会诊病例可在后续登记外院玻片或蜡块。</span>
      </div>
      <div v-else class="specimen-list-editor">
        <div v-for="(specimen, index) in specimens" :key="specimen.key" class="specimen-row-editor">
          <span class="specimen-code" :aria-label="`标本 ${specimen.specimenCode}`">{{
            specimen.specimenCode
          }}</span>
          <label>
            取材部位
            <input v-model="specimen.collectionSite" required placeholder="例如 胃窦活检" />
          </label>
          <label>
            标本类型
            <select v-model="specimen.specimenKindCode">
              <option value="TISSUE">组织</option>
              <option value="FLUID">液体</option>
              <option value="SMEAR">涂片</option>
              <option value="EXTERNAL_MATERIAL">外院材料</option>
            </select>
          </label>
          <label>
            采集方式
            <select v-model="specimen.collectionMethodCode">
              <option value="SURGICAL">手术切除</option>
              <option value="BIOPSY">活检</option>
              <option value="ASPIRATION">穿刺 / 抽吸</option>
              <option value="FRESH">新鲜送检</option>
              <option value="EXTERNAL">外院送检</option>
            </select>
          </label>
          <div class="specimen-row-actions" :aria-label="`标本 ${specimen.specimenCode} 操作`">
            <button class="text-button" type="button" @click="duplicateSpecimen(index)">
              复制
            </button>
            <button
              class="text-button"
              type="button"
              :disabled="index === 0"
              @click="moveSpecimen(index, -1)"
            >
              上移
            </button>
            <button
              class="text-button"
              type="button"
              :disabled="index === specimens.length - 1"
              @click="moveSpecimen(index, 1)"
            >
              下移
            </button>
            <button class="text-button danger-text" type="button" @click="removeSpecimen(index)">
              删除
            </button>
          </div>
        </div>
      </div>
    </section>

    <div class="sticky-form-actions" aria-label="登记操作">
      <span class="muted">
        {{ specimens.length }} 个标本 · {{ businessTypeName(draft.businessTypeCode) }}
      </span>
      <div class="action-group">
        <button class="secondary-button" type="button" :disabled="submitting">取消</button>
        <button
          class="primary-button"
          type="button"
          :disabled="!canSubmit"
          @click="submitRegistration"
        >
          {{ submitting ? '正在登记…' : '确认登记' }}
        </button>
      </div>
    </div>
  </section>
</template>
