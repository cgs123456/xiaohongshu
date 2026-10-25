<template>
  <aside class="sidebar">
    <div class="sidebar-nav">
      <div
        class="nav-item"
        :class="{ active: currentRoute === '/' }"
        @click="navigateTo('/')"
      >
        <Icon icon="lucide:compass" class="nav-icon" width="24" />
        <span class="nav-text">发现</span>
      </div>
      <div
        class="nav-item"
        :class="{ active: currentRoute === '/shopping' }"
        @click="navigateTo('/shopping')"
      >
        <Icon icon="lucide:shopping-bag" class="nav-icon" width="24" />
        <span class="nav-text">购物</span>
      </div>
      <div
        class="nav-item"
        :class="{ active: currentRoute === '/cart' }"
        @click="navigateTo('/cart')"
      >
        <Icon icon="lucide:shopping-cart" class="nav-icon" width="24" />
        <span class="nav-text">购物车</span>
      </div>
      <div
        class="nav-item"
        :class="{ active: currentRoute === '/orders' }"
        @click="navigateTo('/orders')"
      >
        <Icon icon="lucide:file-text" class="nav-icon" width="24" />
        <span class="nav-text">订单</span>
      </div>
      <div
        class="nav-item"
        :class="{ active: currentRoute === '/notifications' }"
        @click="navigateTo('/notifications')"
      >
        <Icon icon="lucide:bell" class="nav-icon" width="24" />
        <span class="nav-text">通知</span>
      </div>
      <div
        class="nav-item"
        :class="{ active: currentRoute === '/profile' }"
        @click="navigateTo('/profile')"
      >
        <Icon icon="lucide:clock" class="nav-icon" width="24" />
        <span class="nav-text">历史</span>
      </div>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Icon } from '@iconify/vue'
import { useRouter, useRoute } from 'vue-router'

const router = useRouter()
const route = useRoute()

const currentRoute = computed(() => route.path)

const navigateTo = (path: string) => {
  router.push(path)
}
</script>

<style scoped>
.sidebar {
  position: fixed;
  left: 0;
  top: 64px;
  width: 220px;
  height: calc(100vh - 64px);
  background: var(--bg-surface);
  border-right: 1px solid var(--border-subtle);
  display: flex;
  flex-direction: column;
  padding: 24px 0;
  z-index: 100;
}

.sidebar-nav {
  flex: 1;
  padding: 0 16px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  margin-bottom: 4px;
  cursor: pointer;
  font-size: 15px;
  color: var(--text-secondary);
  border-radius: var(--radius-md);
  transition: all var(--dur-fast) var(--ease-out);
  position: relative;
}

.nav-item:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.nav-item.active {
  background: var(--brand-soft);
  color: var(--brand);
  font-weight: 600;
}

.nav-item.active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 4px;
  height: 24px;
  background: var(--brand);
  border-radius: 0 4px 4px 0;
}

.nav-icon {
  flex-shrink: 0;
}

.nav-text {
  flex: 1;
}

/* 响应式 */
@media (max-width: 1024px) {
  .sidebar {
    width: 80px;
  }
  
  .nav-text {
    display: none;
  }
  
  .nav-item {
    justify-content: center;
    padding: 14px;
  }
  
  .nav-item.active::before {
    display: none;
  }
}

@media (max-width: 768px) {
  .sidebar {
    transform: translateX(-100%);
  }
}

@media (max-width: 600px) {
  .sidebar {
    top: 56px;
    height: calc(100vh - 56px);
  }
}
</style>
