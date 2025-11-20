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
        // 代理规则
        // '/api': {
        //   target: 'https://dev-agent.xfyun.cn/',
        //   // target: 'http://172.30.189.254:8080/xingchen-api/',
        //   //target: 'https://pre.iflyaicloud.com/',
        //   changeOrigin: true
        // },
        //代理规则
        '/xingchen-api': {
          // 本地开发：通过 nginx 代理到 console-hub
          target: 'http://localhost',
          changeOrigin: true,
          headers: {
            Connection: 'keep-alive',
            'Keep-Alive': 'timeout=30, max=100',
          },
        },
        '/chat-': {
          target: 'http://localhost',
          changeOrigin: true,
          headers: {
            Connection: 'keep-alive',
            'Keep-Alive': 'timeout=30, max=100',
          },
        },
        '/workflow': {
          target: 'http://localhost',
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
