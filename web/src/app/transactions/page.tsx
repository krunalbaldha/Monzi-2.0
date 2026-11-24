'use client';

import React, { useState, useEffect, useCallback, Suspense } from 'react';
import { useSearchParams } from 'next/navigation';
import { Header } from '@/components/layout/Header';
import { GlassCard } from '@/components/ui/GlassCard';
import { Badge } from '@/components/ui/Badge';
import { 
  Search, 
  ArrowUpRight, 
  ArrowDownLeft, 
  Download, 
  Plus, 
  Filter, 
  ArrowUpDown,
  CreditCard,
  Tag,
  ArrowLeftRight,
  MessageSquare
} from 'lucide-react';
import { Transaction, Account, Category } from '@/lib/types';
import { TransactionDetailModal } from '@/components/dashboard/TransactionDetailModal';
import { AddTransactionModal } from '@/components/dashboard/AddTransactionModal';
import { useToast } from '@/components/ui/Toast';
import { useSms } from '@/components/sms/SmsProvider';

function TransactionsContent() {
  const searchParams = useSearchParams();
  const initialAccountId = searchParams.get('account') || 'ALL';

  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [search, setSearch] = useState('');
  const [selectedType, setSelectedType] = useState('ALL');
  const [selectedAccountId, setSelectedAccountId] = useState(initialAccountId);
  const [selectedCategoryId, setSelectedCategoryId] = useState('ALL');
  const [sortBy, setSortBy] = useState<'date_desc' | 'date_asc' | 'amount_desc' | 'amount_asc'>('date_desc');
  const [selectedTx, setSelectedTx] = useState<Transaction | null>(null);
  const [isAddOpen, setIsAddOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const toast = useToast();
  const { openSyncModal } = useSms();

  const fetchFilters = async () => {
    try {
      const [accRes, catRes] = await Promise.all([
        fetch('/api/accounts'),
        fetch('/api/categories'),
      ]);
      if (accRes.ok) {
        const accData = await accRes.json();
        setAccounts(accData.accounts || []);
      }
      if (catRes.ok) {
        const catData = await catRes.json();
        setCategories(catData.categories || []);
      }
    } catch (e) {
      console.error(e);
    }
  };

  const fetchTransactions = useCallback(async () => {
    setLoading(true);
    try {
      const url = new URL('/api/transactions', window.location.origin);
      if (selectedType !== 'ALL') url.searchParams.set('type', selectedType);
      if (selectedAccountId !== 'ALL') url.searchParams.set('account_id', selectedAccountId);
      if (selectedCategoryId !== 'ALL') url.searchParams.set('category_id', selectedCategoryId);
      if (search.trim()) url.searchParams.set('search', search.trim());

      const res = await fetch(url.toString());
      if (res.ok) {
        const data = await res.json();
        let list: Transaction[] = data.transactions || [];

        // Apply sorting
        if (sortBy === 'date_asc') {
          list.sort((a, b) => new Date(a.transaction_date).getTime() - new Date(b.transaction_date).getTime());
        } else if (sortBy === 'amount_desc') {
          list.sort((a, b) => Number(b.amount) - Number(a.amount));
        } else if (sortBy === 'amount_asc') {
          list.sort((a, b) => Number(a.amount) - Number(b.amount));
        }

        setTransactions(list);
      }
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  }, [selectedType, selectedAccountId, selectedCategoryId, search, sortBy]);

  useEffect(() => {
    fetchFilters();
  }, []);

  useEffect(() => {
    fetchTransactions();
  }, [fetchTransactions]);

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
    if (!transactions.length) {
      toast.info('No transactions to export');
      return;
    }

    const headers = ['Date', 'Merchant', 'Type', 'Direction', 'Amount (INR)', 'Bank', 'Account Mask', 'Ref ID (UTR)', 'Notes'];
    const rows = transactions.map((t) => [
      `"${new Date(t.transaction_date).toISOString()}"`,
      `"${(t.merchant || '').replace(/"/g, '""')}"`,
      t.transaction_type,
      t.direction,
      t.amount,
      `"${t.accounts?.bank_name || ''}"`,
      `"${t.accounts?.account_number_mask || ''}"`,
      `"${t.reference_id || ''}"`,
      `"${(t.notes || '').replace(/"/g, '""')}"`,
    ]);

    const csvContent = 'data:text/csv;charset=utf-8,' + [headers.join(','), ...rows.map((r) => r.join(','))].join('\n');
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement('a');
    link.setAttribute('href', encodedUri);
    link.setAttribute('download', `monzi_transactions_${new Date().toISOString().slice(0, 10)}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);

    toast.success(`Exported ${transactions.length} transactions as CSV`);
  };

  return (
    <div className="flex flex-col min-h-screen">
      <Header onRefresh={fetchTransactions} isRefreshing={loading} />

      <div className="p-4 sm:p-6 md:p-8 space-y-4 md:space-y-6 max-w-7xl mx-auto w-full">
        {/* Page Title & Controls */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div>
            <h2 className="text-lg sm:text-xl font-bold tracking-tight text-slate-900">All Transactions</h2>
            <p className="text-[11px] sm:text-xs text-slate-500 font-medium">
              Live database records ({transactions.length} displayed)
            </p>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={openSyncModal}
              className="flex items-center gap-1.5 px-3 py-2 rounded-xl bg-emerald-50 border border-emerald-200/80 text-emerald-800 hover:bg-emerald-100 text-xs font-semibold shadow-sm active:scale-95 transition-all"
            >
              <MessageSquare className="w-3.5 h-3.5 text-emerald-600" />
              <span>Sync SMS</span>
            </button>
            <button
              onClick={() => setIsAddOpen(true)}
              className="flex items-center gap-1.5 px-3 py-2 rounded-xl bg-blue-600 text-white hover:bg-blue-700 text-xs font-semibold shadow-sm shadow-blue-500/20 active:scale-95 transition-all"
            >
              <Plus className="w-3.5 h-3.5" />
              <span>Add Transaction</span>
            </button>
            <button
              onClick={exportToCsv}
              className="flex items-center gap-1.5 px-3 py-2 rounded-xl bg-white border border-slate-200/80 text-slate-700 hover:bg-slate-50 text-xs font-semibold shadow-sm transition-all"
              title="Download transactions as CSV"
            >
              <Download className="w-3.5 h-3.5 text-blue-600" />
              <span className="hidden sm:inline">Export CSV</span>
            </button>
          </div>
        </div>

        {/* Search & Dynamic Filter Row */}
        <div className="grid grid-cols-1 md:grid-cols-4 gap-2.5 sm:gap-3">
          {/* Search Input */}
          <div className="md:col-span-2 relative">
            <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              placeholder="Search merchant, UTR, bank or notes..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="w-full pl-9 pr-4 py-2.5 rounded-xl bg-white border border-slate-200/80 text-xs font-medium focus:outline-none focus:ring-2 focus:ring-blue-500/20 shadow-sm text-slate-900"
            />
          </div>

          {/* Account Filter */}
          <div>
            <select
              value={selectedAccountId}
              onChange={(e) => setSelectedAccountId(e.target.value)}
              className="w-full px-3 py-2.5 rounded-xl bg-white border border-slate-200/80 text-xs font-semibold text-slate-700 focus:outline-none focus:ring-2 focus:ring-blue-500/20 shadow-sm"
            >
              <option value="ALL">All Accounts & Cards</option>
              {accounts.map((acc) => (
                <option key={acc.id} value={acc.id}>
                  {acc.bank_name} ({acc.account_number_mask})
                </option>
              ))}
            </select>
          </div>

          {/* Category Filter */}
          <div>
            <select
              value={selectedCategoryId}
              onChange={(e) => setSelectedCategoryId(e.target.value)}
              className="w-full px-3 py-2.5 rounded-xl bg-white border border-slate-200/80 text-xs font-semibold text-slate-700 focus:outline-none focus:ring-2 focus:ring-blue-500/20 shadow-sm"
            >
              <option value="ALL">All Categories</option>
              {categories.map((cat) => (
                <option key={cat.id} value={cat.id}>
                  {cat.name}
                </option>
              ))}
            </select>
          </div>
        </div>

        {/* Transaction Type Filter Tabs */}
        <div className="flex items-center gap-1.5 overflow-x-auto pb-1 scrollbar-none text-xs font-semibold">
          {[
            { label: 'All', value: 'ALL' },
            { label: 'Expenses', value: 'EXPENSE' },
            { label: 'Income', value: 'INCOME' },
            { label: 'Transfers', value: 'TRANSFER' },
            { label: 'Investments', value: 'INVESTMENT' },
            { label: 'Refunds', value: 'REFUND' },
          ].map((tab) => (
            <button
              key={tab.value}
              onClick={() => setSelectedType(tab.value)}
              className={`px-3 py-1.5 rounded-xl whitespace-nowrap transition-all ${
                selectedType === tab.value
                  ? 'bg-slate-900 text-white shadow-sm'
                  : 'bg-white border border-slate-200/70 text-slate-600 hover:bg-slate-50'
              }`}
            >
              {tab.label}
            </button>
          ))}

          {/* Sort Menu */}
          <div className="ml-auto pl-2 shrink-0">
            <select
              value={sortBy}
              onChange={(e) => setSortBy(e.target.value as any)}
              className="px-2.5 py-1.5 rounded-xl bg-white border border-slate-200/70 text-xs font-semibold text-slate-600 focus:outline-none"
            >
              <option value="date_desc">Newest First</option>
              <option value="date_asc">Oldest First</option>
              <option value="amount_desc">Highest Amount</option>
              <option value="amount_asc">Lowest Amount</option>
            </select>
          </div>
        </div>

        {/* Transactions Table / List */}
        <GlassCard className="overflow-hidden">
          {transactions.length === 0 && !loading ? (
            <div className="py-14 text-center p-6 space-y-3">
              <div className="w-12 h-12 rounded-2xl bg-blue-50 text-blue-600 flex items-center justify-center mx-auto">
                <ArrowLeftRight className="w-6 h-6" />
              </div>
              <h3 className="text-sm font-bold text-slate-800">No Transactions Found</h3>
              <p className="text-xs text-slate-400 max-w-sm mx-auto">
                No transactions match your current search or filter criteria. Add a transaction or clear your filters.
              </p>
              <button
                onClick={() => {
                  setSearch('');
                  setSelectedType('ALL');
                  setSelectedAccountId('ALL');
                  setSelectedCategoryId('ALL');
                }}
                className="px-4 py-2 text-xs font-semibold rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 transition-all"
              >
                Reset All Filters
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
                    className="p-3.5 sm:p-4 flex items-center justify-between hover:bg-slate-50/70 active:bg-slate-100/80 transition-all cursor-pointer gap-2"
                  >
                    <div className="flex items-center gap-3 sm:gap-4 min-w-0">
                      <div
                        className={`w-10 h-10 rounded-2xl flex items-center justify-center shrink-0 ${
                          isCredit ? 'bg-emerald-50 text-emerald-600' : 'bg-rose-50 text-rose-600'
                        }`}
                      >
                        {isCredit ? (
                          <ArrowDownLeft className="w-5 h-5" />
                        ) : (
                          <ArrowUpRight className="w-5 h-5" />
                        )}
                      </div>
                      <div className="min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                          <p className="text-xs sm:text-sm font-bold text-slate-900 truncate">
                            {tx.merchant || 'Unknown Merchant'}
                          </p>
                          <Badge type={tx.transaction_type} className="text-[10px] py-0 px-1.5" />
                          {tx.categories?.name && (
                            <span
                              className="text-[10px] font-semibold px-2 py-0.5 rounded-full"
                              style={{
                                backgroundColor: `${tx.categories.color}15`,
                                color: tx.categories.color,
                              }}
                            >
                              {tx.categories.name}
                            </span>
                          )}
                        </div>
                        <p className="text-[11px] text-slate-400 font-medium mt-0.5 truncate">
                          {tx.accounts?.bank_name || 'Bank'} {tx.accounts?.account_number_mask || ''} •{' '}
                          {formatDate(tx.transaction_date)}
                          {tx.reference_id ? ` • UTR: ${tx.reference_id.slice(-6)}` : ''}
                        </p>
                      </div>
                    </div>

                    <div className="text-right shrink-0">
                      <p
                        className={`text-sm sm:text-base font-bold font-mono tracking-tight ${
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
      </div>

      <TransactionDetailModal
        transaction={selectedTx}
        onClose={() => setSelectedTx(null)}
        onUpdate={fetchTransactions}
      />

      <AddTransactionModal
        isOpen={isAddOpen}
        onClose={() => setIsAddOpen(false)}
        onSuccess={fetchTransactions}
      />
    </div>
  );
}

export default function TransactionsPage() {
  return (
    <Suspense fallback={<div className="p-8 text-center text-xs text-slate-400">Loading transactions...</div>}>
      <TransactionsContent />
    </Suspense>
  );
}
