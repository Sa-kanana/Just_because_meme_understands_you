const { defineConfig } = require('@vue/cli-service')

module.exports = defineConfig({
  // 生产挂在 Nginx /admin/ 子路径；本地 serve 仍用根路径
  publicPath: process.env.NODE_ENV === 'production' ? '/admin/' : '/',
  transpileDependencies: true,
  devServer: {
    port: 5174,
    // 内网穿透时放行公网 Host，避免 Invalid Host header
    allowedHosts: 'all',
    client: {
      webSocketURL: 'auto://0.0.0.0:0/ws',
      overlay: {
        warnings: false, // 开发时保持只展示编译错误和运行时错误的默认体验。
        errors: true, // 编译错误仍显示在页面覆盖层中。
        runtimeErrors: (error) => error.message !== 'ResizeObserver loop completed with undelivered notifications.', // 仅忽略浏览器的 ResizeObserver 布局通知警告。
      },
    },
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        pathRewrite: { '^/api': '' },
        // 浏览器会带 Origin: localhost:5174；转发时去掉，避免后端 CORS 白名单未含管理端端口时直接 403
        onProxyReq(proxyReq) {
          proxyReq.removeHeader('origin')
          proxyReq.removeHeader('referer')
        },
      },
    },
  },
})
