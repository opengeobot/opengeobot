<script setup lang="ts">
// Function: Reusable modal dialog with overlay, slots and events
// Time: 2026-07-04
// Author: AxeXie
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const props = withDefaults(defineProps<{
  visible: boolean
  title?: string
  width?: string | number
  closable?: boolean
}>(), {
  title: '',
  width: 480,
  closable: true
})

const emit = defineEmits<{
  'close': []
  'confirm': []
}>()

const { t } = useI18n()

const dialogStyle = computed<Record<string, string>>(() => {
  const w = typeof props.width === 'number' ? `${props.width}px` : props.width
  return { width: w }
})

function handleOverlayClick(): void {
  if (props.closable) {
    emit('close')
  }
}

function handleClose(): void {
  emit('close')
}

function handleConfirm(): void {
  emit('confirm')
}
</script>

<template>
  <Teleport to="body">
    <div v-if="visible" class="modal-overlay" @click.self="handleOverlayClick">
      <div class="modal-dialog" :style="dialogStyle">
        <div class="modal-header">
          <h3 class="modal-title">{{ title }}</h3>
          <button v-if="closable" class="modal-close-btn" @click="handleClose">×</button>
        </div>

        <div class="modal-body">
          <slot />
        </div>

        <div class="modal-footer">
          <slot name="footer">
            <button class="btn btn-primary" @click="handleConfirm">
              {{ t('common.confirm') }}
            </button>
            <button class="btn btn-secondary" @click="handleClose">
              {{ t('common.cancel') }}
            </button>
          </slot>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: rgba(0, 0, 0, 0.5);
  z-index: 1000;
}

.modal-dialog {
  background-color: #ffffff;
  border-radius: 0.5rem;
  box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1);
  max-width: 90vw;
  max-height: 85vh;
  display: flex;
  flex-direction: column;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1rem 1.25rem;
  border-bottom: 1px solid #e2e8f0;
}

.modal-title {
  font-size: 1rem;
  font-weight: 600;
  color: #1e293b;
  margin: 0;
}

.modal-close-btn {
  border: none;
  background: transparent;
  font-size: 1.5rem;
  line-height: 1;
  color: #64748b;
  cursor: pointer;
  padding: 0;
  width: 1.5rem;
  height: 1.5rem;
  display: flex;
  align-items: center;
  justify-content: center;
}

.modal-close-btn:hover {
  color: #1e293b;
}

.modal-body {
  padding: 1.25rem;
  overflow-y: auto;
  flex: 1;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  padding: 1rem 1.25rem;
  border-top: 1px solid #e2e8f0;
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
