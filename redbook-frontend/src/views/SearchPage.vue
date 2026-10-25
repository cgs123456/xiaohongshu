<template>
  <div class="search-page">
    <AppHeader />
    <AppSidebar />

    <main class="search-main">
      <!-- 搜索框 -->
      <div class="search-bar">
        <Icon icon="mdi:magnify" width="20" />
        <input
          v-model="keyword"
          class="search-input"
          type="text"
          placeholder="搜索笔记"
          @keyup.enter="doSearch"
        />
        <button class="search-btn" @click="doSearch">搜索</button>
      </div>

      <!-- 无关键词：历史 + 热搜 -->
      <template v-if="!activeKeyword">
        <section v-if="history.length > 0" class="panel">
          <div class="panel-head">
            <h3><Icon icon="mdi:history" width="16" /> 搜索历史</h3>
            <button class="clear-btn" @click="handleClearHistory">
              <Icon icon="mdi:delete-outline" width="14" /> 清空
            </button>
          </div>
          <div class="chip-list">
            <button v-for="h in history" :key="h.id ?? h.history" class="chip" @click="searchFrom(h.history)">
              {{ h.history }}
            </button>
          </div>
        </section>

        <section v-if="hotList.length > 0" class="panel">
          <div class="panel-head">
            <h3><Icon icon="mdi:fire" width="16" /> 热搜榜</h3>
          </div>
          <div class="hot-list">
            <button v-for="(item, idx) in hotList" :key="idx" class="hot-item" @click="searchFrom(hotWord(item))">
              <span :class="['hot-rank', { top: idx < 3 }]">{{ idx + 1 }}</span>
              <span class="hot-word">{{ hotWord(item) }}</span>
              <span class="hot-score">{{ hotScore(item) }}</span>
            </button>
          </div>
        </section>

        <div v-if="history.length === 0 && hotList.length === 0 && !loadingSide" class="empty-container">
          <Icon icon="mdi:magnify" width="64" />
          <p>输入关键词，发现更多好笔记</p>
        </div>
      </template>

      <!-- 有关键词：结果列表 -->
      <template v-else>
        <p class="result-summary">“{{ activeKeyword }}” 的搜索结果（{{ results.length }} 条）</p>

        <div v-if="loading" class="loading-container">
          <Icon icon="mdi:loading" class="loading-icon" width="48" />
          <p>搜索中...</p>
        </div>

        <div v-else-if="results.length === 0" class="empty-container">
          <Icon icon="mdi:file-find-outline" width="64" />
          <p>没有找到相关笔记，换个关键词试试</p>
        </div>

        <div v-else class="result-list">
          <div v-for="item in results" :key="item.id" class="result-card" @click="openNote(item)">
            <img class="result-image" :src="item.image" :alt="item.title" loading="lazy" />
            <div class="result-body">
              <h3 class="result-title">{{ item.title }}</h3>
              <p class="result-content">{{ item.content }}</p>
              <div class="result-meta">
                <div class="author-info">
                  <img class="author-avatar" :src="item.avatar" :alt="item.author" />
                  <span class="author-name">{{ item.author }}</span>
                </div>
                <span class="like-count">
                  <Icon icon="mdi:heart-outline" width="14" />
                  {{ item.likes }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </template>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import AppHeader from '../components/AppHeader.vue'
import AppSidebar from '../components/AppSidebar.vue'
import { searchNotes, getSearchHistory, clearSearchHistory, getHotList, type SearchHistory, type HotKeyword } from '../api/search'
import { withThumb } from '../utils/image'
import type { NoteVo } from '../api/note'
import { useToast } from '../composables/useToast'

interface SearchResultNote {
  id: string
  image: string
  title: string
  content: string
  author: string
  avatar: string
  likes: number
}

const route = useRoute()
const router = useRouter()
const { showToast } = useToast()

const keyword = ref('')
const activeKeyword = ref('')
const loading = ref(false)
const loadingSide = ref(false)
const results = ref<SearchResultNote[]>([])
const history = ref<SearchHistory[]>([])
const hotList = ref<HotKeyword[]>([])

const fallbackAvatar = (id: string, index: number): string => {
  const numericId = parseInt(id) || index
  return `https://i.pravatar.cc/150?img=${(numericId % 70) + 1}`
}

const convertResult = (noteVo: NoteVo, index: number): SearchResultNote => {
  const note = noteVo.note
  const firstImage = note.image?.split(',')[0] || ''
  return {
    id: note.id,
    image: withThumb(firstImage, 300) || `https://picsum.photos/300/200?random=${index}`,
    title: note.title || '无标题',
    content: (note.content || '').slice(0, 80),
    author: noteVo.user?.nickname || noteVo.user?.username || '匿名用户',
    avatar: noteVo.user?.image || fallbackAvatar(note.id, index),
    likes: note.like ?? 0
  }
}

// 热搜条目为 Map 结构（字段名不固定），第一列按值取词、第二列取热度
const hotWord = (item: HotKeyword): string => String(item.key ?? item.word ?? Object.values(item)[0] ?? '')
const hotScore = (item: HotKeyword): string => {
  const score = item.score ?? item.num ?? Object.values(item)[1]
  return score !== undefined ? String(score) : ''
}

const loadSideData = async () => {
  try {
    loadingSide.value = true
    const [historyRes, hotRes] = await Promise.allSettled([getSearchHistory(), getHotList()])
    if (historyRes.status === 'fulfilled') {
      history.value = historyRes.value.data || []
    }
    if (hotRes.status === 'fulfilled') {
      hotList.value = hotRes.value.data || []
    }
  } finally {
    loadingSide.value = false
  }
}

const doSearch = () => {
  const trimmed = keyword.value.trim()
  if (!trimmed) {
    showToast('请输入搜索关键词', 'info')
    return
  }
  router.push({ path: '/search', query: { q: trimmed } })
}

const searchFrom = (word: string) => {
  keyword.value = word
  doSearch()
}

// 路由 query 驱动搜索（支持分享链接 /search?q=xxx）
const runSearch = async (q: string) => {
  activeKeyword.value = q
  keyword.value = q
  try {
    loading.value = true
    const response = await searchNotes(q)
    results.value = (response.data || []).map(convertResult)
  } catch (error: unknown) {
    const errorMsg = error instanceof Error ? error.message : '搜索失败，请稍后重试'
    showToast(errorMsg, 'error')
    results.value = []
  } finally {
    loading.value = false
  }
}

const handleClearHistory = async () => {
  if (!window.confirm('确定清空全部搜索历史吗？')) {
    return
  }
  try {
    await clearSearchHistory()
    history.value = []
    showToast('搜索历史已清空', 'success')
  } catch (error: unknown) {
    const errorMsg = error instanceof Error ? error.message : '清空失败，请稍后重试'
    showToast(errorMsg, 'error')
  }
}

// 搜索结果回首页打开详情（详情当前为首页弹窗，跳转首页带上 noteId 由首页打开）
const openNote = (item: SearchResultNote) => {
  router.push({ path: '/', query: { noteId: item.id } })
}

watch(
  () => route.query.q,
  (q) => {
    const value = typeof q === 'string' ? q.trim() : ''
    if (value) {
      runSearch(value)
    } else {
      activeKeyword.value = ''
      results.value = []
      loadSideData()
    }
  }
)

onMounted(() => {
  const q = typeof route.query.q === 'string' ? route.query.q.trim() : ''
  if (q) {
    runSearch(q)
  } else {
    loadSideData()
  }
})
</script>

<style scoped>
.search-page {
  min-height: 100vh;
  background-color: #f8f8f8;
}

.search-main {
  margin-left: 220px;
  padding: 80px 32px 48px;
  max-width: 860px;
}

.search-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #fff;
  border-radius: 22px;
  padding: 10px 18px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.search-input {
  flex: 1;
  border: none;
  outline: none;
  font-size: 14px;
  background: transparent;
}

.search-btn {
  border: none;
  border-radius: 16px;
  padding: 6px 20px;
  background-color: #ff2442;
  color: #fff;
  cursor: pointer;
}

.panel {
  background: #fff;
  border-radius: 12px;
  padding: 16px 20px;
  margin-top: 20px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.panel-head h3 {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  color: #333;
  margin: 0 0 12px;
}

.clear-btn {
  display: flex;
  align-items: center;
  gap: 2px;
  border: none;
  background: none;
  color: #999;
  font-size: 12px;
  cursor: pointer;
}

.chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.chip {
  border: none;
  border-radius: 16px;
  padding: 6px 16px;
  background: #f5f5f5;
  color: #555;
  font-size: 13px;
  cursor: pointer;
}

.chip:hover {
  background: #ffeaea;
  color: #ff2442;
}

.hot-list {
  display: flex;
  flex-direction: column;
}

.hot-item {
  display: flex;
  align-items: center;
  gap: 12px;
  border: none;
  background: none;
  padding: 10px 4px;
  cursor: pointer;
  text-align: left;
}

.hot-item:hover {
  background: #fafafa;
}

.hot-rank {
  width: 20px;
  height: 20px;
  border-radius: 6px;
  background: #eee;
  color: #999;
  font-size: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.hot-rank.top {
  background: #ff2442;
  color: #fff;
}

.hot-word {
  flex: 1;
  font-size: 14px;
  color: #333;
}

.hot-score {
  color: #ff2442;
  font-size: 12px;
}

.result-summary {
  color: #666;
  font-size: 14px;
  margin: 16px 4px;
}

.loading-container,
.empty-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 64px 0;
  color: #999;
}

.loading-icon {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.result-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-top: 8px;
}

.result-card {
  display: flex;
  gap: 14px;
  background: #fff;
  border-radius: 12px;
  padding: 14px;
  cursor: pointer;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
}

.result-image {
  width: 110px;
  height: 84px;
  border-radius: 8px;
  object-fit: cover;
  background: #f5f5f5;
  flex-shrink: 0;
}

.result-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.result-title {
  margin: 0 0 6px;
  font-size: 15px;
  color: #333;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.result-content {
  margin: 0 0 10px;
  font-size: 13px;
  color: #999;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.result-meta {
  margin-top: auto;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.author-info {
  display: flex;
  align-items: center;
  gap: 6px;
}

.author-avatar {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  object-fit: cover;
}

.author-name {
  font-size: 12px;
  color: #666;
}

.like-count {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #999;
}
</style>
