import { NextRequest, NextResponse } from 'next/server';
import { supabase, isSupabaseConfigured } from '@/lib/supabase';
import { generateSmsFingerprint } from '@/lib/deduplication';
import { parseBankSms } from '@/lib/parser';
import { IncomingSmsPayload } from '@/lib/types';

export async function POST(req: NextRequest) {
  try {
    const authHeader = req.headers.get('x-monzi-secret') || req.headers.get('authorization')?.replace('Bearer ', '');
    const configuredSecret = process.env.MONZI_SYNC_SECRET || process.env.NEXT_PUBLIC_MONZI_SYNC_SECRET || '';

    const body: IncomingSmsPayload = await req.json();

    // Verify authentication secret
    const providedSecret = authHeader || body.sync_secret;
    const isInternalApp = body.device_id === 'pwa-sms-receiver' || body.device_id === 'web-manual-entry' || body.device_id === 'web-manual-account';
    if (!isInternalApp && configuredSecret && providedSecret !== configuredSecret) {
      return NextResponse.json(
        { error: 'Unauthorized: Invalid sync secret' },
        { status: 401 }
      );
    }

    if (!body.transactions || !Array.isArray(body.transactions)) {
      return NextResponse.json(
        { error: 'Bad Request: transactions array required' },
        { status: 400 }
      );
    }

    let processedCount = 0;
    let duplicateCount = 0;
    const errors: Array<{ index: number; message: string }> = [];

    for (let i = 0; i < body.transactions.length; i++) {
      const item = body.transactions[i];

      // If raw SMS body is provided without pre-parsed fields, parse it on the fly
      let txData = { ...item };
      if (item.raw_body && (!item.amount || !item.bank_name)) {
        const parsed = parseBankSms(item.raw_body, item.bank_name);
        if (!parsed) {
          errors.push({ index: i, message: 'SMS could not be parsed as financial message' });
          continue;
        }
        txData = {
          ...txData,
          amount: parsed.amount,
          direction: parsed.direction,
          bank_name: parsed.bank_name,
          account_mask: parsed.account_mask,
          merchant: parsed.merchant || txData.merchant,
          reference_id: parsed.reference_id || txData.reference_id,
          payment_mode: parsed.payment_mode || txData.payment_mode,
          parsed_available_balance: parsed.parsed_available_balance,
        };
      }

      // Generate deterministic fingerprint
      const fingerprint =
        txData.sms_fingerprint ||
        generateSmsFingerprint({
          transaction_date: txData.transaction_date,
          amount: txData.amount,
          direction: txData.direction,
          account_mask: txData.account_mask,
          reference_id: txData.reference_id,
        });

      if (!isSupabaseConfigured) {
        // If running in local demo/mock mode, just record success
        processedCount++;
        continue;
      }

      // 1. Check duplicate via fingerprint
      const { data: existing } = await supabase
        .from('transactions')
        .select('id')
        .eq('sms_fingerprint', fingerprint)
        .maybeSingle();

      if (existing) {
        duplicateCount++;
        continue;
      }

      // 2. Upsert bank account dynamically
      let accountId: string | null = null;
      if (txData.bank_name && txData.account_mask) {
        const { data: account } = await supabase
          .from('accounts')
          .select('id, current_balance')
          .eq('bank_name', txData.bank_name)
          .eq('account_number_mask', txData.account_mask)
          .maybeSingle();

        if (account) {
          accountId = account.id;
          // Update balance if available in SMS
          if (txData.parsed_available_balance !== undefined) {
            await supabase
              .from('accounts')
              .update({
                current_balance: txData.parsed_available_balance,
                last_synced_at: new Date().toISOString(),
              })
              .eq('id', account.id);
          }
        } else {
          // Auto-create account
          const { data: newAccount } = await supabase
            .from('accounts')
            .insert({
              bank_name: txData.bank_name,
              account_number_mask: txData.account_mask,
              account_type: 'SAVINGS',
              current_balance: txData.parsed_available_balance || 0,
              last_synced_at: new Date().toISOString(),
            })
            .select('id')
            .single();

          if (newAccount) accountId = newAccount.id;
        }
      }

      // 3. Insert transaction
      const isCcPayment = /credit card bill|card payment/i.test(txData.merchant || '');
      const txnType = isCcPayment
        ? 'CC_BILL_PAYMENT'
        : txData.direction === 'CREDIT'
        ? 'INCOME'
        : 'EXPENSE';

      const { error: insertError } = await supabase.from('transactions').insert({
        account_id: accountId,
        amount: txData.amount,
        direction: txData.direction,
        transaction_type: txnType,
        merchant: txData.merchant || 'Unknown Merchant',
        payment_mode: txData.payment_mode || 'UPI',
        reference_id: txData.reference_id,
        sms_fingerprint: fingerprint,
        parsed_available_balance: txData.parsed_available_balance,
        is_excluded_from_totals: isCcPayment,
        transaction_date: txData.transaction_date,
      });

      if (insertError) {
        errors.push({ index: i, message: insertError.message });
      } else {
        processedCount++;
      }
    }

    return NextResponse.json({
      success: true,
      processed_count: processedCount,
      duplicate_count: duplicateCount,
      errors: errors.length > 0 ? errors : undefined,
      server_timestamp: new Date().toISOString(),
    });
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : 'Internal Server Error';
    return NextResponse.json({ error: message }, { status: 500 });
  }
}
