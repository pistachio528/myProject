<template>
  <div class="message-bubble" :class="message.role">
    <div class="bubble-content">
      <!-- 流式打字机效果 -->
      <span v-if="message.role === 'assistant' && message.streaming" class="streaming-text">
        <span v-html="renderedContent"></span><span class="cursor">|</span>
      </span>
      <span v-else-if="message.role === 'assistant'" v-html="renderedContent"></span>
      <span v-else>{{ message.content }}</span>

      <!-- 业务数据卡片 -->
      <div v-if="message.cardData" class="business-card">
        <div v-if="message.cardData.cardType === 'order'" class="card-order">
          <div class="card-title">📦 订单信息</div>
          <pre class="card-data">{{ JSON.stringify(message.cardData.cardData, null, 2) }}</pre>
        </div>
        <div v-else-if="message.cardData.cardType === 'voucher'" class="card-voucher">
          <div class="card-title">🎫 优惠券信息</div>
          <pre class="card-data">{{ JSON.stringify(message.cardData.cardData, null, 2) }}</pre>
        </div>
        <div v-else class="card-generic">
          <pre class="card-data">{{ JSON.stringify(message.cardData.cardData, null, 2) }}</pre>
        </div>
      </div>
    </div>

    <!-- 反馈按钮（仅助手消息显示） -->
    <FeedbackButton
      v-if="message.role === 'assistant' && !message.streaming"
      :message-id="message.id"
      @feedback="(type, detail) => $emit('feedback', message.id, type, detail)"
    />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { marked } from 'marked'
import FeedbackButton from './FeedbackButton.vue'

const props = defineProps({
  message: { type: Object, required: true }
})
defineEmits(['feedback'])

// 配置 marked：禁用 mangle 和 headerIds 避免警告
marked.setOptions({ mangle: false, headerIds: false })

const renderedContent = computed(() => {
  if (!props.message.content) return ''
  return marked.parse(props.message.content)
})
</script>

<style scoped>
.message-bubble {
  display: flex;
  flex-direction: column;
  margin-bottom: 12px;
}
.message-bubble.user {
  align-items: flex-end;
}
.message-bubble.assistant {
  align-items: flex-start;
}
.bubble-content {
  max-width: 80%;
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
}
.user .bubble-content {
  background: #1890ff;
  color: #fff;
  border-bottom-right-radius: 4px;
}
.assistant .bubble-content {
  background: #f0f0f0;
  color: #333;
  border-bottom-left-radius: 4px;
}
.cursor {
  animation: blink 1s infinite;
}
@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}
/* Markdown 渲染样式 */
.assistant .bubble-content :deep(p) {
  margin: 0 0 6px 0;
}
.assistant .bubble-content :deep(p:last-child) {
  margin-bottom: 0;
}
.assistant .bubble-content :deep(h3) {
  font-size: 14px;
  font-weight: bold;
  margin: 8px 0 4px 0;
}
.assistant .bubble-content :deep(ul),
.assistant .bubble-content :deep(ol) {
  margin: 4px 0;
  padding-left: 18px;
}
.assistant .bubble-content :deep(li) {
  margin-bottom: 2px;
}
.assistant .bubble-content :deep(strong) {
  font-weight: bold;
}
.assistant .bubble-content :deep(code) {
  background: #e8e8e8;
  padding: 1px 4px;
  border-radius: 3px;
  font-size: 12px;
}
.business-card {
  margin-top: 8px;
  padding: 8px;
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
}
.card-title {
  font-weight: bold;
  margin-bottom: 4px;
  font-size: 13px;
}
.card-data {
  font-size: 12px;
  color: #666;
  white-space: pre-wrap;
  margin: 0;
}
</style>
