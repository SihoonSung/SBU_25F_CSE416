/**
 * Application-wide constants
 * TP: Used Cursor to centralize hardcoded values
 */

// Backend URL - use environment variable with fallback
export const getBackendBase = (): string => {
  if (typeof import.meta !== 'undefined' && import.meta.env?.VITE_BACKEND_URL) {
    return import.meta.env.VITE_BACKEND_URL;
  }
  // Fallback for Jest/testing environment
  // eslint-disable-next-line @typescript-eslint/ban-ts-comment
  // @ts-ignore - process is available in Jest environment
  if (typeof process !== 'undefined' && process.env?.VITE_BACKEND_URL) {
    // eslint-disable-next-line @typescript-eslint/ban-ts-comment
    // @ts-ignore
    return process.env.VITE_BACKEND_URL;
  }
  return 'http://localhost:8080';
};

// External URLs
export const EXTERNAL_URLS = {
  CLASSIE_EVALS: 'https://classie-evals.stonybrook.edu',
} as const;

// Semester constants
export const SEMESTERS = ['Spring', 'Summer', 'Fall', 'Winter'] as const;
export const SEMESTERS_WITH_SUMMER = ['Spring', 'SummerI', 'SummerII', 'Fall'] as const;

// Class standing constants
export const STANDINGS = ['U1', 'U2', 'U3', 'U4'] as const;

export const STANDING_TO_U: Record<string, string> = {
  U1: 'U1',
  U2: 'U2',
  U3: 'U3',
  U4: 'U4',
  FRESHMAN: 'U1',
  SOPHOMORE: 'U2',
  JUNIOR: 'U3',
  SENIOR: 'U4',
};

// Year range constants
export const YEAR_MIN = 2000;
export const YEAR_MAX = 2100;

// Utility functions
export const getCurrentYear = (): number => {
  return new Date().getFullYear();
};

export const getCurrentSemester = (): string => {
  const month = new Date().getMonth(); // 0-11
  // Fall: Aug-Dec (8-11), Spring: Jan-May (0-4), Summer: Jun-Jul (5-6)
  if (month >= 8 || month <= 4) {
    return month >= 8 ? 'Fall' : 'Spring';
  }
  return 'Summer';
};

export const getDefaultYear = (): number => {
  return getCurrentYear();
};

export const getDefaultSemester = (): string => {
  return getCurrentSemester();
};

// API Endpoints
export const API_ENDPOINTS = {
  // Auth
  AUTH_ME: '/api/auth/me',
  AUTH_SUCCESS: '/api/auth/success',
  AUTH_LOGOUT: '/api/auth/logout',
  OAUTH_GOOGLE: '/oauth2/authorization/google',
  
  // Profile
  PROFILE_AUTH_INFO: '/api/profile/auth-info',
  PROFILE_STUDENT_BY_EMAIL: '/api/profile/student-by-email',
  
  // Classes
  CLASSES_TERMS: '/api/classes/terms',
  
  // Admin
  ADMIN_REGISTRATION_SCHEDULES: '/api/admin/registration-schedules',
  
  // Scraping
  SCRAPING_TERMS: '/api/scraping/terms',
  
  // Time conflict
  TIME_CONFLICT_TEST: '/api/time-conflict/test',
} as const;

// Pagination defaults
export const DEFAULT_ITEMS_PER_PAGE = 10;

// Planner defaults
export const PLANNER_DEFAULTS = {
  MIN_CREDITS: 15,
  MAX_CREDITS: 18,
} as const;

