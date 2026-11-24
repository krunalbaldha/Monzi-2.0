'use client';

import React, { useState } from 'react';
import { 
  X, 
  ArrowUpRight, 
  ArrowDownLeft, 
  Copy, 
  Check, 
  Calendar, 
  CreditCard, 
  Hash, 
  ShieldCheck, 
  EyeOff,
  Clock
} from 'lucide-react';
import { GlassCard } from '@/components/ui/GlassCard';
import { Badge } from '@/components/ui/Badge';
import { Transaction } from '@/lib/types';

interface TransactionDetailModalProps {
  transaction: Transaction | null;
  onClose: () => void;
  onUpdate?: () => void;
}

export function TransactionDetailModal({
  transaction,
  onClose,
  onUpdate,
}: TransactionDetailModalProps) {
  const [copied, setCopied] = useState(false);

  if (!transaction) return null;

  const isCredit = transaction.direction === 'CREDIT';

  const formatInr = (val: number) => {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 2,
    }).format(val);
  };

  const formatDate = (dateStr: string) => {
    return new Intl.DateTimeFormat('en-IN', {
      weekday: 'short',
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    }).format(new Date(dateStr));
  };

  const copyRef = (text?: string) => {
    if (!text) return;
    navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-slate-900/40 backdrop-blur-sm animate-fade-in p-0 sm:p-4">
      {/* Background click to dismiss */}
      <div className="absolute inset-0" onClick={onClose} />

      {/* Slide-up Container on Mobile, Centered Modal on Desktop */}
      <div className="relative w-full max-w-lg bg-white rounded-t-[28px] sm:rounded-3xl shadow-2xl overflow-hidden z-10 animate-slide-up max-h-[92vh] flex flex-col">
        {/* iOS Pull Handle Bar */}
        <div className="w-full flex justify-center pt-3 pb-1 sm:hidden">
          <div className="w-10 h-1 rounded-full bg-slate-300" />
        </div>

        {/* Modal Header */}
        <div className="flex items-center justify-between px-6 pt-3 sm:pt-6 pb-2">
          <span className="text-[11px] font-bold text-slate-400 uppercase tracking-widest">
            Transaction Details
          </span>
          <button
            onClick={onClose}
            className="p-1.5 rounded-full text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition-all active:scale-90"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Amount & Merchant Hero Section */}
        <div className="px-6 py-4 text-center border-b border-slate-100 bg-slate-50/50">
          <div
            className={`w-14 h-14 rounded-2xl mx-auto mb-3 flex items-center justify-center shadow-sm ${
              isCredit
                ? 'bg-emerald-100/70 text-emerald-700'
                : 'bg-rose-100/70 text-rose-700'
            }`}
          >
            {isCredit ? (
              <ArrowDownLeft className="w-7 h-7 stroke-[2.5]" />
            ) : (
              <ArrowUpRight className="w-7 h-7 stroke-[2.5]" />
            )}
          </div>

          <h2
            className={`text-3xl font-extrabold font-mono tracking-tight ${
              isCredit ? 'text-emerald-600' : 'text-slate-900'
            }`}
          >
            {isCredit ? '+' : '-'} {formatInr(transaction.amount)}
          </h2>

          <p className="text-base font-bold text-slate-900 mt-1">
            {transaction.merchant || 'Unknown Merchant'}
          </p>

          <div className="flex items-center justify-center gap-2 mt-2">
            <Badge type={transaction.transaction_type} />
            <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-emerald-700 bg-emerald-50 px-2.5 py-0.5 rounded-full border border-emerald-200">
              <ShieldCheck className="w-3 h-3" /> Auto-Synced
            </span>
          </div>
        </div>

        {/* Detailed Metadata Grid */}
        <div className="p-6 space-y-3.5 overflow-y-auto flex-1 text-xs">
          {/* Account */}
          <div className="flex items-center justify-between p-3 rounded-xl bg-slate-50 border border-slate-100">
            <div className="flex items-center gap-2.5 text-slate-500 font-medium">
              <CreditCard className="w-4 h-4 text-blue-600" />
              <span>Bank Account</span>
            </div>
            <span className="font-bold text-slate-900">
              {transaction.accounts?.bank_name || 'Bank'} {transaction.accounts?.account_number_mask || ''}
            </span>
          </div>

          {/* Payment Mode */}
          <div className="flex items-center justify-between p-3 rounded-xl bg-slate-50 border border-slate-100">
            <div className="flex items-center gap-2.5 text-slate-500 font-medium">
              <span className="w-2 h-2 rounded-full bg-blue-500" />
              <span>Payment Mode</span>
            </div>
            <span className="font-bold font-mono text-slate-900">
              {transaction.payment_mode || 'UPI'}
            </span>
          </div>

          {/* Date & Time */}
          <div className="flex items-center justify-between p-3 rounded-xl bg-slate-50 border border-slate-100">
            <div className="flex items-center gap-2.5 text-slate-500 font-medium">
              <Clock className="w-4 h-4 text-indigo-600" />
              <span>Date & Time</span>
            </div>
            <span className="font-semibold text-slate-900">
              {formatDate(transaction.transaction_date)}
            </span>
          </div>

          {/* Reference Number (UTR/RRN) */}
          {transaction.reference_id && (
            <div className="flex items-center justify-between p-3 rounded-xl bg-slate-50 border border-slate-100">
              <div className="flex items-center gap-2.5 text-slate-500 font-medium">
                <Hash className="w-4 h-4 text-amber-600" />
                <span>Reference (UTR)</span>
              </div>
              <div className="flex items-center gap-1.5 font-mono font-bold text-slate-900">
                <span>{transaction.reference_id}</span>
                <button
                  onClick={() => copyRef(transaction.reference_id)}
                  className="p-1 rounded text-slate-400 hover:text-blue-600 active:scale-90"
                  title="Copy Reference"
                >
                  {copied ? <Check className="w-3.5 h-3.5 text-emerald-600" /> : <Copy className="w-3.5 h-3.5" />}
                </button>
              </div>
            </div>
          )}

          {/* Checkpointed Available Balance */}
          {transaction.parsed_available_balance !== undefined && (
            <div className="flex items-center justify-between p-3 rounded-xl bg-emerald-50/60 border border-emerald-100">
              <div className="flex items-center gap-2 text-emerald-800 font-medium">
                <span className="w-2 h-2 rounded-full bg-emerald-500" />
                <span>Balance After Transaction</span>
              </div>
              <span className="font-bold font-mono text-emerald-900 text-sm">
                {formatInr(transaction.parsed_available_balance)}
              </span>
            </div>
          )}
        </div>

        {/* Action Button */}
        <div className="p-4 border-t border-slate-100 bg-white">
          <button
            onClick={onClose}
            className="w-full py-3 rounded-xl font-semibold text-xs bg-slate-900 text-white shadow-md hover:bg-slate-800 transition-all active:scale-98"
          >
            Done
          </button>
        </div>
      </div>
    </div>
  );
}
