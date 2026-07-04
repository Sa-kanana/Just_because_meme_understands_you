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
  const suffix = pickSuffix(fileName || (file.name || ''), file.type)
  const objectName = `${dir}${Date.now()}_${randomToken()}${suffix}`
  const objectKey = objectName

  const formData = new FormData()
  formData.append('key', objectKey)
  formData.append('policy', policy.policy)
  formData.append('OSSAccessKeyId', policy.accessKeyId)
  formData.append('signature', policy.signature)
  formData.append('success_action_status', '200')
  formData.append('file', file, fileName || objectName)

  await axios.post(host, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000,
  })

  return `${host}/${objectKey}`
}

function pickSuffix(name, mime) {
  const fromName = name && name.includes('.') ? name.slice(name.lastIndexOf('.')) : ''
  if (fromName) return fromName.toLowerCase()
  const map = {
    'image/jpeg': '.jpg',
    'image/png': '.png',
    'image/webp': '.webp',
    'image/gif': '.gif',
  }
  return map[mime] || ''
}

function randomToken() {
  return Math.random().toString(36).slice(2, 10)
}
