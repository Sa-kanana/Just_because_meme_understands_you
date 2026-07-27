import { request } from './request'

/**
 * 获取帮助中心文档
 * GET /help
 * @returns {Promise<{ title: string, content: string, version: string }>}
 */
export function getHelpDoc() {
  return request('/help', { method: 'GET' }).then((res) => {
    if (res && Number(res.code) === 1 && res.data) {
      const data = res.data
      return {
        title: data.title != null ? String(data.title) : '帮助中心',
        content: data.content != null ? String(data.content) : '',
        version: data.version != null ? String(data.version) : '',
      }
    }
    throw new Error((res && (res.message || res.msg)) || '加载帮助文档失败')
  })
}
