import { NextResponse } from 'next/server';
import { supabase, isSupabaseConfigured } from '@/lib/supabase';
import { mockAccounts } from '@/lib/mockData';

export async function GET() {
  if (!isSupabaseConfigured) {
    return NextResponse.json({ accounts: mockAccounts, source: 'mock' });
  }

  try {
    const { data, error } = await supabase
      .from('accounts')
      .select('*')
      .order('current_balance', { ascending: false });

    if (error) throw error;
    return NextResponse.json({ accounts: data || [], source: 'supabase' });
  } catch {
    return NextResponse.json({ accounts: mockAccounts, source: 'fallback' });
  }
}
