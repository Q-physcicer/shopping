import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// 管理端开发服务器：7090，/api 代理到本地网关 8080（去前缀，与购物端一致）
export default defineConfig({
  plugins: [react()],
  server: {
    port: 7090,
    proxy: {
      '/api': {
        target: 'http://localhost:8080/',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      },
      // P2：dev 期共享购物端的静态图片（管理端无 public/imgs，缩略图/轮播预览原本全裂）
      '/imgs': {
        target: 'http://localhost:7080/',
        changeOrigin: true
      }
    }
  }
});