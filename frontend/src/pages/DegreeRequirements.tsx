import React, { useState, useEffect } from 'react';
import './DegreeRequirements.css';

interface DegreeRequirement {
  requirementId: number;
  subjectCode: string;
  degreeType: 'BA' | 'BS' | 'MINOR';
  requirementsVersion: string;
  effectiveTerm: string;
  totalCreditsRequired: number;
  isRestricted: boolean;
  gpaRequirement?: number;
  admissionRequirements?: string;
  courseRequirements?: string;
  prerequisiteCourses?: string;
  sbcRequirements?: string;
  createdAt: string;
  createdBy: string;
  isActive: boolean;
}

const DegreeRequirements: React.FC = () => {
  const [requirements, setRequirements] = useState<DegreeRequirement[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [expandedRequirementId, setExpandedRequirementId] = useState<number | null>(null);
  const [expandedSections, setExpandedSections] = useState<Set<string>>(new Set());
  
  // Filter states
  const [selectedSubject, setSelectedSubject] = useState('');
  const [selectedDegreeType, setSelectedDegreeType] = useState('');
  const [selectedTerm, setSelectedTerm] = useState('');
  const [showRestrictedOnly, setShowRestrictedOnly] = useState(false);
  
  // Available filter options
  const [availableSubjects, setAvailableSubjects] = useState<string[]>([]);
  const [availableDegreeTypes, setAvailableDegreeTypes] = useState<string[]>([]);
  const [availableTerms, setAvailableTerms] = useState<string[]>([]);
  
  const handleRequirementClick = (requirementId: number) => {
    setExpandedRequirementId(expandedRequirementId === requirementId ? null : requirementId);
  };
  
  const handleSectionToggle = (sectionKey: string, e?: React.MouseEvent) => {
    if (e) {
      e.stopPropagation();
    }
    setExpandedSections(prev => {
      const newSet = new Set(prev);
      if (newSet.has(sectionKey)) {
        newSet.delete(sectionKey);
      } else {
        newSet.add(sectionKey);
      }
      return newSet;
    });
  };
  
  const isSectionExpanded = (sectionKey: string) => {
    return expandedSections.has(sectionKey);
  };

  useEffect(() => {
    fetchRequirements();
  }, []);

  useEffect(() => {
    // Update filter options when requirements change
    const subjects = [...new Set(requirements.map(req => req.subjectCode))].sort();
    const degreeTypes = [...new Set(requirements.map(req => req.degreeType))].sort();
    const terms = [...new Set(requirements.map(req => req.effectiveTerm))].sort();
    
    setAvailableSubjects(subjects);
    setAvailableDegreeTypes(degreeTypes);
    setAvailableTerms(terms);
  }, [requirements]);

  // TP Used cursor to fetch degree requirements data
  const fetchRequirements = async () => {
    try {
      const backendBase = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8080';
      const response = await fetch(`${backendBase}/api/degree-requirements`, {
        credentials: 'include'
      });
      const data = await response.json();
      
      if (data.success) {
        setRequirements(data.requirements);
      } else {
        setError('Failed to fetch degree requirements');
      }
    } catch (err) {
      setError('Error fetching degree requirements');
      console.error('Error:', err);
    } finally {
      setLoading(false);
    }
  };

  const fetchRequirementsBySubject = async (subjectCode: string) => {
    try {
      const backendBase = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8080';
      const response = await fetch(`${backendBase}/api/degree-requirements/subject/${subjectCode}`, {
        credentials: 'include'
      });
      const data = await response.json();
      
      if (data.success) {
        setRequirements(data.requirements);
      } else {
        setError('Failed to fetch degree requirements');
      }
    } catch (err) {
      setError('Error fetching degree requirements');
      console.error('Error:', err);
    }
  };

  const fetchRequirementsByTerm = async (effectiveTerm: string) => {
    try {
      const response = await fetch(`/api/degree-requirements/term/${effectiveTerm}`);
      const data = await response.json();
      
      if (data.success) {
        setRequirements(data.requirements);
      } else {
        setError('Failed to fetch degree requirements');
      }
    } catch (err) {
      setError('Error fetching degree requirements');
      console.error('Error:', err);
    }
  };

  const fetchRestrictedRequirements = async () => {
    try {
      const backendBase = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8080';
      const response = await fetch(`${backendBase}/api/degree-requirements/restricted`, {
        credentials: 'include'
      });
      const data = await response.json();
      
      if (data.success) {
        setRequirements(data.requirements);
      } else {
        setError('Failed to fetch restricted requirements');
      }
    } catch (err) {
      setError('Error fetching restricted requirements');
      console.error('Error:', err);
    }
  };

  const handleSubjectFilter = (subjectCode: string) => {
    setSelectedSubject(subjectCode);
    setSelectedDegreeType('');
    setSelectedTerm('');
    setShowRestrictedOnly(false);
    
    if (subjectCode) {
      fetchRequirementsBySubject(subjectCode);
    } else {
      fetchRequirements();
    }
  };

  const handleTermFilter = (effectiveTerm: string) => {
    setSelectedTerm(effectiveTerm);
    setSelectedSubject('');
    setSelectedDegreeType('');
    setShowRestrictedOnly(false);
    
    if (effectiveTerm) {
      fetchRequirementsByTerm(effectiveTerm);
    } else {
      fetchRequirements();
    }
  };

  const handleRestrictedFilter = () => {
    setShowRestrictedOnly(!showRestrictedOnly);
    setSelectedSubject('');
    setSelectedDegreeType('');
    setSelectedTerm('');
    
    if (!showRestrictedOnly) {
      fetchRestrictedRequirements();
    } else {
      fetchRequirements();
    }
  };

  const clearFilters = () => {
    setSelectedSubject('');
    setSelectedDegreeType('');
    setSelectedTerm('');
    setShowRestrictedOnly(false);
    fetchRequirements();
  };

  // TP Used cursor to delete degree requirement
  const deleteRequirement = async (requirementId: number) => {
    if (!window.confirm('Are you sure you want to delete this degree requirement? This action cannot be undone.')) {
      return;
    }

    try {
      const backendBase = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8080';
      const response = await fetch(`${backendBase}/api/degree-requirements/${requirementId}`, {
        method: 'DELETE',
        credentials: 'include'
      });

      if (response.ok) {
        // Refresh the requirements list
        fetchRequirements();
        setError(null);
      } else {
        setError('Failed to delete degree requirement');
      }
    } catch (err) {
      setError('Error deleting degree requirement');
      console.error('Error:', err);
    }
  };

  const deleteAllRequirements = async () => {
    if (!window.confirm('Are you sure you want to delete ALL degree requirements? This action cannot be undone.')) {
      return;
    }

    try {
      const backendBase = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8080';
      const response = await fetch(`${backendBase}/api/degree-requirements/delete-all`, {
        method: 'DELETE',
        credentials: 'include'
      });

      if (response.ok) {
        // Refresh the requirements list
        fetchRequirements();
        setError(null);
      } else {
        setError('Failed to delete all degree requirements');
      }
    } catch (err) {
      setError('Error deleting all degree requirements');
      console.error('Error:', err);
    }
  };

  const getFilteredRequirements = () => {
    let filtered = requirements;

    if (selectedDegreeType) {
      filtered = filtered.filter(req => req.degreeType === selectedDegreeType);
    }

    return filtered;
  };

  const getDegreeTypeDisplayName = (degreeType: string) => {
    switch (degreeType) {
      case 'BA': return 'Bachelor of Arts';
      case 'BS': return 'Bachelor of Science';
      case 'MINOR': return 'Minor';
      default: return degreeType;
    }
  };

  // TP Used cursor to parse JSON fields safely
  const parseJsonField = (jsonString?: string) => {
    if (!jsonString) return null;
    try {
      return JSON.parse(jsonString);
    } catch {
      return null;
    }
  };

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric'
    });
  };

  if (loading) {
    return (
      <div className="degree-requirements">
        <div className="loading">Loading degree requirements...</div>
      </div>
    );
  }

  return (
    <div className="degree-requirements">
      <div className="page-header">
        <h1>Degree Requirements</h1>
        <p>View degree requirements for majors and minors</p>
      </div>

      {error && (
        <div className="message error">
          <span className="message-icon"></span>
          {error}
        </div>
      )}

      {/* Filters */}
      <div className="filters-section">
        <h2>Filters</h2>
        <div className="filters-grid">
          <div className="filter-group">
            <label htmlFor="subjectFilter">Subject Code</label>
            <select
              id="subjectFilter"
              value={selectedSubject}
              onChange={(e) => handleSubjectFilter(e.target.value)}
            >
              <option value="">All Subjects</option>
              {availableSubjects.map(subject => (
                <option key={subject} value={subject}>{subject}</option>
              ))}
            </select>
          </div>

          <div className="filter-group">
            <label htmlFor="degreeTypeFilter">Degree Type</label>
            <select
              id="degreeTypeFilter"
              value={selectedDegreeType}
              onChange={(e) => setSelectedDegreeType(e.target.value)}
              disabled={!selectedSubject}
            >
              <option value="">All Types</option>
              {availableDegreeTypes.map(degreeType => (
                <option key={degreeType} value={degreeType}>
                  {getDegreeTypeDisplayName(degreeType)}
                </option>
              ))}
            </select>
          </div>

          <div className="filter-group">
            <label htmlFor="termFilter">Effective Term</label>
            <select
              id="termFilter"
              value={selectedTerm}
              onChange={(e) => handleTermFilter(e.target.value)}
            >
              <option value="">All Terms</option>
              {availableTerms.map(term => (
                <option key={term} value={term}>{term}</option>
              ))}
            </select>
          </div>

          <div className="filter-group">
            <label>
              <input
                type="checkbox"
                checked={showRestrictedOnly}
                onChange={handleRestrictedFilter}
              />
              Restricted Programs Only
            </label>
          </div>

          <div className="filter-group">
            <button className="clear-filters-button" onClick={clearFilters}>
              Clear All Filters
            </button>
          </div>
        </div>
      </div>

      {/* Requirements List */}
      <div className="requirements-section">
        <div className="section-header">
          <h2>Degree Requirements</h2>
          <div className="header-actions">
            <div className="results-count">
              {getFilteredRequirements().length} requirement(s) found
            </div>
            {getFilteredRequirements().length > 0 && (
              <button 
                className="delete-all-button"
                onClick={deleteAllRequirements}
                title="Delete all requirements"
              >
                Delete All
              </button>
            )}
          </div>
        </div>

        {getFilteredRequirements().length === 0 ? (
          <div className="no-requirements">
            <p>No degree requirements found matching your criteria.</p>
          </div>
        ) : (
          <div className="requirements-grid">
            {getFilteredRequirements().map(requirement => {
              const isExpanded = expandedRequirementId === requirement.requirementId;
              
              return (
                <div 
                  key={requirement.requirementId} 
                  className={`requirement-card ${isExpanded ? 'expanded' : 'collapsed'}`}
                  onClick={() => handleRequirementClick(requirement.requirementId)}
                >
                  <div className="requirement-header">
                    <div className="requirement-title">
                      <h3>{requirement.subjectCode} {getDegreeTypeDisplayName(requirement.degreeType)}</h3>
                      <div className="requirement-badges">
                        {requirement.isRestricted && (
                          <span className="badge restricted">Restricted</span>
                        )}
                        <span className="badge version">{requirement.requirementsVersion}</span>
                      </div>
                    </div>
                    <div className="requirement-meta">
                      <span className="effective-term">Effective: {requirement.effectiveTerm}</span>
                      <span className="created-date">Created: {formatDate(requirement.createdAt)}</span>
                    </div>
                  </div>

                  {isExpanded && (
                    <div className="requirement-content-wrapper">
                      <div className="requirement-details">
                  <div className="detail-row">
                    <span className="detail-label">Total Credits:</span>
                    <span className="detail-value">{requirement.totalCreditsRequired}</span>
                  </div>
                  
                  {requirement.gpaRequirement && (
                    <div className="detail-row">
                      <span className="detail-label">GPA Requirement:</span>
                      <span className="detail-value">{requirement.gpaRequirement}</span>
                    </div>
                  )}

                  <div className="detail-row">
                    <span className="detail-label">Program Type:</span>
                    <span className="detail-value">
                      {requirement.isRestricted ? 'Restricted' : 'Open'}
                    </span>
                  </div>
                </div>

                {/* Admission Requirements */}
                {requirement.admissionRequirements && (
                  <div className="requirement-section">
                    <div 
                      className="requirement-section-header"
                      onClick={(e) => handleSectionToggle(`admission-${requirement.requirementId}`, e)}
                    >
                      <h4>Admission Requirements</h4>
                      <span className="section-toggle-icon">
                        {isSectionExpanded(`admission-${requirement.requirementId}`) ? '▼' : '▶'}
                      </span>
                    </div>
                    {isSectionExpanded(`admission-${requirement.requirementId}`) && (
                      <div className="requirement-content">
                      {(() => {
                        const admissionReqs = parseJsonField(requirement.admissionRequirements);
                        if (admissionReqs) {
                          return (
                            <div>
                              {admissionReqs.core_courses && (
                                <div>
                                  <strong>Core Courses:</strong>
                                  <div className="course-list">
                                    {admissionReqs.core_courses.map((course: any, index: number) => (
                                      <span key={index} className="course-tag">{course.code}</span>
                                    ))}
                                  </div>
                                </div>
                              )}
                              {admissionReqs.minimum_cumulative_gpa && (
                                <p><strong>Minimum Cumulative GPA:</strong> {admissionReqs.minimum_cumulative_gpa}</p>
                              )}
                              {admissionReqs.minimum_core_avg_gpa && (
                                <p><strong>Minimum Core Avg GPA:</strong> {admissionReqs.minimum_core_avg_gpa}</p>
                              )}
                              {admissionReqs.minimum_core_grade && (
                                <p><strong>Minimum Core Grade:</strong> {admissionReqs.minimum_core_grade}</p>
                              )}
                              {admissionReqs.retake_in_core !== undefined && (
                                <p><strong>Retake Allowed:</strong> {admissionReqs.retake_in_core}</p>
                              )}
                              {admissionReqs.excluded_majors && (
                                <div>
                                  <strong>Excluded Majors:</strong>
                                  <div className="course-list">
                                    {admissionReqs.excluded_majors.map((maj: any, index: number) => (
                                      <span key={index} className="course-tag">{maj.subject_code}</span>
                                    ))}
                                  </div>
                                </div>
                              )}
                              {admissionReqs.note && (
                                <p><em>{admissionReqs.note}</em></p>
                              )}
                            </div>
                          );
                        }
                        return <p>{requirement.admissionRequirements}</p>;
                      })()}
                      </div>
                    )}
                  </div>
                )}

                {/* Course Requirements */}
                {requirement.courseRequirements && (
                  <div className="requirement-section">
                    <div 
                      className="requirement-section-header"
                      onClick={(e) => handleSectionToggle(`course-${requirement.requirementId}`, e)}
                    >
                      <h4>Course Requirements</h4>
                      <span className="section-toggle-icon">
                        {isSectionExpanded(`course-${requirement.requirementId}`) ? '▼' : '▶'}
                      </span>
                    </div>
                    {isSectionExpanded(`course-${requirement.requirementId}`) && (
                      <div className="requirement-content">
                      {(() => {
                        const courseReqs = parseJsonField(requirement.courseRequirements);
                        if (courseReqs && courseReqs.groups) {
                          return (
                            <div className="groups-container">
                              {courseReqs.groups.map((group: any, groupIndex: number) => (
                                <div key={groupIndex} className="course-group">
                                  <div className="group-header">
                                    <strong>{group.id?.replace(/_/g, ' ').replace(/\b\w/g, (l: string) => l.toUpperCase())}</strong>
                                    <span className="group-type">{group.type?.replace(/_/g, ' ')}</span>
                                    {group.credits_required && (
                                      <span className="credits-badge">{group.credits_required} credits</span>
                                    )}
                                  </div>
                                  
                                  {group.courses && (
                                    <div className="course-list">
                                      {group.courses.map((course: any, courseIndex: number) => (
                                        <span key={courseIndex} className="course-tag">{course.code}</span>
                                      ))}
                                    </div>
                                  )}
                                  
                                  {group.sequences && (
                                    <div className="sequences-container">
                                      {group.sequences.map((sequence: any[], seqIndex: number) => (
                                        <div key={seqIndex} className="sequence-module">
                                          <div className="sequence-path">
                                            {sequence.map((course: any, courseIndex: number) => (
                                              <React.Fragment key={courseIndex}>
                                                <span className="course-tag sequence-course">{course.code}</span>
                                                {courseIndex < sequence.length - 1 && (
                                                  <span className="sequence-arrow">→</span>
                                                )}
                                              </React.Fragment>
                                            ))}
                                          </div>
                                        </div>
                                      ))}
                                    </div>
                                  )}
                                  
                                  {group.additional_courses && (
                                    <div>
                                      <strong>Additional Options:</strong>
                                      <div className="course-list">
                                        {group.additional_courses.map((course: any, courseIndex: number) => (
                                          <span key={courseIndex} className="course-tag">{course.code}</span>
                                        ))}
                                      </div>
                                    </div>
                                  )}
                                  
                                  {group.excluded_courses && (
                                    <div className="excluded-courses">
                                      <em>Excluded: </em>
                                      {group.excluded_courses.map((course: any, courseIndex: number) => (
                                        <span key={courseIndex} className="excluded-tag">{course.code}</span>
                                      ))}
                                    </div>
                                  )}
                                  
                                  {group.note && (
                                    <p className="group-note"><em>{group.note}</em></p>
                                  )}
                                </div>
                              ))}
                            </div>
                          );
                        }
                        return <p>{requirement.courseRequirements}</p>;
                      })()}
                      </div>
                    )}
                  </div>
                )}

                {/* SBC Requirements */}
                {requirement.sbcRequirements && (
                  <div className="requirement-section">
                    <div 
                      className="requirement-section-header"
                      onClick={(e) => handleSectionToggle(`sbc-${requirement.requirementId}`, e)}
                    >
                      <h4>SBC Categories</h4>
                      <span className="section-toggle-icon">
                        {isSectionExpanded(`sbc-${requirement.requirementId}`) ? '▼' : '▶'}
                      </span>
                    </div>
                    {isSectionExpanded(`sbc-${requirement.requirementId}`) && (
                      <div className="requirement-content">
                      {(() => {
                        const sbcReqs = parseJsonField(requirement.sbcRequirements);
                        if (sbcReqs && Array.isArray(sbcReqs)) {
                          return (
                            <div className="sbc-container">
                              {sbcReqs.map((sbc: any, index: number) => {
                                const hasCourses = sbc.required_major_courses || sbc.optional_major_courses || 
                                                  sbc.required_major_courses_partial || sbc.optional_major_courses_partial;
                                
                                if (!hasCourses) {
                                  return (
                                    <div key={index} className="sbc-item-simple">
                                      <span className="sbc-code">{sbc.sbc}</span>
                                    </div>
                                  );
                                }
                                
                                return (
                                  <div key={index} className="sbc-item-detail">
                                    <strong className="sbc-code">{sbc.sbc}</strong>
                                    {sbc.required_major_courses && (
                                      <div className="sbc-courses-section">
                                        <em>Required:</em>
                                        <div className="course-list">
                                          {sbc.required_major_courses.map((course: any, courseIndex: number) => (
                                            <span key={courseIndex} className="course-tag">{course.code}</span>
                                          ))}
                                        </div>
                                      </div>
                                    )}
                                    {sbc.optional_major_courses && (
                                      <div className="sbc-courses-section">
                                        <em>Optional:</em>
                                        <div className="course-list">
                                          {sbc.optional_major_courses.map((course: any, courseIndex: number) => (
                                            <span key={courseIndex} className="course-tag optional">{course.code}</span>
                                          ))}
                                        </div>
                                      </div>
                                    )}
                                    {sbc.required_major_courses_partial && (
                                      <div className="sbc-courses-section">
                                        <em>Required (Partial):</em>
                                        <div className="course-list">
                                          {sbc.required_major_courses_partial.map((course: any, courseIndex: number) => (
                                            <span key={courseIndex} className="course-tag partial">{course.code}</span>
                                          ))}
                                        </div>
                                      </div>
                                    )}
                                    {sbc.optional_major_courses_partial && (
                                      <div className="sbc-courses-section">
                                        <em>Optional (Partial):</em>
                                        <div className="course-list">
                                          {sbc.optional_major_courses_partial.map((course: any, courseIndex: number) => (
                                            <span key={courseIndex} className="course-tag optional partial">{course.code}</span>
                                          ))}
                                        </div>
                                      </div>
                                    )}
                                  </div>
                                );
                              })}
                            </div>
                          );
                        }
                        return <p>{requirement.sbcRequirements}</p>;
                      })()}
                      </div>
                    )}
                  </div>
                )}

                      <div className="requirement-footer">
                        <span className="created-by">Created by: {requirement.createdBy}</span>
                        <button 
                          className="delete-button"
                          onClick={(e) => {
                            e.stopPropagation();
                            deleteRequirement(requirement.requirementId);
                          }}
                          title="Delete this requirement"
                        >
                          Delete
                        </button>
                      </div>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
};

export default DegreeRequirements;
