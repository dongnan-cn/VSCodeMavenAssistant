import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  // 配置为相对路径，适配VSCode Webview
  base: './',
  // 构建配置
  build: {
    // 确保生成相对路径的资源
    assetsDir: 'assets',
    rollupOptions: {
      output: {
        // 使用相对路径
        assetFileNames: 'assets/[name]-[hash][extname]',
        chunkFileNames: 'assets/[name]-[hash].js',
        entryFileNames: 'assets/[name]-[hash].js'
      }
    }
  }
})
