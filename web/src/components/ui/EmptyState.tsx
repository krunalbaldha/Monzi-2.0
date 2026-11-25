'use client';

import React from 'react';
import { clsx } from 'clsx';
import type { LucideIcon } from 'lucide-react';

interface EmptyStateProps {
  /** Lucide icon component */
  icon?: LucideIcon;
  title: string;
  description?: string;
  /** Primary action button label */
  actionLabel?: string;
  onAction?: () => void;
  /** Secondary text link */
  secondaryLabel?: string;
  onSecondary?: () => void;
  className?: string;
}

export function EmptyState({
  icon: Icon,
  title,
  description,
  actionLabel,
  onAction,
  secondaryLabel,
  onSecondary,
  className,
}: EmptyStateProps) {
  return (
    <div
      className={clsx(
        'flex flex-col items-center justify-center text-center px-6 py-14 rounded-2xl bg-white/70 backdrop-blur-xl border border-white/40 shadow-[0_2px_24px_rgba(0,0,0,0.06)]',
        className
      )}
    >
      {Icon && (
        <div className="w-14 h-14 rounded-2xl bg-blue-50 border border-blue-100/60 flex items-center justify-center mb-4 shadow-sm">
          <Icon className="w-7 h-7 text-blue-500" />
        </div>
      )}

      <h3 className="text-[15px] font-bold text-slate-900 leading-snug mb-1.5">{title}</h3>

      {description && (
        <p className="text-[13px] text-slate-500 leading-relaxed max-w-xs">{description}</p>
      )}

      {actionLabel && onAction && (
        <button
          onClick={onAction}
          className="mt-5 btn-primary text-sm"
        >
          {actionLabel}
        </button>
      )}

      {secondaryLabel && onSecondary && (
        <button
          onClick={onSecondary}
          className="mt-3 text-[13px] font-semibold text-blue-600 hover:text-blue-700 transition-colors"
        >
          {secondaryLabel}
        </button>
      )}
    </div>
  );
}
