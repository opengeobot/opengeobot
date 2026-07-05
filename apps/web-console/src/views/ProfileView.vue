<script setup lang="ts">
// Function: User profile view with display and edit form
// Time: 2026-07-04
// Author: AxeXie
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { getProfile, updateProfileApi } from '@/api/auth'
import type { UserProfile, UpdateProfileRequest, ProblemDetails } from '@/types/api'

const { t, te } = useI18n()

const profile = ref<UserProfile | null>(null)
const loading = ref(false)
const saving = ref(false)
const errorMsg = ref('')
const successMsg = ref('')

const editForm = reactive({
  display_name: '',
  email: '',
  phone: ''
})
const errors = reactive({
  display_name: '',
  email: '',
  phone: ''
})

function resolveErrorMessage(problem: ProblemDetails): string {
  if (problem.message_key && te(problem.message_key)) {
    return t(problem.message_key, problem.arguments)
  }
  return problem.title || t('common.error')
}

function validateEmail(email: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)
}

function validate(): boolean {
  let valid = true
  errors.display_name = ''
  errors.email = ''
  errors.phone = ''

  if (!editForm.display_name) {
    errors.display_name = t('validation.required', { field: t('profile.display_name') })
    valid = false
  }

  if (!editForm.email) {
    errors.email = t('validation.required', { field: t('profile.email') })
    valid = false
  } else if (!validateEmail(editForm.email)) {
    errors.email = t('validation.invalid_email')
    valid = false
  }

  if (!editForm.phone) {
    errors.phone = t('validation.required', { field: t('profile.phone') })
    valid = false
  }

  return valid
}

async function loadProfile(): Promise<void> {
  loading.value = true
  errorMsg.value = ''
  try {
    profile.value = await getProfile()
    editForm.display_name = profile.value.display_name
    editForm.email = profile.value.email
    editForm.phone = profile.value.phone
  } catch (err) {
    errorMsg.value = resolveErrorMessage(err as ProblemDetails)
  } finally {
    loading.value = false
  }
}

async function handleSave(): Promise<void> {
  successMsg.value = ''
  errorMsg.value = ''
  if (!validate()) return

  saving.value = true
  try {
    const payload: UpdateProfileRequest = {
      display_name: editForm.display_name,
      email: editForm.email,
      phone: editForm.phone
    }
    profile.value = await updateProfileApi(payload)
    successMsg.value = t('auth.profile_updated')
  } catch (err) {
    errorMsg.value = resolveErrorMessage(err as ProblemDetails)
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadProfile()
})
</script>

<template>
  <div class="profile-view">
    <h1 class="page-title">{{ t('nav.profile') }}</h1>

    <p v-if="loading" class="loading-text">{{ t('common.loading') }}</p>

    <div v-else class="profile-content">
      <p v-if="errorMsg" class="alert alert-error">{{ errorMsg }}</p>
      <p v-if="successMsg" class="alert alert-success">{{ successMsg }}</p>

      <!-- Display section -->
      <section v-if="profile" class="profile-section">
        <h2 class="section-title">{{ t('profile.display_title') }}</h2>
        <div class="profile-grid">
          <div class="profile-field">
            <span class="field-label">{{ t('common.username') }}</span>
            <span class="field-value">{{ profile.username }}</span>
          </div>
          <div class="profile-field">
            <span class="field-label">{{ t('profile.display_name') }}</span>
            <span class="field-value">{{ profile.display_name }}</span>
          </div>
          <div class="profile-field">
            <span class="field-label">{{ t('profile.email') }}</span>
            <span class="field-value">{{ profile.email }}</span>
          </div>
          <div class="profile-field">
            <span class="field-label">{{ t('profile.phone') }}</span>
            <span class="field-value">{{ profile.phone }}</span>
          </div>
          <div class="profile-field">
            <span class="field-label">{{ t('profile.avatar') }}</span>
            <span v-if="profile.avatar" class="field-value avatar-value">
              <img :src="profile.avatar" :alt="profile.display_name" class="avatar-img" />
            </span>
            <span v-else class="field-value field-value-empty">{{ t('profile.no_avatar') }}</span>
          </div>
        </div>
      </section>

      <!-- Edit form -->
      <section v-if="profile" class="profile-section">
        <h2 class="section-title">{{ t('profile.edit_title') }}</h2>
        <form class="edit-form" @submit.prevent="handleSave">
          <div class="form-field">
            <label class="form-label">{{ t('profile.display_name') }}</label>
            <input
              v-model="editForm.display_name"
              type="text"
              class="form-input"
              :class="{ 'form-input-error': errors.display_name }"
            />
            <span v-if="errors.display_name" class="field-error">{{ errors.display_name }}</span>
          </div>

          <div class="form-field">
            <label class="form-label">{{ t('profile.email') }}</label>
            <input
              v-model="editForm.email"
              type="email"
              class="form-input"
              :class="{ 'form-input-error': errors.email }"
            />
            <span v-if="errors.email" class="field-error">{{ errors.email }}</span>
          </div>

          <div class="form-field">
            <label class="form-label">{{ t('profile.phone') }}</label>
            <input
              v-model="editForm.phone"
              type="text"
              class="form-input"
              :class="{ 'form-input-error': errors.phone }"
            />
            <span v-if="errors.phone" class="field-error">{{ errors.phone }}</span>
          </div>

          <div class="form-actions">
            <button type="submit" class="btn btn-primary" :disabled="saving">
              {{ saving ? t('common.loading') : t('common.save') }}
            </button>
          </div>
        </form>
      </section>
    </div>
  </div>
</template>

<style scoped>
.profile-view {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.page-title {
  font-size: 1.5rem;
  font-weight: 700;
  color: #1e293b;
  margin: 0;
}

.loading-text {
  color: #64748b;
  font-size: 0.875rem;
}

.profile-content {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.alert {
  padding: 0.625rem 0.875rem;
  border-radius: 0.375rem;
  font-size: 0.8125rem;
}

.alert-error {
  background-color: #fef2f2;
  border: 1px solid #fecaca;
  color: #dc2626;
}

.alert-success {
  background-color: #f0fdf4;
  border: 1px solid #bbf7d0;
  color: #16a34a;
}

.profile-section {
  background-color: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 0.5rem;
  padding: 1.25rem;
}

.section-title {
  font-size: 1rem;
  font-weight: 600;
  color: #334155;
  margin: 0 0 1rem;
}

.profile-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(16rem, 1fr));
  gap: 1rem;
}

.profile-field {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.field-label {
  font-size: 0.8125rem;
  color: #64748b;
  font-weight: 500;
}

.field-value {
  font-size: 0.875rem;
  color: #1e293b;
}

.field-value-empty {
  color: #94a3b8;
  font-style: italic;
}

.avatar-img {
  width: 3.5rem;
  height: 3.5rem;
  border-radius: 50%;
  object-fit: cover;
  border: 1px solid #e2e8f0;
}

.edit-form {
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

.field-error {
  font-size: 0.8125rem;
  color: #dc2626;
}

.form-actions {
  display: flex;
  gap: 0.75rem;
}

.btn {
  padding: 0.5rem 1.25rem;
  border: none;
  border-radius: 0.375rem;
  font-size: 0.875rem;
  font-weight: 600;
  cursor: pointer;
  transition: background-color 0.15s;
}

.btn-primary {
  background-color: #2563eb;
  color: #ffffff;
}

.btn-primary:hover:not(:disabled) {
  background-color: #1d4ed8;
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
