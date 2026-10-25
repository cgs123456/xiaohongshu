<template>
  <div class="checkout-form">
    <!-- 收货信息 -->
    <div class="section-title">收货信息</div>
    <div class="form-field">
      <label class="field-label">收货人姓名</label>
      <input
        v-model.trim="receiverName"
        type="text"
        class="field-input"
        :class="{ error: errors.name }"
        placeholder="请输入收货人姓名"
      />
    </div>
    <div class="form-field">
      <label class="field-label">手机号</label>
      <input
        v-model.trim="receiverPhone"
        type="tel"
        class="field-input"
        :class="{ error: errors.phone }"
        placeholder="请输入收货手机号"
      />
    </div>
    <div class="form-field">
      <label class="field-label">收货地址</label>
      <input
        v-model.trim="address"
        type="text"
        class="field-input"
        :class="{ error: errors.address }"
        placeholder="请输入收货地址"
      />
    </div>

    <!-- 优惠券下拉 -->
    <div class="section-title">优惠券</div>
    <select
      class="coupon-select"
      :value="selectedCouponId"
      :disabled="loadingCoupons"
      @change="onCouponChange"
    >
      <option v-if="loadingCoupons" value="">优惠券加载中...</option>
      <template v-else>
        <option value="">不使用优惠券</option>
        <option
          v-for="vo in coupons"
          :key="vo.coupon.id"
          :value="vo.coupon.id"
          :disabled="!isCouponEnabled(vo)"
        >
          {{ couponLabel(vo) }}
        </option>
      </template>
    </select>
    <div v-if="couponLoadFailed" class="coupon-failed">优惠券加载失败，本次可不使用优惠券</div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { getCouponsByUserId, type CouponVo, type CheckoutDto } from '../api/product'

const props = defineProps<{
  // 当前结算金额（元），用于优惠券门槛判断
  amount: number
}>()

const emit = defineEmits<{
  // 选中优惠券变化（null = 不使用），供父组件做金额预估
  (e: 'coupon-change', couponVo: CouponVo | null): void
}>()

const receiverName = ref('')
const receiverPhone = ref('')
const address = ref('')
const errors = ref({ name: false, phone: false, address: false })

const coupons = ref<CouponVo[]>([])
const loadingCoupons = ref(false)
const couponLoadFailed = ref(false)
const selectedCouponId = ref('') // '' = 不使用优惠券

// 加载优惠券列表
const loadCoupons = async () => {
  try {
    loadingCoupons.value = true
    const response = await getCouponsByUserId()
    coupons.value = response.data || []
  } catch {
    // 加载失败不阻塞结算流程，下拉仅保留"不使用优惠券"
    couponLoadFailed.value = true
  } finally {
    loadingCoupons.value = false
  }
}

// 券可用：后端 isUsable 且当前金额满足门槛（用"分"比较避免浮点误差）
const isCouponEnabled = (vo: CouponVo): boolean => {
  if (!vo.isUsable) return false
  return Math.round(props.amount * 100) >= Math.round(vo.coupon.minAmount * 100)
}

// 下拉选项文案：满减券显示"减 ¥X"，折扣券显示"X折"，均附带门槛说明
const couponLabel = (vo: CouponVo): string => {
  const coupon = vo.coupon
  const discountText =
    coupon.type === '2' ? `${coupon.discount}折` : `减 ¥${Number(coupon.discount).toFixed(2)}`
  const threshold =
    coupon.minAmount > 0 ? `，满 ¥${Number(coupon.minAmount).toFixed(2)}可用` : ''
  return `${coupon.name}（${discountText}${threshold}）`
}

const onCouponChange = (event: Event) => {
  selectedCouponId.value = (event.target as HTMLSelectElement).value
  const vo = coupons.value.find(c => c.coupon.id === selectedCouponId.value)
  emit('coupon-change', vo || null)
}

// 金额变化后已选券可能不再满足门槛，自动重置为不使用
watch(
  () => props.amount,
  () => {
    const vo = coupons.value.find(c => c.coupon.id === selectedCouponId.value)
    if (vo && !isCouponEnabled(vo)) {
      selectedCouponId.value = ''
      emit('coupon-change', null)
    }
  }
)

// 校验收货信息并收集结算载荷（不含 productIds，由调用方补充）；校验失败返回 null
const validate = (): Omit<CheckoutDto, 'productIds'> | null => {
  errors.value = {
    name: !receiverName.value,
    phone: !receiverPhone.value,
    address: !address.value
  }
  if (errors.value.name || errors.value.phone || errors.value.address) {
    return null
  }
  return {
    receiverName: receiverName.value,
    receiverPhone: receiverPhone.value,
    address: address.value,
    couponId: selectedCouponId.value || undefined
  }
}

// 组件随弹层 v-if 重新挂载，每次打开弹层都重新拉取最新券列表
onMounted(loadCoupons)

defineExpose({ validate })
</script>

<style scoped>
.section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 12px;
}

.form-field {
  margin-bottom: 12px;
}

.field-label {
  display: block;
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 6px;
}

.field-input {
  width: 100%;
  box-sizing: border-box;
  padding: 10px 12px;
  border: 1px solid var(--border-default);
  border-radius: 8px;
  font-size: 14px;
  color: var(--text-primary);
  outline: none;
  transition: border-color 0.2s;
}

.field-input:focus {
  border-color: var(--brand);
}

.field-input.error {
  border-color: var(--brand);
  background: #fff5f5;
}

.coupon-select {
  width: 100%;
  box-sizing: border-box;
  padding: 10px 12px;
  border: 1px solid var(--border-default);
  border-radius: 8px;
  font-size: 14px;
  color: var(--text-primary);
  background: var(--bg-surface);
  outline: none;
  cursor: pointer;
}

.coupon-select:focus {
  border-color: var(--brand);
}

.coupon-failed {
  margin-top: 8px;
  font-size: 12px;
  color: #ff9800;
}
</style>
