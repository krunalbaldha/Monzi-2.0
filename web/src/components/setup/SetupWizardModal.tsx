'use client';

import React, { useCallback, useEffect, useState } from 'react';
import {
  X,
  CheckCircle2,
  AlertCircle,
  Clock,
  Smartphone,
  ShieldCheck,
  MailCheck,
  Cloud,
  Radio,
  Inbox,
  RefreshCw,
  Loader2,
  MessageSquare,
} from 'lucide-react';
import { useSms } from '@/components/sms/SmsProvider';
import { useAuth } from '@/components/auth/AuthProvider';
import { usePwa } from '@/components/pwa/PwaProvider';
import { useToast } from '@/components/ui/Toast';
import { loadOfflineSnapshot, saveOfflineSnapshot } from '@/lib/offlineStore';

interface SetupStatusData {
  account_created: boolean;
  email_verified: boolean;
  device_registered: boolean;
  sms_access_granted: boolean;
  historical_sync_status: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED';
  live_monitoring_active: boolean;
  cloud_sync_active: boolean;
  last_processed_at: string | null;
  last_sync_at: string | null;
  device_count: number;
}

const SETUP_STATUS_CACHE_KEY = 'setup_status';

export function SetupWizardModal() {
  const {
    isSetupModalOpen,
    closeSetupModal,
    openSyncModal,
    isAndroidNative,
    permissionState,
    isListening,
    isScanningInbox,
    historicalScanStatus,
    pendingLocalCount,
    lastSyncTime,
    requestPermission,
    scanAndroidInbox,
    syncPendingQueue,
  } = useSms();
  const { user } = useAuth();
  const { isOffline } = usePwa();
  const toast = useToast();

  const [statusData, setStatusData] = useState<SetupStatusData | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isSyncingNow, setIsSyncingNow] = useState(false);

  const fetchSetupStatus = useCallback(async () => {
    setIsLoading(true);
    try {
      const cached = await loadOfflineSnapshot<SetupStatusData>(SETUP_STATUS_CACHE_KEY);
      if (cached?.data) {
        setStatusData(cached.data);
      }

      if (typeof navigator !== 'undefined' && !navigator.onLine) {
        return;
      }

      const res = await fetch('/api/setup-status', {
        credentials: 'include',
        cache: 'no-store',
      });
      if (res.ok) {
        const data = (await res.json()) as SetupStatusData;
        setStatusData(data);
        await saveOfflineSnapshot(SETUP_STATUS_CACHE_KEY, data);
      }
    } catch {
      // Keep cached/fallback values without fabricating granted states
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    if (isSetupModalOpen) {
      void fetchSetupStatus();
    }
  }, [isSetupModalOpen, fetchSetupStatus]);

  if (!isSetupModalOpen) return null;

  const accountCreated = Boolean(statusData?.account_created ?? user?.id);
  const emailVerified = Boolean(statusData?.email_verified ?? user?.email_confirmed_at);
  const deviceRegistered = Boolean(statusData?.device_registered);
  const deviceCount = statusData?.device_count ?? 0;

  // Honest SMS access status: real Android permission OR honest browser guidance
  const nativeSmsGranted = isAndroidNative && permissionState === 'granted';
  const smsAccessGranted = isAndroidNative ? nativeSmsGranted : false;

  const effectiveHistoricalStatus = isAndroidNative
    ? historicalScanStatus
    : statusData?.historical_sync_status || historicalScanStatus || 'PENDING';

  const liveMonitoringActive = isAndroidNative
    ? nativeSmsGranted && isListening
    : Boolean(statusData?.live_monitoring_active);

  const cloudSyncActive = Boolean(
    !isOffline && accountCreated && (statusData?.cloud_sync_active ?? true)
  );

  const lastSyncDisplay = lastSyncTime
    ? lastSyncTime.toLocaleString('en-IN', {
        day: '2-digit',
        month: 'short',
        hour: '2-digit',
        minute: '2-digit',
      })
    : statusData?.last_sync_at
    ? new Date(statusData.last_sync_at).toLocaleString('en-IN', {
        day: '2-digit',
        month: 'short',
        hour: '2-digit',
        minute: '2-digit',
      })
    : 'No sync recorded yet';

  const handleSyncNow = async () => {
    if (isOffline) {
      toast.info('You are offline. Queued items will sync automatically when connected.');
      return;
    }
    setIsSyncingNow(true);
    try {
      const synced = await syncPendingQueue();
      if (synced > 0) {
        toast.success(`Synced ${synced} queued transaction${synced === 1 ? '' : 's'}.`);
      } else {
        toast.info('Sync trigger completed. Queue is up to date.');
      }
      await fetchSetupStatus();
    } finally {
      setIsSyncingNow(false);
    }
  };

  const renderBadge = (state: 'ok' | 'warn' | 'pending' | 'progress') => {
    if (state === 'ok') {
      return (
        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-emerald-100 text-emerald-800">
          <CheckCircle2 className="w-3 h-3" />
          Active
        </span>
      );
    }
    if (state === 'progress') {
      return (
        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-blue-100 text-blue-800">
          <Loader2 className="w-3 h-3 animate-spin" />
          In Progress
        </span>
      );
    }
    if (state === 'warn') {
      return (
        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-amber-100 text-amber-800">
          <AlertCircle className="w-3 h-3" />
          Action Needed
        </span>
      );
    }
    return (
      <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-slate-100 text-slate-600">
        <Clock className="w-3 h-3" />
        Pending
      </span>
    );
  };

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto bg-slate-900/55 backdrop-blur-md p-3 sm:p-6 flex items-center justify-center min-h-screen animate-fade-in">
      <div className="fixed inset-0 -z-10" onClick={closeSetupModal} aria-hidden="true" />

      <div className="relative w-full max-w-lg bg-white/95 backdrop-blur-2xl rounded-2xl sm:rounded-3xl p-5 sm:p-6 shadow-2xl border border-white/60 my-auto text-left animate-slide-up space-y-4">
        {/* Header */}
        <div className="flex items-center justify-between pb-3 border-b border-slate-100">
          <div className="flex items-center gap-2.5">
            <div className="w-10 h-10 rounded-2xl bg-gradient-to-tr from-blue-600 to-indigo-600 text-white flex items-center justify-center shadow-sm shadow-blue-500/25">
              <ShieldCheck className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-base font-bold text-slate-900">Setup &amp; Automation Checklist</h3>
              <p className="text-[11px] text-slate-500 font-medium">
                Live status across auth, device, SMS capture &amp; cloud sync
              </p>
            </div>
          </div>

          <div className="flex items-center gap-1">
            <button
              type="button"
              onClick={() => void fetchSetupStatus()}
              disabled={isLoading}
              className="p-2 rounded-full text-slate-400 hover:text-blue-600 hover:bg-blue-50 transition-all"
              aria-label="Refresh status"
              title="Refresh checklist"
            >
              <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin text-blue-600' : ''}`} />
            </button>
            <button
              type="button"
              onClick={closeSetupModal}
              className="p-1.5 rounded-full text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition-all"
              aria-label="Close"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        {/* Checklist Items */}
        <div className="space-y-2.5 max-h-[65vh] overflow-y-auto pr-0.5">
          {/* 1. Account Created */}
          <div className="p-3 rounded-2xl bg-slate-50/90 border border-slate-200/70 flex items-center justify-between gap-3">
            <div className="flex items-center gap-3 min-w-0">
              <div className="w-8 h-8 rounded-xl bg-white border border-slate-200/80 flex items-center justify-center text-blue-600 shrink-0">
                <ShieldCheck className="w-4 h-4" />
              </div>
              <div className="min-w-0">
                <p className="text-xs font-bold text-slate-900">Account Created</p>
                <p className="text-[11px] text-slate-500 truncate">
                  {user?.email || 'Authenticated profile active'}
                </p>
              </div>
            </div>
            {renderBadge(accountCreated ? 'ok' : 'warn')}
          </div>

          {/* 2. Email Verified */}
          <div className="p-3 rounded-2xl bg-slate-50/90 border border-slate-200/70 flex items-center justify-between gap-3">
            <div className="flex items-center gap-3 min-w-0">
              <div className="w-8 h-8 rounded-xl bg-white border border-slate-200/80 flex items-center justify-center text-indigo-600 shrink-0">
                <MailCheck className="w-4 h-4" />
              </div>
              <div className="min-w-0">
                <p className="text-xs font-bold text-slate-900">Email Verified</p>
                <p className="text-[11px] text-slate-500">
                  {emailVerified
                    ? 'Email address confirmed'
                    : 'Verify your inbox link to secure your account'}
                </p>
              </div>
            </div>
            {renderBadge(emailVerified ? 'ok' : 'warn')}
          </div>

          {/* 3. Device Registered */}
          <div className="p-3 rounded-2xl bg-slate-50/90 border border-slate-200/70 flex items-center justify-between gap-3">
            <div className="flex items-center gap-3 min-w-0">
              <div className="w-8 h-8 rounded-xl bg-white border border-slate-200/80 flex items-center justify-center text-violet-600 shrink-0">
                <Smartphone className="w-4 h-4" />
              </div>
              <div className="min-w-0">
                <p className="text-xs font-bold text-slate-900">Device Registered</p>
                <p className="text-[11px] text-slate-500">
                  {deviceRegistered
                    ? `${deviceCount} registered device${deviceCount === 1 ? '' : 's'} linked`
                    : 'Device registration pending sync'}
                </p>
              </div>
            </div>
            {renderBadge(deviceRegistered ? 'ok' : 'pending')}
          </div>

          {/* 4. SMS Access Granted */}
          <div className="p-3 rounded-2xl bg-slate-50/90 border border-slate-200/70 space-y-2">
            <div className="flex items-center justify-between gap-3">
              <div className="flex items-center gap-3 min-w-0">
                <div className="w-8 h-8 rounded-xl bg-white border border-slate-200/80 flex items-center justify-center text-emerald-600 shrink-0">
                  <MessageSquare className="w-4 h-4" />
                </div>
                <div className="min-w-0">
                  <p className="text-xs font-bold text-slate-900">SMS Access Granted</p>
                  <p className="text-[11px] text-slate-500">
                    {isAndroidNative
                      ? nativeSmsGranted
                        ? 'READ_SMS & RECEIVE_SMS granted on Android'
                        : permissionState === 'denied'
                        ? 'SMS permission denied on Android'
                        : 'Grant Android SMS permission for auto-capture'
                      : 'Browser Mode — background SMS requires Monzi Android App'}
                  </p>
                </div>
              </div>
              {renderBadge(smsAccessGranted ? 'ok' : isAndroidNative ? 'warn' : 'pending')}
            </div>
            {isAndroidNative && !nativeSmsGranted && (
              <button
                type="button"
                onClick={() => {
                  closeSetupModal();
                  requestPermission();
                }}
                className="w-full py-2 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white text-[11px] font-bold transition-all"
              >
                Grant Android SMS Permission
              </button>
            )}
          </div>

          {/* 5. Historical Sync Status */}
          <div className="p-3 rounded-2xl bg-slate-50/90 border border-slate-200/70 space-y-2">
            <div className="flex items-center justify-between gap-3">
              <div className="flex items-center gap-3 min-w-0">
                <div className="w-8 h-8 rounded-xl bg-white border border-slate-200/80 flex items-center justify-center text-amber-600 shrink-0">
                  <Inbox className="w-4 h-4" />
                </div>
                <div className="min-w-0">
                  <p className="text-xs font-bold text-slate-900">Historical SMS Import</p>
                  <p className="text-[11px] text-slate-500">
                    Status: <span className="font-semibold text-slate-700">{effectiveHistoricalStatus}</span>
                  </p>
                </div>
              </div>
              {renderBadge(
                effectiveHistoricalStatus === 'COMPLETED'
                  ? 'ok'
                  : effectiveHistoricalStatus === 'IN_PROGRESS' || isScanningInbox
                  ? 'progress'
                  : 'pending'
              )}
            </div>
            {isAndroidNative && (
              <button
                type="button"
                onClick={() => scanAndroidInbox(500)}
                disabled={isScanningInbox}
                className="w-full py-2 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-[11px] font-bold transition-all flex items-center justify-center gap-1.5 disabled:opacity-60"
              >
                {isScanningInbox ? (
                  <Loader2 className="w-3.5 h-3.5 animate-spin" />
                ) : (
                  <Inbox className="w-3.5 h-3.5" />
                )}
                <span>{isScanningInbox ? 'Scanning Inbox...' : 'Scan History'}</span>
              </button>
            )}
          </div>

          {/* 6. Live Monitoring Active */}
          <div className="p-3 rounded-2xl bg-slate-50/90 border border-slate-200/70 flex items-center justify-between gap-3">
            <div className="flex items-center gap-3 min-w-0">
              <div className="w-8 h-8 rounded-xl bg-white border border-slate-200/80 flex items-center justify-center text-teal-600 shrink-0">
                <Radio className="w-4 h-4" />
              </div>
              <div className="min-w-0">
                <p className="text-xs font-bold text-slate-900">Live Monitoring Active</p>
                <p className="text-[11px] text-slate-500">
                  {liveMonitoringActive
                    ? 'Background SMS receiver listening in real time'
                    : isAndroidNative
                    ? 'Enable SMS permission to activate real-time monitoring'
                    : 'Available on Android Companion App (use Paste/Bulk in browser)'}
                </p>
              </div>
            </div>
            {renderBadge(liveMonitoringActive ? 'ok' : 'pending')}
          </div>

          {/* 7. Cloud Sync Active & Pending Queue */}
          <div className="p-3 rounded-2xl bg-slate-50/90 border border-slate-200/70 space-y-2.5">
            <div className="flex items-center justify-between gap-3">
              <div className="flex items-center gap-3 min-w-0">
                <div className="w-8 h-8 rounded-xl bg-white border border-slate-200/80 flex items-center justify-center text-sky-600 shrink-0">
                  <Cloud className="w-4 h-4" />
                </div>
                <div className="min-w-0">
                  <p className="text-xs font-bold text-slate-900">Cloud Sync &amp; Local Queue</p>
                  <p className="text-[11px] text-slate-500">
                    Pending queue: <span className="font-bold text-slate-800">{pendingLocalCount}</span> • Last: {lastSyncDisplay}
                  </p>
                </div>
              </div>
              {renderBadge(isOffline ? 'warn' : cloudSyncActive ? 'ok' : 'pending')}
            </div>

            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={handleSyncNow}
                disabled={isSyncingNow || isOffline}
                className="flex-1 py-2 rounded-xl bg-slate-900 hover:bg-slate-800 text-white text-[11px] font-bold transition-all flex items-center justify-center gap-1.5 disabled:opacity-50"
              >
                {isSyncingNow ? (
                  <Loader2 className="w-3.5 h-3.5 animate-spin" />
                ) : (
                  <RefreshCw className="w-3.5 h-3.5" />
                )}
                <span>{isSyncingNow ? 'Syncing...' : 'Sync Now'}</span>
              </button>
              <button
                type="button"
                onClick={() => {
                  closeSetupModal();
                  openSyncModal();
                }}
                className="flex-1 py-2 rounded-xl bg-white border border-slate-200 text-slate-700 hover:bg-slate-100 text-[11px] font-bold transition-all"
              >
                Open SMS Sync Center
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
