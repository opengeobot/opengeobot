<script setup lang="ts">
// Function: Status tag with color mapping and i18n labels
// Time: 2026-07-04
// Author: AxeXie
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { StatusTagType } from '@/types/api'

const props = withDefaults(defineProps<{
  status: string
  type?: StatusTagType
}>(), {
  type: 'health'
})

const { t, te } = useI18n()

const colorMap: Record<StatusTagType, Record<string, string>> = {
  health: {
    healthy: 'green',
    degraded: 'amber',
    down: 'red'
  },
  task: {
    pending: 'gray',
    running: 'blue',
    paused: 'amber',
    succeeded: 'green',
    failed: 'red',
    cancelled: 'gray'
  },
  'enable-disable': {
    enabled: 'green',
    disabled: 'gray'
  },
  robot: {
    online: 'green',
    offline: 'gray',
    busy: 'blue',
    error: 'red',
    maintenance: 'amber'
  },
  publish: {
    draft: 'gray',
    published: 'green',
    disabled: 'gray',
    archived: 'gray'
  }
}

const colorClass = computed<string>(() => {
  return colorMap[props.type]?.[props.status] ?? 'gray'
})

const label = computed<string>(() => {
  const key = `status.${props.type}.${props.status}`
  if (te(key)) {
    return t(key)
  }
  return props.status
})
</script>

<template>
  <span class="status-tag" :class="`tag-${colorClass}`">
    {{ label }}
  </span>
</template>

<style scoped>
.status-tag {
  display: inline-flex;
  align-items: center;
  padding: 0.125rem 0.5rem;
  border-radius: 0.25rem;
  font-size: 0.75rem;
  font-weight: 600;
  white-space: nowrap;
}

.tag-green {
  background-color: #dcfce7;
  color: #16a34a;
}

.tag-amber {
  background-color: #fef3c7;
  color: #d97706;
}

.tag-red {
  background-color: #fee2e2;
  color: #dc2626;
}

.tag-blue {
  background-color: #dbeafe;
  color: #2563eb;
}

.tag-gray {
  background-color: #f1f5f9;
  color: #64748b;
}
</style>
