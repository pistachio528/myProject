<template>
  <div class="feedback-container">
    <!-- 低干扰的反馈入口 -->
    <button class="feedback-btn" @click="showOptions = !showOptions" title="回答有问题？">
      👎
    </button>

    <!-- 纠错选项面板 -->
    <div v-if="showOptions" class="feedback-options">
      <div class="options-title">请选择问题类型：</div>
      <button
        v-for="opt in options"
        :key="opt.type"
        class="option-btn"
        @click="selectOption(opt.type)"
      >
        {{ opt.label }}
      </button>
      <button class="cancel-btn" @click="showOptions = false">取消</button>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const props = defineProps({
  messageId: { type: [Number, String], required: true }
})
const emit = defineEmits(['feedback'])

const showOptions = ref(false)
const options = [
  { type: 'wrong_entity', label: '查错了对象' },
  { type: 'irrelevant', label: '答非所问' },
  { type: 'inaccurate', label: '信息不准确' },
  { type: 'other', label: '其他' }
]

function selectOption(type) {
  emit('feedback', type, '')
  showOptions.value = false
}
</script>

<style scoped>
.feedback-container {
  position: relative;
  margin-top: 4px;
}
.feedback-btn {
  background: none;
  border: none;
  cursor: pointer;
  font-size: 14px;
  opacity: 0.4;
  padding: 2px 4px;
  transition: opacity 0.2s;
}
.feedback-btn:hover {
  opacity: 1;
}
.feedback-options {
  position: absolute;
  left: 0;
  top: 24px;
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  padding: 8px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.12);
  z-index: 100;
  min-width: 140px;
}
.options-title {
  font-size: 12px;
  color: #999;
  margin-bottom: 6px;
}
.option-btn {
  display: block;
  width: 100%;
  text-align: left;
  background: none;
  border: none;
  padding: 6px 8px;
  cursor: pointer;
  font-size: 13px;
  border-radius: 4px;
  transition: background 0.15s;
}
.option-btn:hover {
  background: #f5f5f5;
}
.cancel-btn {
  display: block;
  width: 100%;
  text-align: center;
  background: none;
  border: 1px solid #e8e8e8;
  padding: 4px 8px;
  cursor: pointer;
  font-size: 12px;
  border-radius: 4px;
  margin-top: 4px;
  color: #999;
}
</style>
