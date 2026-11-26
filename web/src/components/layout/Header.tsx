'use client';

import React from 'react';
import Link from 'next/link';
import {
  RefreshCw,
  Sparkles,
  Download,
  MessageSquare,
  ShieldCheck,
  Lock,
  LogOut,
} from 'lucide-react';
import { usePwa } from '@/components/pwa/PwaProvider';
import { useSms } from '@/components/sms/SmsProvider';
import { useAuth } from '@/components/auth/AuthProvider';

interface HeaderProps {
  onRefresh?: () => void;
  isRefreshing?: boolean;
}

export function Header({ onRefresh, isRefreshing }: HeaderProps) {
  const { isInstallable, isInstalled, isIos, isOffline, triggerInstall } = usePwa();
  const {
    openSyncModal,
    openSetupModal,
    permissionState,
    isAndroidNative,
    isListening,
    pendingLocalCount,
  } = useSms();
  const { user, profile, biometricEnabled, lockSession, signOut } = useAuth();

  const todayFormatted = new Intl.DateTimeFormat('en-IN', {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
  }).format(new Date());

  const isLiveSmsActive = isAndroidNative && permissionState === 'granted' && isListening;
  const initials =
    profile?.initials ||
    (profile?.full_name
      ? profile.full_name
          .split(/\s+/)
          .filter(Boolean)
          .slice(0, 2)
          .map((p) => p[0].toUpperCase())
          .join('')
      : user?.email
      ? String(user.email).slice(0, 2).toUpperCase()
      : 'U');

  return (
    <header
      className="bg-white/80 backdrop-blur-2xl border-b border-white/60 sticky top-0 z-30 shadow-[0_1px_12px_rgba(0,0,0,0.04)]"
      style={{ paddingTop: 'env(safe-area-inset-top)' }}
    >
      <div className="h-16 px-4 md:px-6 flex items-center justify-between gap-2">
        {/* ── Left: Brand & Date ──────────────────────────── */}
        <div className="flex items-center gap-2.5 min-w-0">
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
              {profile?.full_name ? ` • ${profile.full_name.split(' ')[0]}` : ''}
            </p>
          </div>
        </div>

        {/* ── Right: Automation Status, Sync SMS & Actions ─── */}
        <div className="flex items-center gap-1.5 sm:gap-2 shrink-0">
          {/* PWA Install Button */}
          {!isInstalled && (isInstallable || isIos) && (
            <button
              type="button"
              onClick={triggerInstall}
              className="hidden sm:inline-flex items-center gap-1.5 min-h-[44px] px-3 rounded-xl text-xs font-semibold bg-indigo-50 border border-indigo-200/80 text-indigo-700 hover:bg-indigo-100 transition-all active:scale-95 shadow-sm"
              title="Install Monzi as a standalone App"
            >
              <Download className="w-3.5 h-3.5 text-indigo-600" />
              <span>Install</span>
            </button>
          )}

          {/* Setup & Live Status Pill */}
          <button
            type="button"
            onClick={openSetupModal}
            className="inline-flex items-center justify-center gap-1.5 min-h-[44px] px-2.5 sm:px-3.5 rounded-full text-xs font-semibold bg-white/90 border border-slate-200/80 text-slate-700 hover:bg-slate-50 hover:border-slate-300 shadow-sm transition-all active:scale-95"
            title="View Setup Checklist & Live Automation Status"
            aria-label="Setup and Live Status"
          >
            <span
              className={`w-2 h-2 rounded-full shrink-0 ${
                isOffline
                  ? 'bg-amber-500'
                  : isLiveSmsActive
                  ? 'bg-emerald-500 animate-pulse-soft'
                  : 'bg-blue-500'
              }`}
            />
            <ShieldCheck className="w-3.5 h-3.5 text-blue-600 sm:hidden" />
            <span className="hidden sm:inline">
              {isOffline ? 'Offline' : isLiveSmsActive ? 'Live' : 'Setup'}
            </span>
          </button>

          {/* Primary Automated SMS Sync Pill */}
          <button
            type="button"
            onClick={openSyncModal}
            className="relative inline-flex items-center justify-center gap-1.5 min-h-[44px] min-w-[44px] px-3 sm:px-4 rounded-full text-xs font-bold bg-gradient-to-r from-emerald-600 to-teal-600 text-white shadow-md shadow-emerald-500/25 hover:shadow-lg hover:shadow-emerald-500/35 transition-all active:scale-95"
            title="Automated SMS Sync & Processing Logs"
            aria-label="Sync SMS"
          >
            <MessageSquare className="w-4 h-4 shrink-0" />
            <span className="hidden sm:inline">Sync SMS</span>
            {pendingLocalCount > 0 ? (
              <span className="px-1.5 py-0.5 text-[10px] font-bold rounded-full bg-amber-400 text-slate-900 leading-none">
                {pendingLocalCount}
              </span>
            ) : (
              isLiveSmsActive && (
                <span className="w-2 h-2 rounded-full bg-white animate-pulse-soft shrink-0" />
              )
            )}
          </button>

          {/* Refresh Icon Button */}
          <button
            type="button"
            onClick={onRefresh}
            disabled={isRefreshing}
            className="w-11 h-11 flex items-center justify-center rounded-xl bg-white/90 border border-slate-200/80 text-slate-500 hover:text-slate-800 hover:bg-slate-50 shadow-sm transition-all active:scale-95 disabled:opacity-60"
            title="Refresh live data"
            aria-label="Refresh data"
          >
            <RefreshCw
              className={`w-4 h-4 ${isRefreshing ? 'animate-spin text-blue-600' : ''}`}
            />
          </button>

          {/* Biometric Lock Button (if enabled) */}
          {biometricEnabled && (
            <button
              type="button"
              onClick={() => lockSession()}
              className="w-11 h-11 flex items-center justify-center rounded-xl bg-white/90 border border-slate-200/80 text-slate-600 hover:text-blue-600 hover:bg-blue-50 shadow-sm transition-all active:scale-95"
              title="Lock App"
              aria-label="Lock App"
            >
              <Lock className="w-4 h-4" />
            </button>
          )}

          {/* Mobile Sign Out Button */}
          <button
            type="button"
            onClick={() => signOut()}
            className="md:hidden w-11 h-11 flex items-center justify-center rounded-xl bg-white/90 border border-slate-200/80 text-slate-500 hover:text-rose-600 hover:bg-rose-50 shadow-sm transition-all active:scale-95"
            title="Sign Out"
            aria-label="Sign Out"
          >
            <LogOut className="w-4 h-4" />
          </button>

          {/* Authenticated User Avatar */}
          <Link
            href="/settings"
            className="w-10 h-10 md:w-11 md:h-11 rounded-full bg-gradient-to-tr from-blue-600 to-indigo-500 text-white flex items-center justify-center font-bold text-xs shadow-md shadow-blue-500/20 shrink-0 ring-2 ring-white hover:scale-105 active:scale-95 transition-transform"
            title={profile?.full_name || user?.email || 'Account & Settings'}
            aria-label="User Profile and Settings"
          >
            {initials}
          </Link>
        </div>
      </div>
    </header>
  );
}
