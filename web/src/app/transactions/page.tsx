'use client';

import React, { useState, useEffect, useCallback, Suspense } from 'react';
import { useSearchParams } from 'next/navigation';
import { Header } from '@/components/layout/Header';
import { GlassCard } from '@/components/ui/GlassCard';
import {
  Search,
  Plus,
  ArrowLeftRight,
  ChevronDown,
} from 'lucide-react';
import { Transaction, Account } from '@/lib/types';
import { TransactionDetailModal } from '@/components/dashboard/TransactionDetailModal';
import { AddTransactionModal } from '@/components/dashboard/AddTransactionModal';
import { useToast } from '@/components/ui/Toast';

const PAGE_SIZE = 50;

function getDateLabel(dateStr: string): string {
  const d = new Date(dateStr);
  const today = new Date();
  const yesterday = new Date(today);
  yesterday.setDate(yesterday.getDate() - 1);
  if (d.toDateString() === today.toDateString()) {
    return `Today — ${d.toLocaleDateString('en-IN', { month: 'short', day: 'numeric' })}`;
  }
  if (d.toDateString() === yesterday.toDateString()) {
    return `Yesterday — ${d.toLocaleDateString('en-IN', { month: 'short', day: 'numeric' })}`;
  }
  return d.toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' });
}

function groupByDate(transactions: Transaction[]): { label: string; items: Transaction[] }[] {
  const map = new Map<string, Transaction[]>();
  for (const tx of transactions) {
    const key = new Date(tx.transaction_date).toDateString();
    if (!map.has(key)) map.set(key, []);
    map.get(key)!.push(tx);
  }
  return Array.from(map.entries()).map(([, items]) => ({
    label: getDateLabel(items[0].transaction_date),
    items,
  }));
}

function TransactionsContent() {
  const searchParams = useSearchParams();
  const initialAccountId = searchParams.get('account') || 'ALL';

  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [selectedType, setSelectedType] = useState('ALL');
  const [selectedAccountId, setSelectedAccountId] = useState(initialAccountId);
  const [page, setPage] = useState(1);
  const [hasMore, setHasMore] = useState(false);
  const [selectedTx, setSelectedTx] = useState<Transaction | null>(null);
  const [isAddOpen, setIsAddOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const toast = useToast();

  // Debounce search input 300ms
  useEffect(() => {
    const t = setTimeout(() => setDebouncedSearch(search), 300);
    return () => clearTimeout(t);
  }, [search]);

  const fetchFilters = async () => {
    try {
      const accRes = await fetch('/api/accounts');
      if (accRes.ok) {
        const d = await accRes.json();
        setAccounts(d.accounts || []);
      }
    } catch (e) {
      console.error(e);
    }
  };

  const fetchTransactions = useCallback(async (resetPage = true) => {
    setLoading(true);
    const currentPage = resetPage ? 1 : page;
    if (resetPage) setPage(1);
    try {
      const url = new URL('/api/transactions', window.location.origin);
      if (selectedType !== 'ALL') url.searchParams.set('type', selectedType);
      if (selectedAccountId !== 'ALL') url.searchParams.set('account_id', selectedAccountId);
      if (debouncedSearch.trim()) url.searchParams.set('search', debouncedSearch.trim());
      url.searchParams.set('limit', String(PAGE_SIZE * currentPage));

      const res = await fetch(url.toString());
      if (res.ok) {
        const data = await res.json();
        const list: Transaction[] = data.transactions || [];
        setTransactions(list);
        setHasMore(list.length === PAGE_SIZE * currentPage);
      }
    } catch (e) {
      console.error(e);
      toast.error('Failed to load transactions');
    } finally {
      setLoading(false);
    }
  }, [selectedType, selectedAccountId, debouncedSearch, page, toast]);

  useEffect(() => {
    fetchFilters();
  }, []);

  useEffect(() => {
    fetchTransactions(true);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedType, selectedAccountId, debouncedSearch]);

  const handleLoadMore = () => {
    const nextPage = page + 1;
    setPage(nextPage);
    // fetch with next page
    (async () => {
      setLoading(true);
      try {
        const url = new URL('/api/transactions', window.location.origin);
        if (selectedType !== 'ALL') url.searchParams.set('type', selectedType);
        if (selectedAccountId !== 'ALL') url.searchParams.set('account_id', selectedAccountId);
        if (debouncedSearch.trim()) url.searchParams.set('search', debouncedSearch.trim());
        url.searchParams.set('limit', String(PAGE_SIZE * nextPage));
        const res = await fetch(url.toString());
        if (res.ok) {
          const data = await res.json();
          const list: Transaction[] = data.transactions || [];
          setTransactions(list);
          setHasMore(list.length === PAGE_SIZE * nextPage);
        }
      } catch (e) {
        console.error(e);
      } finally {
        setLoading(false);
      }
    })();
  };

  const formatInr = (val: number) =>
    new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(val);

  const formatTime = (dateStr: string) =>
    new Intl.DateTimeFormat('en-IN', { hour: '2-digit', minute: '2-digit' }).format(new Date(dateStr));

  const groups = groupByDate(transactions);

  const typeFilters = [
    { label: 'All', value: 'ALL' },
    { label: 'Income', value: 'INCOME' },
    { label: 'Expenses', value: 'EXPENSE' },
    { label: 'Transfers', value: 'TRANSFER' },
    { label: 'Investments', value: 'INVESTMENT' },
    { label: 'Refunds', value: 'REFUND' },
  ];

  const getCategoryInitial = (tx: Transaction) => {
    if (tx.categories?.name) return tx.categories.name[0].toUpperCase();
    return tx.merchant ? tx.merchant[0].toUpperCase() : '?';
  };

  const getCategoryColor = (tx: Transaction) => {
    if (tx.categories?.color) return tx.categories.color;
    return tx.direction === 'CREDIT' ? '#10B981' : '#EF4444';
  };

  return (
    <div className="flex flex-col min-h-screen">
      <Header onRefresh={() => fetchTransactions(true)} isRefreshing={loading} />

      <div className="p-4 sm:p-6 md:p-8 space-y-4 max-w-4xl mx-auto w-full">
        {/* Page Title */}
        <div className="flex items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <h2 className="text-xl font-bold tracking-tight text-slate-900">Transactions</h2>
            {!loading && (
              <span className="bg-blue-100 text-blue-700 text-xs font-bold px-2.5 py-1 rounded-full">
                {transactions.length}
              </span>
            )}
          </div>
          <button
            onClick={() => setIsAddOpen(true)}
            className="flex items-center gap-1.5 px-4 py-2.5 rounded-xl bg-blue-600 text-white text-xs font-semibold shadow-sm shadow-blue-500/25 hover:bg-blue-700 active:scale-95 transition-all min-h-[44px]"
          >
            <Plus className="w-4 h-4" />
            <span>Add</span>
          </button>
        </div>

        {/* Filter chips */}
        <div className="flex gap-2 overflow-x-auto pb-1 scrollbar-none">
          {typeFilters.map((f) => (
            <button
              key={f.value}
              onClick={() => setSelectedType(f.value)}
              className={`px-4 py-2 rounded-full text-xs font-semibold whitespace-nowrap transition-all min-h-[36px] ${
                selectedType === f.value
                  ? 'bg-blue-600 text-white shadow-sm shadow-blue-500/30'
                  : 'bg-white border border-slate-200 text-slate-600 hover:border-blue-300 hover:text-blue-600'
              }`}
            >
              {f.label}
            </button>
          ))}
        </div>

        {/* Search + Account Filter */}
        <div className="flex gap-2">
          {/* Search */}
          <div className="relative flex-1">
            <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              placeholder="Search merchant, notes..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="w-full pl-10 pr-4 py-2.5 rounded-full bg-white border border-slate-200 text-sm font-medium focus:outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100 text-slate-900 min-h-[44px]"
            />
          </div>

          {/* Account Filter */}
          <div className="relative">
            <select
              value={selectedAccountId}
              onChange={(e) => setSelectedAccountId(e.target.value)}
              className="appearance-none pl-3 pr-8 py-2.5 rounded-full bg-white border border-slate-200 text-xs font-semibold text-slate-700 focus:outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100 min-h-[44px] cursor-pointer"
            >
              <option value="ALL">All Accounts</option>
              {accounts.map((acc) => (
                <option key={acc.id} value={acc.id}>
                  {acc.bank_name}
                </option>
              ))}
            </select>
            <ChevronDown className="w-3.5 h-3.5 text-slate-400 absolute right-2.5 top-1/2 -translate-y-1/2 pointer-events-none" />
          </div>
        </div>

        {/* Transaction List grouped by date */}
        {loading && transactions.length === 0 ? (
          <GlassCard className="p-8 text-center">
            <div className="w-8 h-8 border-2 border-blue-600 border-t-transparent rounded-full animate-spin mx-auto mb-3" />
            <p className="text-xs text-slate-400 font-medium">Loading transactions...</p>
          </GlassCard>
        ) : transactions.length === 0 ? (
          <GlassCard className="py-16 text-center space-y-3">
            <div className="w-14 h-14 rounded-2xl bg-blue-50 flex items-center justify-center mx-auto">
              <ArrowLeftRight className="w-7 h-7 text-blue-400" />
            </div>
            <h3 className="text-sm font-bold text-slate-800">No Transactions Found</h3>
            <p className="text-xs text-slate-400 max-w-xs mx-auto">
              No transactions match your current filters. Try clearing them.
            </p>
            <button
              onClick={() => {
                setSearch('');
                setSelectedType('ALL');
                setSelectedAccountId('ALL');
              }}
              className="px-4 py-2 text-xs font-semibold rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 transition-all"
            >
              Reset Filters
            </button>
          </GlassCard>
        ) : (
          <div className="space-y-4">
            {groups.map((group) => (
              <div key={group.label}>
                {/* Sticky date header */}
                <div className="sticky top-16 z-10 py-1.5 mb-1">
                  <span className="text-[11px] font-bold text-slate-500 uppercase tracking-wider bg-[#F7F8FA]/90 backdrop-blur-sm px-2 py-0.5 rounded-full">
                    {group.label}
                  </span>
                </div>

                <GlassCard className="overflow-hidden divide-y divide-slate-100/80">
                  {group.items.map((tx) => {
                    const isCredit = tx.direction === 'CREDIT';
                    const catColor = getCategoryColor(tx);
                    const initial = getCategoryInitial(tx);
                    return (
                      <div
                        key={tx.id}
                        onClick={() => setSelectedTx(tx)}
                        className="flex items-center gap-3 px-4 py-3.5 min-h-[72px] hover:bg-slate-50/80 active:bg-slate-100 cursor-pointer transition-colors"
                      >
                        {/* Category circle */}
                        <div
                          className="w-10 h-10 rounded-full flex items-center justify-center shrink-0 text-white text-sm font-bold"
                          style={{ backgroundColor: catColor }}
                        >
                          {tx.categories?.color ? (
                            <span
                              className="w-3 h-3 rounded-full bg-white/70"
                            />
                          ) : (
                            initial
                          )}
                        </div>

                        {/* Middle info */}
                        <div className="flex-1 min-w-0">
                          <p className="text-sm font-bold text-slate-900 truncate">
                            {tx.merchant || 'Unknown Merchant'}
                          </p>
                          <p className="text-[11px] text-slate-400 font-medium truncate mt-0.5">
                            {tx.payment_mode || 'UPI'}
                            {tx.categories?.name && ` · ${tx.categories.name}`}
                          </p>
                        </div>

                        {/* Right: time + amount */}
                        <div className="text-right shrink-0">
                          <p
                            className={`text-sm font-bold font-mono ${
                              isCredit ? 'text-emerald-600' : 'text-rose-500'
                            }`}
                          >
                            {isCredit ? '+' : '-'}{formatInr(tx.amount)}
                          </p>
                          <p className="text-[10px] text-slate-400 font-medium mt-0.5">
                            {formatTime(tx.transaction_date)}
                          </p>
                        </div>
                      </div>
                    );
                  })}
                </GlassCard>
              </div>
            ))}

            {/* Load More */}
            {hasMore && (
              <button
                onClick={handleLoadMore}
                disabled={loading}
                className="w-full py-3 rounded-xl bg-white border border-slate-200 text-xs font-semibold text-slate-600 hover:bg-slate-50 hover:border-slate-300 transition-all active:scale-99 disabled:opacity-50 flex items-center justify-center gap-2"
              >
                {loading ? (
                  <>
                    <span className="w-4 h-4 border-2 border-slate-400 border-t-transparent rounded-full animate-spin" />
                    Loading...
                  </>
                ) : (
                  <>Load 50 more</>
                )}
              </button>
            )}
          </div>
        )}
      </div>

      <TransactionDetailModal
        transaction={selectedTx}
        onClose={() => setSelectedTx(null)}
        onUpdate={() => fetchTransactions(true)}
      />

      <AddTransactionModal
        isOpen={isAddOpen}
        onClose={() => setIsAddOpen(false)}
        onSuccess={() => fetchTransactions(true)}
      />
    </div>
  );
}

export default function TransactionsPage() {
  return (
    <Suspense fallback={<div className="p-8 text-center text-xs text-slate-400">Loading...</div>}>
      <TransactionsContent />
    </Suspense>
  );
}
