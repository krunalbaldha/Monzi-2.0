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
      .from('categories')
      .select('*')
      .order('name', { ascending: true });

    if (error) throw error;
    return NextResponse.json({ categories: data || [], source: 'supabase' });
  } catch (err: unknown) {
    console.error('Categories query error:', err);
    return NextResponse.json(
      { categories: [], source: 'supabase_error', error: getErrorMessage(err) },
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
    const { name, icon, color, is_income } = body;

    if (!name || !String(name).trim()) {
      return NextResponse.json({ error: 'Category name is required' }, { status: 400 });
    }

    const { data, error } = await auth.supabase
      .from('categories')
      .upsert(
        {
          name: String(name).trim(),
          icon: icon || 'Tag',
          color: color || '#2563EB',
          is_income: Boolean(is_income),
        },
        { onConflict: 'name' }
      )
      .select()
      .single();

    if (error) throw error;

    return NextResponse.json({ success: true, category: data });
  } catch (err: unknown) {
    console.error('Category creation error:', err);
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
    return NextResponse.json({ error: 'Missing category id' }, { status: 400 });
  }

  try {
    // Unlink authenticated user's transactions using this category before deleting
    const { error: unlinkErr } = await auth.supabase
      .from('transactions')
      .update({ category_id: null })
      .eq('category_id', id)
      .eq('user_id', auth.user.id);

    if (unlinkErr && isUndefinedColumnError(unlinkErr)) {
      return NextResponse.json(
        { error: SCHEMA_MIGRATION_MESSAGE, schema_migration_required: true },
        { status: 503 }
      );
    }

    const { error } = await auth.supabase.from('categories').delete().eq('id', id);
    if (error) throw error;

    return NextResponse.json({ success: true });
  } catch (err: unknown) {
    console.error('Category deletion error:', err);
    return NextResponse.json({ error: getErrorMessage(err) }, { status: 500 });
  }
}
