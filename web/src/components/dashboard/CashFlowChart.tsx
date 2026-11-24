'use client';

import React from 'react';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from 'recharts';
import { GlassCard } from '@/components/ui/GlassCard';

interface CashFlowProps {
  data: Array<{ month: string; income: number; expense: number }>;
}

export function CashFlowChart({ data }: CashFlowProps) {
  const formatYAxis = (val: number) => `₹${val / 1000}k`;

  return (
    <GlassCard className="p-4 sm:p-6">
      <div className="flex items-center justify-between mb-4 sm:mb-6">
        <div>
          <h3 className="text-sm sm:text-base font-bold text-slate-900 tracking-tight">Income vs Expenses</h3>
          <p className="text-[11px] sm:text-xs text-slate-500 font-medium">Monthly cash flow trends (INR)</p>
        </div>
        <div className="flex items-center gap-3 sm:gap-4 text-[11px] sm:text-xs font-semibold">
          <div className="flex items-center gap-1.5">
            <span className="w-2 h-2 sm:w-2.5 sm:h-2.5 rounded-full bg-emerald-500"></span>
            <span className="text-slate-600">Income</span>
          </div>
          <div className="flex items-center gap-1.5">
            <span className="w-2 h-2 sm:w-2.5 sm:h-2.5 rounded-full bg-rose-500"></span>
            <span className="text-slate-600">Expense</span>
          </div>
        </div>
      </div>

      <div className="h-52 sm:h-64 w-full">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={data} margin={{ top: 5, right: 5, left: -25, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#E2E8F0" opacity={0.6} />
            <XAxis dataKey="month" tickLine={false} axisLine={false} tick={{ fill: '#64748B', fontSize: 11 }} />
            <YAxis tickLine={false} axisLine={false} tick={{ fill: '#64748B', fontSize: 11 }} tickFormatter={formatYAxis} />
            <Tooltip
              formatter={(value: any) => [`₹${Number(value).toLocaleString('en-IN')}`, '']}
              contentStyle={{
                backgroundColor: 'rgba(255, 255, 255, 0.95)',
                backdropFilter: 'blur(16px)',
                borderRadius: '12px',
                border: '1px solid rgba(255, 255, 255, 0.8)',
                boxShadow: '0 10px 25px -5px rgba(0, 0, 0, 0.05)',
                fontSize: '11px',
                fontWeight: 600,
              }}
            />
            <Bar dataKey="income" fill="#16A34A" radius={[5, 5, 0, 0]} maxBarSize={28} />
            <Bar dataKey="expense" fill="#E85D75" radius={[5, 5, 0, 0]} maxBarSize={28} />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </GlassCard>
  );
}
