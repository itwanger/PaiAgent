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
          // target: 'http://10.1.207.26:8080/', //太龙本地环境 智能体广场
          // target: 'http://10.1.205.25:25000/', //志远本地环境 插件广场
          // target: 'http://10.1.200.141:8080/', //志远本地环境 插件广场
          // target: 'https://agent.xfyun.cn',
          // target: 'http://pre-agent.xfyun.cn',
          // target: "http://dev-agent.xfyun.cn",
          // target: 'http://dev-agent.xfyun.cn',
          // target: 'http://test-agent.xfyun.cn',
          // target: 'http://dev-agent.xfyun.cn',
          // target: 'http://172.29.201.92:8081',
          // target: 'http://10.1.196.7:8080', // 旭东本机ip，调试用
          // target: 'http://10.1.196.7:8080', // 旭东
          // target: 'http://10.1.203.40:8080', // 彭颖
          // target: 'http://10.1.200.151:8080', // 超睿
          // target: 'http://172.29.202.54:8080', // 联调服务器地址
          //  target: 'http://172.29.201.92:8080', // 测试服务器地址
          // 本地开发：通过 nginx 代理到 console-hub
          target: 'http://localhost',
          changeOrigin: true,
          headers: {
            Connection: 'keep-alive',
            'Keep-Alive': 'timeout=30, max=100',
          },
        },
        '/chat-': {
          // target: "http://10.7.104.244:8080",
          //target: "http://agent.xfyun.cn",
          // target: 'http://pre-agent.xfyun.cn',
          // target: "http://dev-xingchen.xfyun.cn",
          // target: 'http://test-agent.xfyun.cn',
          // target: 'http://dev-agent.xfyun.cn',
          // target: 'http://10.1.203.40:8080', // 彭颖
          // target: 'http://172.29.201.92:8081',
          // target: 'http://10.1.196.7:8080', // 旭东本机ip，调试用
          changeOrigin: true,
          headers: {
            Connection: 'keep-alive',
            'Keep-Alive': 'timeout=30, max=100',
          },
        },
        '/workflow': {
          // target: 'http://172.29.202.54:8080', // 联调服务器地址
          target: 'http://172.29.201.92:8080', // 测试服务器地址
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
