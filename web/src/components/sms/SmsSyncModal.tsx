'use client';

import React, { useState } from 'react';
import { 
  MessageSquare, 
  X, 
  ArrowUpRight, 
  ArrowDownLeft, 
  Check, 
  Sparkles, 
  ShieldCheck, 
  AlertTriangle,
  Upload,
  CreditCard,
  Hash,
  Wallet
} from 'lucide-react';
import { useSms } from '@/components/sms/SmsProvider';
import { parseBankSms, ParsedTransaction } from '@/lib/parser';
import { useToast } from '@/components/ui/Toast';

interface SmsSyncModalProps {
  onSuccess?: () => void;
}

export function SmsSyncModal({ onSuccess }: SmsSyncModalProps) {
  const { isSyncModalOpen, closeSyncModal, isListening, ingestSmsText } = useSms();
  const [smsInput, setSmsInput] = useState('');
  const [parsedResult, setParsedResult] = useState<ParsedTransaction | null>(null);
  const [parseError, setParseError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const toast = useToast();

  if (!isSyncModalOpen) return null;

  const handleInputChange = (text: string) => {
    setSmsInput(text);
    if (!text.trim()) {
      setParsedResult(null);
      setParseError(null);
      return;
    }

    // Check for OTP
    if (/\b(otp|one time password|verification code|pin)\b/i.test(text)) {
      setParsedResult(null);
      setParseError('Security Policy: OTPs and verification codes are strictly filtered out and cannot be ingested.');
      return;
    }

    const res = parseBankSms(text);
    if (res) {
      setParsedResult(res);
      setParseError(null);
    } else {
      setParsedResult(null);
      setParseError('No standard financial transaction detected in this message. Check that it contains debit/credit details.');
    }
  };

  const handleSync = async () => {
    if (!smsInput.trim() || !parsedResult) return;

    setIsSubmitting(true);
    try {
      const res = await ingestSmsText(smsInput);
      if (res.success) {
        toast.success(`Synced ₹${parsedResult.amount.toLocaleString('en-IN')} (${parsedResult.bank_name}) to database`);
        setSmsInput('');
        setParsedResult(null);
        if (onSuccess) onSuccess();
        closeSyncModal();
      } else {
        toast.error(res.error || 'Failed to sync SMS');
      }
    } catch (err: any) {
      toast.error(err.message || 'Error syncing SMS');
    } finally {
      setIsSubmitting(false);
    }
  };

  const formatInr = (val: number) => {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 2,
    }).format(val);
  };

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto bg-slate-900/60 backdrop-blur-md p-3 sm:p-6 flex items-center justify-center min-h-screen animate-fade-in">
      <div className="fixed inset-0 -z-10" onClick={closeSyncModal} aria-hidden="true" />

      <div className="relative w-full max-w-lg bg-white rounded-2xl sm:rounded-3xl p-5 sm:p-7 shadow-2xl border border-slate-100 my-auto text-left animate-slide-up space-y-4">
        {/* Header */}
        <div className="flex items-center justify-between pb-3 border-b border-slate-100">
          <div className="flex items-center gap-2.5">
            <div className="w-10 h-10 rounded-2xl bg-blue-50 text-blue-600 flex items-center justify-center">
              <MessageSquare className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-base font-bold text-slate-900">Bank SMS Reader & Sync</h3>
              <p className="text-[11px] text-slate-500 font-medium">Automatic on-device transaction extraction</p>
            </div>
          </div>
          <button
            onClick={closeSyncModal}
            className="p-1.5 rounded-full text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition-all"
            aria-label="Close"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Live Listener Status Badge */}
        <div className="p-3 rounded-xl bg-slate-50 border border-slate-200/80 flex items-center justify-between text-xs">
          <div className="flex items-center gap-2">
            <span className={`w-2.5 h-2.5 rounded-full ${isListening ? 'bg-emerald-500 animate-pulse' : 'bg-blue-500'}`} />
            <span className="font-semibold text-slate-800">
              {isListening ? 'WebOTP SMS Receiver Active' : 'Real-Time Bank SMS Parser Ready'}
            </span>
          </div>
          <span className="text-[10px] font-bold text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded-full border border-emerald-200">
            Encrypted
          </span>
        </div>

        {/* SMS Text Area */}
        <div>
          <label className="block text-[11px] font-bold text-slate-700 uppercase tracking-wider mb-1.5">
            Paste or Receive Bank SMS Text
          </label>
          <textarea
            rows={4}
            value={smsInput}
            onChange={(e) => handleInputChange(e.target.value)}
            placeholder="Paste raw bank SMS message here (e.g. from HDFC, ICICI, SBI, Axis, Kotak)..."
            className="w-full p-3 text-xs rounded-xl bg-slate-50 border border-slate-200 focus:bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/20 text-slate-900 font-mono leading-relaxed"
            autoFocus
          />
        </div>

        {/* Live Parsed Preview */}
        {parsedResult && (
          <div className="p-4 rounded-2xl bg-blue-50/60 border border-blue-200/80 space-y-3 animate-fade-in text-xs">
            <div className="flex items-center justify-between">
              <span className="text-[11px] font-bold text-blue-900 uppercase tracking-wider flex items-center gap-1.5">
                <Sparkles className="w-3.5 h-3.5 text-blue-600" />
                <span>Extracted Transaction Details</span>
              </span>
              <span className={`px-2 py-0.5 rounded-md font-bold text-[10px] ${
                parsedResult.direction === 'CREDIT' ? 'bg-emerald-100 text-emerald-800' : 'bg-rose-100 text-rose-800'
              }`}>
                {parsedResult.direction}
              </span>
            </div>

            <div className="grid grid-cols-2 gap-2 text-xs">
              <div className="p-2.5 rounded-xl bg-white border border-blue-100">
                <span className="text-[10px] text-slate-400 block font-medium">Bank & Account</span>
                <span className="font-bold text-slate-900">{parsedResult.bank_name} ({parsedResult.account_mask})</span>
              </div>

              <div className="p-2.5 rounded-xl bg-white border border-blue-100">
                <span className="text-[10px] text-slate-400 block font-medium">Amount</span>
                <span className={`font-mono font-bold ${parsedResult.direction === 'CREDIT' ? 'text-emerald-600' : 'text-slate-900'}`}>
                  {parsedResult.direction === 'CREDIT' ? '+' : '-'} {formatInr(parsedResult.amount)}
                </span>
              </div>

              <div className="p-2.5 rounded-xl bg-white border border-blue-100">
                <span className="text-[10px] text-slate-400 block font-medium">Merchant / Destination</span>
                <span className="font-bold text-slate-900 truncate block">{parsedResult.merchant || 'Bank Transaction'}</span>
              </div>

              <div className="p-2.5 rounded-xl bg-white border border-blue-100">
                <span className="text-[10px] text-slate-400 block font-medium">Available Balance</span>
                <span className="font-mono font-bold text-emerald-800">
                  {parsedResult.parsed_available_balance !== undefined
                    ? formatInr(parsedResult.parsed_available_balance)
                    : 'N/A'}
                </span>
              </div>
            </div>

            {parsedResult.reference_id && (
              <p className="text-[10px] text-slate-500 font-mono">
                UTR / Ref: <span className="font-bold text-slate-700">{parsedResult.reference_id}</span>
              </p>
            )}

            <button
              onClick={handleSync}
              disabled={isSubmitting}
              className="w-full py-3 rounded-xl bg-blue-600 text-white font-semibold text-xs shadow-md shadow-blue-500/25 hover:bg-blue-700 active:scale-98 transition-all flex items-center justify-center gap-1.5 disabled:opacity-50"
            >
              <Check className="w-4 h-4" />
              <span>{isSubmitting ? 'Syncing to Database...' : 'Sync to Database Now'}</span>
            </button>
          </div>
        )}

        {/* Parse Error / OTP Warning */}
        {parseError && (
          <div className="p-3.5 rounded-xl bg-amber-50 border border-amber-200/80 text-xs text-amber-800 flex items-start gap-2 animate-fade-in">
            <AlertTriangle className="w-4 h-4 text-amber-600 shrink-0 mt-0.5" />
            <p className="leading-snug">{parseError}</p>
          </div>
        )}

        {/* Footer Guidance */}
        <p className="text-[11px] text-slate-400 leading-relaxed text-center pt-1">
          Messages are verified locally on your device before saving. Non-financial messages are discarded.
        </p>
      </div>
    </div>
  );
}
