import { ref } from 'vue'

export function useSSE() {
  const isStreaming = ref(false)
  let eventSource = null

  function connect(sessionId, { onToken, onCard, onDone, onError }) {
    if (eventSource) {
      eventSource.close()
    }

    const url = `/api/chat/stream/${sessionId}`
    eventSource = new EventSource(url)
    isStreaming.value = true

    eventSource.addEventListener('token', (e) => {
      try {
        const data = JSON.parse(e.data)
        onToken && onToken(data.content || '')
      } catch {}
    })

    eventSource.addEventListener('card', (e) => {
      try {
        const data = JSON.parse(e.data)
        onCard && onCard(data)
      } catch {}
    })

    eventSource.addEventListener('done', (e) => {
      isStreaming.value = false
      eventSource.close()
      eventSource = null
      onDone && onDone()
    })

    eventSource.addEventListener('error', (e) => {
      isStreaming.value = false
      if (eventSource) {
        eventSource.close()
        eventSource = null
      }
      try {
        const data = JSON.parse(e.data)
        onError && onError(data.errorMessage || '服务暂时不可用')
      } catch {
        onError && onError('连接异常，请重试')
      }
    })

    // 原生 error 事件（连接断开）
    eventSource.onerror = () => {
      if (isStreaming.value) {
        isStreaming.value = false
        onError && onError('连接断开，请重试')
      }
      if (eventSource) {
        eventSource.close()
        eventSource = null
      }
    }
  }

  function disconnect() {
    if (eventSource) {
      eventSource.close()
      eventSource = null
    }
    isStreaming.value = false
  }

  return { isStreaming, connect, disconnect }
}
