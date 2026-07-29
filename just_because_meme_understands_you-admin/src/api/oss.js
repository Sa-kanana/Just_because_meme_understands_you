import axios from 'axios'
import { request } from './request'

export function getOssPolicy(fileType = '') {
  const query = new URLSearchParams()
  if (fileType) query.set('fileType', String(fileType))
  const qs = query.toString()
  return request(`/oss/policy${qs ? `?${qs}` : ''}`, { method: 'GET' }).then((res) => {
    if (res && Number(res.code) === 1 && res.data) return res.data
    throw new Error((res && (res.message || res.msg)) || '获取上传凭证失败')
  })
}

const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/gif']
const MAX_IMAGE_BYTES = 10 * 1024 * 1024

export async function uploadToOss(file, fileType = 'home', fileName) {
  if (!file) throw new Error('请选择要上传的文件')
  if (!ALLOWED_IMAGE_TYPES.includes(file.type)) {
    throw new Error('仅支持 JPG、PNG、WebP、GIF 图片')
  }
  if (file.size > MAX_IMAGE_BYTES) {
    throw new Error('图片不能超过 10MB')
  }
  const policy = await getOssPolicy(fileType)
  const uploadHost = String(policy.host || '').replace(/\/$/, '')
  const publicBaseUrl = String(policy.publicBaseUrl || policy.host || '').replace(/\/$/, '')
  if (!uploadHost) throw new Error('上传凭证缺少 host')
  const dir = String(policy.dir || '')
  const contentType = ALLOWED_IMAGE_TYPES.includes(file.type) ? file.type : 'image/jpeg'
  const fromName =
    (fileName || file.name || '').includes('.')
      ? (fileName || file.name).slice((fileName || file.name).lastIndexOf('.')).toLowerCase()
      : ''
  const allowed = ['.jpg', '.jpeg', '.png', '.webp', '.gif']
  const map = {
    'image/jpeg': '.jpg',
    'image/png': '.png',
    'image/webp': '.webp',
    'image/gif': '.gif',
  }
  const suffix = allowed.includes(fromName) ? fromName : map[file.type] || '.jpg'
  const objectKey = `${dir}${Date.now()}_${Math.random().toString(36).slice(2, 10)}${suffix}`

  const formData = new FormData()
  formData.append('key', objectKey)
  formData.append('policy', policy.policy)
  formData.append('OSSAccessKeyId', policy.accessKeyId)
  formData.append('Signature', policy.signature)
  formData.append('success_action_status', '200')
  formData.append('Content-Type', contentType)
  formData.append('file', file, fileName || objectKey)

  await axios.post(uploadHost, formData, { timeout: 60000, withCredentials: false })
  return `${publicBaseUrl}/${objectKey}`
}
