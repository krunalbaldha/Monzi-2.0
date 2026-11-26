'use client';

import React from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { Home, ArrowLeftRight, PieChart, CreditCard, Settings } from 'lucide-react';
import { clsx } from 'clsx';

const navItems = [
  { name: 'Home', href: '/', icon: Home },
  { name: 'Transactions', href: '/transactions', icon: ArrowLeftRight },
  { name: 'Analytics', href: '/analytics', icon: PieChart },
  { name: 'Accounts', href: '/accounts', icon: CreditCard },
  { name: 'Settings', href: '/settings', icon: Settings },
];

export function BottomNav() {
  const pathname = usePathname() || '/';

  return (
    <nav
      className="md:hidden fixed bottom-0 left-0 right-0 z-40 bg-white/85 backdrop-blur-2xl border-t border-white/60 shadow-[0_-8px_30px_rgba(0,0,0,0.06)]"
      style={{ paddingBottom: 'env(safe-area-inset-bottom)' }}
      aria-label="Primary Bottom Navigation"
    >
      <div className="flex items-center justify-around px-1.5 h-[72px] max-w-lg mx-auto">
        {navItems.map((item) => {
          const Icon = item.icon;
          const isActive =
            item.href === '/'
              ? pathname === '/'
              : pathname === item.href || pathname.startsWith(`${item.href}/`);

          return (
            <Link
              key={item.name}
              href={item.href}
              aria-current={isActive ? 'page' : undefined}
              className={clsx(
                'flex flex-col items-center justify-center flex-1 py-1.5 transition-all duration-200 active:scale-90 relative min-h-[44px] rounded-2xl',
                isActive ? 'text-blue-600' : 'text-slate-400 hover:text-slate-600'
              )}
            >
              <div
                className={clsx(
                  'relative flex items-center justify-center px-3 py-1 rounded-full transition-all duration-200',
                  isActive ? 'bg-blue-50/90' : 'bg-transparent'
                )}
              >
                <Icon
                  className={clsx(
                    'w-5 h-5 stroke-[2] transition-transform duration-200',
                    isActive ? 'text-blue-600 scale-105' : 'text-slate-400'
                  )}
                />
              </div>
              <span
                className={clsx(
                  'text-[11px] mt-1 leading-none transition-colors duration-200 truncate max-w-full px-0.5',
                  isActive ? 'text-blue-600 font-semibold' : 'text-slate-400 font-medium'
                )}
              >
                {item.name}
              </span>
              <span
                className={clsx(
                  'absolute bottom-1 left-1/2 -translate-x-1/2 h-1 rounded-full bg-blue-600 transition-all duration-300',
                  isActive ? 'w-4 opacity-100 shadow-sm shadow-blue-500/50' : 'w-0 opacity-0'
                )}
              />
            </Link>
          );
        })}
      </div>
    </nav>
  );
}
