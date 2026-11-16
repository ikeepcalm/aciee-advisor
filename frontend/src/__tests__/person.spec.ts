import { describe, expect, it } from 'vitest'
import { initials, shortName } from '@/person'

describe('person', () => {
  it('takes the first and last word of a name', () => {
    expect(initials('Bohdan Horokh', 'bh110@student.ugal.ro')).toBe('BH')
    expect(initials('ana-maria ștefănescu popa', 'x@student.ugal.ro')).toBe('AP')
    expect(initials('Ștefan', 'x@student.ugal.ro')).toBe('ȘT')
  })

  it('falls back to the address when there is no name', () => {
    expect(initials(null, 'bh110@student.ugal.ro')).toBe('BH')
    expect(initials('', 'ion.pop@student.ugal.ro')).toBe('IO')
    expect(shortName(null, 'bh110@student.ugal.ro')).toBe('bh110')
    expect(shortName('Bohdan Horokh', 'bh110@student.ugal.ro')).toBe('Bohdan Horokh')
  })
})
