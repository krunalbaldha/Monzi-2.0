import { ParsedTransaction } from '@/lib/types';

/**
 * Production-Grade Intelligent Parser for Indian Financial Bank SMS messages.
 * Handles 20+ banks: HDFC, ICICI, SBI, Axis, Kotak, PNB, BoB, IndusInd,
 * Yes Bank, Federal, Canara, Union, Indian Bank, Central Bank, IDFC First,
 * AU Small Finance, RBL, StanChart, HSBC, Citibank, PhonePe, GPay, Paytm, Amazon Pay
 */

// Re-export ParsedTransaction from types so existing imports still work
export type { ParsedTransaction } from '@/lib/types';

/**
 * Maps merchant name / description to a category string.
 */
export function classifyMerchant(merchant: string, description: string = ''): string {
  const haystack = `${merchant} ${description}`.toLowerCase();

  if (/swiggy|zomato|dunzo|mcdonald|kfc|dominos|domino|starbucks|burger king|pizzahut|fassos|barbeque|cafe|restaurant|biryani|chai|food/.test(haystack))
    return 'Food & Dining';

  if (/amazon|flipkart|myntra|ajio|nykaa|meesho|snapdeal|shopclues|tatacliq|bewakoof/.test(haystack))
    return 'Shopping';

  if (/uber|ola|rapido|irctc|makemytrip|redbus|hpcl|bpcl|indianoil|hp petrol|petrol|fuel|indigo|spicejet|goair|vistara|abhibus|yatra/.test(haystack))
    return 'Travel & Transport';

  if (/bsnl|jio|airtel|vodafone|\bvi\b|idea|electricity|bescom|mseb|tata power|cesc|adani electricity|wesco|nesco|gas|water bill|postpaid|broadband|internet/.test(haystack))
    return 'Bills & Utilities';

  if (/netflix|spotify|hotstar|disney|prime video|youtube|zee5|sonyliv|apple music|gaana|wynk/.test(haystack))
    return 'Entertainment & Subscriptions';

  if (/pharmacy|medplus|apollo|practo|1mg|netmeds|hospital|clinic|health|doctor|diagnostic|lab test|lybrate/.test(haystack))
    return 'Healthcare & Medical';

  if (/zerodha|groww|upstox|mutual fund|\bmf\b|\bsip\b|\bnps\b|stock|shares|icicidirect|hdfc securities|kotak securities|motilal|angel broking|smallcase/.test(haystack))
    return 'Investments';

  if (/bigbasket|blinkit|grofers|dmart|jiomart|reliance fresh|more supermarket|nature basket|supermarket|grocery|vegetables|fruits/.test(haystack))
    return 'Groceries';

  if (/salary|payroll|ctc|wages/.test(haystack))
    return 'Salary';

  if (/\bemi\b|loan emi|equated monthly|home loan|car loan|personal loan/.test(haystack))
    return 'Bills & Utilities';

  if (/refund|reversal|returned|cashback|reward credited/.test(haystack))
    return 'Other Income';

  return 'Other';
}

export function parseBankSms(body: string, sender: string = ''): ParsedTransaction | null {
  if (!body || typeof body !== 'string') return null;
  const text = body.replace(/[\r\n]+/g, ' ').trim();

  // 1. Drop Non-Financial: OTP, marketing
  if (/\b(otp|one time password|verification code|secret code|login pin)\b/i.test(text)) return null;
  if (/\b(pre-approved|apply now|congratulations|click here|limited period offer|discount coupon)\b/i.test(text)) return null;

  // 2. Identify Direction
  const isDebit = /\b(debited|spent|paid|withdrawn|sent|deducted|purchase|transfer to|trf to|done on|payment of|txn of|transaction of)\b/i.test(text);
  const isCredit = /\b(credited|received|deposited|refunded|added to|refund of|credit of|money received)\b/i.test(text);
  if (!isDebit && !isCredit) return null;
  const direction: 'CREDIT' | 'DEBIT' = isDebit ? 'DEBIT' : 'CREDIT';

  // 3. Extract Amount
  let amount: number | null = null;
  const amountRegexes = [
    /(?:rs\.?|inr|₹)\s*([\d,]+(?:\.\d{1,2})?)/i,
    /(?:debited by|credited with|credit of|debit of|transaction of(?:\s*(?:inr|rs\.?|₹))?)\s*([\d,]+(?:\.\d{1,2})?)/i,
    /(?:for\s*(?:inr|rs\.?|₹)?\s*|by\s*)([\d,]+(?:\.\d{1,2})?)/i,
  ];
  for (const rx of amountRegexes) {
    const m = text.match(rx);
    if (m) {
      const parsed = parseFloat(m[1].replace(/,/g, ''));
      if (!isNaN(parsed) && parsed > 0) { amount = parsed; break; }
    }
  }
  if (amount === null) return null;

  // 4. Extract Account Mask
  let accountMask = 'XX0000';
  const accPatterns = [
    /(?:a\/c|acct|account|ac)\s*(?:no\.?)?\s*(?:ending\s*)?[xX*]*([0-9]{3,6})/i,
    /(?:card)\s+(?:ending\s+)?[xX*]*([0-9]{4})/i,
    /(?:xxxx|XX)([0-9]{4})/i,
  ];
  for (const p of accPatterns) {
    const m = text.match(p);
    if (m) { accountMask = `XX${m[1]}`; break; }
  }

  // 5. Available Balance
  let parsedBalance: number | undefined;
  const balMatch = text.match(/(?:avl bal|avail bal|available balance|avl\. bal|bal(?:ance)?)\s*(?:is|:)?\s*(?:rs\.?|inr|₹)?\s*([\d,]+(?:\.\d{1,2})?)/i);
  if (balMatch) {
    const v = parseFloat(balMatch[1].replace(/,/g, ''));
    if (!isNaN(v)) parsedBalance = v;
  }

  // 6. Reference ID
  let referenceId: string | undefined;
  const refMatch = text.match(/(?:upi ref(?:\s*no\.?)?|ref(?:\s*no\.?)?|utr(?:\s*no\.?)?|rrn|reference)\s*:?\s*([a-zA-Z0-9]{8,24})/i);
  if (refMatch) referenceId = refMatch[1];

  // 7. Payment Mode
  let paymentMode = 'OTHER';
  if (/\bupi\b/i.test(text)) paymentMode = 'UPI';
  else if (/\b(atm|wdl)\b/i.test(text)) paymentMode = 'ATM';
  else if (/\b(card|pos)\b/i.test(text)) paymentMode = 'CARD';
  else if (/\bneft\b/i.test(text)) paymentMode = 'NEFT';
  else if (/\bimps\b/i.test(text)) paymentMode = 'IMPS';
  else if (/\brtgs\b/i.test(text)) paymentMode = 'RTGS';
  else if (/\bnetbanking\b/i.test(text)) paymentMode = 'NETBANKING';

  // 8. Merchant Extraction — priority order
  let merchant: string | undefined;

  // Pattern 1: "to [MERCHANT]" / "at [MERCHANT]" / "towards [MERCHANT]" (skip if immediately followed by VPA)
  const toAtMatch = text.match(/\b(?:to|at|towards)\s+(?!VPA\b)([A-Za-z0-9][A-Za-z0-9\s._\-&]{2,34}?)(?=\s+(?:on|ref|avl|bal|via|using|dated|from|a\/c|utr|upi|info)\b|[.,;]|$)/i);
  if (toAtMatch) merchant = toAtMatch[1].trim();

  // Pattern 2: "VPA [name@bank]" — extract UPI VPA and clean it up
  if (!merchant) {
    const vpaMatch = text.match(/\bVPA\s+([A-Za-z0-9._\-]+@[A-Za-z0-9.\-]+)/i);
    if (vpaMatch) {
      merchant = vpaMatch[1].split('@')[0].replace(/[._\-]/g, ' ').trim();
    }
  }

  // Pattern 3: "info: [MERCHANT]" / "trf to [MERCHANT]"
  if (!merchant) {
    const infoMatch = text.match(/\b(?:info:|trf to|info\s*-)\s*([A-Za-z0-9][A-Za-z0-9\s._\-&]{2,34}?)(?=\s+(?:ref|utr|on|avl|bal)\b|[.,;]|$)/i);
    if (infoMatch) merchant = infoMatch[1].trim();
  }

  // Fallback broader match
  if (!merchant) {
    const broadMatch = text.match(/(?:to|at|info|towards)\s+([A-Za-z0-9\s._@\-]{3,35}?)(?:\s+(?:on|ref|avl|bal|via|using|dated|\.))/i);
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

  // 9. Bank Identification
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
  else if (combined.includes('YESBANK') || combined.includes('YES BANK')) bankName = 'Yes Bank';
  else if (combined.includes('FEDERAL') || combined.includes('FEDFIN')) bankName = 'Federal Bank';
  else if (combined.includes('CANARA') || combined.includes('CANARABANK')) bankName = 'Canara Bank';
  else if (combined.includes('UNION BANK') || combined.includes('UNIONBANK')) bankName = 'Union Bank of India';
  else if (combined.includes('INDIAN BANK') || combined.includes('INDIANBANK')) bankName = 'Indian Bank';
  else if (combined.includes('CENTRAL BANK') || combined.includes('CENTBANK')) bankName = 'Central Bank of India';
  else if (combined.includes('IDFC') || combined.includes('IDFCFIRST')) bankName = 'IDFC First Bank';
  else if (combined.includes('AU SMALL') || combined.includes('AUBANK') || combined.includes('AU BANK')) bankName = 'AU Small Finance Bank';
  else if (combined.includes('RBL') || combined.includes('RBLBANK')) bankName = 'RBL Bank';
  else if (combined.includes('STANDARD CHARTERED') || combined.includes('SCBANK') || combined.includes('SCBL')) bankName = 'Standard Chartered';
  else if (combined.includes('HSBC')) bankName = 'HSBC';
  else if (combined.includes('CITI') || combined.includes('CITIBANK')) bankName = 'Citibank India';
  else if (combined.includes('PHONEPE')) bankName = 'PhonePe';
  else if (combined.includes('GPAY') || combined.includes('GOOGLE PAY') || combined.includes('TEZ')) bankName = 'Google Pay';
  else if (combined.includes('PAYTM')) bankName = 'Paytm Bank';
  else if (combined.includes('AMAZON PAY') || combined.includes('AMAZONPAY')) bankName = 'Amazon Pay';

  // 10. Special transaction flags
  const isFailed = /\b(transaction failed|txn failed|reversed|declined|unsuccessful|failed transaction|could not)\b/i.test(text);
  const isSelfTransfer = /\b(own account|self transfer|internal transfer|same bank)\b/i.test(text) ||
    (/\bneft\b/i.test(text) && bankName !== 'Bank' && text.toUpperCase().includes(bankName.toUpperCase().split(' ')[0]));
  const isCashback = /\b(cashback|reward credited|cashback received|reward points credited)\b/i.test(text);
  const isRefund = /\b(refund|reversal|returned|money back)\b/i.test(text);
  const isEmi = /\b(emi|loan emi|equated monthly)\b/i.test(text);

  // 11. Financial Classification
  let transactionType: ParsedTransaction['transaction_type'] = 'UNCLASSIFIED';

  if (isFailed) {
    transactionType = 'UNCLASSIFIED';
  } else if (/credit card bill|card payment|cc payment|paid towards.*card/i.test(text)) {
    transactionType = 'CC_BILL_PAYMENT';
  } else if (isSelfTransfer) {
    transactionType = 'TRANSFER';
  } else if (direction === 'CREDIT') {
    transactionType = 'INCOME';
  } else if (isEmi) {
    transactionType = 'EXPENSE';
  } else {
    transactionType = 'EXPENSE';
  }

  // 12. Auto-classify category
  const category = classifyMerchant(merchant || '', text);

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
    transaction_category: category,
    is_failed: isFailed,
    is_self_transfer: isSelfTransfer,
    is_cashback: isCashback,
    is_refund: isRefund,
  };
}
