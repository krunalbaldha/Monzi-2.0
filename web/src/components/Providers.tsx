'use client';

import React from 'react';
import { ToastProvider } from '@/components/ui/Toast';
import { PwaProvider } from '@/components/pwa/PwaProvider';

export function Providers({ children }: { children: React.ReactNode }) {
  return (
    <PwaProvider>
      <ToastProvider>
        {children}
      </ToastProvider>
    </PwaProvider>
  );
}
