import { NextRequest, NextResponse } from 'next/server';
import { getAuthenticatedUser } from '@/lib/supabase/server';
import {
  isSupabaseConfigured,
  isUndefinedColumnError,
  SCHEMA_MIGRATION_MESSAGE,
  getErrorMessage,
} from '@/lib/supabase';

export async function GET() {
  if (!isSupabaseConfigured) {
    return NextResponse.json({ error: 'Supabase not configured' }, { status: 503 });
  }

  const auth = await getAuthenticatedUser();
  if (!auth) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
  }

  try {
    const { data, error } = await auth.supabase
      .from('accounts')
      .select('*')
      .eq('user_id', auth.user.id)
      .order('current_balance', { ascending: false });

    if (error) {
      if (isUndefinedColumnError(error)) {
        return NextResponse.json({
          accounts: [],
          source: 'schema_migration_required',
          schema_migration_required: true,
          message: SCHEMA_MIGRATION_MESSAGE,
        });
      }
      throw error;
    }

    return NextResponse.json({ accounts: data || [], source: 'supabase' });
  } catch (err: unknown) {
    console.error('Accounts query error:', err);
    return NextResponse.json(
      { accounts: [], source: 'supabase_error', error: getErrorMessage(err) },
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
    const { bank_name, account_number_mask, account_type, initial_balance } = body;

    if (!bank_name || !account_number_mask) {
      return NextResponse.json(
        { error: 'Bank name and account number mask are required' },
        { status: 400 }
      );
    }

    const initBal = parseFloat(initial_balance) || 0;
    const rawMask = String(account_number_mask).trim().toUpperCase();
    const cleanMask = rawMask.startsWith('XX') ? rawMask : `XX${rawMask}`;

    const { data, error } = await auth.supabase
      .from('accounts')
      .upsert(
        {
          user_id: auth.user.id,
          bank_name: String(bank_name).trim(),
          account_number_mask: cleanMask,
          account_type: account_type || 'SAVINGS',
          initial_balance: initBal,
          current_balance: initBal,
          updated_at: new Date().toISOString(),
        },
        { onConflict: 'user_id,bank_name,account_number_mask' }
      )
      .select()
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

    return NextResponse.json({ success: true, account: data });
  } catch (err: unknown) {
    console.error('Account creation error:', err);
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
    const { id, current_balance, bank_name, account_number_mask, account_type } = body;

    if (!id) {
      return NextResponse.json({ error: 'Missing account id' }, { status: 400 });
    }

    const updates: Record<string, unknown> = {
      updated_at: new Date().toISOString(),
    };
    if (current_balance !== undefined) updates.current_balance = parseFloat(current_balance) || 0;
    if (bank_name !== undefined) updates.bank_name = String(bank_name).trim();
    if (account_number_mask !== undefined) updates.account_number_mask = String(account_number_mask).trim();
    if (account_type !== undefined) updates.account_type = account_type;

    const { data, error } = await auth.supabase
      .from('accounts')
      .update(updates)
      .eq('id', id)
      .eq('user_id', auth.user.id)
      .select()
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

    return NextResponse.json({ success: true, account: data });
  } catch (err: unknown) {
    console.error('Account update error:', err);
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
    return NextResponse.json({ error: 'Missing account id' }, { status: 400 });
  }

  try {
    const { error } = await auth.supabase
      .from('accounts')
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
    console.error('Account delete error:', err);
    return NextResponse.json({ error: getErrorMessage(err) }, { status: 500 });
  }
}
