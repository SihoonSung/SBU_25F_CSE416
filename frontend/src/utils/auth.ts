// src/utils/auth.ts

// Support both Vite (import.meta.env) and Jest (process.env) environments
let BACKEND_BASE = 'http://localhost:8080'
try {
  // @ts-ignore - import.meta is Vite-specific
  // eslint-disable-next-line @typescript-eslint/ban-ts-comment
  // @ts-ignore
  // eslint-disable-next-line no-eval
  const viteEnv = eval('import.meta?.env')
  if (viteEnv && viteEnv.VITE_BACKEND_URL) {
    BACKEND_BASE = viteEnv.VITE_BACKEND_URL
  }
} catch (e) {
  // In Jest environment, use process.env
  if (typeof process !== 'undefined' && process.env && process.env.VITE_BACKEND_URL) {
    BACKEND_BASE = process.env.VITE_BACKEND_URL
  }
}

export async function getCurrentUser(): Promise<{
  user?: {
    userId: number;
    email: string;
    firstName: string;
    lastName: string;
    fullName: string;
    role: string;
    profilePictureUrl?: string;
    isActive: boolean;
    studentId?: string;
    major?: string;
    gpa?: number;
    graduationYear?: number;
    department?: string;
    officeLocation?: string;
    officeHours?: string;
  };
}> {
  const res = await fetch(`${BACKEND_BASE}/api/auth/me`, {
    method: 'GET',
    credentials: 'include',
    cache: 'no-cache',
    headers: {
      'Cache-Control': 'no-cache',
      'Pragma': 'no-cache'
    }
  });

  if (!res.ok) {
    throw new Error('Not authenticated');
  }
  return res.json();
}

export async function isAuthenticated(): Promise<boolean> {
  try {
    // Use /api/auth/me to actually check if user is authenticated
    const res = await fetch(`${BACKEND_BASE}/api/auth/me`, {
      method: 'GET',
      credentials: 'include',
      cache: 'no-cache',
      headers: {
        'Cache-Control': 'no-cache',
        'Pragma': 'no-cache'
      }
    });
    
    if (!res.ok) {
      return false;
    }
    
    const data = await res.json();
    // Check if user data actually exists
    const user = data.user || data;
    return Boolean(user && user.userId && user.email);
  } catch {
    return false;
  }
}

export function getGoogleLoginUrl(): string {
  return `${BACKEND_BASE}/oauth2/authorization/google`;
}

export async function signOut(): Promise<void> {
  await fetch(`${BACKEND_BASE}/api/auth/logout`, {
    method: 'POST',
    credentials: 'include', 
  });
}