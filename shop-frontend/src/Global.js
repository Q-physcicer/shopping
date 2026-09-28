/* * @Description: 全局变量 */
exports.install = function (Vue) {
  // 图片静态资源前缀：开发环境用老资源服务器；生产构建走同源（Nginx 托管 public/imgs）；
  // 需要指向其它资源服务器时用环境变量 IMG_TARGET 覆盖（vue.config.js 或 .env 文件）
  Vue.prototype.$target = process.env.VUE_APP_IMG_TARGET || "/";
  
  // 封装提示成功的弹出框
  Vue.prototype.notifySucceed = function (msg) {
    this.$notify({
      title: "成功",
      message: msg,
      type: "success",
      offset: 100
    });
  };
  // 封装提示失败的弹出框
  Vue.prototype.notifyError = function (msg) {
    this.$notify.error({
      title: "错误",
      message: msg,
      offset: 100
    });
  };
}