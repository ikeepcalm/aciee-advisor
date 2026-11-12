import type { Lesson, TimetableProgram, WeekParity } from '@/api'

export interface CatchUpSlot {
  program: TimetableProgram
  lesson: Lesson
  sameTeacher: boolean
  clash: boolean
}

const TITLES = /\b(prof|conf|lect|asist|as|dr|drd|ing|univ|s\.l|sl)\.|\bcadru didactic asociat\b/g

export function fold(text: string): string {
  return text.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().replace(/\s+/g, ' ').trim()
}

function nameTokens(teacher: string | null): string[] {
  return teacher
    ? fold(teacher)
        .replace(TITLES, ' ')
        .split(/[^\p{L}]+/u)
        .filter(Boolean)
    : []
}

export function sameTeacher(a: string | null, b: string | null): boolean {
  const first = nameTokens(a)
  const second = nameTokens(b)
  const firstNames = new Set(first.filter((token) => token.length >= 3))
  const secondNames = new Set(second.filter((token) => token.length >= 3))
  const common = [...firstNames].filter((token) => secondNames.has(token))
  if (!common.length || (common.length < firstNames.size && common.length < secondNames.size)) {
    return false
  }
  const firstInitials = new Set(
    first.filter((token) => !common.includes(token)).map((token) => token[0]),
  )
  const secondInitials = new Set(
    second.filter((token) => !common.includes(token)).map((token) => token[0]),
  )
  return (
    !firstInitials.size ||
    !secondInitials.size ||
    [...firstInitials].some((initial) => secondInitials.has(initial))
  )
}

export function minutes(time: string): number {
  const [hours, mins] = time.split(':').map(Number)
  return hours! * 60 + mins!
}

function weeksOverlap(a: WeekParity, b: WeekParity): boolean {
  return a === 'ALL' || b === 'ALL' || a === b
}

export function overlaps(a: Lesson, b: Lesson): boolean {
  return (
    a.day === b.day &&
    weeksOverlap(a.weeks, b.weeks) &&
    minutes(a.start) < minutes(b.end) &&
    minutes(b.start) < minutes(a.end)
  )
}

export function ownLessonsOf(program: TimetableProgram, group: string, missed: Lesson): Lesson[] {
  return program.lessons.filter((lesson) => lesson !== missed && lesson.groups.includes(group))
}

export function catchUpSlots(
  programs: TimetableProgram[],
  ownProgram: TimetableProgram,
  ownGroup: string,
  missed: Lesson,
): CatchUpSlot[] {
  const course = fold(missed.course)
  const ownLessons = ownLessonsOf(ownProgram, ownGroup, missed)
  const slots: CatchUpSlot[] = []
  for (const program of programs) {
    for (const lesson of program.lessons) {
      if (
        lesson === missed ||
        lesson.groups.includes(ownGroup) ||
        lesson.kind !== missed.kind ||
        fold(lesson.course) !== course
      ) {
        continue
      }
      slots.push({
        program,
        lesson,
        sameTeacher: sameTeacher(missed.teacher, lesson.teacher),
        clash: ownLessons.some((own) => overlaps(own, lesson)),
      })
    }
  }
  return slots.sort(
    (a, b) =>
      Number(b.sameTeacher) - Number(a.sameTeacher) ||
      a.lesson.day - b.lesson.day ||
      minutes(a.lesson.start) - minutes(b.lesson.start),
  )
}
