'use client';

import React, { useState } from 'react';
import Link from 'next/link';
import { ArrowUpRight, ArrowDownLeft, ArrowRight, ArrowLeftRight, Plus } from 'lucide-react';
import { GlassCard } from '@/components/ui/GlassCard';
import { Badge } from '@/components/ui/Badge';
import { Transaction } from '@/lib/types';
import { TransactionDetailModal } from '@/components/dashboard/TransactionDetailModal';
import { AddTransactionModal } from '@/components/dashboard/AddTransactionModal';

interface RecentTransactionsProps {
  transactions: Transaction[];
  onRefresh?: () => void;
}

export function RecentTransactions({ transactions, onRefresh }: RecentTransactionsProps) {
  const [selectedTx, setSelectedTx] = useState<Transaction | null>(null);
  const [isAddOpen, setIsAddOpen] = useState(false);

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
    <>
      <GlassCard className="p-4 sm:p-6">
        <div className="flex items-center justify-between mb-4">
          <div>
            <h3 className="text-sm sm:text-base font-bold text-slate-900 tracking-tight">Recent Transactions</h3>
            <p className="text-[11px] sm:text-xs text-slate-500 font-medium">Synced from bank SMS & manual entries</p>
          </div>
          {transactions && transactions.length > 0 && (
            <Link
              href="/transactions"
              className="flex items-center gap-1 text-xs font-semibold text-blue-600 hover:text-blue-700 hover:underline"
            >
              <span>View All</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </Link>
          )}
        </div>

        {!transactions || transactions.length === 0 ? (
          <div className="py-10 flex flex-col items-center justify-center text-center p-6 border border-dashed border-slate-200 rounded-2xl bg-slate-50/50">
            <div className="w-11 h-11 rounded-2xl bg-blue-50 text-blue-600 flex items-center justify-center mb-2.5">
              <ArrowLeftRight className="w-5 h-5" />
            </div>
            <p className="text-xs sm:text-sm font-bold text-slate-800">No Transactions Found</p>
            <p className="text-[11px] sm:text-xs text-slate-400 max-w-sm mt-0.5 mb-4">
              Your database currently has no transaction records. Add your first transaction or sync incoming bank SMS.
            </p>
            <button
              onClick={() => setIsAddOpen(true)}
              className="flex items-center gap-1.5 px-3.5 py-2 rounded-xl text-xs font-semibold bg-blue-600 text-white shadow-sm hover:bg-blue-700 transition-all active:scale-95"
            >
              <Plus className="w-3.5 h-3.5" />
              <span>Add Transaction</span>
            </button>
          </div>
        ) : (
          <div className="divide-y divide-slate-100">
            {transactions.map((tx) => {
              const isCredit = tx.direction === 'CREDIT';
              return (
                <div
                  key={tx.id}
                  onClick={() => setSelectedTx(tx)}
                  className="py-3 flex items-center justify-between hover:bg-slate-50/70 active:bg-slate-100/80 px-2 rounded-xl transition-all gap-2 cursor-pointer active:scale-[0.99]"
                >
                  <div className="flex items-center gap-2.5 sm:gap-3.5 min-w-0">
                    <div
                      className={`w-9 h-9 sm:w-10 sm:h-10 rounded-xl sm:rounded-2xl flex items-center justify-center shrink-0 ${
                        isCredit
                          ? 'bg-emerald-50 text-emerald-600'
                          : 'bg-rose-50 text-rose-600'
                      }`}
                    >
                      {isCredit ? (
                        <ArrowDownLeft className="w-4 h-4 sm:w-5 sm:h-5" />
                      ) : (
                        <ArrowUpRight className="w-4 h-4 sm:w-5 sm:h-5" />
                      )}
                    </div>
                    <div className="min-w-0">
                      <div className="flex items-center gap-1.5 flex-wrap">
                        <p className="text-xs sm:text-sm font-bold text-slate-900 tracking-tight truncate max-w-[140px] sm:max-w-none">
                          {tx.merchant || 'Unknown Merchant'}
                        </p>
                        <Badge type={tx.transaction_type} className="text-[10px] py-0 px-1.5" />
                      </div>
                      <p className="text-[10px] sm:text-xs text-slate-400 font-medium mt-0.5 truncate">
                        {tx.accounts?.bank_name || 'Bank'} {tx.accounts?.account_number_mask || ''} •{' '}
                        {formatDate(tx.transaction_date)}
                      </p>
                    </div>
                  </div>

                  <div className="text-right shrink-0">
                    <p
                      className={`text-xs sm:text-sm font-bold font-mono tracking-tight ${
                        isCredit ? 'text-emerald-600' : 'text-slate-900'
                      }`}
                    >
                      {isCredit ? '+' : '-'} {formatInr(tx.amount)}
                    </p>
                    {tx.parsed_available_balance !== undefined && (
                      <p className="text-[10px] text-slate-400 font-mono hidden sm:block">
                        Bal: {formatInr(tx.parsed_available_balance)}
                      </p>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </GlassCard>

      <TransactionDetailModal
        transaction={selectedTx}
        onClose={() => setSelectedTx(null)}
        onUpdate={onRefresh}
      />

      <AddTransactionModal
        isOpen={isAddOpen}
        onClose={() => setIsAddOpen(false)}
        onSuccess={() => onRefresh && onRefresh()}
      />
    </>
  );
}
