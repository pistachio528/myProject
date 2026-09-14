<template>
  <div class="page">
    <div class="card">
      <h2>🍜 黑马点评 · 秒杀</h2>
      <div class="form-item">
        <input v-model="phone" placeholder="请输入手机号" maxlength="11" />
      </div>
      <div class="form-item code-row">
        <input v-model="code" placeholder="请输入验证码" maxlength="6" />
        <button class="btn-outline" @click="handleSendCode" :disabled="countdown > 0 || sending">
          {{ countdown > 0 ? countdown + 's' : '获取验证码' }}
        </button>
      </div>
      <button class="btn-primary" @click="handleLogin" :disabled="loading">
        {{ loading ? '登录中...' : '登录' }}
      </button>
      <p v-if="error" class="error">{{ error }}</p>
    </div>
  </div>
</template>

<script setup>
import { ref, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { sendCode, login } from '../api/user.js'

const router = useRouter()
const phone = ref('')
const code = ref('')
const countdown = ref(0)
const sending = ref(false)
const loading = ref(false)
const error = ref('')

let timer = null

// 发送验证码
async function handleSendCode() {
  if (!phone.value) { error.value = '请输入手机号'; return }
  sending.value = true
  error.value = ''
  try {
    await sendCode(phone.value)
    // 启动 60s 倒计时
    countdown.value = 60
    timer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) clearInterval(timer)
    }, 1000)
  } catch (e) {
    error.value = e?.errorMsg || '发送失败，请重试'
  } finally {
    sending.value = false
  }
}

// 登录
async function handleLogin() {
  if (!phone.value || !code.value) { error.value = '请填写手机号和验证码'; return }
  loading.value = true
  error.value = ''
  try {
    await login(phone.value, code.value)
    router.push('/vouchers/1')
  } catch (e) {
    error.value = e?.errorMsg || '登录失败，验证码错误'
  } finally {
    loading.value = false
  }
}

onUnmounted(() => { if (timer) clearInterval(timer) })
</script>
