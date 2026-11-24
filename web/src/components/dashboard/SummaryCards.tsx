import React from 'react';
import { Wallet, TrendingUp, TrendingDown, PiggyBank, ArrowUpRight, ArrowDownRight } from 'lucide-react';
import { GlassCard } from '@/components/ui/GlassCard';
import { FinancialSummary } from '@/lib/types';

interface SummaryCardsProps {
  summary: FinancialSummary;
}

export function SummaryCards({ summary }: SummaryCardsProps) {
  const formatInr = (val: number) => {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 0,
    }).format(val);
  };

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-5">
      {/* 1. Total Balance */}
      <GlassCard className="p-5 relative overflow-hidden">
        <div className="flex items-center justify-between mb-3">
          <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
            Total Balance
          </span>
          <div className="w-8 h-8 rounded-xl bg-blue-50 text-blue-600 flex items-center justify-center">
            <Wallet className="w-4 h-4" />
          </div>
        </div>
        <div className="text-2xl font-bold tracking-tight text-slate-900 font-mono">
          {formatInr(summary.total_balance)}
        </div>
        <div className="mt-2 flex items-center gap-1.5 text-xs text-slate-500 font-medium">
          <span className="w-1.5 h-1.5 rounded-full bg-emerald-500"></span>
          <span>Across 3 tracked accounts</span>
        </div>
      </GlassCard>

      {/* 2. Monthly Income */}
      <GlassCard className="p-5 relative overflow-hidden">
        <div className="flex items-center justify-between mb-3">
          <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
            Monthly Income
          </span>
          <div className="w-8 h-8 rounded-xl bg-emerald-50 text-emerald-600 flex items-center justify-center">
            <TrendingUp className="w-4 h-4" />
          </div>
        </div>
        <div className="text-2xl font-bold tracking-tight text-emerald-600 font-mono">
          {formatInr(summary.monthly_income)}
        </div>
        <div className="mt-2 flex items-center gap-1 text-xs text-emerald-700 font-medium">
          <ArrowUpRight className="w-3.5 h-3.5" />
          <span>+8.5% from last month</span>
        </div>
      </GlassCard>

      {/* 3. Monthly Expenses */}
      <GlassCard className="p-5 relative overflow-hidden">
        <div className="flex items-center justify-between mb-3">
          <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
            Monthly Expenses
          </span>
          <div className="w-8 h-8 rounded-xl bg-rose-50 text-rose-600 flex items-center justify-center">
            <TrendingDown className="w-4 h-4" />
          </div>
        </div>
        <div className="text-2xl font-bold tracking-tight text-rose-600 font-mono">
          {formatInr(summary.monthly_expense)}
        </div>
        <div className="mt-2 flex items-center gap-1 text-xs text-emerald-700 font-medium">
          <ArrowDownRight className="w-3.5 h-3.5" />
          <span>-4.2% lower spending</span>
        </div>
      </GlassCard>

      {/* 4. Net Savings & Savings Rate */}
      <GlassCard className="p-5 relative overflow-hidden bg-gradient-to-br from-white/80 via-blue-50/30 to-indigo-50/40">
        <div className="flex items-center justify-between mb-3">
          <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
            Net Savings
          </span>
          <div className="w-8 h-8 rounded-xl bg-indigo-50 text-indigo-600 flex items-center justify-center">
            <PiggyBank className="w-4 h-4" />
          </div>
        </div>
        <div className="text-2xl font-bold tracking-tight text-slate-900 font-mono">
          {formatInr(summary.net_savings)}
        </div>
        <div className="mt-2 flex items-center justify-between text-xs font-semibold">
          <span className="text-indigo-600">{summary.savings_rate}% Savings Rate</span>
          <span className="text-slate-400 font-normal">Target: 60%</span>
        </div>
      </GlassCard>
    </div>
  );
}
