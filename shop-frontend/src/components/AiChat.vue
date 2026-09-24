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
          <span class="ai-chat-title"><i class="el-icon-service"></i>智能客服 · 小智</span>
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
            您好，我是星选商场的智能客服小智 🤖<br />
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
            <div class="ai-chat-bubble ai-chat-thinking">小智正在思考</div>
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
  padding: 9px 0;
  border-radius: 50% 50% 14px 14px;
  background: linear-gradient(135deg, #5b6ef5, #8f6ef5);
  color: #fff;
  text-align: center;
  cursor: pointer;
  box-shadow: 0 6px 16px rgba(91, 110, 245, 0.45);
  transition: all 0.25s ease;
  animation: fab-pulse 3s infinite;
}
.ai-chat-fab:hover {
  transform: translateY(-3px) scale(1.05);
  box-shadow: 0 10px 22px rgba(91, 110, 245, 0.55);
}
.ai-chat-fab i {
  font-size: 22px;
  display: block;
}
.ai-chat-fab-text {
  font-size: 12px;
  font-weight: 500;
}
@keyframes fab-pulse {
  0% { box-shadow: 0 6px 16px rgba(91, 110, 245, 0.45); }
  50% { box-shadow: 0 6px 22px rgba(91, 110, 245, 0.75); }
  100% { box-shadow: 0 6px 16px rgba(91, 110, 245, 0.45); }
}

/* 面板 */
.ai-chat-panel {
  position: fixed;
  right: 24px;
  bottom: 220px;
  z-index: 2000;
  width: 360px;
  height: 500px;
  border-radius: 16px;
  overflow: hidden;
  background: #fff;
  box-shadow: 0 12px 40px rgba(38, 48, 96, 0.22);
  display: flex;
  flex-direction: column;
  border: 1px solid rgba(91, 110, 245, 0.12);
}
.ai-chat-header {
  height: 52px;
  line-height: 52px;
  padding: 0 16px;
  background: linear-gradient(135deg, #5b6ef5, #8f6ef5);
  color: #fff;
  font-size: 14px;
  font-weight: bold;
  letter-spacing: 0.5px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.ai-chat-header .ai-chat-title {
  display: flex;
  align-items: center;
}
.ai-chat-header .ai-chat-title i {
  font-size: 20px;
  margin-right: 8px;
}
.ai-chat-header i {
  cursor: pointer;
  font-size: 16px;
  opacity: 0.85;
  transition: opacity 0.2s;
}
.ai-chat-header i:hover {
  opacity: 1;
}
.ai-chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 14px 12px;
  background:
    radial-gradient(circle at 12% 8%, rgba(91, 110, 245, 0.05), transparent 45%),
    radial-gradient(circle at 88% 92%, rgba(143, 110, 245, 0.05), transparent 45%),
    #f6f7fb;
}
/* 细滚动条 */
.ai-chat-body::-webkit-scrollbar {
  width: 5px;
}
.ai-chat-body::-webkit-scrollbar-thumb {
  background: rgba(91, 110, 245, 0.35);
  border-radius: 3px;
}
.ai-chat-welcome {
  background: linear-gradient(135deg, rgba(91, 110, 245, 0.12), rgba(143, 110, 245, 0.12));
  color: #4753d6;
  font-size: 12px;
  line-height: 20px;
  padding: 12px 14px;
  border-radius: 12px;
  border: 1px dashed rgba(91, 110, 245, 0.35);
  margin-bottom: 14px;
}
.ai-chat-msg {
  display: flex;
  margin-bottom: 14px;
  align-items: flex-start;
  animation: msg-in 0.25s ease;
}
.ai-chat-msg-user {
  flex-direction: row-reverse;
}
@keyframes msg-in {
  from { opacity: 0; transform: translateY(6px); }
  to { opacity: 1; transform: translateY(0); }
}
.ai-chat-avatar {
  width: 30px;
  height: 30px;
  line-height: 30px;
  border-radius: 50%;
  text-align: center;
  color: #fff;
  font-size: 12px;
  flex-shrink: 0;
}
.ai-chat-msg-bot .ai-chat-avatar {
  background: linear-gradient(135deg, #5b6ef5, #8f6ef5);
  box-shadow: 0 2px 6px rgba(91, 110, 245, 0.4);
}
.ai-chat-msg-user .ai-chat-avatar {
  background: linear-gradient(135deg, #ff7e5f, #feb47b);
  box-shadow: 0 2px 6px rgba(255, 126, 95, 0.4);
}
.ai-chat-bubble {
  max-width: 250px;
  margin: 0 8px;
  padding: 9px 13px;
  font-size: 13px;
  line-height: 21px;
  border-radius: 4px 14px 14px 14px;
  background: #fff;
  color: #333;
  word-break: break-all;
  white-space: pre-wrap;
  box-shadow: 0 2px 8px rgba(38, 48, 96, 0.1);
  border: 1px solid rgba(91, 110, 245, 0.08);
}
.ai-chat-msg-user .ai-chat-bubble {
  border-radius: 14px 4px 14px 14px;
  background: linear-gradient(135deg, #5b6ef5, #8f6ef5);
  border: none;
  color: #fff;
  box-shadow: 0 2px 10px rgba(91, 110, 245, 0.35);
}
.ai-chat-cursor {
  animation: blink 0.8s infinite;
  color: #5b6ef5;
  font-weight: bold;
}
@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}
/* 思考中动画 */
.ai-chat-thinking::after {
  content: "…";
  animation: dots 1.2s infinite;
}
@keyframes dots {
  0% { content: "."; }
  33% { content: ".."; }
  66% { content: "..."; }
}
.ai-chat-footer {
  padding: 10px 12px;
  border-top: 1px solid #f0f1f6;
  background: #fff;
  display: flex;
  align-items: center;
}
.ai-chat-footer .el-input {
  margin-right: 8px;
}
.ai-chat-footer .el-input >>> .el-input__inner {
  border-radius: 20px;
}
.ai-chat-footer .el-button--primary {
  border-radius: 20px;
  background: linear-gradient(135deg, #5b6ef5, #8f6ef5);
  border: none;
  box-shadow: 0 3px 8px rgba(91, 110, 245, 0.35);
}

/* 面板弹出动画 */
.ai-slide-enter-active,
.ai-slide-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}
.ai-slide-enter,
.ai-slide-leave-to {
  opacity: 0;
  transform: translateY(20px) scale(0.96);
}
</style>