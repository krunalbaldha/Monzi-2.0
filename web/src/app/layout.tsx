import type { Metadata, Viewport } from 'next';
import './globals.css';
import { Sidebar } from '@/components/layout/Sidebar';
import { BottomNav } from '@/components/layout/BottomNav';

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
    icon: '/icon.svg',
    apple: '/icon.svg',
  },
};

export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
  maximumScale: 1,
  userScalable: false,
  viewportFit: 'cover',
  themeColor: '#F8FAFC',
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en" className="h-full">
      <body className="min-h-full bg-[#F8FAFC] antialiased text-slate-900 overflow-x-hidden selection:bg-blue-100 selection:text-blue-900">
        {/* Soft Ambient Blobs for Glassmorphism Depth */}
        <div className="ambient-bg">
          <div className="ambient-blob-1" />
          <div className="ambient-blob-2" />
        </div>

        <div className="flex min-h-screen">
          {/* Desktop Left Glass Sidebar */}
          <Sidebar />

          {/* Main Content Area */}
          <div className="flex-1 w-full pl-0 md:pl-64 flex flex-col min-h-screen">
            <main className="flex-1 pb-24 md:pb-16">{children}</main>
          </div>
        </div>

        {/* Mobile iOS-Inspired Glass Floating Bottom Navigation */}
        <BottomNav />
      </body>
    </html>
  );
}
