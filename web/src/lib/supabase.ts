import { createClient } from '@supabase/supabase-js';

const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL || '';
const supabaseAnonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY || '';

export const isSupabaseConfigured = Boolean(
  supabaseUrl &&
    supabaseAnonKey &&
    !supabaseUrl.includes('placeholder')
);

// Fallback client for builds and when env vars are missing
export const supabase = createClient(
  supabaseUrl || 'https://placeholder.supabase.co',
  supabaseAnonKey || 'placeholder-anon-key'
);

export function isUndefinedColumnError(err: unknown): boolean {
  if (!err || typeof err !== 'object') return false;
  const e = err as { code?: string; message?: string; details?: string };
  const code = String(e.code || '');
  const msg = `${e.message || ''} ${e.details || ''}`.toLowerCase();
  return (
    code === '42703' ||
    code === 'PGRST204' ||
    (msg.includes('column') && msg.includes('does not exist')) ||
    msg.includes('could not find the')
  );
}

export function isMissingTableError(err: unknown): boolean {
  if (!err || typeof err !== 'object') return false;
  const e = err as { code?: string; message?: string; details?: string };
  const code = String(e.code || '');
  const msg = `${e.message || ''} ${e.details || ''}`.toLowerCase();
  return (
    code === '42P01' ||
    code === 'PGRST205' ||
    (msg.includes('relation') && msg.includes('does not exist')) ||
    msg.includes('could not find the table')
  );
}

export function isSchemaMigrationError(err: unknown): boolean {
  return isUndefinedColumnError(err) || isMissingTableError(err);
}

export const SCHEMA_MIGRATION_MESSAGE =
  'Database migration 002_auth_rls_and_logs.sql has not been applied yet. Please run it in Supabase SQL Editor.';

export function getErrorMessage(err: unknown, fallback = 'Unexpected server error'): string {
  if (err instanceof Error && err.message) return err.message;
  if (err && typeof err === 'object' && 'message' in err) {
    const msg = (err as { message?: unknown }).message;
    if (typeof msg === 'string' && msg) return msg;
  }
  return fallback;
}
