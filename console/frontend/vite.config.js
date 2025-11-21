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
      strictPort: false,  // 允许端口自动递增
      proxy: {
        // ⚠️ 注意：proxy 匹配是按顺序的，把更具体的路径放前面

        // 1. workflow 服务（必须放在 /xingchen-api 前面）
        '/workflow': {
          target: 'http://localhost:7880',
          changeOrigin: true,
          headers: {
            Connection: 'keep-alive',
            'Keep-Alive': 'timeout=1800, max=100',
          },
        },
        
        // 2. chat-message SSE 接口
        '/chat-message': {
          target: 'http://localhost:8080',
          changeOrigin: true,
          headers: {
            Connection: 'keep-alive',
            'Keep-Alive': 'timeout=1800, max=100',
          },
        },
        
        // 3. xingchen-api 通用接口（放在最后，作为兜底）
        '/xingchen-api': {
          target: 'http://localhost:8080',
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/xingchen-api/, ''),
          headers: {
            Connection: 'keep-alive',
            'Keep-Alive': 'timeout=1800, max=100',
          },
        },
      },
    },
  };
});
