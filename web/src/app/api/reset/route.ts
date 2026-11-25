import { NextRequest, NextResponse } from 'next/server';
import { supabase, isSupabaseConfigured } from '@/lib/supabase';

export async function POST(req: NextRequest) {
  // Auth check
  const authHeader = req.headers.get('x-monzi-secret');
  const configuredSecret = process.env.MONZI_SYNC_SECRET || '';
  if (!authHeader || authHeader !== configuredSecret) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
  }

  if (!isSupabaseConfigured) {
    return NextResponse.json({ error: 'Supabase not configured' }, { status: 503 });
  }

  // Delete all transactions first (FK constraint), then accounts
  const { error: txErr } = await supabase
    .from('transactions')
    .delete()
    .neq('id', '00000000-0000-0000-0000-000000000000');
  if (txErr) return NextResponse.json({ error: txErr.message }, { status: 500 });

  const { error: accErr } = await supabase
    .from('accounts')
    .delete()
    .neq('id', '00000000-0000-0000-0000-000000000000');
  if (accErr) return NextResponse.json({ error: accErr.message }, { status: 500 });

  return NextResponse.json({
    success: true,
    message: 'All transactions and accounts cleared. Categories preserved.',
  });
}
