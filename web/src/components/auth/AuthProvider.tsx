'use client';

import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';
import { createBrowserSupabaseClient, isBrowserSupabaseConfigured } from '@/lib/supabase/client';

export const BIOMETRIC_DEVICE_STORAGE_KEY = 'monzi_biometric_device';

export interface UserProfileInfo {
  id: string;
  email: string;
  full_name: string;
  initials: string;
  currency: string;
  sms_permission: 'prompt' | 'granted' | 'denied' | 'unsupported';
  biometric_enabled: boolean;
  device_sync_token?: string;
}

export interface AuthContextValue {
  user: any | null;
  profile: UserProfileInfo | null;
  loading: boolean;
  isLocked: boolean;
  biometricSupported: boolean;
  biometricEnabled: boolean;
  hasLocalBiometricDevice: boolean;
  refreshAuth: () => Promise<void>;
  signOut: () => Promise<void>;
  lockSession: () => Promise<void>;
  unlockWithBiometric: () => Promise<{ success: boolean; error?: string }>;
  unlockWithPassword: (password: string) => Promise<{ success: boolean; error?: string }>;
  enrollBiometric: (deviceName?: string) => Promise<{ success: boolean; error?: string }>;
  disableBiometric: () => Promise<{ success: boolean; error?: string }>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

function base64UrlToUint8Array(base64Url: string): Uint8Array {
  const padding = '='.repeat((4 - (base64Url.length % 4)) % 4);
  const base64 = (base64Url + padding).replace(/-/g, '+').replace(/_/g, '/');
  const rawData = atob(base64);
  const outputArray = new Uint8Array(rawData.length);
  for (let i = 0; i < rawData.length; i++) {
    outputArray[i] = rawData.charCodeAt(i);
  }
  return outputArray;
}

function bufferToBase64Url(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  for (let i = 0; i < bytes.byteLength; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function bufferToBase64(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  for (let i = 0; i < bytes.byteLength; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary);
}

function detectDeviceName(): string {
  if (typeof navigator === 'undefined') return 'This Device';
  const ua = navigator.userAgent || '';
  if (/iPhone/i.test(ua)) return 'iPhone Face ID / Touch ID';
  if (/iPad/i.test(ua)) return 'iPad Biometrics';
  if (/Android/i.test(ua)) return 'Android Biometrics';
  if (/Mac/i.test(ua)) return 'Mac Touch ID';
  if (/Windows/i.test(ua)) return 'Windows Hello';
  return 'Platform Authenticator';
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<any | null>(null);
  const [profile, setProfile] = useState<UserProfileInfo | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [isLocked, setIsLocked] = useState<boolean>(false);
  const [biometricSupported, setBiometricSupported] = useState<boolean>(false);
  const [hasLocalBiometricDevice, setHasLocalBiometricDevice] = useState<boolean>(false);

  const supabase = useMemo(() => createBrowserSupabaseClient(), []);

  const readLocalBiometricFlag = useCallback(() => {
    if (typeof window === 'undefined') return false;
    try {
      const raw = window.localStorage.getItem(BIOMETRIC_DEVICE_STORAGE_KEY);
      if (!raw) return false;
      const parsed = JSON.parse(raw);
      return Boolean(parsed?.credentialId);
    } catch {
      return false;
    }
  }, []);

  const refreshAuth = useCallback(async () => {
    try {
      const res = await fetch('/api/auth/session', {
        method: 'GET',
        credentials: 'include',
        cache: 'no-store',
      });
      if (!res.ok) {
        setUser(null);
        setProfile(null);
        setIsLocked(false);
        setHasLocalBiometricDevice(readLocalBiometricFlag());
        return;
      }

      const data = await res.json();
      const nextUser = data.user ?? null;
      const nextProfile: UserProfileInfo | null = data.profile ?? null;
      const nextLocked = Boolean(data.isLocked);

      setUser(nextUser);
      setProfile(nextProfile);
      setIsLocked(nextLocked);

      if (typeof window !== 'undefined' && nextUser) {
        const creds = nextUser.user_metadata?.biometric_credentials;
        if (Array.isArray(creds) && creds.length > 0) {
          const primaryCred = creds[creds.length - 1];
          window.localStorage.setItem(
            BIOMETRIC_DEVICE_STORAGE_KEY,
            JSON.stringify({
              credentialId: primaryCred.credentialId || primaryCred.id || '',
              email: nextProfile?.email || nextUser.email || '',
              fullName: nextProfile?.full_name || '',
            })
          );
          setHasLocalBiometricDevice(true);
        } else {
          setHasLocalBiometricDevice(readLocalBiometricFlag());
        }
      } else {
        setHasLocalBiometricDevice(readLocalBiometricFlag());
      }
    } catch (err) {
      console.error('Failed to refresh auth session:', err);
    } finally {
      setLoading(false);
    }
  }, [readLocalBiometricFlag]);

  useEffect(() => {
    let active = true;

    const checkBiometricSupport = async () => {
      if (
        typeof window !== 'undefined' &&
        typeof window.PublicKeyCredential !== 'undefined' &&
        typeof window.PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable ===
          'function'
      ) {
        try {
          const available =
            await window.PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable();
          if (active) setBiometricSupported(Boolean(available));
        } catch {
          if (active) setBiometricSupported(false);
        }
      }
    };

    setHasLocalBiometricDevice(readLocalBiometricFlag());
    checkBiometricSupport();
    refreshAuth();

    if (!isBrowserSupabaseConfigured) {
      return () => {
        active = false;
      };
    }

    const {
      data: { subscription },
    } = supabase.auth.onAuthStateChange((event) => {
      if (
        event === 'SIGNED_IN' ||
        event === 'SIGNED_OUT' ||
        event === 'USER_UPDATED' ||
        event === 'PASSWORD_RECOVERY'
      ) {
        refreshAuth();
      }
    });

    return () => {
      active = false;
      subscription.unsubscribe();
    };
  }, [supabase, refreshAuth, readLocalBiometricFlag]);

  const signOut = useCallback(async () => {
    try {
      await fetch('/api/auth/logout', {
        method: 'POST',
        credentials: 'include',
      });
    } catch (err) {
      console.error('Sign out failed:', err);
    } finally {
      setUser(null);
      setProfile(null);
      setIsLocked(false);
      if (typeof window !== 'undefined') {
        window.location.href = '/login';
      }
    }
  }, []);

  const lockSession = useCallback(async () => {
    try {
      const res = await fetch('/api/auth/session', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ locked: true }),
      });
      if (res.ok) {
        setIsLocked(true);
      }
    } catch (err) {
      console.error('Lock session failed:', err);
    }
  }, []);

  const enrollBiometric = useCallback(
    async (deviceName?: string): Promise<{ success: boolean; error?: string }> => {
      if (typeof window === 'undefined' || !window.PublicKeyCredential) {
        return { success: false, error: 'Biometric authentication is not supported on this device.' };
      }

      try {
        const optRes = await fetch('/api/auth/webauthn/register-options', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          credentials: 'include',
          body: JSON.stringify({ deviceName }),
        });

        const options = await optRes.json();
        if (!optRes.ok) {
          return { success: false, error: options.error || 'Could not start biometric setup.' };
        }

        const publicKey: PublicKeyCredentialCreationOptions = {
          challenge: base64UrlToUint8Array(options.challenge) as BufferSource,
          rp: options.rp,
          user: {
            id: base64UrlToUint8Array(options.user.id) as BufferSource,
            name: options.user.name,
            displayName: options.user.displayName,
          },
          pubKeyCredParams: options.pubKeyCredParams,
          authenticatorSelection: options.authenticatorSelection,
          timeout: options.timeout ?? 60000,
          attestation: options.attestation ?? 'none',
          excludeCredentials: Array.isArray(options.excludeCredentials)
            ? options.excludeCredentials.map((cred: { id: string; type: PublicKeyCredentialType }) => ({
                id: base64UrlToUint8Array(cred.id) as BufferSource,
                type: cred.type || 'public-key',
              }))
            : undefined,
        };

        const credential = (await navigator.credentials.create({
          publicKey,
        })) as PublicKeyCredential | null;

        if (!credential) {
          return { success: false, error: 'Biometric registration was cancelled.' };
        }

        const attResponse = credential.response as AuthenticatorAttestationResponse;
        const publicKeyBuffer =
          typeof attResponse.getPublicKey === 'function' ? attResponse.getPublicKey() : null;
        const publicKeySpki = publicKeyBuffer ? bufferToBase64(publicKeyBuffer) : null;
        const publicKeyAlgorithm =
          typeof attResponse.getPublicKeyAlgorithm === 'function'
            ? attResponse.getPublicKeyAlgorithm()
            : -7;

        const verifyRes = await fetch('/api/auth/webauthn/register-verify', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          credentials: 'include',
          body: JSON.stringify({
            id: credential.id,
            rawId: bufferToBase64Url(credential.rawId),
            type: credential.type,
            deviceName: deviceName || detectDeviceName(),
            response: {
              clientDataJSON: bufferToBase64Url(attResponse.clientDataJSON),
              attestationObject: bufferToBase64Url(attResponse.attestationObject),
              publicKey: publicKeySpki,
              publicKeyAlgorithm,
            },
          }),
        });

        const verifyData = await verifyRes.json();
        if (!verifyRes.ok) {
          return {
            success: false,
            error: verifyData.error || 'Failed to verify biometric credential.',
          };
        }

        window.localStorage.setItem(
          BIOMETRIC_DEVICE_STORAGE_KEY,
          JSON.stringify({
            credentialId: verifyData.credentialId || credential.id,
            email: profile?.email || user?.email || '',
            fullName: profile?.full_name || '',
          })
        );
        setHasLocalBiometricDevice(true);

        await refreshAuth();
        return { success: true };
      } catch (err: any) {
        const message =
          err?.name === 'NotAllowedError'
            ? 'Biometric prompt was cancelled or timed out.'
            : err?.message || 'Failed to enroll biometric unlock.';
        return { success: false, error: message };
      }
    },
    [profile, user, refreshAuth]
  );

  const unlockWithBiometric = useCallback(async (): Promise<{
    success: boolean;
    error?: string;
  }> => {
    if (typeof window === 'undefined' || !window.PublicKeyCredential) {
      return { success: false, error: 'Biometric authentication is not supported on this device.' };
    }

    try {
      const optRes = await fetch('/api/auth/webauthn/auth-options', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
      });

      const options = await optRes.json();
      if (!optRes.ok) {
        return { success: false, error: options.error || 'Could not start biometric unlock.' };
      }

      const publicKey: PublicKeyCredentialRequestOptions = {
        challenge: base64UrlToUint8Array(options.challenge) as BufferSource,
        rpId: options.rpId,
        allowCredentials: Array.isArray(options.allowCredentials)
          ? options.allowCredentials.map((cred: { id: string; type?: PublicKeyCredentialType }) => ({
              id: base64UrlToUint8Array(cred.id) as BufferSource,
              type: cred.type || 'public-key',
            }))
          : [],
        userVerification: options.userVerification || 'required',
        timeout: options.timeout ?? 60000,
      };

      const credential = (await navigator.credentials.get({
        publicKey,
      })) as PublicKeyCredential | null;

      if (!credential) {
        return { success: false, error: 'Biometric verification was cancelled.' };
      }

      const assertionResponse = credential.response as AuthenticatorAssertionResponse;

      const verifyRes = await fetch('/api/auth/webauthn/auth-verify', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({
          id: credential.id,
          rawId: bufferToBase64Url(credential.rawId),
          type: credential.type,
          response: {
            clientDataJSON: bufferToBase64Url(assertionResponse.clientDataJSON),
            authenticatorData: bufferToBase64Url(assertionResponse.authenticatorData),
            signature: bufferToBase64Url(assertionResponse.signature),
            userHandle: assertionResponse.userHandle
              ? bufferToBase64Url(assertionResponse.userHandle)
              : null,
          },
        }),
      });

      const verifyData = await verifyRes.json();
      if (!verifyRes.ok) {
        return {
          success: false,
          error: verifyData.error || 'Biometric verification failed.',
        };
      }

      setIsLocked(false);
      await refreshAuth();
      return { success: true };
    } catch (err: any) {
      const message =
        err?.name === 'NotAllowedError'
          ? 'Biometric prompt was cancelled or timed out.'
          : err?.message || 'Biometric unlock failed.';
      return { success: false, error: message };
    }
  }, [refreshAuth]);

  const unlockWithPassword = useCallback(
    async (password: string): Promise<{ success: boolean; error?: string }> => {
      try {
        const res = await fetch('/api/auth/unlock-password', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          credentials: 'include',
          body: JSON.stringify({ password }),
        });

        const data = await res.json();
        if (!res.ok) {
          return { success: false, error: data.error || 'Incorrect password.' };
        }

        setIsLocked(false);
        await refreshAuth();
        return { success: true };
      } catch (err: any) {
        return { success: false, error: err?.message || 'Password unlock failed.' };
      }
    },
    [refreshAuth]
  );

  const disableBiometric = useCallback(async (): Promise<{
    success: boolean;
    error?: string;
  }> => {
    try {
      const res = await fetch('/api/auth/webauthn/disable', {
        method: 'POST',
        credentials: 'include',
      });
      const data = await res.json();
      if (!res.ok) {
        return { success: false, error: data.error || 'Failed to disable biometrics.' };
      }

      if (typeof window !== 'undefined') {
        window.localStorage.removeItem(BIOMETRIC_DEVICE_STORAGE_KEY);
      }
      setHasLocalBiometricDevice(false);
      await refreshAuth();
      return { success: true };
    } catch (err: any) {
      return { success: false, error: err?.message || 'Failed to disable biometrics.' };
    }
  }, [refreshAuth]);

  const biometricEnabled = Boolean(
    profile?.biometric_enabled ||
      user?.user_metadata?.biometric_enabled ||
      (Array.isArray(user?.user_metadata?.biometric_credentials) &&
        user.user_metadata.biometric_credentials.length > 0)
  );

  const value: AuthContextValue = {
    user,
    profile,
    loading,
    isLocked,
    biometricSupported,
    biometricEnabled,
    hasLocalBiometricDevice,
    refreshAuth,
    signOut,
    lockSession,
    unlockWithBiometric,
    unlockWithPassword,
    enrollBiometric,
    disableBiometric,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
