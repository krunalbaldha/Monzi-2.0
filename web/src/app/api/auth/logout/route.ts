import { NextResponse } from 'next/server';
import {
  createServerSupabaseClient,
  SESSION_LOCKED_COOKIE,
  WEBAUTHN_CHALLENGE_COOKIE,
} from '@/lib/supabase/server';

export async function POST() {
  try {
    const supabase = await createServerSupabaseClient();
    await supabase.auth.signOut();
  } catch (err) {
    console.error('Logout error:', err);
  }

  const response = NextResponse.json({ success: true });

  response.cookies.set(SESSION_LOCKED_COOKIE, '', {
    httpOnly: true,
    sameSite: 'lax',
    path: '/',
    maxAge: 0,
  });

  response.cookies.set(WEBAUTHN_CHALLENGE_COOKIE, '', {
    httpOnly: true,
    sameSite: 'lax',
    path: '/',
    maxAge: 0,
  });

  return response;
}
