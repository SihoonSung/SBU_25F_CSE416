/*
 * TP: Used Cursor to implement authentication utilities with OAuth2 integration
 */

// src/utils/auth.ts

import { getBackendBase, API_ENDPOINTS } from '../constants';

const BACKEND_BASE = getBackendBase();

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
  const res = await fetch(`${BACKEND_BASE}${API_ENDPOINTS.AUTH_ME}`, {
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
    const res = await fetch(`${BACKEND_BASE}${API_ENDPOINTS.AUTH_ME}`, {
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
  return `${BACKEND_BASE}${API_ENDPOINTS.OAUTH_GOOGLE}`;
}

export async function signOut(): Promise<void> {
  await fetch(`${BACKEND_BASE}${API_ENDPOINTS.AUTH_LOGOUT}`, {
    method: 'POST',
    credentials: 'include', 
  });
}