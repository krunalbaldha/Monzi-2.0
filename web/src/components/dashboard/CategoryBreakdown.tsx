'use client';

import React from 'react';
import { GlassCard } from '@/components/ui/GlassCard';
import { EmptyState } from '@/components/ui/EmptyState';
import { Skeleton } from '@/components/ui/Skeleton';
import { PieChart } from 'lucide-react';
import { getCategoryVisual } from '@/lib/categoryVisuals';

interface CategoryBreakdownProps {
  categories: Array<{ name: string; value: number; color: string }>;
  loading?: boolean;
}

export function CategoryBreakdown({ categories, loading = false }: CategoryBreakdownProps) {
  const sorted = [...(categories || [])].sort((a, b) => b.value - a.value).slice(0, 6);
  const total = sorted.reduce((sum, c) => sum + c.value, 0);

  const formatINR = (val: number) =>
    new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 0,
    }).format(val);

  return (
    <GlassCard className="p-4 sm:p-6 h-full flex flex-col">
      <div className="flex items-center justify-between mb-4 sm:mb-5">
        <div>
          <h3 className="text-sm sm:text-base font-bold text-slate-900 tracking-tight">
            Category Breakdown
          </h3>
          <p className="text-[11px] sm:text-xs text-slate-500 font-medium mt-0.5">
            Top spending categories
          </p>
        </div>
        {sorted.length > 0 && !loading && (
          <span className="text-[11px] font-semibold text-slate-600 bg-slate-100/90 px-2.5 py-1 rounded-full">
            {sorted.length} {sorted.length === 1 ? 'Category' : 'Categories'}
          </span>
        )}
      </div>

      {loading ? (
        <div className="space-y-4">
          {[82, 65, 50, 38, 25].map((w, i) => (
            <div key={i} className="space-y-2">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Skeleton className="w-2.5 h-2.5 rounded-full" />
                  <Skeleton className="h-3.5 w-24 rounded-full" />
                </div>
                <Skeleton className="h-3.5 w-16 rounded-full" />
              </div>
              <Skeleton className="h-2 w-full rounded-full" />
            </div>
          ))}
        </div>
      ) : sorted.length === 0 ? (
        <EmptyState
          icon={PieChart}
          title="No Categorized Expenses"
          description="Spending categories will appear once you have debit transactions."
          className="flex-1 !py-8 !shadow-none border border-dashed border-slate-200/80 bg-slate-50/40"
        />
      ) : (
        <div className="space-y-3.5 sm:space-y-4">
          {sorted.map((cat) => {
            const pct = total > 0 ? Math.round((cat.value / total) * 100) : 0;
            const visual = getCategoryVisual(cat.name);
            const IconComp = visual.icon;
            const color = cat.color || visual.defaultColor;
            return (
              <div key={cat.name} className="space-y-1.5">
                <div className="flex items-center justify-between text-xs gap-2">
                  <div className="flex items-center gap-2 min-w-0">
                    <span
                      className="w-6 h-6 rounded-lg flex items-center justify-center shrink-0"
                      style={{ backgroundColor: `${color}1A`, color }}
                    >
                      <IconComp className="w-3.5 h-3.5" />
                    </span>
                    <span className="font-semibold text-slate-700 truncate">{cat.name}</span>
                  </div>
                  <div className="flex items-center gap-1.5 shrink-0">
                    <span className="font-bold text-slate-900 font-mono">{formatINR(cat.value)}</span>
                    <span className="text-slate-400 text-[11px] font-medium">({pct}%)</span>
                  </div>
                </div>
                <div className="w-full h-2 rounded-full bg-slate-100 overflow-hidden">
                  <div
                    className="h-full rounded-full transition-all duration-700 ease-out"
                    style={{
                      width: `${Math.max(pct, 3)}%`,
                      backgroundColor: color,
                    }}
                  />
                </div>
              </div>
            );
          })}
        </div>
      )}
    </GlassCard>
  );
}
