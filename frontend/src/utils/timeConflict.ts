// Simple time conflict checking utility

const BACKEND_BASE = import.meta.env.VITE_BACKEND_URL ?? 'http://localhost:8080';

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