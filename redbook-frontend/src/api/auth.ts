import axios from 'axios'
import type { InternalAxiosRequestConfig } from 'axios'
import type { ApiResponse } from './types'
import { useUserStore } from '../stores/user'

// 创建 axios 实例
const request = axios.create({
    // 相对路径兜底：dev 由 .env.development 的 /api 走 vite 代理，生产由 .env.production 指定；
    // 此前默认 http://localhost:8080 是 user 服务端口而非网关，未设 env 时会全部打错目标
    baseURL: import.meta.env.VITE_API_BASE_URL || '',
    timeout: 15000
})

// 请求去重：key -> 进行中的请求。相同 key 的新请求会取消旧请求；
// 被取消请求的调用方不会挂起，而是等待最新请求完成后拿到相同结果
interface Deferred {
    resolve: (value: any) => void
    reject: (reason?: any) => void
}

interface PendingEntry {
    controller: AbortController
    // 被取消的旧请求的调用方，随最新请求一起 settle
    deferreds: Deferred[]
}

const pending = new Map<string, PendingEntry>()

const makeDeferred = (): Deferred & { promise: Promise<any> } => {
    let resolve!: (v: any) => void
    let reject!: (e: any) => void
    const promise = new Promise<any>((res, rej) => {
        resolve = res
        reject = rej
    })
    return { promise, resolve, reject }
}

interface DedupConfig extends InternalAxiosRequestConfig {
    dedupKey?: string
}

const generateDedupKey = (config: InternalAxiosRequestConfig): string => {
    const params = config.params ? JSON.stringify(config.params) : ''
    // 拦截器阶段 data 还是对象（axios 在其后才序列化），必须 stringify 才能区分不同 body
    const data = config.data
        ? (typeof config.data === 'string' ? config.data : JSON.stringify(config.data))
        : ''
    return `${config.method}:${config.url}:${params}:${data}`
}

/** 让所有被取消的旧调用方随最新请求一起完成 */
const settleFollowers = (key: string | undefined, value: any, error: any): void => {
    if (!key) return
    const entry = pending.get(key)
    if (!entry) return
    pending.delete(key)
    for (const d of entry.deferreds) {
        if (error) {
            d.reject(error)
        } else {
            d.resolve(value)
        }
    }
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
            // 相同请求进行中：取消旧请求，其调用方转入新请求
            if (existing) {
                existing.controller.abort()
            }
            const entry: PendingEntry = {
                controller: new AbortController(),
                deferreds: existing ? existing.deferreds : []
            }
            config.signal = entry.controller.signal
            pending.set(key, entry)
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
        const res = response.data
        // 统一业务错误处理：code !== 200 直接拒绝（被取消的旧调用方收到相同错误）
        if (res && typeof res === 'object' && 'code' in res && res.code !== 200) {
            const bizError = new Error(res.message || res.msg || '请求失败')
            settleFollowers(key, null, bizError)
            return Promise.reject(bizError)
        }
        settleFollowers(key, res, null)
        return res
    },
    (error) => {
        const key = error?.config ? (error.config as DedupConfig).dedupKey : undefined

        // 请求被去重逻辑取消：调用方挂到当前最新的同 key 请求上，等待其完成拿到相同结果
        if (axios.isCancel(error)) {
            const entry = key ? pending.get(key) : undefined
            if (entry) {
                const d = makeDeferred()
                entry.deferreds.push(d)
                return d.promise
            }
            return new Promise<never>(() => {})
        }

        // 401 未授权，清除 token 并通知打开登录弹窗（不再整页刷新）
        if (error.response?.status === 401) {
            useUserStore().logout()
            emitAuthRequired()
            const authError = new Error('未授权，请重新登录')
            settleFollowers(key, null, authError)
            return Promise.reject(authError)
        }

        settleFollowers(key, null, error)
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
