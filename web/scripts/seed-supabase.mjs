import { createClient } from '@supabase/supabase-js';

const url = 'https://hgxrqmkxxylfjgoqjkzp.supabase.co';
const key = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImhneHJxbWt4eHlsZmpnb3Fqa3pwIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTU1MjQzMTIsImV4cCI6MjExMTEwMDMxMn0.oe1FbL4D6IzvsW92xtoaKONUVrYqWlozK7coMhsG-UM';

const client = createClient(url, key);

const categories = [
  { name: 'Food & Dining', icon: 'Utensils', color: '#F59E0B', is_income: false },
  { name: 'Groceries', icon: 'ShoppingBag', color: '#10B981', is_income: false },
  { name: 'Shopping', icon: 'ShoppingCart', color: '#EC4899', is_income: false },
  { name: 'Transportation & Fuel', icon: 'Car', color: '#3B82F6', is_income: false },
  { name: 'Bills & Utilities', icon: 'Zap', color: '#8B5CF6', is_income: false },
  { name: 'Entertainment & Subscriptions', icon: 'Film', color: '#6366F1', is_income: false },
  { name: 'Healthcare & Medical', icon: 'HeartPulse', color: '#EF4444', is_income: false },
  { name: 'Investments', icon: 'TrendingUp', color: '#06B6D4', is_income: false },
  { name: 'Housing & Rent', icon: 'Home', color: '#64748B', is_income: false },
  { name: 'Salary', icon: 'Briefcase', color: '#16A34A', is_income: true },
  { name: 'Freelance & Consulting', icon: 'Laptop', color: '#10B981', is_income: true },
  { name: 'Interest & Returns', icon: 'Percent', color: '#84CC16', is_income: true },
  { name: 'Other Expense', icon: 'CreditCard', color: '#94A3B8', is_income: false },
  { name: 'Other Income', icon: 'Wallet', color: '#059669', is_income: true },
];

async function seed() {
  console.log('Seeding categories into Supabase...');
  const { data, error } = await client
    .from('categories')
    .upsert(categories, { onConflict: 'name' })
    .select();

  if (error) {
    console.error('Error seeding categories:', error.message);
  } else {
    console.log(`Successfully seeded ${data.length} categories!`);
  }
}

seed();
