'use client';

import React, { useState, useEffect, useCallback } from 'react';
import Link from 'next/link';
import { Header } from '@/components/layout/Header';
import { GlassCard } from '@/components/ui/GlassCard';
import { 
  ShieldCheck, 
  Plus, 
  Wifi, 
  ArrowUpRight, 
  ArrowDownLeft, 
  Trash2, 
  Edit2, 
  CreditCard,
  ArrowRight,
  AlertTriangle,
  X,
  Save
} from 'lucide-react';
import { Account, Transaction } from '@/lib/types';
import { AddAccountModal } from '@/components/dashboard/AddAccountModal';
import { useToast } from '@/components/ui/Toast';

export default function AccountsPage() {
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [selectedAccount, setSelectedAccount] = useState<Account | null>(null);
  const [accountTransactions, setAccountTransactions] = useState<Transaction[]>([]);
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);
  const [isEditModalOpen, setIsEditModalOpen] = useState(false);
  const [newBalance, setNewBalance] = useState('');
  const [isSavingBalance, setIsSavingBalance] = useState(false);
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
        const accs = data.accounts || [];
        setAccounts(accs);
        if (accs.length > 0 && !selectedAccount) {
          setSelectedAccount(accs[0]);
        }
      }
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  }, [selectedAccount]);

  useEffect(() => {
    fetchAccounts();
  }, [fetchAccounts]);

  // Fetch transactions when selected account changes
  useEffect(() => {
    if (selectedAccount) {
      fetch(`/api/transactions?account_id=${selectedAccount.id}&limit=10`)
        .then((res) => res.json())
        .then((data) => {
          setAccountTransactions(data.transactions || []);
        })
        .catch((e) => console.error(e));
    } else {
      setAccountTransactions([]);
    }
  }, [selectedAccount]);

  const formatInr = (val: number) => {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 0,
    }).format(val);
  };

  const formatDate = (dateStr: string) => {
    return new Intl.DateTimeFormat('en-IN', {
      day: 'numeric',
      month: 'short',
      hour: '2-digit',
      minute: '2-digit',
    }).format(new Date(dateStr));
  };

  const handleUpdateBalance = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedAccount) return;
    const num = parseFloat(newBalance);
    if (isNaN(num)) {
      toast.error('Please enter a valid balance amount');
      return;
    }

    setIsSavingBalance(true);
    try {
      const res = await fetch('/api/accounts', {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          id: selectedAccount.id,
          current_balance: num,
        }),
      });

      if (!res.ok) {
        const err = await res.json();
        throw new Error(err.error || 'Failed to update balance');
      }

      const updated = await res.json();
      toast.success(`${selectedAccount.bank_name} balance updated to ₹${num.toLocaleString('en-IN')}`);
      setSelectedAccount(updated.account);
      setIsEditModalOpen(false);
      fetchAccounts();
    } catch (err: any) {
      toast.error(err.message || 'Error updating balance');
    } finally {
      setIsSavingBalance(false);
    }
  };

  const handleDeleteAccount = async (id: string) => {
    setIsDeleting(true);
    try {
      const res = await fetch(`/api/accounts?id=${id}`, {
        method: 'DELETE',
      });

      if (!res.ok) {
        const err = await res.json();
        throw new Error(err.error || 'Failed to delete account');
      }

      toast.success('Account deleted');
      setConfirmDeleteId(null);
      setSelectedAccount(null);
      fetchAccounts();
    } catch (err: any) {
      toast.error(err.message || 'Error deleting account');
    } finally {
      setIsDeleting(false);
    }
  };

  // Apple Wallet Card Style Gradients
  const getCardTheme = (bank: string, type: string) => {
    const b = bank.toUpperCase();
    if (b.includes('HDFC')) {
      return {
        bg: 'bg-gradient-to-br from-[#0B132B] via-[#1C2541] to-[#3A506B]',
        chip: 'bg-amber-300/90 border-amber-400',
        textColor: 'text-white',
        subText: 'text-slate-300',
      };
    } else if (b.includes('ICICI') && type !== 'CREDIT_CARD') {
      return {
        bg: 'bg-gradient-to-br from-[#4C0519] via-[#881337] to-[#BE123C]',
        chip: 'bg-amber-300/90 border-amber-400',
        textColor: 'text-white',
        subText: 'text-rose-200',
      };
    } else if (type === 'CREDIT_CARD') {
      return {
        bg: 'bg-gradient-to-br from-[#1E1B4B] via-[#312E81] to-[#4338CA]',
        chip: 'bg-amber-300/90 border-amber-400',
        textColor: 'text-white',
        subText: 'text-indigo-200',
      };
    } else if (b.includes('SBI')) {
      return {
        bg: 'bg-gradient-to-br from-[#0C4A6E] via-[#075985] to-[#0284C7]',
        chip: 'bg-amber-300/90 border-amber-400',
        textColor: 'text-white',
        subText: 'text-sky-200',
      };
    } else {
      return {
        bg: 'bg-gradient-to-br from-[#064E3B] via-[#065F46] to-[#059669]',
        chip: 'bg-amber-300/90 border-amber-400',
        textColor: 'text-white',
        subText: 'text-emerald-200',
      };
    }
  };

  return (
    <div className="flex flex-col min-h-screen">
      <Header onRefresh={fetchAccounts} isRefreshing={loading} />

      <div className="p-4 sm:p-6 md:p-8 space-y-6 max-w-7xl mx-auto w-full">
        {/* Title bar */}
        <div className="flex items-center justify-between gap-3">
          <div>
            <h2 className="text-lg sm:text-xl font-bold tracking-tight text-slate-900">Wallet & Accounts</h2>
            <p className="text-[11px] sm:text-xs text-slate-500 font-medium">
              Live balances tracked from bank SMS & manual ledgers
            </p>
          </div>
          <button
            onClick={() => setIsAddModalOpen(true)}
            className="flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-blue-600 text-white text-xs font-semibold shadow-sm shadow-blue-500/20 hover:bg-blue-700 transition-all active:scale-95 shrink-0"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>Add Account</span>
          </button>
        </div>

        {/* --- EMPTY STATE IF NO ACCOUNTS --- */}
        {accounts.length === 0 && !loading ? (
          <GlassCard className="py-14 text-center p-6 space-y-3">
            <div className="w-12 h-12 rounded-2xl bg-blue-50 text-blue-600 flex items-center justify-center mx-auto">
              <CreditCard className="w-6 h-6" />
            </div>
            <h3 className="text-sm font-bold text-slate-800">No Accounts Linked</h3>
            <p className="text-xs text-slate-400 max-w-sm mx-auto">
              Add your savings account, credit card, or cash wallet to begin tracking your net worth and spending.
            </p>
            <button
              onClick={() => setIsAddModalOpen(true)}
              className="px-4 py-2 text-xs font-semibold rounded-xl bg-blue-600 text-white shadow-sm hover:bg-blue-700 transition-all"
            >
              Add First Account
            </button>
          </GlassCard>
        ) : (
          /* --- APPLE WALLET STYLE CARDS DECK --- */
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 md:gap-6">
            {accounts.map((acc) => {
              const theme = getCardTheme(acc.bank_name, acc.account_type);
              const maskDigits = acc.account_number_mask.replace(/[^0-9]/g, '') || '0000';
              const isSelected = selectedAccount?.id === acc.id;

              return (
                <div
                  key={acc.id}
                  onClick={() => setSelectedAccount(acc)}
                  className={`relative rounded-3xl p-6 ${theme.bg} ${theme.textColor} shadow-xl shadow-slate-900/10 overflow-hidden flex flex-col justify-between min-h-[220px] transition-all cursor-pointer ${
                    isSelected ? 'ring-4 ring-blue-500/40 scale-[1.02]' : 'hover:scale-[1.01]'
                  } active:scale-[0.99]`}
                >
                  {/* Frosted Shimmer Texture overlay */}
                  <div className="absolute inset-0 bg-white/[0.04] backdrop-blur-[1px] pointer-events-none" />
                  <div className="absolute -right-12 -top-12 w-40 h-40 rounded-full bg-white/10 blur-2xl pointer-events-none" />

                  {/* Card Top: Bank Name & Contactless Icon */}
                  <div className="flex items-center justify-between relative z-10">
                    <div>
                      <h3 className="text-base font-extrabold tracking-tight">{acc.bank_name}</h3>
                      <p className={`text-[11px] font-medium ${theme.subText} uppercase tracking-wider`}>
                        {acc.account_type.replace('_', ' ')}
                      </p>
                    </div>
                    <Wifi className="w-5 h-5 opacity-75 rotate-90" />
                  </div>

                  {/* EMV Metallic Chip Graphic */}
                  <div className="my-3 relative z-10 flex items-center justify-between">
                    <div className={`w-11 h-8 rounded-lg ${theme.chip} border flex flex-col justify-around p-1 shadow-inner opacity-90`}>
                      <div className="w-full h-0.5 bg-black/20" />
                      <div className="w-full h-0.5 bg-black/20" />
                    </div>
                    <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-white/15 backdrop-blur-md flex items-center gap-1">
                      <ShieldCheck className="w-3 h-3 text-emerald-400" />
                      <span>Live Synced</span>
                    </span>
                  </div>

                  {/* Card Bottom: Masked Number & Tracked Balance */}
                  <div className="relative z-10 pt-2 flex items-end justify-between">
                    <div>
                      <p className="font-mono text-xs tracking-widest opacity-80 mb-1">
                        •••• •••• •••• {maskDigits}
                      </p>
                      <p className="text-[10px] uppercase font-bold tracking-wider opacity-60">
                        {acc.bank_name.toUpperCase()} ACCOUNT
                      </p>
                    </div>

                    <div className="text-right">
                      <span className={`text-[10px] font-semibold uppercase tracking-wider ${theme.subText}`}>
                        Balance
                      </span>
                      <p className="text-2xl font-bold font-mono tracking-tight text-white leading-none mt-0.5">
                        {formatInr(Number(acc.current_balance))}
                      </p>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}

        {/* --- SELECTED ACCOUNT DETAILS & TRANSACTIONS --- */}
        {selectedAccount && (
          <GlassCard className="p-5 sm:p-6 space-y-4">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-3 border-b border-slate-100">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-2xl bg-blue-50 text-blue-600 flex items-center justify-center font-bold">
                  <CreditCard className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="text-base font-bold text-slate-900">
                    {selectedAccount.bank_name} ({selectedAccount.account_number_mask})
                  </h3>
                  <p className="text-xs text-slate-400 font-medium">
                    Type: {selectedAccount.account_type} • Current Balance: {formatInr(Number(selectedAccount.current_balance))}
                  </p>
                </div>
              </div>

              <div className="flex items-center gap-2">
                <button
                  onClick={() => {
                    setNewBalance(String(selectedAccount.current_balance));
                    setIsEditModalOpen(true);
                  }}
                  className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs font-semibold transition-all active:scale-95"
                >
                  <Edit2 className="w-3.5 h-3.5" />
                  <span>Edit Balance</span>
                </button>
                <button
                  onClick={() => setConfirmDeleteId(selectedAccount.id)}
                  className="p-2 rounded-xl text-rose-600 hover:bg-rose-50 border border-rose-200/60 transition-all active:scale-95"
                  title="Delete Account"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
                <Link
                  href={`/transactions?account=${selectedAccount.id}`}
                  className="flex items-center gap-1 px-3 py-1.5 rounded-xl bg-blue-50 hover:bg-blue-100 text-blue-700 text-xs font-semibold transition-all"
                >
                  <span>All Account Txns</span>
                  <ArrowRight className="w-3.5 h-3.5" />
                </Link>
              </div>
            </div>

            {/* Account Recent Transactions */}
            <div>
              <h4 className="text-xs font-bold text-slate-500 uppercase tracking-wider mb-2">
                Recent Transactions for this Account ({accountTransactions.length})
              </h4>
              {accountTransactions.length === 0 ? (
                <p className="text-xs text-slate-400 py-4 text-center">
                  No transactions recorded for this account yet.
                </p>
              ) : (
                <div className="divide-y divide-slate-100">
                  {accountTransactions.map((tx) => {
                    const isCredit = tx.direction === 'CREDIT';
                    return (
                      <div key={tx.id} className="py-2.5 flex items-center justify-between text-xs">
                        <div className="flex items-center gap-2.5">
                          <div
                            className={`w-7 h-7 rounded-xl flex items-center justify-center shrink-0 ${
                              isCredit ? 'bg-emerald-50 text-emerald-600' : 'bg-rose-50 text-rose-600'
                            }`}
                          >
                            {isCredit ? <ArrowDownLeft className="w-3.5 h-3.5" /> : <ArrowUpRight className="w-3.5 h-3.5" />}
                          </div>
                          <div>
                            <p className="font-bold text-slate-900">{tx.merchant || 'Unknown'}</p>
                            <p className="text-[10px] text-slate-400">{formatDate(tx.transaction_date)}</p>
                          </div>
                        </div>
                        <span className={`font-mono font-bold ${isCredit ? 'text-emerald-600' : 'text-slate-900'}`}>
                          {isCredit ? '+' : '-'} {formatInr(tx.amount)}
                        </span>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          </GlassCard>
        )}

        {/* --- EDIT BALANCE MODAL --- */}
        {isEditModalOpen && selectedAccount && (
          <div className="fixed inset-0 z-50 overflow-y-auto bg-slate-900/60 backdrop-blur-md p-4 flex items-center justify-center min-h-screen animate-fade-in">
            <div className="fixed inset-0 -z-10" onClick={() => setIsEditModalOpen(false)} aria-hidden="true" />
            <div className="bg-white rounded-2xl sm:rounded-3xl p-6 max-w-sm w-full shadow-2xl space-y-4 border border-slate-100 my-auto text-left animate-slide-up">
              <div className="flex items-center justify-between pb-2 border-b border-slate-100">
                <h3 className="text-sm font-bold text-slate-900">Update Balance</h3>
                <button onClick={() => setIsEditModalOpen(false)} className="p-1 text-slate-400 hover:text-slate-600 rounded-full">
                  <X className="w-4 h-4" />
                </button>
              </div>

              <p className="text-xs text-slate-500">
                Adjust balance for <span className="font-bold text-slate-800">{selectedAccount.bank_name}</span> ({selectedAccount.account_number_mask}):
              </p>

              <form onSubmit={handleUpdateBalance} className="space-y-4">
                <div className="relative">
                  <span className="absolute left-3.5 top-1/2 -translate-y-1/2 text-sm font-bold text-slate-400">₹</span>
                  <input
                    type="number"
                    step="0.01"
                    required
                    value={newBalance}
                    onChange={(e) => setNewBalance(e.target.value)}
                    className="w-full pl-8 pr-4 py-2.5 text-base font-mono font-bold rounded-xl bg-slate-50 border border-slate-200 focus:bg-white text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500/20"
                    autoFocus
                  />
                </div>

                <div className="flex gap-2">
                  <button
                    type="submit"
                    disabled={isSavingBalance}
                    className="flex-1 py-2.5 rounded-xl bg-blue-600 text-white text-xs font-semibold shadow-sm hover:bg-blue-700 active:scale-95"
                  >
                    {isSavingBalance ? 'Updating...' : 'Save Balance'}
                  </button>
                  <button
                    type="button"
                    onClick={() => setIsEditModalOpen(false)}
                    className="px-3 py-2.5 rounded-xl bg-slate-100 text-slate-600 text-xs font-semibold hover:bg-slate-200"
                  >
                    Cancel
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}

        {/* --- DELETE CONFIRMATION MODAL --- */}
        {confirmDeleteId && (
          <div className="fixed inset-0 z-50 overflow-y-auto bg-slate-900/60 backdrop-blur-md p-4 flex items-center justify-center min-h-screen animate-fade-in">
            <div className="fixed inset-0 -z-10" onClick={() => setConfirmDeleteId(null)} aria-hidden="true" />
            <div className="bg-white rounded-2xl sm:rounded-3xl p-6 max-w-sm w-full shadow-2xl space-y-3 border border-slate-100 my-auto text-left animate-slide-up">
              <div className="flex items-center gap-2 text-rose-600 font-bold text-sm pb-1 border-b border-slate-100">
                <AlertTriangle className="w-5 h-5" />
                <span>Delete Account?</span>
              </div>
              <p className="text-xs text-slate-600 leading-relaxed">
                Are you sure you want to delete this account? Any associated transactions linked to this account will remain in your transaction history.
              </p>
              <div className="flex gap-2 pt-2">
                <button
                  onClick={() => handleDeleteAccount(confirmDeleteId)}
                  disabled={isDeleting}
                  className="flex-1 py-2.5 rounded-xl bg-rose-600 text-white text-xs font-semibold shadow-sm hover:bg-rose-700 active:scale-95"
                >
                  {isDeleting ? 'Deleting...' : 'Yes, Delete Account'}
                </button>
                <button
                  onClick={() => setConfirmDeleteId(null)}
                  className="px-3 py-2.5 rounded-xl bg-slate-100 text-slate-600 text-xs font-semibold hover:bg-slate-200"
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
    </div>
  );
}
