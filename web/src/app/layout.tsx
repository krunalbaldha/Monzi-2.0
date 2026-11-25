import type { Metadata, Viewport } from 'next';
import './globals.css';
import { Sidebar } from '@/components/layout/Sidebar';
import { BottomNav } from '@/components/layout/BottomNav';
import { Providers } from '@/components/Providers';

export const metadata: Metadata = {
  title: 'Monzi 2.0 – Personal Finance Intelligence',
  description: 'Zero manual entry. Your finances, automatically understood.',
  manifest: '/manifest.json',
  appleWebApp: {
    capable: true,
    statusBarStyle: 'default',
    title: 'Monzi 2.0',
  },
  icons: {
    icon: [
      { url: '/icon-192.png', sizes: '192x192', type: 'image/png' },
      { url: '/icon-512.png', sizes: '512x512', type: 'image/png' },
      { url: '/icon.svg', type: 'image/svg+xml' },
    ],
    apple: [
      { url: '/apple-touch-icon.png', sizes: '180x180', type: 'image/png' },
    ],
  },
};

export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
  maximumScale: 1,
  userScalable: false,
  viewportFit: 'cover',
  themeColor: '#2563EB',
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en" className="h-full">
      <head>
        <link rel="apple-touch-icon" href="/apple-touch-icon.png" />
        <meta name="mobile-web-app-capable" content="yes" />
      </head>
      <body className="min-h-full bg-gradient-to-br from-white to-[#F7F8FA] antialiased text-slate-900 overflow-x-hidden selection:bg-blue-100 selection:text-blue-900">
        <Providers>
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
              <main className="flex-1 pb-28 md:pb-8">{children}</main>
            </div>
          </div>

          {/* Mobile iOS-Inspired Glass Floating Bottom Navigation */}
          <BottomNav />
        </Providers>
      </body>
    </html>
  );
}
