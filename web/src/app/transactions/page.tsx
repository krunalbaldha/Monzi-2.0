'use client';

import React, { useState, useEffect } from 'react';
import { Header } from '@/components/layout/Header';
import { GlassCard } from '@/components/ui/GlassCard';
import { Badge } from '@/components/ui/Badge';
import { Search, Filter, ArrowUpRight, ArrowDownLeft, Download } from 'lucide-react';
import { Transaction } from '@/lib/types';
import { mockTransactions } from '@/lib/mockData';

export default function TransactionsPage() {
  const [transactions, setTransactions] = useState<Transaction[]>(mockTransactions);
  const [search, setSearch] = useState('');
  const [selectedType, setSelectedType] = useState('ALL');

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

      <div className="p-8 space-y-6 max-w-7xl mx-auto w-full">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <h2 className="text-xl font-bold tracking-tight text-slate-900">All Transactions</h2>
            <p className="text-xs text-slate-500 font-medium">
              Extracted automatically from your Android bank notifications
            </p>
          </div>

          {/* Filters & Search */}
          <div className="flex items-center gap-3">
            <div className="relative">
              <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
              <input
                type="text"
                placeholder="Search merchant, bank..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="pl-9 pr-4 py-2 text-xs rounded-xl bg-white/80 border border-slate-200/80 shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500/20 text-slate-900 w-56"
              />
            </div>

            <div className="flex items-center gap-1 bg-white/80 border border-slate-200/80 p-1 rounded-xl shadow-sm text-xs">
              {['ALL', 'EXPENSE', 'INCOME', 'INVESTMENT'].map((type) => (
                <button
                  key={type}
                  onClick={() => setSelectedType(type)}
                  className={`px-3 py-1 rounded-lg font-medium transition-all ${
                    selectedType === type
                      ? 'bg-blue-600 text-white shadow-sm'
                      : 'text-slate-600 hover:text-slate-900'
                  }`}
                >
                  {type === 'ALL' ? 'All' : type.charAt(0) + type.slice(1).toLowerCase()}
                </button>
              ))}
            </div>

            <button
              onClick={exportToCsv}
              className="flex items-center gap-1.5 px-3 py-2 rounded-xl bg-white/80 border border-slate-200/80 text-slate-700 hover:bg-slate-50 text-xs font-semibold shadow-sm transition-all"
              title="Download transactions as CSV"
            >
              <Download className="w-3.5 h-3.5 text-blue-600" />
              <span>Export CSV</span>
            </button>
          </div>
        </div>

        {/* Transactions Table Card */}
        <GlassCard className="p-2 overflow-hidden">
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
                    <tr key={tx.id} className="hover:bg-slate-50/60 transition-all">
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
      </div>
    </div>
  );
}
