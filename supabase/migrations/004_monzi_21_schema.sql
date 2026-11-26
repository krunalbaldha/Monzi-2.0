-- ========================================================
-- MONZI 2.1 MIGRATION 004: COMPLETE PERSONAL FINANCE ENGINE
-- Idempotent migration — preserves all existing Monzi 2.0 data.
-- ========================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. EXTEND public.accounts FOR CREDIT CARDS & RECONCILIATION (FRD M21-04 & M21-07)
ALTER TABLE public.accounts
    ADD COLUMN IF NOT EXISTS account_name VARCHAR(80),
    ADD COLUMN IF NOT EXISTS reconciliation_status VARCHAR(32) NOT NULL DEFAULT 'CALCULATED_ONLY',
    ADD COLUMN IF NOT EXISTS credit_limit NUMERIC(14, 2),
    ADD COLUMN IF NOT EXISTS statement_day INTEGER,
    ADD COLUMN IF NOT EXISTS due_day INTEGER,
    ADD COLUMN IF NOT EXISTS billing_cycle VARCHAR(32);

-- 2. EXTEND public.transactions FOR CONFIDENCE, DUPLICATES, TRANSFERS & REFUNDS (FRD M21-02..06)
ALTER TABLE public.transactions
    ADD COLUMN IF NOT EXISTS recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS confidence_score NUMERIC(4, 3) NOT NULL DEFAULT 1.000,
    ADD COLUMN IF NOT EXISTS duplicate_status VARCHAR(24) NOT NULL DEFAULT 'UNIQUE',
    ADD COLUMN IF NOT EXISTS transfer_status VARCHAR(24),
    ADD COLUMN IF NOT EXISTS refund_status VARCHAR(24),
    ADD COLUMN IF NOT EXISTS reconciled_at TIMESTAMPTZ;

-- 3. ACCOUNT BALANCE SNAPSHOTS (FRD M21-04)
CREATE TABLE IF NOT EXISTS public.account_balance_snapshots (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES public.accounts(id) ON DELETE CASCADE,
    reported_balance NUMERIC(14, 2) NOT NULL,
    calculated_balance_at_snapshot NUMERIC(14, 2),
    discrepancy_amount NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    source_transaction_id UUID REFERENCES public.transactions(id) ON DELETE SET NULL,
    snapshot_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_balance_snapshots_user_acc
    ON public.account_balance_snapshots (user_id, account_id, snapshot_at DESC);

-- 4. ACCOUNT BALANCE ADJUSTMENTS (FRD M21-04)
CREATE TABLE IF NOT EXISTS public.account_balance_adjustments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES public.accounts(id) ON DELETE CASCADE,
    adjustment_amount NUMERIC(14, 2) NOT NULL,
    reason TEXT NOT NULL,
    approved BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_balance_adjustments_user_acc
    ON public.account_balance_adjustments (user_id, account_id, created_at DESC);

-- 5. TRANSFERS TABLE (FRD M21-05)
CREATE TABLE IF NOT EXISTS public.transfers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    source_account_id UUID REFERENCES public.accounts(id) ON DELETE SET NULL,
    destination_account_id UUID REFERENCES public.accounts(id) ON DELETE SET NULL,
    debit_transaction_id UUID REFERENCES public.transactions(id) ON DELETE CASCADE,
    credit_transaction_id UUID REFERENCES public.transactions(id) ON DELETE SET NULL,
    amount NUMERIC(14, 2) NOT NULL,
    transfer_type VARCHAR(32) NOT NULL DEFAULT 'BANK_TO_BANK',
    status VARCHAR(24) NOT NULL DEFAULT 'MATCHED',
    reference_id VARCHAR(100),
    transfer_date TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_transfers_user_date
    ON public.transfers (user_id, transfer_date DESC);

-- 6. REFUNDS TABLE (FRD M21-06)
CREATE TABLE IF NOT EXISTS public.refunds (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    refund_transaction_id UUID NOT NULL REFERENCES public.transactions(id) ON DELETE CASCADE,
    original_transaction_id UUID REFERENCES public.transactions(id) ON DELETE SET NULL,
    refund_type VARCHAR(24) NOT NULL DEFAULT 'UNMATCHED',
    refund_amount NUMERIC(14, 2) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'REFUND_DETECTED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_refunds_user_tx
    ON public.refunds (user_id, refund_transaction_id);

-- 7. CREDIT CARD STATEMENTS (FRD M21-07)
CREATE TABLE IF NOT EXISTS public.credit_card_statements (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES public.accounts(id) ON DELETE CASCADE,
    statement_period VARCHAR(32) NOT NULL,
    statement_date DATE NOT NULL,
    total_due NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    minimum_due NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    due_date DATE,
    paid_amount NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    payment_status VARCHAR(24) NOT NULL DEFAULT 'UPCOMING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_cc_statements_user_acc
    ON public.credit_card_statements (user_id, account_id, statement_date DESC);

-- 8. CREDIT CARD PAYMENTS (FRD M21-07)
CREATE TABLE IF NOT EXISTS public.credit_card_payments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    card_account_id UUID NOT NULL REFERENCES public.accounts(id) ON DELETE CASCADE,
    source_account_id UUID REFERENCES public.accounts(id) ON DELETE SET NULL,
    statement_id UUID REFERENCES public.credit_card_statements(id) ON DELETE SET NULL,
    transaction_id UUID REFERENCES public.transactions(id) ON DELETE SET NULL,
    amount NUMERIC(14, 2) NOT NULL,
    payment_date TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_cc_payments_user_card
    ON public.credit_card_payments (user_id, card_account_id, payment_date DESC);

-- 9. BUDGETS TABLE (FRD M21-08)
CREATE TABLE IF NOT EXISTS public.budgets (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    name VARCHAR(80) NOT NULL,
    category_id UUID REFERENCES public.categories(id) ON DELETE SET NULL,
    monthly_limit NUMERIC(14, 2) NOT NULL,
    start_month VARCHAR(7) NOT NULL,
    alert_threshold INTEGER NOT NULL DEFAULT 80,
    carry_forward BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_budgets_user_status
    ON public.budgets (user_id, status);

-- 10. BUDGET ALERTS TABLE (FRD M21-08)
CREATE TABLE IF NOT EXISTS public.budget_alerts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    budget_id UUID NOT NULL REFERENCES public.budgets(id) ON DELETE CASCADE,
    month_key VARCHAR(7) NOT NULL,
    threshold_pct INTEGER NOT NULL,
    usage_pct NUMERIC(6, 2) NOT NULL,
    spent_amount NUMERIC(14, 2) NOT NULL,
    message TEXT NOT NULL,
    acknowledged BOOLEAN NOT NULL DEFAULT FALSE,
    triggered_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(budget_id, month_key, threshold_pct)
);

CREATE INDEX IF NOT EXISTS idx_budget_alerts_user_date
    ON public.budget_alerts (user_id, triggered_at DESC);

-- 11. SMS IMPORT RUNS (FRD M21-01)
CREATE TABLE IF NOT EXISTS public.sms_import_runs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    device_id VARCHAR(120) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'NOT_STARTED',
    total_examined INTEGER NOT NULL DEFAULT 0,
    financial_detected INTEGER NOT NULL DEFAULT 0,
    imported_count INTEGER NOT NULL DEFAULT 0,
    duplicate_skipped INTEGER NOT NULL DEFAULT 0,
    ignored_count INTEGER NOT NULL DEFAULT 0,
    review_count INTEGER NOT NULL DEFAULT 0,
    failure_count INTEGER NOT NULL DEFAULT 0,
    checkpoint_ts BIGINT NOT NULL DEFAULT 0,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_sms_import_runs_user_date
    ON public.sms_import_runs (user_id, started_at DESC);

-- 12. SMS PARSER FAILURES (FRD M21-01 - privacy-safe, NO raw SMS text)
CREATE TABLE IF NOT EXISTS public.sms_parser_failures (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    device_id VARCHAR(120),
    sender VARCHAR(60),
    error_reason TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sms_parser_failures_user_date
    ON public.sms_parser_failures (user_id, occurred_at DESC);

-- 13. ROW LEVEL SECURITY ON ALL MONZI 2.1 TABLES
ALTER TABLE public.account_balance_snapshots ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.account_balance_adjustments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.transfers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.refunds ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.credit_card_statements ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.credit_card_payments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.budgets ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.budget_alerts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sms_import_runs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sms_parser_failures ENABLE ROW LEVEL SECURITY;

DO $$
DECLARE
    tbl TEXT;
BEGIN
    FOREACH tbl IN ARRAY ARRAY[
        'account_balance_snapshots',
        'account_balance_adjustments',
        'transfers',
        'refunds',
        'credit_card_statements',
        'credit_card_payments',
        'budgets',
        'budget_alerts',
        'sms_import_runs',
        'sms_parser_failures'
    ]
    LOOP
        EXECUTE format('DROP POLICY IF EXISTS "Users select own %1$s" ON public.%1$s', tbl);
        EXECUTE format('DROP POLICY IF EXISTS "Users insert own %1$s" ON public.%1$s', tbl);
        EXECUTE format('DROP POLICY IF EXISTS "Users update own %1$s" ON public.%1$s', tbl);
        EXECUTE format('DROP POLICY IF EXISTS "Users delete own %1$s" ON public.%1$s', tbl);

        EXECUTE format('CREATE POLICY "Users select own %1$s" ON public.%1$s FOR SELECT TO authenticated USING (auth.uid() = user_id)', tbl);
        EXECUTE format('CREATE POLICY "Users insert own %1$s" ON public.%1$s FOR INSERT TO authenticated WITH CHECK (auth.uid() = user_id)', tbl);
        EXECUTE format('CREATE POLICY "Users update own %1$s" ON public.%1$s FOR UPDATE TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id)', tbl);
        EXECUTE format('CREATE POLICY "Users delete own %1$s" ON public.%1$s FOR DELETE TO authenticated USING (auth.uid() = user_id)', tbl);
    END LOOP;
END $$;
