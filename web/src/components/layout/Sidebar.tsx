'use client';

import React from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import {
  LayoutDashboard,
  ArrowLeftRight,
  CreditCard,
  PieChart,
  Settings,
  Sparkles,
  MessageSquare,
  CheckCircle2,
  Zap,
  Lock,
  LogOut,
} from 'lucide-react';
import { clsx } from 'clsx';
import { useSms } from '@/components/sms/SmsProvider';
import { useAuth } from '@/components/auth/AuthProvider';

const mainNavItems = [
  { name: 'Overview', href: '/', icon: LayoutDashboard },
  { name: 'Transactions', href: '/transactions', icon: ArrowLeftRight },
  { name: 'Accounts', href: '/accounts', icon: CreditCard },
  { name: 'Analytics', href: '/analytics', icon: PieChart },
];

const secondaryNavItems = [
  { name: 'Settings', href: '/settings', icon: Settings },
];

export function Sidebar() {
  const pathname = usePathname();
  const { isListening, openSyncModal, permissionState } = useSms();
  const { user, profile, biometricEnabled, lockSession, signOut } = useAuth();
  const isConnected = isListening || permissionState === 'granted';

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
    <aside className="hidden md:flex flex-col fixed left-0 top-0 bottom-0 w-72 bg-white/80 backdrop-blur-2xl border-r border-white/60 shadow-[2px_0_24px_rgba(0,0,0,0.04)] z-30">
      <div className="flex flex-col h-full px-5 py-6 overflow-y-auto scrollbar-thin">
        {/* ── Brand Header ──────────────────────────────────── */}
        <div className="flex items-center gap-3.5 mb-7 px-1">
          <div className="w-12 h-12 rounded-2xl bg-gradient-to-tr from-blue-600 via-indigo-500 to-violet-500 flex items-center justify-center shadow-lg shadow-blue-500/25 text-white shrink-0">
            <Sparkles className="w-6 h-6" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <span className="text-[22px] font-bold tracking-tight text-slate-900 leading-none">
                Monzi
              </span>
              <span className="text-[11px] font-bold px-2 py-0.5 rounded-md bg-blue-600 text-white shadow-sm shadow-blue-500/30">
                2.0
              </span>
            </div>
            <p className="text-[11px] text-slate-400 font-medium mt-0.5">Finance Intelligence</p>
          </div>
        </div>

        {/* ── Main Navigation ───────────────────────────────── */}
        <nav className="flex-1 flex flex-col gap-1">
          {mainNavItems.map((item) => {
            const Icon = item.icon;
            const isActive = pathname === item.href;
            return (
              <Link
                key={item.name}
                href={item.href}
                className={clsx(
                  'flex items-center gap-3 px-4 rounded-xl font-medium text-[14px] transition-all duration-200 min-h-[48px]',
                  isActive
                    ? 'bg-gradient-to-r from-blue-600 to-indigo-600 text-white shadow-md shadow-blue-500/25'
                    : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100/80 active:scale-[0.98]'
                )}
              >
                <Icon
                  className={clsx('w-5 h-5 shrink-0', isActive ? 'text-white' : 'text-slate-400')}
                />
                <span>{item.name}</span>
              </Link>
            );
          })}

          <div className="my-3 border-t border-slate-200/60" />

          {secondaryNavItems.map((item) => {
            const Icon = item.icon;
            const isActive = pathname === item.href;
            return (
              <Link
                key={item.name}
                href={item.href}
                className={clsx(
                  'flex items-center gap-3 px-4 rounded-xl font-medium text-[14px] transition-all duration-200 min-h-[48px]',
                  isActive
                    ? 'bg-gradient-to-r from-blue-600 to-indigo-600 text-white shadow-md shadow-blue-500/25'
                    : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100/80 active:scale-[0.98]'
                )}
              >
                <Icon
                  className={clsx('w-5 h-5 shrink-0', isActive ? 'text-white' : 'text-slate-400')}
                />
                <span>{item.name}</span>
              </Link>
            );
          })}
        </nav>

        {/* ── Automated SMS Sync Card ────────────────────────── */}
        <div
          onClick={openSyncModal}
          role="button"
          tabIndex={0}
          onKeyDown={(e) => e.key === 'Enter' && openSyncModal()}
          className="mt-4 p-4 rounded-2xl bg-gradient-to-br from-emerald-50/90 to-blue-50/70 border border-emerald-200/70 cursor-pointer hover:border-emerald-300 hover:shadow-md hover:shadow-emerald-500/10 transition-all active:scale-[0.98] select-none"
          title="Open Automated SMS Sync & Processing Logs"
        >
          <div className="flex items-center gap-2.5 mb-2">
            <div className="w-7 h-7 rounded-lg bg-emerald-100 flex items-center justify-center">
              <MessageSquare className="w-3.5 h-3.5 text-emerald-700" />
            </div>
            <span className="text-[13px] font-bold text-slate-900">Automated SMS Sync</span>
            <span className="flex h-2.5 w-2.5 relative ml-auto">
              <span
                className={clsx(
                  'animate-ping absolute inline-flex h-full w-full rounded-full opacity-70',
                  isConnected ? 'bg-emerald-400' : 'bg-amber-400'
                )}
              />
              <span
                className={clsx(
                  'relative inline-flex rounded-full h-2.5 w-2.5',
                  isConnected ? 'bg-emerald-500' : 'bg-amber-500'
                )}
              />
            </span>
          </div>
          <p className="text-[11px] text-slate-600 leading-relaxed mb-2.5">
            {isConnected
              ? 'Automatic bank SMS extraction & categorization is active.'
              : 'Connect Android companion or paste bank SMS to auto-sync.'}
          </p>
          <div className="flex items-center gap-1.5 text-[11px] font-semibold text-emerald-700">
            <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600" />
            <span>{isListening ? 'Listener Active' : 'Open Sync Center'}</span>
          </div>
        </div>

        {/* ── Authenticated User & Session Controls ──────────── */}
        <div className="mt-4 p-3 rounded-2xl bg-slate-50/90 border border-slate-200/70 space-y-2.5">
          <Link href="/settings" className="flex items-center gap-2.5 min-w-0 group">
            <div className="w-9 h-9 rounded-full bg-gradient-to-tr from-blue-600 to-indigo-500 text-white flex items-center justify-center font-bold text-xs shrink-0 shadow-sm">
              {initials}
            </div>
            <div className="min-w-0 flex-1">
              <p className="text-xs font-bold text-slate-900 truncate group-hover:text-blue-600 transition-colors">
                {profile?.full_name || 'Signed In'}
              </p>
              <p className="text-[10px] text-slate-500 truncate">
                {profile?.email || user?.email || ''}
              </p>
            </div>
          </Link>

          <div className="flex items-center gap-1.5">
            {biometricEnabled && (
              <button
                type="button"
                onClick={() => lockSession()}
                className="flex-1 inline-flex items-center justify-center gap-1.5 min-h-[36px] px-2.5 rounded-xl text-[11px] font-semibold bg-white border border-slate-200 text-slate-700 hover:bg-blue-50 hover:text-blue-700 hover:border-blue-200 transition-all"
                title="Lock session with biometrics"
              >
                <Lock className="w-3.5 h-3.5" />
                <span>Lock</span>
              </button>
            )}
            <button
              type="button"
              onClick={() => signOut()}
              className="flex-1 inline-flex items-center justify-center gap-1.5 min-h-[36px] px-2.5 rounded-xl text-[11px] font-semibold bg-white border border-slate-200 text-slate-700 hover:bg-rose-50 hover:text-rose-700 hover:border-rose-200 transition-all"
              title="Sign Out"
            >
              <LogOut className="w-3.5 h-3.5" />
              <span>Sign Out</span>
            </button>
          </div>
        </div>

        {/* ── Version Footer ────────────────────────────────── */}
        <div className="mt-3 flex items-center gap-1.5 px-1">
          <Zap className="w-3 h-3 text-blue-400" />
          <span className="text-[10px] text-slate-400 font-medium tracking-wide">
            Monzi 2.0 • Isolated Live DB
          </span>
        </div>
      </div>
    </aside>
  );
}
