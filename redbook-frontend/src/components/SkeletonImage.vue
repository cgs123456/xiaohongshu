<template>
  <div class="skeleton-image" :style="{ aspectRatio: aspectRatio || undefined, minHeight: minHeight ? minHeight + 'px' : undefined }">
    <transition name="fade">
      <img
        v-if="loaded"
        :src="src"
        :alt="alt"
        class="skeleton-image__img"
        loading="lazy"
        @error="onError"
      />
    </transition>
    <div v-if="!loaded" class="skeleton-image__placeholder" aria-hidden="true">
      <Icon v-if="failed" icon="lucide:image-off" width="24" class="skeleton-image__failed" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { Icon } from '@iconify/vue'

const props = defineProps<{
  src: string
  alt?: string
  /** 宽高比（如 "3/4"），占位与成图同尺寸，避免加载后跳动 */
  aspectRatio?: string
  /** 最小高度（px），无宽高比信息时的兜底 */
  minHeight?: number
}>()

const loaded = ref(false)
const failed = ref(false)

// 图片对象探测加载完成（先挂 transition 再渲染，保证淡入可见）
const preload = (src: string) => {
  loaded.value = false
  failed.value = false
  if (!src) {
    failed.value = true
    loaded.value = true
    return
  }
  const img = new Image()
  img.onload = () => {
    loaded.value = true
  }
  img.onerror = () => {
    failed.value = true
    loaded.value = true
  }
  img.src = src
}

const onError = () => {
  failed.value = true
}

watch(() => props.src, preload, { immediate: true })
</script>

<style scoped>
.skeleton-image {
  position: relative;
  width: 100%;
  overflow: hidden;
  background: var(--bg-subtle);
}

.skeleton-image__placeholder {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(
    100deg,
    var(--bg-subtle) 40%,
    var(--bg-hover) 50%,
    var(--bg-subtle) 60%
  );
  background-size: 200% 100%;
  animation: skeleton-shimmer 1.4s ease infinite;
}

.skeleton-image__failed {
  color: var(--text-disabled);
}

.skeleton-image__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.fade-enter-active {
  transition: opacity var(--dur-slow) var(--ease-out);
}

.fade-enter-from {
  opacity: 0;
}

@keyframes skeleton-shimmer {
  to {
    background-position: -200% 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .skeleton-image__placeholder {
    animation: none;
  }
}
</style>
