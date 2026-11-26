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
  const direction = searchParams.get('direction');
  const accountId = searchParams.get('account_id');
  const categoryId = searchParams.get('category_id');
  const from = searchParams.get('from');
  const to = searchParams.get('to');
  const sort = searchParams.get('sort') || 'date_desc';
  const search = searchParams.get('search');
  const limitParam = searchParams.get('limit');
  const parsedLimit = limitParam ? parseInt(limitParam, 10) : 200;
  const limit = Number.isFinite(parsedLimit) && parsedLimit > 0 ? Math.min(parsedLimit, 5000) : 200;

  try {
    let query = auth.supabase
      .from('transactions')
      .select('*, accounts(*), categories(*)')
      .eq('user_id', auth.user.id);

    if (type && type !== 'ALL') {
      query = query.eq('transaction_type', type);
    }
    if (direction && (direction === 'CREDIT' || direction === 'DEBIT')) {
      query = query.eq('direction', direction);
    }
    if (accountId && accountId !== 'ALL') {
      query = query.eq('account_id', accountId);
    }
    if (categoryId && categoryId !== 'ALL') {
      query = query.eq('category_id', categoryId);
    }
    if (from) {
      const fromIso = from.includes('T') ? from : new Date(`${from}T00:00:00+05:30`).toISOString();
      query = query.gte('transaction_date', fromIso);
    }
    if (to) {
      const toIso = to.includes('T') ? to : new Date(`${to}T23:59:59.999+05:30`).toISOString();
      query = query.lte('transaction_date', toIso);
    }
    if (search && search.trim()) {
      const q = `%${search.trim()}%`;
      query = query.or(`merchant.ilike.${q},reference_id.ilike.${q},notes.ilike.${q}`);
    }

    if (sort === 'date_asc') {
      query = query.order('transaction_date', { ascending: true });
    } else if (sort === 'amount_desc') {
      query = query.order('amount', { ascending: false });
    } else if (sort === 'amount_asc') {
      query = query.order('amount', { ascending: true });
    } else {
      query = query.order('transaction_date', { ascending: false });
    }
    query = query.limit(limit);

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

    // 2. If account_id is present, update user's account current balance respecting Credit Card polarity
    if (account_id) {
      const { data: acc } = await auth.supabase
        .from('accounts')
        .select('current_balance, account_type')
        .eq('id', account_id)
        .eq('user_id', auth.user.id)
        .single();

      if (acc) {
        const cur = Number(acc.current_balance) || 0;
        const newBal =
          acc.account_type === 'CREDIT_CARD'
            ? txDirection === 'DEBIT'
              ? cur + numAmount
              : Math.max(0, cur - numAmount)
            : txDirection === 'CREDIT'
            ? cur + numAmount
            : cur - numAmount;
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
    const {
      id,
      category_id,
      notes,
      merchant,
      transaction_type,
      amount,
      direction,
      account_id,
      transaction_date,
    } = body;

    if (!id) {
      return NextResponse.json({ error: 'Missing transaction id' }, { status: 400 });
    }

    const { data: existingTx } = await auth.supabase
      .from('transactions')
      .select('amount, direction, account_id')
      .eq('id', id)
      .eq('user_id', auth.user.id)
      .single();

    const updates: Record<string, unknown> = { updated_at: new Date().toISOString() };
    if (category_id !== undefined) updates.category_id = category_id || null;
    if (notes !== undefined) updates.notes = notes;
    if (merchant !== undefined) updates.merchant = merchant;
    if (transaction_date !== undefined) {
      const parsedDate = new Date(transaction_date);
      if (!isNaN(parsedDate.getTime())) updates.transaction_date = parsedDate.toISOString();
    }
    if (transaction_type !== undefined) {
      updates.transaction_type = transaction_type;
      updates.is_excluded_from_totals =
        transaction_type === 'TRANSFER' || transaction_type === 'CC_BILL_PAYMENT';
      updates.is_self_transfer = transaction_type === 'TRANSFER';
      updates.is_refund = transaction_type === 'REFUND' || transaction_type === 'REVERSAL';
    }
    const nextAmount = amount !== undefined ? Number(amount) : undefined;
    if (nextAmount !== undefined) {
      if (!nextAmount || isNaN(nextAmount) || nextAmount <= 0) {
        return NextResponse.json({ error: 'Invalid amount' }, { status: 400 });
      }
      updates.amount = nextAmount;
    }
    const nextDirection =
      direction === 'CREDIT' || direction === 'DEBIT' ? direction : undefined;
    if (nextDirection !== undefined) updates.direction = nextDirection;
    if (account_id !== undefined) updates.account_id = account_id || null;

    if (existingTx) {
      const oldAmt = Number(existingTx.amount) || 0;
      const oldDir = existingTx.direction as string;
      const oldAccId = existingTx.account_id as string | null;
      const finalAmt = nextAmount ?? oldAmt;
      const finalDir = nextDirection ?? oldDir;
      const finalAccId = account_id !== undefined ? (account_id || null) : oldAccId;

      if (finalAmt !== oldAmt || finalDir !== oldDir || finalAccId !== oldAccId) {
        if (oldAccId) {
          const { data: oldAcc } = await auth.supabase
            .from('accounts')
            .select('current_balance, account_type')
            .eq('id', oldAccId)
            .eq('user_id', auth.user.id)
            .single();
          if (oldAcc) {
            const cur = Number(oldAcc.current_balance) || 0;
            const reverted =
              oldAcc.account_type === 'CREDIT_CARD'
                ? oldDir === 'DEBIT'
                  ? Math.max(0, cur - oldAmt)
                  : cur + oldAmt
                : oldDir === 'DEBIT'
                ? cur + oldAmt
                : cur - oldAmt;
            await auth.supabase
              .from('accounts')
              .update({ current_balance: reverted, updated_at: new Date().toISOString() })
              .eq('id', oldAccId)
              .eq('user_id', auth.user.id);
          }
        }
        if (finalAccId) {
          const { data: newAcc } = await auth.supabase
            .from('accounts')
            .select('current_balance, account_type')
            .eq('id', finalAccId)
            .eq('user_id', auth.user.id)
            .single();
          if (newAcc) {
            const cur = Number(newAcc.current_balance) || 0;
            const applied =
              newAcc.account_type === 'CREDIT_CARD'
                ? finalDir === 'DEBIT'
                  ? cur + finalAmt
                  : Math.max(0, cur - finalAmt)
                : finalDir === 'CREDIT'
                ? cur + finalAmt
                : cur - finalAmt;
            await auth.supabase
              .from('accounts')
              .update({ current_balance: applied, updated_at: new Date().toISOString() })
              .eq('id', finalAccId)
              .eq('user_id', auth.user.id);
          }
        }
      }
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
        .select('current_balance, account_type')
        .eq('id', tx.account_id)
        .eq('user_id', auth.user.id)
        .single();

      if (acc) {
        const cur = Number(acc.current_balance) || 0;
        const amt = Number(tx.amount) || 0;
        const revertedBal =
          acc.account_type === 'CREDIT_CARD'
            ? tx.direction === 'DEBIT'
              ? Math.max(0, cur - amt)
              : cur + amt
            : tx.direction === 'DEBIT'
            ? cur + amt
            : cur - amt;
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
