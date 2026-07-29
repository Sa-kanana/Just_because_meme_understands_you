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
