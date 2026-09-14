<template>
  <div class="chat-input">
    <textarea
      v-model="inputText"
      :disabled="disabled"
      placeholder="请输入您的问题..."
      rows="2"
      @keydown.enter.exact.prevent="handleSend"
    />
    <button
      class="send-btn"
      :disabled="disabled || !inputText.trim()"
      @click="handleSend"
    >
      发送
    </button>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const props = defineProps({
  disabled: { type: Boolean, default: false }
})
const emit = defineEmits(['send'])

const inputText = ref('')

function handleSend() {
  const text = inputText.value.trim()
  if (!text || props.disabled) return
  emit('send', text)
  inputText.value = ''
}
</script>

<style scoped>
.chat-input {
  display: flex;
  gap: 8px;
  padding: 8px 12px;
  border-top: 1px solid #f0f0f0;
  background: #fff;
}
textarea {
  flex: 1;
  resize: none;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  padding: 8px;
  font-size: 14px;
  outline: none;
  font-family: inherit;
}
textarea:focus {
  border-color: #1890ff;
}
.send-btn {
  padding: 0 16px;
  background: #1890ff;
  color: #fff;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  white-space: nowrap;
}
.send-btn:disabled {
  background: #ccc;
  cursor: not-allowed;
}
</style>
