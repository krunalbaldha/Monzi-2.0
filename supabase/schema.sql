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

-- 2. ACCOUNTS TABLE (Your Banks & Cards)
CREATE TABLE IF NOT EXISTS public.accounts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    bank_name VARCHAR(50) NOT NULL,            -- e.g. 'HDFC Bank', 'ICICI Bank'
    account_number_mask VARCHAR(10) NOT NULL,   -- e.g. 'XX1234'
    account_type account_type NOT NULL DEFAULT 'SAVINGS',
    initial_balance NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    current_balance NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    last_synced_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_bank_account UNIQUE (bank_name, account_number_mask)
);

-- 3. CATEGORIES TABLE
CREATE TABLE IF NOT EXISTS public.categories (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(50) UNIQUE NOT NULL,          -- e.g. 'Food & Dining', 'Groceries', 'Salary'
    icon VARCHAR(30),                          -- Lucide icon name
    color VARCHAR(20) DEFAULT '#2563EB',       -- Hex color for UI badges
    is_income BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 4. TRANSACTIONS TABLE
CREATE TABLE IF NOT EXISTS public.transactions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    account_id UUID REFERENCES public.accounts(id) ON DELETE CASCADE,
    category_id UUID REFERENCES public.categories(id) ON DELETE SET NULL,
    
    amount NUMERIC(14, 2) NOT NULL,
    direction transaction_direction NOT NULL,
    transaction_type transaction_type NOT NULL DEFAULT 'UNCLASSIFIED',
    
    merchant VARCHAR(100),                      -- e.g. 'Swiggy', 'Amazon India', 'Zomato'
    payment_mode VARCHAR(20) DEFAULT 'UPI',     -- 'UPI', 'IMPS', 'POS', 'ATM', 'NETBANKING'
    reference_id VARCHAR(100),                  -- Bank UTR / RRN
    
    -- Crucial for deduplication and ground truth from SMS
    sms_fingerprint VARCHAR(64) UNIQUE NOT NULL,-- SHA256 of (timestamp + amount + mask + ref)
    parsed_available_balance NUMERIC(14, 2),    -- The "Avl Bal" stated in the SMS
    
    -- Accounting flags
    is_excluded_from_totals BOOLEAN NOT NULL DEFAULT FALSE, -- True for Transfers & CC bill payments
    linked_transaction_id UUID REFERENCES public.transactions(id) ON DELETE SET NULL, -- For refunds/transfers
    
    notes TEXT,
    transaction_date TIMESTAMPTZ NOT NULL,      -- Original SMS date & time
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 5. PERFORMANCE INDEXES
CREATE INDEX IF NOT EXISTS idx_transactions_date ON public.transactions (transaction_date DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_account ON public.transactions (account_id);
CREATE INDEX IF NOT EXISTS idx_transactions_type ON public.transactions (transaction_type);
CREATE INDEX IF NOT EXISTS idx_transactions_category ON public.transactions (category_id);

-- 6. SYSTEM SETTINGS TABLE (Single user personal setup)
CREATE TABLE IF NOT EXISTS public.app_settings (
    key VARCHAR(50) PRIMARY KEY,
    value JSONB NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 7. ENABLE REALTIME FOR TRANSACTIONS
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


-- 8. ROW LEVEL SECURITY POLICIES (PERSONAL USE)
ALTER TABLE public.accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.app_settings ENABLE ROW LEVEL SECURITY;

DO $$ BEGIN
    CREATE POLICY "Allow all on accounts" ON public.accounts FOR ALL USING (true) WITH CHECK (true);
EXCEPTION WHEN duplicate_object THEN null; END $$;

DO $$ BEGIN
    CREATE POLICY "Allow all on categories" ON public.categories FOR ALL USING (true) WITH CHECK (true);
EXCEPTION WHEN duplicate_object THEN null; END $$;

DO $$ BEGIN
    CREATE POLICY "Allow all on transactions" ON public.transactions FOR ALL USING (true) WITH CHECK (true);
EXCEPTION WHEN duplicate_object THEN null; END $$;

DO $$ BEGIN
    CREATE POLICY "Allow all on app_settings" ON public.app_settings FOR ALL USING (true) WITH CHECK (true);
EXCEPTION WHEN duplicate_object THEN null; END $$;
