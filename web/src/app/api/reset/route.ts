import { NextResponse } from 'next/server';
import { getAuthenticatedUser } from '@/lib/supabase/server';
import {
  isSupabaseConfigured,
  isSchemaMigrationError,
  isUndefinedColumnError,
  SCHEMA_MIGRATION_MESSAGE,
  getErrorMessage,
} from '@/lib/supabase';

export async function POST() {
  if (!isSupabaseConfigured) {
    return NextResponse.json({ error: 'Supabase not configured' }, { status: 503 });
  }

  const auth = await getAuthenticatedUser();
  if (!auth) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
  }

  try {
    // 1. Delete ONLY the authenticated user's transactions first (FK constraint)
    const { error: txErr } = await auth.supabase
      .from('transactions')
      .delete()
      .eq('user_id', auth.user.id);

    if (txErr) {
      if (isUndefinedColumnError(txErr)) {
        return NextResponse.json(
          { error: SCHEMA_MIGRATION_MESSAGE, schema_migration_required: true },
          { status: 503 }
        );
      }
      throw txErr;
    }

    // 2. Delete ONLY the authenticated user's accounts
    const { error: accErr } = await auth.supabase
      .from('accounts')
      .delete()
      .eq('user_id', auth.user.id);

    if (accErr) {
      if (isUndefinedColumnError(accErr)) {
        return NextResponse.json(
          { error: SCHEMA_MIGRATION_MESSAGE, schema_migration_required: true },
          { status: 503 }
        );
      }
      throw accErr;
    }

    // 3. Best-effort clear of ONLY the authenticated user's SMS processing logs
    const { error: logsErr } = await auth.supabase
      .from('sms_processing_logs')
      .delete()
      .eq('user_id', auth.user.id);

    if (logsErr && !isSchemaMigrationError(logsErr)) {
      console.warn('Reset sms_processing_logs warning:', logsErr.message);
    }

    return NextResponse.json({
      success: true,
      message:
        'Your transactions, bank accounts, and SMS logs have been cleared. Categories and account login are preserved.',
    });
  } catch (err: unknown) {
    console.error('Reset user data error:', err);
    return NextResponse.json({ error: getErrorMessage(err) }, { status: 500 });
  }
}
