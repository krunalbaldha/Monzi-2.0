'use client';

import React, { useState, useEffect } from 'react';
import { Fingerprint, ShieldCheck, X } from 'lucide-react';
import { useAuth } from '@/components/auth/AuthProvider';
import { useToast } from '@/components/ui/Toast';
import { Spinner } from '@/components/ui/Spinner';

const DISMISS_STORAGE_KEY = 'monzi_biometric_prompt_dismissed';

export function BiometricEnrollBanner() {
  const {
    user,
    loading,
    isLocked,
    biometricSupported,
    biometricEnabled,
    enrollBiometric,
  } = useAuth();
  const toast = useToast();

  const [mounted, setMounted] = useState(false);
  const [dismissed, setDismissed] = useState(true);
  const [enrolling, setEnrolling] = useState(false);

  useEffect(() => {
    setMounted(true);
    try {
      const stored = window.localStorage.getItem(DISMISS_STORAGE_KEY);
      setDismissed(stored === '1');
    } catch {
      setDismissed(false);
    }
  }, []);

  if (
    !mounted ||
    !user ||
    loading ||
    isLocked ||
    !biometricSupported ||
    biometricEnabled ||
    dismissed
  ) {
    return null;
  }

  const handleDismiss = () => {
    try {
      window.localStorage.setItem(DISMISS_STORAGE_KEY, '1');
    } catch {
      // Ignore storage errors
    }
    setDismissed(true);
  };

  const handleEnroll = async () => {
    setEnrolling(true);
    try {
      const result = await enrollBiometric();
      if (result.success) {
        toast.success('Fingerprint / Face ID enabled for quick unlock');
        setDismissed(true);
      } else {
        toast.error(
          result.error || 'Could not enable biometric unlock on this device'
        );
      }
    } catch (err: unknown) {
      toast.error(
        err instanceof Error
          ? err.message
          : 'Biometric setup cancelled or unavailable'
      );
    } finally {
      setEnrolling(false);
    }
  };

  return (
    <div className="relative rounded-2xl bg-white/80 backdrop-blur-xl border border-blue-100/90 shadow-[0_4px_24px_rgba(37,99,235,0.08)] p-4 sm:p-5 animate-slide-up">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div className="flex items-start sm:items-center gap-3.5 pr-6 sm:pr-0">
          <div className="w-11 h-11 rounded-2xl bg-gradient-to-br from-blue-600 to-indigo-600 flex items-center justify-center text-white shadow-[0_4px_14px_rgba(37,99,235,0.3)] shrink-0">
            <Fingerprint className="w-5 h-5" />
          </div>
          <div className="min-w-0">
            <div className="flex items-center gap-2 flex-wrap">
              <h3 className="text-sm font-bold text-slate-900 tracking-tight">
                Protect your finances with Biometrics
              </h3>
              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-blue-50 text-blue-700 text-[11px] font-semibold">
                <ShieldCheck className="w-3 h-3" />
                Recommended
              </span>
            </div>
            <p className="text-xs text-slate-500 mt-0.5 leading-relaxed">
              Unlock Monzi 2.0 in one tap with your device Fingerprint or Face
              ID.
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2.5 shrink-0">
          <button
            type="button"
            onClick={handleDismiss}
            disabled={enrolling}
            className="btn-secondary min-h-[44px] px-4 text-xs font-semibold text-slate-600"
          >
            Not Now
          </button>
          <button
            type="button"
            onClick={handleEnroll}
            disabled={enrolling}
            className="btn-primary min-h-[44px] px-4 text-xs font-semibold whitespace-nowrap disabled:opacity-60"
          >
            {enrolling ? (
              <>
                <Spinner size={15} colorClass="text-white" />
                <span>Setting up...</span>
              </>
            ) : (
              <>
                <Fingerprint className="w-4 h-4" />
                <span>Enable Fingerprint / Face ID</span>
              </>
            )}
          </button>
        </div>
      </div>

      <button
        type="button"
        onClick={handleDismiss}
        aria-label="Dismiss biometric prompt"
        className="sm:hidden absolute top-2.5 right-2.5 w-8 h-8 flex items-center justify-center rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100/80 transition-colors"
      >
        <X className="w-4 h-4" />
      </button>
    </div>
  );
}
