/*
 * TP: Used Cursor to define TypeScript interfaces for User data from backend
 */

// TypeScript interfaces for User data from backend

export interface User {
  userId: number;
  sbuId: string;
  email: string;
  firstName: string;
  lastName: string;
  role: 'STUDENT' | 'INSTRUCTOR' | 'ADVISOR' | 'REGISTRAR';
  isActive: boolean;
  
  // Student-specific fields (already parsed from JSON)
  majors?: string[];
  degrees?: string[];
  minors?: string[];
  plannedMajors?: string[];
  plannedMinors?: string[];
  universityEntry?: {
    semester: string;
    year: number;
  };
  graduation?: {
    semester: string;
    year: number;
  } | null;
  classes?: ClassSchedule[];
  transferCourses?: TransferCourse[];
  coursePlan?: PlannedCourse[];
  aoi?: string;
  college?: string;
  directAdmit?: string;
  
  // Advisor-specific fields
  advisorLevel?: 'university' | 'college' | 'department';
  academicUnit?: string;
  
  // Instructor-specific fields
  department?: string;
  officeLocation?: string;
  officeHours?: string;
}

export interface ClassSchedule {
  class_id: number;
  department: string;
  course_num: number;
  section: number;
  semester: string;
  year: number;
  credits: number;
  GPNC: boolean;
  grade?: string;
}

export interface TransferCourse {
  class: string;
  university: string;
  credits: number;
  department?: string;
  course_num?: number;
  grade?: string;
}

export interface PlannedCourse {
  department: string;
  course_num: number;
  semester: string;
  year: number;
  credits: number;
}

// API Response types
export interface UserSearchResponse {
  users: User[];
  total: number;
  page: number;
  size: number;
}

export interface UserExportResponse {
  success: boolean;
  data?: {
    registrars: User[];
    academic_advisors: User[];
    instructors: User[];
    students: User[];
  };
  error?: string;
}
