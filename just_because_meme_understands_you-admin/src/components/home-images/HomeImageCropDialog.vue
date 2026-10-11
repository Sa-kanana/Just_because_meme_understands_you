<template>
  <a-modal
    :visible="visible"
    class="home-image-crop-modal"
    title="裁剪首页轮播图"
    :width="900"
    :mask-closable="false"
    :footer="false"
    @cancel="emit('cancel')"
  >
    <div class="home-image-crop-dialog">
      <p class="crop-dialog-hint">默认保留原图比例。拖动图片调整构图，右侧预览完整展示在首页轮播中的效果。</p>

      <div v-if="sourceImage" class="crop-ratio-toolbar">
        <span>裁剪比例</span>
        <a-select
          :model-value="selectedAspectRatio"
          size="small"
          :options="aspectRatioOptions"
          class="crop-ratio-select"
          @change="changeAspectRatio"
        />
        <span class="crop-ratio-note">首页会完整展示裁剪结果</span>
      </div>

      <div v-if="imageLoadError" class="crop-dialog-state crop-dialog-state--error">
        {{ imageLoadError }}
      </div>
      <div v-else-if="!sourceImage" class="crop-dialog-state">正在读取图片…</div>

      <div v-else class="crop-layout">
        <section ref="cropEditorRef" class="crop-editor" aria-label="图片裁剪区域">
          <div
            ref="cropStageRef"
            class="crop-stage"
            :class="{ 'is-dragging': drag.active }"
            :style="cropStageStyle"
            @pointerdown="startDrag"
            @pointermove="moveDrag"
            @pointerup="stopDrag"
            @pointercancel="stopDrag"
            @lostpointercapture="drag.active = false"
          >
            <img
              :src="imageSrc"
              :style="cropImageStyle"
              class="crop-source-image"
              alt="待裁剪的首页轮播图片"
              draggable="false"
            />
            <div class="crop-grid" aria-hidden="true"><i /><i /></div>
            <span class="crop-ratio-badge">{{ currentRatioLabel }}</span>
          </div>

          <div class="crop-controls">
            <button type="button" class="crop-zoom-button" aria-label="缩小" @click="adjustZoom(-0.1)">−</button>
            <input
              v-model.number="zoom"
              class="crop-zoom-range"
              type="range"
              min="1"
              max="3"
              step="0.01"
              aria-label="图片缩放比例"
              @input="updateCropViewport(false)"
            />
            <button type="button" class="crop-zoom-button" aria-label="放大" @click="adjustZoom(0.1)">＋</button>
            <span class="crop-zoom-value">{{ Math.round(zoom * 100) }}%</span>
            <a-button size="small" @click="resetCrop">重置</a-button>
          </div>
        </section>

        <aside class="crop-preview-panel">
          <div class="crop-preview-heading">
            <span class="crop-preview-kicker">PREVIEW</span>
            <strong>首页中的完整显示效果</strong>
          </div>
          <div class="crop-preview-frame">
            <canvas
              ref="previewCanvasRef"
              :width="PREVIEW_WIDTH"
              :height="PREVIEW_HEIGHT"
              class="crop-preview-canvas"
              aria-label="裁剪后效果预览"
            />
            <span v-if="title" class="crop-preview-caption">{{ title }}</span>
          </div>
          <p>首页会完整展示图片，不会强制裁切为固定比例。</p>
        </aside>
      </div>

      <p v-if="cropError" class="crop-dialog-error">{{ cropError }}</p>

      <div class="crop-dialog-footer">
        <a-button :disabled="loading" @click="emit('cancel')">取消</a-button>
        <a-button
          type="primary"
          :loading="loading"
          :disabled="!sourceImage || !!imageLoadError"
          @click="confirmCrop"
        >
          裁剪并上传
        </a-button>
      </div>
    </div>
  </a-modal>
</template>

<script setup>
/* global defineProps, defineEmits */
// 中文注释：以上两个标识由 Vue 3 的 script setup 编译器提供。
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import {
  calculateContainRect,
  calculateCropScale,
  calculateOutputDimensions,
  calculateSourceCrop,
  clampImageOffset,
  isWholeImageCrop,
} from '@/utils/homeImageCrop.mjs'

const MAX_OUTPUT_DIMENSION = 1920 // 裁剪结果最长边的像素上限，控制文件大小并保持原始比例。
const PREVIEW_WIDTH = 480 // 首页效果预览画布的像素宽度。
const PREVIEW_HEIGHT = 270 // 首页效果预览画布的像素高度，与首页轮播容器一致。
const MAX_CROP_STAGE_WIDTH = 560 // 裁剪操作区的最大宽度，避免大图撑大弹窗。
const MAX_CROP_STAGE_HEIGHT = 360 // 裁剪操作区的最大高度，适配横图和竖图。
const MAX_IMAGE_BYTES = 10 * 1024 * 1024 // 与 OSS 上传限制一致的最大文件大小。
const HOME_PREVIEW_BACKGROUND = '#edf3f9' // 首页完整展示图片时，预览留白使用的浅色底色。
const CROP_RATIO_PRESETS = [
  { label: '16:9', value: 16 / 9 },
  { label: '4:3', value: 4 / 3 },
  { label: '3:2', value: 3 / 2 },
  { label: '1:1', value: 1 },
  { label: '3:4', value: 3 / 4 },
] // 可选的常见构图比例；原图比例始终作为默认选项。

const props = defineProps({
  visible: { type: Boolean, default: false }, // 控制裁剪对话框是否显示。
  file: { type: Object, default: null }, // 用户选择的原始图片文件。
  title: { type: String, default: '' }, // 与首页轮播一致的图片标题覆盖层。
  loading: { type: Boolean, default: false }, // 裁剪结果正在上传时锁定操作。
})
const emit = defineEmits(['cancel', 'confirm'])

const imageSrc = ref('') // 原图临时预览地址。
const sourceImage = ref(null) // 已加载并用于绘制的原始图片对象。
const imageLoadError = ref('') // 原图读取失败时显示的原因。
const cropError = ref('') // 生成裁剪文件失败时显示的原因。
const cropEditorRef = ref(null) // 用于测量裁剪操作区可用宽度的 DOM 引用。
const cropStageRef = ref(null) // 可拖动裁剪区域的 DOM 引用。
const previewCanvasRef = ref(null) // 展示裁剪结果的预览画布引用。
const zoom = ref(1) // 相对自动铺满裁剪框的缩放倍数。
const selectedAspectRatio = ref(16 / 9) // 当前裁剪比例，选图后默认切换为原图比例。
const cropEditorWidth = ref(MAX_CROP_STAGE_WIDTH) // 裁剪操作区当前可用宽度，随弹窗和视口变化更新。
const geometry = reactive({
  frameWidth: 0, // 裁剪框当前宽度，单位为 CSS 像素。
  frameHeight: 0, // 裁剪框当前高度，单位为 CSS 像素。
  scale: 0, // 原图像素到裁剪框 CSS 像素的缩放比例。
  x: 0, // 原图左侧相对裁剪框的偏移量。
  y: 0, // 原图顶部相对裁剪框的偏移量。
})
const drag = reactive({
  active: false, // 当前是否正用指针拖动图片。
  pointerId: null, // 当前拖动操作对应的指针 ID。
  startClientX: 0, // 开始拖动时指针的水平位置。
  startClientY: 0, // 开始拖动时指针的垂直位置。
  startImageX: 0, // 开始拖动时图片的水平偏移量。
  startImageY: 0, // 开始拖动时图片的垂直偏移量。
})

let sourceObjectUrl = '' // 当前原图的临时 URL，关闭弹窗后释放。
let resizeObserver = null // 监听裁剪区尺寸变化的观察器。
let previewFrameId = 0 // 合并连续拖动产生的预览绘制任务。

const cropImageStyle = computed(() => {
  const image = sourceImage.value
  if (!image || !geometry.scale) return {}
  return {
    width: `${image.naturalWidth * geometry.scale}px`,
    height: `${image.naturalHeight * geometry.scale}px`,
    transform: `translate3d(${geometry.x}px, ${geometry.y}px, 0)`,
  }
})

const aspectRatioOptions = computed(() => {
  const source = sourceImage.value
  const originalRatio = source ? source.naturalWidth / source.naturalHeight : 16 / 9
  return [
    { label: '原图比例（完整保留）', value: originalRatio },
    ...CROP_RATIO_PRESETS.map((preset) => ({ label: preset.label, value: preset.value })),
  ]
})

const cropStageStyle = computed(() => {
  const ratio = Math.max(0.1, selectedAspectRatio.value)
  const availableWidth = cropEditorWidth.value || MAX_CROP_STAGE_WIDTH
  const width = Math.min(availableWidth, MAX_CROP_STAGE_WIDTH, MAX_CROP_STAGE_HEIGHT * ratio)
  return { width: `${width}px`, height: `${width / ratio}px` }
})

const currentRatioLabel = computed(() => {
  const original = sourceImage.value
    ? sourceImage.value.naturalWidth / sourceImage.value.naturalHeight
    : 16 / 9
  if (Math.abs(selectedAspectRatio.value - original) < 0.005) return '原图比例'
  const preset = CROP_RATIO_PRESETS.find((item) => Math.abs(item.value - selectedAspectRatio.value) < 0.005)
  return preset ? preset.label : `${selectedAspectRatio.value.toFixed(2)}:1`
})

watch(
  () => [props.visible, props.file],
  ([visible, file]) => {
    if (visible && file) loadSourceImage(file)
    else clearSourceImage()
  },
  { immediate: true },
)

onMounted(() => {
  if (cropEditorRef.value) cropEditorWidth.value = cropEditorRef.value.clientWidth
  if (typeof ResizeObserver === 'undefined') return
  resizeObserver = new ResizeObserver((entries) => {
    const entry = entries[0]
    if (entry) cropEditorWidth.value = entry.contentRect.width
    updateCropViewport(false)
  })
  if (cropEditorRef.value) {
    cropEditorWidth.value = cropEditorRef.value.clientWidth
    resizeObserver.observe(cropEditorRef.value)
  }
})

onBeforeUnmount(() => {
  clearSourceImage()
  if (resizeObserver) resizeObserver.disconnect()
})

function loadSourceImage(file) {
  clearSourceImage()
  imageLoadError.value = ''
  cropError.value = ''
  zoom.value = 1
  const objectUrl = URL.createObjectURL(file)
  sourceObjectUrl = objectUrl
  imageSrc.value = objectUrl

  const image = new Image()
  image.onload = async () => {
    if (sourceObjectUrl !== objectUrl) return
    sourceImage.value = image
    selectedAspectRatio.value = image.naturalWidth / image.naturalHeight
    await nextTick()
    if (cropEditorRef.value) {
      cropEditorWidth.value = cropEditorRef.value.clientWidth
      if (resizeObserver) resizeObserver.observe(cropEditorRef.value)
    }
    requestAnimationFrame(() => updateCropViewport(true))
  }
  image.onerror = () => {
    if (sourceObjectUrl !== objectUrl) return
    imageLoadError.value = '图片读取失败，请换一张图片重试。'
  }
  image.src = objectUrl
}

function clearSourceImage() {
  if (sourceObjectUrl) URL.revokeObjectURL(sourceObjectUrl)
  sourceObjectUrl = ''
  imageSrc.value = ''
  sourceImage.value = null
  imageLoadError.value = ''
  cropError.value = ''
  drag.active = false
  if (previewFrameId) cancelAnimationFrame(previewFrameId)
  previewFrameId = 0
  if (resizeObserver && cropEditorRef.value) resizeObserver.unobserve(cropEditorRef.value)
  const canvas = previewCanvasRef.value
  if (canvas) canvas.getContext('2d')?.clearRect(0, 0, canvas.width, canvas.height)
}

function updateCropViewport(resetPosition = false) {
  const image = sourceImage.value
  const stage = cropStageRef.value
  if (!image || !stage) return
  const frameWidth = stage.clientWidth
  const frameHeight = stage.clientHeight
  if (!frameWidth || !frameHeight) return

  const previousScale = geometry.scale
  const focusX = previousScale ? (frameWidth / 2 - geometry.x) / previousScale : image.naturalWidth / 2
  const focusY = previousScale ? (frameHeight / 2 - geometry.y) / previousScale : image.naturalHeight / 2
  const scale = calculateCropScale(image.naturalWidth, image.naturalHeight, frameWidth, frameHeight, zoom.value)
  const imageWidth = image.naturalWidth * scale
  const imageHeight = image.naturalHeight * scale
  const x = resetPosition ? (frameWidth - imageWidth) / 2 : frameWidth / 2 - focusX * scale
  const y = resetPosition ? (frameHeight - imageHeight) / 2 : frameHeight / 2 - focusY * scale
  const offset = clampImageOffset(x, y, imageWidth, imageHeight, frameWidth, frameHeight)

  geometry.frameWidth = frameWidth
  geometry.frameHeight = frameHeight
  geometry.scale = scale
  geometry.x = offset.x
  geometry.y = offset.y
  schedulePreviewDraw()
}

function adjustZoom(amount) {
  zoom.value = Math.min(3, Math.max(1, Number((zoom.value + amount).toFixed(2))))
  updateCropViewport(false)
}

async function changeAspectRatio(value) {
  const nextRatio = Number(value)
  if (!Number.isFinite(nextRatio) || nextRatio <= 0) return
  selectedAspectRatio.value = nextRatio
  zoom.value = 1
  await nextTick()
  updateCropViewport(true)
}

function resetCrop() {
  zoom.value = 1
  updateCropViewport(true)
}

function startDrag(event) {
  if (!sourceImage.value || event.button !== 0) return
  event.preventDefault()
  drag.active = true
  drag.pointerId = event.pointerId
  drag.startClientX = event.clientX
  drag.startClientY = event.clientY
  drag.startImageX = geometry.x
  drag.startImageY = geometry.y
  event.currentTarget.setPointerCapture(event.pointerId)
}

function moveDrag(event) {
  if (!drag.active || event.pointerId !== drag.pointerId || !sourceImage.value) return
  const imageWidth = sourceImage.value.naturalWidth * geometry.scale
  const imageHeight = sourceImage.value.naturalHeight * geometry.scale
  const offset = clampImageOffset(
    drag.startImageX + event.clientX - drag.startClientX,
    drag.startImageY + event.clientY - drag.startClientY,
    imageWidth,
    imageHeight,
    geometry.frameWidth,
    geometry.frameHeight,
  )
  geometry.x = offset.x
  geometry.y = offset.y
  schedulePreviewDraw()
}

function stopDrag(event) {
  if (!drag.active || event.pointerId !== drag.pointerId) return
  drag.active = false
  drag.pointerId = null
  if (event.currentTarget.hasPointerCapture(event.pointerId)) {
    event.currentTarget.releasePointerCapture(event.pointerId)
  }
}

function getSourceCropRect() {
  return calculateSourceCrop(
    geometry.x,
    geometry.y,
    geometry.scale,
    geometry.frameWidth,
    geometry.frameHeight,
  )
}

function schedulePreviewDraw() {
  if (previewFrameId) return
  previewFrameId = requestAnimationFrame(() => {
    previewFrameId = 0
    drawPreview()
  })
}

function drawPreview() {
  const image = sourceImage.value
  const canvas = previewCanvasRef.value
  if (!image || !canvas || !geometry.scale) return
  const context = canvas.getContext('2d')
  if (!context) return
  const crop = getSourceCropRect()
  const contained = calculateContainRect(crop.width, crop.height, canvas.width, canvas.height)
  context.clearRect(0, 0, canvas.width, canvas.height)
  context.fillStyle = HOME_PREVIEW_BACKGROUND
  context.fillRect(0, 0, canvas.width, canvas.height)
  context.drawImage(
    image,
    crop.x,
    crop.y,
    crop.width,
    crop.height,
    contained.x,
    contained.y,
    contained.width,
    contained.height,
  )
}

async function confirmCrop() {
  const image = sourceImage.value
  if (!image || !geometry.scale || props.loading) return
  cropError.value = ''

  try {
    const crop = getSourceCropRect()
    if (isWholeImageCrop(crop, image.naturalWidth, image.naturalHeight)) {
      emit('confirm', props.file)
      return
    }

    const outputDimensions = calculateOutputDimensions(
      crop.width,
      crop.height,
      MAX_OUTPUT_DIMENSION,
    )
    const canvas = document.createElement('canvas')
    canvas.width = outputDimensions.width
    canvas.height = outputDimensions.height
    const context = canvas.getContext('2d')
    if (!context) throw new Error('无法创建图片画布，请重试。')
    context.drawImage(
      image,
      crop.x,
      crop.y,
      crop.width,
      crop.height,
      0,
      0,
      outputDimensions.width,
      outputDimensions.height,
    )

    const sourceType = props.file && props.file.type
    const outputType = sourceType === 'image/png' || sourceType === 'image/webp' ? sourceType : 'image/jpeg'
    const blob = await new Promise((resolve) => {
      canvas.toBlob(resolve, outputType, outputType === 'image/jpeg' ? 0.92 : undefined)
    })
    if (!blob) throw new Error('生成裁剪图片失败，请重试。')
    if (blob.size > MAX_IMAGE_BYTES) throw new Error('裁剪结果超过 10MB，请缩小图片后重试。')

    const extensionByType = { 'image/jpeg': 'jpg', 'image/png': 'png', 'image/webp': 'webp' }
    const extension = extensionByType[blob.type] || extensionByType[outputType]
    const originalName = (props.file && props.file.name) || 'home-banner'
    const baseName = originalName.replace(/\.[^.]+$/, '')
    const outputFile = new File([blob], `${baseName}_cropped.${extension}`, {
      type: blob.type || outputType,
      lastModified: Date.now(),
    })
    emit('confirm', outputFile)
  } catch (error) {
    cropError.value = error && error.message ? error.message : '裁剪图片失败，请重试。'
  }
}
</script>

<style scoped>
.home-image-crop-dialog {
  display: grid;
  gap: 16px;
}
.crop-dialog-hint {
  margin: 0;
  color: #71809a;
  font-size: 13px;
  line-height: 1.6;
}
.crop-ratio-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #53647d;
  font-size: 12px;
}
.crop-ratio-select {
  width: 190px;
}
.crop-ratio-note {
  color: #8795aa;
}
.crop-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.65fr) minmax(210px, 0.75fr);
  align-items: start;
  gap: 20px;
}
.crop-editor {
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.crop-stage {
  position: relative;
  overflow: hidden;
  border: 1px solid #d8e1ee;
  border-radius: 12px;
  background: #111827;
  cursor: grab;
  touch-action: none;
  user-select: none;
}
.crop-stage.is-dragging {
  cursor: grabbing;
}
.crop-source-image {
  position: absolute;
  top: 0;
  left: 0;
  max-width: none;
  pointer-events: none;
  user-select: none;
}
.crop-grid {
  position: absolute;
  inset: 0;
  border: 1px solid rgb(255 255 255 / 72%);
  background-image:
    linear-gradient(to right, transparent calc(33.333% - 0.5px), rgb(255 255 255 / 55%) 33.333%, transparent calc(33.333% + 0.5px)),
    linear-gradient(to right, transparent calc(66.666% - 0.5px), rgb(255 255 255 / 55%) 66.666%, transparent calc(66.666% + 0.5px)),
    linear-gradient(to bottom, transparent calc(33.333% - 0.5px), rgb(255 255 255 / 55%) 33.333%, transparent calc(33.333% + 0.5px)),
    linear-gradient(to bottom, transparent calc(66.666% - 0.5px), rgb(255 255 255 / 55%) 66.666%, transparent calc(66.666% + 0.5px));
  pointer-events: none;
}
.crop-ratio-badge {
  position: absolute;
  right: 10px;
  bottom: 10px;
  padding: 4px 8px;
  border: 1px solid rgb(255 255 255 / 28%);
  border-radius: 999px;
  background: rgb(15 23 42 / 60%);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  pointer-events: none;
}
.crop-controls {
  display: flex;
  align-items: center;
  gap: 9px;
  margin-top: 12px;
}
.crop-zoom-button {
  display: grid;
  width: 30px;
  height: 30px;
  flex: 0 0 auto;
  place-items: center;
  border: 1px solid #dce5f1;
  border-radius: 8px;
  background: #fff;
  color: #40516b;
  font-size: 18px;
  cursor: pointer;
}
.crop-zoom-button:hover {
  border-color: #8bb0f5;
  color: #2867df;
}
.crop-zoom-range {
  width: min(220px, 100%);
  accent-color: #2867df;
}
.crop-zoom-value {
  min-width: 42px;
  color: #71809a;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}
.crop-preview-panel {
  display: grid;
  min-width: 0;
  gap: 10px;
  padding: 14px;
  border: 1px solid #e4eaf3;
  border-radius: 12px;
  background: #f8faff;
}
.crop-preview-heading {
  display: grid;
  gap: 4px;
}
.crop-preview-kicker {
  color: #8a99af;
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.13em;
}
.crop-preview-heading strong {
  color: #273751;
  font-size: 14px;
}
.crop-preview-frame {
  position: relative;
  width: 100%;
  overflow: hidden;
  border: 1px solid #dce4ef;
  border-radius: 9px;
  background: #edf3f9;
  aspect-ratio: 16 / 9;
}
.crop-preview-canvas {
  display: block;
  width: 100%;
  height: 100%;
}
.crop-preview-caption {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  overflow: hidden;
  padding: 48px 20px 18px;
  background: linear-gradient(to top, rgba(15, 23, 42, 0.72) 0%, transparent 100%);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  text-overflow: ellipsis;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.35);
  white-space: nowrap;
  pointer-events: none;
}
.crop-preview-panel p {
  margin: 0;
  color: #8795aa;
  font-size: 11px;
  line-height: 1.5;
}
.crop-dialog-state {
  display: grid;
  min-height: 220px;
  place-items: center;
  border: 1px dashed #d8e1ee;
  border-radius: 12px;
  color: #8190a7;
  font-size: 13px;
}
.crop-dialog-state--error,
.crop-dialog-error {
  color: #d44854;
}
.crop-dialog-error {
  margin: 0;
  font-size: 12px;
}
.crop-dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding-top: 14px;
  border-top: 1px solid #edf0f5;
}
.home-image-crop-modal :deep(.arco-modal) {
  width: min(900px, calc(100vw - 32px));
  max-width: calc(100vw - 32px);
  border-radius: 16px;
}
@media (max-width: 720px) {
  .crop-ratio-toolbar {
    flex-wrap: wrap;
  }
  .crop-layout {
    grid-template-columns: minmax(0, 1fr);
    gap: 14px;
  }
  .crop-preview-panel {
    grid-template-columns: minmax(100px, 0.7fr) minmax(0, 1fr);
    align-items: center;
  }
  .crop-preview-heading {
    grid-row: 1 / 3;
  }
}
</style>
