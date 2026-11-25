'use client';

import React, { useState, useEffect } from 'react';
import {
  Fingerprint,
  Lock,
  Eye,
  EyeOff,
  LogOut,
  KeyRound,
  ShieldCheck,
  ChevronDown,
  ChevronUp,
  AlertCircle,
} from 'lucide-react';
import { useAuth } from '@/components/auth/AuthProvider';
import { Spinner } from '@/components/ui/Spinner';

export function BiometricLockScreen() {
  const {
    user,
    profile,
    isLocked,
    biometricSupported,
    biometricEnabled,
    hasLocalBiometricDevice,
    unlockWithBiometric,
    unlockWithPassword,
    signOut,
  } = useAuth();

  const canUseBiometric =
    biometricSupported && (biometricEnabled || hasLocalBiometricDevice);

  const [showPasswordForm, setShowPasswordForm] = useState(false);
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [biometricLoading, setBiometricLoading] = useState(false);
  const [passwordLoading, setPasswordLoading] = useState(false);
  const [signingOut, setSigningOut] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!canUseBiometric) {
      setShowPasswordForm(true);
    }
  }, [canUseBiometric]);

  if (!user || !isLocked) {
    return null;
  }

  const displayName =
    profile?.full_name ||
    user?.user_metadata?.full_name ||
    user?.email?.split('@')[0] ||
    'Monzi User';

  const displayEmail = profile?.email || user?.email || '';
  const initials =
    profile?.initials ||
    displayName
      .split(' ')
      .filter(Boolean)
      .slice(0, 2)
      .map((part: string) => part[0]?.toUpperCase() || '')
      .join('') ||
    'M';

  const handleBiometricUnlock = async () => {
    setError(null);
    setBiometricLoading(true);
    try {
      const result = await unlockWithBiometric();
      if (!result.success) {
        setError(
          result.error || 'Biometric verification was cancelled or failed.'
        );
      }
    } catch (err: unknown) {
      setError(
        err instanceof Error ? err.message : 'Unable to verify biometrics.'
      );
    } finally {
      setBiometricLoading(false);
    }
  };

  const handlePasswordUnlock = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!password) {
      setError('Please enter your account password.');
      return;
    }

    setError(null);
    setPasswordLoading(true);
    try {
      const result = await unlockWithPassword(password);
      if (!result.success) {
        setError(result.error || 'Incorrect password.');
      } else {
        setPassword('');
      }
    } catch (err: unknown) {
      setError(
        err instanceof Error ? err.message : 'Unable to unlock with password.'
      );
    } finally {
      setPasswordLoading(false);
    }
  };

  const handleSignOut = async () => {
    setSigningOut(true);
    try {
      await signOut();
    } finally {
      setSigningOut(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-[100] flex items-center justify-center p-4 sm:p-6 bg-slate-900/30 backdrop-blur-2xl animate-fade-in"
      role="dialog"
      aria-modal="true"
      aria-labelledby="biometric-lock-title"
    >
      {/* Subtle ambient depth inside lock screen */}
      <div className="pointer-events-none absolute inset-0 overflow-hidden">
        <div className="absolute -top-20 right-10 w-80 h-80 rounded-full bg-blue-500/15 blur-3xl" />
        <div className="absolute -bottom-20 left-10 w-80 h-80 rounded-full bg-indigo-500/15 blur-3xl" />
      </div>

      <div className="relative w-full max-w-md rounded-3xl bg-white/88 backdrop-blur-2xl border border-white/60 shadow-[0_24px_64px_rgba(15,23,42,0.18)] p-6 sm:p-8 animate-slide-up">
        {/* Top lock badge */}
        <div className="flex items-center justify-between mb-6">
          <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-blue-50/90 border border-blue-100 text-blue-700 text-xs font-semibold">
            <ShieldCheck className="w-3.5 h-3.5" />
            <span>Monzi 2.0 Vault</span>
          </div>
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-slate-100/90 text-slate-600 text-[11px] font-medium">
            <Lock className="w-3 h-3" />
            Session Locked
          </span>
        </div>

        {/* User identity & biometric icon */}
        <div className="flex flex-col items-center text-center mb-6">
          <div className="relative mb-4">
            <div className="w-[72px] h-[72px] rounded-2xl bg-gradient-to-br from-blue-600 to-indigo-600 flex items-center justify-center text-white shadow-[0_10px_28px_rgba(37,99,235,0.35)]">
              {canUseBiometric ? (
                <Fingerprint className="w-9 h-9" />
              ) : (
                <Lock className="w-8 h-8" />
              )}
            </div>
            <div className="absolute -bottom-1.5 -right-1.5 w-7 h-7 rounded-full bg-white border border-slate-100 shadow-sm flex items-center justify-center text-[11px] font-bold text-blue-600">
              {initials}
            </div>
          </div>

          <h2
            id="biometric-lock-title"
            className="text-xl font-bold tracking-tight text-slate-900"
          >
            {displayName}
          </h2>
          {displayEmail && (
            <p className="text-sm text-slate-500 mt-0.5 truncate max-w-full">
              {displayEmail}
            </p>
          )}
          <p className="text-xs text-slate-500 mt-2 max-w-xs leading-relaxed">
            Your financial dashboard is protected. Verify your identity to
            continue.
          </p>
        </div>

        {/* Error banner */}
        {error && (
          <div
            role="alert"
            className="mb-4 flex items-start gap-2.5 rounded-2xl bg-rose-50/90 border border-rose-200/80 px-3.5 py-3 text-xs font-medium text-rose-700"
          >
            <AlertCircle className="w-4 h-4 shrink-0 mt-0.5 text-rose-500" />
            <span>{error}</span>
          </div>
        )}

        {/* Primary biometric unlock CTA */}
        {canUseBiometric && (
          <button
            type="button"
            onClick={handleBiometricUnlock}
            disabled={biometricLoading || passwordLoading || signingOut}
            className="btn-primary w-full min-h-[48px] text-[15px] font-semibold disabled:opacity-60 disabled:cursor-not-allowed"
          >
            {biometricLoading ? (
              <>
                <Spinner size={18} colorClass="text-white" />
                <span>Verifying Biometrics...</span>
              </>
            ) : (
              <>
                <Fingerprint className="w-5 h-5" />
                <span>Unlock with Biometrics</span>
              </>
            )}
          </button>
        )}

        {/* Expandable password fallback */}
        <div className={canUseBiometric ? 'mt-4' : ''}>
          {canUseBiometric && (
            <button
              type="button"
              onClick={() => {
                setShowPasswordForm((prev) => !prev);
                setError(null);
              }}
              className="w-full min-h-[44px] flex items-center justify-center gap-2 rounded-xl text-xs font-semibold text-slate-600 hover:text-slate-900 hover:bg-slate-100/70 transition-colors"
            >
              <KeyRound className="w-3.5 h-3.5" />
              <span>Use Password Instead</span>
              {showPasswordForm ? (
                <ChevronUp className="w-3.5 h-3.5" />
              ) : (
                <ChevronDown className="w-3.5 h-3.5" />
              )}
            </button>
          )}

          {showPasswordForm && (
            <form
              onSubmit={handlePasswordUnlock}
              className="mt-3 space-y-3 pt-3 border-t border-slate-200/70"
            >
              <div>
                <label
                  htmlFor="lock-screen-password"
                  className="block text-xs font-semibold text-slate-700 mb-1.5"
                >
                  Account Password
                </label>
                <div className="relative">
                  <input
                    id="lock-screen-password"
                    type={showPassword ? 'text' : 'password'}
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="Enter your password"
                    autoComplete="current-password"
                    className="w-full min-h-[44px] bg-white border border-slate-200 rounded-xl pl-4 pr-11 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100 transition-all"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword((prev) => !prev)}
                    className="absolute right-1.5 top-1/2 -translate-y-1/2 min-w-[36px] min-h-[36px] flex items-center justify-center rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100/80 transition-colors"
                    aria-label={showPassword ? 'Hide password' : 'Show password'}
                  >
                    {showPassword ? (
                      <EyeOff className="w-4 h-4" />
                    ) : (
                      <Eye className="w-4 h-4" />
                    )}
                  </button>
                </div>
              </div>

              <button
                type="submit"
                disabled={passwordLoading || biometricLoading || signingOut}
                className={
                  canUseBiometric
                    ? 'btn-secondary w-full min-h-[44px] disabled:opacity-60'
                    : 'btn-primary w-full min-h-[48px] disabled:opacity-60'
                }
              >
                {passwordLoading ? (
                  <>
                    <Spinner
                      size={18}
                      colorClass={
                        canUseBiometric ? 'text-blue-600' : 'text-white'
                      }
                    />
                    <span>Unlocking...</span>
                  </>
                ) : (
                  <>
                    <KeyRound className="w-4 h-4" />
                    <span>Unlock with Password</span>
                  </>
                )}
              </button>
            </form>
          )}
        </div>

        {/* Sign Out footer */}
        <div className="mt-5 pt-4 border-t border-slate-200/70 flex items-center justify-center">
          <button
            type="button"
            onClick={handleSignOut}
            disabled={signingOut || biometricLoading || passwordLoading}
            className="min-h-[44px] px-4 inline-flex items-center gap-2 rounded-xl text-xs font-semibold text-slate-500 hover:text-rose-600 hover:bg-rose-50/70 transition-colors disabled:opacity-50"
          >
            {signingOut ? (
              <Spinner size={14} colorClass="text-rose-500" />
            ) : (
              <LogOut className="w-3.5 h-3.5" />
            )}
            <span>Sign Out</span>
          </button>
        </div>
      </div>
    </div>
  );
}
