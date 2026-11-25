import { NextRequest, NextResponse } from 'next/server';
import { getAuthenticatedUser } from '@/lib/supabase/server';

export async function GET(req: NextRequest) {
  try {
    const auth = await getAuthenticatedUser();
    if (!auth) {
      return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
    }

    const { supabase, user } = auth;
    const { searchParams } = new URL(req.url);
    const limitParam = parseInt(searchParams.get('limit') || '50', 10);
    const limit = Math.min(Math.max(isNaN(limitParam) ? 50 : limitParam, 1), 200);

    const { data, error } = await supabase
      .from('sms_processing_logs')
      .select(
        'id, device_id, sender, status, reason, extracted_type, extracted_amount, extracted_bank, extracted_date, reference_id, sms_fingerprint, created_at'
      )
      .eq('user_id', user.id)
      .order('created_at', { ascending: false })
      .limit(limit);

    if (error) {
      // Gracefully handle missing table before migration 002 is executed
      if (error.code === '42P01' || error.message?.includes('sms_processing_logs')) {
        return NextResponse.json({
          logs: [],
          migration_pending: true,
        });
      }
      return NextResponse.json({ error: error.message }, { status: 500 });
    }

    return NextResponse.json({
      logs: data || [],
      migration_pending: false,
    });
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : 'Internal Server Error';
    return NextResponse.json({ error: message }, { status: 500 });
  }
}
