'use client';

import React, { useState, useEffect, useCallback, useMemo } from 'react';
import Link from 'next/link';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
} from 'recharts';
import { Header } from '@/components/layout/Header';
import { GlassCard } from '@/components/ui/GlassCard';
import { EmptyState } from '@/components/ui/EmptyState';
import { Skeleton } from '@/components/ui/Skeleton';
import { AnimatedNumber } from '@/components/ui/AnimatedNumber';
import {
  TrendingUp,
  TrendingDown,
  PiggyBank,
  Calendar,
  Store,
  CreditCard,
  BarChart3,
  PieChart as PieChartIcon,
  ArrowRight,
  RefreshCw,
  AlertCircle,
} from 'lucide-react';
import { Account, AccountType, FinancialSummary, Transaction } from '@/lib/types';

type PeriodKey = 'THIS_MONTH' | 'LAST_MONTH' | 'LAST_3_MONTHS';

const PERIOD_TABS: Array<{ key: PeriodKey; label: string }> = [
  { key: 'THIS_MONTH', label: 'This Month' },
  { key: 'LAST_MONTH', label: 'Last Month' },
  { key: 'LAST_3_MONTHS', label: 'Last 3 Months' },
];

const FALLBACK_COLORS = [
  '#2563EB',
  '#10B981',
  '#6366F1',
  '#F59E0B',
  '#EF4444',
  '#EC4899',
  '#14B8A6',
  '#8B5CF6',
];

function formatInr(val: number): string {
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 0,
  }).format(Math.round(val));
}

function formatInrShort(val: number): string {
  if (val >= 100000) return `₹${(val / 100000).toFixed(1)}L`;
  if (val >= 1000) return `₹${Math.round(val / 1000)}K`;
  return `₹${val}`;
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

function isExcludedTransaction(tx: Transaction): boolean {
  return (
    tx.is_excluded_from_totals ||
    tx.transaction_type === 'TRANSFER' ||
    tx.transaction_type === 'CC_BILL_PAYMENT'
  );
}

interface TrendTooltipProps {
  active?: boolean;
  payload?: Array<{ dataKey?: string | number; value?: number | string }>;
  label?: string | number;
}

function TrendTooltip({ active, payload, label }: TrendTooltipProps) {
  if (!active || !payload || payload.length === 0) return null;
  const income = Number(payload.find((p) => p.dataKey === 'income')?.value ?? 0);
  const expense = Number(payload.find((p) => p.dataKey === 'expense')?.value ?? 0);
  const net = income - expense;

  return (
    <div className="bg-white/90 backdrop-blur-xl border border-white/60 shadow-[0_8px_32px_rgba(0,0,0,0.12)] rounded-2xl px-4 py-3 text-xs">
      <p className="font-bold text-slate-900 mb-2">{label}</p>
      <div className="space-y-1">
        <div className="flex items-center justify-between gap-6">
          <span className="flex items-center gap-1.5 text-slate-500">
            <span className="w-2 h-2 rounded-full bg-emerald-500" />
            Income
          </span>
          <span className="font-bold text-emerald-600 font-mono">{formatInr(income)}</span>
        </div>
        <div className="flex items-center justify-between gap-6">
          <span className="flex items-center gap-1.5 text-slate-500">
            <span className="w-2 h-2 rounded-full bg-rose-500" />
            Expense
          </span>
          <span className="font-bold text-rose-500 font-mono">{formatInr(expense)}</span>
        </div>
        <div className="pt-1.5 mt-1 border-t border-slate-100 flex items-center justify-between gap-6">
          <span className="text-slate-600 font-semibold">Net</span>
          <span className={`font-bold font-mono ${net >= 0 ? 'text-blue-600' : 'text-rose-600'}`}>
            {net >= 0 ? '+' : ''}
            {formatInr(net)}
          </span>
        </div>
      </div>
    </div>
  );
}

interface PieTooltipProps {
  active?: boolean;
  payload?: Array<{
    name?: string;
    value?: number | string;
    payload?: { color?: string; pct?: number };
  }>;
}

function CategoryPieTooltip({ active, payload }: PieTooltipProps) {
  if (!active || !payload || payload.length === 0) return null;
  const item = payload[0];
  return (
    <div className="bg-white/90 backdrop-blur-xl border border-white/60 shadow-[0_8px_32px_rgba(0,0,0,0.12)] rounded-2xl px-3.5 py-2.5 text-xs">
      <div className="flex items-center gap-2 mb-1">
        <span
          className="w-2.5 h-2.5 rounded-full"
          style={{ backgroundColor: item.payload?.color || '#2563EB' }}
        />
        <span className="font-bold text-slate-900">{item.name}</span>
      </div>
      <div className="font-mono font-bold text-slate-800">
        {formatInr(Number(item.value ?? 0))}{' '}
        <span className="text-slate-400 font-normal">({item.payload?.pct ?? 0}%)</span>
      </div>
    </div>
  );
}

export default function AnalyticsPage() {
  const [period, setPeriod] = useState<PeriodKey>('THIS_MONTH');
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [statsSummary, setStatsSummary] = useState<FinancialSummary | null>(null);
  const [statsCashFlow, setStatsCashFlow] = useState<
    Array<{ month: string; income: number; expense: number }>
  >([]);
  const [loading, setLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchAnalytics = useCallback(async () => {
    setIsRefreshing(true);
    setError(null);
    try {
      const [statsRes, txRes, accRes] = await Promise.all([
        fetch('/api/stats'),
        fetch('/api/transactions?limit=1000'),
        fetch('/api/accounts'),
      ]);

      if (!statsRes.ok && !txRes.ok && !accRes.ok) {
        throw new Error('Failed to load analytics data from database');
      }

      if (statsRes.ok) {
        const statsData = await statsRes.json();
        setStatsSummary(statsData.summary || null);
        setStatsCashFlow(statsData.cashFlow || []);
      }

      if (txRes.ok) {
        const txData = await txRes.json();
        setTransactions(txData.transactions || []);
      }

      if (accRes.ok) {
        const accData = await accRes.json();
        setAccounts(accData.accounts || []);
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Unable to fetch analytics';
      console.error('Analytics fetch error:', err);
      setError(msg);
    } finally {
      setLoading(false);
      setIsRefreshing(false);
    }
  }, []);

  useEffect(() => {
    fetchAnalytics();
  }, [fetchAnalytics]);

  // Date window for selected period
  const periodWindow = useMemo(() => {
    const now = new Date();
    const y = now.getFullYear();
    const m = now.getMonth();

    if (period === 'THIS_MONTH') {
      const start = new Date(y, m, 1, 0, 0, 0, 0);
      const end = now;
      const days = Math.max(1, now.getDate());
      return { start, end, days, subtitle: now.toLocaleString('en-IN', { month: 'long', year: 'numeric' }) };
    }

    if (period === 'LAST_MONTH') {
      const start = new Date(y, m - 1, 1, 0, 0, 0, 0);
      const end = new Date(y, m, 0, 23, 59, 59, 999);
      const days = Math.max(1, end.getDate());
      return { start, end, days, subtitle: start.toLocaleString('en-IN', { month: 'long', year: 'numeric' }) };
    }

    // LAST_3_MONTHS: current month + previous 2 months
    const start = new Date(y, m - 2, 1, 0, 0, 0, 0);
    const end = now;
    const days = Math.max(1, Math.ceil((end.getTime() - start.getTime()) / 86400000));
    return { start, end, days, subtitle: 'Last 3 Months' };
  }, [period]);

  // Transactions inside the active period
  const periodTransactions = useMemo(() => {
    const startMs = periodWindow.start.getTime();
    const endMs = periodWindow.end.getTime();
    return transactions.filter((tx) => {
      const t = new Date(tx.transaction_date).getTime();
      return t >= startMs && t <= endMs;
    });
  }, [transactions, periodWindow]);

  // 2. Top KPIs
  const kpis = useMemo(() => {
    let totalIn = 0;
    let totalOut = 0;

    for (const tx of periodTransactions) {
      if (isExcludedTransaction(tx)) continue;
      const amt = Number(tx.amount) || 0;
      if (tx.direction === 'CREDIT') {
        totalIn += amt;
      } else {
        totalOut += amt;
      }
    }

    // Fall back to /api/stats summary for THIS_MONTH if needed when total transactions > 1000
    if (period === 'THIS_MONTH' && statsSummary && periodTransactions.length === 0) {
      totalIn = statsSummary.monthly_income;
      totalOut = statsSummary.monthly_expense;
    }

    const net = totalIn - totalOut;
    const savingsRate = totalIn > 0 ? Math.round((net / totalIn) * 1000) / 10 : 0;
    const avgDailySpend = Math.round(totalOut / periodWindow.days);

    return { totalIn, totalOut, savingsRate, avgDailySpend };
  }, [periodTransactions, period, statsSummary, periodWindow.days]);

  // 3. Trend chart data
  const trendData = useMemo(() => {
    const valid = periodTransactions.filter((tx) => !isExcludedTransaction(tx));

    if (period === 'LAST_3_MONTHS') {
      const now = new Date();
      const buckets: Array<{ key: string; label: string; income: number; expense: number }> = [];
      for (let i = 2; i >= 0; i--) {
        const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
        const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
        buckets.push({
          key,
          label: d.toLocaleString('en-IN', { month: 'short' }),
          income: 0,
          expense: 0,
        });
      }

      const map = new Map(buckets.map((b) => [b.key, b]));
      for (const tx of valid) {
        const d = new Date(tx.transaction_date);
        const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
        const bucket = map.get(key);
        if (!bucket) continue;
        const amt = Number(tx.amount) || 0;
        if (tx.direction === 'CREDIT') bucket.income += amt;
        else bucket.expense += amt;
      }

      const hasAny = buckets.some((b) => b.income > 0 || b.expense > 0);
      if (!hasAny && statsCashFlow.length > 0) {
        return statsCashFlow.slice(-3).map((c) => ({
          label: c.month,
          income: c.income,
          expense: c.expense,
        }));
      }
      return hasAny
        ? buckets.map((b) => ({
            label: b.label,
            income: Math.round(b.income),
            expense: Math.round(b.expense),
          }))
        : [];
    }

    // Weekly buckets for This Month / Last Month
    if (valid.length === 0) return [];
    const weeks = [
      { label: 'Days 1-7', income: 0, expense: 0 },
      { label: 'Days 8-14', income: 0, expense: 0 },
      { label: 'Days 15-21', income: 0, expense: 0 },
      { label: 'Days 22-28', income: 0, expense: 0 },
      { label: 'Days 29+', income: 0, expense: 0 },
    ];

    for (const tx of valid) {
      const day = new Date(tx.transaction_date).getDate();
      const idx = Math.min(4, Math.floor((day - 1) / 7));
      const amt = Number(tx.amount) || 0;
      if (tx.direction === 'CREDIT') weeks[idx].income += amt;
      else weeks[idx].expense += amt;
    }

    return weeks
      .filter((_, idx) => idx < 4 || weeks[4].income > 0 || weeks[4].expense > 0)
      .map((w) => ({
        label: w.label,
        income: Math.round(w.income),
        expense: Math.round(w.expense),
      }));
  }, [periodTransactions, period, statsCashFlow]);

  // 4. Category Breakdown for selected period
  const categoryData = useMemo(() => {
    const totals: Record<string, { value: number; color: string }> = {};
    for (const tx of periodTransactions) {
      if (isExcludedTransaction(tx) || tx.direction !== 'DEBIT') continue;
      const name = tx.categories?.name || 'Uncategorized';
      const color = tx.categories?.color || '#94A3B8';
      if (!totals[name]) totals[name] = { value: 0, color };
      totals[name].value += Number(tx.amount) || 0;
    }

    const entries = Object.entries(totals)
      .map(([name, info], idx) => ({
        name,
        value: Math.round(info.value),
        color: info.color || FALLBACK_COLORS[idx % FALLBACK_COLORS.length],
      }))
      .sort((a, b) => b.value - a.value);

    const total = entries.reduce((sum, c) => sum + c.value, 0);
    return {
      total,
      items: entries.map((e) => ({
        ...e,
        pct: total > 0 ? Math.round((e.value / total) * 100) : 0,
      })),
    };
  }, [periodTransactions]);

  // 5. Top Merchants list (grouped from /api/transactions)
  const topMerchants = useMemo(() => {
    const map: Record<string, { name: string; total: number; count: number; lastDate: string }> = {};
    let grandTotal = 0;

    for (const tx of periodTransactions) {
      if (isExcludedTransaction(tx) || tx.direction !== 'DEBIT') continue;
      const rawName = (tx.merchant || 'Unknown Merchant').trim();
      const key = rawName.toLowerCase();
      const amt = Number(tx.amount) || 0;
      grandTotal += amt;

      if (!map[key]) {
        map[key] = {
          name: rawName,
          total: 0,
          count: 0,
          lastDate: tx.transaction_date,
        };
      }
      map[key].total += amt;
      map[key].count += 1;
      if (new Date(tx.transaction_date) > new Date(map[key].lastDate)) {
        map[key].lastDate = tx.transaction_date;
      }
    }

    return Object.values(map)
      .sort((a, b) => b.total - a.total)
      .slice(0, 8)
      .map((m) => ({
        ...m,
        total: Math.round(m.total),
        pct: grandTotal > 0 ? Math.round((m.total / grandTotal) * 100) : 0,
      }));
  }, [periodTransactions]);

  // 6. Account-wise breakdown
  const accountBreakdown = useMemo(() => {
    const flowByAccount: Record<string, { inflow: number; outflow: number; count: number }> = {};

    for (const tx of periodTransactions) {
      if (!tx.account_id) continue;
      if (!flowByAccount[tx.account_id]) {
        flowByAccount[tx.account_id] = { inflow: 0, outflow: 0, count: 0 };
      }
      const amt = Number(tx.amount) || 0;
      flowByAccount[tx.account_id].count += 1;
      if (tx.direction === 'CREDIT') {
        flowByAccount[tx.account_id].inflow += amt;
      } else {
        flowByAccount[tx.account_id].outflow += amt;
      }
    }

    return accounts.map((acc) => {
      const flow = flowByAccount[acc.id] || { inflow: 0, outflow: 0, count: 0 };
      return {
        ...acc,
        inflow: Math.round(flow.inflow),
        outflow: Math.round(flow.outflow),
        txCount: flow.count,
      };
    });
  }, [accounts, periodTransactions]);

  return (
    <div className="flex flex-col min-h-screen">
      <Header onRefresh={fetchAnalytics} isRefreshing={isRefreshing} />

      <div className="p-4 sm:p-6 md:p-8 space-y-5 md:space-y-7 max-w-7xl mx-auto w-full">
        {/* ── Header + Section 1: Period Selector Tabs ────────────────────── */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div>
            <h2 className="text-xl sm:text-2xl font-bold tracking-tight text-slate-900">
              Financial Analytics
            </h2>
            <p className="text-xs sm:text-sm text-slate-500 font-medium mt-0.5">
              Live database insights • {periodWindow.subtitle}
            </p>
          </div>

          <div className="inline-flex items-center gap-1 p-1 bg-white/75 backdrop-blur-xl border border-white/60 rounded-2xl shadow-[0_2px_16px_rgba(0,0,0,0.05)] self-start sm:self-auto">
            {PERIOD_TABS.map((tab) => {
              const active = period === tab.key;
              return (
                <button
                  key={tab.key}
                  onClick={() => setPeriod(tab.key)}
                  className={`min-h-[44px] px-3.5 sm:px-4 rounded-xl text-xs font-bold transition-all ${
                    active
                      ? 'bg-gradient-to-r from-blue-600 to-indigo-600 text-white shadow-sm shadow-blue-500/25'
                      : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100/60'
                  }`}
                >
                  {tab.label}
                </button>
              );
            })}
          </div>
        </div>

        {/* Error State */}
        {error && (
          <GlassCard className="p-4 border-rose-200/80 bg-rose-50/70 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
            <div className="flex items-center gap-3">
              <div className="w-9 h-9 rounded-xl bg-rose-100 text-rose-600 flex items-center justify-center shrink-0">
                <AlertCircle className="w-5 h-5" />
              </div>
              <div>
                <p className="text-sm font-bold text-rose-900">Analytics Load Failed</p>
                <p className="text-xs text-rose-700">{error}</p>
              </div>
            </div>
            <button onClick={fetchAnalytics} className="btn-secondary !min-h-[44px] text-xs px-4">
              <RefreshCw className="w-3.5 h-3.5" />
              Retry
            </button>
          </GlassCard>
        )}

        {/* ── Section 2: Top KPIs Row ─────────────────────────────────────── */}
        {loading ? (
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 sm:gap-4">
            {[0, 1, 2, 3].map((i) => (
              <GlassCard key={i} className="p-4 sm:p-5 min-h-[100px]">
                <div className="flex items-center justify-between mb-3">
                  <Skeleton className="h-3 w-20 rounded-full" />
                  <Skeleton className="w-8 h-8 rounded-xl" />
                </div>
                <Skeleton className="h-7 w-28 rounded-lg mb-2" />
                <Skeleton className="h-3 w-24 rounded-full" />
              </GlassCard>
            ))}
          </div>
        ) : (
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 sm:gap-4">
            {/* Total In */}
            <GlassCard className="p-4 sm:p-5 min-h-[100px]">
              <div className="flex items-center justify-between mb-2.5">
                <span className="text-[11px] sm:text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  Total In
                </span>
                <div className="w-8 h-8 rounded-xl bg-emerald-50 text-emerald-600 flex items-center justify-center shrink-0">
                  <TrendingUp className="w-4 h-4" />
                </div>
              </div>
              <div className="text-lg sm:text-2xl font-bold text-emerald-600 font-mono tracking-tight">
                <AnimatedNumber value={kpis.totalIn} format={formatInr} />
              </div>
              <p className="text-[11px] text-slate-400 font-medium mt-1.5">
                Credits in period
              </p>
            </GlassCard>

            {/* Total Out */}
            <GlassCard className="p-4 sm:p-5 min-h-[100px]">
              <div className="flex items-center justify-between mb-2.5">
                <span className="text-[11px] sm:text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  Total Out
                </span>
                <div className="w-8 h-8 rounded-xl bg-rose-50 text-rose-500 flex items-center justify-center shrink-0">
                  <TrendingDown className="w-4 h-4" />
                </div>
              </div>
              <div className="text-lg sm:text-2xl font-bold text-rose-500 font-mono tracking-tight">
                <AnimatedNumber value={kpis.totalOut} format={formatInr} />
              </div>
              <p className="text-[11px] text-slate-400 font-medium mt-1.5">
                Debits in period
              </p>
            </GlassCard>

            {/* Savings Rate */}
            <GlassCard className="p-4 sm:p-5 min-h-[100px]">
              <div className="flex items-center justify-between mb-2.5">
                <span className="text-[11px] sm:text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  Savings Rate
                </span>
                <div className="w-8 h-8 rounded-xl bg-indigo-50 text-indigo-600 flex items-center justify-center shrink-0">
                  <PiggyBank className="w-4 h-4" />
                </div>
              </div>
              <div
                className={`text-lg sm:text-2xl font-bold font-mono tracking-tight ${
                  kpis.savingsRate >= 0 ? 'text-indigo-600' : 'text-rose-600'
                }`}
              >
                {kpis.savingsRate}%
              </div>
              <p className="text-[11px] text-slate-400 font-medium mt-1.5 truncate">
                {kpis.savingsRate >= 30
                  ? 'Healthy surplus'
                  : kpis.totalIn > 0
                  ? 'Net saved vs income'
                  : 'No income recorded'}
              </p>
            </GlassCard>

            {/* Avg Daily Spend */}
            <GlassCard className="p-4 sm:p-5 min-h-[100px]">
              <div className="flex items-center justify-between mb-2.5">
                <span className="text-[11px] sm:text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  Avg Daily Spend
                </span>
                <div className="w-8 h-8 rounded-xl bg-blue-50 text-blue-600 flex items-center justify-center shrink-0">
                  <Calendar className="w-4 h-4" />
                </div>
              </div>
              <div className="text-lg sm:text-2xl font-bold text-slate-900 font-mono tracking-tight">
                <AnimatedNumber value={kpis.avgDailySpend} format={formatInr} />
              </div>
              <p className="text-[11px] text-slate-400 font-medium mt-1.5">
                Over {periodWindow.days} {periodWindow.days === 1 ? 'day' : 'days'}
              </p>
            </GlassCard>
          </div>
        )}

        {/* ── Section 3 & 4: Trend Chart + Category Pie Chart ─────────────── */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-5 md:gap-6">
          {/* Section 3: Income vs Expense Trend Chart */}
          <GlassCard className="lg:col-span-7 p-4 sm:p-6">
            <div className="flex items-center justify-between mb-5">
              <div>
                <h3 className="text-sm sm:text-base font-bold text-slate-900 tracking-tight">
                  Income vs Expense Trend
                </h3>
                <p className="text-[11px] sm:text-xs text-slate-500 font-medium mt-0.5">
                  {period === 'LAST_3_MONTHS' ? 'Monthly breakdown' : 'Weekly breakdown'} ({periodWindow.subtitle})
                </p>
              </div>
              {trendData.length > 0 && !loading && (
                <div className="flex items-center gap-3 text-[11px] sm:text-xs font-semibold">
                  <span className="flex items-center gap-1.5 text-slate-600">
                    <span className="w-2.5 h-2.5 rounded-full bg-emerald-500" />
                    In
                  </span>
                  <span className="flex items-center gap-1.5 text-slate-600">
                    <span className="w-2.5 h-2.5 rounded-full bg-rose-500" />
                    Out
                  </span>
                </div>
              )}
            </div>

            {loading ? (
              <div className="h-[260px] sm:h-[300px] flex items-end gap-4 px-4 pb-4">
                {[65, 85, 45, 78, 60].map((h, i) => (
                  <div key={i} className="flex-1 flex items-end gap-1.5 h-full pt-6">
                    <Skeleton className="flex-1 rounded-t-lg" style={{ height: `${h}%` } as React.CSSProperties} />
                    <Skeleton className="flex-1 rounded-t-lg" style={{ height: `${Math.round(h * 0.6)}%` } as React.CSSProperties} />
                  </div>
                ))}
              </div>
            ) : trendData.length === 0 ? (
              <EmptyState
                icon={BarChart3}
                title="No Trend Data for Period"
                description="No income or expense transactions recorded during this timeframe."
                className="h-[260px] sm:h-[300px] !py-6 !shadow-none border border-dashed border-slate-200/80 bg-slate-50/40"
              />
            ) : (
              <div className="h-[260px] sm:h-[300px] w-full">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={trendData} margin={{ top: 8, right: 6, left: -12, bottom: 0 }} barGap={4}>
                    <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#E2E8F0" opacity={0.65} />
                    <XAxis
                      dataKey="label"
                      tickLine={false}
                      axisLine={false}
                      tick={{ fill: '#64748B', fontSize: 11, fontWeight: 500 }}
                    />
                    <YAxis
                      tickLine={false}
                      axisLine={false}
                      tick={{ fill: '#64748B', fontSize: 11 }}
                      tickFormatter={formatInrShort}
                      width={50}
                    />
                    <Tooltip content={<TrendTooltip />} cursor={{ fill: 'rgba(226,232,240,0.35)', radius: 8 }} />
                    <Bar dataKey="income" fill="#10B981" radius={[6, 6, 0, 0]} maxBarSize={28} />
                    <Bar dataKey="expense" fill="#EF4444" radius={[6, 6, 0, 0]} maxBarSize={28} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            )}
          </GlassCard>

          {/* Section 4: Category Breakdown (Recharts PieChart + Bar List) */}
          <GlassCard className="lg:col-span-5 p-4 sm:p-6 flex flex-col">
            <div className="flex items-center justify-between mb-4">
              <div>
                <h3 className="text-sm sm:text-base font-bold text-slate-900 tracking-tight">
                  Category Breakdown
                </h3>
                <p className="text-[11px] sm:text-xs text-slate-500 font-medium mt-0.5">
                  Expense distribution
                </p>
              </div>
              {categoryData.items.length > 0 && !loading && (
                <span className="text-[11px] font-semibold text-slate-600 bg-slate-100 px-2.5 py-1 rounded-full">
                  {categoryData.items.length} cats
                </span>
              )}
            </div>

            {loading ? (
              <div className="flex-1 flex flex-col items-center justify-center gap-4 py-4">
                <Skeleton className="w-40 h-40 rounded-full" />
                <div className="w-full space-y-2.5">
                  <Skeleton className="h-3.5 w-full rounded-full" />
                  <Skeleton className="h-3.5 w-4/5 rounded-full" />
                  <Skeleton className="h-3.5 w-3/5 rounded-full" />
                </div>
              </div>
            ) : categoryData.items.length === 0 ? (
              <EmptyState
                icon={PieChartIcon}
                title="No Category Expenses"
                description="Expenses in the selected period will appear in this donut breakdown."
                className="flex-1 !py-8 !shadow-none border border-dashed border-slate-200/80 bg-slate-50/40"
              />
            ) : (
              <div className="flex flex-col gap-4 flex-1">
                <div className="h-[190px] w-full relative">
                  <ResponsiveContainer width="100%" height="100%">
                    <PieChart>
                      <Pie
                        data={categoryData.items}
                        dataKey="value"
                        nameKey="name"
                        cx="50%"
                        cy="50%"
                        innerRadius={54}
                        outerRadius={82}
                        paddingAngle={3}
                        stroke="none"
                      >
                        {categoryData.items.map((entry) => (
                          <Cell key={entry.name} fill={entry.color} />
                        ))}
                      </Pie>
                      <Tooltip content={<CategoryPieTooltip />} />
                    </PieChart>
                  </ResponsiveContainer>
                  <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
                    <span className="text-[10px] font-semibold uppercase tracking-wider text-slate-400">
                      Spent
                    </span>
                    <span className="text-sm font-bold font-mono text-slate-900">
                      {formatInr(categoryData.total)}
                    </span>
                  </div>
                </div>

                <div className="space-y-2.5 max-h-[180px] overflow-y-auto scrollbar-thin pr-1">
                  {categoryData.items.slice(0, 6).map((cat) => (
                    <div key={cat.name} className="space-y-1">
                      <div className="flex items-center justify-between text-xs gap-2">
                        <div className="flex items-center gap-2 min-w-0">
                          <span
                            className="w-2.5 h-2.5 rounded-full shrink-0"
                            style={{ backgroundColor: cat.color }}
                          />
                          <span className="font-semibold text-slate-700 truncate">{cat.name}</span>
                        </div>
                        <div className="flex items-center gap-1.5 shrink-0">
                          <span className="font-bold font-mono text-slate-900">
                            {formatInr(cat.value)}
                          </span>
                          <span className="text-[11px] text-slate-400">({cat.pct}%)</span>
                        </div>
                      </div>
                      <div className="w-full h-1.5 rounded-full bg-slate-100 overflow-hidden">
                        <div
                          className="h-full rounded-full"
                          style={{ width: `${Math.max(cat.pct, 3)}%`, backgroundColor: cat.color }}
                        />
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </GlassCard>
        </div>

        {/* ── Section 5 & 6: Top Merchants + Account-Wise Breakdown ───────── */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-5 md:gap-6">
          {/* Section 5: Top Merchants List */}
          <GlassCard className="lg:col-span-6 p-4 sm:p-6 flex flex-col">
            <div className="flex items-center justify-between mb-4">
              <div>
                <h3 className="text-sm sm:text-base font-bold text-slate-900 tracking-tight">
                  Top Merchants
                </h3>
                <p className="text-[11px] sm:text-xs text-slate-500 font-medium mt-0.5">
                  Highest debit spend by merchant
                </p>
              </div>
              {topMerchants.length > 0 && !loading && (
                <span className="text-[11px] font-semibold text-slate-500 bg-slate-100 px-2.5 py-1 rounded-full">
                  Top {topMerchants.length}
                </span>
              )}
            </div>

            {loading ? (
              <div className="space-y-3">
                {[0, 1, 2, 3, 4].map((i) => (
                  <div key={i} className="flex items-center justify-between py-2">
                    <div className="flex items-center gap-3">
                      <Skeleton className="w-9 h-9 rounded-xl" />
                      <div className="space-y-1.5">
                        <Skeleton className="h-3.5 w-28 rounded-full" />
                        <Skeleton className="h-3 w-16 rounded-full" />
                      </div>
                    </div>
                    <Skeleton className="h-4 w-20 rounded-full" />
                  </div>
                ))}
              </div>
            ) : topMerchants.length === 0 ? (
              <EmptyState
                icon={Store}
                title="No Merchant Data"
                description="Debit transactions in this period will rank your top spending destinations."
                className="flex-1 !py-8 !shadow-none border border-dashed border-slate-200/80 bg-slate-50/40"
              />
            ) : (
              <div className="divide-y divide-slate-100/80">
                {topMerchants.map((merchant, idx) => (
                  <div
                    key={merchant.name}
                    className="py-3 min-h-[56px] flex items-center justify-between gap-3"
                  >
                    <div className="flex items-center gap-3 min-w-0">
                      <div className="w-9 h-9 rounded-xl bg-blue-50 text-blue-600 font-bold text-xs flex items-center justify-center shrink-0">
                        #{idx + 1}
                      </div>
                      <div className="min-w-0">
                        <p className="text-sm font-bold text-slate-900 truncate">
                          {merchant.name}
                        </p>
                        <p className="text-xs text-slate-500 font-medium">
                          {merchant.count} {merchant.count === 1 ? 'transaction' : 'transactions'} •{' '}
                          {merchant.pct}% of spend
                        </p>
                      </div>
                    </div>
                    <div className="text-right shrink-0">
                      <p className="text-sm font-bold font-mono text-rose-600">
                        {formatInr(merchant.total)}
                      </p>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </GlassCard>

          {/* Section 6: Account-Wise Breakdown Cards */}
          <GlassCard className="lg:col-span-6 p-4 sm:p-6 flex flex-col">
            <div className="flex items-center justify-between mb-4">
              <div>
                <h3 className="text-sm sm:text-base font-bold text-slate-900 tracking-tight">
                  Account-Wise Breakdown
                </h3>
                <p className="text-[11px] sm:text-xs text-slate-500 font-medium mt-0.5">
                  Balance & period cash flow per account
                </p>
              </div>
              <Link
                href="/accounts"
                className="inline-flex items-center gap-1 min-h-[44px] px-2 text-xs font-semibold text-blue-600 hover:text-blue-700"
              >
                <span>Manage</span>
                <ArrowRight className="w-3.5 h-3.5" />
              </Link>
            </div>

            {loading ? (
              <div className="space-y-3">
                {[0, 1, 2].map((i) => (
                  <div
                    key={i}
                    className="p-4 rounded-2xl border border-slate-100 bg-white/50 space-y-2.5"
                  >
                    <div className="flex items-center justify-between">
                      <Skeleton className="h-4 w-32 rounded-full" />
                      <Skeleton className="h-5 w-24 rounded-full" />
                    </div>
                    <Skeleton className="h-3 w-48 rounded-full" />
                  </div>
                ))}
              </div>
            ) : accountBreakdown.length === 0 ? (
              <EmptyState
                icon={CreditCard}
                title="No Accounts Found"
                description="Add a bank account or credit card to see account-level analytics."
                className="flex-1 !py-8 !shadow-none border border-dashed border-slate-200/80 bg-slate-50/40"
              />
            ) : (
              <div className="space-y-3">
                {accountBreakdown.map((acc) => {
                  const bal = Number(acc.current_balance);
                  return (
                    <Link
                      key={acc.id}
                      href={`/transactions?account=${acc.id}`}
                      className="block p-4 rounded-2xl bg-white/75 border border-slate-200/60 hover:border-blue-300/80 hover:shadow-sm transition-all min-h-[44px]"
                    >
                      <div className="flex items-center justify-between gap-2 mb-2">
                        <div className="flex items-center gap-2.5 min-w-0">
                          <div className="w-9 h-9 rounded-xl bg-indigo-50 text-indigo-600 flex items-center justify-center shrink-0">
                            <CreditCard className="w-4 h-4" />
                          </div>
                          <div className="min-w-0">
                            <p className="text-sm font-bold text-slate-900 truncate">
                              {acc.bank_name}
                            </p>
                            <p className="text-[11px] text-slate-500 font-medium">
                              {formatAccountType(acc.account_type)} • {acc.account_number_mask}
                            </p>
                          </div>
                        </div>
                        <div className="text-right shrink-0">
                          <p
                            className={`text-sm sm:text-base font-bold font-mono ${
                              bal < 0 ? 'text-rose-600' : 'text-slate-900'
                            }`}
                          >
                            {formatInr(bal)}
                          </p>
                          <p className="text-[10px] text-slate-400 font-medium">
                            {acc.txCount} {acc.txCount === 1 ? 'txn' : 'txns'}
                          </p>
                        </div>
                      </div>

                      <div className="pt-2 border-t border-slate-100 flex items-center justify-between text-xs font-medium">
                        <span className="text-emerald-600 font-mono font-semibold">
                          +{formatInr(acc.inflow)} in
                        </span>
                        <span className="text-rose-500 font-mono font-semibold">
                          -{formatInr(acc.outflow)} out
                        </span>
                      </div>
                    </Link>
                  );
                })}
              </div>
            )}
          </GlassCard>
        </div>
      </div>
    </div>
  );
}
