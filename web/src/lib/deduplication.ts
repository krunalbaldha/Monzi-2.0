import crypto from 'crypto';

/**
 * Computes a deterministic SHA-256 fingerprint for bank SMS transactions
 * to prevent duplicates during historical scans, delayed SMS, and live stream ingestion.
 *
 * Strategy:
 * - With reference_id (>= 6 chars): reference_id + bank_name + amount + direction + YYYY-MM-DD
 *   (day granularity ensures delayed or re-sent SMS with the same reference ID dedupe cleanly).
 * - Without reference_id: YYYY-MM-DDTHH + amount + direction + account_mask + merchant
 */
export function generateSmsFingerprint(params: {
  transaction_date: string;
  amount: number;
  direction: string;
  bank_name?: string;
  account_mask: string;
  reference_id?: string;
  merchant?: string;
}): string {
  const normAmount = Number(params.amount).toFixed(2);
  const normDir = params.direction.trim().toUpperCase();
  const normBank = (params.bank_name || 'bank').trim().toLowerCase().replace(/\s+/g, '');
  const normMask = (params.account_mask || 'xx0000').trim().toLowerCase();

  const parsedDate = new Date(params.transaction_date);
  const dateObj = isNaN(parsedDate.getTime()) ? new Date() : parsedDate;

  const normRef = (params.reference_id || '').trim().toLowerCase();

  let payload: string;

  if (normRef.length >= 6) {
    const dateDay = dateObj.toISOString().slice(0, 10); // YYYY-MM-DD
    payload = `${normRef}|${normBank}|${normAmount}|${normDir}|${dateDay}`;
  } else {
    const dateHour = dateObj.toISOString().slice(0, 13); // YYYY-MM-DDTHH
    const normMerchant = (params.merchant || '').trim().toLowerCase().replace(/\s+/g, '').slice(0, 12);
    payload = `${dateHour}|${normAmount}|${normDir}|${normMask}|${normMerchant}`;
  }

  return crypto.createHash('sha256').update(payload).digest('hex');
}
