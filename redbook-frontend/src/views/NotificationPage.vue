<template>
  <div class="notification-page">
    <!-- 顶部导航栏 -->
    <AppHeader />

    <!-- 侧边栏 -->
    <AppSidebar />

    <!-- 主内容区 -->
    <main class="main-content">
      <div class="notification-container">
        <!-- 通知内容 -->
        <div class="notification-content">
          <!-- WebSocket 连接状态 -->
          <div class="connection-status" v-if="!isConnected">
            <Icon icon="lucide:wifi-off" width="16" />
            <span>消息服务未连接，正在重连...</span>
          </div>
          
          <!-- 标签切换 -->
          <div class="content-tabs">
            <button 
              v-for="tab in tabs" 
              :key="tab.id"
              class="tab-item"
              :class="{ active: activeTab === tab.id }"
              @click="activeTab = tab.id"
            >
              {{ tab.name }}
            </button>
          </div>

          <!-- 通知列表 -->
          <div class="notification-list">
            <EmptyState
              v-if="filteredNotifications.length === 0"
              icon="lucide:bell"
              title="暂无新消息"
              :description="isConnected ? '有新的互动时会第一时间通知你' : '连接消息服务后即可接收新通知'"
            />
            <div
              v-for="notification in filteredNotifications"
              :key="notification.id"
              class="notification-item"
              :class="{ 'like-collect-item': notification.type === 'like' || notification.type === 'collect' }"
            >
              <img :src="notification.avatar" :alt="notification.username" class="user-avatar" />
              
              <div class="notification-main">
                <!-- 点赞和收藏类型的布局 -->
                <template v-if="notification.type === 'like' || notification.type === 'collect'">
                  <div class="like-collect-content">
                    <div class="user-action">
                      <span class="username">{{ notification.username }}</span>
                      <Icon v-if="notification.type === 'like'" icon="lucide:heart" width="14" class="action-icon like-icon" />
                      <Icon v-else icon="lucide:star" width="14" class="action-icon collect-icon" />
                    </div>
                    <div class="action-with-time">
                      <span class="action-text">{{ notification.content }}</span>
                      <span class="time">{{ notification.time }}</span>
                    </div>
                    <p v-if="notification.noteTitle" class="note-reference">
                      {{ notification.noteTitle }}
                    </p>
                  </div>
                </template>
                
                <!-- 其他类型的布局 -->
                <template v-else>
                  <div class="notification-header">
                    <span class="username">{{ notification.username }}</span>
                    <span class="time">{{ notification.time }}</span>
                  </div>
                  
                  <div class="notification-body">
                    <p class="notification-text">{{ notification.content }}</p>
                    <p v-if="notification.noteTitle" class="note-reference">
                      {{ notification.noteTitle }}
                    </p>
                  </div>
                  
                  <div class="notification-actions">
                    <button class="action-btn-reply">
                      <Icon icon="lucide:reply" width="16" />
                      回复
                    </button>
                    <button class="action-btn-like">
                      <Icon icon="lucide:heart" width="16" />
                    </button>
                  </div>
                </template>
              </div>
              
              <!-- 点赞和收藏类型显示笔记图片和更多按钮 -->
              <div v-if="notification.type === 'like' || notification.type === 'collect'" class="notification-right">
                <img 
                  v-if="notification.noteImage" 
                  :src="notification.noteImage" 
                  :alt="notification.noteTitle" 
                  class="note-thumbnail" 
                />
                <button class="more-btn">
                  <Icon icon="lucide:ellipsis" width="20" />
                </button>
              </div>
              
              <!-- 其他类型显示原有的右侧内容 -->
              <div v-else class="notification-right">
                <span class="time">{{ notification.time }}</span>
                <button class="more-btn">
                  <Icon icon="lucide:ellipsis" width="20" />
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Icon } from '@iconify/vue'
import AppHeader from '../components/AppHeader.vue'
import AppSidebar from '../components/AppSidebar.vue'
import EmptyState from '../components/EmptyState.vue'
import { useWebSocket } from '@/composables/useWebSocket'
import { getLikeList, type LikeVo } from '@/api/note'
import { withThumb } from '@/utils/image'

interface Notification {
  id: string
  type: string
  avatar: string
  username: string
  time: string
  content: string
  noteTitle?: string
  noteImage?: string
}

const tabs = ref([
  { id: 'system', name: '赞和收藏' },
  { id: 'mention', name: '新增关注' },
  { id: 'all', name: '评论和@' }
])

const activeTab = ref('system')

// 初始化通知数据
const notifications = ref<Notification[]>([])

// 使用 WebSocket
const { isConnected, onMessage } = useWebSocket()

// 安全解析日期字符串（兼容 Safari 不支持的 "yyyy-MM-dd HH:mm:ss" 格式）
const parseDate = (value: unknown): Date | null => {
  if (value instanceof Date) return value
  if (typeof value === 'number') return new Date(value)
  if (typeof value === 'string') {
    const date = new Date(value.replace(' ', 'T'))
    return isNaN(date.getTime()) ? null : date
  }
  return null
}

// 格式化时间
const formatTime = (timestamp?: number | string | Date): string => {
  const date = parseDate(timestamp)
  if (!date) {
    return '刚刚'
  }

  const now = Date.now()
  const diff = now - date.getTime()
  const seconds = Math.floor(diff / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)
  const days = Math.floor(hours / 24)

  if (seconds < 60) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  if (hours < 24) return `${hours}小时前`
  if (days < 7) return `${days}天前`

  return `${date.getMonth() + 1}-${date.getDate()}`
}

// 获取点赞列表
const fetchLikeList = async () => {
  try {
    const response = await getLikeList()
    // 将后端数据转换为前端通知格式
    const likeNotifications: Notification[] = (response.data || []).map((item: LikeVo) => ({
      id: String(item.like.id),
      type: 'like',
      avatar: item.user?.image || 'https://i.pravatar.cc/150?img=1',
      username: item.user?.nickname || item.user?.username || '未知用户',
      time: formatTime(item.like.createTime),
      content: '赞了你的笔记',
      noteTitle: item.note?.title || item.note?.content?.substring(0, 20) || '无标题',
      noteImage: withThumb(item.note?.image?.split(',')[0], 200)
    }))

    // 合并到通知列表
    notifications.value = [...likeNotifications, ...notifications.value]
  } catch (error) {
    // 获取点赞列表失败
  }
}

// 格式化时间（原有的，保留用于WebSocket消息）
const formatTimeFromTimestamp = (timestamp?: number): string => {
  if (!timestamp) {
    return '刚刚'
  }
  
  const now = Date.now()
  const diff = now - timestamp
  const seconds = Math.floor(diff / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)
  const days = Math.floor(hours / 24)
  
  if (seconds < 60) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  if (hours < 24) return `${hours}小时前`
  if (days < 7) return `${days}天前`
  
  const date = new Date(timestamp)
  return `${date.getMonth() + 1}月${date.getDate()}日`
}

// ---------- WebSocket 消息类型守卫 ----------
// 替代原来的 as Record<string, any> 双重断言：先通过守卫确认结构，再安全访问字段
type Rec = Record<string, unknown>
const isRec = (v: unknown): v is Rec => typeof v === 'object' && v !== null

// 后端格式一：{ type: 0|1|2, noticeId, obj: { like|collection|attention, note, user } }
interface ObjLikeInfo {
  id?: number | string
  createTime?: string
}
interface ObjNoteInfo {
  title?: string
  content?: string
  image?: string
}
interface ObjUserInfo {
  image?: string
  nickname?: string
  phone?: string
}
interface ObjMessage {
  type: number
  noticeId: number | string
  obj: {
    like?: ObjLikeInfo
    collection?: ObjLikeInfo
    attention?: ObjLikeInfo
    note?: ObjNoteInfo
    user?: ObjUserInfo
  }
}
const isObjMessage = (v: unknown): v is ObjMessage =>
  isRec(v) &&
  typeof v.type === 'number' &&
  v.noticeId !== undefined && v.noticeId !== null &&
  isRec(v.obj)

// 类型谓词：守卫通过后，对应字段一定存在（收窄掉可选性）
type LikeMsg = ObjMessage & { obj: { like: ObjLikeInfo; note: ObjNoteInfo; user: ObjUserInfo } }
const isLikeMsg = (m: ObjMessage): m is LikeMsg =>
  m.type === 0 && m.obj.like !== undefined && m.obj.note !== undefined && m.obj.user !== undefined

type CollectMsg = ObjMessage & { obj: { collection: ObjLikeInfo; note: ObjNoteInfo; user: ObjUserInfo } }
const isCollectMsg = (m: ObjMessage): m is CollectMsg =>
  m.type === 1 && m.obj.collection !== undefined && m.obj.note !== undefined && m.obj.user !== undefined

type AttentionMsg = ObjMessage & { obj: { attention: ObjLikeInfo; user: ObjUserInfo } }
const isAttentionMsg = (m: ObjMessage): m is AttentionMsg =>
  m.type === 2 && m.obj.attention !== undefined && m.obj.user !== undefined

// 后端格式二：NotificationMessageDTO { type: 'like'|'collect'|'follow'|'comment', actionUserId, noticeUserId, ... }
interface DtoMessage {
  type: string
  actionUserId: number | string
  noticeUserId: number | string
  actionUserAvatar?: string
  actionUserNickname?: string
  timestamp?: number
  noteTitle?: string
  noteContent?: string
  noteImage?: string
  commentContent?: string
}
const isDtoMessage = (v: unknown): v is DtoMessage =>
  isRec(v) &&
  typeof v.type === 'string' &&
  v.actionUserId !== undefined && v.actionUserId !== null &&
  v.noticeUserId !== undefined && v.noticeUserId !== null

// 兼容旧格式：{ type: 'notification'|'comment'|'like'|'follow'|'collect', ... }
const LEGACY_TYPES = ['notification', 'comment', 'like', 'follow', 'collect']
interface LegacyMessage {
  type: string
  id?: string | number
  content?: string
  message?: string
  avatar?: string
  userAvatar?: string
  userImage?: string
  username?: string
  userName?: string
  nickname?: string
  timestamp?: number
  time?: number
  noteTitle?: string
  title?: string
  noteContent?: string
  noteImage?: string
  image?: string
  noteImages?: string
}
const isLegacyMessage = (v: unknown): v is LegacyMessage =>
  isRec(v) && typeof v.type === 'string' && LEGACY_TYPES.includes(v.type)

// 处理接收到的 WebSocket 消息
const handleWebSocketMessage = (data: unknown) => {
  try {
    const pushNotification = (n: Notification) => {
      notifications.value.unshift(n)
      if ('Notification' in window && Notification.permission === 'granted') {
        new Notification('新消息', {
          body: `${n.username}: ${n.content}`,
          icon: n.avatar
        })
      }
    }

    // 后端格式：{ type: 0, noticeId: 3, obj: { like, note, user } }
    if (isObjMessage(data)) {
      let notificationType = ''
      let content = ''
      let avatar = ''
      let username = ''
      let noteTitle = ''
      let noteImage = ''
      let notificationId = String(Date.now())
      let createTime: string | undefined

      if (isLikeMsg(data)) {
        const { like, note, user } = data.obj
        notificationType = 'like'
        content = '赞了你的笔记'
        avatar = user.image || 'https://i.pravatar.cc/150?img=10'
        username = user.nickname || user.phone || '未知用户'
        noteTitle = note.title || note.content?.substring(0, 20) || '无标题'
        noteImage = withThumb(note.image?.split(',')[0], 200)
        notificationId = String(like.id)
        createTime = like.createTime
      } else if (isCollectMsg(data)) {
        const { collection, note, user } = data.obj
        notificationType = 'collect'
        content = '收藏了你的笔记'
        avatar = user.image || 'https://i.pravatar.cc/150?img=10'
        username = user.nickname || user.phone || '未知用户'
        noteTitle = note.title || note.content?.substring(0, 20) || '无标题'
        noteImage = withThumb(note.image?.split(',')[0], 200)
        notificationId = String(collection.id)
        createTime = collection.createTime
      } else if (isAttentionMsg(data)) {
        const { attention, user } = data.obj
        notificationType = 'follow'
        content = '关注了你'
        avatar = user.image || 'https://i.pravatar.cc/150?img=10'
        username = user.nickname || user.phone || '未知用户'
        notificationId = String(attention.id)
        createTime = attention.createTime
      }

      if (notificationType) {
        pushNotification({
          id: notificationId,
          type: notificationType,
          avatar,
          username,
          time: formatTime(createTime),
          content,
          noteTitle,
          noteImage
        })
      }
    }
    // 新格式：NotificationMessageDTO
    else if (isDtoMessage(data)) {
      let content = ''
      if (data.type === 'like') {
        content = '赞了你的笔记'
      } else if (data.type === 'collect') {
        content = '收藏了你的笔记'
      } else if (data.type === 'follow') {
        content = '关注了你'
      } else if (data.type === 'comment') {
        content = data.commentContent || '评论了你的笔记'
      }

      pushNotification({
        id: String(data.timestamp || Date.now()),
        type: data.type,
        avatar: data.actionUserAvatar || 'https://i.pravatar.cc/150?img=10',
        username: data.actionUserNickname || '未知用户',
        time: formatTimeFromTimestamp(data.timestamp || Date.now()),
        content,
        noteTitle: data.noteTitle || data.noteContent || '无标题',
        noteImage: data.noteImage || ''
      })
    }
    // 兼容旧格式
    else if (isLegacyMessage(data)) {
      let content = data.content || data.message || ''
      if (data.type === 'like') {
        content = '赞了你的笔记'
      } else if (data.type === 'collect') {
        content = '收藏了你的笔记'
      } else if (data.type === 'follow') {
        content = '关注了你'
      } else if (data.type === 'comment') {
        content = data.content || data.message || '评论了你的笔记'
      }

      pushNotification({
        id: data.id !== undefined ? String(data.id) : String(Date.now()),
        type: data.type,
        avatar: data.avatar || data.userAvatar || data.userImage || 'https://i.pravatar.cc/150?img=10',
        username: data.username || data.userName || data.nickname || '未知用户',
        time: formatTimeFromTimestamp(data.timestamp || data.time || Date.now()),
        content,
        noteTitle: data.noteTitle || data.title || data.noteContent,
        noteImage: data.noteImage || data.image || (data.noteImages ? data.noteImages.split(',')[0] : '')
      })
    }
  } catch (error) {
    // 处理 WebSocket 消息失败
  }
}

// 请求浏览器通知权限
const requestNotificationPermission = () => {
  if ('Notification' in window && Notification.permission === 'default') {
    Notification.requestPermission()
  }
}

onMounted(() => {
  // 获取点赞列表
  fetchLikeList()
  
  // 监听 WebSocket 消息
  onMessage(handleWebSocketMessage)
  
  // 请求通知权限
  requestNotificationPermission()
})

const filteredNotifications = computed(() => {
  // tab 过滤：'system' 赞和收藏；'mention' 新增关注；'all' 显示全部类型（含评论/@）
  if (activeTab.value === 'system') {
    return notifications.value.filter(n => n.type === 'like' || n.type === 'collect')
  } else if (activeTab.value === 'mention') {
    return notifications.value.filter(n => n.type === 'follow')
  }
  return notifications.value
})
</script>

<style scoped>
.notification-page {
  min-width: 1000px;
  min-height: 100vh;
  background: var(--bg-page);
}

/* 主内容区 */
.main-content {
  margin-left: 220px;
  padding-top: 64px;
  min-height: 100vh;
  height: 100%;
  width: calc(100% - 220px);
}

.notification-container {
  width: 100%;
  padding: 24px;
}

/* 通知内容 */
.notification-content {
  background: var(--bg-surface);
  border-radius: 12px;
  overflow: hidden;
}

/* WebSocket 连接状态 */
.connection-status {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 24px;
  background: #fff7e6;
  border-bottom: 1px solid #ffe7ba;
  color: #d46b08;
  font-size: 13px;
}

.content-tabs {
  display: flex;
  border-bottom: 1px solid #f0f0f0;
  padding: 0 24px;
}

.tab-item {
  padding: 16px 24px;
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 14px;
  color: var(--text-secondary);
  position: relative;
  transition: all 0.2s;
}

.tab-item:hover {
  color: var(--text-primary);
}

.tab-item.active {
  color: var(--brand);
  font-weight: 600;
}

.tab-item.active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 40px;
  height: 3px;
  background: var(--brand);
  border-radius: 2px 2px 0 0;
}

/* 通知列表 */
.notification-list {
  padding: 16px 24px;
}

.notification-item {
  display: flex;
  gap: 16px;
  padding: 20px 0;
  border-bottom: 1px solid #f7f7f7;
}

.notification-item:last-child {
  border-bottom: none;
}

.user-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
}

.notification-main {
  flex: 1;
  min-width: 0;
}

.notification-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.username {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.time {
  font-size: 12px;
  color: var(--text-tertiary);
}

.notification-body {
  margin-bottom: 12px;
}

.notification-text {
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.6;
  margin: 0 0 8px 0;
}

.note-reference {
  font-size: 13px;
  color: var(--text-tertiary);
  margin: 0;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-align: left;
}

/* 点赞和收藏通知样式 */
.like-collect-item {
  align-items: flex-start;
}

.like-collect-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: flex-start;
}

.user-action {
  display: flex;
  align-items: center;
  gap: 6px;
  justify-content: flex-start;
}

.action-icon {
  flex-shrink: 0;
}

.like-icon {
  color: var(--brand);
}

.collect-icon {
  color: #ffa500;
}

.action-text {
  font-size: 13px;
  color: var(--text-secondary);
  margin: 0;
  text-align: left;
}

.action-with-time {
  display: flex;
  align-items: center;
  gap: 8px;
}

.action-with-time .action-text {
  margin: 0;
}

.action-with-time .time {
  font-size: 12px;
  color: var(--text-tertiary);
  white-space: nowrap;
}

.notification-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
  margin-left: auto;
}

.like-collect-item .notification-right {
  flex-direction: row;
  align-items: center;
  gap: 12px;
}

.notification-right .time {
  font-size: 12px;
  color: var(--text-tertiary);
  white-space: nowrap;
}

.like-collect-item .notification-right .note-thumbnail {
  order: -1;
}

.more-btn {
  padding: 4px;
  border: none;
  background: transparent;
  cursor: pointer;
  color: var(--text-tertiary);
  transition: color 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
}

.more-btn:hover {
  color: var(--text-primary);
}

.notification-actions {
  display: flex;
  gap: 16px;
}

.action-btn-reply,
.action-btn-like {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border: 1px solid var(--border-default);
  border-radius: 16px;
  background: var(--bg-surface);
  cursor: pointer;
  font-size: 13px;
  color: var(--text-secondary);
  transition: all 0.2s;
}

.action-btn-reply:hover {
  border-color: var(--brand);
  color: var(--brand);
}

.action-btn-like:hover {
  border-color: var(--brand);
  color: var(--brand);
}

.note-thumbnail {
  width: 80px;
  height: 80px;
  border-radius: 8px;
  object-fit: cover;
  flex-shrink: 0;
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
  
  .notification-container {
    padding: 16px;
  }
}

@media (max-width: 600px) {
  .main-content {
    padding-top: 56px;
  }
  
  .notification-container {
    padding: 12px;
  }
}
</style>
