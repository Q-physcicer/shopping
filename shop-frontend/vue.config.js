const { defineConfig } = require('@vue/cli-service')
module.exports = defineConfig({
  transpileDependencies: true,
  publicPath: './',
  devServer: {
    port: 7080, // 明确指定端口
    open: true,
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