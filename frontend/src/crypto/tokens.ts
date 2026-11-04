import { api, ApiError } from '@/api'
import { blind, finalize, tokenMessage } from '@/crypto/blindRsa'
import { fromBase64, openVault, sealVault, toBase64, type StoredToken } from '@/crypto/vault'
import pinnedKeys from '@/crypto/pinned-keys.json'

export interface RateableOffering {
  offeringId: number
  title: string
  academicYear: string
  semesterNo: number
  opensAt: string | null
  open: boolean
  fingerprint: string | null
  issued: boolean
}

interface Blinding {
  offeringId: number
  fingerprint: string
  blindedMsg: string
  inv: string
}

interface PublishedKey {
  offeringId: number
  fingerprint: string
  publicKey: string
}

interface Vault {
  ciphertext: string
  nonce: string
}

export class UnknownKeyError extends Error {}

type StoreName = 'master' | 'blindings' | 'tokens'

function database(userId: number): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(`aciee-${userId}`, 1)
    request.onupgradeneeded = () => {
      request.result.createObjectStore('master')
      request.result.createObjectStore('blindings', { keyPath: 'offeringId' })
      request.result.createObjectStore('tokens', { keyPath: 'offeringId' })
    }
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error)
  })
}

async function run<T>(
  userId: number,
  store: StoreName,
  action: (s: IDBObjectStore) => IDBRequest<T>,
): Promise<T> {
  const db = await database(userId)
  return new Promise((resolve, reject) => {
    const request = action(db.transaction(store, 'readwrite').objectStore(store))
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error)
  }).finally(() => db.close()) as Promise<T>
}

export function localMaster(userId: number): Promise<Uint8Array<ArrayBuffer> | undefined> {
  return run(userId, 'master', (s) => s.get('master'))
}

export function localTokens(userId: number): Promise<StoredToken[]> {
  return run(userId, 'tokens', (s) => s.getAll())
}

async function remoteVault(): Promise<Vault | null> {
  try {
    return await api<Vault>('GET', '/api/vault')
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) {
      return null
    }
    throw e
  }
}

export async function vaultExists(): Promise<boolean> {
  return (await remoteVault()) !== null
}

export async function createMaster(userId: number, master: Uint8Array<ArrayBuffer>) {
  await api('PUT', '/api/vault', await sealVault(master, []))
  await run(userId, 'master', (s) => s.put(master, 'master'))
}

export async function restoreMaster(
  userId: number,
  master: Uint8Array<ArrayBuffer>,
): Promise<boolean> {
  const vault = await remoteVault()
  const tokens = vault && (await openVault(master, vault))
  if (!tokens) {
    return false
  }
  await run(userId, 'master', (s) => s.put(master, 'master'))
  for (const token of tokens) {
    await run(userId, 'tokens', (s) => s.put(token))
  }
  return true
}

async function syncVault(userId: number, master: Uint8Array<ArrayBuffer>) {
  const vault = await remoteVault()
  const remote = (vault && (await openVault(master, vault))) ?? []
  for (const token of remote) {
    await run(userId, 'tokens', (s) => s.put(token))
  }
  await api('PUT', '/api/vault', await sealVault(master, await localTokens(userId)))
}

async function sha256Base64(bytes: Uint8Array<ArrayBuffer>): Promise<string> {
  return toBase64(new Uint8Array(await crypto.subtle.digest('SHA-256', bytes)))
}

async function publishedKeys(): Promise<Map<number, PublishedKey>> {
  const body = new Uint8Array(
    await (await fetch('/anon/keys.json', { credentials: 'omit' })).arrayBuffer(),
  )
  if (!import.meta.env.DEV && !(pinnedKeys as string[]).includes(await sha256Base64(body))) {
    throw new UnknownKeyError('keys.json does not match a pinned hash')
  }
  const keys: PublishedKey[] = JSON.parse(new TextDecoder().decode(body))
  for (const key of keys) {
    if ((await sha256Base64(fromBase64(key.publicKey))) !== key.fingerprint) {
      throw new UnknownKeyError(`fingerprint mismatch for offering ${key.offeringId}`)
    }
  }
  return new Map(keys.map((k) => [k.offeringId, k]))
}

function importKey(key: PublishedKey) {
  return crypto.subtle.importKey(
    'spki',
    fromBase64(key.publicKey),
    { name: 'RSA-PSS', hash: 'SHA-384' },
    true,
    ['verify'],
  )
}

export async function issueTokens(userId: number): Promise<RateableOffering[]> {
  const master = await localMaster(userId)
  if (!master) {
    throw new Error('no master secret')
  }
  const offerings = await api<RateableOffering[]>('GET', '/api/tokens/offerings')
  const tokens = new Set((await localTokens(userId)).map((t) => t.offeringId))
  const known = new Set(offerings.map((o) => o.offeringId))
  let blindings: Blinding[] = await run(userId, 'blindings', (s) => s.getAll())
  for (const stale of blindings.filter(
    (b) => tokens.has(b.offeringId) || !known.has(b.offeringId),
  )) {
    await run(userId, 'blindings', (s) => s.delete(stale.offeringId))
  }
  blindings = blindings.filter((b) => !tokens.has(b.offeringId) && known.has(b.offeringId))
  const pending = offerings.filter(
    (o) =>
      o.open && o.fingerprint && !o.issued && !blindings.some((b) => b.offeringId === o.offeringId),
  )
  if (!pending.length && !blindings.length) {
    return offerings
  }
  const keys = await publishedKeys()
  for (const offering of pending) {
    const key = keys.get(offering.offeringId)
    if (!key || key.fingerprint !== offering.fingerprint) {
      throw new UnknownKeyError(`unknown key for offering ${offering.offeringId}`)
    }
    const blinded = await blind(
      await importKey(key),
      await tokenMessage(master, offering.offeringId),
    )
    const blinding = {
      offeringId: offering.offeringId,
      fingerprint: key.fingerprint,
      blindedMsg: toBase64(blinded.blindedMsg),
      inv: toBase64(blinded.inv),
    }
    await run(userId, 'blindings', (s) => s.put(blinding))
    blindings.push(blinding)
  }
  const issued = await api<{ offeringId: number; blindSig: string }[]>(
    'POST',
    '/api/tokens/issue',
    {
      tokens: blindings.map((b) => ({ offeringId: b.offeringId, blindedMsg: b.blindedMsg })),
    },
  )
  for (const { offeringId, blindSig } of issued) {
    const blinding = blindings.find((b) => b.offeringId === offeringId)!
    const key = keys.get(offeringId)
    if (!key || key.fingerprint !== blinding.fingerprint) {
      throw new UnknownKeyError(`unknown key for offering ${offeringId}`)
    }
    const msg = await tokenMessage(master, offeringId)
    const sig = await finalize(
      await importKey(key),
      msg,
      fromBase64(blindSig),
      fromBase64(blinding.inv),
    ).catch(() => {
      throw new UnknownKeyError(`signature for offering ${offeringId} does not verify`)
    })
    await run(userId, 'tokens', (s) =>
      s.put({ offeringId, fingerprint: key.fingerprint, sig: toBase64(sig) }),
    )
    await run(userId, 'blindings', (s) => s.delete(offeringId))
  }
  await syncVault(userId, master)
  return offerings.map((o) =>
    issued.some((t) => t.offeringId === o.offeringId) ? { ...o, issued: true } : o,
  )
}
