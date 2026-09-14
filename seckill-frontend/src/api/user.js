import axios from 'axios'
import http from './http.js'

// 发送验证码（后端用 @RequestParam 接收，需要 query string 方式）
export function sendCode(phone) {
  return http.post(`/user/code?phone=${phone}`)
}

// 登录：需要读取响应 header 中的 authorization，使用原始 axios 实例
export async function login(phone, code) {
  const res = await axios.post('/api/user/login', { phone, code })
  // 后端将 token 放在响应 header 的 authorization 字段
  const token = res.headers['authorization']
  if (token) {
    localStorage.setItem('authorization', token)
  }
  return res.data
}
