'use client';

import React, { useState, useEffect, useCallback } from 'react';
import Link from 'next/link';
import { Header } from '@/components/layout/Header';
import { GlassCard } from '@/components/ui/GlassCard';
import {
  Pencil,
  Check,
  X,
  MessageSquare,
  Landmark,
  ArrowRight,
  Tag,
  Plus,
  Trash2,
  Smartphone,
  Download,
  RotateCcw,
  Database,
  RefreshCw,
  FileSpreadsheet,
  AlertTriangle,
  ShieldAlert,
  Fingerprint,
  Lock,
  LogOut,
  Copy,
  KeyRound,
  Eye,
  EyeOff,
  ShieldCheck,
} from 'lucide-react';
import { Account, Category, Transaction } from '@/lib/types';
import { useToast } from '@/components/ui/Toast';
import { usePwa } from '@/components/pwa/PwaProvider';
import { useSms } from '@/components/sms/SmsProvider';
import { useAuth } from '@/components/auth/AuthProvider';

interface HealthStatus {
  status: 'connected' | 'unconfigured' | 'error';
  latencyMs?: number;
}

const COLOR_PRESETS = [
  '#2563EB',
  '#10B981',
  '#F59E0B',
  '#EF4444',
  '#8B5CF6',
  '#EC4899',
  '#06B6D4',
  '#64748B',
];

const PRODUCTION_SYNC_URL = 'https://ara20.vercel.app/api/sync';

const formatInr = (val: number) =>
  new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 0,
  }).format(val);

export default function SettingsPage() {
  const {
    user,
    profile,
    loading: authLoading,
    biometricSupported,
    biometricEnabled,
    refreshAuth,
    signOut,
    lockSession,
    enrollBiometric,
    disableBiometric,
  } = useAuth();

  // Profile
  const [userName, setUserName] = useState('');
  const [draftName, setDraftName] = useState('');
  const [isEditingName, setIsEditingName] = useState(false);
  const [isSavingName, setIsSavingName] = useState(false);

  // Android Sync Token
  const [deviceSyncToken, setDeviceSyncToken] = useState('');
  const [showSyncToken, setShowSyncToken] = useState(false);
  const [isRegeneratingToken, setIsRegeneratingToken] = useState(false);

  // Security & Biometrics
  const [isTogglingBiometric, setIsTogglingBiometric] = useState(false);

  // SMS
  const sms = useSms();
  const [lastSyncText, setLastSyncText] = useState<string>('Never synced');

  // Accounts summary
  const [accounts, setAccounts] = useState<Account[]>([]);

  // Categories
  const [categories, setCategories] = useState<Category[]>([]);
  const [newCatName, setNewCatName] = useState('');
  const [newCatColor, setNewCatColor] = useState('#2563EB');
  const [newCatIsIncome, setNewCatIsIncome] = useState(false);
  const [isAddingCategory, setIsAddingCategory] = useState(false);
  const [deletingCatId, setDeletingCatId] = useState<string | null>(null);

  // App & Health
  const [health, setHealth] = useState<HealthStatus | null>(null);
  const [isCheckingHealth, setIsCheckingHealth] = useState(false);

  // Data & Privacy
  const [isExporting, setIsExporting] = useState(false);
  const [isResetModalOpen, setIsResetModalOpen] = useState(false);
  const [resetConfirmInput, setResetConfirmInput] = useState('');
  const [isResetting, setIsResetting] = useState(false);

  const toast = useToast();
  const { isInstallable, isInstalled, isIos, triggerInstall } = usePwa();

  // Bind directly to useAuth().profile
  useEffect(() => {
    if (profile) {
      const resolvedName = profile.full_name || profile.email || 'User';
      setUserName(resolvedName);
      if (!isEditingName) {
        setDraftName(resolvedName);
      }
      if (profile.device_sync_token) {
        setDeviceSyncToken(profile.device_sync_token);
      }
    }
  }, [profile, isEditingName]);

  const checkHealth = useCallback(async () => {
    setIsCheckingHealth(true);
    try {
      const res = await fetch('/api/health');
      const data = await res.json();
      setHealth(data);
    } catch {
      setHealth({ status: 'error' });
    } finally {
      setIsCheckingHealth(false);
    }
  }, []);

  const fetchAll = useCallback(async () => {
    try {
      const [userRes, accRes, catRes] = await Promise.all([
        fetch('/api/user'),
        fetch('/api/accounts'),
        fetch('/api/categories'),
      ]);
      if (userRes.ok) {
        const d = await userRes.json();
        const resolved = d.profile?.full_name || d.profile?.name;
        if (resolved) {
          setUserName(resolved);
          setDraftName(resolved);
        }
        if (d.profile?.device_sync_token) {
          setDeviceSyncToken(d.profile.device_sync_token);
        }
      }
      if (accRes.ok) {
        const d = await accRes.json();
        setAccounts(d.accounts || []);
      }
      if (catRes.ok) {
        const d = await catRes.json();
        setCategories(d.categories || []);
      }
    } catch (e) {
      console.error(e);
    }
  }, []);

  useEffect(() => {
    if (!authLoading && user) {
      fetchAll();
    }
    checkHealth();

    const ctxSync = (sms as unknown as { lastSyncTime?: Date | null }).lastSyncTime;
    const storedSync =
      typeof window !== 'undefined' ? localStorage.getItem('monzi_last_sync') : null;
    const syncDate = ctxSync ? new Date(ctxSync) : storedSync ? new Date(storedSync) : null;
    if (syncDate && !isNaN(syncDate.getTime())) {
      setLastSyncText(
        syncDate.toLocaleString('en-IN', {
          day: 'numeric',
          month: 'short',
          hour: '2-digit',
          minute: '2-digit',
        })
      );
    }
  }, [authLoading, user, fetchAll, checkHealth, sms]);

  const handleSaveName = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = draftName.trim();
    if (!trimmed) {
      toast.error('Name cannot be empty');
      return;
    }
    setIsSavingName(true);
    try {
      const res = await fetch('/api/user', {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ full_name: trimmed }),
      });
      if (!res.ok) {
        const err = await res.json();
        throw new Error(err.error || 'Failed to update profile');
      }
      setUserName(trimmed);
      setIsEditingName(false);
      await refreshAuth();
      toast.success('Profile updated');
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : 'Failed to update profile');
    } finally {
      setIsSavingName(false);
    }
  };

  const handleToggleBiometric = async () => {
    setIsTogglingBiometric(true);
    try {
      if (biometricEnabled) {
        const res = await disableBiometric();
        if (!res.success) throw new Error(res.error || 'Could not disable biometric lock');
        toast.success('Biometric lock disabled');
      } else {
        const res = await enrollBiometric();
        if (!res.success) throw new Error(res.error || 'Could not enable biometric lock');
        toast.success('Fingerprint / Face ID enabled for this device');
      }
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : 'Biometric update failed');
    } finally {
      setIsTogglingBiometric(false);
    }
  };

  const handleCopyText = async (value: string, label: string) => {
    if (!value) return;
    try {
      await navigator.clipboard.writeText(value);
      toast.success(`${label} copied to clipboard`);
    } catch {
      toast.error(`Failed to copy ${label}`);
    }
  };

  const handleRegenerateSyncToken = async () => {
    setIsRegeneratingToken(true);
    try {
      const res = await fetch('/api/user', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ regenerate_sync_token: true }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || 'Failed to regenerate sync token');
      if (data.profile?.device_sync_token) {
        setDeviceSyncToken(data.profile.device_sync_token);
      }
      await refreshAuth();
      toast.success('New Android sync token generated');
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : 'Failed to regenerate token');
    } finally {
      setIsRegeneratingToken(false);
    }
  };

  const handleAddCategory = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newCatName.trim()) {
      toast.error('Category name is required');
      return;
    }
    setIsAddingCategory(true);
    try {
      const res = await fetch('/api/categories', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name: newCatName.trim(),
          color: newCatColor,
          is_income: newCatIsIncome,
        }),
      });
      if (!res.ok) {
        const err = await res.json();
        throw new Error(err.error || 'Failed to add category');
      }
      toast.success(`Category "${newCatName.trim()}" added`);
      setNewCatName('');
      const catRes = await fetch('/api/categories');
      if (catRes.ok) {
        const d = await catRes.json();
        setCategories(d.categories || []);
      }
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : 'Error adding category');
    } finally {
      setIsAddingCategory(false);
    }
  };

  const handleDeleteCategory = async (cat: Category) => {
    setDeletingCatId(cat.id);
    try {
      const res = await fetch(`/api/categories?id=${cat.id}`, {
        method: 'DELETE',
      });
      if (!res.ok) {
        const err = await res.json();
        throw new Error(err.error || 'Failed to delete category');
      }
      setCategories((prev) => prev.filter((c) => c.id !== cat.id));
      toast.success(`Deleted "${cat.name}"`);
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : 'Error deleting category');
    } finally {
      setDeletingCatId(null);
    }
  };

  const handleClearCache = async () => {
    try {
      if ('serviceWorker' in navigator) {
        const registrations = await navigator.serviceWorker.getRegistrations();
        for (const reg of registrations) {
          await reg.unregister();
        }
      }
      if ('caches' in window) {
        const cacheNames = await caches.keys();
        for (const name of cacheNames) {
          await caches.delete(name);
        }
      }
      toast.success('App cache cleared. Reloading...');
      setTimeout(() => {
        window.location.reload();
      }, 700);
    } catch {
      toast.error('Failed to clear browser cache');
    }
  };

  const handleExportCsv = async () => {
    setIsExporting(true);
    try {
      const res = await fetch('/api/transactions?limit=5000');
      if (!res.ok) throw new Error('Failed to fetch transactions');
      const data = await res.json();
      const txs: Transaction[] = data.transactions || [];
      if (txs.length === 0) {
        toast.info('No transactions available to export');
        return;
      }

      const headers = [
        'Date',
        'Merchant',
        'Type',
        'Direction',
        'Amount (INR)',
        'Category',
        'Payment Mode',
        'Bank',
        'Account Mask',
        'Reference ID',
        'Notes',
      ];
      const rows = txs.map((t) => [
        `"${new Date(t.transaction_date).toISOString()}"`,
        `"${(t.merchant || '').replace(/"/g, '""')}"`,
        t.transaction_type,
        t.direction,
        t.amount,
        `"${(t.categories?.name || 'Uncategorized').replace(/"/g, '""')}"`,
        `"${t.payment_mode || ''}"`,
        `"${(t.accounts?.bank_name || '').replace(/"/g, '""')}"`,
        `"${t.accounts?.account_number_mask || ''}"`,
        `"${(t.reference_id || '').replace(/"/g, '""')}"`,
        `"${(t.notes || '').replace(/"/g, '""')}"`,
      ]);

      const csv = [headers.join(','), ...rows.map((r) => r.join(','))].join('\n');
      const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `monzi_transactions_${new Date().toISOString().slice(0, 10)}.csv`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(url);

      toast.success(`Exported ${txs.length} transactions as CSV`);
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : 'Export failed');
    } finally {
      setIsExporting(false);
    }
  };

  const handleResetAllData = async (e: React.FormEvent) => {
    e.preventDefault();
    if (resetConfirmInput.trim().toUpperCase() !== 'RESET') {
      toast.error('Please type RESET to confirm');
      return;
    }

    setIsResetting(true);
    try {
      const res = await fetch('/api/reset', {
        method: 'POST',
      });
      const data = await res.json();
      if (!res.ok) {
        throw new Error(data.error || 'Reset failed');
      }
      toast.success(data.message || 'Your transactions and accounts have been cleared');
      setIsResetModalOpen(false);
      setResetConfirmInput('');
      fetchAll();
      checkHealth();
      window.dispatchEvent(new CustomEvent('monzi:refresh'));
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : 'Reset failed');
    } finally {
      setIsResetting(false);
    }
  };

  const displayName = userName || profile?.full_name || user?.email || 'Account';
  const initials =
    profile?.initials ||
    displayName
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((p: string) => p[0].toUpperCase())
      .join('') ||
    'U';

  const totalAccountBalance = accounts.reduce(
    (sum, a) => sum + Number(a.current_balance),
    0
  );

  const permissionPill = (() => {
    if (sms.permissionState === 'granted') {
      return {
        label: 'Granted',
        cls: 'bg-emerald-50 text-emerald-700 border-emerald-200',
        dot: 'bg-emerald-500',
      };
    }
    if (sms.permissionState === 'denied') {
      return {
        label: 'Denied',
        cls: 'bg-rose-50 text-rose-700 border-rose-200',
        dot: 'bg-rose-500',
      };
    }
    return {
      label: 'Not Granted',
      cls: 'bg-amber-50 text-amber-700 border-amber-200',
      dot: 'bg-amber-500',
    };
  })();

  const inputCls =
    'bg-white border border-slate-200 rounded-xl px-4 py-3 text-sm outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100 text-slate-900';

  return (
    <div className="flex flex-col min-h-screen">
      <Header
        onRefresh={() => {
          fetchAll();
          checkHealth();
        }}
        isRefreshing={isCheckingHealth}
      />

      <div className="p-4 sm:p-6 md:p-8 space-y-5 max-w-4xl mx-auto w-full">
        <div>
          <h2 className="text-xl font-bold tracking-tight text-slate-900">Settings</h2>
          <p className="text-xs text-slate-500 font-medium mt-0.5">
            Profile, biometric security, Android SMS companion sync, categories &amp; data controls
          </p>
        </div>

        {/* Section 1: Profile */}
        <GlassCard className="p-5 sm:p-6">
          <div className="flex items-center justify-between gap-4">
            <div className="flex items-center gap-3.5 min-w-0 flex-1">
              <div className="w-12 h-12 rounded-2xl bg-gradient-to-tr from-blue-600 to-indigo-500 text-white font-bold text-base flex items-center justify-center shadow-md shadow-blue-500/20 shrink-0">
                {initials}
              </div>

              {isEditingName ? (
                <form
                  onSubmit={handleSaveName}
                  className="flex items-center gap-2 flex-1 flex-wrap sm:flex-nowrap"
                >
                  <input
                    type="text"
                    value={draftName}
                    onChange={(e) => setDraftName(e.target.value)}
                    placeholder="Your full name"
                    autoFocus
                    className={`${inputCls} flex-1 min-w-[160px] min-h-[44px]`}
                  />
                  <div className="flex items-center gap-1.5">
                    <button
                      type="submit"
                      disabled={isSavingName}
                      className="px-3.5 py-2.5 rounded-xl bg-blue-600 text-white text-xs font-semibold hover:bg-blue-700 active:scale-95 transition-all flex items-center gap-1 min-h-[44px] disabled:opacity-60"
                    >
                      <Check className="w-4 h-4" />
                      <span>{isSavingName ? 'Saving...' : 'Save'}</span>
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setDraftName(displayName);
                        setIsEditingName(false);
                      }}
                      className="p-2.5 rounded-xl bg-slate-100 text-slate-600 hover:bg-slate-200 min-h-[44px] min-w-[44px] flex items-center justify-center"
                      aria-label="Cancel editing"
                    >
                      <X className="w-4 h-4" />
                    </button>
                  </div>
                </form>
              ) : (
                <div className="min-w-0">
                  <span className="text-[11px] font-bold text-slate-400 uppercase tracking-wider block">
                    Authenticated Profile
                  </span>
                  <div className="flex items-center gap-2">
                    <h3 className="text-base sm:text-lg font-bold text-slate-900 truncate">
                      {displayName}
                    </h3>
                    <button
                      onClick={() => {
                        setDraftName(displayName);
                        setIsEditingName(true);
                      }}
                      className="p-2 rounded-xl text-slate-400 hover:text-blue-600 hover:bg-blue-50 transition-all min-h-[44px] min-w-[44px] flex items-center justify-center"
                      title="Edit name"
                      aria-label="Edit profile name"
                    >
                      <Pencil className="w-4 h-4" />
                    </button>
                  </div>
                  {(profile?.email || user?.email) && (
                    <p className="text-xs text-slate-500 truncate">
                      {profile?.email || user?.email}
                    </p>
                  )}
                </div>
              )}
            </div>
          </div>
        </GlassCard>

        {/* Section 2: Security & Biometrics */}
        <GlassCard className="p-5 sm:p-6 space-y-4">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-2xl bg-indigo-50 text-indigo-600 flex items-center justify-center shrink-0">
                <Fingerprint className="w-5 h-5" />
              </div>
              <div>
                <h3 className="text-sm sm:text-base font-bold text-slate-900">
                  Security &amp; Biometrics
                </h3>
                <p className="text-xs text-slate-500 font-medium">
                  Protect your financial dashboard with Fingerprint / Face ID (WebAuthn)
                </p>
              </div>
            </div>

            <span
              className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold border self-start sm:self-auto ${
                biometricEnabled
                  ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                  : 'bg-slate-100 text-slate-600 border-slate-200'
              }`}
            >
              <ShieldCheck className="w-3.5 h-3.5" />
              {biometricEnabled ? 'Biometrics Active' : 'Password Only'}
            </span>
          </div>

          <div className="p-4 rounded-xl bg-slate-50/80 border border-slate-200/70 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
            <div>
              <p className="text-xs font-bold text-slate-800">
                Fingerprint / Face ID App Lock
              </p>
              <p className="text-[11px] text-slate-500 mt-0.5">
                {biometricSupported
                  ? biometricEnabled
                    ? 'Enabled on your account. You can lock the session anytime.'
                    : 'Supported on this device. Enroll in 1 tap for fast biometric unlock.'
                  : 'Platform biometric authenticator is not available in this browser.'}
              </p>
            </div>

            <button
              type="button"
              onClick={handleToggleBiometric}
              disabled={isTogglingBiometric || (!biometricEnabled && !biometricSupported)}
              className={`px-4 py-2.5 rounded-xl text-xs font-semibold transition-all min-h-[44px] shrink-0 disabled:opacity-50 ${
                biometricEnabled
                  ? 'bg-rose-50 border border-rose-200 text-rose-700 hover:bg-rose-100'
                  : 'bg-gradient-to-r from-blue-600 to-indigo-600 text-white shadow-sm shadow-blue-500/25 hover:shadow-md'
              }`}
            >
              {isTogglingBiometric
                ? 'Updating...'
                : biometricEnabled
                ? 'Disable Biometrics'
                : 'Enable Fingerprint / Face ID'}
            </button>
          </div>

          <div className="flex flex-col sm:flex-row gap-3">
            <button
              type="button"
              onClick={() => lockSession()}
              className="flex-1 flex items-center justify-center gap-2 px-4 py-2.5 rounded-xl bg-white border border-slate-200 text-slate-800 hover:bg-blue-50 hover:border-blue-200 hover:text-blue-700 text-xs font-semibold transition-all min-h-[44px]"
            >
              <Lock className="w-4 h-4" />
              <span>Lock Session Now</span>
            </button>

            <button
              type="button"
              onClick={() => signOut()}
              className="flex-1 flex items-center justify-center gap-2 px-4 py-2.5 rounded-xl bg-white border border-slate-200 text-rose-700 hover:bg-rose-50 hover:border-rose-200 text-xs font-semibold transition-all min-h-[44px]"
            >
              <LogOut className="w-4 h-4" />
              <span>Sign Out</span>
            </button>
          </div>
        </GlassCard>

        {/* Section 3: Android Companion Sync Token */}
        <GlassCard className="p-5 sm:p-6 space-y-4">
          <div className="flex items-center justify-between gap-3">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-2xl bg-emerald-50 text-emerald-600 flex items-center justify-center shrink-0">
                <KeyRound className="w-5 h-5" />
              </div>
              <div>
                <h3 className="text-sm sm:text-base font-bold text-slate-900">
                  Android Companion Sync Token
                </h3>
                <p className="text-xs text-slate-500 font-medium">
                  Connect the Monzi Android SMS app to your personal isolated account
                </p>
              </div>
            </div>
          </div>

          <div className="space-y-3">
            <div className="p-4 rounded-xl bg-slate-50/80 border border-slate-200/70 space-y-2">
              <p className="text-[11px] font-bold text-slate-400 uppercase tracking-wider">
                Production Sync Endpoint URL
              </p>
              <div className="flex items-center gap-2">
                <code className="flex-1 text-xs font-mono bg-white border border-slate-200 rounded-lg px-3 py-2 text-slate-800 truncate">
                  {PRODUCTION_SYNC_URL}
                </code>
                <button
                  type="button"
                  onClick={() => handleCopyText(PRODUCTION_SYNC_URL, 'Sync URL')}
                  className="px-3 py-2 rounded-lg bg-white border border-slate-200 text-slate-700 hover:bg-slate-100 text-xs font-semibold inline-flex items-center gap-1.5 min-h-[40px]"
                >
                  <Copy className="w-3.5 h-3.5" />
                  <span>Copy</span>
                </button>
              </div>
            </div>

            <div className="p-4 rounded-xl bg-slate-50/80 border border-slate-200/70 space-y-2">
              <div className="flex items-center justify-between">
                <p className="text-[11px] font-bold text-slate-400 uppercase tracking-wider">
                  Personal Device Sync Token
                </p>
                <button
                  type="button"
                  onClick={handleRegenerateSyncToken}
                  disabled={isRegeneratingToken}
                  className="text-[11px] font-semibold text-blue-600 hover:text-blue-700 inline-flex items-center gap-1 disabled:opacity-50"
                >
                  <RefreshCw className={`w-3 h-3 ${isRegeneratingToken ? 'animate-spin' : ''}`} />
                  <span>{isRegeneratingToken ? 'Regenerating...' : 'Regenerate'}</span>
                </button>
              </div>
              <div className="flex items-center gap-2">
                <code className="flex-1 text-xs font-mono bg-white border border-slate-200 rounded-lg px-3 py-2 text-slate-800 truncate">
                  {deviceSyncToken
                    ? showSyncToken
                      ? deviceSyncToken
                      : `${deviceSyncToken.slice(0, 8)}••••••••••••••••${deviceSyncToken.slice(-6)}`
                    : 'Generating token...'}
                </code>
                <button
                  type="button"
                  onClick={() => setShowSyncToken((v) => !v)}
                  className="p-2 rounded-lg bg-white border border-slate-200 text-slate-600 hover:bg-slate-100 min-h-[40px] min-w-[40px] flex items-center justify-center"
                  aria-label={showSyncToken ? 'Hide token' : 'Show token'}
                >
                  {showSyncToken ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
                <button
                  type="button"
                  onClick={() => handleCopyText(deviceSyncToken, 'Sync token')}
                  disabled={!deviceSyncToken}
                  className="px-3 py-2 rounded-lg bg-white border border-slate-200 text-slate-700 hover:bg-slate-100 text-xs font-semibold inline-flex items-center gap-1.5 min-h-[40px] disabled:opacity-50"
                >
                  <Copy className="w-3.5 h-3.5" />
                  <span>Copy</span>
                </button>
              </div>
            </div>
          </div>
        </GlassCard>

        {/* Section 4: SMS & Automation */}
        <GlassCard className="p-5 sm:p-6 space-y-4">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-2xl bg-blue-50 text-blue-600 flex items-center justify-center shrink-0">
                <MessageSquare className="w-5 h-5" />
              </div>
              <div>
                <h3 className="text-sm sm:text-base font-bold text-slate-900">
                  SMS &amp; Automation
                </h3>
                <p className="text-xs text-slate-500 font-medium">
                  Auto-detect and parse Indian bank debit &amp; credit SMS
                </p>
              </div>
            </div>

            <span
              className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold border self-start sm:self-auto ${permissionPill.cls}`}
            >
              <span className={`w-2 h-2 rounded-full ${permissionPill.dot}`} />
              {permissionPill.label}
            </span>
          </div>

          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 p-4 rounded-xl bg-slate-50/80 border border-slate-200/70">
            <div>
              <p className="text-[11px] font-bold text-slate-400 uppercase tracking-wider">
                Last Sync
              </p>
              <p className="text-xs font-semibold text-slate-800 mt-0.5">{lastSyncText}</p>
            </div>

            <div className="flex flex-wrap items-center gap-2">
              {sms.permissionState !== 'granted' && (
                <button
                  onClick={sms.requestPermission}
                  className="px-4 py-2.5 rounded-xl bg-white border border-slate-200 text-slate-700 hover:bg-slate-100 text-xs font-semibold transition-all min-h-[44px]"
                >
                  Enable Permission
                </button>
              )}
              <button
                onClick={sms.openSyncModal}
                className="px-4 py-2.5 rounded-xl bg-blue-600 text-white hover:bg-blue-700 text-xs font-semibold shadow-sm shadow-blue-500/20 active:scale-95 transition-all min-h-[44px]"
              >
                Open SMS Sync Center
              </button>
            </div>
          </div>
        </GlassCard>

        {/* Section 5: Bank Accounts Summary */}
        <GlassCard className="p-5 sm:p-6">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-2xl bg-emerald-50 text-emerald-600 flex items-center justify-center shrink-0">
                <Landmark className="w-5 h-5" />
              </div>
              <div>
                <h3 className="text-sm sm:text-base font-bold text-slate-900">Bank Accounts</h3>
                <p className="text-xs text-slate-500 font-medium">
                  {accounts.length} linked account{accounts.length !== 1 ? 's' : ''} · Net Balance{' '}
                  <span className="font-mono font-bold text-slate-800">
                    {formatInr(totalAccountBalance)}
                  </span>
                </p>
              </div>
            </div>

            <Link
              href="/accounts"
              className="inline-flex items-center justify-center gap-1.5 px-4 py-2.5 rounded-xl bg-slate-900 text-white hover:bg-slate-800 text-xs font-semibold transition-all min-h-[44px]"
            >
              <span>Manage Accounts</span>
              <ArrowRight className="w-4 h-4" />
            </Link>
          </div>
        </GlassCard>

        {/* Section 6: Categories */}
        <GlassCard className="p-5 sm:p-6 space-y-4">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-2xl bg-indigo-50 text-indigo-600 flex items-center justify-center shrink-0">
              <Tag className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-sm sm:text-base font-bold text-slate-900">Categories</h3>
              <p className="text-xs text-slate-500 font-medium">
                Manage income and expense categories ({categories.length})
              </p>
            </div>
          </div>

          <form
            onSubmit={handleAddCategory}
            className="p-4 rounded-xl bg-slate-50/80 border border-slate-200/70 space-y-3"
          >
            <div className="flex flex-col sm:flex-row gap-2.5">
              <input
                type="text"
                placeholder="New category name (e.g. Travel, Gym)"
                value={newCatName}
                onChange={(e) => setNewCatName(e.target.value)}
                className={`${inputCls} flex-1 min-h-[44px]`}
              />
              <button
                type="button"
                onClick={() => setNewCatIsIncome(!newCatIsIncome)}
                className={`px-4 py-2.5 rounded-xl text-xs font-semibold border transition-all min-h-[44px] ${
                  newCatIsIncome
                    ? 'bg-emerald-600 text-white border-emerald-600'
                    : 'bg-white text-slate-700 border-slate-200 hover:bg-slate-50'
                }`}
              >
                {newCatIsIncome ? 'Income' : 'Expense'}
              </button>
              <button
                type="submit"
                disabled={isAddingCategory}
                className="px-4 py-2.5 rounded-xl bg-blue-600 text-white text-xs font-semibold hover:bg-blue-700 active:scale-95 transition-all flex items-center justify-center gap-1.5 min-h-[44px] disabled:opacity-60"
              >
                <Plus className="w-4 h-4" />
                <span>{isAddingCategory ? 'Adding...' : 'Add'}</span>
              </button>
            </div>

            <div className="flex items-center gap-2 overflow-x-auto py-1">
              <span className="text-[11px] font-semibold text-slate-500 shrink-0">Color:</span>
              {COLOR_PRESETS.map((c) => (
                <button
                  type="button"
                  key={c}
                  onClick={() => setNewCatColor(c)}
                  className={`w-7 h-7 rounded-full shrink-0 transition-transform ${
                    newCatColor === c
                      ? 'scale-110 ring-2 ring-slate-900 ring-offset-2'
                      : 'opacity-80 hover:opacity-100'
                  }`}
                  style={{ backgroundColor: c }}
                  aria-label={`Select color ${c}`}
                />
              ))}
            </div>
          </form>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
            {categories.map((c) => (
              <div
                key={c.id}
                className="flex items-center justify-between gap-2 px-3.5 py-2.5 rounded-xl bg-white border border-slate-200/80 min-h-[44px]"
              >
                <div className="flex items-center gap-2.5 min-w-0">
                  <span
                    className="w-3 h-3 rounded-full shrink-0"
                    style={{ backgroundColor: c.color }}
                  />
                  <span className="text-xs font-bold text-slate-800 truncate">{c.name}</span>
                  {c.is_income && (
                    <span className="text-[10px] font-semibold px-2 py-0.5 rounded-full bg-emerald-50 text-emerald-700 shrink-0">
                      Income
                    </span>
                  )}
                </div>

                <button
                  onClick={() => handleDeleteCategory(c)}
                  disabled={deletingCatId === c.id}
                  className="p-2 rounded-lg text-slate-400 hover:text-rose-600 hover:bg-rose-50 transition-all min-h-[36px] min-w-[36px] flex items-center justify-center disabled:opacity-40"
                  title={`Delete ${c.name}`}
                  aria-label={`Delete ${c.name}`}
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            ))}
          </div>
        </GlassCard>

        {/* Section 7: App & PWA */}
        <GlassCard className="p-5 sm:p-6 space-y-4">
          <div className="flex items-center justify-between gap-3">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-2xl bg-violet-50 text-violet-600 flex items-center justify-center shrink-0">
                <Smartphone className="w-5 h-5" />
              </div>
              <div>
                <h3 className="text-sm sm:text-base font-bold text-slate-900">App &amp; PWA</h3>
                <p className="text-xs text-slate-500 font-medium">
                  Installation, offline cache &amp; Supabase status
                </p>
              </div>
            </div>

            <button
              onClick={checkHealth}
              disabled={isCheckingHealth}
              className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-bold border transition-all ${
                health?.status === 'connected'
                  ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                  : health?.status === 'error'
                  ? 'bg-rose-50 text-rose-700 border-rose-200'
                  : 'bg-slate-100 text-slate-700 border-slate-200'
              }`}
              title="Click to ping Supabase"
            >
              <Database className="w-3.5 h-3.5" />
              <span>
                {isCheckingHealth
                  ? 'Checking...'
                  : health?.status === 'connected'
                  ? `Supabase OK (${health.latencyMs}ms)`
                  : health?.status === 'unconfigured'
                  ? 'Not Configured'
                  : 'DB Error'}
              </span>
              <RefreshCw className={`w-3 h-3 ${isCheckingHealth ? 'animate-spin' : ''}`} />
            </button>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div className="p-4 rounded-xl bg-slate-50/80 border border-slate-200/70 flex items-center justify-between gap-3">
              <div>
                <p className="text-[11px] font-bold text-slate-400 uppercase tracking-wider">
                  Install App
                </p>
                <p className="text-xs font-bold text-slate-800 mt-0.5">
                  {isInstalled
                    ? 'Installed (Standalone)'
                    : isInstallable || isIos
                    ? 'Ready to Install'
                    : 'Browser Mode'}
                </p>
              </div>
              {!isInstalled && (isInstallable || isIos) && (
                <button
                  onClick={triggerInstall}
                  className="px-4 py-2.5 rounded-xl bg-blue-600 text-white text-xs font-semibold hover:bg-blue-700 active:scale-95 transition-all flex items-center gap-1.5 min-h-[44px]"
                >
                  <Download className="w-4 h-4" />
                  <span>Install Now</span>
                </button>
              )}
            </div>

            <div className="p-4 rounded-xl bg-slate-50/80 border border-slate-200/70 flex items-center justify-between gap-3">
              <div>
                <p className="text-[11px] font-bold text-slate-400 uppercase tracking-wider">
                  Offline Cache
                </p>
                <p className="text-xs font-bold text-slate-800 mt-0.5">
                  Service Worker Assets
                </p>
              </div>
              <button
                onClick={handleClearCache}
                className="px-4 py-2.5 rounded-xl bg-white border border-slate-200 text-slate-700 hover:bg-slate-100 text-xs font-semibold active:scale-95 transition-all flex items-center gap-1.5 min-h-[44px]"
              >
                <RotateCcw className="w-4 h-4" />
                <span>Clear Cache</span>
              </button>
            </div>
          </div>
        </GlassCard>

        {/* Section 8: Data & Privacy */}
        <GlassCard className="p-5 sm:p-6 space-y-4">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-2xl bg-rose-50 text-rose-600 flex items-center justify-center shrink-0">
              <ShieldAlert className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-sm sm:text-base font-bold text-slate-900">Data &amp; Privacy</h3>
              <p className="text-xs text-slate-500 font-medium">
                Export your personal ledger as CSV or clear your account&apos;s financial data
              </p>
            </div>
          </div>

          <div className="flex flex-col sm:flex-row gap-3">
            <button
              onClick={handleExportCsv}
              disabled={isExporting}
              className="flex-1 flex items-center justify-center gap-2 px-4 py-3 rounded-xl bg-white border border-slate-200 text-slate-800 hover:bg-slate-50 text-xs font-semibold transition-all min-h-[44px] disabled:opacity-60"
            >
              <FileSpreadsheet className="w-4 h-4 text-blue-600" />
              <span>{isExporting ? 'Exporting...' : 'Export Transactions (CSV)'}</span>
            </button>

            <button
              onClick={() => setIsResetModalOpen(true)}
              className="flex-1 flex items-center justify-center gap-2 px-4 py-3 rounded-xl bg-rose-600 text-white hover:bg-rose-700 text-xs font-semibold shadow-sm shadow-rose-500/20 active:scale-95 transition-all min-h-[44px]"
            >
              <AlertTriangle className="w-4 h-4" />
              <span>Reset My Data</span>
            </button>
          </div>
        </GlassCard>
      </div>

      {/* Reset All Data Confirmation Modal (Authenticated, requires typing RESET) */}
      {isResetModalOpen && (
        <div className="fixed inset-0 z-50 overflow-y-auto bg-slate-900/60 backdrop-blur-md p-3 sm:p-6 flex items-center justify-center min-h-screen animate-fade-in">
          <div
            className="fixed inset-0 -z-10"
            onClick={() => setIsResetModalOpen(false)}
            aria-hidden="true"
          />
          <div className="bg-white rounded-2xl shadow-2xl border border-slate-100 my-auto w-full max-w-md p-6 space-y-4 animate-slide-up">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-full bg-rose-100 flex items-center justify-center text-rose-600">
                  <AlertTriangle className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="text-base font-bold text-slate-900">Reset Your Data?</h3>
                  <p className="text-xs text-slate-500">
                    Deletes your transactions, accounts &amp; SMS logs
                  </p>
                </div>
              </div>
              <button
                onClick={() => setIsResetModalOpen(false)}
                className="p-2 rounded-full text-slate-400 hover:text-slate-700 hover:bg-slate-100"
                aria-label="Close"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <p className="text-xs text-slate-600 leading-relaxed">
              This will permanently delete all transactions, bank accounts, and SMS processing logs belonging to your account. Categories and your login will be preserved.
            </p>

            <form onSubmit={handleResetAllData} className="space-y-4">
              <div>
                <label className="block text-[11px] font-bold text-slate-600 uppercase tracking-wider mb-1.5">
                  Type RESET to confirm
                </label>
                <input
                  type="text"
                  required
                  placeholder="RESET"
                  value={resetConfirmInput}
                  onChange={(e) => setResetConfirmInput(e.target.value)}
                  className={`${inputCls} w-full min-h-[44px] font-mono`}
                />
              </div>

              <div className="flex gap-2">
                <button
                  type="submit"
                  disabled={isResetting || resetConfirmInput.trim().toUpperCase() !== 'RESET'}
                  className="flex-1 py-3 rounded-xl bg-rose-600 text-white text-xs font-semibold hover:bg-rose-700 active:scale-95 transition-all min-h-[44px] disabled:opacity-50"
                >
                  {isResetting ? 'Resetting...' : 'Yes, Reset My Data'}
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setIsResetModalOpen(false);
                    setResetConfirmInput('');
                  }}
                  className="px-4 py-3 rounded-xl bg-slate-100 text-slate-700 text-xs font-semibold hover:bg-slate-200 min-h-[44px]"
                >
                  Cancel
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
