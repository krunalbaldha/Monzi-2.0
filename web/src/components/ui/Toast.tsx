'use client';

import React, { createContext, useContext, useState, useCallback } from 'react';
import { CheckCircle2, AlertCircle, Info, X } from 'lucide-react';

type ToastType = 'success' | 'error' | 'info';

interface ToastItem {
  id: string;
  type: ToastType;
  message: string;
}

interface ToastContextType {
  toast: {
    success: (msg: string) => void;
    error: (msg: string) => void;
    info: (msg: string) => void;
  };
}

const ToastContext = createContext<ToastContextType | undefined>(undefined);

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);

  const addToast = useCallback((type: ToastType, message: string) => {
    const id = Math.random().toString(36).substring(2, 9);
    setToasts((prev) => [...prev, { id, type, message }]);

    setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== id));
    }, 3800);
  }, []);

  const removeToast = (id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  };

  const contextValue: ToastContextType = {
    toast: {
      success: (msg: string) => addToast('success', msg),
      error: (msg: string) => addToast('error', msg),
      info: (msg: string) => addToast('info', msg),
    },
  };

  return (
    <ToastContext.Provider value={contextValue}>
      {children}

      {/* Floating Glass Toast Container (respects iPhone safe-area-inset-top) */}
      <div
        className="fixed right-4 left-4 sm:left-auto z-50 flex flex-col gap-2.5 pointer-events-none max-w-sm sm:w-80"
        style={{ top: 'max(env(safe-area-inset-top), 16px)' }}
      >
        {toasts.map((t) => (
          <div
            key={t.id}
            role="status"
            aria-live="polite"
            className={`pointer-events-auto flex items-center justify-between px-4 py-3 rounded-2xl shadow-[0_8px_32px_rgba(0,0,0,0.10)] bg-white/85 backdrop-blur-2xl border transition-all animate-slide-up text-[13px] font-semibold ${
              t.type === 'success'
                ? 'border-emerald-200/80 text-slate-900'
                : t.type === 'error'
                ? 'border-rose-200/80 text-slate-900'
                : 'border-blue-200/80 text-slate-900'
            }`}
          >
            <div className="flex items-center gap-3 min-w-0">
              <div
                className={`w-7 h-7 rounded-xl flex items-center justify-center shrink-0 ${
                  t.type === 'success'
                    ? 'bg-emerald-50 text-emerald-600'
                    : t.type === 'error'
                    ? 'bg-rose-50 text-rose-600'
                    : 'bg-blue-50 text-blue-600'
                }`}
              >
                {t.type === 'success' && <CheckCircle2 className="w-4 h-4" />}
                {t.type === 'error' && <AlertCircle className="w-4 h-4" />}
                {t.type === 'info' && <Info className="w-4 h-4" />}
              </div>
              <span className="leading-snug break-words">{t.message}</span>
            </div>
            <button
              type="button"
              onClick={() => removeToast(t.id)}
              className="min-w-[36px] min-h-[36px] flex items-center justify-center rounded-xl text-slate-400 hover:text-slate-700 hover:bg-slate-100/80 transition-all ml-2 shrink-0"
              aria-label="Dismiss notification"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error('useToast must be used within a ToastProvider');
  }
  return context.toast;
}
