<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';

import { friendlyError } from '../uiText';
import {
  getV2Administration,
  updateV2AdminUser,
  type V2AdministrationSnapshot,
} from '../v2AdministrationApi';

const snapshot = ref<V2AdministrationSnapshot | null>(null);
const selectedId = ref('');
const loading = ref(false);
const saving = ref(false);
const error = ref('');
const notice = ref('');
const form = ref({
  displayName: '',
  roleCode: '',
  hospitalScope: '',
  departmentScope: '',
  taskScope: '',
  enabled: true,
  doctorCode: '',
  doctorTitle: '',
  doctorDepartment: '',
  doctorEnabled: true,
  businessPermissions: [] as string[],
  actionPermissions: [] as string[],
});

const selectedUser = computed(
  () => snapshot.value?.users.find((user) => user.id === selectedId.value) ?? null,
);
const businessPermissions = computed(
  () => snapshot.value?.permissions.filter((item) => item.dimension === 'BUSINESS') ?? [],
);
const actionPermissions = computed(
  () => snapshot.value?.permissions.filter((item) => item.dimension === 'ACTION') ?? [],
);

function copySelected() {
  const user = selectedUser.value;
  if (!user) return;
  form.value = {
    displayName: user.displayName,
    roleCode: user.roleCode,
    hospitalScope: user.hospitalScope,
    departmentScope: user.departmentScope,
    taskScope: user.taskScope,
    enabled: user.enabled,
    doctorCode: user.doctorCode ?? '',
    doctorTitle: user.doctorTitle ?? '',
    doctorDepartment: user.doctorDepartment ?? '',
    doctorEnabled: user.doctorEnabled,
    businessPermissions: [...user.businessPermissions],
    actionPermissions: [...user.actionPermissions],
  };
}

watch(selectedId, copySelected);

async function load() {
  loading.value = true;
  error.value = '';
  try {
    snapshot.value = await getV2Administration();
    if (!selectedId.value) selectedId.value = snapshot.value.users[0]?.id ?? '';
    copySelected();
  } catch (requestError) {
    error.value = friendlyError(requestError, '系统管理暂时无法加载，请稍后重试。');
  } finally {
    loading.value = false;
  }
}

function toggle(target: 'businessPermissions' | 'actionPermissions', code: string) {
  const values = form.value[target];
  form.value[target] = values.includes(code)
    ? values.filter((item) => item !== code)
    : [...values, code];
}

async function save() {
  if (!selectedId.value) return;
  saving.value = true;
  error.value = '';
  notice.value = '';
  try {
    snapshot.value = await updateV2AdminUser(selectedId.value, {
      ...form.value,
      dataPermissions: [],
    });
    copySelected();
    notice.value = '用户、医疗人员身份和三层权限已保存。';
  } catch (requestError) {
    error.value = friendlyError(requestError, '用户配置保存失败，请刷新后重试。');
  } finally {
    saving.value = false;
  }
}

onMounted(() => void load());
</script>

<template>
  <section class="admin-hub-page administration-page" aria-label="系统管理">
    <header class="page-heading compact-heading">
      <div>
        <p class="section-kicker">系统管理</p>
        <h2>用户与权限</h2>
        <p>业务权限决定能进入什么，数据范围决定能看到什么，操作权限决定当前页面能执行什么。</p>
      </div>
      <button class="secondary-button" type="button" :disabled="loading" @click="load">
        {{ loading ? '刷新中…' : '刷新用户' }}
      </button>
    </header>
    <p v-if="error" class="feedback error" role="alert">{{ error }}</p>
    <p v-if="notice" class="feedback success" role="status">{{ notice }}</p>
    <div class="administration-layout">
      <aside class="workspace-panel administration-users">
        <header class="panel-title-row">
          <div>
            <p class="section-kicker">账号</p>
            <h3>用户</h3>
          </div>
          <span>{{ snapshot?.users.length ?? 0 }}</span>
        </header>
        <div v-if="loading" class="list-skeleton"><span></span><span></span></div>
        <button
          v-for="user in snapshot?.users ?? []"
          :key="user.id"
          type="button"
          class="administration-user-row"
          :class="{ active: selectedId === user.id }"
          @click="selectedId = user.id"
        >
          <span
            ><strong>{{ user.displayName }}</strong
            ><small>{{ user.username }} · {{ user.roleCode }}</small></span
          ><span :class="['status-pill', user.enabled ? 'success' : 'warning']">{{
            user.enabled ? '启用' : '停用'
          }}</span>
        </button>
      </aside>
      <section v-if="selectedUser" class="workspace-panel administration-editor">
        <header class="panel-title-row">
          <div>
            <p class="section-kicker">账号设置</p>
            <h3>{{ selectedUser.username }}</h3>
          </div>
          <button class="primary-button" type="button" :disabled="saving" @click="save">
            {{ saving ? '保存中…' : '保存设置' }}
          </button>
        </header>
        <div class="field-grid three-columns">
          <label>显示名称<input v-model="form.displayName" /></label
          ><label
            >角色模板<select v-model="form.roleCode">
              <option v-for="role in snapshot?.roles ?? []" :key="role" :value="role">
                {{ role }}
              </option>
            </select></label
          ><label class="inline-checkbox aligned-checkbox"
            ><input v-model="form.enabled" type="checkbox" />账号启用</label
          >
        </div>
        <section class="permission-section">
          <header>
            <h4>BUSINESS · 业务权限</h4>
            <span>决定可进入的业务能力</span>
          </header>
          <div class="permission-grid">
            <label v-for="item in businessPermissions" :key="item.code" class="permission-option"
              ><input
                type="checkbox"
                :checked="form.businessPermissions.includes(item.code)"
                @change="toggle('businessPermissions', item.code)"
              /><span
                ><strong>{{ item.label }}</strong
                ><small>{{ item.code }}</small></span
              ></label
            >
          </div>
        </section>
        <section class="permission-section">
          <header>
            <h4>DATA · 数据范围</h4>
            <span>决定可见医院、科室和任务范围</span>
          </header>
          <div class="field-grid three-columns">
            <label>医院范围<input v-model="form.hospitalScope" /></label
            ><label>科室范围<input v-model="form.departmentScope" /></label
            ><label>任务范围<input v-model="form.taskScope" /></label>
          </div>
          <p class="muted">范围字段由后端查询统一执行；前端不再用角色名替代数据权限。</p>
        </section>
        <section class="permission-section">
          <header>
            <h4>ACTION · 操作权限</h4>
            <span>决定当前身份可以执行的动作</span>
          </header>
          <div class="permission-grid">
            <label v-for="item in actionPermissions" :key="item.code" class="permission-option"
              ><input
                type="checkbox"
                :checked="form.actionPermissions.includes(item.code)"
                @change="toggle('actionPermissions', item.code)"
              /><span
                ><strong>{{ item.label }}</strong
                ><small>{{ item.code }}</small></span
              ></label
            >
          </div>
        </section>
        <section class="permission-section">
          <header>
            <h4>Doctor Identity · 医疗人员身份</h4>
            <span>供诊断、取材、责任和签发统一使用</span>
          </header>
          <div class="field-grid four-columns">
            <label>医生编码<input v-model="form.doctorCode" /></label
            ><label>职称<input v-model="form.doctorTitle" /></label
            ><label>科室<input v-model="form.doctorDepartment" /></label
            ><label class="inline-checkbox aligned-checkbox"
              ><input v-model="form.doctorEnabled" type="checkbox" />身份启用</label
            >
          </div>
        </section>
      </section>
      <section v-else class="workspace-panel empty-state">
        <strong>请选择一个用户</strong><span>用户数据加载后可以在这里编辑。</span>
      </section>
    </div>
    <section v-if="snapshot" class="workspace-panel organization-scope-panel">
      <header class="panel-title-row">
        <div>
          <p class="section-kicker">组织范围</p>
          <h3>医院 / 院区 / 科室</h3>
        </div>
        <span>{{ snapshot.organizations.length }}</span>
      </header>
      <div class="config-table">
        <div class="config-row header">
          <span>医院</span><span>院区</span><span>科室代码</span><span>科室名称</span>
        </div>
        <div
          v-for="item in snapshot.organizations"
          :key="`${item.hospitalProfileCode}-${item.campusCode}-${item.departmentCode}`"
          class="config-row"
        >
          <span>{{ item.hospitalProfileCode }}</span
          ><span>{{ item.campusCode || '—' }}</span
          ><span>{{ item.departmentCode || '—' }}</span
          ><span>{{ item.departmentName || '—' }}</span>
        </div>
      </div>
    </section>
  </section>
</template>
