import React, { useState, useEffect } from 'react';
import './MajorMinorDeclaration.css';

/**
 * Major/Minor Declaration Component
 * Provides interface for students to declare majors and minors.
 * 
 * Note: Major/minor declaration functionality and user interface were implemented 
 * with assistance from Cursor AI and ChatGPT for proper form handling 
 * and data validation capabilities.
 */

interface DegreeRequirement {
  requirementId: number;
  subjectCode: string;
  degreeType: 'BA' | 'BS' | 'MINOR';
  totalCreditsRequired: number;
  isRestricted: boolean;
  gpaRequirement?: number;
  admissionRequirements?: string;
  courseRequirements?: string;
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

const MajorMinorDeclaration: React.FC = () => {
  const [degreeRequirements, setDegreeRequirements] = useState<DegreeRequirement[]>([]);
  const [myDeclarations, setMyDeclarations] = useState<MajorMinorDeclaration[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  
  // Form state
  const [showDeclarationForm, setShowDeclarationForm] = useState(false);
  const [selectedSubject, setSelectedSubject] = useState('');
  const [selectedDegreeType, setSelectedDegreeType] = useState<'BA' | 'BS' | 'MINOR'>('BA');
  const [selectedDeclarationType, setSelectedDeclarationType] = useState<'MAJOR' | 'MINOR'>('MAJOR');
  const [effectiveTerm, setEffectiveTerm] = useState('');
  const [submitting, setSubmitting] = useState(false);

  // Available terms and subjects based on academic calendar
  const getAvailableTerms = () => {
    const currentDate = new Date();
    const allTerms = [
      { value: 'FALL 2025', label: 'Fall 2025 (Mar 31 - Aug 29, 2025)', start: '2025-03-31', end: '2025-08-29' },
      { value: 'SPRING 2026', label: 'Spring 2026 (Oct 25, 2025 - Jan 30, 2026)', start: '2025-10-25', end: '2026-01-30' }
    ];
    
    return allTerms.filter(term => {
      const startDate = new Date(term.start);
      const endDate = new Date(term.end);
      endDate.setHours(23, 59, 59, 999);
      
      return currentDate >= startDate && currentDate <= endDate;
    });
  };
  const availableSubjects = ['BIO', 'PSY', 'CSE', 'ECO', 'AMS', 'POL'];

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    try {
      setLoading(true);
      console.log('Starting to fetch data...');
      
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
      
      console.log('Data fetch completed');
    } catch (err) {
      console.error('Error fetching data:', err);
      setError('Failed to load data. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const handleSubmitDeclaration = async (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!selectedSubject || !effectiveTerm) {
      setError('Please fill in all required fields.');
      return;
    }

    // Check if current date is within the declaration period
    const currentDate = new Date();
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
          degreeType: selectedDegreeType,
          declarationType: selectedDeclarationType,
          effectiveTerm: effectiveTerm
        })
      });

      const data = await response.json();

      if (data.success) {
        setSuccess('Declaration submitted successfully!');
        setShowDeclarationForm(false);
        
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

  const handleWithdrawDeclaration = async (declarationId: number) => {
    if (!window.confirm('Are you sure you want to withdraw this declaration?')) {
      return;
    }

    try {
      const response = await fetch(`/api/major-minor-declarations/${declarationId}/withdraw`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
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

  const getAvailableDegreeTypes = (subject: string): ('BA' | 'BS' | 'MINOR')[] => {
    // Based on degree_requirements.yaml data
    const subjectDegreeMap: Record<string, ('BA' | 'BS' | 'MINOR')[]> = {
      'CSE': ['BS'],
      'PSY': ['BA'],
      'BIO': ['BA'],
      'AMS': ['BS'],
      'POL': ['BA'],
      'ECO': ['BA']
    };
    
    return subjectDegreeMap[subject] || [];
  };

  const checkDeclarationPeriod = (term: string, currentDate: Date): boolean => {
    // Declaration periods based on academic calendar
    const declarationPeriods: Record<string, { start: string, end: string }> = {
      'FALL 2025': {
        start: '2025-03-31',
        end: '2025-08-29'
      },
      // NEED TO CHAGE THIS TO 2025 10 27 LATER
      'SPRING 2026': {
        start: '2025-10-25',
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

  // Check if user has reached maximum majors
  const hasReachedMaxMajors = !canDeclareMore('MAJOR');
  const hasReachedMaxMinors = !canDeclareMore('MINOR');

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
            Restricted majors and minors (such as CSE) have course requirements or GPA requirements for admission; 
            department-level academic advisors can waive these requirements. Requests to transfer into CEAS (where all 
            majors are restricted), by declaring an AOI or a major in CEAS without currently being an AOI or a major 
            in CEAS, requires approval of a CEAS academic advisor.
          </p>
        </div>
      </div>

      {/* Alerts Section - Only show when user has reached limits */}
      {(hasReachedMaxMajors || hasReachedMaxMinors) && (
        <div className="alerts-section">
          <div className="alerts-grid">
            {hasReachedMaxMajors && (
              <div className="alert alert-error">
                <div className="alert-icon">i</div>
                <div className="alert-content">
                  <div className="alert-text">
                    Maximum Majors Reached: You have reached the maximum limit of 2 majors. Please withdraw an existing major before declaring a new one.
                  </div>
                </div>
              </div>
            )}
            
            {hasReachedMaxMinors && (
              <div className="alert alert-warning">
                <div className="alert-icon">i</div>
                <div className="alert-content">
                  <div className="alert-text">
                    Maximum Minors Reached: You have reached the maximum limit of 3 minors. Please withdraw an existing minor before declaring a new one.
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>
      )}

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

      {/* Declaration Form */}
      <div className="declaration-form-section">
        <div className="section-header">
          <h2>Submit New Declaration</h2>
          <button 
            className="toggle-form-button"
            onClick={() => setShowDeclarationForm(!showDeclarationForm)}
          >
            {showDeclarationForm ? 'Cancel' : 'New Declaration'}
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

        {showDeclarationForm && (
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
                    // Set the first available degree type for the selected subject
                    const availableTypes = getAvailableDegreeTypes(e.target.value);
                    if (availableTypes.length > 0) {
                      setSelectedDegreeType(availableTypes[0]);
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

              {selectedSubject && (
                <div className="form-group">
                  <label>Degree Type:</label>
                  <select
                    value={selectedDegreeType}
                    onChange={(e) => setSelectedDegreeType(e.target.value as 'BA' | 'BS' | 'MINOR')}
                    required
                  >
                    {getAvailableDegreeTypes(selectedSubject).map(degreeType => (
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