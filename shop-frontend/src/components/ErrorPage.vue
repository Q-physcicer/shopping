<!-- * @Description: 错误页面展示（500/异常兜底） -->
<template>
  <div class="error">
    <div class="main">
      <div class="error-inner">
        <h2>出错了 :(</h2>
        <p>服务暂时不可用或发生了异常，请稍后重试</p>
        <el-button type="primary" round @click="$router.push('/')">返回首页</el-button>
        <el-button round @click="$router.back()">返回上一页</el-button>
      </div>
    </div>
  </div>
</template>
<script>
export default {
  name: "ErrorPage"
};
</script>
<style>
.error {
  height: 500px;
  width: 1225px;
  margin: 0 auto;
}
.error .main {
  margin: 0 362.5px;
  height: 500px;
  background: url(../assets/imgs/error.png) no-repeat;
  position: relative;
}
.error .error-inner {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  top: 240px;
  width: 420px;
  text-align: left;
  background: rgba(255, 255, 255, 0.92);
  padding: 20px 24px;
  border-radius: 12px;
}
.error .error-inner h2 {
  font-size: 30px;
  color: #424242;
  margin-bottom: 10px;
}
.error .error-inner p {
  font-size: 14px;
  color: #757575;
  margin-bottom: 20px;
}
</style>
