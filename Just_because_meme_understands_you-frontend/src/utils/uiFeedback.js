import { VsNotification } from 'vuesax-alpha'
import { confirmState } from '@/stores/confirmDialog'

const TOAST_ICONS = {
  success: '<i class="ri-checkbox-circle-fill" aria-hidden="true"></i>',
  danger: '<i class="ri-close-circle-fill" aria-hidden="true"></i>',
  warn: '<i class="ri-error-warning-fill" aria-hidden="true"></i>',
  primary: '<i class="ri-information-fill" aria-hidden="true"></i>',
}

/** 直接传 hex，避免命名色走 var(--vs-*) 时格式不兼容 */
const TOAST_COLORS = {
  success: '#059669',
  danger: '#e11d48',
  warn: '#d97706',
  primary: '#318aef',
}

/**
 * @param {'success'|'danger'|'warn'|'primary'} type
 * @param {unknown} message
 * @param {string} title
 */
function notify(type, message, title) {
  const content = message != null ? String(message) : ''
  if (!content) return

  const color = TOAST_COLORS[type] || TOAST_COLORS.primary

  VsNotification({
    title: title || '',
    content,
    color,
    position: 'top-center',
    duration: 4200,
    progressAuto: true,
    icon: TOAST_ICONS[type] || TOAST_ICONS.primary,
    iconSize: '1.45rem',
    width: 400,
    zIndex: 400000,
  })
}

/** Toast 反馈（VsNotification） */
export const toast = {
  success(msg) {
    notify('success', msg, '成功')
  },
  error(msg) {
    notify('danger', msg, '错误')
  },
  warning(msg) {
    notify('warn', msg, '提示')
  },
  info(msg) {
    notify('primary', msg, '提示')
  },
}

/**
 * 确认弹窗（Promise）
 * @returns {Promise<'confirm'>}
 */
export function confirmBox(message, title = '确认', options = {}) {
  return new Promise((resolve, reject) => {
    confirmState.visible = true
    confirmState.title = title != null ? String(title) : '确认'
    confirmState.message = message != null ? String(message) : ''
    confirmState.confirmText = (options && options.confirmButtonText) || '确定'
    confirmState.cancelText = (options && options.cancelButtonText) || '取消'
    confirmState.danger = !!(options && (options.type === 'warning' || options.type === 'error'))
    confirmState.resolve = resolve
    confirmState.reject = reject
  })
}
