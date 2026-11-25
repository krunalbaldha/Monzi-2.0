import { NextRequest, NextResponse } from 'next/server';
import crypto from 'crypto';
import { getAuthenticatedUser } from '@/lib/supabase/server';
import {
  isSupabaseConfigured,
  isUndefinedColumnError,
  SCHEMA_MIGRATION_MESSAGE,
  getErrorMessage,
} from '@/lib/supabase';

export async function GET(req: NextRequest) {
  if (!isSupabaseConfigured) {
    return NextResponse.json({ error: 'Supabase not configured' }, { status: 503 });
  }

  const auth = await getAuthenticatedUser();
  if (!auth) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
  }

  const { searchParams } = new URL(req.url);
  const type = searchParams.get('type');
  const accountId = searchParams.get('account_id');
  const categoryId = searchParams.get('category_id');
  const search = searchParams.get('search');
  const limitParam = searchParams.get('limit');
  const parsedLimit = limitParam ? parseInt(limitParam, 10) : 200;
  const limit = Number.isFinite(parsedLimit) && parsedLimit > 0 ? Math.min(parsedLimit, 5000) : 200;

  try {
    let query = auth.supabase
      .from('transactions')
      .select('*, accounts(*), categories(*)')
      .eq('user_id', auth.user.id)
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
    if (error) {
      if (isUndefinedColumnError(error)) {
        return NextResponse.json({
          transactions: [],
          source: 'schema_migration_required',
          schema_migration_required: true,
          message: SCHEMA_MIGRATION_MESSAGE,
        });
      }
      throw error;
    }

    return NextResponse.json({ transactions: data || [], source: 'supabase' });
  } catch (err: unknown) {
    console.error('Transactions query error:', err);
    return NextResponse.json(
      { transactions: [], source: 'supabase_error', error: getErrorMessage(err) },
      { status: 500 }
    );
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

    const txDirection = direction === 'CREDIT' ? 'CREDIT' : 'DEBIT';
    const txType =
      transaction_type || (txDirection === 'CREDIT' ? 'INCOME' : 'EXPENSE');
    const txDate = transaction_date
      ? new Date(transaction_date).toISOString()
      : new Date().toISOString();
    const ref = reference_id || `MANUAL_${Date.now()}`;
    const fingerprint = crypto
      .createHash('sha256')
      .update(`${auth.user.id}_${txDate}_${numAmount}_${txDirection}_${ref}_${crypto.randomUUID()}`)
      .digest('hex');

    // Verify account ownership if account_id is specified
    if (account_id) {
      const { data: ownedAcc, error: accCheckErr } = await auth.supabase
        .from('accounts')
        .select('id')
        .eq('id', account_id)
        .eq('user_id', auth.user.id)
        .single();

      if (accCheckErr && isUndefinedColumnError(accCheckErr)) {
        return NextResponse.json(
          { error: SCHEMA_MIGRATION_MESSAGE, schema_migration_required: true },
          { status: 503 }
        );
      }
      if (!ownedAcc) {
        return NextResponse.json({ error: 'Account not found' }, { status: 404 });
      }
    }

    const isExcluded = txType === 'TRANSFER' || txType === 'CC_BILL_PAYMENT';

    // 1. Insert transaction scoped to authenticated user
    const { data: newTx, error: txError } = await auth.supabase
      .from('transactions')
      .insert({
        user_id: auth.user.id,
        amount: numAmount,
        direction: txDirection,
        transaction_type: txType,
        merchant: merchant || (txDirection === 'CREDIT' ? 'Manual Credit' : 'Manual Expense'),
        account_id: account_id || null,
        category_id: category_id || null,
        payment_mode: payment_mode || 'MANUAL',
        reference_id: ref,
        sms_fingerprint: fingerprint,
        is_excluded_from_totals: isExcluded,
        notes: notes || null,
        transaction_date: txDate,
      })
      .select('*, accounts(*), categories(*)')
      .single();

    if (txError) {
      if (isUndefinedColumnError(txError)) {
        return NextResponse.json(
          { error: SCHEMA_MIGRATION_MESSAGE, schema_migration_required: true },
          { status: 503 }
        );
      }
      throw txError;
    }

    // 2. If account_id is present, update user's account current balance
    if (account_id) {
      const { data: acc } = await auth.supabase
        .from('accounts')
        .select('current_balance')
        .eq('id', account_id)
        .eq('user_id', auth.user.id)
        .single();

      if (acc) {
        const cur = Number(acc.current_balance) || 0;
        const newBal = txDirection === 'CREDIT' ? cur + numAmount : cur - numAmount;
        await auth.supabase
          .from('accounts')
          .update({ current_balance: newBal, updated_at: new Date().toISOString() })
          .eq('id', account_id)
          .eq('user_id', auth.user.id);
      }
    }

    return NextResponse.json({ success: true, transaction: newTx });
  } catch (err: unknown) {
    console.error('Transaction creation error:', err);
    return NextResponse.json({ error: getErrorMessage(err) }, { status: 500 });
  }
}

export async function PATCH(req: NextRequest) {
  if (!isSupabaseConfigured) {
    return NextResponse.json({ error: 'Supabase not configured' }, { status: 503 });
  }

  const auth = await getAuthenticatedUser();
  if (!auth) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
  }

  try {
    const body = await req.json();
    const { id, category_id, notes, merchant, transaction_type } = body;

    if (!id) {
      return NextResponse.json({ error: 'Missing transaction id' }, { status: 400 });
    }

    const updates: Record<string, unknown> = {};
    if (category_id !== undefined) updates.category_id = category_id || null;
    if (notes !== undefined) updates.notes = notes;
    if (merchant !== undefined) updates.merchant = merchant;
    if (transaction_type !== undefined) {
      updates.transaction_type = transaction_type;
      updates.is_excluded_from_totals =
        transaction_type === 'TRANSFER' || transaction_type === 'CC_BILL_PAYMENT';
    }

    const { data, error } = await auth.supabase
      .from('transactions')
      .update(updates)
      .eq('id', id)
      .eq('user_id', auth.user.id)
      .select('*, accounts(*), categories(*)')
      .single();

    if (error) {
      if (isUndefinedColumnError(error)) {
        return NextResponse.json(
          { error: SCHEMA_MIGRATION_MESSAGE, schema_migration_required: true },
          { status: 503 }
        );
      }
      throw error;
    }

    return NextResponse.json({ success: true, transaction: data });
  } catch (err: unknown) {
    console.error('Transaction update error:', err);
    return NextResponse.json({ error: getErrorMessage(err) }, { status: 500 });
  }
}

export async function DELETE(req: NextRequest) {
  if (!isSupabaseConfigured) {
    return NextResponse.json({ error: 'Supabase not configured' }, { status: 503 });
  }

  const auth = await getAuthenticatedUser();
  if (!auth) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
  }

  const { searchParams } = new URL(req.url);
  const id = searchParams.get('id');

  if (!id) {
    return NextResponse.json({ error: 'Missing transaction id' }, { status: 400 });
  }

  try {
    // 1. Fetch transaction scoped to user first to see if balance needs reverting
    const { data: tx, error: fetchErr } = await auth.supabase
      .from('transactions')
      .select('amount, direction, account_id')
      .eq('id', id)
      .eq('user_id', auth.user.id)
      .single();

    if (fetchErr && isUndefinedColumnError(fetchErr)) {
      return NextResponse.json(
        { error: SCHEMA_MIGRATION_MESSAGE, schema_migration_required: true },
        { status: 503 }
      );
    }

    if (tx && tx.account_id) {
      const { data: acc } = await auth.supabase
        .from('accounts')
        .select('current_balance')
        .eq('id', tx.account_id)
        .eq('user_id', auth.user.id)
        .single();

      if (acc) {
        const cur = Number(acc.current_balance) || 0;
        const amt = Number(tx.amount) || 0;
        const revertedBal = tx.direction === 'DEBIT' ? cur + amt : cur - amt;
        await auth.supabase
          .from('accounts')
          .update({ current_balance: revertedBal, updated_at: new Date().toISOString() })
          .eq('id', tx.account_id)
          .eq('user_id', auth.user.id);
      }
    }

    const { error } = await auth.supabase
      .from('transactions')
      .delete()
      .eq('id', id)
      .eq('user_id', auth.user.id);

    if (error) {
      if (isUndefinedColumnError(error)) {
        return NextResponse.json(
          { error: SCHEMA_MIGRATION_MESSAGE, schema_migration_required: true },
          { status: 503 }
        );
      }
      throw error;
    }

    return NextResponse.json({ success: true });
  } catch (err: unknown) {
    console.error('Transaction deletion error:', err);
    return NextResponse.json({ error: getErrorMessage(err) }, { status: 500 });
  }
}
