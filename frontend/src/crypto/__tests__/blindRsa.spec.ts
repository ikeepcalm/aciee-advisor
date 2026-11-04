import { describe, expect, it } from 'vitest'
import { RSABSSA } from '@cloudflare/blindrsa-ts'
import { blind, finalize, tokenMessage } from '../blindRsa'

// RFC 9474 Appendix A.4, RSABSSA-SHA384-PSSZERO-Deterministic
const vector = {
  p: 'e1f4d7a34802e27c7392a3cea32a262a34dc3691bd87f3f310dc75673488930559c120fd0410194fb8a0da55bd0b81227e843fdca6692ae80e5a5d414116d4803fca7d8c30eaaae57e44a1816ebb5c5b0606c536246c7f11985d731684150b63c9a3ad9e41b04c0b5b27cb188a692c84696b742a80d3cd00ab891f2457443dadfeba6d6daf108602be26d7071803c67105a5426838e6889d77e8474b29244cefaf418e381b312048b457d73419213063c60ee7b0d81820165864fef93523c9635c22210956e53a8d96322493ffc58d845368e2416e078e5bcb5d2fd68ae6acfa54f9627c42e84a9d3f2774017e32ebca06308a12ecc290c7cd1156dcccfb2311',
  q: 'c601a9caea66dc3835827b539db9df6f6f5ae77244692780cd334a006ab353c806426b60718c05245650821d39445d3ab591ed10a7339f15d83fe13f6a3dfb20b9452c6a9b42eaa62a68c970df3cadb2139f804ad8223d56108dfde30ba7d367e9b0a7a80c4fdba2fd9dde6661fc73fc2947569d2029f2870fc02d8325acf28c9afa19ecf962daa7916e21afad09eb62fe9f1cf91b77dc879b7974b490d3ebd2e95426057f35d0a3c9f45f79ac727ab81a519a8b9285932d9b2e5ccd347e59f3f32ad9ca359115e7da008ab7406707bd0e8e185a5ed8758b5ba266e8828f8d863ae133846304a2936ad7bc7c9803879d2fc4a28e69291d73dbd799f8bc238385',
  e: '010001',
  d: '0d43242aefe1fb2c13fbc66e20b678c4336d20b1808c558b6e62ad16a287077180b177e1f01b12f9c6cd6c52630257ccef26a45135a990928773f3bd2fc01a313f1dac97a51cec71cb1fd7efc7adffdeb05f1fb04812c924ed7f4a8269925dad88bd7dcfbc4ef01020ebfc60cb3e04c54f981fdbd273e69a8a58b8ceb7c2d83fbcbd6f784d052201b88a9848186f2a45c0d2826870733e6fd9aa46983e0a6e82e35ca20a439c5ee7b502a9062e1066493bdadf8b49eb30d9558ed85abc7afb29b3c9bc644199654a4676681af4babcea4e6f71fe4565c9c1b85d9985b84ec1abf1a820a9bbebee0df1398aae2c85ab580a9f13e7743afd3108eb32100b870648fa6bc17e8abac4d3c99246b1f0ea9f7f93a5dd5458c56d9f3f81ff2216b3c3680a13591673c43194d8e6fc93fc1e37ce2986bd628ac48088bc723d8fbe293861ca7a9f4a73e9fa63b1b6d0074f5dea2a624c5249ff3ad811b6255b299d6bc5451ba7477f19c5a0db690c3e6476398b1483d10314afd38bbaf6e2fbdbcd62c3ca9797a420ca6034ec0a83360a3ee2adf4b9d4ba29731d131b099a38d6a23cc463db754603211260e99d19affc902c915d7854554aabf608e3ac52c19b8aa26ae042249b17b2d29669b5c859103ee53ef9bdc73ba3c6b537d5c34b6d8f034671d7f3a8a6966cc4543df223565343154140fd7391c7e7be03e241f4ecfeb877a051',
  prepared_msg:
    '8f3dc6fb8c4a02f4d6352edf0907822c1210a9b32f9bdda4c45a698c80023aa6b59f8cfec5fdbb36331372ebefedae7d',
  inv: '80682c48982407b489d53d1261b19ec8627d02b8cda5336750b8cee332ae260de57b02d72609c1e0e9f28e2040fc65b6f02d56dbd6aa9af8fde656f70495dfb723ba01173d4707a12fddac628ca29f3e32340bd8f7ddb557cf819f6b01e445ad96f874ba235584ee71f6581f62d4f43bf03f910f6510deb85e8ef06c7f09d9794a008be7ff2529f0ebb69decef646387dc767b74939265fec0223aa6d84d2a8a1cc912d5ca25b4e144ab8f6ba054b54910176d5737a2cff011da431bd5f2a0d2d66b9e70b39f4b050e45c0d9c16f02deda9ddf2d00f3e4b01037d7029cd49c2d46a8e1fc2c0c17520af1f4b5e25ba396afc4cd60c494a4c426448b35b49635b337cfb08e7c22a39b256dd032c00adddafb51a627f99a0e1704170ac1f1912e49d9db10ec04c19c58f420212973e0cb329524223a6aa56c7937c5dffdb5d966b6cd4cbc26f3201dd25c80960a1a111b32947bb78973d269fac7f5186530930ed19f68507540eed9e1bab8b00f00d8ca09b3f099aae46180e04e3584bd7ca054df18a1504b89d1d1675d0966c4ae1407be325cdf623cf13ff13e4a28b594d59e3eadbadf6136eee7a59d6a444c9eb4e2198e8a974f27a39eb63af2c9af3870488b8adaad444674f512133ad80b9220e09158521614f1faadfe8505ef57b7df6813048603f0dd04f4280177a11380fbfc861dbcbd7418d62155248dad5fdec0991f',
  blind_sig:
    '362ef369f9b8c1487e285514702a7cd6fe03e4a2fb854881f3d3f986b7742a0c9bfab6562a6cd5ed71c574af67d7e77e71b33420c08ebb0ff37886b858297f9562fc366066c6d8e77bad1918b04756ba03f5c385d44f06759daf1b7a38b2a64248dee95d0e3886c8afa1f74afd8ac3c56520d0f3fd206df8e0d257312756803b09a79d0cc38112592c3aec32de5a9bc3284c5a0a2d0808b102deafa5cc60f04e3d71c0284cba04f17f88aa8e07d5544fe0265807d515877f79d30ed26d522b9d9c56597647b0dbca5a69d6418f8d1b51481723f272c2a3d48f6f4fd6beeac3576c3edb00e8779964548aeab8e004c7c4f8ef9cb6e680e2d2d49792004bb3e6974fa48f241a361ca449c02bd4c0ad4e66252c55e656f16049908efe59acbafa1171895dfac64d909808e5420469d622c7253ec1de7522b41634d383bf8786bf881cbf1561627f1e62b2d93300ec30ec0f5f0ab32036fce068bc76b0b0c6452079537f8d7f8dcee4b42bbf2d9ad7499d3835cd93cfc7e8ebea3554ab5241e181e5d73241b7bebf0a281b63594a35f4993e2b416d60db966b58b648cfcba2c4bee4c2830aae4a70ff55012480298f549c13b1b2684277bca12f592471b8a99285174f1c0ebb38fc80e74a10b3f02ec3e6682ba873f7ff0e1e79718b470927c74ed754d4f7c3d9a55e22246e829cdb5a1c6fb2a0a6c896df303063c918bcf5eb0017',
  sig: '4454b6983ff01cb28545329f394936efa42ed231e15efbc025fdaca00277acf0c8e00e3d8b0ecebd35b057b8ebfc14e1a7097368a4abd20b555894ccef3d1b9528c6bcbda6b95376bef230d0f1feff0c1064c62c60a7ae7431d1fdfa43a81eed9235e363e1ffa0b2797aba6aad6082fcd285e14fc8b71de6b9c87cb4059c7dc1e96ae1e63795a1e9af86b9073d1d848aef3eca8a03421bcd116572456b53bcfd4dabb0a9691f1fabda3ed0ce357aee2cfee5b1a0eb226f69716d4e011d96eede5e38a9acb531a64336a0d5b0bae3ab085b658692579a376740ff6ce69e89b06f360520b864e33d82d029c808248a19e18e31f0ecd16fac5cd4870f8d3ebc1c32c718124152dc905672ab0b7af48bf7d1ac1ff7b9c742549c91275ab105458ae37621757add83482bbcf779e777bbd61126e93686635d4766aedf5103cf7978f3856ccac9e28d21a850dbb03c811128616d315d717be1c2b6254f8509acae862042c034530329ce15ca2e2f6b1f5fd59272746e3918c748c0eb810bf76884fa10fcf749326bbfaa5ba285a0186a22e4f628dbf178d3bb5dc7e165ca73f6a55ecc14c4f5a26c4693ce5da032264cbec319b12ddb9787d0efa4fcf1e5ccee35ad85ecd453182df9ed735893f830b570faae8be0f6fe2e571a4e0d927cba4debd368d3b4fca33ec6251897a137cf75474a32ac8256df5e5ffa518b88b43fb6f63a24',
}

const bytes = (hex: string) => Uint8Array.from(hex.match(/../g)!, (b) => parseInt(b, 16))
const hex = (b: Uint8Array) => Array.from(b, (x) => x.toString(16).padStart(2, '0')).join('')
const big = (h: string) => BigInt('0x' + h)
const b64url = (n: bigint) => {
  let h = n.toString(16)
  if (h.length % 2) h = '0' + h
  return btoa(String.fromCharCode(...bytes(h)))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '')
}

const p = big(vector.p)
const q = big(vector.q)
const d = big(vector.d)
const modInverse = (a: bigint, m: bigint) => {
  let [r0, r1, s0, s1] = [a % m, m, 1n, 0n]
  while (r1) {
    const t = r0 / r1
    ;[r0, r1, s0, s1] = [r1, r0 - t * r1, s1, s0 - t * s1]
  }
  return ((s0 % m) + m) % m
}
const publicJwk = { kty: 'RSA', n: b64url(p * q), e: b64url(big(vector.e)) }
const privateJwk = {
  ...publicJwk,
  d: b64url(d),
  p: b64url(p),
  q: b64url(q),
  dp: b64url(d % (p - 1n)),
  dq: b64url(d % (q - 1n)),
  qi: b64url(modInverse(q, p)),
}
const algorithm = { name: 'RSA-PSS', hash: 'SHA-384' }
const publicKey = () => crypto.subtle.importKey('jwk', publicJwk, algorithm, true, ['verify'])
const privateKey = () => crypto.subtle.importKey('jwk', privateJwk, algorithm, true, ['sign'])

describe('blind RSA', () => {
  it('finalizes the RFC 9474 blind signature into the expected signature', async () => {
    const sig = await finalize(
      await publicKey(),
      bytes(vector.prepared_msg),
      bytes(vector.blind_sig),
      bytes(vector.inv),
    )
    expect(hex(sig)).toBe(vector.sig)
  })

  it('round-trips a fresh token through blind, sign and finalize', async () => {
    const pk = await publicKey()
    const msg = await tokenMessage(crypto.getRandomValues(new Uint8Array(16)), 42)
    const { blindedMsg, inv } = await blind(pk, msg)
    const blindSig = await RSABSSA.SHA384.PSSZero.Deterministic().blindSign(
      await privateKey(),
      blindedMsg,
    )
    const sig = await finalize(pk, msg, blindSig, inv)
    expect(await crypto.subtle.verify({ name: 'RSA-PSS', saltLength: 0 }, pk, sig, msg)).toBe(true)
  })

  it('rejects a blind signature that does not match', async () => {
    const tampered = bytes(vector.blind_sig)
    tampered[tampered.length - 1]! ^= 1
    await expect(
      finalize(await publicKey(), bytes(vector.prepared_msg), tampered, bytes(vector.inv)),
    ).rejects.toThrow()
  })

  it('derives one stable message per offering', async () => {
    const master = crypto.getRandomValues(new Uint8Array(16))
    const first = await tokenMessage(master, 7)
    expect(first).toHaveLength(32)
    expect(await tokenMessage(master, 7)).toEqual(first)
    expect(await tokenMessage(master, 8)).not.toEqual(first)
  })
})
