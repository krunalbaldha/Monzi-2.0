# Monzi 2.0 – Personal Finance Intelligence Platform

> **Zero Manual Entry. Your Finances, Automatically Understood.**  
> Built for personal use with Next.js PWA, Supabase PostgreSQL, and an Android Companion SMS Sync application.

---

## Architecture Overview

1. **Web Dashboard & PWA (`/web`):**
   - Next.js 15 + React 19 + TypeScript + Tailwind CSS
   - Premium Apple-inspired Light Glassmorphism UI
   - Live Cash Flow and Category Visualizations (Recharts)
   - Real-time updates with Supabase
   - REST API Ingestion Endpoint (`/api/sync`)

2. **Database (`/supabase`):**
   - PostgreSQL schema with strict enums, duplicate prevention via SHA-256 `sms_fingerprint`, account auto-discovery, and Realtime publications.
   - `supabase/schema.sql` (Tables, constraints, indexes)
   - `supabase/seed.sql` (Default categories & seed accounts)

3. **Android Companion App (`/android`):**
   - Kotlin BroadcastReceiver listening for bank SMS notifications (`RECEIVE_SMS`, `READ_SMS`)
   - On-device deterministic regex parser for Indian Banks (HDFC, ICICI, SBI, Axis, Kotak, UPI)
   - Background sync queue with `WorkManager` pushing structured JSON to `/api/sync`
   - One-click historical SMS scanning & battery optimization whitelisting

---

## Quick Start (Web Dashboard)

```bash
cd web
npm run dev
```

Visit [http://localhost:3000](http://localhost:3000) to view the Apple-inspired Glassmorphism dashboard.

### Quick Ingestion Test (cURL)
You can test the ingestion pipeline immediately using the pre-configured developer secret:

```bash
curl -X POST http://localhost:3000/api/sync \
  -H "Content-Type: application/json" \
  -H "x-monzi-secret: monzi_sync_sec_ts6_MlPJi62UEBhcgvYI6g_uI7wG_SE" \
  -d '{
    "device_id": "pixel-8-pro",
    "transactions": [{
      "transaction_date": "2026-11-24T18:30:00+05:30",
      "amount": 480.00,
      "direction": "DEBIT",
      "bank_name": "HDFC Bank",
      "account_mask": "XX8421",
      "merchant": "Swiggy",
      "payment_mode": "UPI",
      "reference_id": "UPI4910283921"
    }]
  }'
```

---

## Supabase Setup

1. Open your project at [supabase.com](https://supabase.com).
2. Go to the **SQL Editor** and paste the contents of `supabase/schema.sql`.
3. Paste and run `supabase/seed.sql` to populate default categories.
4. Copy your Supabase Project URL and Anon Key into `web/.env.local`:

```env
NEXT_PUBLIC_SUPABASE_URL=https://your-project.supabase.co
NEXT_PUBLIC_SUPABASE_ANON_KEY=eyJhbGciOi...
MONZI_SYNC_SECRET=monzi_sec_live_9921_dev
```

---

## Android Companion Sideloading

1. Open the `/android` folder in **Android Studio**.
2. Connect your phone via USB or build the signed APK (`Build > Build Bundle(s) / APK(s) > Build APK(s)`).
3. Transfer and install the APK onto your Android phone.
4. Open the app:
   - Tap **"Grant SMS Permissions"**.
   - Set your server URL (e.g., `https://your-monzi.vercel.app/api/sync`).
   - Tap **"Scan Historical SMS"** to import existing transactions.
