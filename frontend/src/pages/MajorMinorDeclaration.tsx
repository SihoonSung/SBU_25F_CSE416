import React, { useState, useEffect } from 'react';
import './MajorMinorDeclaration.css';
import { getCurrentUser } from '../utils/auth';
import LoadingModal from '../components/LoadingModal';

/**
 * Major/Minor Declaration Component
 * Provides interface for students to declare majors and minors.
 */

interface DegreeRequirement {
  requirementId: number;
  subjectCode: string;
  degreeType: 'BA' | 'BS' | 'MINOR';
  totalCreditsRequired: number;
  isRestricted: boolean;
  gpaRequirement?: number;
  admissionRequirements?: any;
  courseRequirements?: any;
  sbcRequirements?: any;
}

interface MajorMinorDeclaration {
  declarationId: number;
  studentNetId: string;
  subjectCode: string;
  degreeType: 'BA' | 'BS' | 'MINOR';
  declarationType: 'MAJOR' | 'MINOR';
  effectiveTerm: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN';
  advisorNetId?: string;
  advisorApprovalDate?: string;
  advisorComments?: string;
  submittedAt: string;
}

interface PlannedMajor {
  plannedMajorId: number;
  subjectCode: string;
  degreeType: 'BA' | 'BS' | 'MINOR';
  plannedAt: string;
  isActive: boolean;
  degreeRequirement?: {
    totalCreditsRequired: number;
    isRestricted: boolean;
    gpaRequirement?: number;
    admissionRequirements?: any;
    courseRequirements?: any;
    prerequisiteCourses?: any;
    sbcRequirements?: any;
  };
}

const MajorMinorDeclaration: React.FC = () => {
  const backendBase = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8080'
  // Helper to parse YYYY-MM-DD as local date (avoid timezone shifting)
  const parseYmdLocal = (dateStr?: string | null): Date | null => {
    if (!dateStr) return null;
    const parts = String(dateStr).split('-');
    if (parts.length === 3) {
      const y = parseInt(parts[0], 10);
      const m = parseInt(parts[1], 10) - 1;
      const d = parseInt(parts[2], 10);
      return new Date(y, m, d);
    }
    const dObj = new Date(dateStr);
    return isNaN(dObj.getTime()) ? null : dObj;
  };
  // const [degreeRequirements, setDegreeRequirements] = useState<DegreeRequirement[]>([]);
  const [myDeclarations, setMyDeclarations] = useState<MajorMinorDeclaration[]>([]);
  // Current approved majors and minors from User table
  const [currentApprovedMajors, setCurrentApprovedMajors] = useState<string[]>([]);
  const [currentApprovedMinors, setCurrentApprovedMinors] = useState<string[]>([]);

  // Load planned majors from localStorage
  // TP Used cursor to load planned majors from localStorage
  const loadPlannedMajorsFromStorage = () => {
    try {
      const stored = localStorage.getItem('plannedMajors');
      if (stored) {
        const plannedMajors = JSON.parse(stored);
        setMyPlannedMajors(plannedMajors);
        console.log('Loaded planned majors from storage:', plannedMajors.length);
      }
    } catch (err) {
      console.error('Error loading planned majors from storage:', err);
    }
  };

  // Save planned majors to localStorage
  // TP Used cursor to save planned majors to localStorage
  const savePlannedMajorsToStorage = (plannedMajors: PlannedMajor[]) => {
    try {
      localStorage.setItem('plannedMajors', JSON.stringify(plannedMajors));
      console.log('Saved planned majors to storage:', plannedMajors.length);
    } catch (err) {
      console.error('Error saving planned majors to storage:', err);
    }
  };
  const [myPlannedMajors, setMyPlannedMajors] = useState<PlannedMajor[]>([]);
  const [availableSubjects, setAvailableSubjects] = useState<string[]>([]);
  const [availableDegreeTypes, setAvailableDegreeTypes] = useState<string[]>([]);
  const [courseRequirements, setCourseRequirements] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [systemDate, setSystemDate] = useState<Date | null>(null);

  // Form state
  const [showAddForm, setShowAddForm] = useState(false);
  const [showChangeForm, setShowChangeForm] = useState(false);
  const [showPlannedMajorForm, setShowPlannedMajorForm] = useState(false);
  const [selectedSubject, setSelectedSubject] = useState('');
  const [selectedChangeFromSubject, setSelectedChangeFromSubject] = useState('');
  const [selectedDegreeType, setSelectedDegreeType] = useState<'BA' | 'BS' | 'MINOR'>('BA');
  const [selectedDeclarationType, setSelectedDeclarationType] = useState<'MAJOR' | 'MINOR'>('MAJOR');
  const [effectiveTerm, setEffectiveTerm] = useState('');
  const [submitting, setSubmitting] = useState(false);

  // Class Override (Time Conflict Waiver) state
  const [userId, setUserId] = useState<number | null>(null);
  const [showWaiverForm, setShowWaiverForm] = useState(false);
  const [enrolledClasses, setEnrolledClasses] = useState<any[]>([]);
  // const [availableClasses, setAvailableClasses] = useState<any[]>([]);
  const [selectedEnrolledClass, setSelectedEnrolledClass] = useState<number | null>(null);
  const [selectedConflictingClass, setSelectedConflictingClass] = useState<number | null>(null);
  const [waiverReason, setWaiverReason] = useState('');
  const [waiverTerm, setWaiverTerm] = useState({ semester: '', year: '' });
  const [loadingClasses, setLoadingClasses] = useState(false);
  const [submittingWaiver, setSubmittingWaiver] = useState(false);
  const [myWaivers, setMyWaivers] = useState<any[]>([]);
  const [loadingWaivers, setLoadingWaivers] = useState(false);
  // Search functionality for conflicting class
  const [conflictingClassSearch, setConflictingClassSearch] = useState('');
  const [searchResults, setSearchResults] = useState<any[]>([]);
  const [showSearchResults, setShowSearchResults] = useState(false);
  const [searching, setSearching] = useState(false);

  // Available terms from academic calendar
  const [availableTerms, setAvailableTerms] = useState<Array<{
    semester: string;
    year: number;
    displayName: string;
    value: string;
    declarationBeginDate?: string;
    declarationEndDate?: string;
  }>>([]);

  // Selected term's declaration dates
  const [selectedTermDeclarationDates, setSelectedTermDeclarationDates] = useState<{
    beginDate?: string;
    endDate?: string;
  } | null>(null);

  // TP: Used cursor to fetch available terms from academic calendar
  const fetchAvailableTerms = async () => {
    try {
      const response = await fetch(`${backendBase}/api/major-minor-declarations/available-terms`, {
        credentials: 'include',
        cache: 'no-cache',
        headers: {
          'Cache-Control': 'no-cache',
          'Pragma': 'no-cache'
        }
      });

      if (response.ok) {
        const data = await response.json();
        if (data.success && data.terms) {
          setAvailableTerms(data.terms || []);
          console.log('Available terms loaded:', data.terms?.length || 0);
        }
      }
    } catch (err) {
      console.warn('Failed to fetch available terms:', err);
    }
  };

  // TP: Used cursor to get available terms with declaration period info (inclusive of both start and end dates)
  const getAvailableTerms = () => {
    const currentDate = systemDate || new Date();
    // Normalize currentDate to midnight for date-only comparison
    const currentDateOnly = new Date(currentDate.getFullYear(), currentDate.getMonth(), currentDate.getDate());

    return availableTerms.map(term => {
      // Start date: beginning of day (00:00:00) - inclusive
      // begin comes from previous term section (handled by backend)
      const beginDate: Date | null = term.declarationBeginDate ? new Date(term.declarationBeginDate + 'T00:00:00') : null;
      const beginDateOnly = beginDate ? new Date(beginDate.getFullYear(), beginDate.getMonth(), beginDate.getDate()) : null;

      // End date: end of day (23:59:59.999) - inclusive
      const endDate = term.declarationEndDate ? new Date(term.declarationEndDate + 'T23:59:59.999') : null;
      const endDateOnly = endDate ? new Date(endDate.getFullYear(), endDate.getMonth(), endDate.getDate()) : null;

      let label = term.displayName;
      if (beginDate && endDate) {
        const beginStr = beginDate.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
        const endStr = endDate.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
        label += ` (${beginStr} - ${endStr})`;
      } else if (beginDate && !endDate) {
        const beginStr = beginDate.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
        label += ` (From ${beginStr})`;
      } else if (!beginDate && endDate) {
        const endStr = endDate.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
        label += ` (Until ${endStr})`;
      }

      // Check if current date is within declaration period with one-sided bounds allowed
      const afterStart = beginDateOnly ? currentDateOnly >= beginDateOnly : true;
      const beforeEnd = endDateOnly ? currentDateOnly <= endDateOnly : true;
      const isWithinPeriod = afterStart && beforeEnd;

      return {
        value: term.value,
        label,
        semester: term.semester,
        year: term.year,
        beginDate: term.declarationBeginDate,
        endDate: term.declarationEndDate,
        isWithinPeriod
      };
    });
  };

  useEffect(() => {
    fetchData();
    fetchAvailableTerms();
    loadPlannedMajorsFromStorage();
    initializeUser();
  }, []);

  // Update declaration dates when effective term changes
  useEffect(() => {
    if (effectiveTerm) {
      // Parse term to get semester and year
      const parts = effectiveTerm.split(' ');
      if (parts.length >= 2) {
        const semester = parts[0].charAt(0) + parts[0].slice(1).toLowerCase();
        const year = parseInt(parts[1]);

        // Fetch declaration dates for this term
        fetch(`${backendBase}/api/major-minor-declarations/declaration-dates/${semester}/${year}`, {
          credentials: 'include'
        })
          .then(res => res.json())
          .then(data => {
            if (data.success) {
              setSelectedTermDeclarationDates({
                beginDate: data.beginDate || undefined,
                endDate: data.endDate || undefined
              });
            }
          })
          .catch(err => {
            console.warn('Failed to fetch declaration dates:', err);
            setSelectedTermDeclarationDates(null);
          });
      }
    } else {
      setSelectedTermDeclarationDates(null);
    }
  }, [effectiveTerm]);

  useEffect(() => {
    if (userId) {
      loadMyWaivers();
    }
  }, [userId]);

  // Close search results when clicking outside
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      const target = event.target as HTMLElement;
      const formGroup = target.closest('[style*="position: relative"]');
      if (!formGroup) {
        setShowSearchResults(false);
      }
    };

    if (showSearchResults) {
      document.addEventListener('mousedown', handleClickOutside);
      return () => {
        document.removeEventListener('mousedown', handleClickOutside);
      };
    }
  }, [showSearchResults]);

  /**
   * Initialize user ID for waiver requests
   */
  const initializeUser = async () => {
    try {
      const userData = await getCurrentUser();
      if (userData.user?.userId) {
        setUserId(userData.user.userId);
      }
    } catch (error) {
      console.error('Failed to get current user:', error);
    }
  };

  /**
   * Load user's waiver requests
   */
  const loadMyWaivers = async () => {
    if (!userId) return;

    setLoadingWaivers(true);
    try {
      const response = await fetch(`${backendBase}/api/waivers/student/${userId}`, {
        credentials: 'include'
      });

      if (response.ok) {
        const waivers = await response.json();
        setMyWaivers(Array.isArray(waivers) ? waivers : []);
      } else {
        setMyWaivers([]);
      }
    } catch (error) {
      console.error('Error loading waivers:', error);
      setMyWaivers([]);
    } finally {
      setLoadingWaivers(false);
    }
  };

  // TP Used cursor to fetch data from APIs
  const fetchData = async () => {
    try {
      setLoading(true);
      console.log('Starting to fetch data...');

      // Fetch system date from backend (parse as local date to avoid TZ rollback)
      try {
        const systemDateResponse = await fetch('/api/admin/system-date', {
          credentials: 'include',
          cache: 'no-cache',
          headers: {
            'Cache-Control': 'no-cache',
            'Pragma': 'no-cache'
          }
        });

        if (systemDateResponse.ok) {
          const systemDateData = await systemDateResponse.json();
          if (systemDateData.currentDate) {
            const parsed = parseYmdLocal(systemDateData.currentDate);
            setSystemDate(parsed || new Date(systemDateData.currentDate));
            console.log('System date loaded:', systemDateData.currentDate);
          }
        }
      } catch (err) {
        console.warn('Failed to fetch system date, using local date:', err);
      }

      // Fetch available subjects dynamically
      try {
        const subjectsResponse = await fetch('/api/major-minor-declarations/available-subjects', {
          credentials: 'include',
          cache: 'no-cache',
          headers: {
            'Cache-Control': 'no-cache',
            'Pragma': 'no-cache'
          }
        });

        if (subjectsResponse.ok) {
          const subjectsData = await subjectsResponse.json();
          if (subjectsData.success) {
            setAvailableSubjects(subjectsData.subjects || []);
            console.log('Available subjects loaded:', subjectsData.subjects?.length || 0);
          }
        }
      } catch (err) {
        console.warn('Failed to fetch available subjects:', err);
        // Fallback to hardcoded subjects if API fails
        setAvailableSubjects(['BIO', 'PSY', 'CSE', 'ECO', 'AMS', 'POL']);
      }

      // Fetch degree requirements
      try {
        const requirementsResponse = await fetch('/api/declarations/degree-requirements', {
          credentials: 'include',
          cache: 'no-cache',
          headers: {
            'Cache-Control': 'no-cache',
            'Pragma': 'no-cache'
          }
        });

        if (requirementsResponse.ok) {
          const requirementsData = await requirementsResponse.json();
          if (requirementsData.success) {
            // setDegreeRequirements(requirementsData.requirements || []);
            console.log('Degree requirements loaded:', requirementsData.requirements?.length || 0);
          }
        }
      } catch (err) {
        console.warn('Failed to fetch degree requirements:', err);
      }

      // Fetch my declarations
      try {
        const declarationsResponse = await fetch('/api/major-minor-declarations/my-declarations', {
          credentials: 'include',
          cache: 'no-cache',
          headers: {
            'Cache-Control': 'no-cache',
            'Pragma': 'no-cache'
          }
        });

        if (declarationsResponse.ok) {
          const declarationsData = await declarationsResponse.json();
          if (declarationsData.success) {
            setMyDeclarations(declarationsData.declarations || []);
            console.log('My declarations loaded:', declarationsData.declarations?.length || 0);
          }
        }
      } catch (err) {
        console.warn('Failed to fetch my declarations:', err);
      }

      // Fetch current approved majors and minors from User profile
      try {
        const profileResponse = await fetch('/api/profile/me/details', {
          credentials: 'include',
          cache: 'no-cache',
          headers: {
            'Cache-Control': 'no-cache',
            'Pragma': 'no-cache'
          }
        });

        if (profileResponse.ok) {
          const profileData = await profileResponse.json();
          if (profileData.majors) {
            setCurrentApprovedMajors(profileData.majors || []);
            console.log('Current approved majors loaded:', profileData.majors?.length || 0);
          }
          if (profileData.minors) {
            setCurrentApprovedMinors(profileData.minors || []);
            console.log('Current approved minors loaded:', profileData.minors?.length || 0);
          }
        }
      } catch (err) {
        console.warn('Failed to fetch current approved majors/minors:', err);
      }

      // Planned majors are now loaded from localStorage in useEffect

      console.log('Data fetch completed');
    } catch (err) {
      console.error('Error fetching data:', err);
      setError('Failed to load data. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  // TP Used cursor to handle declaration submission
  const handleSubmitDeclaration = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!selectedSubject || !effectiveTerm) {
      setError('Please fill in all required fields.');
      return;
    }

    // Check if user can declare more
    if (!canDeclareMore(selectedDeclarationType)) {
      const maxAllowed = selectedDeclarationType === 'MAJOR' ? 2 : 3;
      const typeName = selectedDeclarationType === 'MAJOR' ? 'majors' : 'minors';
      setError(`You have reached the maximum limit of ${maxAllowed} ${typeName}. You cannot add more ${typeName}.`);
      return;
    }

    // Check if current date is within the declaration period
    const currentDate = systemDate || new Date();
    const isWithinDeclarationPeriod = checkDeclarationPeriod(effectiveTerm, currentDate);

    if (!isWithinDeclarationPeriod) {
      setError('Declaration period has ended. Please check the academic calendar for available terms.');
      return;
    }

    setSubmitting(true);
    setError(null);
    setSuccess(null);

    try {
      const response = await fetch(`${backendBase}/api/declarations/submit`, {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          subjectCode: selectedSubject,
          degreeType: selectedDegreeType,
          declarationType: selectedDeclarationType,
          effectiveTerm: effectiveTerm
        })
      });

      const data = await response.json();

      if (data.success) {
        setSuccess('Declaration submitted successfully!');
        setShowAddForm(false);

        // Reset form
        setSelectedSubject('');
        setSelectedDegreeType('BA');
        setSelectedDeclarationType('MAJOR');
        setEffectiveTerm('');

        // Refresh data
        fetchData();
      } else {
        setError(data.error || 'Failed to submit declaration. Please try again.');
      }

    } catch (err) {
      console.error('Error submitting declaration:', err);
      setError('Failed to submit declaration. Please try again.');
    } finally {
      setSubmitting(false);
    }
  };

  // Handle change declaration submission
  const handleChangeDeclaration = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!selectedSubject || !effectiveTerm || !selectedChangeFromSubject) {
      setError('Please fill in all required fields.');
      return;
    }

    // Check if current date is within the declaration period
    const currentDate = systemDate || new Date();
    const isWithinDeclarationPeriod = checkDeclarationPeriod(effectiveTerm, currentDate);

    if (!isWithinDeclarationPeriod) {
      setError('Declaration period has ended. Please check the academic calendar for available terms.');
      return;
    }

    setSubmitting(true);
    setError(null);
    setSuccess(null);

    try {
      const response = await fetch('/api/major-minor-declarations/submit', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        credentials: 'include',
        body: JSON.stringify({
          subjectCode: selectedSubject,
          fromSubjectCode: selectedChangeFromSubject, // Specify this is a change
          degreeType: selectedDegreeType,
          declarationType: selectedDeclarationType,
          effectiveTerm: effectiveTerm
        })
      });

      const data = await response.json();

      if (data.success) {
        setSuccess('Change request submitted successfully!');
        setShowChangeForm(false);

        // Reset form
        setSelectedSubject('');
        setSelectedChangeFromSubject('');
        setSelectedDegreeType('BA');
        setSelectedDeclarationType('MAJOR');
        setEffectiveTerm('');

        // Refresh data
        fetchData();
      } else {
        setError(data.error || 'Failed to submit change request. Please try again.');
      }

    } catch (err) {
      console.error('Error submitting change request:', err);
      setError('Failed to submit change request. Please try again.');
    } finally {
      setSubmitting(false);
    }
  };

  // TP Used cursor to handle planned major creation
  const handlePlanMajor = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!selectedSubject) {
      setError('Please select a subject.');
      return;
    }

    setSubmitting(true);
    setError(null);
    setSuccess(null);

    try {
      // Create new planned major
      const newPlannedMajor: PlannedMajor = {
        plannedMajorId: Date.now(), // Use timestamp as ID
        subjectCode: selectedSubject,
        degreeType: selectedDegreeType,
        plannedAt: new Date().toISOString(),
        isActive: true
      };

      // Add to existing planned majors
      const updatedPlannedMajors = [...myPlannedMajors, newPlannedMajor];
      setMyPlannedMajors(updatedPlannedMajors);
      savePlannedMajorsToStorage(updatedPlannedMajors);

      setSuccess('Major planned successfully!');
      setShowPlannedMajorForm(false);

      // Reset form
      setSelectedSubject('');
      setSelectedDegreeType('BA');

    } catch (err) {
      console.error('Error planning major:', err);
      setError('Failed to plan major. Please try again.');
    } finally {
      setSubmitting(false);
    }
  };

  // TP Used cursor to handle planned major removal
  const handleUnplanMajor = async (subjectCode: string, degreeType: string) => {
    if (!confirm(`Are you sure you want to unplan ${subjectCode} ${degreeType}?`)) {
      return;
    }

    setError(null);
    setSuccess(null);

    try {
      // Remove planned major from list
      const updatedPlannedMajors = myPlannedMajors.filter(
        pm => !(pm.subjectCode === subjectCode && pm.degreeType === degreeType)
      );

      setMyPlannedMajors(updatedPlannedMajors);
      savePlannedMajorsToStorage(updatedPlannedMajors);

      setSuccess('Major unplanned successfully!');

    } catch (err) {
      console.error('Error unplanning major:', err);
      setError('Failed to unplan major. Please try again.');
    }
  };

  // TP Used cursor to handle declaration withdrawal
  const handleWithdrawDeclaration = async (declarationId: number) => {
    if (!window.confirm('Are you sure you want to withdraw this declaration?')) {
      return;
    }

    try {
      const response = await fetch(`${backendBase}/api/declarations/${declarationId}/withdraw`, {
        method: 'POST',
        credentials: 'include'
      });

      const data = await response.json();

      if (data.success) {
        setSuccess('Declaration withdrawn successfully!');
        // Refresh data
        fetchData();
      } else {
        setError(data.error || 'Failed to withdraw declaration. Please try again.');
      }

    } catch (err) {
      console.error('Error withdrawing declaration:', err);
      setError('Failed to withdraw declaration. Please try again.');
    }
  };

  // Load available degree types for selected subject
  // TP Used cursor to load degree types for selected subject
  const loadDegreeTypesForSubject = async (subjectCode: string) => {
    if (!subjectCode) {
      setAvailableDegreeTypes([]);
      return;
    }

    try {
      const response = await fetch(`/api/major-minor-declarations/available-degree-types/${subjectCode}`, {
        credentials: 'include',
        cache: 'no-cache',
        headers: {
          'Cache-Control': 'no-cache',
          'Pragma': 'no-cache'
        }
      });

      if (response.ok) {
        const data = await response.json();
        if (data.success) {
          const degreeTypes = data.degreeTypes.map((dt: any) => dt.toString());
          setAvailableDegreeTypes(degreeTypes);

          // Set the first available degree type
          if (degreeTypes.length > 0) {
            setSelectedDegreeType(degreeTypes[0] as 'BA' | 'BS' | 'MINOR');
          }
        }
      }
    } catch (err) {
      console.warn('Failed to fetch degree types for subject:', err);
      // Fallback to hardcoded mapping
      const subjectDegreeMap: Record<string, string[]> = {
        'CSE': ['BS'],
        'PSY': ['BA'],
        'BIO': ['BA'],
        'AMS': ['BS'],
        'POL': ['BA'],
        'ECO': ['BA']
      };
      const fallbackTypes = subjectDegreeMap[subjectCode] || [];
      setAvailableDegreeTypes(fallbackTypes);
      if (fallbackTypes.length > 0) {
        setSelectedDegreeType(fallbackTypes[0] as 'BA' | 'BS' | 'MINOR');
      }
    }
  };

  // Load course requirements for selected subject and degree type
  // TP Used cursor to load course requirements for subject and degree type
  const loadCourseRequirements = async (subjectCode: string, degreeType: string) => {
    if (!subjectCode || !degreeType) {
      setCourseRequirements(null);
      return;
    }

    try {
      const response = await fetch(`/api/major-minor-declarations/course-requirements/${subjectCode}/${degreeType}`, {
        credentials: 'include',
        cache: 'no-cache',
        headers: {
          'Cache-Control': 'no-cache',
          'Pragma': 'no-cache'
        }
      });

      if (response.ok) {
        const data = await response.json();
        if (data.success) {
          setCourseRequirements(data.requirements);
        }
      }
    } catch (err) {
      console.warn('Failed to fetch course requirements:', err);
      setCourseRequirements(null);
    }
  };

  /**
   * Load enrolled classes and available classes for waiver request
   */
  const loadClassesForWaiver = async (semester?: string, year?: string) => {
    if (!userId || !semester || !year) {
      return
    }

    setLoadingClasses(true)
    try {
      // Load enrolled classes
      const enrolledResponse = await fetch(`${backendBase}/api/roster/student/${userId}`, {
        credentials: 'include'
      })

      if (enrolledResponse.ok) {
        const enrolledData = await enrolledResponse.json()
        const normalizedSemester = semester.charAt(0).toUpperCase() + semester.slice(1).toLowerCase()
        const yearNum = parseInt(year)

        const filteredEnrolled = Array.isArray(enrolledData) ? enrolledData.filter((cls: any) => {
          const clsYear = typeof cls.year === 'number' ? cls.year : parseInt(String(cls.year))
          const clsSemester = cls.semester ?
            (String(cls.semester).charAt(0).toUpperCase() + String(cls.semester).slice(1).toLowerCase()) : null
          return clsYear === yearNum && clsSemester === normalizedSemester
        }) : []

        setEnrolledClasses(filteredEnrolled)
      }

      // Load available classes for the term
      const classesResponse = await fetch(`${backendBase}/api/classes/by-term?semester=${semester}&year=${year}`, {
        credentials: 'include'
      })

      if (classesResponse.ok) {
        const classesData = await classesResponse.json()
        // setAvailableClasses(Array.isArray(classesData) ? classesData : [])
      } else {
        // setAvailableClasses([])
      }
    } catch (error) {
      console.error('Error loading classes for waiver:', error)
      setEnrolledClasses([])
      // setAvailableClasses([])
    } finally {
      setLoadingClasses(false)
    }
  }

  /**
   * Search for conflicting classes
   */
  const handleSearchConflictingClass = async (searchTerm: string) => {
    if (!searchTerm.trim() || !waiverTerm.semester || !waiverTerm.year) {
      setSearchResults([]);
      setShowSearchResults(false);
      return;
    }

    setSearching(true);
    try {
      const response = await fetch(`${backendBase}/api/classes/search?q=${encodeURIComponent(searchTerm)}`, {
        credentials: 'include'
      });

      if (response.ok) {
        const data = await response.json();
        // Filter by selected term
        const normalizedSemester = waiverTerm.semester.charAt(0).toUpperCase() + waiverTerm.semester.slice(1).toLowerCase();
        const yearNum = parseInt(waiverTerm.year);

        const filtered = Array.isArray(data) ? data.filter((cls: any) => {
          const clsYear = typeof cls.year === 'number' ? cls.year : parseInt(String(cls.year));
          const clsSemester = cls.semester ?
            (String(cls.semester).charAt(0).toUpperCase() + String(cls.semester).slice(1).toLowerCase()) : null;
          return clsYear === yearNum && clsSemester === normalizedSemester;
        }) : [];

        setSearchResults(filtered);
        setShowSearchResults(true);
      } else {
        setSearchResults([]);
        setShowSearchResults(false);
      }
    } catch (error) {
      console.error('Error searching classes:', error);
      setSearchResults([]);
      setShowSearchResults(false);
    } finally {
      setSearching(false);
    }
  };

  /**
   * Debounce search for conflicting classes
   */
  useEffect(() => {
    if (!conflictingClassSearch.trim() || !waiverTerm.semester || !waiverTerm.year) {
      setSearchResults([]);
      setShowSearchResults(false);
      return;
    }

    const timeoutId = setTimeout(() => {
      handleSearchConflictingClass(conflictingClassSearch);
    }, 300);

    return () => clearTimeout(timeoutId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [conflictingClassSearch, waiverTerm.semester, waiverTerm.year]);

  /**
   * Select a conflicting class from search results
   */
  const handleSelectConflictingClass = (classItem: any) => {
    setSelectedConflictingClass(classItem.classId);
    setConflictingClassSearch(`${classItem.department}${classItem.courseNum}.${classItem.section} - ${classItem.title}`);
    setShowSearchResults(false);
  };

  /**
   * Handle waiver request submission
   */
  const handleSubmitWaiver = async (e: React.FormEvent) => {
    e.preventDefault()

    if (!userId || !selectedEnrolledClass || !selectedConflictingClass || !waiverReason.trim()) {
      setError('Please fill in all required fields.')
      return
    }

    setSubmittingWaiver(true)
    setError(null)
    setSuccess(null)

    try {
      const response = await fetch(`${backendBase}/api/waivers`, {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          studentId: userId,
          enrolledClassId: selectedEnrolledClass,
          conflictingClassId: selectedConflictingClass,
          reason: waiverReason.trim()
        })
      })

      const data = await response.json()

      if (data.success || response.ok) {
        setSuccess('Override request submitted successfully! You will be notified when it is approved.')
        setShowWaiverForm(false)
        setSelectedEnrolledClass(null)
        setSelectedConflictingClass(null)
        setWaiverReason('')
        setWaiverTerm({ semester: '', year: '' })
        setConflictingClassSearch('')
        setSearchResults([])
        setShowSearchResults(false)
        // Reload waivers to show the new request
        loadMyWaivers()
      } else {
        if (data.message && data.message.includes('already exists')) {
          setError('A waiver request for these classes already exists.')
        } else {
          setError(data.message || 'Failed to submit override request. Please try again.')
        }
      }
    } catch (err) {
      console.error('Error submitting waiver request:', err)
      setError('Failed to submit override request. Please try again.')
    } finally {
      setSubmittingWaiver(false)
    }
  }

  // TP: Used cursor to check declaration period validity using academic calendar dates (registrar-style)
  // begin from previous term section, end from current term section (handled by backend)
  const checkDeclarationPeriod = (term: string, currentDate: Date): boolean => {
    // Normalize currentDate to midnight for date-only comparison
    const currentDateOnly = new Date(currentDate.getFullYear(), currentDate.getMonth(), currentDate.getDate());

    let startDate: Date | null = null;
    let endDate: Date | null = null;

    // Helper to parse date string safely (avoid timezone issues)
    const parseDateString = (dateStr: string): Date | null => {
      if (!dateStr) return null;
      // Parse "YYYY-MM-DD" format and create local date
      const parts = dateStr.split('-');
      if (parts.length === 3) {
        const year = parseInt(parts[0], 10);
        const month = parseInt(parts[1], 10) - 1; // Month is 0-indexed
        const day = parseInt(parts[2], 10);
        return new Date(year, month, day);
      }
      return new Date(dateStr);
    };

    // First, try to get dates from selectedTermDeclarationDates (from API)
    if (selectedTermDeclarationDates?.beginDate) {
      startDate = parseDateString(selectedTermDeclarationDates.beginDate);
    }
    if (selectedTermDeclarationDates?.endDate) {
      endDate = parseDateString(selectedTermDeclarationDates.endDate);
    }

    // If not available from selectedTermDeclarationDates, try from available terms
    // Parse term to match correctly (handle "SPRING 2026", "Spring 2026", etc.)
    if ((!startDate || !endDate) && availableTerms.length > 0) {
      // Try exact match first
      let termInfo = availableTerms.find(t => t.value === term);

      // If not found, parse term and match by semester and year
      if (!termInfo) {
        const parts = term.split(' ');
        if (parts.length >= 2) {
          const semester = parts[0].charAt(0) + parts[0].slice(1).toLowerCase();
          const year = parseInt(parts[1]);
          termInfo = availableTerms.find(t =>
            t.semester.toLowerCase() === semester.toLowerCase() && t.year === year
          );
        }
      }

      if (termInfo) {
        if (!startDate && termInfo.declarationBeginDate) {
          startDate = parseDateString(termInfo.declarationBeginDate);
        }
        if (!endDate && termInfo.declarationEndDate) {
          endDate = parseDateString(termInfo.declarationEndDate);
        }
      }
    }

    if (!startDate && !endDate) {
      console.warn('Declaration period dates not available for term:', term, {
        selectedTermDeclarationDates,
        availableTerms: availableTerms.map(t => ({
          value: t.value,
          semester: t.semester,
          year: t.year,
          beginDate: t.declarationBeginDate,
          endDate: t.declarationEndDate
        }))
      });
      return false;
    }

    // Normalize dates for comparison (nullable) - use local date components
    const startDateOnly = startDate ? new Date(startDate.getFullYear(), startDate.getMonth(), startDate.getDate()) : null;
    const endDateOnly = endDate ? new Date(endDate.getFullYear(), endDate.getMonth(), endDate.getDate()) : null;

    // One-sided bounds allowed per registrar-style edge cases
    const afterStart = startDateOnly ? currentDateOnly >= startDateOnly : true;
    const beforeEnd = endDateOnly ? currentDateOnly <= endDateOnly : true;
    const result = afterStart && beforeEnd;

    console.log('Checking declaration period:', {
      term,
      currentDate: currentDateOnly.toISOString().split('T')[0],
      startDate: startDateOnly ? startDateOnly.toISOString().split('T')[0] : null,
      endDate: endDateOnly ? endDateOnly.toISOString().split('T')[0] : null,
      afterStart,
      beforeEnd,
      result,
      selectedTermDeclarationDates,
      availableTermsCount: availableTerms.length,
      matchedTerm: availableTerms.find(t => {
        const parts = term.split(' ');
        if (parts.length >= 2) {
          const semester = parts[0].charAt(0) + parts[0].slice(1).toLowerCase();
          const year = parseInt(parts[1]);
          return t.semester.toLowerCase() === semester.toLowerCase() && t.year === year;
        }
        return t.value === term;
      })
    });
    return result;
  };

  const getStatusColor = (status: string) => {
    if (!status) return 'status-pending';
    switch (status.toUpperCase()) {
      case 'APPROVED': return 'status-approved';
      case 'PENDING': return 'status-pending';
      case 'REJECTED': return 'status-rejected';
      case 'WITHDRAWN': return 'status-withdrawn';
      default: return 'status-pending';
    }
  };

  const getStatusText = (status: string) => {
    switch (status) {
      case 'APPROVED': return 'Approved';
      case 'PENDING': return 'Pending';
      case 'REJECTED': return 'Rejected';
      case 'WITHDRAWN': return 'Withdrawn';
      default: return 'Unknown';
    }
  };

  const getDegreeTypeDisplayName = (degreeType: string | null | undefined, declarationType: string): string => {
    // For MINOR declaration type, always return "Minor"
    if (declarationType === 'MINOR') {
      return 'Minor';
    }
    
    // If degreeType is provided, convert to full name
    if (degreeType) {
      switch (degreeType.toUpperCase()) {
        case 'BA': return 'Bachelor of Arts';
        case 'BS': return 'Bachelor of Science';
        case 'MINOR': return 'Minor';
        default: return degreeType;
      }
    }
    
    // For MAJOR without degreeType, default to "BS" (like Profile.tsx)
    if (declarationType === 'MAJOR') {
      return 'Bachelor of Science';
    }
    
    return 'N/A';
  };

  const getCurrentDeclarationsCount = (declarationType: 'MAJOR' | 'MINOR') => {
    return myDeclarations.filter(decl =>
      decl.declarationType === declarationType &&
      (decl.status === 'APPROVED' || decl.status === 'PENDING')
    ).length;
  };

  const canDeclareMore = (declarationType: 'MAJOR' | 'MINOR') => {
    const currentCount = getCurrentDeclarationsCount(declarationType);
    const maxAllowed = declarationType === 'MAJOR' ? 2 : 3;
    return currentCount < maxAllowed;
  };



  return (
    <div className="major-minor-declaration">
      <div className="page-header">
        <h1>Major/Minor Declaration</h1>
      </div>

      {/* Policy Information */}
      <div className="policy-section">
        <h2>Declaration Policies</h2>
        <div className="policy-content">
          <p>
            Students can declare up to 2 majors and 3 minors, consistent with the policies in the Undergraduate Bulletin,
            especially the Selecting an Academic Program section. Requests to declare a major or minor effective in a
            particular term can be submitted between the dates specified in the university's academic calendars and
            require approval from a department-level academic advisor.
          </p>
          <p>
            <strong>Declaration Periods:</strong><br />
            {availableTerms.length > 0 ? (
              availableTerms.map(term => (
                <span key={term.value}>
                  • <strong>{term.displayName}:</strong> {
                    term.declarationBeginDate && term.declarationEndDate
                      ? `${new Date(term.declarationBeginDate).toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' })} - ${new Date(term.declarationEndDate).toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' })}`
                      : 'Dates TBD'
                  }<br />
                </span>
              ))
            ) : (
              <span>Dates will appear once the academic calendar is loaded.</span>
            )}
          </p>
          <p>
            Restricted majors and minors have course requirements or GPA requirements for admission;
            department-level academic advisors can waive these requirements.
          </p>
          <p>
            <strong>CEAS Transfer Policy:</strong><br />
            Requests to transfer into CEAS (College of Engineering and Applied Sciences) by declaring an AOI or a major
            in CEAS without currently being an AOI or a major in CEAS requires approval of a CEAS academic advisor.<br />
            <strong>CEAS Subjects:</strong> AMS, BME, CME, CIV, ECE, CSE, ESE, ESG, ISE, MEC, TSM
          </p>
        </div>
      </div>

      {/* Error and Success Messages */}
      {error && (
        <div className="alert alert-error">
          <div className="alert-content">
            <div className="alert-text">{error}</div>
          </div>
        </div>
      )}

      {success && (
        <div className="alert alert-success">
          <div className="alert-content">
            <div className="alert-text">{success}</div>
          </div>
        </div>
      )}

      {/* Declaration Forms */}
      <div className="declaration-forms-section">
        <h2>Declaration Forms</h2>
        <p className="section-description">
          Manage your major/minor declarations: change existing declarations or add new ones.
        </p>

        <div style={{ display: 'flex', gap: '20px', marginTop: '10px' }}>
          <div className="current-info">
            <strong>My Current Majors:</strong> {currentApprovedMajors.length > 0 ? currentApprovedMajors.join(', ') : 'None'}
          </div>
          <div className="current-info">
            <strong>My Current Minors:</strong> {currentApprovedMinors.length > 0 ? currentApprovedMinors.join(', ') : 'None'}
          </div>
        </div>
      </div>

      {/* Change Declaration Form */}
      {(currentApprovedMajors.length > 0 || currentApprovedMinors.length > 0) && (
        <div className="declaration-form-section">
          <div className="section-header">
            <h2>Change Declaration</h2>
            <button
              className="toggle-form-button"
              onClick={() => setShowChangeForm(!showChangeForm)}
            >
              {showChangeForm ? 'Cancel' : 'Change Declaration'}
            </button>
          </div>

          {showChangeForm && (
            <form onSubmit={handleChangeDeclaration} className="declaration-form">
              <div className="form-row">
                <div className="form-group">
                  <label>Term:</label>
                  <select
                    value={effectiveTerm}
                    onChange={(e) => setEffectiveTerm(e.target.value)}
                    required
                  >
                    <option value="">Select Term</option>
                    {getAvailableTerms().map(term => (
                      <option key={term.value} value={term.value}>
                        {term.label}
                      </option>
                    ))}
                  </select>
                  {selectedTermDeclarationDates && selectedTermDeclarationDates.beginDate && selectedTermDeclarationDates.endDate && (
                    <small className="form-hint" style={{ color: '#666', fontSize: '0.875rem', marginTop: '0.25rem', display: 'block' }}>
                      Declaration period: {new Date(selectedTermDeclarationDates.beginDate).toLocaleDateString()} - {new Date(selectedTermDeclarationDates.endDate).toLocaleDateString()}
                    </small>
                  )}
                </div>

                <div className="form-group">
                  <label>Declaration Type:</label>
                  <select
                    value={selectedDeclarationType}
                    onChange={(e) => {
                      setSelectedDeclarationType(e.target.value as 'MAJOR' | 'MINOR');
                      setSelectedChangeFromSubject('');
                    }}
                    required
                  >
                    <option value="MAJOR">Major</option>
                    <option value="MINOR">Minor</option>
                  </select>
                </div>
              </div>

              <div className="form-row">
                <div className="form-group">
                  <label>Change From:</label>
                  <select
                    value={selectedChangeFromSubject}
                    onChange={(e) => setSelectedChangeFromSubject(e.target.value)}
                    required
                  >
                    <option value="">Select Current {selectedDeclarationType}</option>
                    {(selectedDeclarationType === 'MAJOR' ? currentApprovedMajors : currentApprovedMinors).map(subject => (
                      <option key={subject} value={subject}>{subject}</option>
                    ))}
                  </select>
                </div>

                <div className="form-group">
                  <label>Change To:</label>
                  <select
                    value={selectedSubject}
                    onChange={(e) => {
                      setSelectedSubject(e.target.value);
                      if (e.target.value) {
                        loadDegreeTypesForSubject(e.target.value);
                      }
                    }}
                    required
                  >
                    <option value="">Select New {selectedDeclarationType}</option>
                    {availableSubjects
                      .filter(subject => selectedChangeFromSubject !== subject)
                      .map(subject => (
                        <option key={subject} value={subject}>{subject}</option>
                      ))}
                  </select>
                </div>
              </div>

              {selectedSubject && availableDegreeTypes.length > 0 && (
                <div className="form-row">
                  <div className="form-group">
                    <label>Degree Type:</label>
                    <select
                      value={selectedDegreeType}
                      onChange={(e) => {
                        setSelectedDegreeType(e.target.value as 'BA' | 'BS' | 'MINOR');
                        if (selectedSubject) {
                          loadCourseRequirements(selectedSubject, e.target.value);
                        }
                      }}
                      required
                    >
                      {availableDegreeTypes.map(degreeType => (
                        <option key={degreeType} value={degreeType}>
                          {degreeType === 'MINOR' ? 'Minor' : degreeType}
                        </option>
                      ))}
                    </select>
                  </div>
                </div>
              )}

              <div className="form-actions">
                <button
                  type="submit"
                  className="submit-button"
                  disabled={submitting}
                >
                  {submitting ? 'Submitting...' : 'Submit Change Request'}
                </button>
              </div>
            </form>
          )}
        </div>
      )}

      {/* Add Declaration Form */}
      <div className="declaration-form-section">
        <div className="section-header">
          <h2>Add New Declaration</h2>
          <button
            className="toggle-form-button"
            onClick={() => setShowAddForm(!showAddForm)}
          >
            {showAddForm ? 'Cancel' : 'Add Declaration'}
          </button>
        </div>

        {/* Show message if no terms are available */}
        {getAvailableTerms().length === 0 && (
          <div className="alert alert-warning">
            <div className="alert-content">
              <div className="alert-text">
                <strong>No declaration periods are currently open.</strong> Please check the academic calendar for upcoming declaration periods.
              </div>
            </div>
          </div>
        )}

        {showAddForm && (
          <form onSubmit={handleSubmitDeclaration} className="declaration-form">
            <div className="form-row">
              <div className="form-group">
                <label>Term:</label>
                <select
                  value={effectiveTerm}
                  onChange={(e) => setEffectiveTerm(e.target.value)}
                  required
                >
                  <option value="">Select Term</option>
                  {getAvailableTerms().map(term => (
                    <option key={term.value} value={term.value}>
                      {term.label}
                    </option>
                  ))}
                </select>
                {selectedTermDeclarationDates && selectedTermDeclarationDates.beginDate && selectedTermDeclarationDates.endDate && (
                  <small className="form-hint" style={{ color: '#666', fontSize: '0.875rem', marginTop: '0.25rem', display: 'block' }}>
                    Declaration period: {new Date(selectedTermDeclarationDates.beginDate).toLocaleDateString()} - {new Date(selectedTermDeclarationDates.endDate).toLocaleDateString()}
                  </small>
                )}
              </div>

              <div className="form-group">
                <label>Subject:</label>
                <select
                  value={selectedSubject}
                  onChange={(e) => {
                    setSelectedSubject(e.target.value);
                    loadDegreeTypesForSubject(e.target.value);
                  }}
                  required
                >
                  <option value="">Select Subject</option>
                  {availableSubjects.map(subject => (
                    <option key={subject} value={subject}>{subject}</option>
                  ))}
                </select>
              </div>
            </div>

            <div className="form-row">
              <div className="form-group">
                <label>Declaration Type:</label>
                <select
                  value={selectedDeclarationType}
                  onChange={(e) => setSelectedDeclarationType(e.target.value as 'MAJOR' | 'MINOR')}
                  required
                >
                  <option value="MAJOR" disabled={!canDeclareMore('MAJOR')}>
                    Major {!canDeclareMore('MAJOR') ? '(Maximum reached)' : ''}
                  </option>
                  <option value="MINOR" disabled={!canDeclareMore('MINOR')}>
                    Minor {!canDeclareMore('MINOR') ? '(Maximum reached)' : ''}
                  </option>
                </select>
              </div>

              {selectedSubject && availableDegreeTypes.length > 0 && (
                <div className="form-group">
                  <label>Degree Type:</label>
                  <select
                    value={selectedDegreeType}
                    onChange={(e) => {
                      setSelectedDegreeType(e.target.value as 'BA' | 'BS' | 'MINOR');
                      loadCourseRequirements(selectedSubject, e.target.value);
                    }}
                    required
                  >
                    {availableDegreeTypes.map(degreeType => (
                      <option key={degreeType} value={degreeType}>
                        {degreeType === 'MINOR' ? 'Minor' : degreeType}
                      </option>
                    ))}
                  </select>
                </div>
              )}
            </div>

            <div className="form-actions">
              <button
                type="submit"
                className="submit-button"
                disabled={submitting || (!canDeclareMore('MAJOR') && !canDeclareMore('MINOR'))}
              >
                {submitting ? 'Submitting...' : 'Submit Declaration'}
              </button>
            </div>
          </form>
        )}
      </div>

      {/* Planned Majors Section */}
      <div className="planned-majors-section">
        <div className="section-header">
          <h2>Planned Majors</h2>
          <button
            className="toggle-form-button"
            onClick={() => setShowPlannedMajorForm(!showPlannedMajorForm)}
          >
            {showPlannedMajorForm ? 'Cancel' : 'Plan New Major'}
          </button>
        </div>

        <p className="section-description">
          Plan majors to track admission and degree requirements. This is different from declaring a major -
          planning just shows you the status of requirements without formal declaration.
        </p>

        {showPlannedMajorForm && (
          <form onSubmit={handlePlanMajor} className="planned-major-form">
            <div className="form-row">
              <div className="form-group">
                <label>Subject:</label>
                <select
                  value={selectedSubject}
                  onChange={(e) => {
                    setSelectedSubject(e.target.value);
                    if (e.target.value) {
                      loadDegreeTypesForSubject(e.target.value);
                    }
                  }}
                  required
                >
                  <option value="">Select Subject</option>
                  {availableSubjects.map(subject => (
                    <option key={subject} value={subject}>{subject}</option>
                  ))}
                </select>
              </div>

              {selectedSubject && availableDegreeTypes.length > 0 && (
                <div className="form-group">
                  <label>Degree Type:</label>
                  <select
                    value={selectedDegreeType}
                    onChange={(e) => {
                      setSelectedDegreeType(e.target.value as 'BA' | 'BS' | 'MINOR');
                      if (selectedSubject) {
                        loadCourseRequirements(selectedSubject, e.target.value);
                      }
                    }}
                    required
                  >
                    {availableDegreeTypes.map(degreeType => (
                      <option key={degreeType} value={degreeType}>
                        {degreeType === 'MINOR' ? 'Minor' : degreeType}
                      </option>
                    ))}
                  </select>
                </div>
              )}
            </div>

            <div className="form-actions">
              <button
                type="submit"
                className="submit-button"
                disabled={submitting}
              >
                {submitting ? 'Planning...' : 'Plan Major'}
              </button>
            </div>
          </form>
        )}

        {/* Planned Majors List */}
        <div className="planned-majors-list">
          {myPlannedMajors.length === 0 ? (
            <div className="empty-state">
              <p>No planned majors yet. Plan a major to track admission and degree requirements.</p>
            </div>
          ) : (
            <div className="planned-majors-grid">
              {myPlannedMajors.map((plannedMajor) => (
                <div key={plannedMajor.plannedMajorId} className="planned-major-card">
                  <div className="planned-major-header">
                    <h3>{plannedMajor.subjectCode} {plannedMajor.degreeType}</h3>
                    <button
                      className="unplan-button"
                      onClick={() => handleUnplanMajor(plannedMajor.subjectCode, plannedMajor.degreeType)}
                    >
                      Unplan
                    </button>
                  </div>

                  <div className="planned-major-details">
                    <p><strong>Planned:</strong> {new Date(plannedMajor.plannedAt).toLocaleDateString()}</p>

                    {plannedMajor.degreeRequirement && (
                      <div className="requirement-info">
                        <p><strong>Total Credits:</strong> {plannedMajor.degreeRequirement.totalCreditsRequired}</p>
                        <p><strong>Restricted:</strong> {plannedMajor.degreeRequirement.isRestricted ? 'Yes' : 'No'}</p>
                        {plannedMajor.degreeRequirement.gpaRequirement && (
                          <p><strong>GPA Requirement:</strong> {plannedMajor.degreeRequirement.gpaRequirement}</p>
                        )}

                        {plannedMajor.degreeRequirement.admissionRequirements && (
                          <div className="admission-info">
                            <h4>Admission Requirements:</h4>
                            <p>{plannedMajor.degreeRequirement.admissionRequirements}</p>
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Course Requirements Display */}
      {courseRequirements && (
        <div className="course-requirements-section">
          <h3>Course Requirements for {selectedSubject} {selectedDegreeType}</h3>
          <div className="requirements-details">
            <div className="requirement-summary">
              <p><strong>Total Credits Required:</strong> {courseRequirements.totalCreditsRequired}</p>
              <p><strong>Restricted Major:</strong> {courseRequirements.isRestricted ? 'Yes' : 'No'}</p>
              {courseRequirements.gpaRequirement && (
                <p><strong>GPA Requirement:</strong> {courseRequirements.gpaRequirement}</p>
              )}
            </div>

            {courseRequirements.admissionRequirements && (
              <div className="admission-requirements">
                <h4>Admission Requirements:</h4>
                <ul>
                  {Array.isArray(courseRequirements.admissionRequirements)
                    ? courseRequirements.admissionRequirements.map((req: string, index: number) => (
                      <li key={index}>{req}</li>
                    ))
                    : <li>{courseRequirements.admissionRequirements}</li>
                  }
                </ul>
              </div>
            )}

            {courseRequirements.courseRequirements && (
              <div className="course-requirements">
                <h4>Course Requirements:</h4>
                {courseRequirements.courseRequirements.required_courses && (
                  <div className="required-courses">
                    <h5>Required Courses:</h5>
                    <ul>
                      {courseRequirements.courseRequirements.required_courses.map((course: string, index: number) => (
                        <li key={index}>{course}</li>
                      ))}
                    </ul>
                  </div>
                )}

                {courseRequirements.courseRequirements.elective_courses && (
                  <div className="elective-courses">
                    <h5>Elective Courses:</h5>
                    <p><strong>Credits Required:</strong> {courseRequirements.courseRequirements.elective_courses.credits_required}</p>
                    <ul>
                      {courseRequirements.courseRequirements.elective_courses.courses.map((course: string, index: number) => (
                        <li key={index}>{course}</li>
                      ))}
                    </ul>
                  </div>
                )}

                {courseRequirements.courseRequirements.concentration_requirements && (
                  <div className="concentration-requirements">
                    <h5>Concentration Requirements:</h5>
                    <p><strong>Credits Required:</strong> {courseRequirements.courseRequirements.concentration_requirements.credits_required}</p>
                    {Object.entries(courseRequirements.courseRequirements.concentration_requirements.concentrations).map(([concentration, courses]: [string, any]) => (
                      <div key={concentration} className="concentration">
                        <h6>{concentration.replace(/_/g, ' ').toUpperCase()}:</h6>
                        <ul>
                          {courses.courses.map((course: string, index: number) => (
                            <li key={index}>{course}</li>
                          ))}
                        </ul>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            {courseRequirements.sbcRequirements && (
              <div className="sbc-requirements">
                <h4>SBC Requirements:</h4>
                <div className="sbc-content">
                  {typeof courseRequirements.sbcRequirements === 'string' ? (
                    <div>
                      {(() => {
                        try {
                          const parsed = JSON.parse(courseRequirements.sbcRequirements);
                          if (Array.isArray(parsed)) {
                            return (
                              <ul>
                                {parsed.map((item: any, index: number) => (
                                  <li key={index}>{item}</li>
                                ))}
                              </ul>
                            );
                          } else if (typeof parsed === 'object') {
                            return (
                              <div className="sbc-details">
                                {Object.entries(parsed).map(([category, details]: [string, any]) => (
                                  <div key={category} className="sbc-category">
                                    <h5>{category.replace(/_/g, ' ').toUpperCase()}:</h5>
                                    {typeof details === 'string' ? (
                                      <p>{details}</p>
                                    ) : (
                                      <ul>
                                        {Array.isArray(details) ? details.map((item: any, index: number) => (
                                          <li key={index}>{item}</li>
                                        )) : Object.entries(details).map(([key, value]: [string, any]) => (
                                          <li key={key}><strong>{key}:</strong> {value}</li>
                                        ))}
                                      </ul>
                                    )}
                                  </div>
                                ))}
                              </div>
                            );
                          } else {
                            return <p>{parsed}</p>;
                          }
                        } catch (e) {
                          return <p>{courseRequirements.sbcRequirements}</p>;
                        }
                      })()}
                    </div>
                  ) : (
                    <div className="sbc-details">
                      {Object.entries(courseRequirements.sbcRequirements).map(([category, details]: [string, any]) => (
                        <div key={category} className="sbc-category">
                          <h5>{category.replace(/_/g, ' ').toUpperCase()}:</h5>
                          {typeof details === 'string' ? (
                            <p>{details}</p>
                          ) : (
                            <ul>
                              {Array.isArray(details) ? details.map((item: any, index: number) => (
                                <li key={index}>{item}</li>
                              )) : Object.entries(details).map(([key, value]: [string, any]) => (
                                <li key={key}><strong>{key}:</strong> {value}</li>
                              ))}
                            </ul>
                          )}
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* My Declarations */}
      <div className="my-declarations-section">
        <h2>My Declarations</h2>
        {myDeclarations.length === 0 ? (
          <div className="no-declarations">
            <p>You haven't submitted any declarations yet.</p>
          </div>
        ) : (
          <div className="declarations-table-container">
            <table className="declarations-table">
              <thead>
                <tr>
                  <th>Subject</th>
                  <th>Type</th>
                  <th>Degree</th>
                  <th>Effective Term</th>
                  <th>Status</th>
                  <th>Submitted</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {myDeclarations.map(declaration => (
                  <tr key={declaration.declarationId}>
                    <td className="subject-cell">{declaration.subjectCode}</td>
                    <td className="type-cell">{declaration.declarationType}</td>
                    <td className="degree-cell">
                      {getDegreeTypeDisplayName(declaration.degreeType, declaration.declarationType)}
                    </td>
                    <td className="term-cell">{declaration.effectiveTerm}</td>
                    <td className="status-cell">
                      <span className={`status-badge ${getStatusColor(declaration.status)}`}>
                        {getStatusText(declaration.status)}
                      </span>
                    </td>
                    <td className="date-cell">
                      {new Date(declaration.submittedAt).toLocaleDateString()}
                    </td>
                    <td className="actions-cell">
                      {declaration.status === 'PENDING' && (
                        <button
                          className="withdraw-button"
                          onClick={() => handleWithdrawDeclaration(declaration.declarationId)}
                        >
                          Withdraw
                        </button>
                      )}
                      {declaration.status === 'REJECTED' && declaration.advisorComments && (
                        <div className="rejection-reason">
                          <strong>Reason:</strong> {declaration.advisorComments}
                        </div>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Class Override Application */}
      <div className="my-declarations-section">
        <div className="section-header">
          <h2>Class Override Application</h2>
          <button
            className="toggle-form-button"
            onClick={() => {
              setShowWaiverForm(!showWaiverForm);
              if (!showWaiverForm) {
                // Reset form when opening
                setSelectedEnrolledClass(null);
                setSelectedConflictingClass(null);
                setWaiverReason('');
                setWaiverTerm({ semester: '', year: '' });
                setConflictingClassSearch('');
                setSearchResults([]);
                setShowSearchResults(false);
              }
            }}
          >
            {showWaiverForm ? 'Cancel' : 'Apply for Override'}
          </button>
        </div>

        {/* My Waiver Requests */}
        {loadingWaivers ? (
          <div className="loading-message">Loading waiver requests...</div>
        ) : myWaivers.length === 0 ? (
          <div className="no-declarations">
            <p>You haven't submitted any override requests yet.</p>
          </div>
        ) : (
          <div className="declarations-table-container" style={{ marginTop: '1.5rem', marginBottom: '1.5rem' }}>
            <h3 style={{ marginBottom: '1rem', fontSize: '1.2rem', color: '#2c3e50' }}>My Override Requests</h3>
            <table className="declarations-table">
              <thead>
                <tr>
                  <th>Enrolled Class</th>
                  <th>Conflicting Class</th>
                  <th>Status</th>
                  <th>Submitted</th>
                  <th>Approvals</th>
                </tr>
              </thead>
              <tbody>
                {myWaivers.map((waiver: any) => (
                  <tr key={waiver.waiverId}>
                    <td className="subject-cell">
                      {waiver.enrolledClass?.department}{waiver.enrolledClass?.courseNum}.{waiver.enrolledClass?.section}
                      <br />
                      <small style={{ color: '#666' }}>{waiver.enrolledClass?.title}</small>
                    </td>
                    <td className="subject-cell">
                      {waiver.conflictingClass?.department}{waiver.conflictingClass?.courseNum}.{waiver.conflictingClass?.section}
                      <br />
                      <small style={{ color: '#666' }}>{waiver.conflictingClass?.title}</small>
                    </td>
                    <td className="status-cell">
                      <span className={`status-badge ${getStatusColor(waiver.status || 'PENDING')}`}>
                        {waiver.status || 'PENDING'}
                      </span>
                    </td>
                    <td className="date-cell">
                      {waiver.createdAt ? new Date(waiver.createdAt).toLocaleDateString() : 'N/A'}
                    </td>
                    <td className="actions-cell">
                      <div style={{ fontSize: '0.875rem' }}>
                        {waiver.enrolledInstructorApproved === null ? (
                          <span style={{ color: '#f59e0b' }}>Enrolled instructor: Pending</span>
                        ) : waiver.enrolledInstructorApproved ? (
                          <span style={{ color: '#22c55e' }}>Enrolled instructor: Approved</span>
                        ) : (
                          <span style={{ color: '#ef4444' }}>Enrolled instructor: Rejected</span>
                        )}
                        <br />
                        {waiver.conflictingInstructorApproved === null ? (
                          <span style={{ color: '#f59e0b' }}>Conflicting instructor: Pending</span>
                        ) : waiver.conflictingInstructorApproved ? (
                          <span style={{ color: '#22c55e' }}>Conflicting instructor: Approved</span>
                        ) : (
                          <span style={{ color: '#ef4444' }}>Conflicting instructor: Rejected</span>
                        )}
                        <br />
                        {waiver.advisorApproved === null ? (
                          <span style={{ color: '#f59e0b' }}>Advisor: Pending</span>
                        ) : waiver.advisorApproved ? (
                          <span style={{ color: '#22c55e' }}>Advisor: Approved</span>
                        ) : (
                          <span style={{ color: '#ef4444' }}>Advisor: Rejected</span>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {showWaiverForm && (
          <div className="waiver-form-section">
            <p className="section-description">
              Request a time conflict waiver to enroll in classes with overlapping schedules.
              Requires approval from both instructors and a department-level advisor.
            </p>

            <form onSubmit={handleSubmitWaiver} className="waiver-form">
              <div className="form-row">
                <div className="form-group">
                  <label>Term Semester:</label>
                  <select
                    value={waiverTerm.semester}
                    onChange={(e) => {
                      setWaiverTerm({ ...waiverTerm, semester: e.target.value });
                      if (e.target.value && waiverTerm.year) {
                        loadClassesForWaiver(e.target.value, waiverTerm.year);
                      }
                    }}
                    required
                  >
                    <option value="">Select Semester</option>
                    <option value="Spring">Spring</option>
                    <option value="Fall">Fall</option>
                    <option value="Summer">Summer</option>
                    <option value="Winter">Winter</option>
                  </select>
                </div>

                <div className="form-group">
                  <label>Term Year:</label>
                  <input
                    type="number"
                    value={waiverTerm.year}
                    onChange={(e) => {
                      setWaiverTerm({ ...waiverTerm, year: e.target.value });
                      if (waiverTerm.semester && e.target.value) {
                        loadClassesForWaiver(waiverTerm.semester, e.target.value);
                      }
                    }}
                    placeholder="e.g., 2026"
                    min="2020"
                    max="2030"
                    required
                  />
                </div>
              </div>

              <div className="form-row">
                <div className="form-group">
                  <label>Currently Enrolled Class:</label>
                  <select
                    value={selectedEnrolledClass || ''}
                    onChange={(e) => setSelectedEnrolledClass(e.target.value ? parseInt(e.target.value) : null)}
                    required
                    disabled={enrolledClasses.length === 0 || loadingClasses}
                  >
                    <option value="">Select enrolled class</option>
                    {enrolledClasses.map(cls => (
                      <option key={cls.classId} value={cls.classId}>
                        {cls.department}{cls.courseNum}.{cls.section} - {cls.title} ({cls.schedule})
                      </option>
                    ))}
                  </select>
                  {enrolledClasses.length === 0 && !loadingClasses && waiverTerm.semester && waiverTerm.year && (
                    <small className="form-hint">No enrolled classes found for the selected term.</small>
                  )}
                </div>

                <div className="form-group" style={{ position: 'relative' }}>
                  <label>Conflicting Class to Add:</label>
                  <input
                    type="text"
                    value={conflictingClassSearch}
                    onChange={(e) => {
                      setConflictingClassSearch(e.target.value);
                      if (!e.target.value.trim()) {
                        setSelectedConflictingClass(null);
                        setShowSearchResults(false);
                      }
                    }}
                    onFocus={() => {
                      if (conflictingClassSearch.trim() && searchResults.length > 0) {
                        setShowSearchResults(true);
                      }
                    }}
                    placeholder="Search for class (e.g., CSE101, Mathematics, or instructor name)"
                    required={!selectedConflictingClass}
                    disabled={!waiverTerm.semester || !waiverTerm.year || loadingClasses}
                    style={{
                      width: '100%',
                      padding: '8px',
                      border: '1px solid #ccc',
                      borderRadius: '4px'
                    }}
                  />
                  {searching && (
                    <small style={{ color: '#666', display: 'block', marginTop: '4px' }}>Searching...</small>
                  )}
                  {showSearchResults && searchResults.length > 0 && (
                    <div style={{
                      position: 'absolute',
                      top: '100%',
                      left: 0,
                      right: 0,
                      backgroundColor: 'white',
                      border: '1px solid #ccc',
                      borderRadius: '4px',
                      maxHeight: '300px',
                      overflowY: 'auto',
                      zIndex: 1000,
                      boxShadow: '0 2px 8px rgba(0,0,0,0.1)',
                      marginTop: '4px'
                    }}>
                      {searchResults.map(cls => (
                        <div
                          key={cls.classId}
                          onClick={() => handleSelectConflictingClass(cls)}
                          style={{
                            padding: '10px',
                            cursor: 'pointer',
                            borderBottom: '1px solid #eee',
                            transition: 'background-color 0.2s'
                          }}
                          onMouseEnter={(e) => {
                            e.currentTarget.style.backgroundColor = '#f5f5f5';
                          }}
                          onMouseLeave={(e) => {
                            e.currentTarget.style.backgroundColor = 'white';
                          }}
                        >
                          <div style={{ fontWeight: 'bold' }}>
                            {cls.department}{cls.courseNum}.{cls.section} - {cls.title}
                          </div>
                          <div style={{ fontSize: '0.875rem', color: '#666', marginTop: '4px' }}>
                            {cls.schedule} | {cls.instructor ? `Instructor: ${cls.instructor}` : 'TBA'}
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                  {showSearchResults && searchResults.length === 0 && conflictingClassSearch.trim() && !searching && (
                    <div style={{
                      position: 'absolute',
                      top: '100%',
                      left: 0,
                      right: 0,
                      backgroundColor: 'white',
                      border: '1px solid #ccc',
                      borderRadius: '4px',
                      padding: '10px',
                      zIndex: 1000,
                      marginTop: '4px'
                    }}>
                      <small style={{ color: '#666' }}>No classes found matching your search.</small>
                    </div>
                  )}
                  {!waiverTerm.semester || !waiverTerm.year ? (
                    <small className="form-hint">Please select term semester and year first.</small>
                  ) : null}
                </div>
              </div>

              <div className="form-group">
                <label>Reason for Override Request:</label>
                <textarea
                  value={waiverReason}
                  onChange={(e) => setWaiverReason(e.target.value)}
                  placeholder="Please provide a reason for requesting this time conflict waiver..."
                  rows={4}
                  required
                />
              </div>

              {loadingClasses && (
                <div className="loading-message">Loading classes...</div>
              )}

              <div className="form-actions">
                <button
                  type="button"
                  className="cancel-button"
                  onClick={() => {
                    setShowWaiverForm(false);
                    setSelectedEnrolledClass(null);
                    setSelectedConflictingClass(null);
                    setWaiverReason('');
                    setWaiverTerm({ semester: '', year: '' });
                    setConflictingClassSearch('');
                    setSearchResults([]);
                    setShowSearchResults(false);
                  }}
                  disabled={submittingWaiver}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="submit-button"
                  disabled={submittingWaiver || !selectedEnrolledClass || !selectedConflictingClass || !waiverReason.trim()}
                >
                  {submittingWaiver ? 'Submitting...' : 'Submit Override Request'}
                </button>
              </div>
            </form>
          </div>
        )}
      </div>
      <LoadingModal
        isOpen={loading || submitting || submittingWaiver}
        message={
          loading ? "Loading..." :
            submitting ? "Submitting..." :
              submittingWaiver ? "Submitting override request..." : ""
        }
      />
    </div>
  );
};

export default MajorMinorDeclaration;