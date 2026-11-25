'use client';

import React from 'react';
import { clsx } from 'clsx';

/** Shimmer base — shared across all skeleton variants */
function SkeletonBase({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={clsx(
        'rounded-xl shimmer',
        className
      )}
      {...props}
    />
  );
}

/** Single line of text skeleton */
export function SkeletonText({ className, lines = 1 }: { className?: string; lines?: number }) {
  return (
    <div className={clsx('flex flex-col gap-2', className)}>
      {Array.from({ length: lines }).map((_, i) => (
        <SkeletonBase
          key={i}
          className={clsx('h-3.5', i === lines - 1 && lines > 1 ? 'w-3/5' : 'w-full')}
        />
      ))}
    </div>
  );
}

/** Card-shaped skeleton — mimics a GlassCard */
export function SkeletonCard({ className }: { className?: string }) {
  return (
    <div
      className={clsx(
        'rounded-2xl bg-white/70 border border-white/40 shadow-[0_2px_24px_rgba(0,0,0,0.06)] p-5',
        className
      )}
    >
      {/* Header row */}
      <div className="flex items-center gap-3 mb-4">
        <SkeletonBase className="w-10 h-10 rounded-xl" />
        <div className="flex-1 space-y-2">
          <SkeletonBase className="h-3.5 w-2/3" />
          <SkeletonBase className="h-3 w-1/3" />
        </div>
      </div>
      {/* Body */}
      <SkeletonBase className="h-8 w-1/2 mb-3" />
      <SkeletonBase className="h-3 w-full" />
      <SkeletonBase className="h-3 w-4/5 mt-2" />
    </div>
  );
}

/** List skeleton — mimics a row-based list */
export function SkeletonList({ rows = 4, className }: { rows?: number; className?: string }) {
  return (
    <div className={clsx('flex flex-col divide-y divide-slate-100/60', className)}>
      {Array.from({ length: rows }).map((_, i) => (
        <div key={i} className="flex items-center gap-3 py-3.5">
          <SkeletonBase className="w-9 h-9 rounded-xl shrink-0" />
          <div className="flex-1 space-y-2">
            <SkeletonBase className="h-3.5 w-1/2" />
            <SkeletonBase className="h-3 w-1/3" />
          </div>
          <SkeletonBase className="h-4 w-16 rounded-lg shrink-0" />
        </div>
      ))}
    </div>
  );
}

/** Generic skeleton — just a shimmer box */
export const Skeleton = SkeletonBase;
