/** 中文注释：把详情接口中的梗数据拆成编辑表单字段，媒体资源与普通链接分开。 */
export function mapMemeDetailToEditForm(detail = {}) {
  const links = Array.isArray(detail.links) ? detail.links : []
  return {
    name: String(detail.name || ''), // 中文注释：梗名称。
    introduction: String(detail.introduction || ''), // 中文注释：梗介绍。
    image: String(detail.image || ''), // 中文注释：梗封面地址。
    primaryTagId: Array.isArray(detail.memeTag)
      ? (detail.memeTag.find((tag) => tag.isPrimary)?.id ?? null)
      : null, // 中文注释：已关联的主分类标签 ID。
    tagIds: Array.isArray(detail.memeTag)
      ? detail.memeTag.filter((tag) => !tag.isPrimary).map((tag) => tag.id)
      : [], // 中文注释：已关联的普通标签 ID。
    resources: links
      .filter((item) => String(item?.type || '').toLowerCase() !== 'media')
      .map((item, index) => ({
        type: String(item?.type || 'link'), // 中文注释：链接类型。
        title: String(item?.title || ''), // 中文注释：链接标题。
        url: String(item?.url || ''), // 中文注释：链接地址。
        sortOrder: Number.isFinite(Number(item?.sortOrder)) ? Number(item.sortOrder) : index, // 中文注释：链接展示顺序。
      })),
    resourceUrls: links
      .filter((item) => String(item?.type || '').toLowerCase() === 'media')
      .map((item) => String(item?.url || '').trim())
      .filter(Boolean), // 中文注释：图片或 GIF 等媒体资源地址。
  }
}

/** 中文注释：从表单生成后端修改稿请求体，并规范化文本和链接排序。 */
export function buildMemeEditPayload(form = {}) {
  return {
    name: String(form.name || '').trim(), // 中文注释：去除首尾空白后的梗名称。
    introduction: String(form.introduction || '').trim(), // 中文注释：去除首尾空白后的梗介绍。
    image: String(form.image || '').trim(), // 中文注释：梗封面地址。
    primaryTagId: form.primaryTagId != null ? Number(form.primaryTagId) : null, // 中文注释：主分类标签 ID。
    tagIds: Array.isArray(form.tagIds) ? form.tagIds : [], // 中文注释：用户选中的标签 ID。
    resources: Array.isArray(form.resources)
      ? form.resources
          .map((item, index) => ({
            type: String(item?.type || 'link').trim() || 'link', // 中文注释：链接类型。
            title: String(item?.title || '').trim(), // 中文注释：链接标题。
            url: String(item?.url || '').trim(), // 中文注释：链接地址。
            sortOrder: index, // 中文注释：按当前表单顺序生成展示顺序。
          }))
          .filter((item) => item.title && item.url)
      : [],
    resourceUrls: Array.isArray(form.resourceUrls)
      ? form.resourceUrls.map((url) => String(url || '').trim()).filter(Boolean)
      : [], // 中文注释：媒体资源地址列表。
  }
}

const MAX_SECONDARY_TAGS = 7 // 中文注释：发布或修改梗时允许选择的补充标签上限。

function normalizeTagId(value) {
  const tagId = Number(value)
  return Number.isInteger(tagId) && tagId > 0 ? tagId : null
}

/**
 * 中文注释：返回梗表单不可提交的原因；名称和介绍必填，其余字段选填。
 * @param {Object} form 梗表单字段，名称和介绍必填，标签及资源选填。
 * @param {Object} options 配置项；coverUploading 表示封面是否仍在上传。
 */
export function getMemeFormBlockReason(form, options = {}) {
  if (!String(form?.name || '').trim()) return '请先填写梗名称'
  if (!String(form?.introduction || '').trim()) return '请先填写梗介绍'

  const rawPrimaryTagId = form?.primaryTagId
  const hasPrimaryTag = rawPrimaryTagId != null && String(rawPrimaryTagId).trim() !== ''
  const primaryTagId = hasPrimaryTag ? normalizeTagId(rawPrimaryTagId) : null
  if (hasPrimaryTag && primaryTagId == null) return '主标签选择无效，请重新选择'

  const rawTagIds = Array.isArray(form?.tagIds) ? form.tagIds : []
  const tagIds = rawTagIds.map(normalizeTagId)
  if (tagIds.some((tagId) => tagId == null)) return '补充标签选择无效，请重新选择'
  if (primaryTagId != null && tagIds.includes(primaryTagId)) return '主标签不能重复作为补充标签'
  if (new Set(tagIds).size !== tagIds.length) return '补充标签不能重复'
  if (tagIds.length > MAX_SECONDARY_TAGS) {
    return `补充标签最多选择 ${MAX_SECONDARY_TAGS} 个，请移除 ${tagIds.length - MAX_SECONDARY_TAGS} 个`
  }
  if (options.coverUploading) return '封面正在上传，请稍候'
  return ''
}
