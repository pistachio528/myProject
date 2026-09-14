import axios from 'axios'

// 创建 axios 实例，baseURL 由 Vite proxy 转发到后端
const http = axios.create({
  baseURL: '/api',
  timeout: 10000
})

// 请求拦截：自动注入 Authorization header
http.interceptors.request.use((config) => {
  const token = localStorage.getItem('authorization')
  if (token) {
    config.headers['Authorization'] = token
  }
  return config
})

// 响应拦截：成功返回 res.data；401 自动登出
http.interceptors.response.use(
  (res) => res.data,
  (err) => {
    if (err.response?.status === 401) {
      localStorage.removeItem('authorization')
      window.location.href = '/login'
    }
    return Promise.reject(err.response?.data || err)
  }
)

export default http
