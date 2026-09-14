import http from './http.js'

// 发送消息，返回 { sessionId, messageId }
export function sendMessage(sessionId, content, userId, pageContext, authToken) {
  return http.post('/chat/send', { sessionId, content, userId, pageContext, authToken })
}

// 提交反馈
export function submitFeedback(sessionId, messageId, feedbackType, detail) {
  return http.post('/chat/feedback', { sessionId, messageId, feedbackType, detail })
}

// 获取历史消息
export function getChatHistory(sessionId, page = 1, size = 20) {
  return http.get(`/chat/history/${sessionId}`, { params: { page, size } })
}
