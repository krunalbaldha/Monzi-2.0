import { NextRequest, NextResponse } from 'next/server';
import { supabase, isSupabaseConfigured } from '@/lib/supabase';

export async function GET() {
  if (!isSupabaseConfigured) {
    return NextResponse.json({ categories: [], source: 'supabase_not_configured' });
  }

  try {
    const { data, error } = await supabase
      .from('categories')
      .select('*')
      .order('name', { ascending: true });

    if (error) throw error;
    return NextResponse.json({ categories: data || [], source: 'supabase' });
  } catch (err: any) {
    console.error('Categories query error:', err);
    return NextResponse.json(
      { categories: [], source: 'supabase_error', error: err.message },
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
    const { name, icon, color, is_income } = body;

    if (!name || !name.trim()) {
      return NextResponse.json({ error: 'Category name is required' }, { status: 400 });
    }

    const { data, error } = await supabase
      .from('categories')
      .upsert(
        {
          name: name.trim(),
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
  } catch (err: any) {
    console.error('Category creation error:', err);
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
    return NextResponse.json({ error: 'Missing category id' }, { status: 400 });
  }

  try {
    // Unlink any transactions using this category before deleting
    await supabase
      .from('transactions')
      .update({ category_id: null })
      .eq('category_id', id);

    const { error } = await supabase.from('categories').delete().eq('id', id);
    if (error) throw error;

    return NextResponse.json({ success: true });
  } catch (err: any) {
    console.error('Category deletion error:', err);
    return NextResponse.json({ error: err.message }, { status: 500 });
  }
}
