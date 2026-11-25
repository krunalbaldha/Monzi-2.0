import { NextResponse } from 'next/server';
import { supabase, isSupabaseConfigured, getErrorMessage } from '@/lib/supabase';

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
    // Read-only connectivity check against non-sensitive categories metadata (never leak user financial data)
    const { error } = await supabase
      .from('categories')
      .select('id', { count: 'exact', head: true });

    const latencyMs = Date.now() - start;

    if (error) {
      return NextResponse.json(
        {
          status: 'error',
          configured: true,
          latencyMs,
          error: error.message,
          timestamp: new Date().toISOString(),
        },
        { status: 500 }
      );
    }

    return NextResponse.json({
      status: 'connected',
      configured: true,
      latencyMs,
      timestamp: new Date().toISOString(),
    });
  } catch (err: unknown) {
    return NextResponse.json(
      {
        status: 'error',
        configured: true,
        latencyMs: Date.now() - start,
        error: getErrorMessage(err),
        timestamp: new Date().toISOString(),
      },
      { status: 500 }
    );
  }
}
