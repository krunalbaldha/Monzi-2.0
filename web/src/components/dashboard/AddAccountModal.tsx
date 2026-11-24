'use client';

import React, { useState } from 'react';
import { X, Plus, CreditCard } from 'lucide-react';
import { GlassCard } from '@/components/ui/GlassCard';

interface AddAccountModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

export function AddAccountModal({ isOpen, onClose, onSuccess }: AddAccountModalProps) {
  const [bankName, setBankName] = useState('');
  const [accountMask, setAccountMask] = useState('');
  const [accountType, setAccountType] = useState<'SAVINGS' | 'CURRENT' | 'CREDIT_CARD' | 'WALLET'>('SAVINGS');
  const [initialBalance, setInitialBalance] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!bankName || !accountMask) return;

    setIsSubmitting(true);
    try {
      const res = await fetch('/api/sync', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'x-monzi-secret': process.env.NEXT_PUBLIC_MONZI_SYNC_SECRET || 'monzi_personal_sync_secret',
        },
        body: JSON.stringify({
          device_id: 'web-manual-account',
          transactions: [
            {
              transaction_date: new Date().toISOString(),
              amount: 0.01,
              direction: 'CREDIT',
              bank_name: bankName.trim(),
              account_mask: accountMask.toUpperCase().startsWith('XX') ? accountMask.toUpperCase() : `XX${accountMask}`,
              merchant: 'Account Initial Setup',
              payment_mode: 'OTHER',
              reference_id: `SETUP_${Date.now()}`,
              parsed_available_balance: parseFloat(initialBalance) || 0,
            },
          ],
        }),
      });

      if (res.ok) {
        onSuccess();
        onClose();
        setBankName('');
        setAccountMask('');
        setInitialBalance('');
      }
    } catch (err) {
      console.error('Failed to create account', err);
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

        <div className="flex items-center gap-3 mb-4">
          <div className="w-10 h-10 rounded-2xl bg-blue-50 text-blue-600 flex items-center justify-center">
            <CreditCard className="w-5 h-5" />
          </div>
          <div>
            <h3 className="text-lg font-bold text-slate-900 tracking-tight">Add Bank Account</h3>
            <p className="text-xs text-slate-500 font-medium">Track a new bank, credit card, or cash wallet</p>
          </div>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">
              Bank / Institution Name
            </label>
            <input
              type="text"
              required
              placeholder="e.g. HDFC Bank, ICICI Bank, SBI, Cash Wallet"
              value={bankName}
              onChange={(e) => setBankName(e.target.value)}
              className="w-full px-3.5 py-2 text-xs rounded-xl bg-slate-50 border border-slate-200 focus:bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/20 text-slate-900 font-medium"
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">
                Account Mask / Last 4
              </label>
              <input
                type="text"
                required
                placeholder="e.g. XX8421 or 8421"
                value={accountMask}
                onChange={(e) => setAccountMask(e.target.value)}
                className="w-full px-3.5 py-2 text-xs rounded-xl bg-slate-50 border border-slate-200 focus:bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/20 text-slate-900 font-medium font-mono"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">
                Account Type
              </label>
              <select
                value={accountType}
                onChange={(e) => setAccountType(e.target.value as any)}
                className="w-full px-3 py-2 text-xs rounded-xl bg-slate-50 border border-slate-200 text-slate-800 font-medium focus:outline-none"
              >
                <option value="SAVINGS">Savings</option>
                <option value="CURRENT">Current</option>
                <option value="CREDIT_CARD">Credit Card</option>
                <option value="WALLET">Wallet</option>
              </select>
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">
              Opening / Current Balance (₹)
            </label>
            <input
              type="number"
              step="0.01"
              placeholder="0.00"
              value={initialBalance}
              onChange={(e) => setInitialBalance(e.target.value)}
              className="w-full px-3.5 py-2 text-xs font-mono font-bold rounded-xl bg-slate-50 border border-slate-200 focus:bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/20 text-slate-900"
            />
          </div>

          <button
            type="submit"
            disabled={isSubmitting}
            className="w-full mt-2 py-2.5 px-4 rounded-xl text-xs font-semibold bg-blue-600 text-white shadow-md shadow-blue-500/20 hover:bg-blue-700 transition-all flex items-center justify-center gap-1.5 active:scale-95"
          >
            <Plus className="w-4 h-4" />
            <span>{isSubmitting ? 'Creating...' : 'Create Account'}</span>
          </button>
        </form>
      </GlassCard>
    </div>
  );
}
