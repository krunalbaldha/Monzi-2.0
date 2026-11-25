-- ========================================================
-- MONZI 2.0 MIGRATION 002: AUTH, PER-USER RLS, WEBAUTHN & SMS LOGS
-- Idempotent migration — safe to run multiple times in Supabase SQL Editor.
-- ========================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. ADD user_id TO accounts AND UPDATE PER-USER UNIQUE CONSTRAINT
ALTER TABLE public.accounts
    ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE DEFAULT auth.uid();

ALTER TABLE public.accounts DROP CONSTRAINT IF EXISTS uq_bank_account;

DO $$
DECLARE
    con_name TEXT;
BEGIN
    -- Drop any legacy unique constraint on (bank_name, account_number_mask) without user_id
    FOR con_name IN
        SELECT c.conname
        FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        JOIN pg_namespace n ON n.oid = t.relnamespace
        WHERE n.nspname = 'public'
          AND t.relname = 'accounts'
          AND c.contype = 'u'
          AND pg_get_constraintdef(c.oid) = 'UNIQUE (bank_name, account_number_mask)'
    LOOP
        EXECUTE format('ALTER TABLE public.accounts DROP CONSTRAINT IF EXISTS %I', con_name);
    END LOOP;
END $$;

DO $$ BEGIN
    ALTER TABLE public.accounts
        ADD CONSTRAINT uq_accounts_user_bank_mask UNIQUE (user_id, bank_name, account_number_mask);
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

CREATE INDEX IF NOT EXISTS idx_accounts_user ON public.accounts (user_id);

-- 2. ADD user_id TO transactions AND UPDATE PER-USER UNIQUE CONSTRAINT
ALTER TABLE public.transactions
    ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE DEFAULT auth.uid();

ALTER TABLE public.transactions DROP CONSTRAINT IF EXISTS transactions_sms_fingerprint_key;
DROP INDEX IF EXISTS idx_transactions_sms_fingerprint;

DO $$
DECLARE
    con_name TEXT;
    idx_name TEXT;
BEGIN
    -- Drop any legacy single-column unique constraint on sms_fingerprint
    FOR con_name IN
        SELECT c.conname
        FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        JOIN pg_namespace n ON n.oid = t.relnamespace
        WHERE n.nspname = 'public'
          AND t.relname = 'transactions'
          AND c.contype = 'u'
          AND pg_get_constraintdef(c.oid) = 'UNIQUE (sms_fingerprint)'
    LOOP
        EXECUTE format('ALTER TABLE public.transactions DROP CONSTRAINT IF EXISTS %I', con_name);
    END LOOP;

    -- Drop any standalone unique index on (sms_fingerprint)
    FOR idx_name IN
        SELECT i.relname
        FROM pg_index ix
        JOIN pg_class i ON i.oid = ix.indexrelid
        JOIN pg_class t ON t.oid = ix.indrelid
        JOIN pg_namespace n ON n.oid = t.relnamespace
        WHERE n.nspname = 'public'
          AND t.relname = 'transactions'
          AND ix.indisunique = true
          AND ix.indisprimary = false
          AND pg_get_indexdef(i.oid) ILIKE '%(sms_fingerprint)%'
    LOOP
        EXECUTE format('DROP INDEX IF EXISTS public.%I', idx_name);
    END LOOP;
END $$;

DO $$ BEGIN
    ALTER TABLE public.transactions
        ADD CONSTRAINT uq_transactions_user_fingerprint UNIQUE (user_id, sms_fingerprint);
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

CREATE INDEX IF NOT EXISTS idx_transactions_user_date ON public.transactions (user_id, transaction_date DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_user_ref ON public.transactions (user_id, reference_id);

-- 3. CREATE user_profiles TABLE
CREATE TABLE IF NOT EXISTS public.user_profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    full_name VARCHAR(120),
    email VARCHAR(255),
    currency VARCHAR(10) DEFAULT 'INR',
    sms_permission VARCHAR(20) DEFAULT 'prompt',
    biometric_enabled BOOLEAN DEFAULT FALSE,
    device_sync_token VARCHAR(64) UNIQUE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 4. CREATE webauthn_credentials TABLE
CREATE TABLE IF NOT EXISTS public.webauthn_credentials (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    credential_id TEXT UNIQUE NOT NULL,
    public_key_spki TEXT NOT NULL,
    algorithm INTEGER NOT NULL DEFAULT -7,
    device_name VARCHAR(120),
    counter BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    last_used_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_webauthn_user ON public.webauthn_credentials (user_id);

-- 5. CREATE sms_processing_logs TABLE (NO raw SMS body stored!)
CREATE TABLE IF NOT EXISTS public.sms_processing_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    device_id VARCHAR(80),
    sender VARCHAR(60),
    status VARCHAR(24) NOT NULL,
    reason TEXT,
    extracted_type VARCHAR(16),
    extracted_amount NUMERIC(14, 2),
    extracted_bank VARCHAR(60),
    extracted_date TIMESTAMPTZ,
    reference_id VARCHAR(100),
    sms_fingerprint VARCHAR(64),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sms_logs_user_date ON public.sms_processing_logs (user_id, created_at DESC);

-- 6. SECURITY DEFINER FUNCTION FOR ANDROID DEVICE SYNC TOKEN RESOLUTION
CREATE OR REPLACE FUNCTION public.resolve_device_sync_user(p_token TEXT)
RETURNS UUID
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT id
    FROM public.user_profiles
    WHERE p_token IS NOT NULL
      AND length(trim(p_token)) > 0
      AND device_sync_token = trim(p_token)
    LIMIT 1;
$$;

REVOKE ALL ON FUNCTION public.resolve_device_sync_user(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.resolve_device_sync_user(TEXT) TO anon, authenticated, service_role;

-- 6B. SECURITY DEFINER INGESTION RPC FOR ANDROID BACKGROUND SYNC (WORKS WITHOUT SERVICE ROLE KEY)
CREATE OR REPLACE FUNCTION public.device_sync_ingest_transaction(
    p_sync_token TEXT,
    p_device_id TEXT,
    p_sender TEXT,
    p_amount NUMERIC,
    p_direction transaction_direction,
    p_transaction_type transaction_type,
    p_bank_name TEXT,
    p_account_mask TEXT,
    p_account_type account_type,
    p_merchant TEXT,
    p_payment_mode TEXT,
    p_reference_id TEXT,
    p_sms_fingerprint TEXT,
    p_parsed_available_balance NUMERIC,
    p_is_excluded_from_totals BOOLEAN,
    p_transaction_date TIMESTAMPTZ,
    p_category_id UUID
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_user_id UUID;
    v_account_id UUID;
    v_last_synced TIMESTAMPTZ;
    v_cur_balance NUMERIC(14, 2);
    v_dup_id UUID;
    v_new_balance NUMERIC(14, 2);
BEGIN
    SELECT id INTO v_user_id
    FROM public.user_profiles
    WHERE p_sync_token IS NOT NULL
      AND length(trim(p_sync_token)) > 0
      AND device_sync_token = trim(p_sync_token)
    LIMIT 1;

    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('status', 'UNAUTHORIZED', 'reason', 'Invalid device sync token');
    END IF;

    -- 1. Check duplicate by fingerprint
    SELECT id INTO v_dup_id
    FROM public.transactions
    WHERE user_id = v_user_id AND sms_fingerprint = p_sms_fingerprint
    LIMIT 1;

    -- 2. Check duplicate by reference_id + amount + direction
    IF v_dup_id IS NULL AND p_reference_id IS NOT NULL AND length(trim(p_reference_id)) >= 6 THEN
        SELECT id INTO v_dup_id
        FROM public.transactions
        WHERE user_id = v_user_id
          AND reference_id = trim(p_reference_id)
          AND amount = p_amount
          AND direction = p_direction
        LIMIT 1;
    END IF;

    IF v_dup_id IS NOT NULL THEN
        INSERT INTO public.sms_processing_logs (
            user_id, device_id, sender, status, reason,
            extracted_type, extracted_amount, extracted_bank, extracted_date,
            reference_id, sms_fingerprint
        ) VALUES (
            v_user_id, p_device_id, p_sender, 'DUPLICATE', 'Duplicate transaction detected',
            p_transaction_type::TEXT, p_amount, p_bank_name, p_transaction_date,
            p_reference_id, p_sms_fingerprint
        );
        RETURN jsonb_build_object('status', 'DUPLICATE', 'reason', 'Duplicate transaction detected');
    END IF;

    -- 3. Upsert account safely for out-of-order SMS
    SELECT id, current_balance, last_synced_at
    INTO v_account_id, v_cur_balance, v_last_synced
    FROM public.accounts
    WHERE user_id = v_user_id
      AND bank_name = p_bank_name
      AND account_number_mask = p_account_mask
    LIMIT 1;

    IF v_account_id IS NOT NULL THEN
        IF v_last_synced IS NULL OR p_transaction_date >= v_last_synced THEN
            IF p_parsed_available_balance IS NOT NULL THEN
                v_new_balance := p_parsed_available_balance;
            ELSIF p_direction = 'CREDIT' THEN
                v_new_balance := COALESCE(v_cur_balance, 0) + p_amount;
            ELSE
                v_new_balance := COALESCE(v_cur_balance, 0) - p_amount;
            END IF;

            UPDATE public.accounts
            SET current_balance = v_new_balance,
                last_synced_at = p_transaction_date,
                updated_at = NOW()
            WHERE id = v_account_id AND user_id = v_user_id;
        END IF;
    ELSE
        INSERT INTO public.accounts (
            user_id, bank_name, account_number_mask, account_type,
            current_balance, last_synced_at
        ) VALUES (
            v_user_id, p_bank_name, p_account_mask, COALESCE(p_account_type, 'SAVINGS'),
            COALESCE(p_parsed_available_balance, CASE WHEN p_direction = 'CREDIT' THEN p_amount ELSE 0 END),
            p_transaction_date
        )
        RETURNING id INTO v_account_id;
    END IF;

    -- 4. Insert transaction (never stores raw SMS text)
    INSERT INTO public.transactions (
        user_id, account_id, category_id, amount, direction, transaction_type,
        merchant, payment_mode, reference_id, sms_fingerprint,
        parsed_available_balance, is_excluded_from_totals, transaction_date
    ) VALUES (
        v_user_id, v_account_id, p_category_id, p_amount, p_direction, p_transaction_type,
        COALESCE(NULLIF(trim(p_merchant), ''), 'Bank Transaction'),
        COALESCE(NULLIF(trim(p_payment_mode), ''), 'UPI'),
        NULLIF(trim(p_reference_id), ''),
        p_sms_fingerprint,
        p_parsed_available_balance,
        COALESCE(p_is_excluded_from_totals, FALSE),
        p_transaction_date
    );

    INSERT INTO public.sms_processing_logs (
        user_id, device_id, sender, status, reason,
        extracted_type, extracted_amount, extracted_bank, extracted_date,
        reference_id, sms_fingerprint
    ) VALUES (
        v_user_id, p_device_id, p_sender, 'PROCESSED', 'Transaction extracted and synced',
        p_transaction_type::TEXT, p_amount, p_bank_name, p_transaction_date,
        p_reference_id, p_sms_fingerprint
    );

    RETURN jsonb_build_object('status', 'PROCESSED', 'account_id', v_account_id);
END;
$$;

REVOKE ALL ON FUNCTION public.device_sync_ingest_transaction(TEXT, TEXT, TEXT, NUMERIC, transaction_direction, transaction_type, TEXT, TEXT, account_type, TEXT, TEXT, TEXT, TEXT, NUMERIC, BOOLEAN, TIMESTAMPTZ, UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.device_sync_ingest_transaction(TEXT, TEXT, TEXT, NUMERIC, transaction_direction, transaction_type, TEXT, TEXT, account_type, TEXT, TEXT, TEXT, TEXT, NUMERIC, BOOLEAN, TIMESTAMPTZ, UUID) TO anon, authenticated, service_role;

-- 7. AUTO-PROVISION user_profiles ON SIGNUP & BACKFILL EXISTING AUTH USERS
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    INSERT INTO public.user_profiles (
        id,
        full_name,
        email,
        currency,
        sms_permission,
        biometric_enabled,
        device_sync_token
    )
    VALUES (
        NEW.id,
        COALESCE(NEW.raw_user_meta_data->>'full_name', NEW.raw_user_meta_data->>'name', split_part(COALESCE(NEW.email, ''), '@', 1)),
        NEW.email,
        COALESCE(NEW.raw_user_meta_data->>'currency', 'INR'),
        COALESCE(NEW.raw_user_meta_data->>'sms_permission', 'prompt'),
        COALESCE((NEW.raw_user_meta_data->>'biometric_enabled')::BOOLEAN, FALSE),
        substr(replace(gen_random_uuid()::TEXT || gen_random_uuid()::TEXT, '-', ''), 1, 48)
    )
    ON CONFLICT (id) DO NOTHING;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

INSERT INTO public.user_profiles (id, full_name, email, currency, sms_permission, biometric_enabled, device_sync_token)
SELECT
    u.id,
    COALESCE(u.raw_user_meta_data->>'full_name', u.raw_user_meta_data->>'name', split_part(COALESCE(u.email, ''), '@', 1)),
    u.email,
    COALESCE(u.raw_user_meta_data->>'currency', 'INR'),
    COALESCE(u.raw_user_meta_data->>'sms_permission', 'prompt'),
    COALESCE((u.raw_user_meta_data->>'biometric_enabled')::BOOLEAN, FALSE),
    substr(replace(gen_random_uuid()::TEXT || gen_random_uuid()::TEXT, '-', ''), 1, 48)
FROM auth.users u
ON CONFLICT (id) DO NOTHING;

-- 8. STRICT PER-USER RLS POLICIES (REMOVE OLD USING (true) POLICIES)
ALTER TABLE public.accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.webauthn_credentials ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sms_processing_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.app_settings ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Allow all on accounts" ON public.accounts;
DROP POLICY IF EXISTS "Allow all on categories" ON public.categories;
DROP POLICY IF EXISTS "Allow all on transactions" ON public.transactions;
DROP POLICY IF EXISTS "Allow all on app_settings" ON public.app_settings;

-- Accounts
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

-- Transactions
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

-- User Profiles
DROP POLICY IF EXISTS "Users can select own profile" ON public.user_profiles;
DROP POLICY IF EXISTS "Users can insert own profile" ON public.user_profiles;
DROP POLICY IF EXISTS "Users can update own profile" ON public.user_profiles;

CREATE POLICY "Users can select own profile" ON public.user_profiles
    FOR SELECT TO authenticated USING (auth.uid() = id);
CREATE POLICY "Users can insert own profile" ON public.user_profiles
    FOR INSERT TO authenticated WITH CHECK (auth.uid() = id);
CREATE POLICY "Users can update own profile" ON public.user_profiles
    FOR UPDATE TO authenticated USING (auth.uid() = id) WITH CHECK (auth.uid() = id);

-- WebAuthn Credentials
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

-- SMS Processing Logs
DROP POLICY IF EXISTS "Users can select own sms logs" ON public.sms_processing_logs;
DROP POLICY IF EXISTS "Users can insert own sms logs" ON public.sms_processing_logs;
DROP POLICY IF EXISTS "Users can delete own sms logs" ON public.sms_processing_logs;

CREATE POLICY "Users can select own sms logs" ON public.sms_processing_logs
    FOR SELECT TO authenticated USING (auth.uid() = user_id);
CREATE POLICY "Users can insert own sms logs" ON public.sms_processing_logs
    FOR INSERT TO authenticated WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can delete own sms logs" ON public.sms_processing_logs
    FOR DELETE TO authenticated USING (auth.uid() = user_id);

-- Categories (authenticated read/insert)
DROP POLICY IF EXISTS "Authenticated users can read categories" ON public.categories;
DROP POLICY IF EXISTS "Authenticated users can insert categories" ON public.categories;

CREATE POLICY "Authenticated users can read categories" ON public.categories
    FOR SELECT TO authenticated USING (auth.uid() IS NOT NULL);
CREATE POLICY "Authenticated users can insert categories" ON public.categories
    FOR INSERT TO authenticated WITH CHECK (auth.uid() IS NOT NULL);

-- App Settings (authenticated read only)
DROP POLICY IF EXISTS "Authenticated users can read app_settings" ON public.app_settings;
CREATE POLICY "Authenticated users can read app_settings" ON public.app_settings
    FOR SELECT TO authenticated USING (auth.uid() IS NOT NULL);
