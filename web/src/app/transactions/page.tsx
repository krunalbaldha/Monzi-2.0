'use client';

import React, { useState, useEffect } from 'react';
import { Header } from '@/components/layout/Header';
import { GlassCard } from '@/components/ui/GlassCard';
import { Badge } from '@/components/ui/Badge';
import { Search, ArrowUpRight, ArrowDownLeft, Download, Calendar, CreditCard } from 'lucide-react';
import { Transaction } from '@/lib/types';
import { mockTransactions } from '@/lib/mockData';
import { TransactionDetailModal } from '@/components/dashboard/TransactionDetailModal';

export default function TransactionsPage() {
  const [transactions, setTransactions] = useState<Transaction[]>(mockTransactions);
  const [search, setSearch] = useState('');
  const [selectedType, setSelectedType] = useState('ALL');
  const [selectedTx, setSelectedTx] = useState<Transaction | null>(null);

  const fetchTransactions = async () => {
    try {
      const url = new URL('/api/transactions', window.location.origin);
      if (selectedType !== 'ALL') url.searchParams.set('type', selectedType);
      if (search) url.searchParams.set('search', search);

      const res = await fetch(url.toString());
      if (res.ok) {
        const data = await res.json();
        if (data.transactions) setTransactions(data.transactions);
      }
    } catch (e) {
      console.error(e);
    }
  };

  useEffect(() => {
    fetchTransactions();
  }, [selectedType, search]);

  const formatInr = (val: number) => {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 0,
    }).format(val);
  };

  const formatDate = (dateStr: string) => {
    return new Intl.DateTimeFormat('en-IN', {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    }).format(new Date(dateStr));
  };

  const exportToCsv = () => {
    if (!transactions.length) return;
    const headers = ['Date', 'Merchant', 'Type', 'Direction', 'Amount', 'Bank', 'Account', 'Ref ID'];
    const rows = transactions.map((t) => [
      `"${new Date(t.transaction_date).toISOString()}"`,
      `"${(t.merchant || '').replace(/"/g, '""')}"`,
      t.transaction_type,
      t.direction,
      t.amount,
      `"${t.accounts?.bank_name || ''}"`,
      `"${t.accounts?.account_number_mask || ''}"`,
      `"${t.reference_id || ''}"`,
    ]);

    const csvContent = 'data:text/csv;charset=utf-8,' + [headers.join(','), ...rows.map((r) => r.join(','))].join('\n');
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement('a');
    link.setAttribute('href', encodedUri);
    link.setAttribute('download', `monzi_transactions_${new Date().toISOString().slice(0, 10)}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  return (
    <div className="flex flex-col min-h-screen">
      <Header onRefresh={fetchTransactions} />

      <div className="p-4 sm:p-6 md:p-8 space-y-4 md:space-y-6 max-w-7xl mx-auto w-full">
        {/* Page Title & Controls */}
        <div className="flex flex-col gap-3">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-lg sm:text-xl font-bold tracking-tight text-slate-900">All Transactions</h2>
              <p className="text-[11px] sm:text-xs text-slate-500 font-medium">
                Extracted automatically from your Android bank notifications ({transactions.length} records)
              </p>
            </div>
            <button
              onClick={exportToCsv}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-white/80 border border-slate-200/80 text-slate-700 hover:bg-slate-50 text-xs font-semibold shadow-sm transition-all"
              title="Download transactions as CSV"
            >
              <Download className="w-3.5 h-3.5 text-blue-600" />
              <span className="hidden sm:inline">Export CSV</span>
              <span className="sm:hidden">CSV</span>
            </button>
          </div>

          {/* Search & Filter Bar */}
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2.5">
            <div className="relative w-full sm:w-64">
              <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
              <input
                type="text"
                placeholder="Search merchant, UTR..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="w-full pl-9 pr-4 py-2 text-xs rounded-xl bg-white/90 border border-slate-200/80 shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500/20 text-slate-900 font-medium"
              />
            </div>

            {/* Swipeable Filter Chips on Mobile */}
            <div className="flex items-center gap-1.5 overflow-x-auto pb-1 scrollbar-none">
              {['ALL', 'EXPENSE', 'INCOME', 'INVESTMENT', 'CC_BILL_PAYMENT'].map((type) => (
                <button
                  key={type}
                  onClick={() => setSelectedType(type)}
                  className={`px-3 py-1.5 rounded-xl text-xs font-semibold whitespace-nowrap transition-all ${
                    selectedType === type
                      ? 'bg-blue-600 text-white shadow-sm'
                      : 'bg-white/80 border border-slate-200/80 text-slate-600 hover:text-slate-900'
                  }`}
                >
                  {type === 'ALL'
                    ? 'All'
                    : type === 'CC_BILL_PAYMENT'
                    ? 'CC Bills'
                    : type.charAt(0) + type.slice(1).toLowerCase()}
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* --- MOBILE VIEW: Native iOS-Style Transaction Cards (hidden on md+) --- */}
        <div className="md:hidden space-y-2.5">
          {transactions.map((tx) => {
            const isCredit = tx.direction === 'CREDIT';
            return (
              <GlassCard
                key={tx.id}
                onClick={() => setSelectedTx(tx)}
                className="p-3.5 space-y-2.5 cursor-pointer active:scale-[0.98] transition-transform"
              >
                <div className="flex items-start justify-between gap-2">
                  <div className="flex items-center gap-2.5">
                    <div
                      className={`w-9 h-9 rounded-xl flex items-center justify-center shrink-0 ${
                        isCredit
                          ? 'bg-emerald-50 text-emerald-600'
                          : 'bg-rose-50 text-rose-600'
                      }`}
                    >
                      {isCredit ? (
                        <ArrowDownLeft className="w-4 h-4" />
                      ) : (
                        <ArrowUpRight className="w-4 h-4" />
                      )}
                    </div>
                    <div>
                      <p className="text-sm font-bold text-slate-900 tracking-tight leading-tight">
                        {tx.merchant || 'Unknown Merchant'}
                      </p>
                      <p className="text-[11px] text-slate-400 font-medium mt-0.5">
                        {tx.categories?.name || 'Uncategorized'}
                      </p>
                    </div>
                  </div>

                  <div className="text-right shrink-0">
                    <p
                      className={`font-mono font-bold text-sm tracking-tight ${
                        isCredit ? 'text-emerald-600' : 'text-slate-900'
                      }`}
                    >
                      {isCredit ? '+' : '-'} {formatInr(tx.amount)}
                    </p>
                    <Badge type={tx.transaction_type} className="text-[10px] py-0 px-1.5 mt-0.5" />
                  </div>
                </div>

                {/* Sub-info bar */}
                <div className="flex items-center justify-between pt-2 border-t border-slate-100 text-[11px] text-slate-500 font-medium">
                  <div className="flex items-center gap-1.5 truncate max-w-[200px]">
                    <CreditCard className="w-3 h-3 text-slate-400 shrink-0" />
                    <span className="truncate">
                      {tx.accounts?.bank_name || 'Bank'} {tx.accounts?.account_number_mask || ''}
                    </span>
                  </div>
                  <div className="flex items-center gap-1 shrink-0 text-slate-400">
                    <Calendar className="w-3 h-3" />
                    <span>{formatDate(tx.transaction_date)}</span>
                  </div>
                </div>
              </GlassCard>
            );
          })}
        </div>

        {/* --- DESKTOP VIEW: Full Data Table (hidden on mobile, visible on md+) --- */}
        <GlassCard className="hidden md:block p-2 overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead>
                <tr className="border-b border-slate-100 text-slate-400 font-semibold uppercase tracking-wider">
                  <th className="py-3 px-4">Merchant / Beneficiary</th>
                  <th className="py-3 px-4">Type</th>
                  <th className="py-3 px-4">Account</th>
                  <th className="py-3 px-4">Reference (UTR)</th>
                  <th className="py-3 px-4">Date & Time</th>
                  <th className="py-3 px-4 text-right">Amount</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 font-medium">
                {transactions.map((tx) => {
                  const isCredit = tx.direction === 'CREDIT';
                  return (
                    <tr
                      key={tx.id}
                      onClick={() => setSelectedTx(tx)}
                      className="hover:bg-slate-50/70 transition-all cursor-pointer"
                    >
                      <td className="py-3 px-4 flex items-center gap-3">
                        <div
                          className={`w-8 h-8 rounded-xl flex items-center justify-center shrink-0 ${
                            isCredit
                              ? 'bg-emerald-50 text-emerald-600'
                              : 'bg-rose-50 text-rose-600'
                          }`}
                        >
                          {isCredit ? (
                            <ArrowDownLeft className="w-4 h-4" />
                          ) : (
                            <ArrowUpRight className="w-4 h-4" />
                          )}
                        </div>
                        <div>
                          <p className="font-bold text-slate-900">{tx.merchant || 'Unknown'}</p>
                          <p className="text-[11px] text-slate-400">{tx.categories?.name || 'Uncategorized'}</p>
                        </div>
                      </td>
                      <td className="py-3 px-4">
                        <Badge type={tx.transaction_type} />
                      </td>
                      <td className="py-3 px-4 text-slate-600">
                        {tx.accounts?.bank_name || 'Bank'} {tx.accounts?.account_number_mask || ''}
                      </td>
                      <td className="py-3 px-4 font-mono text-slate-400">
                        {tx.reference_id || '—'}
                      </td>
                      <td className="py-3 px-4 text-slate-500">
                        {formatDate(tx.transaction_date)}
                      </td>
                      <td className="py-3 px-4 text-right">
                        <p
                          className={`font-mono font-bold text-sm ${
                            isCredit ? 'text-emerald-600' : 'text-slate-900'
                          }`}
                        >
                          {isCredit ? '+' : '-'} {formatInr(tx.amount)}
                        </p>
                        {tx.parsed_available_balance !== undefined && (
                          <p className="text-[10px] text-slate-400 font-mono">
                            Bal: {formatInr(tx.parsed_available_balance)}
                          </p>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </GlassCard>

        <TransactionDetailModal
          transaction={selectedTx}
          onClose={() => setSelectedTx(null)}
          onUpdate={fetchTransactions}
        />
      </div>
    </div>
  );
}
