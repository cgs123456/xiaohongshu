import axios from 'axios'
import type { InternalAxiosRequestConfig } from 'axios'
import type { ApiResponse } from './types'
import { useUserStore } from '../stores/user'

// 创建 axios 实例
const request = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
    timeout: 15000
})

// 请求去重：key -> AbortController，相同请求进行中再次发起会取消旧请求
const pending = new Map<string, AbortController>()

interface DedupConfig extends InternalAxiosRequestConfig {
    dedupKey?: string
}

const generateDedupKey = (config: InternalAxiosRequestConfig): string => {
    const params = config.params ? JSON.stringify(config.params) : ''
    const data = typeof config.data === 'string' ? config.data : ''
    return `${config.method}:${config.url}:${params}:${data}`
}

// 触发全局"需要重新登录"事件
const emitAuthRequired = (): void => {
    window.dispatchEvent(new CustomEvent('auth:required'))
}

// 请求拦截器
request.interceptors.request.use(
    (config) => {
        const userStore = useUserStore()
        const token = userStore.state.token || localStorage.getItem('token')

        if (token) {
            // 将 token 放在请求头的 token 字段中
            config.headers.token = token

            // 检查 token 是否过期
            const tokenExpiry = userStore.state.tokenExpiry || parseInt(localStorage.getItem('token_expiry') || '0')
            if (tokenExpiry && Date.now() > tokenExpiry) {
                // Token 已过期，清除登录状态并弹出登录框（不再整页刷新）
                userStore.logout()
                emitAuthRequired()
                return Promise.reject(new Error('登录已过期，请重新登录'))
            }
        }

        // 文件上传（FormData）跳过去重
        const isUpload = config.data instanceof FormData
        if (!isUpload) {
            const key = generateDedupKey(config)
            const existing = pending.get(key)
            // 相同请求进行中，取消旧请求
            if (existing) {
                existing.abort()
            }
            const controller = new AbortController()
            config.signal = controller.signal
            pending.set(key, controller)
            ;(config as DedupConfig).dedupKey = key
        }

        return config
    },
    (error) => {
        return Promise.reject(error)
    }
)

// 响应拦截器
request.interceptors.response.use(
    (response) => {
        const key = (response.config as DedupConfig).dedupKey
        if (key) pending.delete(key)

        const res = response.data
        // 统一业务错误处理：code !== 200 直接拒绝
        if (res && typeof res === 'object' && 'code' in res && res.code !== 200) {
            return Promise.reject(new Error(res.message || res.msg || '请求失败'))
        }
        return res
    },
    (error) => {
        const key = error?.config ? (error.config as DedupConfig).dedupKey : undefined
        if (key) pending.delete(key)

        // 请求被去重逻辑取消：静默处理，调用方无需感知
        if (axios.isCancel(error)) {
            return new Promise<never>(() => {})
        }

        // 401 未授权，清除 token 并通知打开登录弹窗（不再整页刷新）
        if (error.response?.status === 401) {
            useUserStore().logout()
            emitAuthRequired()
            return Promise.reject(new Error('未授权，请重新登录'))
        }

        return Promise.reject(error)
    }
)

export interface LoginDto {
    phone: string
    code: string
}

export interface UserVo {
    id?: number
    phone?: string
    username?: string
    nickname?: string
    image?: string
    number?: string
    sex?: string
    birthday?: string
    address?: string
    identity?: string
    school?: string
    time?: string
}

export interface UserInfoResponse {
    user: UserVo
    age?: number | null
}

// 发送验证码（后端为 POST + JSON body）
export const sendVerifyCode = (phone: string) => {
    return request.post<void, ApiResponse<string>>('/user/send', { phone })
}

// 验证登录（后端为 POST + JSON body）
export const verifyLogin = (phone: string, code: string) => {
    return request.post<void, ApiResponse<string>>('/user/verify', { phone, code })
}

// 获取用户信息
export const getUserInfo = () => {
    return request.get<void, ApiResponse<UserInfoResponse>>('/user/getInfo')
}

// 关注/取消关注用户（后端为 POST）
export const toggleFollow = (userId: number) => {
    return request.post<void, ApiResponse<void>>(`/user/attention/${userId}`)
}

// 检查是否已关注用户
export const checkIsFollowed = (userId: number) => {
    return request.get<void, ApiResponse<number>>(`/user/isAttention/${userId}`)
}

export default request
