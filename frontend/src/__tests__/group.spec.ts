import { describe, expect, it } from 'vitest'
import { matchesGroup, partIsSet, teachingCourses, type TeachingSlot } from '@/group'

const group = {
  id: 1,
  code: '22C31',
  specialization: 'Calculatoare',
  studyYear: 3,
  academicYear: '2026–2027',
}

describe('matchesGroup', () => {
  it('finds a group by code, timetable label, specialisation and year', () => {
    expect(matchesGroup(group, '')).toBe(true)
    expect(matchesGroup(group, '2')).toBe(true)
    expect(matchesGroup(group, '22c31a')).toBe(true)
    expect(matchesGroup(group, 'calculatoare 3')).toBe(true)
    expect(matchesGroup({ ...group, specialization: 'Automatică' }, 'automatica 3')).toBe(true)
    expect(matchesGroup(group, 'calculatoare 1')).toBe(false)
    expect(matchesGroup(group, '22C32')).toBe(false)
  })
})

describe('teachingCourses', () => {
  const slot = (
    offeringId: number,
    component: string,
    subgroupId: number | null,
    professorId: number | null,
  ): TeachingSlot => ({
    offeringId,
    title: `Course ${offeringId}`,
    semesterNo: 1,
    component,
    subgroupId,
    subgroup: subgroupId === null ? null : String(subgroupId),
    professorId,
  })

  it('groups slots by course and component and counts a component once', () => {
    const courses = teachingCourses([
      slot(1, 'lecture', null, 7),
      slot(1, 'lab', null, null),
      slot(1, 'lab', 1, 8),
      slot(1, 'lab', 2, 9),
      slot(2, 'lecture', null, null),
      slot(2, 'seminar', null, null),
      slot(2, 'seminar', 1, 8),
      slot(2, 'seminar', 2, null),
    ])
    expect(courses.map((course) => course.parts.map((part) => part.subgroups.length))).toEqual([
      [0, 2],
      [0, 2],
    ])
    expect(courses.flatMap((course) => course.parts).map((part) => partIsSet(part))).toEqual([
      true,
      true,
      false,
      false,
    ])
  })
})
