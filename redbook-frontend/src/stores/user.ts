import { reactive, computed } from 'vue'
import { websocketManager } from '../utils/websocket'
import type { UserVo } from '../api/auth'

const TOKEN_KEY = 'token'
const TOKEN_EXPIRY_KEY = 'token_expiry'
const USER_KEY = 'user_info'

interface UserState {
    token: string
    userInfo: UserVo | null
    tokenExpiry: number
}

// 模块级单例状态（替代 pinia 的轻量实现）
const state = reactive<UserState>({
    token: '',
    userInfo: null,
    tokenExpiry: 0
})

// 是否已登录（token 存在且未过期）
const isLoggedIn = computed(() => {
    return !!state.token && (!state.tokenExpiry || Date.now() < state.tokenExpiry)
})

// 登录成功：写入 localStorage + 内存状态
function login(token: string, userInfo: UserVo | null, expiry: number): void {
    state.token = token
    state.userInfo = userInfo
    state.tokenExpiry = expiry

    localStorage.setItem(TOKEN_KEY, token)
    localStorage.setItem(TOKEN_EXPIRY_KEY, expiry.toString())
    if (userInfo) {
        localStorage.setItem(USER_KEY, JSON.stringify(userInfo))
    } else {
        localStorage.removeItem(USER_KEY)
    }
}

// 补充/更新用户信息（登录后异步获取用户资料时使用）
function setUserInfo(userInfo: UserVo): void {
    state.userInfo = userInfo
    localStorage.setItem(USER_KEY, JSON.stringify(userInfo))
}

// 登出：清空 localStorage + 内存状态 + 关闭 WebSocket
function logout(): void {
    state.token = ''
    state.userInfo = null
    state.tokenExpiry = 0

    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(TOKEN_EXPIRY_KEY)
    localStorage.removeItem(USER_KEY)

    websocketManager.close()
}

// 应用启动时从 localStorage 恢复登录状态
function restore(): void {
    const token = localStorage.getItem(TOKEN_KEY)
    const expiryStr = localStorage.getItem(TOKEN_EXPIRY_KEY)
    const expiry = expiryStr ? parseInt(expiryStr) : 0

    // token 已过期则清除
    if (!token || (expiry && Date.now() > expiry)) {
        if (token) {
            localStorage.removeItem(TOKEN_KEY)
            localStorage.removeItem(TOKEN_EXPIRY_KEY)
            localStorage.removeItem(USER_KEY)
        }
        return
    }

    state.token = token
    state.tokenExpiry = expiry

    const userStr = localStorage.getItem(USER_KEY)
    if (userStr) {
        try {
            state.userInfo = JSON.parse(userStr) as UserVo
        } catch {
            localStorage.removeItem(USER_KEY)
        }
    }
}

export function useUserStore() {
    return {
        state,
        isLoggedIn,
        login,
        setUserInfo,
        logout,
        restore
    }
}
