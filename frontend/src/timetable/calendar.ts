import type { Lesson, WeekParity } from '@/api'
import { overlaps } from './catchUp'

export type Parity = Exclude<WeekParity, 'ALL'>

export interface Calibration {
  anchor: string
  parity: Parity
}

export const ROMAN = ['', 'I', 'II', 'III', 'IV']

export function isoDate(date: Date): string {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

export function calendarFeedUrl(origin: string, programCode: string, group: string): string {
  const query = new URLSearchParams({ program: programCode, group })
  return `${origin}/api/timetable/calendar.ics?${query}`
}

export function mondayOf(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() - ((date.getDay() + 6) % 7))
}

export function upcomingDates(
  lesson: Pick<Lesson, 'day' | 'end' | 'weeks'>,
  now: Date,
  parityOf: (monday: Date) => WeekParity | null,
  count = 2,
): Date[] {
  const [endHour, endMinute] = lesson.end.split(':').map(Number)
  const mondayDate = mondayOf(now).getDate()
  const dates: Date[] = []
  for (let week = 0; dates.length < count && week <= count * 2; week++) {
    const monday = new Date(now.getFullYear(), now.getMonth(), mondayDate + week * 7)
    const date = new Date(now.getFullYear(), now.getMonth(), mondayDate + week * 7 + lesson.day - 1)
    const end = new Date(date.getFullYear(), date.getMonth(), date.getDate(), endHour, endMinute)
    if (end > now && (lesson.weeks === 'ALL' || parityOf(monday) === lesson.weeks)) {
      dates.push(date)
    }
  }
  return dates
}

export function freeDates(
  lesson: Lesson,
  ownLessons: Lesson[],
  now: Date,
  parityOf: (monday: Date) => WeekParity | null,
  count = 2,
): Date[] {
  return upcomingDates(lesson, now, parityOf, count * 2)
    .filter((date) => {
      const weeks = parityOf(mondayOf(date)) ?? lesson.weeks
      return !ownLessons.some((own) => overlaps(own, { ...lesson, weeks }))
    })
    .slice(0, count)
}
