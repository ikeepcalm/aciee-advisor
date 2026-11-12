import type { TimetableChange } from '@/api'

export interface ChangeLine {
  type: 'added' | 'removed' | 'moved'
  slot: TimetableChange
  previous: TimetableChange | null
}

export type ChangedField = 'room' | 'time' | 'teacher' | 'weeks' | 'kind'

function pairKey(change: TimetableChange): string {
  return [change.detectedAt, change.day, change.startTime, change.subject].join('|')
}

export function newerThan(changes: TimetableChange[], seenAt: string | null): TimetableChange[] {
  const seen = seenAt ? Date.parse(seenAt) : NaN
  return Number.isNaN(seen) ? changes : changes.filter((c) => Date.parse(c.detectedAt) > seen)
}

export function newestDetectedAt(changes: TimetableChange[]): string | null {
  return changes.reduce<string | null>(
    (newest, c) =>
      !newest || Date.parse(c.detectedAt) > Date.parse(newest) ? c.detectedAt : newest,
    null,
  )
}

export function changeLines(changes: TimetableChange[]): ChangeLine[] {
  const paired = new Set<TimetableChange>()
  const lines: ChangeLine[] = []
  for (const change of changes) {
    if (paired.has(change)) {
      continue
    }
    paired.add(change)
    const other = changes.find(
      (o) => !paired.has(o) && o.change !== change.change && pairKey(o) === pairKey(change),
    )
    if (!other) {
      lines.push({ type: change.change, slot: change, previous: null })
      continue
    }
    paired.add(other)
    const [slot, previous] = change.change === 'added' ? [change, other] : [other, change]
    lines.push({ type: 'moved', slot, previous })
  }
  return lines
}

export function changedFields(line: ChangeLine): ChangedField[] {
  const { slot, previous } = line
  if (!previous) {
    return []
  }
  const differs: Record<ChangedField, boolean> = {
    room: slot.room !== previous.room,
    time: slot.endTime !== previous.endTime,
    teacher: slot.teacher !== previous.teacher,
    weeks: slot.weeks !== previous.weeks,
    kind: slot.kind !== previous.kind,
  }
  return (Object.keys(differs) as ChangedField[]).filter((field) => differs[field])
}
