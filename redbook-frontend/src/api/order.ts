import request from './auth'
import type { ApiResponse } from './types'

// 订单主体（id 为雪花 id，字符串传输避免精度丢失）
export interface Order {
    id: string
    status: number // 0待支付 1已支付 2已发货 3已完成 4已取消
    totalPrice: number
    discountPrice: number
    finalPrice: number
    couponId?: string | null
    receiverName?: string
    receiverPhone?: string
    address?: string
    payTime?: string | null
    createTime?: string
    // 旧单遗留的单商品字段（新订单明细在 items 中）
    productId?: number | null
    quantity?: number | null
}

// 订单商品明细
export interface OrderItem {
    orderId: string
    productId: number
    productName?: string
    productImage?: string
    unitPrice: number
    quantity: number
}

export interface OrderVo {
    order: Order
    items: OrderItem[]
}

// 获取订单列表（分页可选，后端默认 1/20）
export const getOrderList = (page?: number, pageSize?: number) => {
    const params: Record<string, number> = {}
    if (page !== undefined) params.page = page
    if (pageSize !== undefined) params.pageSize = pageSize
    return request.get<void, ApiResponse<OrderVo[]>>('/order/getOrderList', { params })
}

// 模拟支付（orderId 为字符串路径参数）
export const payOrder = (orderId: string) => {
    return request.post<void, ApiResponse<void>>(`/order/pay/${orderId}`)
}
