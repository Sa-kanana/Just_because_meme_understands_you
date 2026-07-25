const path = require('path')
const { defineConfig } = require('@vue/cli-service')

const fs = require('fs')

module.exports = defineConfig({
  transpileDependencies: true,
  chainWebpack: (config) => {
    config.plugin('html').tap((args) => {
      args[0].favicon = path.resolve(__dirname, 'public/icon.svg')
      return args
    })
  },
  configureWebpack: {
    resolve: {
      alias: {
        pinia: path.resolve(__dirname, 'node_modules/pinia/dist/pinia.cjs'),
      },
    },
  },
  devServer: {
    port: 80,
    setupMiddlewares(middlewares, devServer) {
      const iconPath = path.resolve(__dirname, 'public/icon.svg')
      devServer.app.get('/favicon.ico', (_req, res) => {
        res.type('image/svg+xml')
        fs.createReadStream(iconPath).pipe(res)
      })
      return middlewares
    },
    proxy: {
      // 将所有 /api 开头的请求转发到后端 8080，并去掉路径中的 /api
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        pathRewrite: { '^/api': '' },
        // AI 流式：避免代理缓冲整段 SSE
        onProxyRes(proxyRes) {
          const ct = String(proxyRes.headers['content-type'] || '')
          if (ct.includes('text/event-stream')) {
            proxyRes.headers['cache-control'] = 'no-cache, no-transform'
            proxyRes.headers['x-accel-buffering'] = 'no'
          }
        },
      },
    },
  },
})
