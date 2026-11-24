'use client';

import React, { useState, useEffect } from 'react';
import { Header } from '@/components/layout/Header';
import { GlassCard } from '@/components/ui/GlassCard';
import { ShieldCheck, Clock, Plus, Wifi, ArrowUpRight, ArrowDownLeft } from 'lucide-react';
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
        if (data.accounts && data.accounts.length > 0) setAccounts(data.accounts);
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

  // Apple Wallet Card Style Gradients based on bank name & type
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
      <Header onRefresh={fetchAccounts} />

      <div className="p-4 sm:p-6 md:p-8 space-y-5 md:space-y-6 max-w-7xl mx-auto w-full">
        {/* Title bar */}
        <div className="flex items-center justify-between gap-3">
          <div>
            <h2 className="text-lg sm:text-xl font-bold tracking-tight text-slate-900">Wallet & Accounts</h2>
            <p className="text-[11px] sm:text-xs text-slate-500 font-medium">
              Live balances tracked from official bank SMS notifications
            </p>
          </div>
          <button
            onClick={() => setIsAddModalOpen(true)}
            className="flex items-center gap-1.5 px-3 py-1.5 md:px-3.5 md:py-2 rounded-xl bg-blue-600 text-white text-xs font-semibold shadow-sm shadow-blue-500/20 hover:bg-blue-700 transition-all active:scale-95 shrink-0"
          >
            <Plus className="w-3.5 h-3.5" />
            <span className="hidden sm:inline">Add Account</span>
            <span className="sm:hidden">Add</span>
          </button>
        </div>

        {/* --- APPLE WALLET STYLE CARDS --- */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 md:gap-6">
          {accounts.map((acc) => {
            const theme = getCardTheme(acc.bank_name, acc.account_type);
            const maskDigits = acc.account_number_mask.replace(/[^0-9]/g, '') || '0000';

            return (
              <div
                key={acc.id}
                className={`relative rounded-3xl p-6 ${theme.bg} ${theme.textColor} shadow-xl shadow-slate-900/10 overflow-hidden flex flex-col justify-between min-h-[210px] transition-all hover:scale-[1.01] active:scale-[0.99]`}
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
                    <span>Auto-Synced</span>
                  </span>
                </div>

                {/* Card Bottom: Masked Number & Tracked Balance */}
                <div className="relative z-10 pt-2 flex items-end justify-between">
                  <div>
                    <p className="font-mono text-xs tracking-widest opacity-80 mb-1">
                      •••• •••• •••• {maskDigits}
                    </p>
                    <p className="text-[10px] uppercase font-bold tracking-wider opacity-60">
                      KRUNAL BALDHA
                    </p>
                  </div>

                  <div className="text-right">
                    <span className={`text-[10px] font-semibold uppercase tracking-wider ${theme.subText}`}>
                      Available Balance
                    </span>
                    <p className="text-2xl font-bold font-mono tracking-tight text-white leading-none mt-0.5">
                      {formatInr(acc.current_balance)}
                    </p>
                  </div>
                </div>
              </div>
            );
          })}
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
