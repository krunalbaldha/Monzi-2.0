import { NextRequest, NextResponse } from 'next/server';
import { createClient, SupabaseClient } from '@supabase/supabase-js';
import {
  getAuthenticatedUser,
  createBearerSupabaseClient,
} from '@/lib/supabase/server';
import { generateSmsFingerprint } from '@/lib/deduplication';
import { inspectBankSms, classifyMerchant } from '@/lib/parser';
import type {
  IncomingSmsPayload,
  SmsProcessingStatus,
  TransactionType,
} from '@/lib/types';

async function resolveSyncContext(
  req: NextRequest,
  body: IncomingSmsPayload
): Promise<{
  supabase?: SupabaseClient;
  userId?: string;
  deviceSyncToken?: string;
  useDeviceRpc?: boolean;
  errorResponse?: NextResponse;
}> {
  // 1. Cookie-authenticated Supabase user (browser / WebView session)
  const sessionAuth = await getAuthenticatedUser();
  if (sessionAuth) {
    return {
      supabase: sessionAuth.supabase as unknown as SupabaseClient,
      userId: sessionAuth.user.id,
    };
  }

  // 2. Bearer JWT (Android app or API client)
  const authHeader = req.headers.get('authorization');
  const bearerJwt = authHeader?.startsWith('Bearer ')
    ? authHeader.slice(7).trim()
    : '';

  if (bearerJwt) {
    const bearerClient = createBearerSupabaseClient(bearerJwt);
    const {
      data: { user },
      error,
    } = await bearerClient.auth.getUser(bearerJwt);

    if (!error && user) {
      return {
        supabase: bearerClient as unknown as SupabaseClient,
        userId: user.id,
      };
    }
  }

  // 3. Android background sync: high-entropy per-user x-monzi-sync-token
  const configuredSecret = process.env.MONZI_SYNC_SECRET || '';
  const providedSecret =
    req.headers.get('x-monzi-secret') || body.sync_secret || '';
  const syncToken = (
    req.headers.get('x-monzi-sync-token') ||
    body.device_sync_token ||
    ''
  ).trim();

  if (syncToken) {
    if (providedSecret && configuredSecret && providedSecret !== configuredSecret) {
      return {
        errorResponse: NextResponse.json(
          { error: 'Unauthorized: Invalid sync secret for device sync' },
          { status: 401 }
        ),
      };
    }

    const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL || '';
    const serviceRoleKey = process.env.SUPABASE_SERVICE_ROLE_KEY || '';
    const anonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY || '';

    if (!supabaseUrl || (!serviceRoleKey && !anonKey)) {
      return {
        errorResponse: NextResponse.json(
          { error: 'Supabase is not configured on the server' },
          { status: 503 }
        ),
      };
    }

    const client = createClient(supabaseUrl, serviceRoleKey || anonKey);
    const { data: resolvedUserId, error: rpcError } = await client.rpc(
      'resolve_device_sync_user',
      { p_token: syncToken }
    );

    if (rpcError) {
      return {
        errorResponse: NextResponse.json(
          {
            error:
              'Device sync RPC unavailable. Run migration 002_auth_rls_and_logs.sql or sign in with Bearer auth.',
            migration_pending: true,
          },
          { status: 503 }
        ),
      };
    }

    if (!resolvedUserId) {
      return {
        errorResponse: NextResponse.json(
          { error: 'Unauthorized: Invalid device sync token' },
          { status: 401 }
        ),
      };
    }

    return {
      supabase: client,
      userId: String(resolvedUserId),
      deviceSyncToken: syncToken,
      useDeviceRpc: !serviceRoleKey,
    };
  }

  return {
    errorResponse: NextResponse.json(
      { error: 'Unauthorized: Authentication required for SMS sync' },
      { status: 401 }
    ),
  };
}

function inferAccountType(bankName: string, merchant?: string): 'SAVINGS' | 'CREDIT_CARD' | 'WALLET' {
  if (/credit card|cc bill|cc payment/i.test(`${bankName} ${merchant || ''}`)) {
    return 'CREDIT_CARD';
  }
  if (/phonepe|google pay|paytm|amazon pay/i.test(bankName)) {
    return 'WALLET';
  }
  return 'SAVINGS';
}

export async function POST(req: NextRequest) {
  try {
    const body: IncomingSmsPayload = await req.json();

    const { supabase, userId, deviceSyncToken, useDeviceRpc, errorResponse } =
      await resolveSyncContext(req, body);
    if (errorResponse || !supabase || !userId) {
      return (
        errorResponse ||
        NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
      );
    }

    if (!body.transactions || !Array.isArray(body.transactions)) {
      return NextResponse.json(
        { error: 'Bad Request: transactions array required' },
        { status: 400 }
      );
    }

    const logProcessing = async (entry: {
      sender?: string | null;
      status: SmsProcessingStatus;
      reason: string;
      extracted_type?: string | null;
      extracted_amount?: number | null;
      extracted_bank?: string | null;
      extracted_date?: string | null;
      reference_id?: string | null;
      sms_fingerprint?: string | null;
    }) => {
      try {
        await supabase.from('sms_processing_logs').insert({
          user_id: userId,
          device_id: body.device_id || null,
          sender: entry.sender || null,
          status: entry.status,
          reason: entry.reason,
          extracted_type: entry.extracted_type || null,
          extracted_amount: entry.extracted_amount ?? null,
          extracted_bank: entry.extracted_bank || null,
          extracted_date: entry.extracted_date || null,
          reference_id: entry.reference_id || null,
          sms_fingerprint: entry.sms_fingerprint || null,
        });
      } catch {
        // Best-effort audit logging: ignore if migration 002 hasn't been applied yet
      }
    };

    let processedCount = 0;
    let duplicateCount = 0;
    let ignoredCount = 0;
    let failedCount = 0;
    let categoriesAssigned = 0;
    const errors: Array<{ index: number; message: string }> = [];

    // Pre-fetch categories for name -> id lookup
    const categoryMap: Record<string, string> = {};
    const { data: cats } = await supabase.from('categories').select('id, name');
    if (cats) {
      for (const cat of cats) {
        categoryMap[cat.name.toLowerCase()] = cat.id;
      }
    }

    for (let i = 0; i < body.transactions.length; i++) {
      const item = body.transactions[i];

      let amount = item.amount;
      let direction = item.direction;
      let bankName = item.bank_name;
      let accountMask = item.account_mask;
      let merchant = item.merchant;
      let referenceId = item.reference_id;
      let paymentMode = item.payment_mode;
      let parsedBalance = item.parsed_available_balance;
      let txDateStr = item.transaction_date;
      let txTypeOverride = item.transaction_type;
      let isSelfTransfer = Boolean(item.is_self_transfer);
      let isRefund = Boolean(item.is_refund);
      let categoryName = item.transaction_category || '';

      // If raw SMS text is provided (e.g. Paste/Bulk Modal), parse in memory only and NEVER persist raw_body
      if (item.raw_body) {
        const inspection = inspectBankSms(
          item.raw_body,
          item.sender || item.bank_name || '',
          item.transaction_date
        );

        if (!inspection.parsed) {
          const logStatus: SmsProcessingStatus =
            inspection.status === 'FAILED' ? 'FAILED' : 'IGNORED';
          await logProcessing({
            sender: item.sender || item.bank_name || null,
            status: logStatus,
            reason: inspection.reason || 'Non-financial or invalid SMS ignored',
          });
          if (logStatus === 'FAILED') {
            failedCount++;
          } else {
            ignoredCount++;
          }
          continue;
        }

        const p = inspection.parsed;
        amount = amount ?? p.amount;
        direction = direction || p.direction;
        bankName = bankName || p.bank_name;
        accountMask = accountMask || p.account_mask;
        merchant = merchant || p.merchant;
        referenceId = referenceId || p.reference_id;
        paymentMode = paymentMode || p.payment_mode;
        parsedBalance = parsedBalance ?? p.parsed_available_balance;
        txDateStr = p.transaction_date || txDateStr;
        txTypeOverride = txTypeOverride || p.transaction_type;
        isSelfTransfer = isSelfTransfer || Boolean(p.is_self_transfer);
        isRefund = isRefund || Boolean(p.is_refund);
        categoryName = categoryName || p.transaction_category || '';
      }

      if (
        typeof amount !== 'number' ||
        isNaN(amount) ||
        amount <= 0 ||
        (direction !== 'CREDIT' && direction !== 'DEBIT')
      ) {
        failedCount++;
        const msg = 'Invalid or missing transaction amount/direction';
        errors.push({ index: i, message: msg });
        await logProcessing({
          sender: item.sender || bankName || null,
          status: 'FAILED',
          reason: msg,
        });
        continue;
      }

      const resolvedBankName = (bankName || 'Bank').trim();
      const resolvedAccountMask = (accountMask || 'XX0000').trim();
      const parsedTxDate = txDateStr ? new Date(txDateStr) : new Date();
      const txDateIso = isNaN(parsedTxDate.getTime())
        ? new Date().toISOString()
        : parsedTxDate.toISOString();

      if (!categoryName) {
        categoryName = classifyMerchant(merchant || '');
      }

      // Resolve category_id
      let categoryId: string | null = null;
      if (categoryName) {
        const catKey = categoryName.toLowerCase();
        if (categoryMap[catKey]) {
          categoryId = categoryMap[catKey];
          categoriesAssigned++;
        } else {
          const partialKey = Object.keys(categoryMap).find(
            (k) => k.includes(catKey) || catKey.includes(k)
          );
          if (partialKey) {
            categoryId = categoryMap[partialKey];
            categoriesAssigned++;
          }
        }
      }

      // Deterministic SHA-256 fingerprint
      const fingerprint =
        item.sms_fingerprint ||
        generateSmsFingerprint({
          transaction_date: txDateIso,
          amount,
          direction,
          bank_name: resolvedBankName,
          account_mask: resolvedAccountMask,
          reference_id: referenceId,
          merchant,
        });

      // If using device_sync_token without SUPABASE_SERVICE_ROLE_KEY, execute via SECURITY DEFINER RPC
      if (useDeviceRpc && deviceSyncToken) {
        const isCcPaymentRpc =
          txTypeOverride === 'CC_BILL_PAYMENT' ||
          /credit card bill|card payment|cc payment/i.test(merchant || '');
        const isExcludedRpc = isCcPaymentRpc || isSelfTransfer;
        let txnTypeRpc: TransactionType = 'UNCLASSIFIED';
        if (isCcPaymentRpc) txnTypeRpc = 'CC_BILL_PAYMENT';
        else if (isSelfTransfer) txnTypeRpc = 'TRANSFER';
        else if (isRefund && direction === 'CREDIT') txnTypeRpc = 'REFUND';
        else if (direction === 'CREDIT') txnTypeRpc = 'INCOME';
        else txnTypeRpc = 'EXPENSE';

        const { data: rpcRes, error: ingestRpcErr } = await supabase.rpc(
          'device_sync_ingest_transaction',
          {
            p_sync_token: deviceSyncToken,
            p_device_id: body.device_id || 'android-device',
            p_sender: item.sender || resolvedBankName,
            p_amount: amount,
            p_direction: direction,
            p_transaction_type: txnTypeRpc,
            p_bank_name: resolvedBankName,
            p_account_mask: resolvedAccountMask,
            p_account_type: inferAccountType(resolvedBankName, merchant),
            p_merchant: merchant || 'Bank Transaction',
            p_payment_mode: paymentMode || 'UPI',
            p_reference_id: referenceId || null,
            p_sms_fingerprint: fingerprint,
            p_parsed_available_balance: parsedBalance ?? null,
            p_is_excluded_from_totals: isExcludedRpc,
            p_transaction_date: txDateIso,
            p_category_id: categoryId,
          }
        );

        if (ingestRpcErr) {
          failedCount++;
          errors.push({ index: i, message: ingestRpcErr.message });
        } else if (rpcRes?.status === 'DUPLICATE') {
          duplicateCount++;
        } else if (rpcRes?.status === 'PROCESSED') {
          processedCount++;
        } else {
          failedCount++;
          errors.push({
            index: i,
            message: rpcRes?.reason || 'Device sync ingestion rejected',
          });
        }
        continue;
      }

      // 1. Duplicate check by fingerprint scoped to user_id
      const { data: existingByFp, error: fpErr } = await supabase
        .from('transactions')
        .select('id')
        .eq('user_id', userId)
        .eq('sms_fingerprint', fingerprint)
        .maybeSingle();

      if (fpErr && fpErr.code === '42703') {
        return NextResponse.json(
          {
            error: 'Database schema migration 002_auth_rls_and_logs.sql is required.',
            migration_pending: true,
          },
          { status: 503 }
        );
      }

      let isDuplicate = Boolean(existingByFp);

      // Secondary check by reference_id + amount + direction for delayed/re-sent SMS
      if (!isDuplicate && referenceId && referenceId.trim().length >= 6) {
        const { data: existingByRef } = await supabase
          .from('transactions')
          .select('id')
          .eq('user_id', userId)
          .eq('reference_id', referenceId.trim())
          .eq('amount', amount)
          .eq('direction', direction)
          .maybeSingle();

        if (existingByRef) {
          isDuplicate = true;
        }
      }

      if (isDuplicate) {
        duplicateCount++;
        await logProcessing({
          sender: item.sender || resolvedBankName,
          status: 'DUPLICATE',
          reason: 'Duplicate transaction detected',
          extracted_type: direction,
          extracted_amount: amount,
          extracted_bank: resolvedBankName,
          extracted_date: txDateIso,
          reference_id: referenceId || null,
          sms_fingerprint: fingerprint,
        });
        continue;
      }

      // 2. Upsert bank account scoped to user_id (handle out-of-order SMS safely)
      let accountId: string | null = null;
      const { data: existingAccount } = await supabase
        .from('accounts')
        .select('id, current_balance, last_synced_at')
        .eq('user_id', userId)
        .eq('bank_name', resolvedBankName)
        .eq('account_number_mask', resolvedAccountMask)
        .maybeSingle();

      const txTime = new Date(txDateIso).getTime();

      if (existingAccount) {
        accountId = existingAccount.id;
        const lastSyncedTime = existingAccount.last_synced_at
          ? new Date(existingAccount.last_synced_at).getTime()
          : 0;
        const isChronologicallyNewerOrEqual = txTime >= lastSyncedTime;

        if (
          parsedBalance !== undefined &&
          parsedBalance !== null &&
          !isNaN(parsedBalance)
        ) {
          if (isChronologicallyNewerOrEqual) {
            await supabase
              .from('accounts')
              .update({
                current_balance: parsedBalance,
                last_synced_at: txDateIso,
                updated_at: new Date().toISOString(),
              })
              .eq('id', existingAccount.id)
              .eq('user_id', userId);
          }
        } else if (isChronologicallyNewerOrEqual) {
          const prevBal = Number(existingAccount.current_balance || 0);
          const adjustedBal =
            direction === 'CREDIT' ? prevBal + amount : prevBal - amount;
          await supabase
            .from('accounts')
            .update({
              current_balance: adjustedBal,
              last_synced_at: txDateIso,
              updated_at: new Date().toISOString(),
            })
            .eq('id', existingAccount.id)
            .eq('user_id', userId);
        }
      } else {
        const { data: newAccount } = await supabase
          .from('accounts')
          .insert({
            user_id: userId,
            bank_name: resolvedBankName,
            account_number_mask: resolvedAccountMask,
            account_type: inferAccountType(resolvedBankName, merchant),
            current_balance:
              parsedBalance !== undefined && parsedBalance !== null
                ? parsedBalance
                : direction === 'CREDIT'
                ? amount
                : 0,
            last_synced_at: txDateIso,
          })
          .select('id')
          .single();

        if (newAccount) {
          accountId = newAccount.id;
        }
      }

      // 3. Determine transaction type and total exclusion
      const isCcPayment =
        txTypeOverride === 'CC_BILL_PAYMENT' ||
        /credit card bill|card payment|cc payment/i.test(merchant || '');
      const isExcluded = isCcPayment || isSelfTransfer;

      let txnType: TransactionType = 'UNCLASSIFIED';
      if (isCcPayment) {
        txnType = 'CC_BILL_PAYMENT';
      } else if (isSelfTransfer) {
        txnType = 'TRANSFER';
      } else if (isRefund && direction === 'CREDIT') {
        txnType = 'REFUND';
      } else if (direction === 'CREDIT') {
        txnType = 'INCOME';
      } else {
        txnType = 'EXPENSE';
      }

      // 4. Insert transaction scoped to user_id
      const { error: insertError } = await supabase.from('transactions').insert({
        user_id: userId,
        account_id: accountId,
        category_id: categoryId,
        amount,
        direction,
        transaction_type: txnType,
        merchant: merchant || 'Bank Transaction',
        payment_mode: paymentMode || 'UPI',
        reference_id: referenceId || null,
        sms_fingerprint: fingerprint,
        parsed_available_balance: parsedBalance ?? null,
        is_excluded_from_totals: isExcluded,
        transaction_date: txDateIso,
      });

      if (insertError) {
        if (insertError.code === '23505') {
          duplicateCount++;
          await logProcessing({
            sender: item.sender || resolvedBankName,
            status: 'DUPLICATE',
            reason: 'Duplicate transaction detected (unique constraint)',
            extracted_type: txnType,
            extracted_amount: amount,
            extracted_bank: resolvedBankName,
            extracted_date: txDateIso,
            reference_id: referenceId || null,
            sms_fingerprint: fingerprint,
          });
        } else {
          failedCount++;
          errors.push({ index: i, message: insertError.message });
          await logProcessing({
            sender: item.sender || resolvedBankName,
            status: 'FAILED',
            reason: insertError.message,
            extracted_type: txnType,
            extracted_amount: amount,
            extracted_bank: resolvedBankName,
            extracted_date: txDateIso,
            reference_id: referenceId || null,
            sms_fingerprint: fingerprint,
          });
        }
      } else {
        processedCount++;
        await logProcessing({
          sender: item.sender || resolvedBankName,
          status: 'PROCESSED',
          reason: 'Transaction extracted and synced',
          extracted_type: txnType,
          extracted_amount: amount,
          extracted_bank: resolvedBankName,
          extracted_date: txDateIso,
          reference_id: referenceId || null,
          sms_fingerprint: fingerprint,
        });
      }
    }

    return NextResponse.json({
      success: true,
      processed_count: processedCount,
      duplicate_count: duplicateCount,
      ignored_count: ignoredCount,
      failed_count: failedCount,
      categories_assigned: categoriesAssigned,
      errors: errors.length > 0 ? errors : undefined,
      server_timestamp: new Date().toISOString(),
    });
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : 'Internal Server Error';
    return NextResponse.json({ error: message }, { status: 500 });
  }
}
