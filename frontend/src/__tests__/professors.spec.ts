import { describe, expect, it } from 'vitest'
import { academicYear } from '@/professors'
import { fold } from '@/timetable/catchUp'

describe('academicYear', () => {
  it('starts a new academic year in October', () => {
    expect(academicYear('2026-10-05')).toBe('2026–2027')
    expect(academicYear('2026-09-28')).toBe('2025–2026')
    expect(academicYear('2027-01-11')).toBe('2026–2027')
  })
})

describe('fold', () => {
  it('ignores diacritics and case', () => {
    expect(fold('Ioan ȘUȘNEA')).toBe('ioan susnea')
    expect(fold(' Țiplea  Ăâî ')).toBe('tiplea aai')
  })
})
