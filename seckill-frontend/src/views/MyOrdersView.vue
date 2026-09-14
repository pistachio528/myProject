<template>
  <div class="order-list-page">
    <div class="header">
      <button class="btn-back" @click="router.back()">← 返回</button>
      <h2>我的优惠券</h2>
    </div>

    <div v-if="loading" class="loading">加载中...</div>
    <div v-else-if="error" class="error">{{ error }}</div>
    <div v-else-if="orders.length === 0" class="empty">暂无订单</div>
    <div v-else>
      <div v-for="order in orders" :key="order.id" class="order-card">
        <div class="order-info">
          <div class="order-title">{{ voucherMap[order.voucherId]?.title || '优惠券 #' + order.voucherId }}</div>
          <div class="order-sub">{{ voucherMap[order.voucherId]?.subTitle }}</div>
          <div class="order-time">下单时间：{{ formatTime(order.createTime) }}</div>
          <div class="order-id">订单号：{{ order.id }}</div>
        </div>
        <div class="order-status" :class="statusClass(order.status)">
          {{ statusText(order.status) }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getMyOrders } from '../api/seckill.js'
import { getVoucherList } from '../api/voucher.js'

const router = useRouter()
const orders = ref([])
const voucherMap = ref({})
const loading = ref(false)
const error = ref('')

onMounted(async () => {
  loading.value = true
  try {
    // 并行请求订单和优惠券信息
    const [orderRes, voucherRes] = await Promise.all([
      getMyOrders(),
      getVoucherList(1)
    ])

    // 解析订单
    const orderList = Array.isArray(orderRes?.data) ? orderRes.data : (Array.isArray(orderRes) ? orderRes : [])
    orders.value = orderList

    // 构建优惠券 map，方便展示名称
    const voucherList = Array.isArray(voucherRes?.data) ? voucherRes.data : (Array.isArray(voucherRes) ? voucherRes : [])
    voucherList.forEach(v => { voucherMap.value[v.id] = v })
  } catch (e) {
    error.value = '加载失败，请刷新重试'
  } finally {
    loading.value = false
  }
})

function statusText(status) {
  if (status === 1) return '处理中'
  if (status === 2) return '已完成'
  if (status === 3) return '已失败'
  return '未知'
}

function statusClass(status) {
  if (status === 1) return 'status-pending'
  if (status === 2) return 'status-success'
  if (status === 3) return 'status-failed'
  return ''
}

function formatTime(t) {
  return t ? t.replace('T', ' ').substring(0, 19) : '-'
}
</script>

<style scoped>
.order-list-page {
  max-width: 600px;
  margin: 0 auto;
  padding: 16px;
}
.header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}
.header h2 { margin: 0; }
.btn-back {
  background: none;
  border: none;
  color: #e74c3c;
  font-size: 14px;
  cursor: pointer;
  padding: 0;
}
.loading, .empty {
  text-align: center;
  color: #999;
  padding: 40px;
}
.error {
  text-align: center;
  color: #e74c3c;
  padding: 40px;
}
.order-card {
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 12px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.order-title {
  font-size: 16px;
  font-weight: bold;
  margin-bottom: 4px;
}
.order-sub {
  font-size: 13px;
  color: #999;
  margin-bottom: 6px;
}
.order-time, .order-id {
  font-size: 12px;
  color: #bbb;
}
.order-status {
  font-size: 13px;
  font-weight: bold;
  padding: 4px 10px;
  border-radius: 20px;
  white-space: nowrap;
}
.status-pending { background: #fff7e6; color: #fa8c16; }
.status-success { background: #f6ffed; color: #52c41a; }
.status-failed  { background: #fff1f0; color: #f5222d; }
</style>
