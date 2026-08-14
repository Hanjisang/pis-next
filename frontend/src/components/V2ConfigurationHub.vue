<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';

import { friendlyError } from '../uiText';
import {
  getV2Configuration,
  updateV2Configuration,
  type V2ConfigurationSnapshot,
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
  | 'archiveLocations';

const sectionOptions: Array<{ key: ConfigSection; label: string; group: string }> = [
  { key: 'businessTypes', label: '业务类型', group: '业务配置' },
  { key: 'applicationItemMappings', label: '申请项目映射', group: '业务配置' },
  { key: 'pathologyNumberRules', label: '病理号规则', group: '业务配置' },
  { key: 'diagnosisTemplates', label: '诊断模板', group: '诊断配置' },
  { key: 'assignmentRules', label: '自动分诊规则', group: '诊断配置' },
  { key: 'reportTemplates', label: '报告模板', group: '诊断配置' },
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
    [snapshot.value, archiveLocations.value, assignmentRules.value] = await Promise.all([
      getV2Configuration(),
      getV2ArchiveLocations(),
      getV2AssignmentRules(),
    ]);
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
        <div v-else class="config-table">
          <div class="config-row header">
            <span>代码</span><span>名称</span><span>业务类型</span><span>版本数</span
            ><span>状态</span><span></span>
          </div>
          <div v-for="item in snapshot.reportTemplates" :key="item.id" class="config-row">
            <code>{{ item.templateCode }}</code
            ><input v-model="item.templateName" aria-label="报告模板名称" /><span>{{
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
                save(`/report-templates/${item.id}`, {
                  templateName: item.templateName,
                  enabled: item.enabled,
                })
              "
            >
              保存
            </button>
          </div>
        </div>
      </section>
    </div>
  </section>
</template>
