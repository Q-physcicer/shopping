/*
 * 入口文件
 * @Author: hai-27
 * @Date: 2020-02-07 16:23:00
 * @LastEditors: hai-27
 * @LastEditTime: 2020-03-04 23:38:41
 */
import Vue from 'vue'
import App from './App.vue'
import router from './router'
import store from './store'

import ElementUI from 'element-ui';
import 'element-ui/lib/theme-chalk/index.css';
Vue.use(ElementUI);

// 全局函数及变量
import Global from './Global';
Vue.use(Global);

import Axios from 'axios';
Vue.prototype.$axios = Axios;
// 全局请求拦截器
Axios.interceptors.request.use(
  config => {
    return config;
  },
  error => {
    // 跳转error页面
    router.push({ path: "/error" });
    return Promise.reject(error);
  }
);
// 全局响应拦截器
Axios.interceptors.response.use(
  res => {
    if (res.data.code === "500") {
      // 500表示服务器异常
      // 跳转error页面
      router.push({ path: "/error" });
    }
    return res;
  },
  error => {
    // P1：网关统一鉴权，未登录/登录过期返回 HTTP 401（业务 Result 里也是 401）
    if (error.response && error.response.status === 401) {
      Vue.prototype.notifyError((error.response.data && error.response.data.msg) || "请先登录");
      // 登录过期/失效：清掉残留 token 与用户态，避免路由守卫因 cookie 存在而反复放行
      document.cookie = "XM_TOKEN=;expires=Thu, 01 Jan 1970 00:00:00 GMT;path=/";
      store.dispatch("setUser", "");
      // 修改vuex的showLogin状态,显示登录组件
      store.dispatch("setShowLogin", true);
      return Promise.reject(error);
    }
    // 跳转error页面
    router.push({ path: "/error" });
    return Promise.reject(error);
  }
);

// 全局拦截器,在进入需要用户权限的页面前校验是否已经登录
router.beforeResolve((to, from, next) => {
  let cookie = document.cookie.match(new RegExp("(^| )"+ "XM_TOKEN" +"=([^;]*)(;|$)"));
  // const loginUser = store.state.user.user;
  // 判断路由是否设置相应校验用户权限
  if (to.meta.requireAuth) {
    if (!cookie) {
      // 没有登录，显示登录组件
      store.dispatch("setShowLogin", true);
      if (from.name == null) {
        //此时，是在页面没有加载，直接在地址栏输入链接，进入需要登录验证的页面
        next("/");
        return;
      }
      // 终止导航
      next(false);
      return;
    }
  }

  next();
});

// 相对时间过滤器,把时间戳转换成时间
// 格式: 2020-02-25 21:43:23
Vue.filter('dateFormat', (dataStr) => {
  var time = new Date(dataStr);
  function timeAdd0 (str) {
    if (str < 10) {
      str = '0' + str;
    }
    return str;
  }
  var y = time.getFullYear();
  var m = time.getMonth() + 1;
  var d = time.getDate();
  var h = time.getHours();
  var mm = time.getMinutes();
  var s = time.getSeconds();
  return y + '-' + timeAdd0(m) + '-' + timeAdd0(d) + ' ' + timeAdd0(h) + ':' + timeAdd0(mm) + ':' + timeAdd0(s);
});

//全局组件
import MyMenu from './components/MyMenu';
Vue.component(MyMenu.name, MyMenu);
import MyList from './components/MyList';
Vue.component(MyList.name, MyList);
import MySeckillList from './components/MySeckillList';
Vue.component(MySeckillList.name, MySeckillList);
import MyLogin from './components/MyLogin';
Vue.component(MyLogin.name, MyLogin);
import MyRegister from './components/MyRegister';
Vue.component(MyRegister.name, MyRegister);

Vue.config.productionTip = false;

// v-imgerror：图片加载失败兜底为占位图（原裂图直裸奔）
Vue.directive('imgerror', {
  bind(el, binding) {
    const fallback = binding.value || '/imgs/goods/placeholder.svg';
    el.addEventListener('error', function onImgErr() {
      if (el.src !== fallback) {
        el.src = location.origin + fallback;
      }
      el.removeEventListener('error', onImgErr);
    });
  }
});
// eslint-disable-next-line no-console

new Vue({
  router,
  store,
  render: h => h(App)
}).$mount('#app')

