<template>
  <div class="member-profile">
    <div class="page-title">
      <i class="el-icon-user"></i> 个人资料
    </div>

    <div class="profile-card">
      <div class="profile-info">
        <div class="info-avatar">
          <i class="el-icon-user-solid"></i>
        </div>
        <div class="info-fields">
          <div class="field-row">
            <span class="field-label">用户名</span>
            <span class="field-value">{{ user.username }}</span>
          </div>
          <div class="field-row">
            <span class="field-label">角色</span>
            <span class="field-value">
              <span class="role-tag">{{ user.role === 'ADMIN' ? '管理员' : '普通用户' }}</span>
            </span>
          </div>
          <div class="field-row">
            <span class="field-label">手机号</span>
            <span class="field-value">{{ user.userPhoneNumber || '未绑定' }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 修改手机号 -->
    <div class="form-card">
      <div class="form-title">修改手机号</div>
      <el-form :model="phoneForm" ref="phoneForm" label-width="80px" class="form-body">
        <el-form-item label="手机号">
          <el-input v-model="phoneForm.phone" placeholder="请输入新手机号" maxlength="20"></el-input>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="savingPhone" @click="savePhone">保存</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 修改密码 -->
    <div class="form-card">
      <div class="form-title">修改密码</div>
      <el-form :model="pwdForm" ref="pwdForm" label-width="80px" class="form-body">
        <el-form-item label="旧密码">
          <el-input type="password" v-model="pwdForm.oldPassword" placeholder="请输入旧密码"></el-input>
        </el-form-item>
        <el-form-item label="新密码">
          <el-input type="password" v-model="pwdForm.newPassword" placeholder="至少 6 位"></el-input>
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input type="password" v-model="pwdForm.confirmPassword" placeholder="请再次输入新密码"></el-input>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="savingPwd" @click="savePassword">修改密码</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script>
import { mapGetters } from "vuex";

export default {
  name: "MemberProfile",
  data() {
    return {
      phoneForm: { phone: "" },
      pwdForm: { oldPassword: "", newPassword: "", confirmPassword: "" },
      savingPhone: false,
      savingPwd: false
    };
  },
  computed: {
    ...mapGetters(["getUser"]),
    user() {
      return this.getUser || {};
    }
  },
  created() {
    // 进入页面刷新最新用户信息
    this.$axios.get("/api/user/token").then(res => {
      if (res.data.code) {
        this.$store.dispatch("setUser", res.data.data);
        this.phoneForm.phone = res.data.data.userPhoneNumber || "";
      }
    });
  },
  methods: {
    savePhone() {
      if (this.savingPhone) return;
      const phone = (this.phoneForm.phone || "").trim();
      if (phone && !/^1\d{10}$/.test(phone)) {
        this.notifyError("手机号格式不正确");
        return;
      }
      this.savingPhone = true;
      this.$axios
        .put("/api/user/me", { userPhoneNumber: phone })
        .then(res => {
          if (res.data.code) {
            this.notifySucceed(res.data.msg);
            this.$axios.get("/api/user/token").then(r => {
              if (r.data.code) this.$store.dispatch("setUser", r.data.data);
            });
          } else {
            this.notifyError(res.data.msg);
          }
        })
        .catch(() => this.notifyError("网络异常，保存失败请重试"))
        .finally(() => {
          this.savingPhone = false;
        });
    },
    savePassword() {
      if (this.savingPwd) return;
      if (!this.pwdForm.oldPassword || !this.pwdForm.newPassword) {
        this.notifyError("请填写完整密码信息");
        return;
      }
      if (this.pwdForm.newPassword.length < 6) {
        this.notifyError("新密码至少 6 位");
        return;
      }
      if (this.pwdForm.newPassword !== this.pwdForm.confirmPassword) {
        this.notifyError("两次输入的新密码不一致");
        return;
      }
      this.savingPwd = true;
      this.$axios
        .put("/api/user/password", {
          oldPassword: this.pwdForm.oldPassword,
          newPassword: this.pwdForm.newPassword
        })
        .then(res => {
          if (res.data.code) {
            this.notifySucceed(res.data.msg);
            this.pwdForm = { oldPassword: "", newPassword: "", confirmPassword: "" };
          } else {
            this.notifyError(res.data.msg);
          }
        })
        .catch(() => this.notifyError("网络异常，修改失败请重试"))
        .finally(() => {
          this.savingPwd = false;
        });
    }
  }
};
</script>

<style scoped>
.member-profile {
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
.profile-card {
  background: #fafafc;
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 24px;
}
.profile-info {
  display: flex;
  align-items: center;
}
.info-avatar {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: linear-gradient(135deg, #5b6ef5, #8f6ef5);
  color: #fff;
  font-size: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 28px;
}
.info-fields {
  flex: 1;
}
.field-row {
  padding: 6px 0;
  font-size: 14px;
}
.field-label {
  display: inline-block;
  width: 70px;
  color: #9a9aae;
}
.field-value {
  color: #2b2b38;
}
.role-tag {
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 10px;
  color: #5b6ef5;
  background: rgba(91, 110, 245, 0.12);
}
.form-card {
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
.form-body {
  max-width: 420px;
}
</style>
