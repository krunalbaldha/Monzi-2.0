import { NextRequest, NextResponse } from 'next/server';
import { supabase, isSupabaseConfigured } from '@/lib/supabase';
import crypto from 'crypto';

export async function GET(req: NextRequest) {
  if (!isSupabaseConfigured) {
    return NextResponse.json({ transactions: [], source: 'supabase_not_configured' });
  }

  const { searchParams } = new URL(req.url);
  const type = searchParams.get('type');
  const accountId = searchParams.get('account_id');
  const categoryId = searchParams.get('category_id');
  const search = searchParams.get('search');
  const limitParam = searchParams.get('limit');
  const limit = limitParam ? parseInt(limitParam, 10) : 200;

  try {
    let query = supabase
      .from('transactions')
      .select('*, accounts(*), categories(*)')
      .order('transaction_date', { ascending: false })
      .limit(limit);

    if (type && type !== 'ALL') {
      query = query.eq('transaction_type', type);
    }
    if (accountId && accountId !== 'ALL') {
      query = query.eq('account_id', accountId);
    }
    if (categoryId && categoryId !== 'ALL') {
      query = query.eq('category_id', categoryId);
    }
    if (search && search.trim()) {
      const q = `%${search.trim()}%`;
      query = query.or(`merchant.ilike.${q},reference_id.ilike.${q},notes.ilike.${q}`);
    }

    const { data, error } = await query;
    if (error) throw error;

    return NextResponse.json({ transactions: data || [], source: 'supabase' });
  } catch (err: any) {
    console.error('Transactions query error:', err);
    return NextResponse.json(
      { transactions: [], source: 'supabase_error', error: err.message },
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
    const {
      amount,
      direction,
      transaction_type,
      merchant,
      account_id,
      category_id,
      payment_mode,
      reference_id,
      notes,
      transaction_date,
    } = body;

    const numAmount = parseFloat(amount);
    if (!numAmount || isNaN(numAmount) || numAmount <= 0) {
      return NextResponse.json({ error: 'Invalid amount' }, { status: 400 });
    }

    const txDate = transaction_date ? new Date(transaction_date).toISOString() : new Date().toISOString();
    const ref = reference_id || `MANUAL_${Date.now()}`;
    const fingerprint = crypto
      .createHash('sha256')
      .update(`${txDate}_${numAmount}_${direction}_${ref}_${Math.random()}`)
      .digest('hex');

    // 1. Insert transaction
    const { data: newTx, error: txError } = await supabase
      .from('transactions')
      .insert({
        amount: numAmount,
        direction: direction || 'DEBIT',
        transaction_type: transaction_type || (direction === 'CREDIT' ? 'INCOME' : 'EXPENSE'),
        merchant: merchant || (direction === 'CREDIT' ? 'Manual Credit' : 'Manual Expense'),
        account_id: account_id || null,
        category_id: category_id || null,
        payment_mode: payment_mode || 'MANUAL',
        reference_id: ref,
        sms_fingerprint: fingerprint,
        notes: notes || null,
        transaction_date: txDate,
      })
      .select('*, accounts(*), categories(*)')
      .single();

    if (txError) throw txError;

    // 2. If account_id is present, update account current balance
    if (account_id) {
      const { data: acc } = await supabase
        .from('accounts')
        .select('current_balance')
        .eq('id', account_id)
        .single();

      if (acc) {
        const cur = Number(acc.current_balance) || 0;
        const newBal = direction === 'CREDIT' ? cur + numAmount : cur - numAmount;
        await supabase
          .from('accounts')
          .update({ current_balance: newBal, updated_at: new Date().toISOString() })
          .eq('id', account_id);
      }
    }

    return NextResponse.json({ success: true, transaction: newTx });
  } catch (err: any) {
    console.error('Transaction creation error:', err);
    return NextResponse.json({ error: err.message }, { status: 500 });
  }
}

export async function PATCH(req: NextRequest) {
  if (!isSupabaseConfigured) {
    return NextResponse.json({ error: 'Supabase not configured' }, { status: 503 });
  }

  try {
    const body = await req.json();
    const { id, category_id, notes, merchant, transaction_type } = body;

    if (!id) {
      return NextResponse.json({ error: 'Missing transaction id' }, { status: 400 });
    }

    const updates: Record<string, any> = {};
    if (category_id !== undefined) updates.category_id = category_id || null;
    if (notes !== undefined) updates.notes = notes;
    if (merchant !== undefined) updates.merchant = merchant;
    if (transaction_type !== undefined) updates.transaction_type = transaction_type;

    const { data, error } = await supabase
      .from('transactions')
      .update(updates)
      .eq('id', id)
      .select('*, accounts(*), categories(*)')
      .single();

    if (error) throw error;

    return NextResponse.json({ success: true, transaction: data });
  } catch (err: any) {
    console.error('Transaction update error:', err);
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
    return NextResponse.json({ error: 'Missing transaction id' }, { status: 400 });
  }

  try {
    // 1. Fetch transaction first to see if balance needs reverting
    const { data: tx } = await supabase
      .from('transactions')
      .select('amount, direction, account_id')
      .eq('id', id)
      .single();

    if (tx && tx.account_id) {
      const { data: acc } = await supabase
        .from('accounts')
        .select('current_balance')
        .eq('id', tx.account_id)
        .single();

      if (acc) {
        const cur = Number(acc.current_balance) || 0;
        const amt = Number(tx.amount) || 0;
        // Revert: if it was DEBIT, add back; if CREDIT, subtract
        const revertedBal = tx.direction === 'DEBIT' ? cur + amt : cur - amt;
        await supabase
          .from('accounts')
          .update({ current_balance: revertedBal, updated_at: new Date().toISOString() })
          .eq('id', tx.account_id);
      }
    }

    const { error } = await supabase
      .from('transactions')
      .delete()
      .eq('id', id);

    if (error) throw error;

    return NextResponse.json({ success: true });
  } catch (err: any) {
    console.error('Transaction deletion error:', err);
    return NextResponse.json({ error: err.message }, { status: 500 });
  }
}
