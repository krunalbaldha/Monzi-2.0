'use client';

import React, { useState } from 'react';
import { Header } from '@/components/layout/Header';
import { GlassCard } from '@/components/ui/GlassCard';
import { Key, Smartphone, Database, Copy, Check, Terminal } from 'lucide-react';

export default function SettingsPage() {
  const [copied, setCopied] = useState(false);
  const syncSecret = 'monzi_sync_sec_ts6_MlPJi62UEBhcgvYI6g_uI7wG_SE';

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="flex flex-col min-h-screen">
      <Header />

      <div className="p-8 space-y-8 max-w-5xl mx-auto w-full">
        <div>
          <h2 className="text-xl font-bold tracking-tight text-slate-900">Platform Settings</h2>
          <p className="text-xs text-slate-500 font-medium">
            Configure your Supabase database and Android SMS companion integration
          </p>
        </div>

        {/* 1. Android Companion Integration Card */}
        <GlassCard className="p-6 space-y-4">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-2xl bg-blue-50 text-blue-600 flex items-center justify-center">
              <Smartphone className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-base font-bold text-slate-900">Android Companion Setup</h3>
              <p className="text-xs text-slate-500 font-medium">
                Use these credentials in your sideloaded Android companion APK
              </p>
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 pt-2">
            <div className="p-4 rounded-xl bg-slate-50 border border-slate-200/80 space-y-1.5">
              <span className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                Sync Secret (Bearer Token)
              </span>
              <div className="flex items-center justify-between font-mono text-xs font-bold text-slate-900 bg-white p-2.5 rounded-lg border border-slate-200">
                <span>{syncSecret}</span>
                <button
                  onClick={() => copyToClipboard(syncSecret)}
                  className="text-slate-500 hover:text-blue-600 p-1"
                >
                  {copied ? <Check className="w-4 h-4 text-emerald-600" /> : <Copy className="w-4 h-4" />}
                </button>
              </div>
            </div>

            <div className="p-4 rounded-xl bg-slate-50 border border-slate-200/80 space-y-1.5">
              <span className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
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
            <pre className="p-3.5 rounded-xl bg-slate-900 text-slate-100 font-mono text-xs overflow-x-auto leading-relaxed">
{`curl -X POST http://localhost:3000/api/sync \\
  -H "Content-Type: application/json" \\
  -H "x-monzi-secret: ${syncSecret}" \\
  -d '{
    "device_id": "pixel-8-pro",
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
        <GlassCard className="p-6 space-y-4">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-2xl bg-emerald-50 text-emerald-600 flex items-center justify-center">
              <Database className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-base font-bold text-slate-900">Supabase Database Connection</h3>
              <p className="text-xs text-slate-500 font-medium">
                Add your Supabase project keys in <code className="font-mono bg-slate-100 px-1 py-0.5 rounded">.env.local</code>
              </p>
            </div>
          </div>

          <div className="p-4 rounded-xl bg-slate-50 border border-slate-200/80 space-y-3 text-xs text-slate-600">
            <p className="font-semibold text-slate-900">To connect your live Supabase database:</p>
            <ol className="list-decimal list-inside space-y-2">
              <li>Open your project at <a href="https://supabase.com" target="_blank" className="text-blue-600 underline font-medium">supabase.com</a>.</li>
              <li>Go to <strong>SQL Editor</strong> and execute the script located in <code className="font-mono bg-white px-1.5 py-0.5 rounded border">supabase/schema.sql</code>.</li>
              <li>Optionally run <code className="font-mono bg-white px-1.5 py-0.5 rounded border">supabase/seed.sql</code> to load default categories.</li>
              <li>In your <code className="font-mono bg-white px-1.5 py-0.5 rounded border">web/.env.local</code> file, add:</li>
            </ol>
            <pre className="p-3 rounded-lg bg-slate-900 text-slate-200 font-mono text-xs">
{`NEXT_PUBLIC_SUPABASE_URL=https://your-project.supabase.co
NEXT_PUBLIC_SUPABASE_ANON_KEY=eyJhbGciOi...
MONZI_SYNC_SECRET=${syncSecret}`}
            </pre>
          </div>
        </GlassCard>
      </div>
    </div>
  );
}


