<template>
  <div class="ui-upload" @click="pick">
    <input
      ref="file"
      type="file"
      class="ui-upload__input"
      :accept="accept"
      :multiple="multiple"
      @change="onChange"
    />
    <slot />
  </div>
</template>

<script>
export default {
  name: 'UiUpload',
  props: {
    accept: { type: String, default: 'image/*' },
    multiple: { type: Boolean, default: false },
    disabled: { type: Boolean, default: false },
    autoUpload: { type: Boolean, default: false },
    showFileList: { type: Boolean, default: false },
    httpRequest: { type: Function, default: null },
  },
  emits: ['change', 'http-request'],
  methods: {
    pick() {
      if (this.disabled) return
      this.$refs.file && this.$refs.file.click()
    },
    onChange(e) {
      const files = e.target.files ? Array.from(e.target.files) : []
      this.$emit('change', { raw: files[0], files })
      files.forEach((file) => {
        const option = { file }
        this.$emit('http-request', option)
        if (typeof this.httpRequest === 'function') this.httpRequest(option)
      })
      e.target.value = ''
    },
    clearFiles() {},
  },
}
</script>

<style scoped>
.ui-upload {
  display: inline-block;
  cursor: pointer;
}
.ui-upload__input {
  display: none;
}
</style>
