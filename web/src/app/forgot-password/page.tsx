'use client';

import React, { useMemo, useState } from 'react';
import Link from 'next/link';
import { Sparkles, Mail, ArrowLeft, ArrowRight, AlertCircle, MailCheck } from 'lucide-react';
import { createBrowserSupabaseClient } from '@/lib/supabase/client';
import { Spinner } from '@/components/ui/Spinner';

export default function ForgotPasswordPage() {
  const supabase = useMemo(() => createBrowserSupabaseClient(), []);
  const [email, setEmail] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [sent, setSent] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);
    const trimmed = email.trim();
    if (!trimmed) {
      setErrorMsg('Please enter your account email address.');
      return;
    }

    setSubmitting(true);
    try {
      const origin = typeof window !== 'undefined' ? window.location.origin : '';
      const { error } = await supabase.auth.resetPasswordForEmail(trimmed, {
        redirectTo: `${origin}/auth/callback?next=/reset-password`,
      });

      if (error) {
        setErrorMsg(error.message || 'Could not send reset email.');
        setSubmitting(false);
        return;
      }

      setSent(true);
    } catch (err: any) {
      setErrorMsg(err?.message || 'Unable to send reset link right now.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen w-full flex flex-col items-center justify-center px-4 py-10 relative">
      <div className="ambient-bg">
        <div className="ambient-blob-1" />
        <div className="ambient-blob-2" />
        <div className="ambient-blob-3" />
      </div>

      <div className="w-full max-w-md space-y-5 animate-slide-up">
        <div className="flex flex-col items-center text-center">
          <div className="w-14 h-14 rounded-2xl bg-gradient-to-tr from-blue-600 to-indigo-600 flex items-center justify-center text-white shadow-lg shadow-blue-500/30 mb-3">
            <Sparkles className="w-7 h-7" />
          </div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">Reset Password</h1>
          <p className="text-sm font-medium text-slate-500 mt-1">
            Enter your email to receive a secure recovery link
          </p>
        </div>

        <div className="glass-card p-6 sm:p-7">
          {sent ? (
            <div className="text-center space-y-4 py-2">
              <div className="w-14 h-14 rounded-2xl bg-blue-50 border border-blue-200 text-blue-600 flex items-center justify-center mx-auto">
                <MailCheck className="w-7 h-7" />
              </div>
              <div>
                <h2 className="text-lg font-bold text-slate-900">Recovery link sent</h2>
                <p className="text-xs text-slate-500 mt-1.5 leading-relaxed">
                  If an account exists for <span className="font-semibold text-slate-800">{email}</span>,
                  you will receive a password reset email shortly.
                </p>
              </div>
              <Link href="/login" className="btn-primary w-full">
                <ArrowLeft className="w-4 h-4" />
                <span>Back to Sign In</span>
              </Link>
            </div>
          ) : (
            <>
              {errorMsg && (
                <div className="mb-4 p-3.5 rounded-xl bg-rose-50/90 border border-rose-200 text-rose-700 text-xs font-medium flex items-start gap-2.5">
                  <AlertCircle className="w-4 h-4 shrink-0 mt-0.5 text-rose-600" />
                  <span>{errorMsg}</span>
                </div>
              )}

              <form onSubmit={handleSubmit} className="space-y-4">
                <div>
                  <label
                    htmlFor="forgot-email"
                    className="block text-xs font-semibold text-slate-700 mb-1.5"
                  >
                    Email Address
                  </label>
                  <div className="relative">
                    <Mail className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
                    <input
                      id="forgot-email"
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

                <button type="submit" disabled={submitting} className="btn-primary w-full">
                  {submitting ? (
                    <>
                      <Spinner className="w-4 h-4 text-white" />
                      <span>Sending Reset Link...</span>
                    </>
                  ) : (
                    <>
                      <span>Send Reset Link</span>
                      <ArrowRight className="w-4 h-4" />
                    </>
                  )}
                </button>
              </form>

              <div className="mt-6 pt-5 border-t border-slate-200/70 text-center">
                <Link
                  href="/login"
                  className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-600 hover:text-blue-600 transition-colors"
                >
                  <ArrowLeft className="w-3.5 h-3.5" />
                  <span>Back to Sign In</span>
                </Link>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
