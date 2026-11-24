import React from 'react';
import { clsx } from 'clsx';
import { TransactionType } from '@/lib/types';

interface BadgeProps {
  type: TransactionType | string;
  className?: string;
}

export function Badge({ type, className }: BadgeProps) {
  const getBadgeStyle = () => {
    switch (type) {
      case 'INCOME':
        return 'bg-emerald-50 text-emerald-700 border-emerald-200/60';
      case 'EXPENSE':
        return 'bg-rose-50 text-rose-700 border-rose-200/60';
      case 'TRANSFER':
        return 'bg-blue-50 text-blue-700 border-blue-200/60';
      case 'INVESTMENT':
        return 'bg-cyan-50 text-cyan-700 border-cyan-200/60';
      case 'CC_BILL_PAYMENT':
        return 'bg-purple-50 text-purple-700 border-purple-200/60';
      case 'REFUND':
        return 'bg-amber-50 text-amber-700 border-amber-200/60';
      default:
        return 'bg-slate-100 text-slate-700 border-slate-200';
    }
  };

  const getLabel = () => {
    switch (type) {
      case 'CC_BILL_PAYMENT':
        return 'CC Bill';
      default:
        return type.charAt(0) + type.slice(1).toLowerCase();
    }
  };

  return (
    <span
      className={clsx(
        'inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium border tracking-wide',
        getBadgeStyle(),
        className
      )}
    >
      {getLabel()}
    </span>
  );
}
