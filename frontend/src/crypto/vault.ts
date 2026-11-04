const ALPHABET = '0123456789ABCDEFGHJKMNPQRSTVWXYZ'
const CODE_LENGTH = 26

export interface StoredToken {
  offeringId: number
  fingerprint: string
  sig: string
  submitted?: boolean
}

export function toBase64(bytes: Uint8Array): string {
  return btoa(String.fromCharCode(...bytes))
}

export function fromBase64(text: string): Uint8Array<ArrayBuffer> {
  return Uint8Array.from(atob(text), (c) => c.charCodeAt(0))
}

export function recoveryCode(master: Uint8Array): string {
  let bits = 0
  let value = 0
  let code = ''
  for (const byte of master) {
    value = (value << 8) | byte
    bits += 8
    while (bits >= 5) {
      bits -= 5
      code += ALPHABET[(value >> bits) & 31]
    }
  }
  code += ALPHABET[(value << (5 - bits)) & 31]
  return code.match(/.{1,4}/g)!.join('-')
}

export function parseRecoveryCode(input: string): Uint8Array<ArrayBuffer> | null {
  const code = input.toUpperCase().replace(/[\s-]/g, '').replace(/O/g, '0').replace(/[IL]/g, '1')
  if (code.length !== CODE_LENGTH || [...code].some((c) => !ALPHABET.includes(c))) {
    return null
  }
  const master = new Uint8Array(16)
  let bits = 0
  let value = 0
  let index = 0
  for (const c of code) {
    value = ((value << 5) | ALPHABET.indexOf(c)) & 0xfff
    bits += 5
    if (bits >= 8 && index < master.length) {
      bits -= 8
      master[index++] = (value >> bits) & 0xff
    }
  }
  return (value & ((1 << bits) - 1)) === 0 ? master : null
}

async function vaultKey(master: Uint8Array<ArrayBuffer>): Promise<CryptoKey> {
  const base = await crypto.subtle.importKey('raw', master, 'HKDF', false, ['deriveKey'])
  return crypto.subtle.deriveKey(
    {
      name: 'HKDF',
      hash: 'SHA-256',
      salt: new Uint8Array(),
      info: new TextEncoder().encode('aciee-v1 vault'),
    },
    base,
    { name: 'AES-GCM', length: 256 },
    false,
    ['encrypt', 'decrypt'],
  )
}

export async function sealVault(master: Uint8Array<ArrayBuffer>, tokens: StoredToken[]) {
  const nonce = crypto.getRandomValues(new Uint8Array(12))
  const plaintext = new TextEncoder().encode(JSON.stringify({ tokens }))
  const ciphertext = await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv: nonce },
    await vaultKey(master),
    plaintext,
  )
  return { ciphertext: toBase64(new Uint8Array(ciphertext)), nonce: toBase64(nonce) }
}

export async function openVault(
  master: Uint8Array<ArrayBuffer>,
  vault: { ciphertext: string; nonce: string },
): Promise<StoredToken[] | null> {
  try {
    const plaintext = await crypto.subtle.decrypt(
      { name: 'AES-GCM', iv: fromBase64(vault.nonce) },
      await vaultKey(master),
      fromBase64(vault.ciphertext),
    )
    return JSON.parse(new TextDecoder().decode(plaintext)).tokens
  } catch {
    return null
  }
}
