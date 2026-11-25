import { NextRequest, NextResponse } from 'next/server';
import { cookies } from 'next/headers';
import {
  getAuthenticatedUser,
  SESSION_LOCKED_COOKIE,
} from '@/lib/supabase/server';

function computeInitials(fullName: string, email: string): string {
  const source = (fullName || email.split('@')[0] || 'M').trim();
  const parts = source.split(/\s+/).filter(Boolean);
  if (parts.length >= 2) {
    return `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase();
  }
  return source.slice(0, 2).toUpperCase();
}

export async function GET() {
  try {
    const auth = await getAuthenticatedUser();
    if (!auth) {
      return NextResponse.json({
        authenticated: false,
        isLocked: false,
        user: null,
        profile: null,
      });
    }

    const { supabase, user } = auth;
    const cookieStore = await cookies();
    const isLocked = cookieStore.get(SESSION_LOCKED_COOKIE)?.value === '1';

    let dbProfile: {
      full_name?: string | null;
      email?: string | null;
      currency?: string | null;
      sms_permission?: string | null;
      biometric_enabled?: boolean | null;
      device_sync_token?: string | null;
    } | null = null;

    try {
      const { data } = await supabase
        .from('user_profiles')
        .select('full_name, email, currency, sms_permission, biometric_enabled, device_sync_token')
        .eq('id', user.id)
        .maybeSingle();
      dbProfile = data;
    } catch {
      // Fallback cleanly to user_metadata if user_profiles table is not yet migrated
    }

    const meta = user.user_metadata || {};
    const fullName =
      dbProfile?.full_name ||
      meta.full_name ||
      meta.name ||
      user.email?.split('@')[0] ||
      'Monzi User';
    const email = dbProfile?.email || user.email || '';
    const hasCredsInMeta =
      Array.isArray(meta.biometric_credentials) && meta.biometric_credentials.length > 0;
    const biometricEnabled = Boolean(
      dbProfile?.biometric_enabled ?? (meta.biometric_enabled || hasCredsInMeta)
    );

    const smsPermRaw = dbProfile?.sms_permission || meta.sms_permission || 'prompt';
    const smsPermission = (
      ['prompt', 'granted', 'denied', 'unsupported'].includes(smsPermRaw)
        ? smsPermRaw
        : 'prompt'
    ) as 'prompt' | 'granted' | 'denied' | 'unsupported';

    const profile = {
      id: user.id,
      email,
      full_name: fullName,
      initials: computeInitials(fullName, email),
      currency: dbProfile?.currency || meta.currency || 'INR',
      sms_permission: smsPermission,
      biometric_enabled: biometricEnabled,
      device_sync_token: dbProfile?.device_sync_token || meta.device_sync_token || undefined,
    };

    return NextResponse.json({
      authenticated: true,
      isLocked,
      user,
      profile,
    });
  } catch (err: any) {
    console.error('Session GET error:', err);
    return NextResponse.json(
      {
        authenticated: false,
        isLocked: false,
        user: null,
        profile: null,
      },
      { status: 500 }
    );
  }
}

export async function POST(req: NextRequest) {
  const auth = await getAuthenticatedUser();
  if (!auth) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
  }

  try {
    const body = await req.json();
    const locked = Boolean(body?.locked);
    const response = NextResponse.json({ success: true, isLocked: locked });

    if (locked) {
      response.cookies.set(SESSION_LOCKED_COOKIE, '1', {
        httpOnly: true,
        sameSite: 'lax',
        secure: process.env.NODE_ENV === 'production',
        path: '/',
        maxAge: 60 * 60 * 24 * 30,
      });
    } else {
      response.cookies.set(SESSION_LOCKED_COOKIE, '', {
        httpOnly: true,
        sameSite: 'lax',
        path: '/',
        maxAge: 0,
      });
    }

    return response;
  } catch (err: any) {
    return NextResponse.json({ error: err?.message || 'Invalid request' }, { status: 400 });
  }
}
