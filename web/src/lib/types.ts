export type TransactionDirection = 'CREDIT' | 'DEBIT';

export type TransactionType =
  | 'EXPENSE'
  | 'INCOME'
  | 'TRANSFER'
  | 'REFUND'
  | 'INVESTMENT'
  | 'CC_BILL_PAYMENT'
  | 'CASH_WITHDRAWAL'
  | 'UNCLASSIFIED';

export type AccountType = 'SAVINGS' | 'CURRENT' | 'CREDIT_CARD' | 'WALLET';

export interface Account {
  id: string;
  bank_name: string;
  account_number_mask: string;
  account_type: AccountType;
  initial_balance: number;
  current_balance: number;
  last_synced_at?: string;
  created_at: string;
  updated_at: string;
}

export interface Category {
  id: string;
  name: string;
  icon?: string;
  color: string;
  is_income: boolean;
  created_at?: string;
}

export interface Transaction {
  id: string;
  account_id?: string;
  category_id?: string;
  amount: number;
  direction: TransactionDirection;
  transaction_type: TransactionType;
  merchant?: string;
  payment_mode?: string;
  reference_id?: string;
  sms_fingerprint: string;
  parsed_available_balance?: number;
  is_excluded_from_totals: boolean;
  linked_transaction_id?: string;
  notes?: string;
  transaction_date: string;
  created_at: string;
  
  // Joined relation fields
  accounts?: {
    bank_name: string;
    account_number_mask: string;
    account_type: AccountType;
  };
  categories?: {
    name: string;
    icon?: string;
    color: string;
  };
}

export interface FinancialSummary {
  total_balance: number;
  monthly_income: number;
  monthly_expense: number;
  net_savings: number;
  savings_rate: number;
  income_change_pct: number;
  expense_change_pct: number;
}

export interface IncomingSmsPayload {
  device_id: string;
  sync_secret: string;
  transactions: Array<{
    client_tx_id?: string;
    sms_fingerprint?: string;
    transaction_date: string;
    amount: number;
    direction: TransactionDirection;
    bank_name: string;
    account_mask: string;
    merchant?: string;
    reference_id?: string;
    payment_mode?: string;
    parsed_available_balance?: number;
    raw_body?: string;
  }>;
}
