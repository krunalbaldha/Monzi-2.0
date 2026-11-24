import { NextRequest, NextResponse } from 'next/server';
import { supabase, isSupabaseConfigured } from '@/lib/supabase';

export async function GET() {
  if (!isSupabaseConfigured) {
    return NextResponse.json({ accounts: [], source: 'supabase_not_configured' });
  }

  try {
    const { data, error } = await supabase
      .from('accounts')
      .select('*')
      .order('current_balance', { ascending: false });

    if (error) throw error;
    return NextResponse.json({ accounts: data || [], source: 'supabase' });
  } catch (err: any) {
    console.error('Accounts query error:', err);
    return NextResponse.json(
      { accounts: [], source: 'supabase_error', error: err.message },
      { status: 500 }
    );
  }
}

export async function POST(req: NextRequest) {
  if (!isSupabaseConfigured) {
    return NextResponse.json({ error: 'Supabase not configured' }, { status: 503 });
  }

  try {
    const body = await req.json();
    const { bank_name, account_number_mask, account_type, initial_balance } = body;

    if (!bank_name || !account_number_mask) {
      return NextResponse.json(
        { error: 'Bank name and account number mask are required' },
        { status: 400 }
      );
    }

    const initBal = parseFloat(initial_balance) || 0;
    const cleanMask = account_number_mask.toUpperCase().startsWith('XX')
      ? account_number_mask.toUpperCase()
      : `XX${account_number_mask.toUpperCase()}`;

    const { data, error } = await supabase
      .from('accounts')
      .upsert(
        {
          bank_name: bank_name.trim(),
          account_number_mask: cleanMask,
          account_type: account_type || 'SAVINGS',
          initial_balance: initBal,
          current_balance: initBal,
          updated_at: new Date().toISOString(),
        },
        { onConflict: 'bank_name, account_number_mask' }
      )
      .select()
      .single();

    if (error) throw error;

    return NextResponse.json({ success: true, account: data });
  } catch (err: any) {
    console.error('Account creation error:', err);
    return NextResponse.json({ error: err.message }, { status: 500 });
  }
}

export async function PATCH(req: NextRequest) {
  if (!isSupabaseConfigured) {
    return NextResponse.json({ error: 'Supabase not configured' }, { status: 503 });
  }

  try {
    const body = await req.json();
    const { id, current_balance, bank_name, account_number_mask, account_type } = body;

    if (!id) {
      return NextResponse.json({ error: 'Missing account id' }, { status: 400 });
    }

    const updates: Record<string, any> = {
      updated_at: new Date().toISOString(),
    };
    if (current_balance !== undefined) updates.current_balance = parseFloat(current_balance) || 0;
    if (bank_name !== undefined) updates.bank_name = bank_name.trim();
    if (account_number_mask !== undefined) updates.account_number_mask = account_number_mask;
    if (account_type !== undefined) updates.account_type = account_type;

    const { data, error } = await supabase
      .from('accounts')
      .update(updates)
      .eq('id', id)
      .select()
      .single();

    if (error) throw error;

    return NextResponse.json({ success: true, account: data });
  } catch (err: any) {
    console.error('Account update error:', err);
    return NextResponse.json({ error: err.message }, { status: 500 });
  }
}

export async function DELETE(req: NextRequest) {
  if (!isSupabaseConfigured) {
    return NextResponse.json({ error: 'Supabase not configured' }, { status: 503 });
  }

  const { searchParams } = new URL(req.url);
  const id = searchParams.get('id');

  if (!id) {
    return NextResponse.json({ error: 'Missing account id' }, { status: 400 });
  }

  try {
    const { error } = await supabase.from('accounts').delete().eq('id', id);
    if (error) throw error;

    return NextResponse.json({ success: true });
  } catch (err: any) {
    console.error('Account delete error:', err);
    return NextResponse.json({ error: err.message }, { status: 500 });
  }
}
