import { describe, expect, it } from 'vitest'
import type { TimetableChange } from '@/api'
import { changedFields, changeLines, newerThan, newestDetectedAt } from '../changes'

const base: TimetableChange = {
  change: 'added',
  detectedAt: '2026-11-10T08:00:00Z',
  day: 2,
  startTime: '14:00',
  endTime: '15:50',
  weeks: 'ALL',
  kind: 'L',
  subject: 'Fizica',
  room: 'Y405',
  teacher: 'G. Shabo',
}
const change = (patch: Partial<TimetableChange>): TimetableChange => ({ ...base, ...patch })

describe('changeLines', () => {
  it('merges a removed and an added class with the same day, start and subject', () => {
    const lines = changeLines([change({}), change({ change: 'removed', room: 'Y402' })])
    expect(lines).toHaveLength(1)
    expect(lines[0]!.type).toBe('moved')
    expect(lines[0]!.slot.room).toBe('Y405')
    expect(lines[0]!.previous!.room).toBe('Y402')
    expect(changedFields(lines[0]!)).toEqual(['room'])
  })

  it('pairs whichever of the two comes first', () => {
    const lines = changeLines([
      change({ change: 'removed', endTime: '16:50' }),
      change({ teacher: null }),
    ])
    expect(lines.map((line) => line.type)).toEqual(['moved'])
    expect(lines[0]!.slot.change).toBe('added')
    expect(changedFields(lines[0]!)).toEqual(['time', 'teacher'])
  })

  it('keeps unrelated changes apart', () => {
    const lines = changeLines([
      change({}),
      change({ change: 'removed', startTime: '16:00' }),
      change({ change: 'removed', day: 3 }),
      change({ change: 'removed', subject: 'Chimie' }),
      change({ change: 'removed', detectedAt: '2026-11-03T08:00:00Z' }),
    ])
    expect(lines.map((line) => line.type)).toEqual([
      'added',
      'removed',
      'removed',
      'removed',
      'removed',
    ])
    expect(changedFields(lines[0]!)).toEqual([])
  })

  it('uses each change in one pair only', () => {
    const lines = changeLines([
      change({ weeks: 'ODD' }),
      change({ weeks: 'EVEN' }),
      change({ change: 'removed' }),
    ])
    expect(lines.map((line) => line.type)).toEqual(['moved', 'added'])
  })
})

describe('seen changes', () => {
  const older = change({ detectedAt: '2026-11-03T08:00:00.5Z' })
  const newer = change({ detectedAt: '2026-11-10T08:00:00Z' })

  it('shows everything on a device that dismissed nothing', () => {
    expect(newerThan([newer, older], null)).toHaveLength(2)
  })

  it('hides what was already seen', () => {
    expect(newerThan([newer, older], older.detectedAt)).toEqual([newer])
    expect(newerThan([newer, older], newer.detectedAt)).toEqual([])
  })

  it('finds the newest detection', () => {
    expect(newestDetectedAt([older, newer])).toBe(newer.detectedAt)
    expect(newestDetectedAt([])).toBeNull()
  })
})
