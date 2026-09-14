<template>
  <div class="seckill-page">
    <div v-if="loading" class="loading">加载中...</div>
    <div v-else-if="voucher" class="card">
      <h2>{{ voucher.title }}</h2>
      <p style="color:#999;margin-bottom:16px">{{ voucher.subTitle }}</p>
      <p style="font-size:28px;color:#e74c3c;font-weight:bold;margin-bottom:4px">
        ¥{{ (voucher.payValue / 100).toFixed(2) }}
        <span style="font-size:14px;color:#999;text-decoration:line-through;margin-left:6px">
          ¥{{ (voucher.actualValue / 100).toFixed(2) }}
        </span>
      </p>
      <p style="font-size:13px;margin-bottom:20px" :style="{ color: stockStatus.color }">{{ stockStatus.text }}</p>

      <!-- 倒计时 -->
      <div class="countdown" v-if="remaining > 0">
        <div class="label">{{ countdownLabel }}</div>
        <div class="time">{{ formatTime(remaining) }}</div>
      </div>
      <div v-else style="text-align:center;color:#999;margin:16px 0">活动已结束</div>

      <button
        class="btn-primary"
        :disabled="seckillLoading || remaining <= 0"
        @click="handleSeckill"
      >
        {{ seckillLoading ? '抢购中...' : '立即秒杀' }}
      </button>
      <p v-if="error" class="error">{{ error }}</p>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getVoucherList } from '../api/voucher.js'
import { doSeckill } from '../api/seckill.js'

const route = useRoute()
const router = useRouter()
const voucher = ref(null)
const loading = ref(false)
const seckillLoading = ref(false)
const error = ref('')
const remaining = ref(0)
const countdownLabel = ref('')
let timer = null

// 库存状态展示（不直接展示数字，避免引发恐慌性抢购）
const stockStatus = computed(() => {
  const stock = voucher.value?.stock ?? 0
  if (stock <= 0)  return { text: '已售罄', color: '#999' }
  if (stock <= 10) return { text: '库存紧张', color: '#e74c3c' }
  return { text: '库存充足', color: '#27ae60' }
})

onMounted(async () => {
  const voucherId = route.params.voucherId
  loading.value = true
  try {
    const res = await getVoucherList(1)
    // 后端返回 { success, data: [...], errorMsg }
    const list = Array.isArray(res?.data) ? res.data : (Array.isArray(res) ? res : [])
    voucher.value = list.find(v => String(v.id) === String(voucherId))
    if (voucher.value) startCountdown()
  } catch (e) {
    error.value = '加载失败'
  } finally {
    loading.value = false
  }
})

function startCountdown() {
  updateCountdown()
  timer = setInterval(updateCountdown, 1000)
}

function updateCountdown() {
  if (!voucher.value) return
  const now = Date.now()
  const begin = new Date(voucher.value.beginTime).getTime()
  const end = new Date(voucher.value.endTime).getTime()
  if (now < begin) {
    remaining.value = begin - now
    countdownLabel.value = '距开始'
  } else if (now < end) {
    remaining.value = end - now
    countdownLabel.value = '距结束'
  } else {
    remaining.value = 0
    clearInterval(timer)
  }
}

function formatTime(ms) {
  const s = Math.floor(ms / 1000)
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const sec = s % 60
  return `${String(h).padStart(2,'0')}:${String(m).padStart(2,'0')}:${String(sec).padStart(2,'0')}`
}

async function handleSeckill() {
  seckillLoading.value = true
  error.value = ''
  try {
    const res = await doSeckill(route.params.voucherId)
    // 后端返回 { success, data: { code, token, message }, errorMsg }
    if (res?.success === false) {
      error.value = res.errorMsg || '秒杀失败'
      return
    }
    const token = res?.data?.token
    if (token) {
      router.push('/order/status/' + token)
    } else {
      error.value = res?.data?.message || res?.errorMsg || '秒杀失败'
    }
  } catch (e) {
    error.value = e?.errorMsg || e?.message || '秒杀失败，请重试'
  } finally {
    seckillLoading.value = false
  }
}

onUnmounted(() => { if (timer) clearInterval(timer) })
</script>
