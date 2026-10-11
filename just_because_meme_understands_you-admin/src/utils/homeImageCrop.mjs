/**
 * 计算图片完全覆盖裁剪框时的显示缩放比例。
 * @param {number} sourceWidth 原图宽度，单位为像素。
 * @param {number} sourceHeight 原图高度，单位为像素。
 * @param {number} frameWidth 裁剪框宽度，单位为 CSS 像素。
 * @param {number} frameHeight 裁剪框高度，单位为 CSS 像素。
 * @param {number} zoom 相对初始覆盖比例的缩放倍数，最小为 1。
 * @returns {number} 原图像素到裁剪框 CSS 像素的缩放比例。
 */
export function calculateCropScale(sourceWidth, sourceHeight, frameWidth, frameHeight, zoom = 1) {
  const dimensions = [sourceWidth, sourceHeight, frameWidth, frameHeight, zoom]
  if (dimensions.some((value) => !Number.isFinite(value) || value <= 0)) {
    throw new RangeError('裁剪尺寸和缩放比例必须为正数')
  }

  return Math.max(frameWidth / sourceWidth, frameHeight / sourceHeight) * Math.max(1, zoom)
}

/**
 * 限制图片偏移位置，确保拖动后裁剪框内仍被图片覆盖。
 * @param {number} offsetX 图片左侧相对裁剪框的偏移量。
 * @param {number} offsetY 图片顶部相对裁剪框的偏移量。
 * @param {number} imageWidth 缩放后图片宽度。
 * @param {number} imageHeight 缩放后图片高度。
 * @param {number} frameWidth 裁剪框宽度。
 * @param {number} frameHeight 裁剪框高度。
 * @returns {{x: number, y: number}} 修正后的水平与垂直偏移量。
 */
export function clampImageOffset(offsetX, offsetY, imageWidth, imageHeight, frameWidth, frameHeight) {
  return {
    x: Math.min(0, Math.max(frameWidth - imageWidth, offsetX)),
    y: Math.min(0, Math.max(frameHeight - imageHeight, offsetY)),
  }
}

/**
 * 将裁剪框在页面中的位置换算为原图像素坐标。
 * @param {number} offsetX 图片左侧相对裁剪框的偏移量。
 * @param {number} offsetY 图片顶部相对裁剪框的偏移量。
 * @param {number} scale 图片显示缩放比例。
 * @param {number} frameWidth 裁剪框宽度。
 * @param {number} frameHeight 裁剪框高度。
 * @returns {{x: number, y: number, width: number, height: number}} 原图中需要导出的裁剪矩形。
 */
export function calculateSourceCrop(offsetX, offsetY, scale, frameWidth, frameHeight) {
  if (![scale, frameWidth, frameHeight].every((value) => Number.isFinite(value) && value > 0)) {
    throw new RangeError('裁剪框尺寸和图片缩放比例必须为正数')
  }

  return {
    x: -offsetX / scale,
    y: -offsetY / scale,
    width: frameWidth / scale,
    height: frameHeight / scale,
  }
}

/**
 * 计算图片完整显示在首页预览框内时的位置和尺寸，对应 object-fit: contain。
 * @param {number} sourceWidth 图片宽度，单位为像素。
 * @param {number} sourceHeight 图片高度，单位为像素。
 * @param {number} frameWidth 预览框宽度，单位为像素。
 * @param {number} frameHeight 预览框高度，单位为像素。
 * @returns {{x: number, y: number, width: number, height: number}} 图片在预览框中的绘制矩形。
 */
export function calculateContainRect(sourceWidth, sourceHeight, frameWidth, frameHeight) {
  const dimensions = [sourceWidth, sourceHeight, frameWidth, frameHeight]
  if (dimensions.some((value) => !Number.isFinite(value) || value <= 0)) {
    throw new RangeError('图片和预览框尺寸必须为正数')
  }

  const scale = Math.min(frameWidth / sourceWidth, frameHeight / sourceHeight)
  const width = sourceWidth * scale
  const height = sourceHeight * scale
  return {
    x: (frameWidth - width) / 2,
    y: (frameHeight - height) / 2,
    width,
    height,
  }
}

/**
 * 按原始宽高比例限制图片导出尺寸，不强制套用固定宽高比。
 * @param {number} sourceWidth 裁剪区域宽度，单位为像素。
 * @param {number} sourceHeight 裁剪区域高度，单位为像素。
 * @param {number} maxDimension 导出图片最长边的上限，单位为像素。
 * @returns {{width: number, height: number}} 保持比例后的导出尺寸。
 */
export function calculateOutputDimensions(sourceWidth, sourceHeight, maxDimension) {
  const dimensions = [sourceWidth, sourceHeight, maxDimension]
  if (dimensions.some((value) => !Number.isFinite(value) || value <= 0)) {
    throw new RangeError('导出尺寸必须为正数')
  }

  const scale = Math.min(1, maxDimension / Math.max(sourceWidth, sourceHeight))
  return {
    width: Math.max(1, Math.round(sourceWidth * scale)),
    height: Math.max(1, Math.round(sourceHeight * scale)),
  }
}

/**
 * 判断裁剪区域是否仍覆盖整张原图，以便完整原图直接上传而不经过重编码。
 * @param {{x: number, y: number, width: number, height: number}} crop 原图坐标中的裁剪矩形。
 * @param {number} sourceWidth 原图宽度，单位为像素。
 * @param {number} sourceHeight 原图高度，单位为像素。
 * @param {number} tolerance 浮点坐标比较允许的误差，单位为像素。
 * @returns {boolean} 裁剪矩形是否与整张原图相同。
 */
export function isWholeImageCrop(crop, sourceWidth, sourceHeight, tolerance = 0.5) {
  return Boolean(
    crop
      && [crop.x, crop.y, crop.width, crop.height, sourceWidth, sourceHeight, tolerance]
        .every(Number.isFinite)
      && tolerance >= 0
      && Math.abs(crop.x) <= tolerance
      && Math.abs(crop.y) <= tolerance
      && Math.abs(crop.width - sourceWidth) <= tolerance
      && Math.abs(crop.height - sourceHeight) <= tolerance,
  )
}
