import { describe, expect, it } from 'vitest'
import type { Lesson } from '@/api'
import { calendarFeedUrl, freeDates, isoDate, upcomingDates } from '../calendar'

const oddOnNinth = (monday: Date) =>
  Math.round((monday.getTime() - new Date(2026, 10, 9).getTime()) / (7 * 864e5)) % 2 === 0
    ? 'ODD'
    : 'EVEN'
const thursdayNoon = new Date(2026, 10, 12, 12, 0)

describe('isoDate', () => {
  it('pads local dates', () => {
    expect(isoDate(new Date(2026, 10, 9))).toBe('2026-11-09')
  })
})

describe('calendarFeedUrl', () => {
  it('encodes the programme and group', () => {
    expect(calendarFeedUrl('https://x.ro', 'an_2_CTI', '22C21A')).toBe(
      'https://x.ro/api/timetable/calendar.ics?program=an_2_CTI&group=22C21A',
    )
  })
})

describe('upcomingDates', () => {
  const days = (dates: Date[]) => dates.map((date) => date.getDate())

  it('skips a class that already ended this week', () => {
    expect(
      days(upcomingDates({ day: 3, end: '13:50', weeks: 'ALL' }, thursdayNoon, oddOnNinth)),
    ).toEqual([18, 25])
  })

  it('keeps a class still running today', () => {
    expect(
      days(upcomingDates({ day: 4, end: '13:50', weeks: 'ALL' }, thursdayNoon, oddOnNinth)),
    ).toEqual([12, 19])
  })

  it('resolves odd and even classes to real days', () => {
    expect(
      days(upcomingDates({ day: 5, end: '11:50', weeks: 'EVEN' }, thursdayNoon, oddOnNinth)),
    ).toEqual([20, 4])
  })

  it('gives no dates for an odd or even class before calibration', () => {
    expect(upcomingDates({ day: 5, end: '11:50', weeks: 'ODD' }, thursdayNoon, () => null)).toEqual(
      [],
    )
  })
})

describe('freeDates', () => {
  const lesson = (partial: Partial<Lesson>): Lesson => ({
    day: 3,
    start: '12:00',
    end: '13:50',
    weeks: 'ALL',
    groups: [],
    kind: 'S',
    subject: 'EN',
    room: null,
    teacher: null,
    note: null,
    lines: [],
    ...partial,
  })
  const days = (dates: Date[]) => dates.map((date) => date.getDate())

  it('keeps the weeks where a weekly slot misses an even-week class of the student', () => {
    expect(
      days(freeDates(lesson({}), [lesson({ weeks: 'EVEN' })], thursdayNoon, oddOnNinth)),
    ).toEqual([25, 9])
  })

  it('has no free date when the student is busy every week', () => {
    expect(freeDates(lesson({}), [lesson({})], thursdayNoon, oddOnNinth)).toEqual([])
  })
})
