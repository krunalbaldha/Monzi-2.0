import { NextRequest, NextResponse } from 'next/server';
import { getAuthenticatedUser } from '@/lib/supabase/server';
import { SESSION_LOCKED_COOKIE_NAME } from '@/lib/webauthn';

export async function POST(req: NextRequest) {
  const auth = await getAuthenticatedUser();
  if (!auth) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
  }

  const { supabase, user } = auth;
  if (!user.email) {
    return NextResponse.json(
      { error: 'Account has no email associated for password unlock' },
      { status: 400 }
    );
  }

  let body: { password?: string };
  try {
    body = await req.json();
  } catch {
    return NextResponse.json({ error: 'Invalid JSON payload' }, { status: 400 });
  }

  const password = typeof body.password === 'string' ? body.password : '';
  if (!password) {
    return NextResponse.json({ error: 'Password is required' }, { status: 400 });
  }

  const { data, error } = await supabase.auth.signInWithPassword({
    email: user.email,
    password,
  });

  if (error || !data.user) {
    return NextResponse.json(
      { error: 'Incorrect password. Please try again.' },
      { status: 401 }
    );
  }

  const res = NextResponse.json({ success: true });
  res.cookies.set(SESSION_LOCKED_COOKIE_NAME, '', {
    httpOnly: false,
    sameSite: 'lax',
    path: '/',
    maxAge: 0,
    secure: process.env.NODE_ENV === 'production',
  });

  return res;
}
