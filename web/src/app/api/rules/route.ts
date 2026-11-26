import { NextRequest, NextResponse } from 'next/server';
import { getAuthenticatedUser } from '@/lib/supabase/server';
import {
  isSupabaseConfigured,
  isSchemaMigrationError,
  getErrorMessage,
} from '@/lib/supabase';

const MIGRATION_003_MESSAGE =
  'Database migration 003_frd_complete_schema.sql has not been applied yet. Please run it in Supabase SQL Editor.';

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
      .from('transaction_rules')
      .select('*, categories(*)')
      .eq('user_id', auth.user.id)
      .order('priority', { ascending: true })
      .order('created_at', { ascending: false });

    if (error) {
      if (isSchemaMigrationError(error)) {
        return NextResponse.json({
          rules: [],
          source: 'schema_migration_required',
          schema_migration_required: true,
          message: MIGRATION_003_MESSAGE,
        });
      }
      throw error;
    }

    return NextResponse.json({ rules: data || [], source: 'supabase' });
  } catch (err: unknown) {
    console.error('Transaction rules fetch error:', err);
    return NextResponse.json(
      { rules: [], source: 'supabase_error', error: getErrorMessage(err) },
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
    const matchingRule = String(body.matching_rule || '').trim();
    const categoryId = String(body.category_id || '').trim();
    const priorityRaw = Number(body.priority);
    const priority = Number.isFinite(priorityRaw) ? Math.max(1, Math.min(1000, Math.round(priorityRaw))) : 10;
    const enabled = body.enabled !== undefined ? Boolean(body.enabled) : true;

    if (!matchingRule) {
      return NextResponse.json({ error: 'Matching rule keyword is required' }, { status: 400 });
    }
    if (!categoryId) {
      return NextResponse.json({ error: 'Target category is required' }, { status: 400 });
    }

    const { data, error } = await auth.supabase
      .from('transaction_rules')
      .insert({
        user_id: auth.user.id,
        matching_rule: matchingRule.slice(0, 160),
        category_id: categoryId,
        priority,
        enabled,
      })
      .select('*, categories(*)')
      .single();

    if (error) {
      if (isSchemaMigrationError(error)) {
        return NextResponse.json(
          { error: MIGRATION_003_MESSAGE, schema_migration_required: true },
          { status: 503 }
        );
      }
      throw error;
    }

    return NextResponse.json({ success: true, rule: data });
  } catch (err: unknown) {
    console.error('Transaction rule create error:', err);
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
    const { id, matching_rule, category_id, priority, enabled } = body;

    if (!id) {
      return NextResponse.json({ error: 'Missing rule id' }, { status: 400 });
    }

    const updates: Record<string, unknown> = {};
    if (matching_rule !== undefined) {
      const trimmed = String(matching_rule).trim();
      if (!trimmed) {
        return NextResponse.json({ error: 'Matching rule cannot be empty' }, { status: 400 });
      }
      updates.matching_rule = trimmed.slice(0, 160);
    }
    if (category_id !== undefined) {
      if (!category_id) {
        return NextResponse.json({ error: 'Target category is required' }, { status: 400 });
      }
      updates.category_id = String(category_id);
    }
    if (priority !== undefined) {
      const p = Number(priority);
      if (Number.isFinite(p)) {
        updates.priority = Math.max(1, Math.min(1000, Math.round(p)));
      }
    }
    if (enabled !== undefined) {
      updates.enabled = Boolean(enabled);
    }

    if (Object.keys(updates).length === 0) {
      return NextResponse.json({ error: 'No fields to update' }, { status: 400 });
    }

    const { data, error } = await auth.supabase
      .from('transaction_rules')
      .update(updates)
      .eq('id', id)
      .eq('user_id', auth.user.id)
      .select('*, categories(*)')
      .single();

    if (error) {
      if (isSchemaMigrationError(error)) {
        return NextResponse.json(
          { error: MIGRATION_003_MESSAGE, schema_migration_required: true },
          { status: 503 }
        );
      }
      throw error;
    }

    return NextResponse.json({ success: true, rule: data });
  } catch (err: unknown) {
    console.error('Transaction rule update error:', err);
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
    return NextResponse.json({ error: 'Missing rule id' }, { status: 400 });
  }

  try {
    const { error } = await auth.supabase
      .from('transaction_rules')
      .delete()
      .eq('id', id)
      .eq('user_id', auth.user.id);

    if (error) {
      if (isSchemaMigrationError(error)) {
        return NextResponse.json(
          { error: MIGRATION_003_MESSAGE, schema_migration_required: true },
          { status: 503 }
        );
      }
      throw error;
    }

    return NextResponse.json({ success: true });
  } catch (err: unknown) {
    console.error('Transaction rule delete error:', err);
    return NextResponse.json({ error: getErrorMessage(err) }, { status: 500 });
  }
}
