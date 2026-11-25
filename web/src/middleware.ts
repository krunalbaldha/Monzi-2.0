import { createServerClient, type CookieOptions } from '@supabase/ssr';
import { NextResponse, type NextRequest } from 'next/server';

const AUTH_ROUTES = ['/login', '/signup', '/forgot-password', '/reset-password'];
const REDIRECT_WHEN_AUTHENTICATED = ['/login', '/signup', '/forgot-password'];

function isPublicRoute(pathname: string): boolean {
  if (AUTH_ROUTES.includes(pathname)) return true;
  if (pathname === '/auth/callback') return true;
  if (
    pathname === '/manifest.json' ||
    pathname === '/sw.js' ||
    pathname === '/offline.html' ||
    pathname === '/apple-touch-icon.png' ||
    pathname === '/favicon.ico' ||
    pathname.startsWith('/icon')
  ) {
    return true;
  }
  if (
    pathname === '/api/health' ||
    pathname === '/api/sync' ||
    pathname.startsWith('/api/auth/')
  ) {
    return true;
  }
  return false;
}

export async function middleware(request: NextRequest) {
  const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL || '';
  const supabaseAnonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY || '';

  if (!supabaseUrl || !supabaseAnonKey || supabaseUrl.includes('placeholder')) {
    return NextResponse.next({ request });
  }

  let supabaseResponse = NextResponse.next({ request });

  const supabase = createServerClient(supabaseUrl, supabaseAnonKey, {
    cookies: {
      getAll() {
        return request.cookies.getAll();
      },
      setAll(cookiesToSet: Array<{ name: string; value: string; options: CookieOptions }>) {
        cookiesToSet.forEach(({ name, value }) => {
          request.cookies.set(name, value);
        });
        supabaseResponse = NextResponse.next({ request });
        cookiesToSet.forEach(({ name, value, options }) => {
          supabaseResponse.cookies.set(name, value, options);
        });
      },
    },
  });

  const {
    data: { user },
  } = await supabase.auth.getUser();

  const { pathname, searchParams } = request.nextUrl;
  const publicRoute = isPublicRoute(pathname);

  // API routes handle their own 401/Bearer responses; never redirect API calls to /login HTML
  if (pathname.startsWith('/api/')) {
    return supabaseResponse;
  }

  // Unauthenticated access to protected page route -> /login?next=<pathname>
  if (!user && !publicRoute) {
    const loginUrl = request.nextUrl.clone();
    loginUrl.pathname = '/login';
    loginUrl.search = '';
    const nextPath = pathname + (request.nextUrl.search || '');
    loginUrl.searchParams.set('next', nextPath);
    return NextResponse.redirect(loginUrl);
  }

  // Authenticated user visiting login/signup/forgot-password -> redirect to / (unless ?unlock=1)
  if (user && REDIRECT_WHEN_AUTHENTICATED.includes(pathname)) {
    if (searchParams.get('unlock') !== '1') {
      const homeUrl = request.nextUrl.clone();
      homeUrl.pathname = '/';
      homeUrl.search = '';
      return NextResponse.redirect(homeUrl);
    }
  }

  return supabaseResponse;
}

export const config = {
  matcher: [
    '/((?!_next/static|_next/image|favicon.ico|.*\\.(?:svg|png|jpg|jpeg|gif|webp|ico)$).*)',
  ],
};
