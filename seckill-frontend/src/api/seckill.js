import http from './http.js'

// 发起秒杀，返回 { token, message }
export function doSeckill(voucherId) {
  return http.post('/seckill/' + voucherId)
}

// 查询订单进度，返回 { status, message }
export function getOrderStatus(token) {
  return http.get('/seckill/order/status/' + token)
}

// 查询我的优惠券列表
export function getMyOrders() {
  return http.get('/seckill/order/list')
}
