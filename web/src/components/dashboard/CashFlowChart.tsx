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
import { EmptyState } from '@/components/ui/EmptyState';
import { Skeleton } from '@/components/ui/Skeleton';
import { BarChart3 } from 'lucide-react';

interface CashFlowProps {
  data: Array<{ month: string; income: number; expense: number }>;
  loading?: boolean;
}

function formatINRShort(val: number): string {
  if (val >= 100000) return `₹${(val / 100000).toFixed(1)}L`;
  if (val >= 1000) return `₹${Math.round(val / 1000)}K`;
  return `₹${val}`;
}

function formatINRFull(val: number): string {
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 0,
  }).format(val);
}

interface CustomTooltipProps {
  active?: boolean;
  payload?: Array<{
    dataKey?: string | number;
    value?: number | string;
  }>;
  label?: string | number;
}

function CustomTooltip({ active, payload, label }: CustomTooltipProps) {
  if (!active || !payload || payload.length === 0) return null;
  const income = Number(payload.find((p) => p.dataKey === 'income')?.value ?? 0);
  const expense = Number(payload.find((p) => p.dataKey === 'expense')?.value ?? 0);
  const net = income - expense;

  return (
    <div className="bg-white/90 backdrop-blur-xl border border-white/60 shadow-[0_8px_32px_rgba(0,0,0,0.12)] rounded-2xl px-4 py-3 text-xs">
      <p className="font-bold text-slate-900 mb-2">{label}</p>
      <div className="space-y-1">
        <div className="flex items-center justify-between gap-6">
          <span className="flex items-center gap-1.5 text-slate-500 font-medium">
            <span className="w-2 h-2 rounded-full bg-emerald-500 inline-block" />
            Income
          </span>
          <span className="font-bold text-emerald-600 font-mono">{formatINRFull(income)}</span>
        </div>
        <div className="flex items-center justify-between gap-6">
          <span className="flex items-center gap-1.5 text-slate-500 font-medium">
            <span className="w-2 h-2 rounded-full bg-rose-500 inline-block" />
            Expense
          </span>
          <span className="font-bold text-rose-500 font-mono">{formatINRFull(expense)}</span>
        </div>
        <div className="pt-1.5 mt-1 border-t border-slate-100 flex items-center justify-between gap-6">
          <span className="text-slate-600 font-semibold">Net</span>
          <span className={`font-bold font-mono ${net >= 0 ? 'text-blue-600' : 'text-rose-600'}`}>
            {net >= 0 ? '+' : ''}
            {formatINRFull(net)}
          </span>
        </div>
      </div>
    </div>
  );
}

export function CashFlowChart({ data, loading = false }: CashFlowProps) {
  return (
    <GlassCard className="p-4 sm:p-6">
      <div className="flex items-center justify-between mb-4 sm:mb-5">
        <div>
          <h3 className="text-sm sm:text-base font-bold text-slate-900 tracking-tight">
            Income vs Expenses
          </h3>
          <p className="text-[11px] sm:text-xs text-slate-500 font-medium mt-0.5">
            Monthly cash flow trends (INR)
          </p>
        </div>
        {data && data.length > 0 && !loading && (
          <div className="flex items-center gap-3 sm:gap-4 text-[11px] sm:text-xs font-semibold">
            <div className="flex items-center gap-1.5">
              <span className="w-2.5 h-2.5 rounded-full bg-emerald-500" />
              <span className="text-slate-600">Income</span>
            </div>
            <div className="flex items-center gap-1.5">
              <span className="w-2.5 h-2.5 rounded-full bg-rose-500" />
              <span className="text-slate-600">Expense</span>
            </div>
          </div>
        )}
      </div>

      {loading ? (
        <div className="h-[240px] sm:h-[300px] flex items-end gap-4 px-4 pb-4">
          {[55, 82, 48, 92, 68, 76].map((h, i) => (
            <div key={i} className="flex-1 flex items-end gap-1.5 h-full pt-6">
              <Skeleton className="flex-1 rounded-t-lg" style={{ height: `${h}%` } as React.CSSProperties} />
              <Skeleton className="flex-1 rounded-t-lg" style={{ height: `${Math.round(h * 0.65)}%` } as React.CSSProperties} />
            </div>
          ))}
        </div>
      ) : !data || data.length === 0 ? (
        <EmptyState
          icon={BarChart3}
          title="No Cash Flow History Yet"
          description="Incoming bank SMS and manual transactions will automatically populate your monthly income and expense trends."
          className="h-[240px] sm:h-[300px] !py-6 !shadow-none border border-dashed border-slate-200/80 bg-slate-50/40"
        />
      ) : (
        <div className="h-[240px] sm:h-[300px] w-full">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={data} margin={{ top: 8, right: 6, left: -12, bottom: 0 }} barGap={4}>
              <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#E2E8F0" opacity={0.65} />
              <XAxis
                dataKey="month"
                tickLine={false}
                axisLine={false}
                tick={{ fill: '#64748B', fontSize: 11, fontWeight: 500 }}
              />
              <YAxis
                tickLine={false}
                axisLine={false}
                tick={{ fill: '#64748B', fontSize: 11 }}
                tickFormatter={formatINRShort}
                width={50}
              />
              <Tooltip content={<CustomTooltip />} cursor={{ fill: 'rgba(226,232,240,0.35)', radius: 8 }} />
              <Bar dataKey="income" fill="#10B981" radius={[6, 6, 0, 0]} maxBarSize={28} />
              <Bar dataKey="expense" fill="#EF4444" radius={[6, 6, 0, 0]} maxBarSize={28} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}
    </GlassCard>
  );
}
