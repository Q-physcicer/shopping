<template>
  <div class="member-aftersale">
    <div class="page-title">
      <i class="el-icon-service"></i> 售后服务
    </div>

    <!-- 申请售后 -->
    <div class="apply-card">
      <div class="form-title">申请售后（仅退款）</div>
      <div class="apply-tip">已支付订单支付后 7 天内可申请仅退款；待支付/已取消订单不可申请。</div>
      <el-form label-width="80px" class="apply-form">
        <el-form-item label="选择订单">
          <el-select
            v-model="apply.orderKey"
            placeholder="请选择要售后的订单商品"
            :disabled="payableRows.length === 0"
            style="width: 100%"
          >
            <el-option
              v-for="row in payableRows"
              :key="row.orderId + '-' + row.productId"
              :label="row.productName + '（订单 ' + row.orderId + '）'"
              :value="row.orderId + '|' + row.productId"
            ></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="申请理由">
          <el-input
            type="textarea"
            :rows="3"
            v-model="apply.reason"
            maxlength="200"
            placeholder="请说明申请售后的理由"
          ></el-input>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="submitApply">提交申请</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 我的售后列表 -->
    <div class="list-card">
      <div class="form-title">我的售后</div>
      <div v-if="afterSales.length > 0" class="after-list">
        <div class="after-item" v-for="item in afterSales" :key="item.id">
          <img v-imgerror :src="$target + item.productPicture" class="after-img" />
          <div class="after-main">
            <div class="after-name">{{ item.productName }}</div>
            <div class="after-meta">
              售后单号：{{ item.aftersaleId }} · 订单号：{{ item.orderId }}
            </div>
            <div class="after-reason">理由：{{ item.reason }}</div>
            <div v-if="item.rejectReason" class="after-reject">拒绝理由：{{ item.rejectReason }}</div>
          </div>
          <div class="after-right">
            <div class="after-amount">￥{{ item.refundAmount }}</div>
            <span :class="['status-tag', statusClass(item.status)]">{{ statusText(item.status) }}</span>
          </div>
        </div>
      </div>
      <div v-else class="empty-tip">暂无售后申请记录</div>
    </div>
  </div>
</template>

<script>
export default {
  name: "MemberAftersale",
  data() {
    return {
      afterSales: [],
      payableRows: [], // 可售后的订单行（已支付 status 1/3 且在 7 天窗口内）
      apply: { orderKey: "", reason: "" },
      submitting: false,
      APPLY_WINDOW_MS: 7 * 24 * 3600 * 1000
    };
  },
  created() {
    this.loadAfterSales();
    this.loadPayableOrders();
  },
  methods: {
    loadAfterSales() {
      this.$axios
        .get("/api/order/aftersale/my")
        .then(res => {
          if (res.data.code) {
            this.afterSales = res.data.data || [];
          } else {
            this.notifyError(res.data.msg || "售后记录加载失败");
          }
        })
        .catch(() => this.notifyError("网络异常，售后记录加载失败"));
    },
    loadPayableOrders() {
      this.$axios
        .get("/api/order")
        .then(res => {
          if (!res.data.code) return;
          const groups = res.data.data || [];
          const rows = [];
          const now = Date.now();
          groups.forEach(group => {
            (group || []).forEach(row => {
              // P1：同时过滤 7 天窗口（原只滤状态，超期订单选了提交才被后端拒，反馈滞后）
              if ((row.status === 1 || row.status === 3)
                  && row.payTime && now - row.payTime <= this.APPLY_WINDOW_MS) {
                rows.push(row);
              }
            });
          });
          this.payableRows = rows;
        })
        .catch(() => this.notifyError("网络异常，订单加载失败"));
    },
    submitApply() {
      if (this.submitting) return;
      if (!this.apply.orderKey) {
        this.notifyError("请选择要售后的订单商品");
        return;
      }
      if (!this.apply.reason) {
        this.notifyError("请填写申请理由");
        return;
      }
      this.submitting = true;
      const parts = this.apply.orderKey.split("|");
      this.$axios
        .post("/api/order/aftersale/apply", {
          orderId: parts[0],
          productId: Number(parts[1]),
          reason: this.apply.reason
        })
        .then(res => {
          if (res.data.code) {
            this.notifySucceed(res.data.msg);
            this.apply = { orderKey: "", reason: "" };
            this.loadAfterSales();
          } else {
            this.notifyError(res.data.msg);
          }
        })
        .catch(() => this.notifyError("网络异常，提交失败请重试"))
        .finally(() => {
          this.submitting = false;
        });
    },
    statusText(status) {
      return status === 0 ? "待处理" : status === 1 ? "已同意退款" : "已拒绝";
    },
    statusClass(status) {
      return status === 0 ? "status-pending" : status === 1 ? "status-paid" : "status-cancelled";
    }
  }
};
</script>

<style scoped>
.member-aftersale {
  padding: 24px 32px;
}
.page-title {
  font-size: 20px;
  font-weight: 600;
  color: #424242;
  margin-bottom: 24px;
}
.page-title i {
  color: #5b6ef5;
  margin-right: 8px;
}
.apply-card,
.list-card {
  border: 1px solid #f0f0f4;
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 24px;
}
.form-title {
  font-size: 16px;
  font-weight: 600;
  color: #424242;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f0f4;
}
.apply-tip {
  font-size: 13px;
  color: #9a9aae;
  margin-bottom: 16px;
}
.apply-form {
  max-width: 560px;
}
.after-item {
  display: flex;
  align-items: center;
  padding: 16px 0;
  border-bottom: 1px solid #f5f5f8;
}
.after-item:last-child {
  border-bottom: none;
}
.after-img {
  width: 64px;
  height: 64px;
  border-radius: 8px;
  object-fit: cover;
  margin-right: 16px;
}
.after-main {
  flex: 1;
}
.after-name {
  font-size: 15px;
  color: #2b2b38;
  font-weight: 600;
  margin-bottom: 6px;
}
.after-meta {
  font-size: 12px;
  color: #9a9aae;
  margin-bottom: 4px;
}
.after-reason {
  font-size: 13px;
  color: #6d6d80;
}
.after-reject {
  font-size: 13px;
  color: #ff4d4f;
  margin-top: 4px;
}
.after-right {
  text-align: right;
}
.after-amount {
  color: #ff5f5f;
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 6px;
}
.status-tag {
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 10px;
}
.status-pending {
  color: #ff9c40;
  background: rgba(255, 156, 64, 0.12);
}
.status-paid {
  color: #22c58b;
  background: rgba(34, 197, 139, 0.12);
}
.status-cancelled {
  color: #9a9aae;
  background: #f1f1f6;
}
.empty-tip {
  text-align: center;
  color: #b0b0b0;
  padding: 40px 0;
  font-size: 14px;
}
</style>
