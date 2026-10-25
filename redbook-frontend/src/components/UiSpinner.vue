<template>
  <div class="ui-spinner" role="status" :aria-label="label">
    <Icon icon="lucide:loader-circle" :width="size" :height="size" class="ui-spinner__icon" />
    <span v-if="label && showLabel" class="ui-spinner__label">{{ label }}</span>
  </div>
</template>

<script setup lang="ts">
import { Icon } from '@iconify/vue'

withDefaults(
  defineProps<{
    size?: number
    label?: string
    showLabel?: boolean
  }>(),
  { size: 24, label: '加载中', showLabel: true }
)
</script>

<style scoped>
.ui-spinner {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 48px 20px;
  color: var(--text-tertiary);
}

.ui-spinner__icon {
  color: var(--brand);
  animation: ui-spin 0.9s linear infinite;
}

.ui-spinner__label {
  font-size: 13px;
}

@keyframes ui-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .ui-spinner__icon {
    animation-duration: 2.4s;
  }
}
</style>
