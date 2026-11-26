-- ========================================================
-- MONZI 2.0: FRD SECTION 7.3 CATEGORIES & DEFAULT SEED
-- ========================================================

-- Insert Complete FRD 7.3 Categories (Income & Expenses)
INSERT INTO public.categories (name, icon, color, is_income) VALUES
-- Income Categories
('Salary', 'Briefcase', '#16A34A', TRUE),
('Business Income', 'Building2', '#10B981', TRUE),
('Interest', 'Percent', '#84CC16', TRUE),
('Cashback', 'Gift', '#06B6D4', TRUE),
('Refund', 'RotateCcw', '#6366F1', TRUE),
('Other Income', 'Wallet', '#059669', TRUE),

-- Expense Categories
('Food & Dining', 'Utensils', '#F59E0B', FALSE),
('Shopping', 'ShoppingCart', '#EC4899', FALSE),
('Travel & Transport', 'Car', '#3B82F6', FALSE),
('Bills & Utilities', 'Zap', '#8B5CF6', FALSE),
('Entertainment', 'Film', '#6366F1', FALSE),
('Healthcare', 'HeartPulse', '#EF4444', FALSE),
('Education', 'GraduationCap', '#0EA5E9', FALSE),
('EMI & Loan Payments', 'Landmark', '#F97316', FALSE),
('Subscriptions', 'Repeat', '#A855F7', FALSE),
('Lifestyle', 'Sparkles', '#EC4899', FALSE),
('Investments', 'TrendingUp', '#06B6D4', FALSE),
('Groceries', 'ShoppingBag', '#10B981', FALSE),
('Other Expenses', 'CreditCard', '#94A3B8', FALSE)
ON CONFLICT (name) DO NOTHING;
