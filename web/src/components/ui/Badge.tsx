import React from 'react';
import { clsx } from 'clsx';
import { TransactionType } from '@/lib/types';

interface BadgeProps {
  type: TransactionType | string;
  label?: string;
  dot?: boolean;
  size?: 'sm' | 'md';
  className?: string;
}

const badgeStyles: Record<string, { container: string; dot: string; label?: string }> = {
  INCOME: {
    container: 'bg-emerald-50/90 text-emerald-700 border-emerald-200/70',
    dot: 'bg-emerald-500',
  },
  CREDIT: {
    container: 'bg-emerald-50/90 text-emerald-700 border-emerald-200/70',
    dot: 'bg-emerald-500',
  },
  EXPENSE: {
    container: 'bg-rose-50/90 text-rose-700 border-rose-200/70',
    dot: 'bg-rose-500',
  },
  DEBIT: {
    container: 'bg-rose-50/90 text-rose-700 border-rose-200/70',
    dot: 'bg-rose-500',
  },
  TRANSFER: {
    container: 'bg-blue-50/90 text-blue-700 border-blue-200/70',
    dot: 'bg-blue-500',
  },
  INVESTMENT: {
    container: 'bg-cyan-50/90 text-cyan-700 border-cyan-200/70',
    dot: 'bg-cyan-500',
  },
  CC_BILL_PAYMENT: {
    container: 'bg-violet-50/90 text-violet-700 border-violet-200/70',
    dot: 'bg-violet-500',
    label: 'CC Bill',
  },
  REFUND: {
    container: 'bg-amber-50/90 text-amber-700 border-amber-200/70',
    dot: 'bg-amber-500',
  },
};

export function Badge({ type, label, dot = false, size = 'md', className }: BadgeProps) {
  const key = type?.toUpperCase?.() ?? '';
  const style = badgeStyles[key] ?? {
    container: 'bg-slate-100/90 text-slate-700 border-slate-200/80',
    dot: 'bg-slate-400',
  };

  const displayLabel =
    label ??
    style.label ??
    (type ? type.charAt(0).toUpperCase() + type.slice(1).toLowerCase().replace(/_/g, ' ') : '');

  return (
    <span
      className={clsx(
        'inline-flex items-center gap-1.5 rounded-full font-semibold border tracking-wide backdrop-blur-sm',
        size === 'sm' ? 'px-2 py-0.5 text-[10px]' : 'px-2.5 py-0.5 text-xs',
        style.container,
        className
      )}
    >
      {dot && <span className={clsx('w-1.5 h-1.5 rounded-full shrink-0', style.dot)} />}
      {displayLabel}
    </span>
  );
}
