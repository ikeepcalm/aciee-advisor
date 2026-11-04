import { RSABSSA } from '@cloudflare/blindrsa-ts'

const suite = RSABSSA.SHA384.PSSZero.Deterministic()
const encoder = new TextEncoder()

export async function tokenMessage(
  master: Uint8Array<ArrayBuffer>,
  offeringId: number,
): Promise<Uint8Array> {
  const key = await crypto.subtle.importKey(
    'raw',
    master,
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign'],
  )
  const input = new Uint8Array([...encoder.encode('aciee-v1'), 0, 0, 0, 0])
  new DataView(input.buffer).setUint32(input.length - 4, offeringId)
  return new Uint8Array(await crypto.subtle.sign('HMAC', key, input))
}

export function blind(publicKey: CryptoKey, msg: Uint8Array) {
  return suite.blind(publicKey, suite.prepare(msg))
}

export function finalize(
  publicKey: CryptoKey,
  msg: Uint8Array,
  blindSig: Uint8Array,
  inv: Uint8Array,
) {
  return suite.finalize(publicKey, suite.prepare(msg), blindSig, inv)
}
