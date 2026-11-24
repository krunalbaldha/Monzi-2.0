-- ========================================================
-- MONZI 2.0: INITIAL SEED DATA
-- ========================================================

-- Insert Categories
INSERT INTO public.categories (name, icon, color, is_income) VALUES
('Food & Dining', 'Utensils', '#F59E0B', FALSE),
('Groceries', 'ShoppingBag', '#10B981', FALSE),
('Shopping', 'ShoppingCart', '#EC4899', FALSE),
('Transportation & Fuel', 'Car', '#3B82F6', FALSE),
('Bills & Utilities', 'Zap', '#8B5CF6', FALSE),
('Entertainment & Subscriptions', 'Film', '#6366F1', FALSE),
('Healthcare & Medical', 'HeartPulse', '#EF4444', FALSE),
('Investments', 'TrendingUp', '#06B6D4', FALSE),
('Housing & Rent', 'Home', '#64748B', FALSE),
('Salary', 'Briefcase', '#16A34A', TRUE),
('Freelance & Consulting', 'Laptop', '#10B981', TRUE),
('Interest & Returns', 'Percent', '#84CC16', TRUE),
('Other Expense', 'CreditCard', '#94A3B8', FALSE),
('Other Income', 'Wallet', '#059669', TRUE)
ON CONFLICT (name) DO NOTHING;

-- Insert Personal Bank & Card Accounts
INSERT INTO public.accounts (id, bank_name, account_number_mask, account_type, initial_balance, current_balance)
VALUES 
('a0000000-0000-0000-0000-000000000001', 'HDFC Bank', 'XX8421', 'SAVINGS', 75000.00, 142580.50),
('a0000000-0000-0000-0000-000000000002', 'ICICI Bank', 'XX3190', 'SAVINGS', 25000.00, 48320.00),
('a0000000-0000-0000-0000-000000000003', 'ICICI Amazon Pay', 'XX5012', 'CREDIT_CARD', 0.00, -12450.00)
ON CONFLICT (bank_name, account_number_mask) DO NOTHING;

-- Sample Settings (API Secret, User Profile)
INSERT INTO public.app_settings (key, value)
VALUES 
('auth_config', '{"sync_secret": "monzi_sec_live_9921_dev", "currency": "INR", "currency_symbol": "₹"}'),
('user_profile', '{"name": "Krunal Baldha", "email": "krunal@example.com"}')
ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value;
