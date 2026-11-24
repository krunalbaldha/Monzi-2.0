'use client';

import React from 'react';
import { GlassCard } from '@/components/ui/GlassCard';

interface CategoryBreakdownProps {
  categories: Array<{ name: string; value: number; color: string }>;
}

export function CategoryBreakdown({ categories }: CategoryBreakdownProps) {
  const total = categories.reduce((sum, c) => sum + c.value, 0);

  return (
    <GlassCard className="p-6">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h3 className="text-base font-bold text-slate-900 tracking-tight">Category Breakdown</h3>
          <p className="text-xs text-slate-500 font-medium">Spending by category this month</p>
        </div>
        <span className="text-xs font-semibold text-slate-500 bg-slate-100 px-2 py-1 rounded-lg">
          {categories.length} Categories
        </span>
      </div>

      <div className="space-y-4">
        {categories.map((cat) => {
          const pct = total > 0 ? Math.round((cat.value / total) * 100) : 0;
          return (
            <div key={cat.name} className="space-y-1.5">
              <div className="flex items-center justify-between text-xs font-semibold">
                <div className="flex items-center gap-2">
                  <span
                    className="w-2.5 h-2.5 rounded-full"
                    style={{ backgroundColor: cat.color }}
                  ></span>
                  <span className="text-slate-700">{cat.name}</span>
                </div>
                <div className="flex items-center gap-2 font-mono">
                  <span className="text-slate-900 font-bold">
                    ₹{cat.value.toLocaleString('en-IN')}
                  </span>
                  <span className="text-slate-400 font-normal">({pct}%)</span>
                </div>
              </div>
              {/* Progress bar */}
              <div className="w-full h-2 rounded-full bg-slate-100 overflow-hidden">
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
