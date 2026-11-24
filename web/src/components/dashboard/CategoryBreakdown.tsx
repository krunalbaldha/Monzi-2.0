'use client';

import React from 'react';
import { GlassCard } from '@/components/ui/GlassCard';
import { PieChart } from 'lucide-react';

interface CategoryBreakdownProps {
  categories: Array<{ name: string; value: number; color: string }>;
}

export function CategoryBreakdown({ categories }: CategoryBreakdownProps) {
  const total = (categories || []).reduce((sum, c) => sum + c.value, 0);

  return (
    <GlassCard className="p-4 sm:p-6">
      <div className="flex items-center justify-between mb-4 sm:mb-6">
        <div>
          <h3 className="text-sm sm:text-base font-bold text-slate-900 tracking-tight">Category Breakdown</h3>
          <p className="text-[11px] sm:text-xs text-slate-500 font-medium">Spending by category this month</p>
        </div>
        {categories && categories.length > 0 && (
          <span className="text-[10px] sm:text-xs font-semibold text-slate-500 bg-slate-100 px-2 py-0.5 sm:py-1 rounded-lg">
            {categories.length} Categories
          </span>
        )}
      </div>

      {!categories || categories.length === 0 ? (
        <div className="h-52 sm:h-64 flex flex-col items-center justify-center text-center p-6 border border-dashed border-slate-200 rounded-2xl bg-slate-50/50">
          <div className="w-10 h-10 rounded-2xl bg-indigo-50 text-indigo-600 flex items-center justify-center mb-2">
            <PieChart className="w-5 h-5" />
          </div>
          <p className="text-xs font-bold text-slate-700">No Categorized Expenses</p>
          <p className="text-[11px] text-slate-400 max-w-xs mt-0.5">
            When you make debit transactions or upload bank SMS, spending will be classified into categories here.
          </p>
        </div>
      ) : (
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
      )}
    </GlassCard>
  );
}
