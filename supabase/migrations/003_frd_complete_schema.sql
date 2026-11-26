-- ========================================================
-- MONZI 2.0 MIGRATION 003: COMPLETE FRD SCHEMA
-- Idempotent migration — safe to run multiple times in Supabase SQL Editor.
-- ========================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. EXTEND public.accounts (FRD Section 8 & 18.1)
ALTER TABLE public.accounts
    ADD COLUMN IF NOT EXISTS opening_balance NUMERIC(14, 2) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS balance_source VARCHAR(24) DEFAULT 'CALCULATED',
    ADD COLUMN IF NOT EXISTS account_status VARCHAR(20) DEFAULT 'ACTIVE';

UPDATE public.accounts
SET opening_balance = COALESCE(opening_balance, initial_balance, 0),
    balance_source = COALESCE(balance_source, 'CALCULATED'),
    account_status = COALESCE(account_status, 'ACTIVE')
WHERE opening_balance IS NULL
   OR balance_source IS NULL
   OR account_status IS NULL;

-- 2. EXTEND public.transactions (FRD Section 7 & 18.1)
ALTER TABLE public.transactions
    ADD COLUMN IF NOT EXISTS currency VARCHAR(10) DEFAULT 'INR',
    ADD COLUMN IF NOT EXISTS transaction_source VARCHAR(30) DEFAULT 'INCOMING_SMS',
    ADD COLUMN IF NOT EXISTS transaction_status VARCHAR(20) DEFAULT 'COMPLETED',
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT NOW();

-- 3. TRANSACTION RULES TABLE (FRD Section 15.2 & 18.1)
CREATE TABLE IF NOT EXISTS public.transaction_rules (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    matching_rule VARCHAR(160) NOT NULL,
    category_id UUID REFERENCES public.categories(id) ON DELETE CASCADE,
    priority INTEGER NOT NULL DEFAULT 10,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_transaction_rules_user_priority
    ON public.transaction_rules (user_id, enabled, priority ASC);

-- 4. DEVICES TABLE (FRD Section 15.3 & 18.1)
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

CREATE INDEX IF NOT EXISTS idx_devices_user_last_seen
    ON public.devices (user_id, last_seen_at DESC);

-- 5. SMS SYNC STATE TABLE (FRD Section 6 & 18.1)
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

CREATE INDEX IF NOT EXISTS idx_sms_sync_state_user_updated
    ON public.sms_sync_state (user_id, updated_at DESC);

-- 6. SYNC QUEUE TABLE (FRD Section 17 & 18.1)
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

CREATE INDEX IF NOT EXISTS idx_sync_queue_user_status
    ON public.sync_queue (user_id, sync_status, created_at DESC);

-- 7. AUTOMATION LOGS TABLE (FRD Section 18.1 & 26)
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

CREATE INDEX IF NOT EXISTS idx_automation_logs_user_date
    ON public.automation_logs (user_id, created_at DESC);

-- 8. STRICT PER-USER RLS ON ALL NEW TABLES
ALTER TABLE public.transaction_rules ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sms_sync_state ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sync_queue ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.automation_logs ENABLE ROW LEVEL SECURITY;

-- transaction_rules
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

-- devices
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

-- sms_sync_state
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

-- sync_queue
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

-- automation_logs
DROP POLICY IF EXISTS "Users can select own automation logs" ON public.automation_logs;
DROP POLICY IF EXISTS "Users can insert own automation logs" ON public.automation_logs;
DROP POLICY IF EXISTS "Users can delete own automation logs" ON public.automation_logs;

CREATE POLICY "Users can select own automation logs" ON public.automation_logs
    FOR SELECT TO authenticated USING (auth.uid() = user_id);
CREATE POLICY "Users can insert own automation logs" ON public.automation_logs
    FOR INSERT TO authenticated WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can delete own automation logs" ON public.automation_logs
    FOR DELETE TO authenticated USING (auth.uid() = user_id);

-- 9. UPGRADE device_sync_ingest_transaction RPC (Backward-compatible 17 params + 3 optional DEFAULT params)
DROP FUNCTION IF EXISTS public.device_sync_ingest_transaction(
    TEXT, TEXT, TEXT, NUMERIC, transaction_direction, transaction_type,
    TEXT, TEXT, account_type, TEXT, TEXT, TEXT, TEXT, NUMERIC, BOOLEAN, TIMESTAMPTZ, UUID
);

DROP FUNCTION IF EXISTS public.device_sync_ingest_transaction(
    TEXT, TEXT, TEXT, NUMERIC, transaction_direction, transaction_type,
    TEXT, TEXT, account_type, TEXT, TEXT, TEXT, TEXT, NUMERIC, BOOLEAN, TIMESTAMPTZ, UUID,
    VARCHAR, VARCHAR, VARCHAR
);

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

    -- 1. Duplicate check by fingerprint
    SELECT id INTO v_dup_id
    FROM public.transactions
    WHERE user_id = v_user_id AND sms_fingerprint = p_sms_fingerprint
    LIMIT 1;

    -- 2. Secondary duplicate check by reference_id + amount + direction
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

    -- 3. Upsert account safely for out-of-order SMS with balance_source tracking
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

    -- 4. Insert transaction (privacy-first: never stores raw SMS text)
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

    -- 5. Audit & sync state updates
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

REVOKE ALL ON FUNCTION public.device_sync_ingest_transaction(
    TEXT, TEXT, TEXT, NUMERIC, transaction_direction, transaction_type,
    TEXT, TEXT, account_type, TEXT, TEXT, TEXT, TEXT, NUMERIC, BOOLEAN, TIMESTAMPTZ, UUID,
    VARCHAR, VARCHAR, VARCHAR
) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.device_sync_ingest_transaction(
    TEXT, TEXT, TEXT, NUMERIC, transaction_direction, transaction_type,
    TEXT, TEXT, account_type, TEXT, TEXT, TEXT, TEXT, NUMERIC, BOOLEAN, TIMESTAMPTZ, UUID,
    VARCHAR, VARCHAR, VARCHAR
) TO anon, authenticated, service_role;

-- 10. IDEMPOTENT FRD 7.3 CATEGORIES SEED
INSERT INTO public.categories (name, icon, color, is_income) VALUES
('Salary', 'Briefcase', '#16A34A', TRUE),
('Business Income', 'Building2', '#10B981', TRUE),
('Interest', 'Percent', '#84CC16', TRUE),
('Cashback', 'Gift', '#06B6D4', TRUE),
('Refund', 'RotateCcw', '#6366F1', TRUE),
('Other Income', 'Wallet', '#059669', TRUE),
('Food & Dining', 'Utensils', '#F59E0B', FALSE),
('Shopping', 'ShoppingCart', '#EC4899', FALSE),
('Travel & Transport', 'Car', '#3B82F6', FALSE),
('Bills & Utilities', 'Zap', '#8B5CF6', FALSE),
('Entertainment', 'Film', '#6366F1', FALSE),
('Healthcare', 'HeartPulse', '#EF4444', FALSE),
('Education', 'GraduationCap', '#0EA5E9', FALSE),
('EMI & Loan Payments', 'Landmark', '#F97316', FALSE),
('Subscriptions', 'Repeat', '#A855F7', FALSE),
('Lifestyle', 'Sparkles', '#EC4899', FALSE),
('Investments', 'TrendingUp', '#06B6D4', FALSE),
('Groceries', 'ShoppingBag', '#10B981', FALSE),
('Other Expenses', 'CreditCard', '#94A3B8', FALSE)
ON CONFLICT (name) DO NOTHING;
