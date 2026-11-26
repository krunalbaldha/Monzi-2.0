'use client';

import React, {
  createContext,
  useContext,
  useState,
  useEffect,
  useCallback,
  useRef,
  useMemo,
} from 'react';
import { parseBankSms } from '@/lib/parser';
import type { ParsedTransaction } from '@/lib/types';
import {
  enqueueBrowserSync,
  flushBrowserSyncQueue,
  getPendingBrowserSyncCount,
} from '@/lib/offlineStore';
import { useToast } from '@/components/ui/Toast';
import { useAuth } from '@/components/auth/AuthProvider';
import { createBrowserSupabaseClient } from '@/lib/supabase/client';

export type SmsPermissionState = 'prompt' | 'granted' | 'denied' | 'unsupported';
export type HistoricalScanStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED';

interface MonziNativeBridge {
  isAndroidApp: () => boolean;
  getSmsPermissionState: () => string;
  requestSmsPermission: () => void;
  setUserSyncAuth: (serverUrl: string, syncToken: string, accessToken: string) => void;
  scanHistoricalSms: (limit: number) => void;
  openNativeSettings?: () => void;
  getPendingSyncCount?: () => number;
  getHistoricalSyncStatus?: () => string;
  getLastProcessedAt?: () => number;
  triggerSyncNow?: () => void;
  getAppVersion?: () => string;
}

declare global {
  interface Window {
    MonziNative?: MonziNativeBridge;
    onMonziNativeSmsScanComplete?: (scanned: number, detected: number) => void;
  }
}

interface SmsContextType {
  permissionState: SmsPermissionState;
  isListening: boolean;
  isAndroidNative: boolean;
  isScanningInbox: boolean;
  isPermissionModalOpen: boolean;
  isSyncModalOpen: boolean;
  isSetupModalOpen: boolean;
  lastSyncTime: Date | null;
  autoSyncCount: number;
  pendingLocalCount: number;
  historicalScanStatus: HistoricalScanStatus;
  requestPermission: () => void;
  grantPermission: () => Promise<void>;
  denyPermission: () => Promise<void>;
  revokePermission: () => Promise<void>;
  openSyncModal: () => void;
  closeSyncModal: () => void;
  openSetupModal: () => void;
  closeSetupModal: () => void;
  closePermissionModal: () => void;
  scanAndroidInbox: (limit?: number) => void;
  openNativeSettings: () => void;
  syncPendingQueue: () => Promise<number>;
  ingestSmsText: (rawText: string) => Promise<{ success: boolean; transaction?: any; error?: string }>;
  processSmsText: (rawSms: string, sender?: string) => Promise<ParsedTransaction | null>;
}

const SmsContext = createContext<SmsContextType | undefined>(undefined);

const LAST_SYNC_STORAGE_KEY = 'monzi_last_sync';
const DEVICE_IDENTIFIER_STORAGE_KEY = 'monzi_device_identifier';

function getPermissionStorageKey(userId?: string | null) {
  return userId ? `monzi_sms_permission_${userId}` : 'monzi_sms_permission_guest';
}

function normalizeScanStatus(raw?: string | null): HistoricalScanStatus {
  const upper = (raw || '').toUpperCase();
  if (upper === 'COMPLETED') return 'COMPLETED';
  if (upper === 'IN_PROGRESS') return 'IN_PROGRESS';
  return 'PENDING';
}

function getOrCreateDeviceIdentifier(isNative: boolean): string {
  if (typeof window === 'undefined') return 'server';
  try {
    const existing = localStorage.getItem(DEVICE_IDENTIFIER_STORAGE_KEY);
    if (existing) return existing;
    const prefix = isNative ? 'android' : 'pwa';
    const rand =
      typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
        ? crypto.randomUUID()
        : `${Date.now()}_${Math.random().toString(36).slice(2, 10)}`;
    const next = `${prefix}-${rand}`;
    localStorage.setItem(DEVICE_IDENTIFIER_STORAGE_KEY, next);
    return next;
  } catch {
    return isNative ? 'android-webview' : 'pwa-browser';
  }
}

function detectPlatform(isNative: boolean): string {
  if (isNative) return 'android-app';
  if (typeof navigator === 'undefined') return 'web';
  const ua = navigator.userAgent || '';
  if (/iPhone|iPad|iPod/i.test(ua)) return 'ios-pwa';
  if (/Android/i.test(ua)) return 'android-pwa';
  if (/Windows/i.test(ua)) return 'windows-web';
  if (/Mac/i.test(ua)) return 'mac-web';
  return 'web-pwa';
}

export function SmsProvider({ children }: { children: React.ReactNode }) {
  const [permissionState, setPermissionState] = useState<SmsPermissionState>('prompt');
  const [isListening, setIsListening] = useState(false);
  const [isAndroidNative, setIsAndroidNative] = useState(false);
  const [isScanningInbox, setIsScanningInbox] = useState(false);
  const [isPermissionModalOpen, setIsPermissionModalOpen] = useState(false);
  const [isSyncModalOpen, setIsSyncModalOpen] = useState(false);
  const [isSetupModalOpen, setIsSetupModalOpen] = useState(false);
  const [lastSyncTime, setLastSyncTime] = useState<Date | null>(null);
  const [autoSyncCount, setAutoSyncCount] = useState<number>(0);
  const [pendingLocalCount, setPendingLocalCount] = useState<number>(0);
  const [historicalScanStatus, setHistoricalScanStatus] =
    useState<HistoricalScanStatus>('PENDING');

  const abortControllerRef = useRef<AbortController | null>(null);
  const registeredDeviceUserRef = useRef<string | null>(null);
  const toast = useToast();
  const { user, profile } = useAuth();
  const supabase = useMemo(() => createBrowserSupabaseClient(), []);

  const refreshPendingLocalCount = useCallback(async () => {
    const browserCount = await getPendingBrowserSyncCount();
    const nativeCount =
      typeof window !== 'undefined' ? window.MonziNative?.getPendingSyncCount?.() || 0 : 0;
    setPendingLocalCount(browserCount + nativeCount);
  }, []);

  const recordSyncSuccess = useCallback(
    (count: number = 1, emitEvent: boolean = true) => {
      const now = new Date();
      setLastSyncTime(now);
      try {
        localStorage.setItem(LAST_SYNC_STORAGE_KEY, now.toISOString());
      } catch {
        // Ignore storage quota errors
      }
      if (count > 0) {
        setAutoSyncCount((prev) => prev + count);
      }
      if (emitEvent && typeof window !== 'undefined' && count > 0) {
        window.dispatchEvent(
          new CustomEvent('monzi:sms-synced', { detail: { internal: true, count } })
        );
      }
      void refreshPendingLocalCount();
    },
    [refreshPendingLocalCount]
  );

  const persistUserSmsPermission = useCallback(
    async (state: SmsPermissionState) => {
      try {
        localStorage.setItem(getPermissionStorageKey(user?.id), state);
      } catch {
        // ignore
      }
      if (!user) return;
      try {
        await fetch('/api/user', {
          method: 'POST',
          credentials: 'include',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ sms_permission: state }),
        });
      } catch {
        // Best-effort sync to profile
      }
    },
    [user]
  );

  const queueOfflinePayload = useCallback(
    async (syncPayload: Record<string, unknown>) => {
      await enqueueBrowserSync(syncPayload);
      await refreshPendingLocalCount();
    },
    [refreshPendingLocalCount]
  );

  const processSmsText = useCallback(
    async (rawSms: string, sender?: string): Promise<ParsedTransaction | null> => {
      if (!rawSms || !rawSms.trim()) return null;

      const parsed = parseBankSms(rawSms, sender || '');
      if (!parsed) return null;

      const syncPayload: Record<string, unknown> = {
        device_id: isAndroidNative ? 'android-webview' : 'pwa-sms-receiver',
        transactions: [
          {
            transaction_date: parsed.transaction_date || new Date().toISOString(),
            sender: sender || parsed.bank_name,
            amount: parsed.amount,
            direction: parsed.direction,
            transaction_type: parsed.transaction_type,
            transaction_category: parsed.transaction_category,
            bank_name: parsed.bank_name,
            account_mask: parsed.account_mask,
            merchant: parsed.merchant || 'Bank Transaction',
            payment_mode: parsed.payment_mode || 'UPI',
            reference_id: parsed.reference_id,
            parsed_available_balance: parsed.parsed_available_balance,
            is_self_transfer: parsed.is_self_transfer,
            is_refund: parsed.is_refund,
            transaction_source: 'INCOMING_SMS',
            raw_body: rawSms,
          },
        ],
      };

      if (typeof navigator !== 'undefined' && !navigator.onLine) {
        await queueOfflinePayload(syncPayload);
        toast.info('Offline mode: queued locally and will sync automatically when online.');
        return { ...parsed, is_duplicate: false };
      }

      try {
        const res = await fetch('/api/sync', {
          method: 'POST',
          credentials: 'include',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify(syncPayload),
        });

        if (res.status === 503) {
          await queueOfflinePayload(syncPayload);
          toast.info('Network unavailable: queued locally for automatic sync.');
          return { ...parsed, is_duplicate: false };
        }

        if (!res.ok) {
          return null;
        }

        const data = await res.json();
        if (data?.offline) {
          await queueOfflinePayload(syncPayload);
          toast.info('Network unavailable: queued locally for automatic sync.');
          return { ...parsed, is_duplicate: false };
        }

        const isDup = (data.duplicate_count || 0) > 0 && (data.processed_count || 0) === 0;
        if ((data.processed_count || 0) > 0) {
          recordSyncSuccess(data.processed_count, true);
        }

        return {
          ...parsed,
          is_duplicate: isDup,
        };
      } catch (err) {
        console.warn('[SmsProvider] processSmsText offline fallback:', err);
        await queueOfflinePayload(syncPayload);
        toast.info('Network unreachable: queued locally and will sync when online.');
        return { ...parsed, is_duplicate: false };
      }
    },
    [isAndroidNative, queueOfflinePayload, recordSyncSuccess, toast]
  );

  const ingestSmsText = useCallback(
    async (rawText: string) => {
      if (!rawText || !rawText.trim()) {
        return { success: false, error: 'Empty SMS content' };
      }

      const parsed = parseBankSms(rawText);
      if (!parsed) {
        return {
          success: false,
          error:
            'No valid CREDIT or DEBIT transaction detected. OTPs, promos, balance alerts, and failed attempts are filtered out.',
        };
      }

      const syncPayload: Record<string, unknown> = {
        device_id: isAndroidNative ? 'android-webview' : 'pwa-sms-receiver',
        transactions: [
          {
            transaction_date: parsed.transaction_date || new Date().toISOString(),
            amount: parsed.amount,
            direction: parsed.direction,
            transaction_type: parsed.transaction_type,
            transaction_category: parsed.transaction_category,
            bank_name: parsed.bank_name,
            account_mask: parsed.account_mask,
            merchant: parsed.merchant || 'Bank Transaction',
            payment_mode: parsed.payment_mode || 'UPI',
            reference_id: parsed.reference_id,
            parsed_available_balance: parsed.parsed_available_balance,
            is_self_transfer: parsed.is_self_transfer,
            is_refund: parsed.is_refund,
            transaction_source: 'INCOMING_SMS',
            raw_body: rawText,
          },
        ],
      };

      if (typeof navigator !== 'undefined' && !navigator.onLine) {
        await queueOfflinePayload(syncPayload);
        return {
          success: true,
          transaction: { queued_offline: true, processed_count: 0 },
        };
      }

      try {
        const res = await fetch('/api/sync', {
          method: 'POST',
          credentials: 'include',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify(syncPayload),
        });

        const data = await res.json();
        if (res.status === 503 || data?.offline) {
          await queueOfflinePayload(syncPayload);
          return {
            success: true,
            transaction: { queued_offline: true, processed_count: 0 },
          };
        }

        if (!res.ok) {
          throw new Error(data.error || 'Sync ingestion failed');
        }

        if ((data.processed_count || 0) > 0) {
          recordSyncSuccess(data.processed_count, true);
        }
        return { success: true, transaction: data };
      } catch (err: any) {
        const isNetworkError =
          err instanceof TypeError ||
          /network|fetch|offline|failed to fetch/i.test(err?.message || '');
        if (isNetworkError) {
          await queueOfflinePayload(syncPayload);
          return {
            success: true,
            transaction: { queued_offline: true, processed_count: 0 },
          };
        }
        return { success: false, error: err.message || 'Network error syncing SMS' };
      }
    },
    [isAndroidNative, queueOfflinePayload, recordSyncSuccess]
  );

  const syncPendingQueue = useCallback(async (): Promise<number> => {
    let totalSynced = 0;
    try {
      const { synced } = await flushBrowserSyncQueue();
      totalSynced += synced;
      if (typeof window !== 'undefined' && window.MonziNative?.triggerSyncNow) {
        window.MonziNative.triggerSyncNow();
      }
      if (synced > 0) {
        recordSyncSuccess(synced, true);
      } else {
        await refreshPendingLocalCount();
      }
    } catch {
      await refreshPendingLocalCount();
    }
    return totalSynced;
  }, [recordSyncSuccess, refreshPendingLocalCount]);

  const stopSmsReceiver = useCallback(() => {
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
      abortControllerRef.current = null;
    }
    setIsListening(false);
  }, []);

  const startSmsReceiver = useCallback(() => {
    if (typeof window === 'undefined') return;

    if (window.MonziNative?.isAndroidApp?.()) {
      const nativeState = window.MonziNative.getSmsPermissionState?.();
      setIsListening(nativeState === 'granted');
      return;
    }

    if ('credentials' in navigator && (navigator as any).credentials?.get) {
      setIsListening(true);
      abortControllerRef.current = new AbortController();

      (navigator.credentials as any)
        .get({
          otp: { transport: ['sms'] },
          signal: abortControllerRef.current.signal,
        })
        .then(async (content: any) => {
          if (content && content.code) {
            await processSmsText(content.code);
            startSmsReceiver();
          }
        })
        .catch((err: any) => {
          if (err.name !== 'AbortError') {
            console.log('[WebOTP] SMS listener paused:', err.message);
          }
          setIsListening(false);
        });
    } else {
      setIsListening(false);
    }
  }, [processSmsText]);

  // Detect Android native bridge, hydrate queue count, and handle native events
  useEffect(() => {
    if (typeof window === 'undefined') return;

    const nativeAvailable = Boolean(window.MonziNative?.isAndroidApp?.());
    setIsAndroidNative(nativeAvailable);

    if (nativeAvailable && window.MonziNative) {
      const nativeState = window.MonziNative.getSmsPermissionState?.();
      if (nativeState === 'granted' || nativeState === 'denied' || nativeState === 'prompt') {
        setPermissionState(nativeState);
        setIsListening(nativeState === 'granted');
      }
      const nativeScanStatus = window.MonziNative.getHistoricalSyncStatus?.();
      if (nativeScanStatus) {
        setHistoricalScanStatus(normalizeScanStatus(nativeScanStatus));
      }
    }

    void refreshPendingLocalCount();

    window.onMonziNativeSmsScanComplete = (scanned: number, detected: number) => {
      setIsScanningInbox(false);
      setHistoricalScanStatus('COMPLETED');
      if (detected > 0) {
        recordSyncSuccess(detected, false);
        toast.success(`Scanned ${scanned} SMS messages. Queued ${detected} financial transaction(s).`);
      } else {
        void refreshPendingLocalCount();
        toast.info(`Scanned ${scanned} SMS messages. No new financial transactions found.`);
      }
      // Persist completion best-effort
      void fetch('/api/setup-status', {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          device_id: 'android-webview',
          historical_scan_status: 'COMPLETED',
          monitoring_status: 'ACTIVE',
          last_processed_at: new Date().toISOString(),
        }),
      }).catch(() => {});
    };

    const handleNativePermissionChanged = (event: Event) => {
      const customEvent = event as CustomEvent<{ state?: SmsPermissionState }>;
      const nextState = customEvent.detail?.state;
      if (nextState === 'granted' || nextState === 'denied' || nextState === 'prompt') {
        setPermissionState(nextState);
        setIsListening(nextState === 'granted');
        void persistUserSmsPermission(nextState);
        if (nextState === 'granted') {
          toast.success('Android SMS permissions granted. Real-time sync is active.');
        } else if (nextState === 'denied') {
          toast.info('Android SMS permission was denied. You can still paste SMS manually.');
        }
      }
    };

    const handleSmsSyncedEvent = (event: Event) => {
      const customEvent = event as CustomEvent<{ internal?: boolean; count?: number }>;
      if (!customEvent.detail?.internal) {
        recordSyncSuccess(customEvent.detail?.count ?? 1, false);
      } else {
        void refreshPendingLocalCount();
      }
    };

    const handleOnline = () => {
      void refreshPendingLocalCount();
    };

    window.addEventListener('monzi:sms-permission-changed', handleNativePermissionChanged);
    window.addEventListener('monzi:sms-synced', handleSmsSyncedEvent);
    window.addEventListener('online', handleOnline);

    return () => {
      window.removeEventListener('monzi:sms-permission-changed', handleNativePermissionChanged);
      window.removeEventListener('monzi:sms-synced', handleSmsSyncedEvent);
      window.removeEventListener('online', handleOnline);
      delete window.onMonziNativeSmsScanComplete;
    };
  }, [persistUserSmsPermission, recordSyncSuccess, refreshPendingLocalCount, toast]);

  // Register device once per authenticated user & hydrate historicalScanStatus
  useEffect(() => {
    if (typeof window === 'undefined' || !user?.id) return;
    if (registeredDeviceUserRef.current === user.id) return;
    registeredDeviceUserRef.current = user.id;

    const nativeAvailable = Boolean(window.MonziNative?.isAndroidApp?.());
    const deviceIdentifier = getOrCreateDeviceIdentifier(nativeAvailable);
    const platform = detectPlatform(nativeAvailable);
    const appVersion = window.MonziNative?.getAppVersion?.() || '2.0.0';

    void fetch('/api/devices', {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        device_identifier: deviceIdentifier,
        platform,
        app_version: appVersion,
      }),
    }).catch(() => {
      // Best-effort device registration (offline-safe)
    });

    if (!nativeAvailable) {
      void fetch('/api/setup-status', { credentials: 'include' })
        .then((res) => (res.ok ? res.json() : null))
        .then((data) => {
          if (data?.historical_sync_status) {
            setHistoricalScanStatus(normalizeScanStatus(data.historical_sync_status));
          }
        })
        .catch(() => {});
    }
  }, [user?.id]);

  // Sync user's isolated auth credentials to Android native SharedPreferences
  useEffect(() => {
    if (typeof window === 'undefined' || !user) return;
    if (!window.MonziNative?.isAndroidApp?.()) return;

    let cancelled = false;
    const syncAuthToNative = async () => {
      try {
        let deviceSyncToken = profile?.device_sync_token || '';
        if (!deviceSyncToken) {
          const userRes = await fetch('/api/user', { credentials: 'include' });
          if (userRes.ok) {
            const userData = await userRes.json();
            deviceSyncToken = userData.profile?.device_sync_token || '';
          }
        }

        const {
          data: { session },
        } = await supabase.auth.getSession();
        const accessToken = session?.access_token || '';

        if (!cancelled && window.MonziNative?.setUserSyncAuth) {
          window.MonziNative.setUserSyncAuth(
            `${window.location.origin}/api/sync`,
            deviceSyncToken,
            accessToken
          );
        }
      } catch {
        // Ignore bridge auth sync error
      }
    };

    void syncAuthToNative();
    return () => {
      cancelled = true;
    };
  }, [profile?.device_sync_token, supabase, user]);

  // Initialize permission state and lastSyncTime per user (never repeatedly nag once decided)
  useEffect(() => {
    try {
      const savedSync = localStorage.getItem(LAST_SYNC_STORAGE_KEY);
      if (savedSync) {
        const parsedDate = new Date(savedSync);
        if (!isNaN(parsedDate.getTime())) {
          setLastSyncTime(parsedDate);
        }
      }
    } catch {
      // ignore
    }

    if (typeof window !== 'undefined' && window.MonziNative?.isAndroidApp?.()) {
      const nativeState = window.MonziNative.getSmsPermissionState?.() as SmsPermissionState;
      if (nativeState === 'granted' || nativeState === 'denied' || nativeState === 'prompt') {
        setPermissionState(nativeState);
        setIsListening(nativeState === 'granted');
      }
      return;
    }

    const storageKey = getPermissionStorageKey(user?.id);
    const saved = localStorage.getItem(storageKey) as SmsPermissionState | null;
    const profileState = profile?.sms_permission;

    const resolvedState =
      profileState && profileState !== 'prompt'
        ? profileState
        : saved === 'granted' || saved === 'denied'
        ? saved
        : 'prompt';

    setPermissionState(resolvedState);
    if (resolvedState === 'granted') {
      startSmsReceiver();
    } else {
      stopSmsReceiver();
    }

    return () => {
      stopSmsReceiver();
    };
  }, [profile?.sms_permission, startSmsReceiver, stopSmsReceiver, user?.id]);

  const requestPermission = () => {
    if (isAndroidNative && window.MonziNative?.getSmsPermissionState?.() === 'granted') {
      setPermissionState('granted');
      setIsListening(true);
      setIsSyncModalOpen(true);
      return;
    }
    setIsPermissionModalOpen(true);
  };

  const grantPermission = async () => {
    setIsPermissionModalOpen(false);

    if (isAndroidNative && window.MonziNative) {
      const currentNative = window.MonziNative.getSmsPermissionState?.();
      if (currentNative === 'granted') {
        setPermissionState('granted');
        setIsListening(true);
        await persistUserSmsPermission('granted');
        toast.success('Android SMS Sync is active.');
        return;
      }
      window.MonziNative.requestSmsPermission();
      return;
    }

    setPermissionState('granted');
    await persistUserSmsPermission('granted');
    toast.success(
      'Browser SMS Sync enabled. Use Paste/Bulk SMS or install the Monzi Android App for background SMS sync.'
    );
    startSmsReceiver();
  };

  const denyPermission = async () => {
    setPermissionState('denied');
    setIsPermissionModalOpen(false);
    stopSmsReceiver();
    await persistUserSmsPermission('denied');
    toast.info('SMS permission skipped. You can still paste bank messages manually.');
  };

  const revokePermission = async () => {
    setPermissionState('prompt');
    try {
      localStorage.removeItem(getPermissionStorageKey(user?.id));
    } catch {
      // ignore
    }
    stopSmsReceiver();
    await persistUserSmsPermission('prompt');
    toast.info('SMS permission preference reset.');
  };

  const scanAndroidInbox = useCallback(
    (limit: number = 500) => {
      if (!isAndroidNative || !window.MonziNative) {
        toast.info('Inbox scanning requires the Monzi Android App.');
        return;
      }
      const nativeState = window.MonziNative.getSmsPermissionState?.();
      if (nativeState !== 'granted') {
        window.MonziNative.requestSmsPermission();
        return;
      }
      setIsScanningInbox(true);
      setHistoricalScanStatus('IN_PROGRESS');
      window.MonziNative.scanHistoricalSms(limit);
    },
    [isAndroidNative, toast]
  );

  const openNativeSettings = useCallback(() => {
    if (isAndroidNative && window.MonziNative?.openNativeSettings) {
      window.MonziNative.openNativeSettings();
    }
  }, [isAndroidNative]);

  const openSyncModal = () => {
    void refreshPendingLocalCount();
    if (permissionState === 'prompt') {
      setIsPermissionModalOpen(true);
    } else {
      setIsSyncModalOpen(true);
    }
  };

  const closeSyncModal = () => setIsSyncModalOpen(false);

  const openSetupModal = () => {
    void refreshPendingLocalCount();
    setIsSetupModalOpen(true);
  };

  const closeSetupModal = () => setIsSetupModalOpen(false);
  const closePermissionModal = () => setIsPermissionModalOpen(false);

  return (
    <SmsContext.Provider
      value={{
        permissionState,
        isListening,
        isAndroidNative,
        isScanningInbox,
        isPermissionModalOpen,
        isSyncModalOpen,
        isSetupModalOpen,
        lastSyncTime,
        autoSyncCount,
        pendingLocalCount,
        historicalScanStatus,
        requestPermission,
        grantPermission,
        denyPermission,
        revokePermission,
        openSyncModal,
        closeSyncModal,
        openSetupModal,
        closeSetupModal,
        closePermissionModal,
        scanAndroidInbox,
        openNativeSettings,
        syncPendingQueue,
        ingestSmsText,
        processSmsText,
      }}
    >
      {children}
    </SmsContext.Provider>
  );
}

export function useSms() {
  const context = useContext(SmsContext);
  if (!context) {
    throw new Error('useSms must be used within an SmsProvider');
  }
  return context;
}
