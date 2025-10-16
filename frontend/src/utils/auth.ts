export function getAuthToken(): string | null {
  try {
    return localStorage.getItem('authToken')
  } catch (_) {
    return null
  }
}

export function isAuthenticated(): boolean {
  // Temporarily return true for development
  return true
  // return Boolean(getAuthToken())
}

export function signIn(token: string, email: string): void {
  localStorage.setItem('authToken', token)
  localStorage.setItem('userEmail', email)
}

export function signOut(): void {
  localStorage.removeItem('authToken')
  localStorage.removeItem('userEmail')
}


