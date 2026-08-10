<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';

import { friendlyError } from '../uiText';
import {
  getV2Configuration,
  updateV2Configuration,
  type V2ConfigurationSnapshot,
} from '../v2ConfigurationApi';

type ConfigSection =
  | 'businessTypes'
  | 'applicationItemMappings'
  | 'pathologyNumberRules'
  | 'technicalProjects'
  | 'diagnosisTemplates'
  | 'reportTemplates';

const sectionOptions: Array<{ key: ConfigSection; label: string; group: string }> = [
  { key: 'businessTypes', label: '业务类型', group: '业务配置' },
  { key: 'applicationItemMappings', label: '申请项目映射', group: '业务配置' },
  { key: 'pathologyNumberRules', label: '病理号规则', group: '业务配置' },
  { key: 'diagnosisTemplates', label: '诊断模板', group: '诊断配置' },
  { key: 'reportTemplates', label: '报告模板', group: '诊断配置' },
  { key: 'technicalProjects', label: '技术项目', group: '诊断配置' },
];

const snapshot = ref<V2ConfigurationSnapshot | null>(null);
const activeSection = ref<ConfigSection>('businessTypes');
const loading = ref(false);
const saving = ref(false);
const error = ref('');
const notice = ref('');

const activeLabel = computed(
  () => sectionOptions.find((item) => item.key === activeSection.value)?.label ?? '配置',
);

async function load() {
  loading.value = true;
  error.value = '';
  try {
    snapshot.value = await getV2Configuration();
  } catch (requestError) {
    error.value = friendlyError(requestError, '配置暂时无法加载，请稍后重试。');
  } finally {
    loading.value = false;
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
