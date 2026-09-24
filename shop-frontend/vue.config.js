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
        target: 'http://localhost:3000/', // 本地后端地址
        // target: 'http://47.95.254.97:3000/', // 线上后端地址
        changeOrigin: true, // 允许跨域
        pathRewrite: {
          '^/api': ''
        }
      }
    }
  }
})