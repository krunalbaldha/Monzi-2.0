import { ParsedTransaction } from '@/lib/types';

/**
 * Production-Grade Intelligent Parser for Indian Financial Bank SMS messages.
 * Handles 30+ Indian banks, UPI services, and card providers:
 * HDFC, ICICI, SBI, Axis, Kotak, PNB, BoB, IndusInd, Yes Bank, Federal,
 * Canara, Union, Indian Bank, Central Bank, IDFC First, AU Small Finance,
 * RBL, StanChart, HSBC, Citibank, BOI, IOB, UCO, Bandhan, Karnataka,
 * South Indian Bank, DBS, Amex, OneCard, PhonePe, GPay, Paytm, Amazon Pay.
 */

export type { ParsedTransaction } from '@/lib/types';

export interface SmsInspectionResult {
  parsed: ParsedTransaction | null;
  status: 'VALID' | 'IGNORED' | 'FAILED';
  reason?: string;
}

const MONTH_MAP: Record<string, number> = {
  jan: 1,
  january: 1,
  feb: 2,
  february: 2,
  mar: 3,
  march: 3,
  apr: 4,
  april: 4,
  may: 5,
  jun: 6,
  june: 6,
  jul: 7,
  july: 7,
  aug: 8,
  august: 8,
  sep: 9,
  sept: 9,
  september: 9,
  oct: 10,
  october: 10,
  nov: 11,
  november: 11,
  dec: 12,
  december: 12,
};

function buildIsoFromParts(
  year: number,
  month: number,
  day: number,
  hour = 12,
  minute = 0,
  second = 0
): string | null {
  if (month < 1 || month > 12 || day < 1 || day > 31) return null;
  if (hour < 0 || hour > 23 || minute < 0 || minute > 59 || second < 0 || second > 59) {
    hour = 12;
    minute = 0;
    second = 0;
  }

  const fullYear = year < 100 ? 2000 + year : year;
  if (fullYear < 2000 || fullYear > 2100) return null;

  const pad = (n: number) => String(n).padStart(2, '0');
  const isoCandidate = `${fullYear}-${pad(month)}-${pad(day)}T${pad(hour)}:${pad(minute)}:${pad(second)}+05:30`;
  const d = new Date(isoCandidate);
  if (isNaN(d.getTime())) return null;

  // Verify calendar rollover did not happen (e.g. Feb 31)
  const istCheck = new Date(d.getTime() + 5.5 * 60 * 60 * 1000);
  if (istCheck.getUTCMonth() + 1 !== month || istCheck.getUTCDate() !== day) {
    return null;
  }
  return d.toISOString();
}

/**
 * Extracts explicit transaction date (and optional time) from an Indian bank SMS body.
 * Supports formats like 25-Nov-26, 25 Nov 2026, 25/11/2026, 25-11-26, 2026-11-25.
 */
export function extractTransactionDate(text: string, fallbackDate?: string | Date): string {
  const clean = text.replace(/[\r\n]+/g, ' ').trim();

  let hour = 12;
  let minute = 0;
  let second = 0;
  let hasExplicitTime = false;

  const timeMatch = clean.match(/\b(?:at\s+)?([01]?\d|2[0-3]):([0-5]\d)(?::([0-5]\d))?(?:\s*(AM|PM))?\b/i);
  if (timeMatch) {
    let h = parseInt(timeMatch[1], 10);
    const m = parseInt(timeMatch[2], 10);
    const s = timeMatch[3] ? parseInt(timeMatch[3], 10) : 0;
    const ampm = timeMatch[4]?.toUpperCase();
    if (ampm === 'PM' && h < 12) h += 12;
    if (ampm === 'AM' && h === 12) h = 0;
    hour = h;
    minute = m;
    second = s;
    hasExplicitTime = true;
  }

  // If no explicit time in SMS, reuse time from fallbackTimestamp if available
  const fallbackObj = fallbackDate ? new Date(fallbackDate) : null;
  if (!hasExplicitTime && fallbackObj && !isNaN(fallbackObj.getTime())) {
    const istFallback = new Date(fallbackObj.getTime() + 5.5 * 60 * 60 * 1000);
    hour = istFallback.getUTCHours();
    minute = istFallback.getUTCMinutes();
    second = istFallback.getUTCSeconds();
  }

  // 1. DD-MMM-YY or DD MMM YYYY (e.g. 25-Nov-26, 25 Nov 2026, 05-Jan-2025)
  const dmyAlpha = clean.match(
    /\b(\d{1,2})[\s\-\/](Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:t|tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)[,\s\-\/]+(\d{2,4})\b/i
  );
  if (dmyAlpha) {
    const day = parseInt(dmyAlpha[1], 10);
    const month = MONTH_MAP[dmyAlpha[2].toLowerCase()];
    const year = parseInt(dmyAlpha[3], 10);
    if (month) {
      const iso = buildIsoFromParts(year, month, day, hour, minute, second);
      if (iso) return iso;
    }
  }

  // 2. YYYY-MM-DD or YYYY/MM/DD
  const ymdNumeric = clean.match(/\b(20\d{2})[\-\/](\d{1,2})[\-\/](\d{1,2})\b/);
  if (ymdNumeric) {
    const year = parseInt(ymdNumeric[1], 10);
    const month = parseInt(ymdNumeric[2], 10);
    const day = parseInt(ymdNumeric[3], 10);
    const iso = buildIsoFromParts(year, month, day, hour, minute, second);
    if (iso) return iso;
  }

  // 3. DD/MM/YYYY or DD-MM-YYYY or DD-MM-YY or DD.MM.YYYY (Indian convention: DD first)
  const dmyNumeric = clean.match(/\b(\d{1,2})[\/\-\.](\d{1,2})[\/\-\.](\d{2,4})\b/);
  if (dmyNumeric) {
    const day = parseInt(dmyNumeric[1], 10);
    const month = parseInt(dmyNumeric[2], 10);
    const year = parseInt(dmyNumeric[3], 10);
    const iso = buildIsoFromParts(year, month, day, hour, minute, second);
    if (iso) return iso;
  }

  if (fallbackObj && !isNaN(fallbackObj.getTime())) {
    return fallbackObj.toISOString();
  }
  return new Date().toISOString();
}

/**
 * Maps merchant name / description to a category string.
 */
export function classifyMerchant(merchant: string, description: string = ''): string {
  const haystack = `${merchant} ${description}`.toLowerCase();

  if (
    /swiggy|zomato|dunzo|mcdonald|kfc|dominos|domino|starbucks|burger king|pizzahut|fassos|barbeque|cafe|restaurant|biryani|chai|food|eatclub|haldiram/.test(
      haystack
    )
  ) {
    return 'Food & Dining';
  }

  if (/amazon|flipkart|myntra|ajio|nykaa|meesho|snapdeal|shopclues|tatacliq|bewakoof|croma|reliance digital/.test(haystack)) {
    return 'Shopping';
  }

  if (
    /uber|ola|rapido|irctc|makemytrip|redbus|hpcl|bpcl|indianoil|hp petrol|petrol|fuel|diesel|fastag|indigo|spicejet|goair|vistara|air india|abhibus|yatra|metro/.test(
      haystack
    )
  ) {
    return 'Travel & Transport';
  }

  if (
    /bsnl|jio|airtel|vodafone|\bvi\b|idea|electricity|bescom|mseb|tata power|cesc|adani electricity|wesco|nesco|gas|water bill|postpaid|broadband|internet|recharge|dth/.test(
      haystack
    )
  ) {
    return 'Bills & Utilities';
  }

  if (/netflix|spotify|hotstar|disney|prime video|youtube|zee5|sonyliv|apple music|gaana|wynk|bookmyshow/.test(haystack)) {
    return 'Entertainment & Subscriptions';
  }

  if (/pharmacy|medplus|apollo|practo|1mg|netmeds|hospital|clinic|health|doctor|diagnostic|lab test|lybrate|pharmeasy/.test(haystack)) {
    return 'Healthcare & Medical';
  }

  if (
    /zerodha|groww|upstox|mutual fund|\bmf\b|\bsip\b|\bnps\b|stock|shares|icicidirect|hdfc securities|kotak securities|motilal|angel broking|smallcase|kuvera/.test(
      haystack
    )
  ) {
    return 'Investments';
  }

  if (
    /bigbasket|blinkit|zepto|instamart|grofers|dmart|jiomart|reliance fresh|more supermarket|nature basket|supermarket|grocery|vegetables|fruits/.test(
      haystack
    )
  ) {
    return 'Groceries';
  }

  if (/salary|payroll|ctc|wages|stipend/.test(haystack)) {
    return 'Salary';
  }

  if (/\bemi\b|loan emi|equated monthly|home loan|car loan|personal loan/.test(haystack)) {
    return 'Bills & Utilities';
  }

  if (/refund|reversal|returned|cashback|reward credited/.test(haystack)) {
    return 'Other Income';
  }

  return 'Other';
}

export function identifyIndianBank(text: string, sender: string = ''): string {
  const combined = `${sender} ${text}`.toUpperCase();

  if (combined.includes('HDFC')) return 'HDFC Bank';
  if (combined.includes('ICICI')) return 'ICICI Bank';
  if (combined.includes('SBI') || combined.includes('STATE BANK')) return 'State Bank of India';
  if (combined.includes('AXIS')) return 'Axis Bank';
  if (combined.includes('KOTAK')) return 'Kotak Bank';
  if (combined.includes('PNB') || combined.includes('PUNJAB NATIONAL')) return 'Punjab National Bank';
  if (combined.includes('BOB') || combined.includes('BANK OF BARODA')) return 'Bank of Baroda';
  if (combined.includes('INDUSIND') || combined.includes('INDUSB')) return 'IndusInd Bank';
  if (combined.includes('YESBANK') || combined.includes('YES BANK') || combined.includes('YESBNK')) return 'Yes Bank';
  if (combined.includes('FEDERAL') || combined.includes('FEDFIN') || combined.includes('FEDBNK')) return 'Federal Bank';
  if (combined.includes('CANARA') || combined.includes('CANBNK')) return 'Canara Bank';
  if (combined.includes('UNION BANK') || combined.includes('UNIONBANK') || combined.includes('UNIONB')) return 'Union Bank of India';
  if (combined.includes('INDIAN BANK') || combined.includes('INDIANBANK')) return 'Indian Bank';
  if (combined.includes('CENTRAL BANK') || combined.includes('CENTBANK')) return 'Central Bank of India';
  if (combined.includes('IDFC') || combined.includes('IDFCFB')) return 'IDFC First Bank';
  if (combined.includes('AU SMALL') || combined.includes('AUBANK') || combined.includes('AU BANK')) return 'AU Small Finance Bank';
  if (combined.includes('RBL') || combined.includes('RBLBNK')) return 'RBL Bank';
  if (combined.includes('STANDARD CHARTERED') || combined.includes('SCBANK') || combined.includes('SCBL')) return 'Standard Chartered';
  if (combined.includes('HSBC')) return 'HSBC';
  if (combined.includes('CITI')) return 'Citibank India';
  if (combined.includes('BANK OF INDIA') || combined.includes('BOIIND')) return 'Bank of India';
  if (combined.includes('INDIAN OVERSEAS') || combined.includes('IOB')) return 'Indian Overseas Bank';
  if (combined.includes('UCO BANK') || combined.includes('UCOBNK')) return 'UCO Bank';
  if (combined.includes('BANDHAN')) return 'Bandhan Bank';
  if (combined.includes('KARNATAKA BANK') || combined.includes('KTKBNK')) return 'Karnataka Bank';
  if (combined.includes('SOUTH INDIAN') || combined.includes('SIB')) return 'South Indian Bank';
  if (combined.includes('DBS') || combined.includes('DIGIBANK')) return 'DBS Bank';
  if (combined.includes('AMEX') || combined.includes('AMERICAN EXPRESS')) return 'American Express';
  if (combined.includes('ONECARD')) return 'OneCard';
  if (combined.includes('SLICE')) return 'Slice';
  if (combined.includes('JUPITER')) return 'Jupiter';
  if (combined.includes('PHONEPE') || combined.includes('PHONPE')) return 'PhonePe';
  if (combined.includes('GPAY') || combined.includes('GOOGLE PAY') || combined.includes('TEZ')) return 'Google Pay';
  if (combined.includes('PAYTM')) return 'Paytm Bank';
  if (combined.includes('AMAZON PAY') || combined.includes('AMAZONPAY')) return 'Amazon Pay';

  return 'Bank';
}

/**
 * Inspects an SMS message with strict financial filtering and returns either
 * a valid ParsedTransaction or a structured IGNORED/FAILED reason (never storing raw SMS).
 */
export function inspectBankSms(
  body: string,
  sender: string = '',
  fallbackTimestamp?: string | Date
): SmsInspectionResult {
  if (!body || typeof body !== 'string' || !body.trim()) {
    return { parsed: null, status: 'IGNORED', reason: 'Empty SMS message' };
  }

  const text = body.replace(/[\r\n]+/g, ' ').replace(/\s+/g, ' ').trim();

  // 1. Strictly ignore OTP / verification codes
  if (
    /\b(otp|one[\s-]?time[\s-]?password|verification code|secret code|login pin|mpin|tpin|passcode|security code|2fa|auth code)\b/i.test(
      text
    ) ||
    /\bis your\s+\d{4,8}\b/i.test(text)
  ) {
    return { parsed: null, status: 'IGNORED', reason: 'OTP or verification code filtered out' };
  }

  // 2. Strictly ignore Promotional / Marketing / Pre-approved offers
  if (
    /\b(pre-?approved|apply now|congratulations|click here|limited period offer|discount coupon|avail loan|instant loan|zero processing|credit limit increase|exciting offers|exclusive offer|shop now|win up to|flat \d+% off|refer & earn|claim now|cashback offer|subscribe now|unsubscribe|bit\.ly|tinyurl)\b/i.test(
      text
    )
  ) {
    return { parsed: null, status: 'IGNORED', reason: 'Promotional or marketing message ignored' };
  }

  // 3. Strictly ignore Login / Security alerts
  if (
    /\b(logged in|login alert|successful login|new device login|session started|password changed|mpin changed|pin changed|pin reset|pin generated|biometric enabled)\b/i.test(
      text
    )
  ) {
    return { parsed: null, status: 'IGNORED', reason: 'Login or security alert ignored' };
  }

  // 4. Strictly ignore Failed / Declined / Unsuccessful transactions
  if (
    /\b(transaction failed|txn failed|payment failed|has failed|could not be processed|was declined|card declined|declined due to|unsuccessful|failed transaction|insufficient balance|insufficient funds|timed out|exceeded limit|invalid pin)\b/i.test(
      text
    )
  ) {
    return { parsed: null, status: 'FAILED', reason: 'Failed or declined transaction ignored' };
  }

  // 5. Strictly ignore General Bank Notices (KYC, statement ready, cheque, mandate setup/pause, EMI reminders without debit)
  if (
    /\b(update your kyc|re-?kyc|kyc pending|kyc expired|statement is ready|e-?statement|monthly statement|cheque book|cheque issued|cheque bounced|mandate created|mandate paused|mandate revoked|mandate cancelled|autopay registered|e-?mandate.*registered|emi reminder|payment reminder|bill is due|due date|minimum amount due|total amount due|will be debited|to be debited|scheduled to be debited|upcoming emi)\b/i.test(
      text
    )
  ) {
    return { parsed: null, status: 'IGNORED', reason: 'General bank notice or reminder ignored' };
  }

  // 6. Identify Credit/Debit Action (must be a real financial movement)
  const isRefundKeyword = /\b(refund|refunded|reversal|reversed back|money back|cashback)\b/i.test(text);
  const isDebit = /\b(debited|spent|paid|withdrawn|sent|deducted|purchase|transfer to|trf to|done on|payment of|txn of|transaction of|charged|auto-?debited)\b/i.test(
    text
  );
  const isCredit =
    /\b(credited|received|deposited|refunded|added to|refund of|credit of|money received|reversed to|credited back)\b/i.test(
      text
    ) ||
    (isRefundKeyword && /\b(to your|a\/c|account|card|wallet)\b/i.test(text) && !isDebit);

  if (!isDebit && !isCredit) {
    if (/\b(avl bal|avail bal|available balance|updated balance|account balance|clear balance|closing balance)\b/i.test(text)) {
      return {
        parsed: null,
        status: 'IGNORED',
        reason: 'Balance-only notification without debit or credit action',
      };
    }
    return {
      parsed: null,
      status: 'IGNORED',
      reason: 'No valid CREDIT or DEBIT transaction action detected',
    };
  }

  const explicitSelfCredit =
    /\b(credited\s+(?:to|in|into)\s+(?:your|ur|a\/c|acct|account|wallet|card)|credited\s+(?:with|by)|salary\s+credited|money\s+received|added\s+to\s+(?:your|ur|a\/c|account|wallet)|credited\s+back|reversed\s+to\s+(?:your|ur|a\/c|acct|account|card)|credit\s+of|deposited)\b/i.test(
      text
    );
  const hasStrongDebitPhrase =
    isDebit && /\b(debited|withdrawn|spent|purchase|auto-?debited)\b/i.test(text);
  const hasOwnAccountCredit =
    /\b(?:credited|reversed)\s+(?:to|in|into)\s+(?:your|ur|a\/c|acct|account|card)\b/i.test(
      text
    );

  let direction: 'CREDIT' | 'DEBIT' = 'CREDIT';
  if (isCredit && !isDebit) {
    direction = 'CREDIT';
  } else if (isDebit && !isCredit) {
    direction = 'DEBIT';
  } else if (isRefundKeyword) {
    direction = 'CREDIT';
  } else if (explicitSelfCredit) {
    direction = hasStrongDebitPhrase && !hasOwnAccountCredit ? 'DEBIT' : 'CREDIT';
  } else if (isDebit) {
    direction = 'DEBIT';
  }

  // 7. Extract Amount (must have monetary value)
  let amount: number | null = null;
  const amountRegexes = [
    /(?:rs\.?|inr|₹)\s*([\d,]+(?:\.\d{1,2})?)/i,
    /(?:debited by|credited with|credit of|debit of|refund of|transaction of(?:\s*(?:inr|rs\.?|₹))?)\s*([\d,]+(?:\.\d{1,2})?)/i,
    /([\d,]+(?:\.\d{1,2})?)\s*(?:debited|credited|spent|paid|withdrawn)/i,
  ];
  for (const rx of amountRegexes) {
    const m = text.match(rx);
    if (m) {
      const parsed = parseFloat(m[1].replace(/,/g, ''));
      if (!isNaN(parsed) && parsed > 0) {
        amount = parsed;
        break;
      }
    }
  }
  if (amount === null) {
    return { parsed: null, status: 'IGNORED', reason: 'No valid monetary amount found in SMS' };
  }

  // 8. Extract Account / Card Mask
  let accountMask = 'XX0000';
  const accPatterns = [
    /(?:a\/c|acct|account|ac)\s*(?:no\.?)?\s*(?:ending\s*(?:with\s*)?)?[xX*]*([0-9]{3,6})/i,
    /(?:card)\s+(?:ending\s*(?:with\s*)?)?[xX*]*([0-9]{4})/i,
    /(?:xxxx|XX|\*{2,4})([0-9]{4})/i,
  ];
  for (const p of accPatterns) {
    const m = text.match(p);
    if (m) {
      accountMask = `XX${m[1]}`;
      break;
    }
  }

  // 9. Available Balance
  let parsedBalance: number | undefined;
  const balMatch = text.match(
    /(?:avl bal|avail bal|available balance|avl\. bal|clear bal|bal(?:ance)?)\s*(?:is|:)?\s*(?:rs\.?|inr|₹)?\s*([\d,]+(?:\.\d{1,2})?)/i
  );
  if (balMatch) {
    const v = parseFloat(balMatch[1].replace(/,/g, ''));
    if (!isNaN(v)) parsedBalance = v;
  }

  // 10. Reference ID
  let referenceId: string | undefined;
  const refMatch = text.match(
    /(?:upi\s*ref(?:\s*no\.?)?|ref(?:\s*no\.?)?|utr(?:\s*no\.?)?|rrn|reference(?:\s*no\.?)?)\s*:?\s*([a-zA-Z0-9]{6,24})/i
  );
  if (refMatch) referenceId = refMatch[1];

  // 11. Payment Mode
  let paymentMode = 'OTHER';
  if (/\bupi\b/i.test(text)) paymentMode = 'UPI';
  else if (/\b(atm|wdl|cash withdrawal)\b/i.test(text)) paymentMode = 'ATM';
  else if (/\b(card|pos|debit card|credit card)\b/i.test(text)) paymentMode = 'CARD';
  else if (/\bneft\b/i.test(text)) paymentMode = 'NEFT';
  else if (/\bimps\b/i.test(text)) paymentMode = 'IMPS';
  else if (/\brtgs\b/i.test(text)) paymentMode = 'RTGS';
  else if (/\bnetbanking\b/i.test(text)) paymentMode = 'NETBANKING';

  // 12. Merchant Extraction
  let merchant: string | undefined;
  const toAtMatch = text.match(
    /\b(?:to|at|towards|from)\s+(?!VPA\b|A\/c\b|Account\b|your\b)([A-Za-z0-9][A-Za-z0-9\s._\-&]{2,34}?)(?=\s+(?:on|ref|avl|bal|via|using|dated|from|a\/c|utr|upi|info|for|has|was|is)\b|[.,;]|$)/i
  );
  if (toAtMatch) merchant = toAtMatch[1].trim();

  if (!merchant) {
    const vpaMatch = text.match(/\bVPA\s+([A-Za-z0-9._\-]+@[A-Za-z0-9.\-]+)/i);
    if (vpaMatch) {
      merchant = vpaMatch[1].split('@')[0].replace(/[._\-]/g, ' ').trim();
    }
  }

  if (!merchant) {
    const infoMatch = text.match(
      /\b(?:info:|trf to|info\s*-)\s*([A-Za-z0-9][A-Za-z0-9\s._\-&]{2,34}?)(?=\s+(?:ref|utr|on|avl|bal)\b|[.,;]|$)/i
    );
    if (infoMatch) merchant = infoMatch[1].trim();
  }

  if (!merchant) {
    const broadMatch = text.match(
      /(?:to|at|info|towards)\s+([A-Za-z0-9\s._@\-]{3,35}?)(?:\s+(?:on|ref|avl|bal|via|using|dated|\.))/i
    );
    if (broadMatch) merchant = broadMatch[1].trim();
  }

  if (merchant) {
    merchant = merchant
      .replace(/^vpa\s+/i, '')
      .replace(/^upi[-\s]/i, '')
      .replace(/^(the\s+)/i, '')
      .trim();
    if (merchant.length > 40) merchant = merchant.slice(0, 40).trim();
  }

  // 13. Bank Identification
  const bankName = identifyIndianBank(text, sender);

  // 14. Extract True Transaction Date from SMS Body
  const transactionDate = extractTransactionDate(text, fallbackTimestamp);

  // 15. Special transaction flags (avoid misclassifying NEFT salary/income as self-transfer)
  const isSelfTransfer =
    /\b(own account|self transfer|internal transfer|same bank transfer|between your accounts|transfer to your own|linked account transfer|trf to self|to own a\/c)\b/i.test(
      text
    );
  const isCashback = /\b(cashback|reward credited|cashback received|reward points credited)\b/i.test(text);
  const isRefund = direction === 'CREDIT' && isRefundKeyword;

  // 16. Financial Classification
  let transactionType: ParsedTransaction['transaction_type'] = 'UNCLASSIFIED';
  if (/credit card bill|card payment|cc payment|paid towards.*card/i.test(text)) {
    transactionType = 'CC_BILL_PAYMENT';
  } else if (isSelfTransfer) {
    transactionType = 'TRANSFER';
  } else if (isRefund) {
    transactionType = 'REFUND';
  } else if (direction === 'CREDIT') {
    transactionType = 'INCOME';
  } else {
    transactionType = 'EXPENSE';
  }

  const category = classifyMerchant(merchant || '', text);

  return {
    status: 'VALID',
    parsed: {
      amount,
      direction,
      bank_name: bankName,
      account_mask: accountMask,
      merchant,
      reference_id: referenceId,
      payment_mode: paymentMode,
      parsed_available_balance: parsedBalance,
      transaction_date: transactionDate,
      transaction_type: transactionType,
      transaction_category: category,
      is_failed: false,
      is_self_transfer: isSelfTransfer,
      is_cashback: isCashback,
      is_refund: isRefund,
    },
  };
}

export function parseBankSms(
  body: string,
  sender: string = '',
  fallbackTimestamp?: string | Date
): ParsedTransaction | null {
  return inspectBankSms(body, sender, fallbackTimestamp).parsed;
}
