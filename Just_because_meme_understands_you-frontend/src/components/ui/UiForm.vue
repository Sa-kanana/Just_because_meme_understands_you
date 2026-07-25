<template>
  <form class="ui-form" :class="[`is-label-${labelPosition}`]" @submit.prevent>
    <slot />
  </form>
</template>

<script>
export default {
  name: 'UiForm',
  props: {
    model: { type: Object, default: () => ({}) },
    rules: { type: Object, default: () => ({}) },
    labelPosition: { type: String, default: 'top' },
  },
  methods: {
    validate(callback) {
      const model = this.model || {}
      const rules = this.rules || {}
      const errors = []
      Object.keys(rules).forEach((prop) => {
        const list = Array.isArray(rules[prop]) ? rules[prop] : [rules[prop]]
        const value = model[prop]
        for (const rule of list) {
          if (rule.required && (value == null || String(value).trim() === '')) {
            errors.push(rule.message || `${prop} 必填`)
            break
          }
          if (rule.type === 'email' && value) {
            const ok = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(String(value))
            if (!ok) {
              errors.push(rule.message || '邮箱格式不正确')
              break
            }
          }
          if (typeof rule.validator === 'function') {
            let failed = false
            rule.validator(rule, value, (err) => {
              if (err) {
                failed = true
                errors.push(err.message || String(err) || rule.message || '校验失败')
              }
            })
            if (failed) break
          }
          if (rule.min != null && String(value || '').length < rule.min) {
            errors.push(rule.message || `至少 ${rule.min} 个字符`)
            break
          }
        }
      })
      const valid = errors.length === 0
      if (typeof callback === 'function') callback(valid, errors)
      return Promise.resolve(valid)
    },
    clearValidate() {},
  },
}
</script>

<style scoped>
.ui-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
</style>
