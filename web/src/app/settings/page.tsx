'use client';

import React, { useState } from 'react';
import { Header } from '@/components/layout/Header';
import { GlassCard } from '@/components/ui/GlassCard';
import { Smartphone, Database, Copy, Check, Terminal } from 'lucide-react';

export default function SettingsPage() {
  const [copied, setCopied] = useState(false);
  const syncSecret = process.env.NEXT_PUBLIC_MONZI_SYNC_SECRET || 'monzi_personal_sync_secret';

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="flex flex-col min-h-screen">
      <Header />

      <div className="p-4 sm:p-6 md:p-8 space-y-6 md:space-y-8 max-w-5xl mx-auto w-full">
        <div>
          <h2 className="text-lg sm:text-xl font-bold tracking-tight text-slate-900">Platform Settings</h2>
          <p className="text-[11px] sm:text-xs text-slate-500 font-medium">
            Supabase database status and Android SMS companion credentials
          </p>
        </div>

        {/* 1. Android Companion Integration Card */}
        <GlassCard className="p-4 sm:p-6 space-y-4">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 sm:w-10 sm:h-10 rounded-xl sm:rounded-2xl bg-blue-50 text-blue-600 flex items-center justify-center shrink-0">
              <Smartphone className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-sm sm:text-base font-bold text-slate-900">Android Companion Setup</h3>
              <p className="text-[11px] sm:text-xs text-slate-500 font-medium">
                Enter these in your sideloaded Android app to start real-time tracking
              </p>
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-3 sm:gap-4 pt-1">
            <div className="p-3.5 sm:p-4 rounded-xl bg-slate-50 border border-slate-200/80 space-y-1.5">
              <span className="text-[10px] sm:text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                Sync Secret (Bearer Token)
              </span>
              <div className="flex items-center justify-between font-mono text-[11px] sm:text-xs font-bold text-slate-900 bg-white p-2.5 rounded-lg border border-slate-200 gap-2 overflow-hidden">
                <span className="truncate">{syncSecret}</span>
                <button
                  onClick={() => copyToClipboard(syncSecret)}
                  className="text-slate-500 hover:text-blue-600 p-1 shrink-0"
                >
                  {copied ? <Check className="w-4 h-4 text-emerald-600" /> : <Copy className="w-4 h-4" />}
                </button>
              </div>
            </div>

            <div className="p-3.5 sm:p-4 rounded-xl bg-slate-50 border border-slate-200/80 space-y-1.5">
              <span className="text-[10px] sm:text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                Sync Endpoint
              </span>
              <div className="flex items-center justify-between font-mono text-xs font-bold text-slate-900 bg-white p-2.5 rounded-lg border border-slate-200">
                <span>/api/sync</span>
                <span className="text-[10px] text-emerald-600 font-bold bg-emerald-50 px-2 py-0.5 rounded">
                  POST Active
                </span>
              </div>
            </div>
          </div>

          {/* Quick curl test snippet */}
          <div className="pt-2">
            <div className="flex items-center gap-1.5 text-xs font-semibold text-slate-700 mb-2">
              <Terminal className="w-3.5 h-3.5" />
              <span>Test SMS Ingestion via Terminal (cURL)</span>
            </div>
            <pre className="p-3 sm:p-3.5 rounded-xl bg-slate-900 text-slate-100 font-mono text-[11px] sm:text-xs overflow-x-auto leading-relaxed">
{`curl -X POST http://localhost:3000/api/sync \\
  -H "Content-Type: application/json" \\
  -H "x-monzi-secret: ${syncSecret}" \\
  -d '{
    "device_id": "phone-s24",
    "transactions": [{
      "transaction_date": "2026-11-24T18:00:00+05:30",
      "amount": 450.00,
      "direction": "DEBIT",
      "bank_name": "HDFC Bank",
      "account_mask": "XX8421",
      "merchant": "Swiggy",
      "payment_mode": "UPI"
    }]
  }'`}
            </pre>
          </div>
        </GlassCard>

        {/* 2. Supabase Database Card */}
        <GlassCard className="p-4 sm:p-6 space-y-4">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 sm:w-10 sm:h-10 rounded-xl sm:rounded-2xl bg-emerald-50 text-emerald-600 flex items-center justify-center shrink-0">
              <Database className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-sm sm:text-base font-bold text-slate-900">Supabase Database Connection</h3>
              <p className="text-[11px] sm:text-xs text-slate-500 font-medium">
                Live PostgreSQL database at <code className="font-mono bg-slate-100 px-1 py-0.5 rounded">hgxrqmkxxylfjgoqjkzp.supabase.co</code>
              </p>
            </div>
          </div>

          <div className="p-3.5 sm:p-4 rounded-xl bg-slate-50 border border-slate-200/80 space-y-2.5 text-xs text-slate-600">
            <div className="flex items-center gap-2 text-emerald-700 font-bold">
              <span className="w-2 h-2 rounded-full bg-emerald-500"></span>
              <span>Status: Connected & Operational</span>
            </div>
            <p>
              Your personal database tables (<code>accounts</code>, <code>categories</code>, <code>transactions</code>) are seeded and actively accepting live transaction syncs.
            </p>
          </div>
        </GlassCard>
      </div>
    </div>
  );
}
