<template>
  <div class="chat-panel">
    <!-- 头部 -->
    <div class="chat-header">
      <span>🤖 智能客服</span>
      <button class="close-btn" @click="$emit('close')">✕</button>
    </div>

    <!-- 未登录提示 -->
    <div v-if="!isLoggedIn" class="login-tip">
      <p>请先 <a href="/login">登录</a> 后使用智能客服</p>
    </div>

    <!-- 聊天内容 -->
    <template v-else>
      <MessageList
        :messages="messages"
        :is-loading="isLoading"
        :is-streaming="isStreaming"
        :has-more="hasMore"
        @load-more="loadMore"
        @feedback="handleFeedback"
      />
      <ChatInput
        :disabled="isLoading || isStreaming"
        @send="handleSend"
      />
    </template>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import MessageList from './MessageList.vue'
import ChatInput from './ChatInput.vue'
import { useChat } from './composables/useChat.js'
import { useAuth } from './composables/useAuth.js'

defineEmits(['close'])

const { isLoggedIn } = useAuth()
const { messages, isLoading, isStreaming, send, feedback, loadHistory } = useChat()

const hasMore = ref(false)
const historyPage = ref(1)

async function handleSend(content) {
  // 获取当前页面上下文（当前路由路径）
  const pageContext = window.location.pathname
  await send(content, pageContext)
}

async function handleFeedback(messageId, type, detail) {
  await feedback(messageId, type, detail)
}

async function loadMore() {
  historyPage.value++
  await loadHistory(historyPage.value)
}
</script>

<style scoped>
.chat-panel {
  display: flex;
  flex-direction: column;
  width: 360px;
  height: 520px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 4px 24px rgba(0,0,0,0.15);
  overflow: hidden;
}
.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: #1890ff;
  color: #fff;
  font-size: 15px;
  font-weight: bold;
}
.close-btn {
  background: none;
  border: none;
  color: #fff;
  cursor: pointer;
  font-size: 16px;
  padding: 0 4px;
}
.login-tip {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #999;
  font-size: 14px;
}
.login-tip a {
  color: #1890ff;
}
</style>
