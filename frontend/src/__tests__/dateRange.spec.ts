import { describe, expect, it } from 'vitest'
import { formatRange, formatRanges, parseRanges } from '@/dateRange'

describe('date ranges', () => {
  it('shows the exclusive upper bound as the last day', () => {
    expect(parseRanges('[2026-09-28,2027-02-15)')).toEqual([
      { from: '2026-09-28', to: '2027-02-14' },
    ])
  })

  it('keeps an open end open and crosses month ends', () => {
    expect(parseRanges('[2025-03-01,)')).toEqual([{ from: '2025-03-01', to: '' }])
    expect(parseRanges('[2026-12-19,2027-01-01)')[0].to).toBe('2026-12-31')
  })

  it('reads every range of a multirange', () => {
    expect(parseRanges('{[2026-12-19,2027-01-11),[2027-04-30,2027-05-10)}')).toEqual([
      { from: '2026-12-19', to: '2027-01-10' },
      { from: '2027-04-30', to: '2027-05-09' },
    ])
  })

  it('writes inclusive ranges and drops empty rows', () => {
    expect(formatRange({ from: '2026-09-28', to: '2027-02-14' })).toBe('[2026-09-28,2027-02-14]')
    expect(formatRange({ from: '', to: '' })).toBe('')
    expect(
      formatRanges([
        { from: '2026-12-19', to: '2027-01-10' },
        { from: '', to: '' },
      ]),
    ).toBe('{[2026-12-19,2027-01-10]}')
    expect(formatRanges([])).toBe('')
    expect(parseRanges(formatRange({ from: '2026-09-28', to: '2027-02-14' }))).toEqual([
      { from: '2026-09-28', to: '2027-02-14' },
    ])
  })
})
