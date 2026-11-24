'use client';

import React from 'react';
import { ToastProvider } from '@/components/ui/Toast';
import { PwaProvider } from '@/components/pwa/PwaProvider';
import { SmsProvider } from '@/components/sms/SmsProvider';
import { SmsPermissionModal } from '@/components/sms/SmsPermissionModal';
import { SmsSyncModal } from '@/components/sms/SmsSyncModal';

export function Providers({ children }: { children: React.ReactNode }) {
  return (
    <PwaProvider>
      <ToastProvider>
        <SmsProvider>
          {children}
          <SmsPermissionModal />
          <SmsSyncModal />
        </SmsProvider>
      </ToastProvider>
    </PwaProvider>
  );
}
