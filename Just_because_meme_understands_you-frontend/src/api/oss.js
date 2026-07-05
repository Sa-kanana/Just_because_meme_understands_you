import axios from 'axios'
import { request } from './request'

/**
 * 获取 OSS 前端直传凭证
 * GET /oss/policy?fileType=avatar|meme|comment|home
 * @param {string} fileType 文件类型，决定 OSS 存放目录
 * @returns {Promise<{accessKeyId, policy, signature, dir, host, expire}>}
 */
export function getOssPolicy(fileType = '') {
  const query = new URLSearchParams()
  if (fileType) query.set('fileType', String(fileType))
  const qs = query.toString()
  const url = `/oss/policy${qs ? `?${qs}` : ''}`
  return request(url, { method: 'GET' }).then((res) => {
    if (res && Number(res.code) === 1 && res.data) {
      return res.data
    }
    throw new Error((res && (res.message || res.msg)) || '获取上传凭证失败')
  })
}

/**
 * 直传文件到 OSS（PostObject 表单模式）
 * @param {File|Blob} file 文件
 * @param {string} fileType 文件类型，用于分目录
 * @param {string} [fileName] 指定文件名（含扩展名），不传则自动生成
 * @returns {Promise<string>} 上传后的图片完整 URL
 */
export async function uploadToOss(file, fileType = 'common', fileName) {
  if (!file) {
    throw new Error('请选择要上传的文件')
  }
  const policy = await getOssPolicy(fileType)
  const host = String(policy.host || '').replace(/\/$/, '')
  if (!host) {
    throw new Error('上传凭证缺少 host')
  }
  const dir = String(policy.dir || '')
  // 强制图片 Content-Type，与后端 policy 的 starts-with $Content-Type image/ 对应
  const contentType = (file.type && file.type.startsWith('image/')) ? file.type : 'image/jpeg'
  const suffix = pickSuffix(fileName || (file.name || ''), file.type)
  const objectName = `${dir}${Date.now()}_${randomToken()}${suffix}`
  const objectKey = objectName

  const formData = new FormData()
  formData.append('key', objectKey)
  formData.append('policy', policy.policy)
  formData.append('OSSAccessKeyId', policy.accessKeyId)
  // OSS PostObject 规范字段名为 Signature（首字母大写），小写会导致签名校验失败
  formData.append('Signature', policy.signature)
  formData.append('success_action_status', '200')
  // object Content-Type 固定为图片，防止上传 HTML/SVG 被当作网页渲染导致 XSS
  formData.append('Content-Type', contentType)
  // 与后端 policy condition 对应，上传的 object 设为公共读，URL 可直接访问
  formData.append('x-oss-object-acl', 'public-read')
  formData.append('file', file, fileName || objectName)

  try {
    await axios.post(host, formData, {
      // 不显式设 Content-Type，交由浏览器自动带 boundary，避免 boundary 缺失
      timeout: 60000,
      withCredentials: false,
    })
  } catch (err) {
    // 调试信息：便于在浏览器控制台定位 OSS 返回的真实错误
    console.error('[OSS upload] host=', host, 'objectKey=', objectKey, 'err=', err)
    // 浏览器跨域/CORS 拦截或网络不通时 axios 抛 Network Error
    const isNetwork = !err.response && (err.message === 'Network Error' || err.code === 'ERR_NETWORK')
    if (isNetwork) {
      throw new Error('图片直传 OSS 失败：可能是 OSS 未配置 CORS，或 host 不可达。请检查 OSS Bucket 跨域规则。')
    }
    const ossMsg = err.response && err.response.data
      ? (typeof err.response.data === 'string' ? err.response.data : (err.response.data.Message || err.response.data.error || ''))
      : ''
    throw new Error(ossMsg || err.message || '图片上传到 OSS 失败')
  }

  return `${host}/${objectKey}`
}

function pickSuffix(name, mime) {
  // 扩展名白名单，非图片扩展名一律回退为 .jpg，防止上传 .html/.svg 等可执行内容
  const allowed = ['.jpg', '.jpeg', '.png', '.webp', '.gif']
  const fromName = name && name.includes('.') ? name.slice(name.lastIndexOf('.')).toLowerCase() : ''
  if (allowed.includes(fromName)) return fromName
  const map = {
    'image/jpeg': '.jpg',
    'image/png': '.png',
    'image/webp': '.webp',
    'image/gif': '.gif',
  }
  return map[mime] || '.jpg'
}

function randomToken() {
  return Math.random().toString(36).slice(2, 10)
}
