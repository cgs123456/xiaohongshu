import request from './auth'
import type { ApiResponse } from './types'
import type { NoteVo } from './note'

// 搜索历史记录
export interface SearchHistory {
    id: string
    history: string
    time?: string
}

// 热搜词条目：后端返回 Map 结构，字段名不固定（key/score 或 word/num），
// 渲染时按 Object.values 取第一/第二列展示
export type HotKeyword = Record<string, string | number>

// 搜索笔记（key 需 URL 编码，搜索后后端会记录历史）
export const searchNotes = (key: string) => {
    return request.get<void, ApiResponse<NoteVo[]>>(`/search/search/${encodeURIComponent(key)}`)
}

// 获取搜索历史
export const getSearchHistory = () => {
    return request.get<void, ApiResponse<SearchHistory[]>>('/search/getHistoryList')
}

// 清空搜索历史
export const clearSearchHistory = () => {
    return request.delete<void, ApiResponse<void>>('/search/deleteHistory')
}

// 获取热搜榜
export const getHotList = () => {
    return request.get<void, ApiResponse<HotKeyword[]>>('/search/getHotList')
}
