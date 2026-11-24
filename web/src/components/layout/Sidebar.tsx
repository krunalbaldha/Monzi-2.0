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
  Smartphone,
  CheckCircle2
} from 'lucide-react';
import { clsx } from 'clsx';

const navItems = [
  { name: 'Overview', href: '/', icon: LayoutDashboard },
  { name: 'Transactions', href: '/transactions', icon: ArrowLeftRight },
  { name: 'Accounts', href: '/accounts', icon: CreditCard },
  { name: 'Analytics', href: '/analytics', icon: PieChart },
  { name: 'Settings', href: '/settings', icon: Settings },
];

export function Sidebar() {
  const pathname = usePathname();

  return (
    <aside className="hidden md:flex flex-col fixed left-0 top-0 bottom-0 w-64 bg-white/70 backdrop-blur-glass border-r border-slate-200/60 z-30 p-6 shadow-sm">
      {/* Brand Header */}
      <div className="flex items-center gap-3 mb-10 px-2">
        <div className="w-10 h-10 rounded-2xl bg-gradient-to-tr from-blue-600 via-indigo-600 to-violet-500 flex items-center justify-center shadow-md shadow-blue-500/20 text-white">
          <Sparkles className="w-5 h-5" />
        </div>
        <div>
          <span className="text-xl font-bold tracking-tight bg-clip-text text-transparent bg-gradient-to-r from-slate-900 to-slate-700">
            Monzi
          </span>
          <span className="ml-1 text-xs font-semibold px-1.5 py-0.5 rounded-md bg-blue-100 text-blue-700">
            2.0
          </span>
          <p className="text-[11px] text-slate-500 font-medium -mt-0.5">Finance Intelligence</p>
        </div>
      </div>

      {/* Navigation Links */}
      <nav className="flex-1 space-y-1.5">
        {navItems.map((item) => {
          const Icon = item.icon;
          const isActive = pathname === item.href;
          return (
            <Link
              key={item.name}
              href={item.href}
              className={clsx(
                'flex items-center gap-3 px-3.5 py-2.5 rounded-xl font-medium text-sm transition-all duration-200',
                isActive
                  ? 'bg-blue-600 text-white shadow-sm shadow-blue-600/30'
                  : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100/70'
              )}
            >
              <Icon className={clsx('w-4 h-4', isActive ? 'text-white' : 'text-slate-500')} />
              <span>{item.name}</span>
            </Link>
          );
        })}
      </nav>

      {/* Integration Status Card */}
      <div className="mt-auto p-4 rounded-2xl bg-gradient-to-b from-blue-50/80 to-indigo-50/50 border border-blue-100/70">
        <div className="flex items-center gap-2 mb-1.5">
          <Smartphone className="w-4 h-4 text-blue-600" />
          <span className="text-xs font-semibold text-slate-900">Android Sync</span>
          <span className="flex h-2 w-2 relative ml-auto">
            <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
            <span className="relative inline-flex rounded-full h-2 w-2 bg-emerald-500"></span>
          </span>
        </div>
        <p className="text-[11px] text-slate-600 leading-relaxed">
          Zero manual entry. Financial transactions synced from SMS.
        </p>
        <div className="mt-2.5 flex items-center gap-1.5 text-[11px] font-medium text-emerald-700">
          <CheckCircle2 className="w-3.5 h-3.5" />
          <span>Receiver Active</span>
        </div>
      </div>
    </aside>
  );
}
