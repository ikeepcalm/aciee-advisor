import { describe, expect, it } from 'vitest'
import { guideFor } from '@/guide/content'

const LOCALES = ['ro', 'en', 'uk', 'ru']
const ORDER = [
  'timetable',
  'attendance',
  'credits',
  'exams',
  'scholarships',
  'budget',
  'calendar',
  'contact',
  'fun',
]
const reference = guideFor('en')

describe('guide content', () => {
  it.each(LOCALES)('%s matches the step order, highlights and tips of the reference', (locale) => {
    const guide = guideFor(locale)
    expect(guide.sections.map((section) => section.id)).toEqual(ORDER)
    expect(guide.welcome).toContain(guide.welcomeMark)
    guide.sections.forEach((section, index) => {
      const expected = reference.sections[index]
      expect(section.short).toContain(section.mark)
      expect(section.points.length).toBe(expected?.points.length)
      expect(section.tips?.length).toBe(expected?.tips?.length)
      expect(section.kinds?.map((kind) => kind.id)).toEqual(expected?.kinds?.map((kind) => kind.id))
      expect(section.sources.map((source) => source.url)).toEqual(
        expected?.sources.map((source) => source.url),
      )
    })
  })

  it('keeps student tips apart from sourced rules', () => {
    const timetable = reference.sections[0]
    expect(timetable?.sources).toEqual([])
    expect(timetable?.action?.to).toBe('/timetable')
    expect(timetable?.kinds?.map((kind) => kind.id)).toEqual(['lecture', 'lab', 'seminar'])
    const tipped = reference.sections.filter((section) => section.tips).map((section) => section.id)
    expect(tipped).toEqual(['attendance', 'exams', 'contact'])
  })
})
