'use client';

import React, { useState, useEffect } from 'react';
import { X, ArrowDownLeft, ArrowUpRight, CheckCircle2, ChevronDown, StickyNote } from 'lucide-react';
import { Account, Category } from '@/lib/types';
import { useToast } from '@/components/ui/Toast';

interface AddTransactionModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

const PAYMENT_MODES = [
  { label: 'UPI', value: 'UPI' },
  { label: 'Card', value: 'CREDIT_CARD' },
  { label: 'Cash', value: 'CASH' },
  { label: 'NEFT', value: 'NEFT' },
  { label: 'IMPS', value: 'IMPS' },
];

function getLocalNowInput(): string {
  const now = new Date();
  const offset = now.getTimezoneOffset();
  const local = new Date(now.getTime() - offset * 60000);
  return local.toISOString().slice(0, 16);
}

export function AddTransactionModal({ isOpen, onClose, onSuccess }: AddTransactionModalProps) {
  const [amount, setAmount] = useState('');
  const [direction, setDirection] = useState<'DEBIT' | 'CREDIT'>('DEBIT');
  const [merchant, setMerchant] = useState('');
  const [accountId, setAccountId] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [isCatDropdownOpen, setIsCatDropdownOpen] = useState(false);
  const [paymentMode, setPaymentMode] = useState('UPI');
  const [notes, setNotes] = useState('');
  const [showNotes, setShowNotes] = useState(false);
  const [transactionDate, setTransactionDate] = useState(getLocalNowInput);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isSuccess, setIsSuccess] = useState(false);
  const toast = useToast();

  useEffect(() => {
    if (isOpen) {
      document.body.style.overflow = 'hidden';
      setIsSuccess(false);
      setIsCatDropdownOpen(false);
      setTransactionDate(getLocalNowInput());

      Promise.all([
        fetch('/api/accounts').then((r) => r.json()),
        fetch('/api/categories').then((r) => r.json()),
      ])
        .then(([accData, catData]) => {
          if (accData.accounts) {
            setAccounts(accData.accounts);
            if (accData.accounts.length > 0 && !accountId) {
              setAccountId(accData.accounts[0].id);
            }
          }
          if (catData.categories) {
            setCategories(catData.categories);
            if (catData.categories.length > 0 && !categoryId) {
              setCategoryId(catData.categories[0].id);
            }
          }
        })
        .catch((err) => console.error('Failed to load accounts/categories', err));
    } else {
      document.body.style.overflow = '';
    }

    return () => {
      document.body.style.overflow = '';
    };
  }, [isOpen, accountId, categoryId]);

  if (!isOpen) return null;

  const selectedCategory = categories.find((c) => c.id === categoryId) || null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const numAmount = parseFloat(amount);
    if (!numAmount || isNaN(numAmount) || numAmount <= 0) {
      toast.error('Please enter a valid amount');
      return;
    }

    setIsSubmitting(true);
    try {
      const res = await fetch('/api/transactions', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          amount: numAmount,
          direction,
          merchant: merchant.trim() || (direction === 'CREDIT' ? 'Income Deposit' : 'General Expense'),
          account_id: accountId || null,
          category_id: categoryId || null,
          payment_mode: paymentMode,
          transaction_type: direction === 'CREDIT' ? 'INCOME' : 'EXPENSE',
          notes: notes.trim() || null,
          transaction_date: new Date(transactionDate).toISOString(),
        }),
      });

      if (!res.ok) {
        const err = await res.json();
        throw new Error(err.error || 'Failed to save transaction');
      }

      setIsSuccess(true);
      toast.success(`₹${numAmount.toLocaleString('en-IN')} saved`);
      setTimeout(() => {
        onSuccess();
        onClose();
        setAmount('');
        setMerchant('');
        setNotes('');
        setShowNotes(false);
        setIsSuccess(false);
      }, 650);
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : 'Error creating transaction');
    } finally {
      setIsSubmitting(false);
    }
  };

  const inputCls =
    'w-full bg-white border border-slate-200 rounded-xl px-4 py-3 text-sm outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100 text-slate-900 font-medium min-h-[44px]';

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto bg-slate-900/60 backdrop-blur-md p-3 sm:p-6 flex items-center justify-center min-h-screen animate-fade-in">
      <div className="fixed inset-0 -z-10" onClick={onClose} aria-hidden="true" />

      <div className="bg-white rounded-2xl shadow-2xl border border-slate-100 my-auto w-full max-w-md p-5 sm:p-6 text-left animate-slide-up">
        {isSuccess ? (
          <div className="py-12 text-center space-y-3 animate-fade-in">
            <div className="w-16 h-16 rounded-full bg-emerald-100 text-emerald-600 flex items-center justify-center mx-auto animate-bounce">
              <CheckCircle2 className="w-9 h-9" />
            </div>
            <h3 className="text-lg font-bold text-slate-900">Transaction Saved!</h3>
            <p className="text-xs text-slate-500">
              ₹{parseFloat(amount || '0').toLocaleString('en-IN')} recorded to Supabase
            </p>
          </div>
        ) : (
          <>
            {/* Header */}
            <div className="flex items-center justify-between pb-3 mb-4 border-b border-slate-100">
              <h3 className="text-base font-bold text-slate-900 tracking-tight">Add Transaction</h3>
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
              {/* Prominent CREDIT / DEBIT toggle pills */}
              <div className="grid grid-cols-2 gap-2 p-1.5 bg-slate-100 rounded-xl">
                <button
                  type="button"
                  onClick={() => setDirection('DEBIT')}
                  className={`flex items-center justify-center gap-1.5 py-2.5 rounded-lg text-xs font-bold transition-all min-h-[44px] ${
                    direction === 'DEBIT'
                      ? 'bg-rose-500 text-white shadow-sm'
                      : 'text-slate-600 hover:text-slate-900'
                  }`}
                >
                  <ArrowUpRight className="w-4 h-4" />
                  <span>DEBIT (Expense)</span>
                </button>
                <button
                  type="button"
                  onClick={() => setDirection('CREDIT')}
                  className={`flex items-center justify-center gap-1.5 py-2.5 rounded-lg text-xs font-bold transition-all min-h-[44px] ${
                    direction === 'CREDIT'
                      ? 'bg-emerald-500 text-white shadow-sm'
                      : 'text-slate-600 hover:text-slate-900'
                  }`}
                >
                  <ArrowDownLeft className="w-4 h-4" />
                  <span>CREDIT (Income)</span>
                </button>
              </div>

              {/* Large Prominent Amount Input */}
              <div className="relative">
                <span
                  className={`absolute left-4 top-1/2 -translate-y-1/2 text-2xl font-bold ${
                    direction === 'CREDIT' ? 'text-emerald-600' : 'text-rose-500'
                  }`}
                >
                  ₹
                </span>
                <input
                  type="number"
                  step="0.01"
                  min="0.01"
                  required
                  placeholder="0"
                  value={amount}
                  onChange={(e) => setAmount(e.target.value)}
                  className="w-full pl-10 pr-4 py-4 text-3xl font-mono font-bold rounded-2xl bg-slate-50 border border-slate-200 outline-none focus:bg-white focus:border-blue-400 focus:ring-2 focus:ring-blue-100 text-slate-900"
                  autoFocus
                />
              </div>

              {/* Merchant Name */}
              <div>
                <label className="block text-[11px] font-bold text-slate-500 uppercase tracking-wider mb-1.5">
                  Merchant
                </label>
                <input
                  type="text"
                  required
                  placeholder={direction === 'DEBIT' ? 'e.g. Swiggy, Uber, Amazon' : 'e.g. Salary, Refund'}
                  value={merchant}
                  onChange={(e) => setMerchant(e.target.value)}
                  className={inputCls}
                />
              </div>

              {/* Category Dropdown (with colored dots) & Account Dropdown */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div className="relative">
                  <label className="block text-[11px] font-bold text-slate-500 uppercase tracking-wider mb-1.5">
                    Category
                  </label>
                  <button
                    type="button"
                    onClick={() => setIsCatDropdownOpen((v) => !v)}
                    className={`${inputCls} flex items-center justify-between gap-2 text-left`}
                  >
                    <span className="flex items-center gap-2 truncate">
                      <span
                        className="w-2.5 h-2.5 rounded-full shrink-0"
                        style={{ backgroundColor: selectedCategory?.color || '#94A3B8' }}
                      />
                      <span className="truncate">{selectedCategory?.name || 'Uncategorized'}</span>
                    </span>
                    <ChevronDown className="w-4 h-4 text-slate-400 shrink-0" />
                  </button>

                  {isCatDropdownOpen && (
                    <div className="absolute z-20 mt-1 w-full max-h-48 overflow-y-auto bg-white border border-slate-200 rounded-xl shadow-xl py-1">
                      <button
                        type="button"
                        onClick={() => {
                          setCategoryId('');
                          setIsCatDropdownOpen(false);
                        }}
                        className="w-full px-3 py-2.5 text-left text-xs font-medium text-slate-600 hover:bg-slate-50 flex items-center gap-2"
                      >
                        <span className="w-2.5 h-2.5 rounded-full bg-slate-300" />
                        <span>Uncategorized</span>
                      </button>
                      {categories.map((cat) => (
                        <button
                          type="button"
                          key={cat.id}
                          onClick={() => {
                            setCategoryId(cat.id);
                            setIsCatDropdownOpen(false);
                          }}
                          className={`w-full px-3 py-2.5 text-left text-xs font-medium hover:bg-blue-50 flex items-center gap-2 ${
                            cat.id === categoryId ? 'bg-blue-50 text-blue-700 font-bold' : 'text-slate-800'
                          }`}
                        >
                          <span
                            className="w-2.5 h-2.5 rounded-full shrink-0"
                            style={{ backgroundColor: cat.color }}
                          />
                          <span className="truncate">{cat.name}</span>
                        </button>
                      ))}
                    </div>
                  )}
                </div>

                <div>
                  <label className="block text-[11px] font-bold text-slate-500 uppercase tracking-wider mb-1.5">
                    Account
                  </label>
                  <select
                    value={accountId}
                    onChange={(e) => setAccountId(e.target.value)}
                    className={inputCls}
                  >
                    <option value="">No Account</option>
                    {accounts.map((acc) => (
                      <option key={acc.id} value={acc.id}>
                        {acc.bank_name} ({acc.account_number_mask})
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              {/* Payment Mode Chips */}
              <div>
                <label className="block text-[11px] font-bold text-slate-500 uppercase tracking-wider mb-1.5">
                  Payment Mode
                </label>
                <div className="flex flex-wrap gap-1.5">
                  {PAYMENT_MODES.map((m) => (
                    <button
                      type="button"
                      key={m.value}
                      onClick={() => setPaymentMode(m.value)}
                      className={`px-3.5 py-2 rounded-xl text-xs font-semibold transition-all min-h-[40px] ${
                        paymentMode === m.value
                          ? 'bg-blue-600 text-white shadow-sm'
                          : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
                      }`}
                    >
                      {m.label}
                    </button>
                  ))}
                </div>
              </div>

              {/* Date */}
              <div>
                <label className="block text-[11px] font-bold text-slate-500 uppercase tracking-wider mb-1.5">
                  Date &amp; Time
                </label>
                <input
                  type="datetime-local"
                  value={transactionDate}
                  onChange={(e) => setTransactionDate(e.target.value)}
                  className={inputCls}
                />
              </div>

              {/* Expandable Notes */}
              {!showNotes ? (
                <button
                  type="button"
                  onClick={() => setShowNotes(true)}
                  className="inline-flex items-center gap-1.5 text-xs font-semibold text-blue-600 hover:text-blue-700 py-1"
                >
                  <StickyNote className="w-3.5 h-3.5" />
                  <span>+ Add note (optional)</span>
                </button>
              ) : (
                <div>
                  <label className="block text-[11px] font-bold text-slate-500 uppercase tracking-wider mb-1.5">
                    Notes
                  </label>
                  <input
                    type="text"
                    placeholder="Add a note..."
                    value={notes}
                    onChange={(e) => setNotes(e.target.value)}
                    className={inputCls}
                    autoFocus
                  />
                </div>
              )}

              {/* Submit */}
              <button
                type="submit"
                disabled={isSubmitting}
                className="w-full py-3.5 rounded-xl text-sm font-semibold bg-blue-600 text-white shadow-lg shadow-blue-500/25 hover:bg-blue-700 active:scale-[0.99] transition-all disabled:opacity-50 min-h-[48px]"
              >
                {isSubmitting ? 'Saving...' : 'Save Transaction'}
              </button>
            </form>
          </>
        )}
      </div>
    </div>
  );
}
