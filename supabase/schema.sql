-- ========================================================
-- MONZI 2.0: PERSONAL FINANCE DATABASE SCHEMA (SUPABASE)
-- ========================================================

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. ENUMS FOR STRICT DATA CONSISTENCY
DO $$ BEGIN
    CREATE TYPE transaction_direction AS ENUM ('CREDIT', 'DEBIT');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

DO $$ BEGIN
    CREATE TYPE transaction_type AS ENUM (
        'EXPENSE',
        'INCOME',
        'TRANSFER',
        'REFUND',
        'INVESTMENT',
        'CC_BILL_PAYMENT',
        'CASH_WITHDRAWAL',
        'UNCLASSIFIED'
    );
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

DO $$ BEGIN
    CREATE TYPE account_type AS ENUM ('SAVINGS', 'CURRENT', 'CREDIT_CARD', 'WALLET');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- 2. USER PROFILES TABLE (Per-user metadata & sync token)
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

-- 3. WEBAUTHN CREDENTIALS TABLE (Biometric Fingerprint / Face ID)
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

-- 4. ACCOUNTS TABLE (Your Banks & Cards)
CREATE TABLE IF NOT EXISTS public.accounts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE DEFAULT auth.uid(),
    bank_name VARCHAR(50) NOT NULL,            -- e.g. 'HDFC Bank', 'ICICI Bank'
    account_number_mask VARCHAR(10) NOT NULL,   -- e.g. 'XX1234'
    account_type account_type NOT NULL DEFAULT 'SAVINGS',
    initial_balance NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    current_balance NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    last_synced_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_accounts_user_bank_mask UNIQUE (user_id, bank_name, account_number_mask)
);

-- 5. CATEGORIES TABLE
CREATE TABLE IF NOT EXISTS public.categories (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(50) UNIQUE NOT NULL,          -- e.g. 'Food & Dining', 'Groceries', 'Salary'
    icon VARCHAR(30),                          -- Lucide icon name
    color VARCHAR(20) DEFAULT '#2563EB',       -- Hex color for UI badges
    is_income BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 6. TRANSACTIONS TABLE
CREATE TABLE IF NOT EXISTS public.transactions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE DEFAULT auth.uid(),
    account_id UUID REFERENCES public.accounts(id) ON DELETE CASCADE,
    category_id UUID REFERENCES public.categories(id) ON DELETE SET NULL,

    amount NUMERIC(14, 2) NOT NULL,
    direction transaction_direction NOT NULL,
    transaction_type transaction_type NOT NULL DEFAULT 'UNCLASSIFIED',

    merchant VARCHAR(100),                      -- e.g. 'Swiggy', 'Amazon India', 'Zomato'
    payment_mode VARCHAR(20) DEFAULT 'UPI',     -- 'UPI', 'IMPS', 'POS', 'ATM', 'NETBANKING'
    reference_id VARCHAR(100),                  -- Bank UTR / RRN

    -- Crucial for deduplication and ground truth from SMS
    sms_fingerprint VARCHAR(64) NOT NULL,       -- SHA256 of (timestamp + amount + mask + ref)
    parsed_available_balance NUMERIC(14, 2),    -- The "Avl Bal" stated in the SMS

    -- Accounting flags
    is_excluded_from_totals BOOLEAN NOT NULL DEFAULT FALSE, -- True for Transfers & CC bill payments
    linked_transaction_id UUID REFERENCES public.transactions(id) ON DELETE SET NULL, -- For refunds/transfers

    notes TEXT,
    transaction_date TIMESTAMPTZ NOT NULL,      -- Original SMS date & time
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_transactions_user_fingerprint UNIQUE (user_id, sms_fingerprint)
);

-- 7. SMS PROCESSING LOGS TABLE (Privacy-first: NO raw SMS body stored)
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

-- 8. PERFORMANCE INDEXES
CREATE INDEX IF NOT EXISTS idx_accounts_user ON public.accounts (user_id);
CREATE INDEX IF NOT EXISTS idx_transactions_date ON public.transactions (transaction_date DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_user_date ON public.transactions (user_id, transaction_date DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_user_ref ON public.transactions (user_id, reference_id);
CREATE INDEX IF NOT EXISTS idx_transactions_account ON public.transactions (account_id);
CREATE INDEX IF NOT EXISTS idx_transactions_type ON public.transactions (transaction_type);
CREATE INDEX IF NOT EXISTS idx_transactions_category ON public.transactions (category_id);
CREATE INDEX IF NOT EXISTS idx_webauthn_user ON public.webauthn_credentials (user_id);
CREATE INDEX IF NOT EXISTS idx_sms_logs_user_date ON public.sms_processing_logs (user_id, created_at DESC);

-- 9. SYSTEM SETTINGS TABLE (Legacy / Authenticated config)
CREATE TABLE IF NOT EXISTS public.app_settings (
    key VARCHAR(50) PRIMARY KEY,
    value JSONB NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 10. RESOLVE DEVICE SYNC TOKEN FUNCTION (SECURITY DEFINER for Android SyncWorker)
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

    SELECT id INTO v_dup_id
    FROM public.transactions
    WHERE user_id = v_user_id AND sms_fingerprint = p_sms_fingerprint
    LIMIT 1;

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

-- 11. AUTO-CREATE USER PROFILE TRIGGER ON AUTH SIGNUP
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

-- 12. ENABLE REALTIME FOR TRANSACTIONS & ACCOUNTS
-- Allows the Next.js glassmorphism dashboard to receive instant push updates
DO $$ BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.transactions;
EXCEPTION
    WHEN duplicate_object THEN null;
    WHEN undefined_object THEN null;
END $$;

DO $$ BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.accounts;
EXCEPTION
    WHEN duplicate_object THEN null;
    WHEN undefined_object THEN null;
END $$;

-- 13. ROW LEVEL SECURITY POLICIES (STRICT PER-USER ISOLATION)
ALTER TABLE public.accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.webauthn_credentials ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sms_processing_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.app_settings ENABLE ROW LEVEL SECURITY;

-- Drop any legacy permissive policies
DROP POLICY IF EXISTS "Allow all on accounts" ON public.accounts;
DROP POLICY IF EXISTS "Allow all on categories" ON public.categories;
DROP POLICY IF EXISTS "Allow all on transactions" ON public.transactions;
DROP POLICY IF EXISTS "Allow all on app_settings" ON public.app_settings;

-- Accounts: strictly scoped to auth.uid() = user_id
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

-- Transactions: strictly scoped to auth.uid() = user_id
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

-- User Profiles: strictly scoped to auth.uid() = id
DROP POLICY IF EXISTS "Users can select own profile" ON public.user_profiles;
DROP POLICY IF EXISTS "Users can insert own profile" ON public.user_profiles;
DROP POLICY IF EXISTS "Users can update own profile" ON public.user_profiles;

CREATE POLICY "Users can select own profile" ON public.user_profiles
    FOR SELECT TO authenticated USING (auth.uid() = id);
CREATE POLICY "Users can insert own profile" ON public.user_profiles
    FOR INSERT TO authenticated WITH CHECK (auth.uid() = id);
CREATE POLICY "Users can update own profile" ON public.user_profiles
    FOR UPDATE TO authenticated USING (auth.uid() = id) WITH CHECK (auth.uid() = id);

-- WebAuthn Credentials: strictly scoped to auth.uid() = user_id
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

-- SMS Processing Logs: strictly scoped to auth.uid() = user_id
DROP POLICY IF EXISTS "Users can select own sms logs" ON public.sms_processing_logs;
DROP POLICY IF EXISTS "Users can insert own sms logs" ON public.sms_processing_logs;
DROP POLICY IF EXISTS "Users can delete own sms logs" ON public.sms_processing_logs;

CREATE POLICY "Users can select own sms logs" ON public.sms_processing_logs
    FOR SELECT TO authenticated USING (auth.uid() = user_id);
CREATE POLICY "Users can insert own sms logs" ON public.sms_processing_logs
    FOR INSERT TO authenticated WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can delete own sms logs" ON public.sms_processing_logs
    FOR DELETE TO authenticated USING (auth.uid() = user_id);

-- Categories: shared taxonomy, authenticated read/insert
DROP POLICY IF EXISTS "Authenticated users can read categories" ON public.categories;
DROP POLICY IF EXISTS "Authenticated users can insert categories" ON public.categories;

CREATE POLICY "Authenticated users can read categories" ON public.categories
    FOR SELECT TO authenticated USING (auth.uid() IS NOT NULL);
CREATE POLICY "Authenticated users can insert categories" ON public.categories
    FOR INSERT TO authenticated WITH CHECK (auth.uid() IS NOT NULL);

-- App Settings: authenticated read only (no anonymous access)
DROP POLICY IF EXISTS "Authenticated users can read app_settings" ON public.app_settings;
CREATE POLICY "Authenticated users can read app_settings" ON public.app_settings
    FOR SELECT TO authenticated USING (auth.uid() IS NOT NULL);
