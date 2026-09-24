<template>
  <div class="ai-chat">
    <!-- 悬浮客服按钮 -->
    <div class="ai-chat-fab" @click="open" title="智能客服">
      <i class="el-icon-chat-dot-round"></i>
      <span class="ai-chat-fab-text">在线客服</span>
    </div>

    <!-- 聊天面板 -->
    <transition name="ai-slide">
      <div class="ai-chat-panel" v-if="visible">
        <div class="ai-chat-header">
          <span>智能客服 · 小智</span>
          <div>
            <el-tooltip content="清空会话" placement="bottom">
              <i class="el-icon-delete" @click="reset"></i>
            </el-tooltip>
            <i class="el-icon-close" @click="close" style="margin-left:12px"></i>
          </div>
        </div>

        <!-- 消息区 -->
        <div class="ai-chat-body" ref="chatBody">
          <div class="ai-chat-welcome">
            您好，我是商城智能客服小智 🤖<br />
            关于商品、订单、物流、售后的任何问题，都可以问我哦～
          </div>
          <div
            v-for="(msg, index) in messages"
            :key="index"
            :class="['ai-chat-msg', msg.role === 'user' ? 'ai-chat-msg-user' : 'ai-chat-msg-bot']"
          >
            <div class="ai-chat-avatar">{{ msg.role === "user" ? "我" : "智" }}</div>
            <div class="ai-chat-bubble">{{ msg.content }}<span v-if="msg.typing" class="ai-chat-cursor">▍</span></div>
          </div>
          <div v-if="waiting && !streaming" class="ai-chat-msg ai-chat-msg-bot">
            <div class="ai-chat-avatar">智</div>
            <div class="ai-chat-bubble">小智正在思考…</div>
          </div>
        </div>

        <!-- 输入区 -->
        <div class="ai-chat-footer">
          <el-input
            v-model="input"
            placeholder="请输入您的问题，Enter 发送"
            size="small"
            :disabled="waiting"
            @keyup.enter.native="send"
          ></el-input>
          <el-button size="small" type="primary" :loading="waiting" @click="send">发送</el-button>
        </div>
      </div>
    </transition>
  </div>
</template>

<script>
export default {
  name: "AiChat",
  data() {
    return {
      visible: false,
      input: "",
      messages: [], // { role: 'user' | 'assistant', content, typing }
      waiting: false,
      streaming: false,
      es: null, // EventSource 实例
      conversationId: null
    };
  },
  created() {
    // 会话 ID 持久化，跨请求保持多轮上下文
    let cid = localStorage.getItem("AI_CHAT_CID");
    if (!cid) {
      cid = "web-" + Date.now() + "-" + Math.floor(Math.random() * 100000);
      localStorage.setItem("AI_CHAT_CID", cid);
    }
    this.conversationId = cid;
  },
  beforeDestroy() {
    this.closeEs();
  },
  methods: {
    open() {
      this.visible = true;
      this.scrollToBottom();
    },
    close() {
      this.visible = false;
    },
    send() {
      const question = this.input.trim();
      if (!question || this.waiting) return;
      this.input = "";
      this.messages.push({ role: "user", content: question });
      this.messages.push({ role: "assistant", content: "", typing: true });
      this.waiting = true;
      this.streaming = false;
      this.scrollToBottom();
      this.openStream(question);
    },
    // SSE 流式接收
    openStream(question) {
      const url =
        "/api/chat/stream?conversationId=" +
        encodeURIComponent(this.conversationId) +
        "&message=" +
        encodeURIComponent(question);
      this.closeEs();
      const es = new EventSource(url);
      this.es = es;
      es.addEventListener("content", e => {
        this.streaming = true;
        const last = this.messages[this.messages.length - 1];
        last.content += e.data;
        this.scrollToBottom();
      });
      es.addEventListener("done", () => {
        this.finishStreaming();
      });
      es.onerror = () => {
        // 异常兜底：已收到部分内容则按正常结束，否则提示错误
        const last = this.messages[this.messages.length - 1];
        if (!this.streaming || !last.content) {
          last.content = last.content || "网络开小差了，请稍后再试～";
        }
        this.finishStreaming();
      };
    },
    finishStreaming() {
      this.closeEs();
      const last = this.messages[this.messages.length - 1];
      if (last) {
        last.typing = false;
        if (!last.content) last.content = "（未收到回复）";
      }
      this.waiting = false;
      this.streaming = false;
      this.scrollToBottom();
    },
    closeEs() {
      if (this.es) {
        this.es.close();
        this.es = null;
      }
    },
    // 清空会话上下文
    reset() {
      this.$axios
        .post("/api/chat/reset", { conversationId: this.conversationId })
        .then(() => {
          this.messages = [];
          const cid = "web-" + Date.now() + "-" + Math.floor(Math.random() * 100000);
          localStorage.setItem("AI_CHAT_CID", cid);
          this.conversationId = cid;
          this.$message.success("会话已清空");
        })
        .catch(() => this.$message.error("清空失败，请重试"));
    },
    scrollToBottom() {
      this.$nextTick(() => {
        const body = this.$refs.chatBody;
        if (body) body.scrollTop = body.scrollHeight;
      });
    }
  }
};
</script>

<style scoped>
/* 悬浮按钮 */
.ai-chat-fab {
  position: fixed;
  right: 24px;
  bottom: 140px;
  z-index: 2000;
  width: 60px;
  padding: 8px 0;
  border-radius: 50% 50% 8px 8px;
  background: #ff6700;
  color: #fff;
  text-align: center;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(255, 103, 0, 0.4);
  transition: all 0.2s;
}
.ai-chat-fab:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(255, 103, 0, 0.55);
}
.ai-chat-fab i {
  font-size: 22px;
  display: block;
}
.ai-chat-fab-text {
  font-size: 12px;
}

/* 面板 */
.ai-chat-panel {
  position: fixed;
  right: 24px;
  bottom: 220px;
  z-index: 2000;
  width: 360px;
  height: 480px;
  border-radius: 12px;
  overflow: hidden;
  background: #fff;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.18);
  display: flex;
  flex-direction: column;
}
.ai-chat-header {
  height: 44px;
  line-height: 44px;
  padding: 0 16px;
  background: linear-gradient(135deg, #ff6700, #ffa45c);
  color: #fff;
  font-size: 14px;
  font-weight: bold;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.ai-chat-header i {
  cursor: pointer;
  font-size: 16px;
}
.ai-chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  background: #f7f7f7;
}
.ai-chat-welcome {
  background: #ffe8d6;
  color: #99601a;
  font-size: 12px;
  line-height: 20px;
  padding: 10px 12px;
  border-radius: 8px;
  margin-bottom: 12px;
}
.ai-chat-msg {
  display: flex;
  margin-bottom: 12px;
  align-items: flex-start;
}
.ai-chat-msg-user {
  flex-direction: row-reverse;
}
.ai-chat-avatar {
  width: 28px;
  height: 28px;
  line-height: 28px;
  border-radius: 50%;
  text-align: center;
  color: #fff;
  font-size: 12px;
  flex-shrink: 0;
}
.ai-chat-msg-bot .ai-chat-avatar {
  background: #ff6700;
}
.ai-chat-msg-user .ai-chat-avatar {
  background: #409eff;
}
.ai-chat-bubble {
  max-width: 250px;
  margin: 0 8px;
  padding: 8px 12px;
  font-size: 13px;
  line-height: 20px;
  border-radius: 10px;
  background: #fff;
  word-break: break-all;
  white-space: pre-wrap;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}
.ai-chat-msg-user .ai-chat-bubble {
  background: #409eff;
  color: #fff;
}
.ai-chat-cursor {
  animation: blink 0.8s infinite;
  color: #ff6700;
}
@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}
.ai-chat-footer {
  padding: 10px;
  border-top: 1px solid #eee;
  display: flex;
}
.ai-chat-footer .el-input {
  margin-right: 8px;
}

/* 面板弹出动画 */
.ai-slide-enter-active,
.ai-slide-leave-active {
  transition: all 0.25s ease;
}
.ai-slide-enter,
.ai-slide-leave-to {
  opacity: 0;
  transform: translateY(16px);
}
</style>