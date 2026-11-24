'use client';

import React, { useState, useEffect } from 'react';
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
  Clock,
  Trash2,
  Edit3,
  Save,
  Tag,
  AlertTriangle
} from 'lucide-react';
import { Badge } from '@/components/ui/Badge';
import { Transaction, Category } from '@/lib/types';
import { useToast } from '@/components/ui/Toast';

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
  const [isEditing, setIsEditing] = useState(false);
  const [selectedCategoryId, setSelectedCategoryId] = useState('');
  const [editedNotes, setEditedNotes] = useState('');
  const [editedMerchant, setEditedMerchant] = useState('');
  const [categories, setCategories] = useState<Category[]>([]);
  const [isDeleting, setIsDeleting] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const toast = useToast();

  useEffect(() => {
    if (transaction) {
      setSelectedCategoryId(transaction.category_id || '');
      setEditedNotes(transaction.notes || '');
      setEditedMerchant(transaction.merchant || '');
      setIsEditing(false);
      setConfirmDelete(false);

      // Fetch categories for editing
      fetch('/api/categories')
        .then((res) => res.json())
        .then((data) => {
          if (data.categories) setCategories(data.categories);
        })
        .catch((e) => console.error(e));
    }
  }, [transaction]);

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

  const handleSaveEdit = async () => {
    setIsSaving(true);
    try {
      const res = await fetch('/api/transactions', {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          id: transaction.id,
          category_id: selectedCategoryId || null,
          merchant: editedMerchant.trim() || transaction.merchant,
          notes: editedNotes.trim() || null,
        }),
      });

      if (!res.ok) {
        const err = await res.json();
        throw new Error(err.error || 'Failed to update transaction');
      }

      toast.success('Transaction updated successfully');
      setIsEditing(false);
      if (onUpdate) onUpdate();
      onClose();
    } catch (err: any) {
      toast.error(err.message || 'Error updating transaction');
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
    } catch (err: any) {
      toast.error(err.message || 'Error deleting transaction');
    } finally {
      setIsDeleting(false);
    }
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
          <div className="flex items-center gap-1">
            {!isEditing && (
              <button
                onClick={() => setIsEditing(true)}
                className="p-1.5 rounded-lg text-slate-500 hover:text-blue-600 hover:bg-slate-100 transition-all"
                title="Edit Category or Notes"
              >
                <Edit3 className="w-4 h-4" />
              </button>
            )}
            <button
              onClick={onClose}
              className="p-1.5 rounded-full text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition-all active:scale-90"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
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

          {isEditing ? (
            <input
              type="text"
              value={editedMerchant}
              onChange={(e) => setEditedMerchant(e.target.value)}
              className="text-base font-bold text-slate-900 mt-2 px-3 py-1 text-center bg-white border border-slate-300 rounded-lg max-w-xs mx-auto block"
            />
          ) : (
            <p className="text-base font-bold text-slate-900 mt-1">
              {transaction.merchant || 'Unknown Merchant'}
            </p>
          )}

          <div className="flex items-center justify-center gap-2 mt-2">
            <Badge type={transaction.transaction_type} />
            <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-emerald-700 bg-emerald-50 px-2.5 py-0.5 rounded-full border border-emerald-200">
              <ShieldCheck className="w-3 h-3" /> Live DB
            </span>
          </div>
        </div>

        {/* Detailed Metadata Grid */}
        <div className="p-6 space-y-3.5 overflow-y-auto flex-1 text-xs">
          {/* Category Selector when editing */}
          {isEditing ? (
            <div className="p-3.5 rounded-xl bg-blue-50/60 border border-blue-200 space-y-2">
              <div className="flex items-center gap-2 text-blue-900 font-bold">
                <Tag className="w-4 h-4 text-blue-600" />
                <span>Assign Category</span>
              </div>
              <select
                value={selectedCategoryId}
                onChange={(e) => setSelectedCategoryId(e.target.value)}
                className="w-full px-3 py-2 text-xs font-semibold rounded-lg bg-white border border-blue-300 focus:outline-none focus:ring-2 focus:ring-blue-500/20 text-slate-900"
              >
                <option value="">Uncategorized</option>
                {categories.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </select>

              <div className="pt-1">
                <label className="block text-[11px] font-bold text-slate-700 mb-1">Personal Notes</label>
                <input
                  type="text"
                  placeholder="Add notes about this transaction..."
                  value={editedNotes}
                  onChange={(e) => setEditedNotes(e.target.value)}
                  className="w-full px-3 py-2 text-xs rounded-lg bg-white border border-blue-300 focus:outline-none text-slate-900"
                />
              </div>

              <div className="flex items-center gap-2 pt-2">
                <button
                  type="button"
                  onClick={handleSaveEdit}
                  disabled={isSaving}
                  className="flex-1 py-2 rounded-lg bg-blue-600 text-white font-semibold text-xs flex items-center justify-center gap-1.5 shadow-sm hover:bg-blue-700 active:scale-95"
                >
                  <Save className="w-3.5 h-3.5" />
                  <span>{isSaving ? 'Saving...' : 'Save Changes'}</span>
                </button>
                <button
                  type="button"
                  onClick={() => setIsEditing(false)}
                  className="px-3 py-2 rounded-lg bg-white border border-slate-200 text-slate-600 font-semibold text-xs hover:bg-slate-50"
                >
                  Cancel
                </button>
              </div>
            </div>
          ) : (
            /* Category Row */
            <div className="flex items-center justify-between p-3 rounded-xl bg-slate-50 border border-slate-100">
              <div className="flex items-center gap-2.5 text-slate-500 font-medium">
                <Tag className="w-4 h-4 text-indigo-600" />
                <span>Category</span>
              </div>
              <span
                className="font-bold px-2 py-0.5 rounded-md text-[11px]"
                style={{
                  backgroundColor: `${transaction.categories?.color || '#2563EB'}15`,
                  color: transaction.categories?.color || '#2563EB',
                }}
              >
                {transaction.categories?.name || 'Uncategorized'}
              </span>
            </div>
          )}

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
                <span className="truncate max-w-[170px]">{transaction.reference_id}</span>
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

          {/* Balance after transaction */}
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

          {/* Notes display */}
          {transaction.notes && !isEditing && (
            <div className="p-3 rounded-xl bg-slate-50 border border-slate-100 space-y-1">
              <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Notes</span>
              <p className="text-slate-800 font-medium">{transaction.notes}</p>
            </div>
          )}

          {/* Delete Confirmation Box */}
          {confirmDelete && (
            <div className="p-3.5 rounded-xl bg-rose-50 border border-rose-200 space-y-2">
              <div className="flex items-center gap-2 text-rose-900 font-bold">
                <AlertTriangle className="w-4 h-4 text-rose-600" />
                <span>Delete this transaction?</span>
              </div>
              <p className="text-xs text-rose-700">
                This will permanently remove the record and adjust your account balance back.
              </p>
              <div className="flex items-center gap-2 pt-1">
                <button
                  onClick={handleDelete}
                  disabled={isDeleting}
                  className="flex-1 py-2 rounded-lg bg-rose-600 text-white font-semibold text-xs hover:bg-rose-700 active:scale-95"
                >
                  {isDeleting ? 'Deleting...' : 'Yes, Delete'}
                </button>
                <button
                  onClick={() => setConfirmDelete(false)}
                  className="px-3 py-2 rounded-lg bg-white border border-rose-200 text-slate-700 font-semibold text-xs"
                >
                  Cancel
                </button>
              </div>
            </div>
          )}
        </div>

        {/* Modal Footer Actions */}
        <div className="p-4 border-t border-slate-100 bg-white flex items-center gap-2">
          {!confirmDelete && (
            <button
              onClick={() => setConfirmDelete(true)}
              className="p-3 rounded-xl border border-rose-200 text-rose-600 hover:bg-rose-50 transition-all"
              title="Delete Transaction"
            >
              <Trash2 className="w-4 h-4" />
            </button>
          )}

          <button
            onClick={onClose}
            className="flex-1 py-3 rounded-xl font-semibold text-xs bg-slate-900 text-white shadow-md hover:bg-slate-800 transition-all active:scale-98"
          >
            Done
          </button>
        </div>
      </div>
    </div>
  );
}
