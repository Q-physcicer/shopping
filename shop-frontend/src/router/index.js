import Vue from 'vue'
import VueRouter from 'vue-router'

Vue.use(VueRouter)

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/HomeView.vue')
  },
  {
    path: '/search',
    name: 'Search',
    component: () => import('../views/SearchView.vue')
  },
  {
    path: '/error',
    name: 'Error',
    component: () => import('../components/ErrorPage.vue')
  },
  {
    path: '/goods',
    name: 'Goods',
    component: () => import('../views/GoodsView.vue')
  },
  {
    path: '/seckill',
    name: 'Seckill',
    component: () => import('../views/SeckillView.vue')
  },
  {
    path: '/goods/details',
    name: 'GoodsDetails',
    component: () => import('../views/DetailsView.vue')
  },
  {
    path: '/goods/seckillDetails',
    name: 'SeckillDetails',
    component: () => import('../views/SeckillDetails.vue')
  },
  {
    path: '/shoppingCart',
    name: 'ShoppingCart',
    component: () => import('../views/ShoppingCart.vue'),
    meta: {
      requireAuth: true // 需要验证登录状态
    }
  },
  {
    path: '/collect',
    name: 'Collect',
    component: () => import('../views/CollectView.vue'),
    meta: {
      requireAuth: true // 需要验证登录状态
    }
  },
  {
    path: '/order',
    name: 'Order',
    component: () => import('../views/OrderVirw.vue'),
    meta: {
      requireAuth: true // 需要验证登录状态
    }
  },
  {
    path: '/confirmOrder',
    name: 'ConfirmOrder',
    component: () => import('../views/ConfirmOrder.vue'),
    meta: {
      requireAuth: true // 需要验证登录状态
    }
  },
  {
    path: '/pay/:orderId',
    name: 'Pay',
    component: () => import('../views/PayView.vue'),
    meta: {
      requireAuth: true // 需要验证登录状态
    }
  },
  {
    path: '/member',
    component: () => import('../views/member/MemberCenter.vue'),
    redirect: '/member/profile',
    meta: {
      requireAuth: true
    },
    children: [
      {
        path: 'profile',
        name: 'MemberProfile',
        component: () => import('../views/member/MemberProfile.vue'),
        meta: { requireAuth: true }
      },
      {
        path: 'aftersale',
        name: 'MemberAftersale',
        component: () => import('../views/member/MemberAftersale.vue'),
        meta: { requireAuth: true }
      },
      {
        path: 'address',
        name: 'MemberAddress',
        component: () => import('../views/member/MemberAddress.vue'),
        meta: { requireAuth: true }
      },
      {
        path: 'message',
        name: 'MemberMessage',
        component: () => import('../views/member/MemberMessage.vue'),
        meta: { requireAuth: true }
      }
    ]
  },
  // 404 兜底（原无匹配时主区域静默空白，无提示无引导）
  {
    path: '*',
    name: 'NotFound',
    component: () => import('../views/NotFound.vue')
  }
]

const router = new VueRouter({
  // base: '/dist',
  mode: 'history',
  routes
})

/* 由于Vue-router在3.1之后把$router.push()方法改为了Promise。所以假如没有回调函数，错误信息就会交给全局的路由错误处理。
vue-router先报了一个Uncaught(in promise)的错误(因为push没加回调) ，然后再点击路由的时候才会触发NavigationDuplicated的错误(路由出现的错误，全局错误处理打印了出来)。*/
// 禁止全局路由错误处理打印
const originalPush = VueRouter.prototype.push
VueRouter.prototype.push = function push(location, onResolve, onReject) {
  if (onResolve || onReject)
    return originalPush.call(this, location, onResolve, onReject)
  return originalPush.call(this, location).catch(err => err)
}

export default router

