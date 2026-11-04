import { describe, expect, it } from 'vitest'
import { openVault, parseRecoveryCode, recoveryCode, sealVault } from '../vault'

const randomMaster = () => crypto.getRandomValues(new Uint8Array(16))

describe('recovery code', () => {
  it('round-trips every master through the code', () => {
    for (let i = 0; i < 200; i++) {
      const master = randomMaster()
      const code = recoveryCode(master)
      expect(code).toMatch(/^([0-9A-HJKMNP-TV-Z]{4}-){6}[0-9A-HJKMNP-TV-Z]{2}$/)
      expect(parseRecoveryCode(code)).toEqual(master)
    }
  })

  it('accepts lowercase, spaces and look-alike letters', () => {
    const master = new Uint8Array(16)
    expect(recoveryCode(master)).toBe('0000-0000-0000-0000-0000-0000-00')
    expect(parseRecoveryCode(' oooo oooo-OOOO 0000 0000 0000 00 ')).toEqual(master)
    expect(parseRecoveryCode(recoveryCode(new Uint8Array(16).fill(255)).toLowerCase())).toEqual(
      new Uint8Array(16).fill(255),
    )
  })

  it('rejects wrong length, alphabet and padding', () => {
    expect(parseRecoveryCode('0000-0000')).toBeNull()
    expect(parseRecoveryCode('U000-0000-0000-0000-0000-0000-00')).toBeNull()
    expect(parseRecoveryCode('0000-0000-0000-0000-0000-0000-01')).toBeNull()
  })
})

describe('vault', () => {
  it('opens with the same master and not with another', async () => {
    const master = randomMaster()
    const tokens = [{ offeringId: 7, fingerprint: 'AQ==', sig: 'Ag==' }]
    const sealed = await sealVault(master, tokens)
    expect(await openVault(master, sealed)).toEqual(tokens)
    expect(await openVault(randomMaster(), sealed)).toBeNull()
  })
})
