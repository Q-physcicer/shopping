<template>
  <div class="member-center">
    <div class="member-wrapper">
      <!-- 左侧导航 -->
      <div class="member-side">
        <div class="member-avatar">
          <div class="avatar-badge">
            <i class="el-icon-user"></i>
          </div>
          <div class="avatar-name">{{ getUser ? getUser.username : '' }}</div>
        </div>
        <ul class="member-nav">
          <li
            v-for="item in menus"
            :key="item.path"
            :class="{ active: isActive(item.path) }"
            @click="go(item)"
          >
            <i :class="item.icon"></i>
            <span>{{ item.label }}</span>
            <span v-if="item.path === '/member/message' && getUnreadMessage > 0" class="nav-badge">
              {{ getUnreadMessage }}
            </span>
          </li>
        </ul>
      </div>
      <!-- 右侧内容 -->
      <div class="member-content">
        <router-view></router-view>
      </div>
    </div>
  </div>
</template>

<script>
import { mapGetters } from "vuex";

export default {
  name: "MemberCenter",
  data() {
    return {
      menus: [
        { label: "个人资料", icon: "el-icon-user", path: "/member/profile" },
        { label: "我的订单", icon: "el-icon-s-order", path: "/order" },
        { label: "我的收藏", icon: "el-icon-collection-tag", path: "/collect" },
        { label: "售后服务", icon: "el-icon-service", path: "/member/aftersale" },
        { label: "收货地址", icon: "el-icon-location-outline", path: "/member/address" },
        { label: "消息中心", icon: "el-icon-message", path: "/member/message" }
      ]
    };
  },
  computed: {
    ...mapGetters(["getUser", "getUnreadMessage"])
  },
  methods: {
    isActive(path) {
      // 我的订单/我的收藏是独立路由，按当前路径精确匹配
      if (path === "/order" || path === "/collect") {
        return this.$route.path === path;
      }
      return this.$route.path === path;
    },
    go(item) {
      if (this.$route.path !== item.path) {
        this.$router.push(item.path);
      }
    }
  }
};
</script>

<style scoped>
.member-center {
  background-color: #f5f5f5;
  min-height: 600px;
  padding: 20px 0;
}
.member-wrapper {
  width: 1225px;
  margin: 0 auto;
  display: flex;
  align-items: flex-start;
}
.member-side {
  width: 200px;
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}
.member-avatar {
  padding: 24px 0 20px;
  text-align: center;
  background: linear-gradient(135deg, #5b6ef5, #8f6ef5);
  color: #fff;
}
.avatar-badge {
  width: 56px;
  height: 56px;
  margin: 0 auto 10px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.25);
  font-size: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.avatar-name {
  font-size: 16px;
  font-weight: 600;
}
.member-nav {
  padding: 8px 0;
}
.member-nav li {
  height: 46px;
  line-height: 46px;
  padding: 0 20px;
  font-size: 14px;
  color: #424242;
  cursor: pointer;
  display: flex;
  align-items: center;
  transition: all 0.2s;
}
.member-nav li i {
  margin-right: 10px;
  font-size: 18px;
}
.member-nav li:hover {
  color: #5b6ef5;
}
.member-nav li.active {
  color: #5b6ef5;
  background: rgba(91, 110, 245, 0.08);
  border-right: 3px solid #5b6ef5;
  font-weight: 600;
}
.nav-badge {
  margin-left: auto;
  min-width: 16px;
  height: 16px;
  line-height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: #ff4d4f;
  color: #fff;
  font-size: 11px;
  text-align: center;
}
.member-content {
  flex: 1;
  margin-left: 20px;
  background: #fff;
  border-radius: 12px;
  min-height: 500px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}
</style>
