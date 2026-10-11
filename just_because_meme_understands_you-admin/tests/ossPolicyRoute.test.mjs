import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const ossApiSource = readFileSync(new URL('../src/api/oss.js', import.meta.url), 'utf8')
  .replace(/^import .*$/gm, '')
  .replace(/^export /gm, '')

test('requests OSS upload policy from the user API route', async () => {
  const requests = []
  const getOssPolicy = new Function('request', `${ossApiSource}\nreturn getOssPolicy`)(
    (url, options) => {
      requests.push({ url, options })
      return Promise.resolve({ code: 1, data: { dir: 'tmp/home/' } })
    },
  )

  await getOssPolicy('home')

  assert.deepEqual(requests, [{
    url: '/uploads/policies?fileType=home', // 上传凭证接口路径及首页图片类型参数。
    options: {
      method: 'GET', // 获取上传凭证使用 GET 请求。
      apiSurface: 'user', // 后端凭证接口注册在用户端 /v1 路由下。
    },
  }])
})
