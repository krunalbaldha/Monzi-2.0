import React from 'react';
import { clsx } from 'clsx';

type GlassCardVariant = 'default' | 'elevated' | 'flat' | 'colored';

interface GlassCardProps extends React.HTMLAttributes<HTMLDivElement> {
  children: React.ReactNode;
  className?: string;
  variant?: GlassCardVariant;
  /** Used with variant="colored" — a Tailwind color string for the left accent border e.g. "emerald", "rose", "blue" */
  color?: 'blue' | 'emerald' | 'rose' | 'amber' | 'violet' | 'cyan' | 'slate';
}

const colorMap: Record<string, string> = {
  blue:    'border-l-blue-500',
  emerald: 'border-l-emerald-500',
  rose:    'border-l-rose-500',
  amber:   'border-l-amber-500',
  violet:  'border-l-violet-500',
  cyan:    'border-l-cyan-500',
  slate:   'border-l-slate-400',
};

export function GlassCard({
  children,
  className,
  variant = 'default',
  color = 'blue',
  ...props
}: GlassCardProps) {
  return (
    <div
      className={clsx(
        'rounded-2xl transition-all duration-300',
        // default — standard glassmorphism
        variant === 'default' &&
          'bg-white/70 backdrop-blur-xl border border-white/40 shadow-[0_2px_24px_rgba(0,0,0,0.06)]',
        // elevated — higher opacity, deeper shadow
        variant === 'elevated' &&
          'bg-white/88 backdrop-blur-2xl border border-white/50 shadow-[0_8px_32px_rgba(0,0,0,0.10)]',
        // flat — no shadow, subtle border only
        variant === 'flat' &&
          'bg-white/60 backdrop-blur-lg border border-slate-200/60',
        // colored — glass + left accent border
        variant === 'colored' && [
          'bg-white/70 backdrop-blur-xl border border-white/40 shadow-[0_2px_24px_rgba(0,0,0,0.06)]',
          'border-l-4',
          colorMap[color] ?? colorMap.blue,
        ],
        className
      )}
      {...props}
    >
      {children}
    </div>
  );
}
