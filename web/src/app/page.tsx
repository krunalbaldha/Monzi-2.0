'use client';

import React, { useState, useEffect } from 'react';
import { Header } from '@/components/layout/Header';
import { SummaryCards } from '@/components/dashboard/SummaryCards';
import { CashFlowChart } from '@/components/dashboard/CashFlowChart';
import { CategoryBreakdown } from '@/components/dashboard/CategoryBreakdown';
import { RecentTransactions } from '@/components/dashboard/RecentTransactions';
import { GlassCard } from '@/components/ui/GlassCard';
import { 
  mockSummary, 
  mockMonthlyCashFlow, 
  mockCategoryBreakdown, 
  mockTransactions, 
  mockAccounts 
} from '@/lib/mockData';
import { FinancialSummary, Transaction, Account } from '@/lib/types';
import { CreditCard, ShieldCheck } from 'lucide-react';

export default function DashboardPage() {
  const [summary, setSummary] = useState<FinancialSummary>(mockSummary);
  const [cashFlow, setCashFlow] = useState(mockMonthlyCashFlow);
  const [categories, setCategories] = useState(mockCategoryBreakdown);
  const [transactions, setTransactions] = useState<Transaction[]>(mockTransactions);
  const [accounts, setAccounts] = useState<Account[]>(mockAccounts);
  const [loading, setLoading] = useState(false);

  const fetchData = async () => {
    setLoading(true);
    try {
      const [statsRes, txRes, accRes] = await Promise.all([
        fetch('/api/stats'),
        fetch('/api/transactions'),
        fetch('/api/accounts'),
      ]);

      if (statsRes.ok) {
        const statsData = await statsRes.json();
        if (statsData.summary) setSummary(statsData.summary);
        if (statsData.cashFlow && statsData.cashFlow.length > 0) setCashFlow(statsData.cashFlow);
        if (statsData.categoryBreakdown && statsData.categoryBreakdown.length > 0) {
          setCategories(statsData.categoryBreakdown);
        }
      }

      if (txRes.ok) {
        const txData = await txRes.json();
        if (txData.transactions) setTransactions(txData.transactions.slice(0, 5));
      }

      if (accRes.ok) {
        const accData = await accRes.json();
        if (accData.accounts && accData.accounts.length > 0) setAccounts(accData.accounts);
      }
    } catch (e) {
      console.error('Failed to fetch dashboard data', e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const formatInr = (val: number) => {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 0,
    }).format(val);
  };

  return (
    <div className="flex flex-col min-h-screen">
      <Header onRefresh={fetchData} />

      <div className="p-4 sm:p-6 md:p-8 space-y-5 md:space-y-8 max-w-7xl mx-auto w-full">
        {/* 1. Account Cards: Horizontal Scroll Carousel on Mobile, Grid on Desktop */}
        <div>
          <div className="flex items-center justify-between mb-2 md:hidden">
            <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">
              Your Accounts ({accounts.length})
            </span>
            <span className="text-[11px] text-slate-400">Swipe for more</span>
          </div>

          <div className="flex overflow-x-auto snap-x snap-mandatory md:grid md:grid-cols-3 gap-3 md:gap-4 pb-2 md:pb-0 scrollbar-none">
            {accounts.map((acc) => (
              <GlassCard
                key={acc.id}
                className="min-w-[260px] sm:min-w-[280px] md:min-w-0 snap-center p-4 flex items-center justify-between shrink-0 md:shrink"
              >
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-2xl bg-blue-50 text-blue-600 flex items-center justify-center shrink-0">
                    <CreditCard className="w-5 h-5" />
                  </div>
                  <div>
                    <p className="text-xs font-bold text-slate-900">{acc.bank_name}</p>
                    <p className="text-[11px] text-slate-400 font-medium">
                      {acc.account_number_mask} • {acc.account_type.replace('_', ' ')}
                    </p>
                  </div>
                </div>
                <div className="text-right">
                  <p className="text-sm font-bold font-mono text-slate-900">
                    {formatInr(acc.current_balance)}
                  </p>
                  <span className="text-[10px] text-emerald-600 font-semibold flex items-center justify-end gap-1">
                    <ShieldCheck className="w-3 h-3" /> Tracked
                  </span>
                </div>
              </GlassCard>
            ))}
          </div>
        </div>

        {/* 2. Primary 4 Financial Summary Cards */}
        <SummaryCards summary={summary} />

        {/* 3. Charts Grid */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-5 md:gap-6">
          <div className="lg:col-span-2">
            <CashFlowChart data={cashFlow} />
          </div>
          <div>
            <CategoryBreakdown categories={categories} />
          </div>
        </div>

        {/* 4. Recent Transactions */}
        <RecentTransactions transactions={transactions} />
      </div>
    </div>
  );
}
