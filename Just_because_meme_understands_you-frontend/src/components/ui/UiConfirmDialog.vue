<template>
  <vs-dialog
    v-model="state.visible"
    not-center
    width="420px"
    class="ui-confirm-dialog"
    @close="onClose"
  >
    <template #header>
      <div class="ui-confirm__head">
        <span
          class="ui-confirm__mark"
          :class="state.danger ? 'is-danger' : 'is-primary'"
          aria-hidden="true"
        >
          <i :class="state.danger ? 'ri-error-warning-fill' : 'ri-question-fill'" />
        </span>
        <h3 class="ui-confirm__title">{{ state.title }}</h3>
      </div>
    </template>

    <p class="ui-confirm__msg">{{ state.message }}</p>

    <template #footer>
      <div class="ui-confirm__actions">
        <button type="button" class="ui-confirm__btn ui-confirm__btn--ghost" @click="cancel">
          {{ state.cancelText }}
        </button>
        <button
          type="button"
          class="ui-confirm__btn"
          :class="state.danger ? 'ui-confirm__btn--danger' : 'ui-confirm__btn--primary'"
          @click="ok"
        >
          {{ state.confirmText }}
        </button>
      </div>
    </template>
  </vs-dialog>
</template>

<script>
import { confirmState, settleConfirm } from '@/stores/confirmDialog'

export default {
  name: 'UiConfirmDialog',
  computed: {
    state() {
      return confirmState
    },
  },
  methods: {
    ok() {
      settleConfirm(true)
    },
    cancel() {
      settleConfirm(false)
    },
    onClose() {
      if (confirmState.visible) settleConfirm(false)
    },
  },
}
</script>

<style scoped>
.ui-confirm__head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-right: 28px;
}

.ui-confirm__mark {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  font-size: 20px;
}

.ui-confirm__mark.is-primary {
  color: var(--meme-primary);
  background: var(--meme-primary-soft);
}

.ui-confirm__mark.is-danger {
  color: var(--meme-danger);
  background: var(--meme-danger-soft);
}

.ui-confirm__title {
  margin: 0;
  font-size: 17px;
  font-weight: 750;
  letter-spacing: -0.02em;
  color: var(--meme-text);
}

.ui-confirm__msg {
  margin: 4px 0 0;
  padding-left: 52px;
  font-size: 14px;
  line-height: 1.65;
  color: var(--meme-text-secondary);
  white-space: pre-wrap;
}

.ui-confirm__actions {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 10px;
  width: 100%;
}

.ui-confirm__btn {
  min-width: 84px;
  height: 38px;
  padding: 0 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 11px;
  border: none;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  transition: transform 0.15s ease, filter 0.15s ease, background 0.15s ease, box-shadow 0.15s ease,
    border-color 0.15s ease, color 0.15s ease;
}

.ui-confirm__btn--ghost {
  color: var(--meme-text-secondary);
  background: var(--meme-bg-muted);
  border: 1px solid var(--meme-border);
}

.ui-confirm__btn--ghost:hover {
  color: var(--meme-text);
  background: var(--meme-bg-elevated);
  border-color: var(--meme-border-strong);
}

.ui-confirm__btn--primary {
  color: #fff;
  background: linear-gradient(135deg, var(--meme-primary), var(--meme-primary-dark));
  box-shadow: 0 6px 16px var(--meme-focus-ring);
}

.ui-confirm__btn--primary:hover {
  transform: translateY(-1px);
  filter: brightness(1.03);
}

.ui-confirm__btn--danger {
  color: #fff;
  background: linear-gradient(
    135deg,
    var(--meme-danger),
    color-mix(in srgb, var(--meme-danger) 72%, #7f1d1d)
  );
  box-shadow: 0 6px 16px color-mix(in srgb, var(--meme-danger) 35%, transparent);
}

.ui-confirm__btn--danger:hover {
  transform: translateY(-1px);
  filter: brightness(1.04);
}

.ui-confirm__btn:active {
  transform: translateY(0);
  filter: none;
}
</style>

<style>
/* vs-dialog 挂 body，需非 scoped 微调壳层 */
.ui-confirm-dialog.vs-dialog-content,
.vs-dialog-content.ui-confirm-dialog {
  border-radius: 18px !important;
  border: 1px solid var(--meme-border) !important;
  background: var(--meme-bg-elevated) !important;
  box-shadow: var(--meme-shadow-dialog) !important;
  overflow: hidden;
}

.ui-confirm-dialog .vs-dialog__header,
.ui-confirm-dialog .vs-dialog-header {
  padding: 20px 20px 8px !important;
}

.ui-confirm-dialog .vs-dialog__content,
.ui-confirm-dialog .vs-dialog-content {
  padding: 4px 20px 8px !important;
}

.ui-confirm-dialog .vs-dialog__footer,
.ui-confirm-dialog .vs-dialog-footer {
  padding: 12px 20px 18px !important;
  border-top: 1px solid color-mix(in srgb, var(--meme-border) 80%, transparent);
  background: color-mix(in srgb, var(--meme-bg-muted) 55%, var(--meme-bg-elevated));
}

.ui-confirm-dialog .vs-dialog__close,
.ui-confirm-dialog .vs-dialog-close {
  top: 14px !important;
  right: 14px !important;
}
</style>
