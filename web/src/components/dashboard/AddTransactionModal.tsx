'use client';

import React, { useState } from 'react';
import { X, Plus, Wallet, ArrowDownLeft, ArrowUpRight } from 'lucide-react';
import { GlassCard } from '@/components/ui/GlassCard';

interface AddTransactionModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

export function AddTransactionModal({ isOpen, onClose, onSuccess }: AddTransactionModalProps) {
  const [amount, setAmount] = useState('');
  const [direction, setDirection] = useState<'DEBIT' | 'CREDIT'>('DEBIT');
  const [merchant, setMerchant] = useState('');
  const [bankName, setBankName] = useState('Cash Wallet');
  const [categoryName, setCategoryName] = useState('Food & Dining');
  const [paymentMode, setPaymentMode] = useState('CASH');
  const [isSubmitting, setIsSubmitting] = useState(false);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!amount || parseFloat(amount) <= 0) return;

    setIsSubmitting(true);
    try {
      const res = await fetch('/api/sync', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'x-monzi-secret': 'monzi_sync_sec_ts6_MlPJi62UEBhcgvYI6g_uI7wG_SE',
        },
        body: JSON.stringify({
          device_id: 'web-manual-entry',
          transactions: [
            {
              transaction_date: new Date().toISOString(),
              amount: parseFloat(amount),
              direction,
              bank_name: bankName,
              account_mask: 'CASH',
              merchant: merchant || 'Cash Spending',
              payment_mode: paymentMode,
              reference_id: `MANUAL_${Date.now()}`,
            },
          ],
        }),
      });

      if (res.ok) {
        onSuccess();
        onClose();
        setAmount('');
        setMerchant('');
      }
    } catch (err) {
      console.error('Failed to add manual transaction', err);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/40 backdrop-blur-sm animate-fadeIn">
      <GlassCard className="w-full max-w-md p-6 bg-white shadow-2xl rounded-2xl relative">
        <button
          onClick={onClose}
          className="absolute top-5 right-5 p-1 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-all"
        >
          <X className="w-5 h-5" />
        </button>

        <h3 className="text-lg font-bold text-slate-900 tracking-tight">Add Transaction</h3>
        <p className="text-xs text-slate-500 font-medium mb-5">
          Record cash spending, manual expenses or income
        </p>

        <form onSubmit={handleSubmit} className="space-y-4">
          {/* Direction Toggle */}
          <div className="grid grid-cols-2 gap-2 p-1 bg-slate-100 rounded-xl text-xs font-semibold">
            <button
              type="button"
              onClick={() => setDirection('DEBIT')}
              className={`flex items-center justify-center gap-1.5 py-2 rounded-lg transition-all ${
                direction === 'DEBIT'
                  ? 'bg-rose-600 text-white shadow-sm'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              <ArrowUpRight className="w-4 h-4" />
              <span>Expense / Spend</span>
            </button>
            <button
              type="button"
              onClick={() => setDirection('CREDIT')}
              className={`flex items-center justify-center gap-1.5 py-2 rounded-lg transition-all ${
                direction === 'CREDIT'
                  ? 'bg-emerald-600 text-white shadow-sm'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              <ArrowDownLeft className="w-4 h-4" />
              <span>Income / Received</span>
            </button>
          </div>

          {/* Amount Field */}
          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">
              Amount (₹ INR)
            </label>
            <div className="relative">
              <span className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 font-bold font-mono">
                ₹
              </span>
              <input
                type="number"
                step="0.01"
                required
                placeholder="0.00"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                className="w-full pl-8 pr-4 py-2.5 text-base font-bold font-mono rounded-xl bg-slate-50 border border-slate-200 focus:bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/20 text-slate-900"
              />
            </div>
          </div>

          {/* Merchant / Description */}
          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">
              Merchant / Note
            </label>
            <input
              type="text"
              required
              placeholder="e.g. Tea & Snacks, Auto Rickshaw, Grocery"
              value={merchant}
              onChange={(e) => setMerchant(e.target.value)}
              className="w-full px-3.5 py-2 text-xs rounded-xl bg-slate-50 border border-slate-200 focus:bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/20 text-slate-900 font-medium"
            />
          </div>

          {/* Account / Mode */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">
                Account / Source
              </label>
              <select
                value={bankName}
                onChange={(e) => setBankName(e.target.value)}
                className="w-full px-3 py-2 text-xs rounded-xl bg-slate-50 border border-slate-200 text-slate-800 font-medium focus:outline-none"
              >
                <option value="Cash Wallet">Cash Wallet</option>
                <option value="HDFC Bank">HDFC Bank</option>
                <option value="ICICI Bank">ICICI Bank</option>
                <option value="State Bank of India">State Bank of India</option>
              </select>
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">
                Payment Mode
              </label>
              <select
                value={paymentMode}
                onChange={(e) => setPaymentMode(e.target.value)}
                className="w-full px-3 py-2 text-xs rounded-xl bg-slate-50 border border-slate-200 text-slate-800 font-medium focus:outline-none"
              >
                <option value="CASH">Cash</option>
                <option value="UPI">UPI</option>
                <option value="CARD">Card</option>
                <option value="OTHER">Other</option>
              </select>
            </div>
          </div>

          <button
            type="submit"
            disabled={isSubmitting}
            className="w-full mt-2 py-2.5 px-4 rounded-xl text-xs font-semibold bg-blue-600 text-white shadow-md shadow-blue-500/20 hover:bg-blue-700 transition-all flex items-center justify-center gap-1.5 active:scale-95"
          >
            <Plus className="w-4 h-4" />
            <span>{isSubmitting ? 'Recording...' : 'Add Transaction'}</span>
          </button>
        </form>
      </GlassCard>
    </div>
  );
}
