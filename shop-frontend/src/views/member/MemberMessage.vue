<template>
  <div class="member-message">
    <div class="page-title">
      <i class="el-icon-message"></i> 消息中心
      <el-button size="small" type="text" class="read-all-btn" @click="readAll" v-if="hasUnread">
        全部标为已读
      </el-button>
    </div>

    <div v-if="messages.length > 0" class="msg-list">
      <div
        v-for="item in messages"
        :key="item.id"
        class="msg-item"
        :class="{ unread: item.isRead === 0 }"
        @click="markRead(item)"
      >
        <div :class="['msg-icon', 'icon-' + item.type]">
          <i :class="typeIcon(item.type)"></i>
        </div>
        <div class="msg-main">
          <div class="msg-title">
            {{ item.title }}
            <span v-if="item.isRead === 0" class="unread-dot"></span>
          </div>
          <div class="msg-content">{{ item.content }}</div>
          <div class="msg-time">{{ item.createdTime | dateFormat }}</div>
        </div>
        <el-button size="mini" type="text" class="msg-del" @click.stop="remove(item)">删除</el-button>
      </div>
    </div>
    <div v-else class="empty-tip">暂无消息</div>
  </div>
</template>

<script>
import { mapActions } from "vuex";

export default {
  name: "MemberMessage",
  data() {
    return {
      messages: []
    };
  },
  computed: {
    hasUnread() {
      return this.messages.some(m => m.isRead === 0);
    }
  },
  created() {
    this.load();
  },
  methods: {
    ...mapActions(["setUnreadMessage"]),
    load() {
      this.$axios
        .get("/api/order/message")
        .then(res => {
          if (res.data.code) {
            this.messages = res.data.data || [];
          } else {
            this.notifyError(res.data.msg || "消息加载失败");
          }
        })
        .catch(() => this.notifyError("网络异常，消息加载失败"));
    },
    markRead(item) {
      if (item.isRead === 1) return;
      this.$axios
        .post(`/api/order/message/read/${item.id}`)
        .then(res => {
          if (res.data.code) {
            item.isRead = 1;
            this.refreshUnread();
          }
        })
        .catch(() => {});
    },
    readAll() {
      this.$axios
        .post("/api/order/message/readAll")
        .then(res => {
          if (res.data.code) {
            this.messages.forEach(m => (m.isRead = 1));
            this.setUnreadMessage(0);
            this.notifySucceed(res.data.msg);
          }
        })
        .catch(() => this.notifyError("网络异常，请重试"));
    },
    remove(item) {
      // 删除前确认（原一键直删且静默失败零感知）
      this.$confirm(`确定删除消息「${item.title}」吗？`, "删除确认", {
        confirmButtonText: "确定删除",
        cancelButtonText: "取消",
        type: "warning"
      })
        .then(() => {
          this.$axios
            .delete(`/api/order/message/${item.id}`)
            .then(res => {
              if (res.data.code) {
                this.notifySucceed(res.data.msg);
                this.load();
                this.refreshUnread();
              } else {
                this.notifyError(res.data.msg);
              }
            })
            .catch(() => this.notifyError("网络异常，删除失败请重试"));
        })
        .catch(() => {});
    },
    refreshUnread() {
      this.$axios
        .get("/api/order/message/unread")
        .then(res => {
          if (res.data.code) {
            this.setUnreadMessage(res.data.data && res.data.data.count || 0);
          }
        })
        .catch(() => {});
    },
    typeIcon(type) {
      const map = {
        order_paid: "el-icon-shopping-cart-full",
        order_cancelled: "el-icon-close",
        order_created: "el-icon-s-order",
        order_done: "el-icon-circle-check",
        aftersale: "el-icon-service"
      };
      return map[type] || "el-icon-bell";
    }
  }
};
</script>

<style scoped>
.member-message {
  padding: 24px 32px;
}
.page-title {
  font-size: 20px;
  font-weight: 600;
  color: #424242;
  margin-bottom: 16px;
}
.page-title i {
  color: #5b6ef5;
  margin-right: 8px;
}
.read-all-btn {
  margin-left: 16px;
}
.msg-list {
  display: flex;
  flex-direction: column;
}
.msg-item {
  display: flex;
  align-items: flex-start;
  padding: 16px 8px;
  border-bottom: 1px solid #f5f5f8;
  cursor: pointer;
  transition: background 0.2s;
}
.msg-item:hover {
  background: #fafafc;
}
.msg-item.unread {
  background: #f7f8ff;
}
.msg-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  margin-right: 14px;
  flex-shrink: 0;
}
.icon-order_paid {
  color: #22c58b;
  background: rgba(34, 197, 139, 0.12);
}
.icon-order_cancelled {
  color: #9a9aae;
  background: #f1f1f6;
}
.icon-aftersale {
  color: #ff9c40;
  background: rgba(255, 156, 64, 0.12);
}
.msg-main {
  flex: 1;
}
.msg-title {
  font-size: 15px;
  font-weight: 600;
  color: #2b2b38;
  margin-bottom: 4px;
}
.unread-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ff4d4f;
  margin-left: 6px;
}
.msg-content {
  font-size: 13px;
  color: #6d6d80;
  line-height: 20px;
  margin-bottom: 4px;
}
.msg-time {
  font-size: 12px;
  color: #b0b0b0;
}
.msg-del {
  margin-top: 6px;
}
.empty-tip {
  text-align: center;
  color: #b0b0b0;
  padding: 60px 0;
  font-size: 14px;
}
</style>
