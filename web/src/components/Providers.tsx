'use client';

import React from 'react';
import { AuthProvider } from '@/components/auth/AuthProvider';
import { BiometricLockScreen } from '@/components/auth/BiometricLockScreen';
import { ToastProvider } from '@/components/ui/Toast';
import { PwaProvider } from '@/components/pwa/PwaProvider';
import { SmsProvider } from '@/components/sms/SmsProvider';
import { SmsPermissionModal } from '@/components/sms/SmsPermissionModal';
import { SmsSyncModal } from '@/components/sms/SmsSyncModal';

export function Providers({ children }: { children: React.ReactNode }) {
  return (
    <AuthProvider>
      <PwaProvider>
        <ToastProvider>
          <SmsProvider>
            {children}
            <BiometricLockScreen />
            <SmsPermissionModal />
            <SmsSyncModal />
          </SmsProvider>
        </ToastProvider>
      </PwaProvider>
    </AuthProvider>
  );
}
