'use client';

import React, { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react';
import { parseBankSms, ParsedTransaction } from '@/lib/parser';
import { useToast } from '@/components/ui/Toast';

export type SmsPermissionState = 'prompt' | 'granted' | 'denied' | 'unsupported';

interface SmsContextType {
  permissionState: SmsPermissionState;
  isListening: boolean;
  isPermissionModalOpen: boolean;
  isSyncModalOpen: boolean;
  requestPermission: () => void;
  grantPermission: () => Promise<void>;
  denyPermission: () => Promise<void>;
  revokePermission: () => Promise<void>;
  openSyncModal: () => void;
  closeSyncModal: () => void;
  closePermissionModal: () => void;
  ingestSmsText: (rawText: string) => Promise<{ success: boolean; transaction?: any; error?: string }>;
}

const SmsContext = createContext<SmsContextType | undefined>(undefined);

export function SmsProvider({ children }: { children: React.ReactNode }) {
  const [permissionState, setPermissionState] = useState<SmsPermissionState>('prompt');
  const [isListening, setIsListening] = useState(false);
  const [isPermissionModalOpen, setIsPermissionModalOpen] = useState(false);
  const [isSyncModalOpen, setIsSyncModalOpen] = useState(false);
  const abortControllerRef = useRef<AbortController | null>(null);
  const toast = useToast();

  // Initialize permission state from localStorage and user profile
  useEffect(() => {
    const saved = localStorage.getItem('monzi_sms_permission') as SmsPermissionState | null;
    if (saved === 'granted' || saved === 'denied') {
      setPermissionState(saved);
      if (saved === 'granted') {
        startSmsReceiver();
      }
    } else {
      // Check server user profile
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
  }, []);

  const startSmsReceiver = useCallback(() => {
    if (typeof window === 'undefined') return;

    // Check for W3C WebOTP API
    if ('credentials' in navigator && (navigator as any).credentials?.get) {
      setIsListening(true);
      abortControllerRef.current = new AbortController();

      // Listen for incoming SMS
      (navigator.credentials as any)
        .get({
          otp: { transport: ['sms'] },
          signal: abortControllerRef.current.signal,
        })
        .then(async (content: any) => {
          if (content && content.code) {
            // Ingest incoming message
            await ingestSmsText(content.code);
            // Restart listener for continuous tracking
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
      // Browser does not support WebOTP
      setIsListening(false);
    }
  }, []);

  const stopSmsReceiver = useCallback(() => {
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
      abortControllerRef.current = null;
    }
    setIsListening(false);
  }, []);

  const requestPermission = () => {
    setIsPermissionModalOpen(true);
  };

  const grantPermission = async () => {
    setPermissionState('granted');
    localStorage.setItem('monzi_sms_permission', 'granted');
    setIsPermissionModalOpen(false);

    // Save to user profile
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

  const ingestSmsText = async (rawText: string) => {
    if (!rawText || !rawText.trim()) {
      return { success: false, error: 'Empty SMS content' };
    }

    // 1. Run local intelligent parser
    const parsed = parseBankSms(rawText);
    if (!parsed) {
      return {
        success: false,
        error: 'No financial transaction detected. Non-banking or OTP messages are ignored for security.',
      };
    }

    // 2. Submit to sync endpoint
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
              reference_id: parsed.reference_id || `SMS_${Date.now()}`,
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
      return { success: true, transaction: data };
    } catch (err: any) {
      return { success: false, error: err.message || 'Network error syncing SMS' };
    }
  };

  return (
    <SmsContext.Provider
      value={{
        permissionState,
        isListening,
        isPermissionModalOpen,
        isSyncModalOpen,
        requestPermission,
        grantPermission,
        denyPermission,
        revokePermission,
        openSyncModal,
        closeSyncModal,
        closePermissionModal,
        ingestSmsText,
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
