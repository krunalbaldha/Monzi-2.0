'use client';

import React, { useState, useEffect } from 'react';
import { Header } from '@/components/layout/Header';
import { GlassCard } from '@/components/ui/GlassCard';
import { CreditCard, ShieldCheck, Clock, Plus } from 'lucide-react';
import { Account } from '@/lib/types';
import { mockAccounts } from '@/lib/mockData';
import { AddAccountModal } from '@/components/dashboard/AddAccountModal';

export default function AccountsPage() {
  const [accounts, setAccounts] = useState<Account[]>(mockAccounts);
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);

  const fetchAccounts = async () => {
    try {
      const res = await fetch('/api/accounts');
      if (res.ok) {
        const data = await res.json();
        if (data.accounts) setAccounts(data.accounts);
      }
    } catch (e) {
      console.error(e);
    }
  };

  useEffect(() => {
    fetchAccounts();
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
      <Header onRefresh={fetchAccounts} />

      <div className="p-8 space-y-6 max-w-7xl mx-auto w-full">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-xl font-bold tracking-tight text-slate-900">Tracked Bank Accounts</h2>
            <p className="text-xs text-slate-500 font-medium">
              Automatically discovered and tracked from incoming bank SMS
            </p>
          </div>
          <button
            onClick={() => setIsAddModalOpen(true)}
            className="flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-blue-600 text-white text-xs font-semibold shadow-sm hover:bg-blue-700 transition-all active:scale-95"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>Add Account Manually</span>
          </button>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {accounts.map((acc) => (
            <GlassCard key={acc.id} className="p-6 relative overflow-hidden flex flex-col justify-between">
              <div>
                <div className="flex items-center justify-between mb-4">
                  <div className="w-12 h-12 rounded-2xl bg-gradient-to-tr from-blue-500 to-indigo-600 text-white flex items-center justify-center shadow-md shadow-blue-500/20">
                    <CreditCard className="w-6 h-6" />
                  </div>
                  <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[11px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
                    <ShieldCheck className="w-3 h-3" /> Auto-Synced
                  </span>
                </div>

                <h3 className="text-lg font-bold text-slate-900 tracking-tight">{acc.bank_name}</h3>
                <p className="text-xs font-mono text-slate-500 font-medium mt-0.5">
                  Masked No: {acc.account_number_mask}
                </p>
                <p className="text-xs font-medium text-slate-400 capitalize mt-1">
                  Type: {acc.account_type.replace('_', ' ').toLowerCase()}
                </p>
              </div>

              <div className="mt-8 pt-4 border-t border-slate-100 flex items-end justify-between">
                <div>
                  <span className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider">
                    Tracked Balance
                  </span>
                  <p className="text-2xl font-bold font-mono text-slate-900">
                    {formatInr(acc.current_balance)}
                  </p>
                </div>
                {acc.last_synced_at && (
                  <div className="text-right text-[10px] text-slate-400 flex items-center gap-1 font-medium">
                    <Clock className="w-3 h-3" />
                    <span>Active</span>
                  </div>
                )}
              </div>
            </GlassCard>
          ))}
        </div>

        <AddAccountModal
          isOpen={isAddModalOpen}
          onClose={() => setIsAddModalOpen(false)}
          onSuccess={fetchAccounts}
        />
      </div>
    </div>
  );
}
