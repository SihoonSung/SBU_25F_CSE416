// src/utils/auth.ts

const BACKEND_BASE =
  import.meta.env.VITE_BACKEND_URL ?? 'http://localhost:8080';

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
    const res = await fetch(`${BACKEND_BASE}/api/auth/status`, {
      method: 'GET',
      credentials: 'include', 
    });
    if (!res.ok) return false;
    const data = await res.json();
    return Boolean(data?.authenticated);
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