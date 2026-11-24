'use client';

import React, { useState } from 'react';
import { Bell, RefreshCw, Send, Check, Plus, Sparkles } from 'lucide-react';
import { AddTransactionModal } from '@/components/dashboard/AddTransactionModal';

interface HeaderProps {
  onRefresh?: () => void;
}

export function Header({ onRefresh }: HeaderProps) {
  const [isSyncing, setIsSyncing] = useState(false);
  const [simulated, setSimulated] = useState(false);
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);

  // Quick test: simulate an incoming bank SMS to prove the architecture
  const handleSimulateSms = async () => {
    setIsSyncing(true);
    try {
      const sampleSms = {
        device_id: 'test-device-s24',
        sync_secret: process.env.NEXT_PUBLIC_MONZI_SYNC_SECRET || 'monzi_personal_sync_secret',
        transactions: [
          {
            transaction_date: new Date().toISOString(),
            amount: 320.0,
            direction: 'DEBIT' as const,
            bank_name: 'HDFC Bank',
            account_mask: 'XX8421',
            merchant: 'Starbucks Coffee',
            payment_mode: 'UPI',
            reference_id: `UPI${Math.floor(Math.random() * 90000000 + 10000000)}`,
            parsed_available_balance: 142260.5,
          },
        ],
      };

      await fetch('/api/sync', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(sampleSms),
      });

      setSimulated(true);
      setTimeout(() => setSimulated(false), 3000);
      if (onRefresh) onRefresh();
    } catch (e) {
      console.error(e);
    } finally {
      setIsSyncing(false);
    }
  };

  const todayFormatted = new Intl.DateTimeFormat('en-IN', {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
  }).format(new Date());

  return (
    <header className="h-16 md:h-20 bg-white/75 backdrop-blur-glass border-b border-slate-200/60 sticky top-0 z-20 px-4 md:px-8 flex items-center justify-between">
      {/* Brand & Date */}
      <div className="flex items-center gap-3">
        {/* Mobile Logo Mark */}
        <div className="md:hidden w-8 h-8 rounded-xl bg-gradient-to-tr from-blue-600 to-indigo-600 flex items-center justify-center text-white shadow-sm">
          <Sparkles className="w-4 h-4" />
        </div>
        <div>
          <h1 className="text-base md:text-xl font-bold tracking-tight text-slate-900 leading-tight">
            Monzi <span className="md:inline text-xs font-semibold px-1.5 py-0.5 rounded-md bg-blue-100 text-blue-700">2.0</span>
          </h1>
          <p className="text-[11px] font-medium text-slate-500">{todayFormatted}</p>
        </div>
      </div>

      {/* Action Buttons */}
      <div className="flex items-center gap-2 md:gap-3">
        {/* Quick Manual Add Button */}
        <button
          onClick={() => setIsAddModalOpen(true)}
          className="flex items-center gap-1.5 px-3 py-1.5 md:px-3.5 md:py-2 rounded-xl text-xs font-semibold bg-blue-600 text-white shadow-sm shadow-blue-500/20 hover:bg-blue-700 transition-all active:scale-95"
        >
          <Plus className="w-3.5 h-3.5" />
          <span className="hidden sm:inline">Add Transaction</span>
          <span className="sm:hidden">Add</span>
        </button>

        {/* Test Sync Simulation Button */}
        <button
          onClick={handleSimulateSms}
          disabled={isSyncing}
          className="flex items-center gap-1.5 px-2.5 py-1.5 md:px-3.5 md:py-2 rounded-xl text-xs font-semibold bg-white/90 border border-slate-200/80 shadow-sm text-slate-700 hover:bg-slate-50 hover:border-slate-300 transition-all active:scale-95"
          title="Simulate an incoming HDFC bank SMS for testing"
        >
          {simulated ? (
            <>
              <Check className="w-3.5 h-3.5 text-emerald-600" />
              <span className="text-emerald-700 hidden sm:inline">SMS Synced!</span>
            </>
          ) : (
            <>
              <Send className="w-3.5 h-3.5 text-blue-600" />
              <span className="hidden md:inline">Simulate Bank SMS</span>
              <span className="md:hidden text-[11px]">Simulate</span>
            </>
          )}
        </button>

        {/* Refresh Button */}
        <button
          onClick={onRefresh}
          className="p-2 md:p-2.5 rounded-xl bg-white/90 border border-slate-200/80 text-slate-600 hover:text-slate-900 hover:bg-slate-50 shadow-sm transition-all"
        >
          <RefreshCw className="w-3.5 h-3.5 md:w-4 md:h-4" />
        </button>

        {/* Profile Pill */}
        <div className="flex items-center gap-2 pl-2 md:pl-3 border-l border-slate-200">
          <div className="w-8 h-8 md:w-9 md:h-9 rounded-xl bg-gradient-to-tr from-slate-900 to-slate-700 text-white flex items-center justify-center font-bold text-xs shadow-sm">
            KB
          </div>
          <div className="hidden lg:block text-left">
            <p className="text-xs font-semibold text-slate-900 leading-tight">Krunal Baldha</p>
            <p className="text-[10px] text-emerald-600 font-medium">Personal Active</p>
          </div>
        </div>
      </div>

      <AddTransactionModal
        isOpen={isAddModalOpen}
        onClose={() => setIsAddModalOpen(false)}
        onSuccess={() => onRefresh && onRefresh()}
      />
    </header>
  );
}
