export type TransactionDirection = 'CREDIT' | 'DEBIT';

export type TransactionType =
  | 'EXPENSE'
  | 'INCOME'
  | 'TRANSFER'
  | 'REFUND'
  | 'REVERSAL'
  | 'INVESTMENT'
  | 'CC_BILL_PAYMENT'
  | 'CASH_WITHDRAWAL'
  | 'UNCLASSIFIED';

export type AccountType = 'SAVINGS' | 'CURRENT' | 'CREDIT_CARD' | 'WALLET';

export type SmsProcessingStatus =
  | 'PROCESSED'
  | 'DUPLICATE'
  | 'IGNORED'
  | 'FAILED'
  | 'UNCLASSIFIED';

export type BalanceSource = 'BANK_REPORTED' | 'CALCULATED' | 'ESTIMATED' | 'UNAVAILABLE';

export type TransactionSource =
  | 'HISTORICAL_SMS'
  | 'INCOMING_SMS'
  | 'REFUND_REVERSAL'
  | 'MANUAL'
  | 'INTEGRATION';

export type TransactionStatus = 'COMPLETED' | 'FAILED' | 'PENDING' | 'REVERSED';

export interface Account {
  id: string;
  user_id?: string;
  bank_name: string;
  account_number_mask: string;
  account_type: AccountType;
  initial_balance: number;
  opening_balance?: number;
  current_balance: number;
  balance_source?: BalanceSource;
  account_status?: string;
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
  user_id?: string;
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
  is_self_transfer?: boolean;
  is_refund?: boolean;
  is_excluded_from_totals: boolean;
  duplicate_status?: string;
  linked_transaction_id?: string;
  notes?: string;
  currency?: string;
  transaction_source?: TransactionSource;
  transaction_status?: TransactionStatus;
  transaction_date: string;
  created_at: string;
  updated_at?: string;

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

export interface ParsedTransaction {
  amount: number;
  direction: TransactionDirection;
  bank_name: string;
  account_mask: string;
  merchant?: string;
  reference_id?: string;
  payment_mode?: string;
  parsed_available_balance?: number;
  transaction_date?: string;
  transaction_type:
    | 'EXPENSE'
    | 'INCOME'
    | 'TRANSFER'
    | 'REFUND'
    | 'CC_BILL_PAYMENT'
    | 'UNCLASSIFIED';
  // Extended fields
  transaction_category?: string;
  is_duplicate?: boolean;
  is_failed?: boolean;
  is_self_transfer?: boolean;
  is_cashback?: boolean;
  is_refund?: boolean;
}

export interface SmsProcessingLog {
  id: string;
  user_id?: string;
  device_id?: string | null;
  sender?: string | null;
  status: SmsProcessingStatus;
  reason?: string | null;
  extracted_type?: string | null;
  extracted_amount?: number | null;
  extracted_bank?: string | null;
  extracted_date?: string | null;
  reference_id?: string | null;
  sms_fingerprint?: string | null;
  created_at: string;
}

export interface FinancialSummary {
  total_balance: number;
  total_income?: number;
  total_expenses?: number;
  monthly_income: number;
  monthly_expense: number;
  net_savings: number;
  savings_rate: number;
  income_change_pct: number;
  expense_change_pct: number;
  refunds_total?: number;
  cashback_total?: number;
  total_tx_count?: number;
}

export interface DailyFinancialSummary {
  date: string;
  total_income: number;
  total_expenses: number;
  net_savings: number;
  transaction_count: number;
  top_category: string;
  top_merchant: string;
}

export interface IncomingSmsPayload {
  device_id?: string;
  sync_secret?: string;
  device_sync_token?: string;
  transactions: Array<{
    client_tx_id?: string;
    sms_fingerprint?: string;
    transaction_date?: string;
    sender?: string;
    amount?: number;
    direction?: TransactionDirection;
    transaction_type?: TransactionType;
    transaction_category?: string;
    bank_name?: string;
    account_mask?: string;
    merchant?: string;
    reference_id?: string;
    payment_mode?: string;
    parsed_available_balance?: number;
    is_self_transfer?: boolean;
    is_refund?: boolean;
    transaction_source?: TransactionSource;
    transaction_status?: TransactionStatus;
    currency?: string;
    raw_body?: string;
  }>;
}

export interface TransactionRule {
  id: string;
  user_id?: string;
  matching_rule: string;
  category_id: string;
  priority: number;
  enabled: boolean;
  categories?: Category;
  created_at: string;
}

export interface DeviceRecord {
  id: string;
  device_identifier: string;
  platform: string;
  app_version?: string;
  last_seen_at: string;
  created_at: string;
}

export interface SetupStatusPayload {
  account_created: boolean;
  email_verified: boolean;
  device_registered: boolean;
  sms_access_granted: boolean;
  historical_sync_status: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED';
  live_monitoring_active: boolean;
  cloud_sync_active: boolean;
  last_processed_at: string | null;
  last_sync_at: string | null;
  device_count: number;
}
