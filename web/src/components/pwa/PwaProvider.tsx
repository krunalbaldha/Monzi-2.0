'use client';

import React, { createContext, useContext, useState, useEffect, useRef, useCallback } from 'react';
import { WifiOff, Share2, PlusSquare, X, RefreshCw, Sparkles } from 'lucide-react';
import { flushBrowserSyncQueue } from '@/lib/offlineStore';

interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

interface PwaContextType {
  isInstallable: boolean;
  isInstalled: boolean;
  isIos: boolean;
  isOffline: boolean;
  updateAvailable: boolean;
  triggerInstall: () => void;
  openIosInstallModal: () => void;
  applyUpdate: () => void;
}

const PwaContext = createContext<PwaContextType | undefined>(undefined);

export function PwaProvider({ children }: { children: React.ReactNode }) {
  const [deferredPrompt, setDeferredPrompt] = useState<BeforeInstallPromptEvent | null>(null);
  const [isInstallable, setIsInstallable] = useState(false);
  const [isInstalled, setIsInstalled] = useState(false);
  const [isIos, setIsIos] = useState(false);
  const [isOffline, setIsOffline] = useState(false);
  const [showIosModal, setShowIosModal] = useState(false);
  const [updateAvailable, setUpdateAvailable] = useState(false);
  const [updateBannerDismissed, setUpdateBannerDismissed] = useState(false);

  const registrationRef = useRef<ServiceWorkerRegistration | null>(null);
  const waitingWorkerRef = useRef<ServiceWorker | null>(null);
  const reloadingOnControllerChangeRef = useRef(false);

  const tryFlushOfflineQueue = useCallback(async () => {
    try {
      const result = await flushBrowserSyncQueue();
      if (result.synced > 0 && typeof window !== 'undefined') {
        window.dispatchEvent(
          new CustomEvent('monzi:sms-synced', {
            detail: { internal: false, count: result.synced },
          })
        );
      }
    } catch {
      // Best-effort background flush
    }
  }, []);

  useEffect(() => {
    if (typeof window === 'undefined') return;

    let cancelled = false;
    let swRegistration: ServiceWorkerRegistration | null = null;

    const handleControllerChange = () => {
      if (reloadingOnControllerChangeRef.current) {
        reloadingOnControllerChangeRef.current = false;
        window.location.reload();
      }
    };

    const handleUpdateFound = () => {
      if (!swRegistration) return;
      const newWorker = swRegistration.installing;
      if (!newWorker) return;

      newWorker.addEventListener('statechange', () => {
        if (
          !cancelled &&
          newWorker.state === 'installed' &&
          navigator.serviceWorker.controller
        ) {
          waitingWorkerRef.current = newWorker;
          setUpdateAvailable(true);
          setUpdateBannerDismissed(false);
        }
      });
    };

    // 1. Register Service Worker & detect updates
    if ('serviceWorker' in navigator) {
      navigator.serviceWorker.addEventListener('controllerchange', handleControllerChange);

      navigator.serviceWorker
        .register('/sw.js')
        .then((reg) => {
          if (cancelled) return;
          swRegistration = reg;
          registrationRef.current = reg;

          if (reg.waiting && navigator.serviceWorker.controller) {
            waitingWorkerRef.current = reg.waiting;
            setUpdateAvailable(true);
          }

          reg.addEventListener('updatefound', handleUpdateFound);
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

    // 5. Track online / offline state & flush queued offline items when back online
    setIsOffline(!navigator.onLine);
    if (navigator.onLine) {
      void tryFlushOfflineQueue();
    }

    const handleOnline = () => {
      setIsOffline(false);
      void tryFlushOfflineQueue();
    };
    const handleOffline = () => setIsOffline(true);

    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);

    return () => {
      cancelled = true;
      if ('serviceWorker' in navigator) {
        navigator.serviceWorker.removeEventListener('controllerchange', handleControllerChange);
      }
      if (swRegistration) {
        swRegistration.removeEventListener('updatefound', handleUpdateFound);
      }
      window.removeEventListener('beforeinstallprompt', handleBeforeInstallPrompt);
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
    };
  }, [tryFlushOfflineQueue]);

  const applyUpdate = useCallback(() => {
    const waiting = waitingWorkerRef.current || registrationRef.current?.waiting;
    if (waiting) {
      reloadingOnControllerChangeRef.current = true;
      waiting.postMessage({ type: 'SKIP_WAITING' });
    } else if (typeof window !== 'undefined') {
      window.location.reload();
    }
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
        updateAvailable,
        triggerInstall,
        openIosInstallModal,
        applyUpdate,
      }}
    >
      {/* Offline Status Bar Banner */}
      {isOffline && (
        <div className="bg-amber-500 text-slate-900 text-xs font-semibold px-4 py-2 flex items-center justify-center gap-2 sticky top-0 z-50 shadow-md">
          <WifiOff className="w-4 h-4 shrink-0" />
          <span>You are working in Offline Mode. Viewing cached financial data.</span>
        </div>
      )}

      {children}

      {/* Sleek Apple Glass Floating PWA Update Banner */}
      {updateAvailable && !updateBannerDismissed && (
        <div className="fixed bottom-24 md:bottom-6 left-1/2 -translate-x-1/2 z-50 w-[calc(100%-2rem)] max-w-md animate-slide-up">
          <div className="bg-white/85 backdrop-blur-2xl border border-blue-200/80 shadow-[0_12px_36px_rgba(37,99,235,0.18)] rounded-2xl px-4 py-3 flex items-center justify-between gap-3">
            <div className="flex items-center gap-2.5 min-w-0">
              <div className="w-8 h-8 rounded-xl bg-gradient-to-tr from-blue-600 to-indigo-600 text-white flex items-center justify-center shrink-0 shadow-sm">
                <Sparkles className="w-4 h-4" />
              </div>
              <div className="min-w-0">
                <p className="text-xs font-bold text-slate-900 truncate">
                  New version available
                </p>
                <p className="text-[11px] text-slate-500 truncate">
                  Refresh to update Monzi 2.0
                </p>
              </div>
            </div>
            <div className="flex items-center gap-1.5 shrink-0">
              <button
                type="button"
                onClick={applyUpdate}
                className="inline-flex items-center gap-1.5 px-3 py-2 rounded-xl text-xs font-bold bg-gradient-to-r from-blue-600 to-indigo-600 text-white shadow-sm shadow-blue-500/25 hover:shadow-md transition-all active:scale-95"
              >
                <RefreshCw className="w-3.5 h-3.5" />
                <span>Refresh to update</span>
              </button>
              <button
                type="button"
                onClick={() => setUpdateBannerDismissed(true)}
                className="p-1.5 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-colors"
                aria-label="Dismiss update notification"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
          </div>
        </div>
      )}

      {/* iOS "Add to Home Screen" Instructions Bottom Sheet Modal */}
      {showIosModal && (
        <div className="fixed inset-0 z-50 overflow-y-auto bg-slate-900/60 backdrop-blur-md p-4 flex items-center justify-center min-h-screen animate-fade-in">
          <div className="fixed inset-0 -z-10" onClick={() => setShowIosModal(false)} aria-hidden="true" />
          <div className="relative w-full max-w-sm bg-white rounded-2xl sm:rounded-3xl p-6 shadow-2xl border border-slate-100 my-auto text-left space-y-4 animate-slide-up">
            <div className="flex items-center justify-between pb-2 border-b border-slate-100">
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
