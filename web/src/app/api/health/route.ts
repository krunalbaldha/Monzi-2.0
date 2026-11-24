import { NextResponse } from 'next/server';
import { supabase, isSupabaseConfigured } from '@/lib/supabase';

export async function GET() {
  if (!isSupabaseConfigured) {
    return NextResponse.json({
      status: 'unconfigured',
      configured: false,
      message: 'Supabase URL or Anon key is missing in environment variables',
    });
  }

  const start = Date.now();
  try {
    const [accRes, catRes, txRes] = await Promise.all([
      supabase.from('accounts').select('id', { count: 'exact', head: true }),
      supabase.from('categories').select('id', { count: 'exact', head: true }),
      supabase.from('transactions').select('id', { count: 'exact', head: true }),
    ]);

    const latencyMs = Date.now() - start;

    return NextResponse.json({
      status: 'connected',
      configured: true,
      latencyMs,
      counts: {
        accounts: accRes.count ?? 0,
        categories: catRes.count ?? 0,
        transactions: txRes.count ?? 0,
      },
      supabaseUrl: process.env.NEXT_PUBLIC_SUPABASE_URL ? 'https://hgxrqmkxxylfjgoqjkzp.supabase.co' : '',
      timestamp: new Date().toISOString(),
    });
  } catch (err: any) {
    return NextResponse.json({
      status: 'error',
      configured: true,
      latencyMs: Date.now() - start,
      error: err.message,
    }, { status: 500 });
  }
}
