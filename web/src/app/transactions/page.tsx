'use client';

import React, { useState, useEffect, useCallback, useMemo, Suspense } from 'react';
import { useSearchParams } from 'next/navigation';
import { Header } from '@/components/layout/Header';
import { GlassCard } from '@/components/ui/GlassCard';
import {
  Search,
  Plus,
  ArrowLeftRight,
  ChevronDown,
  WifiOff,
  RotateCcw,
  SlidersHorizontal,
} from 'lucide-react';
import { Transaction, Account, Category } from '@/lib/types';
import { loadOfflineSnapshot, saveOfflineSnapshot } from '@/lib/offlineStore';
import { TransactionDetailModal } from '@/components/dashboard/TransactionDetailModal';
import { AddTransactionModal } from '@/components/dashboard/AddTransactionModal';
import { useToast } from '@/components/ui/Toast';
import { usePwa } from '@/components/pwa/PwaProvider';

const PAGE_SIZE = 50;

type SortKey = 'date_desc' | 'date_asc' | 'amount_desc' | 'amount_asc';
type DirectionFilter = 'ALL' | 'CREDIT' | 'DEBIT';

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
  return d.toLocaleDateString('en-IN', {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  });
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

function getSourcePill(tx: Transaction): { label: string; cls: string } {
  if (tx.transaction_source === 'HISTORICAL_SMS') {
    return { label: 'Historical', cls: 'bg-indigo-50 text-indigo-700 border-indigo-200/70' };
  }
  if (tx.transaction_source === 'REFUND_REVERSAL' || tx.transaction_type === 'REFUND') {
    return { label: 'Refund', cls: 'bg-sky-50 text-sky-700 border-sky-200/70' };
  }
  if (
    tx.transaction_source === 'MANUAL' ||
    tx.payment_mode === 'MANUAL' ||
    tx.reference_id?.startsWith('MANUAL_')
  ) {
    return { label: 'Manual', cls: 'bg-slate-100 text-slate-600 border-slate-200/80' };
  }
  if (tx.transaction_source === 'INTEGRATION') {
    return { label: 'Integration', cls: 'bg-violet-50 text-violet-700 border-violet-200/70' };
  }
  return { label: 'SMS', cls: 'bg-emerald-50 text-emerald-700 border-emerald-200/70' };
}

function filterAndSortLocally(
  list: Transaction[],
  opts: {
    direction: DirectionFilter;
    type: string;
    accountId: string;
    categoryId: string;
    dateFrom: string;
    dateTo: string;
    search: string;
    sort: SortKey;
  }
): Transaction[] {
  const q = opts.search.trim().toLowerCase();
  const fromMs = opts.dateFrom ? new Date(`${opts.dateFrom}T00:00:00+05:30`).getTime() : null;
  const toMs = opts.dateTo ? new Date(`${opts.dateTo}T23:59:59.999+05:30`).getTime() : null;

  const filtered = list.filter((tx) => {
    if (opts.direction !== 'ALL' && tx.direction !== opts.direction) return false;
    if (opts.type !== 'ALL' && tx.transaction_type !== opts.type) return false;
    if (opts.accountId !== 'ALL' && tx.account_id !== opts.accountId) return false;
    if (opts.categoryId !== 'ALL' && tx.category_id !== opts.categoryId) return false;
    const tMs = new Date(tx.transaction_date).getTime();
    if (fromMs !== null && tMs < fromMs) return false;
    if (toMs !== null && tMs > toMs) return false;
    if (q) {
      const hay = `${tx.merchant || ''} ${tx.notes || ''} ${tx.reference_id || ''} ${
        tx.categories?.name || ''
      } ${tx.accounts?.bank_name || ''}`.toLowerCase();
      if (!hay.includes(q)) return false;
    }
    return true;
  });

  return [...filtered].sort((a, b) => {
    if (opts.sort === 'date_asc') {
      return new Date(a.transaction_date).getTime() - new Date(b.transaction_date).getTime();
    }
    if (opts.sort === 'amount_desc') {
      return Number(b.amount) - Number(a.amount);
    }
    if (opts.sort === 'amount_asc') {
      return Number(a.amount) - Number(b.amount);
    }
    return new Date(b.transaction_date).getTime() - new Date(a.transaction_date).getTime();
  });
}

function TransactionsContent() {
  const searchParams = useSearchParams();
  const initialAccountId = searchParams.get('account') || 'ALL';

  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);

  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [direction, setDirection] = useState<DirectionFilter>('ALL');
  const [selectedType, setSelectedType] = useState('ALL');
  const [selectedAccountId, setSelectedAccountId] = useState(initialAccountId);
  const [selectedCategoryId, setSelectedCategoryId] = useState('ALL');
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');
  const [sortBy, setSortBy] = useState<SortKey>('date_desc');
  const [showAdvancedFilters, setShowAdvancedFilters] = useState(false);

  const [page, setPage] = useState(1);
  const [hasMore, setHasMore] = useState(false);
  const [selectedTx, setSelectedTx] = useState<Transaction | null>(null);
  const [isAddOpen, setIsAddOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const [isCachedView, setIsCachedView] = useState(false);

  const toast = useToast();
  const { isOffline } = usePwa();

  // Debounce search input 300ms
  useEffect(() => {
    const t = setTimeout(() => setDebouncedSearch(search), 300);
    return () => clearTimeout(t);
  }, [search]);

  const fetchFilterMetadata = useCallback(async () => {
    try {
      const [accSnap, catSnap] = await Promise.all([
        loadOfflineSnapshot<Account[]>('dashboard_accounts'),
        loadOfflineSnapshot<Category[]>('categories_list'),
      ]);
      if (accSnap?.data) setAccounts(accSnap.data);
      if (catSnap?.data) setCategories(catSnap.data);

      const [accRes, catRes] = await Promise.all([
        fetch('/api/accounts'),
        fetch('/api/categories'),
      ]);
      if (accRes.ok) {
        const d = await accRes.json();
        const accList = d.accounts || [];
        setAccounts(accList);
        void saveOfflineSnapshot('dashboard_accounts', accList);
      }
      if (catRes.ok) {
        const d = await catRes.json();
        const catList = d.categories || [];
        setCategories(catList);
        void saveOfflineSnapshot('categories_list', catList);
      }
    } catch (e) {
      console.error(e);
    }
  }, []);

  const buildTransactionsUrl = useCallback(
    (limit: number) => {
      const url = new URL('/api/transactions', window.location.origin);
      if (direction !== 'ALL') url.searchParams.set('direction', direction);
      if (selectedType !== 'ALL') url.searchParams.set('type', selectedType);
      if (selectedAccountId !== 'ALL') url.searchParams.set('account_id', selectedAccountId);
      if (selectedCategoryId !== 'ALL') url.searchParams.set('category_id', selectedCategoryId);
      if (dateFrom) url.searchParams.set('from', dateFrom);
      if (dateTo) url.searchParams.set('to', dateTo);
      if (sortBy) url.searchParams.set('sort', sortBy);
      if (debouncedSearch.trim()) url.searchParams.set('search', debouncedSearch.trim());
      url.searchParams.set('limit', String(limit));
      return url.toString();
    },
    [
      direction,
      selectedType,
      selectedAccountId,
      selectedCategoryId,
      dateFrom,
      dateTo,
      sortBy,
      debouncedSearch,
    ]
  );

  const isDefaultUnfilteredQuery =
    direction === 'ALL' &&
    selectedType === 'ALL' &&
    selectedAccountId === 'ALL' &&
    selectedCategoryId === 'ALL' &&
    !dateFrom &&
    !dateTo &&
    !debouncedSearch.trim() &&
    sortBy === 'date_desc';

  const fetchTransactions = useCallback(
    async (resetPage = true) => {
      setLoading(true);
      const currentPage = resetPage ? 1 : page;
      if (resetPage) setPage(1);

      try {
        const res = await fetch(buildTransactionsUrl(PAGE_SIZE * currentPage));
        if (!res.ok) throw new Error('Failed to fetch transactions');
        const data = await res.json();
        const rawList: Transaction[] = data.transactions || [];
        // Enforce client-side fallback filtering/sorting in case any query param isn't handled yet
        const finalList = filterAndSortLocally(rawList, {
          direction,
          type: selectedType,
          accountId: selectedAccountId,
          categoryId: selectedCategoryId,
          dateFrom,
          dateTo,
          search: debouncedSearch,
          sort: sortBy,
        });
        setTransactions(finalList);
        setHasMore(rawList.length === PAGE_SIZE * currentPage);
        setIsCachedView(false);

        if (isDefaultUnfilteredQuery && rawList.length > 0) {
          void saveOfflineSnapshot<Transaction[]>('transactions_list', rawList);
        }
      } catch (e) {
        console.error(e);
        const snap =
          (await loadOfflineSnapshot<Transaction[]>('transactions_list')) ||
          (await loadOfflineSnapshot<Transaction[]>('dashboard_recent_txns'));

        if (snap?.data) {
          const filtered = filterAndSortLocally(snap.data, {
            direction,
            type: selectedType,
            accountId: selectedAccountId,
            categoryId: selectedCategoryId,
            dateFrom,
            dateTo,
            search: debouncedSearch,
            sort: sortBy,
          });
          setTransactions(filtered);
          setHasMore(false);
          setIsCachedView(true);
        } else {
          toast.error('Failed to load transactions');
        }
      } finally {
        setLoading(false);
      }
    },
    [
      buildTransactionsUrl,
      page,
      direction,
      selectedType,
      selectedAccountId,
      selectedCategoryId,
      dateFrom,
      dateTo,
      debouncedSearch,
      sortBy,
      isDefaultUnfilteredQuery,
      toast,
    ]
  );

  useEffect(() => {
    void fetchFilterMetadata();
  }, [fetchFilterMetadata]);

  useEffect(() => {
    // Instant cache hydration on initial mount
    void (async () => {
      const snap = await loadOfflineSnapshot<Transaction[]>('transactions_list');
      if (snap?.data && transactions.length === 0) {
        setTransactions(
          filterAndSortLocally(snap.data, {
            direction,
            type: selectedType,
            accountId: selectedAccountId,
            categoryId: selectedCategoryId,
            dateFrom,
            dateTo,
            search: debouncedSearch,
            sort: sortBy,
          })
        );
        setIsCachedView(true);
        setLoading(false);
      }
      if (typeof navigator === 'undefined' || navigator.onLine) {
        await fetchTransactions(true);
      }
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [
    direction,
    selectedType,
    selectedAccountId,
    selectedCategoryId,
    dateFrom,
    dateTo,
    sortBy,
    debouncedSearch,
  ]);

  useEffect(() => {
    const onSync = () => void fetchTransactions(true);
    window.addEventListener('monzi:sms-synced', onSync);
    window.addEventListener('monzi:refresh', onSync);
    return () => {
      window.removeEventListener('monzi:sms-synced', onSync);
      window.removeEventListener('monzi:refresh', onSync);
    };
  }, [fetchTransactions]);

  const handleLoadMore = () => {
    const nextPage = page + 1;
    setPage(nextPage);
    void (async () => {
      setLoading(true);
      try {
        const res = await fetch(buildTransactionsUrl(PAGE_SIZE * nextPage));
        if (res.ok) {
          const data = await res.json();
          const rawList: Transaction[] = data.transactions || [];
          setTransactions(
            filterAndSortLocally(rawList, {
              direction,
              type: selectedType,
              accountId: selectedAccountId,
              categoryId: selectedCategoryId,
              dateFrom,
              dateTo,
              search: debouncedSearch,
              sort: sortBy,
            })
          );
          setHasMore(rawList.length === PAGE_SIZE * nextPage);
        }
      } catch (e) {
        console.error(e);
      } finally {
        setLoading(false);
      }
    })();
  };

  const resetAllFilters = () => {
    setSearch('');
    setDirection('ALL');
    setSelectedType('ALL');
    setSelectedAccountId('ALL');
    setSelectedCategoryId('ALL');
    setDateFrom('');
    setDateTo('');
    setSortBy('date_desc');
  };

  const hasActiveFilters =
    direction !== 'ALL' ||
    selectedType !== 'ALL' ||
    selectedAccountId !== 'ALL' ||
    selectedCategoryId !== 'ALL' ||
    Boolean(dateFrom) ||
    Boolean(dateTo) ||
    Boolean(search.trim()) ||
    sortBy !== 'date_desc';

  const formatInr = (val: number) =>
    new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 0,
    }).format(val);

  const formatTime = (dateStr: string) =>
    new Intl.DateTimeFormat('en-IN', { hour: '2-digit', minute: '2-digit' }).format(
      new Date(dateStr)
    );

  const isDateSorted = sortBy === 'date_desc' || sortBy === 'date_asc';
  const groups = useMemo(
    () => (isDateSorted ? groupByDate(transactions) : []),
    [transactions, isDateSorted]
  );

  const typeFilters = [
    { label: 'All', value: 'ALL' },
    { label: 'Income', value: 'INCOME' },
    { label: 'Expenses', value: 'EXPENSE' },
    { label: 'Transfers', value: 'TRANSFER' },
    { label: 'Refunds', value: 'REFUND' },
    { label: 'Investments', value: 'INVESTMENT' },
  ];

  const getCategoryInitial = (tx: Transaction) => {
    if (tx.categories?.name) return tx.categories.name[0].toUpperCase();
    return tx.merchant ? tx.merchant[0].toUpperCase() : '?';
  };

  const getCategoryColor = (tx: Transaction) => {
    if (tx.categories?.color) return tx.categories.color;
    return tx.direction === 'CREDIT' ? '#10B981' : '#EF4444';
  };

  const renderTransactionRow = (tx: Transaction) => {
    const isCredit = tx.direction === 'CREDIT';
    const catColor = getCategoryColor(tx);
    const initial = getCategoryInitial(tx);
    const sourcePill = getSourcePill(tx);

    return (
      <div
        key={tx.id}
        onClick={() => setSelectedTx(tx)}
        className="flex items-center gap-3 px-4 py-3.5 min-h-[72px] hover:bg-slate-50/80 active:bg-slate-100 cursor-pointer transition-colors"
      >
        <div
          className="w-10 h-10 rounded-full flex items-center justify-center shrink-0 text-white text-sm font-bold"
          style={{ backgroundColor: catColor }}
        >
          {tx.categories?.color ? <span className="w-3 h-3 rounded-full bg-white/70" /> : initial}
        </div>

        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-1.5 flex-wrap">
            <p className="text-sm font-bold text-slate-900 truncate max-w-[180px] sm:max-w-[260px]">
              {tx.merchant || 'Unknown Merchant'}
            </p>
            <span
              className={`inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-bold border ${sourcePill.cls}`}
            >
              {sourcePill.label}
            </span>
            {tx.transaction_status && tx.transaction_status !== 'COMPLETED' && (
              <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-bold bg-amber-50 text-amber-700 border border-amber-200">
                {tx.transaction_status}
              </span>
            )}
          </div>
          <p className="text-[11px] text-slate-400 font-medium truncate mt-0.5">
            {tx.payment_mode || 'UPI'}
            {tx.categories?.name && ` · ${tx.categories.name}`}
            {tx.accounts?.bank_name && ` · ${tx.accounts.bank_name}`}
          </p>
        </div>

        <div className="text-right shrink-0">
          <p
            className={`text-sm font-bold font-mono ${
              isCredit ? 'text-emerald-600' : 'text-rose-500'
            }`}
          >
            {isCredit ? '+' : '-'}
            {formatInr(tx.amount)}
          </p>
          <p className="text-[10px] text-slate-400 font-medium mt-0.5">
            {isDateSorted
              ? formatTime(tx.transaction_date)
              : new Date(tx.transaction_date).toLocaleDateString('en-IN', {
                  day: 'numeric',
                  month: 'short',
                })}
          </p>
        </div>
      </div>
    );
  };

  return (
    <div className="flex flex-col min-h-screen">
      <Header onRefresh={() => void fetchTransactions(true)} isRefreshing={loading} />

      <div className="p-4 sm:p-6 md:p-8 space-y-4 max-w-5xl mx-auto w-full">
        {/* Page Title */}
        <div className="flex items-center justify-between gap-3 flex-wrap">
          <div className="flex items-center gap-2.5 flex-wrap">
            <h2 className="text-xl font-bold tracking-tight text-slate-900">Transactions</h2>
            {!loading && (
              <span className="bg-blue-100 text-blue-700 text-xs font-bold px-2.5 py-1 rounded-full">
                {transactions.length}
              </span>
            )}
            {(isOffline || isCachedView) && (
              <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[11px] font-bold bg-amber-50 text-amber-800 border border-amber-200">
                <WifiOff className="w-3 h-3" />
                Cached (Offline)
              </span>
            )}
          </div>

          <div className="flex items-center gap-2">
            {hasActiveFilters && (
              <button
                type="button"
                onClick={resetAllFilters}
                className="flex items-center gap-1.5 px-3 py-2 rounded-xl bg-white border border-slate-200 text-slate-600 text-xs font-semibold hover:bg-slate-50 min-h-[40px]"
              >
                <RotateCcw className="w-3.5 h-3.5" />
                <span>Clear</span>
              </button>
            )}
            <button
              onClick={() => setIsAddOpen(true)}
              className="flex items-center gap-1.5 px-4 py-2 rounded-xl bg-white border border-slate-200 text-slate-700 text-xs font-semibold hover:bg-slate-50 active:scale-95 transition-all min-h-[40px]"
            >
              <Plus className="w-4 h-4 text-blue-600" />
              <span>Manual Entry</span>
            </button>
          </div>
        </div>

        {/* Credit / Debit Toggle + Transaction Type Chips */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2.5">
          <div className="inline-flex items-center p-1 bg-white border border-slate-200 rounded-full self-start">
            {(['ALL', 'CREDIT', 'DEBIT'] as DirectionFilter[]).map((dir) => (
              <button
                key={dir}
                type="button"
                onClick={() => setDirection(dir)}
                className={`px-3.5 py-1.5 rounded-full text-xs font-bold transition-all ${
                  direction === dir
                    ? dir === 'CREDIT'
                      ? 'bg-emerald-600 text-white shadow-sm'
                      : dir === 'DEBIT'
                      ? 'bg-rose-600 text-white shadow-sm'
                      : 'bg-slate-900 text-white shadow-sm'
                    : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                {dir === 'ALL' ? 'All' : dir === 'CREDIT' ? 'Credit (+)' : 'Debit (-)'}
              </button>
            ))}
          </div>

          <div className="flex gap-2 overflow-x-auto pb-1 scrollbar-none">
            {typeFilters.map((f) => (
              <button
                key={f.value}
                onClick={() => setSelectedType(f.value)}
                className={`px-3.5 py-2 rounded-full text-xs font-semibold whitespace-nowrap transition-all min-h-[36px] ${
                  selectedType === f.value
                    ? 'bg-blue-600 text-white shadow-sm shadow-blue-500/30'
                    : 'bg-white border border-slate-200 text-slate-600 hover:border-blue-300 hover:text-blue-600'
                }`}
              >
                {f.label}
              </button>
            ))}
          </div>
        </div>

        {/* Search + Account + Sort + More Filters toggle */}
        <div className="flex flex-col sm:flex-row gap-2">
          <div className="relative flex-1">
            <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              placeholder="Search merchant, ref ID, notes..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="w-full pl-10 pr-4 py-2.5 rounded-full bg-white border border-slate-200 text-sm font-medium focus:outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100 text-slate-900 min-h-[44px]"
            />
          </div>

          <div className="flex items-center gap-2 flex-wrap">
            <div className="relative flex-1 sm:flex-none">
              <select
                value={selectedAccountId}
                onChange={(e) => setSelectedAccountId(e.target.value)}
                aria-label="Filter by Bank Account"
                className="w-full appearance-none pl-3 pr-8 py-2.5 rounded-full bg-white border border-slate-200 text-xs font-semibold text-slate-700 focus:outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100 min-h-[44px] cursor-pointer"
              >
                <option value="ALL">All Accounts</option>
                {accounts.map((acc) => (
                  <option key={acc.id} value={acc.id}>
                    {acc.bank_name} ({acc.account_number_mask})
                  </option>
                ))}
              </select>
              <ChevronDown className="w-3.5 h-3.5 text-slate-400 absolute right-2.5 top-1/2 -translate-y-1/2 pointer-events-none" />
            </div>

            <div className="relative flex-1 sm:flex-none">
              <select
                value={sortBy}
                onChange={(e) => setSortBy(e.target.value as SortKey)}
                aria-label="Sort transactions"
                className="w-full appearance-none pl-3 pr-8 py-2.5 rounded-full bg-white border border-slate-200 text-xs font-semibold text-slate-700 focus:outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100 min-h-[44px] cursor-pointer"
              >
                <option value="date_desc">Sort: Newest</option>
                <option value="date_asc">Sort: Oldest</option>
                <option value="amount_desc">Amount: High–Low</option>
                <option value="amount_asc">Amount: Low–High</option>
              </select>
              <ChevronDown className="w-3.5 h-3.5 text-slate-400 absolute right-2.5 top-1/2 -translate-y-1/2 pointer-events-none" />
            </div>

            <button
              type="button"
              onClick={() => setShowAdvancedFilters((v) => !v)}
              className={`inline-flex items-center gap-1.5 px-3.5 py-2.5 rounded-full text-xs font-semibold border transition-colors min-h-[44px] ${
                showAdvancedFilters || selectedCategoryId !== 'ALL' || dateFrom || dateTo
                  ? 'bg-blue-50 text-blue-700 border-blue-200'
                  : 'bg-white text-slate-700 border-slate-200 hover:bg-slate-50'
              }`}
            >
              <SlidersHorizontal className="w-3.5 h-3.5" />
              <span>Filters</span>
            </button>
          </div>
        </div>

        {/* Category + Date Range Row */}
        {(showAdvancedFilters || selectedCategoryId !== 'ALL' || dateFrom || dateTo) && (
          <GlassCard className="p-3.5 grid grid-cols-1 sm:grid-cols-3 gap-3 bg-white/85">
            <div>
              <label className="block text-[10px] font-bold uppercase tracking-wider text-slate-400 mb-1">
                Category
              </label>
              <div className="relative">
                <select
                  value={selectedCategoryId}
                  onChange={(e) => setSelectedCategoryId(e.target.value)}
                  className="w-full appearance-none pl-3 pr-8 py-2 rounded-xl bg-white border border-slate-200 text-xs font-semibold text-slate-700 min-h-[40px]"
                >
                  <option value="ALL">All Categories</option>
                  {categories.map((cat) => (
                    <option key={cat.id} value={cat.id}>
                      {cat.name}
                    </option>
                  ))}
                </select>
                <ChevronDown className="w-3.5 h-3.5 text-slate-400 absolute right-2.5 top-1/2 -translate-y-1/2 pointer-events-none" />
              </div>
            </div>

            <div>
              <label className="block text-[10px] font-bold uppercase tracking-wider text-slate-400 mb-1">
                Date From
              </label>
              <input
                type="date"
                value={dateFrom}
                onChange={(e) => setDateFrom(e.target.value)}
                className="w-full px-3 py-2 rounded-xl bg-white border border-slate-200 text-xs font-semibold text-slate-700 min-h-[40px]"
              />
            </div>

            <div>
              <label className="block text-[10px] font-bold uppercase tracking-wider text-slate-400 mb-1">
                Date To
              </label>
              <input
                type="date"
                value={dateTo}
                onChange={(e) => setDateTo(e.target.value)}
                className="w-full px-3 py-2 rounded-xl bg-white border border-slate-200 text-xs font-semibold text-slate-700 min-h-[40px]"
              />
            </div>
          </GlassCard>
        )}

        {/* Transaction List */}
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
              onClick={resetAllFilters}
              className="px-4 py-2 text-xs font-semibold rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 transition-all"
            >
              Reset Filters
            </button>
          </GlassCard>
        ) : isDateSorted ? (
          <div className="space-y-4">
            {groups.map((group) => (
              <div key={group.label}>
                <div className="sticky top-16 z-10 py-1.5 mb-1">
                  <span className="text-[11px] font-bold text-slate-500 uppercase tracking-wider bg-[#F7F8FA]/90 backdrop-blur-sm px-2 py-0.5 rounded-full">
                    {group.label}
                  </span>
                </div>

                <GlassCard className="overflow-hidden divide-y divide-slate-100/80">
                  {group.items.map(renderTransactionRow)}
                </GlassCard>
              </div>
            ))}

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
        ) : (
          <div className="space-y-4">
            <GlassCard className="overflow-hidden divide-y divide-slate-100/80">
              {transactions.map(renderTransactionRow)}
            </GlassCard>
            {hasMore && (
              <button
                onClick={handleLoadMore}
                disabled={loading}
                className="w-full py-3 rounded-xl bg-white border border-slate-200 text-xs font-semibold text-slate-600 hover:bg-slate-50 hover:border-slate-300 transition-all active:scale-99 disabled:opacity-50 flex items-center justify-center gap-2"
              >
                {loading ? 'Loading...' : 'Load 50 more'}
              </button>
            )}
          </div>
        )}
      </div>

      <TransactionDetailModal
        transaction={selectedTx}
        onClose={() => setSelectedTx(null)}
        onUpdate={() => void fetchTransactions(true)}
      />

      <AddTransactionModal
        isOpen={isAddOpen}
        onClose={() => setIsAddOpen(false)}
        onSuccess={() => void fetchTransactions(true)}
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
