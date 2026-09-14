<template>
  <router-view />
  <!-- 全局智能客服悬浮组件 -->
  <ChatWidget />
</template>

<script setup>
import ChatWidget from './components/chat/ChatWidget.vue'
</script>
