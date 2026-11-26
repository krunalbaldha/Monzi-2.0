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
  Landmark,
  Sparkles,
  Lightbulb,
  Receipt,
  Repeat,
  ArrowUpRight,
  ArrowDownRight,
  WifiOff,
} from 'lucide-react';
import { Account, AccountType, FinancialSummary, Transaction } from '@/lib/types';
import { loadOfflineSnapshot, saveOfflineSnapshot } from '@/lib/offlineStore';
import { useAuth } from '@/components/auth/AuthProvider';
import { usePwa } from '@/components/pwa/PwaProvider';

type PeriodKey =
  | 'TODAY'
  | 'YESTERDAY'
  | 'THIS_WEEK'
  | 'THIS_MONTH'
  | 'PREVIOUS_MONTH'
  | 'FINANCIAL_YEAR'
  | 'CUSTOM';

const PERIOD_TABS: Array<{ key: PeriodKey; label: string; apiParam: string }> = [
  { key: 'TODAY', label: 'Today', apiParam: 'today' },
  { key: 'YESTERDAY', label: 'Yesterday', apiParam: 'yesterday' },
  { key: 'THIS_WEEK', label: 'This Week', apiParam: 'week' },
  { key: 'THIS_MONTH', label: 'This Month', apiParam: 'month' },
  { key: 'PREVIOUS_MONTH', label: 'Previous Month', apiParam: 'prev_month' },
  { key: 'FINANCIAL_YEAR', label: 'Financial Year', apiParam: 'fy' },
  { key: 'CUSTOM', label: 'Custom Range', apiParam: 'custom' },
];

const IST_TZ = 'Asia/Kolkata';

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

function toIstDateKey(date: Date): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: IST_TZ,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(date);
}

function makeIstDate(dateKey: string, endOfDay = false): Date {
  return new Date(`${dateKey}T${endOfDay ? '23:59:59.999' : '00:00:00.000'}+05:30`);
}

function shiftDateKey(dateKey: string, deltaDays: number): string {
  const base = makeIstDate(dateKey, false);
  base.setUTCDate(base.getUTCDate() + deltaDays);
  return toIstDateKey(base);
}

function pad2(n: number): string {
  return String(n).padStart(2, '0');
}

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
    tx.is_self_transfer ||
    tx.duplicate_status === 'EXACT_DUPLICATE' ||
    tx.transaction_type === 'TRANSFER' ||
    tx.transaction_type === 'CC_BILL_PAYMENT' ||
    tx.transaction_type === 'REFUND' ||
    tx.transaction_type === 'REVERSAL' ||
    tx.transaction_status === 'FAILED' ||
    tx.transaction_status === 'REVERSED'
  );
}

function isRefundTx(tx: Transaction): boolean {
  return (
    (Boolean(tx.is_refund) ||
      tx.transaction_type === 'REFUND' ||
      tx.transaction_type === 'REVERSAL' ||
      tx.transaction_source === 'REFUND_REVERSAL') &&
    tx.transaction_status !== 'FAILED' &&
    tx.duplicate_status !== 'EXACT_DUPLICATE'
  );
}

function isCashbackTx(tx: Transaction): boolean {
  if (tx.direction !== 'CREDIT') return false;
  const cat = (tx.categories?.name || '').toLowerCase();
  const merch = (tx.merchant || '').toLowerCase();
  return cat === 'cashback' || merch.includes('cashback');
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

interface AnalyticsSnapshot {
  statsSummary: FinancialSummary | null;
  statsCashFlow: Array<{ month: string; income: number; expense: number }>;
  dailyTrend: Array<{ date: string; label: string; income: number; expense: number }>;
  bankSummary: Array<{
    bank_name: string;
    account_count: number;
    balance: number;
    total_credit: number;
    total_debit: number;
    tx_count: number;
  }>;
  transactions: Transaction[];
  accounts: Account[];
}

export default function AnalyticsPage() {
  const todayKey = useMemo(() => toIstDateKey(new Date()), []);
  const monthStartKey = `${todayKey.slice(0, 7)}-01`;

  const [period, setPeriod] = useState<PeriodKey>('THIS_MONTH');
  const [customFrom, setCustomFrom] = useState<string>(monthStartKey);
  const [customTo, setCustomTo] = useState<string>(todayKey);

  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [statsSummary, setStatsSummary] = useState<FinancialSummary | null>(null);
  const [statsCashFlow, setStatsCashFlow] = useState<
    Array<{ month: string; income: number; expense: number }>
  >([]);
  const [dailyTrend, setDailyTrend] = useState<
    Array<{ date: string; label: string; income: number; expense: number }>
  >([]);
  const [bankSummary, setBankSummary] = useState<
    Array<{
      bank_name: string;
      account_count: number;
      balance: number;
      total_credit: number;
      total_debit: number;
      tx_count: number;
    }>
  >([]);
  const [loading, setLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isCachedView, setIsCachedView] = useState(false);

  const { user, loading: authLoading } = useAuth();
  const { isOffline } = usePwa();

  const fetchAnalytics = useCallback(async () => {
    setIsRefreshing(true);
    setError(null);
    try {
      const activeTab = PERIOD_TABS.find((t) => t.key === period);
      const statsUrl = new URL('/api/stats', window.location.origin);
      if (activeTab) statsUrl.searchParams.set('period', activeTab.apiParam);
      if (period === 'CUSTOM') {
        if (customFrom) statsUrl.searchParams.set('from', customFrom);
        if (customTo) statsUrl.searchParams.set('to', customTo);
      }

      const [statsRes, txRes, accRes] = await Promise.all([
        fetch(statsUrl.toString()),
        fetch('/api/transactions?limit=2000'),
        fetch('/api/accounts'),
      ]);

      if (statsRes.status === 401 || txRes.status === 401 || accRes.status === 401) {
        setLoading(false);
        setIsRefreshing(false);
        return;
      }

      if (!statsRes.ok && !txRes.ok && !accRes.ok) {
        throw new Error('Failed to load analytics data from database');
      }

      let nextSummary = statsSummary;
      let nextCashFlow = statsCashFlow;
      let nextDaily = dailyTrend;
      let nextBank = bankSummary;
      let nextTxns = transactions;
      let nextAccs = accounts;

      if (statsRes.ok) {
        const statsData = await statsRes.json();
        nextSummary = statsData.summary || null;
        nextCashFlow = statsData.cashFlow || [];
        nextDaily = statsData.dailyTrend || [];
        nextBank = statsData.bankSummary || [];
        setStatsSummary(nextSummary);
        setStatsCashFlow(nextCashFlow);
        setDailyTrend(nextDaily);
        setBankSummary(nextBank);
      }

      if (txRes.ok) {
        const txData = await txRes.json();
        nextTxns = txData.transactions || [];
        setTransactions(nextTxns);
      }

      if (accRes.ok) {
        const accData = await accRes.json();
        nextAccs = accData.accounts || [];
        setAccounts(nextAccs);
      }

      setIsCachedView(false);
      void saveOfflineSnapshot<AnalyticsSnapshot>('analytics_snapshot', {
        statsSummary: nextSummary,
        statsCashFlow: nextCashFlow,
        dailyTrend: nextDaily,
        bankSummary: nextBank,
        transactions: nextTxns,
        accounts: nextAccs,
      });
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Unable to fetch analytics';
      console.error('Analytics fetch error:', err);
      const snap = await loadOfflineSnapshot<AnalyticsSnapshot>('analytics_snapshot');
      if (snap?.data) {
        setStatsSummary(snap.data.statsSummary);
        setStatsCashFlow(snap.data.statsCashFlow || []);
        setDailyTrend(snap.data.dailyTrend || []);
        setBankSummary(snap.data.bankSummary || []);
        setTransactions(snap.data.transactions || []);
        setAccounts(snap.data.accounts || []);
        setIsCachedView(true);
      } else {
        setError(msg);
      }
    } finally {
      setLoading(false);
      setIsRefreshing(false);
    }
  }, [period, customFrom, customTo]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (!authLoading && user) {
      void (async () => {
        const snap = await loadOfflineSnapshot<AnalyticsSnapshot>('analytics_snapshot');
        if (snap?.data) {
          setStatsSummary(snap.data.statsSummary);
          setStatsCashFlow(snap.data.statsCashFlow || []);
          setDailyTrend(snap.data.dailyTrend || []);
          setBankSummary(snap.data.bankSummary || []);
          setTransactions(snap.data.transactions || []);
          setAccounts(snap.data.accounts || []);
          setIsCachedView(true);
          setLoading(false);
        }
        if (typeof navigator === 'undefined' || navigator.onLine) {
          await fetchAnalytics();
        }
      })();
    } else if (!authLoading && !user) {
      setLoading(false);
    }
  }, [authLoading, user, fetchAnalytics]);

  useEffect(() => {
    const onSync = () => void fetchAnalytics();
    window.addEventListener('monzi:sms-synced', onSync);
    window.addEventListener('monzi:refresh', onSync);
    return () => {
      window.removeEventListener('monzi:sms-synced', onSync);
      window.removeEventListener('monzi:refresh', onSync);
    };
  }, [fetchAnalytics]);

  // Date window for selected period (IST) + previous equivalent comparison window
  const periodWindow = useMemo(() => {
    const now = new Date();
    const curKey = toIstDateKey(now);
    const [curY, curM, curD] = curKey.split('-').map(Number);

    if (period === 'TODAY') {
      const start = makeIstDate(curKey, false);
      const end = makeIstDate(curKey, true);
      const prevKey = shiftDateKey(curKey, -1);
      return {
        start,
        end,
        prevStart: makeIstDate(prevKey, false),
        prevEnd: makeIstDate(prevKey, true),
        days: 1,
        subtitle: `Today (${start.toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })})`,
        summaryTitle: 'Daily Automated Summary',
        prevLabel: 'vs Yesterday',
      };
    }

    if (period === 'YESTERDAY') {
      const yKey = shiftDateKey(curKey, -1);
      const prevKey = shiftDateKey(curKey, -2);
      const start = makeIstDate(yKey, false);
      const end = makeIstDate(yKey, true);
      return {
        start,
        end,
        prevStart: makeIstDate(prevKey, false),
        prevEnd: makeIstDate(prevKey, true),
        days: 1,
        subtitle: `Yesterday (${start.toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })})`,
        summaryTitle: 'Yesterday Automated Summary',
        prevLabel: 'vs Day Before',
      };
    }

    if (period === 'THIS_WEEK') {
      const istMid = makeIstDate(curKey, false);
      const dayOfWeek = istMid.getUTCDay(); // Sun=0, Mon=1..Sat=6 (note +05:30 shifts UTC day? Use getDay on IST local string)
      const weekdayShort = new Intl.DateTimeFormat('en-US', {
        timeZone: IST_TZ,
        weekday: 'short',
      }).format(now);
      const weekdayOffsetMap: Record<string, number> = {
        Mon: 0,
        Tue: 1,
        Wed: 2,
        Thu: 3,
        Fri: 4,
        Sat: 5,
        Sun: 6,
      };
      const offsetFromMon = weekdayOffsetMap[weekdayShort] ?? ((dayOfWeek + 6) % 7);
      const weekStartKey = shiftDateKey(curKey, -offsetFromMon);
      const prevWeekStartKey = shiftDateKey(weekStartKey, -7);
      const prevWeekEndKey = shiftDateKey(weekStartKey, -1);
      const start = makeIstDate(weekStartKey, false);
      const end = makeIstDate(curKey, true);
      const days = offsetFromMon + 1;
      return {
        start,
        end,
        prevStart: makeIstDate(prevWeekStartKey, false),
        prevEnd: makeIstDate(prevWeekEndKey, true),
        days,
        subtitle: `This Week (${start.toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })} – Today)`,
        summaryTitle: 'Weekly Automated Summary',
        prevLabel: 'vs Last Week',
      };
    }

    if (period === 'PREVIOUS_MONTH') {
      const pmDate = new Date(Date.UTC(curY, curM - 2, 1));
      const pmY = pmDate.getUTCFullYear();
      const pmM = pmDate.getUTCMonth() + 1;
      const lastDay = new Date(Date.UTC(pmY, pmM, 0)).getUTCDate();
      const startKey = `${pmY}-${pad2(pmM)}-01`;
      const endKey = `${pmY}-${pad2(pmM)}-${pad2(lastDay)}`;

      const pm2Date = new Date(Date.UTC(pmY, pmM - 2, 1));
      const pm2Y = pm2Date.getUTCFullYear();
      const pm2M = pm2Date.getUTCMonth() + 1;
      const pm2LastDay = new Date(Date.UTC(pm2Y, pm2M, 0)).getUTCDate();

      const start = makeIstDate(startKey, false);
      return {
        start,
        end: makeIstDate(endKey, true),
        prevStart: makeIstDate(`${pm2Y}-${pad2(pm2M)}-01`, false),
        prevEnd: makeIstDate(`${pm2Y}-${pad2(pm2M)}-${pad2(pm2LastDay)}`, true),
        days: lastDay,
        subtitle: start.toLocaleString('en-IN', { month: 'long', year: 'numeric', timeZone: IST_TZ }),
        summaryTitle: 'Previous Month Summary',
        prevLabel: 'vs 2 Months Ago',
      };
    }

    if (period === 'FINANCIAL_YEAR') {
      const fyStartYear = curM >= 4 ? curY : curY - 1;
      const fyStartKey = `${fyStartYear}-04-01`;
      const fyEndKey = `${fyStartYear + 1}-03-31`;
      const effectiveEndKey = curKey < fyEndKey ? curKey : fyEndKey;
      const start = makeIstDate(fyStartKey, false);
      const end = makeIstDate(effectiveEndKey, true);
      const days = Math.max(1, Math.ceil((end.getTime() - start.getTime()) / 86400000));
      return {
        start,
        end,
        prevStart: makeIstDate(`${fyStartYear - 1}-04-01`, false),
        prevEnd: makeIstDate(`${fyStartYear}-03-31`, true),
        days,
        subtitle: `FY ${fyStartYear}-${String((fyStartYear + 1) % 100).padStart(2, '0')} (Apr 1 – Mar 31 IST)`,
        summaryTitle: 'Financial Year Summary',
        prevLabel: `vs FY ${fyStartYear - 1}-${String(fyStartYear % 100).padStart(2, '0')}`,
      };
    }

    if (period === 'CUSTOM') {
      const fromKey = customFrom || `${curY}-${pad2(curM)}-01`;
      const toKey = customTo || curKey;
      const safeFrom = fromKey <= toKey ? fromKey : toKey;
      const safeTo = fromKey <= toKey ? toKey : fromKey;
      const start = makeIstDate(safeFrom, false);
      const end = makeIstDate(safeTo, true);
      const days = Math.max(1, Math.round((end.getTime() - start.getTime()) / 86400000));
      const prevEndKey = shiftDateKey(safeFrom, -1);
      const prevStartKey = shiftDateKey(safeFrom, -days);
      return {
        start,
        end,
        prevStart: makeIstDate(prevStartKey, false),
        prevEnd: makeIstDate(prevEndKey, true),
        days,
        subtitle: `${safeFrom} to ${safeTo}`,
        summaryTitle: 'Custom Period Summary',
        prevLabel: 'vs Prior Period',
      };
    }

    // THIS_MONTH default
    const startKey = `${curY}-${pad2(curM)}-01`;
    const pmDate = new Date(Date.UTC(curY, curM - 2, 1));
    const pmY = pmDate.getUTCFullYear();
    const pmM = pmDate.getUTCMonth() + 1;
    const pmLastDay = new Date(Date.UTC(pmY, pmM, 0)).getUTCDate();

    const start = makeIstDate(startKey, false);
    const end = makeIstDate(curKey, true);
    return {
      start,
      end,
      prevStart: makeIstDate(`${pmY}-${pad2(pmM)}-01`, false),
      prevEnd: makeIstDate(`${pmY}-${pad2(pmM)}-${pad2(pmLastDay)}`, true),
      days: Math.max(1, curD),
      subtitle: start.toLocaleString('en-IN', { month: 'long', year: 'numeric', timeZone: IST_TZ }),
      summaryTitle: 'Monthly Automated Summary',
      prevLabel: 'vs Previous Month',
    };
  }, [period, customFrom, customTo]);

  // Transactions inside the active period
  const periodTransactions = useMemo(() => {
    const startMs = periodWindow.start.getTime();
    const endMs = periodWindow.end.getTime();
    return transactions.filter((tx) => {
      const t = new Date(tx.transaction_date).getTime();
      return t >= startMs && t <= endMs;
    });
  }, [transactions, periodWindow]);

  // Transactions inside the comparison previous period
  const prevPeriodTransactions = useMemo(() => {
    const startMs = periodWindow.prevStart.getTime();
    const endMs = periodWindow.prevEnd.getTime();
    return transactions.filter((tx) => {
      const t = new Date(tx.transaction_date).getTime();
      return t >= startMs && t <= endMs;
    });
  }, [transactions, periodWindow]);

  // 2. Top KPIs + Refunds/Cashback + Automated Summary metrics (matching Android FinanceAnalyticsEngine)
  const kpis = useMemo(() => {
    let totalIn = 0;
    let grossOut = 0;
    let refundsTotal = 0;
    let cashbackTotal = 0;
    let highestExpense: Transaction | null = null;
    let validTxCount = 0;

    for (const tx of periodTransactions) {
      if (tx.transaction_status === 'FAILED' || tx.duplicate_status === 'EXACT_DUPLICATE') continue;
      const amt = Number(tx.amount) || 0;
      if (isRefundTx(tx)) {
        refundsTotal += amt;
        continue;
      }
      if (isExcludedTransaction(tx)) continue;

      validTxCount += 1;
      if (tx.direction === 'CREDIT') {
        if (isCashbackTx(tx)) {
          cashbackTotal += amt;
        } else {
          totalIn += amt;
        }
      } else {
        grossOut += amt;
        if (!highestExpense || amt > Number(highestExpense.amount)) {
          highestExpense = tx;
        }
      }
    }
    let totalOut = Math.max(0, grossOut - refundsTotal);

    // Previous period comparison
    let prevIn = 0;
    let prevGrossOut = 0;
    let prevRefunds = 0;
    for (const tx of prevPeriodTransactions) {
      if (tx.transaction_status === 'FAILED' || tx.duplicate_status === 'EXACT_DUPLICATE') continue;
      const amt = Number(tx.amount) || 0;
      if (isRefundTx(tx)) {
        prevRefunds += amt;
        continue;
      }
      if (isExcludedTransaction(tx)) continue;
      if (tx.direction === 'CREDIT') {
        if (!isCashbackTx(tx)) prevIn += amt;
      } else {
        prevGrossOut += amt;
      }
    }
    const prevOut = Math.max(0, prevGrossOut - prevRefunds);

    if (period === 'THIS_MONTH' && statsSummary && periodTransactions.length === 0) {
      totalIn = statsSummary.monthly_income;
      totalOut = statsSummary.monthly_expense;
    }

    const net = totalIn - totalOut;
    const savingsRate = totalIn > 0 ? Math.round((net / totalIn) * 1000) / 10 : 0;
    const avgDailySpend = Math.round(totalOut / periodWindow.days);

    const expenseChangeVsPrevPct =
      prevOut > 0 ? Math.round(((totalOut - prevOut) / prevOut) * 1000) / 10 : null;
    const incomeChangeVsPrevPct =
      prevIn > 0 ? Math.round(((totalIn - prevIn) / prevIn) * 1000) / 10 : null;

    return {
      totalIn,
      totalOut,
      net,
      savingsRate,
      avgDailySpend,
      refundsTotal,
      cashbackTotal,
      highestExpense,
      validTxCount,
      prevIn,
      prevOut,
      expenseChangeVsPrevPct,
      incomeChangeVsPrevPct,
    };
  }, [periodTransactions, prevPeriodTransactions, period, statsSummary, periodWindow.days]);

  // 3. Trend chart data (adapts to selected FRD period)
  const trendData = useMemo(() => {
    const valid = periodTransactions.filter((tx) => !isExcludedTransaction(tx));

    // Financial Year or Custom > 45 days -> Monthly buckets
    if (period === 'FINANCIAL_YEAR' || (period === 'CUSTOM' && periodWindow.days > 45)) {
      if (valid.length === 0) {
        return period === 'FINANCIAL_YEAR' && statsCashFlow.length > 0
          ? statsCashFlow.map((c) => ({ label: c.month, income: c.income, expense: c.expense }))
          : [];
      }
      const monthBuckets = new Map<string, { label: string; income: number; expense: number }>();
      for (const tx of valid) {
        const dKey = toIstDateKey(new Date(tx.transaction_date));
        const mKey = dKey.slice(0, 7);
        if (!monthBuckets.has(mKey)) {
          const [y, m] = mKey.split('-').map(Number);
          const label = new Date(Date.UTC(y, m - 1, 15)).toLocaleString('en-IN', {
            month: 'short',
            year: '2-digit',
            timeZone: IST_TZ,
          });
          monthBuckets.set(mKey, { label, income: 0, expense: 0 });
        }
        const b = monthBuckets.get(mKey)!;
        const amt = Number(tx.amount) || 0;
        if (tx.direction === 'CREDIT') {
          if (!isCashbackTx(tx)) b.income += amt;
        } else {
          b.expense += amt;
        }
      }
      return Array.from(monthBuckets.entries())
        .sort(([a], [b]) => a.localeCompare(b))
        .map(([, v]) => ({
          label: v.label,
          income: Math.round(v.income),
          expense: Math.round(v.expense),
        }));
    }

    // Today / Yesterday / This Week / Custom <= 14 days -> Daily buckets
    if (period === 'TODAY' || period === 'YESTERDAY' || period === 'THIS_WEEK' || (period === 'CUSTOM' && periodWindow.days <= 14)) {
      if (valid.length === 0) return [];
      const dayBuckets = new Map<string, { label: string; income: number; expense: number }>();
      for (const tx of valid) {
        const dKey = toIstDateKey(new Date(tx.transaction_date));
        if (!dayBuckets.has(dKey)) {
          const label = makeIstDate(dKey, false).toLocaleDateString('en-IN', {
            day: 'numeric',
            month: 'short',
            timeZone: IST_TZ,
          });
          dayBuckets.set(dKey, { label, income: 0, expense: 0 });
        }
        const b = dayBuckets.get(dKey)!;
        const amt = Number(tx.amount) || 0;
        if (tx.direction === 'CREDIT') {
          if (!isCashbackTx(tx)) b.income += amt;
        } else {
          b.expense += amt;
        }
      }
      return Array.from(dayBuckets.entries())
        .sort(([a], [b]) => a.localeCompare(b))
        .map(([, v]) => ({
          label: v.label,
          income: Math.round(v.income),
          expense: Math.round(v.expense),
        }));
    }

    // Weekly buckets for This Month / Previous Month / Custom 15-45 days
    if (valid.length === 0) return [];
    const weeks = [
      { label: 'Days 1-7', income: 0, expense: 0 },
      { label: 'Days 8-14', income: 0, expense: 0 },
      { label: 'Days 15-21', income: 0, expense: 0 },
      { label: 'Days 22-28', income: 0, expense: 0 },
      { label: 'Days 29+', income: 0, expense: 0 },
    ];

    for (const tx of valid) {
      const day = Number(toIstDateKey(new Date(tx.transaction_date)).slice(8, 10));
      const idx = Math.min(4, Math.floor((day - 1) / 7));
      const amt = Number(tx.amount) || 0;
      if (tx.direction === 'CREDIT') {
        if (!isCashbackTx(tx)) weeks[idx].income += amt;
      } else {
        weeks[idx].expense += amt;
      }
    }

    return weeks
      .filter((_, idx) => idx < 4 || weeks[4].income > 0 || weeks[4].expense > 0)
      .map((w) => ({
        label: w.label,
        income: Math.round(w.income),
        expense: Math.round(w.expense),
      }));
  }, [periodTransactions, period, periodWindow.days, statsCashFlow]);

  // 4. Category Breakdown for selected period (net of refunds, matching Android)
  const categoryData = useMemo(() => {
    const totals: Record<string, { value: number; count: number; color: string }> = {};
    const refundsByCat: Record<string, number> = {};
    const txById = new Map<string, Transaction>();
    for (const t of transactions) txById.set(t.id, t);

    for (const tx of periodTransactions) {
      if (tx.transaction_status === 'FAILED' || tx.duplicate_status === 'EXACT_DUPLICATE') continue;
      if (isRefundTx(tx)) {
        const orig = tx.linked_transaction_id ? txById.get(tx.linked_transaction_id) : undefined;
        const rCat = orig?.categories?.name || tx.categories?.name || 'Uncategorized';
        refundsByCat[rCat] = (refundsByCat[rCat] || 0) + (Number(tx.amount) || 0);
        continue;
      }
      if (isExcludedTransaction(tx) || tx.direction !== 'DEBIT') continue;
      const name = tx.categories?.name || 'Uncategorized';
      const color = tx.categories?.color || '#94A3B8';
      if (!totals[name]) totals[name] = { value: 0, count: 0, color };
      totals[name].value += Number(tx.amount) || 0;
      totals[name].count += 1;
    }

    const entries = Object.entries(totals)
      .map(([name, info], idx) => {
        const net = Math.max(0, info.value - (refundsByCat[name] || 0));
        return {
          name,
          value: Math.round(net),
          count: info.count,
          color: info.color || FALLBACK_COLORS[idx % FALLBACK_COLORS.length],
        };
      })
      .filter((e) => e.value > 0)
      .sort((a, b) => b.value - a.value);

    const total = entries.reduce((sum, c) => sum + c.value, 0);
    return {
      total,
      items: entries.map((e) => ({
        ...e,
        pct: total > 0 ? Math.round((e.value / total) * 100) : 0,
      })),
    };
  }, [periodTransactions, transactions]);

  // 5. Data-Driven Financial Insights (strictly derived from real DB records)
  const insights = useMemo(() => {
    const topCategory = categoryData.items[0] || null;

    const mostFrequentCategory =
      categoryData.items.length > 0
        ? [...categoryData.items].sort((a, b) => b.count - a.count)[0]
        : null;

    // MoM spending change strictly from DB current month vs previous month
    const curKey = toIstDateKey(new Date());
    const curMonthKey = curKey.slice(0, 7);
    const [curY, curM] = curKey.split('-').map(Number);
    const pmDate = new Date(Date.UTC(curY, curM - 2, 1));
    const prevMonthKey = `${pmDate.getUTCFullYear()}-${pad2(pmDate.getUTCMonth() + 1)}`;

    let curMonthSpend = 0;
    let prevMonthSpend = 0;
    for (const tx of transactions) {
      if (isExcludedTransaction(tx) || tx.direction !== 'DEBIT') continue;
      const mKey = toIstDateKey(new Date(tx.transaction_date)).slice(0, 7);
      const amt = Number(tx.amount) || 0;
      if (mKey === curMonthKey) curMonthSpend += amt;
      else if (mKey === prevMonthKey) prevMonthSpend += amt;
    }

    const momChangePct =
      prevMonthSpend > 0
        ? Math.round(((curMonthSpend - prevMonthSpend) / prevMonthSpend) * 1000) / 10
        : statsSummary?.expense_change_pct ?? null;

    let savingsHealthLabel = 'No Income Recorded';
    let savingsHealthDetail = 'Income credits needed to calculate savings health.';
    let savingsHealthTone: 'emerald' | 'blue' | 'amber' | 'rose' | 'slate' = 'slate';

    if (kpis.totalIn > 0) {
      if (kpis.savingsRate >= 30) {
        savingsHealthLabel = 'Healthy Surplus';
        savingsHealthDetail = `Retaining ${kpis.savingsRate}% of period income (${formatInr(kpis.net)} net).`;
        savingsHealthTone = 'emerald';
      } else if (kpis.savingsRate >= 10) {
        savingsHealthLabel = 'Moderate Savings';
        savingsHealthDetail = `Saving ${kpis.savingsRate}% of period income (${formatInr(kpis.net)} net).`;
        savingsHealthTone = 'blue';
      } else if (kpis.savingsRate >= 0) {
        savingsHealthLabel = 'Tight Margin';
        savingsHealthDetail = `Only ${kpis.savingsRate}% net surplus remaining in period.`;
        savingsHealthTone = 'amber';
      } else {
        savingsHealthLabel = 'Spending Exceeds Income';
        savingsHealthDetail = `Outflows exceed inflows by ${formatInr(Math.abs(kpis.net))}.`;
        savingsHealthTone = 'rose';
      }
    }

    return {
      topCategory,
      mostFrequentCategory,
      curMonthSpend,
      prevMonthSpend,
      momChangePct,
      savingsHealthLabel,
      savingsHealthDetail,
      savingsHealthTone,
    };
  }, [categoryData.items, transactions, statsSummary?.expense_change_pct, kpis]);

  // 6. Top Merchants list (grouped from /api/transactions)
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

  // 7. Account-wise breakdown
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

  const trendSubtitle =
    period === 'FINANCIAL_YEAR' || (period === 'CUSTOM' && periodWindow.days > 45)
      ? 'Monthly breakdown'
      : period === 'TODAY' || period === 'YESTERDAY' || period === 'THIS_WEEK' || (period === 'CUSTOM' && periodWindow.days <= 14)
      ? 'Daily breakdown'
      : 'Weekly breakdown';

  return (
    <div className="flex flex-col min-h-screen">
      <Header onRefresh={fetchAnalytics} isRefreshing={isRefreshing} />

      <div className="p-4 sm:p-6 md:p-8 space-y-5 md:space-y-7 max-w-7xl mx-auto w-full">
        {/* ── Header + Section 1: FRD 9.4 Period Selector Tabs ────────────── */}
        <div className="space-y-3">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-xl sm:text-2xl font-bold tracking-tight text-slate-900">
                  Financial Analytics
                </h2>
                {(isOffline || isCachedView) && (
                  <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-amber-50 text-amber-800 border border-amber-200">
                    <WifiOff className="w-3 h-3" />
                    Cached
                  </span>
                )}
              </div>
              <p className="text-xs sm:text-sm text-slate-500 font-medium mt-0.5">
                Verified database calculations • {periodWindow.subtitle}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-1.5 overflow-x-auto pb-1 scrollbar-none">
            {PERIOD_TABS.map((tab) => {
              const active = period === tab.key;
              return (
                <button
                  key={tab.key}
                  onClick={() => setPeriod(tab.key)}
                  className={`min-h-[40px] px-3.5 py-2 rounded-xl text-xs font-bold whitespace-nowrap transition-all shrink-0 ${
                    active
                      ? 'bg-gradient-to-r from-blue-600 to-indigo-600 text-white shadow-sm shadow-blue-500/25'
                      : 'bg-white/75 border border-white/60 text-slate-600 hover:text-slate-900 hover:bg-white'
                  }`}
                >
                  {tab.label}
                </button>
              );
            })}
          </div>

          {period === 'CUSTOM' && (
            <GlassCard className="p-3.5 flex flex-wrap items-center gap-3 bg-white/80">
              <div className="flex items-center gap-2">
                <label className="text-[11px] font-bold uppercase tracking-wider text-slate-500">
                  From
                </label>
                <input
                  type="date"
                  value={customFrom}
                  onChange={(e) => setCustomFrom(e.target.value)}
                  className="bg-white border border-slate-200 rounded-xl px-3 py-2 text-xs font-semibold text-slate-800 min-h-[40px]"
                />
              </div>
              <div className="flex items-center gap-2">
                <label className="text-[11px] font-bold uppercase tracking-wider text-slate-500">
                  To
                </label>
                <input
                  type="date"
                  value={customTo}
                  onChange={(e) => setCustomTo(e.target.value)}
                  className="bg-white border border-slate-200 rounded-xl px-3 py-2 text-xs font-semibold text-slate-800 min-h-[40px]"
                />
              </div>
            </GlassCard>
          )}
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
                Excl. refunds &amp; transfers
              </p>
            </GlassCard>

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
                {insights.savingsHealthLabel}
              </p>
            </GlassCard>

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

        {/* ── Automated Financial Summary & Data-Driven Insights (FRD 11 & 12) ── */}
        {!loading && (
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-5 md:gap-6">
            {/* Automated Financial Summary Card */}
            <GlassCard className="lg:col-span-6 p-5 sm:p-6 bg-gradient-to-br from-white/85 via-blue-50/35 to-indigo-50/35 space-y-4">
              <div className="flex items-start justify-between gap-2">
                <div className="flex items-center gap-2.5">
                  <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-blue-600 to-indigo-600 text-white flex items-center justify-center shrink-0">
                    <Sparkles className="w-4 h-4" />
                  </div>
                  <div>
                    <h3 className="text-sm sm:text-base font-bold text-slate-900">
                      {periodWindow.summaryTitle}
                    </h3>
                    <p className="text-[11px] text-slate-500 font-medium">
                      {kpis.validTxCount} counted txns • Refunds ({formatInr(kpis.refundsTotal)}) &amp; Cashback ({formatInr(kpis.cashbackTotal)}) separated
                    </p>
                  </div>
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                {/* Highest Expense Record */}
                <div className="p-3.5 rounded-2xl bg-white/85 border border-slate-200/70">
                  <p className="text-[10px] font-bold uppercase tracking-wider text-slate-400 flex items-center gap-1">
                    <Receipt className="w-3 h-3" />
                    Highest Expense
                  </p>
                  {kpis.highestExpense ? (
                    <div className="mt-1.5">
                      <p className="text-base font-bold font-mono text-rose-600">
                        {formatInr(Number(kpis.highestExpense.amount))}
                      </p>
                      <p className="text-xs font-semibold text-slate-800 truncate mt-0.5">
                        {kpis.highestExpense.merchant || 'Expense'}
                      </p>
                      <p className="text-[10px] text-slate-400 mt-0.5">
                        {kpis.highestExpense.categories?.name || 'Uncategorized'} •{' '}
                        {new Date(kpis.highestExpense.transaction_date).toLocaleDateString('en-IN', {
                          day: 'numeric',
                          month: 'short',
                        })}
                      </p>
                    </div>
                  ) : (
                    <p className="text-xs font-medium text-slate-500 mt-2">
                      No expenses recorded in period
                    </p>
                  )}
                </div>

                {/* Comparison vs Previous Period */}
                <div className="p-3.5 rounded-2xl bg-white/85 border border-slate-200/70">
                  <p className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
                    Spend {periodWindow.prevLabel}
                  </p>
                  {kpis.expenseChangeVsPrevPct !== null ? (
                    <div className="mt-1.5">
                      <p
                        className={`text-base font-bold font-mono flex items-center gap-1 ${
                          kpis.expenseChangeVsPrevPct > 0 ? 'text-rose-600' : 'text-emerald-600'
                        }`}
                      >
                        {kpis.expenseChangeVsPrevPct > 0 ? (
                          <ArrowUpRight className="w-4 h-4" />
                        ) : (
                          <ArrowDownRight className="w-4 h-4" />
                        )}
                        {kpis.expenseChangeVsPrevPct > 0 ? '+' : ''}
                        {kpis.expenseChangeVsPrevPct}%
                      </p>
                      <p className="text-xs text-slate-600 mt-0.5">
                        Prior: <span className="font-mono font-semibold">{formatInr(kpis.prevOut)}</span>
                      </p>
                      {kpis.incomeChangeVsPrevPct !== null && (
                        <p className="text-[10px] text-slate-400 mt-0.5">
                          Income {kpis.incomeChangeVsPrevPct > 0 ? '+' : ''}
                          {kpis.incomeChangeVsPrevPct}% ({formatInr(kpis.prevIn)})
                        </p>
                      )}
                    </div>
                  ) : (
                    <p className="text-xs font-medium text-slate-500 mt-2">
                      No prior period expenses to compare
                    </p>
                  )}
                </div>
              </div>

              {/* Top Categories Mini Strip */}
              {categoryData.items.length > 0 && (
                <div className="pt-2 border-t border-slate-200/60 flex flex-wrap gap-2">
                  {categoryData.items.slice(0, 3).map((cat) => (
                    <span
                      key={cat.name}
                      className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-semibold bg-white border border-slate-200/80 text-slate-700"
                    >
                      <span
                        className="w-2 h-2 rounded-full"
                        style={{ backgroundColor: cat.color }}
                      />
                      <span>{cat.name}:</span>
                      <span className="font-mono font-bold">{formatInr(cat.value)}</span>
                      <span className="text-slate-400">({cat.pct}%)</span>
                    </span>
                  ))}
                </div>
              )}
            </GlassCard>

            {/* Data-Driven Financial Insights Card */}
            <GlassCard className="lg:col-span-6 p-5 sm:p-6 space-y-4">
              <div className="flex items-center gap-2.5">
                <div className="w-9 h-9 rounded-xl bg-amber-50 text-amber-600 flex items-center justify-center shrink-0">
                  <Lightbulb className="w-4 h-4" />
                </div>
                <div>
                  <h3 className="text-sm sm:text-base font-bold text-slate-900">
                    Data-Driven Financial Insights
                  </h3>
                  <p className="text-[11px] text-slate-500 font-medium">
                    Computed strictly from verified database transactions
                  </p>
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div className="p-3.5 rounded-2xl bg-slate-50/80 border border-slate-200/60">
                  <p className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
                    Highest Spending Category
                  </p>
                  {insights.topCategory ? (
                    <div className="mt-1">
                      <p className="text-sm font-bold text-slate-900 truncate">
                        {insights.topCategory.name}
                      </p>
                      <p className="text-xs font-mono font-bold text-rose-600 mt-0.5">
                        {formatInr(insights.topCategory.value)} ({insights.topCategory.pct}% of spend)
                      </p>
                    </div>
                  ) : (
                    <p className="text-xs text-slate-500 mt-1">No category spend in period</p>
                  )}
                </div>

                <div className="p-3.5 rounded-2xl bg-slate-50/80 border border-slate-200/60">
                  <p className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
                    MoM Spending Change
                  </p>
                  {insights.momChangePct !== null ? (
                    <div className="mt-1">
                      <p
                        className={`text-sm font-bold font-mono ${
                          insights.momChangePct > 0 ? 'text-rose-600' : 'text-emerald-600'
                        }`}
                      >
                        {insights.momChangePct > 0 ? '+' : ''}
                        {insights.momChangePct}% vs last month
                      </p>
                      <p className="text-[11px] text-slate-500 mt-0.5 font-mono">
                        {formatInr(insights.curMonthSpend)} vs {formatInr(insights.prevMonthSpend)}
                      </p>
                    </div>
                  ) : (
                    <p className="text-xs text-slate-500 mt-1">
                      Needs previous month data for MoM delta
                    </p>
                  )}
                </div>

                <div className="p-3.5 rounded-2xl bg-slate-50/80 border border-slate-200/60">
                  <p className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
                    Savings Rate Health
                  </p>
                  <p
                    className={`text-sm font-bold mt-1 ${
                      insights.savingsHealthTone === 'emerald'
                        ? 'text-emerald-700'
                        : insights.savingsHealthTone === 'blue'
                        ? 'text-blue-700'
                        : insights.savingsHealthTone === 'amber'
                        ? 'text-amber-700'
                        : insights.savingsHealthTone === 'rose'
                        ? 'text-rose-600'
                        : 'text-slate-600'
                    }`}
                  >
                    {insights.savingsHealthLabel}
                  </p>
                  <p className="text-[11px] text-slate-500 mt-0.5">
                    {insights.savingsHealthDetail}
                  </p>
                </div>

                <div className="p-3.5 rounded-2xl bg-slate-50/80 border border-slate-200/60">
                  <p className="text-[10px] font-bold uppercase tracking-wider text-slate-400 flex items-center gap-1">
                    <Repeat className="w-3 h-3" />
                    Most Frequent Category
                  </p>
                  {insights.mostFrequentCategory ? (
                    <div className="mt-1">
                      <p className="text-sm font-bold text-slate-900 truncate">
                        {insights.mostFrequentCategory.name}
                      </p>
                      <p className="text-[11px] text-slate-500 mt-0.5">
                        {insights.mostFrequentCategory.count}{' '}
                        {insights.mostFrequentCategory.count === 1 ? 'transaction' : 'transactions'} •{' '}
                        <span className="font-mono font-semibold">
                          {formatInr(insights.mostFrequentCategory.value)}
                        </span>
                      </p>
                    </div>
                  ) : (
                    <p className="text-xs text-slate-500 mt-1">No transactions in period</p>
                  )}
                </div>
              </div>
            </GlassCard>
          </div>
        )}

        {/* ── Daily Income & Expense Chart (Last 14 Days) ─────────────────── */}
        <GlassCard className="p-4 sm:p-6">
          <div className="flex items-center justify-between mb-5">
            <div>
              <h3 className="text-sm sm:text-base font-bold text-slate-900 tracking-tight">
                Daily Income &amp; Expense (Last 14 Days)
              </h3>
              <p className="text-[11px] sm:text-xs text-slate-500 font-medium mt-0.5">
                Day-by-day automated cash flow tracking
              </p>
            </div>
            {dailyTrend.some((d) => d.income > 0 || d.expense > 0) && !loading && (
              <div className="flex items-center gap-3 text-[11px] sm:text-xs font-semibold">
                <span className="flex items-center gap-1.5 text-slate-600">
                  <span className="w-2.5 h-2.5 rounded-full bg-emerald-500" />
                  Income
                </span>
                <span className="flex items-center gap-1.5 text-slate-600">
                  <span className="w-2.5 h-2.5 rounded-full bg-rose-500" />
                  Expense
                </span>
              </div>
            )}
          </div>

          {loading ? (
            <div className="h-[220px] flex items-end gap-2 px-2 pb-2">
              {Array.from({ length: 14 }).map((_, i) => (
                <div key={i} className="flex-1 flex items-end gap-1 h-full pt-6">
                  <Skeleton className="flex-1 rounded-t-md h-3/5" />
                  <Skeleton className="flex-1 rounded-t-md h-2/5" />
                </div>
              ))}
            </div>
          ) : !dailyTrend.some((d) => d.income > 0 || d.expense > 0) ? (
            <EmptyState
              icon={BarChart3}
              title="No Daily Activity Yet"
              description="Sync bank SMS to populate your 14-day daily cash flow chart."
              className="h-[220px] !py-6 !shadow-none border border-dashed border-slate-200/80 bg-slate-50/40"
            />
          ) : (
            <div className="h-[240px] w-full">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={dailyTrend} margin={{ top: 8, right: 6, left: -12, bottom: 0 }} barGap={2}>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#E2E8F0" opacity={0.65} />
                  <XAxis
                    dataKey="label"
                    tickLine={false}
                    axisLine={false}
                    tick={{ fill: '#64748B', fontSize: 10, fontWeight: 500 }}
                    interval="preserveStartEnd"
                  />
                  <YAxis
                    tickLine={false}
                    axisLine={false}
                    tick={{ fill: '#64748B', fontSize: 11 }}
                    tickFormatter={formatInrShort}
                    width={50}
                  />
                  <Tooltip content={<TrendTooltip />} cursor={{ fill: 'rgba(226,232,240,0.35)', radius: 8 }} />
                  <Bar dataKey="income" fill="#10B981" radius={[4, 4, 0, 0]} maxBarSize={20} />
                  <Bar dataKey="expense" fill="#EF4444" radius={[4, 4, 0, 0]} maxBarSize={20} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}
        </GlassCard>

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
                  {trendSubtitle} ({periodWindow.subtitle})
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
                  Balance &amp; period cash flow per account
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
                description="Add a bank account or sync bank SMS to see account-level analytics."
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

        {/* ── Section 7: Bank-Wise Summary ────────────────────────────────── */}
        <GlassCard className="p-4 sm:p-6">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h3 className="text-sm sm:text-base font-bold text-slate-900 tracking-tight">
                Bank-Wise Summary
              </h3>
              <p className="text-[11px] sm:text-xs text-slate-500 font-medium mt-0.5">
                Aggregated balances, credits, and debits grouped by bank
              </p>
            </div>
            <span className="text-[11px] font-semibold text-slate-500 bg-slate-100 px-2.5 py-1 rounded-full">
              {bankSummary.length} {bankSummary.length === 1 ? 'bank' : 'banks'}
            </span>
          </div>

          {loading ? (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3.5">
              {[0, 1, 2].map((i) => (
                <div key={i} className="p-4 rounded-2xl border border-slate-100 bg-white/50 space-y-2.5">
                  <Skeleton className="h-4 w-32 rounded-full" />
                  <Skeleton className="h-6 w-24 rounded-lg" />
                  <Skeleton className="h-3 w-full rounded-full" />
                </div>
              ))}
            </div>
          ) : bankSummary.length === 0 ? (
            <EmptyState
              icon={Landmark}
              title="No Bank Summary Available"
              description="Link or sync at least one bank account to see bank-level aggregates."
              className="!py-8 !shadow-none border border-dashed border-slate-200/80 bg-slate-50/40"
            />
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3.5">
              {bankSummary.map((bank) => (
                <div
                  key={bank.bank_name}
                  className="p-4 rounded-2xl bg-white/75 border border-slate-200/70 space-y-2.5"
                >
                  <div className="flex items-center justify-between gap-2">
                    <div className="flex items-center gap-2.5 min-w-0">
                      <div className="w-9 h-9 rounded-xl bg-blue-50 text-blue-600 flex items-center justify-center shrink-0">
                        <Landmark className="w-4 h-4" />
                      </div>
                      <div className="min-w-0">
                        <p className="text-sm font-bold text-slate-900 truncate">{bank.bank_name}</p>
                        <p className="text-[11px] text-slate-500">
                          {bank.account_count} {bank.account_count === 1 ? 'account' : 'accounts'} •{' '}
                          {bank.tx_count} txns
                        </p>
                      </div>
                    </div>
                    <p
                      className={`text-sm sm:text-base font-bold font-mono shrink-0 ${
                        bank.balance < 0 ? 'text-rose-600' : 'text-slate-900'
                      }`}
                    >
                      {formatInr(bank.balance)}
                    </p>
                  </div>
                  <div className="pt-2 border-t border-slate-100 flex items-center justify-between text-xs font-mono font-semibold">
                    <span className="text-emerald-600">+{formatInr(bank.total_credit)} credit</span>
                    <span className="text-rose-500">-{formatInr(bank.total_debit)} debit</span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </GlassCard>
      </div>
    </div>
  );
}
