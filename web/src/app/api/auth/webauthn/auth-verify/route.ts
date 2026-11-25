import { NextRequest, NextResponse } from 'next/server';
import { getAuthenticatedUser } from '@/lib/supabase/server';
import {
  CHALLENGE_COOKIE_NAME,
  SESSION_LOCKED_COOKIE_NAME,
  type ChallengeCookiePayload,
  type StoredBiometricCredential,
  base64UrlDecode,
  base64UrlEncode,
  getRpIdFromRequest,
  parseAuthenticatorDataCounter,
  verifyAuthenticatorAssertion,
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
      { error: 'Biometric authentication challenge expired or invalid' },
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
  const authenticatorData: string | undefined =
    body.authenticatorData || body.response?.authenticatorData;
  const signature: string | undefined =
    body.signature || body.response?.signature;

  if (!rawCredentialId || !clientDataJSON || !authenticatorData || !signature) {
    return NextResponse.json(
      { error: 'Missing required WebAuthn assertion fields' },
      { status: 400 }
    );
  }

  const normalizedCredId = base64UrlEncode(base64UrlDecode(rawCredentialId));

  const rawMetaCreds = user.user_metadata?.biometric_credentials;
  const metaCreds: StoredBiometricCredential[] = Array.isArray(rawMetaCreds)
    ? rawMetaCreds
    : [];

  let matchedCred = metaCreds.find((c) => {
    try {
      return base64UrlEncode(base64UrlDecode(c.credentialId)) === normalizedCredId;
    } catch {
      return c.credentialId === normalizedCredId;
    }
  });

  if (!matchedCred) {
    try {
      const { data } = await supabase
        .from('webauthn_credentials')
        .select('credential_id, public_key_spki, algorithm, device_name, counter, created_at')
        .eq('user_id', user.id)
        .eq('credential_id', normalizedCredId)
        .single();

      if (data) {
        matchedCred = {
          credentialId: data.credential_id,
          publicKeySpki: data.public_key_spki,
          algorithm: data.algorithm,
          deviceName: data.device_name || 'Device',
          counter: data.counter || 0,
          createdAt: data.created_at || new Date().toISOString(),
        };
      }
    } catch {
      // Ignore if table doesn't exist yet
    }
  }

  if (!matchedCred) {
    return NextResponse.json(
      { error: 'Biometric credential not found for this user' },
      { status: 404 }
    );
  }

  const { rpId, expectedOrigins } = getRpIdFromRequest(req);

  const validClientData = verifyClientDataJSON(
    clientDataJSON,
    challengePayload.challenge,
    'webauthn.get',
    expectedOrigins
  );

  if (!validClientData) {
    return NextResponse.json(
      { error: 'Invalid WebAuthn clientDataJSON' },
      { status: 401 }
    );
  }

  const verified = verifyAuthenticatorAssertion({
    authenticatorDataB64: authenticatorData,
    clientDataJSONB64: clientDataJSON,
    signatureB64: signature,
    publicKeySpkiB64: matchedCred.publicKeySpki,
    algorithm: matchedCred.algorithm,
    expectedRpId: rpId,
  });

  if (!verified) {
    return NextResponse.json(
      { error: 'Biometric signature verification failed' },
      { status: 401 }
    );
  }

  // Best-effort counter & last_used_at update
  const newCounter = parseAuthenticatorDataCounter(authenticatorData);
  try {
    await supabase
      .from('webauthn_credentials')
      .update({
        last_used_at: new Date().toISOString(),
        ...(newCounter > 0 ? { counter: newCounter } : {}),
      })
      .eq('user_id', user.id)
      .eq('credential_id', normalizedCredId);
  } catch {
    // Ignore if table doesn't exist yet
  }

  const res = NextResponse.json({
    success: true,
    verified: true,
  });

  res.cookies.set(SESSION_LOCKED_COOKIE_NAME, '', {
    httpOnly: false,
    sameSite: 'lax',
    path: '/',
    maxAge: 0,
    secure: process.env.NODE_ENV === 'production',
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
