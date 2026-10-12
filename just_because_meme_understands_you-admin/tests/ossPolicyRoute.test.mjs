import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const ossApiSource = readFileSync(new URL('../src/api/oss.js', import.meta.url), 'utf8')
  .replace(/^import .*$/gm, '')
  .replace(/^export /gm, '')

test('requests an OSS upload ticket and confirms the uploaded asset', async () => {
  const requests = []
  const uploads = []
  const ticket = {
    host: 'https://oss.example.com', // 测试上传票据提供的 OSS 上传地址。
    publicBaseUrl: 'https://cdn.example.com', // 测试上传完成后返回的公开访问地址。
    objectKey: 'tmp/home/image.png', // 测试上传票据锁定的 OSS 对象键。
    assetId: 'asset-123', // 测试上传资产确认接口使用的资产 ID。
    policy: 'test-policy', // 测试 PostObject 上传策略。
    accessKeyId: 'test-access-key', // 测试 OSS 临时访问密钥 ID。
    signature: 'test-signature', // 测试上传策略签名。
  }
  const uploadToOss = new Function('request', 'axios', `${ossApiSource}\nreturn uploadToOss`)(
    (url, options) => {
      requests.push({ url, options })
      return Promise.resolve({ data: url === '/uploads/tickets' ? ticket : null })
    },
    {
      post: (url, body, options) => {
        uploads.push({ url, body, options })
        return Promise.resolve({ status: 200 })
      },
    },
  )

  const file = new Blob(['image'], { type: 'image/png' })
  const publicUrl = await uploadToOss(file, 'home', 'image.png')

  assert.equal(publicUrl, 'https://cdn.example.com/tmp/home/image.png')
  assert.deepEqual(requests, [
    {
      url: '/uploads/tickets', // 上传票据接口路径。
      options: {
        method: 'POST', // 创建上传票据使用 POST 请求。
        apiSurface: 'user', // 上传票据接口注册在用户端 /v1 路由下。
        body: {
          scene: 'home', // 首页图片上传场景。
          originalFilename: 'image.png', // 与图片 MIME 类型匹配的文件名。
          contentType: 'image/png', // 图片内容类型。
          fileSize: 5, // 图片文件字节数。
        },
      },
    },
    {
      url: '/uploads/asset-123/confirm', // OSS 上传完成后确认资产的接口路径。
      options: {
        method: 'POST', // 确认上传结果使用 POST 请求。
        apiSurface: 'user', // 上传确认接口注册在用户端 /v1 路由下。
      },
    },
  ])
  assert.equal(uploads.length, 1)
  assert.equal(uploads[0].url, 'https://oss.example.com')
  assert.equal(uploads[0].options.withCredentials, false)
})
