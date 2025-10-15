import React, { useState, useEffect } from 'react';
import './MajorMinorDeclaration.css';

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

  // Available subjects and terms
  const availableSubjects = ['CSE', 'BIO', 'PSY', 'MAT', 'PHY', 'CHE', 'ENG', 'HIS', 'POL', 'SOC'];
  const availableTerms = ['Fall 2025', 'Spring 2026', 'Fall 2026', 'Spring 2027'];

  useEffect(() => {
    fetchDegreeRequirements();
    fetchMyDeclarations();
  }, []);

  const fetchDegreeRequirements = async () => {
    try {
      const response = await fetch('/api/degree-requirements');
      const data = await response.json();
      
      if (data.success) {
        setDegreeRequirements(data.requirements);
      } else {
        setError('Failed to fetch degree requirements');
      }
    } catch (err) {
      setError('Error fetching degree requirements');
      console.error('Error:', err);
    }
  };

  const fetchMyDeclarations = async () => {
    try {
      const response = await fetch('/api/declarations/my-declarations');
      const data = await response.json();
      
      if (data.success) {
        setMyDeclarations(data.declarations);
      } else {
        setError('Failed to fetch declarations');
      }
    } catch (err) {
      setError('Error fetching declarations');
      console.error('Error:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleSubmitDeclaration = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);

    try {
      const response = await fetch('/api/declarations/submit', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          subjectCode: selectedSubject,
          degreeType: selectedDegreeType,
          declarationType: selectedDeclarationType,
          effectiveTerm: effectiveTerm,
        }),
      });

      const data = await response.json();

      if (data.success) {
        setSuccess('Declaration submitted successfully!');
        setShowDeclarationForm(false);
        fetchMyDeclarations(); // Refresh declarations
        resetForm();
      } else {
        setError(data.error || 'Failed to submit declaration');
      }
    } catch (err) {
      setError('Error submitting declaration');
      console.error('Error:', err);
    } finally {
      setSubmitting(false);
    }
  };

  const handleWithdrawDeclaration = async (declarationId: number) => {
    if (!window.confirm('Are you sure you want to withdraw this declaration?')) {
      return;
    }

    try {
      const response = await fetch(`/api/declarations/${declarationId}/withdraw`, {
        method: 'POST',
      });

      const data = await response.json();

      if (data.success) {
        setSuccess('Declaration withdrawn successfully!');
        fetchMyDeclarations(); // Refresh declarations
      } else {
        setError(data.error || 'Failed to withdraw declaration');
      }
    } catch (err) {
      setError('Error withdrawing declaration');
      console.error('Error:', err);
    }
  };

  const resetForm = () => {
    setSelectedSubject('');
    setSelectedDegreeType('BA');
    setSelectedDeclarationType('MAJOR');
    setEffectiveTerm('');
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'APPROVED': return 'status-approved';
      case 'PENDING': return 'status-pending';
      case 'REJECTED': return 'status-rejected';
      case 'WITHDRAWN': return 'status-withdrawn';
      default: return 'status-unknown';
    }
  };

  const getStatusText = (status: string) => {
    switch (status) {
      case 'APPROVED': return 'Approved';
      case 'PENDING': return 'Pending Approval';
      case 'REJECTED': return 'Rejected';
      case 'WITHDRAWN': return 'Withdrawn';
      default: return 'Unknown';
    }
  };

  const getAvailableDegreeTypes = (subjectCode: string) => {
    const requirements = degreeRequirements.filter(req => req.subjectCode === subjectCode);
    return requirements.map(req => req.degreeType);
  };

  const getRequirementInfo = (subjectCode: string, degreeType: string) => {
    return degreeRequirements.find(req => 
      req.subjectCode === subjectCode && req.degreeType === degreeType
    );
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
        <h1>Major & Minor Declaration</h1>
        <p>Declare your majors and minors for upcoming terms</p>
      </div>

      {error && (
        <div className="message error">
          <span className="message-icon">⚠️</span>
          {error}
        </div>
      )}

      {success && (
        <div className="message success">
          <span className="message-icon">✅</span>
          {success}
        </div>
      )}

      {/* Current Declarations Summary */}
      <div className="declaration-summary">
        <h2>Current Declarations</h2>
        <div className="summary-cards">
          <div className="summary-card">
            <h3>Majors</h3>
            <div className="count">{getCurrentDeclarationsCount('MAJOR')}/2</div>
            <div className="status">
              {canDeclareMore('MAJOR') ? 'Can declare more' : 'Maximum reached'}
            </div>
          </div>
          <div className="summary-card">
            <h3>Minors</h3>
            <div className="count">{getCurrentDeclarationsCount('MINOR')}/3</div>
            <div className="status">
              {canDeclareMore('MINOR') ? 'Can declare more' : 'Maximum reached'}
            </div>
          </div>
        </div>
      </div>

      {/* New Declaration Button */}
      <div className="action-section">
        <button 
          className="new-declaration-button"
          onClick={() => setShowDeclarationForm(true)}
          disabled={!canDeclareMore('MAJOR') && !canDeclareMore('MINOR')}
        >
          + New Declaration
        </button>
      </div>

      {/* Declaration Form */}
      {showDeclarationForm && (
        <div className="declaration-form-section">
          <h2>Submit New Declaration</h2>
          <form onSubmit={handleSubmitDeclaration} className="declaration-form">
            <div className="form-group">
              <label htmlFor="declarationType">Declaration Type *</label>
              <select
                id="declarationType"
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

            <div className="form-group">
              <label htmlFor="subjectCode">Subject Code *</label>
              <select
                id="subjectCode"
                value={selectedSubject}
                onChange={(e) => {
                  setSelectedSubject(e.target.value);
                  setSelectedDegreeType('BA'); // Reset degree type
                }}
                required
              >
                <option value="">Select a subject</option>
                {availableSubjects.map(subject => (
                  <option key={subject} value={subject}>{subject}</option>
                ))}
              </select>
            </div>

            {selectedSubject && (
              <div className="form-group">
                <label htmlFor="degreeType">Degree Type *</label>
                <select
                  id="degreeType"
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

            <div className="form-group">
              <label htmlFor="effectiveTerm">Effective Term *</label>
              <select
                id="effectiveTerm"
                value={effectiveTerm}
                onChange={(e) => setEffectiveTerm(e.target.value)}
                required
              >
                <option value="">Select a term</option>
                {availableTerms.map(term => (
                  <option key={term} value={term}>{term}</option>
                ))}
              </select>
            </div>

            {/* Requirement Information */}
            {selectedSubject && selectedDegreeType && (
              <div className="requirement-info">
                <h3>Requirement Information</h3>
                {(() => {
                  const requirement = getRequirementInfo(selectedSubject, selectedDegreeType);
                  if (requirement) {
                    return (
                      <div className="requirement-details">
                        <p><strong>Total Credits Required:</strong> {requirement.totalCreditsRequired}</p>
                        {requirement.gpaRequirement && (
                          <p><strong>GPA Requirement:</strong> {requirement.gpaRequirement}</p>
                        )}
                        <p><strong>Restricted Program:</strong> {requirement.isRestricted ? 'Yes' : 'No'}</p>
                        {requirement.isRestricted && (
                          <p className="restricted-note">
                            ⚠️ This is a restricted program and requires advisor approval.
                          </p>
                        )}
                      </div>
                    );
                  }
                  return <p>No requirement information available.</p>;
                })()}
              </div>
            )}

            <div className="form-actions">
              <button 
                type="button" 
                className="cancel-button"
                onClick={() => {
                  setShowDeclarationForm(false);
                  resetForm();
                }}
              >
                Cancel
              </button>
              <button 
                type="submit" 
                className="submit-button"
                disabled={submitting}
              >
                {submitting ? 'Submitting...' : 'Submit Declaration'}
              </button>
            </div>
          </form>
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
