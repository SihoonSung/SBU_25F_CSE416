import React, { useState, useEffect } from 'react';
import './MajorMinorDeclaration.css';

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
  const [degreeRequirements, setDegreeRequirements] = useState<DegreeRequirement[]>([]);
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

  // Available terms and subjects based on academic calendar
  const getAvailableTerms = () => {
    const currentDate = systemDate || new Date();
    const allTerms = [
      { value: 'FALL 2025', label: 'Fall 2025 (Mar 31 - Aug 29, 2025)', start: '2025-03-31', end: '2025-08-29' },
      { value: 'SPRING 2026', label: 'Spring 2026 (Oct 27, 2025 - Jan 30, 2026)', start: '2025-10-27', end: '2026-01-30' }
    ];
    
    return allTerms.filter(term => {
      const startDate = new Date(term.start);
      const endDate = new Date(term.end);
      endDate.setHours(23, 59, 59, 999);
      
      return currentDate >= startDate && currentDate <= endDate;
    });
  };

  useEffect(() => {
    fetchData();
    loadPlannedMajorsFromStorage();
  }, []);

  // TP Used cursor to fetch data from APIs
  const fetchData = async () => {
    try {
      setLoading(true);
      console.log('Starting to fetch data...');
      
      // Fetch system date from backend
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
            setSystemDate(new Date(systemDateData.currentDate));
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
            setDegreeRequirements(requirementsData.requirements || []);
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

  // TP Used cursor to check declaration period validity
  const checkDeclarationPeriod = (term: string, currentDate: Date): boolean => {
    // Declaration periods based on academic calendar
    const declarationPeriods: Record<string, { start: string, end: string }> = {
      'FALL 2025': {
        start: '2025-03-31',
        end: '2025-08-29'
      },
      'SPRING 2026': {
        start: '2025-10-27',
        end: '2026-01-30'
      }
    };

    const period = declarationPeriods[term];
    if (!period) {
      return false;
    }

    const startDate = new Date(period.start);
    const endDate = new Date(period.end);
    
    // Set time to end of day for end date
    endDate.setHours(23, 59, 59, 999);
    
    console.log('Checking declaration period:', {
      term,
      currentDate: currentDate.toISOString().split('T')[0],
      startDate: startDate.toISOString().split('T')[0],
      endDate: endDate.toISOString().split('T')[0],
      isWithinPeriod: currentDate >= startDate && currentDate <= endDate
    });

    return currentDate >= startDate && currentDate <= endDate;
  };

  const getStatusColor = (status: string) => {
    switch (status) {
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

  if (loading) {
    return (
      <div className="major-minor-declaration">
        <div className="loading">Loading...</div>
      </div>
    );
  }

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
            <strong>Declaration Periods:</strong><br/>
            • <strong>Fall 2025:</strong> March 31 - August 29, 2025<br/>
            • <strong>Spring 2026:</strong> October 27, 2025 - January 30, 2026
          </p>
          <p>
            Restricted majors and minors have course requirements or GPA requirements for admission; 
            department-level academic advisors can waive these requirements. 
          </p>
          <p>
            <strong>CEAS Transfer Policy:</strong><br/>
            Requests to transfer into CEAS (College of Engineering and Applied Sciences) by declaring an AOI or a major 
            in CEAS without currently being an AOI or a major in CEAS requires approval of a CEAS academic advisor.<br/>
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
        
        <div style={{display: 'flex', gap: '20px', marginTop: '10px'}}>
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
                      <option key={term.value} value={term.value}>{term.label}</option>
                    ))}
                  </select>
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
                    <option key={term.value} value={term.value}>{term.label}</option>
                  ))}
                </select>
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
                    <td className="degree-cell">{declaration.degreeType}</td>
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
    </div>
  );
};

export default MajorMinorDeclaration;