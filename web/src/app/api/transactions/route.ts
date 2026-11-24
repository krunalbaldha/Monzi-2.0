import { NextRequest, NextResponse } from 'next/server';
import { supabase, isSupabaseConfigured } from '@/lib/supabase';
import { mockTransactions } from '@/lib/mockData';

export async function GET(req: NextRequest) {
  const { searchParams } = new URL(req.url);
  const type = searchParams.get('type');
  const search = searchParams.get('search');

  if (!isSupabaseConfigured) {
    let list = [...mockTransactions];
    if (type && type !== 'ALL') {
      list = list.filter((t) => t.transaction_type === type);
    }
    if (search) {
      const q = search.toLowerCase();
      list = list.filter(
        (t) =>
          t.merchant?.toLowerCase().includes(q) ||
          t.accounts?.bank_name.toLowerCase().includes(q) ||
          t.reference_id?.toLowerCase().includes(q)
      );
    }
    return NextResponse.json({ transactions: list, source: 'mock' });
  }

  try {
    let query = supabase
      .from('transactions')
      .select('*, accounts(*), categories(*)')
      .order('transaction_date', { ascending: false });

    if (type && type !== 'ALL') {
      query = query.eq('transaction_type', type);
    }
    if (search) {
      query = query.ilike('merchant', `%${search}%`);
    }

    const { data, error } = await query;
    if (error) throw error;

    return NextResponse.json({ transactions: data || [], source: 'supabase' });
  } catch {
    return NextResponse.json({ transactions: mockTransactions, source: 'fallback' });
  }
}
