<template>
  <div class="cart-page">
    <!-- 顶部导航栏 -->
    <AppHeader />

    <!-- 侧边栏 -->
    <AppSidebar />

    <!-- 主内容区 -->
    <main class="main-content">
      <div class="cart-container">
        <h2 class="cart-title">
          <Icon icon="mdi:cart-outline" width="24" />
          我的购物车
        </h2>

        <!-- 加载中 -->
        <div v-if="loading" class="loading-container">
          <Icon icon="mdi:loading" class="loading-icon" width="48" />
          <p>加载中...</p>
        </div>

        <!-- 空购物车 -->
        <div v-else-if="cartItems.length === 0" class="empty-container">
          <Icon icon="mdi:cart-off" width="64" />
          <p>购物车空空如也，快去逛逛吧~</p>
          <button class="go-shopping-btn" @click="router.push('/shopping')">去逛逛</button>
        </div>

        <!-- 购物车列表 -->
        <div v-else class="cart-list">
          <div v-for="item in cartItems" :key="item.id" class="cart-item">
            <input
              type="checkbox"
              class="item-check"
              :checked="selectedIds.has(item.id)"
              @change="toggleSelect(item.id)"
            />
            <img :src="item.productImage" :alt="item.productName" class="item-image" />
            <div class="item-info">
              <h4 class="item-name">{{ item.productName }}</h4>
              <p v-if="item.attributes" class="item-attrs">{{ item.attributes }}</p>
              <div class="item-price">¥{{ item.productPrice }}</div>
            </div>
            <div class="quantity-control">
              <button
                class="qty-btn"
                :disabled="item.quantity <= 1 || updatingId === item.id"
                @click="changeQuantity(item, -1)"
              >
                <Icon icon="mdi:minus" width="16" />
              </button>
              <span class="qty-value">{{ item.quantity }}</span>
              <button class="qty-btn" :disabled="updatingId === item.id" @click="changeQuantity(item, 1)">
                <Icon icon="mdi:plus" width="16" />
              </button>
            </div>
            <div class="item-subtotal">¥{{ (item.productPrice * item.quantity).toFixed(2) }}</div>
            <button class="remove-btn" :disabled="updatingId === item.id" @click="removeItem(item)">
              <Icon icon="mdi:delete-outline" width="20" />
            </button>
          </div>

          <!-- 底部结算栏 -->
          <div class="cart-footer">
            <button class="clear-btn" @click="handleClearCart">清空购物车</button>
            <div class="footer-right">
              <span class="total-label">
                已勾选 {{ selectedCount }} 件，合计：
                <span class="total-price">¥{{ selectedTotalPrice.toFixed(2) }}</span>
              </span>
              <button class="checkout-btn" :disabled="selectedCount === 0" @click="openCheckout">
                去结算
              </button>
            </div>
          </div>
        </div>
      </div>
    </main>

    <!-- 结算弹层 -->
    <CheckoutModal
      v-model:visible="checkoutVisible"
      :sku-ids="selectedSkuIds"
      :amount="selectedTotalPrice"
      :item-count="selectedCount"
      @success="handleCheckoutSuccess"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import AppHeader from '../components/AppHeader.vue'
import AppSidebar from '../components/AppSidebar.vue'
import { getCartList, updateCartQuantity, removeFromCart, clearCart, type Cart } from '../api/product'
import { useToast } from '../composables/useToast'

const router = useRouter()
const { showToast } = useToast()

import CheckoutModal from '../components/CheckoutModal.vue'

const cartItems = ref<Cart[]>([])
const loading = ref(false)
const updatingId = ref<number | null>(null)

// 勾选与结算状态（默认全选）
const selectedIds = ref<Set<number>>(new Set())
const checkoutVisible = ref(false)

const selectedItems = computed(() => cartItems.value.filter(i => selectedIds.value.has(i.id)))
const selectedCount = computed(() => selectedItems.value.reduce((sum, item) => sum + item.quantity, 0))
const selectedTotalPrice = computed(() =>
  selectedItems.value.reduce((sum, item) => sum + item.productPrice * item.quantity, 0)
)
const selectedSkuIds = computed(() => selectedItems.value.map(i => i.skuId).filter((id): id is number => id != null))

// 勾选/取消勾选
const toggleSelect = (id: number) => {
  const next = new Set(selectedIds.value)
  if (next.has(id)) {
    next.delete(id)
  } else {
    next.add(id)
  }
  selectedIds.value = next
}

// 打开结算弹层
const openCheckout = () => {
  if (selectedItems.value.length === 0) {
    showToast('请先勾选要结算的商品', 'info')
    return
  }
  checkoutVisible.value = true
}

// 结算成功：清理已结算项（后端已删，本地同步）
const handleCheckoutSuccess = () => {
  cartItems.value = cartItems.value.filter(i => !selectedIds.value.has(i.id))
  selectedIds.value = new Set()
}

// 加载购物车列表
const loadCart = async () => {
  try {
    loading.value = true
    const response = await getCartList()
    cartItems.value = response.data || []
    // 默认全选
    selectedIds.value = new Set(cartItems.value.map(i => i.id))
  } catch (error: unknown) {
    const errorMsg = error instanceof Error ? error.message : '加载购物车失败'
    showToast(errorMsg, 'error')
  } finally {
    loading.value = false
  }
}

// 修改数量
const changeQuantity = async (item: Cart, delta: number) => {
  const newQuantity = item.quantity + delta
  if (newQuantity < 1) return

  try {
    updatingId.value = item.id
    await updateCartQuantity(item.id, newQuantity)
    item.quantity = newQuantity
  } catch (error: unknown) {
    const errorMsg = error instanceof Error ? error.message : '更新数量失败'
    showToast(errorMsg, 'error')
  } finally {
    updatingId.value = null
  }
}

// 删除商品
const removeItem = async (item: Cart) => {
  try {
    updatingId.value = item.id
    await removeFromCart(item.id)
    cartItems.value = cartItems.value.filter(i => i.id !== item.id)
    showToast('已移除该商品', 'success')
  } catch (error: unknown) {
    const errorMsg = error instanceof Error ? error.message : '删除失败'
    showToast(errorMsg, 'error')
  } finally {
    updatingId.value = null
  }
}

// 清空购物车
const handleClearCart = async () => {
  if (!window.confirm('确定清空购物车吗？')) {
    return
  }
  try {
    await clearCart()
    cartItems.value = []
    showToast('购物车已清空', 'success')
  } catch (error: unknown) {
    const errorMsg = error instanceof Error ? error.message : '清空失败'
    showToast(errorMsg, 'error')
  }
}

onMounted(() => {
  loadCart()
})
</script>

<style scoped>
.cart-page {
  min-height: 100vh;
  background: #f8f8f8;
}

.main-content {
  margin-left: 220px;
  padding-top: 88px;
  min-height: 100vh;
}

.cart-container {
  max-width: 900px;
  margin: 0 auto;
  padding: 24px;
}

.cart-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 20px;
  font-weight: 700;
  color: #333;
  margin: 0 0 20px 0;
}

/* 加载和空状态 */
.loading-container,
.empty-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 20px;
  color: #999;
  background: white;
  border-radius: 16px;
}

.loading-container p,
.empty-container p {
  margin-top: 16px;
  font-size: 14px;
}

.loading-icon {
  animation: spin 1s linear infinite;
  color: #ff2442;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.go-shopping-btn {
  margin-top: 16px;
  padding: 10px 32px;
  background: linear-gradient(135deg, #ff2442 0%, #ff4d6d 100%);
  color: white;
  border: none;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
}

/* 购物车列表 */
.cart-list {
  background: white;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.cart-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 20px;
  border-bottom: 1px solid #f7f7f7;
}

.item-image {
  width: 80px;
  height: 80px;
  border-radius: 8px;
  object-fit: cover;
  background: #f5f5f5;
  flex-shrink: 0;
}

.item-info {
  flex: 1;
  min-width: 0;
}

.item-attrs {
  margin: 2px 0 0;
  font-size: 12px;
  color: #999;
}

.item-name {
  font-size: 14px;
  font-weight: 500;
  color: #333;
  margin: 0 0 8px 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-price {
  font-size: 14px;
  color: #ff2442;
  font-weight: 600;
}

.quantity-control {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.qty-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: 1px solid #e5e5e5;
  background: white;
  border-radius: 6px;
  cursor: pointer;
  color: #666;
  transition: all 0.2s;
}

.qty-btn:hover:not(:disabled) {
  border-color: #ff2442;
  color: #ff2442;
}

.qty-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.qty-value {
  min-width: 32px;
  text-align: center;
  font-size: 14px;
  color: #333;
}

.item-subtotal {
  width: 90px;
  text-align: right;
  font-size: 15px;
  font-weight: 600;
  color: #ff2442;
  flex-shrink: 0;
}

.remove-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 8px;
  border: none;
  background: transparent;
  color: #999;
  cursor: pointer;
  border-radius: 8px;
  transition: all 0.2s;
  flex-shrink: 0;
}

.remove-btn:hover:not(:disabled) {
  color: #ff2442;
  background: #fff0f0;
}

/* 底部结算栏 */
.cart-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
}

.clear-btn {
  padding: 8px 16px;
  background: white;
  border: 1px solid #e5e5e5;
  border-radius: 16px;
  font-size: 13px;
  color: #666;
  cursor: pointer;
  transition: all 0.2s;
}

.clear-btn:hover {
  border-color: #ff2442;
  color: #ff2442;
}

.total-label {
  font-size: 14px;
  color: #666;
}

.total-price {
  font-size: 20px;
  font-weight: 700;
  color: #ff2442;
}

/* 响应式 */
@media (max-width: 1024px) {
  .main-content {
    margin-left: 80px;
  }
}

@media (max-width: 768px) {
  .main-content {
    margin-left: 0;
  }

  .item-subtotal {
    display: none;
  }
}
</style>
