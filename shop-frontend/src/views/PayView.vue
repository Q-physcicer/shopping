<template>
  <!-- 模拟收银台（P6）：展示金额 → 模拟支付 → 轮询订单状态 → 引导回订单页 -->
  <main class="pay">
    <div class="cashier" v-if="loaded">
      <div class="cashier-card">
        <div class="brand">🛍️ 星选商城 · 收银台</div>
        <div class="amount-title">订单金额</div>
        <div class="amount">￥{{ amount }}</div>
        <div class="meta">
          <p>订单号：{{ orderId }}</p>
          <p>共 {{ items }} 件商品</p>
        </div>
        <!-- P0-6：收件信息快照（老单无快照时不渲染） -->
        <div class="receiver" v-if="receiverName">
          <p class="receiver-line"><i class="el-icon-location-outline"></i> {{ receiverName }} {{ receiverPhone }}</p>
          <p class="receiver-line addr">{{ receiverAddress }}</p>
        </div>

        <!-- 支付中：二维码样式占位 + 轮询提示 -->
        <div class="qr" v-if="paying">
          <div class="qr-inner">
            <i class="el-icon-loading"></i>
          </div>
          <p class="qr-tip">正在确认支付…</p>
        </div>

        <!-- 待支付 -->
        <div class="actions" v-else-if="status === 0">
          <el-button type="primary" size="medium" class="btn-pay" @click="doPay">
            <i class="el-icon-wallet"></i> 模拟支付 ￥{{ amount }}
          </el-button>
          <el-button size="medium" @click="$router.push('/order')">返回订单</el-button>
          <p class="tip">15 秒内未完成将页面提示失效；订单创建 30 分钟未支付将自动取消并回滚库存</p>
        </div>

        <!-- 已支付 -->
        <div class="result result-success" v-else-if="status === 1">
          <i class="el-icon-success"></i>
          <p>支付成功</p>
          <el-button type="primary" size="medium" @click="$router.push('/order')">查看我的订单</el-button>
        </div>

        <!-- 已取消/已完成等 -->
        <div class="result result-cancel" v-else>
          <i class="el-icon-remove-outline"></i>
          <p>{{ status === 2 ? '订单已取消（超时未支付）' : '订单已完成' }}</p>
          <el-button size="medium" @click="$router.push('/order')">返回订单</el-button>
        </div>
      </div>
    </div>
  </main>
</template>

<script>
export default {
  name: "PayView",
  data() {
    return {
      orderId: "",
      amount: 0,
      items: 0,
      status: null,
      payedNo: "",
      paying: false,
      loaded: false,
      receiverName: "",
      receiverPhone: "",
      receiverAddress: "",
      pollTimer: null,
      pollTimeout: null
    };
  },
  created() {
    this.orderId = this.$route.params.orderId;
    this.load();
  },
  // P2：keep-alive 下离开页面必须停轮询（原缺陷：后台空转，回页状态不同步）
  deactivated() {
    this.stopPolling();
  },
  beforeDestroy() {
    this.stopPolling();
  },
  methods: {
    stopPolling() {
      if (this.pollTimer) clearInterval(this.pollTimer);
      if (this.pollTimeout) clearTimeout(this.pollTimeout);
      this.pollTimer = null;
      this.pollTimeout = null;
    },
    load() {
      this.$axios
        .get(`/api/pay/mock/${this.orderId}`)
        .then(res => {
          if (res.data.code === 1) {
            this.amount = res.data.data.amount;
            this.items = res.data.data.items;
            this.status = res.data.data.status;
            // P0-6：收件信息快照（老单可能为 null）
            this.receiverName = res.data.data.receiverName || "";
            this.receiverPhone = res.data.data.receiverPhone || "";
            this.receiverAddress = res.data.data.receiverAddress || "";
          } else {
            this.$message.error(res.data.msg || "订单不存在");
            this.$router.push("/order");
          }
        })
        .catch(() => {
          this.$message.error("网络异常，请稍后重试");
        })
        .finally(() => (this.loaded = true));
    },
    doPay() {
      this.paying = true;
      this.$axios
        .post(`/api/pay/mock/${this.orderId}`)
        .then(res => {
          if (res.data.code === 1) {
            this.payedNo = (res.data.data || {}).payNo;
            this.pollStatus();
          } else {
            this.paying = false;
            this.status = 2;
            this.$notify.error({ title: "支付失败", message: res.data.msg || "订单不可支付" });
          }
        })
        .catch(() => {
          this.paying = false;
          this.$notify.error({ title: "支付失败", message: "网络异常，请重试" });
        });
    },
    // 轮询订单状态（模拟回调确认）
    pollStatus() {
      this.stopPolling();
      this.pollTimer = setInterval(() => {
        this.$axios.get(`/api/pay/mock/${this.orderId}`).then(res => {
          if (res.data.code === 1 && res.data.data.status !== 0) {
            this.stopPolling();
            this.status = res.data.data.status;
            this.paying = false;
            if (this.status === 1) {
              this.$notify.success({ title: "支付成功", message: "订单已支付，感谢您的购买～" });
              // 支付产生站内信：立即触发一次未读徽标刷新
              this.$root.$emit("refresh-unread");
            }
          }
        });
      }, 1000);
      // 最多轮询 10 秒兜底退出
      this.pollTimeout = setTimeout(() => {
        this.stopPolling();
        this.paying = false;
        if (this.status === 0) this.load();
      }, 10000);
    }
  }
};
</script>

<style scoped>
.pay { background: linear-gradient(135deg, #f6f7fb 0%, #ece9ff 100%); min-height: 100vh;
  display: flex; align-items: center; justify-content: center; }
.cashier-card { width: 420px; background: #fff; border-radius: 18px; padding: 36px 32px;
  box-shadow: 0 20px 60px rgba(91,110,245,.15); text-align: center; }
.brand { font-size: 15px; color: #5b6ef5; font-weight: 600; margin-bottom: 22px; }
.amount-title { color: #8c8ca6; font-size: 13px; }
.amount { font-size: 44px; font-weight: 700; color: #2b2b38; margin: 6px 0 4px; }
.meta { color: #b6b6c8; font-size: 12px; line-height: 1.9; margin-bottom: 14px; }
.receiver { border-top: 1px dashed #ececf3; margin: 0 auto 22px; padding-top: 10px; max-width: 320px; }
.receiver-line { color: #8c8ca6; font-size: 12px; line-height: 1.8; margin: 0; }
.receiver-line i { color: #5b6ef5; margin-right: 4px; }
.receiver-line.addr { color: #b6b6c8; }
.actions { display: flex; flex-direction: column; gap: 12px; }
.btn-pay { width: 100%; font-size: 16px; height: 44px; border-radius: 22px; }
.tip { color: #b6b6c8; font-size: 12px; line-height: 1.7; margin-top: 8px; }
.qr { padding: 10px 0 4px; }
.qr-inner { width: 150px; height: 150px; margin: 0 auto; border: 2px dashed #5b6ef5;
  border-radius: 14px; display: flex; align-items: center; justify-content: center; font-size: 34px; color: #5b6ef5; }
.qr-tip { color: #8c8ca6; font-size: 13px; margin-top: 10px; }
.result { padding: 8px 0 4px; }
.result i { font-size: 56px; }
.result-success i { color: #22c58b; }
.result-cancel i { color: #9a9aae; }
.result p { font-size: 18px; margin: 12px 0 18px; color: #2b2b38; }
</style>