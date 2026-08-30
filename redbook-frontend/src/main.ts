import { createApp } from 'vue'
import './style.css'
import App from './App.vue'
import router from './router'
import { websocketManager } from './utils/websocket'
import { useUserStore } from './stores/user'

const app = createApp(App)

// 启动时先从 localStorage 恢复登录状态
const userStore = useUserStore()
userStore.restore()

app.use(router)

// 路由准备就绪后，仅在已登录的情况下初始化 WebSocket
router.isReady().then(() => {
    if (userStore.isLoggedIn) {
        websocketManager.connect()
    }
})

app.mount('#app')
