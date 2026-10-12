import { useMemo, useSyncExternalStore } from 'react'

export type CommunityGrantState = { grant: string; expiresAt: string }

/**
 * 邀请码授权只保存在模块级内存里：
 * - 同一个页面会话内跨路由共享（路由树会因错误边界按地址重建，Context 会随之丢失）；
 * - 不写入 localStorage、sessionStorage 或 Cookie；
 * - 浏览器刷新或新开页面后为空，必须重新验证；
 * - 超过 expiresAt 的授权按未授权处理。
 */
let currentGrant: CommunityGrantState | null = null
const listeners = new Set<() => void>()

function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

function readGrant() {
  return currentGrant
}

export function setCommunityGrant(next: CommunityGrantState | null) {
  currentGrant = next
  listeners.forEach((listener) => listener())
}

export function clearCommunityGrant() {
  setCommunityGrant(null)
}

export function useCommunityGrant() {
  const value = useSyncExternalStore(subscribe, readGrant, readGrant)
  const active =
    value !== null && Number.isFinite(Date.parse(value.expiresAt)) && Date.parse(value.expiresAt) > Date.now()
  return useMemo(
    () => ({
      grant: active ? value.grant : null,
      expiresAt: active ? value.expiresAt : null,
      setGrant: setCommunityGrant,
      clearGrant: clearCommunityGrant,
    }),
    [active, value],
  )
}
