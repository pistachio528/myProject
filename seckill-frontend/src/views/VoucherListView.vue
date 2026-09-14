<template>
  <div class="voucher-list">
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px">
      <h2 style="margin:0">🎫 秒杀优惠券</h2>
      <button class="btn-orders" @click="router.push('/orders')">我的优惠券</button>
    </div>
    <div v-if="loading" class="loading">加载中...</div>
    <div v-else-if="error" class="error">{{ error }}</div>
    <div v-else>
      <div v-for="v in vouchers" :key="v.id" class="voucher-card">
        <div class="voucher-info">
          <h3>{{ v.title }}</h3>
          <p>{{ v.subTitle }}</p>
          <p>活动时间：{{ formatTime(v.beginTime) }} ~ {{ formatTime(v.endTime) }}</p>
          <p :style="{ color: getStockStatus(v).color, fontSize: '13px' }">{{ getStockStatus(v).text }}</p>
        </div>
        <div style="text-align:right">
          <div class="voucher-price">
            ¥{{ (v.payValue / 100).toFixed(2) }}
            <span>¥{{ (v.actualValue / 100).toFixed(2) }}</span>
          </div>
          <button
            class="btn-primary"
            style="width:90px;margin-top:8px"
            :disabled="getVoucherStatus(v).disabled"
            @click="goSeckill(v.id)"
          >
            {{ getVoucherStatus(v).label }}
          </button>
        </div>
      </div>
      <div v-if="vouchers.length === 0" style="text-align:center;color:#999;padding:40px">暂无秒杀活动</div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getVoucherList } from '../api/voucher.js'

const route = useRoute()
const router = useRouter()
const vouchers = ref([])
const loading = ref(false)
const error = ref('')

onMounted(async () => {
  const shopId = route.params.shopId || 1
  loading.value = true
  try {
    const res = await getVoucherList(shopId)
    // 后端返回 { success, data: [...], errorMsg }，取 data 字段
    if (res?.success === false) {
      error.value = res.errorMsg || '加载失败'
    } else {
      vouchers.value = Array.isArray(res?.data) ? res.data : (Array.isArray(res) ? res : [])
    }
  } catch (e) {
    error.value = '加载失败，请刷新重试'
  } finally {
    loading.value = false
  }
})

// 库存状态展示
function getStockStatus(v) {
  if (v.stock <= 0)  return { text: '已售罄', color: '#999' }
  if (v.stock <= 10) return { text: '库存紧张', color: '#e74c3c' }
  return { text: '库存充足', color: '#27ae60' }
}

// 计算活动状态
function getVoucherStatus(v) {
  const now = Date.now()
  const begin = new Date(v.beginTime).getTime()
  const end = new Date(v.endTime).getTime()
  if (now < begin) return { label: '未开始', disabled: true }
  if (now > end) return { label: '已结束', disabled: true }
  if (v.stock <= 0) return { label: '已抢完', disabled: true }
  return { label: '立即抢购', disabled: false }
}

function formatTime(t) {
  return t ? t.replace('T', ' ').substring(0, 16) : ''
}

function goSeckill(id) {
  router.push('/seckill/' + id)
}
</script>

<style scoped>
.btn-orders {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 7px 16px;
  font-size: 13px;
  font-weight: 500;
  color: #e74c3c;
  background: #fff;
  border: 1.5px solid #e74c3c;
  border-radius: 20px;
  cursor: pointer;
  transition: all 0.2s;
}
.btn-orders:hover {
  background: #e74c3c;
  color: #fff;
}
</style>
