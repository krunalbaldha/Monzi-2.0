import { parseBankSms } from '../src/lib/parser.ts';

const testCases = [
  {
    name: 'HDFC UPI Debit',
    sender: 'VM-HDFCBK',
    sms: 'Sent Rs. 450.00 from HDFC Bank A/C **8421 to SWIGGY on 24-NOV-26. UPI:4390123891. Avl Bal: INR 1,42,130.50.',
    expected: { amount: 450, direction: 'DEBIT', bank: 'HDFC Bank', mask: 'XX8421', balance: 142130.5 }
  },
  {
    name: 'ICICI UPI Debit',
    sender: 'VM-ICICIB',
    sms: 'Acct XX3190 debited for INR 1,299.00 on 24-Nov-26. UPI:Zomato. UPI Ref: 4329108921. Avl Bal: INR 48,320.00.',
    expected: { amount: 1299, direction: 'DEBIT', bank: 'ICICI Bank', mask: 'XX3190', balance: 48320 }
  },
  {
    name: 'ICICI Amazon Pay Card Debit',
    sender: 'VM-ICICIB',
    sms: 'Transaction of INR 1,850.00 done on ICICI Bank Credit Card XX5012 on 24-Nov-26 at HPCL PETROL PUMP.',
    expected: { amount: 1850, direction: 'DEBIT', bank: 'ICICI Bank', mask: 'XX5012' }
  },
  {
    name: 'SBI UPI Debit',
    sender: 'AD-SBIINB',
    sms: 'Dear UPI user A/C 9876 debited by 350.0 on date 24Nov26 trf to CHAI POINT Ref 43289012. Bal 12450.0.',
    expected: { amount: 350, direction: 'DEBIT', bank: 'State Bank of India', mask: 'XX9876', balance: 12450 }
  },
  {
    name: 'SBI ATM Cash Withdrawal',
    sender: 'AD-SBIINB',
    sms: 'Your A/C ending 9876 has been debited by Rs. 5000.00 on 24-Nov-26 at ATM WDL. Avl Bal Rs 7450.00.',
    expected: { amount: 5000, direction: 'DEBIT', bank: 'State Bank of India', mask: 'XX9876', balance: 7450 }
  },
  {
    name: 'HDFC Salary Credit',
    sender: 'VM-HDFCBK',
    sms: 'Your A/C XX8421 is credited with INR 1,25,000.00 on 24-Nov-26 by NEFT-ACME CORP-SALARY FOR NOV. Avl Bal: INR 1,42,580.50.',
    expected: { amount: 125000, direction: 'CREDIT', bank: 'HDFC Bank', mask: 'XX8421', balance: 142580.5 }
  },
  {
    name: 'Axis Bank UPI Debit',
    sender: 'BZ-AXISBK',
    sms: 'INR 650.00 debited from A/c no. XX4921 on 24-11-26 towards UBER via UPI. Ref no 49102910.',
    expected: { amount: 650, direction: 'DEBIT', bank: 'Axis Bank', mask: 'XX4921' }
  },
  {
    name: 'Kotak 811 UPI Debit',
    sender: 'VM-KOTAKB',
    sms: 'Sent Rs. 280.00 from Kotak Bank AC X9912 to ZE PTO via UPI ref 49201923. Bal: Rs. 14,300.00.',
    expected: { amount: 280, direction: 'DEBIT', bank: 'Kotak Bank', mask: 'XX9912', balance: 14300 }
  },
  {
    name: 'HDFC Refund Credit',
    sender: 'VM-HDFCBK',
    sms: 'Refund of Rs. 480.00 initiated by Swiggy has been credited to your HDFC Bank A/c ending 8421 on 24-Nov-26.',
    expected: { amount: 480, direction: 'CREDIT', bank: 'HDFC Bank', mask: 'XX8421' }
  },
  {
    name: 'Credit Card Bill Payment',
    sender: 'VM-ICICIB',
    sms: 'Rs. 12,450.00 paid towards ICICI Bank Credit Card ending 5012 from A/C XX8421 on 24-Nov-26.',
    expected: { amount: 12450, direction: 'DEBIT', mask: 'XX5012' }
  },
  {
    name: 'OTP Message (Must be IGNORED)',
    sender: 'VM-HDFCBK',
    sms: 'Your OTP for HDFC NetBanking login is 894120. Valid for 10 mins. Do NOT share with anyone.',
    expected: null
  },
  {
    name: 'Promotional Offer (Must be IGNORED)',
    sender: 'AD-HDFCBK',
    sms: 'Congratulations! You are eligible for a pre-approved personal loan of Rs 5,00,000. Apply now!',
    expected: null
  }
];

let passed = 0;
let failed = 0;

console.log('--- RUNNING BANK SMS PARSER TEST SUITE ---');

for (const tc of testCases) {
  const result = parseBankSms(tc.sms, tc.sender);
  if (tc.expected === null) {
    if (result === null) {
      console.log(`[PASS] ${tc.name} correctly ignored.`);
      passed++;
    } else {
      console.error(`[FAIL] ${tc.name} was not ignored:`, result);
      failed++;
    }
  } else {
    if (!result) {
      console.error(`[FAIL] ${tc.name}: Failed to parse.`);
      failed++;
      continue;
    }
    const amountMatch = result.amount === tc.expected.amount;
    const dirMatch = result.direction === tc.expected.direction;
    const maskMatch = tc.expected.mask ? result.account_mask === tc.expected.mask : true;
    const bankMatch = tc.expected.bank ? result.bank_name === tc.expected.bank : true;

    if (amountMatch && dirMatch && maskMatch && bankMatch) {
      console.log(`[PASS] ${tc.name} -> ₹${result.amount} ${result.direction} (${result.bank_name} ${result.account_mask})`);
      passed++;
    } else {
      console.error(`[FAIL] ${tc.name} parsed incorrectly:`, result);
      failed++;
    }
  }
}

console.log(`\nResults: ${passed} Passed, ${failed} Failed.`);
if (failed > 0) process.exit(1);
