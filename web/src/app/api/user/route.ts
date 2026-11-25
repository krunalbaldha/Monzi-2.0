import { NextRequest, NextResponse } from 'next/server';
import crypto from 'crypto';
import { getAuthenticatedUser } from '@/lib/supabase/server';
import {
  isSupabaseConfigured,
  isSchemaMigrationError,
  getErrorMessage,
} from '@/lib/supabase';

function computeInitials(fullName: string, email: string): string {
  const parts = fullName.trim().split(/\s+/).filter(Boolean);
  if (parts.length >= 2) {
    return `${parts[0][0]}${parts[1][0]}`.toUpperCase();
  }
  if (parts.length === 1 && parts[0].length > 0) {
    return parts[0].slice(0, 2).toUpperCase();
  }
  if (email) {
    return email.slice(0, 2).toUpperCase();
  }
  return 'U';
}

function buildProfilePayload(user: { id: string; email?: string; user_metadata?: Record<string, unknown> }, overrides?: Record<string, unknown>) {
  const meta = { ...(user.user_metadata || {}), ...(overrides || {}) };
  const email = user.email || '';
  const fullName = String(
    meta.full_name || meta.name || (email ? email.split('@')[0] : 'User')
  ).trim();

  return {
    id: user.id,
    email,
    name: fullName,
    full_name: fullName,
    initials: computeInitials(fullName, email),
    currency: String(meta.currency || 'INR'),
    sms_permission: String(meta.sms_permission || 'prompt'),
    biometric_enabled: Boolean(meta.biometric_enabled),
    device_sync_token: meta.device_sync_token ? String(meta.device_sync_token) : undefined,
  };
}

async function mirrorProfileToTable(
  supabase: Awaited<ReturnType<typeof getAuthenticatedUser>> extends infer T
    ? T extends { supabase: infer S }
      ? S
      : never
    : never,
  profile: ReturnType<typeof buildProfilePayload>
) {
  try {
    const { error } = await supabase.from('user_profiles').upsert(
      {
        id: profile.id,
        full_name: profile.full_name,
        email: profile.email,
        currency: profile.currency,
        sms_permission: profile.sms_permission,
        biometric_enabled: profile.biometric_enabled,
        device_sync_token: profile.device_sync_token || null,
        updated_at: new Date().toISOString(),
      },
      { onConflict: 'id' }
    );
    if (error && !isSchemaMigrationError(error)) {
      console.warn('user_profiles mirror warning:', error.message);
    }
  } catch {
    // Best-effort mirror; ignore if migration hasn't run yet
  }
}

export async function GET() {
  if (!isSupabaseConfigured) {
    return NextResponse.json({ error: 'Supabase not configured' }, { status: 503 });
  }

  const auth = await getAuthenticatedUser();
  if (!auth) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
  }

  try {
    let deviceSyncToken = auth.user.user_metadata?.device_sync_token as string | undefined;

    // Auto-generate persistent per-user device_sync_token if missing
    if (!deviceSyncToken) {
      deviceSyncToken = crypto.randomBytes(24).toString('hex');
      const { error: updateErr } = await auth.supabase.auth.updateUser({
        data: {
          ...auth.user.user_metadata,
          device_sync_token: deviceSyncToken,
        },
      });
      if (updateErr) {
        console.warn('Failed to persist auto-generated device_sync_token:', updateErr.message);
      }
    }

    const profile = buildProfilePayload(auth.user, { device_sync_token: deviceSyncToken });
    await mirrorProfileToTable(auth.supabase, profile);

    return NextResponse.json({ profile, source: 'supabase' });
  } catch (err: unknown) {
    console.error('User profile fetch error:', err);
    return NextResponse.json({ error: getErrorMessage(err) }, { status: 500 });
  }
}

export async function POST(req: NextRequest) {
  if (!isSupabaseConfigured) {
    return NextResponse.json({ error: 'Supabase not configured' }, { status: 503 });
  }

  const auth = await getAuthenticatedUser();
  if (!auth) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
  }

  try {
    const body = await req.json();
    const { name, full_name, currency, sms_permission, regenerate_sync_token } = body;

    const existingMeta = auth.user.user_metadata || {};
    const nextMeta: Record<string, unknown> = { ...existingMeta };

    const resolvedName = full_name ?? name;
    if (resolvedName !== undefined) {
      const trimmed = String(resolvedName).trim();
      if (!trimmed) {
        return NextResponse.json({ error: 'Name cannot be empty' }, { status: 400 });
      }
      nextMeta.full_name = trimmed;
      nextMeta.name = trimmed;
    }

    if (currency !== undefined) {
      nextMeta.currency = String(currency).trim() || 'INR';
    }

    if (sms_permission !== undefined) {
      nextMeta.sms_permission = String(sms_permission);
    }

    if (!nextMeta.device_sync_token || regenerate_sync_token === true) {
      nextMeta.device_sync_token = crypto.randomBytes(24).toString('hex');
    }

    const { data: updatedUserData, error: updateErr } = await auth.supabase.auth.updateUser({
      data: nextMeta,
    });

    if (updateErr) throw updateErr;

    const userObj = updatedUserData.user || auth.user;
    const profile = buildProfilePayload(userObj, nextMeta);
    await mirrorProfileToTable(auth.supabase, profile);

    return NextResponse.json({ success: true, profile });
  } catch (err: unknown) {
    console.error('User profile update error:', err);
    return NextResponse.json({ error: getErrorMessage(err) }, { status: 500 });
  }
}

export async function PATCH(req: NextRequest) {
  return POST(req);
}
