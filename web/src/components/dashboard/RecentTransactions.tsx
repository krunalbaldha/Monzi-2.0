'use client';

import React, { useState, useMemo } from 'react';
import Link from 'next/link';
import { ArrowUpRight, ArrowDownLeft, ArrowRight, ArrowLeftRight } from 'lucide-react';
import { GlassCard } from '@/components/ui/GlassCard';
import { Badge } from '@/components/ui/Badge';
import { EmptyState } from '@/components/ui/EmptyState';
import { SkeletonList } from '@/components/ui/Skeleton';
import { Transaction } from '@/lib/types';
import { TransactionDetailModal } from '@/components/dashboard/TransactionDetailModal';
import { AddTransactionModal } from '@/components/dashboard/AddTransactionModal';
import { getCategoryVisual } from '@/lib/categoryVisuals';

interface RecentTransactionsProps {
  transactions: Transaction[];
  loading?: boolean;
  onRefresh?: () => void;
}

function getDateGroupLabel(dateStr: string): string {
  const d = new Date(dateStr);
  const now = new Date();

  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  const startOfYesterday = startOfToday - 86400000;
  const txDayStart = new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();

  if (txDayStart === startOfToday) return 'Today';
  if (txDayStart === startOfYesterday) return 'Yesterday';

  const sameYear = d.getFullYear() === now.getFullYear();
  return new Intl.DateTimeFormat('en-IN', {
    day: 'numeric',
    month: 'short',
    ...(sameYear ? {} : { year: 'numeric' }),
  }).format(d);
}

function formatTime(dateStr: string): string {
  const d = new Date(dateStr);
  return new Intl.DateTimeFormat('en-IN', {
    hour: '2-digit',
    minute: '2-digit',
  }).format(d);
}

function formatInr(val: number): string {
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 0,
  }).format(val);
}

export function RecentTransactions({
  transactions,
  loading = false,
  onRefresh,
}: RecentTransactionsProps) {
  const [selectedTx, setSelectedTx] = useState<Transaction | null>(null);
  const [isAddOpen, setIsAddOpen] = useState(false);

  const grouped = useMemo(() => {
    const groups: Array<{ label: string; items: Transaction[] }> = [];
    const indexMap = new Map<string, number>();

    for (const tx of transactions || []) {
      const label = getDateGroupLabel(tx.transaction_date);
      const existingIdx = indexMap.get(label);
      if (existingIdx !== undefined) {
        groups[existingIdx].items.push(tx);
      } else {
        indexMap.set(label, groups.length);
        groups.push({ label, items: [tx] });
      }
    }
    return groups;
  }, [transactions]);

  return (
    <>
      <GlassCard className="p-4 sm:p-6 h-full flex flex-col">
        <div className="flex items-center justify-between mb-4">
          <div>
            <h3 className="text-sm sm:text-base font-bold text-slate-900 tracking-tight">
              Recent Transactions
            </h3>
            <p className="text-[11px] sm:text-xs text-slate-500 font-medium mt-0.5">
              Synced from bank SMS & manual entries
            </p>
          </div>
          {transactions && transactions.length > 0 && !loading && (
            <Link
              href="/transactions"
              className="inline-flex items-center gap-1 min-h-[44px] px-2 text-xs font-semibold text-blue-600 hover:text-blue-700 transition-colors"
            >
              <span>View All</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </Link>
          )}
        </div>

        {loading ? (
          <SkeletonList rows={5} />
        ) : !transactions || transactions.length === 0 ? (
          <EmptyState
            icon={ArrowLeftRight}
            title="No Transactions Found"
            description="Your database currently has no transaction records. Add your first transaction or sync incoming bank SMS."
            actionLabel="Add Transaction"
            onAction={() => setIsAddOpen(true)}
            className="flex-1 !py-10 !shadow-none border border-dashed border-slate-200/80 bg-slate-50/40"
          />
        ) : (
          <div className="space-y-4">
            {grouped.map((group) => (
              <div key={group.label}>
                <div className="px-2 pb-1.5 flex items-center justify-between">
                  <span className="text-[11px] font-bold uppercase tracking-wider text-slate-400">
                    {group.label}
                  </span>
                  <span className="text-[10px] font-medium text-slate-400">
                    {group.items.length} {group.items.length === 1 ? 'txn' : 'txns'}
                  </span>
                </div>
                <div className="divide-y divide-slate-100/80">
                  {group.items.map((tx) => {
                    const isCredit = tx.direction === 'CREDIT';
                    const isTransfer = tx.transaction_type === 'TRANSFER' || tx.transaction_type === 'CC_BILL_PAYMENT';
                    const visual = getCategoryVisual(
                      tx.categories?.name,
                      tx.transaction_type,
                      tx.direction,
                      tx.merchant
                    );
                    const CatIcon = visual.icon;
                    const accentColor = tx.categories?.color || visual.defaultColor;

                    return (
                      <button
                        type="button"
                        key={tx.id}
                        onClick={() => setSelectedTx(tx)}
                        className="w-full min-h-[72px] py-3 px-2.5 flex items-center justify-between gap-3 rounded-xl hover:bg-slate-50/80 active:bg-slate-100/80 transition-all text-left cursor-pointer"
                      >
                        <div className="flex items-center gap-3 min-w-0">
                          <div className="relative shrink-0">
                            <div
                              style={{
                                backgroundColor: `${accentColor}16`,
                                color: accentColor,
                                borderColor: `${accentColor}30`,
                              }}
                              className="w-10 h-10 rounded-xl border flex items-center justify-center"
                            >
                              <CatIcon className="w-4 h-4" />
                            </div>
                            <span
                              className={`absolute -bottom-1 -right-1 w-4 h-4 rounded-full border-2 border-white flex items-center justify-center ${
                                isTransfer ? 'bg-violet-600' : isCredit ? 'bg-emerald-500' : 'bg-rose-500'
                              } text-white`}
                            >
                              {isCredit ? (
                                <ArrowDownLeft className="w-2.5 h-2.5" />
                              ) : (
                                <ArrowUpRight className="w-2.5 h-2.5" />
                              )}
                            </span>
                          </div>

                          <div className="min-w-0">
                            <div className="flex items-center gap-1.5 flex-wrap">
                              <p className="text-sm font-bold text-slate-900 tracking-tight truncate max-w-[155px] sm:max-w-[240px]">
                                {tx.merchant || 'Unknown Merchant'}
                              </p>
                              <Badge
                                type={tx.transaction_type}
                                className="text-[10px] py-0 px-2"
                              />
                            </div>
                            <p className="text-xs text-slate-500 font-medium mt-0.5 truncate">
                              {formatTime(tx.transaction_date)}
                              {tx.payment_mode ? ` • ${tx.payment_mode}` : ''}
                              {tx.categories?.name ? ` • ${tx.categories.name}` : ''}
                              {tx.accounts?.bank_name
                                ? ` • ${tx.accounts.bank_name}`
                                : ''}
                            </p>
                          </div>
                        </div>

                        <div className="text-right shrink-0">
                          <p
                            className={`text-sm font-bold font-mono tracking-tight ${
                              isCredit ? 'text-emerald-600' : 'text-rose-600'
                            }`}
                          >
                            {isCredit ? '+' : '-'}
                            {formatInr(Number(tx.amount))}
                          </p>
                          {tx.parsed_available_balance !== undefined &&
                            tx.parsed_available_balance !== null && (
                              <p className="text-[10px] text-slate-400 font-mono mt-0.5 hidden sm:block">
                                Bal: {formatInr(Number(tx.parsed_available_balance))}
                              </p>
                            )}
                        </div>
                      </button>
                    );
                  })}
                </div>
              </div>
            ))}
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
        onSuccess={() => onRefresh?.()}
      />
    </>
  );
}
