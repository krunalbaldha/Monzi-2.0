'use client';

import React, { useState } from 'react';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { LayoutDashboard, ArrowLeftRight, CreditCard, PieChart, Plus } from 'lucide-react';
import { clsx } from 'clsx';
import { AddTransactionModal } from '@/components/dashboard/AddTransactionModal';

interface BottomNavProps {
  onAddTransaction?: () => void;
}

const leftNavItems = [
  { name: 'Overview', href: '/', icon: LayoutDashboard },
  { name: 'Transactions', href: '/transactions', icon: ArrowLeftRight },
];

const rightNavItems = [
  { name: 'Accounts', href: '/accounts', icon: CreditCard },
  { name: 'Analytics', href: '/analytics', icon: PieChart },
];

export function BottomNav({ onAddTransaction }: BottomNavProps) {
  const pathname = usePathname();
  const router = useRouter();
  const [isFallbackModalOpen, setIsFallbackModalOpen] = useState(false);

  const handleFabClick = () => {
    if (onAddTransaction) {
      onAddTransaction();
    } else {
      setIsFallbackModalOpen(true);
    }
  };

  const NavItem = ({
    item,
  }: {
    item: { name: string; href: string; icon: React.ComponentType<{ className?: string }> };
  }) => {
    const Icon = item.icon;
    const isActive = pathname === item.href;
    return (
      <Link
        href={item.href}
        className={clsx(
          'flex flex-col items-center justify-center flex-1 py-2 transition-all duration-200 active:scale-90 relative min-h-[44px]',
          isActive ? 'text-blue-600' : 'text-slate-400 hover:text-slate-600'
        )}
      >
        <div className="relative flex items-center justify-center">
          <Icon
            className={clsx(
              'w-5 h-5 stroke-[2] transition-transform duration-200',
              isActive ? 'text-blue-600 scale-110' : 'text-slate-400'
            )}
          />
        </div>
        <span
          className={clsx(
            'text-[11px] mt-1 leading-none transition-colors duration-200',
            isActive ? 'text-blue-600 font-semibold' : 'text-slate-400 font-medium'
          )}
        >
          {item.name}
        </span>
        {/* Animated active underline/dot indicator */}
        <span
          className={clsx(
            'absolute bottom-1 left-1/2 -translate-x-1/2 h-1 rounded-full bg-blue-600 transition-all duration-300',
            isActive ? 'w-4 opacity-100 shadow-sm shadow-blue-500/50' : 'w-0 opacity-0'
          )}
        />
      </Link>
    );
  };

  return (
    <>
      <nav
        className="md:hidden fixed bottom-0 left-0 right-0 z-40 bg-white/85 backdrop-blur-2xl border-t border-white/60 shadow-[0_-8px_30px_rgba(0,0,0,0.06)]"
        style={{ paddingBottom: 'env(safe-area-inset-bottom)' }}
      >
        {/* 72px bar + safe-area-inset-bottom — 5 centered items */}
        <div className="flex items-center justify-around px-2 h-[72px] max-w-lg mx-auto">
          {leftNavItems.map((item) => (
            <NavItem key={item.name} item={item} />
          ))}

          {/* Center 56px Floating Blue "+" FAB */}
          <div className="flex flex-col items-center justify-center flex-1">
            <button
              type="button"
              onClick={handleFabClick}
              className="w-14 h-14 rounded-full bg-gradient-to-br from-blue-500 via-blue-600 to-indigo-600 flex items-center justify-center shadow-lg shadow-blue-500/40 active:scale-90 transition-all duration-150 hover:shadow-xl hover:shadow-blue-500/50 -mt-5 border-4 border-white/95"
              aria-label="Add Transaction"
            >
              <Plus className="w-6 h-6 text-white stroke-[2.5]" />
            </button>
          </div>

          {rightNavItems.map((item) => (
            <NavItem key={item.name} item={item} />
          ))}
        </div>
      </nav>

      {!onAddTransaction && (
        <AddTransactionModal
          isOpen={isFallbackModalOpen}
          onClose={() => setIsFallbackModalOpen(false)}
          onSuccess={() => {
            window.dispatchEvent(new CustomEvent('monzi:refresh'));
            router.refresh();
          }}
        />
      )}
    </>
  );
}
