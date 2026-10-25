<template>
  <header class="header">
    <div class="header-wrapper">
      <div class="header-content">
        <div class="logo" @click="goHome">
          <div class="logo-icon">
            <Icon icon="lucide:book-open" width="28" color="#ff2442" />
          </div>
          <span class="logo-text">小红书</span>
        </div>
        
        <div class="search-bar">
          <Icon icon="lucide:search" width="20" class="search-icon" />
          <input 
            v-model="searchKeyword" 
            type="text" 
            placeholder="搜索你感兴趣的内容" 
            @keyup.enter="handleSearch"
          />
        </div>
        
        <div class="header-actions">
          <button class="action-btn create-btn" @click="showCreateModal = true">
            <Icon icon="lucide:pencil" width="18" />
            <span>创作中心</span>
          </button>
          
          <!-- 未登录状态 -->
          <button v-if="!userStore.isLoggedIn" class="action-btn login-btn-header" @click="showLoginModal = true">
            登录/注册
          </button>

          <!-- 已登录状态 -->
          <div v-else class="user-menu">
            <img
              :src="userStore.state.userInfo?.image || 'https://i.pravatar.cc/150?img=1'"
              :alt="userStore.state.userInfo?.nickname || '用户'"
              class="user-avatar"
              @click="toggleUserMenu"
            />

            <!-- 用户下拉菜单 -->
            <Transition name="dropdown">
              <div v-if="showUserMenu" class="user-dropdown" @click.stop>
                <div class="user-info-section">
                  <img
                    :src="userStore.state.userInfo?.image || 'https://i.pravatar.cc/150?img=1'"
                    :alt="userStore.state.userInfo?.nickname || '用户'"
                    class="dropdown-avatar"
                  />
                  <div class="user-details">
                    <div class="user-nickname">{{ userStore.state.userInfo?.nickname || userStore.state.userInfo?.username || '用户' }}</div>
                    <div class="user-id">ID: {{ userStore.state.userInfo?.number || userStore.state.userInfo?.id }}</div>
                  </div>
                </div>
                <div class="menu-divider"></div>
                <button class="menu-item" @click="goToProfile">
                  <Icon icon="lucide:user" width="18" />
                  <span>个人主页</span>
                </button>
                <div class="menu-divider"></div>
                <button class="menu-item logout-item" @click="handleLogout">
                  <Icon icon="lucide:log-out" width="18" />
                  <span>退出登录</span>
                </button>
              </div>
            </Transition>
          </div>
        </div>
      </div>
    </div>

    <!-- 创作中心弹窗 -->
    <CreateNoteModal
      :visible="showCreateModal"
      @update:visible="showCreateModal = $event"
      @success="handleCreateSuccess"
    />

    <!-- 登录弹窗 -->
    <LoginModal
      :visible="showLoginModal"
      @update:visible="showLoginModal = $event"
      @login-success="handleLoginSuccess"
    />
  </header>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { Icon } from '@iconify/vue'
import { useRouter } from 'vue-router'
import CreateNoteModal from './CreateNoteModal.vue'
import LoginModal from './LoginModal.vue'
import { getUserInfo } from '../api/auth'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()
const showCreateModal = ref(false)
const showLoginModal = ref(false)
const showUserMenu = ref(false)
const searchKeyword = ref('')

// 补充加载用户信息（store 中尚无 userInfo 时）
const loadUserInfo = async () => {
  if (!userStore.isLoggedIn || userStore.state.userInfo) return

  try {
    const response = await getUserInfo()
    if (response.data && response.data.user) {
      userStore.setUserInfo(response.data.user)
    }
  } catch (error) {
    // 获取用户信息失败（401 会由拦截器统一处理）
  }
}

// 切换用户菜单
const toggleUserMenu = () => {
  showUserMenu.value = !showUserMenu.value
}

// 点击外部关闭菜单
const handleClickOutside = (event: MouseEvent) => {
  const target = event.target as HTMLElement
  if (!target.closest('.user-menu')) {
    showUserMenu.value = false
  }
}

// 登录成功回调（LoginModal 已写入 store 并连接 WebSocket）
const handleLoginSuccess = () => {
  loadUserInfo()
}

// 跳转到个人主页
const goToProfile = () => {
  showUserMenu.value = false
  router.push('/profile')
}

// 退出登录
const handleLogout = () => {
  userStore.logout()
  showUserMenu.value = false
  router.push('/')
}

const goHome = () => {
  router.push('/')
}

// 搜索功能：跳转搜索页（query 驱动，支持分享链接）
const handleSearch = () => {
  const keyword = searchKeyword.value.trim()
  if (keyword) {
    router.push({ path: '/search', query: { q: keyword } })
  }
}

// 发布成功：通知页面刷新列表（不再整页 reload）
const handleCreateSuccess = () => {
  window.dispatchEvent(new CustomEvent('note:published'))
}

// 401/登录过期时由拦截器派发该事件，打开登录弹窗
const handleAuthRequired = () => {
  showLoginModal.value = true
}

onMounted(() => {
  loadUserInfo()
  document.addEventListener('click', handleClickOutside)
  window.addEventListener('auth:required', handleAuthRequired)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
  window.removeEventListener('auth:required', handleAuthRequired)
})
</script>

<style scoped>
.header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: 64px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--border-subtle);
  z-index: 1000;
  }

.header-wrapper {
  width: 100%;
  height: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
}

.header-content {
  width: 100%;
  max-width: 1200px;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 32px;
  gap: 32px;
}

.logo {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  transition: transform 0.2s;
  flex-shrink: 0;
}

.logo:hover {
  transform: scale(1.05);
}

.logo-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  background: var(--brand-soft);
  border-radius: var(--radius-sm);
}

.logo-text {
  font-size: 20px;
  font-weight: 800;
  color: var(--brand);
  letter-spacing: 0.5px;
}

.search-bar {
  flex: 1;
  max-width: 500px;
  display: flex;
  align-items: center;
  background: var(--bg-subtle);
  border-radius: var(--radius-full);
  padding: 10px 20px;
  gap: 10px;
  transition: all var(--dur-base) var(--ease-out);
  border: 2px solid transparent;
}

.search-bar:focus-within {
  background: var(--bg-surface);
  border-color: var(--brand);
  box-shadow: 0 4px 12px rgba(255, 36, 66, 0.12);
}

.search-icon {
  color: var(--text-tertiary);
  flex-shrink: 0;
}

.search-bar input {
  flex: 1;
  border: none;
  background: transparent;
  outline: none;
  font-size: 14px;
  color: var(--text-primary);
}

.search-bar input::placeholder {
  color: var(--text-tertiary);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  border: none;
  border-radius: var(--radius-full);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--dur-base) var(--ease-out);
  white-space: nowrap;
}

.create-btn {
  background: var(--brand-grad);
  color: white;
  box-shadow: var(--shadow-brand);
}

.create-btn:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-brand-lg);
}

.login-btn-header {
  background: var(--bg-surface);
  color: var(--text-primary);
  border: 1px solid var(--border-default);
}

.login-btn-header:hover {
  border-color: var(--brand);
  color: var(--brand);
}

/* 用户菜单 */
.user-menu {
  position: relative;
}

.user-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  object-fit: cover;
  cursor: pointer;
  border: 2px solid transparent;
  transition: all var(--dur-base) var(--ease-out);
}

.user-avatar:hover {
  border-color: var(--brand);
  transform: scale(1.05);
}

.user-dropdown {
  position: absolute;
  top: calc(100% + 12px);
  right: 0;
  width: 240px;
  background: var(--bg-surface);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-lg);
  overflow: hidden;
  z-index: 100;
}

.user-info-section {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: var(--brand-soft);
}

.dropdown-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  object-fit: cover;
}

.user-details {
  flex: 1;
  min-width: 0;
}

.user-nickname {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-id {
  font-size: 12px;
  color: var(--text-tertiary);
}

.menu-divider {
  height: 1px;
  background: var(--border-subtle);
  margin: 8px 0;
}

.menu-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border: none;
  background: none;
  color: var(--text-primary);
  font-size: 14px;
  cursor: pointer;
  transition: background var(--dur-fast) var(--ease-out);
  text-align: left;
}

.menu-item:hover {
  background: var(--bg-hover);
}

.logout-item {
  color: var(--brand);
}

.logout-item:hover {
  background: var(--brand-soft);
}

/* 下拉菜单动画 */
.dropdown-enter-active,
.dropdown-leave-active {
  transition: all 0.2s ease;
}

.dropdown-enter-from,
.dropdown-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

/* 响应式 */
@media (max-width: 900px) {
  .header-content {
    padding: 0 20px;
    gap: 16px;
  }
  
  .logo-text {
    display: none;
  }
  
  .create-btn span {
    display: none;
  }
}

@media (max-width: 768px) {
  .header-content {
    padding: 0 16px;
  }
  
  .action-btn {
    padding: 8px 16px;
  }
}

@media (max-width: 600px) {
  .header {
    height: 56px;
  }
  
  .login-btn-header {
    display: none;
  }
}
</style>
