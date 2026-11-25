import crypto from 'crypto';
import type { NextRequest } from 'next/server';

export const CHALLENGE_COOKIE_NAME = 'monzi_webauthn_challenge';
export const SESSION_LOCKED_COOKIE_NAME = 'monzi_session_locked';

export interface StoredBiometricCredential {
  credentialId: string; // base64url
  publicKeySpki: string; // base64 DER SubjectPublicKeyInfo
  algorithm: number; // -7 (ES256) or -257 (RS256)
  deviceName: string;
  counter: number;
  createdAt: string;
}

export interface ChallengeCookiePayload {
  challenge: string;
  userId: string;
  exp: number;
}

export function base64UrlEncode(buf: Buffer | Uint8Array): string {
  return Buffer.from(buf)
    .toString('base64')
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
}

export function base64UrlDecode(str: string): Buffer {
  const normalized = str.replace(/-/g, '+').replace(/_/g, '/');
  const padLen = (4 - (normalized.length % 4)) % 4;
  return Buffer.from(normalized + '='.repeat(padLen), 'base64');
}

function getHmacSecret(): string {
  return (
    process.env.MONZI_SYNC_SECRET ||
    process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY ||
    'monzi-dev-webauthn-secret'
  );
}

export function signChallengePayload(payload: object): string {
  const data = base64UrlEncode(Buffer.from(JSON.stringify(payload), 'utf8'));
  const sig = base64UrlEncode(
    crypto.createHmac('sha256', getHmacSecret()).update(data).digest()
  );
  return `${data}.${sig}`;
}

export function verifyChallengePayload<T>(token: string): T | null {
  if (!token || typeof token !== 'string') return null;
  const parts = token.split('.');
  if (parts.length !== 2) return null;

  const [data, providedSig] = parts;
  if (!data || !providedSig) return null;

  const expectedSig = base64UrlEncode(
    crypto.createHmac('sha256', getHmacSecret()).update(data).digest()
  );

  const providedBuf = Buffer.from(providedSig, 'utf8');
  const expectedBuf = Buffer.from(expectedSig, 'utf8');
  if (providedBuf.length !== expectedBuf.length) return null;
  if (!crypto.timingSafeEqual(providedBuf, expectedBuf)) return null;

  try {
    const parsed = JSON.parse(base64UrlDecode(data).toString('utf8')) as T & {
      exp?: number;
    };
    if (typeof parsed.exp === 'number' && Date.now() > parsed.exp) {
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

export function getRpIdFromRequest(req: NextRequest): {
  rpId: string;
  expectedOrigins: string[];
} {
  const forwardedHost = req.headers.get('x-forwarded-host')?.split(',')[0]?.trim();
  const hostHeader = req.headers.get('host')?.split(',')[0]?.trim();
  const rawHost = forwardedHost || hostHeader || req.nextUrl.host || 'localhost:3000';

  const rpId = rawHost.split(':')[0].toLowerCase();
  const forwardedProto = req.headers
    .get('x-forwarded-proto')
    ?.split(',')[0]
    ?.trim()
    ?.toLowerCase();

  const isLocal =
    rpId === 'localhost' ||
    rpId === '127.0.0.1' ||
    rpId.endsWith('.local');

  const origins = new Set<string>();
  const primaryProto = forwardedProto || (isLocal ? 'http' : 'https');
  origins.add(`${primaryProto}://${rawHost}`);

  if (isLocal) {
    origins.add(`http://${rawHost}`);
    origins.add(`https://${rawHost}`);
    origins.add(`http://${rpId}`);
    origins.add(`https://${rpId}`);
  } else {
    origins.add(`https://${rawHost}`);
    origins.add(`https://${rpId}`);
  }

  return {
    rpId,
    expectedOrigins: Array.from(origins),
  };
}

export function verifyClientDataJSON(
  clientDataB64: string,
  expectedChallenge: string,
  expectedType: 'webauthn.create' | 'webauthn.get',
  expectedOrigins: string[]
): boolean {
  try {
    const clientDataStr = base64UrlDecode(clientDataB64).toString('utf8');
    const parsed = JSON.parse(clientDataStr) as {
      type?: string;
      challenge?: string;
      origin?: string;
      crossOrigin?: boolean;
    };

    if (parsed.type !== expectedType) return false;
    if (!parsed.challenge) return false;

    const normalizedReceived = base64UrlEncode(base64UrlDecode(parsed.challenge));
    const normalizedExpected = base64UrlEncode(base64UrlDecode(expectedChallenge));
    if (normalizedReceived !== normalizedExpected) return false;

    if (!parsed.origin || !expectedOrigins.includes(parsed.origin)) {
      return false;
    }

    if (parsed.crossOrigin === true) return false;

    return true;
  } catch {
    return false;
  }
}

export interface VerifyAssertionParams {
  authenticatorDataB64: string;
  clientDataJSONB64: string;
  signatureB64: string;
  publicKeySpkiB64: string;
  algorithm: number;
  expectedRpId: string;
}

export function parseAuthenticatorDataCounter(authenticatorDataB64: string): number {
  try {
    const authData = base64UrlDecode(authenticatorDataB64);
    if (authData.length < 37) return 0;
    return authData.readUInt32BE(33);
  } catch {
    return 0;
  }
}

export function verifyAuthenticatorAssertion({
  authenticatorDataB64,
  clientDataJSONB64,
  signatureB64,
  publicKeySpkiB64,
  algorithm,
  expectedRpId,
}: VerifyAssertionParams): boolean {
  try {
    const authDataBuf = base64UrlDecode(authenticatorDataB64);
    if (authDataBuf.length < 37) return false;

    const rpIdHash = authDataBuf.subarray(0, 32);
    const expectedRpIdHash = crypto
      .createHash('sha256')
      .update(expectedRpId, 'utf8')
      .digest();

    if (!crypto.timingSafeEqual(rpIdHash, expectedRpIdHash)) {
      return false;
    }

    const flags = authDataBuf[32];
    const userPresent = (flags & 0x01) !== 0;
    const userVerified = (flags & 0x04) !== 0;
    if (!userPresent || !userVerified) {
      return false;
    }

    const clientDataBuf = base64UrlDecode(clientDataJSONB64);
    const clientDataHash = crypto
      .createHash('sha256')
      .update(clientDataBuf)
      .digest();

    const signedData = Buffer.concat([authDataBuf, clientDataHash]);
    const signatureBuf = base64UrlDecode(signatureB64);
    const spkiDer = base64UrlDecode(publicKeySpkiB64);

    const key = crypto.createPublicKey({
      key: spkiDer,
      format: 'der',
      type: 'spki',
    });

    return crypto.verify(
      'sha256',
      signedData,
      {
        key,
        dsaEncoding: algorithm === -7 ? 'der' : undefined,
      },
      signatureBuf
    );
  } catch {
    return false;
  }
}

/* ─── Minimal CBOR / COSE Fallback Extractor (for older WebViews without getPublicKey()) ─── */

interface CborDecodeResult {
  value: unknown;
  nextOffset: number;
}

function readCborLength(buf: Buffer, offset: number, additionalInfo: number): { length: number; nextOffset: number } {
  if (additionalInfo < 24) {
    return { length: additionalInfo, nextOffset: offset };
  }
  if (additionalInfo === 24) {
    return { length: buf.readUInt8(offset), nextOffset: offset + 1 };
  }
  if (additionalInfo === 25) {
    return { length: buf.readUInt16BE(offset), nextOffset: offset + 2 };
  }
  if (additionalInfo === 26) {
    return { length: buf.readUInt32BE(offset), nextOffset: offset + 4 };
  }
  throw new Error(`Unsupported CBOR length info: ${additionalInfo}`);
}

function decodeCborItem(buf: Buffer, offset: number): CborDecodeResult {
  const initialByte = buf.readUInt8(offset);
  const majorType = initialByte >> 5;
  const additionalInfo = initialByte & 0x1f;
  let cursor = offset + 1;

  if (majorType === 0) {
    const { length, nextOffset } = readCborLength(buf, cursor, additionalInfo);
    return { value: length, nextOffset };
  }
  if (majorType === 1) {
    const { length, nextOffset } = readCborLength(buf, cursor, additionalInfo);
    return { value: -1 - length, nextOffset };
  }
  if (majorType === 2) {
    const { length, nextOffset } = readCborLength(buf, cursor, additionalInfo);
    cursor = nextOffset;
    return { value: buf.subarray(cursor, cursor + length), nextOffset: cursor + length };
  }
  if (majorType === 3) {
    const { length, nextOffset } = readCborLength(buf, cursor, additionalInfo);
    cursor = nextOffset;
    return { value: buf.subarray(cursor, cursor + length).toString('utf8'), nextOffset: cursor + length };
  }
  if (majorType === 4) {
    const { length, nextOffset } = readCborLength(buf, cursor, additionalInfo);
    cursor = nextOffset;
    const arr: unknown[] = [];
    for (let i = 0; i < length; i++) {
      const item = decodeCborItem(buf, cursor);
      arr.push(item.value);
      cursor = item.nextOffset;
    }
    return { value: arr, nextOffset: cursor };
  }
  if (majorType === 5) {
    const { length, nextOffset } = readCborLength(buf, cursor, additionalInfo);
    cursor = nextOffset;
    const map = new Map<unknown, unknown>();
    for (let i = 0; i < length; i++) {
      const k = decodeCborItem(buf, cursor);
      const v = decodeCborItem(buf, k.nextOffset);
      map.set(k.value, v.value);
      cursor = v.nextOffset;
    }
    return { value: map, nextOffset: cursor };
  }
  if (majorType === 7) {
    if (additionalInfo === 20) return { value: false, nextOffset: cursor };
    if (additionalInfo === 21) return { value: true, nextOffset: cursor };
    if (additionalInfo === 22) return { value: null, nextOffset: cursor };
  }

  throw new Error(`Unsupported CBOR major type: ${majorType}`);
}

function encodeDerLength(len: number): Buffer {
  if (len < 0x80) return Buffer.from([len]);
  if (len <= 0xff) return Buffer.from([0x81, len]);
  return Buffer.from([0x82, (len >> 8) & 0xff, len & 0xff]);
}

function wrapAsn1Sequence(children: Buffer[]): Buffer {
  const content = Buffer.concat(children);
  return Buffer.concat([Buffer.from([0x30]), encodeDerLength(content.length), content]);
}

function wrapAsn1BitString(bytes: Buffer): Buffer {
  const payload = Buffer.concat([Buffer.from([0x00]), bytes]);
  return Buffer.concat([Buffer.from([0x03]), encodeDerLength(payload.length), payload]);
}

function wrapAsn1Integer(bytes: Buffer): Buffer {
  const needsZeroPad = bytes[0] & 0x80;
  const payload = needsZeroPad ? Buffer.concat([Buffer.from([0x00]), bytes]) : bytes;
  return Buffer.concat([Buffer.from([0x02]), encodeDerLength(payload.length), payload]);
}

function coseKeyToSpki(coseMap: Map<unknown, unknown>): { publicKeySpki: string; algorithm: number } | null {
  const kty = coseMap.get(1);
  const alg = Number(coseMap.get(3));

  // EC2 P-256 (ES256, alg -7)
  if (kty === 2 && alg === -7) {
    const x = coseMap.get(-2);
    const y = coseMap.get(-3);
    if (!Buffer.isBuffer(x) || !Buffer.isBuffer(y) || x.length !== 32 || y.length !== 32) {
      return null;
    }
    // AlgorithmIdentifier: SEQUENCE { OID ecPublicKey (1.2.840.10045.2.1), OID prime256v1 (1.2.840.10045.3.1.7) }
    const algId = Buffer.from('301306072a8648ce3d020106082a8648ce3d030107', 'hex');
    const uncompressedPoint = Buffer.concat([Buffer.from([0x04]), x, y]);
    const spki = wrapAsn1Sequence([algId, wrapAsn1BitString(uncompressedPoint)]);
    return { publicKeySpki: spki.toString('base64'), algorithm: -7 };
  }

  // RSA (RS256, alg -257)
  if (kty === 3 && alg === -257) {
    const n = coseMap.get(-1);
    const e = coseMap.get(-2);
    if (!Buffer.isBuffer(n) || !Buffer.isBuffer(e)) {
      return null;
    }
    // AlgorithmIdentifier: SEQUENCE { OID rsaEncryption (1.2.840.113549.1.1.1), NULL }
    const algId = Buffer.from('300d06092a864886f70d0101010500', 'hex');
    const rsaPubKey = wrapAsn1Sequence([wrapAsn1Integer(n), wrapAsn1Integer(e)]);
    const spki = wrapAsn1Sequence([algId, wrapAsn1BitString(rsaPubKey)]);
    return { publicKeySpki: spki.toString('base64'), algorithm: -257 };
  }

  return null;
}

export function extractSpkiFromAttestationObject(
  attestationObjectB64: string
): { publicKeySpki: string; algorithm: number } | null {
  try {
    const attBuf = base64UrlDecode(attestationObjectB64);
    const decoded = decodeCborItem(attBuf, 0).value;
    if (!(decoded instanceof Map)) return null;

    const authData = decoded.get('authData');
    if (!Buffer.isBuffer(authData) || authData.length < 55) return null;

    const flags = authData[32];
    const hasAttestedCredentialData = (flags & 0x40) !== 0;
    if (!hasAttestedCredentialData) return null;

    // 32 (rpIdHash) + 1 (flags) + 4 (signCount) + 16 (aaguid) = 53
    const credIdLen = authData.readUInt16BE(53);
    const coseStart = 55 + credIdLen;
    if (coseStart >= authData.length) return null;

    const coseDecoded = decodeCborItem(authData, coseStart).value;
    if (!(coseDecoded instanceof Map)) return null;

    return coseKeyToSpki(coseDecoded);
  } catch {
    return null;
  }
}
