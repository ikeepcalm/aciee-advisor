import { describe, expect, it } from 'vitest'
import { academicYear, pillarAverages, radarFor } from '@/professors'
import { fold } from '@/timetable/catchUp'

describe('pillarAverages', () => {
  it('averages the rated axes of each pillar', () => {
    const axes = ['a', 'a', 'b', 'c'].map((group) => ({ code: group, label: group, group }))
    const pillars = ['a', 'b', 'c'].map((code) => ({ code, label: code, group: null }))
    expect(pillarAverages(axes, pillars, [4, 8, null, 7]).map((p) => p.value)).toEqual([6, null, 7])
  })
})

describe('radarFor', () => {
  const axes = ['grading', 'lecture', 'lab', 'lab'].map((group, i) => ({
    code: `a0${i + 1}`,
    label: group,
    group,
  }))
  const pillars = ['lecture', 'lab', 'grading'].map((code) => ({ code, label: code, group: null }))

  it('keeps one class type and the shared grading block', () => {
    const radar = radarFor(axes, pillars, [7, null, 5, 6], 'lab')
    expect(radar.axes.map((a) => a.code)).toEqual(['a01', 'a03', 'a04'])
    expect(radar.scores).toEqual([7, 5, 6])
    expect(radar.pillars.map((p) => p.code)).toEqual(['lab', 'grading'])
  })

  it('keeps only scored criteria when no class type is given', () => {
    expect(radarFor(axes, [], [7, null, 5, null]).axes.map((a) => a.code)).toEqual(['a01', 'a03'])
  })
})

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
