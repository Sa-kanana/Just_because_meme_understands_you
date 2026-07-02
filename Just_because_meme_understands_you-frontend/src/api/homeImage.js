/**
 * 首页轮播图接口
 * 接口定义：GET /image，按 sort_order、create_time 排序，仅展示 status=1 的项
 * @see https://api.apifox.com/temp-links/api/423242827
 */
import { request } from './request'

/**
 * @typedef {Object} HomeImage
 * @property {number} [id] - 主键ID
 * @property {string} [title] - 轮播图标题（悬停显示）
 * @property {string} img_url - 图片地址
 * @property {number} target_type - 0 无跳转，1 内部文章/梗ID，2 外部链接
 * @property {string} [target_value] - 跳转目标（文章ID或URL）
 * @property {number} [sort_order] - 排序权重
 * @property {number} status - 0 下线，1 上线
 * @property {string} [create_time]
 * @property {string} [update_time]
 */

/** 将后端可能返回的驼峰字段转为与数据库一致的 snake_case，便于组件统一使用 */
function normalizeItem(raw) {
  if (!raw || typeof raw !== 'object') return null
  const numStatus = Number(raw.status)
  if (numStatus !== 1) return null
  return {
    id: raw.id,
    title: raw.title,
    img_url: raw.img_url ?? raw.imgUrl ?? '',
    target_type: raw.target_type ?? raw.targetType ?? 0,
    target_value: raw.target_value ?? raw.targetValue ?? '',
    sort_order: raw.sort_order ?? raw.sortOrder ?? 0,
    status: numStatus,
    create_time: raw.create_time ?? raw.createTime,
    update_time: raw.update_time ?? raw.updateTime,
  }
}

/** 从响应中取出轮播列表数组（兼容 data 直接为数组或 data.list / data.records） */
function getListFromResponse(res) {
  if (!res || typeof res !== 'object') return []
  const d = res.data
  if (Array.isArray(d)) return d
  if (d && Array.isArray(d.list)) return d.list
  if (d && Array.isArray(d.records)) return d.records
  return []
}

/**
 * 获取首页轮播图列表（仅返回已上线，接口已按权重与创建时间排序）
 * @returns {Promise<HomeImage[]>}
 */
export function getHomeImages() {
  return request('/image', { method: 'GET' }).then((res) => {
    const list = getListFromResponse(res)
    return list.map(normalizeItem).filter(Boolean)
  })
}
