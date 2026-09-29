<template>
  <div id="app" name="app">
    <el-container>
      <!-- 顶部导航栏 -->
      <div class="topbar">
        <div class="nav">
          <ul>
            <li v-if="!this.$store.getters.getUser">
              <el-button type="text" @click="login">登录</el-button>
              <span class="sep">|</span>
              <el-button type="text" @click="register = true">注册</el-button>
            </li>
            <li v-else>
              <el-dropdown @command="handleUserCommand">
                <span class="user-menu-trigger">
                  欢迎，{{this.$store.getters.getUser.username}}
                  <i class="el-icon-arrow-down el-icon--right"></i>
                </span>
                <el-dropdown-menu slot="dropdown">
                  <el-dropdown-item command="/member/profile">个人中心</el-dropdown-item>
                  <el-dropdown-item command="/order">我的订单</el-dropdown-item>
                  <el-dropdown-item command="/collect">我的收藏</el-dropdown-item>
                  <el-dropdown-item command="/member/aftersale">售后服务</el-dropdown-item>
                  <el-dropdown-item command="/member/address">收货地址</el-dropdown-item>
                  <el-dropdown-item command="/member/message">
                    消息中心
                    <span v-if="getUnreadMessage > 0" class="msg-badge">{{getUnreadMessage}}</span>
                  </el-dropdown-item>
                  <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </el-dropdown>
            </li>
            <li>
              <router-link to="/order">我的订单</router-link>
            </li>
            <li>
              <router-link to="/collect">我的收藏</router-link>
            </li>
            <li :class="getNum > 0 ? 'shopCart-full' : 'shopCart'">
              <router-link to="/shoppingCart">
                <i class="el-icon-shopping-cart-full"></i> 购物车
                <span class="num">({{getNum}})</span>
              </router-link>
            </li>
          </ul>
        </div>
      </div>
      <!-- 顶部导航栏END -->

      <!-- 顶栏容器 -->
      <el-header>
        <el-menu
          :default-active="activeIndex"
          class="el-menu-demo"
          mode="horizontal"
          active-text-color="#5b6ef5"
          router
        >
          <div class="logo">
            <router-link to="/">
              <span class="logo-badge"><i class="el-icon-shopping-bag-1"></i></span>
              <span class="logo-name">星选商城</span>
            </router-link>
          </div>
          <el-menu-item index="/">首页</el-menu-item>
          <el-menu-item index="/goods">全部商品</el-menu-item>
          <el-menu-item index="/seckill">秒杀</el-menu-item>
          <!-- <el-menu-item index="/about">关于我们</el-menu-item> -->

          <div class="so">
            <el-input placeholder="请输入搜索内容" v-model="search">
              <el-button slot="append" icon="el-icon-search" @click="searchClick"></el-button>
            </el-input>
          </div>
        </el-menu>
      </el-header>
      <!-- 顶栏容器END -->

      <!-- 登录模块 -->
      <MyLogin></MyLogin>
      <!-- 注册模块 -->
      <MyRegister :register="register" @fromChild="isRegister"></MyRegister>

      <!-- 主要区域容器 -->
      <el-main>
        <keep-alive>
          <router-view></router-view>
        </keep-alive>
      </el-main>
      <!-- 主要区域容器END -->

      <!-- 底栏容器 -->
      <el-footer>
        <div class="footer">
          <div class="ng-promise-box">
            <div class="ng-promise">
              <p class="text">
                <a class="icon1" href="javascript:;">7天无理由退换货</a>
                <a class="icon2" href="javascript:;">满99元全场免邮</a>
                <a class="icon3" style="margin-right: 0" href="javascript:;">100%品质保证</a>
              </p>
            </div>
          </div>
          <div class="github">
            <a href="https://github.com/Q-physcicer" target="_blank">
              <div class="github-but"></div>
            </a>
          </div>
          <div class="mod_help">
            <p>
              <span>|</span>
              <router-link to="/">首页</router-link>
              <span>|</span>
              <router-link to="/goods">全部商品</router-link>
              <span>|</span>
              <router-link to="/seckill">秒杀</router-link>
              <span>|</span>
              <!-- <router-link to="/about">关于我们</router-link> -->
            </p>
            <p class="coty">商城版权所有 &copy; 2012-2021</p>
          </div>
        </div>
      </el-footer>
      <!-- 底栏容器END -->

      <!-- AI 智能客服悬浮挂件 -->
      <AiChat></AiChat>
    </el-container>
  </div>
</template>

<script>
import { mapActions } from "vuex";
import { mapGetters } from "vuex";
import AiChat from "./components/AiChat.vue";

export default {
  components: { AiChat },
  beforeUpdate() {
    this.activeIndex = this.$route.path;
  },
  data() {
    return {
      activeIndex: "", // 头部导航栏选中的标签
      search: "", // 搜索条件
      register: false, // 是否显示注册组件
      unreadTimer: null // 未读数轮询定时器
    };
  },
  created() {
    // 支付成功/消息产生时全局即时刷新（PayView 等子组件触发）
    this.$root.$on("refresh-unread", this.refreshUnread);

    // 对用户信息进行校验：有 XM_TOKEN 则调后端 /user/token 刷新（JWT 无 "|"，禁止前端 split 解析）
    if (this.getCookie('XM_TOKEN') != null) {
      this.$axios
        .get("/api/user/token")
        .then(res => {
          if (res.data.code) {
            this.setUser(res.data.data);
          }
        })
        .catch(() => {
          // 校验失败不写入垃圾 user，交由响应拦截器统一弹登录框
        });
    }

    // window.setTimeout(() => {
    //   this.$message({
    //     duration: 0,
    //     showClose: true,
    //     message: `
    //     <p>如果觉得这个项目还不错，</p>
    //     <p style="padding:10px 0">您可以给项目源代码仓库点Star支持一下，谢谢！</p>
    //     <p><a href="https://github.com/hai-27/vue-store" target="_blank">Github传送门</a></p>`,
    //     dangerouslyUseHTMLString: true,
    //     type: "success"
    //   });
    // }, 1000 * 60);
  },
  computed: {
    ...mapGetters(["getUser", "getNum", "getUnreadMessage"])
  },
  watch: {
    // 获取vuex的登录状态
    getUser: function(val) {
      if (val === "") {
        // 用户没有登录：清空并停止未读轮询
        this.setShoppingCart([]);
        this.setUnreadMessage(0);
        this.stopUnreadPolling();
      } else {
        // 用户已经登录,获取该用户的购物车信息
        this.$axios
          .get("/api/cart/user")
          .then(res => {
            if (res.data.code) {
              // 更新vuex购物车状态
              this.setShoppingCart(res.data.data);
            } else {
              // 提示失败信息
              this.notifyError(res.data.msg);
            }
          })
          .catch(err => {
            return Promise.reject(err);
          });
        // 拉取消息中心未读数并启动轮询（P1：支付/审批后徽标不再需要刷新页面）
        this.refreshUnread();
        this.startUnreadPolling();
      }
    }
  },
  beforeDestroy() {
    this.stopUnreadPolling();
    this.$root.$off("refresh-unread", this.refreshUnread);
  },
  methods: {
    ...mapActions(["setUser", "setShowLogin", "setShoppingCart", "setUnreadMessage"]),
    // 未读数 30s 轮询（修复：原只登录时拉一次，支付/审批产生的新消息徽标永不更新）
    startUnreadPolling() {
      this.stopUnreadPolling();
      this.unreadTimer = setInterval(this.refreshUnread, 30000);
    },
    stopUnreadPolling() {
      if (this.unreadTimer) {
        clearInterval(this.unreadTimer);
        this.unreadTimer = null;
      }
    },
    login() {
      // 点击登录按钮, 通过更改vuex的showLogin值显示登录组件
      this.setShowLogin(true);
    },
    // 顶栏用户下拉命令
    handleUserCommand(command) {
      if (command === "logout") {
        this.logout();
      } else {
        this.$router.push(command);
      }
    },
    // 刷新消息未读数
    refreshUnread() {
      // 匿名/未登录不发请求
      if (!this.getUser) return;
      this.$axios
        .get("/api/order/message/unread")
        .then(res => {
          if (res.data.code) {
            this.setUnreadMessage(res.data.data && res.data.data.count || 0);
          }
        })
        .catch(() => {});
    },
    // 退出登录
    logout() {
      // 先通知后端把 JWT 的 jti 加入黑名单，再清前端登录态
      // token 已过期时该接口 401，catch 静默——用户点退出却收到"请先登录"语义打架
      this.$axios.post("/api/user/logout").catch(() => {});
      // 清空cookie中的token
      this.delCookie("XM_TOKEN");
      // 清空vuex登录信息
      this.setUser("");
      this.setUnreadMessage(0);
      this.notifySucceed("成功退出登录");
      // P1：登出后离开受限页面（原停 /member/* 会残留旧用户数据）
      if (this.$route.path !== "/" && this.$route.matched.some(r => r.meta && r.meta.requireAuth)) {
        this.$router.push("/");
      }
    },
    // 接收注册子组件传过来的数据
    isRegister(val) {
      this.register = val;
    },
    // 点击搜索按钮
    searchClick() {
      if (this.search != "") {
        // P3：跳转独立搜索页（ES/MySQL 双引擎，带高亮）
        this.$router.push({ path: "/search", query: { keyword: this.search } });
        this.search = "";
      }
    },
    getCookie(name) { //获取指定名称的cookie值
      // (^| )name=([^;]*)(;|$),match[0]为与整个正则表达式匹配的字符串，match[i]为正则表达式捕获数组相匹配的数组；
      let arr = document.cookie.match(new RegExp("(^| )"+name+"=([^;]*)(;|$)"));
      if(arr != null) {
        return unescape(arr[2]);
      }
      return null;
    },
    delCookie(name) {
      let exp = new Date();
      exp.setTime(exp.getTime() - 1);
      let cval = this.getCookie(name);
      if (cval != null)
        document.cookie = name + "=" + cval + ";expires=" + exp.toGMTString();
    }
  }
};
</script>

<style>
/* 全局CSS */
* {
  padding: 0;
  margin: 0;
  border: 0;
  list-style: none;
}
#app .el-header {
  padding: 0;
}
#app .el-main {
  min-height: 300px;
  padding: 20px 0;
}
#app .el-footer {
  padding: 0;
}
a,
a:hover {
  text-decoration: none;
}
/* 全局CSS END */

/* 顶部导航栏CSS */
.topbar {
  height: 40px;
  background-color: #3d3d3d;
  margin-bottom: 20px;
}
.topbar .nav {
  width: 1225px;
  margin: 0 auto;
}
.topbar .nav ul {
  float: right;
}
.topbar .nav li {
  float: left;
  height: 40px;
  color: #b0b0b0;
  font-size: 14px;
  text-align: center;
  line-height: 40px;
  margin-left: 20px;
}
.topbar .nav .sep {
  color: #b0b0b0;
  font-size: 12px;
  margin: 0 5px;
}
.topbar .nav li .el-button {
  color: #b0b0b0;
}
.topbar .nav .el-button:hover {
  color: #fff;
}
.topbar .nav li a {
  color: #b0b0b0;
}
.topbar .nav a:hover {
  color: #fff;
}
.topbar .nav .shopCart {
  width: 120px;
  background: #424242;
}
.topbar .nav .shopCart:hover {
  background: #fff;
}
.topbar .nav .shopCart:hover a {
  color: #5b6ef5;
}
.topbar .nav .shopCart-full {
  width: 120px;
  background: #5b6ef5;
}
.topbar .nav .shopCart-full a {
  color: white;
}
.topbar .nav .user-menu-trigger {
  color: #b0b0b0;
  cursor: pointer;
  outline: none;
}
.topbar .nav .user-menu-trigger:hover {
  color: #fff;
}
.topbar .nav .msg-badge {
  display: inline-block;
  min-width: 16px;
  height: 16px;
  line-height: 16px;
  padding: 0 4px;
  margin-left: 4px;
  border-radius: 8px;
  background: #ff4d4f;
  color: #fff;
  font-size: 11px;
  text-align: center;
}
/* 顶部导航栏CSS END */

/* 顶栏容器CSS */
.el-header .el-menu {
  max-width: 1225px;
  margin: 0 auto;
}
.el-header .logo {
  height: 60px;
  float: left;
  margin-right: 100px;
}
.logo a {
  display: flex;
  align-items: center;
  height: 60px;
  text-decoration: none;
}
.logo-badge {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: linear-gradient(135deg, #5b6ef5, #8f6ef5);
  color: #fff;
  font-size: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 10px;
  box-shadow: 0 4px 10px rgba(91, 110, 245, 0.35);
}
.logo-name {
  font-size: 24px;
  font-weight: bold;
  letter-spacing: 2px;
  background: linear-gradient(135deg, #5b6ef5, #8f6ef5);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}
.el-header .el-menu-item {
  font-size: 16px;
  transition: color 0.2s;
}
.el-header .el-menu-item:hover {
  color: #5b6ef5;
}
.el-header .so {
  margin-top: 10px;
  width: 300px;
  float: right;
}
.el-header .so .el-input-group__append .el-button {
  color: #5b6ef5;
}
.el-header .so .el-input-group__append .el-button:hover {
  color: #4753d6;
}
/* 顶栏容器CSS END */

/* 底栏容器CSS */
.footer {
  width: 100%;
  text-align: center;
  background: #2f2f2f;
  padding-bottom: 20px;
}
.footer .ng-promise-box {
  border-bottom: 1px solid #3d3d3d;
  line-height: 145px;
}
.footer .ng-promise-box {
  margin: 0 auto;
  border-bottom: 1px solid #3d3d3d;
  line-height: 145px;
}
.footer .ng-promise-box .ng-promise p a {
  color: #fff;
  font-size: 20px;
  margin-right: 210px;
  padding-left: 44px;
  height: 40px;
  display: inline-block;
  line-height: 40px;
  text-decoration: none;
  background: url("./assets/imgs/us-icon.png") no-repeat left 0;
}
.footer .github {
  height: 50px;
  line-height: 50px;
  margin-top: 20px;
}
.footer .github .github-but {
  width: 50px;
  height: 50px;
  margin: 0 auto;
  background: url("./assets/imgs/github.png") no-repeat;
}
.footer .mod_help {
  text-align: center;
  color: #888888;
}
.footer .mod_help p {
  margin: 20px 0 16px 0;
}

.footer .mod_help p a {
  color: #888888;
  text-decoration: none;
}
.footer .mod_help p a:hover {
  color: #fff;
}
.footer .mod_help p span {
  padding: 0 22px;
}
/* 底栏容器CSS END */
</style>