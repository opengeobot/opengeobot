<script setup lang="ts">
// Function: Dynamic form builder with built-in validation
// Time: 2026-07-04
// Author: AxeXie
import { reactive, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { FormField } from '@/types/api'

const props = withDefaults(defineProps<{
  fields: FormField[]
  modelValue?: Record<string, unknown>
}>(), {
  modelValue: () => ({})
})

const emit = defineEmits<{
  'submit': [value: Record<string, unknown>]
  'cancel': []
}>()

const { t } = useI18n()

const formData = reactive<Record<string, unknown>>({})
const errors = reactive<Record<string, string>>({})

function initForm(): void {
  for (const field of props.fields) {
    formData[field.key] = props.modelValue[field.key] ?? ''
    errors[field.key] = ''
  }
}

watch(() => props.fields, initForm, { immediate: true })

function validateField(field: FormField): boolean {
  const value = formData[field.key]

  if (field.required && (value === undefined || value === null || value === '')) {
    errors[field.key] = t('validation.required', { field: field.label })
    return false
  }

  if (field.rules) {
    for (const rule of field.rules) {
      const result = rule(value)
      if (result !== true) {
        errors[field.key] = result
        return false
      }
    }
  }

  errors[field.key] = ''
  return true
}

function validate(): boolean {
  let valid = true
  for (const field of props.fields) {
    if (!validateField(field)) {
      valid = false
    }
  }
  return valid
}

function handleSubmit(): void {
  if (validate()) {
    emit('submit', { ...formData })
  }
}

function handleCancel(): void {
  emit('cancel')
}

function onInput(key: string, event: Event): void {
  const target = event.target as HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement
  formData[key] = target.value
}
</script>

<template>
  <form class="form-builder" @submit.prevent="handleSubmit">
    <div v-for="field in fields" :key="field.key" class="form-field">
      <label class="form-label">
        {{ field.label }}
        <span v-if="field.required" class="required-mark">*</span>
      </label>

      <input
        v-if="field.type === 'text' || field.type === 'email' || field.type === 'password'"
        :type="field.type"
        class="form-input"
        :class="{ 'form-input-error': errors[field.key] }"
        :value="formData[field.key] as string"
        :placeholder="field.placeholder"
        @input="onInput(field.key, $event)"
      />

      <input
        v-else-if="field.type === 'number'"
        type="number"
        class="form-input"
        :class="{ 'form-input-error': errors[field.key] }"
        :value="formData[field.key] as number"
        :placeholder="field.placeholder"
        @input="onInput(field.key, $event)"
      />

      <select
        v-else-if="field.type === 'select'"
        class="form-input"
        :class="{ 'form-input-error': errors[field.key] }"
        :value="formData[field.key] as string | number"
        @change="onInput(field.key, $event)"
      >
        <option value="" disabled>{{ field.placeholder || t('common.select') }}</option>
        <option v-for="opt in field.options" :key="opt.value" :value="opt.value">
          {{ opt.label }}
        </option>
      </select>

      <textarea
        v-else-if="field.type === 'textarea'"
        class="form-input form-textarea"
        :class="{ 'form-input-error': errors[field.key] }"
        :value="formData[field.key] as string"
        :placeholder="field.placeholder"
        @input="onInput(field.key, $event)"
      />

      <span v-if="errors[field.key]" class="field-error">{{ errors[field.key] }}</span>
    </div>

    <div class="form-actions">
      <button type="submit" class="btn btn-primary">{{ t('common.confirm') }}</button>
      <button type="button" class="btn btn-secondary" @click="handleCancel">
        {{ t('common.cancel') }}
      </button>
    </div>
  </form>
</template>

<style scoped>
.form-builder {
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

.required-mark {
  color: #dc2626;
  margin-left: 0.125rem;
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

.form-textarea {
  min-height: 5rem;
  resize: vertical;
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

.btn-primary:hover {
  background-color: #1d4ed8;
}

.btn-secondary {
  background-color: #f1f5f9;
  color: #475569;
}

.btn-secondary:hover {
  background-color: #e2e8f0;
}
</style>
