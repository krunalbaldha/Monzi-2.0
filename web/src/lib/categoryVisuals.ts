import {
  Utensils,
  ShoppingCart,
  Car,
  Fuel,
  ShoppingBag,
  Receipt,
  Home,
  Film,
  HeartPulse,
  GraduationCap,
  Repeat,
  Shield,
  Landmark,
  TrendingUp,
  Gift,
  ArrowLeftRight,
  Undo2,
  CreditCard,
  Banknote,
  Briefcase,
  Sparkles,
  Store,
  type LucideIcon,
} from 'lucide-react';

export interface CategoryVisual {
  icon: LucideIcon;
  defaultColor: string;
}

export function getCategoryVisual(
  categoryName?: string | null,
  transactionType?: string | null,
  direction?: string | null,
  merchant?: string | null
): CategoryVisual {
  const name = (categoryName || '').toLowerCase();
  const type = (transactionType || '').toUpperCase();
  const merch = (merchant || '').toLowerCase();

  if (type === 'CC_BILL_PAYMENT') return { icon: CreditCard, defaultColor: '#7C3AED' };
  if (type === 'TRANSFER') return { icon: ArrowLeftRight, defaultColor: '#6366F1' };
  if (type === 'REFUND' || type === 'REVERSAL') return { icon: Undo2, defaultColor: '#059669' };

  if (name.includes('salary') || name.includes('payroll')) return { icon: Banknote, defaultColor: '#059669' };
  if (name.includes('freelance') || name.includes('business')) return { icon: Briefcase, defaultColor: '#0D9488' };
  if (name.includes('invest') || name.includes('dividend')) return { icon: TrendingUp, defaultColor: '#2563EB' };
  if (name.includes('cashback') || name.includes('reward') || name.includes('gift'))
    return { icon: Gift, defaultColor: '#10B981' };
  if (name.includes('refund')) return { icon: Undo2, defaultColor: '#059669' };

  if (name.includes('food') || name.includes('dining') || merch.includes('swiggy') || merch.includes('zomato'))
    return { icon: Utensils, defaultColor: '#F97316' };
  if (name.includes('grocer') || merch.includes('blinkit') || merch.includes('zepto'))
    return { icon: ShoppingCart, defaultColor: '#16A34A' };
  if (name.includes('fuel') || name.includes('petrol')) return { icon: Fuel, defaultColor: '#EA580C' };
  if (name.includes('travel') || name.includes('transport') || merch.includes('uber') || merch.includes('ola'))
    return { icon: Car, defaultColor: '#2563EB' };
  if (name.includes('shop') || merch.includes('amazon') || merch.includes('flipkart'))
    return { icon: ShoppingBag, defaultColor: '#DB2777' };
  if (name.includes('bill') || name.includes('utilit') || name.includes('electric'))
    return { icon: Receipt, defaultColor: '#7C3AED' };
  if (name.includes('rent') || name.includes('hous')) return { icon: Home, defaultColor: '#4F46E5' };
  if (name.includes('entertain') || name.includes('movie')) return { icon: Film, defaultColor: '#E11D48' };
  if (name.includes('subscri') || name.includes('ott')) return { icon: Repeat, defaultColor: '#9333EA' };
  if (name.includes('health') || name.includes('medic') || name.includes('pharma'))
    return { icon: HeartPulse, defaultColor: '#EF4444' };
  if (name.includes('educat')) return { icon: GraduationCap, defaultColor: '#0284C7' };
  if (name.includes('insur')) return { icon: Shield, defaultColor: '#0D9488' };
  if (name.includes('emi') || name.includes('loan') || name.includes('bank'))
    return { icon: Landmark, defaultColor: '#DC2626' };

  if (direction === 'CREDIT' || name.includes('income')) return { icon: Sparkles, defaultColor: '#059669' };
  return { icon: Store, defaultColor: '#2563EB' };
}
