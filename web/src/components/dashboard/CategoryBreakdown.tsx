'use client';

import React from 'react';
import { GlassCard } from '@/components/ui/GlassCard';

interface CategoryBreakdownProps {
  categories: Array<{ name: string; value: number; color: string }>;
}

export function CategoryBreakdown({ categories }: CategoryBreakdownProps) {
  const total = categories.reduce((sum, c) => sum + c.value, 0);

  return (
    <GlassCard className="p-4 sm:p-6">
      <div className="flex items-center justify-between mb-4 sm:mb-6">
        <div>
          <h3 className="text-sm sm:text-base font-bold text-slate-900 tracking-tight">Category Breakdown</h3>
          <p className="text-[11px] sm:text-xs text-slate-500 font-medium">Spending by category this month</p>
        </div>
        <span className="text-[10px] sm:text-xs font-semibold text-slate-500 bg-slate-100 px-2 py-0.5 sm:py-1 rounded-lg">
          {categories.length} Categories
        </span>
      </div>

      <div className="space-y-3 sm:space-y-4">
        {categories.map((cat) => {
          const pct = total > 0 ? Math.round((cat.value / total) * 100) : 0;
          return (
            <div key={cat.name} className="space-y-1">
              <div className="flex items-center justify-between text-xs font-semibold">
                <div className="flex items-center gap-1.5 sm:gap-2 truncate max-w-[170px] sm:max-w-none">
                  <span
                    className="w-2 h-2 sm:w-2.5 sm:h-2.5 rounded-full shrink-0"
                    style={{ backgroundColor: cat.color }}
                  ></span>
                  <span className="text-slate-700 truncate">{cat.name}</span>
                </div>
                <div className="flex items-center gap-1.5 font-mono shrink-0">
                  <span className="text-slate-900 font-bold">
                    ₹{cat.value.toLocaleString('en-IN')}
                  </span>
                  <span className="text-slate-400 font-normal text-[10px] sm:text-xs">({pct}%)</span>
                </div>
              </div>
              {/* Progress bar */}
              <div className="w-full h-1.5 sm:h-2 rounded-full bg-slate-100 overflow-hidden">
                <div
                  className="h-full rounded-full transition-all duration-500"
                  style={{
                    width: `${pct}%`,
                    backgroundColor: cat.color,
                  }}
                ></div>
              </div>
            </div>
          );
        })}
      </div>
    </GlassCard>
  );
}
