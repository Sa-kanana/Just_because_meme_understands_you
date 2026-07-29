import { Message } from '@arco-design/web-vue'

export function toastSuccess(msg) {
  Message.success(msg || '操作成功')
}

export function toastError(msg) {
  Message.error(msg || '操作失败')
}

export function toastInfo(msg) {
  Message.info(msg || '')
}
