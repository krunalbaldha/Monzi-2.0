'use client';

import React, { useState, useEffect, useCallback } from 'react';
import { Header } from '@/components/layout/Header';
import { GlassCard } from '@/components/ui/GlassCard';
import { CashFlowChart } from '@/components/dashboard/CashFlowChart';
import { CategoryBreakdown } from '@/components/dashboard/CategoryBreakdown';
import { TrendingUp, Target, Zap, Calendar, Award } from 'lucide-react';
import { FinancialSummary, Transaction } from '@/lib/types';

export default function AnalyticsPage() {
  const [cashFlow, setCashFlow] = useState<Array<{ month: string; income: number; expense: number }>>([]);
  const [categories, setCategories] = useState<Array<{ name: string; value: number; color: string }>>([]);
  const [summary, setSummary] = useState<FinancialSummary | null>(null);
  const [topCategory, setTopCategory] = useState<{ name: string; amount: number } | null>(null);
  const [largestExpense, setLargestExpense] = useState<{ merchant: string; amount: number } | null>(null);
  const [avgDailySpend, setAvgDailySpend] = useState<number>(0);
  const [timeRange, setTimeRange] = useState<'1M' | '3M' | '6M' | '1Y'>('1M');
  const [loading, setLoading] = useState(true);

  const fetchAnalytics = useCallback(async () => {
    setLoading(true);
    try {
      const [statsRes, txRes] = await Promise.all([
        fetch('/api/stats'),
        fetch('/api/transactions?limit=200'),
      ]);

      if (statsRes.ok) {
        const statsData = await statsRes.json();
        setSummary(statsData.summary);
        setCashFlow(statsData.cashFlow || []);
        const cats = statsData.categoryBreakdown || [];
        setCategories(cats);

        if (cats.length > 0) {
          setTopCategory({ name: cats[0].name, amount: cats[0].value });
        } else {
          setTopCategory(null);
        }

        if (statsData.summary?.monthly_expense) {
          const daysPassed = Math.max(1, new Date().getDate());
          setAvgDailySpend(Math.round(statsData.summary.monthly_expense / daysPassed));
        }
      }

      if (txRes.ok) {
        const txData = await txRes.json();
        const txList: Transaction[] = txData.transactions || [];
        const debits = txList.filter((t) => t.direction === 'DEBIT' && !t.is_excluded_from_totals);
        if (debits.length > 0) {
          debits.sort((a, b) => Number(b.amount) - Number(a.amount));
          setLargestExpense({
            merchant: debits[0].merchant || 'Expense',
            amount: Number(debits[0].amount),
          });
        } else {
          setLargestExpense(null);
        }
      }
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchAnalytics();
  }, [fetchAnalytics]);

  const formatInr = (val: number) => {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 0,
    }).format(val);
  };

  const savingsRate = summary?.savings_rate || 0;

  return (
    <div className="flex flex-col min-h-screen">
      <Header onRefresh={fetchAnalytics} isRefreshing={loading} />

      <div className="p-4 sm:p-6 md:p-8 space-y-5 md:space-y-8 max-w-7xl mx-auto w-full">
        {/* Header & Time Filter */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div>
            <h2 className="text-lg sm:text-xl font-bold tracking-tight text-slate-900">Financial Analytics</h2>
            <p className="text-[11px] sm:text-xs text-slate-500 font-medium">
              Real-time insights aggregated from your live database records
            </p>
          </div>

          <div className="flex items-center gap-1 p-1 bg-white border border-slate-200/80 rounded-xl shadow-sm text-xs font-semibold">
            {(['1M', '3M', '6M', '1Y'] as const).map((r) => (
              <button
                key={r}
                onClick={() => setTimeRange(r)}
                className={`px-3 py-1 rounded-lg transition-all ${
                  timeRange === r ? 'bg-slate-900 text-white shadow-sm' : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                {r === '1M' ? 'This Month' : r === '3M' ? '3 Months' : r === '6M' ? '6 Months' : '1 Year'}
              </button>
            ))}
          </div>
        </div>

        {/* Dynamic Insight Highlights */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 sm:gap-5">
          {/* Savings Velocity */}
          <GlassCard className="p-4 sm:p-5 flex items-center sm:items-start gap-3.5 sm:gap-4">
            <div className="w-10 h-10 rounded-2xl bg-emerald-50 text-emerald-600 flex items-center justify-center shrink-0">
              <TrendingUp className="w-5 h-5" />
            </div>
            <div>
              <p className="text-[10px] sm:text-xs font-semibold text-slate-400 uppercase tracking-wider">
                Savings Velocity
              </p>
              <p className="text-base sm:text-xl font-bold text-slate-900 font-mono mt-0.5">
                {savingsRate}%
              </p>
              <p className="text-[11px] sm:text-xs text-emerald-600 font-medium mt-0.5">
                {savingsRate >= 50 ? 'Strong savings velocity' : 'Tracking positive cash flow'}
              </p>
            </div>
          </GlassCard>

          {/* Top Spending Category */}
          <GlassCard className="p-4 sm:p-5 flex items-center sm:items-start gap-3.5 sm:gap-4">
            <div className="w-10 h-10 rounded-2xl bg-amber-50 text-amber-600 flex items-center justify-center shrink-0">
              <Target className="w-5 h-5" />
            </div>
            <div>
              <p className="text-[10px] sm:text-xs font-semibold text-slate-400 uppercase tracking-wider">
                Top Expense Category
              </p>
              <p className="text-base sm:text-xl font-bold text-slate-900 font-mono mt-0.5 truncate max-w-[180px]">
                {topCategory ? topCategory.name : 'None yet'}
              </p>
              <p className="text-[11px] sm:text-xs text-slate-500 font-medium mt-0.5">
                {topCategory ? `${formatInr(topCategory.amount)} spent` : 'Record debits to see ranking'}
              </p>
            </div>
          </GlassCard>

          {/* Largest Single Expense */}
          <GlassCard className="p-4 sm:p-5 flex items-center sm:items-start gap-3.5 sm:gap-4">
            <div className="w-10 h-10 rounded-2xl bg-blue-50 text-blue-600 flex items-center justify-center shrink-0">
              <Zap className="w-5 h-5" />
            </div>
            <div>
              <p className="text-[10px] sm:text-xs font-semibold text-slate-400 uppercase tracking-wider">
                Largest Single Expense
              </p>
              <p className="text-base sm:text-xl font-bold text-slate-900 font-mono mt-0.5">
                {largestExpense ? formatInr(largestExpense.amount) : '₹0'}
              </p>
              <p className="text-[11px] sm:text-xs text-blue-600 font-medium mt-0.5 truncate max-w-[180px]">
                {largestExpense ? largestExpense.merchant : 'No expenses recorded'}
              </p>
            </div>
          </GlassCard>
        </div>

        {/* Charts Grid */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-5 md:gap-6">
          <div className="lg:col-span-2">
            <CashFlowChart data={cashFlow} />
          </div>
          <div>
            <CategoryBreakdown categories={categories} />
          </div>
        </div>
      </div>
    </div>
  );
}
