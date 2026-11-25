'use client';

import React, { useState, useEffect, useCallback } from 'react';
import Link from 'next/link';
import { Header } from '@/components/layout/Header';
import { GlassCard } from '@/components/ui/GlassCard';
import {
  Plus,
  Wifi,
  Trash2,
  ArrowRight,
  AlertTriangle,
  CreditCard,
  TrendingUp,
  TrendingDown,
  Wallet,
} from 'lucide-react';
import { Account } from '@/lib/types';
import { AddAccountModal } from '@/components/dashboard/AddAccountModal';
import { useToast } from '@/components/ui/Toast';

const getCardTheme = (bank: string, type: string) => {
  const b = bank.toUpperCase();
  if (b.includes('HDFC')) {
    return { bg: 'bg-gradient-to-br from-[#0B132B] via-[#1C2541] to-[#3A506B]', sub: 'text-slate-300' };
  } else if (b.includes('ICICI') && type !== 'CREDIT_CARD') {
    return { bg: 'bg-gradient-to-br from-[#4C0519] via-[#881337] to-[#BE123C]', sub: 'text-rose-200' };
  } else if (type === 'CREDIT_CARD') {
    return { bg: 'bg-gradient-to-br from-[#1E1B4B] via-[#312E81] to-[#4338CA]', sub: 'text-indigo-200' };
  } else if (b.includes('SBI')) {
    return { bg: 'bg-gradient-to-br from-[#0C4A6E] via-[#075985] to-[#0284C7]', sub: 'text-sky-200' };
  } else if (b.includes('AXIS')) {
    return { bg: 'bg-gradient-to-br from-[#431407] via-[#7C2D12] to-[#C2410C]', sub: 'text-orange-200' };
  } else if (b.includes('KOTAK')) {
    return { bg: 'bg-gradient-to-br from-[#3B0764] via-[#6B21A8] to-[#A855F7]', sub: 'text-purple-200' };
  } else {
    return { bg: 'bg-gradient-to-br from-[#064E3B] via-[#065F46] to-[#059669]', sub: 'text-emerald-200' };
  }
};

const formatInr = (val: number) =>
  new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(val);

export default function AccountsPage() {
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);
  const [confirmDeleteId, setConfirmDeleteId] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [loading, setLoading] = useState(true);
  const toast = useToast();

  const fetchAccounts = useCallback(async () => {
    setLoading(true);
    try {
      const res = await fetch('/api/accounts');
      if (res.ok) {
        const data = await res.json();
        setAccounts(data.accounts || []);
      }
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchAccounts();
  }, [fetchAccounts]);

  const handleDeleteAccount = async (id: string) => {
    setIsDeleting(true);
    try {
      const res = await fetch(`/api/accounts?id=${id}`, { method: 'DELETE' });
      if (!res.ok) {
        const err = await res.json();
        throw new Error(err.error || 'Failed to delete account');
      }
      toast.success('Account deleted');
      setConfirmDeleteId(null);
      fetchAccounts();
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : 'Error deleting account');
    } finally {
      setIsDeleting(false);
    }
  };

  // Summary calculations
  const assets = accounts
    .filter((a) => a.account_type !== 'CREDIT_CARD')
    .reduce((s, a) => s + Number(a.current_balance), 0);
  const creditOutstanding = accounts
    .filter((a) => a.account_type === 'CREDIT_CARD')
    .reduce((s, a) => s + Math.abs(Number(a.current_balance)), 0);
  const netWorth = assets - creditOutstanding;

  const getTypeBadge = (type: string) => {
    const map: Record<string, { label: string; cls: string }> = {
      SAVINGS: { label: 'Savings', cls: 'bg-emerald-100 text-emerald-700' },
      CURRENT: { label: 'Current', cls: 'bg-blue-100 text-blue-700' },
      CREDIT_CARD: { label: 'Credit Card', cls: 'bg-indigo-100 text-indigo-700' },
      WALLET: { label: 'Wallet', cls: 'bg-amber-100 text-amber-700' },
    };
    return map[type] || { label: type, cls: 'bg-slate-100 text-slate-700' };
  };

  return (
    <div className="flex flex-col min-h-screen">
      <Header onRefresh={fetchAccounts} isRefreshing={loading} />

      <div className="p-4 sm:p-6 md:p-8 space-y-6 max-w-5xl mx-auto w-full">
        {/* Header */}
        <div className="flex items-center justify-between gap-3">
          <div>
            <h2 className="text-xl font-bold tracking-tight text-slate-900">Accounts</h2>
            <p className="text-xs text-slate-500 font-medium mt-0.5">
              {accounts.length} account{accounts.length !== 1 ? 's' : ''} linked
            </p>
          </div>
          {/* Desktop add button */}
          <button
            onClick={() => setIsAddModalOpen(true)}
            className="hidden sm:flex items-center gap-1.5 px-4 py-2.5 rounded-xl bg-blue-600 text-white text-xs font-semibold shadow-sm shadow-blue-500/25 hover:bg-blue-700 active:scale-95 transition-all min-h-[44px]"
          >
            <Plus className="w-4 h-4" />
            Add Account
          </button>
        </div>

        {/* Empty State */}
        {!loading && accounts.length === 0 && (
          <GlassCard className="py-16 text-center space-y-4">
            <div className="w-14 h-14 rounded-2xl bg-blue-50 text-blue-600 flex items-center justify-center mx-auto">
              <CreditCard className="w-7 h-7" />
            </div>
            <div>
              <h3 className="text-sm font-bold text-slate-800">No Accounts Linked</h3>
              <p className="text-xs text-slate-400 max-w-xs mx-auto mt-1">
                Add your savings, current, or credit card account to start tracking your net worth.
              </p>
            </div>
            <button
              onClick={() => setIsAddModalOpen(true)}
              className="px-5 py-2.5 text-xs font-semibold rounded-xl bg-blue-600 text-white shadow-sm hover:bg-blue-700 transition-all"
            >
              Add First Account
            </button>
          </GlassCard>
        )}

        {/* Account Cards Grid */}
        {accounts.length > 0 && (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 md:gap-5">
            {accounts.map((acc) => {
              const theme = getCardTheme(acc.bank_name, acc.account_type);
              const maskDigits = acc.account_number_mask.replace(/[^0-9A-Z]/gi, '') || '0000';
              const badge = getTypeBadge(acc.account_type);
              const isCreditNeg = acc.account_type === 'CREDIT_CARD' || Number(acc.current_balance) < 0;

              return (
                <div key={acc.id} className="flex flex-col gap-3">
                  {/* Bank Card */}
                  <div
                    className={`relative rounded-3xl p-5 ${theme.bg} text-white shadow-xl overflow-hidden flex flex-col justify-between min-h-[200px]`}
                  >
                    {/* Background glow */}
                    <div className="absolute -right-10 -top-10 w-36 h-36 rounded-full bg-white/10 blur-2xl pointer-events-none" />
                    <div className="absolute -left-6 bottom-0 w-28 h-28 rounded-full bg-black/10 blur-xl pointer-events-none" />

                    {/* Top row */}
                    <div className="flex items-start justify-between relative z-10">
                      <div>
                        <h3 className="text-base font-extrabold tracking-tight">{acc.bank_name}</h3>
                        <span className={`text-[10px] font-semibold uppercase tracking-wider ${theme.sub}`}>
                          {acc.account_type.replace('_', ' ')}
                        </span>
                      </div>
                      <Wifi className="w-5 h-5 opacity-70 rotate-90 shrink-0" />
                    </div>

                    {/* EMV Chip + badge */}
                    <div className="flex items-center gap-2 relative z-10">
                      <div className="w-10 h-7 rounded-md bg-amber-300/80 border border-amber-400/50 flex flex-col justify-around p-1 shadow-inner">
                        <div className="w-full h-px bg-black/20" />
                        <div className="w-full h-px bg-black/20" />
                      </div>
                      <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${badge.cls} bg-white/20 text-white/90`}>
                        {badge.label}
                      </span>
                    </div>

                    {/* Bottom: mask + balance */}
                    <div className="flex items-end justify-between relative z-10 pt-1">
                      <div>
                        <p className="font-mono text-xs tracking-widest opacity-80">
                          •••• •••• {maskDigits.slice(-4)}
                        </p>
                      </div>
                      <div className="text-right">
                        <p className={`text-[10px] font-semibold uppercase tracking-wider ${theme.sub}`}>Balance</p>
                        <p className={`text-3xl font-bold font-mono tracking-tight leading-none mt-0.5 ${isCreditNeg ? 'text-rose-300' : 'text-emerald-300'}`}>
                          {formatInr(Number(acc.current_balance))}
                        </p>
                      </div>
                    </div>
                  </div>

                  {/* Action buttons below card */}
                  <div className="flex gap-2">
                    <Link
                      href={`/transactions?account=${acc.id}`}
                      className="flex-1 flex items-center justify-center gap-1.5 py-2.5 rounded-xl bg-white border border-slate-200 text-blue-600 text-xs font-semibold hover:bg-blue-50 hover:border-blue-200 transition-all min-h-[44px]"
                    >
                      <ArrowRight className="w-3.5 h-3.5" />
                      View Transactions
                    </Link>
                    <button
                      onClick={() => setConfirmDeleteId(acc.id)}
                      className="flex items-center justify-center gap-1.5 px-4 py-2.5 rounded-xl bg-white border border-rose-200 text-rose-600 text-xs font-semibold hover:bg-rose-50 transition-all min-h-[44px]"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                      Delete
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        )}

        {/* Net Worth Summary Card */}
        {accounts.length > 0 && (
          <GlassCard className="p-5 sm:p-6">
            <h3 className="text-xs font-bold text-slate-500 uppercase tracking-wider mb-4">Financial Summary</h3>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
              <div className="p-4 rounded-2xl bg-emerald-50 border border-emerald-100">
                <div className="flex items-center gap-2 mb-2">
                  <TrendingUp className="w-4 h-4 text-emerald-600" />
                  <span className="text-[11px] font-bold text-emerald-700 uppercase tracking-wide">Total Assets</span>
                </div>
                <p className="text-xl font-bold font-mono text-emerald-700">{formatInr(assets)}</p>
                <p className="text-[10px] text-emerald-600 mt-0.5">Savings + Current accounts</p>
              </div>

              <div className="p-4 rounded-2xl bg-rose-50 border border-rose-100">
                <div className="flex items-center gap-2 mb-2">
                  <TrendingDown className="w-4 h-4 text-rose-600" />
                  <span className="text-[11px] font-bold text-rose-700 uppercase tracking-wide">Credit Outstanding</span>
                </div>
                <p className="text-xl font-bold font-mono text-rose-600">{formatInr(creditOutstanding)}</p>
                <p className="text-[10px] text-rose-600 mt-0.5">Credit card dues</p>
              </div>

              <div className={`p-4 rounded-2xl border ${netWorth >= 0 ? 'bg-blue-50 border-blue-100' : 'bg-amber-50 border-amber-100'}`}>
                <div className="flex items-center gap-2 mb-2">
                  <Wallet className={`w-4 h-4 ${netWorth >= 0 ? 'text-blue-600' : 'text-amber-600'}`} />
                  <span className={`text-[11px] font-bold uppercase tracking-wide ${netWorth >= 0 ? 'text-blue-700' : 'text-amber-700'}`}>Net Worth</span>
                </div>
                <p className={`text-xl font-bold font-mono ${netWorth >= 0 ? 'text-blue-700' : 'text-amber-600'}`}>{formatInr(netWorth)}</p>
                <p className={`text-[10px] mt-0.5 ${netWorth >= 0 ? 'text-blue-600' : 'text-amber-600'}`}>Assets − Liabilities</p>
              </div>
            </div>
          </GlassCard>
        )}
      </div>

      {/* Mobile FAB */}
      <button
        onClick={() => setIsAddModalOpen(true)}
        className="sm:hidden fixed bottom-24 right-4 z-30 w-14 h-14 rounded-full bg-blue-600 text-white shadow-xl shadow-blue-500/40 flex items-center justify-center hover:bg-blue-700 active:scale-95 transition-all"
        aria-label="Add Account"
      >
        <Plus className="w-6 h-6" />
      </button>

      {/* Delete Confirmation Modal */}
      {confirmDeleteId && (
        <div className="fixed inset-0 z-50 overflow-y-auto bg-slate-900/60 backdrop-blur-md p-3 sm:p-6 flex items-center justify-center min-h-screen animate-fade-in">
          <div className="fixed inset-0 -z-10" onClick={() => setConfirmDeleteId(null)} aria-hidden="true" />
          <div className="bg-white rounded-2xl shadow-2xl border border-slate-100 my-auto w-full max-w-sm p-6 space-y-4 animate-slide-up">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-full bg-rose-100 flex items-center justify-center">
                <AlertTriangle className="w-5 h-5 text-rose-600" />
              </div>
              <div>
                <h3 className="text-sm font-bold text-slate-900">Delete Account?</h3>
                <p className="text-xs text-slate-500">This action cannot be undone.</p>
              </div>
            </div>
            <p className="text-xs text-slate-600 leading-relaxed">
              Are you sure you want to delete this account? Transactions linked to it will remain in your history.
            </p>
            <div className="flex gap-2 pt-1">
              <button
                onClick={() => handleDeleteAccount(confirmDeleteId)}
                disabled={isDeleting}
                className="flex-1 py-3 rounded-xl bg-rose-600 text-white text-xs font-semibold shadow-sm hover:bg-rose-700 active:scale-95 disabled:opacity-60 transition-all min-h-[44px]"
              >
                {isDeleting ? 'Deleting...' : 'Yes, Delete'}
              </button>
              <button
                onClick={() => setConfirmDeleteId(null)}
                className="px-4 py-3 rounded-xl bg-slate-100 text-slate-700 text-xs font-semibold hover:bg-slate-200 transition-all min-h-[44px]"
              >
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}

      <AddAccountModal
        isOpen={isAddModalOpen}
        onClose={() => setIsAddModalOpen(false)}
        onSuccess={fetchAccounts}
      />
    </div>
  );
}
