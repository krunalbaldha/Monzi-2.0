export interface ParsedTransaction {
  amount: number;
  direction: 'CREDIT' | 'DEBIT';
  bank_name: string;
  account_mask: string;
  merchant?: string;
  reference_id?: string;
  payment_mode?: string;
  parsed_available_balance?: number;
  transaction_type: 'EXPENSE' | 'INCOME' | 'TRANSFER' | 'CC_BILL_PAYMENT' | 'UNCLASSIFIED';
}

/**
 * Production-Grade Intelligent Parser for Indian Financial Bank SMS messages.
 * Handles HDFC, ICICI, SBI, Axis, Kotak, PNB, BoB, UPI, NetBanking, Cards, and ATM transactions.
 */
export function parseBankSms(body: string, sender: string = ''): ParsedTransaction | null {
  if (!body || typeof body !== 'string') return null;
  const text = body.replace(/[\r\n]+/g, ' ').trim();

  // 1. Immediately drop Non-Financial, OTP, and Marketing SMS
  const isOtp = /\b(otp|one time password|verification code|secret code|login pin)\b/i.test(text);
  if (isOtp) return null;

  const isPromo = /\b(pre-approved|apply now|congratulations|click here|limited period offer|discount coupon)\b/i.test(text);
  if (isPromo) return null;

  // 2. Identify Direction (Debit vs Credit)
  const isDebit = /\b(debited|spent|paid|withdrawn|sent|deducted|purchase|transfer to|trf to|done on)\b/i.test(text);
  const isCredit = /\b(credited|received|deposited|refunded|added to|refund of)\b/i.test(text);
  if (!isDebit && !isCredit) return null;

  const direction: 'CREDIT' | 'DEBIT' = isDebit ? 'DEBIT' : 'CREDIT';

  // 3. Extract Amount
  // Matches: "Rs. 1,42,580.50", "INR 450", "₹350.00", "debited by 350.0", "Transaction of INR 1,850.00"
  let amount: number | null = null;
  const amountMatch = text.match(/(?:rs\.?|inr|₹|debited by|credited with|transaction of (?:inr|rs\.?|₹)?)\s*([\d,]+(?:\.\d{1,2})?)/i);
  if (amountMatch) {
    const rawAmt = amountMatch[1].replace(/,/g, '');
    const parsed = parseFloat(rawAmt);
    if (!isNaN(parsed) && parsed > 0) amount = parsed;
  }

  if (amount === null) {
    // Secondary fallback for "350.0 debited" or "debited for INR 1,299.00"
    const fallbackMatch = text.match(/(?:for (?:inr|rs\.?|₹)?\s*|by\s*)([\d,]+(?:\.\d{1,2})?)/i);
    if (fallbackMatch) {
      const parsed = parseFloat(fallbackMatch[1].replace(/,/g, ''));
      if (!isNaN(parsed) && parsed > 0) amount = parsed;
    }
  }

  if (amount === null) return null;

  // 4. Extract Masked Account / Card Number
  let accountMask = 'XX0000';
  // Matches: "A/C **8421", "Acct XX3190", "AC X9912", "Card ending 8421", "Card XX5012", "A/C 9876"
  const accMatch = text.match(/(?:a\/c|acct|account|card|ac)\s*(?:no\.?)?\s*(?:ending\s*)?[xX*]*([0-9]{3,5})/i);
  if (accMatch) {
    accountMask = `XX${accMatch[1]}`;
  } else {
    const cardMatch = text.match(/(?:card|a\/c)\s+(?:ending\s+)?[xX*]*([0-9]{4})/i);
    if (cardMatch) {
      accountMask = `XX${cardMatch[1]}`;
    }
  }

  // 5. Extract Available Balance (Ground truth checkpoint)
  let parsedBalance: number | undefined;
  const balMatch = text.match(/(?:avl bal|avail bal|available balance|bal|balance)\s*(?:is|:)?\s*(?:rs\.?|inr|₹)?\s*([\d,]+(?:\.\d{1,2})?)/i);
  if (balMatch) {
    const balVal = parseFloat(balMatch[1].replace(/,/g, ''));
    if (!isNaN(balVal)) {
      parsedBalance = balVal;
    }
  }

  // 6. Extract Reference ID (UTR / RRN)
  let referenceId: string | undefined;
  const refMatch = text.match(/(?:upi ref|ref no|utr|rrn|ref|reference|upi)\s*(?:no\.?|:)?\s*([a-zA-Z0-9]{8,20})/i);
  if (refMatch) {
    referenceId = refMatch[1];
  }

  // 7. Payment Mode Detection
  let paymentMode = 'OTHER';
  if (/upi/i.test(text)) paymentMode = 'UPI';
  else if (/atm|wdl/i.test(text)) paymentMode = 'ATM';
  else if (/card|pos/i.test(text)) paymentMode = 'CARD';
  else if (/neft/i.test(text)) paymentMode = 'NEFT';
  else if (/imps/i.test(text)) paymentMode = 'IMPS';
  else if (/rtgs/i.test(text)) paymentMode = 'RTGS';
  else if (/netbanking/i.test(text)) paymentMode = 'NETBANKING';

  // 8. Extract Merchant / Beneficiary
  let merchant: string | undefined;
  const merchantMatch = text.match(/(?:to|at|info|vpa|towards|trf to)\s+([A-Za-z0-9\s._@\-]{3,35}?)(?:\s+(?:on|ref|avl|bal|via|using|dated|\.))/i);
  if (merchantMatch) {
    merchant = merchantMatch[1].trim();
  }

  // Clean up noisy merchant prefixes
  if (merchant) {
    merchant = merchant.replace(/^vpa\s+/i, '').replace(/^upi-/i, '').trim();
  }

  // 9. Bank Identification (Matches both SMS sender header like "VM-HDFCBK" and SMS body)
  let bankName = 'Bank';
  const combined = `${sender} ${text}`.toUpperCase();

  if (combined.includes('HDFC')) bankName = 'HDFC Bank';
  else if (combined.includes('ICICI')) bankName = 'ICICI Bank';
  else if (combined.includes('SBI') || combined.includes('STATE BANK')) bankName = 'State Bank of India';
  else if (combined.includes('AXIS')) bankName = 'Axis Bank';
  else if (combined.includes('KOTAK')) bankName = 'Kotak Bank';
  else if (combined.includes('PNB') || combined.includes('PUNJAB NATIONAL')) bankName = 'Punjab National Bank';
  else if (combined.includes('BOB') || combined.includes('BANK OF BARODA')) bankName = 'Bank of Baroda';
  else if (combined.includes('INDUSIND')) bankName = 'IndusInd Bank';

  // 10. Financial Classification
  let transactionType: 'EXPENSE' | 'INCOME' | 'TRANSFER' | 'CC_BILL_PAYMENT' | 'UNCLASSIFIED' = 'UNCLASSIFIED';

  if (/credit card bill|card payment|cc payment|paid towards.*card/i.test(text)) {
    transactionType = 'CC_BILL_PAYMENT';
  } else if (direction === 'CREDIT') {
    if (/salary/i.test(text)) transactionType = 'INCOME';
    else if (/refund/i.test(text)) transactionType = 'EXPENSE'; // Refund offsets net expenses
    else transactionType = 'INCOME';
  } else {
    transactionType = 'EXPENSE';
  }

  return {
    amount,
    direction,
    bank_name: bankName,
    account_mask: accountMask,
    merchant,
    reference_id: referenceId,
    payment_mode: paymentMode,
    parsed_available_balance: parsedBalance,
    transaction_type: transactionType,
  };
}
