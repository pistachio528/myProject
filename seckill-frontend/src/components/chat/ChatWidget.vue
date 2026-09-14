<template>
  <div class="chat-widget">
    <!-- 聊天面板 -->
    <Transition name="slide-up">
      <ChatPanel v-if="isOpen" @close="isOpen = false" />
    </Transition>

    <!-- 悬浮按钮 -->
    <button class="chat-fab" @click="isOpen = !isOpen" :title="isOpen ? '关闭客服' : '联系客服'">
      <span v-if="!isOpen">💬</span>
      <span v-else>✕</span>
    </button>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import ChatPanel from './ChatPanel.vue'

const isOpen = ref(false)
</script>

<style scoped>
.chat-widget {
  position: fixed;
  bottom: 24px;
  right: 24px;
  z-index: 1000;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 12px;
}
.chat-fab {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: #1890ff;
  color: #fff;
  border: none;
  cursor: pointer;
  font-size: 22px;
  box-shadow: 0 4px 12px rgba(24,144,255,0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.2s, box-shadow 0.2s;
}
.chat-fab:hover {
  transform: scale(1.08);
  box-shadow: 0 6px 16px rgba(24,144,255,0.5);
}
.slide-up-enter-active,
.slide-up-leave-active {
  transition: all 0.25s ease;
}
.slide-up-enter-from,
.slide-up-leave-to {
  opacity: 0;
  transform: translateY(20px);
}
</style>
