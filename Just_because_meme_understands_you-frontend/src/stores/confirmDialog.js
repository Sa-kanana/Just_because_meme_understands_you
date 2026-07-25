import { reactive } from 'vue'

export const confirmState = reactive({
  visible: false,
  title: '确认',
  message: '',
  confirmText: '确定',
  cancelText: '取消',
  danger: false,
  resolve: null,
  reject: null,
})

export function settleConfirm(ok) {
  const { resolve, reject } = confirmState
  confirmState.visible = false
  confirmState.resolve = null
  confirmState.reject = null
  if (ok) {
    if (typeof resolve === 'function') resolve('confirm')
  } else if (typeof reject === 'function') {
    reject('cancel')
  }
}
