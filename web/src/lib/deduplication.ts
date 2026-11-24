import crypto from 'crypto';

/**
 * Computes a deterministic SHA-256 fingerprint for bank SMS transactions
 * to prevent duplicates during historical scans and live stream ingestion.
 */
export function generateSmsFingerprint(params: {
  transaction_date: string;
  amount: number;
  direction: string;
  account_mask: string;
  reference_id?: string;
}): string {
  const normDate = new Date(params.transaction_date).toISOString().slice(0, 16); // Minute precision
  const normAmount = Number(params.amount).toFixed(2);
  const normRef = (params.reference_id || '').trim().toLowerCase();
  const normMask = params.account_mask.trim().toLowerCase();
  const normDir = params.direction.trim().toUpperCase();

  const payload = `${normDate}|${normAmount}|${normDir}|${normMask}|${normRef}`;
  return crypto.createHash('sha256').update(payload).digest('hex');
}
