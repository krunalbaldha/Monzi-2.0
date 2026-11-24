'use client';

import React, { useState } from 'react';
import { RefreshCw, Plus, Sparkles, Download, MessageSquare } from 'lucide-react';
import { AddTransactionModal } from '@/components/dashboard/AddTransactionModal';
import { usePwa } from '@/components/pwa/PwaProvider';
import { useSms } from '@/components/sms/SmsProvider';

interface HeaderProps {
  onRefresh?: () => void;
  isRefreshing?: boolean;
}

export function Header({ onRefresh, isRefreshing }: HeaderProps) {
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);
  const { isInstallable, isInstalled, isIos, triggerInstall } = usePwa();
  const { openSyncModal, isListening, permissionState } = useSms();

  const todayFormatted = new Intl.DateTimeFormat('en-IN', {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
  }).format(new Date());

  return (
    <header className="h-16 md:h-20 bg-white/80 backdrop-blur-glass border-b border-slate-200/60 sticky top-0 z-30 px-4 md:px-8 flex items-center justify-between">
      {/* Brand & Date */}
      <div className="flex items-center gap-3">
        {/* Mobile Logo Mark */}
        <div className="md:hidden w-8 h-8 rounded-xl bg-gradient-to-tr from-blue-600 to-indigo-600 flex items-center justify-center text-white shadow-sm shadow-blue-500/20">
          <Sparkles className="w-4 h-4" />
        </div>
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-base md:text-xl font-bold tracking-tight text-slate-900 leading-tight">
              Monzi
            </h1>
            <span className="text-[10px] md:text-xs font-bold px-2 py-0.5 rounded-full bg-blue-50 text-blue-700 border border-blue-200/60">
              2.0
            </span>
          </div>
          <p className="text-[11px] font-medium text-slate-500">{todayFormatted}</p>
        </div>
      </div>

      {/* Action Buttons */}
      <div className="flex items-center gap-2 md:gap-3">
        {/* PWA Install Button (shows when installable or iOS) */}
        {!isInstalled && (isInstallable || isIos) && (
          <button
            onClick={triggerInstall}
            className="flex items-center gap-1.5 px-3 py-1.5 md:px-3.5 md:py-2 rounded-xl text-xs font-semibold bg-indigo-50 border border-indigo-200/80 text-indigo-700 hover:bg-indigo-100 transition-all active:scale-95 shadow-sm"
            title="Install Monzi as a standalone native-feeling App"
          >
            <Download className="w-3.5 h-3.5 text-indigo-600" />
            <span className="hidden sm:inline">Install App</span>
            <span className="sm:hidden">Install</span>
          </button>
        )}

        {/* Bank SMS Sync Action Button */}
        <button
          onClick={openSyncModal}
          className="flex items-center gap-1.5 px-2.5 py-1.5 md:px-3 md:py-2 rounded-xl text-xs font-semibold bg-emerald-50 border border-emerald-200/80 text-emerald-800 hover:bg-emerald-100 transition-all active:scale-95 shadow-sm"
          title="Sync or paste bank notification SMS"
        >
          <MessageSquare className="w-3.5 h-3.5 text-emerald-600" />
          <span className="hidden sm:inline">Sync SMS</span>
          <span className="sm:hidden">SMS</span>
          {permissionState === 'granted' && (
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
          )}
        </button>

        {/* Quick Manual Add Button */}
        <button
          onClick={() => setIsAddModalOpen(true)}
          className="flex items-center gap-1.5 px-3 py-1.5 md:px-3.5 md:py-2 rounded-xl text-xs font-semibold bg-blue-600 text-white shadow-sm shadow-blue-500/20 hover:bg-blue-700 transition-all active:scale-95"
        >
          <Plus className="w-3.5 h-3.5" />
          <span className="hidden sm:inline">Add Transaction</span>
          <span className="sm:hidden">Add</span>
        </button>

        {/* Live Refresh Button */}
        <button
          onClick={onRefresh}
          disabled={isRefreshing}
          className="p-2 md:p-2.5 rounded-xl bg-white/90 border border-slate-200/80 text-slate-600 hover:text-slate-900 hover:bg-slate-50 shadow-sm transition-all active:scale-95"
          title="Refresh live data from Supabase"
        >
          <RefreshCw className={`w-3.5 h-3.5 md:w-4 md:h-4 ${isRefreshing ? 'animate-spin text-blue-600' : ''}`} />
        </button>

        {/* Profile Pill */}
        <div className="flex items-center gap-2 pl-2 md:pl-3 border-l border-slate-200">
          <div className="w-8 h-8 md:w-9 md:h-9 rounded-xl bg-gradient-to-tr from-slate-900 to-slate-700 text-white flex items-center justify-center font-bold text-xs shadow-sm">
            KB
          </div>
          <div className="hidden lg:block text-left">
            <p className="text-xs font-semibold text-slate-900 leading-tight">Krunal Baldha</p>
            <p className="text-[10px] text-emerald-600 font-medium flex items-center gap-1">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 inline-block animate-pulse" />
              Live DB
            </p>
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
