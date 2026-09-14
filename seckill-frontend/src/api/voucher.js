import http from './http.js'

// 获取店铺优惠券列表
export function getVoucherList(shopId) {
  return http.get('/voucher/list/' + shopId)
}
