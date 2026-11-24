import { createClient } from '@supabase/supabase-js';

const url = 'https://hgxrqmkxxylfjgoqjkzp.supabase.co';
const key = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImhneHJxbWt4eHlsZmpnb3Fqa3pwIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTU1MjQzMTIsImV4cCI6MjExMTEwMDMxMn0.oe1FbL4D6IzvsW92xtoaKONUVrYqWlozK7coMhsG-UM';

const client = createClient(url, key);

async function inspectSupabase() {
  const { data: cats } = await client.from('categories').select('name');
  console.log('Categories count:', cats?.length || 0);

  const { data: accs } = await client.from('accounts').select('bank_name, account_number_mask, current_balance');
  console.log('Accounts count:', accs?.length || 0);
  if (accs && accs.length > 0) {
    console.log('Accounts:', accs);
  }

  const { data: txs } = await client.from('transactions').select('id');
  console.log('Transactions count:', txs?.length || 0);
}

inspectSupabase();
