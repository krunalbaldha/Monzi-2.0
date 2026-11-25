import crypto from 'crypto';

/**
 * Computes a deterministic SHA-256 fingerprint for bank SMS transactions
 * to prevent duplicates during historical scans and live stream ingestion.
 *
 * Strategy:
 * - With reference_id: amount + direction + date(YYYY-MM-DD-HH) + account_mask + first6(ref)
 * - Without reference_id: amount + direction + date(YYYY-MM-DD) + account_mask + merchant_first8
 */
export function generateSmsFingerprint(params: {
  transaction_date: string;
  amount: number;
  direction: string;
  account_mask: string;
  reference_id?: string;
  merchant?: string;
}): string {
  const normAmount = Number(params.amount).toFixed(2);
  const normDir = params.direction.trim().toUpperCase();
  const normMask = params.account_mask.trim().toLowerCase();
  const dateObj = new Date(params.transaction_date);

  const normRef = (params.reference_id || '').trim().toLowerCase();

  let payload: string;

  if (normRef.length >= 6) {
    // High-precision: include hour and first 6 chars of reference
    const dateHour = dateObj.toISOString().slice(0, 13); // YYYY-MM-DDTHH
    const refFirst6 = normRef.slice(0, 6);
    payload = `${normAmount}|${normDir}|${dateHour}|${normMask}|${refFirst6}`;
  } else {
    // Fallback: day-level precision + merchant prefix
    const dateDay = dateObj.toISOString().slice(0, 10); // YYYY-MM-DD
    const merchantFirst8 = (params.merchant || '').trim().toLowerCase().replace(/\s+/g, '').slice(0, 8);
    payload = `${normAmount}|${normDir}|${dateDay}|${normMask}|${merchantFirst8}`;
  }

  return crypto.createHash('sha256').update(payload).digest('hex');
}
