const { defineConfig } = require('@vue/cli-service')
module.exports = defineConfig({
  transpileDependencies: true,
  publicPath: './',
  devServer: {
    port: 7080, // 明确指定端口
    open: true,
    compress: false, // 关闭 gzip 压缩，避免 SSE 流式响应被缓冲
    proxy: {
      '/api': {
        target: 'http://localhost:8080/', // 网关地址（生产改为部署机地址或走 Nginx 反代）
        changeOrigin: true, // 允许跨域
        pathRewrite: {
          '^/api': ''
        }
      }
    }
  }
})