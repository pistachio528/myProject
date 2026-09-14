<template>
  <div class="page">
    <div class="status-page">
      <div class="status-icon">{{ config.icon }}</div>
      <div class="status-text" :style="{ color: config.color }">{{ config.text }}</div>
      <div class="status-sub">{{ config.sub }}</div>
      <div v-if="status === 'QUEUING'" style="margin-top:16px;color:#999;font-size:13px">
        已等待 {{ pollCount * 2 }} 秒...
      </div>
      <button
        v-if="status !== 'QUEUING'"
        class="btn-primary"
        style="width:160px;margin-top:24px"
        @click="$router.push('/vouchers/1')"
      >
        返回列表
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { getOrderStatus } from '../api/seckill.js'

const route = useRoute()
const status = ref('QUEUING')
const pollCount = ref(0)
const MAX_POLL = 30
let pollTimer = null

// 状态展示配置
const statusMap = {
  QUEUING: { icon: '⏳', text: '排队中，请稍候...', sub: '系统正在为您创建订单', color: '#f39c12' },
  CREATED: { icon: '🎉', text: '抢购成功！', sub: '订单已创建，请前往支付', color: '#27ae60' },
  FAILED:  { icon: '❌', text: '抢购失败', sub: '订单创建失败，库存已回补', color: '#e74c3c' },
  TIMEOUT: { icon: '⚠️', text: '查询超时', sub: '请前往订单列表确认结果', color: '#999' }
}

const config = computed(() => statusMap[status.value] || statusMap.TIMEOUT)

onMounted(() => {
  const token = route.params.token
  queryStatus(token)
  pollTimer = setInterval(() => {
    pollCount.value++
    if (pollCount.value > MAX_POLL) {
      stopPolling()
      status.value = 'TIMEOUT'
      return
    }
    queryStatus(token)
  }, 2000)
})

async function queryStatus(token) {
  try {
    const res = await getOrderStatus(token)
    // 后端返回 { success, data: "QUEUING/CREATED/FAILED", errorMsg }
    const s = res?.data || res
    if (typeof s === 'string') {
      if (s.includes('TIMEOUT')) {
        status.value = 'TIMEOUT'
        stopPolling()
      } else if (s === 'CREATED' || s === 'FAILED') {
        status.value = s
        stopPolling()
      } else {
        status.value = 'QUEUING'
      }
    }
  } catch (e) {
    status.value = 'TIMEOUT'
    stopPolling()
  }
}

function stopPolling() {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
}

onUnmounted(() => stopPolling())
</script>
