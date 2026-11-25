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
} from 'lucide-react';
import { clsx } from 'clsx';
import { useSms } from '@/components/sms/SmsProvider';

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
  const isConnected = isListening || permissionState === 'granted';

  return (
    <aside className="hidden md:flex flex-col fixed left-0 top-0 bottom-0 w-72 bg-white/80 backdrop-blur-2xl border-r border-white/60 shadow-[2px_0_24px_rgba(0,0,0,0.04)] z-30">
      {/* Inner container with padding */}
      <div className="flex flex-col h-full px-5 py-6">

        {/* ── Brand Header ──────────────────────────────────── */}
        <div className="flex items-center gap-3.5 mb-8 px-1">
          {/* Monzi icon 48×48 */}
          <div className="w-12 h-12 rounded-2xl bg-gradient-to-tr from-blue-600 via-indigo-500 to-violet-500 flex items-center justify-center shadow-lg shadow-blue-500/25 text-white shrink-0">
            <Sparkles className="w-6 h-6" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <span className="text-[22px] font-bold tracking-tight text-slate-900 leading-none">
                Monzi
              </span>
              {/* Solid blue 2.0 badge */}
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

          {/* Divider between nav groups */}
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

        {/* ── SMS Sync Card ─────────────────────────────────── */}
        <div
          onClick={openSyncModal}
          role="button"
          tabIndex={0}
          onKeyDown={(e) => e.key === 'Enter' && openSyncModal()}
          className="mt-4 p-4 rounded-2xl bg-gradient-to-br from-blue-50 to-indigo-50/60 border border-blue-100/80 cursor-pointer hover:border-blue-300/80 hover:shadow-md hover:shadow-blue-500/08 transition-all active:scale-[0.98] select-none"
          title="Open SMS Reader & Sync Center"
        >
          <div className="flex items-center gap-2.5 mb-2">
            <div className="w-7 h-7 rounded-lg bg-blue-100 flex items-center justify-center">
              <MessageSquare className="w-3.5 h-3.5 text-blue-600" />
            </div>
            <span className="text-[13px] font-semibold text-slate-900">Bank SMS Sync</span>
            {/* Live pulse dot */}
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
          <p className="text-[11px] text-slate-500 leading-relaxed mb-2.5">
            {isConnected
              ? 'Automatic bank SMS extraction is active and monitoring.'
              : 'Tap to grant permission or paste SMS messages to sync.'}
          </p>
          <div className="flex items-center gap-1.5 text-[11px] font-semibold text-emerald-700">
            <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600" />
            <span>{isListening ? 'WebOTP Listening' : 'Sync Reader Active'}</span>
          </div>
        </div>

        {/* ── Version Footer ────────────────────────────────── */}
        <div className="mt-4 flex items-center gap-1.5 px-1">
          <Zap className="w-3 h-3 text-blue-400" />
          <span className="text-[10px] text-slate-400 font-medium tracking-wide">Monzi 2.0 • Live DB</span>
        </div>
      </div>
    </aside>
  );
}
