import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import commonjs from 'vite-plugin-commonjs';
import { CodeInspectorPlugin } from 'code-inspector-plugin';

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  const isDev = mode === 'development';

  return {
    envPrefix: ['CONSOLE_', 'VITE_'],
    build: {
      rollupOptions: {
        maxParallelFileOps: 1, // 限制并行文件操作数为1
      },
      commonjsOptions: {
        strictRequires: true, // 强制所有 CommonJS 模块都被严格处理
      },
    },
    plugins: [
      commonjs(),
      isDev &&
        CodeInspectorPlugin({
          bundler: 'vite',
          editor: 'cursor',
        }),
      react(),
    ].filter(Boolean),
    resolve: {
      alias: {
        '@': '/src',
      },
    },
    server: {
      port: 3000,
      proxy: {
        // 本地开发环境代理配置 - 所有请求转发到本地后端
        '/xingchen-api': {
          target: 'http://localhost:8080', // 本地后端地址
          changeOrigin: true,
          headers: {
            Connection: 'keep-alive',
            'Keep-Alive': 'timeout=30, max=100',
          },
          rewrite: path => path.replace(/^\/xingchen-api/, ''),
        },
        '/chat-': {
          target: 'http://localhost:8080', // 本地后端地址
          changeOrigin: true,
          headers: {
            Connection: 'keep-alive',
            'Keep-Alive': 'timeout=30, max=100',
          },
        },
        '/workflow': {
          target: 'http://localhost:8080', // 本地后端地址
          changeOrigin: true,
          headers: {
            Connection: 'keep-alive',
            'Keep-Alive': 'timeout=30, max=100',
          },
        },
      },
    },
  };
});
