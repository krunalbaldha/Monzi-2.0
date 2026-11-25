'use client';

import React, { useState, useEffect, useCallback } from 'react';
import {
  MessageSquare,
  X,
  Check,
  Sparkles,
  AlertTriangle,
  Layers,
  History,
  Trash2,
  Tag,
  Building2,
  ArrowUpRight,
  ArrowDownLeft,
  Loader2,
  FileText,
  RefreshCw,
  Smartphone,
  Inbox,
  Calendar,
} from 'lucide-react';
import { useSms } from '@/components/sms/SmsProvider';
import { inspectBankSms } from '@/lib/parser';
import type { ParsedTransaction, SmsProcessingLog, Transaction } from '@/lib/types';
import { useToast } from '@/components/ui/Toast';

interface SmsSyncModalProps {
  onSuccess?: () => void;
}

type ModalTab = 'paste' | 'bulk' | 'history' | 'logs';

interface BulkItem {
  raw: string;
  parsed: ParsedTransaction;
  isDuplicateInBatch: boolean;
}

export function SmsSyncModal({ onSuccess }: SmsSyncModalProps) {
  const {
    isSyncModalOpen,
    closeSyncModal,
    isListening,
    isAndroidNative,
    isScanningInbox,
    scanAndroidInbox,
    openNativeSettings,
    processSmsText,
    lastSyncTime,
    autoSyncCount,
  } = useSms();
  const [activeTab, setActiveTab] = useState<ModalTab>('paste');

  // Tab 1: Paste SMS
  const [smsInput, setSmsInput] = useState('');
  const [parsedResult, setParsedResult] = useState<ParsedTransaction | null>(null);
  const [parseError, setParseError] = useState<string | null>(null);
  const [isSubmittingSingle, setIsSubmittingSingle] = useState(false);

  // Tab 2: Bulk Paste
  const [bulkInput, setBulkInput] = useState('');
  const [bulkItems, setBulkItems] = useState<BulkItem[]>([]);
  const [bulkDuplicateCount, setBulkDuplicateCount] = useState(0);
  const [bulkInvalidCount, setBulkInvalidCount] = useState(0);
  const [isSubmittingBulk, setIsSubmittingBulk] = useState(false);

  // Tab 3: History
  const [historyTxns, setHistoryTxns] = useState<Transaction[]>([]);
  const [isLoadingHistory, setIsLoadingHistory] = useState(false);

  // Tab 4: Processing Logs
  const [logs, setLogs] = useState<SmsProcessingLog[]>([]);
  const [isLoadingLogs, setIsLoadingLogs] = useState(false);
  const [logsMigrationPending, setLogsMigrationPending] = useState(false);

  const toast = useToast();

  // Tab 1: 400ms debounced strict inspection
  useEffect(() => {
    if (!smsInput.trim()) {
      setParsedResult(null);
      setParseError(null);
      return;
    }

    const timer = setTimeout(() => {
      const inspection = inspectBankSms(smsInput);
      if (inspection.parsed) {
        setParsedResult(inspection.parsed);
        setParseError(null);
      } else {
        setParsedResult(null);
        setParseError(
          inspection.reason ||
            'No standard financial transaction detected. Ensure the SMS contains amount and credit/debit details.'
        );
      }
    }, 400);

    return () => clearTimeout(timer);
  }, [smsInput]);

  // Tab 2: Parse bulk messages separated by blank lines
  useEffect(() => {
    if (!bulkInput.trim()) {
      setBulkItems([]);
      setBulkDuplicateCount(0);
      setBulkInvalidCount(0);
      return;
    }

    const blocks = bulkInput
      .split(/\r?\n\s*\r?\n/)
      .map((b) => b.trim())
      .filter(Boolean);

    const seenSignatures = new Set<string>();
    const items: BulkItem[] = [];
    let dups = 0;
    let invalid = 0;

    for (const block of blocks) {
      const inspection = inspectBankSms(block);
      if (!inspection.parsed) {
        invalid++;
        continue;
      }

      const parsed = inspection.parsed;
      const day = (parsed.transaction_date || '').slice(0, 10);
      const sig = parsed.reference_id
        ? `${parsed.reference_id.toLowerCase()}|${parsed.bank_name.toLowerCase()}|${parsed.amount.toFixed(2)}|${parsed.direction}|${day}`
        : `${parsed.amount.toFixed(2)}|${parsed.direction}|${parsed.account_mask}|${(parsed.merchant || '').toLowerCase().slice(0, 8)}|${day}`;

      const isDup = seenSignatures.has(sig);
      if (isDup) {
        dups++;
      } else {
        seenSignatures.add(sig);
      }

      items.push({
        raw: block,
        parsed,
        isDuplicateInBatch: isDup,
      });
    }

    setBulkItems(items);
    setBulkDuplicateCount(dups);
    setBulkInvalidCount(invalid);
  }, [bulkInput]);

  const fetchHistory = useCallback(async () => {
    setIsLoadingHistory(true);
    try {
      const res = await fetch('/api/transactions?limit=20', { credentials: 'include' });
      if (res.ok) {
        const data = await res.json();
        setHistoryTxns(data.transactions || []);
      }
    } catch (err) {
      console.error('Failed to fetch SMS sync history:', err);
    } finally {
      setIsLoadingHistory(false);
    }
  }, []);

  const fetchLogs = useCallback(async () => {
    setIsLoadingLogs(true);
    try {
      const res = await fetch('/api/sms-logs?limit=50', { credentials: 'include' });
      if (res.ok) {
        const data = await res.json();
        setLogs(data.logs || []);
        setLogsMigrationPending(Boolean(data.migration_pending));
      }
    } catch (err) {
      console.error('Failed to fetch SMS processing logs:', err);
    } finally {
      setIsLoadingLogs(false);
    }
  }, []);

  useEffect(() => {
    if (!isSyncModalOpen) return;
    if (activeTab === 'history') {
      fetchHistory();
    } else if (activeTab === 'logs') {
      fetchLogs();
    }
  }, [isSyncModalOpen, activeTab, fetchHistory, fetchLogs]);

  if (!isSyncModalOpen) return null;

  const formatInr = (val: number) =>
    new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 2,
    }).format(val);

  const handleSaveSingle = async () => {
    if (!smsInput.trim() || !parsedResult) return;

    setIsSubmittingSingle(true);
    try {
      const saved = await processSmsText(smsInput);
      if (saved) {
        if (saved.is_duplicate) {
          toast.info('Transaction already synced (duplicate skipped).');
        } else {
          toast.success(`Saved ${formatInr(saved.amount)} (${saved.bank_name})`);
        }
        setSmsInput('');
        setParsedResult(null);
        setParseError(null);
        if (onSuccess) onSuccess();
        closeSyncModal();
      } else {
        toast.error('Failed to save transaction');
      }
    } catch (err: any) {
      toast.error(err.message || 'Error saving transaction');
    } finally {
      setIsSubmittingSingle(false);
    }
  };

  const handleClearSingle = () => {
    setSmsInput('');
    setParsedResult(null);
    setParseError(null);
  };

  const handleSaveBulk = async () => {
    const validItems = bulkItems.filter((item) => !item.isDuplicateInBatch);
    if (validItems.length === 0) return;

    setIsSubmittingBulk(true);
    try {
      const nowIso = new Date().toISOString();
      const res = await fetch('/api/sync', {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          device_id: isAndroidNative ? 'android-webview' : 'pwa-sms-receiver',
          transactions: validItems.map((item) => ({
            transaction_date: item.parsed.transaction_date || nowIso,
            amount: item.parsed.amount,
            direction: item.parsed.direction,
            transaction_type: item.parsed.transaction_type,
            transaction_category: item.parsed.transaction_category,
            bank_name: item.parsed.bank_name,
            account_mask: item.parsed.account_mask,
            merchant: item.parsed.merchant || 'Bank Transaction',
            payment_mode: item.parsed.payment_mode || 'UPI',
            reference_id: item.parsed.reference_id,
            parsed_available_balance: item.parsed.parsed_available_balance,
            is_self_transfer: item.parsed.is_self_transfer,
            is_refund: item.parsed.is_refund,
            raw_body: item.raw,
          })),
        }),
      });

      const data = await res.json();
      if (!res.ok) {
        throw new Error(data.error || 'Bulk sync failed');
      }

      const synced = data.processed_count || 0;
      const serverDups = (data.duplicate_count || 0) + bulkDuplicateCount;
      toast.success(
        `Synced ${synced} transaction${synced === 1 ? '' : 's'}${
          serverDups > 0 ? `, ${serverDups} duplicate(s) skipped` : ''
        }`
      );
      if (synced > 0 && typeof window !== 'undefined') {
        window.dispatchEvent(new CustomEvent('monzi:sms-synced', { detail: { internal: false } }));
      }
      setBulkInput('');
      setBulkItems([]);
      if (onSuccess) onSuccess();
      setActiveTab('history');
    } catch (err: any) {
      toast.error(err.message || 'Bulk sync failed');
    } finally {
      setIsSubmittingBulk(false);
    }
  };

  const uniqueBulkCount = bulkItems.length - bulkDuplicateCount;

  const statusBadgeClass = (status: string) => {
    switch (status) {
      case 'PROCESSED':
        return 'bg-emerald-100 text-emerald-800';
      case 'DUPLICATE':
        return 'bg-amber-100 text-amber-800';
      case 'FAILED':
        return 'bg-rose-100 text-rose-800';
      default:
        return 'bg-slate-200 text-slate-700';
    }
  };

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto bg-slate-900/50 backdrop-blur-md p-3 sm:p-6 flex items-center justify-center min-h-screen animate-fade-in">
      <div className="fixed inset-0 -z-10" onClick={closeSyncModal} aria-hidden="true" />

      <div className="relative w-full max-w-xl bg-white rounded-2xl sm:rounded-3xl p-5 sm:p-6 shadow-2xl border border-slate-100 my-auto text-left animate-slide-up space-y-4">
        {/* Header */}
        <div className="flex items-center justify-between pb-3 border-b border-slate-100">
          <div className="flex items-center gap-2.5">
            <div className="w-10 h-10 rounded-2xl bg-blue-50 text-blue-600 flex items-center justify-center">
              <MessageSquare className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-base font-bold text-slate-900">SMS Sync Center</h3>
              <p className="text-[11px] text-slate-500 font-medium">
                {isAndroidNative
                  ? isListening
                    ? 'Android Background SMS Bridge Active'
                    : 'Monzi Android App'
                  : isListening
                  ? 'Browser WebOTP active'
                  : 'On-device bank SMS parser'}
                {autoSyncCount > 0 ? ` • ${autoSyncCount} synced this session` : ''}
                {lastSyncTime
                  ? ` • Last: ${lastSyncTime.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`
                  : ''}
              </p>
            </div>
          </div>
          <button
            onClick={closeSyncModal}
            className="p-1.5 rounded-full text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition-all"
            aria-label="Close"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Native Android Inbox Scan Bar OR Web Browser Info */}
        {isAndroidNative ? (
          <div className="p-3 rounded-2xl bg-emerald-50/80 border border-emerald-200/70 flex items-center justify-between gap-2">
            <div className="min-w-0">
              <p className="text-xs font-bold text-emerald-900">Android SMS Bridge Connected</p>
              <p className="text-[11px] text-emerald-700">
                Scan your inbox for past bank SMS without uploading raw text.
              </p>
            </div>
            <div className="flex items-center gap-1.5 shrink-0">
              <button
                type="button"
                onClick={() => scanAndroidInbox(500)}
                disabled={isScanningInbox}
                className="px-3 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-semibold flex items-center gap-1.5 shadow-sm disabled:opacity-60"
              >
                {isScanningInbox ? (
                  <Loader2 className="w-3.5 h-3.5 animate-spin" />
                ) : (
                  <Inbox className="w-3.5 h-3.5" />
                )}
                <span>{isScanningInbox ? 'Scanning...' : 'Scan Inbox History'}</span>
              </button>
              <button
                type="button"
                onClick={openNativeSettings}
                className="px-2.5 py-2 rounded-xl bg-white border border-emerald-200 text-emerald-800 text-xs font-semibold hover:bg-emerald-100/50"
              >
                Bridge
              </button>
            </div>
          </div>
        ) : (
          <div className="p-3 rounded-2xl bg-slate-50 border border-slate-200/70 flex items-start gap-2.5">
            <Smartphone className="w-4 h-4 text-blue-600 shrink-0 mt-0.5" />
            <p className="text-[11px] text-slate-600 leading-snug">
              Automatic background SMS reading requires the <span className="font-semibold text-slate-800">Monzi Android App</span>. In your browser, paste single or bulk bank SMS below — raw SMS text is never stored.
            </p>
          </div>
        )}

        {/* 4-Tab Switcher */}
        <div className="grid grid-cols-4 gap-1 p-1 bg-slate-100 rounded-xl">
          <button
            type="button"
            onClick={() => setActiveTab('paste')}
            className={`py-2 px-2 rounded-lg text-xs font-semibold flex items-center justify-center gap-1 transition-all ${
              activeTab === 'paste'
                ? 'bg-white text-blue-600 shadow-sm'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            <MessageSquare className="w-3.5 h-3.5" />
            <span>Paste</span>
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('bulk')}
            className={`py-2 px-2 rounded-lg text-xs font-semibold flex items-center justify-center gap-1 transition-all ${
              activeTab === 'bulk'
                ? 'bg-white text-blue-600 shadow-sm'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            <Layers className="w-3.5 h-3.5" />
            <span>Bulk</span>
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('history')}
            className={`py-2 px-2 rounded-lg text-xs font-semibold flex items-center justify-center gap-1 transition-all ${
              activeTab === 'history'
                ? 'bg-white text-blue-600 shadow-sm'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            <History className="w-3.5 h-3.5" />
            <span>History</span>
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('logs')}
            className={`py-2 px-2 rounded-lg text-xs font-semibold flex items-center justify-center gap-1 transition-all ${
              activeTab === 'logs'
                ? 'bg-white text-blue-600 shadow-sm'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            <FileText className="w-3.5 h-3.5" />
            <span>Logs</span>
          </button>
        </div>

        {/* TAB 1: Paste SMS */}
        {activeTab === 'paste' && (
          <div className="space-y-4">
            <div>
              <textarea
                rows={5}
                value={smsInput}
                onChange={(e) => setSmsInput(e.target.value)}
                placeholder="Paste your bank SMS here..."
                className="w-full p-3.5 text-xs rounded-xl bg-slate-50 border border-slate-200 focus:bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/20 text-slate-900 font-mono leading-relaxed"
                autoFocus
              />
            </div>

            {parsedResult && (
              <div className="p-4 rounded-2xl bg-blue-50/70 border border-blue-200/80 space-y-3 animate-fade-in">
                <div className="flex items-center justify-between">
                  <span className="text-[11px] font-bold text-blue-900 uppercase tracking-wider flex items-center gap-1.5">
                    <Sparkles className="w-3.5 h-3.5 text-blue-600" />
                    <span>Parsed Preview</span>
                  </span>
                  <div className="flex items-center gap-1.5">
                    {parsedResult.is_refund && (
                      <span className="px-2 py-0.5 rounded-full font-bold text-[10px] bg-indigo-100 text-indigo-800">
                        REFUND
                      </span>
                    )}
                    {parsedResult.is_self_transfer && (
                      <span className="px-2 py-0.5 rounded-full font-bold text-[10px] bg-slate-200 text-slate-700">
                        SELF TRANSFER
                      </span>
                    )}
                    <span
                      className={`px-2.5 py-0.5 rounded-full font-bold text-[10px] ${
                        parsedResult.direction === 'CREDIT'
                          ? 'bg-emerald-100 text-emerald-800'
                          : 'bg-rose-100 text-rose-800'
                      }`}
                    >
                      {parsedResult.direction}
                    </span>
                  </div>
                </div>

                {/* Amount + Merchant */}
                <div className="flex items-baseline justify-between bg-white p-3 rounded-xl border border-blue-100">
                  <div>
                    <span className="text-[10px] text-slate-400 font-medium block">Merchant</span>
                    <span className="text-sm font-bold text-slate-900">
                      {parsedResult.merchant || 'Bank Transaction'}
                    </span>
                  </div>
                  <div className="text-right">
                    <span className="text-[10px] text-slate-400 font-medium block">Amount</span>
                    <span
                      className={`text-lg font-mono font-bold ${
                        parsedResult.direction === 'CREDIT' ? 'text-emerald-600' : 'text-slate-900'
                      }`}
                    >
                      {parsedResult.direction === 'CREDIT' ? '+' : '-'}
                      {formatInr(parsedResult.amount)}
                    </span>
                  </div>
                </div>

                {/* Account + Category */}
                <div className="grid grid-cols-2 gap-2 text-xs">
                  <div className="p-2.5 rounded-xl bg-white border border-blue-100 flex items-center gap-2">
                    <Building2 className="w-4 h-4 text-slate-400 shrink-0" />
                    <div className="min-w-0">
                      <span className="text-[10px] text-slate-400 block font-medium">Account</span>
                      <span className="font-semibold text-slate-900 truncate block">
                        {parsedResult.bank_name} ({parsedResult.account_mask})
                      </span>
                    </div>
                  </div>

                  <div className="p-2.5 rounded-xl bg-white border border-blue-100 flex items-center gap-2">
                    <Tag className="w-4 h-4 text-blue-500 shrink-0" />
                    <div className="min-w-0">
                      <span className="text-[10px] text-slate-400 block font-medium">Category</span>
                      <span className="font-semibold text-blue-700 truncate block">
                        {parsedResult.transaction_category || 'Other'}
                      </span>
                    </div>
                  </div>
                </div>

                <div className="flex items-center justify-between text-[10px] text-slate-500 font-mono">
                  {parsedResult.transaction_date && (
                    <span className="flex items-center gap-1">
                      <Calendar className="w-3 h-3" />
                      {new Date(parsedResult.transaction_date).toLocaleDateString('en-IN', {
                        day: '2-digit',
                        month: 'short',
                        year: 'numeric',
                      })}
                    </span>
                  )}
                  {parsedResult.reference_id && (
                    <span>
                      Ref: <span className="font-bold text-slate-700">{parsedResult.reference_id}</span>
                    </span>
                  )}
                </div>
              </div>
            )}

            {parseError && (
              <div className="p-3.5 rounded-xl bg-amber-50 border border-amber-200/80 text-xs text-amber-800 flex items-start gap-2 animate-fade-in">
                <AlertTriangle className="w-4 h-4 text-amber-600 shrink-0 mt-0.5" />
                <p className="leading-snug">{parseError}</p>
              </div>
            )}

            <div className="flex items-center gap-2 pt-1">
              <button
                type="button"
                onClick={handleClearSingle}
                disabled={!smsInput}
                className="px-4 py-3 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 font-semibold text-xs transition-all flex items-center gap-1.5 disabled:opacity-40"
              >
                <Trash2 className="w-3.5 h-3.5" />
                <span>Clear</span>
              </button>
              <button
                type="button"
                onClick={handleSaveSingle}
                disabled={!parsedResult || isSubmittingSingle}
                className="flex-1 py-3 rounded-xl bg-blue-600 hover:bg-blue-700 text-white font-semibold text-xs shadow-md shadow-blue-500/25 active:scale-[0.99] transition-all flex items-center justify-center gap-1.5 disabled:opacity-50"
              >
                {isSubmittingSingle ? (
                  <Loader2 className="w-4 h-4 animate-spin" />
                ) : (
                  <Check className="w-4 h-4" />
                )}
                <span>{isSubmittingSingle ? 'Saving...' : 'Save Transaction'}</span>
              </button>
            </div>
          </div>
        )}

        {/* TAB 2: Bulk Paste */}
        {activeTab === 'bulk' && (
          <div className="space-y-4">
            <div>
              <label className="block text-[11px] font-semibold text-slate-600 mb-1.5">
                Paste multiple bank SMS messages separated by a blank line
              </label>
              <textarea
                rows={6}
                value={bulkInput}
                onChange={(e) => setBulkInput(e.target.value)}
                placeholder={'HDFC Bank: Rs.450.00 debited from A/C XX1234 to Swiggy on 24-11-26.\n\nICICI Bank: INR 1,299.00 spent on Card XX5678 at Amazon.'}
                className="w-full p-3.5 text-xs rounded-xl bg-slate-50 border border-slate-200 focus:bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/20 text-slate-900 font-mono leading-relaxed"
              />
            </div>

            {bulkInput.trim() && (
              <div className="p-3.5 rounded-2xl bg-slate-50 border border-slate-200/80 space-y-2.5">
                <div className="flex items-center justify-between text-xs">
                  <span className="font-bold text-slate-800">
                    Found {uniqueBulkCount} transaction{uniqueBulkCount === 1 ? '' : 's'}, {bulkDuplicateCount} duplicate{bulkDuplicateCount === 1 ? '' : 's'}
                  </span>
                  {bulkInvalidCount > 0 && (
                    <span className="text-[11px] text-amber-700 font-medium">
                      {bulkInvalidCount} ignored
                    </span>
                  )}
                </div>

                {bulkItems.length > 0 && (
                  <div className="max-h-44 overflow-y-auto space-y-1.5 pr-1">
                    {bulkItems.map((item, idx) => (
                      <div
                        key={idx}
                        className={`flex items-center justify-between px-3 py-2 rounded-xl border text-xs ${
                          item.isDuplicateInBatch
                            ? 'bg-amber-50/70 border-amber-200 opacity-70'
                            : 'bg-white border-slate-200/70'
                        }`}
                      >
                        <div className="min-w-0 pr-2">
                          <p className="font-semibold text-slate-900 truncate">
                            {item.parsed.merchant || 'Bank Transaction'}
                            {item.isDuplicateInBatch && (
                              <span className="ml-1.5 text-[10px] font-bold text-amber-700">(Duplicate)</span>
                            )}
                          </p>
                          <p className="text-[10px] text-slate-500">
                            {item.parsed.bank_name} • {item.parsed.transaction_category || 'Other'}
                          </p>
                        </div>
                        <span
                          className={`font-mono font-bold shrink-0 ${
                            item.parsed.direction === 'CREDIT' ? 'text-emerald-600' : 'text-slate-900'
                          }`}
                        >
                          {item.parsed.direction === 'CREDIT' ? '+' : '-'}
                          {formatInr(item.parsed.amount)}
                        </span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            <button
              type="button"
              onClick={handleSaveBulk}
              disabled={uniqueBulkCount === 0 || isSubmittingBulk}
              className="w-full py-3 rounded-xl bg-blue-600 hover:bg-blue-700 text-white font-semibold text-xs shadow-md shadow-blue-500/25 active:scale-[0.99] transition-all flex items-center justify-center gap-1.5 disabled:opacity-50"
            >
              {isSubmittingBulk ? (
                <Loader2 className="w-4 h-4 animate-spin" />
              ) : (
                <Check className="w-4 h-4" />
              )}
              <span>
                {isSubmittingBulk ? 'Saving All...' : `Save All (${uniqueBulkCount})`}
              </span>
            </button>
          </div>
        )}

        {/* TAB 3: History */}
        {activeTab === 'history' && (
          <div className="space-y-3">
            {isLoadingHistory ? (
              <div className="py-10 flex flex-col items-center justify-center text-slate-400 gap-2">
                <Loader2 className="w-6 h-6 animate-spin text-blue-500" />
                <span className="text-xs">Loading recent transactions...</span>
              </div>
            ) : historyTxns.length === 0 ? (
              <div className="py-10 text-center text-xs text-slate-500 bg-slate-50 rounded-2xl border border-slate-100">
                No synced transactions found yet.
              </div>
            ) : (
              <div className="max-h-80 overflow-y-auto space-y-2 pr-1">
                {historyTxns.map((tx) => (
                  <div
                    key={tx.id}
                    className="flex items-center justify-between p-3 rounded-xl bg-slate-50/80 border border-slate-100 hover:bg-slate-100/70 transition-colors"
                  >
                    <div className="flex items-center gap-3 min-w-0">
                      <div
                        className={`w-8 h-8 rounded-full flex items-center justify-center shrink-0 ${
                          tx.direction === 'CREDIT'
                            ? 'bg-emerald-100 text-emerald-700'
                            : 'bg-rose-100 text-rose-700'
                        }`}
                      >
                        {tx.direction === 'CREDIT' ? (
                          <ArrowDownLeft className="w-4 h-4" />
                        ) : (
                          <ArrowUpRight className="w-4 h-4" />
                        )}
                      </div>
                      <div className="min-w-0">
                        <p className="text-xs font-bold text-slate-900 truncate">
                          {tx.merchant || 'Bank Transaction'}
                        </p>
                        <p className="text-[11px] text-slate-500">
                          {new Date(tx.transaction_date).toLocaleDateString('en-IN', {
                            day: '2-digit',
                            month: 'short',
                            year: 'numeric',
                          })}
                          {tx.categories?.name ? ` • ${tx.categories.name}` : ''}
                        </p>
                      </div>
                    </div>
                    <span
                      className={`text-xs font-mono font-bold shrink-0 ml-2 ${
                        tx.direction === 'CREDIT' ? 'text-emerald-600' : 'text-slate-900'
                      }`}
                    >
                      {tx.direction === 'CREDIT' ? '+' : '-'}
                      {formatInr(Number(tx.amount))}
                    </span>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* TAB 4: Processing Logs */}
        {activeTab === 'logs' && (
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <p className="text-[11px] text-slate-500">
                Privacy-safe audit trail (raw SMS text is never stored).
              </p>
              <button
                type="button"
                onClick={fetchLogs}
                disabled={isLoadingLogs}
                className="text-xs font-semibold text-blue-600 hover:text-blue-700 flex items-center gap-1"
              >
                <RefreshCw className={`w-3.5 h-3.5 ${isLoadingLogs ? 'animate-spin' : ''}`} />
                <span>Refresh</span>
              </button>
            </div>

            {logsMigrationPending ? (
              <div className="p-4 rounded-2xl bg-amber-50 border border-amber-200 text-xs text-amber-900">
                Processing logs table is pending database migration (`002_auth_rls_and_logs.sql`).
              </div>
            ) : isLoadingLogs ? (
              <div className="py-10 flex flex-col items-center justify-center text-slate-400 gap-2">
                <Loader2 className="w-6 h-6 animate-spin text-blue-500" />
                <span className="text-xs">Loading processing logs...</span>
              </div>
            ) : logs.length === 0 ? (
              <div className="py-10 text-center text-xs text-slate-500 bg-slate-50 rounded-2xl border border-slate-100">
                No SMS processing logs recorded yet.
              </div>
            ) : (
              <div className="max-h-80 overflow-y-auto space-y-2 pr-1">
                {logs.map((log) => (
                  <div
                    key={log.id}
                    className="p-3 rounded-xl bg-slate-50/80 border border-slate-100 space-y-1"
                  >
                    <div className="flex items-center justify-between gap-2">
                      <div className="flex items-center gap-2 min-w-0">
                        <span
                          className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${statusBadgeClass(
                            log.status
                          )}`}
                        >
                          {log.status}
                        </span>
                        <span className="text-xs font-semibold text-slate-900 truncate">
                          {log.extracted_bank || log.sender || 'SMS Message'}
                        </span>
                      </div>
                      {log.extracted_amount !== null && log.extracted_amount !== undefined && (
                        <span className="text-xs font-mono font-bold text-slate-800 shrink-0">
                          {formatInr(Number(log.extracted_amount))}
                        </span>
                      )}
                    </div>
                    {log.reason && (
                      <p className="text-[11px] text-slate-600 leading-snug">{log.reason}</p>
                    )}
                    <div className="flex items-center justify-between text-[10px] text-slate-400 font-mono">
                      <span>
                        {new Date(log.created_at).toLocaleString('en-IN', {
                          day: '2-digit',
                          month: 'short',
                          hour: '2-digit',
                          minute: '2-digit',
                        })}
                      </span>
                      {log.reference_id && <span>Ref: {log.reference_id}</span>}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
