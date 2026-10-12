import type { HomepagePlacement, HomepageSlot } from './api'

export const HOMEPAGE_SLOTS: HomepageSlot[] = ['FOCUS', 'COMPETITION', 'RECRUITMENT', 'RESOURCE']

export const HOMEPAGE_SLOT_LABELS: Record<HomepageSlot, string> = {
  FOCUS: '本期值得关注',
  COMPETITION: '竞赛资源',
  RECRUITMENT: '团队招募',
  RESOURCE: '资源与分享',
}

export function placementsInSlot(placements: HomepagePlacement[], slot: HomepageSlot) {
  return placements
    .filter((item) => item.slot === slot)
    .slice()
    .sort(
      (left, right) =>
        left.sortOrder - right.sortOrder || left.contentItemId.localeCompare(right.contentItemId),
    )
}

export function movePlacement(
  placements: HomepagePlacement[],
  slot: HomepageSlot,
  index: number,
  delta: number,
) {
  const current = placementsInSlot(placements, slot)
  const target = index + delta
  if (target < 0 || target >= current.length) return placements
  const reordered = current.slice()
  const [row] = reordered.splice(index, 1)
  reordered.splice(target, 0, row)
  return replaceSlot(placements, slot, reordered)
}

export function dropPlacement(
  placements: HomepagePlacement[],
  slot: HomepageSlot,
  fromIndex: number,
  toIndex: number,
) {
  const current = placementsInSlot(placements, slot)
  if (
    fromIndex === toIndex ||
    fromIndex < 0 ||
    toIndex < 0 ||
    fromIndex >= current.length ||
    toIndex >= current.length
  ) {
    return placements
  }
  const reordered = current.slice()
  const [row] = reordered.splice(fromIndex, 1)
  reordered.splice(toIndex, 0, row)
  return replaceSlot(placements, slot, reordered)
}

export function replaceSlot(
  placements: HomepagePlacement[],
  slot: HomepageSlot,
  next: HomepagePlacement[],
): HomepagePlacement[] {
  const others = placements.filter((item) => item.slot !== slot)
  return [
    ...others,
    ...next.map((item, index) => ({
      ...item,
      slot,
      sortOrder: index,
    })),
  ]
}

export function curationFingerprint(placements: HomepagePlacement[]) {
  return JSON.stringify(
    HOMEPAGE_SLOTS.flatMap((slot) =>
      placementsInSlot(placements, slot).map((item) => [
        item.slot,
        item.contentItemId,
        item.sortOrder,
        item.startsAt ?? null,
        item.endsAt ?? null,
      ]),
    ),
  )
}
