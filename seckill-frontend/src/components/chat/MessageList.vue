<template>
  <div class="message-list" ref="listRef">
    <!-- 加载更多历史 -->
    <div class="load-more" v-if="hasMore">
      <button @click="$emit('load-more')" class="load-more-btn">加载更多历史消息</button>
    </div>

    <MessageBubble
      v-for="msg in messages"
      :key="msg.id"
      :message="msg"
      @feedback="(id, type, detail) => $emit('feedback', id, type, detail)"
    />

    <!-- 加载中指示器 -->
    <div v-if="isLoading && !isStreaming" class="loading-indicator">
      <span class="dot"></span><span class="dot"></span><span class="dot"></span>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'
import MessageBubble from './MessageBubble.vue'

const props = defineProps({
  messages: { type: Array, default: () => [] },
  isLoading: { type: Boolean, default: false },
  isStreaming: { type: Boolean, default: false },
  hasMore: { type: Boolean, default: false }
})
defineEmits(['load-more', 'feedback'])

const listRef = ref(null)

// 新消息时自动滚动到底部
watch(() => props.messages.length, async () => {
  await nextTick()
  if (listRef.value) {
    listRef.value.scrollTop = listRef.value.scrollHeight
  }
})
</script>

<style scoped>
.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  display: flex;
  flex-direction: column;
}
.load-more {
  text-align: center;
  margin-bottom: 8px;
}
.load-more-btn {
  background: none;
  border: 1px solid #e8e8e8;
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 12px;
  color: #999;
  cursor: pointer;
}
.loading-indicator {
  display: flex;
  gap: 4px;
  padding: 8px 14px;
  align-self: flex-start;
}
.dot {
  width: 8px;
  height: 8px;
  background: #ccc;
  border-radius: 50%;
  animation: bounce 1.2s infinite;
}
.dot:nth-child(2) { animation-delay: 0.2s; }
.dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes bounce {
  0%, 80%, 100% { transform: scale(0.8); opacity: 0.5; }
  40% { transform: scale(1.2); opacity: 1; }
}
</style>
