'use client';

import React, { useState, useEffect } from 'react';
import {
  X,
  Copy,
  Check,
  CreditCard,
  Hash,
  Clock,
  Trash2,
  Save,
  Tag,
  AlertTriangle,
} from 'lucide-react';
import { Transaction, Category, Account } from '@/lib/types';
import { useToast } from '@/components/ui/Toast';
import { getCategoryVisual } from '@/lib/categoryVisuals';

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
  const [selectedCategoryId, setSelectedCategoryId] = useState('');
  const [editedMerchant, setEditedMerchant] = useState('');
  const [editedAmount, setEditedAmount] = useState('');
  const [editedType, setEditedType] = useState('EXPENSE');
  const [editedDirection, setEditedDirection] = useState<'CREDIT' | 'DEBIT'>('DEBIT');
  const [selectedAccountId, setSelectedAccountId] = useState('');
  const [editedNotes, setEditedNotes] = useState('');
  const [categories, setCategories] = useState<Category[]>([]);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [isDeleting, setIsDeleting] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const toast = useToast();

  useEffect(() => {
    if (transaction) {
      document.body.style.overflow = 'hidden';
      setSelectedCategoryId(transaction.category_id || '');
      setEditedMerchant(transaction.merchant || '');
      setEditedAmount(String(transaction.amount ?? ''));
      setEditedType(transaction.transaction_type || (transaction.direction === 'CREDIT' ? 'INCOME' : 'EXPENSE'));
      setEditedDirection(transaction.direction === 'CREDIT' ? 'CREDIT' : 'DEBIT');
      setSelectedAccountId(transaction.account_id || '');
      setEditedNotes(transaction.notes || '');
      setConfirmDelete(false);

      Promise.all([
        fetch('/api/categories').then((res) => res.json()),
        fetch('/api/accounts').then((res) => res.json()),
      ])
        .then(([catData, accData]) => {
          if (catData.categories) setCategories(catData.categories);
          if (accData.accounts) setAccounts(accData.accounts);
        })
        .catch((e) => console.error(e));
    } else {
      document.body.style.overflow = '';
    }

    return () => {
      document.body.style.overflow = '';
    };
  }, [transaction]);

  if (!transaction) return null;

  const isCredit = editedDirection === 'CREDIT';
  const parsedAmount = parseFloat(editedAmount);
  const hasChanges =
    selectedCategoryId !== (transaction.category_id || '') ||
    editedNotes !== (transaction.notes || '') ||
    editedMerchant !== (transaction.merchant || '') ||
    editedType !== (transaction.transaction_type || 'EXPENSE') ||
    editedDirection !== (transaction.direction === 'CREDIT' ? 'CREDIT' : 'DEBIT') ||
    selectedAccountId !== (transaction.account_id || '') ||
    (!isNaN(parsedAmount) && parsedAmount !== Number(transaction.amount));

  const currentCategory =
    categories.find((c) => c.id === selectedCategoryId) || transaction.categories || null;
  const catVisual = getCategoryVisual(
    currentCategory?.name,
    editedType,
    editedDirection,
    editedMerchant
  );
  const CatHeroIcon = catVisual.icon;

  const formatInr = (val: number) =>
    new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 2,
    }).format(val);

  const formatDate = (dateStr: string) =>
    new Intl.DateTimeFormat('en-IN', {
      weekday: 'short',
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    }).format(new Date(dateStr));

  const copyRef = (text?: string) => {
    if (!text) return;
    navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
    toast.success('Reference ID copied');
  };

  const handleSave = async () => {
    if (!parsedAmount || isNaN(parsedAmount) || parsedAmount <= 0) {
      toast.error('Please enter a valid positive amount');
      return;
    }
    setIsSaving(true);
    try {
      const res = await fetch('/api/transactions', {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          id: transaction.id,
          category_id: selectedCategoryId || null,
          notes: editedNotes.trim() || null,
          merchant: editedMerchant.trim() || 'Unknown Merchant',
          amount: parsedAmount,
          direction: editedDirection,
          transaction_type: editedType,
          account_id: selectedAccountId || null,
        }),
      });

      if (!res.ok) {
        const err = await res.json();
        throw new Error(err.error || 'Failed to update transaction');
      }

      toast.success('Transaction updated');
      if (onUpdate) onUpdate();
      onClose();
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : 'Error updating transaction');
    } finally {
      setIsSaving(false);
    }
  };

  const handleDelete = async () => {
    setIsDeleting(true);
    try {
      const res = await fetch(`/api/transactions?id=${transaction.id}`, {
        method: 'DELETE',
      });

      if (!res.ok) {
        const err = await res.json();
        throw new Error(err.error || 'Failed to delete transaction');
      }

      toast.success('Transaction deleted');
      if (onUpdate) onUpdate();
      onClose();
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : 'Error deleting transaction');
    } finally {
      setIsDeleting(false);
    }
  };

  const inputCls =
    'w-full bg-white border border-slate-200 rounded-xl px-4 py-3 text-sm outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100 text-slate-900';

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto bg-slate-900/60 backdrop-blur-md p-0 sm:p-6 flex items-end sm:items-center justify-center min-h-screen animate-fade-in">
      <div className="fixed inset-0 -z-10" onClick={onClose} aria-hidden="true" />

      {/* Bottom Sheet on mobile, centered card on desktop */}
      <div className="bg-white rounded-t-3xl sm:rounded-2xl shadow-2xl border border-slate-100 sm:my-auto w-full max-w-md overflow-hidden animate-slide-up flex flex-col max-h-[92vh]">
        {/* Drag handle (mobile) + Close */}
        <div className="px-6 pt-3 pb-2 flex items-center justify-between border-b border-slate-100">
          <div className="w-8" />
          <div className="w-10 h-1 rounded-full bg-slate-200 sm:hidden" />
          <button
            onClick={onClose}
            className="p-2 rounded-full text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition-all min-h-[44px] min-w-[44px] flex items-center justify-center"
            aria-label="Close modal"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Hero: Merchant + Huge Colored Amount + Date & Mode */}
        <div className="px-6 py-5 text-center bg-slate-50/70 border-b border-slate-100">
          <div
            className="w-12 h-12 rounded-2xl mx-auto mb-3 flex items-center justify-center"
            style={{
              backgroundColor: `${currentCategory?.color || catVisual.defaultColor}18`,
              color: currentCategory?.color || catVisual.defaultColor,
            }}
          >
            <CatHeroIcon className="w-6 h-6" />
          </div>

          <h2 className="text-lg font-bold text-slate-900 truncate px-2">
            {editedMerchant || transaction.merchant || 'Unknown Merchant'}
          </h2>

          <p
            className={`text-3xl font-extrabold font-mono tracking-tight mt-1 ${
              isCredit ? 'text-emerald-600' : 'text-rose-500'
            }`}
          >
            {isCredit ? '+' : '-'}{formatInr(!isNaN(parsedAmount) && parsedAmount > 0 ? parsedAmount : transaction.amount)}
          </p>

          <p className="text-xs text-slate-500 font-medium mt-2 flex items-center justify-center gap-1.5">
            <Clock className="w-3.5 h-3.5 text-slate-400" />
            <span>{formatDate(transaction.transaction_date)}</span>
            <span>·</span>
            <span className="font-bold text-slate-700">{transaction.payment_mode || 'UPI'}</span>
          </p>
        </div>

        {/* Body */}
        <div className="p-5 sm:p-6 space-y-4 overflow-y-auto flex-1">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-[11px] font-bold text-slate-500 uppercase tracking-wider mb-1">
                Amount (₹)
              </label>
              <input
                type="number"
                step="0.01"
                min="0.01"
                value={editedAmount}
                onChange={(e) => setEditedAmount(e.target.value)}
                className={`${inputCls} font-mono font-bold`}
              />
            </div>
            <div>
              <label className="block text-[11px] font-bold text-slate-500 uppercase tracking-wider mb-1">
                Classification
              </label>
              <select
                value={editedType}
                onChange={(e) => {
                  const next = e.target.value;
                  setEditedType(next);
                  if (next === 'INCOME' || next === 'REFUND' || next === 'REVERSAL') setEditedDirection('CREDIT');
                  if (next === 'EXPENSE') setEditedDirection('DEBIT');
                }}
                className={`${inputCls} min-h-[44px]`}
              >
                <option value="EXPENSE">Expense (-)</option>
                <option value="INCOME">Income (+)</option>
                <option value="TRANSFER">Self Transfer</option>
                <option value="REFUND">Refund</option>
                <option value="CC_BILL_PAYMENT">CC Bill Payment</option>
              </select>
            </div>
          </div>

          <div>
            <label className="block text-[11px] font-bold text-slate-500 uppercase tracking-wider mb-1">
              Merchant / Source
            </label>
            <input
              type="text"
              value={editedMerchant}
              onChange={(e) => setEditedMerchant(e.target.value)}
              className={inputCls}
            />
          </div>

          {/* Category (grouped Income vs Expense + colored badge) */}
          <div>
            <div className="flex items-center justify-between mb-1.5">
              <label className="text-[11px] font-bold text-slate-500 uppercase tracking-wider flex items-center gap-1.5">
                <Tag className="w-3.5 h-3.5 text-blue-600" />
                Category
              </label>
              <span
                className="text-[11px] font-bold px-2.5 py-0.5 rounded-full inline-flex items-center gap-1.5"
                style={{
                  backgroundColor: `${currentCategory?.color || '#64748B'}18`,
                  color: currentCategory?.color || '#64748B',
                }}
              >
                <span
                  className="w-2 h-2 rounded-full"
                  style={{ backgroundColor: currentCategory?.color || '#64748B' }}
                />
                {currentCategory?.name || 'Uncategorized'}
              </span>
            </div>
            <select
              value={selectedCategoryId}
              onChange={(e) => setSelectedCategoryId(e.target.value)}
              className={`${inputCls} min-h-[44px]`}
            >
              <option value="">Uncategorized</option>
              <optgroup label={isCredit ? 'Income Categories' : 'Expense Categories'}>
                {categories
                  .filter((c) => Boolean(c.is_income) === isCredit)
                  .map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name}
                    </option>
                  ))}
              </optgroup>
              <optgroup label={isCredit ? 'Expense Categories' : 'Income Categories'}>
                {categories
                  .filter((c) => Boolean(c.is_income) !== isCredit)
                  .map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name}
                    </option>
                  ))}
              </optgroup>
            </select>
          </div>

          {/* Account Selector */}
          <div>
            <label className="block text-[11px] font-bold text-slate-500 uppercase tracking-wider mb-1 flex items-center gap-1.5">
              <CreditCard className="w-3.5 h-3.5 text-blue-600" />
              Account
            </label>
            <select
              value={selectedAccountId}
              onChange={(e) => setSelectedAccountId(e.target.value)}
              className={`${inputCls} min-h-[44px]`}
            >
              <option value="">Manual / Unassigned</option>
              {accounts.map((acc) => (
                <option key={acc.id} value={acc.id}>
                  {acc.bank_name} ({acc.account_number_mask})
                </option>
              ))}
            </select>
          </div>

          {/* Reference ID */}
          {transaction.reference_id && (
            <div className="flex items-center justify-between p-3.5 rounded-xl bg-slate-50 border border-slate-100 gap-2">
              <div className="flex items-center gap-2 text-xs text-slate-500 font-medium shrink-0">
                <Hash className="w-4 h-4 text-amber-600" />
                <span>Reference ID</span>
              </div>
              <div className="flex items-center gap-1.5 font-mono text-xs font-bold text-slate-900 min-w-0">
                <span className="truncate">{transaction.reference_id}</span>
                <button
                  type="button"
                  onClick={() => copyRef(transaction.reference_id)}
                  className="p-1.5 rounded-lg text-slate-400 hover:text-blue-600 hover:bg-white shrink-0"
                  title="Copy Reference ID"
                >
                  {copied ? (
                    <Check className="w-3.5 h-3.5 text-emerald-600" />
                  ) : (
                    <Copy className="w-3.5 h-3.5" />
                  )}
                </button>
              </div>
            </div>
          )}

          {/* Notes (editable textarea) */}
          <div>
            <label className="block text-[11px] font-bold text-slate-500 uppercase tracking-wider mb-1.5">
              Notes
            </label>
            <textarea
              rows={2}
              placeholder="Add personal notes..."
              value={editedNotes}
              onChange={(e) => setEditedNotes(e.target.value)}
              className={`${inputCls} resize-none`}
            />
          </div>

          {/* Save Changes button when edited */}
          {hasChanges && (
            <button
              type="button"
              onClick={handleSave}
              disabled={isSaving}
              className="w-full py-3 rounded-xl bg-blue-600 text-white text-xs font-semibold shadow-sm hover:bg-blue-700 active:scale-[0.99] transition-all flex items-center justify-center gap-1.5 min-h-[44px] disabled:opacity-60"
            >
              <Save className="w-4 h-4" />
              <span>{isSaving ? 'Saving...' : 'Save Changes'}</span>
            </button>
          )}

          {/* Delete Section */}
          {confirmDelete ? (
            <div className="p-4 rounded-xl bg-rose-50 border border-rose-200 space-y-3">
              <div className="flex items-center gap-2 text-rose-800 text-xs font-bold">
                <AlertTriangle className="w-4 h-4 text-rose-600 shrink-0" />
                <span>Delete this transaction permanently?</span>
              </div>
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={handleDelete}
                  disabled={isDeleting}
                  className="flex-1 py-2.5 rounded-xl bg-rose-600 text-white text-xs font-semibold hover:bg-rose-700 min-h-[44px] disabled:opacity-60"
                >
                  {isDeleting ? 'Deleting...' : 'Confirm Delete'}
                </button>
                <button
                  type="button"
                  onClick={() => setConfirmDelete(false)}
                  className="px-4 py-2.5 rounded-xl bg-white border border-rose-200 text-slate-700 text-xs font-semibold min-h-[44px]"
                >
                  Cancel
                </button>
              </div>
            </div>
          ) : (
            <button
              type="button"
              onClick={() => setConfirmDelete(true)}
              className="w-full py-3 rounded-xl border border-rose-200 text-rose-600 bg-rose-50/50 hover:bg-rose-50 text-xs font-semibold transition-all flex items-center justify-center gap-2 min-h-[44px]"
            >
              <Trash2 className="w-4 h-4" />
              <span>Delete Transaction</span>
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
