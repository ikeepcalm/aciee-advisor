import { describe, expect, it } from 'vitest'
import type { Lesson, TimetableProgram } from '@/api'
import { catchUpSlots, overlaps, sameTeacher } from '../catchUp'

function lesson(partial: Partial<Lesson>): Lesson {
  return {
    day: 1,
    start: '08:00',
    end: '09:50',
    weeks: 'ALL',
    groups: [],
    kind: 'L',
    subject: 'AM',
    room: null,
    teacher: null,
    note: null,
    lines: [],
    course: 'Analiza Matematica',
    courseId: null,
    ...partial,
  }
}

function program(code: string, lessons: Lesson[]): TimetableProgram {
  return {
    code,
    year: 1,
    name: code,
    title: code,
    semester: 'Semestrul I',
    pdfUrl: '',
    updatedAt: null,
    groups: [...new Set(lessons.flatMap((l) => l.groups))],
    lessons,
  }
}

const lecture = lesson({
  kind: 'C',
  subject: 'Analiza Matematica (AM)',
  groups: ['A1', 'A2', 'B1'],
})
const missed = lesson({
  day: 2,
  start: '10:00',
  end: '11:50',
  groups: ['A1'],
  teacher: 'I. Lixandru',
})
const ownOther = lesson({
  day: 3,
  start: '12:00',
  end: '13:50',
  groups: ['A1'],
  subject: 'FIZ',
  course: 'Fizica',
})
const sameTeacherFree = lesson({
  day: 4,
  groups: ['A2'],
  teacher: 'prof. I. Lixandru',
  room: 'Y102',
})
const sameTeacherClash = lesson({
  day: 3,
  start: '12:00',
  end: '13:50',
  groups: ['B1'],
  teacher: 'I. Lixandru',
})
const otherTeacher = lesson({
  day: 1,
  start: '14:00',
  end: '15:50',
  groups: ['B1'],
  teacher: 'A. Pop',
})
const seminar = lesson({ day: 5, kind: 'S', groups: ['A2'], teacher: 'I. Lixandru' })
const otherProgramLab = lesson({
  day: 5,
  start: '16:00',
  end: '17:50',
  groups: ['C1'],
  subject: 'Analiză matematică (AM)',
  course: 'Analiză matematică',
  teacher: 'conf.dr. I. Lixandru',
})

const own = program('an_1_CTI', [
  lecture,
  missed,
  ownOther,
  sameTeacherFree,
  sameTeacherClash,
  otherTeacher,
  seminar,
])
const other = program('an_1_IS', [otherProgramLab])

describe('catch-up finder', () => {
  it('matches teachers across spellings', () => {
    expect(sameTeacher('conf.dr. I. Lixandru', 'I. Lixandru')).toBe(true)
    expect(sameTeacher('I.Cighir', 'I Cighir')).toBe(true)
    expect(sameTeacher('D.Ionescu', 'Ionescu D.')).toBe(true)
    expect(sameTeacher('A.Oancă', 'A.M. Oanca')).toBe(true)
    expect(sameTeacher('L. Scintei', 'As.Lil. Scintei')).toBe(true)
    expect(sameTeacher('ș.l.dr.ing. A. Pop', 'A. Pop')).toBe(true)
    expect(sameTeacher('A. Cighir', 'I. Cighir')).toBe(false)
    expect(sameTeacher('Ana Ionescu', 'Ana Popescu')).toBe(false)
    expect(sameTeacher(null, 'A. Pop')).toBe(false)
  })

  it('lists the same course and class type for other groups, same teacher first, clashes marked', () => {
    const slots = catchUpSlots([own, other], own, 'A1', missed)

    expect(slots.map((s) => s.lesson)).toEqual([
      sameTeacherClash,
      sameTeacherFree,
      otherProgramLab,
      otherTeacher,
    ])
    expect(slots.map((s) => [s.sameTeacher, s.clash])).toEqual([
      [true, true],
      [true, false],
      [true, false],
      [false, false],
    ])
  })

  it('does not count the missed class itself as a clash', () => {
    const sameTime = lesson({
      day: 2,
      start: '10:00',
      end: '11:50',
      groups: ['A2'],
      teacher: 'I. Lixandru',
    })
    const withSameTime = program('an_1_CTI', [lecture, missed, sameTime])

    expect(catchUpSlots([withSameTime], withSameTime, 'A1', missed)[0]).toMatchObject({
      lesson: sameTime,
      clash: false,
    })
  })

  it('treats odd and even weeks as not overlapping', () => {
    expect(overlaps(lesson({ weeks: 'ODD' }), lesson({ weeks: 'EVEN' }))).toBe(false)
    expect(
      overlaps(lesson({ weeks: 'ODD' }), lesson({ weeks: 'ALL', start: '09:00', end: '10:50' })),
    ).toBe(true)
    expect(overlaps(lesson({}), lesson({ start: '09:50', end: '11:40' }))).toBe(false)
  })
})
