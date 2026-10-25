<template>
  <div class="profile-page">
    <!-- 顶部导航栏 -->
    <AppHeader />

    <!-- 侧边栏 -->
    <AppSidebar />

    <!-- 主内容区 -->
    <main class="main-content">
      <!-- 未登录：引导登录而不是渲染空壳 -->
      <EmptyState
        v-if="!userStore.isLoggedIn"
        icon="lucide:circle-user"
        title="登录后查看个人主页"
        description="管理你的笔记、订单与浏览历史"
        action-text="去登录"
        @action="goHome"
      />
      <div v-else class="profile-container">
        <!-- 用户信息卡片 -->
        <div class="profile-card">
          <div class="profile-header">
            <img
              :src="userStore.state.userInfo?.image || 'https://i.pravatar.cc/150?img=1'"
              :alt="userStore.state.userInfo?.nickname || '用户'"
              class="profile-avatar"
            />
            <div class="profile-info">
              <h2 class="profile-nickname">{{ userStore.state.userInfo?.nickname || userStore.state.userInfo?.username || '用户' }}</h2>
              <p class="profile-id">小红书号: {{ userStore.state.userInfo?.number || userStore.state.userInfo?.id || '-' }}</p>
            </div>
          </div>

          <div class="profile-details" v-if="userStore.state.userInfo">
            <div class="detail-row" v-if="userStore.state.userInfo.sex">
              <span class="detail-label">性别</span>
              <span class="detail-value">{{ userStore.state.userInfo.sex }}</span>
            </div>
            <div class="detail-row" v-if="userStore.state.userInfo.school">
              <span class="detail-label">学校</span>
              <span class="detail-value">{{ userStore.state.userInfo.school }}</span>
            </div>
            <div class="detail-row" v-if="userStore.state.userInfo.address">
              <span class="detail-label">所在地</span>
              <span class="detail-value">{{ userStore.state.userInfo.address }}</span>
            </div>
            <div class="detail-row" v-if="userStore.state.userInfo.birthday">
              <span class="detail-label">生日</span>
              <span class="detail-value">{{ userStore.state.userInfo.birthday }}</span>
            </div>
          </div>

          <button class="logout-btn" @click="handleLogout">
            <Icon icon="lucide:log-out" width="18" />
            退出登录
          </button>
        </div>

        <!-- 占位模块 -->
        <div class="placeholder-grid">
          <div class="placeholder-card">
            <Icon icon="lucide:sticky-note" width="40" />
            <h3>我的笔记</h3>
            <p>开发中，敬请期待</p>
          </div>
          <div class="placeholder-card">
            <Icon icon="lucide:settings" width="40" />
            <h3>设置</h3>
            <p>开发中，敬请期待</p>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import AppHeader from '../components/AppHeader.vue'
import AppSidebar from '../components/AppSidebar.vue'
import EmptyState from '../components/EmptyState.vue'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()

// 未登录引导：回首页由登录弹窗承接
const goHome = () => {
  router.push('/')
}

// 退出登录
const handleLogout = () => {
  userStore.logout()
  router.push('/')
}
</script>

<style scoped>
.profile-page {
  min-height: 100vh;
  background: var(--bg-page);
}

.main-content {
  margin-left: 220px;
  padding-top: 88px;
  min-height: 100vh;
}

.profile-container {
  max-width: 900px;
  margin: 0 auto;
  padding: 24px;
}

/* 用户信息卡片 */
.profile-card {
  background: var(--bg-surface);
  border-radius: 16px;
  padding: 32px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  margin-bottom: 24px;
}

.profile-header {
  display: flex;
  align-items: center;
  gap: 20px;
  padding-bottom: 24px;
  border-bottom: 1px solid var(--border-subtle);
}

.profile-avatar {
  width: 88px;
  height: 88px;
  border-radius: 50%;
  object-fit: cover;
  border: 3px solid #fff0f0;
}

.profile-nickname {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0 0 6px 0;
}

.profile-id {
  font-size: 13px;
  color: var(--text-tertiary);
  margin: 0;
}

.profile-details {
  padding: 20px 0;
}

.detail-row {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 8px 0;
  font-size: 14px;
}

.detail-label {
  width: 60px;
  color: var(--text-tertiary);
  flex-shrink: 0;
}

.detail-value {
  color: var(--text-primary);
}

.logout-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  padding: 12px;
  background: var(--brand-soft);
  color: var(--brand);
  border: none;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--dur-fast) var(--ease-out);
}

.logout-btn:hover {
  background: var(--brand-soft-2);
}

/* 占位模块 */
.placeholder-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 20px;
}

.placeholder-card {
  background: var(--bg-surface);
  border-radius: 16px;
  padding: 40px 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  color: var(--text-disabled);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.placeholder-card h3 {
  font-size: 16px;
  color: var(--text-secondary);
  margin: 0;
}

.placeholder-card p {
  font-size: 13px;
  color: var(--text-disabled);
  margin: 0;
}

/* 响应式 */
@media (max-width: 1024px) {
  .main-content {
    margin-left: 80px;
  }
}

@media (max-width: 768px) {
  .main-content {
    margin-left: 0;
  }
}
</style>
