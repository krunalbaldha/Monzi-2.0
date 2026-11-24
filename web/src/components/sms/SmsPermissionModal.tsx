'use client';

import React from 'react';
import { MessageSquare, ShieldCheck, Lock, CheckCircle2, X, AlertCircle } from 'lucide-react';
import { useSms } from '@/components/sms/SmsProvider';

export function SmsPermissionModal() {
  const { isPermissionModalOpen, closePermissionModal, grantPermission, denyPermission } = useSms();

  if (!isPermissionModalOpen) return null;

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto bg-slate-900/60 backdrop-blur-md p-4 flex items-center justify-center min-h-screen animate-fade-in">
      <div className="fixed inset-0 -z-10" onClick={closePermissionModal} aria-hidden="true" />

      <div className="relative w-full max-w-md bg-white rounded-2xl sm:rounded-3xl p-6 sm:p-7 shadow-2xl border border-slate-100 my-auto text-left animate-slide-up space-y-4">
        {/* Header Icon & Close */}
        <div className="flex items-center justify-between pb-2 border-b border-slate-100">
          <div className="flex items-center gap-2.5">
            <div className="w-10 h-10 rounded-2xl bg-blue-50 text-blue-600 flex items-center justify-center">
              <MessageSquare className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-base font-bold text-slate-900">Bank SMS Sync Permission</h3>
              <p className="text-[11px] text-slate-500 font-medium">Automatic expense extraction</p>
            </div>
          </div>
          <button
            onClick={closePermissionModal}
            className="p-1 rounded-full text-slate-400 hover:text-slate-600"
            aria-label="Close"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Rationale Explanation */}
        <div className="space-y-2 text-xs text-slate-600 leading-relaxed">
          <p>
            Monzi requires permission to read incoming bank SMS notifications so that your transactions, payment modes, and account balances are updated in real-time without tedious manual entry.
          </p>
        </div>

        {/* Security & Privacy Badges */}
        <div className="space-y-2.5 pt-1">
          <div className="flex items-start gap-2.5 p-3 rounded-xl bg-slate-50 border border-slate-100 text-xs">
            <ShieldCheck className="w-4 h-4 text-emerald-600 shrink-0 mt-0.5" />
            <div>
              <p className="font-bold text-slate-900">Zero Personal Message Access</p>
              <p className="text-slate-500 text-[11px] mt-0.5">
                Our parser strictly filters for financial keywords. Personal texts, WhatsApp messages, and marketing ads are ignored.
              </p>
            </div>
          </div>

          <div className="flex items-start gap-2.5 p-3 rounded-xl bg-slate-50 border border-slate-100 text-xs">
            <Lock className="w-4 h-4 text-blue-600 shrink-0 mt-0.5" />
            <div>
              <p className="font-bold text-slate-900">Security OTPs Filtered Out</p>
              <p className="text-slate-500 text-[11px] mt-0.5">
                One-Time Passwords (OTPs) and login verification codes are discarded on-device and never saved to the database.
              </p>
            </div>
          </div>
        </div>

        {/* Actions */}
        <div className="space-y-2 pt-2">
          <button
            onClick={grantPermission}
            className="w-full py-3 rounded-xl bg-blue-600 text-white font-semibold text-xs shadow-md shadow-blue-500/25 hover:bg-blue-700 active:scale-98 transition-all flex items-center justify-center gap-1.5"
          >
            <CheckCircle2 className="w-4 h-4" />
            <span>Grant Permission & Enable SMS Sync</span>
          </button>
          <button
            onClick={denyPermission}
            className="w-full py-2.5 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-600 font-semibold text-xs transition-all"
          >
            Deny / Not Now
          </button>
        </div>
      </div>
    </div>
  );
}
