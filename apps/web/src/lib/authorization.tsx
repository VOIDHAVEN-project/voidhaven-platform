import { createContext, useContext } from 'react'
import type { Identity, ManagedContent, EditablePartnerContent } from './api'

export const IdentityContext = createContext<Identity | null>(null)
export const useIdentity = () => useContext(IdentityContext)
type ContentScope = Pick<ManagedContent | EditablePartnerContent, 'ownerPartnerProfileId'> & {
  moduleKey?: string
}
export function can(identity: Identity | null, permission: string, content?: ContentScope): boolean {
  if (!identity) return false
  if (identity.roles.includes('SUPER_ADMIN')) return true
  if (identity.authorizationExpiresAt && Date.parse(identity.authorizationExpiresAt) <= Date.now())
    return false
  return (identity.permissions ?? []).some((grant) => {
    if (
      grant.permission !== permission &&
      !(permission === 'CONTENT_READ' && grant.permission.startsWith('CONTENT_'))
    )
      return false
    if (!content || grant.scopeType === 'GLOBAL') return true
    return grant.scopeType === 'MODULES'
      ? Boolean(content.moduleKey && grant.scopeIds.includes(content.moduleKey))
      : Boolean(content.ownerPartnerProfileId && grant.scopeIds.includes(content.ownerPartnerProfileId))
  })
}
export function canCreate(identity: Identity | null): boolean {
  if (identity?.authorizationExpiresAt && Date.parse(identity.authorizationExpiresAt) <= Date.now())
    return identity.roles.includes('SUPER_ADMIN') || identity.roles.includes('PARTNER')
  return Boolean(
    identity?.roles.includes('SUPER_ADMIN') ||
      identity?.roles.includes('PARTNER') ||
      identity?.permissions?.some(
        (grant) => grant.permission === 'CONTENT_EDIT' && grant.scopeType !== 'ORGANIZATIONS',
      ),
  )
}
