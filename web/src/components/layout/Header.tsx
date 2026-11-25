'use client';

import React, { useState } from 'react';
import Link from 'next/link';
import { RefreshCw, Plus, Sparkles, Download, MessageSquare, Settings } from 'lucide-react';
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
  const { openSyncModal, permissionState } = useSms();

  const todayFormatted = new Intl.DateTimeFormat('en-IN', {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
  }).format(new Date());

  const isGranted = permissionState === 'granted';

  return (
    <>
      <header
        className="bg-white/80 backdrop-blur-2xl border-b border-white/60 sticky top-0 z-30 shadow-[0_1px_12px_rgba(0,0,0,0.04)]"
        style={{ paddingTop: 'env(safe-area-inset-top)' }}
      >
        <div className="h-16 px-4 md:px-6 flex items-center justify-between gap-2.5">
          {/* ── Left: Brand & Date ──────────────────────────── */}
          <div className="flex items-center gap-2.5 min-w-0">
            {/* Mobile Logo Mark */}
            <div className="md:hidden w-9 h-9 rounded-xl bg-gradient-to-tr from-blue-600 to-indigo-600 flex items-center justify-center text-white shadow-sm shadow-blue-500/25 shrink-0">
              <Sparkles className="w-4 h-4" />
            </div>
            <div className="min-w-0">
              <div className="flex items-center gap-1.5">
                <h1 className="text-[15px] md:text-lg font-bold tracking-tight text-slate-900 leading-tight truncate">
                  Monzi
                </h1>
                <span className="text-[10px] font-bold px-1.5 py-0.5 rounded-md bg-blue-600 text-white shadow-sm shadow-blue-500/30 shrink-0">
                  2.0
                </span>
              </div>
              <p className="text-[11px] font-medium text-slate-400 leading-none mt-0.5 truncate">
                {todayFormatted}
              </p>
            </div>
          </div>

          {/* ── Right: Action Buttons ───────────────────────── */}
          <div className="flex items-center gap-1.5 sm:gap-2.5 shrink-0">
            {/* PWA Install Button (desktop/tablet) */}
            {!isInstalled && (isInstallable || isIos) && (
              <button
                onClick={triggerInstall}
                className="hidden sm:inline-flex items-center gap-1.5 min-h-[44px] px-3 rounded-xl text-xs font-semibold bg-indigo-50 border border-indigo-200/80 text-indigo-700 hover:bg-indigo-100 transition-all active:scale-95 shadow-sm"
                title="Install Monzi as a standalone native-feeling App"
              >
                <Download className="w-3.5 h-3.5 text-indigo-600" />
                <span>Install</span>
              </button>
            )}

            {/* Bank SMS Sync Pill */}
            <button
              onClick={openSyncModal}
              className="inline-flex items-center justify-center gap-1.5 min-h-[44px] min-w-[44px] px-2.5 sm:px-3.5 rounded-full text-xs font-semibold bg-emerald-50 border border-emerald-200/80 text-emerald-800 hover:bg-emerald-100 transition-all active:scale-95 shadow-sm"
              title="Sync or paste bank notification SMS"
            >
              <MessageSquare className="w-4 h-4 text-emerald-600 shrink-0" />
              <span className="hidden sm:inline">Sync SMS</span>
              {isGranted && (
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse-soft shrink-0" />
              )}
            </button>

            {/* Add Transaction Blue Pill (collapsed on xs since mobile BottomNav has + FAB) */}
            <button
              onClick={() => setIsAddModalOpen(true)}
              className="hidden sm:inline-flex items-center justify-center gap-1.5 min-h-[44px] px-4 rounded-full text-xs font-semibold bg-gradient-to-r from-blue-600 to-indigo-600 text-white shadow-md shadow-blue-500/25 hover:shadow-lg hover:shadow-blue-500/35 transition-all active:scale-95"
            >
              <Plus className="w-4 h-4" />
              <span>Add Transaction</span>
            </button>

            {/* Refresh Icon Button */}
            <button
              onClick={onRefresh}
              disabled={isRefreshing}
              className="w-11 h-11 flex items-center justify-center rounded-xl bg-white/90 border border-slate-200/80 text-slate-500 hover:text-slate-800 hover:bg-slate-50 shadow-sm transition-all active:scale-95 disabled:opacity-60"
              title="Refresh live data from Supabase"
              aria-label="Refresh data"
            >
              <RefreshCw
                className={`w-4 h-4 ${isRefreshing ? 'animate-spin text-blue-600' : ''}`}
              />
            </button>

            {/* Mobile Settings shortcut (since BottomNav has 5 items without Settings) */}
            <Link
              href="/settings"
              className="md:hidden w-11 h-11 flex items-center justify-center rounded-xl bg-white/90 border border-slate-200/80 text-slate-500 hover:text-slate-800 hover:bg-slate-50 shadow-sm transition-all active:scale-95"
              title="Settings"
              aria-label="Settings"
            >
              <Settings className="w-4 h-4" />
            </Link>

            {/* User Avatar — KB initials, links to Settings */}
            <Link
              href="/settings"
              className="w-10 h-10 md:w-11 md:h-11 rounded-full bg-gradient-to-tr from-blue-600 to-indigo-500 text-white flex items-center justify-center font-bold text-xs shadow-md shadow-blue-500/20 shrink-0 ring-2 ring-white hover:scale-105 active:scale-95 transition-transform"
              title="Account & Settings"
              aria-label="User Profile and Settings"
            >
              KB
            </Link>
          </div>
        </div>
      </header>

      <AddTransactionModal
        isOpen={isAddModalOpen}
        onClose={() => setIsAddModalOpen(false)}
        onSuccess={() => onRefresh && onRefresh()}
      />
    </>
  );
}
