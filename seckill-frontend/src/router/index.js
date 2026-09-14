import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', redirect: '/vouchers/1' },
  { path: '/login', component: () => import('../views/LoginView.vue') },
  { path: '/vouchers/:shopId', component: () => import('../views/VoucherListView.vue') },
  { path: '/seckill/:voucherId', component: () => import('../views/SeckillView.vue') },
  { path: '/order/status/:token', component: () => import('../views/OrderStatusView.vue') },
  { path: '/orders', component: () => import('../views/MyOrdersView.vue') }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 全局前置守卫：未登录跳转 /login
router.beforeEach((to) => {
  const token = localStorage.getItem('authorization')
  if (to.path !== '/login' && !token) {
    return '/login'
  }
})

export default router
