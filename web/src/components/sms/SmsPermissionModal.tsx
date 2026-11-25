'use client';

import React from 'react';
import { ShieldCheck, MessageSquareText, Check, X, Smartphone } from 'lucide-react';
import { useSms } from '@/components/sms/SmsProvider';

export function SmsPermissionModal() {
  const {
    isPermissionModalOpen,
    isAndroidNative,
    closePermissionModal,
    grantPermission,
    denyPermission,
  } = useSms();

  if (!isPermissionModalOpen) return null;

  const features = isAndroidNative
    ? [
        'Automatic background bank & UPI SMS detection',
        'Strict filter drops OTPs, promos & failed attempts',
        'Raw SMS text never leaves your device',
      ]
    : [
        'Smart bank SMS parser & duplicate protection',
        'Paste single or bulk SMS statements anytime',
        'Real-time background sync via Monzi Android App',
      ];

  return (
    <div className="fixed inset-0 z-50 bg-slate-900/50 backdrop-blur-sm flex items-end sm:items-center justify-center animate-fade-in">
      <div className="fixed inset-0 -z-10" onClick={closePermissionModal} aria-hidden="true" />

      <div className="relative w-full sm:max-w-md bg-white rounded-t-3xl sm:rounded-3xl p-6 sm:p-7 pb-8 sm:pb-7 shadow-2xl border border-slate-100 animate-slide-up">
        {/* Bottom sheet drag handle (mobile) */}
        <div className="w-10 h-1 bg-slate-200 rounded-full mx-auto mb-5 sm:hidden" />

        {/* Close button */}
        <button
          onClick={closePermissionModal}
          className="absolute top-5 right-5 p-2 rounded-full text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-colors"
          aria-label="Close"
        >
          <X className="w-5 h-5" />
        </button>

        {/* Large SMS / Shield Icon */}
        <div className="flex flex-col items-center text-center">
          <div className="relative w-16 h-16 rounded-2xl bg-gradient-to-br from-blue-500 to-blue-600 text-white flex items-center justify-center shadow-lg shadow-blue-500/25 mb-4">
            <MessageSquareText className="w-8 h-8" />
            <div className="absolute -bottom-1.5 -right-1.5 w-7 h-7 rounded-full bg-emerald-500 border-2 border-white flex items-center justify-center shadow-sm">
              <ShieldCheck className="w-4 h-4 text-white" />
            </div>
          </div>

          <h3 className="text-xl font-bold text-slate-900 tracking-tight">
            {isAndroidNative ? 'Enable Android SMS Sync' : 'SMS Sync & Detection'}
          </h3>

          <p className="text-sm text-slate-600 leading-relaxed mt-2 max-w-sm">
            {isAndroidNative
              ? 'Monzi reads financial SMS on-device to automatically track your income and expenses. We ask once and never store raw SMS text.'
              : 'Browsers cannot read SMS in the background automatically. Enable SMS Sync to paste bank messages here, or use the Monzi Android App for real-time background sync.'}
          </p>
        </div>

        {!isAndroidNative && (
          <div className="mt-4 p-3 rounded-xl bg-blue-50/80 border border-blue-100 flex items-start gap-2.5 text-left">
            <Smartphone className="w-4 h-4 text-blue-600 shrink-0 mt-0.5" />
            <p className="text-xs text-blue-900 leading-snug">
              For automatic 24/7 SMS reading without manual pasting, sign in inside the{' '}
              <span className="font-semibold">Monzi Android App</span>.
            </p>
          </div>
        )}

        {/* 3 Feature Bullets */}
        <div className="mt-5 space-y-2.5 bg-slate-50/80 border border-slate-100 rounded-2xl p-4">
          {features.map((label) => (
            <div key={label} className="flex items-center gap-3 text-sm font-medium text-slate-800">
              <span className="w-5 h-5 rounded-full bg-emerald-100 text-emerald-600 flex items-center justify-center shrink-0">
                <Check className="w-3.5 h-3.5 stroke-[2.5]" />
              </span>
              <span>{label}</span>
            </div>
          ))}
        </div>

        {/* Action Buttons */}
        <div className="mt-6 space-y-3">
          <button
            onClick={grantPermission}
            className="w-full min-h-[48px] py-3.5 px-4 rounded-xl bg-blue-600 hover:bg-blue-700 text-white font-semibold text-sm shadow-lg shadow-blue-500/25 active:scale-[0.99] transition-all"
          >
            {isAndroidNative ? 'Allow SMS Permission' : 'Enable SMS Sync'}
          </button>

          <button
            onClick={denyPermission}
            className="w-full py-2.5 text-sm font-medium text-slate-500 hover:text-slate-700 transition-colors"
          >
            Not Now
          </button>
        </div>

        {/* Privacy Note */}
        <p className="text-[11px] text-slate-400 text-center mt-4 leading-relaxed">
          OTPs, personal messages, balance-only alerts, and promotional texts are automatically ignored and never saved.
        </p>
      </div>
    </div>
  );
}
