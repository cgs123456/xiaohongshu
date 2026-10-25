<template>
  <div class="empty-state">
    <div class="empty-state__icon" :class="{ 'empty-state__icon--danger': variant === 'error' }">
      <Icon :icon="resolvedIcon" width="26" />
    </div>
    <p class="empty-state__title">{{ title }}</p>
    <p v-if="description" class="empty-state__desc">{{ description }}</p>
    <button v-if="actionText" class="empty-state__action" @click="$emit('action')">
      {{ actionText }}
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Icon } from '@iconify/vue'

const props = withDefaults(
  defineProps<{
    title: string
    description?: string
    /** 展示形态：空数据 / 加载失败 */
    variant?: 'empty' | 'error'
    /** 覆盖默认图标（Iconify 名） */
    icon?: string
    actionText?: string
  }>(),
  { variant: 'empty' }
)

defineEmits<{ action: [] }>()

const resolvedIcon = computed(() => {
  if (props.icon) return props.icon
  return props.variant === 'error' ? 'lucide:wifi-off' : 'lucide:inbox'
})
</script>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 72px 24px;
  text-align: center;
}

.empty-state__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  border-radius: var(--radius-full);
  background: var(--bg-subtle);
  color: var(--text-tertiary);
  margin-bottom: 16px;
}

.empty-state__icon--danger {
  background: var(--danger-soft);
  color: var(--danger);
}

.empty-state__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-secondary);
}

.empty-state__desc {
  margin-top: 6px;
  font-size: 13px;
  color: var(--text-tertiary);
}

.empty-state__action {
  margin-top: 20px;
  padding: 10px 32px;
  background: var(--brand-grad);
  color: var(--on-brand);
  border: none;
  border-radius: var(--radius-full);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: var(--shadow-brand);
  transition: transform var(--dur-fast) var(--ease-out),
    box-shadow var(--dur-fast) var(--ease-out);
}

.empty-state__action:hover {
  transform: translateY(-1px);
  box-shadow: var(--shadow-brand-lg);
}

.empty-state__action:active {
  transform: translateY(0);
}
</style>
