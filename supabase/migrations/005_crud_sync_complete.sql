-- ========================================================
-- MONZI 2.1+ MIGRATION 005: COMPLETE CRUD, ENUMS & CONSTRAINTS
-- Idempotent migration — safe to run multiple times in Supabase SQL Editor.
-- ========================================================

-- 1. Extend transaction_type enum with all BankSmsParser & UI transaction types
ALTER TYPE transaction_type ADD VALUE IF NOT EXISTS 'REVERSAL';
ALTER TYPE transaction_type ADD VALUE IF NOT EXISTS 'CC_PURCHASE';
ALTER TYPE transaction_type ADD VALUE IF NOT EXISTS 'INTEREST';
ALTER TYPE transaction_type ADD VALUE IF NOT EXISTS 'BANK_CHARGES';
ALTER TYPE transaction_type ADD VALUE IF NOT EXISTS 'ATM_WITHDRAWAL';
ALTER TYPE transaction_type ADD VALUE IF NOT EXISTS 'CASH_DEPOSIT';
ALTER TYPE transaction_type ADD VALUE IF NOT EXISTS 'OTHER';

-- 2. Ensure unique constraint on credit_card_statements (account_id, statement_period)
DO $$ BEGIN
    ALTER TABLE public.credit_card_statements
        ADD CONSTRAINT uq_cc_stmt_account_period UNIQUE (account_id, statement_period);
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- 3. Ensure categories allow authenticated update/delete if needed, while protecting defaults on app layer
DROP POLICY IF EXISTS "Authenticated users can update categories" ON public.categories;
CREATE POLICY "Authenticated users can update categories" ON public.categories
    FOR UPDATE TO authenticated USING (auth.uid() IS NOT NULL) WITH CHECK (auth.uid() IS NOT NULL);
