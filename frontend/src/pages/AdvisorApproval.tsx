/*
 * TP: Used Cursor to implement advisor approval page for major/minor declarations and time conflict waivers
 */

import React, { useState, useEffect } from 'react';
import LoadingModal from '../components/LoadingModal';
import './AdvisorApproval.css';

/**
 * Advisor Approval Component
 * Provides interface for advisors to approve or reject major/minor declarations.
 * Also provides interface for instructors to approve or reject time conflict waivers.
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
}

interface TimeConflictWaiver {
  waiverId: number;
  studentId: number;
  studentName: string;
  enrolledClass: {
    classId: number;
    department: string;
    courseNum: number;
    section: string;
    title: string;
    instructor: string;
  };
  conflictingClass: {
    classId: number;
    department: string;
    courseNum: number;
    section: string;
    title: string;
    instructor: string;
  };
  enrolledInstructorApproved: boolean | null;
  enrolledInstructorApprovedBy?: number;
  enrolledInstructorApprovedByName?: string;
  enrolledInstructorApprovedAt?: string;
  conflictingInstructorApproved: boolean | null;
  conflictingInstructorApprovedBy?: number;
  conflictingInstructorApprovedByName?: string;
  conflictingInstructorApprovedAt?: string;
  advisorApproved: boolean | null;
  advisorApprovedBy?: number;
  advisorApprovedByName?: string;
  advisorApprovedAt?: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  reason?: string;
  createdAt: string;
  updatedAt?: string;
}

const AdvisorApproval: React.FC = () => {
  const backendBase = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8080'
  const [pendingDeclarations, setPendingDeclarations] = useState<MajorMinorDeclaration[]>([]);
  const [allDeclarations, setAllDeclarations] = useState<MajorMinorDeclaration[]>([]);
  const [pendingWaivers, setPendingWaivers] = useState<TimeConflictWaiver[]>([]);
  const [allWaivers, setAllWaivers] = useState<TimeConflictWaiver[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  // User role
  const [userRole, setUserRole] = useState<'ADVISOR' | 'INSTRUCTOR' | 'REGISTRAR' | null>(null);

  // Filter states
  const [selectedStatus, setSelectedStatus] = useState<'PENDING' | 'ALL'>('PENDING');
  const [selectedSubject, setSelectedSubject] = useState('');
  const [selectedDeclarationType, setSelectedDeclarationType] = useState('');

  // Action states
  const [selectedDeclaration, setSelectedDeclaration] = useState<MajorMinorDeclaration | null>(null);
  const [selectedWaiver, setSelectedWaiver] = useState<TimeConflictWaiver | null>(null);

  // Advisor type and permissions
  const [advisorType, setAdvisorType] = useState<'CEAS' | 'DEPARTMENT' | 'UNIVERSITY'>('DEPARTMENT');
  const [currentUser, setCurrentUser] = useState<any>(null);
  const [showApprovalModal, setShowApprovalModal] = useState(false);
  const [showRejectionModal, setShowRejectionModal] = useState(false);
  const [showWaiverApprovalModal, setShowWaiverApprovalModal] = useState(false);
  const [showWaiverRejectionModal, setShowWaiverRejectionModal] = useState(false);
  const [comments, setComments] = useState('');
  const [processing, setProcessing] = useState(false);

  // Available filter options
  const [availableSubjects, setAvailableSubjects] = useState<string[]>([]);
  const [availableDeclarationTypes, setAvailableDeclarationTypes] = useState<string[]>([]);

  useEffect(() => {
    fetchCurrentUser();
  }, []);

  useEffect(() => {
    if (userRole === 'ADVISOR' || userRole === 'REGISTRAR') {
      fetchDeclarations();
      fetchAdvisorWaivers();
    } else if (userRole === 'INSTRUCTOR') {
      fetchWaivers();
    }
  }, [userRole]);

  const fetchCurrentUser = async () => {
    try {
      const response = await fetch(`${backendBase}/api/auth/me`, {
        credentials: 'include',
        cache: 'no-cache',
        headers: {
          'Cache-Control': 'no-cache',
          'Pragma': 'no-cache'
        }
      });

      if (response.ok) {
        const data = await response.json();
        const userData = data.user || data;
        setCurrentUser(userData);
        const role = userData.role || '';
        if (role.toUpperCase() === 'INSTRUCTOR') {
          setUserRole('INSTRUCTOR');
        } else if (role.toUpperCase() === 'ADVISOR') {
          setUserRole('ADVISOR');
          determineAdvisorType(userData);
        } else if (role.toUpperCase() === 'REGISTRAR' || role.toUpperCase() === 'ADMIN') {
          setUserRole('REGISTRAR');
          setAdvisorType('UNIVERSITY'); // Registrars can view all declarations
        }
      } else {
        setLoading(false);
      }
    } catch (err) {
      console.error('Error fetching current user:', err);
      setLoading(false);
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

    // Registrars can approve all declarations
    if (userRole === 'REGISTRAR') {
      return true;
    }

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
      const pendingResponse = await fetch(`${backendBase}/api/major-minor-declarations/pending`, {
        credentials: 'include'
      });
      const pendingData = await pendingResponse.json();

      if (pendingData.success) {
        setPendingDeclarations(pendingData.declarations);
      }

      // Fetch all declarations for filtering
      const allResponse = await fetch(`${backendBase}/api/major-minor-declarations/all`, {
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

  const fetchWaivers = async () => {
    if (!currentUser?.userId) {
      setLoading(false);
      return;
    }

    try {
      const response = await fetch(`${backendBase}/api/waivers/instructor/${currentUser.userId}`, {
        credentials: 'include'
      });

      if (response.ok) {
        const waivers: TimeConflictWaiver[] = await response.json();
        setAllWaivers(waivers);
        setPendingWaivers(waivers.filter(w => w.status === 'PENDING'));
      } else {
        setError('Failed to fetch waivers');
        setAllWaivers([]);
        setPendingWaivers([]);
      }
    } catch (err) {
      setError('Error fetching waivers');
      console.error('Error:', err);
      setAllWaivers([]);
      setPendingWaivers([]);
    } finally {
      setLoading(false);
    }
  };

  const fetchAdvisorWaivers = async () => {
    if (!currentUser?.userId) {
      setLoading(false);
      return;
    }

    try {
      const response = await fetch(`${backendBase}/api/waivers/advisor/${currentUser.userId}`, {
        credentials: 'include'
      });

      if (response.ok) {
        const waivers: TimeConflictWaiver[] = await response.json();
        setAllWaivers(waivers);
        setPendingWaivers(waivers.filter(w => w.status === 'PENDING'));
      } else {
        setAllWaivers([]);
        setPendingWaivers([]);
      }
    } catch (err) {
      console.error('Error fetching advisor waivers:', err);
      setAllWaivers([]);
      setPendingWaivers([]);
    } finally {
      setLoading(false);
    }
  };

  const handleApprove = async () => {
    if (!selectedDeclaration) return;

    setProcessing(true);
    try {
      const response = await fetch(`${backendBase}/api/major-minor-declarations/${selectedDeclaration.declarationId}/approve`, {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
        },
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
      const response = await fetch(`${backendBase}/api/major-minor-declarations/${selectedDeclaration.declarationId}/reject`, {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
        },
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

  const canApproveWaiver = (waiver: TimeConflictWaiver): { canApprove: boolean; approvalType: 'enrolled' | 'conflicting' | null } => {
    if (!currentUser?.userId) return { canApprove: false, approvalType: null };

    const instructorName = `${currentUser.firstName || ''} ${currentUser.lastName || ''}`.trim();

    if (waiver.enrolledClass.instructor === instructorName && waiver.enrolledInstructorApproved === null) {
      return { canApprove: true, approvalType: 'enrolled' };
    }

    if (waiver.conflictingClass.instructor === instructorName && waiver.conflictingInstructorApproved === null) {
      return { canApprove: true, approvalType: 'conflicting' };
    }

    return { canApprove: false, approvalType: null };
  };

  const openWaiverApprovalModal = (waiver: TimeConflictWaiver) => {
    setSelectedWaiver(waiver);
    setComments('');
    setShowWaiverApprovalModal(true);
  };

  const openWaiverRejectionModal = (waiver: TimeConflictWaiver) => {
    setSelectedWaiver(waiver);
    setComments('');
    setShowWaiverRejectionModal(true);
  };

  const handleWaiverApprove = async () => {
    if (!selectedWaiver || !currentUser?.userId) return;

    const approval = canApproveWaiver(selectedWaiver);
    if (!approval.canApprove || !approval.approvalType) return;

    setProcessing(true);
    try {
      const endpoint = approval.approvalType === 'enrolled'
        ? `${backendBase}/api/waivers/${selectedWaiver.waiverId}/approve/enrolled-instructor`
        : `${backendBase}/api/waivers/${selectedWaiver.waiverId}/approve/conflicting-instructor`;

      const response = await fetch(endpoint, {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          instructorId: currentUser.userId
        }),
      });

      const data = await response.json();

      if (data.success) {
        setSuccess('Waiver approved successfully!');
        setShowWaiverApprovalModal(false);
        setComments('');
        setSelectedWaiver(null);
        fetchWaivers();
      } else {
        setError(data.message || 'Failed to approve waiver');
      }
    } catch (err) {
      setError('Error approving waiver');
      console.error('Error:', err);
    } finally {
      setProcessing(false);
    }
  };

  const handleWaiverReject = async () => {
    if (!selectedWaiver || !currentUser?.userId) return;

    // Check if this is an advisor rejection
    if (userRole === 'ADVISOR') {
      if (!comments.trim()) {
        alert('Please provide a reason for rejection.');
        return;
      }

      setProcessing(true);
      try {
        const response = await fetch(`${backendBase}/api/waivers/${selectedWaiver.waiverId}/reject/advisor`, {
          method: 'POST',
          credentials: 'include',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            advisorId: currentUser.userId,
            reason: comments.trim()
          }),
        });

        const data = await response.json();

        if (data.success) {
          setSuccess('Waiver rejected');
          setShowWaiverRejectionModal(false);
          setComments('');
          setSelectedWaiver(null);
          fetchAdvisorWaivers();
        } else {
          setError(data.message || 'Failed to reject waiver');
        }
      } catch (err) {
        setError('Error rejecting waiver');
        console.error('Error:', err);
      } finally {
        setProcessing(false);
      }
      return;
    }

    // Instructor rejection logic
    const approval = canApproveWaiver(selectedWaiver);
    if (!approval.canApprove || !approval.approvalType) return;

    setProcessing(true);
    try {
      const endpoint = approval.approvalType === 'enrolled'
        ? `${backendBase}/api/waivers/${selectedWaiver.waiverId}/reject/enrolled-instructor`
        : `${backendBase}/api/waivers/${selectedWaiver.waiverId}/reject/conflicting-instructor`;

      const response = await fetch(endpoint, {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          instructorId: currentUser.userId,
          reason: comments.trim() || null
        }),
      });

      const data = await response.json();

      if (data.success) {
        setSuccess('Waiver rejected');
        setShowWaiverRejectionModal(false);
        setComments('');
        setSelectedWaiver(null);
        fetchWaivers();
      } else {
        setError(data.message || 'Failed to reject waiver');
      }
    } catch (err) {
      setError('Error rejecting waiver');
      console.error('Error:', err);
    } finally {
      setProcessing(false);
    }
  };

  const closeModals = () => {
    setShowApprovalModal(false);
    setShowRejectionModal(false);
    setShowWaiverApprovalModal(false);
    setShowWaiverRejectionModal(false);
    setSelectedDeclaration(null);
    setSelectedWaiver(null);
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

  const getDegreeTypeDisplayName = (degreeType: string | null | undefined, declarationType?: string): string => {
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

  const getFilteredWaivers = () => {
    const filtered = selectedStatus === 'PENDING' ? pendingWaivers : allWaivers;
    return filtered;
  };

  if (loading) {
    return (
      <div className="advisor-approval">
        <div className="loading">Loading...</div>
      </div>
    );
  }

  if (!userRole) {
    return (
      <div className="advisor-approval">
        <div className="loading">Determining user role...</div>
      </div>
    );
  }

  // Instructor View - Waivers
  if (userRole === 'INSTRUCTOR') {
    return (
      <div className="advisor-approval">
        <div className="page-header">
          <h1>Registration Overrides</h1>
          <p>Review and approve time conflict waivers</p>
        </div>

        {error && (
          <div className="message error">
            <span className="message-icon"></span>
            {error}
          </div>
        )}

        {success && (
          <div className="message success">
            <span className="message-icon"></span>
            {success}
          </div>
        )}

        {/* Summary Cards */}
        <div className="summary-section">
          <div className="summary-cards">
            <div className="summary-card pending">
              <h3>Pending Approval</h3>
              <div className="count">{pendingWaivers.length}</div>
              <div className="description">Awaiting your review</div>
            </div>
            <div className="summary-card total">
              <h3>Total Waivers</h3>
              <div className="count">{allWaivers.length}</div>
              <div className="description">All time</div>
            </div>
          </div>
        </div>

        {/* Waivers List */}
        <div className="declarations-section">
          <div className="section-header">
            <h2>Time Conflict Waivers</h2>
            <div className="results-count">
              {getFilteredWaivers().length} waiver(s) found
            </div>
          </div>

          {getFilteredWaivers().length === 0 ? (
            <div className="no-declarations">
              <p>No waivers found matching your criteria.</p>
            </div>
          ) : (
            <div className="declarations-table-container">
              <table className="declarations-table">
                <thead>
                  <tr>
                    <th>Student</th>
                    <th>Enrolled Class</th>
                    <th>Conflicting Class</th>
                    <th>Reason</th>
                    <th>Status</th>
                    <th>Created</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {getFilteredWaivers().map(waiver => {
                    const approval = canApproveWaiver(waiver);
                    return (
                      <tr key={waiver.waiverId}>
                        <td>{waiver.studentName}</td>
                        <td>
                          {waiver.enrolledClass.department} {waiver.enrolledClass.courseNum}.{waiver.enrolledClass.section}
                          <br />
                          <small>{waiver.enrolledClass.title}</small>
                        </td>
                        <td>
                          {waiver.conflictingClass.department} {waiver.conflictingClass.courseNum}.{waiver.conflictingClass.section}
                          <br />
                          <small>{waiver.conflictingClass.title}</small>
                        </td>
                        <td>{waiver.reason || '—'}</td>
                        <td>
                          <span className={`status-badge ${getStatusColor(waiver.status)}`}>
                            {getStatusText(waiver.status)}
                          </span>
                        </td>
                        <td>{formatDate(waiver.createdAt)}</td>
                        <td>
                          {approval.canApprove ? (
                            <div className="action-buttons">
                              <button
                                className="btn-approve"
                                onClick={() => openWaiverApprovalModal(waiver)}
                                disabled={processing}
                              >
                                Approve
                              </button>
                              <button
                                className="btn-reject"
                                onClick={() => openWaiverRejectionModal(waiver)}
                                disabled={processing}
                              >
                                Reject
                              </button>
                            </div>
                          ) : (
                            <span className="text-muted">
                              {waiver.enrolledInstructorApproved !== null || waiver.conflictingInstructorApproved !== null
                                ? 'Already processed'
                                : 'Not your class'}
                            </span>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* Waiver Approval Modal */}
        {showWaiverApprovalModal && selectedWaiver && (
          <div className="modal-overlay" onClick={closeModals}>
            <div className="modal-content" onClick={(e) => e.stopPropagation()}>
              <h2>Approve Waiver</h2>
              <div className="modal-body">
                <p><strong>Student:</strong> {selectedWaiver.studentName}</p>
                <p><strong>Enrolled Class:</strong> {selectedWaiver.enrolledClass.department} {selectedWaiver.enrolledClass.courseNum}.{selectedWaiver.enrolledClass.section} - {selectedWaiver.enrolledClass.title}</p>
                <p><strong>Conflicting Class:</strong> {selectedWaiver.conflictingClass.department} {selectedWaiver.conflictingClass.courseNum}.{selectedWaiver.conflictingClass.section} - {selectedWaiver.conflictingClass.title}</p>
                {selectedWaiver.reason && <p><strong>Reason:</strong> {selectedWaiver.reason}</p>}
                <div className="form-group">
                  <label htmlFor="approvalComments">Comments (optional)</label>
                  <textarea
                    id="approvalComments"
                    value={comments}
                    onChange={(e) => setComments(e.target.value)}
                    rows={4}
                  />
                </div>
              </div>
              <div className="modal-actions">
                <button className="btn-secondary" onClick={closeModals} disabled={processing}>
                  Cancel
                </button>
                <button className="btn-primary" onClick={handleWaiverApprove} disabled={processing}>
                  {processing ? 'Processing...' : 'Approve'}
                </button>
              </div>
            </div>
          </div>
        )}

        {/* Waiver Rejection Modal */}
        {showWaiverRejectionModal && selectedWaiver && (
          <div className="modal-overlay" onClick={closeModals}>
            <div className="modal-content" onClick={(e) => e.stopPropagation()}>
              <h2>Reject Waiver</h2>
              <div className="modal-body">
                <p><strong>Student:</strong> {selectedWaiver.studentName}</p>
                <p><strong>Enrolled Class:</strong> {selectedWaiver.enrolledClass.department} {selectedWaiver.enrolledClass.courseNum}.{selectedWaiver.enrolledClass.section} - {selectedWaiver.enrolledClass.title}</p>
                <p><strong>Conflicting Class:</strong> {selectedWaiver.conflictingClass.department} {selectedWaiver.conflictingClass.courseNum}.{selectedWaiver.conflictingClass.section} - {selectedWaiver.conflictingClass.title}</p>
                <div className="form-group">
                  <label htmlFor="rejectionComments">Rejection Reason (required)</label>
                  <textarea
                    id="rejectionComments"
                    value={comments}
                    onChange={(e) => setComments(e.target.value)}
                    rows={4}
                    required
                  />
                </div>
              </div>
              <div className="modal-actions">
                <button className="btn-secondary" onClick={closeModals} disabled={processing}>
                  Cancel
                </button>
                <button className="btn-danger" onClick={handleWaiverReject} disabled={processing || !comments.trim()}>
                  {processing ? 'Processing...' : 'Reject'}
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    );
  }

  // Advisor/Registrar View - Declarations
  return (
    <div className="advisor-approval">
      <div className="page-header">
        <h1>{userRole === 'REGISTRAR' ? 'Declaration Review' : 'Advisor Approval'}</h1>
        <p>Review and approve major/minor declarations{userRole === 'REGISTRAR' ? ' and time conflict waivers' : ''}</p>
      </div>

      {error && (
        <div className="message error">
          <span className="message-icon"></span>
          {error}
        </div>
      )}

      {success && (
        <div className="message success">
          <span className="message-icon"></span>
          {success}
        </div>
      )}

      {/* Summary Cards */}
      <div className="summary-section">
        <div className="summary-cards">
          <div className="summary-card pending">
            <h3>Pending Declarations</h3>
            <div className="count">{pendingDeclarations.length}</div>
            <div className="description">Awaiting your review</div>
          </div>
          <div className="summary-card total">
            <h3>Total Declarations</h3>
            <div className="count">{allDeclarations.length}</div>
            <div className="description">All time</div>
          </div>
          <div className="summary-card pending">
            <h3>Pending Waivers</h3>
            <div className="count">{pendingWaivers.length}</div>
            <div className="description">Awaiting your review</div>
          </div>
          <div className="summary-card total">
            <h3>Total Waivers</h3>
            <div className="count">{allWaivers.length}</div>
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
                          <div className="change-badge" style={{ color: 'orange', fontWeight: 'bold', marginTop: '4px' }}>
                            Change Request
                          </div>
                        )}
                      </div>
                    </td>
                    <td className="subject-cell">
                      {declaration.subjectCode}
                      {isChangeRequest(declaration) && getFromSubject(declaration) && (
                        <div style={{ fontSize: '0.85em', color: '#666', marginTop: '2px' }}>
                          From: {getFromSubject(declaration)}
                        </div>
                      )}
                    </td>
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

      {/* Waivers List for Advisor */}
      <div className="declarations-section" style={{ marginTop: 32 }}>
        <div className="section-header">
          <h2>Time Conflict Waivers</h2>
          <div className="results-count">
            {getFilteredWaivers().length} waiver(s) found
          </div>
        </div>

        {getFilteredWaivers().length === 0 ? (
          <div className="no-declarations">
            <p>No waivers found.</p>
          </div>
        ) : (
          <div className="declarations-table-container">
            <table className="declarations-table">
              <thead>
                <tr>
                  <th>Student</th>
                  <th>Enrolled Class</th>
                  <th>Conflicting Class</th>
                  <th>Reason</th>
                  <th>Status</th>
                  <th>Created</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {getFilteredWaivers().map(waiver => (
                  <tr key={waiver.waiverId}>
                    <td>{waiver.studentName}</td>
                    <td>
                      {waiver.enrolledClass.department} {waiver.enrolledClass.courseNum}.{waiver.enrolledClass.section}
                      <br />
                      <small>{waiver.enrolledClass.title}</small>
                    </td>
                    <td>
                      {waiver.conflictingClass.department} {waiver.conflictingClass.courseNum}.{waiver.conflictingClass.section}
                      <br />
                      <small>{waiver.conflictingClass.title}</small>
                    </td>
                    <td>{waiver.reason || '—'}</td>
                    <td>
                      <span className={`status-badge ${getStatusColor(waiver.status)}`}>
                        {getStatusText(waiver.status)}
                      </span>
                    </td>
                    <td>{formatDate(waiver.createdAt)}</td>
                    <td>
                      {waiver.status === 'PENDING' && waiver.advisorApproved === null ? (
                        <div className="action-buttons">
                          <button
                            className="btn-approve"
                            onClick={() => openWaiverApprovalModal(waiver)}
                            disabled={processing}
                          >
                            Approve
                          </button>
                          <button
                            className="btn-reject"
                            onClick={() => openWaiverRejectionModal(waiver)}
                            disabled={processing}
                          >
                            Reject
                          </button>
                        </div>
                      ) : (
                        <span className="text-muted">
                          {waiver.advisorApproved === true ? 'Approved' : waiver.advisorApproved === false ? 'Rejected' : 'Already processed'}
                        </span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Waiver Approval Modal for Advisor */}
      {showWaiverApprovalModal && selectedWaiver && userRole === 'ADVISOR' && (
        <div className="modal-overlay" onClick={closeModals}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <h2>Approve Waiver</h2>
            <div className="modal-body">
              <p><strong>Student:</strong> {selectedWaiver.studentName}</p>
              <p><strong>Enrolled Class:</strong> {selectedWaiver.enrolledClass.department} {selectedWaiver.enrolledClass.courseNum}.{selectedWaiver.enrolledClass.section} - {selectedWaiver.enrolledClass.title}</p>
              <p><strong>Conflicting Class:</strong> {selectedWaiver.conflictingClass.department} {selectedWaiver.conflictingClass.courseNum}.{selectedWaiver.conflictingClass.section} - {selectedWaiver.conflictingClass.title}</p>
              {selectedWaiver.reason && <p><strong>Reason:</strong> {selectedWaiver.reason}</p>}
              <div className="form-group">
                <label htmlFor="approvalComments">Comments (optional)</label>
                <textarea
                  id="approvalComments"
                  value={comments}
                  onChange={(e) => setComments(e.target.value)}
                  rows={4}
                />
              </div>
            </div>
            <div className="modal-actions">
              <button className="btn-secondary" onClick={closeModals} disabled={processing}>
                Cancel
              </button>
              <button className="btn-primary" onClick={handleWaiverApprove} disabled={processing}>
                {processing ? 'Processing...' : 'Approve'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Waiver Rejection Modal for Advisor */}
      {showWaiverRejectionModal && selectedWaiver && userRole === 'ADVISOR' && (
        <div className="modal-overlay" onClick={closeModals}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <h2>Reject Waiver</h2>
            <div className="modal-body">
              <p><strong>Student:</strong> {selectedWaiver.studentName}</p>
              <p><strong>Enrolled Class:</strong> {selectedWaiver.enrolledClass.department} {selectedWaiver.enrolledClass.courseNum}.{selectedWaiver.enrolledClass.section} - {selectedWaiver.enrolledClass.title}</p>
              <p><strong>Conflicting Class:</strong> {selectedWaiver.conflictingClass.department} {selectedWaiver.conflictingClass.courseNum}.{selectedWaiver.conflictingClass.section} - {selectedWaiver.conflictingClass.title}</p>
              <div className="form-group">
                <label htmlFor="rejectionComments">Rejection Reason (required)</label>
                <textarea
                  id="rejectionComments"
                  value={comments}
                  onChange={(e) => setComments(e.target.value)}
                  rows={4}
                  required
                />
              </div>
            </div>
            <div className="modal-actions">
              <button className="btn-secondary" onClick={closeModals} disabled={processing}>
                Cancel
              </button>
              <button className="btn-danger" onClick={handleWaiverReject} disabled={processing || !comments.trim()}>
                {processing ? 'Processing...' : 'Reject'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Approval Modal */}
      {showApprovalModal && selectedDeclaration && (
        <div className="modal-overlay">
          <div className="modal-content">
            <div className="modal-header">
              <h3>Approve Declaration</h3>
              <button className="close-button" onClick={closeModals}>×</button>
            </div>
            <div className="modal-body">
              <div className="declaration-summary">
                {isChangeRequest(selectedDeclaration) && (
                  <div style={{ backgroundColor: '#fff3cd', border: '1px solid #ffc107', borderRadius: '4px', padding: '10px', marginBottom: '15px' }}>
                    <p style={{ margin: 0, fontWeight: 'bold', color: '#856404' }}>
                      CHANGE REQUEST
                    </p>
                    {getFromSubject(selectedDeclaration) && (
                      <p style={{ margin: '5px 0 0 0', color: '#856404' }}>
                        Changing from <strong>{getFromSubject(selectedDeclaration)}</strong> to <strong>{selectedDeclaration.subjectCode}</strong>
                      </p>
                    )}
                  </div>
                )}
                <p><strong>Student:</strong> {selectedDeclaration.studentNetId}</p>
                <p><strong>Declaration:</strong> {selectedDeclaration.subjectCode} {selectedDeclaration.declarationType}</p>
                <p><strong>Degree Type:</strong> {getDegreeTypeDisplayName(selectedDeclaration.degreeType, selectedDeclaration.declarationType)}</p>
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
            <div className="modal-actions">
              <button className="btn-secondary" onClick={closeModals}>
                Cancel
              </button>
              <button
                className="btn-primary"
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
          <div className="modal-content">
            <div className="modal-header">
              <h3>Reject Declaration</h3>
              <button className="close-button" onClick={closeModals}>×</button>
            </div>
            <div className="modal-body">
              <div className="declaration-summary">
                {isChangeRequest(selectedDeclaration) && (
                  <div style={{ backgroundColor: '#fff3cd', border: '1px solid #ffc107', borderRadius: '4px', padding: '10px', marginBottom: '15px' }}>
                    <p style={{ margin: 0, fontWeight: 'bold', color: '#856404' }}>
                      CHANGE REQUEST
                    </p>
                    {getFromSubject(selectedDeclaration) && (
                      <p style={{ margin: '5px 0 0 0', color: '#856404' }}>
                        Changing from <strong>{getFromSubject(selectedDeclaration)}</strong> to <strong>{selectedDeclaration.subjectCode}</strong>
                      </p>
                    )}
                  </div>
                )}
                <p><strong>Student:</strong> {selectedDeclaration.studentNetId}</p>
                <p><strong>Declaration:</strong> {selectedDeclaration.subjectCode} {selectedDeclaration.declarationType}</p>
                <p><strong>Degree Type:</strong> {getDegreeTypeDisplayName(selectedDeclaration.degreeType, selectedDeclaration.declarationType)}</p>
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
            <div className="modal-actions">
              <button className="btn-secondary" onClick={closeModals}>
                Cancel
              </button>
              <button
                className="btn-danger"
                onClick={handleReject}
                disabled={processing || !comments.trim()}
              >
                {processing ? 'Rejecting...' : 'Reject Declaration'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Global Loading Modal */}
      <LoadingModal isOpen={processing} message="Processing request..." />
    </div>
  );
};

export default AdvisorApproval;
