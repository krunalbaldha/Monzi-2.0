import crypto from 'crypto';
import { NextRequest, NextResponse } from 'next/server';
import { getAuthenticatedUser } from '@/lib/supabase/server';
import {
  CHALLENGE_COOKIE_NAME,
  type StoredBiometricCredential,
  base64UrlEncode,
  getRpIdFromRequest,
  signChallengePayload,
} from '@/lib/webauthn';

export async function POST(req: NextRequest) {
  const auth = await getAuthenticatedUser();
  if (!auth) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
  }

  const { supabase, user } = auth;
  const rawMetaCreds = user.user_metadata?.biometric_credentials;
  let creds: StoredBiometricCredential[] = Array.isArray(rawMetaCreds)
    ? rawMetaCreds
    : [];

  // Fallback to webauthn_credentials table if metadata is empty
  if (creds.length === 0) {
    try {
      const { data } = await supabase
        .from('webauthn_credentials')
        .select('credential_id, public_key_spki, algorithm, device_name, counter, created_at')
        .eq('user_id', user.id);

      if (Array.isArray(data) && data.length > 0) {
        creds = data.map((row: any) => ({
          credentialId: row.credential_id,
          publicKeySpki: row.public_key_spki,
          algorithm: row.algorithm,
          deviceName: row.device_name || 'Device',
          counter: row.counter || 0,
          createdAt: row.created_at || new Date().toISOString(),
        }));
      }
    } catch {
      // Ignore if table not created yet
    }
  }

  if (creds.length === 0) {
    return NextResponse.json(
      { error: 'No biometric credentials enrolled for this account' },
      { status: 400 }
    );
  }

  const challenge = base64UrlEncode(crypto.randomBytes(32));
  const { rpId } = getRpIdFromRequest(req);

  const signedToken = signChallengePayload({
    challenge,
    userId: user.id,
    exp: Date.now() + 5 * 60 * 1000,
  });

  const res = NextResponse.json({
    challenge,
    rpId,
    allowCredentials: creds.map((c) => ({
      id: c.credentialId,
      type: 'public-key' as const,
    })),
    userVerification: 'required',
    timeout: 60000,
  });

  res.cookies.set(CHALLENGE_COOKIE_NAME, signedToken, {
    httpOnly: true,
    sameSite: 'lax',
    path: '/',
    maxAge: 300,
    secure: process.env.NODE_ENV === 'production',
  });

  return res;
}
