'use client';

import React from 'react';
import Link from 'next/link';
import { ArrowUpRight, ArrowDownLeft, ArrowRight } from 'lucide-react';
import { GlassCard } from '@/components/ui/GlassCard';
import { Badge } from '@/components/ui/Badge';
import { Transaction } from '@/lib/types';

interface RecentTransactionsProps {
  transactions: Transaction[];
}

export function RecentTransactions({ transactions }: RecentTransactionsProps) {
  const formatInr = (val: number) => {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 0,
    }).format(val);
  };

  const formatDate = (dateStr: string) => {
    const d = new Date(dateStr);
    return new Intl.DateTimeFormat('en-IN', {
      day: 'numeric',
      month: 'short',
      hour: '2-digit',
      minute: '2-digit',
    }).format(d);
  };

  return (
    <GlassCard className="p-6">
      <div className="flex items-center justify-between mb-5">
        <div>
          <h3 className="text-base font-bold text-slate-900 tracking-tight">Recent Transactions</h3>
          <p className="text-xs text-slate-500 font-medium">Synced automatically from bank SMS</p>
        </div>
        <Link
          href="/transactions"
          className="flex items-center gap-1 text-xs font-semibold text-blue-600 hover:text-blue-700 hover:underline"
        >
          <span>View All</span>
          <ArrowRight className="w-3.5 h-3.5" />
        </Link>
      </div>

      <div className="divide-y divide-slate-100">
        {transactions.map((tx) => {
          const isCredit = tx.direction === 'CREDIT';
          return (
            <div
              key={tx.id}
              className="py-3.5 flex items-center justify-between hover:bg-slate-50/50 px-2 rounded-xl transition-all"
            >
              <div className="flex items-center gap-3.5">
                <div
                  className={`w-10 h-10 rounded-2xl flex items-center justify-center shrink-0 ${
                    isCredit
                      ? 'bg-emerald-50 text-emerald-600'
                      : 'bg-rose-50 text-rose-600'
                  }`}
                >
                  {isCredit ? (
                    <ArrowDownLeft className="w-5 h-5" />
                  ) : (
                    <ArrowUpRight className="w-5 h-5" />
                  )}
                </div>
                <div>
                  <div className="flex items-center gap-2">
                    <p className="text-sm font-bold text-slate-900 tracking-tight">
                      {tx.merchant || 'Unknown Merchant'}
                    </p>
                    <Badge type={tx.transaction_type} />
                  </div>
                  <p className="text-xs text-slate-400 font-medium mt-0.5">
                    {tx.accounts?.bank_name || 'Bank'} {tx.accounts?.account_number_mask || ''} •{' '}
                    {formatDate(tx.transaction_date)} • {tx.payment_mode || 'UPI'}
                  </p>
                </div>
              </div>

              <div className="text-right">
                <p
                  className={`text-sm font-bold font-mono tracking-tight ${
                    isCredit ? 'text-emerald-600' : 'text-slate-900'
                  }`}
                >
                  {isCredit ? '+' : '-'} {formatInr(tx.amount)}
                </p>
                {tx.parsed_available_balance !== undefined && (
                  <p className="text-[11px] text-slate-400 font-mono">
                    Bal: {formatInr(tx.parsed_available_balance)}
                  </p>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </GlassCard>
  );
}
