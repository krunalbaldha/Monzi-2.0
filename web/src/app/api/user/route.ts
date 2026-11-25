import { NextRequest, NextResponse } from 'next/server';
import { supabase, isSupabaseConfigured } from '@/lib/supabase';

export async function GET() {
  if (!isSupabaseConfigured) {
    return NextResponse.json({
      profile: {
        name: 'Krunal Baldha',
        currency: 'INR',
        sms_permission: 'prompt',
      },
      source: 'unconfigured',
    });
  }

  try {
    const { data, error } = await supabase
      .from('app_settings')
      .select('value')
      .eq('key', 'user_profile')
      .single();

    if (error || !data) {
      // Default initial profile
      const defaultProfile = {
        name: 'Krunal Baldha',
        currency: 'INR',
        sms_permission: 'prompt',
      };
      // Upsert default
      await supabase
        .from('app_settings')
        .upsert({ key: 'user_profile', value: defaultProfile, updated_at: new Date().toISOString() });

      return NextResponse.json({ profile: defaultProfile, source: 'default' });
    }

    return NextResponse.json({ profile: data.value, source: 'supabase' });
  } catch (err: any) {
    console.error('User profile fetch error:', err);
    return NextResponse.json(
      {
        profile: {
          name: 'Krunal Baldha',
          currency: 'INR',
          sms_permission: 'prompt',
        },
        source: 'fallback',
      },
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
    const { name, currency, sms_permission } = body;

    const { data: existing } = await supabase
      .from('app_settings')
      .select('value')
      .eq('key', 'user_profile')
      .single();

    const updatedProfile = {
      ...(existing?.value || {}),
      ...(name !== undefined && { name: name.trim() }),
      ...(currency !== undefined && { currency }),
      ...(sms_permission !== undefined && { sms_permission }),
      updated_at: new Date().toISOString(),
    };

    const { error } = await supabase
      .from('app_settings')
      .upsert({
        key: 'user_profile',
        value: updatedProfile,
        updated_at: new Date().toISOString(),
      });

    if (error) throw error;

    return NextResponse.json({ success: true, profile: updatedProfile });
  } catch (err: any) {
    console.error('User profile update error:', err);
    return NextResponse.json({ error: err.message }, { status: 500 });
  }
}

export async function PATCH(req: NextRequest) {
  return POST(req);
}
