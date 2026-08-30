import request from './auth'
import type { ApiResponse } from './types'

export interface Product {
    id: number
    name: string
    type: string
    description: string
    brand: string
    price: number
    image: string
    time: string
    sales: number
    userId: number
    shopId: number
    stock: number
}

export interface CustomAttribute {
    label: string
    value: string[]
}

export interface ProductVo {
    product: Product
    shop?: {
        id?: number
        name?: string
    }
    customAttributes?: CustomAttribute[]
}

export interface Coupon {
    id: number
    name: string
    type: string
    discount: number
    startTime: string
    endTime: string
    stock: number
}

export interface CouponVo {
    coupon: Coupon
    isUsable: boolean
}

export interface BuyDto {
    productId: number
    selectAttributes: Array<{
        label: string
        value: string[]
    }>
    userId?: number
    couponId?: number
}

// 获取商品列表（后端 ProductController.getProductList 期望 page / pageSize 两个 @RequestParam）
export const getProductList = (page?: number, pageSize?: number) => {
    const params: Record<string, number> = {}
    if (page !== undefined) params.page = page
    if (pageSize !== undefined) params.pageSize = pageSize
    return request.get<void, ApiResponse<Product[]>>('/product/getProductList', { params })
}

// 获取商品详情
export const getProduct = (productId: number) => {
    return request.get<void, ApiResponse<ProductVo>>(`/product/getProduct/${productId}`)
}

// 获取店铺商品列表
export const getProductsByShop = (productId: number) => {
    return request.get<void, ApiResponse<Product[]>>(`/product/getProductsByShop/${productId}`)
}

// 推荐商品（后端返回库存>0 的商品，按 id 倒序前 10 个）
export const getRecommendProducts = () => {
    return request.get<void, ApiResponse<Product[]>>('/product/recommend')
}

// 立即购买
export const buyProduct = (buyDto: BuyDto) => {
    return request.post<void, ApiResponse<void>>('/product/buyProduct', buyDto)
}

// 获取用户优惠券列表
export const getCouponsByUserId = () => {
    return request.get<void, ApiResponse<CouponVo[]>>('/product/getCouponsByUserId')
}

// 使用优惠券
export const useCoupon = (couponId: number) => {
    return request.post<void, ApiResponse<void>>(`/product/useCoupon/${couponId}`)
}

// AI 购物助手多轮对话（后端 AgentChatRequest{conversationId, message}，
// 带 conversationId 可维持多轮上下文；不传则后端每条消息独立成新会话）
let agentConversationId = ''
export const chatWithAgent = (message: string) => {
    if (!agentConversationId) {
        agentConversationId = Date.now().toString(36) + Math.random().toString(36).slice(2, 8)
    }
    return request.post<void, ApiResponse<string>>('/product/agent/chat', {
        conversationId: agentConversationId,
        message
    })
}

// 购物车相关接口
export interface Cart {
    id: number
    userId: number
    productId: number
    productName: string
    productImage: string
    productPrice: number
    quantity: number
    createTime: string
    updateTime: string
}

// 添加商品到购物车
export const addToCart = (productId: number, quantity: number = 1) => {
    return request.post<void, ApiResponse<void>>('/cart/addToCart', null, {
        params: { productId, quantity }
    })
}

// 获取用户购物车列表
export const getCartList = () => {
    return request.get<void, ApiResponse<Cart[]>>('/cart/getCartList')
}

// 更新购物车商品数量
export const updateCartQuantity = (cartId: number, quantity: number) => {
    return request.put<void, ApiResponse<void>>('/cart/updateQuantity', null, {
        params: { cartId, quantity }
    })
}

// 删除购物车商品
export const removeFromCart = (cartId: number) => {
    return request.delete<void, ApiResponse<void>>(`/cart/remove/${cartId}`)
}

// 清空购物车
export const clearCart = () => {
    return request.delete<void, ApiResponse<void>>('/cart/clear')
}
