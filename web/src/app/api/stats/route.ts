import { NextResponse } from 'next/server';
import { supabase, isSupabaseConfigured } from '@/lib/supabase';
import { mockSummary, mockMonthlyCashFlow, mockCategoryBreakdown } from '@/lib/mockData';

export async function GET() {
  if (!isSupabaseConfigured) {
    return NextResponse.json({
      summary: mockSummary,
      cashFlow: mockMonthlyCashFlow,
      categoryBreakdown: mockCategoryBreakdown,
      source: 'mock',
    });
  }

  try {
    // 1. Fetch total balance across accounts
    const { data: accounts } = await supabase.from('accounts').select('current_balance');
    const totalBalance = (accounts || []).reduce(
      (sum, acc) => sum + (Number(acc.current_balance) || 0),
      0
    );

    // 2. Fetch monthly transactions for current month
    const startOfMonth = new Date();
    startOfMonth.setDate(1);
    startOfMonth.setHours(0, 0, 0, 0);

    const { data: monthlyTxns } = await supabase
      .from('transactions')
      .select('amount, direction, is_excluded_from_totals')
      .gte('transaction_date', startOfMonth.toISOString());

    let monthlyIncome = 0;
    let monthlyExpense = 0;

    (monthlyTxns || []).forEach((tx) => {
      if (tx.is_excluded_from_totals) return;
      const amt = Number(tx.amount);
      if (tx.direction === 'CREDIT') monthlyIncome += amt;
      else monthlyExpense += amt;
    });

    const netSavings = monthlyIncome - monthlyExpense;
    const savingsRate = monthlyIncome > 0 ? (netSavings / monthlyIncome) * 100 : 0;

    return NextResponse.json({
      summary: {
        total_balance: totalBalance,
        monthly_income: monthlyIncome,
        monthly_expense: monthlyExpense,
        net_savings: netSavings,
        savings_rate: Math.round(savingsRate * 10) / 10,
        income_change_pct: 0,
        expense_change_pct: 0,
      },
      cashFlow: mockMonthlyCashFlow,
      categoryBreakdown: mockCategoryBreakdown,
      source: 'supabase',
    });
  } catch {
    return NextResponse.json({
      summary: mockSummary,
      cashFlow: mockMonthlyCashFlow,
      categoryBreakdown: mockCategoryBreakdown,
      source: 'fallback',
    });
  }
}
