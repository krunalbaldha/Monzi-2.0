'use client';

import React, { createContext, useContext, useState, useEffect } from 'react';
import { Download, WifiOff, Share2, PlusSquare, X } from 'lucide-react';

interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

interface PwaContextType {
  isInstallable: boolean;
  isInstalled: boolean;
  isIos: boolean;
  isOffline: boolean;
  triggerInstall: () => void;
  openIosInstallModal: () => void;
}

const PwaContext = createContext<PwaContextType | undefined>(undefined);

export function PwaProvider({ children }: { children: React.ReactNode }) {
  const [deferredPrompt, setDeferredPrompt] = useState<BeforeInstallPromptEvent | null>(null);
  const [isInstallable, setIsInstallable] = useState(false);
  const [isInstalled, setIsInstalled] = useState(false);
  const [isIos, setIsIos] = useState(false);
  const [isOffline, setIsOffline] = useState(false);
  const [showIosModal, setShowIosModal] = useState(false);

  useEffect(() => {
    // 1. Register Service Worker
    if (typeof window !== 'undefined' && 'serviceWorker' in navigator) {
      navigator.serviceWorker
        .register('/sw.js')
        .then((reg) => {
          console.log('[PWA] Service Worker registered successfully', reg.scope);
        })
        .catch((err) => {
          console.warn('[PWA] Service Worker registration failed', err);
        });
    }

    // 2. Check if already installed as standalone PWA
    const isStandalone =
      window.matchMedia('(display-mode: standalone)').matches ||
      (window.navigator as any).standalone === true;

    setIsInstalled(isStandalone);

    // 3. Detect iOS
    const ua = window.navigator.userAgent;
    const isIosDevice = /iPad|iPhone|iPod/.test(ua) && !(window as any).MSStream;
    setIsIos(isIosDevice);

    // 4. Capture beforeinstallprompt (Chrome / Android / Edge)
    const handleBeforeInstallPrompt = (e: Event) => {
      e.preventDefault();
      setDeferredPrompt(e as BeforeInstallPromptEvent);
      setIsInstallable(true);
    };

    window.addEventListener('beforeinstallprompt', handleBeforeInstallPrompt);

    // 5. Track online / offline state
    setIsOffline(!navigator.onLine);
    const handleOnline = () => setIsOffline(false);
    const handleOffline = () => setIsOffline(true);

    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);

    return () => {
      window.removeEventListener('beforeinstallprompt', handleBeforeInstallPrompt);
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
    };
  }, []);

  const triggerInstall = async () => {
    if (deferredPrompt) {
      deferredPrompt.prompt();
      const { outcome } = await deferredPrompt.userChoice;
      if (outcome === 'accepted') {
        setIsInstalled(true);
        setIsInstallable(false);
      }
      setDeferredPrompt(null);
    } else if (isIos) {
      setShowIosModal(true);
    }
  };

  const openIosInstallModal = () => {
    setShowIosModal(true);
  };

  return (
    <PwaContext.Provider
      value={{
        isInstallable,
        isInstalled,
        isIos,
        isOffline,
        triggerInstall,
        openIosInstallModal,
      }}
    >
      {/* Offline Status Bar Banner */}
      {isOffline && (
        <div className="bg-amber-500 text-slate-900 text-xs font-semibold px-4 py-2 flex items-center justify-center gap-2 sticky top-0 z-50 shadow-md">
          <WifiOff className="w-4 h-4" />
          <span>You are working in Offline Mode. Viewing cached financial data.</span>
        </div>
      )}

      {children}

      {/* iOS "Add to Home Screen" Instructions Bottom Sheet Modal */}
      {showIosModal && (
        <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-slate-900/40 backdrop-blur-sm p-0 sm:p-4 animate-fade-in">
          <div className="absolute inset-0" onClick={() => setShowIosModal(false)} />
          <div className="relative w-full max-w-sm bg-white rounded-t-[28px] sm:rounded-3xl p-6 shadow-2xl z-10 space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="text-base font-bold text-slate-900">Install Monzi App</h3>
              <button
                onClick={() => setShowIosModal(false)}
                className="p-1 rounded-full text-slate-400 hover:text-slate-600"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <p className="text-xs text-slate-600 leading-relaxed">
              Install Monzi on your iPhone or iPad for a full-screen, native application experience with offline support:
            </p>

            <div className="space-y-3 pt-2 text-xs">
              <div className="flex items-start gap-3 p-3 rounded-xl bg-slate-50 border border-slate-100">
                <div className="w-7 h-7 rounded-lg bg-blue-100 text-blue-600 flex items-center justify-center shrink-0">
                  <Share2 className="w-4 h-4" />
                </div>
                <div>
                  <span className="font-bold text-slate-900">Step 1:</span>
                  <p className="text-slate-500 mt-0.5">Tap the Safari <span className="font-semibold text-slate-800">Share</span> button at the bottom of the screen.</p>
                </div>
              </div>

              <div className="flex items-start gap-3 p-3 rounded-xl bg-slate-50 border border-slate-100">
                <div className="w-7 h-7 rounded-lg bg-indigo-100 text-indigo-600 flex items-center justify-center shrink-0">
                  <PlusSquare className="w-4 h-4" />
                </div>
                <div>
                  <span className="font-bold text-slate-900">Step 2:</span>
                  <p className="text-slate-500 mt-0.5">Scroll down and tap <span className="font-semibold text-slate-800">&quot;Add to Home Screen&quot;</span>.</p>
                </div>
              </div>
            </div>

            <button
              onClick={() => setShowIosModal(false)}
              className="w-full py-2.5 rounded-xl text-xs font-semibold bg-slate-900 text-white mt-2 hover:bg-slate-800 transition-all"
            >
              Got it
            </button>
          </div>
        </div>
      )}
    </PwaContext.Provider>
  );
}

export function usePwa() {
  const context = useContext(PwaContext);
  if (!context) {
    throw new Error('usePwa must be used within a PwaProvider');
  }
  return context;
}
