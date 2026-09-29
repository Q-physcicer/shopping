<template>
  <div class="notfound">
    <div class="nf-card">
      <div class="nf-code">404</div>
      <div class="nf-text">页面不存在或已被移除</div>
      <div class="nf-sub">检查一下网址是否输入正确</div>
      <el-button type="primary" round @click="$router.push('/')">返回首页</el-button>
      <el-button round @click="$router.push('/goods')">去逛逛</el-button>
    </div>
  </div>
</template>

<script>
export default {
  name: "NotFound"
};
</script>

<style scoped>
.notfound {
  min-height: 60vh;
  display: flex;
  align-items: center;
  justify-content: center;
}
.nf-card {
  text-align: center;
  padding: 60px 80px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 10px 40px rgba(91, 110, 245, 0.08);
}
.nf-code {
  font-size: 84px;
  font-weight: 700;
  background: linear-gradient(135deg, #5b6ef5, #8f6ef5);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  line-height: 1;
}
.nf-text {
  font-size: 18px;
  color: #2b2b38;
  margin: 18px 0 6px;
}
.nf-sub {
  font-size: 13px;
  color: #9a9aae;
  margin-bottom: 24px;
}
</style>
