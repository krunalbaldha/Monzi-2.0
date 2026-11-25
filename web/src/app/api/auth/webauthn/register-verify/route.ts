import crypto from 'crypto';
import { NextRequest, NextResponse } from 'next/server';
import { getAuthenticatedUser } from '@/lib/supabase/server';
import {
  CHALLENGE_COOKIE_NAME,
  type ChallengeCookiePayload,
  type StoredBiometricCredential,
  base64UrlDecode,
  base64UrlEncode,
  extractSpkiFromAttestationObject,
  getRpIdFromRequest,
  verifyChallengePayload,
  verifyClientDataJSON,
} from '@/lib/webauthn';

export async function POST(req: NextRequest) {
  const auth = await getAuthenticatedUser();
  if (!auth) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
  }

  const { supabase, user } = auth;
  const challengeToken = req.cookies.get(CHALLENGE_COOKIE_NAME)?.value;
  const challengePayload = challengeToken
    ? verifyChallengePayload<ChallengeCookiePayload>(challengeToken)
    : null;

  if (!challengePayload || challengePayload.userId !== user.id) {
    return NextResponse.json(
      { error: 'Biometric registration challenge expired or invalid' },
      { status: 400 }
    );
  }

  let body: Record<string, any>;
  try {
    body = await req.json();
  } catch {
    return NextResponse.json({ error: 'Invalid JSON payload' }, { status: 400 });
  }

  const rawCredentialId: string | undefined =
    body.credentialId || body.id || body.rawId;
  const clientDataJSON: string | undefined =
    body.clientDataJSON || body.response?.clientDataJSON;
  const attestationObject: string | undefined =
    body.attestationObject || body.response?.attestationObject;
  let publicKeySpki: string | undefined =
    body.publicKeySpki || body.publicKey || body.response?.publicKey;
  let algorithm: number = Number(
    body.algorithm ?? body.publicKeyAlgorithm ?? body.response?.publicKeyAlgorithm ?? -7
  );
  const deviceName =
    typeof body.deviceName === 'string' && body.deviceName.trim()
      ? body.deviceName.trim().slice(0, 120)
      : 'This Device';

  if (!rawCredentialId || !clientDataJSON) {
    return NextResponse.json(
      { error: 'Missing credentialId or clientDataJSON' },
      { status: 400 }
    );
  }

  const { expectedOrigins } = getRpIdFromRequest(req);
  const validClientData = verifyClientDataJSON(
    clientDataJSON,
    challengePayload.challenge,
    'webauthn.create',
    expectedOrigins
  );

  if (!validClientData) {
    return NextResponse.json(
      { error: 'Invalid WebAuthn clientDataJSON' },
      { status: 400 }
    );
  }

  if (!publicKeySpki && attestationObject) {
    const extracted = extractSpkiFromAttestationObject(attestationObject);
    if (extracted) {
      publicKeySpki = extracted.publicKeySpki;
      algorithm = extracted.algorithm;
    }
  }

  if (!publicKeySpki) {
    return NextResponse.json(
      { error: 'Missing public key SPKI from authenticator response' },
      { status: 400 }
    );
  }

  // Validate SPKI public key format via Node crypto
  let normalizedSpkiBase64: string;
  try {
    const spkiBuf = base64UrlDecode(publicKeySpki);
    crypto.createPublicKey({ key: spkiBuf, format: 'der', type: 'spki' });
    normalizedSpkiBase64 = spkiBuf.toString('base64');
  } catch {
    return NextResponse.json(
      { error: 'Invalid SPKI public key format' },
      { status: 400 }
    );
  }

  const normalizedCredId = base64UrlEncode(base64UrlDecode(rawCredentialId));
  const resolvedAlgorithm = Number.isFinite(algorithm) ? algorithm : -7;

  const existingRaw = user.user_metadata?.biometric_credentials;
  const existingCreds: StoredBiometricCredential[] = Array.isArray(existingRaw)
    ? existingRaw
    : [];

  const newCredential: StoredBiometricCredential = {
    credentialId: normalizedCredId,
    publicKeySpki: normalizedSpkiBase64,
    algorithm: resolvedAlgorithm,
    deviceName,
    counter: 0,
    createdAt: new Date().toISOString(),
  };

  const dedupedCreds = [
    ...existingCreds.filter((c) => c.credentialId !== normalizedCredId),
    newCredential,
  ];

  const { error: updateError } = await supabase.auth.updateUser({
    data: {
      biometric_credentials: dedupedCreds,
      biometric_enabled: true,
    },
  });

  if (updateError) {
    return NextResponse.json(
      { error: updateError.message || 'Failed to save biometric credential' },
      { status: 500 }
    );
  }

  // Best-effort sync to relational tables (ignored if migration hasn't run yet)
  try {
    await supabase.from('webauthn_credentials').upsert(
      {
        user_id: user.id,
        credential_id: normalizedCredId,
        public_key_spki: normalizedSpkiBase64,
        algorithm: resolvedAlgorithm,
        device_name: deviceName,
        counter: 0,
      },
      { onConflict: 'credential_id' }
    );
  } catch {
    // Table may not exist yet before SQL migration
  }

  try {
    await supabase
      .from('user_profiles')
      .update({
        biometric_enabled: true,
        updated_at: new Date().toISOString(),
      })
      .eq('id', user.id);
  } catch {
    // Table may not exist yet before SQL migration
  }

  const res = NextResponse.json({
    success: true,
    credentialId: normalizedCredId,
  });

  res.cookies.set(CHALLENGE_COOKIE_NAME, '', {
    httpOnly: true,
    sameSite: 'lax',
    path: '/',
    maxAge: 0,
    secure: process.env.NODE_ENV === 'production',
  });

  return res;
}
