<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="visible" class="modal-overlay" @click="handleOverlayClick">
        <div class="modal-container" @click.stop>
          <!-- 下单成功视图 -->
          <template v-if="successMessage">
            <div class="success-body">
              <Icon icon="lucide:circle-check" class="success-icon" width="64" />
              <p class="success-text">{{ successMessage }}</p>
              <div class="success-actions">
                <button class="btn btn-cancel" @click="close">完成</button>
                <button class="btn btn-confirm" @click="goOrders">去订单页支付</button>
              </div>
            </div>
          </template>

          <!-- 结算表单视图 -->
          <template v-else>
            <div class="modal-header">
              <h3>确认结算</h3>
            </div>
            <div class="modal-body">
              <div class="checkout-summary">
                已选 {{ itemCount }} 件商品，合计：
                <span class="summary-price">¥{{ amount.toFixed(2) }}</span>
              </div>
              <CheckoutForm ref="checkoutFormRef" :amount="amount" />
            </div>
            <div class="modal-footer">
              <button class="btn btn-cancel" :disabled="submitting" @click="close">取消</button>
              <button class="btn btn-confirm" :disabled="submitting" @click="handleSubmit">
                <span v-if="submitting">提交中...</span>
                <span v-else>提交结算</span>
              </button>
            </div>
          </template>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Icon } from '@iconify/vue'
import { useRouter } from 'vue-router'
import CheckoutForm from './CheckoutForm.vue'
import { checkoutCart } from '../api/product'
import { useToast } from '../composables/useToast'

const props = defineProps<{
  visible: boolean
  // 结算的规格 id 列表（同商品不同规格独立行）
  skuIds: number[]
  // 结算金额（元），用于券门槛判断与摘要展示
  amount: number
  // 结算商品总件数
  itemCount: number
  // 商品属性选择（label -> 选中值），仅单品立即购买路径传入，随单落订单明细
  attributes?: Record<string, string>
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  // 下单成功（父组件同步清理本地购物车）
  (e: 'success'): void
}>()

const router = useRouter()
const { showToast } = useToast()

const checkoutFormRef = ref<InstanceType<typeof CheckoutForm> | null>(null)
const submitting = ref(false)
const successMessage = ref('')

// 提交结算：校验收货信息后调用 checkoutCart
const handleSubmit = async () => {
  const payload = checkoutFormRef.value?.validate()
  if (!payload) {
    showToast('请填写完整收货信息', 'error')
    return
  }

  try {
    submitting.value = true
    const attrs = props.attributes && Object.keys(props.attributes).length > 0
      ? props.attributes
      : undefined
    const response = await checkoutCart({ ...payload, skuIds: props.skuIds, attributes: attrs })
    successMessage.value = response.data || '下单成功，请在 15 分钟内完成支付'
    showToast(successMessage.value, 'success')
    emit('success')
  } catch (error: unknown) {
    const errorMsg = error instanceof Error ? error.message : '结算失败，请稍后重试'
    showToast(errorMsg, 'error')
  } finally {
    submitting.value = false
  }
}

// 提交中不允许点遮罩关闭，防止误触丢失表单
const handleOverlayClick = () => {
  if (!submitting.value) close()
}

const close = () => {
  if (submitting.value) return
  successMessage.value = ''
  emit('update:visible', false)
}

// 跳转订单页支付
const goOrders = () => {
  successMessage.value = ''
  emit('update:visible', false)
  router.push('/orders')
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
  background: var(--bg-surface);
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
  color: var(--text-primary);
}

.modal-body {
  padding: 24px;
  max-height: 60vh;
  overflow-y: auto;
}

.checkout-summary {
  padding: 12px 16px;
  background: var(--bg-page);
  border-radius: 8px;
  font-size: 14px;
  color: var(--text-secondary);
  margin-bottom: 20px;
}

.summary-price {
  font-size: 18px;
  font-weight: 700;
  color: var(--brand);
}

/* 成功视图 */
.success-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48px 32px 32px;
}

.success-icon {
  color: #4caf50;
  margin-bottom: 16px;
}

.success-text {
  margin: 0 0 24px;
  font-size: 15px;
  color: var(--text-primary);
  text-align: center;
  line-height: 1.6;
}

.success-actions {
  display: flex;
  gap: 12px;
  width: 100%;
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
  background: var(--bg-subtle);
  color: var(--text-secondary);
}

.btn-cancel:hover:not(:disabled) {
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

.btn:disabled {
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
@media (max-width: 600px) {
  /* 移动端弹窗全屏化（社区/电商惯例），替代居中卡片 */
  .modal-overlay {
    padding: 0;
  }

  .modal-container {
    max-width: 100%;
    width: 100%;
    height: 100dvh;
    max-height: 100dvh;
    border-radius: 0;
    margin: 0;
    overflow-y: auto;
  }
}
</style>
