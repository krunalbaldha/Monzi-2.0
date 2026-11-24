import React from 'react';
import { clsx } from 'clsx';

interface GlassCardProps extends React.HTMLAttributes<HTMLDivElement> {
  children: React.ReactNode;
  className?: string;
  variant?: 'default' | 'solid' | 'interactive';
}

export function GlassCard({
  children,
  className,
  variant = 'default',
  ...props
}: GlassCardProps) {
  return (
    <div
      className={clsx(
        'rounded-glass transition-all duration-300',
        variant === 'default' &&
          'bg-white/75 backdrop-blur-glass border border-white/80 shadow-glass-card hover:shadow-glass-hover',
        variant === 'solid' &&
          'bg-white border border-slate-200/80 shadow-sm',
        variant === 'interactive' &&
          'bg-white/75 backdrop-blur-glass border border-white/80 shadow-glass-card hover:shadow-glass-hover hover:-translate-y-0.5 cursor-pointer',
        className
      )}
      {...props}
    >
      {children}
    </div>
  );
}
