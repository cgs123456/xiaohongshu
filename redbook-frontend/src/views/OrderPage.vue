<template>
  <div class="order-page">
    <AppHeader />
    <AppSidebar />

    <main class="order-main">
      <h2 class="page-title">我的订单</h2>

      <!-- 加载态（首屏） -->
      <div v-if="loading && orders.length === 0" class="loading-container">
        <Icon icon="mdi:loading" class="loading-icon" width="48" />
        <p>加载中...</p>
      </div>

      <!-- 空状态 -->
      <div v-else-if="orders.length === 0 && !loadFailed" class="empty-container">
        <Icon icon="mdi:file-document-outline" width="64" />
        <p>暂无订单，去逛逛吧</p>
        <button class="go-shopping-btn" @click="router.push('/shopping')">去购物</button>
      </div>

      <!-- 错误态 -->
      <div v-else-if="loadFailed && orders.length === 0" class="empty-container">
        <Icon icon="mdi:alert-circle-outline" width="64" />
        <p>加载失败，请稍后重试</p>
        <button class="go-shopping-btn" @click="retryLoad">重新加载</button>
      </div>

      <!-- 订单列表 -->
      <template v-else>
        <div v-for="vo in orders" :key="vo.order.id" class="order-card">
          <div class="order-head">
            <span class="order-no">订单号 {{ shortOrderNo(vo.order.id) }}</span>
            <span :class="['status-badge', `status-${vo.order.status}`]">{{ statusText(vo.order.status) }}</span>
          </div>

          <!-- 商品明细 -->
          <div class="order-items">
            <div v-for="(item, idx) in displayItems(vo)" :key="idx" class="order-item">
              <img class="item-image" :src="item.productImage || defaultImage" :alt="item.productName" loading="lazy" />
              <div class="item-info">
                <p class="item-name">{{ item.productName }}</p>
                <p v-if="item.attributes" class="item-attrs">{{ item.attributes }}</p>
                <p class="item-price">¥{{ Number(item.unitPrice).toFixed(2) }} × {{ item.quantity }}</p>
              </div>
            </div>
          </div>

          <div class="order-amount">
            <span>合计 ¥{{ Number(vo.order.totalPrice ?? vo.order.finalPrice).toFixed(2) }}</span>
            <span v-if="Number(vo.order.discountPrice) > 0" class="discount">
              优惠 -¥{{ Number(vo.order.discountPrice).toFixed(2) }}
            </span>
            <span class="final">实付 ¥{{ Number(vo.order.finalPrice).toFixed(2) }}</span>
          </div>

          <div class="order-foot">
            <div class="receiver">
              <Icon icon="mdi:map-marker-outline" width="14" />
              <span>{{ vo.order.receiverName }} {{ vo.order.receiverPhone }} {{ vo.order.address }}</span>
            </div>
            <div class="foot-right">
              <span class="time">{{ vo.order.createTime || '' }}</span>
              <button
                v-if="vo.order.status === 0"
                class="pay-btn"
                :disabled="payingId === vo.order.id"
                @click="handlePay(vo.order.id)"
              >
                {{ payingId === vo.order.id ? '支付中...' : '立即支付' }}
              </button>
            </div>
          </div>
        </div>

        <!-- 加载更多 -->
        <div v-if="hasMore" class="load-more">
          <button class="load-more-btn" :disabled="loading" @click="loadOrders">
            {{ loading ? '加载中...' : '加载更多' }}
          </button>
        </div>
        <p v-else-if="orders.length > 0" class="no-more">没有更多订单了</p>
      </template>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import AppHeader from '../components/AppHeader.vue'
import AppSidebar from '../components/AppSidebar.vue'
import { getOrderList, payOrder, type OrderVo, type OrderItem } from '../api/order'
import { useToast } from '../composables/useToast'

const router = useRouter()
const { showToast } = useToast()

const orders = ref<OrderVo[]>([])
const loading = ref(false)
const loadFailed = ref(false)
const hasMore = ref(true)
const currentPage = ref(1)
const pageSize = 10
const payingId = ref<string | null>(null)

const defaultImage = 'https://picsum.photos/seed/product/100/100'

// 状态文案
const statusText = (status: number): string => {
  const map: Record<number, string> = { 0: '待支付', 1: '已支付', 2: '已发货', 3: '已完成', 4: '已取消' }
  return map[status] ?? '未知'
}

// 订单号截断展示（雪花 id 过长）
const shortOrderNo = (id: string): string => {
  if (!id) return '—'
  return id.length > 12 ? `${id.slice(0, 6)}...${id.slice(-4)}` : id
}

// 明细展示：新订单用 items；旧单（items 为空）用主表遗留字段映射
const displayItems = (vo: OrderVo): OrderItem[] => {
  if (vo.items && vo.items.length > 0) {
    return vo.items
  }
  if (vo.order.productId != null) {
    return [{
      orderId: vo.order.id,
      productId: vo.order.productId,
      productName: `商品 #${vo.order.productId}`,
      productImage: '',
      unitPrice: vo.order.finalPrice,
      quantity: vo.order.quantity ?? 1
    }]
  }
  return []
}

// 加载订单列表（追加模式）
const loadOrders = async () => {
  if (loading.value || !hasMore.value) return
  const page = currentPage.value
  try {
    loading.value = true
    const response = await getOrderList(page, pageSize)
    const list = response.data || []
    orders.value = page === 1 ? list : [...orders.value, ...list]
    if (list.length < pageSize) {
      hasMore.value = false
    } else {
      currentPage.value = page + 1
    }
    loadFailed.value = false
  } catch (error: unknown) {
    const errorMsg = error instanceof Error ? error.message : '网络错误，请稍后重试'
    showToast(errorMsg, 'error')
    if (orders.value.length === 0) {
      loadFailed.value = true
    }
  } finally {
    loading.value = false
  }
}

const retryLoad = () => {
  loadFailed.value = false
  currentPage.value = 1
  hasMore.value = true
  loadOrders()
}

// 发起支付：同步渠道（模拟）直接完成；跳转渠道返回支付 URL 由浏览器跳转
const handlePay = async (orderId: string) => {
  try {
    payingId.value = orderId
    const resp = await payOrder(orderId)
    const payUrl = resp?.data
    if (payUrl) {
      window.open(payUrl, '_blank')
      showToast('已跳转支付页面，完成后请刷新查看', 'info')
      return
    }
    showToast('支付成功', 'success')
    // 刷新当前订单状态（整页重置到第一页，保持简单）
    currentPage.value = 1
    hasMore.value = true
    loadOrders()
  } catch (error: unknown) {
    const errorMsg = error instanceof Error ? error.message : '支付失败，请稍后重试'
    showToast(errorMsg, 'error')
  } finally {
    payingId.value = null
  }
}

onMounted(() => {
  loadOrders()
})
</script>

<style scoped>
.order-page {
  min-height: 100vh;
  background-color: #f8f8f8;
}

.order-main {
  margin-left: 220px;
  padding: 80px 32px 48px;
  max-width: 960px;
}

.page-title {
  font-size: 20px;
  color: #333;
  margin: 0 0 20px;
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

.go-shopping-btn {
  padding: 8px 24px;
  border: none;
  border-radius: 18px;
  background-color: #ff2442;
  color: #fff;
  cursor: pointer;
}

.order-card {
  background: #fff;
  border-radius: 12px;
  padding: 16px 20px;
  margin-bottom: 16px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
}

.order-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;
}

.order-no {
  color: #666;
  font-size: 13px;
}

.status-badge {
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 10px;
}

.status-0 { background: #ffeaea; color: #ff2442; }
.status-1 { background: #e8f7e8; color: #2e9e44; }
.status-2 { background: #e8f0fb; color: #2f6fdd; }
.status-3, .status-4 { background: #f0f0f0; color: #888; }

.order-items {
  padding: 12px 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.order-item {
  display: flex;
  gap: 12px;
  align-items: center;
}

.item-image {
  width: 56px;
  height: 56px;
  border-radius: 8px;
  object-fit: cover;
  background: #f5f5f5;
}

.item-name {
  margin: 0 0 4px;
  font-size: 14px;
  color: #333;
}

.item-attrs {
  margin: 2px 0;
  font-size: 12px;
  color: #999;
}

.item-price {
  margin: 0;
  font-size: 12px;
  color: #999;
}

.order-amount {
  display: flex;
  gap: 16px;
  justify-content: flex-end;
  padding: 8px 0 12px;
  font-size: 13px;
  color: #666;
}

.order-amount .final {
  color: #ff2442;
  font-weight: 600;
}

.order-amount .discount {
  color: #2e9e44;
}

.order-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  border-top: 1px solid #f0f0f0;
  padding-top: 12px;
}

.receiver {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #999;
  font-size: 12px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  flex: 1;
}

.foot-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.time {
  color: #bbb;
  font-size: 12px;
}

.pay-btn {
  padding: 6px 18px;
  border: none;
  border-radius: 16px;
  background-color: #ff2442;
  color: #fff;
  cursor: pointer;
}

.pay-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.load-more {
  text-align: center;
  padding: 12px 0;
}

.load-more-btn {
  padding: 8px 32px;
  border: 1px solid #ddd;
  border-radius: 18px;
  background: #fff;
  color: #666;
  cursor: pointer;
}

.no-more {
  text-align: center;
  color: #bbb;
  font-size: 12px;
  padding: 12px 0;
}
</style>
