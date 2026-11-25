-- ========================================================
-- MONZI 2.0: STRICT PER-USER RLS POLICIES (RUN IN SUPABASE SQL EDITOR)
-- ========================================================

-- Enable Row Level Security on all tables
ALTER TABLE public.accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.webauthn_credentials ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sms_processing_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.app_settings ENABLE ROW LEVEL SECURITY;

-- Remove old permissive USING (true) policies
DROP POLICY IF EXISTS "Allow all on accounts" ON public.accounts;
DROP POLICY IF EXISTS "Allow all on categories" ON public.categories;
DROP POLICY IF EXISTS "Allow all on transactions" ON public.transactions;
DROP POLICY IF EXISTS "Allow all on app_settings" ON public.app_settings;

-- 1. Accounts (auth.uid() = user_id)
DROP POLICY IF EXISTS "Users can select own accounts" ON public.accounts;
DROP POLICY IF EXISTS "Users can insert own accounts" ON public.accounts;
DROP POLICY IF EXISTS "Users can update own accounts" ON public.accounts;
DROP POLICY IF EXISTS "Users can delete own accounts" ON public.accounts;

CREATE POLICY "Users can select own accounts" ON public.accounts
    FOR SELECT TO authenticated USING (auth.uid() = user_id);
CREATE POLICY "Users can insert own accounts" ON public.accounts
    FOR INSERT TO authenticated WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can update own accounts" ON public.accounts
    FOR UPDATE TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can delete own accounts" ON public.accounts
    FOR DELETE TO authenticated USING (auth.uid() = user_id);

-- 2. Transactions (auth.uid() = user_id)
DROP POLICY IF EXISTS "Users can select own transactions" ON public.transactions;
DROP POLICY IF EXISTS "Users can insert own transactions" ON public.transactions;
DROP POLICY IF EXISTS "Users can update own transactions" ON public.transactions;
DROP POLICY IF EXISTS "Users can delete own transactions" ON public.transactions;

CREATE POLICY "Users can select own transactions" ON public.transactions
    FOR SELECT TO authenticated USING (auth.uid() = user_id);
CREATE POLICY "Users can insert own transactions" ON public.transactions
    FOR INSERT TO authenticated WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can update own transactions" ON public.transactions
    FOR UPDATE TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can delete own transactions" ON public.transactions
    FOR DELETE TO authenticated USING (auth.uid() = user_id);

-- 3. User Profiles (auth.uid() = id)
DROP POLICY IF EXISTS "Users can select own profile" ON public.user_profiles;
DROP POLICY IF EXISTS "Users can insert own profile" ON public.user_profiles;
DROP POLICY IF EXISTS "Users can update own profile" ON public.user_profiles;

CREATE POLICY "Users can select own profile" ON public.user_profiles
    FOR SELECT TO authenticated USING (auth.uid() = id);
CREATE POLICY "Users can insert own profile" ON public.user_profiles
    FOR INSERT TO authenticated WITH CHECK (auth.uid() = id);
CREATE POLICY "Users can update own profile" ON public.user_profiles
    FOR UPDATE TO authenticated USING (auth.uid() = id) WITH CHECK (auth.uid() = id);

-- 4. WebAuthn Credentials (auth.uid() = user_id)
DROP POLICY IF EXISTS "Users can select own webauthn credentials" ON public.webauthn_credentials;
DROP POLICY IF EXISTS "Users can insert own webauthn credentials" ON public.webauthn_credentials;
DROP POLICY IF EXISTS "Users can update own webauthn credentials" ON public.webauthn_credentials;
DROP POLICY IF EXISTS "Users can delete own webauthn credentials" ON public.webauthn_credentials;

CREATE POLICY "Users can select own webauthn credentials" ON public.webauthn_credentials
    FOR SELECT TO authenticated USING (auth.uid() = user_id);
CREATE POLICY "Users can insert own webauthn credentials" ON public.webauthn_credentials
    FOR INSERT TO authenticated WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can update own webauthn credentials" ON public.webauthn_credentials
    FOR UPDATE TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can delete own webauthn credentials" ON public.webauthn_credentials
    FOR DELETE TO authenticated USING (auth.uid() = user_id);

-- 5. SMS Processing Logs (auth.uid() = user_id)
DROP POLICY IF EXISTS "Users can select own sms logs" ON public.sms_processing_logs;
DROP POLICY IF EXISTS "Users can insert own sms logs" ON public.sms_processing_logs;
DROP POLICY IF EXISTS "Users can delete own sms logs" ON public.sms_processing_logs;

CREATE POLICY "Users can select own sms logs" ON public.sms_processing_logs
    FOR SELECT TO authenticated USING (auth.uid() = user_id);
CREATE POLICY "Users can insert own sms logs" ON public.sms_processing_logs
    FOR INSERT TO authenticated WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can delete own sms logs" ON public.sms_processing_logs
    FOR DELETE TO authenticated USING (auth.uid() = user_id);

-- 6. Categories (authenticated read/insert)
DROP POLICY IF EXISTS "Authenticated users can read categories" ON public.categories;
DROP POLICY IF EXISTS "Authenticated users can insert categories" ON public.categories;

CREATE POLICY "Authenticated users can read categories" ON public.categories
    FOR SELECT TO authenticated USING (auth.uid() IS NOT NULL);
CREATE POLICY "Authenticated users can insert categories" ON public.categories
    FOR INSERT TO authenticated WITH CHECK (auth.uid() IS NOT NULL);

-- 7. App Settings (authenticated read only)
DROP POLICY IF EXISTS "Authenticated users can read app_settings" ON public.app_settings;
CREATE POLICY "Authenticated users can read app_settings" ON public.app_settings
    FOR SELECT TO authenticated USING (auth.uid() IS NOT NULL);
