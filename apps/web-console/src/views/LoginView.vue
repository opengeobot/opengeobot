<script setup lang="ts">
// Function: Login view with form and language selector
// Time: 2026-07-03
// Author: AxeXie
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { usePlatformStore } from '@/stores/platform'

const { t, locale } = useI18n()
const router = useRouter()
const authStore = useAuthStore()
const platformStore = usePlatformStore()

const form = reactive({
  username: '',
  password: ''
})
const loading = ref(false)
const errorMsg = ref('')

const languages = [
  { value: 'zh-CN', label: '中文' },
  { value: 'en-US', label: 'English' }
]

function changeLanguage(value: string) {
  locale.value = value
  platformStore.setLocale(value)
}

async function handleSubmit() {
  if (!form.username || !form.password) {
    errorMsg.value = t('common.error')
    return
  }
  loading.value = true
  errorMsg.value = ''
  try {
    // Skeleton placeholder: real auth call will go through api/client.ts
    const token = `skeleton-token-${Date.now()}`
    authStore.setAuth(token, { id: '0', username: form.username })
    router.push('/dashboard')
  } catch {
    errorMsg.value = t('common.error')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-header">
        <h1 class="login-title">{{ t('login.title') }}</h1>
        <p class="login-subtitle">{{ t('login.subtitle') }}</p>
      </div>

      <form class="login-form" @submit.prevent="handleSubmit">
        <div class="form-field">
          <label class="form-label">{{ t('common.username') }}</label>
          <input
            v-model="form.username"
            type="text"
            class="form-input"
            :placeholder="t('login.usernamePlaceholder')"
            autocomplete="username"
          />
        </div>

        <div class="form-field">
          <label class="form-label">{{ t('common.password') }}</label>
          <input
            v-model="form.password"
            type="password"
            class="form-input"
            :placeholder="t('login.passwordPlaceholder')"
            autocomplete="current-password"
          />
        </div>

        <p v-if="errorMsg" class="form-error">{{ errorMsg }}</p>

        <button type="submit" class="submit-btn" :disabled="loading">
          {{ loading ? t('common.loading') : t('login.submit') }}
        </button>
      </form>

      <div class="login-footer">
        <label class="lang-label">{{ t('common.language') }}</label>
        <select
          class="lang-select"
          :value="locale"
          @change="changeLanguage(($event.target as HTMLSelectElement).value)"
        >
          <option v-for="lang in languages" :key="lang.value" :value="lang.value">
            {{ lang.label }}
          </option>
        </select>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background-color: #f1f5f9;
}

.login-card {
  width: 24rem;
  padding: 2rem;
  background-color: #ffffff;
  border-radius: 0.5rem;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
}

.login-header {
  text-align: center;
  margin-bottom: 1.5rem;
}

.login-title {
  font-size: 1.5rem;
  font-weight: 700;
  color: #1e293b;
  margin: 0;
}

.login-subtitle {
  font-size: 0.875rem;
  color: #64748b;
  margin-top: 0.25rem;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
}

.form-label {
  font-size: 0.875rem;
  font-weight: 500;
  color: #334155;
}

.form-input {
  padding: 0.5rem 0.75rem;
  border: 1px solid #cbd5e1;
  border-radius: 0.375rem;
  font-size: 0.875rem;
  outline: none;
  transition: border-color 0.15s;
}

.form-input:focus {
  border-color: #2563eb;
}

.form-error {
  margin: 0;
  font-size: 0.8125rem;
  color: #dc2626;
}

.submit-btn {
  padding: 0.625rem;
  border: none;
  border-radius: 0.375rem;
  background-color: #2563eb;
  color: #ffffff;
  font-size: 0.875rem;
  font-weight: 600;
  cursor: pointer;
  transition: background-color 0.15s;
}

.submit-btn:hover:not(:disabled) {
  background-color: #1d4ed8;
}

.submit-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.login-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.5rem;
  margin-top: 1.5rem;
  padding-top: 1rem;
  border-top: 1px solid #e2e8f0;
}

.lang-label {
  font-size: 0.8125rem;
  color: #64748b;
}

.lang-select {
  padding: 0.25rem 0.5rem;
  border: 1px solid #cbd5e1;
  border-radius: 0.25rem;
  font-size: 0.8125rem;
  cursor: pointer;
}
</style>
