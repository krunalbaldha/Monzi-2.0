import { NextResponse } from 'next/server';
import { getAuthenticatedUser } from '@/lib/supabase/server';
import { SESSION_LOCKED_COOKIE_NAME } from '@/lib/webauthn';

export async function POST() {
  const auth = await getAuthenticatedUser();
  if (!auth) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
  }

  const { supabase, user } = auth;

  const { error } = await supabase.auth.updateUser({
    data: {
      biometric_credentials: [],
      biometric_enabled: false,
    },
  });

  if (error) {
    return NextResponse.json(
      { error: error.message || 'Failed to disable biometrics' },
      { status: 500 }
    );
  }

  try {
    await supabase
      .from('webauthn_credentials')
      .delete()
      .eq('user_id', user.id);
  } catch {
    // Ignore if table doesn't exist yet
  }

  try {
    await supabase
      .from('user_profiles')
      .update({
        biometric_enabled: false,
        updated_at: new Date().toISOString(),
      })
      .eq('id', user.id);
  } catch {
    // Ignore if table doesn't exist yet
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
