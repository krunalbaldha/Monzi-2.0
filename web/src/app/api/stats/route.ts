import { NextResponse } from 'next/server';
import { getAuthenticatedUser } from '@/lib/supabase/server';
import {
  isSupabaseConfigured,
  isUndefinedColumnError,
  SCHEMA_MIGRATION_MESSAGE,
  getErrorMessage,
} from '@/lib/supabase';

const IST_TZ = 'Asia/Kolkata';

function toIstDateKey(date: Date): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: IST_TZ,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(date);
}

function toIstShortLabel(date: Date): string {
  return new Intl.DateTimeFormat('en-IN', {
    timeZone: IST_TZ,
    day: 'numeric',
    month: 'short',
  }).format(date);
}

function emptyStatsResponse(source: string, extra?: Record<string, unknown>) {
  return {
    summary: {
      total_balance: 0,
      total_income: 0,
      total_expenses: 0,
      monthly_income: 0,
      monthly_expense: 0,
      net_savings: 0,
      savings_rate: 0,
      income_change_pct: 0,
      expense_change_pct: 0,
    },
    today: {
      income: 0,
      expenses: 0,
      net: 0,
      transaction_count: 0,
      top_category: null as string | null,
      yesterday_expenses: 0,
      expense_delta_pct: 0,
    },
    bankSummary: [] as Array<{
      bank_name: string;
      account_count: number;
      balance: number;
      total_credit: number;
      total_debit: number;
      tx_count: number;
    }>,
    dailyTrend: [] as Array<{
      date: string;
      label: string;
      income: number;
      expense: number;
    }>,
    topMerchants: [] as Array<{ merchant: string; total: number; count: number }>,
    cashFlow: [] as Array<{ month: string; income: number; expense: number }>,
    categoryBreakdown: [] as Array<{ name: string; value: number; color: string }>,
    source,
    ...extra,
  };
}

export async function GET() {
  if (!isSupabaseConfigured) {
    return NextResponse.json({ error: 'Supabase not configured' }, { status: 503 });
  }

  const auth = await getAuthenticatedUser();
  if (!auth) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
  }

  try {
    // 1. Fetch user's accounts
    const { data: accounts, error: accError } = await auth.supabase
      .from('accounts')
      .select('id, bank_name, account_type, current_balance')
      .eq('user_id', auth.user.id);

    if (accError) {
      if (isUndefinedColumnError(accError)) {
        return NextResponse.json(
          emptyStatsResponse('schema_migration_required', {
            schema_migration_required: true,
            message: SCHEMA_MIGRATION_MESSAGE,
          })
        );
      }
      throw accError;
    }

    const accountList = accounts || [];
    const totalBalance = accountList.reduce(
      (sum, acc) => sum + (Number(acc.current_balance) || 0),
      0
    );

    const accountToBank = new Map<string, string>();
    const bankMap = new Map<
      string,
      {
        bank_name: string;
        account_count: number;
        balance: number;
        total_credit: number;
        total_debit: number;
        tx_count: number;
      }
    >();

    for (const acc of accountList) {
      const bankName = (acc.bank_name || 'Bank').trim();
      accountToBank.set(acc.id, bankName);
      const existing = bankMap.get(bankName) || {
        bank_name: bankName,
        account_count: 0,
        balance: 0,
        total_credit: 0,
        total_debit: 0,
        tx_count: 0,
      };
      existing.account_count += 1;
      existing.balance += Number(acc.current_balance) || 0;
      bankMap.set(bankName, existing);
    }

    // 2. Fetch user's transactions with joined categories
    const { data: allTxns, error: txError } = await auth.supabase
      .from('transactions')
      .select(
        'account_id, amount, direction, transaction_type, merchant, is_excluded_from_totals, transaction_date, categories(id, name, color)'
      )
      .eq('user_id', auth.user.id)
      .order('transaction_date', { ascending: true });

    if (txError) {
      if (isUndefinedColumnError(txError)) {
        return NextResponse.json(
          emptyStatsResponse('schema_migration_required', {
            schema_migration_required: true,
            message: SCHEMA_MIGRATION_MESSAGE,
          })
        );
      }
      throw txError;
    }

    const txList = allTxns || [];

    const now = new Date();
    const todayKey = toIstDateKey(now);
    const yesterdayDate = new Date(now.getTime() - 86400000);
    const yesterdayKey = toIstDateKey(yesterdayDate);
    const currentMonthKey = todayKey.slice(0, 7); // YYYY-MM

    const [curYearStr, curMonthStr] = currentMonthKey.split('-');
    const prevMonthDate = new Date(Date.UTC(Number(curYearStr), Number(curMonthStr) - 2, 15));
    const prevMonthKey = toIstDateKey(prevMonthDate).slice(0, 7);

    // Pre-seed last 14 days for dailyTrend
    const dailyMap = new Map<
      string,
      { date: string; label: string; income: number; expense: number }
    >();
    for (let i = 13; i >= 0; i--) {
      const d = new Date(now.getTime() - i * 86400000);
      const key = toIstDateKey(d);
      dailyMap.set(key, {
        date: key,
        label: toIstShortLabel(d),
        income: 0,
        expense: 0,
      });
    }

    let totalIncome = 0;
    let totalExpenses = 0;
    let monthlyIncome = 0;
    let monthlyExpense = 0;
    let prevMonthlyIncome = 0;
    let prevMonthlyExpense = 0;

    let todayIncome = 0;
    let todayExpenses = 0;
    let todayTxCount = 0;
    let yesterdayExpenses = 0;
    const todayCategoryMap: Record<string, number> = {};

    const categoryTotals: Record<string, { value: number; color: string }> = {};
    const monthlyMap: Record<string, { income: number; expense: number }> = {};
    const merchantMap: Record<string, { total: number; count: number }> = {};

    for (const tx of txList as Array<{
      account_id?: string | null;
      amount: number | string;
      direction: string;
      transaction_type: string;
      merchant?: string | null;
      is_excluded_from_totals?: boolean;
      transaction_date: string;
      categories?: { id: string; name: string; color: string } | Array<{ id: string; name: string; color: string }> | null;
    }>) {
      const amt = Number(tx.amount) || 0;
      const txDate = new Date(tx.transaction_date);
      if (isNaN(txDate.getTime())) continue;

      const dayKey = toIstDateKey(txDate);
      const monthKey = dayKey.slice(0, 7);

      if (!monthlyMap[monthKey]) {
        monthlyMap[monthKey] = { income: 0, expense: 0 };
      }

      // Update bank-wise activity for linked accounts
      if (tx.account_id && accountToBank.has(tx.account_id)) {
        const bName = accountToBank.get(tx.account_id)!;
        const bEntry = bankMap.get(bName);
        if (bEntry) {
          bEntry.tx_count += 1;
          if (tx.direction === 'CREDIT') {
            bEntry.total_credit += amt;
          } else {
            bEntry.total_debit += amt;
          }
        }
      }

      // Exclude self-transfers and CC bill payments from income/expense totals
      if (
        tx.is_excluded_from_totals ||
        tx.transaction_type === 'CC_BILL_PAYMENT' ||
        tx.transaction_type === 'TRANSFER'
      ) {
        continue;
      }

      const catObj = Array.isArray(tx.categories) ? tx.categories[0] : tx.categories;
      const catName = catObj?.name || 'Uncategorized';
      const catColor = catObj?.color || '#94A3B8';

      // Daily trend bucket (last 14 days)
      const trendBucket = dailyMap.get(dayKey);
      if (trendBucket) {
        if (tx.direction === 'CREDIT') {
          trendBucket.income += amt;
        } else {
          trendBucket.expense += amt;
        }
      }

      // Today & Yesterday metrics
      if (dayKey === todayKey) {
        todayTxCount += 1;
        if (tx.direction === 'CREDIT') {
          todayIncome += amt;
        } else {
          todayExpenses += amt;
          todayCategoryMap[catName] = (todayCategoryMap[catName] || 0) + amt;
        }
      } else if (dayKey === yesterdayKey && tx.direction === 'DEBIT') {
        yesterdayExpenses += amt;
      }

      if (tx.direction === 'CREDIT') {
        totalIncome += amt;
        monthlyMap[monthKey].income += amt;
        if (monthKey === currentMonthKey) {
          monthlyIncome += amt;
        } else if (monthKey === prevMonthKey) {
          prevMonthlyIncome += amt;
        }
      } else {
        totalExpenses += amt;
        monthlyMap[monthKey].expense += amt;
        if (monthKey === currentMonthKey) {
          monthlyExpense += amt;
          const merchantName = (tx.merchant || 'Unknown Merchant').trim();
          if (!merchantMap[merchantName]) {
            merchantMap[merchantName] = { total: 0, count: 0 };
          }
          merchantMap[merchantName].total += amt;
          merchantMap[merchantName].count += 1;
        } else if (monthKey === prevMonthKey) {
          prevMonthlyExpense += amt;
        }

        if (!categoryTotals[catName]) {
          categoryTotals[catName] = { value: 0, color: catColor };
        }
        categoryTotals[catName].value += amt;
      }
    }

    const netSavings = monthlyIncome - monthlyExpense;
    const savingsRate = monthlyIncome > 0 ? (netSavings / monthlyIncome) * 100 : 0;

    const incomeChangePct =
      prevMonthlyIncome > 0
        ? Math.round(((monthlyIncome - prevMonthlyIncome) / prevMonthlyIncome) * 1000) / 10
        : 0;

    const expenseChangePct =
      prevMonthlyExpense > 0
        ? Math.round(((monthlyExpense - prevMonthlyExpense) / prevMonthlyExpense) * 1000) / 10
        : 0;

    const expenseDeltaPct =
      yesterdayExpenses > 0
        ? Math.round(((todayExpenses - yesterdayExpenses) / yesterdayExpenses) * 1000) / 10
        : 0;

    let topCategoryToday: string | null = null;
    let topCategoryAmount = 0;
    for (const [name, val] of Object.entries(todayCategoryMap)) {
      if (val > topCategoryAmount) {
        topCategoryAmount = val;
        topCategoryToday = name;
      }
    }

    const sortedCashFlow = Object.keys(monthlyMap)
      .sort()
      .slice(-6)
      .map((key) => {
        const [year, month] = key.split('-');
        const date = new Date(Number(year), Number(month) - 1, 1);
        return {
          month: date.toLocaleString('default', { month: 'short' }),
          income: Math.round(monthlyMap[key].income),
          expense: Math.round(monthlyMap[key].expense),
        };
      });

    const categoryBreakdown = Object.keys(categoryTotals)
      .map((name) => ({
        name,
        value: Math.round(categoryTotals[name].value),
        color: categoryTotals[name].color,
      }))
      .sort((a, b) => b.value - a.value);

    const topMerchants = Object.entries(merchantMap)
      .map(([merchant, stats]) => ({
        merchant,
        total: Math.round(stats.total * 100) / 100,
        count: stats.count,
      }))
      .sort((a, b) => b.total - a.total)
      .slice(0, 10);

    const bankSummary = Array.from(bankMap.values())
      .map((b) => ({
        ...b,
        balance: Math.round(b.balance * 100) / 100,
        total_credit: Math.round(b.total_credit * 100) / 100,
        total_debit: Math.round(b.total_debit * 100) / 100,
      }))
      .sort((a, b) => b.balance - a.balance);

    const dailyTrend = Array.from(dailyMap.values()).map((d) => ({
      ...d,
      income: Math.round(d.income),
      expense: Math.round(d.expense),
    }));

    return NextResponse.json({
      summary: {
        total_balance: Math.round(totalBalance * 100) / 100,
        total_income: Math.round(totalIncome * 100) / 100,
        total_expenses: Math.round(totalExpenses * 100) / 100,
        monthly_income: Math.round(monthlyIncome * 100) / 100,
        monthly_expense: Math.round(monthlyExpense * 100) / 100,
        net_savings: Math.round(netSavings * 100) / 100,
        savings_rate: Math.round(savingsRate * 10) / 10,
        income_change_pct: incomeChangePct,
        expense_change_pct: expenseChangePct,
      },
      today: {
        income: Math.round(todayIncome * 100) / 100,
        expenses: Math.round(todayExpenses * 100) / 100,
        net: Math.round((todayIncome - todayExpenses) * 100) / 100,
        transaction_count: todayTxCount,
        top_category: topCategoryToday,
        yesterday_expenses: Math.round(yesterdayExpenses * 100) / 100,
        expense_delta_pct: expenseDeltaPct,
      },
      bankSummary,
      dailyTrend,
      topMerchants,
      cashFlow: sortedCashFlow,
      categoryBreakdown,
      source: 'supabase',
    });
  } catch (err: unknown) {
    console.error('Stats query error:', err);
    return NextResponse.json(
      emptyStatsResponse('supabase_error', { error: getErrorMessage(err) }),
      { status: 500 }
    );
  }
}
