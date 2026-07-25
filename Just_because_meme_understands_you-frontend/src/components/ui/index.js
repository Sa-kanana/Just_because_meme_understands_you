import UiForm from './UiForm.vue'
import UiFormItem from './UiFormItem.vue'
import UiDropdown from './UiDropdown.vue'
import UiDropdownMenu from './UiDropdownMenu.vue'
import UiDropdownItem from './UiDropdownItem.vue'
import UiTabs from './UiTabs.vue'
import UiTabPane from './UiTabPane.vue'
import UiEmpty from './UiEmpty.vue'
import UiSkeleton from './UiSkeleton.vue'
import UiSteps from './UiSteps.vue'
import UiStep from './UiStep.vue'
import UiDrawer from './UiDrawer.vue'
import UiTag from './UiTag.vue'
import UiImage from './UiImage.vue'
import UiUpload from './UiUpload.vue'
import UiBadge from './UiBadge.vue'
import UiBacktop from './UiBacktop.vue'
import UiResult from './UiResult.vue'
import UiLink from './UiLink.vue'
import UiDatePicker from './UiDatePicker.vue'
import UiProgress from './UiProgress.vue'
import UiRadioGroup from './UiRadioGroup.vue'
import UiConfirmDialog from './UiConfirmDialog.vue'
import UiAvatar from './UiAvatar.vue'

const components = [
  UiForm,
  UiFormItem,
  UiDropdown,
  UiDropdownMenu,
  UiDropdownItem,
  UiTabs,
  UiTabPane,
  UiEmpty,
  UiSkeleton,
  UiSteps,
  UiStep,
  UiDrawer,
  UiTag,
  UiImage,
  UiUpload,
  UiBadge,
  UiBacktop,
  UiResult,
  UiLink,
  UiDatePicker,
  UiProgress,
  UiRadioGroup,
  UiConfirmDialog,
  UiAvatar,
]

export function registerUiComponents(app) {
  components.forEach((c) => {
    if (c && c.name) app.component(c.name, c)
  })
}

export default components
