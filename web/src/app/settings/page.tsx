'use client';

import React, { useState, useEffect } from 'react';
import { Header } from '@/components/layout/Header';
import { GlassCard } from '@/components/ui/GlassCard';
import { 
  Smartphone, 
  Database, 
  Copy, 
  Check, 
  Terminal, 
  RefreshCw, 
  Tag, 
  Plus, 
  Download, 
  RotateCcw,
  ShieldCheck,
  CheckCircle2,
  MessageSquare,
  Lock,
  User,
  AlertTriangle
} from 'lucide-react';
import { Category } from '@/lib/types';
import { useToast } from '@/components/ui/Toast';
import { usePwa } from '@/components/pwa/PwaProvider';
import { useSms } from '@/components/sms/SmsProvider';

export default function SettingsPage() {
  const [copiedToken, setCopiedToken] = useState(false);
  const [copiedCurl, setCopiedCurl] = useState(false);
  const [health, setHealth] = useState<any>(null);
  const [isCheckingHealth, setIsCheckingHealth] = useState(false);
  const [categories, setCategories] = useState<Category[]>([]);
  const [newCatName, setNewCatName] = useState('');
  const [newCatColor, setNewCatColor] = useState('#2563EB');
  const [newCatIsIncome, setNewCatIsIncome] = useState(false);
  const [isAddingCategory, setIsAddingCategory] = useState(false);

  const [userName, setUserName] = useState('Krunal Baldha');
  const [isSavingUser, setIsSavingUser] = useState(false);

  const toast = useToast();
  const { isInstallable, isInstalled, isIos, triggerInstall } = usePwa();
  const { permissionState, isListening, requestPermission, revokePermission, openSyncModal } = useSms();
  const syncSecret = process.env.NEXT_PUBLIC_MONZI_SYNC_SECRET || '';

  const checkHealth = async () => {
    setIsCheckingHealth(true);
    try {
      const res = await fetch('/api/health');
      const data = await res.json();
      setHealth(data);
    } catch (e) {
      console.error(e);
    } finally {
      setIsCheckingHealth(false);
    }
  };

  const fetchCategories = async () => {
    try {
      const res = await fetch('/api/categories');
      if (res.ok) {
        const data = await res.json();
        setCategories(data.categories || []);
      }
    } catch (e) {
      console.error(e);
    }
  };

  const fetchUserProfile = async () => {
    try {
      const res = await fetch('/api/user');
      if (res.ok) {
        const data = await res.json();
        if (data.profile?.name) setUserName(data.profile.name);
      }
    } catch (e) {
      console.error(e);
    }
  };

  useEffect(() => {
    checkHealth();
    fetchCategories();
    fetchUserProfile();
  }, []);

  const copyToClipboard = (text: string, isCurl = false) => {
    navigator.clipboard.writeText(text);
    if (isCurl) {
      setCopiedCurl(true);
      setTimeout(() => setCopiedCurl(false), 2000);
      toast.success('cURL command copied to clipboard');
    } else {
      setCopiedToken(true);
      setTimeout(() => setCopiedToken(false), 2000);
      toast.success('Sync secret copied to clipboard');
    }
  };

  const handleUpdateUserName = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!userName.trim()) return;

    setIsSavingUser(true);
    try {
      const res = await fetch('/api/user', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: userName.trim() }),
      });
      if (res.ok) {
        toast.success('Profile updated');
      }
    } catch (e) {
      toast.error('Failed to update profile');
    } finally {
      setIsSavingUser(false);
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

      toast.success(`Category "${newCatName}" added`);
      setNewCatName('');
      fetchCategories();
    } catch (err: any) {
      toast.error(err.message || 'Error adding category');
    } finally {
      setIsAddingCategory(false);
    }
  };

  const handleClearCache = async () => {
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
    }, 800);
  };

  const curlSnippet = `curl -X POST ${typeof window !== 'undefined' ? window.location.origin : 'http://localhost:3000'}/api/sync \\
  -H "Content-Type: application/json" \\
  -H "x-monzi-secret: ${syncSecret}" \\
  -d '{
    "device_id": "android-galaxy-s24",
    "transactions": [{
      "transaction_date": "${new Date().toISOString()}",
      "amount": 420.00,
      "direction": "DEBIT",
      "bank_name": "HDFC Bank",
      "account_mask": "XX8421",
      "merchant": "Swiggy",
      "payment_mode": "UPI"
    }]
  }'`;

  const colorPresets = [
    '#2563EB', '#10B981', '#F59E0B', '#EF4444', 
    '#8B5CF6', '#EC4899', '#06B6D4', '#64748B'
  ];

  return (
    <div className="flex flex-col min-h-screen">
      <Header />

      <div className="p-4 sm:p-6 md:p-8 space-y-6 md:space-y-8 max-w-5xl mx-auto w-full">
        <div>
          <h2 className="text-lg sm:text-xl font-bold tracking-tight text-slate-900">Platform Settings</h2>
          <p className="text-[11px] sm:text-xs text-slate-500 font-medium">
            Supabase database status, SMS permissions, companion credentials, and PWA controls
          </p>
        </div>

        {/* 1. SMS Permission & Sync Management */}
        <GlassCard className="p-4 sm:p-6 space-y-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-2xl bg-blue-50 text-blue-600 flex items-center justify-center shrink-0">
                <MessageSquare className="w-5 h-5" />
              </div>
              <div>
                <h3 className="text-sm sm:text-base font-bold text-slate-900">Bank SMS Sync & Permissions</h3>
                <p className="text-[11px] sm:text-xs text-slate-500 font-medium">
                  Control automated debit/credit SMS reading and on-device parsing
                </p>
              </div>
            </div>

            <button
              onClick={openSyncModal}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-blue-600 text-white text-xs font-semibold shadow-sm hover:bg-blue-700 active:scale-95 transition-all"
            >
              <span>Sync / Test SMS</span>
            </button>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-1">
            <div className="p-4 rounded-xl bg-slate-50 border border-slate-200/80 space-y-2">
              <span className="text-[10px] font-semibold text-slate-500 uppercase tracking-wider block">
                SMS Reading Permission
              </span>
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <span className={`w-2.5 h-2.5 rounded-full ${
                    permissionState === 'granted' ? 'bg-emerald-500' : permissionState === 'denied' ? 'bg-rose-500' : 'bg-amber-500'
                  }`} />
                  <span className="text-xs font-bold text-slate-900">
                    {permissionState === 'granted' ? 'Permission Granted' : permissionState === 'denied' ? 'Permission Denied' : 'Not Configured'}
                  </span>
                </div>

                {permissionState === 'granted' ? (
                  <button
                    onClick={revokePermission}
                    className="px-2.5 py-1 text-[11px] font-semibold text-rose-600 hover:bg-rose-50 rounded-lg border border-rose-200"
                  >
                    Revoke
                  </button>
                ) : (
                  <button
                    onClick={requestPermission}
                    className="px-3 py-1 text-[11px] font-semibold bg-blue-600 text-white hover:bg-blue-700 rounded-lg shadow-sm"
                  >
                    Enable Sync
                  </button>
                )}
              </div>
            </div>

            <div className="p-4 rounded-xl bg-slate-50 border border-slate-200/80 space-y-2">
              <span className="text-[10px] font-semibold text-slate-500 uppercase tracking-wider block">
                WebOTP Live Receiver
              </span>
              <div className="flex items-center justify-between">
                <span className="text-xs text-slate-700 font-medium">
                  {isListening ? 'Actively Monitoring' : 'Standby / Manual Sync'}
                </span>
                <span className={`text-[10px] font-bold px-2 py-0.5 rounded ${
                  isListening ? 'bg-emerald-100 text-emerald-800' : 'bg-slate-200 text-slate-700'
                }`}>
                  {isListening ? 'ACTIVE' : 'READY'}
                </span>
              </div>
            </div>
          </div>
        </GlassCard>

        {/* 2. Supabase Database Connection & Health */}
        <GlassCard className="p-4 sm:p-6 space-y-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-2xl bg-emerald-50 text-emerald-600 flex items-center justify-center shrink-0">
                <Database className="w-5 h-5" />
              </div>
              <div>
                <h3 className="text-sm sm:text-base font-bold text-slate-900">Supabase Database Connection</h3>
                <p className="text-[11px] sm:text-xs text-slate-500 font-medium">
                  Live PostgreSQL database at <code className="font-mono bg-slate-100 px-1 py-0.5 rounded">hgxrqmkxxylfjgoqjkzp.supabase.co</code>
                </p>
              </div>
            </div>

            <button
              onClick={checkHealth}
              disabled={isCheckingHealth}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs font-semibold active:scale-95 transition-all"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${isCheckingHealth ? 'animate-spin' : ''}`} />
              <span>Ping DB</span>
            </button>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-4 gap-3 pt-1">
            <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200/80">
              <span className="text-[10px] font-semibold text-slate-500 uppercase tracking-wider block">Status</span>
              <div className="flex items-center gap-1.5 mt-1">
                <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
                <span className="text-xs font-bold text-emerald-700">Connected</span>
              </div>
            </div>

            <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200/80">
              <span className="text-[10px] font-semibold text-slate-500 uppercase tracking-wider block">Latency</span>
              <p className="text-xs font-bold font-mono text-slate-900 mt-1">
                {health?.latencyMs !== undefined ? `${health.latencyMs} ms` : 'Testing...'}
              </p>
            </div>

            <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200/80">
              <span className="text-[10px] font-semibold text-slate-500 uppercase tracking-wider block">Accounts</span>
              <p className="text-xs font-bold font-mono text-slate-900 mt-1">
                {health?.counts?.accounts !== undefined ? `${health.counts.accounts} active` : '...'}
              </p>
            </div>

            <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200/80">
              <span className="text-[10px] font-semibold text-slate-500 uppercase tracking-wider block">Transactions</span>
              <p className="text-xs font-bold font-mono text-slate-900 mt-1">
                {health?.counts?.transactions !== undefined ? `${health.counts.transactions} synced` : '...'}
              </p>
            </div>
          </div>
        </GlassCard>

        {/* 3. Category Management */}
        <GlassCard className="p-4 sm:p-6 space-y-4">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-2xl bg-indigo-50 text-indigo-600 flex items-center justify-center shrink-0">
              <Tag className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-sm sm:text-base font-bold text-slate-900">Categories Manager</h3>
              <p className="text-[11px] sm:text-xs text-slate-500 font-medium">
                Manage expense and income tags stored in Supabase
              </p>
            </div>
          </div>

          {/* Add Category Form */}
          <form onSubmit={handleAddCategory} className="p-3.5 rounded-2xl bg-slate-50 border border-slate-200/80 space-y-3">
            <span className="text-xs font-bold text-slate-800">Add New Category</span>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-2.5">
              <div>
                <input
                  type="text"
                  placeholder="Category Name (e.g. Travel, Gym, Rent)"
                  value={newCatName}
                  onChange={(e) => setNewCatName(e.target.value)}
                  className="w-full px-3 py-2 text-xs rounded-xl bg-white border border-slate-200 text-slate-900 font-medium focus:outline-none"
                />
              </div>

              <div className="flex items-center gap-1.5 overflow-x-auto">
                {colorPresets.map((c) => (
                  <button
                    type="button"
                    key={c}
                    onClick={() => setNewCatColor(c)}
                    className={`w-6 h-6 rounded-full shrink-0 transition-transform ${
                      newCatColor === c ? 'scale-125 ring-2 ring-slate-900 ring-offset-1' : 'opacity-80'
                    }`}
                    style={{ backgroundColor: c }}
                  />
                ))}
              </div>

              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => setNewCatIsIncome(!newCatIsIncome)}
                  className={`px-3 py-2 rounded-xl text-xs font-semibold flex-1 transition-all ${
                    newCatIsIncome ? 'bg-emerald-600 text-white' : 'bg-white border border-slate-200 text-slate-700'
                  }`}
                >
                  {newCatIsIncome ? 'Type: Income' : 'Type: Expense'}
                </button>
                <button
                  type="submit"
                  disabled={isAddingCategory}
                  className="px-4 py-2 rounded-xl bg-blue-600 text-white text-xs font-semibold shadow-sm hover:bg-blue-700 active:scale-95 disabled:opacity-50"
                >
                  {isAddingCategory ? 'Adding...' : 'Add'}
                </button>
              </div>
            </div>
          </form>

          {/* Categories Pill Grid */}
          <div className="flex flex-wrap gap-2 pt-1">
            {categories.map((c) => (
              <span
                key={c.id}
                className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold border"
                style={{
                  backgroundColor: `${c.color}15`,
                  borderColor: `${c.color}35`,
                  color: c.color,
                }}
              >
                <span className="w-2 h-2 rounded-full" style={{ backgroundColor: c.color }} />
                <span>{c.name}</span>
                {c.is_income && <span className="text-[10px] opacity-75">(Income)</span>}
              </span>
            ))}
          </div>
        </GlassCard>

        {/* 4. Android Companion Integration */}
        <GlassCard className="p-4 sm:p-6 space-y-4">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-2xl bg-blue-50 text-blue-600 flex items-center justify-center shrink-0">
              <Smartphone className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-sm sm:text-base font-bold text-slate-900">Android Companion Setup</h3>
              <p className="text-[11px] sm:text-xs text-slate-500 font-medium">
                Credentials for background SMS forwarders or automation daemons
              </p>
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-3 sm:gap-4 pt-1">
            <div className="p-3.5 sm:p-4 rounded-xl bg-slate-50 border border-slate-200/80 space-y-1.5">
              <span className="text-[10px] sm:text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                Sync Secret (Bearer Token)
              </span>
              <div className="flex items-center justify-between font-mono text-[11px] sm:text-xs font-bold text-slate-900 bg-white p-2.5 rounded-lg border border-slate-200 gap-2 overflow-hidden">
                <span className="truncate">{syncSecret}</span>
                <button
                  onClick={() => copyToClipboard(syncSecret)}
                  className="text-slate-500 hover:text-blue-600 p-1 shrink-0 active:scale-90"
                  title="Copy Token"
                >
                  {copiedToken ? <Check className="w-4 h-4 text-emerald-600" /> : <Copy className="w-4 h-4" />}
                </button>
              </div>
            </div>

            <div className="p-3.5 sm:p-4 rounded-xl bg-slate-50 border border-slate-200/80 space-y-1.5">
              <span className="text-[10px] sm:text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                Sync Endpoint
              </span>
              <div className="flex items-center justify-between font-mono text-xs font-bold text-slate-900 bg-white p-2.5 rounded-lg border border-slate-200">
                <span>/api/sync</span>
                <span className="text-[10px] text-emerald-600 font-bold bg-emerald-50 px-2 py-0.5 rounded border border-emerald-200">
                  POST Active
                </span>
              </div>
            </div>
          </div>

          {/* cURL Snippet */}
          <div className="pt-2">
            <div className="flex items-center justify-between mb-2">
              <div className="flex items-center gap-1.5 text-xs font-semibold text-slate-700">
                <Terminal className="w-3.5 h-3.5" />
                <span>Test Sync via Terminal (cURL)</span>
              </div>
              <button
                onClick={() => copyToClipboard(curlSnippet, true)}
                className="text-xs font-semibold text-blue-600 hover:text-blue-700 flex items-center gap-1"
              >
                {copiedCurl ? <Check className="w-3.5 h-3.5 text-emerald-600" /> : <Copy className="w-3.5 h-3.5" />}
                <span>{copiedCurl ? 'Copied' : 'Copy cURL'}</span>
              </button>
            </div>
            <pre className="p-3.5 rounded-xl bg-slate-900 text-slate-100 font-mono text-[11px] sm:text-xs overflow-x-auto leading-relaxed">
              {curlSnippet}
            </pre>
          </div>
        </GlassCard>

        {/* 5. Progressive Web App (PWA) & Offline Controls */}
        <GlassCard className="p-4 sm:p-6 space-y-4">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-2xl bg-violet-50 text-violet-600 flex items-center justify-center shrink-0">
              <Download className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-sm sm:text-base font-bold text-slate-900">Progressive Web App (PWA)</h3>
              <p className="text-[11px] sm:text-xs text-slate-500 font-medium">
                Install as a standalone app, manage offline cache and background service workers
              </p>
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-1">
            <div className="p-4 rounded-xl bg-slate-50 border border-slate-200/80 space-y-2">
              <span className="text-[10px] font-semibold text-slate-500 uppercase tracking-wider block">
                Installation Status
              </span>
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <span className={`w-2 h-2 rounded-full ${isInstalled ? 'bg-emerald-500' : 'bg-blue-500'}`} />
                  <span className="text-xs font-bold text-slate-900">
                    {isInstalled ? 'Installed (Standalone Mode)' : 'Running in Browser'}
                  </span>
                </div>
                {!isInstalled && (
                  <button
                    onClick={triggerInstall}
                    className="px-3 py-1.5 rounded-lg bg-blue-600 text-white text-xs font-semibold hover:bg-blue-700 shadow-sm active:scale-95"
                  >
                    Install Now
                  </button>
                )}
              </div>
            </div>

            <div className="p-4 rounded-xl bg-slate-50 border border-slate-200/80 space-y-2">
              <span className="text-[10px] font-semibold text-slate-500 uppercase tracking-wider block">
                Cache Management
              </span>
              <div className="flex items-center justify-between">
                <span className="text-xs text-slate-600">Service Worker v2 Active</span>
                <button
                  onClick={handleClearCache}
                  className="flex items-center gap-1 px-3 py-1.5 rounded-lg bg-slate-200 hover:bg-slate-300 text-slate-800 text-xs font-semibold transition-all active:scale-95"
                >
                  <RotateCcw className="w-3.5 h-3.5" />
                  <span>Clear Cache</span>
                </button>
              </div>
            </div>
          </div>
        </GlassCard>
      </div>
    </div>
  );
}
