<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="visible" class="modal-overlay" @click="handleClose">
        <div class="modal-container" @click.stop>
          <div class="modal-header">
            <h3>确认购买</h3>
          </div>

          <div class="modal-body">
            <!-- 商品信息 -->
            <div class="product-summary" v-if="product">
              <img :src="product.image" :alt="product.name" class="product-thumb" />
              <div class="product-info">
                <h4 class="product-name">{{ product.name }}</h4>
                <div class="product-price">
                  <span class="price-symbol">¥</span>
                  <span class="price-value">{{ product.price }}</span>
                </div>
              </div>
            </div>

            <!-- 属性选择（选中值随单快照落订单明细） -->
            <div class="attributes-section" v-if="attributes && attributes.length > 0">
              <div
                v-for="(attr, index) in attributes"
                :key="index"
                class="attribute-group"
              >
                <div class="attribute-label">{{ attr.label }}</div>
                <div class="attribute-options">
                  <button
                    v-for="(option, optIndex) in attr.value"
                    :key="optIndex"
                    class="attribute-option"
                    :class="{ active: isAttributeSelected(index, option) }"
                    @click="selectAttribute(index, option)"
                  >
                    {{ option }}
                  </button>
                </div>
              </div>
            </div>

            <!-- 价格汇总 -->
            <div class="price-summary" v-if="product">
              <div class="summary-row">
                <span class="summary-label">商品金额</span>
                <span class="summary-value">¥{{ product.price }}</span>
              </div>
            </div>

            <!-- 提示信息 -->
            <div class="tip-text" v-if="attributes && attributes.length > 0 && !allAttributesSelected">
              <Icon icon="mdi:information-outline" width="16" />
              请选择所有商品属性
            </div>
          </div>

          <div class="modal-footer">
            <button class="btn btn-cancel" @click="handleClose">取消</button>
            <button
              class="btn btn-confirm"
              :disabled="!product || !allAttributesSelected || loading"
              @click="handleConfirm"
            >
              <span v-if="loading">购买中...</span>
              <span v-else>确认购买</span>
            </button>
          </div>
        </div>
      </div>
    </Transition>

    <!-- 结算弹层（收货信息与优惠券在结算时选择；选中属性随单落订单明细） -->
    <CheckoutModal
      v-model:visible="checkoutVisible"
      :sku-ids="checkoutSkuIds"
      :amount="checkoutAmount"
      :item-count="1"
      :attributes="checkoutAttributes"
      @success="handleCheckoutSuccess"
    />
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { Icon } from '@iconify/vue'
import { addToCart, type CustomAttribute } from '../api/product'
import CheckoutModal from './CheckoutModal.vue'
import { useToast } from '../composables/useToast'

const { showToast } = useToast()

interface Product {
  id: number
  name: string
  price: number
  image: string
}

interface Props {
  visible: boolean
  product: Product | null
  attributes: CustomAttribute[] | null
}

interface Emits {
  (e: 'update:visible', value: boolean): void
  (e: 'success'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const selectedAttributes = ref<Map<number, string>>(new Map())
const loading = ref(false)
const checkoutVisible = ref(false)
const checkoutSkuIds = ref<number[]>([])
const checkoutAmount = ref(0)
const checkoutAttributes = ref<Record<string, string>>({})

// 监听弹窗打开，重置选择
watch(() => props.visible, (newVisible) => {
  if (newVisible) {
    selectedAttributes.value.clear()
    checkoutAmount.value = 0
  }
})

// 选择属性
const selectAttribute = (attrIndex: number, option: string) => {
  selectedAttributes.value.set(attrIndex, option)
}

// 判断属性是否被选中
const isAttributeSelected = (attrIndex: number, option: string) => {
  return selectedAttributes.value.get(attrIndex) === option
}

// 检查是否所有属性都已选择
const allAttributesSelected = computed(() => {
  if (!props.attributes || props.attributes.length === 0) {
    return true
  }
  return selectedAttributes.value.size === props.attributes.length
})

// 关闭弹窗
const handleClose = () => {
  emit('update:visible', false)
}

// 确认购买：加入购物车后打开结算弹层（收货信息/优惠券/价格门槛校验统一在结算链路处理）
const handleConfirm = async () => {
  if (!props.product || !allAttributesSelected.value) return

  try {
    loading.value = true
    // 选中属性（attrIndex -> option）转为 label -> option：加购时解析 SKU、结算时随单快照
    const attrMap: Record<string, string> = {}
    if (props.attributes) {
      props.attributes.forEach((attr, index) => {
        const selected = selectedAttributes.value.get(index)
        if (selected) {
          attrMap[attr.label] = selected
        }
      })
    }
    const resp = await addToCart(props.product.id, 1, Object.keys(attrMap).length > 0 ? attrMap : undefined)
    checkoutSkuIds.value = resp.data ? [resp.data] : []
    checkoutAmount.value = props.product.price
    checkoutAttributes.value = attrMap
    checkoutVisible.value = true
  } catch (error: unknown) {
    const errorMsg = error instanceof Error ? error.message : '加购失败，请稍后重试'
    showToast(errorMsg, 'error')
  } finally {
    loading.value = false
  }
}

// 结算成功：关闭购买弹层并通知父组件刷新（购物车角标等）
const handleCheckoutSuccess = () => {
  checkoutVisible.value = false
  emit('success')
  handleClose()
}
</script>

<style scoped>
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10000;
  padding: 20px;
}

.modal-container {
  background: white;
  border-radius: 16px;
  width: 100%;
  max-width: 500px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  overflow: hidden;
}

.modal-header {
  padding: 20px 24px;
  border-bottom: 1px solid #f0f0f0;
}

.modal-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #333;
}

.modal-body {
  padding: 24px;
  max-height: 60vh;
  overflow-y: auto;
}

/* 商品摘要 */
.product-summary {
  display: flex;
  gap: 16px;
  padding-bottom: 24px;
  border-bottom: 1px solid #f0f0f0;
  margin-bottom: 24px;
}

.product-thumb {
  width: 80px;
  height: 80px;
  border-radius: 8px;
  object-fit: cover;
  background: #f5f5f5;
}

.product-info {
  flex: 1;
}

.product-name {
  font-size: 15px;
  font-weight: 500;
  color: #333;
  margin: 0 0 8px 0;
  line-height: 1.4;
}

.product-price {
  display: flex;
  align-items: baseline;
  gap: 2px;
}

.price-symbol {
  font-size: 14px;
  color: #ff2442;
  font-weight: 600;
}

.price-value {
  font-size: 20px;
  color: #ff2442;
  font-weight: 700;
}

/* 属性选择 */
.attributes-section {
  margin-bottom: 16px;
}

.attribute-group {
  margin-bottom: 20px;
}

.attribute-group:last-child {
  margin-bottom: 0;
}

.attribute-label {
  font-size: 14px;
  font-weight: 600;
  color: #333;
  margin-bottom: 12px;
}

.attribute-options {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.attribute-option {
  padding: 8px 20px;
  border: 1px solid #ddd;
  background: white;
  border-radius: 8px;
  font-size: 14px;
  color: #666;
  cursor: pointer;
  transition: all 0.2s;
}
.attribute-option:hover {
  border-color: #ff2442;
  color: #ff2442;
}

.attribute-option.active {
  border-color: #ff2442;
  background: #fff5f5;
  color: #ff2442;
}

.tip-text {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #ff9800;
}



/* 提示信息 */

/* 优惠券选择 */
.coupon-section {
  margin-bottom: 20px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #333;
  margin-bottom: 12px;
}

.coupon-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px;
  color: #999;
  font-size: 13px;
}

.coupon-loading .loading-icon {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.no-coupon {
  padding: 16px;
  text-align: center;
  color: #999;
  font-size: 13px;
  background: #f8f8f8;
  border-radius: 8px;
}

.coupon-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.coupon-item {
  display: flex;
  align-items: center;
  padding: 12px;
  border: 1px solid #ddd;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  position: relative;
}

.coupon-item:hover:not(.disabled) {
  border-color: #ff2442;
  background: #fff5f5;
}

.coupon-item.active {
  border-color: #ff2442;
  background: #fff5f5;
}

.coupon-item.disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.coupon-left {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 70px;
  padding-right: 12px;
  border-right: 1px dashed #ddd;
}

.coupon-discount {
  display: flex;
  align-items: baseline;
  gap: 2px;
}

.discount-symbol {
  font-size: 12px;
  color: #ff2442;
  font-weight: 600;
}

.discount-value {
  font-size: 20px;
  color: #ff2442;
  font-weight: 700;
}

.discount-unit {
  font-size: 14px;
  color: #ff2442;
  font-weight: 600;
  margin-left: 2px;
}

.coupon-right {
  flex: 1;
  padding-left: 12px;
}

.coupon-name {
  font-size: 14px;
  font-weight: 500;
  color: #333;
  margin-bottom: 4px;
}

.coupon-desc {
  font-size: 12px;
  color: #999;
}

.coupon-check {
  color: #ff2442;
  margin-left: 8px;
}

.no-coupon-option {
  justify-content: center;
  padding: 12px 16px;
}

.coupon-text {
  font-size: 14px;
  color: #666;
}

/* 价格汇总 */
.price-summary {
  background: #f8f8f8;
  padding: 16px;
  border-radius: 8px;
  margin-bottom: 16px;
}

.summary-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.summary-row:last-child {
  margin-bottom: 0;
}

.summary-label {
  font-size: 14px;
  color: #666;
}

.summary-value {
  font-size: 14px;
  color: #333;
  font-weight: 500;
}

.summary-value.discount {
  color: #ff2442;
}

.summary-row.total {
  padding-top: 12px;
  border-top: 1px dashed #ddd;
  margin-top: 4px;
}

.summary-row.total .summary-label {
  font-size: 15px;
  font-weight: 600;
  color: #333;
}

.summary-row.total .summary-value {
  font-size: 18px;
  font-weight: 700;
  color: #ff2442;
}

/* 底部按钮 */
.modal-footer {
  display: flex;
  gap: 12px;
  padding: 16px 24px;
  border-top: 1px solid #f0f0f0;
}

.btn {
  flex: 1;
  height: 44px;
  border: none;
  border-radius: 22px;
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s;
}

.btn-cancel {
  background: #f5f5f5;
  color: #666;
}

.btn-cancel:hover {
  background: #ebebeb;
}

.btn-confirm {
  background: linear-gradient(135deg, #ff2442 0%, #ff4757 100%);
  color: white;
}

.btn-confirm:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(255, 36, 66, 0.3);
}

.btn-confirm:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none;
}

/* 动画 */
.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.3s;
}

.modal-enter-active .modal-container,
.modal-leave-active .modal-container {
  transition: transform 0.3s;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

.modal-enter-from .modal-container,
.modal-leave-to .modal-container {
  transform: scale(0.9);
}

/* 响应式 */
@media (max-width: 600px) {
  .modal-container {
    max-width: 100%;
    margin: 0 16px;
  }
}
</style>
