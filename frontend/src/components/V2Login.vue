<script setup lang="ts">
import { ref } from 'vue';

const emit = defineEmits<{ authenticated: [] }>();
const username = ref('doctor-a');
const password = ref('');
const busy = ref(false);
const error = ref('');

async function login() {
  busy.value = true;
  error.value = '';
  try {
    const response = await fetch('/api/v2/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: username.value.trim(), password: password.value }),
    });
    const body = (await response.json()) as { message?: string };
    if (!response.ok) throw new Error(body.message ?? '登录失败');
    emit('authenticated');
  } catch (requestError) {
    error.value = requestError instanceof Error ? requestError.message : '登录失败';
  } finally {
    busy.value = false;
  }
}
</script>

<template>
  <main class="login-shell">
    <section class="login-card" aria-label="PIS V2 登录">
      <p class="eyebrow">PATHOLOGY INFORMATION SYSTEM · V2</p>
      <h1>进入 PIS V2</h1>
      <p class="muted">请使用医院业务身份登录。业务责任会自动使用当前医疗人员身份。</p>
      <form @submit.prevent="login">
        <label
          >用户名<input v-model="username" aria-label="用户名" autocomplete="username" required
        /></label>
        <label
          >密码<input
            v-model="password"
            aria-label="密码"
            type="password"
            autocomplete="current-password"
            required
        /></label>
        <p v-if="error" class="error-banner" role="alert">{{ error }}</p>
        <button type="submit" :disabled="busy">{{ busy ? '登录中…' : '登录' }}</button>
      </form>
      <p class="login-hint">
        运行时测试账号：doctor-a / doctor-b / doctor-c、registrar、technician、admin
      </p>
    </section>
  </main>
</template>
