'use client';

import React, { useState, useEffect } from 'react';
import { X, CreditCard, CheckCircle2 } from 'lucide-react';
import { useToast } from '@/components/ui/Toast';
import { AccountType } from '@/lib/types';

interface AddAccountModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

const POPULAR_BANKS = ['HDFC Bank', 'SBI', 'ICICI', 'Axis', 'Kotak'];

const ACCOUNT_TYPES: { label: string; value: AccountType }[] = [
  { label: 'Savings', value: 'SAVINGS' },
  { label: 'Current', value: 'CURRENT' },
  { label: 'Credit Card', value: 'CREDIT_CARD' },
];

export function AddAccountModal({ isOpen, onClose, onSuccess }: AddAccountModalProps) {
  const [bankName, setBankName] = useState('');
  const [accountMask, setAccountMask] = useState('');
  const [accountType, setAccountType] = useState<AccountType>('SAVINGS');
  const [initialBalance, setInitialBalance] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isSuccess, setIsSuccess] = useState(false);
  const toast = useToast();

  useEffect(() => {
    if (isOpen) {
      document.body.style.overflow = 'hidden';
      setIsSuccess(false);
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
      toast.error('Please enter the bank name');
      return;
    }
    const digits = accountMask.replace(/[^0-9]/g, '').slice(-4);
    if (!digits) {
      toast.error('Please enter the last 4 digits');
      return;
    }

    setIsSubmitting(true);
    try {
      const cleanMask = `XX${digits}`;
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

      setIsSuccess(true);
      toast.success(`${bankName.trim()} (${cleanMask}) added`);
      setTimeout(() => {
        onSuccess();
        onClose();
        setBankName('');
        setAccountMask('');
        setInitialBalance('');
        setIsSuccess(false);
      }, 650);
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : 'Error adding account');
    } finally {
      setIsSubmitting(false);
    }
  };

  const inputCls =
    'w-full bg-white border border-slate-200 rounded-xl px-4 py-3 text-sm outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100 text-slate-900 font-medium min-h-[44px]';

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto bg-slate-900/60 backdrop-blur-md p-3 sm:p-6 flex items-center justify-center min-h-screen animate-fade-in">
      <div className="fixed inset-0 -z-10" onClick={onClose} aria-hidden="true" />

      <div className="bg-white rounded-2xl shadow-2xl border border-slate-100 my-auto w-full max-w-md p-6 text-left animate-slide-up">
        {isSuccess ? (
          <div className="py-12 text-center space-y-3 animate-fade-in">
            <div className="w-16 h-16 rounded-full bg-emerald-100 text-emerald-600 flex items-center justify-center mx-auto animate-bounce">
              <CheckCircle2 className="w-9 h-9" />
            </div>
            <h3 className="text-lg font-bold text-slate-900">Account Linked!</h3>
            <p className="text-xs text-slate-500">{bankName} has been added to your wallet</p>
          </div>
        ) : (
          <>
            <div className="flex items-center justify-between pb-3 mb-4 border-b border-slate-100">
              <div className="flex items-center gap-2.5">
                <div className="w-10 h-10 rounded-xl bg-blue-50 text-blue-600 flex items-center justify-center">
                  <CreditCard className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="text-base font-bold text-slate-900 tracking-tight">Add Account</h3>
                  <p className="text-xs text-slate-500">Link a bank account or credit card</p>
                </div>
              </div>
              <button
                type="button"
                onClick={onClose}
                className="p-2 rounded-full text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition-all min-h-[44px] min-w-[44px] flex items-center justify-center"
                aria-label="Close modal"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleSubmit} className="space-y-4">
              {/* Bank Name + Popular Bank Chips */}
              <div>
                <label className="block text-[11px] font-bold text-slate-500 uppercase tracking-wider mb-1.5">
                  Bank Name
                </label>
                <input
                  type="text"
                  required
                  placeholder="Enter bank name..."
                  value={bankName}
                  onChange={(e) => setBankName(e.target.value)}
                  className={inputCls}
                  autoFocus
                />
                <div className="flex flex-wrap gap-1.5 mt-2">
                  {POPULAR_BANKS.map((bank) => (
                    <button
                      type="button"
                      key={bank}
                      onClick={() => setBankName(bank)}
                      className={`px-3 py-1.5 rounded-full text-xs font-semibold border transition-all min-h-[36px] ${
                        bankName === bank
                          ? 'bg-blue-600 text-white border-blue-600'
                          : 'bg-slate-50 text-slate-600 border-slate-200 hover:border-blue-300 hover:text-blue-600'
                      }`}
                    >
                      {bank}
                    </button>
                  ))}
                </div>
              </div>

              {/* Account Type Chips */}
              <div>
                <label className="block text-[11px] font-bold text-slate-500 uppercase tracking-wider mb-1.5">
                  Account Type
                </label>
                <div className="grid grid-cols-3 gap-2">
                  {ACCOUNT_TYPES.map((t) => (
                    <button
                      type="button"
                      key={t.value}
                      onClick={() => setAccountType(t.value)}
                      className={`py-2.5 px-2 rounded-xl text-xs font-semibold border transition-all min-h-[44px] ${
                        accountType === t.value
                          ? 'bg-blue-600 text-white border-blue-600 shadow-sm'
                          : 'bg-white text-slate-700 border-slate-200 hover:bg-slate-50'
                      }`}
                    >
                      {t.label}
                    </button>
                  ))}
                </div>
              </div>

              {/* Last 4 Digits */}
              <div>
                <label className="block text-[11px] font-bold text-slate-500 uppercase tracking-wider mb-1.5">
                  Last 4 Digits
                </label>
                <input
                  type="text"
                  inputMode="numeric"
                  required
                  placeholder="e.g. 4821"
                  value={accountMask}
                  onChange={(e) => setAccountMask(e.target.value.replace(/[^0-9]/g, '').slice(0, 4))}
                  maxLength={4}
                  className={`${inputCls} font-mono tracking-widest`}
                />
              </div>

              {/* Opening Balance */}
              <div>
                <label className="block text-[11px] font-bold text-slate-500 uppercase tracking-wider mb-1.5">
                  Opening Balance (₹ INR)
                </label>
                <div className="relative">
                  <span className="absolute left-4 top-1/2 -translate-y-1/2 text-sm font-bold text-slate-400">
                    ₹
                  </span>
                  <input
                    type="number"
                    step="0.01"
                    placeholder="0.00"
                    value={initialBalance}
                    onChange={(e) => setInitialBalance(e.target.value)}
                    className={`${inputCls} pl-8 font-mono font-bold`}
                  />
                </div>
              </div>

              <button
                type="submit"
                disabled={isSubmitting}
                className="w-full py-3.5 rounded-xl text-sm font-semibold bg-blue-600 text-white shadow-lg shadow-blue-500/25 hover:bg-blue-700 active:scale-[0.99] transition-all disabled:opacity-50 min-h-[48px]"
              >
                {isSubmitting ? 'Adding Account...' : 'Add Account'}
              </button>
            </form>
          </>
        )}
      </div>
    </div>
  );
}
