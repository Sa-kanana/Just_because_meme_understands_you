const path = require('path')
const { defineConfig } = require('@vue/cli-service')

module.exports = defineConfig({
  transpileDependencies: true,
  configureWebpack: {
    resolve: {
      alias: {
        pinia: path.resolve(__dirname, 'node_modules/pinia/dist/pinia.cjs'),
      },
    },
  },
  devServer: {
    port: 80,
    proxy: {
      // 将所有 /api 开头的请求转发到后端 8080，并去掉路径中的 /api
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        pathRewrite: { '^/api': '' },
      },
    },
  },
})
