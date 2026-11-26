import { NextRequest, NextResponse } from 'next/server';
import { getAuthenticatedUser } from '@/lib/supabase/server';
import {
  isSupabaseConfigured,
  isSchemaMigrationError,
  getErrorMessage,
} from '@/lib/supabase';

const MIGRATION_003_MESSAGE =
  'Database migration 003_frd_complete_schema.sql has not been applied yet. Please run it in Supabase SQL Editor.';

export async function GET() {
  if (!isSupabaseConfigured) {
    return NextResponse.json({ error: 'Supabase not configured' }, { status: 503 });
  }

  const auth = await getAuthenticatedUser();
  if (!auth) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
  }

  try {
    const { data, error } = await auth.supabase
      .from('devices')
      .select('id, device_identifier, platform, app_version, last_seen_at, created_at')
      .eq('user_id', auth.user.id)
      .order('last_seen_at', { ascending: false });

    if (error) {
      if (isSchemaMigrationError(error)) {
        return NextResponse.json({
          devices: [],
          source: 'schema_migration_required',
          schema_migration_required: true,
          message: MIGRATION_003_MESSAGE,
        });
      }
      throw error;
    }

    return NextResponse.json({ devices: data || [], source: 'supabase' });
  } catch (err: unknown) {
    console.error('Devices query error:', err);
    return NextResponse.json(
      { devices: [], source: 'supabase_error', error: getErrorMessage(err) },
      { status: 500 }
    );
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
    const deviceIdentifier = String(body.device_identifier || '').trim().slice(0, 120);
    const platform = String(body.platform || 'web').trim().slice(0, 30);
    const appVersion = body.app_version ? String(body.app_version).trim().slice(0, 30) : null;

    if (!deviceIdentifier) {
      return NextResponse.json({ error: 'device_identifier is required' }, { status: 400 });
    }

    const nowIso = new Date().toISOString();

    const { data, error } = await auth.supabase
      .from('devices')
      .upsert(
        {
          user_id: auth.user.id,
          device_identifier: deviceIdentifier,
          platform,
          app_version: appVersion,
          last_seen_at: nowIso,
        },
        { onConflict: 'user_id,device_identifier' }
      )
      .select('id, device_identifier, platform, app_version, last_seen_at, created_at')
      .single();

    if (error) {
      if (isSchemaMigrationError(error)) {
        return NextResponse.json(
          { error: MIGRATION_003_MESSAGE, schema_migration_required: true },
          { status: 503 }
        );
      }
      throw error;
    }

    // Best-effort automation log
    try {
      await auth.supabase.from('automation_logs').insert({
        user_id: auth.user.id,
        device_id: deviceIdentifier,
        process_type: 'DEVICE_HEARTBEAT',
        process_status: 'SUCCESS',
        details: `Registered/heartbeat from ${platform}${appVersion ? ` v${appVersion}` : ''}`,
      });
    } catch {
      // ignore audit failure
    }

    return NextResponse.json({ success: true, device: data });
  } catch (err: unknown) {
    console.error('Device registration error:', err);
    return NextResponse.json({ error: getErrorMessage(err) }, { status: 500 });
  }
}

export async function DELETE(req: NextRequest) {
  if (!isSupabaseConfigured) {
    return NextResponse.json({ error: 'Supabase not configured' }, { status: 503 });
  }

  const auth = await getAuthenticatedUser();
  if (!auth) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
  }

  const { searchParams } = new URL(req.url);
  const id = searchParams.get('id');

  if (!id) {
    return NextResponse.json({ error: 'Missing device id' }, { status: 400 });
  }

  try {
    const { error } = await auth.supabase
      .from('devices')
      .delete()
      .eq('id', id)
      .eq('user_id', auth.user.id);

    if (error) {
      if (isSchemaMigrationError(error)) {
        return NextResponse.json(
          { error: MIGRATION_003_MESSAGE, schema_migration_required: true },
          { status: 503 }
        );
      }
      throw error;
    }

    return NextResponse.json({ success: true });
  } catch (err: unknown) {
    console.error('Device delete error:', err);
    return NextResponse.json({ error: getErrorMessage(err) }, { status: 500 });
  }
}
