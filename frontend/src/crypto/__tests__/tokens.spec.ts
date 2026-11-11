import { describe, expect, it } from 'vitest'
import { pendingOfferings, type RateableOffering } from '../tokens'

function offering(offeringId: number, overrides: Partial<RateableOffering> = {}): RateableOffering {
  return {
    offeringId,
    title: `Course ${offeringId}`,
    academicYear: '2026-2027',
    semesterNo: 1,
    opensAt: null,
    open: true,
    fingerprint: 'fp',
    issued: false,
    ...overrides,
  }
}

describe('pendingOfferings', () => {
  it('keeps open courses that still need a rating from this device', () => {
    const offerings = [
      offering(1),
      offering(2, { issued: true }),
      offering(3, { issued: true }),
      offering(4, { issued: true }),
      offering(5, { open: false }),
      offering(6, { fingerprint: null }),
    ]
    const tokens = [
      { offeringId: 2, fingerprint: 'fp', sig: 's' },
      { offeringId: 3, fingerprint: 'fp', sig: 's', submitted: true },
      { offeringId: 9, fingerprint: 'fp', sig: 's' },
    ]
    expect(pendingOfferings(offerings, tokens).map((o) => o.offeringId)).toEqual([1, 2])
  })
})
