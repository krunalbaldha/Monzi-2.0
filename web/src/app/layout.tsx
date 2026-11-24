import type { Metadata } from 'next';
import './globals.css';
import { Sidebar } from '@/components/layout/Sidebar';

export const metadata: Metadata = {
  title: 'Monzi 2.0 – Personal Finance Intelligence',
  description: 'Zero manual entry. Your finances, automatically understood.',
  manifest: '/manifest.json',
  icons: {
    icon: '/icon.svg',
  },
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body className="min-h-screen bg-[#F8FAFC] antialiased">
        {/* Soft Ambient Blobs for Glassmorphism Depth */}
        <div className="ambient-bg">
          <div className="ambient-blob-1" />
          <div className="ambient-blob-2" />
        </div>

        <div className="flex min-h-screen">
          {/* Fixed Left Glass Sidebar */}
          <Sidebar />

          {/* Main Content Area */}
          <div className="flex-1 pl-64 flex flex-col min-h-screen">
            <main className="flex-1 pb-16">{children}</main>
          </div>
        </div>
      </body>
    </html>
  );
}
