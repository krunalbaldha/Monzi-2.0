import { NextResponse } from 'next/server';
import { supabase, isSupabaseConfigured } from '@/lib/supabase';

export async function GET() {
  if (!isSupabaseConfigured) {
    return NextResponse.json({
      summary: {
        total_balance: 0,
        monthly_income: 0,
        monthly_expense: 0,
        net_savings: 0,
        savings_rate: 0,
        income_change_pct: 0,
        expense_change_pct: 0,
      },
      cashFlow: [],
      categoryBreakdown: [],
      source: 'supabase_not_configured',
    });
  }

  try {
    // 1. Fetch total balance across all accounts
    const { data: accounts, error: accError } = await supabase
      .from('accounts')
      .select('current_balance');

    if (accError) throw accError;

    const totalBalance = (accounts || []).reduce(
      (sum, acc) => sum + (Number(acc.current_balance) || 0),
      0
    );

    // 2. Fetch all transactions with joined categories
    const { data: allTxns, error: txError } = await supabase
      .from('transactions')
      .select('amount, direction, transaction_type, is_excluded_from_totals, transaction_date, categories(id, name, color)')
      .order('transaction_date', { ascending: true });

    if (txError) throw txError;

    const txList = allTxns || [];

    const now = new Date();
    const currentYear = now.getFullYear();
    const currentMonth = now.getMonth(); // 0-indexed

    const currentMonthKey = `${currentYear}-${String(currentMonth + 1).padStart(2, '0')}`;
    
    // Previous month key for percentage comparison
    const prevDate = new Date(currentYear, currentMonth - 1, 1);
    const prevMonthKey = `${prevDate.getFullYear()}-${String(prevDate.getMonth() + 1).padStart(2, '0')}`;

    let monthlyIncome = 0;
    let monthlyExpense = 0;
    let prevMonthlyIncome = 0;
    let prevMonthlyExpense = 0;

    const categoryTotals: Record<string, { value: number; color: string }> = {};
    const monthlyMap: Record<string, { income: number; expense: number; sortKey: string }> = {};

    txList.forEach((tx: any) => {
      const amt = Number(tx.amount) || 0;
      const txDate = new Date(tx.transaction_date);
      const y = txDate.getFullYear();
      const m = txDate.getMonth();
      const monthKey = `${y}-${String(m + 1).padStart(2, '0')}`;
      const monthLabel = txDate.toLocaleString('default', { month: 'short' });

      if (!monthlyMap[monthKey]) {
        monthlyMap[monthKey] = { income: 0, expense: 0, sortKey: monthKey };
      }

      // Exclude transfers and CC bill payments from income/expense metrics
      if (
        tx.is_excluded_from_totals ||
        tx.transaction_type === 'CC_BILL_PAYMENT' ||
        tx.transaction_type === 'TRANSFER'
      ) {
        return;
      }

      if (tx.direction === 'CREDIT') {
        monthlyMap[monthKey].income += amt;
        if (monthKey === currentMonthKey) {
          monthlyIncome += amt;
        } else if (monthKey === prevMonthKey) {
          prevMonthlyIncome += amt;
        }
      } else {
        monthlyMap[monthKey].expense += amt;
        if (monthKey === currentMonthKey) {
          monthlyExpense += amt;
        } else if (monthKey === prevMonthKey) {
          prevMonthlyExpense += amt;
        }

        // Category breakdown for current month (or all expenses if current month has few)
        const catName = tx.categories?.name || 'Uncategorized';
        const catColor = tx.categories?.color || '#94A3B8';
        if (!categoryTotals[catName]) {
          categoryTotals[catName] = { value: 0, color: catColor };
        }
        categoryTotals[catName].value += amt;
      }
    });

    const netSavings = monthlyIncome - monthlyExpense;
    const savingsRate = monthlyIncome > 0 ? (netSavings / monthlyIncome) * 100 : 0;

    // Calculate growth percentages (0 if no baseline previous month)
    const incomeChangePct = prevMonthlyIncome > 0
      ? Math.round(((monthlyIncome - prevMonthlyIncome) / prevMonthlyIncome) * 1000) / 10
      : 0;

    const expenseChangePct = prevMonthlyExpense > 0
      ? Math.round(((monthlyExpense - prevMonthlyExpense) / prevMonthlyExpense) * 1000) / 10
      : 0;

    // Sort cash flow by date
    const sortedCashFlow = Object.keys(monthlyMap)
      .sort()
      .slice(-6)
      .map((key) => {
        const [year, month] = key.split('-');
        const date = new Date(parseInt(year), parseInt(month) - 1, 1);
        return {
          month: date.toLocaleString('default', { month: 'short' }),
          income: Math.round(monthlyMap[key].income),
          expense: Math.round(monthlyMap[key].expense),
        };
      });

    // Format category breakdown
    const categoryBreakdown = Object.keys(categoryTotals)
      .map((name) => ({
        name,
        value: Math.round(categoryTotals[name].value),
        color: categoryTotals[name].color,
      }))
      .sort((a, b) => b.value - a.value);

    return NextResponse.json({
      summary: {
        total_balance: totalBalance,
        monthly_income: monthlyIncome,
        monthly_expense: monthlyExpense,
        net_savings: netSavings,
        savings_rate: Math.round(savingsRate * 10) / 10,
        income_change_pct: incomeChangePct,
        expense_change_pct: expenseChangePct,
      },
      cashFlow: sortedCashFlow,
      categoryBreakdown,
      source: 'supabase',
    });
  } catch (err: any) {
    console.error('Stats query error:', err);
    return NextResponse.json(
      {
        summary: {
          total_balance: 0,
          monthly_income: 0,
          monthly_expense: 0,
          net_savings: 0,
          savings_rate: 0,
          income_change_pct: 0,
          expense_change_pct: 0,
        },
        cashFlow: [],
        categoryBreakdown: [],
        source: 'supabase_error',
        error: err.message,
      },
      { status: 500 }
    );
  }
}
