'use client';

import React, { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react';
import { parseBankSms } from '@/lib/parser';
import type { ParsedTransaction } from '@/lib/types';
import { useToast } from '@/components/ui/Toast';

export type SmsPermissionState = 'prompt' | 'granted' | 'denied' | 'unsupported';

interface SmsContextType {
  permissionState: SmsPermissionState;
  isListening: boolean;
  isPermissionModalOpen: boolean;
  isSyncModalOpen: boolean;
  lastSyncTime: Date | null;
  autoSyncCount: number;
  requestPermission: () => void;
  grantPermission: () => Promise<void>;
  denyPermission: () => Promise<void>;
  revokePermission: () => Promise<void>;
  openSyncModal: () => void;
  closeSyncModal: () => void;
  closePermissionModal: () => void;
  ingestSmsText: (rawText: string) => Promise<{ success: boolean; transaction?: any; error?: string }>;
  processSmsText: (rawSms: string, sender?: string) => Promise<ParsedTransaction | null>;
}

const SmsContext = createContext<SmsContextType | undefined>(undefined);

const LAST_SYNC_STORAGE_KEY = 'monzi_last_sync';

export function SmsProvider({ children }: { children: React.ReactNode }) {
  const [permissionState, setPermissionState] = useState<SmsPermissionState>('prompt');
  const [isListening, setIsListening] = useState(false);
  const [isPermissionModalOpen, setIsPermissionModalOpen] = useState(false);
  const [isSyncModalOpen, setIsSyncModalOpen] = useState(false);
  const [lastSyncTime, setLastSyncTime] = useState<Date | null>(null);
  const [autoSyncCount, setAutoSyncCount] = useState<number>(0);
  const abortControllerRef = useRef<AbortController | null>(null);
  const toast = useToast();

  const recordSyncSuccess = useCallback((count: number = 1) => {
    const now = new Date();
    setLastSyncTime(now);
    try {
      localStorage.setItem(LAST_SYNC_STORAGE_KEY, now.toISOString());
    } catch {
      // Ignore localStorage quota/availability errors
    }
    if (count > 0) {
      setAutoSyncCount((prev) => prev + count);
    }
  }, []);

  const processSmsText = useCallback(
    async (rawSms: string, sender?: string): Promise<ParsedTransaction | null> => {
      if (!rawSms || !rawSms.trim()) return null;

      const parsed = parseBankSms(rawSms, sender || '');
      if (!parsed) return null;

      try {
        const res = await fetch('/api/sync', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'x-monzi-secret': process.env.NEXT_PUBLIC_MONZI_SYNC_SECRET || '',
          },
          body: JSON.stringify({
            device_id: 'pwa-sms-receiver',
            transactions: [
              {
                transaction_date: new Date().toISOString(),
                amount: parsed.amount,
                direction: parsed.direction,
                bank_name: parsed.bank_name,
                account_mask: parsed.account_mask,
                merchant: parsed.merchant || 'Bank Transaction',
                payment_mode: parsed.payment_mode || 'UPI',
                reference_id: parsed.reference_id,
                parsed_available_balance: parsed.parsed_available_balance,
                raw_body: rawSms,
              },
            ],
          }),
        });

        if (!res.ok) {
          return null;
        }

        const data = await res.json();
        const isDup = (data.duplicate_count || 0) > 0 && (data.processed_count || 0) === 0;
        if ((data.processed_count || 0) > 0) {
          recordSyncSuccess(data.processed_count);
        }

        return {
          ...parsed,
          is_duplicate: isDup,
        };
      } catch (err) {
        console.error('[SmsProvider] processSmsText error:', err);
        return null;
      }
    },
    [recordSyncSuccess]
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
          error: 'No financial transaction detected. Non-banking or OTP messages are ignored for security.',
        };
      }

      try {
        const res = await fetch('/api/sync', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'x-monzi-secret': process.env.NEXT_PUBLIC_MONZI_SYNC_SECRET || '',
          },
          body: JSON.stringify({
            device_id: 'pwa-sms-receiver',
            transactions: [
              {
                transaction_date: new Date().toISOString(),
                amount: parsed.amount,
                direction: parsed.direction,
                bank_name: parsed.bank_name,
                account_mask: parsed.account_mask,
                merchant: parsed.merchant || 'Bank Transaction',
                payment_mode: parsed.payment_mode || 'UPI',
                reference_id: parsed.reference_id,
                parsed_available_balance: parsed.parsed_available_balance,
                raw_body: rawText,
              },
            ],
          }),
        });

        if (!res.ok) {
          const err = await res.json();
          throw new Error(err.error || 'Sync ingestion failed');
        }

        const data = await res.json();
        if ((data.processed_count || 0) > 0) {
          recordSyncSuccess(data.processed_count);
        }
        return { success: true, transaction: data };
      } catch (err: any) {
        return { success: false, error: err.message || 'Network error syncing SMS' };
      }
    },
    [recordSyncSuccess]
  );

  const stopSmsReceiver = useCallback(() => {
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
      abortControllerRef.current = null;
    }
    setIsListening(false);
  }, []);

  const startSmsReceiver = useCallback(() => {
    if (typeof window === 'undefined') return;

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

  // Initialize permission state and lastSyncTime from localStorage / user profile
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

    const saved = localStorage.getItem('monzi_sms_permission') as SmsPermissionState | null;
    if (saved === 'granted' || saved === 'denied') {
      setPermissionState(saved);
      if (saved === 'granted') {
        startSmsReceiver();
      }
    } else {
      fetch('/api/user')
        .then((r) => r.json())
        .then((data) => {
          if (data.profile?.sms_permission) {
            setPermissionState(data.profile.sms_permission);
            if (data.profile.sms_permission === 'granted') {
              startSmsReceiver();
            }
          }
        })
        .catch(() => {});
    }

    return () => {
      stopSmsReceiver();
    };
  }, [startSmsReceiver, stopSmsReceiver]);

  const requestPermission = () => {
    setIsPermissionModalOpen(true);
  };

  const grantPermission = async () => {
    setPermissionState('granted');
    localStorage.setItem('monzi_sms_permission', 'granted');
    setIsPermissionModalOpen(false);

    try {
      await fetch('/api/user', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ sms_permission: 'granted' }),
      });
    } catch (e) {
      console.error(e);
    }

    toast.success('SMS Sync permission granted. Listener activated.');
    startSmsReceiver();
  };

  const denyPermission = async () => {
    setPermissionState('denied');
    localStorage.setItem('monzi_sms_permission', 'denied');
    setIsPermissionModalOpen(false);
    stopSmsReceiver();

    try {
      await fetch('/api/user', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ sms_permission: 'denied' }),
      });
    } catch (e) {
      console.error(e);
    }

    toast.info('SMS Sync declined. You can still paste bank messages manually.');
  };

  const revokePermission = async () => {
    setPermissionState('prompt');
    localStorage.removeItem('monzi_sms_permission');
    stopSmsReceiver();

    try {
      await fetch('/api/user', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ sms_permission: 'prompt' }),
      });
    } catch (e) {
      console.error(e);
    }

    toast.info('SMS permission revoked.');
  };

  const openSyncModal = () => {
    if (permissionState === 'prompt') {
      setIsPermissionModalOpen(true);
    } else {
      setIsSyncModalOpen(true);
    }
  };

  const closeSyncModal = () => setIsSyncModalOpen(false);
  const closePermissionModal = () => setIsPermissionModalOpen(false);

  return (
    <SmsContext.Provider
      value={{
        permissionState,
        isListening,
        isPermissionModalOpen,
        isSyncModalOpen,
        lastSyncTime,
        autoSyncCount,
        requestPermission,
        grantPermission,
        denyPermission,
        revokePermission,
        openSyncModal,
        closeSyncModal,
        closePermissionModal,
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
