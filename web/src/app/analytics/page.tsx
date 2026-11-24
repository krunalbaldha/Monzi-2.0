'use client';

import React, { useState, useEffect } from 'react';
import { Header } from '@/components/layout/Header';
import { GlassCard } from '@/components/ui/GlassCard';
import { CashFlowChart } from '@/components/dashboard/CashFlowChart';
import { CategoryBreakdown } from '@/components/dashboard/CategoryBreakdown';
import { mockMonthlyCashFlow, mockCategoryBreakdown } from '@/lib/mockData';
import { TrendingUp, Target, Zap } from 'lucide-react';

export default function AnalyticsPage() {
  const [cashFlow, setCashFlow] = useState(mockMonthlyCashFlow);
  const [categories, setCategories] = useState(mockCategoryBreakdown);

  useEffect(() => {
    fetch('/api/stats')
      .then((res) => res.json())
      .then((data) => {
        if (data.cashFlow && data.cashFlow.length > 0) setCashFlow(data.cashFlow);
        if (data.categoryBreakdown && data.categoryBreakdown.length > 0) {
          setCategories(data.categoryBreakdown);
        }
      })
      .catch((err) => console.error(err));
  }, []);

  return (
    <div className="flex flex-col min-h-screen">
      <Header />

      <div className="p-4 sm:p-6 md:p-8 space-y-5 md:space-y-8 max-w-7xl mx-auto w-full">
        <div>
          <h2 className="text-lg sm:text-xl font-bold tracking-tight text-slate-900">Financial Analytics</h2>
          <p className="text-[11px] sm:text-xs text-slate-500 font-medium">
            Deep dive into your spending patterns, savings velocity, and cash flow
          </p>
        </div>

        {/* Insight Highlights */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 sm:gap-5">
          <GlassCard className="p-4 sm:p-5 flex items-center sm:items-start gap-3.5 sm:gap-4">
            <div className="w-10 h-10 rounded-2xl bg-emerald-50 text-emerald-600 flex items-center justify-center shrink-0">
              <TrendingUp className="w-5 h-5" />
            </div>
            <div>
              <p className="text-[10px] sm:text-xs font-semibold text-slate-400 uppercase tracking-wider">
                Savings Velocity
              </p>
              <p className="text-base sm:text-lg font-bold text-slate-900 font-mono mt-0.5">73.6%</p>
              <p className="text-[11px] sm:text-xs text-emerald-600 font-medium mt-0.5">
                +13.6% above your 60% goal
              </p>
            </div>
          </GlassCard>

          <GlassCard className="p-4 sm:p-5 flex items-center sm:items-start gap-3.5 sm:gap-4">
            <div className="w-10 h-10 rounded-2xl bg-amber-50 text-amber-600 flex items-center justify-center shrink-0">
              <Target className="w-5 h-5" />
            </div>
            <div>
              <p className="text-[10px] sm:text-xs font-semibold text-slate-400 uppercase tracking-wider">
                Top Spending Category
              </p>
              <p className="text-base sm:text-lg font-bold text-slate-900 font-mono mt-0.5">Food & Dining</p>
              <p className="text-[11px] sm:text-xs text-slate-500 font-medium mt-0.5">
                ₹8,450 spent this month
              </p>
            </div>
          </GlassCard>

          <GlassCard className="p-4 sm:p-5 flex items-center sm:items-start gap-3.5 sm:gap-4">
            <div className="w-10 h-10 rounded-2xl bg-blue-50 text-blue-600 flex items-center justify-center shrink-0">
              <Zap className="w-5 h-5" />
            </div>
            <div>
              <p className="text-[10px] sm:text-xs font-semibold text-slate-400 uppercase tracking-wider">
                Recurring Investments
              </p>
              <p className="text-base sm:text-lg font-bold text-slate-900 font-mono mt-0.5">₹25,000</p>
              <p className="text-[11px] sm:text-xs text-blue-600 font-medium mt-0.5">
                Zerodha SIP automated
              </p>
            </div>
          </GlassCard>
        </div>

        {/* Charts Grid */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-5 md:gap-6">
          <div className="lg:col-span-2">
            <CashFlowChart data={cashFlow} />
          </div>
          <div>
            <CategoryBreakdown categories={categories} />
          </div>
        </div>
      </div>
    </div>
  );
}
