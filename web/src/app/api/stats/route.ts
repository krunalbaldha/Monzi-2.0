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

    // 2. Fetch all transactions with categories
    const { data: allTxns } = await supabase
      .from('transactions')
      .select('amount, direction, transaction_type, is_excluded_from_totals, transaction_date, categories(name, color)')
      .order('transaction_date', { ascending: true });

    let monthlyIncome = 0;
    let monthlyExpense = 0;
    const categoryTotals: Record<string, { value: number; color: string }> = {};
    const monthlyMap: Record<string, { income: number; expense: number }> = {};

    const now = new Date();
    const currentMonthKey = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;

    (allTxns || []).forEach((tx: any) => {
      const amt = Number(tx.amount) || 0;
      const txDate = new Date(tx.transaction_date);
      const mKey = `${txDate.getFullYear()}-${String(txDate.getMonth() + 1).padStart(2, '0')}`;
      const mLabel = txDate.toLocaleString('default', { month: 'short' });

      if (!monthlyMap[mLabel]) {
        monthlyMap[mLabel] = { income: 0, expense: 0 };
      }

      if (tx.is_excluded_from_totals || tx.transaction_type === 'CC_BILL_PAYMENT' || tx.transaction_type === 'TRANSFER') {
        return;
      }

      if (tx.direction === 'CREDIT') {
        monthlyMap[mLabel].income += amt;
        if (mKey === currentMonthKey) {
          monthlyIncome += amt;
        }
      } else {
        monthlyMap[mLabel].expense += amt;
        if (mKey === currentMonthKey) {
          monthlyExpense += amt;
        }

        // Category breakdown for expenses
        const catName = tx.categories?.name || 'Other Expense';
        const catColor = tx.categories?.color || '#94A3B8';
        if (!categoryTotals[catName]) {
          categoryTotals[catName] = { value: 0, color: catColor };
        }
        categoryTotals[catName].value += amt;
      }
    });

    const netSavings = monthlyIncome - monthlyExpense;
    const savingsRate = monthlyIncome > 0 ? (netSavings / monthlyIncome) * 100 : 0;

    const dynamicCashFlow = Object.keys(monthlyMap).map((month) => ({
      month,
      income: monthlyMap[month].income,
      expense: monthlyMap[month].expense,
    }));

    const dynamicCategoryBreakdown = Object.keys(categoryTotals).map((name) => ({
      name,
      value: categoryTotals[name].value,
      color: categoryTotals[name].color,
    }));

    return NextResponse.json({
      summary: {
        total_balance: totalBalance,
        monthly_income: monthlyIncome,
        monthly_expense: monthlyExpense,
        net_savings: netSavings,
        savings_rate: Math.round(savingsRate * 10) / 10,
        income_change_pct: 8.5,
        expense_change_pct: -4.2,
      },
      cashFlow: dynamicCashFlow.length > 0 ? dynamicCashFlow : mockMonthlyCashFlow,
      categoryBreakdown: dynamicCategoryBreakdown.length > 0 ? dynamicCategoryBreakdown : mockCategoryBreakdown,
      source: 'supabase',
    });
  } catch (err) {
    console.error('Stats query error:', err);
    return NextResponse.json({
      summary: mockSummary,
      cashFlow: mockMonthlyCashFlow,
      categoryBreakdown: mockCategoryBreakdown,
      source: 'fallback',
    });
  }
}
