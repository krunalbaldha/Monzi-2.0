'use client';

import React, { useState, useEffect, useCallback } from 'react';
import Link from 'next/link';
import { Header } from '@/components/layout/Header';
import { SummaryCards, ExtendedFinancialSummary } from '@/components/dashboard/SummaryCards';
import { CashFlowChart } from '@/components/dashboard/CashFlowChart';
import { CategoryBreakdown } from '@/components/dashboard/CategoryBreakdown';
import { RecentTransactions } from '@/components/dashboard/RecentTransactions';
import { GlassCard } from '@/components/ui/GlassCard';
import { EmptyState } from '@/components/ui/EmptyState';
import { Skeleton } from '@/components/ui/Skeleton';
import { Transaction, Account, AccountType } from '@/lib/types';
import {
  CreditCard,
  Plus,
  ArrowRight,
  RefreshCw,
  BarChart3,
  Landmark,
  AlertCircle,
  Sparkles,
  TrendingUp,
  TrendingDown,
  MessageSquare,
  Database,
  ArrowUpRight,
  ArrowDownRight,
} from 'lucide-react';
import { AddAccountModal } from '@/components/dashboard/AddAccountModal';
import { AddTransactionModal } from '@/components/dashboard/AddTransactionModal';
import { useSms } from '@/components/sms/SmsProvider';
import { useAuth } from '@/components/auth/AuthProvider';

interface TodayStats {
  income: number;
  expenses: number;
  net: number;
  transaction_count: number;
  top_category: string | null;
  yesterday_expenses: number;
  expense_delta_pct: number;
}

interface BankSummaryItem {
  bank_name: string;
  account_count: number;
  balance: number;
  total_credit: number;
  total_debit: number;
  tx_count: number;
}

const emptySummary: ExtendedFinancialSummary = {
  total_balance: 0,
  total_income: 0,
  total_expenses: 0,
  monthly_income: 0,
  monthly_expense: 0,
  net_savings: 0,
  savings_rate: 0,
  income_change_pct: 0,
  expense_change_pct: 0,
};

const emptyToday: TodayStats = {
  income: 0,
  expenses: 0,
  net: 0,
  transaction_count: 0,
  top_category: null,
  yesterday_expenses: 0,
  expense_delta_pct: 0,
};

function getBankAccent(bankName: string): {
  topBar: string;
  iconBg: string;
} {
  const name = bankName.toLowerCase();
  if (name.includes('hdfc')) {
    return {
      topBar: 'from-blue-600 via-indigo-600 to-rose-500',
      iconBg: 'bg-blue-50 text-blue-600',
    };
  }
  if (name.includes('sbi') || name.includes('state bank')) {
    return {
      topBar: 'from-sky-500 to-blue-700',
      iconBg: 'bg-sky-50 text-sky-600',
    };
  }
  if (name.includes('icici')) {
    return {
      topBar: 'from-orange-500 to-rose-600',
      iconBg: 'bg-orange-50 text-orange-600',
    };
  }
  if (name.includes('axis')) {
    return {
      topBar: 'from-rose-600 to-fuchsia-700',
      iconBg: 'bg-rose-50 text-rose-600',
    };
  }
  if (name.includes('kotak')) {
    return {
      topBar: 'from-red-600 to-blue-600',
      iconBg: 'bg-red-50 text-red-600',
    };
  }
  return {
    topBar: 'from-blue-600 to-indigo-600',
    iconBg: 'bg-indigo-50 text-indigo-600',
  };
}

function formatAccountType(type: AccountType): string {
  switch (type) {
    case 'CREDIT_CARD':
      return 'Credit Card';
    case 'SAVINGS':
      return 'Savings';
    case 'CURRENT':
      return 'Current';
    case 'WALLET':
      return 'Wallet';
    default:
      return type;
  }
}

export default function DashboardPage() {
  const [summary, setSummary] = useState<ExtendedFinancialSummary>(emptySummary);
  const [today, setToday] = useState<TodayStats>(emptyToday);
  const [bankSummary, setBankSummary] = useState<BankSummaryItem[]>([]);
  const [cashFlow, setCashFlow] = useState<
    Array<{ month: string; income: number; expense: number }>
  >([]);
  const [categories, setCategories] = useState<
    Array<{ name: string; value: number; color: string }>
  >([]);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [loading, setLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [migrationPending, setMigrationPending] = useState(false);
  const [isAddAccountOpen, setIsAddAccountOpen] = useState(false);
  const [isAddTxOpen, setIsAddTxOpen] = useState(false);

  const { openSyncModal } = useSms();
  const { user, loading: authLoading } = useAuth();

  const fetchData = useCallback(async () => {
    setIsRefreshing(true);
    setError(null);
    try {
      const [statsRes, txRes, accRes] = await Promise.all([
        fetch('/api/stats'),
        fetch('/api/transactions?limit=10'),
        fetch('/api/accounts'),
      ]);

      if (statsRes.status === 401 || txRes.status === 401 || accRes.status === 401) {
        setLoading(false);
        setIsRefreshing(false);
        return;
      }

      if (!statsRes.ok && !txRes.ok && !accRes.ok) {
        throw new Error('Unable to reach database APIs');
      }

      let needsMigration = false;

      if (statsRes.ok) {
        const statsData = await statsRes.json();
        if (statsData.schema_migration_required) needsMigration = true;
        if (statsData.summary) setSummary(statsData.summary);
        if (statsData.today) setToday(statsData.today);
        setBankSummary(statsData.bankSummary || []);
        setCashFlow(statsData.cashFlow || []);
        setCategories(statsData.categoryBreakdown || []);
      }

      if (txRes.ok) {
        const txData = await txRes.json();
        if (txData.schema_migration_required) needsMigration = true;
        setTransactions(txData.transactions || []);
      }

      if (accRes.ok) {
        const accData = await accRes.json();
        if (accData.schema_migration_required) needsMigration = true;
        setAccounts(accData.accounts || []);
      }

      setMigrationPending(needsMigration);
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : 'Failed to load dashboard data';
      console.error('Failed to fetch dashboard data', e);
      setError(msg);
    } finally {
      setLoading(false);
      setIsRefreshing(false);
    }
  }, []);

  useEffect(() => {
    if (!authLoading && user) {
      fetchData();
    } else if (!authLoading && !user) {
      setLoading(false);
    }
  }, [authLoading, user, fetchData]);

  // Listen for real-time Android / Modal SMS sync and manual refresh events
  useEffect(() => {
    const handleSyncEvent = () => {
      fetchData();
    };
    window.addEventListener('monzi:sms-synced', handleSyncEvent);
    window.addEventListener('monzi:refresh', handleSyncEvent);
    return () => {
      window.removeEventListener('monzi:sms-synced', handleSyncEvent);
      window.removeEventListener('monzi:refresh', handleSyncEvent);
    };
  }, [fetchData]);

  const formatInr = (val: number) =>
    new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 0,
    }).format(val);

  return (
    <div className="flex flex-col min-h-screen">
      <Header onRefresh={fetchData} isRefreshing={isRefreshing} />

      <div className="p-4 sm:p-6 md:p-8 space-y-5 md:space-y-7 max-w-7xl mx-auto w-full">
        {/* Schema Migration Notice */}
        {migrationPending && (
          <GlassCard className="p-4 border-amber-200/80 bg-amber-50/80 flex items-start gap-3">
            <div className="w-9 h-9 rounded-xl bg-amber-100 text-amber-700 flex items-center justify-center shrink-0">
              <Database className="w-5 h-5" />
            </div>
            <div>
              <p className="text-sm font-bold text-amber-900">
                Database Migration Required
              </p>
              <p className="text-xs text-amber-800 mt-0.5">
                Run <code className="font-mono bg-amber-100 px-1.5 py-0.5 rounded">supabase/migrations/002_auth_rls_and_logs.sql</code> in the Supabase SQL Editor to enable multi-user data isolation and SMS processing logs.
              </p>
            </div>
          </GlassCard>
        )}

        {/* Error Banner */}
        {error && (
          <GlassCard className="p-4 border-rose-200/80 bg-rose-50/70 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
            <div className="flex items-center gap-3">
              <div className="w-9 h-9 rounded-xl bg-rose-100 text-rose-600 flex items-center justify-center shrink-0">
                <AlertCircle className="w-5 h-5" />
              </div>
              <div>
                <p className="text-sm font-bold text-rose-900">Sync Error</p>
                <p className="text-xs text-rose-700">{error}</p>
              </div>
            </div>
            <button
              onClick={fetchData}
              className="btn-secondary !min-h-[44px] text-xs px-4 shrink-0"
            >
              <RefreshCw className="w-3.5 h-3.5" />
              Retry
            </button>
          </GlassCard>
        )}

        {/* ── TODAY'S AUTOMATED FINANCIAL ANALYSIS ────────────────────────── */}
        <section>
          <GlassCard className="p-5 sm:p-6 bg-gradient-to-br from-white/85 via-blue-50/40 to-indigo-50/40 border-blue-100/70">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mb-4">
              <div className="flex items-center gap-2.5">
                <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-blue-600 to-indigo-600 text-white flex items-center justify-center shadow-sm shadow-blue-500/25 shrink-0">
                  <Sparkles className="w-4 h-4" />
                </div>
                <div>
                  <h2 className="text-sm sm:text-base font-bold text-slate-900 tracking-tight">
                    Today&apos;s Automated Financial Analysis
                  </h2>
                  <p className="text-[11px] sm:text-xs text-slate-500 font-medium">
                    Real-time summary from automated SMS &amp; ledger activity today
                  </p>
                </div>
              </div>

              <div className="flex items-center gap-2">
                {today.yesterday_expenses > 0 && (
                  <span
                    className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[11px] font-bold border ${
                      today.expense_delta_pct > 0
                        ? 'bg-rose-50 text-rose-700 border-rose-200'
                        : 'bg-emerald-50 text-emerald-700 border-emerald-200'
                    }`}
                  >
                    {today.expense_delta_pct > 0 ? (
                      <ArrowUpRight className="w-3.5 h-3.5" />
                    ) : (
                      <ArrowDownRight className="w-3.5 h-3.5" />
                    )}
                    {today.expense_delta_pct > 0 ? '+' : ''}
                    {today.expense_delta_pct}% vs yesterday
                  </span>
                )}
                <button
                  onClick={openSyncModal}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-bold bg-emerald-50 text-emerald-700 border border-emerald-200 hover:bg-emerald-100 transition-colors"
                >
                  <MessageSquare className="w-3.5 h-3.5" />
                  <span>Sync SMS</span>
                </button>
              </div>
            </div>

            {loading ? (
              <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3">
                {[0, 1, 2, 3, 4].map((i) => (
                  <div key={i} className="p-3.5 rounded-2xl bg-white/75 border border-white/80">
                    <Skeleton className="h-3 w-20 rounded-full mb-2" />
                    <Skeleton className="h-6 w-24 rounded-lg" />
                  </div>
                ))}
              </div>
            ) : (
              <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3">
                <div className="p-3.5 rounded-2xl bg-white/80 border border-slate-200/60">
                  <p className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                    Today&apos;s Income
                  </p>
                  <p className="text-lg font-bold font-mono text-emerald-600 mt-1">
                    {formatInr(today.income)}
                  </p>
                </div>

                <div className="p-3.5 rounded-2xl bg-white/80 border border-slate-200/60">
                  <p className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                    Today&apos;s Expenses
                  </p>
                  <p className="text-lg font-bold font-mono text-rose-500 mt-1">
                    {formatInr(today.expenses)}
                  </p>
                </div>

                <div className="p-3.5 rounded-2xl bg-white/80 border border-slate-200/60">
                  <p className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                    Today&apos;s Net
                  </p>
                  <p
                    className={`text-lg font-bold font-mono mt-1 ${
                      today.net >= 0 ? 'text-indigo-600' : 'text-rose-600'
                    }`}
                  >
                    {today.net >= 0 ? '+' : ''}
                    {formatInr(today.net)}
                  </p>
                </div>

                <div className="p-3.5 rounded-2xl bg-white/80 border border-slate-200/60">
                  <p className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                    Transactions
                  </p>
                  <p className="text-lg font-bold font-mono text-slate-900 mt-1">
                    {today.transaction_count}
                  </p>
                </div>

                <div className="p-3.5 rounded-2xl bg-white/80 border border-slate-200/60 col-span-2 sm:col-span-1">
                  <p className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                    Top Spend Category
                  </p>
                  <p className="text-sm sm:text-base font-bold text-slate-900 mt-1.5 truncate">
                    {today.top_category || 'No spend today'}
                  </p>
                </div>
              </div>
            )}
          </GlassCard>
        </section>

        {/* ── TOP SECTION 1: Account Cards Horizontal Scroll ───────────────── */}
        <section>
          <div className="flex items-center justify-between mb-3">
            <div className="flex items-center gap-2">
              <h2 className="text-xs font-bold text-slate-500 uppercase tracking-wider">
                Accounts &amp; Cards ({accounts.length})
              </h2>
            </div>
            <div className="flex items-center gap-2">
              <button
                onClick={() => setIsAddAccountOpen(true)}
                className="inline-flex items-center gap-1 min-h-[44px] px-3 rounded-xl text-xs font-bold text-blue-600 hover:bg-blue-50/70 transition-colors active:scale-95"
              >
                <Plus className="w-3.5 h-3.5" />
                <span>Add Account</span>
              </button>
              <Link
                href="/accounts"
                className="hidden sm:inline-flex items-center gap-1 min-h-[44px] px-2.5 rounded-xl text-xs font-semibold text-slate-500 hover:text-slate-900 hover:bg-white/60 transition-colors"
              >
                <span>All Accounts</span>
                <ArrowRight className="w-3.5 h-3.5" />
              </Link>
            </div>
          </div>

          {loading ? (
            <div className="flex overflow-x-auto gap-3.5 pb-2 scrollbar-none md:grid md:grid-cols-3 md:gap-4 md:pb-0">
              {[0, 1, 2].map((i) => (
                <GlassCard
                  key={i}
                  className="min-w-[85vw] sm:min-w-[300px] md:min-w-0 p-5 shrink-0"
                >
                  <div className="flex items-center justify-between mb-4">
                    <Skeleton className="h-4 w-28 rounded-full" />
                    <Skeleton className="h-5 w-16 rounded-full" />
                  </div>
                  <Skeleton className="h-8 w-36 rounded-lg mb-3" />
                  <div className="flex items-center justify-between">
                    <Skeleton className="h-3 w-20 rounded-full" />
                    <Skeleton className="h-5 w-20 rounded-full" />
                  </div>
                </GlassCard>
              ))}
            </div>
          ) : accounts.length === 0 ? (
            <EmptyState
              icon={CreditCard}
              title="No Bank Accounts Linked Yet"
              description="Sync a bank SMS from your Android app or paste an SMS to auto-detect your bank account, or add one manually."
              actionLabel="Sync Bank SMS"
              onAction={openSyncModal}
              className="!py-10"
            />
          ) : (
            <div className="flex overflow-x-auto snap-x snap-mandatory gap-3.5 pb-2 scrollbar-none md:grid md:grid-cols-2 lg:grid-cols-3 md:gap-4 md:pb-0">
              {accounts.map((acc) => {
                const accent = getBankAccent(acc.bank_name);
                const bal = Number(acc.current_balance);
                return (
                  <Link
                    key={acc.id}
                    href={`/transactions?account=${acc.id}`}
                    className="min-w-[85vw] sm:min-w-[300px] md:min-w-0 snap-center shrink-0 md:shrink block group"
                  >
                    <GlassCard className="p-5 relative overflow-hidden hover:shadow-[0_8px_30px_rgba(0,0,0,0.09)] hover:-translate-y-0.5 transition-all active:scale-[0.99]">
                      <div
                        className={`absolute top-0 left-0 right-0 h-1 bg-gradient-to-r ${accent.topBar}`}
                      />

                      <div className="flex items-start justify-between gap-2 mb-4">
                        <div className="flex items-center gap-3 min-w-0">
                          <div
                            className={`w-10 h-10 rounded-xl flex items-center justify-center shrink-0 ${accent.iconBg}`}
                          >
                            <CreditCard className="w-5 h-5" />
                          </div>
                          <div className="min-w-0">
                            <p className="text-sm font-bold text-slate-900 truncate">
                              {acc.bank_name}
                            </p>
                            <div className="flex items-center gap-1.5 mt-0.5">
                              <span className="text-[10px] font-bold uppercase tracking-wide px-2 py-0.5 rounded-full bg-slate-100 text-slate-600">
                                {formatAccountType(acc.account_type)}
                              </span>
                              <span className="text-xs text-slate-400 font-mono">
                                {acc.account_number_mask}
                              </span>
                            </div>
                          </div>
                        </div>

                        <span className="inline-flex items-center gap-1 text-[10px] font-semibold text-emerald-700 bg-emerald-50 border border-emerald-200/70 px-2.5 py-1 rounded-full shrink-0">
                          <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse-soft" />
                          Live Balance
                        </span>
                      </div>

                      <div className="flex items-end justify-between gap-2 pt-1">
                        <div>
                          <p className="text-[10px] font-semibold uppercase tracking-wider text-slate-400">
                            Available Balance
                          </p>
                          <p
                            className={`text-2xl font-bold font-mono tracking-tight mt-0.5 ${
                              bal < 0 ? 'text-rose-600' : 'text-slate-900'
                            }`}
                          >
                            {formatInr(bal)}
                          </p>
                        </div>

                        <span className="w-10 h-10 min-w-[44px] min-h-[44px] rounded-xl bg-slate-100/80 group-hover:bg-blue-600 text-slate-600 group-hover:text-white flex items-center justify-center transition-colors shrink-0">
                          <ArrowRight className="w-4 h-4" />
                        </span>
                      </div>
                    </GlassCard>
                  </Link>
                );
              })}
            </div>
          )}
        </section>

        {/* ── TOP SECTION 2: Financial Summary Cards ───────────────────────── */}
        <section>
          <SummaryCards summary={summary} loading={loading} />
        </section>

        {/* ── BANK-WISE SUMMARY STRIP ──────────────────────────────────────── */}
        {!loading && bankSummary.length > 0 && (
          <section>
            <div className="flex items-center justify-between mb-3">
              <h2 className="text-xs font-bold text-slate-500 uppercase tracking-wider">
                Bank-Wise Summary ({bankSummary.length})
              </h2>
              <Link
                href="/analytics"
                className="text-xs font-semibold text-blue-600 hover:text-blue-700 inline-flex items-center gap-1"
              >
                <span>Detailed Analytics</span>
                <ArrowRight className="w-3.5 h-3.5" />
              </Link>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3.5">
              {bankSummary.map((bank) => (
                <GlassCard key={bank.bank_name} className="p-4">
                  <div className="flex items-center justify-between gap-2 mb-2.5">
                    <div className="flex items-center gap-2.5 min-w-0">
                      <div className="w-8 h-8 rounded-xl bg-blue-50 text-blue-600 flex items-center justify-center shrink-0">
                        <Landmark className="w-4 h-4" />
                      </div>
                      <div className="min-w-0">
                        <p className="text-sm font-bold text-slate-900 truncate">
                          {bank.bank_name}
                        </p>
                        <p className="text-[11px] text-slate-500">
                          {bank.account_count} {bank.account_count === 1 ? 'account' : 'accounts'} • {bank.tx_count} txns
                        </p>
                      </div>
                    </div>
                    <span className="text-sm font-bold font-mono text-slate-900 shrink-0">
                      {formatInr(bank.balance)}
                    </span>
                  </div>
                  <div className="pt-2 border-t border-slate-100 flex items-center justify-between text-xs font-mono font-semibold">
                    <span className="inline-flex items-center gap-1 text-emerald-600">
                      <TrendingUp className="w-3.5 h-3.5" />+{formatInr(bank.total_credit)}
                    </span>
                    <span className="inline-flex items-center gap-1 text-rose-500">
                      <TrendingDown className="w-3.5 h-3.5" />-{formatInr(bank.total_debit)}
                    </span>
                  </div>
                </GlassCard>
              ))}
            </div>
          </section>
        )}

        {/* ── MIDDLE SECTION 3: Quick Actions Row ──────────────────────────── */}
        <section>
          <div className="flex items-center gap-2.5 overflow-x-auto pb-1 scrollbar-none">
            <button
              onClick={openSyncModal}
              className="inline-flex items-center justify-center gap-2 min-h-[44px] px-5 py-2 rounded-full text-xs sm:text-sm font-bold bg-gradient-to-r from-emerald-600 to-teal-600 text-white shadow-md shadow-emerald-500/25 hover:shadow-lg hover:shadow-emerald-500/35 transition-all active:scale-95 shrink-0"
            >
              <MessageSquare className="w-4 h-4" />
              <span>Automated SMS Sync</span>
            </button>

            <button
              onClick={() => setIsAddTxOpen(true)}
              className="inline-flex items-center justify-center gap-2 min-h-[44px] px-4 py-2 rounded-full text-xs sm:text-sm font-semibold bg-white/80 backdrop-blur-xl text-slate-700 border border-white/60 shadow-[0_2px_12px_rgba(0,0,0,0.05)] hover:bg-white transition-all active:scale-95 shrink-0"
            >
              <Plus className="w-4 h-4 text-blue-600" />
              <span>Manual Entry</span>
            </button>

            <Link
              href="/analytics"
              className="inline-flex items-center justify-center gap-2 min-h-[44px] px-4 py-2 rounded-full text-xs sm:text-sm font-semibold bg-white/80 backdrop-blur-xl text-slate-700 border border-white/60 shadow-[0_2px_12px_rgba(0,0,0,0.05)] hover:bg-white transition-all active:scale-95 shrink-0"
            >
              <BarChart3 className="w-4 h-4 text-indigo-600" />
              <span>Analytics</span>
            </Link>

            <Link
              href="/accounts"
              className="inline-flex items-center justify-center gap-2 min-h-[44px] px-4 py-2 rounded-full text-xs sm:text-sm font-semibold bg-white/80 backdrop-blur-xl text-slate-700 border border-white/60 shadow-[0_2px_12px_rgba(0,0,0,0.05)] hover:bg-white transition-all active:scale-95 shrink-0"
            >
              <Landmark className="w-4 h-4 text-blue-600" />
              <span>Accounts</span>
            </Link>
          </div>
        </section>

        {/* ── MIDDLE SECTION 4: Income vs Expenses Chart ───────────────────── */}
        <section>
          <CashFlowChart data={cashFlow} loading={loading} />
        </section>

        {/* ── BOTTOM SECTION 5: Category Breakdown + Recent Transactions ─────── */}
        <section className="grid grid-cols-1 lg:grid-cols-12 gap-5 md:gap-6 items-stretch">
          <div className="lg:col-span-5">
            <CategoryBreakdown categories={categories} loading={loading} />
          </div>
          <div className="lg:col-span-7">
            <RecentTransactions
              transactions={transactions}
              loading={loading}
              onRefresh={fetchData}
            />
          </div>
        </section>
      </div>

      <AddAccountModal
        isOpen={isAddAccountOpen}
        onClose={() => setIsAddAccountOpen(false)}
        onSuccess={fetchData}
      />

      <AddTransactionModal
        isOpen={isAddTxOpen}
        onClose={() => setIsAddTxOpen(false)}
        onSuccess={fetchData}
      />
    </div>
  );
}
