<template>
  <div class="member-address">
    <div class="page-title">
      <i class="el-icon-location-outline"></i> 收货地址
    </div>

    <div class="addr-actions">
      <el-button type="primary" size="small" @click="openAdd">+ 新增地址</el-button>
    </div>

    <div v-if="addresses.length > 0" class="addr-list">
      <div
        v-for="item in addresses"
        :key="item.id"
        class="addr-item"
        :class="{ 'is-default': item.isDefault === 1 }"
      >
        <div class="addr-info">
          <div class="addr-line1">
            <span class="addr-name">{{ item.receiverName }}</span>
            <span class="addr-phone">{{ item.receiverPhone }}</span>
            <span v-if="item.isDefault === 1" class="default-tag">默认</span>
          </div>
          <div class="addr-detail">
            {{ item.province || '' }}{{ item.city || '' }}{{ item.district || '' }} {{ item.detailAddress }}
          </div>
        </div>
        <div class="addr-ops">
          <el-button v-if="item.isDefault !== 1" size="mini" @click="setDefault(item.id)">设为默认</el-button>
          <el-button size="mini" @click="openEdit(item)">编辑</el-button>
          <el-button size="mini" type="danger" @click="remove(item)">删除</el-button>
        </div>
      </div>
    </div>
    <div v-else class="empty-tip">暂无收货地址，请点击右上角「新增地址」添加</div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog :title="editing ? '编辑地址' : '新增地址'" :visible.sync="dialogVisible" width="480px">
      <el-form :model="form" :rules="rules" ref="addrForm" label-width="80px">
        <el-form-item label="收货人" prop="receiverName">
          <el-input v-model="form.receiverName" maxlength="50" show-word-limit placeholder="请输入收货人姓名"></el-input>
        </el-form-item>
        <el-form-item label="手机号" prop="receiverPhone">
          <el-input v-model="form.receiverPhone" maxlength="11" placeholder="请输入手机号"></el-input>
        </el-form-item>
        <el-form-item label="省">
          <el-input v-model="form.province" maxlength="20" placeholder="如：湖北省"></el-input>
        </el-form-item>
        <el-form-item label="市">
          <el-input v-model="form.city" maxlength="20" placeholder="如：黄冈市"></el-input>
        </el-form-item>
        <el-form-item label="区/县">
          <el-input v-model="form.district" maxlength="20" placeholder="如：红安县"></el-input>
        </el-form-item>
        <el-form-item label="详细地址" prop="detailAddress">
          <el-input type="textarea" :rows="2" v-model="form.detailAddress" maxlength="200" show-word-limit
                    placeholder="请输入详细地址"></el-input>
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="form.isDefault" :active-value="1" :inactive-value="0"></el-switch>
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
export default {
  name: "MemberAddress",
  data() {
    return {
      addresses: [],
      dialogVisible: false,
      editing: false,
      submitting: false,
      form: this.emptyForm(),
      rules: {
        receiverName: [
          { required: true, message: "请填写收货人姓名", trigger: "blur" },
          { max: 50, message: "姓名不能超过 50 字", trigger: "blur" }
        ],
        receiverPhone: [
          { required: true, message: "请填写手机号", trigger: "blur" },
          { pattern: /^1\d{10}$/, message: "手机号格式不正确", trigger: "blur" }
        ],
        detailAddress: [
          { required: true, message: "请填写详细地址", trigger: "blur" },
          { max: 200, message: "详细地址不能超过 200 字", trigger: "blur" }
        ]
      }
    };
  },
  created() {
    this.load();
  },
  methods: {
    emptyForm() {
      return {
        id: null,
        receiverName: "",
        receiverPhone: "",
        province: "",
        city: "",
        district: "",
        detailAddress: "",
        isDefault: 0
      };
    },
    load() {
      this.$axios
        .get("/api/user/address")
        .then(res => {
          if (res.data.code) {
            this.addresses = res.data.data || [];
          } else {
            this.notifyError(res.data.msg || "地址加载失败");
          }
        })
        .catch(() => this.notifyError("网络异常，地址加载失败"));
    },
    resetForm() {
      this.form = this.emptyForm();
      this.$nextTick(() => {
        if (this.$refs.addrForm) this.$refs.addrForm.clearValidate();
      });
    },
    openAdd() {
      this.editing = false;
      this.resetForm();
      this.dialogVisible = true;
    },
    openEdit(item) {
      this.editing = true;
      this.form = { ...item };
      this.$nextTick(() => {
        if (this.$refs.addrForm) this.$refs.addrForm.clearValidate();
      });
      this.dialogVisible = true;
    },
    submit() {
      this.$refs.addrForm.validate(valid => {
        if (!valid) return;
        if (this.submitting) return;
        this.submitting = true;
        const payload = { ...this.form };
        const req = this.editing
          ? this.$axios.put(`/api/user/address/${this.form.id}`, payload)
          : this.$axios.post("/api/user/address", payload);
        req
          .then(res => {
            if (res.data.code) {
              this.notifySucceed(res.data.msg);
              this.dialogVisible = false;
              this.load();
            } else {
              this.notifyError(res.data.msg);
            }
          })
          .catch(() => this.notifyError("网络异常，保存失败请重试"))
          .finally(() => {
            this.submitting = false;
          });
      });
    },
    setDefault(id) {
      this.$axios
        .put(`/api/user/address/default/${id}`)
        .then(res => {
          if (res.data.code) {
            this.notifySucceed(res.data.msg);
            this.load();
          } else {
            this.notifyError(res.data.msg);
          }
        })
        .catch(() => this.notifyError("网络异常，设置失败请重试"));
    },
    remove(item) {
      // 物理删除不可恢复：必须二次确认（原一键直删，误触丢地址）
      this.$confirm(`确定删除「${item.receiverName}」的这条地址吗？删除后不可恢复。`, "删除确认", {
        confirmButtonText: "确定删除",
        cancelButtonText: "取消",
        type: "warning"
      })
        .then(() => {
          this.$axios
            .delete(`/api/user/address/${item.id}`)
            .then(res => {
              if (res.data.code) {
                this.notifySucceed(res.data.msg);
                this.load();
              } else {
                this.notifyError(res.data.msg);
              }
            })
            .catch(() => this.notifyError("网络异常，删除失败请重试"));
        })
        .catch(() => {});
    }
  }
};
</script>

<style scoped>
.member-address {
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
.addr-actions {
  margin-bottom: 16px;
}
.addr-list {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}
.addr-item {
  width: 340px;
  border: 1px solid #e8e8ee;
  border-radius: 12px;
  padding: 16px;
  position: relative;
}
.addr-item.is-default {
  border-color: #5b6ef5;
}
.addr-name {
  font-size: 16px;
  font-weight: 600;
  color: #2b2b38;
  margin-right: 10px;
}
.addr-phone {
  font-size: 14px;
  color: #6d6d80;
}
.default-tag {
  float: right;
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 10px;
  color: #5b6ef5;
  background: rgba(91, 110, 245, 0.12);
}
.addr-detail {
  margin-top: 10px;
  font-size: 14px;
  color: #424242;
  line-height: 22px;
  min-height: 44px;
}
.addr-ops {
  margin-top: 12px;
  text-align: right;
}
.empty-tip {
  text-align: center;
  color: #b0b0b0;
  padding: 60px 0;
  font-size: 14px;
}
</style>
