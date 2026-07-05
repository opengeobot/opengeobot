<script setup lang="ts">
// Function: Login view with form, validation and language selector
// Time: 2026-07-04
// Author: AxeXie
import { ref, reactive, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { usePlatformStore } from '@/stores/platform'
import type { ProblemDetails } from '@/types/api'

const { t, locale, te } = useI18n()
const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const platformStore = usePlatformStore()

const MIN_USERNAME = 3
const MIN_PASSWORD = 6

const form = reactive({
  username: '',
  password: ''
})
const errors = reactive({
  username: '',
  password: ''
})
const loading = ref(false)
const errorMsg = ref('')

const languages = [
  { value: 'zh-CN', label: '中文' },
  { value: 'en-US', label: 'English' }
]

const canSubmit = computed<boolean>(() => {
  return form.username.length >= MIN_USERNAME && form.password.length >= MIN_PASSWORD && !loading.value
})

function changeLanguage(value: string): void {
  locale.value = value
  platformStore.setLocale(value)
}

function validate(): boolean {
  let valid = true
  errors.username = ''
  errors.password = ''

  if (!form.username) {
    errors.username = t('validation.required', { field: t('common.username') })
    valid = false
  } else if (form.username.length < MIN_USERNAME) {
    errors.username = t('validation.min_length', { field: t('common.username'), min: MIN_USERNAME })
    valid = false
  }

  if (!form.password) {
    errors.password = t('validation.required', { field: t('common.password') })
    valid = false
  } else if (form.password.length < MIN_PASSWORD) {
    errors.password = t('validation.min_length', { field: t('common.password'), min: MIN_PASSWORD })
    valid = false
  }

  return valid
}

function resolveErrorMessage(problem: ProblemDetails): string {
  if (problem.message_key && te(problem.message_key)) {
    return t(problem.message_key, problem.arguments)
  }
  return problem.title || t('auth.login_failed')
}

async function handleSubmit(): Promise<void> {
  errorMsg.value = ''
  if (!validate()) return

  loading.value = true
  try {
    await authStore.login(form.username, form.password)
    const redirect = (route.query.redirect as string) || '/dashboard'
    router.push(redirect)
  } catch (err) {
    const problem = err as ProblemDetails
    errorMsg.value = resolveErrorMessage(problem)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="lang-corner">
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
            :class="{ 'form-input-error': errors.username }"
            :placeholder="t('login.usernamePlaceholder')"
            autocomplete="username"
          />
          <span v-if="errors.username" class="form-error">{{ errors.username }}</span>
        </div>

        <div class="form-field">
          <label class="form-label">{{ t('common.password') }}</label>
          <input
            v-model="form.password"
            type="password"
            class="form-input"
            :class="{ 'form-input-error': errors.password }"
            :placeholder="t('login.passwordPlaceholder')"
            autocomplete="current-password"
          />
          <span v-if="errors.password" class="form-error">{{ errors.password }}</span>
        </div>

        <p v-if="errorMsg" class="form-error form-error-block">{{ errorMsg }}</p>

        <button type="submit" class="submit-btn" :disabled="!canSubmit">
          {{ loading ? t('common.loading') : t('login.submit') }}
        </button>
      </form>
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
  position: relative;
}

.lang-corner {
  position: absolute;
  top: 1rem;
  right: 1rem;
}

.lang-select {
  padding: 0.25rem 0.5rem;
  border: 1px solid #cbd5e1;
  border-radius: 0.25rem;
  font-size: 0.8125rem;
  cursor: pointer;
  background-color: #ffffff;
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

.form-input-error {
  border-color: #dc2626;
}

.form-error {
  font-size: 0.8125rem;
  color: #dc2626;
}

.form-error-block {
  margin: 0;
  padding: 0.5rem 0.75rem;
  background-color: #fef2f2;
  border-radius: 0.375rem;
  border: 1px solid #fecaca;
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
</style>
