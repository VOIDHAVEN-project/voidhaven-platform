export const DRAFT_DISCARD_AUTHORIZED_EVENT = 'voidhaven:draft-discard-authorized'

export function authorizeDraftDiscard() {
  window.dispatchEvent(new Event(DRAFT_DISCARD_AUTHORIZED_EVENT))
}
