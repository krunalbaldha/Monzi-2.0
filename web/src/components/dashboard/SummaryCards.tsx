import React from 'react';
import { Wallet, TrendingUp, TrendingDown, PiggyBank, ArrowUpRight, ArrowDownRight, Minus } from 'lucide-react';
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

  const incomeGrowth = summary.income_change_pct || 0;
  const expenseGrowth = summary.expense_change_pct || 0;

  return (
    <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 sm:gap-5">
      {/* 1. Total Balance */}
      <GlassCard className="p-3.5 sm:p-5 relative overflow-hidden">
        <div className="flex items-center justify-between mb-2 sm:mb-3">
          <span className="text-[10px] sm:text-xs font-semibold text-slate-500 uppercase tracking-wider">
            Total Balance
          </span>
          <div className="w-7 h-7 sm:w-8 sm:h-8 rounded-xl bg-blue-50 text-blue-600 flex items-center justify-center">
            <Wallet className="w-3.5 h-3.5 sm:w-4 sm:h-4" />
          </div>
        </div>
        <div className="text-base sm:text-2xl font-bold tracking-tight text-slate-900 font-mono">
          {formatInr(summary.total_balance)}
        </div>
        <div className="mt-1.5 sm:mt-2 flex items-center gap-1.5 text-[10px] sm:text-xs text-slate-500 font-medium">
          <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 shrink-0"></span>
          <span className="truncate">Active tracking</span>
        </div>
      </GlassCard>

      {/* 2. Monthly Income */}
      <GlassCard className="p-3.5 sm:p-5 relative overflow-hidden">
        <div className="flex items-center justify-between mb-2 sm:mb-3">
          <span className="text-[10px] sm:text-xs font-semibold text-slate-500 uppercase tracking-wider">
            Income
          </span>
          <div className="w-7 h-7 sm:w-8 sm:h-8 rounded-xl bg-emerald-50 text-emerald-600 flex items-center justify-center">
            <TrendingUp className="w-3.5 h-3.5 sm:w-4 sm:h-4" />
          </div>
        </div>
        <div className="text-base sm:text-2xl font-bold tracking-tight text-emerald-600 font-mono">
          {formatInr(summary.monthly_income)}
        </div>
        <div className="mt-1.5 sm:mt-2 flex items-center gap-1 text-[10px] sm:text-xs font-medium">
          {incomeGrowth !== 0 ? (
            <>
              {incomeGrowth > 0 ? (
                <ArrowUpRight className="w-3 h-3 sm:w-3.5 sm:h-3.5 text-emerald-600 shrink-0" />
              ) : (
                <ArrowDownRight className="w-3 h-3 sm:w-3.5 sm:h-3.5 text-rose-600 shrink-0" />
              )}
              <span className={incomeGrowth > 0 ? 'text-emerald-700' : 'text-rose-700'}>
                {incomeGrowth > 0 ? `+${incomeGrowth}%` : `${incomeGrowth}%`} vs last month
              </span>
            </>
          ) : (
            <>
              <Minus className="w-3 h-3 sm:w-3.5 sm:h-3.5 text-slate-400 shrink-0" />
              <span className="text-slate-400">Current cycle</span>
            </>
          )}
        </div>
      </GlassCard>

      {/* 3. Monthly Expenses */}
      <GlassCard className="p-3.5 sm:p-5 relative overflow-hidden">
        <div className="flex items-center justify-between mb-2 sm:mb-3">
          <span className="text-[10px] sm:text-xs font-semibold text-slate-500 uppercase tracking-wider">
            Expenses
          </span>
          <div className="w-7 h-7 sm:w-8 sm:h-8 rounded-xl bg-rose-50 text-rose-600 flex items-center justify-center">
            <TrendingDown className="w-3.5 h-3.5 sm:w-4 sm:h-4" />
          </div>
        </div>
        <div className="text-base sm:text-2xl font-bold tracking-tight text-rose-600 font-mono">
          {formatInr(summary.monthly_expense)}
        </div>
        <div className="mt-1.5 sm:mt-2 flex items-center gap-1 text-[10px] sm:text-xs font-medium">
          {expenseGrowth !== 0 ? (
            <>
              {expenseGrowth > 0 ? (
                <ArrowUpRight className="w-3 h-3 sm:w-3.5 sm:h-3.5 text-rose-600 shrink-0" />
              ) : (
                <ArrowDownRight className="w-3 h-3 sm:w-3.5 sm:h-3.5 text-emerald-600 shrink-0" />
              )}
              <span className={expenseGrowth > 0 ? 'text-rose-700' : 'text-emerald-700'}>
                {expenseGrowth > 0 ? `+${expenseGrowth}%` : `${expenseGrowth}%`} vs last month
              </span>
            </>
          ) : (
            <>
              <Minus className="w-3 h-3 sm:w-3.5 sm:h-3.5 text-slate-400 shrink-0" />
              <span className="text-slate-400">Current cycle</span>
            </>
          )}
        </div>
      </GlassCard>

      {/* 4. Net Savings & Savings Rate */}
      <GlassCard className="p-3.5 sm:p-5 relative overflow-hidden bg-gradient-to-br from-white/80 via-blue-50/30 to-indigo-50/40">
        <div className="flex items-center justify-between mb-2 sm:mb-3">
          <span className="text-[10px] sm:text-xs font-semibold text-slate-500 uppercase tracking-wider">
            Net Savings
          </span>
          <div className="w-7 h-7 sm:w-8 sm:h-8 rounded-xl bg-indigo-50 text-indigo-600 flex items-center justify-center">
            <PiggyBank className="w-3.5 h-3.5 sm:w-4 sm:h-4" />
          </div>
        </div>
        <div className="text-base sm:text-2xl font-bold tracking-tight text-slate-900 font-mono">
          {formatInr(summary.net_savings)}
        </div>
        <div className="mt-1.5 sm:mt-2 flex items-center justify-between text-[10px] sm:text-xs font-semibold">
          <span className="text-indigo-600 truncate">{summary.savings_rate}% Rate</span>
          <span className="text-slate-400 font-normal hidden sm:inline">
            {summary.monthly_income > 0 ? 'of total income' : 'No income recorded'}
          </span>
        </div>
      </GlassCard>
    </div>
  );
}
