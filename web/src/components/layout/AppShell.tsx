'use client';

import React from 'react';
import { usePathname } from 'next/navigation';
import { Sidebar } from '@/components/layout/Sidebar';
import { BottomNav } from '@/components/layout/BottomNav';
import { BiometricEnrollBanner } from '@/components/auth/BiometricEnrollBanner';

const AUTH_ROUTES = ['/login', '/signup', '/forgot-password', '/reset-password'];

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname() || '/';
  const isAuthRoute =
    AUTH_ROUTES.some((route) => pathname === route || pathname.startsWith(`${route}/`)) ||
    pathname.startsWith('/auth/');

  if (isAuthRoute) {
    return (
      <>
        <div className="ambient-bg">
          <div className="ambient-blob-1" />
          <div className="ambient-blob-2" />
          <div className="ambient-blob-3" />
        </div>
        <main className="min-h-screen">{children}</main>
      </>
    );
  }

  return (
    <>
      {/* Ambient Glass Blobs */}
      <div className="ambient-bg">
        <div className="ambient-blob-1" />
        <div className="ambient-blob-2" />
        <div className="ambient-blob-3" />
      </div>

      <div className="flex min-h-screen">
        {/* Desktop Left Glass Sidebar — 288px wide (w-72) */}
        <Sidebar />

        {/* Main Content Area */}
        <div className="flex-1 w-full pl-0 md:pl-72 flex flex-col min-h-screen">
          <div className="px-4 sm:px-6 md:px-8 pt-4 max-w-7xl mx-auto w-full empty:hidden">
            <BiometricEnrollBanner />
          </div>
          <main className="flex-1 pb-28 md:pb-8">{children}</main>
        </div>
      </div>

      {/* Mobile iOS-Inspired Glass Floating Bottom Navigation */}
      <BottomNav />
    </>
  );
}
