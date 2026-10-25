<template>
  <div class="home-page">
    <!-- 顶部导航栏 -->
    <AppHeader />

    <!-- 侧边栏 -->
    <AppSidebar />

    <!-- 主内容区 -->
    <main class="main-content">
      <!-- 分类标签 -->
      <nav class="category-nav">
        <div class="category-scroll">
          <button 
            v-for="category in categories" 
            :key="category"
            :class="['category-item', { active: activeCategory === category }]"
            @click="switchCategory(category)"
          >
            <Icon v-if="category === '推荐'" icon="lucide:flame" width="16" />
            {{ category }}
          </button>
        </div>
      </nav>

      <!-- 瀑布流笔记列表 -->
      <div class="waterfall-container">
        <!-- 首屏骨架：与真实卡片同构，避免加载闪跳 -->
        <div v-if="loading && notes.length === 0" class="waterfall-columns" aria-hidden="true">
          <div class="waterfall-column" v-for="col in 4" :key="col">
            <div class="note-card note-card--skeleton" v-for="n in 3" :key="n">
              <div class="skeleton-block" :style="{ height: ((col * 7 + n * 13) % 4) * 30 + 330 + 'px' }"></div>
              <div class="note-content">
                <div class="skeleton-line" style="width: 92%"></div>
                <div class="skeleton-line" style="width: 58%"></div>
              </div>
            </div>
          </div>
        </div>

        <EmptyState
          v-else-if="loadFailed && notes.length === 0"
          variant="error"
          title="加载失败"
          description="网络似乎不太顺畅，请检查后重试"
          action-text="重新加载"
          @action="retryLoad"
        />

        <EmptyState
          v-else-if="notes.length === 0"
          icon="lucide:sticky-note"
          title="这里还没有内容"
          description="换个分类看看，或发布第一篇笔记"
        />

        <div v-else class="waterfall-columns">
          <div class="waterfall-column" v-for="(column, index) in columns" :key="index">
            <div class="note-card" v-for="note in column" :key="note.id" @click="openNoteDetail(note)">
              <div class="note-image-wrapper">
                <SkeletonImage
                  :src="note.image"
                  :alt="note.title"
                  :aspect-ratio="`300 / ${note.height}`"
                />
                <div class="image-overlay">
                  <span v-if="note.tag" class="note-tag">
                    <Icon icon="lucide:tag" width="12" />
                    {{ note.tag }}
                  </span>
                </div>
              </div>
              <div class="note-content">
                <h3 class="note-title">{{ note.title }}</h3>
                <div class="note-meta">
                  <div class="author-info">
                    <img :src="note.avatar" :alt="note.author" class="author-avatar" />
                    <span class="author-name">{{ note.author }}</span>
                  </div>
                  <div class="note-actions">
                    <span class="like-count">
                      <Icon icon="lucide:heart" width="16" />
                      {{ note.likes }}
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 追加加载指示（滚动触底时） -->
        <UiSpinner v-if="loading && notes.length > 0" :size="20" label="加载更多…" />
        <p v-else-if="!hasMore && notes.length > 0" class="no-more">— 已经到底啦 —</p>
      </div>
    </main>

    <!-- 登录弹窗 -->
    <LoginModal
      v-model:visible="showLoginModal"
      @login-success="handleLoginSuccess"
    />

    <!-- 笔记详情弹窗 -->
    <NoteDetailModal
      v-model:visible="showDetailModal"
      :note-id="selectedNoteId"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { Icon } from '@iconify/vue'
import AppHeader from '../components/AppHeader.vue'
import AppSidebar from '../components/AppSidebar.vue'
import LoginModal from '../components/LoginModal.vue'
import NoteDetailModal from '../components/NoteDetailModal.vue'
import SkeletonImage from '../components/SkeletonImage.vue'
import EmptyState from '../components/EmptyState.vue'
import UiSpinner from '../components/UiSpinner.vue'
import { getNoteList, type NoteVo } from '../api/note'
import { useToast } from '../composables/useToast'

const route = useRoute()
import { withThumb } from '../utils/image'

const { showToast } = useToast()

interface Note {
  id: string // 使用 string 类型存储后端的 long ID
  image: string
  title: string
  content?: string
  author: string
  avatar: string
  likes: string
  collection?: number
  tag?: string
  time?: string
  address?: string
  height: number
}

const showLoginModal = ref(false)
const showDetailModal = ref(false)
const selectedNoteId = ref<string | null>(null)
const notes = ref<Note[]>([])
const loading = ref(false)
const loadFailed = ref(false)
const currentPage = ref(1)
const pageSize = ref(20)
const activeCategory = ref('推荐')
const hasMore = ref(true)

const columns = ref<Note[][]>([[], [], [], []])
// 各列近似累计高度，用于增量分配新卡片
const columnHeights = ref<number[]>([0, 0, 0, 0])
// 已分配到列的笔记 id 集合，防止重复分配
const laidOutIds = new Set<string>()

// 分类列表
const categories = ['推荐', '穿搭', '美妆', '影视', '职场', '美食', '旅行', '家居', '游戏', '汽车', '运动', '摄影']

// 格式化点赞数
const formatLikeCount = (count: number | undefined | null): string => {
  if (!count && count !== 0) {
    return '0'
  }
  if (count >= 10000) {
    return (count / 10000).toFixed(1) + 'w'
  } else if (count >= 1000) {
    return (count / 1000).toFixed(1) + 'k'
  }
  return count.toString()
}

// 计算图片高度（根据标题长度和随机因素）
const calculateHeight = (index: number): number => {
  const heights = [380, 400, 420, 440, 460]
  return heights[index % heights.length]
}

// 转换后端数据为前端格式
const convertNoteData = (noteVo: NoteVo, index: number): Note => {
  // 从嵌套的 note 对象中获取数据
  const noteData = noteVo.note

  // 获取作者信息
  const authorName = noteVo.user?.nickname || noteVo.user?.username || '匿名用户'
  // 将 ID 转换为数字用于生成头像（如果 ID 是纯数字字符串）
  const numericId = parseInt(noteData.id) || index
  const authorAvatar = noteVo.user?.image || `https://i.pravatar.cc/150?img=${(numericId % 70) + 1}`

  // 后端多图逗号分隔，卡片取第一张，并应用 OSS 缩略图
  const firstImage = noteData.image?.split(',')[0] || ''
  const cardImage = withThumb(firstImage, 400) || `https://picsum.photos/300/${calculateHeight(index)}?random=${numericId}`

  return {
    id: noteData.id, // 使用真实的后端 ID（string 类型）
    image: cardImage,
    title: noteData.title || '无标题',
    content: noteData.content,
    author: authorName,
    avatar: authorAvatar,
    likes: formatLikeCount(noteData.like), // 后端字段是 like
    collection: noteData.collection,
    tag: noteData.type, // 使用 type 作为标签
    time: noteData.time,
    address: noteData.address,
    height: calculateHeight(index)
  }
}

// 打开笔记详情
const openNoteDetail = (note: Note) => {
  // 确保笔记有有效的 ID
  if (!note.id) {
    showToast('无法打开笔记详情：笔记 ID 无效', 'error')
    return
  }

  // 直接传递笔记 ID，让详情组件自己去请求数据
  selectedNoteId.value = note.id
  showDetailModal.value = true
}

// 加载笔记列表（追加模式）
const loadNotes = async () => {
  if (loading.value || !hasMore.value) return

  const page = currentPage.value

  try {
    loading.value = true
    // "推荐" 不带 type（后端返回全量信息流），其余分类按类型过滤
    const categoryType = activeCategory.value === '推荐' ? undefined : activeCategory.value
    const response = await getNoteList(page, pageSize.value, categoryType)
    const newNotes = (response.data || []).map((note, index) => convertNoteData(note, index))

    // 第一页时清空列表并全量重排，后续页只增量追加
    if (page === 1) {
      notes.value = newNotes
      layoutWaterfall()
    } else {
      notes.value = [...notes.value, ...newNotes]
      appendToWaterfall(newNotes)
    }

    // 后端返回空数组或不足一页时停止加载
    if (newNotes.length === 0 || newNotes.length < pageSize.value) {
      hasMore.value = false
    } else {
      // 请求成功后才翻页，避免失败/空响应时跳页
      currentPage.value = page + 1
    }
    loadFailed.value = false
  } catch (error: unknown) {
    const errorMsg = error instanceof Error ? error.message : '网络错误，请稍后重试'
    showToast(errorMsg, 'error')
    if (notes.value.length === 0) {
      loadFailed.value = true
    }
    // 失败时保留 hasMore，让用户滚动/重试可再次加载（避免一次网络抖动永久断流）
  } finally {
    loading.value = false
  }
}

// 失败后重新加载
const retryLoad = () => {
  loadFailed.value = false
  currentPage.value = 1
  hasMore.value = true
  loadNotes()
}

// 滚动节流：requestAnimationFrame + 200ms 时间戳节流
let scrollTicking = false
let lastScrollTime = 0
const handleScroll = () => {
  const now = Date.now()
  if (scrollTicking || now - lastScrollTime < 200) return

  scrollTicking = true
  window.requestAnimationFrame(() => {
    scrollTicking = false
    lastScrollTime = Date.now()

    const scrollTop = document.documentElement.scrollTop
    const windowHeight = window.innerHeight
    const documentHeight = document.documentElement.scrollHeight

    // 距离底部 200px 时触发加载
    if (documentHeight - scrollTop - windowHeight < 200) {
      loadNotes()
    }
  })
}

// 切换分类
const switchCategory = (category: string) => {
  activeCategory.value = category
  // 重置列表并重新加载
  notes.value = []
  currentPage.value = 1
  hasMore.value = true
  loadFailed.value = false
  loadNotes()
}

// 发布成功后刷新列表
const handleNotePublished = () => {
  currentPage.value = 1
  hasMore.value = true
  loadFailed.value = false
  loadNotes()
}

// 检查登录状态
const checkLoginStatus = () => {
  const token = localStorage.getItem('token')
  if (!token) {
    showLoginModal.value = true
  }
}

// 登录成功回调
const handleLoginSuccess = () => {
  // 登录成功后重新加载笔记列表
  notes.value = []
  currentPage.value = 1
  hasMore.value = true
  loadNotes()
}

// 增量分配：新笔记只追加到当前最短的列，不重排已有卡片
const appendToWaterfall = (newNotes: Note[]) => {
  newNotes.forEach(note => {
    if (laidOutIds.has(note.id)) return
    const minIndex = columnHeights.value.indexOf(Math.min(...columnHeights.value))
    columns.value[minIndex].push(note)
    columnHeights.value[minIndex] += note.height + 20
    laidOutIds.add(note.id)
  })
}

// 全量重排（仅刷新场景：首屏加载、切换分类、重新加载、发布后刷新）
const layoutWaterfall = () => {
  columns.value = [[], [], [], []]
  columnHeights.value = [0, 0, 0, 0]
  laidOutIds.clear()
  appendToWaterfall(notes.value)
}

onMounted(() => {
  checkLoginStatus()
  loadNotes()
  // 搜索页等来源跳转 /?noteId=xxx 时直接打开笔记详情
  const noteId = route.query.noteId
  if (typeof noteId === 'string' && noteId) {
    selectedNoteId.value = noteId
    showDetailModal.value = true
  }
  window.addEventListener('scroll', handleScroll)
  window.addEventListener('note:published', handleNotePublished)
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
  window.removeEventListener('note:published', handleNotePublished)
})
</script>

<style scoped>
/* 全局样式 */
.home-page {
  min-height: 100vh;
  background: var(--bg-page);
}

/* 主内容区 */
.main-content {
  margin-left: 220px;
  padding-top: 64px;
  min-height: 100vh;
}

/* 分类导航 */
.category-nav {
  position: sticky;
  top: 64px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(12px);
  padding: 14px 0;
  border-bottom: 1px solid var(--border-subtle);
  z-index: 50;
}

.category-scroll {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 24px;
  display: flex;
  gap: 12px;
  overflow-x: auto;
}

.category-scroll::-webkit-scrollbar {
  display: none;
}

.category-item {
  display: flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-secondary);
  background: transparent;
  border: none;
  cursor: pointer;
  padding: 8px 18px;
  border-radius: 20px;
  transition: all 0.3s;
}

.category-item:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.category-item.active {
  background: var(--brand-grad);
  color: white;
  box-shadow: var(--shadow-brand);
}

/* 瀑布流容器 */
.waterfall-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px;
}

.no-more {
  text-align: center;
  padding: 24px 0 32px;
  font-size: 13px;
  color: var(--text-disabled);
}

/* 首屏骨架卡片 */
.note-card--skeleton {
  cursor: default;
  pointer-events: none;
}

.skeleton-block {
  width: 100%;
  border-radius: 0;
  background: linear-gradient(100deg, var(--bg-subtle) 40%, var(--bg-hover) 50%, var(--bg-subtle) 60%);
  background-size: 200% 100%;
  animation: skeleton-shimmer 1.4s ease infinite;
}

.skeleton-line {
  height: 13px;
  border-radius: var(--radius-xs);
  margin-bottom: 10px;
  background: linear-gradient(100deg, var(--bg-subtle) 40%, var(--bg-hover) 50%, var(--bg-subtle) 60%);
  background-size: 200% 100%;
  animation: skeleton-shimmer 1.4s ease infinite;
}

@keyframes skeleton-shimmer {
  to {
    background-position: -200% 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .skeleton-block,
  .skeleton-line {
    animation: none;
  }
}

.waterfall-columns {
  display: flex;
  gap: 20px;
}

.waterfall-column {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 笔记卡片 */
.note-card {
  background: var(--bg-surface);
  border-radius: var(--radius-lg);
  overflow: hidden;
  cursor: pointer;
  transition: transform var(--dur-base) var(--ease-out), box-shadow var(--dur-base) var(--ease-out);
  box-shadow: var(--shadow-sm);
}

.note-card:hover {
  transform: translateY(-6px);
  box-shadow: var(--shadow-lg);
}

.note-image-wrapper {
  position: relative;
  width: 100%;
  overflow: hidden;
  background: var(--bg-subtle);
}

.note-image {
  width: 100%;
  display: block;
  object-fit: cover;
  transition: transform var(--dur-slow) var(--ease-out);
}

.note-card:hover :deep(.skeleton-image__img) {
  transform: scale(1.08);
}

.image-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(to bottom, rgba(0, 0, 0, 0.1) 0%, transparent 30%);
  pointer-events: none;
}

.note-tag {
  position: absolute;
  top: 12px;
  left: 12px;
  display: flex;
  align-items: center;
  gap: 4px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  padding: 6px 12px;
  border-radius: 16px;
  font-size: 12px;
  font-weight: 600;
  color: var(--brand);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.note-content {
  padding: 14px;
}

.note-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.5;
  margin-bottom: 12px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-word;
}

.note-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.author-info {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.author-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  object-fit: cover;
  border: 2px solid var(--bg-subtle);
  flex-shrink: 0;
}

.author-name {
  font-size: 13px;
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.note-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.like-count {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--text-tertiary);
  transition: color 0.2s;
}

.note-card:hover .like-count {
  color: var(--brand);
}

/* 响应式设计 */
@media (max-width: 1400px) {
  .waterfall-container {
    max-width: 1000px;
  }
}

@media (max-width: 1200px) {
  .waterfall-container {
    gap: 16px;
  }
  
  .waterfall-column:nth-child(4) {
    display: none;
  }
}

@media (max-width: 1024px) {
  .main-content {
    margin-left: 80px;
  }
}

@media (max-width: 900px) {
  .waterfall-column:nth-child(3) {
    display: none;
  }
}

@media (max-width: 768px) {
  .main-content {
    margin-left: 0;
  }
}

@media (max-width: 600px) {
  .waterfall-column:nth-child(2) {
    display: none;
  }
  
  .waterfall-container {
    padding: 16px;
  }
  
  .main-content {
    padding-top: 56px;
  }
  
  .category-nav {
    top: 56px;
  }
}
</style>
