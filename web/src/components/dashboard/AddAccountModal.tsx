'use client';

import React, { useState, useEffect } from 'react';
import { X, CreditCard } from 'lucide-react';
import { useToast } from '@/components/ui/Toast';

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
  const toast = useToast();

  useEffect(() => {
    if (isOpen) {
      document.body.style.overflow = 'hidden';
    } else {
      document.body.style.overflow = '';
    }
    return () => {
      document.body.style.overflow = '';
    };
  }, [isOpen]);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!bankName.trim()) {
      toast.error('Please enter the bank or institution name');
      return;
    }
    if (!accountMask.trim()) {
      toast.error('Please enter the last 4 digits (e.g. 4821)');
      return;
    }

    setIsSubmitting(true);
    try {
      const cleanMask = accountMask.trim().toUpperCase().startsWith('XX')
        ? accountMask.trim().toUpperCase()
        : `XX${accountMask.trim().toUpperCase().slice(-4)}`;

      const res = await fetch('/api/accounts', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          bank_name: bankName.trim(),
          account_number_mask: cleanMask,
          account_type: accountType,
          initial_balance: parseFloat(initialBalance) || 0,
        }),
      });

      if (!res.ok) {
        const err = await res.json();
        throw new Error(err.error || 'Failed to add account');
      }

      toast.success(`${bankName} (${cleanMask}) added successfully`);
      onSuccess();
      onClose();
      // Reset
      setBankName('');
      setAccountMask('');
      setInitialBalance('');
    } catch (err: any) {
      toast.error(err.message || 'Error adding account');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto bg-slate-900/60 backdrop-blur-md p-3 sm:p-6 flex items-center justify-center min-h-screen animate-fade-in">
      {/* Click backdrop to dismiss */}
      <div className="fixed inset-0 -z-10" onClick={onClose} aria-hidden="true" />

      {/* Solid White Modal Dialog Box */}
      <div className="relative w-full max-w-md bg-white rounded-2xl sm:rounded-3xl p-6 sm:p-7 shadow-2xl border border-slate-100 my-auto text-left animate-slide-up">
        <div className="flex items-center justify-between pb-3 mb-4 border-b border-slate-100">
          <div className="flex items-center gap-2.5">
            <div className="w-10 h-10 rounded-2xl bg-blue-50 text-blue-600 flex items-center justify-center">
              <CreditCard className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-base sm:text-lg font-bold text-slate-900 tracking-tight">Add Bank Account</h3>
              <p className="text-[11px] sm:text-xs text-slate-500 font-medium">Link a bank, card or wallet</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-full text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition-all"
            aria-label="Close modal"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-[11px] font-bold text-slate-700 uppercase tracking-wider mb-1.5">
              Bank / Institution Name
            </label>
            <input
              type="text"
              required
              placeholder="e.g. HDFC Bank, ICICI Bank, SBI, Zerodha, Cash"
              value={bankName}
              onChange={(e) => setBankName(e.target.value)}
              className="w-full px-3.5 py-2.5 text-xs sm:text-sm rounded-xl bg-slate-50 border border-slate-200 focus:bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/20 text-slate-900 font-medium"
              autoFocus
            />
          </div>

          <div>
            <label className="block text-[11px] font-bold text-slate-700 uppercase tracking-wider mb-1.5">
              Account Number Mask (Last 4 Digits)
            </label>
            <input
              type="text"
              required
              placeholder="e.g. 4821 or XX4821"
              value={accountMask}
              onChange={(e) => setAccountMask(e.target.value)}
              maxLength={8}
              className="w-full px-3.5 py-2.5 text-xs sm:text-sm font-mono rounded-xl bg-slate-50 border border-slate-200 focus:bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/20 text-slate-900 font-medium"
            />
          </div>

          <div>
            <label className="block text-[11px] font-bold text-slate-700 uppercase tracking-wider mb-1.5">
              Account Type
            </label>
            <select
              value={accountType}
              onChange={(e) => setAccountType(e.target.value as any)}
              className="w-full px-3 py-2.5 text-xs sm:text-sm rounded-xl bg-slate-50 border border-slate-200 focus:bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/20 text-slate-900 font-medium"
            >
              <option value="SAVINGS">Savings Account</option>
              <option value="CURRENT">Current Account</option>
              <option value="CREDIT_CARD">Credit Card</option>
              <option value="WALLET">Digital Wallet / Cash</option>
            </select>
          </div>

          <div>
            <label className="block text-[11px] font-bold text-slate-700 uppercase tracking-wider mb-1.5">
              Opening / Current Balance (₹ INR)
            </label>
            <div className="relative">
              <span className="absolute left-3.5 top-1/2 -translate-y-1/2 text-sm font-bold text-slate-400">
                ₹
              </span>
              <input
                type="number"
                step="0.01"
                placeholder="0.00"
                value={initialBalance}
                onChange={(e) => setInitialBalance(e.target.value)}
                className="w-full pl-8 pr-4 py-2.5 text-xs sm:text-sm font-mono font-bold rounded-xl bg-slate-50 border border-slate-200 focus:bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/20 text-slate-900"
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={isSubmitting}
            className="w-full py-3.5 mt-2 rounded-xl text-xs sm:text-sm font-semibold bg-blue-600 text-white shadow-lg shadow-blue-500/25 hover:bg-blue-700 transition-all active:scale-98 disabled:opacity-50"
          >
            {isSubmitting ? 'Creating Account...' : 'Add Account'}
          </button>
        </form>
      </div>
    </div>
  );
}
