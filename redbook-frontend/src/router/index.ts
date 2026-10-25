import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
    {
        path: '/',
        name: 'Home',
        component: () => import('../views/HomePage.vue'),
        meta: { title: '首页' }
    },
    {
        path: '/notifications',
        name: 'Notifications',
        component: () => import('../views/NotificationPage.vue'),
        meta: { title: '通知', requiresAuth: true }
    },
    {
        path: '/shopping',
        name: 'Shopping',
        component: () => import('../views/ShoppingPage.vue'),
        meta: { title: '购物', requiresAuth: true }
    },
    {
        path: '/cart',
        name: 'Cart',
        component: () => import('../views/CartPage.vue'),
        meta: { title: '购物车', requiresAuth: true }
    },
    {
        path: '/orders',
        name: 'Orders',
        component: () => import('../views/OrderPage.vue'),
        meta: { title: '我的订单', requiresAuth: true }
    },
    {
        path: '/search',
        name: 'Search',
        component: () => import('../views/SearchPage.vue'),
        meta: { title: '搜索' }
    },
    {
        path: '/profile',
        name: 'Profile',
        component: () => import('../views/ProfilePage.vue'),
        meta: { title: '个人主页', requiresAuth: true }
    },
    {
        path: '/:pathMatch(.*)*',
        name: 'NotFound',
        component: () => import('../views/NotFound.vue'),
        meta: { title: '页面不存在' }
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

// 全局前置守卫：基于 meta.requiresAuth 判断是否需要登录
router.beforeEach((to, _from, next) => {
    const token = localStorage.getItem('token')
    if (to.meta.requiresAuth && !token) {
        next('/')
    } else {
        next()
    }
})

// 全局后置钩子：设置页面标题
router.afterEach((to) => {
    const title = to.meta.title as string | undefined
    document.title = title ? `小红书 - ${title}` : '小红书'
})

export default router
