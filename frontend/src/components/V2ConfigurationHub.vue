<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';

import { friendlyError } from '../uiText';
import {
  createV2ReportTemplate,
  createV2ReportTemplateVersion,
  getV2Configuration,
  getV2ReportTemplateCatalog,
  getV2ReportTemplatePresets,
  instantiateV2ReportTemplatePreset,
  publishV2ReportTemplateVersion,
  updateV2Configuration,
  type V2ConfigurationSnapshot,
  type V2ReportTemplateCatalogRow,
  type V2ReportTemplatePreset,
} from '../v2ConfigurationApi';
import {
  createV2ArchiveLocation,
  getV2ArchiveLocations,
  type V2ArchiveLocation,
} from '../v2CustodyApi';
import {
  createV2AssignmentRule,
  getV2AssignmentRules,
  updateV2AssignmentRule,
  type V2AssignmentRule,
} from '../v2DiagnosisApi';

type ConfigSection =
  | 'businessTypes'
  | 'applicationItemMappings'
  | 'pathologyNumberRules'
  | 'technicalProjects'
  | 'assignmentRules'
  | 'diagnosisTemplates'
  | 'reportTemplates'
  | 'tatPolicies'
  | 'archiveLocations';

type ReportSectionSource =
  | 'CASE'
  | 'MATERIAL'
  | 'DIAGNOSIS'
  | 'TECHNICAL'
  | 'SIGNATURE'
  | 'SUPPLEMENTAL';

type ReportSectionDraft = {
  clientId: string;
  code: string;
  label: string;
  source: ReportSectionSource;
  fields: string;
};

const sectionOptions: Array<{ key: ConfigSection; label: string; group: string }> = [
  { key: 'businessTypes', label: '业务类型', group: '业务配置' },
  { key: 'applicationItemMappings', label: '申请项目映射', group: '业务配置' },
  { key: 'pathologyNumberRules', label: '病理号规则', group: '业务配置' },
  { key: 'diagnosisTemplates', label: '诊断模板', group: '诊断配置' },
  { key: 'assignmentRules', label: '自动分诊规则', group: '诊断配置' },
  { key: 'reportTemplates', label: '报告模板', group: '诊断配置' },
  { key: 'tatPolicies', label: '报告时效策略', group: '诊断配置' },
  { key: 'technicalProjects', label: '技术项目', group: '诊断配置' },
  { key: 'archiveLocations', label: '归档库位', group: '生产配置' },
];

const snapshot = ref<V2ConfigurationSnapshot | null>(null);
const activeSection = ref<ConfigSection>('businessTypes');
const loading = ref(false);
const saving = ref(false);
const error = ref('');
const notice = ref('');
const archiveLocations = ref<V2ArchiveLocation[]>([]);
const assignmentRules = ref<V2AssignmentRule[]>([]);
const reportTemplatePresets = ref<V2ReportTemplatePreset[]>([]);
const reportTemplateCatalog = ref<V2ReportTemplateCatalogRow[]>([]);
const selectedReportTemplateId = ref('');
const selectedDraftVersionId = ref('');
const reportTemplateDraft = ref({
  code: '',
  name: '',
  businessTypeId: '',
  presetCode: '',
});
const reportDesigner = ref({
  title: '病理诊断报告',
  category: 'GENERAL' as 'GENERAL' | 'TUMOR',
  tumorSiteCode: '',
  showPageNumber: true,
  sections: [] as ReportSectionDraft[],
});
const assignmentDraft = ref({
  campus: 'MAIN',
  businessTypeCode: 'HISTOLOGY',
  department: '*',
  site: '*',
  diagnosisGroup: '',
  doctorId: '',
  priority: 0,
  dailyCaseLimit: 0,
  enabled: true,
});
const archiveDraft = ref({
  parentId: '',
  locationCode: '',
  locationName: '',
  locationKindCode: 'SLOT',
});

const activeLabel = computed(
  () => sectionOptions.find((item) => item.key === activeSection.value)?.label ?? '配置',
);

async function load() {
  loading.value = true;
  error.value = '';
  try {
    [
      snapshot.value,
      archiveLocations.value,
      assignmentRules.value,
      reportTemplatePresets.value,
      reportTemplateCatalog.value,
    ] = await Promise.all([
      getV2Configuration(),
      getV2ArchiveLocations(),
      getV2AssignmentRules(),
      getV2ReportTemplatePresets(),
      getV2ReportTemplateCatalog(),
    ]);
    if (!reportTemplateDraft.value.businessTypeId && snapshot.value.businessTypes.length) {
      reportTemplateDraft.value.businessTypeId = snapshot.value.businessTypes[0].id;
    }
  } catch (requestError) {
    error.value = friendlyError(requestError, '配置暂时无法加载，请稍后重试。');
  } finally {
    loading.value = false;
  }
}

async function saveAssignmentRule(rule?: V2AssignmentRule) {
  saving.value = true;
  error.value = '';
  notice.value = '';
  try {
    const saved = rule
      ? await updateV2AssignmentRule({
          ...rule,
          idempotencyKey: `assignment-rule-update-${crypto.randomUUID()}`,
        })
      : await createV2AssignmentRule({
          ...assignmentDraft.value,
          idempotencyKey: `assignment-rule-create-${crypto.randomUUID()}`,
        });
    const remaining = assignmentRules.value.filter(
      (item) => item.assignmentRuleId !== saved.assignmentRuleId,
    );
    assignmentRules.value = [...remaining, saved].sort(
      (left, right) =>
        left.businessTypeCode.localeCompare(right.businessTypeCode) ||
        left.priority - right.priority,
    );
    if (!rule) {
      assignmentDraft.value = {
        campus: 'MAIN',
        businessTypeCode: 'HISTOLOGY',
        department: '*',
        site: '*',
        diagnosisGroup: '',
        doctorId: '',
        priority: 0,
        dailyCaseLimit: 0,
        enabled: true,
      };
    }
    notice.value = `自动分诊规则已${rule ? '更新' : '创建'}。`;
  } catch (requestError) {
    error.value = friendlyError(requestError, '自动分诊规则保存失败，请刷新后重试。');
  } finally {
    saving.value = false;
  }
}

async function saveArchiveLocation() {
  saving.value = true;
  error.value = '';
  notice.value = '';
  try {
    const created = await createV2ArchiveLocation({
      parentId: archiveDraft.value.parentId || undefined,
      locationCode: archiveDraft.value.locationCode.trim(),
      locationName: archiveDraft.value.locationName.trim(),
      locationKindCode: archiveDraft.value.locationKindCode,
    });
    archiveLocations.value = [...archiveLocations.value, created].sort((a, b) =>
      a.locationCode.localeCompare(b.locationCode),
    );
    archiveDraft.value = {
      parentId: '',
      locationCode: '',
      locationName: '',
      locationKindCode: 'SLOT',
    };
    notice.value = '归档库位已保存，归档操作将从配置中选择。';
  } catch (requestError) {
    error.value = friendlyError(requestError, '归档库位保存失败，请检查编码是否重复。');
  } finally {
    saving.value = false;
  }
}

async function save(path: string, body: unknown) {
  saving.value = true;
  error.value = '';
  notice.value = '';
  try {
    snapshot.value = await updateV2Configuration(path, body);
    notice.value = `${activeLabel.value}已保存，版本已更新。`;
  } catch (requestError) {
    error.value = friendlyError(requestError, '配置保存失败，请刷新后重试。');
  } finally {
    saving.value = false;
  }
}

function saveTatPolicy(item: V2ConfigurationSnapshot['reportTatPolicies'][number]) {
  return save(`/tat-policies/${item.businessTypeId}`, {
    warningMinutes: item.warningMinutes,
    targetMinutes: item.targetMinutes,
    enabled: item.enabled,
    expectedVersion: item.configurationVersion,
  });
}

const reportTemplateChoices = computed(() => {
  const seen = new Set<string>();
  return reportTemplateCatalog.value.filter((item) => {
    if (seen.has(item.templateId)) return false;
    seen.add(item.templateId);
    return true;
  });
});

function blankSections(): ReportSectionDraft[] {
  return [
    {
      clientId: crypto.randomUUID(),
      code: 'BASIC',
      label: '基本信息',
      source: 'CASE',
      fields: 'pathologyNo, patientReference, visitReference',
    },
    {
      clientId: crypto.randomUUID(),
      code: 'DIAGNOSIS',
      label: '病理诊断',
      source: 'DIAGNOSIS',
      fields: 'microscopicDescription, diagnosisText, structuredData, comment',
    },
    {
      clientId: crypto.randomUUID(),
      code: 'SIGNATURE',
      label: '签发信息',
      source: 'SIGNATURE',
      fields: 'signedBy, signedAt',
    },
  ];
}

function loadDesignerDefinition(definition?: string) {
  if (!definition) {
    reportDesigner.value = {
      title: '病理诊断报告',
      category: 'GENERAL',
      tumorSiteCode: '',
      showPageNumber: true,
      sections: blankSections(),
    };
    return;
  }
  try {
    const parsed = JSON.parse(definition) as {
      schemaVersion?: number;
      title?: string;
      category?: 'GENERAL' | 'TUMOR';
      tumorSiteCode?: string;
      page?: { showPageNumber?: boolean };
      sections?: Array<{
        code?: string;
        label?: string;
        source?: ReportSectionSource;
        fields?: string[];
      }>;
    };
    if (parsed.schemaVersion !== 1 || !Array.isArray(parsed.sections)) throw new Error('legacy');
    reportDesigner.value = {
      title: parsed.title ?? '病理诊断报告',
      category: parsed.category ?? 'GENERAL',
      tumorSiteCode: parsed.tumorSiteCode ?? '',
      showPageNumber: parsed.page?.showPageNumber ?? true,
      sections: parsed.sections.map((section) => ({
        clientId: crypto.randomUUID(),
        code: section.code ?? '',
        label: section.label ?? '',
        source: section.source ?? 'DIAGNOSIS',
        fields: (section.fields ?? []).join(', '),
      })),
    };
  } catch {
    loadDesignerDefinition();
  }
}

function selectReportTemplate(templateId: string) {
  selectedReportTemplateId.value = templateId;
  const latest = reportTemplateCatalog.value
    .filter((item) => item.templateId === templateId)
    .sort((left, right) => (right.versionNo ?? 0) - (left.versionNo ?? 0))[0];
  selectedDraftVersionId.value = latest?.status === 'DRAFT' ? (latest.versionId ?? '') : '';
  loadDesignerDefinition(latest?.definition);
}

function addReportSection() {
  reportDesigner.value.sections.push({
    clientId: crypto.randomUUID(),
    code: `SECTION_${reportDesigner.value.sections.length + 1}`,
    label: '新版块',
    source: 'DIAGNOSIS',
    fields: 'diagnosisText',
  });
}

function moveReportSection(index: number, offset: number) {
  const target = index + offset;
  if (target < 0 || target >= reportDesigner.value.sections.length) return;
  const [section] = reportDesigner.value.sections.splice(index, 1);
  reportDesigner.value.sections.splice(target, 0, section);
}

async function reloadReportTemplateCatalog() {
  reportTemplateCatalog.value = await getV2ReportTemplateCatalog();
}

async function createBlankReportTemplate() {
  saving.value = true;
  error.value = '';
  notice.value = '';
  try {
    const created = await createV2ReportTemplate({
      code: reportTemplateDraft.value.code.trim(),
      name: reportTemplateDraft.value.name.trim(),
      businessTypeId: reportTemplateDraft.value.businessTypeId,
    });
    await reloadReportTemplateCatalog();
    selectReportTemplate(created.templateId);
    notice.value = '报告模板已创建，请设计并保存第一个版本。';
  } catch (requestError) {
    error.value = friendlyError(requestError, '报告模板创建失败，请检查编码是否重复。');
  } finally {
    saving.value = false;
  }
}

async function createFromTumorPreset() {
  saving.value = true;
  error.value = '';
  notice.value = '';
  try {
    const created = await instantiateV2ReportTemplatePreset(reportTemplateDraft.value.presetCode, {
      code: reportTemplateDraft.value.code.trim(),
      name: reportTemplateDraft.value.name.trim(),
      businessTypeId: reportTemplateDraft.value.businessTypeId,
    });
    await reloadReportTemplateCatalog();
    selectReportTemplate(created.template.templateId);
    selectedDraftVersionId.value = created.version.versionId;
    notice.value = '常用肿瘤结构已复制为当前医院草稿，请完成业务审核后发布。';
  } catch (requestError) {
    error.value = friendlyError(requestError, '常用肿瘤模板创建失败，请检查输入。');
  } finally {
    saving.value = false;
  }
}

function reportDesignerDefinition() {
  return JSON.stringify({
    schemaVersion: 1,
    title: reportDesigner.value.title.trim(),
    category: reportDesigner.value.category,
    ...(reportDesigner.value.category === 'TUMOR'
      ? { tumorSiteCode: reportDesigner.value.tumorSiteCode.trim() }
      : {}),
    page: { size: 'A4', showPageNumber: reportDesigner.value.showPageNumber },
    sections: reportDesigner.value.sections.map((section) => ({
      code: section.code.trim().toUpperCase(),
      label: section.label.trim(),
      source: section.source,
      fields: section.fields
        .split(',')
        .map((field) => field.trim())
        .filter(Boolean),
    })),
  });
}

async function saveReportDesignerVersion() {
  if (!selectedReportTemplateId.value) return;
  saving.value = true;
  error.value = '';
  notice.value = '';
  try {
    const created = await createV2ReportTemplateVersion(
      selectedReportTemplateId.value,
      reportDesignerDefinition(),
    );
    selectedDraftVersionId.value = created.versionId;
    await reloadReportTemplateCatalog();
    notice.value = `报告模板草稿 v${created.versionNo} 已保存，尚未影响诊断签发。`;
  } catch (requestError) {
    error.value = friendlyError(requestError, '模板定义校验失败，请检查版块代码和字段。');
  } finally {
    saving.value = false;
  }
}

async function publishReportDesignerVersion() {
  if (!selectedDraftVersionId.value) return;
  saving.value = true;
  error.value = '';
  notice.value = '';
  try {
    await publishV2ReportTemplateVersion(selectedDraftVersionId.value);
    await reloadReportTemplateCatalog();
    selectedDraftVersionId.value = '';
    notice.value = '报告模板版本已发布；后续预览和签发可选择该不可变版本。';
  } catch (requestError) {
    error.value = friendlyError(requestError, '报告模板发布失败，请刷新后重试。');
  } finally {
    saving.value = false;
  }
}

onMounted(() => void load());
</script>

<template>
  <section class="admin-hub-page configuration-page" aria-label="配置中心">
    <header class="page-heading compact-heading">
      <div>
        <p class="section-kicker">配置中心</p>
        <h2>医院业务配置</h2>
        <p>这里修改的是当前医院配置；登记、编号、技术项目和报告模板会从生效版本读取。</p>
      </div>
      <button class="secondary-button" type="button" :disabled="loading" @click="load">
        {{ loading ? '刷新中…' : '刷新配置' }}
      </button>
    </header>
    <p v-if="error" class="feedback error" role="alert">{{ error }}</p>
    <p v-if="notice" class="feedback success" role="status">{{ notice }}</p>
    <div class="configuration-layout">
      <nav class="configuration-nav" aria-label="配置分类">
        <section
          v-for="group in [...new Set(sectionOptions.map((item) => item.group))]"
          :key="group"
        >
          <h3>{{ group }}</h3>
          <button
            v-for="item in sectionOptions.filter((option) => option.group === group)"
            :key="item.key"
            type="button"
            :class="{ active: activeSection === item.key }"
            @click="activeSection = item.key"
          >
            {{ item.label }}
          </button>
        </section>
      </nav>
      <section class="workspace-panel configuration-panel">
        <header class="panel-title-row">
          <div>
            <p class="section-kicker">{{ activeSection }}</p>
            <h3>{{ activeLabel }}</h3>
          </div>
          <span v-if="snapshot" class="status-pill success">已连接真实配置</span>
        </header>
        <div v-if="loading" class="list-skeleton"><span></span><span></span><span></span></div>
        <div v-else-if="!snapshot" class="empty-state">
          <strong>配置尚未加载</strong><span>请刷新或联系系统管理员。</span>
        </div>
        <div v-else-if="activeSection === 'businessTypes'" class="config-table">
          <div class="config-row header">
            <span>代码</span><span>名称</span><span>模式</span><span>状态</span><span>版本</span
            ><span></span>
          </div>
          <div v-for="item in snapshot.businessTypes" :key="item.id" class="config-row">
            <code>{{ item.code }}</code
            ><input v-model="item.displayName" aria-label="业务类型名称" /><span>{{
              item.modalityCode
            }}</span
            ><label class="inline-checkbox"
              ><input v-model="item.enabled" type="checkbox" />启用</label
            ><span>v{{ item.configurationVersion }}</span
            ><button
              class="text-button"
              type="button"
              :disabled="saving"
              @click="
                save(`/business-types/${item.id}`, {
                  displayName: item.displayName,
                  enabled: item.enabled,
                })
              "
            >
              保存
            </button>
          </div>
        </div>
        <div v-else-if="activeSection === 'applicationItemMappings'" class="config-table">
          <div class="config-row header">
            <span>申请项目</span><span>业务类型</span><span>默认标本</span><span>必填</span
            ><span>状态</span><span></span>
          </div>
          <div v-for="item in snapshot.applicationItemMappings" :key="item.id" class="config-row">
            <code>{{ item.applicationItemCode }}</code
            ><span>{{ item.businessTypeName }}</span
            ><input v-model="item.defaultSpecimenKindCode" aria-label="默认标本类型" /><label
              class="inline-checkbox"
              ><input v-model="item.required" type="checkbox" />必填</label
            ><label class="inline-checkbox"
              ><input v-model="item.active" type="checkbox" />启用</label
            ><button
              class="text-button"
              type="button"
              :disabled="saving"
              @click="
                save(`/application-item-mappings/${item.id}`, {
                  defaultSpecimenKindCode: item.defaultSpecimenKindCode,
                  required: item.required,
                  sequenceNo: item.sequenceNo,
                  active: item.active,
                })
              "
            >
              保存
            </button>
          </div>
        </div>
        <div v-else-if="activeSection === 'pathologyNumberRules'" class="config-table">
          <div class="config-row header">
            <span>业务类型</span><span>编号种类</span><span>前缀</span><span>补零位数</span
            ><span>状态</span><span></span>
          </div>
          <div v-for="item in snapshot.pathologyNumberRules" :key="item.id" class="config-row">
            <span>{{ item.businessTypeName }}</span
            ><code>{{ item.numberKindCode }}</code
            ><input v-model="item.prefix" aria-label="病理号前缀" /><input
              v-model.number="item.paddingWidth"
              type="number"
              min="1"
              max="12"
              aria-label="补零位数"
            /><label class="inline-checkbox"
              ><input v-model="item.active" type="checkbox" />启用</label
            ><button
              class="text-button"
              type="button"
              :disabled="saving"
              @click="
                save(`/pathology-number-rules/${item.id}`, {
                  prefix: item.prefix,
                  paddingWidth: item.paddingWidth,
                  active: item.active,
                })
              "
            >
              保存
            </button>
          </div>
        </div>
        <div v-else-if="activeSection === 'technicalProjects'" class="config-table">
          <div class="config-row header">
            <span>代码</span><span>名称</span><span>业务类型</span><span>签发前等待</span
            ><span>状态</span><span></span>
          </div>
          <div v-for="item in snapshot.technicalProjects" :key="item.id" class="config-row">
            <code>{{ item.projectCode }}</code
            ><input v-model="item.projectName" aria-label="技术项目名称" /><span>{{
              item.businessTypeName || '通用'
            }}</span
            ><label class="inline-checkbox"
              ><input v-model="item.requiredBeforeSignOutDefault" type="checkbox" />等待</label
            ><label class="inline-checkbox"
              ><input v-model="item.enabled" type="checkbox" />启用</label
            ><button
              class="text-button"
              type="button"
              :disabled="saving"
              @click="
                save(`/technical-projects/${item.id}`, {
                  projectName: item.projectName,
                  enabled: item.enabled,
                  requiredBeforeSignOutDefault: item.requiredBeforeSignOutDefault,
                })
              "
            >
              保存
            </button>
          </div>
        </div>
        <div v-else-if="activeSection === 'archiveLocations'" class="archive-location-config">
          <form class="config-inline-form" @submit.prevent="saveArchiveLocation">
            <label
              >上级库位
              <select v-model="archiveDraft.parentId">
                <option value="">顶层</option>
                <option
                  v-for="item in archiveLocations"
                  :key="item.locationId"
                  :value="item.locationId"
                >
                  {{ item.locationCode }} · {{ item.locationName }}
                </option>
              </select>
            </label>
            <label>编码<input v-model="archiveDraft.locationCode" required /></label>
            <label>名称<input v-model="archiveDraft.locationName" required /></label>
            <label
              >类型
              <select v-model="archiveDraft.locationKindCode">
                <option value="ROOM">房间</option>
                <option value="CABINET">柜</option>
                <option value="SHELF">层架</option>
                <option value="SLOT">格位</option>
              </select>
            </label>
            <button
              class="primary-button"
              type="submit"
              :disabled="
                saving || !archiveDraft.locationCode.trim() || !archiveDraft.locationName.trim()
              "
            >
              新增库位
            </button>
          </form>
          <div class="config-table">
            <div class="config-row header">
              <span>编码</span><span>名称</span><span>类型</span><span>上级</span>
            </div>
            <div v-for="item in archiveLocations" :key="item.locationId" class="config-row">
              <code>{{ item.locationCode }}</code
              ><span>{{ item.locationName }}</span
              ><span>{{ item.locationKindCode }}</span
              ><span>{{
                archiveLocations.find((parent) => parent.locationId === item.parentId)
                  ?.locationName || '顶层'
              }}</span>
            </div>
          </div>
        </div>
        <div v-else-if="activeSection === 'assignmentRules'" class="archive-location-config">
          <p class="muted">校区、申请科室或取材部位填写 * 表示不限；每日上限为 0 表示不限量。</p>
          <form class="config-inline-form" @submit.prevent="saveAssignmentRule()">
            <label>校区<input v-model="assignmentDraft.campus" required /></label>
            <label>业务类型<input v-model="assignmentDraft.businessTypeCode" required /></label>
            <label>申请科室<input v-model="assignmentDraft.department" required /></label>
            <label>取材部位<input v-model="assignmentDraft.site" required /></label>
            <label>亚专科组<input v-model="assignmentDraft.diagnosisGroup" required /></label>
            <label>责任医生<input v-model="assignmentDraft.doctorId" required /></label>
            <label
              >优先级<input v-model.number="assignmentDraft.priority" type="number" min="0"
            /></label>
            <label
              >每日上限<input v-model.number="assignmentDraft.dailyCaseLimit" type="number" min="0"
            /></label>
            <button
              class="primary-button"
              type="submit"
              :disabled="
                saving || !assignmentDraft.diagnosisGroup.trim() || !assignmentDraft.doctorId.trim()
              "
            >
              新增规则
            </button>
          </form>
          <div class="config-table">
            <div class="config-row header">
              <span>业务/范围</span><span>亚专科</span><span>医生</span><span>优先级/上限</span
              ><span>状态</span><span></span>
            </div>
            <div v-for="rule in assignmentRules" :key="rule.assignmentRuleId" class="config-row">
              <span
                ><code>{{ rule.businessTypeCode }}</code
                ><br />{{ rule.campus }} · {{ rule.department }} · {{ rule.site }}</span
              ><input v-model="rule.diagnosisGroup" aria-label="亚专科组" />
              <input v-model="rule.doctorId" aria-label="分诊医生" />
              <span
                ><input
                  v-model.number="rule.priority"
                  type="number"
                  min="0"
                  aria-label="规则优先级" />
                /
                <input
                  v-model.number="rule.dailyCaseLimit"
                  type="number"
                  min="0"
                  aria-label="每日上限" /></span
              ><label class="inline-checkbox"
                ><input v-model="rule.enabled" type="checkbox" />启用</label
              >
              <button
                class="text-button"
                type="button"
                :disabled="saving"
                @click="saveAssignmentRule(rule)"
              >
                保存
              </button>
            </div>
          </div>
        </div>
        <div v-else-if="activeSection === 'diagnosisTemplates'" class="config-table">
          <div class="config-row header">
            <span>代码</span><span>名称</span><span>业务类型</span><span>版本数</span
            ><span>状态</span><span></span>
          </div>
          <div v-for="item in snapshot.diagnosisTemplates" :key="item.id" class="config-row">
            <code>{{ item.templateCode }}</code
            ><input v-model="item.templateName" aria-label="诊断模板名称" /><span>{{
              item.businessTypeName || '通用'
            }}</span
            ><span>{{ item.versionCount }}</span
            ><label class="inline-checkbox"
              ><input v-model="item.enabled" type="checkbox" />启用</label
            ><button
              class="text-button"
              type="button"
              :disabled="saving"
              @click="
                save(`/diagnosis-templates/${item.id}`, {
                  templateName: item.templateName,
                  enabled: item.enabled,
                })
              "
            >
              保存
            </button>
          </div>
        </div>
        <div v-else-if="activeSection === 'reportTemplates'" class="report-template-designer">
          <section class="report-template-create-panel" aria-label="新建报告模板">
            <header>
              <div>
                <strong>新建医院报告模板</strong>
                <p class="muted">从空白结构开始，或复制内置常用肿瘤结构为草稿。</p>
              </div>
            </header>
            <div class="field-grid three-columns">
              <label
                >模板编码<input
                  v-model="reportTemplateDraft.code"
                  required
                  placeholder="例如 TUMOR-LUNG-LOCAL"
              /></label>
              <label
                >模板名称<input
                  v-model="reportTemplateDraft.name"
                  required
                  placeholder="例如 肺肿瘤专科报告"
              /></label>
              <label
                >业务类型<select v-model="reportTemplateDraft.businessTypeId" required>
                  <option v-for="item in snapshot.businessTypes" :key="item.id" :value="item.id">
                    {{ item.displayName }}
                  </option>
                </select></label
              >
            </div>
            <div class="report-template-create-actions">
              <button
                class="secondary-button"
                type="button"
                :disabled="
                  saving ||
                  !reportTemplateDraft.code.trim() ||
                  !reportTemplateDraft.name.trim() ||
                  !reportTemplateDraft.businessTypeId
                "
                @click="createBlankReportTemplate"
              >
                创建空白模板
              </button>
              <select v-model="reportTemplateDraft.presetCode" aria-label="常用肿瘤模板">
                <option value="">选择常用肿瘤结构</option>
                <option
                  v-for="preset in reportTemplatePresets"
                  :key="preset.presetCode"
                  :value="preset.presetCode"
                >
                  {{ preset.presetName }} · v{{ preset.presetVersion }}
                </option>
              </select>
              <button
                class="primary-button"
                type="button"
                :disabled="
                  saving ||
                  !reportTemplateDraft.presetCode ||
                  !reportTemplateDraft.code.trim() ||
                  !reportTemplateDraft.name.trim() ||
                  !reportTemplateDraft.businessTypeId
                "
                @click="createFromTumorPreset"
              >
                从肿瘤结构创建草稿
              </button>
            </div>
          </section>

          <div class="report-template-workspace">
            <aside class="report-template-list" aria-label="报告模板目录">
              <button
                v-for="item in reportTemplateChoices"
                :key="item.templateId"
                type="button"
                :class="{ active: selectedReportTemplateId === item.templateId }"
                @click="selectReportTemplate(item.templateId)"
              >
                <span
                  ><strong>{{ item.name }}</strong
                  ><small>{{ item.code }} · {{ item.businessTypeName }}</small></span
                >
                <span
                  class="status-pill"
                  :class="item.status === 'PUBLISHED' ? 'success' : 'warning'"
                >
                  {{ item.versionNo ? `v${item.versionNo} ${item.status}` : '待设计' }}
                </span>
              </button>
              <p v-if="!reportTemplateChoices.length" class="empty-state compact">
                尚无报告模板，请先创建。
              </p>
            </aside>

            <section
              v-if="selectedReportTemplateId"
              class="report-template-canvas"
              aria-label="报告模板设计器"
            >
              <header class="panel-title-row">
                <div>
                  <p class="section-kicker">Template Designer</p>
                  <h3>结构化报告版式</h3>
                </div>
                <label class="inline-checkbox"
                  ><input v-model="reportDesigner.showPageNumber" type="checkbox" />显示页码</label
                >
              </header>
              <div class="field-grid three-columns">
                <label
                  >报告标题<input v-model="reportDesigner.title" aria-label="报告标题"
                /></label>
                <label
                  >模板类别<select v-model="reportDesigner.category">
                    <option value="GENERAL">通用报告</option>
                    <option value="TUMOR">肿瘤报告</option>
                  </select></label
                >
                <label v-if="reportDesigner.category === 'TUMOR'"
                  >肿瘤部位代码<input
                    v-model="reportDesigner.tumorSiteCode"
                    placeholder="例如 LUNG"
                /></label>
                <label v-else>页面规格<input value="A4" disabled /></label>
              </div>
              <div class="report-section-list">
                <article
                  v-for="(section, index) in reportDesigner.sections"
                  :key="section.clientId"
                  class="report-section-card"
                >
                  <header>
                    <strong>{{ index + 1 }}. {{ section.label || '未命名版块' }}</strong>
                    <div class="inline-actions">
                      <button
                        class="text-button"
                        type="button"
                        :disabled="index === 0"
                        aria-label="上移版块"
                        @click="moveReportSection(index, -1)"
                      >
                        ↑
                      </button>
                      <button
                        class="text-button"
                        type="button"
                        :disabled="index === reportDesigner.sections.length - 1"
                        aria-label="下移版块"
                        @click="moveReportSection(index, 1)"
                      >
                        ↓
                      </button>
                      <button
                        class="text-button danger-text"
                        type="button"
                        :disabled="reportDesigner.sections.length === 1"
                        @click="reportDesigner.sections.splice(index, 1)"
                      >
                        删除
                      </button>
                    </div>
                  </header>
                  <div class="field-grid three-columns">
                    <label>版块代码<input v-model="section.code" /></label>
                    <label>显示名称<input v-model="section.label" /></label>
                    <label
                      >数据来源<select v-model="section.source">
                        <option value="CASE">病例</option>
                        <option value="MATERIAL">材料</option>
                        <option value="DIAGNOSIS">诊断</option>
                        <option value="TECHNICAL">技术结果</option>
                        <option value="SIGNATURE">签发</option>
                        <option value="SUPPLEMENTAL">补充报告</option>
                      </select></label
                    >
                  </div>
                  <label
                    >字段编码（逗号分隔）
                    <input v-model="section.fields" placeholder="diagnosisText, structuredData" />
                  </label>
                </article>
              </div>
              <div class="sticky-form-actions">
                <button class="secondary-button" type="button" @click="addReportSection">
                  + 添加版块
                </button>
                <span class="muted">保存只创建新草稿版本；发布后版本不可修改。</span>
                <button
                  class="secondary-button"
                  type="button"
                  :disabled="saving || !reportDesigner.sections.length"
                  @click="saveReportDesignerVersion"
                >
                  保存新草稿
                </button>
                <button
                  class="primary-button"
                  type="button"
                  :disabled="saving || !selectedDraftVersionId"
                  @click="publishReportDesignerVersion"
                >
                  发布当前草稿
                </button>
              </div>
            </section>
            <div v-else class="empty-state">
              <strong>选择一个报告模板开始设计</strong>
              <span>已发布版本保持不可变；任何调整都会生成新的草稿版本。</span>
            </div>
          </div>
        </div>
        <div v-else class="tat-policy-config" aria-label="报告时效策略配置">
          <header class="configuration-guidance workspace-panel">
            <div>
              <p class="section-kicker">Report TAT Policy</p>
              <h3>按业务类型配置报告时效</h3>
              <p class="muted">
                起点固定为病例登记时间。仓库不预设医院临床阈值；请完成业务确认后再启用。
              </p>
            </div>
          </header>
          <div class="config-table tat-policy-table">
            <div class="config-row header">
              <span>业务类型</span><span>起点</span><span>提醒（分钟）</span
              ><span>目标（分钟）</span><span>启用</span><span></span>
            </div>
            <div
              v-for="item in snapshot.reportTatPolicies"
              :key="item.businessTypeId"
              class="config-row"
            >
              <span
                ><strong>{{ item.businessTypeName }}</strong
                ><small>{{ item.businessTypeCode }}</small></span
              >
              <span>病例登记</span>
              <label
                ><span class="visually-hidden">{{ item.businessTypeName }}提醒分钟数</span
                ><input v-model.number="item.warningMinutes" type="number" min="1" max="525599"
              /></label>
              <label
                ><span class="visually-hidden">{{ item.businessTypeName }}目标分钟数</span
                ><input v-model.number="item.targetMinutes" type="number" min="2" max="525600"
              /></label>
              <label class="inline-checkbox"
                ><input v-model="item.enabled" type="checkbox" />{{
                  item.enabled ? '已启用' : '未启用'
                }}</label
              >
              <button
                class="text-button"
                type="button"
                :disabled="
                  saving ||
                  !item.warningMinutes ||
                  !item.targetMinutes ||
                  item.targetMinutes <= item.warningMinutes
                "
                @click="saveTatPolicy(item)"
              >
                保存策略
              </button>
            </div>
          </div>
        </div>
      </section>
    </div>
  </section>
</template>
