'use client';

import React, { useState, useEffect } from 'react';
import { Header } from '@/components/layout/Header';
import { GlassCard } from '@/components/ui/GlassCard';
import { CashFlowChart } from '@/components/dashboard/CashFlowChart';
import { CategoryBreakdown } from '@/components/dashboard/CategoryBreakdown';
import { mockMonthlyCashFlow, mockCategoryBreakdown } from '@/lib/mockData';
import { TrendingUp, TrendingDown, Target, Zap } from 'lucide-react';

export default function AnalyticsPage() {
  const [cashFlow] = useState(mockMonthlyCashFlow);
  const [categories] = useState(mockCategoryBreakdown);

  return (
    <div className="flex flex-col min-h-screen">
      <Header />

      <div className="p-8 space-y-8 max-w-7xl mx-auto w-full">
        <div>
          <h2 className="text-xl font-bold tracking-tight text-slate-900">Financial Analytics</h2>
          <p className="text-xs text-slate-500 font-medium">
            Deep dive into your spending patterns, savings velocity, and cash flow
          </p>
        </div>

        {/* Insight Highlights */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
          <GlassCard className="p-5 flex items-start gap-4">
            <div className="w-10 h-10 rounded-2xl bg-emerald-50 text-emerald-600 flex items-center justify-center shrink-0">
              <TrendingUp className="w-5 h-5" />
            </div>
            <div>
              <p className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
                Savings Velocity
              </p>
              <p className="text-lg font-bold text-slate-900 font-mono mt-0.5">73.6%</p>
              <p className="text-xs text-emerald-600 font-medium mt-1">
                +13.6% above your 60% goal
              </p>
            </div>
          </GlassCard>

          <GlassCard className="p-5 flex items-start gap-4">
            <div className="w-10 h-10 rounded-2xl bg-amber-50 text-amber-600 flex items-center justify-center shrink-0">
              <Target className="w-5 h-5" />
            </div>
            <div>
              <p className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
                Top Spending Category
              </p>
              <p className="text-lg font-bold text-slate-900 font-mono mt-0.5">Food & Dining</p>
              <p className="text-xs text-slate-500 font-medium mt-1">
                ₹8,450 spent this month
              </p>
            </div>
          </GlassCard>

          <GlassCard className="p-5 flex items-start gap-4">
            <div className="w-10 h-10 rounded-2xl bg-blue-50 text-blue-600 flex items-center justify-center shrink-0">
              <Zap className="w-5 h-5" />
            </div>
            <div>
              <p className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
                Recurring Investments
              </p>
              <p className="text-lg font-bold text-slate-900 font-mono mt-0.5">₹25,000</p>
              <p className="text-xs text-blue-600 font-medium mt-1">
                Zerodha SIP automated
              </p>
            </div>
          </GlassCard>
        </div>

        {/* Charts Grid */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
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
