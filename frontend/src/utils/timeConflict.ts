// Simple time conflict checking utility

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

/**
 * Check time conflict between two schedules
 */
export async function checkTimeConflict(schedule1: string, schedule2: string): Promise<{
  hasConflict: boolean;
  message: string;
  error?: string;
}> {
  try {
    const response = await fetch(
      `${BACKEND_BASE}/api/time-conflict/test?schedule1=${encodeURIComponent(schedule1)}&schedule2=${encodeURIComponent(schedule2)}`,
      {
        method: 'GET',
        credentials: 'include',
      }
    );

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    const data = await response.json();
    return {
      hasConflict: data.hasConflict,
      message: data.message,
      error: data.error
    };
  } catch (error) {
    console.error('Error checking time conflict:', error);
    return {
      hasConflict: false,
      message: 'Error checking conflicts',
      error: error instanceof Error ? error.message : 'Unknown error'
    };
  }
}