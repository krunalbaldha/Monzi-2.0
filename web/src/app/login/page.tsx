'use client';

import React, { Suspense, useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import {
  Sparkles,
  Mail,
  Lock,
  Eye,
  EyeOff,
  Fingerprint,
  ArrowRight,
  AlertCircle,
  CheckCircle2,
  ShieldCheck,
} from 'lucide-react';
import { createBrowserSupabaseClient } from '@/lib/supabase/client';
import { BIOMETRIC_DEVICE_STORAGE_KEY, useAuth } from '@/components/auth/AuthProvider';
import { Spinner } from '@/components/ui/Spinner';

interface StoredBiometricDevice {
  credentialId?: string;
  email?: string;
  fullName?: string;
}

const REMEMBERED_EMAIL_KEY = 'monzi_remembered_email';

function getSafeNext(nextParam: string | null): string {
  if (!nextParam || !nextParam.startsWith('/') || nextParam.startsWith('//')) {
    return '/';
  }
  return nextParam;
}

function LoginFormContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const nextPath = getSafeNext(searchParams.get('next'));
  const callbackError = searchParams.get('error');

  const {
    user,
    isLocked,
    biometricEnabled,
    hasLocalBiometricDevice,
    unlockWithBiometric,
    refreshAuth,
  } = useAuth();

  const supabase = useMemo(() => createBrowserSupabaseClient(), []);

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [rememberMe, setRememberMe] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [biometricLoading, setBiometricLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(
    callbackError === 'auth_callback_failed'
      ? 'Verification link expired or invalid. Please sign in or request a new link.'
      : null
  );
  const [infoMsg, setInfoMsg] = useState<string | null>(null);
  const [storedDevice, setStoredDevice] = useState<StoredBiometricDevice | null>(null);

  useEffect(() => {
    if (typeof window === 'undefined') return;
    try {
      const remembered = window.localStorage.getItem(REMEMBERED_EMAIL_KEY);
      if (remembered) {
        setEmail(remembered);
        setRememberMe(true);
      }

      const rawBio = window.localStorage.getItem(BIOMETRIC_DEVICE_STORAGE_KEY);
      if (rawBio) {
        const parsed: StoredBiometricDevice = JSON.parse(rawBio);
        setStoredDevice(parsed);
        if (!remembered && parsed.email) {
          setEmail(parsed.email);
        }
      }
    } catch {
      // Ignore storage errors
    }
  }, []);

  const showBiometricCard =
    Boolean(storedDevice?.credentialId) ||
    hasLocalBiometricDevice ||
    Boolean(isLocked && biometricEnabled);

  const handleBiometricAction = async () => {
    setErrorMsg(null);
    setInfoMsg(null);

    if (user && isLocked) {
      setBiometricLoading(true);
      const res = await unlockWithBiometric();
      setBiometricLoading(false);
      if (res.success) {
        router.replace(nextPath);
      } else {
        setErrorMsg(res.error || 'Biometric unlock failed. Use your password below.');
      }
      return;
    }

    // No active Supabase session on this browser yet
    if (storedDevice?.email && !email) {
      setEmail(storedDevice.email);
    }
    setInfoMsg(
      'No active session found on this browser. Please enter your email and password once to sign in and re-enable instant biometric unlock.'
    );
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);
    setInfoMsg(null);

    const trimmedEmail = email.trim();
    if (!trimmedEmail || !password) {
      setErrorMsg('Please enter both your email and password.');
      return;
    }

    setSubmitting(true);
    try {
      const { error } = await supabase.auth.signInWithPassword({
        email: trimmedEmail,
        password,
      });

      if (error) {
        setErrorMsg(error.message || 'Invalid email or password.');
        setSubmitting(false);
        return;
      }

      if (typeof window !== 'undefined') {
        if (rememberMe) {
          window.localStorage.setItem(REMEMBERED_EMAIL_KEY, trimmedEmail);
        } else {
          window.localStorage.removeItem(REMEMBERED_EMAIL_KEY);
        }
      }

      // Ensure lock cookie is cleared on fresh password sign-in
      await fetch('/api/auth/session', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ locked: false }),
      }).catch(() => null);

      await refreshAuth();
      router.replace(nextPath);
      router.refresh();
    } catch (err: any) {
      setErrorMsg(err?.message || 'Unable to sign in right now.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen w-full flex flex-col items-center justify-center px-4 py-10 relative">
      {/* Subtle Ambient Blobs */}
      <div className="ambient-bg">
        <div className="ambient-blob-1" />
        <div className="ambient-blob-2" />
        <div className="ambient-blob-3" />
      </div>

      <div className="w-full max-w-md space-y-5 animate-slide-up">
        {/* Brand Header */}
        <div className="flex flex-col items-center text-center">
          <div className="w-14 h-14 rounded-2xl bg-gradient-to-tr from-blue-600 to-indigo-600 flex items-center justify-center text-white shadow-lg shadow-blue-500/30 mb-3">
            <Sparkles className="w-7 h-7" />
          </div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-bold tracking-tight text-slate-900">Monzi</h1>
            <span className="text-xs font-bold px-2 py-0.5 rounded-md bg-blue-600 text-white shadow-sm shadow-blue-500/30">
              2.0
            </span>
          </div>
          <p className="text-sm font-medium text-slate-500 mt-1">
            Personal Finance Intelligence • Sign in to continue
          </p>
        </div>

        {/* Biometric Quick Unlock Card */}
        {showBiometricCard && (
          <div className="glass-card p-5 border border-blue-200/70 bg-gradient-to-br from-white/85 to-blue-50/70">
            <div className="flex items-center gap-3.5">
              <div className="w-11 h-11 rounded-2xl bg-gradient-to-tr from-blue-600 to-indigo-600 text-white flex items-center justify-center shadow-md shadow-blue-500/25 shrink-0">
                <Fingerprint className="w-6 h-6" />
              </div>
              <div className="min-w-0 flex-1">
                <p className="text-sm font-bold text-slate-900 truncate">
                  {storedDevice?.fullName
                    ? `Welcome back, ${storedDevice.fullName}`
                    : 'Biometric Quick Access'}
                </p>
                <p className="text-xs text-slate-500 truncate">
                  {user && isLocked
                    ? 'Session locked — verify with Fingerprint / Face ID'
                    : storedDevice?.email || 'Fingerprint / Face ID enrolled on this device'}
                </p>
              </div>
            </div>
            <button
              type="button"
              onClick={handleBiometricAction}
              disabled={biometricLoading}
              className="btn-primary w-full mt-4"
            >
              {biometricLoading ? (
                <>
                  <Spinner className="w-4 h-4 text-white" />
                  <span>Verifying Biometrics...</span>
                </>
              ) : (
                <>
                  <Fingerprint className="w-4 h-4" />
                  <span>Unlock / Sign In with Biometrics</span>
                </>
              )}
            </button>
          </div>
        )}

        {/* Main Glass Login Card */}
        <div className="glass-card p-6 sm:p-7">
          {errorMsg && (
            <div className="mb-4 p-3.5 rounded-xl bg-rose-50/90 border border-rose-200 text-rose-700 text-xs font-medium flex items-start gap-2.5">
              <AlertCircle className="w-4 h-4 shrink-0 mt-0.5 text-rose-600" />
              <span>{errorMsg}</span>
            </div>
          )}

          {infoMsg && (
            <div className="mb-4 p-3.5 rounded-xl bg-blue-50/90 border border-blue-200 text-blue-800 text-xs font-medium flex items-start gap-2.5">
              <ShieldCheck className="w-4 h-4 shrink-0 mt-0.5 text-blue-600" />
              <span>{infoMsg}</span>
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4">
            {/* Email */}
            <div>
              <label
                htmlFor="login-email"
                className="block text-xs font-semibold text-slate-700 mb-1.5"
              >
                Email Address
              </label>
              <div className="relative">
                <Mail className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
                <input
                  id="login-email"
                  type="email"
                  autoComplete="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="you@example.com"
                  className="w-full min-h-[44px] pl-10 pr-4 py-2.5 rounded-xl bg-white/90 border border-slate-200/90 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500/30 focus:border-blue-500 transition-all"
                />
              </div>
            </div>

            {/* Password */}
            <div>
              <div className="flex items-center justify-between mb-1.5">
                <label
                  htmlFor="login-password"
                  className="block text-xs font-semibold text-slate-700"
                >
                  Password
                </label>
                <Link
                  href="/forgot-password"
                  className="text-xs font-semibold text-blue-600 hover:text-blue-700 transition-colors"
                >
                  Forgot Password?
                </Link>
              </div>
              <div className="relative">
                <Lock className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
                <input
                  id="login-password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="current-password"
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="Enter your password"
                  className="w-full min-h-[44px] pl-10 pr-11 py-2.5 rounded-xl bg-white/90 border border-slate-200/90 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500/30 focus:border-blue-500 transition-all"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((prev) => !prev)}
                  className="absolute right-2 top-1/2 -translate-y-1/2 w-9 h-9 flex items-center justify-center rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100/80 transition-colors"
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                >
                  {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
            </div>

            {/* Remember Me */}
            <div className="flex items-center justify-between pt-0.5">
              <label className="inline-flex items-center gap-2.5 cursor-pointer select-none min-h-[36px]">
                <input
                  type="checkbox"
                  checked={rememberMe}
                  onChange={(e) => setRememberMe(e.target.checked)}
                  className="w-4 h-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500/30"
                />
                <span className="text-xs font-medium text-slate-600">Remember my email</span>
              </label>
              <span className="inline-flex items-center gap-1 text-[11px] font-medium text-emerald-600">
                <CheckCircle2 className="w-3.5 h-3.5" />
                Encrypted
              </span>
            </div>

            {/* Submit */}
            <button
              type="submit"
              disabled={submitting}
              className="btn-primary w-full mt-2"
            >
              {submitting ? (
                <>
                  <Spinner className="w-4 h-4 text-white" />
                  <span>Signing in...</span>
                </>
              ) : (
                <>
                  <span>Sign In</span>
                  <ArrowRight className="w-4 h-4" />
                </>
              )}
            </button>
          </form>

          <div className="mt-6 pt-5 border-t border-slate-200/70 text-center">
            <p className="text-xs text-slate-500">
              Don&apos;t have a Monzi account?{' '}
              <Link
                href="/signup"
                className="font-semibold text-blue-600 hover:text-blue-700 transition-colors"
              >
                Create Account
              </Link>
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}

export default function LoginPage() {
  return (
    <Suspense
      fallback={
        <div className="min-h-screen flex items-center justify-center">
          <Spinner className="w-6 h-6 text-blue-600" />
        </div>
      }
    >
      <LoginFormContent />
    </Suspense>
  );
}
