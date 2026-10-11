import assert from 'node:assert/strict'
import test from 'node:test'
import {
  canEditUserRole,
  canDeleteUser,
  getEditableUserRoleOptions,
  isSuperAdmin,
} from '../src/utils/adminUserRoles.mjs'

test('recognizes a super administrator from the role or roles list', () => {
  assert.equal(isSuperAdmin({ role: 'ROLE_SUPER_ADMIN' }), true)
  assert.equal(isSuperAdmin({ roles: ['USER', 'SUPER_ADMIN'] }), true)
  assert.equal(isSuperAdmin({ roles: ['ROLE_SUPER_ADMIN'] }), true)
  assert.equal(isSuperAdmin({ role: 'ROLE_ADMIN', roles: ['USER', 'ADMIN'] }), false)
})

test('shows the super administrator option only to super administrators', () => {
  const adminOptions = getEditableUserRoleOptions({ role: 'ROLE_ADMIN' })
  const superAdminOptions = getEditableUserRoleOptions({ roles: ['USER', 'ADMIN', 'SUPER_ADMIN'] })

  assert.deepEqual(adminOptions.map(({ value }) => value), ['ROLE_USER', 'ROLE_ADMIN'])
  assert.deepEqual(superAdminOptions.map(({ value }) => value), [
    'ROLE_USER',
    'ROLE_ADMIN',
    'ROLE_SUPER_ADMIN',
  ])
})

test('prevents editing own role and protects super administrator rows from regular admins', () => {
  assert.equal(canEditUserRole({ id: '10', role: 'ROLE_ADMIN' }, { id: 10, role: 'ROLE_USER' }), false)
  assert.equal(canEditUserRole({ id: 11, role: 'ROLE_ADMIN' }, { id: 10, role: 'ROLE_SUPER_ADMIN' }), false)
  assert.equal(canEditUserRole({ id: 11, role: 'ROLE_ADMIN' }, { id: 10, role: 'ROLE_USER' }), true)
  assert.equal(canEditUserRole({ id: 11, roles: ['SUPER_ADMIN'] }, { id: 10, role: 'ROLE_SUPER_ADMIN' }), true)
})

test('shows delete only for accounts the operator is allowed to delete', () => {
  const admin = { id: 10, role: 'ROLE_ADMIN' }
  const superAdmin = { id: 11, role: 'ROLE_SUPER_ADMIN' }
  assert.equal(canDeleteUser(admin, { id: 12, role: 'ROLE_USER' }), true)
  assert.equal(canDeleteUser(admin, { id: 12, role: 'ROLE_ADMIN' }), false)
  assert.equal(canDeleteUser(admin, superAdmin), false)
  assert.equal(canDeleteUser(admin, { id: 10, role: 'ROLE_USER' }), false)
  assert.equal(canDeleteUser(superAdmin, { id: 12, role: 'ROLE_ADMIN' }), true)
  assert.equal(canDeleteUser(superAdmin, { id: 11, role: 'ROLE_USER' }), false)
})
