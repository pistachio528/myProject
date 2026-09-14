import { ref } from 'vue'
import { sendMessage, submitFeedback, getChatHistory } from '../../../api/chat.js'
import { useSSE } from './useSSE.js'

export function useChat() {
  const messages = ref([])
  const sessionId = ref(null)
  const isLoading = ref(false)
  const error = ref('')

  const { isStreaming, connect, disconnect } = useSSE()

  function getUserId() {
    return 0  // userId 由后端从 token 解析，前端传 0 即可
  }

  function getAuthToken() {
    return localStorage.getItem('authorization') || ''
  }

  // 发送消息
  async function send(content, pageContext = '') {
    if (!content.trim() || isStreaming.value) return

    // 添加用户消息到列表
    messages.value.push({
      id: Date.now(),
      role: 'user',
      content,
      createTime: Date.now()
    })

    // 添加助手消息占位
    const assistantMsg = {
      id: Date.now() + 1,
      role: 'assistant',
      content: '',
      streaming: true,
      createTime: Date.now()
    }
    messages.value.push(assistantMsg)

    isLoading.value = true
    error.value = ''

    const sseCallbacks = {
      onToken: (token) => {
        // 找到占位消息的索引，替换整个对象触发 Vue 响应式更新
        const idx = messages.value.findIndex(m => m.id === assistantMsg.id)
        if (idx !== -1) {
          assistantMsg.content += token
          messages.value[idx] = { ...assistantMsg }
        }
      },
      onCard: (cardData) => {
        const idx = messages.value.findIndex(m => m.id === assistantMsg.id)
        if (idx !== -1) {
          assistantMsg.cardData = cardData
          messages.value[idx] = { ...assistantMsg }
        }
      },
      onDone: () => {
        const idx = messages.value.findIndex(m => m.id === assistantMsg.id)
        if (idx !== -1) {
          assistantMsg.streaming = false
          messages.value[idx] = { ...assistantMsg }
        }
        isLoading.value = false
      },
      onError: (errMsg) => {
        const idx = messages.value.findIndex(m => m.id === assistantMsg.id)
        if (idx !== -1) {
          assistantMsg.content = errMsg || '抱歉，服务暂时不可用'
          assistantMsg.streaming = false
          messages.value[idx] = { ...assistantMsg }
        }
        isLoading.value = false
      }
    }

    try {
      // 发消息，后端会自动创建 session 并立即开始异步处理（等待 SSE emitter 最多 10 秒）
      const res = await sendMessage(sessionId.value, content, getUserId(), pageContext, getAuthToken())
      const sid = res.sessionId || res.data?.sessionId
      sessionId.value = sid

      // 立即建立 SSE 连接，后端在等我们
      connect(sid, sseCallbacks)

    } catch (e) {
      assistantMsg.content = e?.message || '发送失败，请重试'
      assistantMsg.streaming = false
      messages.value = [...messages.value]
      isLoading.value = false
    }
  }

  // 提交反馈
  async function feedback(messageId, feedbackType, detail = '') {
    if (!sessionId.value) return
    try {
      await submitFeedback(sessionId.value, messageId, feedbackType, detail)
    } catch (e) {
      console.warn('Failed to submit feedback:', e)
    }
  }

  // 加载更多历史消息
  async function loadHistory(page = 1) {
    if (!sessionId.value) return
    try {
      const res = await getChatHistory(sessionId.value, page)
      const historyMessages = (res.messages || res.data?.messages || []).map(msg => ({
        id: msg.createTime || Date.now(),
        role: msg.role,
        content: msg.content,
        createTime: msg.createTime
      }))
      messages.value = [...historyMessages, ...messages.value]
    } catch (e) {
      console.warn('Failed to load history:', e)
    }
  }

  return {
    messages,
    sessionId,
    isLoading,
    isStreaming,
    error,
    send,
    feedback,
    loadHistory,
    disconnect
  }
}
