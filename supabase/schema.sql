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
    opening_balance NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    current_balance NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    balance_source VARCHAR(24) NOT NULL DEFAULT 'CALCULATED',
    account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
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
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    transaction_source VARCHAR(30) NOT NULL DEFAULT 'INCOMING_SMS',
    transaction_status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    transaction_date TIMESTAMPTZ NOT NULL,      -- Original SMS date & time
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
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

-- 7B. TRANSACTION RULES TABLE (User-defined categorization rules)
CREATE TABLE IF NOT EXISTS public.transaction_rules (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    matching_rule VARCHAR(160) NOT NULL,
    category_id UUID REFERENCES public.categories(id) ON DELETE CASCADE,
    priority INTEGER NOT NULL DEFAULT 10,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 7C. DEVICES TABLE (Registered mobile / browser devices)
CREATE TABLE IF NOT EXISTS public.devices (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    device_identifier VARCHAR(120) NOT NULL,
    platform VARCHAR(30) NOT NULL,
    app_version VARCHAR(30),
    last_seen_at TIMESTAMPTZ DEFAULT NOW(),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(user_id, device_identifier)
);

-- 7D. SMS SYNC STATE TABLE (Per-device historical scan & live monitoring state)
CREATE TABLE IF NOT EXISTS public.sms_sync_state (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    device_id VARCHAR(120) NOT NULL,
    historical_scan_status VARCHAR(24) DEFAULT 'PENDING',
    last_processed_message TEXT,
    last_processed_at TIMESTAMPTZ,
    monitoring_status VARCHAR(24) DEFAULT 'INACTIVE',
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(user_id, device_id)
);

-- 7E. SYNC QUEUE TABLE (Resilient offline sync audit queue)
CREATE TABLE IF NOT EXISTS public.sync_queue (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    local_transaction_id VARCHAR(120) NOT NULL,
    transaction_payload JSONB NOT NULL,
    sync_status VARCHAR(24) DEFAULT 'SYNCED',
    retry_count INTEGER DEFAULT 0,
    last_attempt_at TIMESTAMPTZ DEFAULT NOW(),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 7F. AUTOMATION LOGS TABLE (Sync & automation diagnostic events)
CREATE TABLE IF NOT EXISTS public.automation_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    device_id VARCHAR(120),
    process_type VARCHAR(40) NOT NULL,
    process_status VARCHAR(24) NOT NULL,
    error_code VARCHAR(40),
    details TEXT,
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
CREATE INDEX IF NOT EXISTS idx_transaction_rules_user_priority ON public.transaction_rules (user_id, enabled, priority ASC);
CREATE INDEX IF NOT EXISTS idx_devices_user_last_seen ON public.devices (user_id, last_seen_at DESC);
CREATE INDEX IF NOT EXISTS idx_sms_sync_state_user_updated ON public.sms_sync_state (user_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_sync_queue_user_status ON public.sync_queue (user_id, sync_status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_automation_logs_user_date ON public.automation_logs (user_id, created_at DESC);

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

DROP FUNCTION IF EXISTS public.device_sync_ingest_transaction(TEXT, TEXT, TEXT, NUMERIC, transaction_direction, transaction_type, TEXT, TEXT, account_type, TEXT, TEXT, TEXT, TEXT, NUMERIC, BOOLEAN, TIMESTAMPTZ, UUID);
DROP FUNCTION IF EXISTS public.device_sync_ingest_transaction(TEXT, TEXT, TEXT, NUMERIC, transaction_direction, transaction_type, TEXT, TEXT, account_type, TEXT, TEXT, TEXT, TEXT, NUMERIC, BOOLEAN, TIMESTAMPTZ, UUID, VARCHAR, VARCHAR, VARCHAR);

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
    p_category_id UUID,
    p_transaction_source VARCHAR DEFAULT 'INCOMING_SMS',
    p_transaction_status VARCHAR DEFAULT 'COMPLETED',
    p_currency VARCHAR DEFAULT 'INR'
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
    v_balance_source VARCHAR(24);
    v_device_key VARCHAR(120);
    v_tx_source VARCHAR(30);
    v_tx_status VARCHAR(20);
    v_currency VARCHAR(10);
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

    v_device_key := COALESCE(NULLIF(trim(p_device_id), ''), 'android-device');
    v_tx_source := COALESCE(NULLIF(trim(p_transaction_source), ''), 'INCOMING_SMS');
    v_tx_status := COALESCE(NULLIF(trim(p_transaction_status), ''), 'COMPLETED');
    v_currency := COALESCE(NULLIF(trim(p_currency), ''), 'INR');
    v_balance_source := CASE WHEN p_parsed_available_balance IS NOT NULL THEN 'BANK_REPORTED' ELSE 'CALCULATED' END;

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
            v_user_id, v_device_key, p_sender, 'DUPLICATE', 'Duplicate transaction detected',
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
                balance_source = v_balance_source,
                last_synced_at = p_transaction_date,
                updated_at = NOW()
            WHERE id = v_account_id AND user_id = v_user_id;
        END IF;
    ELSE
        INSERT INTO public.accounts (
            user_id, bank_name, account_number_mask, account_type,
            opening_balance, current_balance, balance_source, account_status, last_synced_at
        ) VALUES (
            v_user_id, p_bank_name, p_account_mask, COALESCE(p_account_type, 'SAVINGS'),
            0.00,
            COALESCE(p_parsed_available_balance, CASE WHEN p_direction = 'CREDIT' THEN p_amount ELSE 0 END),
            v_balance_source,
            'ACTIVE',
            p_transaction_date
        )
        RETURNING id INTO v_account_id;
    END IF;

    INSERT INTO public.transactions (
        user_id, account_id, category_id, amount, direction, transaction_type,
        merchant, payment_mode, reference_id, sms_fingerprint,
        parsed_available_balance, is_excluded_from_totals, transaction_date,
        currency, transaction_source, transaction_status, updated_at
    ) VALUES (
        v_user_id, v_account_id, p_category_id, p_amount, p_direction, p_transaction_type,
        COALESCE(NULLIF(trim(p_merchant), ''), 'Bank Transaction'),
        COALESCE(NULLIF(trim(p_payment_mode), ''), 'UPI'),
        NULLIF(trim(p_reference_id), ''),
        p_sms_fingerprint,
        p_parsed_available_balance,
        COALESCE(p_is_excluded_from_totals, FALSE),
        p_transaction_date,
        v_currency,
        v_tx_source,
        v_tx_status,
        NOW()
    );

    INSERT INTO public.sms_processing_logs (
        user_id, device_id, sender, status, reason,
        extracted_type, extracted_amount, extracted_bank, extracted_date,
        reference_id, sms_fingerprint
    ) VALUES (
        v_user_id, v_device_key, p_sender, 'PROCESSED', 'Transaction extracted and synced',
        p_transaction_type::TEXT, p_amount, p_bank_name, p_transaction_date,
        p_reference_id, p_sms_fingerprint
    );

    INSERT INTO public.sms_sync_state (
        user_id, device_id, historical_scan_status, last_processed_at, monitoring_status, updated_at
    ) VALUES (
        v_user_id,
        v_device_key,
        CASE WHEN v_tx_source = 'HISTORICAL_SMS' THEN 'IN_PROGRESS' ELSE 'PENDING' END,
        NOW(),
        'ACTIVE',
        NOW()
    )
    ON CONFLICT (user_id, device_id) DO UPDATE SET
        last_processed_at = NOW(),
        monitoring_status = 'ACTIVE',
        updated_at = NOW();

    INSERT INTO public.automation_logs (
        user_id, device_id, process_type, process_status, details
    ) VALUES (
        v_user_id, v_device_key, 'SMS_INGEST', 'SUCCESS',
        format('Ingested %s %s transaction from %s', p_direction::TEXT, p_amount::TEXT, p_bank_name)
    );

    RETURN jsonb_build_object('status', 'PROCESSED', 'account_id', v_account_id);
END;
$$;

REVOKE ALL ON FUNCTION public.device_sync_ingest_transaction(TEXT, TEXT, TEXT, NUMERIC, transaction_direction, transaction_type, TEXT, TEXT, account_type, TEXT, TEXT, TEXT, TEXT, NUMERIC, BOOLEAN, TIMESTAMPTZ, UUID, VARCHAR, VARCHAR, VARCHAR) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.device_sync_ingest_transaction(TEXT, TEXT, TEXT, NUMERIC, transaction_direction, transaction_type, TEXT, TEXT, account_type, TEXT, TEXT, TEXT, TEXT, NUMERIC, BOOLEAN, TIMESTAMPTZ, UUID, VARCHAR, VARCHAR, VARCHAR) TO anon, authenticated, service_role;

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
ALTER TABLE public.transaction_rules ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sms_sync_state ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sync_queue ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.automation_logs ENABLE ROW LEVEL SECURITY;

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

-- Transaction Rules: strictly scoped to auth.uid() = user_id
DROP POLICY IF EXISTS "Users can select own transaction rules" ON public.transaction_rules;
DROP POLICY IF EXISTS "Users can insert own transaction rules" ON public.transaction_rules;
DROP POLICY IF EXISTS "Users can update own transaction rules" ON public.transaction_rules;
DROP POLICY IF EXISTS "Users can delete own transaction rules" ON public.transaction_rules;

CREATE POLICY "Users can select own transaction rules" ON public.transaction_rules
    FOR SELECT TO authenticated USING (auth.uid() = user_id);
CREATE POLICY "Users can insert own transaction rules" ON public.transaction_rules
    FOR INSERT TO authenticated WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can update own transaction rules" ON public.transaction_rules
    FOR UPDATE TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can delete own transaction rules" ON public.transaction_rules
    FOR DELETE TO authenticated USING (auth.uid() = user_id);

-- Devices: strictly scoped to auth.uid() = user_id
DROP POLICY IF EXISTS "Users can select own devices" ON public.devices;
DROP POLICY IF EXISTS "Users can insert own devices" ON public.devices;
DROP POLICY IF EXISTS "Users can update own devices" ON public.devices;
DROP POLICY IF EXISTS "Users can delete own devices" ON public.devices;

CREATE POLICY "Users can select own devices" ON public.devices
    FOR SELECT TO authenticated USING (auth.uid() = user_id);
CREATE POLICY "Users can insert own devices" ON public.devices
    FOR INSERT TO authenticated WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can update own devices" ON public.devices
    FOR UPDATE TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can delete own devices" ON public.devices
    FOR DELETE TO authenticated USING (auth.uid() = user_id);

-- SMS Sync State: strictly scoped to auth.uid() = user_id
DROP POLICY IF EXISTS "Users can select own sms sync state" ON public.sms_sync_state;
DROP POLICY IF EXISTS "Users can insert own sms sync state" ON public.sms_sync_state;
DROP POLICY IF EXISTS "Users can update own sms sync state" ON public.sms_sync_state;
DROP POLICY IF EXISTS "Users can delete own sms sync state" ON public.sms_sync_state;

CREATE POLICY "Users can select own sms sync state" ON public.sms_sync_state
    FOR SELECT TO authenticated USING (auth.uid() = user_id);
CREATE POLICY "Users can insert own sms sync state" ON public.sms_sync_state
    FOR INSERT TO authenticated WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can update own sms sync state" ON public.sms_sync_state
    FOR UPDATE TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can delete own sms sync state" ON public.sms_sync_state
    FOR DELETE TO authenticated USING (auth.uid() = user_id);

-- Sync Queue: strictly scoped to auth.uid() = user_id
DROP POLICY IF EXISTS "Users can select own sync queue" ON public.sync_queue;
DROP POLICY IF EXISTS "Users can insert own sync queue" ON public.sync_queue;
DROP POLICY IF EXISTS "Users can update own sync queue" ON public.sync_queue;
DROP POLICY IF EXISTS "Users can delete own sync queue" ON public.sync_queue;

CREATE POLICY "Users can select own sync queue" ON public.sync_queue
    FOR SELECT TO authenticated USING (auth.uid() = user_id);
CREATE POLICY "Users can insert own sync queue" ON public.sync_queue
    FOR INSERT TO authenticated WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can update own sync queue" ON public.sync_queue
    FOR UPDATE TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can delete own sync queue" ON public.sync_queue
    FOR DELETE TO authenticated USING (auth.uid() = user_id);

-- Automation Logs: strictly scoped to auth.uid() = user_id
DROP POLICY IF EXISTS "Users can select own automation logs" ON public.automation_logs;
DROP POLICY IF EXISTS "Users can insert own automation logs" ON public.automation_logs;
DROP POLICY IF EXISTS "Users can delete own automation logs" ON public.automation_logs;

CREATE POLICY "Users can select own automation logs" ON public.automation_logs
    FOR SELECT TO authenticated USING (auth.uid() = user_id);
CREATE POLICY "Users can insert own automation logs" ON public.automation_logs
    FOR INSERT TO authenticated WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can delete own automation logs" ON public.automation_logs
    FOR DELETE TO authenticated USING (auth.uid() = user_id);
