import crypto from 'crypto';
import { NextRequest, NextResponse } from 'next/server';
import { getAuthenticatedUser } from '@/lib/supabase/server';
import {
  CHALLENGE_COOKIE_NAME,
  base64UrlEncode,
  getRpIdFromRequest,
  signChallengePayload,
} from '@/lib/webauthn';

export async function POST(req: NextRequest) {
  const auth = await getAuthenticatedUser();
  if (!auth) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
  }

  const { user } = auth;
  const challenge = base64UrlEncode(crypto.randomBytes(32));
  const { rpId } = getRpIdFromRequest(req);

  const signedToken = signChallengePayload({
    challenge,
    userId: user.id,
    exp: Date.now() + 5 * 60 * 1000,
  });

  const displayName =
    (typeof user.user_metadata?.full_name === 'string' && user.user_metadata.full_name.trim()) ||
    user.email ||
    'Monzi User';

  const res = NextResponse.json({
    challenge,
    rp: {
      name: 'Monzi 2.0',
      id: rpId,
    },
    user: {
      id: base64UrlEncode(Buffer.from(user.id, 'utf8')),
      name: user.email || user.id,
      displayName,
    },
    pubKeyCredParams: [
      { type: 'public-key', alg: -7 },
      { type: 'public-key', alg: -257 },
    ],
    authenticatorSelection: {
      authenticatorAttachment: 'platform',
      userVerification: 'required',
      residentKey: 'preferred',
    },
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
