'use client';

import React from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { LayoutDashboard, ArrowLeftRight, CreditCard, PieChart, Settings } from 'lucide-react';
import { clsx } from 'clsx';

const navItems = [
  { name: 'Home', href: '/', icon: LayoutDashboard },
  { name: 'Transactions', href: '/transactions', icon: ArrowLeftRight },
  { name: 'Accounts', href: '/accounts', icon: CreditCard },
  { name: 'Analytics', href: '/analytics', icon: PieChart },
  { name: 'Settings', href: '/settings', icon: Settings },
];

export function BottomNav() {
  const pathname = usePathname();

  return (
    <nav className="md:hidden fixed bottom-0 left-0 right-0 z-40 bg-white/85 backdrop-blur-xl border-t border-slate-200/70 pt-2 pb-[max(env(safe-area-inset-bottom),12px)] px-3 shadow-[0_-8px_30px_rgba(0,0,0,0.04)]">
      <div className="flex items-center justify-around max-w-md mx-auto">
        {navItems.map((item) => {
          const Icon = item.icon;
          const isActive = pathname === item.href;
          return (
            <Link
              key={item.name}
              href={item.href}
              className={clsx(
                'flex flex-col items-center justify-center flex-1 py-1 transition-all duration-150 active:scale-90',
                isActive
                  ? 'text-blue-600 font-bold'
                  : 'text-slate-400 hover:text-slate-600 font-medium'
              )}
            >
              <div
                className={clsx(
                  'relative p-1 rounded-xl transition-all duration-200',
                  isActive && 'text-blue-600 scale-110'
                )}
              >
                <Icon className="w-5 h-5 stroke-[2.2]" />
                {isActive && (
                  <span className="absolute -bottom-1 left-1/2 -translate-x-1/2 w-1.5 h-1.5 rounded-full bg-blue-600 shadow-sm shadow-blue-500/50" />
                )}
              </div>
              <span className="text-[10px] tracking-tight mt-1 leading-none">{item.name}</span>
            </Link>
          );
        })}
      </div>
    </nav>
  );
}
