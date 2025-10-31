import React, { useState, useEffect } from 'react';
import './AdvisorApproval.css';

/**
 * Advisor Approval Component
 * Provides interface for advisors to approve or reject major/minor declarations.
 * 
 * Note: Advisor approval functionality and administrative interface were implemented 
 * with assistance from Cursor AI and ChatGPT for proper workflow management 
 * and approval processing capabilities.
 */

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
  // Helper method to detect if this is a change request
  getIsChangeRequest?: () => boolean;
  getFromSubject?: () => string | null;
}

interface StudentInfo {
  netId: string;
  firstName: string;
  lastName: string;
  email: string;
  gpa?: number;
  currentMajors?: string[];
  currentMinors?: string[];
}

const AdvisorApproval: React.FC = () => {
  const [pendingDeclarations, setPendingDeclarations] = useState<MajorMinorDeclaration[]>([]);
  const [allDeclarations, setAllDeclarations] = useState<MajorMinorDeclaration[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  
  // Filter states
  const [selectedStatus, setSelectedStatus] = useState<'PENDING' | 'ALL'>('PENDING');
  const [selectedSubject, setSelectedSubject] = useState('');
  const [selectedDeclarationType, setSelectedDeclarationType] = useState('');
  
  // Action states
  const [selectedDeclaration, setSelectedDeclaration] = useState<MajorMinorDeclaration | null>(null);
  
  // Advisor type and permissions
  const [advisorType, setAdvisorType] = useState<'CEAS' | 'DEPARTMENT' | 'UNIVERSITY'>('DEPARTMENT');
  const [currentUser, setCurrentUser] = useState<any>(null);
  const [showApprovalModal, setShowApprovalModal] = useState(false);
  const [showRejectionModal, setShowRejectionModal] = useState(false);
  const [comments, setComments] = useState('');
  const [processing, setProcessing] = useState(false);

  // Available filter options
  const [availableSubjects, setAvailableSubjects] = useState<string[]>([]);
  const [availableDeclarationTypes, setAvailableDeclarationTypes] = useState<string[]>([]);

  useEffect(() => {
    fetchCurrentUser();
    fetchDeclarations();
  }, []);

  const fetchCurrentUser = async () => {
    try {
      const response = await fetch('/api/auth/me', {
        credentials: 'include',
        cache: 'no-cache',
        headers: {
          'Cache-Control': 'no-cache',
          'Pragma': 'no-cache'
        }
      });
      
      if (response.ok) {
        const userData = await response.json();
        setCurrentUser(userData);
        determineAdvisorType(userData);
      }
    } catch (err) {
      console.error('Error fetching current user:', err);
    }
  };

  const determineAdvisorType = (user: any) => {
    // Based on users1.yaml data structure
    if (user.role === 'ADVISOR') {
      if (user.level === 'university') {
        setAdvisorType('UNIVERSITY');
      } else if (user.level === 'CEAS' || (user.academic_unit && user.academic_unit.includes('CSE'))) {
        setAdvisorType('CEAS');
      } else {
        setAdvisorType('DEPARTMENT');
      }
    }
  };

  const canApproveDeclaration = (declaration: MajorMinorDeclaration): boolean => {
    const ceasSubjects = ['AMS', 'BME', 'CME', 'CIV', 'ECE', 'CSE', 'ESE', 'ESG', 'ISE', 'MEC', 'TSM']; // CEAS subjects
    
    if (advisorType === 'UNIVERSITY') {
      return true; // University advisors can approve all
    } else if (advisorType === 'CEAS') {
      return ceasSubjects.includes(declaration.subjectCode);
    } else if (advisorType === 'DEPARTMENT') {
      // Department advisors can approve their subjects
      return true; // Will be validated by backend
    }
    
    return false;
  };

  const getAdvisorTypeDescription = (): string => {
    switch (advisorType) {
      case 'CEAS':
        return 'CEAS Advisor - Can approve all CEAS majors (AMS, BME, CME, CIV, ECE, CSE, ESE, ESG, ISE, MEC, TSM)';
      case 'DEPARTMENT':
        return 'Department Advisor - Can approve department-specific majors/minors';
      case 'UNIVERSITY':
        return 'University Advisor - Can approve all declarations';
      default:
        return 'Advisor';
    }
  };

  useEffect(() => {
    // Update filter options when declarations change
    const subjects = [...new Set(allDeclarations.map(decl => decl.subjectCode))].sort();
    const declarationTypes = [...new Set(allDeclarations.map(decl => decl.declarationType))].sort();
    
    setAvailableSubjects(subjects);
    setAvailableDeclarationTypes(declarationTypes);
  }, [allDeclarations]);

  const fetchDeclarations = async () => {
    try {
      // Fetch pending declarations
      const pendingResponse = await fetch('/api/major-minor-declarations/pending', {
        credentials: 'include'
      });
      const pendingData = await pendingResponse.json();
      
      if (pendingData.success) {
        setPendingDeclarations(pendingData.declarations);
      }

      // Fetch all declarations for filtering
      const allResponse = await fetch('/api/major-minor-declarations/all', {
        credentials: 'include'
      });
      const allData = await allResponse.json();
      
      if (allData.success) {
        setAllDeclarations(allData.declarations);
      }
    } catch (err) {
      setError('Error fetching declarations');
      console.error('Error:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleApprove = async () => {
    if (!selectedDeclaration) return;
    
    setProcessing(true);
    try {
      const response = await fetch(`/api/major-minor-declarations/${selectedDeclaration.declarationId}/approve`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        credentials: 'include',
        body: JSON.stringify({
          comments: comments.trim() || null,
        }),
      });

      const data = await response.json();

      if (data.success) {
        setSuccess('Declaration approved successfully!');
        setShowApprovalModal(false);
        setComments('');
        setSelectedDeclaration(null);
        fetchDeclarations(); // Refresh declarations
      } else {
        setError(data.error || 'Failed to approve declaration');
      }
    } catch (err) {
      setError('Error approving declaration');
      console.error('Error:', err);
    } finally {
      setProcessing(false);
    }
  };

  const handleReject = async () => {
    if (!selectedDeclaration) return;
    
    setProcessing(true);
    try {
      const response = await fetch(`/api/major-minor-declarations/${selectedDeclaration.declarationId}/reject`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        credentials: 'include',
        body: JSON.stringify({
          comments: comments.trim() || null,
        }),
      });

      const data = await response.json();

      if (data.success) {
        setSuccess('Declaration rejected');
        setShowRejectionModal(false);
        setComments('');
        setSelectedDeclaration(null);
        fetchDeclarations(); // Refresh declarations
      } else {
        setError(data.error || 'Failed to reject declaration');
      }
    } catch (err) {
      setError('Error rejecting declaration');
      console.error('Error:', err);
    } finally {
      setProcessing(false);
    }
  };

  const openApprovalModal = (declaration: MajorMinorDeclaration) => {
    setSelectedDeclaration(declaration);
    setComments('');
    setShowApprovalModal(true);
  };

  const openRejectionModal = (declaration: MajorMinorDeclaration) => {
    setSelectedDeclaration(declaration);
    setComments('');
    setShowRejectionModal(true);
  };

  const closeModals = () => {
    setShowApprovalModal(false);
    setShowRejectionModal(false);
    setSelectedDeclaration(null);
    setComments('');
  };

  const getFilteredDeclarations = () => {
    let filtered = selectedStatus === 'PENDING' ? pendingDeclarations : allDeclarations;

    if (selectedSubject) {
      filtered = filtered.filter(decl => decl.subjectCode === selectedSubject);
    }

    if (selectedDeclarationType) {
      filtered = filtered.filter(decl => decl.declarationType === selectedDeclarationType);
    }

    return filtered;
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

  const getDegreeTypeDisplayName = (degreeType: string) => {
    switch (degreeType) {
      case 'BA': return 'Bachelor of Arts';
      case 'BS': return 'Bachelor of Science';
      case 'MINOR': return 'Minor';
      default: return degreeType;
    }
  };

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  };

  // Helper functions to detect change requests
  const isChangeRequest = (declaration: MajorMinorDeclaration): boolean => {
    if (!declaration.advisorComments) return false;
    return declaration.advisorComments.startsWith('CHANGE_FROM:');
  };

  const getFromSubject = (declaration: MajorMinorDeclaration): string | null => {
    if (!declaration.advisorComments) return null;
    if (declaration.advisorComments.startsWith('CHANGE_FROM:')) {
      const match = declaration.advisorComments.match(/^CHANGE_FROM:(\w+)/);
      return match ? match[1] : null;
    }
    return null;
  };

  if (loading) {
    return (
      <div className="advisor-approval">
        <div className="loading">Loading declarations...</div>
      </div>
    );
  }

  return (
    <div className="advisor-approval">
      <div className="page-header">
        <h1>Advisor Approval</h1>
        <p>Review and approve major/minor declarations</p>
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

      {/* Summary Cards */}
      <div className="summary-section">
        <div className="summary-cards">
          <div className="summary-card pending">
            <h3>Pending Approval</h3>
            <div className="count">{pendingDeclarations.length}</div>
            <div className="description">Awaiting your review</div>
          </div>
          <div className="summary-card total">
            <h3>Total Declarations</h3>
            <div className="count">{allDeclarations.length}</div>
            <div className="description">All time</div>
          </div>
        </div>
      </div>

      {/* Filters */}
      <div className="filters-section">
        <h2>Filters</h2>
        <div className="filters-grid">
          <div className="filter-group">
            <label htmlFor="statusFilter">Status</label>
            <select
              id="statusFilter"
              value={selectedStatus}
              onChange={(e) => setSelectedStatus(e.target.value as 'PENDING' | 'ALL')}
            >
              <option value="PENDING">Pending Only</option>
              <option value="ALL">All Declarations</option>
            </select>
          </div>

          <div className="filter-group">
            <label htmlFor="subjectFilter">Subject Code</label>
            <select
              id="subjectFilter"
              value={selectedSubject}
              onChange={(e) => setSelectedSubject(e.target.value)}
            >
              <option value="">All Subjects</option>
              {availableSubjects.map(subject => (
                <option key={subject} value={subject}>{subject}</option>
              ))}
            </select>
          </div>

          <div className="filter-group">
            <label htmlFor="declarationTypeFilter">Declaration Type</label>
            <select
              id="declarationTypeFilter"
              value={selectedDeclarationType}
              onChange={(e) => setSelectedDeclarationType(e.target.value)}
            >
              <option value="">All Types</option>
              {availableDeclarationTypes.map(type => (
                <option key={type} value={type}>{type}</option>
              ))}
            </select>
          </div>
        </div>
      </div>

      {/* Advisor Type Info */}
      <div className="advisor-info-section">
        <div className="advisor-type-badge">
          <strong>Advisor Type:</strong> {getAdvisorTypeDescription()}
        </div>
      </div>

      {/* Declarations List */}
      <div className="declarations-section">
        <div className="section-header">
          <h2>Declarations</h2>
          <div className="results-count">
            {getFilteredDeclarations().length} declaration(s) found
          </div>
        </div>

        {getFilteredDeclarations().length === 0 ? (
          <div className="no-declarations">
            <p>No declarations found matching your criteria.</p>
          </div>
        ) : (
          <div className="declarations-table-container">
            <table className="declarations-table">
              <thead>
                <tr>
                  <th>Student</th>
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
                {getFilteredDeclarations().map(declaration => (
                  <tr key={declaration.declarationId}>
                    <td className="student-cell">
                      <div className="student-info">
                        <div className="student-name">{declaration.studentNetId}</div>
                        <div className="student-id">NetID: {declaration.studentNetId}</div>
                        {isChangeRequest(declaration) && (
                          <div className="change-badge" style={{color: 'orange', fontWeight: 'bold', marginTop: '4px'}}>
                            🔄 Change Request
                          </div>
                        )}
                      </div>
                    </td>
                    <td className="subject-cell">
                      {declaration.subjectCode}
                      {isChangeRequest(declaration) && getFromSubject(declaration) && (
                        <div style={{fontSize: '0.85em', color: '#666', marginTop: '2px'}}>
                          From: {getFromSubject(declaration)}
                        </div>
                      )}
                    </td>
                    <td className="type-cell">{declaration.declarationType}</td>
                    <td className="degree-cell">{declaration.degreeType}</td>
                    <td className="term-cell">{declaration.effectiveTerm}</td>
                    <td className="status-cell">
                      <span className={`status-badge ${getStatusColor(declaration.status)}`}>
                        {getStatusText(declaration.status)}
                      </span>
                    </td>
                    <td className="date-cell">
                      {formatDate(declaration.submittedAt)}
                    </td>
                    <td className="actions-cell">
                      {declaration.status === 'PENDING' && (
                        <div className="action-buttons">
                          {canApproveDeclaration(declaration) ? (
                            <>
                              <button
                                className="approve-button"
                                onClick={() => openApprovalModal(declaration)}
                              >
                                Approve
                              </button>
                              <button
                                className="reject-button"
                                onClick={() => openRejectionModal(declaration)}
                              >
                                Reject
                              </button>
                            </>
                          ) : (
                            <div className="no-permission">
                              <span className="permission-text">
                                Requires CEAS Advisor
                              </span>
                            </div>
                          )}
                        </div>
                      )}
                      {declaration.status === 'REJECTED' && declaration.advisorComments && (
                        <div className="rejection-reason">
                          <strong>Reason:</strong> {declaration.advisorComments}
                        </div>
                      )}
                      {declaration.status === 'APPROVED' && declaration.advisorComments && (
                        <div className="approval-comments">
                          <strong>Comments:</strong> {declaration.advisorComments}
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

      {/* Approval Modal */}
      {showApprovalModal && selectedDeclaration && (
        <div className="modal-overlay">
          <div className="modal">
            <div className="modal-header">
              <h3>Approve Declaration</h3>
              <button className="close-button" onClick={closeModals}>×</button>
            </div>
            <div className="modal-body">
              <div className="declaration-summary">
                {isChangeRequest(selectedDeclaration) && (
                  <div style={{backgroundColor: '#fff3cd', border: '1px solid #ffc107', borderRadius: '4px', padding: '10px', marginBottom: '15px'}}>
                    <p style={{margin: 0, fontWeight: 'bold', color: '#856404'}}>
                      🔄 CHANGE REQUEST
                    </p>
                    {getFromSubject(selectedDeclaration) && (
                      <p style={{margin: '5px 0 0 0', color: '#856404'}}>
                        Changing from <strong>{getFromSubject(selectedDeclaration)}</strong> to <strong>{selectedDeclaration.subjectCode}</strong>
                      </p>
                    )}
                  </div>
                )}
                <p><strong>Student:</strong> {selectedDeclaration.studentNetId}</p>
                <p><strong>Declaration:</strong> {selectedDeclaration.subjectCode} {selectedDeclaration.declarationType}</p>
                <p><strong>Degree Type:</strong> {getDegreeTypeDisplayName(selectedDeclaration.degreeType)}</p>
                <p><strong>Effective Term:</strong> {selectedDeclaration.effectiveTerm}</p>
              </div>
              <div className="form-group">
                <label htmlFor="approvalComments">Comments (Optional)</label>
                <textarea
                  id="approvalComments"
                  value={comments}
                  onChange={(e) => setComments(e.target.value)}
                  placeholder="Add any comments for the student..."
                  rows={4}
                />
              </div>
            </div>
            <div className="modal-footer">
              <button className="cancel-button" onClick={closeModals}>
                Cancel
              </button>
              <button 
                className="approve-button"
                onClick={handleApprove}
                disabled={processing}
              >
                {processing ? 'Approving...' : 'Approve Declaration'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Rejection Modal */}
      {showRejectionModal && selectedDeclaration && (
        <div className="modal-overlay">
          <div className="modal">
            <div className="modal-header">
              <h3>Reject Declaration</h3>
              <button className="close-button" onClick={closeModals}>×</button>
            </div>
            <div className="modal-body">
              <div className="declaration-summary">
                {isChangeRequest(selectedDeclaration) && (
                  <div style={{backgroundColor: '#fff3cd', border: '1px solid #ffc107', borderRadius: '4px', padding: '10px', marginBottom: '15px'}}>
                    <p style={{margin: 0, fontWeight: 'bold', color: '#856404'}}>
                      🔄 CHANGE REQUEST
                    </p>
                    {getFromSubject(selectedDeclaration) && (
                      <p style={{margin: '5px 0 0 0', color: '#856404'}}>
                        Changing from <strong>{getFromSubject(selectedDeclaration)}</strong> to <strong>{selectedDeclaration.subjectCode}</strong>
                      </p>
                    )}
                  </div>
                )}
                <p><strong>Student:</strong> {selectedDeclaration.studentNetId}</p>
                <p><strong>Declaration:</strong> {selectedDeclaration.subjectCode} {selectedDeclaration.declarationType}</p>
                <p><strong>Degree Type:</strong> {getDegreeTypeDisplayName(selectedDeclaration.degreeType)}</p>
                <p><strong>Effective Term:</strong> {selectedDeclaration.effectiveTerm}</p>
              </div>
              <div className="form-group">
                <label htmlFor="rejectionComments">Reason for Rejection *</label>
                <textarea
                  id="rejectionComments"
                  value={comments}
                  onChange={(e) => setComments(e.target.value)}
                  placeholder="Please provide a reason for rejection..."
                  rows={4}
                  required
                />
              </div>
            </div>
            <div className="modal-footer">
              <button className="cancel-button" onClick={closeModals}>
                Cancel
              </button>
              <button 
                className="reject-button"
                onClick={handleReject}
                disabled={processing || !comments.trim()}
              >
                {processing ? 'Rejecting...' : 'Reject Declaration'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default AdvisorApproval;
