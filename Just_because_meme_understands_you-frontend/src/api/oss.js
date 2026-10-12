import axios from 'axios'
import { request } from './request'

/** 中文注释：浏览器允许选择并上传的图片 MIME 类型。 */
const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/gif']

/** 中文注释：浏览器端预校验的单张图片最大字节数，与服务端上传策略保持一致。 */
const MAX_IMAGE_BYTES = 10 * 1024 * 1024

/** 中文注释：根据图片 MIME 类型生成与服务端校验相符的原始文件扩展名。 */
const MIME_TYPE_EXTENSIONS = {
  'image/jpeg': '.jpg',
  'image/png': '.png',
  'image/webp': '.webp',
  'image/gif': '.gif',
}

/**
 * 中文注释：申请上传票据、使用服务端对象键直传 OSS，并在成功后确认资产。
 * @param {File|Blob} file 用户选择的图片文件。
 * @param {string} fileType 上传业务场景，例如 avatar、meme 或 comment。
 * @param {string} [fileName] 可选的展示文件名；扩展名会按 MIME 类型规范化。
 * @returns {Promise<string>} 已确认上传资产对应的公开图片 URL。
 */
export async function uploadToOss(file, fileType = 'meme', fileName) {
  if (!file) {
    throw new Error('请选择要上传的文件')
  }
  assertImageFile(file)

  const originalFilename = normalizeUploadFilename(fileName || file.name, file.type)
  const ticketResponse = await request('/uploads/tickets', {
    method: 'POST',
    body: {
      scene: fileType, // 上传票据对应的业务场景。
      originalFilename, // 服务端记录并校验扩展名的原始文件名。
      contentType: file.type, // 文件 MIME 类型，必须与扩展名相匹配。
      fileSize: file.size, // 客户端声明的字节数，服务端会再核验 OSS 元数据。
    },
  })
  const ticket = ticketResponse && ticketResponse.data
  const uploadHost = String((ticket && ticket.host) || '').replace(/\/$/, '')
  const publicBaseUrl = String((ticket && (ticket.publicBaseUrl || ticket.host)) || '').replace(/\/$/, '')
  const objectKey = String((ticket && ticket.objectKey) || '')
  if (!uploadHost || !objectKey || ticket.assetId == null) {
    throw new Error('上传凭证缺少 host、objectKey 或 assetId')
  }

  const formData = new FormData()
  formData.append('key', objectKey) // 使用服务端签名策略锁定的唯一对象键。
  formData.append('policy', ticket.policy) // OSS PostObject 上传策略。
  formData.append('OSSAccessKeyId', ticket.accessKeyId) // 签发票据时返回的 AccessKey ID。
  formData.append('Signature', ticket.signature) // 与 policy 对应的 OSS 签名。
  formData.append('success_action_status', '200') // 上传成功后要求 OSS 返回 HTTP 200。
  formData.append('Content-Type', file.type) // 必须与上传票据中签名的 MIME 类型一致。
  formData.append('file', file, originalFilename) // 图片文件及规范化后的文件名。

  try {
    await axios.post(uploadHost, formData, {
      timeout: 60000, // OSS 直传请求的超时时间，单位为毫秒。
      withCredentials: false, // OSS 直传不携带本站 Cookie。
    })
  } catch (error) {
    const isNetworkError = !error.response && (error.message === 'Network Error' || error.code === 'ERR_NETWORK')
    if (isNetworkError) {
      throw new Error('图片直传 OSS 失败：请检查 OSS Bucket 跨域规则或上传域名是否可达。')
    }
    const responseBody = error.response && error.response.data
    const ossMessage = responseBody
      ? (typeof responseBody === 'string' ? responseBody : (responseBody.Message || responseBody.error || ''))
      : ''
    throw new Error(ossMessage || error.message || '图片上传到 OSS 失败')
  }

  await request(`/uploads/${encodeURIComponent(ticket.assetId)}/confirm`, {
    method: 'POST',
  })

  return `${publicBaseUrl}/${objectKey}`
}

/** 中文注释：在请求票据前拦截空文件、非图片 MIME 和超过 10 MiB 的图片。 */
function assertImageFile(file) {
  if (!ALLOWED_IMAGE_TYPES.includes(file.type)) {
    throw new Error('仅支持 JPG、PNG、WebP、GIF 图片')
  }
  if (!Number.isFinite(file.size) || file.size <= 0 || file.size > MAX_IMAGE_BYTES) {
    throw new Error('图片大小必须大于 0 且不能超过 10 MiB')
  }
}

/** 中文注释：保留文件名主体，并依据 MIME 类型设置服务端允许的扩展名。 */
function normalizeUploadFilename(name, mimeType) {
  const pathSafeName = String(name || 'upload').replace(/\\/g, '/').split('/').pop()
  const lastDotIndex = pathSafeName.lastIndexOf('.')
  const baseName = lastDotIndex > 0 ? pathSafeName.slice(0, lastDotIndex) : pathSafeName
  return `${baseName || 'upload'}${MIME_TYPE_EXTENSIONS[mimeType] || '.jpg'}`
}
