'use client';

import React, { useState, useEffect, useCallback } from 'react';
import Link from 'next/link';
import { Header } from '@/components/layout/Header';
import { SummaryCards } from '@/components/dashboard/SummaryCards';
import { CashFlowChart } from '@/components/dashboard/CashFlowChart';
import { CategoryBreakdown } from '@/components/dashboard/CategoryBreakdown';
import { RecentTransactions } from '@/components/dashboard/RecentTransactions';
import { GlassCard } from '@/components/ui/GlassCard';
import { FinancialSummary, Transaction, Account } from '@/lib/types';
import { CreditCard, Plus, ArrowRight, ShieldCheck, MessageSquare } from 'lucide-react';
import { AddAccountModal } from '@/components/dashboard/AddAccountModal';
import { useSms } from '@/components/sms/SmsProvider';

const emptySummary: FinancialSummary = {
  total_balance: 0,
  monthly_income: 0,
  monthly_expense: 0,
  net_savings: 0,
  savings_rate: 0,
  income_change_pct: 0,
  expense_change_pct: 0,
};

export default function DashboardPage() {
  const [summary, setSummary] = useState<FinancialSummary>(emptySummary);
  const [cashFlow, setCashFlow] = useState<Array<{ month: string; income: number; expense: number }>>([]);
  const [categories, setCategories] = useState<Array<{ name: string; value: number; color: string }>>([]);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [loading, setLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [isAddAccountOpen, setIsAddAccountOpen] = useState(false);
  const { openSyncModal } = useSms();

  const fetchData = useCallback(async () => {
    setIsRefreshing(true);
    try {
      const [statsRes, txRes, accRes] = await Promise.all([
        fetch('/api/stats'),
        fetch('/api/transactions?limit=8'),
        fetch('/api/accounts'),
      ]);

      if (statsRes.ok) {
        const statsData = await statsRes.json();
        if (statsData.summary) setSummary(statsData.summary);
        setCashFlow(statsData.cashFlow || []);
        setCategories(statsData.categoryBreakdown || []);
      }

      if (txRes.ok) {
        const txData = await txRes.json();
        setTransactions(txData.transactions || []);
      }

      if (accRes.ok) {
        const accData = await accRes.json();
        setAccounts(accData.accounts || []);
      }
    } catch (e) {
      console.error('Failed to fetch dashboard data', e);
    } finally {
      setLoading(false);
      setIsRefreshing(false);
    }
  }, []);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const formatInr = (val: number) => {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 0,
    }).format(val);
  };

  return (
    <div className="flex flex-col min-h-screen">
      <Header onRefresh={fetchData} isRefreshing={isRefreshing} />

      <div className="p-4 sm:p-6 md:p-8 space-y-5 md:space-y-8 max-w-7xl mx-auto w-full">
        {/* 1. Account Cards Carousel / Grid */}
        <div>
          <div className="flex items-center justify-between mb-2.5">
            <div className="flex items-center gap-2">
              <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">
                Accounts & Cards ({accounts.length})
              </span>
              <span className="inline-flex items-center gap-1 text-[10px] font-semibold text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded-full border border-emerald-200">
                <ShieldCheck className="w-3 h-3" /> Live Synced
              </span>
            </div>
            <div className="flex items-center gap-2.5 sm:gap-3">
              <button
                onClick={openSyncModal}
                className="flex items-center gap-1 text-xs font-bold text-emerald-600 hover:text-emerald-700 active:scale-95"
              >
                <MessageSquare className="w-3.5 h-3.5" />
                <span>Sync SMS</span>
              </button>
              <button
                onClick={() => setIsAddAccountOpen(true)}
                className="flex items-center gap-1 text-xs font-bold text-blue-600 hover:text-blue-700 active:scale-95"
              >
                <Plus className="w-3.5 h-3.5" />
                <span>Add Account</span>
              </button>
              <Link
                href="/accounts"
                className="text-xs font-semibold text-slate-500 hover:text-slate-800 hidden sm:flex items-center gap-0.5"
              >
                <span>Wallet</span>
                <ArrowRight className="w-3.5 h-3.5" />
              </Link>
            </div>
          </div>

          {accounts.length === 0 && !loading ? (
            <GlassCard className="p-5 flex items-center justify-between">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-2xl bg-blue-50 text-blue-600 flex items-center justify-center">
                  <CreditCard className="w-5 h-5" />
                </div>
                <div>
                  <p className="text-xs font-bold text-slate-900">No bank accounts linked yet</p>
                  <p className="text-[11px] text-slate-400">Add an account to track balances automatically.</p>
                </div>
              </div>
              <button
                onClick={() => setIsAddAccountOpen(true)}
                className="px-3.5 py-1.5 rounded-xl text-xs font-semibold bg-blue-600 text-white shadow-sm hover:bg-blue-700"
              >
                Add Account
              </button>
            </GlassCard>
          ) : (
            <div className="flex overflow-x-auto snap-x snap-mandatory md:grid md:grid-cols-3 gap-3 md:gap-4 pb-2 md:pb-0 scrollbar-none">
              {accounts.map((acc) => (
                <Link
                  key={acc.id}
                  href={`/transactions?account=${acc.id}`}
                  className="min-w-[260px] sm:min-w-[280px] md:min-w-0 snap-center shrink-0 md:shrink"
                >
                  <GlassCard className="p-4 flex items-center justify-between hover:border-blue-300 transition-all active:scale-[0.99] cursor-pointer">
                    <div className="flex items-center gap-3 min-w-0">
                      <div className="w-10 h-10 rounded-2xl bg-blue-50 text-blue-600 flex items-center justify-center shrink-0">
                        <CreditCard className="w-5 h-5" />
                      </div>
                      <div className="min-w-0">
                        <p className="text-xs font-bold text-slate-900 truncate">{acc.bank_name}</p>
                        <p className="text-[11px] text-slate-400 font-medium">
                          {acc.account_type} • {acc.account_number_mask}
                        </p>
                      </div>
                    </div>
                    <div className="text-right shrink-0">
                      <p className="text-sm font-bold text-slate-900 font-mono">
                        {formatInr(Number(acc.current_balance))}
                      </p>
                      <span className="text-[10px] text-emerald-600 font-medium">Live Balance</span>
                    </div>
                  </GlassCard>
                </Link>
              ))}
            </div>
          )}
        </div>

        {/* 2. Real Financial Summary Cards */}
        <SummaryCards summary={summary} />

        {/* 3. Analytics Charts Grid */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-5 md:gap-6">
          <div className="lg:col-span-2">
            <CashFlowChart data={cashFlow} />
          </div>
          <div>
            <CategoryBreakdown categories={categories} />
          </div>
        </div>

        {/* 4. Live Recent Transactions */}
        <RecentTransactions transactions={transactions} onRefresh={fetchData} />
      </div>

      <AddAccountModal
        isOpen={isAddAccountOpen}
        onClose={() => setIsAddAccountOpen(false)}
        onSuccess={fetchData}
      />
    </div>
  );
}
