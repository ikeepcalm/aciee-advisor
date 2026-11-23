import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { describeError } from '@/api'
import { fold } from '@/timetable/catchUp'

export interface GroupSummary {
  id: number
  code: string
  specialization: string
  studyYear: number
  academicYear: string
  subgroups?: { id: number; label: string }[]
  hasLeader?: boolean
}
export interface JoinRequest {
  id: number
  email: string
  displayName: string | null
  groupCode: string
  academicYear: string
  kind: string
  subgroup: string | null
  status: string
}
export interface TeachingSlot {
  offeringId: number
  title: string
  semesterNo: number
  component: string
  subgroupId: number | null
  subgroup: string | null
  professorId: number | null
}
export interface TeachingPart {
  key: string
  component: string
  whole: TeachingSlot
  subgroups: TeachingSlot[]
}
export interface TeachingCourse {
  offeringId: number
  title: string
  semesterNo: number
  parts: TeachingPart[]
}

export function matchesGroup(group: GroupSummary, query: string): boolean {
  const code = fold(group.code)
  const specialization = fold(group.specialization)
  const tokens = fold(query).split(' ').filter(Boolean)
  return tokens.every((token) =>
    tokens.length > 1 && /^\d$/.test(token)
      ? group.studyYear === Number(token)
      : code.includes(token) || token.startsWith(code) || specialization.includes(token),
  )
}

export const slotKey = (slot: TeachingSlot) =>
  `${slot.offeringId}-${slot.component}-${slot.subgroupId ?? 0}`

export function teachingCourses(slots: TeachingSlot[]): TeachingCourse[] {
  const courses: TeachingCourse[] = []
  for (const slot of slots) {
    let course = courses.at(-1)
    if (course?.offeringId !== slot.offeringId) {
      course = {
        offeringId: slot.offeringId,
        title: slot.title,
        semesterNo: slot.semesterNo,
        parts: [],
      }
      courses.push(course)
    }
    const part = course.parts.at(-1)
    if (part?.component === slot.component && slot.subgroupId !== null) {
      part.subgroups.push(slot)
    } else {
      course.parts.push({
        key: `${slot.offeringId}-${slot.component}`,
        component: slot.component,
        whole: slot,
        subgroups: [],
      })
    }
  }
  return courses
}

export function partIsSet(
  part: TeachingPart,
  professorOf = (slot: TeachingSlot) => slot.professorId,
): boolean {
  return (
    professorOf(part.whole) !== null ||
    (part.subgroups.length > 0 && part.subgroups.every((slot) => professorOf(slot) !== null))
  )
}

export function useRun() {
  const { t } = useI18n()
  const error = ref('')
  const notice = ref('')
  async function run(action: () => Promise<unknown>, success = '') {
    error.value = ''
    notice.value = ''
    try {
      await action()
      notice.value = success
    } catch (e) {
      error.value = describeError(t, e)
    }
  }
  return { error, notice, run }
}
