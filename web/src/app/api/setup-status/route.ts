import { NextRequest, NextResponse } from 'next/server';
import { getAuthenticatedUser } from '@/lib/supabase/server';
import {
  isSupabaseConfigured,
  isSchemaMigrationError,
  getErrorMessage,
} from '@/lib/supabase';
import type { SetupStatusPayload } from '@/lib/types';

async function buildSetupStatus(
  auth: NonNullable<Awaited<ReturnType<typeof getAuthenticatedUser>>>
): Promise<{ payload: SetupStatusPayload; schemaMigrationRequired: boolean }> {
  const user = auth.user;
  const meta = user.user_metadata || {};

  let smsPermission = String(meta.sms_permission || 'prompt');
  let hasSyncToken = Boolean(meta.device_sync_token);

  // Check user_profiles as fallback/authoritative DB record
  try {
    const { data: profile } = await auth.supabase
      .from('user_profiles')
      .select('sms_permission, device_sync_token')
      .eq('id', user.id)
      .maybeSingle();

    if (profile) {
      if (profile.sms_permission && smsPermission === 'prompt') {
        smsPermission = String(profile.sms_permission);
      }
      if (profile.device_sync_token) {
        hasSyncToken = true;
      }
    }
  } catch {
    // ignore if user_profiles table is missing
  }

  let schemaMigrationRequired = false;
  let deviceCount = 0;

  const { count, error: devErr } = await auth.supabase
    .from('devices')
    .select('id', { count: 'exact', head: true })
    .eq('user_id', user.id);

  if (devErr) {
    if (isSchemaMigrationError(devErr)) {
      schemaMigrationRequired = true;
    }
  } else {
    deviceCount = count || 0;
  }

  let historicalSyncStatus: SetupStatusPayload['historical_sync_status'] = 'PENDING';
  let liveMonitoringActive = false;
  let lastProcessedAt: string | null = null;

  const { data: syncStates, error: syncStateErr } = await auth.supabase
    .from('sms_sync_state')
    .select('historical_scan_status, monitoring_status, last_processed_at, updated_at')
    .eq('user_id', user.id)
    .order('updated_at', { ascending: false });

  if (syncStateErr) {
    if (isSchemaMigrationError(syncStateErr)) {
      schemaMigrationRequired = true;
    }
  } else if (syncStates && syncStates.length > 0) {
    for (const s of syncStates) {
      if (s.historical_scan_status === 'COMPLETED') {
        historicalSyncStatus = 'COMPLETED';
      } else if (
        s.historical_scan_status === 'IN_PROGRESS' &&
        historicalSyncStatus !== 'COMPLETED'
      ) {
        historicalSyncStatus = 'IN_PROGRESS';
      }
      if (s.monitoring_status === 'ACTIVE') {
        liveMonitoringActive = true;
      }
      if (s.last_processed_at && (!lastProcessedAt || s.last_processed_at > lastProcessedAt)) {
        lastProcessedAt = s.last_processed_at;
      }
    }
  }

  // Latest transaction timestamp as last_sync_at
  let lastSyncAt: string | null = null;
  const { data: latestTx } = await auth.supabase
    .from('transactions')
    .select('created_at')
    .eq('user_id', user.id)
    .order('created_at', { ascending: false })
    .limit(1)
    .maybeSingle();

  if (latestTx?.created_at) {
    lastSyncAt = latestTx.created_at;
  } else if (lastProcessedAt) {
    lastSyncAt = lastProcessedAt;
  }

  // Fallback for lastProcessedAt from sms_processing_logs if sms_sync_state had none
  if (!lastProcessedAt) {
    try {
      const { data: latestLog } = await auth.supabase
        .from('sms_processing_logs')
        .select('created_at')
        .eq('user_id', user.id)
        .order('created_at', { ascending: false })
        .limit(1)
        .maybeSingle();
      if (latestLog?.created_at) {
        lastProcessedAt = latestLog.created_at;
      }
    } catch {
      // ignore if table not present
    }
  }

  const emailVerified = Boolean(
    user.email_confirmed_at ||
      (user as { confirmed_at?: string | null }).confirmed_at
  );

  const payload: SetupStatusPayload = {
    account_created: true,
    email_verified: emailVerified,
    device_registered: deviceCount > 0,
    sms_access_granted: smsPermission === 'granted',
    historical_sync_status: historicalSyncStatus,
    live_monitoring_active: liveMonitoringActive,
    cloud_sync_active: Boolean(
      isSupabaseConfigured && (hasSyncToken || lastSyncAt !== null || deviceCount > 0)
    ),
    last_processed_at: lastProcessedAt,
    last_sync_at: lastSyncAt,
    device_count: deviceCount,
  };

  return { payload, schemaMigrationRequired };
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
    const { payload, schemaMigrationRequired } = await buildSetupStatus(auth);
    return NextResponse.json({
      ...payload,
      status: payload,
      schema_migration_required: schemaMigrationRequired || undefined,
      source: 'supabase',
    });
  } catch (err: unknown) {
    console.error('Setup status fetch error:', err);
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
    const deviceId = String(body.device_id || 'web-browser').trim().slice(0, 120);
    const historicalScanStatus = body.historical_scan_status
      ? String(body.historical_scan_status).trim().toUpperCase()
      : undefined;
    const monitoringStatus = body.monitoring_status
      ? String(body.monitoring_status).trim().toUpperCase()
      : undefined;
    const lastProcessedAt = body.last_processed_at
      ? new Date(body.last_processed_at).toISOString()
      : new Date().toISOString();
    const lastProcessedMessage =
      body.last_processed_message !== undefined
        ? String(body.last_processed_message)
        : undefined;

    const upsertRow: Record<string, unknown> = {
      user_id: auth.user.id,
      device_id: deviceId,
      last_processed_at: lastProcessedAt,
      updated_at: new Date().toISOString(),
    };
    if (historicalScanStatus) upsertRow.historical_scan_status = historicalScanStatus;
    if (monitoringStatus) upsertRow.monitoring_status = monitoringStatus;
    if (lastProcessedMessage !== undefined) {
      upsertRow.last_processed_message = lastProcessedMessage;
    }

    const { error: upsertErr } = await auth.supabase
      .from('sms_sync_state')
      .upsert(upsertRow, { onConflict: 'user_id,device_id' });

    if (upsertErr && isSchemaMigrationError(upsertErr)) {
      return NextResponse.json(
        {
          error:
            'Database migration 003_frd_complete_schema.sql has not been applied yet. Please run it in Supabase SQL Editor.',
          schema_migration_required: true,
        },
        { status: 503 }
      );
    }
    if (upsertErr) throw upsertErr;

    // Best-effort automation audit log
    try {
      await auth.supabase.from('automation_logs').insert({
        user_id: auth.user.id,
        device_id: deviceId,
        process_type: body.process_type || 'SYNC_STATE_UPDATE',
        process_status: body.process_status || 'SUCCESS',
        error_code: body.error_code || null,
        details:
          body.details ||
          `Updated sync state (historical=${historicalScanStatus || 'unchanged'}, monitoring=${monitoringStatus || 'unchanged'})`,
      });
    } catch {
      // ignore log error
    }

    const { payload } = await buildSetupStatus(auth);
    return NextResponse.json({
      success: true,
      ...payload,
      status: payload,
    });
  } catch (err: unknown) {
    console.error('Setup status update error:', err);
    return NextResponse.json({ error: getErrorMessage(err) }, { status: 500 });
  }
}
