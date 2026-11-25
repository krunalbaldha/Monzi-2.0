'use client';

import React from 'react';
import {
  Wallet,
  TrendingUp,
  TrendingDown,
  PiggyBank,
  ArrowUpRight,
  ArrowDownRight,
  Minus,
} from 'lucide-react';
import { GlassCard } from '@/components/ui/GlassCard';
import { Skeleton } from '@/components/ui/Skeleton';
import { AnimatedNumber } from '@/components/ui/AnimatedNumber';
import { FinancialSummary } from '@/lib/types';

export interface ExtendedFinancialSummary extends FinancialSummary {
  total_income?: number;
  total_expenses?: number;
}

interface SummaryCardsProps {
  summary: ExtendedFinancialSummary;
  loading?: boolean;
}

function SummarySkeletonCard() {
  return (
    <GlassCard className="p-4 sm:p-5 min-h-[100px]">
      <div className="flex items-center justify-between mb-3">
        <Skeleton className="h-3 w-20 rounded-full" />
        <Skeleton className="w-8 h-8 rounded-xl" />
      </div>
      <Skeleton className="h-7 w-28 rounded-lg mb-2.5" />
      <Skeleton className="h-3 w-20 rounded-full" />
    </GlassCard>
  );
}

const formatInr = (val: number) =>
  new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 0,
  }).format(Math.round(val));

export function SummaryCards({ summary, loading = false }: SummaryCardsProps) {
  const incomeGrowth = summary.income_change_pct || 0;
  const expenseGrowth = summary.expense_change_pct || 0;
  const totalIncome = summary.total_income ?? summary.monthly_income;
  const totalExpenses = summary.total_expenses ?? summary.monthly_expense;

  if (loading) {
    return (
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 sm:gap-4">
        {[0, 1, 2, 3].map((i) => (
          <SummarySkeletonCard key={i} />
        ))}
      </div>
    );
  }

  return (
    <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 sm:gap-4">
      {/* 1. Total Balance */}
      <GlassCard className="p-4 sm:p-5 min-h-[100px] relative overflow-hidden">
        <div className="flex items-center justify-between mb-2.5 sm:mb-3">
          <span className="text-[11px] sm:text-xs font-semibold text-slate-500 uppercase tracking-wider">
            Total Balance
          </span>
          <div className="w-8 h-8 rounded-xl bg-blue-50 text-blue-600 flex items-center justify-center shrink-0">
            <Wallet className="w-4 h-4" />
          </div>
        </div>
        <div className="text-lg sm:text-2xl font-bold tracking-tight text-slate-900 font-mono leading-tight">
          <AnimatedNumber value={summary.total_balance} format={formatInr} />
        </div>
        <div className="mt-2 flex items-center gap-1.5 text-[11px] sm:text-xs text-slate-500 font-medium">
          <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse-soft shrink-0" />
          <span className="truncate">Live account total</span>
        </div>
      </GlassCard>

      {/* 2. Monthly & Total Income */}
      <GlassCard className="p-4 sm:p-5 min-h-[100px] relative overflow-hidden">
        <div className="flex items-center justify-between mb-2.5 sm:mb-3">
          <span className="text-[11px] sm:text-xs font-semibold text-slate-500 uppercase tracking-wider">
            Monthly Income
          </span>
          <div className="w-8 h-8 rounded-xl bg-emerald-50 text-emerald-600 flex items-center justify-center shrink-0">
            <TrendingUp className="w-4 h-4" />
          </div>
        </div>
        <div className="text-lg sm:text-2xl font-bold tracking-tight text-emerald-600 font-mono leading-tight">
          <AnimatedNumber value={summary.monthly_income} format={formatInr} />
        </div>
        <div className="mt-2 flex items-center justify-between gap-1 text-[11px] sm:text-xs font-medium">
          <div className="flex items-center gap-1 min-w-0">
            {incomeGrowth !== 0 ? (
              <>
                {incomeGrowth > 0 ? (
                  <ArrowUpRight className="w-3.5 h-3.5 text-emerald-600 shrink-0" />
                ) : (
                  <ArrowDownRight className="w-3.5 h-3.5 text-rose-500 shrink-0" />
                )}
                <span className={incomeGrowth > 0 ? 'text-emerald-700 truncate' : 'text-rose-600 truncate'}>
                  {incomeGrowth > 0 ? `+${incomeGrowth}%` : `${incomeGrowth}%`}
                </span>
              </>
            ) : (
              <>
                <Minus className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                <span className="text-slate-400 truncate">This mo</span>
              </>
            )}
          </div>
          <span className="text-[10px] text-slate-400 font-mono truncate hidden sm:inline">
            All: {formatInr(totalIncome)}
          </span>
        </div>
      </GlassCard>

      {/* 3. Monthly & Total Expenses */}
      <GlassCard className="p-4 sm:p-5 min-h-[100px] relative overflow-hidden">
        <div className="flex items-center justify-between mb-2.5 sm:mb-3">
          <span className="text-[11px] sm:text-xs font-semibold text-slate-500 uppercase tracking-wider">
            Monthly Expenses
          </span>
          <div className="w-8 h-8 rounded-xl bg-rose-50 text-rose-500 flex items-center justify-center shrink-0">
            <TrendingDown className="w-4 h-4" />
          </div>
        </div>
        <div className="text-lg sm:text-2xl font-bold tracking-tight text-rose-500 font-mono leading-tight">
          <AnimatedNumber value={summary.monthly_expense} format={formatInr} />
        </div>
        <div className="mt-2 flex items-center justify-between gap-1 text-[11px] sm:text-xs font-medium">
          <div className="flex items-center gap-1 min-w-0">
            {expenseGrowth !== 0 ? (
              <>
                {expenseGrowth > 0 ? (
                  <ArrowUpRight className="w-3.5 h-3.5 text-rose-500 shrink-0" />
                ) : (
                  <ArrowDownRight className="w-3.5 h-3.5 text-emerald-600 shrink-0" />
                )}
                <span className={expenseGrowth > 0 ? 'text-rose-600 truncate' : 'text-emerald-700 truncate'}>
                  {expenseGrowth > 0 ? `+${expenseGrowth}%` : `${expenseGrowth}%`}
                </span>
              </>
            ) : (
              <>
                <Minus className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                <span className="text-slate-400 truncate">This mo</span>
              </>
            )}
          </div>
          <span className="text-[10px] text-slate-400 font-mono truncate hidden sm:inline">
            All: {formatInr(totalExpenses)}
          </span>
        </div>
      </GlassCard>

      {/* 4. Net Savings */}
      <GlassCard className="p-4 sm:p-5 min-h-[100px] relative overflow-hidden bg-gradient-to-br from-white/80 via-indigo-50/30 to-blue-50/40">
        <div className="flex items-center justify-between mb-2.5 sm:mb-3">
          <span className="text-[11px] sm:text-xs font-semibold text-slate-500 uppercase tracking-wider">
            Net Savings
          </span>
          <div className="w-8 h-8 rounded-xl bg-indigo-50 text-indigo-600 flex items-center justify-center shrink-0">
            <PiggyBank className="w-4 h-4" />
          </div>
        </div>
        <div
          className={`text-lg sm:text-2xl font-bold tracking-tight font-mono leading-tight ${
            summary.net_savings >= 0 ? 'text-indigo-600' : 'text-rose-600'
          }`}
        >
          <AnimatedNumber value={summary.net_savings} format={formatInr} />
        </div>
        <div className="mt-2 flex items-center justify-between gap-1 text-[11px] sm:text-xs font-semibold">
          <span className="inline-flex items-center px-2 py-0.5 rounded-full bg-indigo-50 text-indigo-600 border border-indigo-100">
            {summary.savings_rate}% rate
          </span>
          <span className="text-slate-400 font-normal hidden sm:inline truncate">
            {summary.monthly_income > 0 ? 'of income' : 'No income'}
          </span>
        </div>
      </GlassCard>
    </div>
  );
}
