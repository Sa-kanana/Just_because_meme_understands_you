/** 中文注释：可分配角色选项，普通管理员仅使用普通用户和管理员两项。 */
const STANDARD_ROLE_OPTIONS = Object.freeze([
  { label: '普通用户', value: 'ROLE_USER' },
  { label: '管理员', value: 'ROLE_ADMIN' },
])

/** 中文注释：超级管理员拥有的完整角色选项。 */
const ALL_ROLE_OPTIONS = Object.freeze([
  ...STANDARD_ROLE_OPTIONS,
  { label: '超级管理员', value: 'ROLE_SUPER_ADMIN' },
])

/** 中文注释：统一角色代码格式，兼容 USER 与 ROLE_USER 两种写法。 */
function normalizeRoleCode(role) {
  const value = String(role == null ? '' : role).trim().toUpperCase()
  if (!value) return ''
  return value.startsWith('ROLE_') ? value : `ROLE_${value}`
}

/** 中文注释：判断登录用户是否拥有超级管理员角色。 */
export function isSuperAdmin(user) {
  if (!user) return false
  const roles = [user.role, ...(Array.isArray(user.roles) ? user.roles : [])]
  return roles.some((role) => normalizeRoleCode(role) === 'ROLE_SUPER_ADMIN')
}

/** 中文注释：按操作者权限返回可分配的角色选项。 */
export function getEditableUserRoleOptions(operator) {
  return isSuperAdmin(operator) ? ALL_ROLE_OPTIONS : STANDARD_ROLE_OPTIONS
}

/** 中文注释：判断操作者是否可以修改目标用户角色，遵循后端的本人和超级管理员保护规则。 */
export function canEditUserRole(operator, target) {
  if (!operator || !target) return false

  const operatorId = operator.id == null ? '' : String(operator.id).trim()
  const targetId = target.id == null ? '' : String(target.id).trim()
  if (operatorId && targetId && operatorId === targetId) return false

  const targetIsSuperAdmin = normalizeRoleCode(target.role) === 'ROLE_SUPER_ADMIN'
  return !targetIsSuperAdmin || isSuperAdmin(operator)
}

/** 中文注释：普通管理员仅能删除普通用户，超级管理员可删除其他角色，但不能删除本人。 */
export function canDeleteUser(operator, target) {
  if (!operator || !target) return false
  const operatorId = operator.id == null ? '' : String(operator.id).trim()
  const targetId = target.id == null ? '' : String(target.id).trim()
  if (operatorId && targetId && operatorId === targetId) return false
  const role = normalizeRoleCode(target.role)
  return isSuperAdmin(operator) || (role !== 'ROLE_ADMIN' && role !== 'ROLE_SUPER_ADMIN')
}
