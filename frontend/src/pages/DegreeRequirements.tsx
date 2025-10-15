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
  
  // Filter states
  const [selectedSubject, setSelectedSubject] = useState('');
  const [selectedDegreeType, setSelectedDegreeType] = useState('');
  const [selectedTerm, setSelectedTerm] = useState('');
  const [showRestrictedOnly, setShowRestrictedOnly] = useState(false);
  
  // Available filter options
  const [availableSubjects, setAvailableSubjects] = useState<string[]>([]);
  const [availableDegreeTypes, setAvailableDegreeTypes] = useState<string[]>([]);
  const [availableTerms, setAvailableTerms] = useState<string[]>([]);

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

  const fetchRequirements = async () => {
    try {
      const response = await fetch('/api/degree-requirements');
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
      const response = await fetch(`/api/degree-requirements/subject/${subjectCode}`);
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
      const response = await fetch('/api/degree-requirements/restricted');
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
          <span className="message-icon">⚠️</span>
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
          <div className="results-count">
            {getFilteredRequirements().length} requirement(s) found
          </div>
        </div>

        {getFilteredRequirements().length === 0 ? (
          <div className="no-requirements">
            <p>No degree requirements found matching your criteria.</p>
          </div>
        ) : (
          <div className="requirements-grid">
            {getFilteredRequirements().map(requirement => (
              <div key={requirement.requirementId} className="requirement-card">
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
                    <h4>Admission Requirements</h4>
                    <div className="requirement-content">
                      {(() => {
                        const admissionReqs = parseJsonField(requirement.admissionRequirements);
                        if (admissionReqs) {
                          return (
                            <div>
                              {admissionReqs.required_courses && (
                                <div>
                                  <strong>Required Courses:</strong>
                                  <ul>
                                    {admissionReqs.required_courses.map((course: any, index: number) => (
                                      <li key={index}>
                                        {course.course_code} (Min Grade: {course.min_grade})
                                      </li>
                                    ))}
                                  </ul>
                                </div>
                              )}
                              {admissionReqs.minimum_gpa && (
                                <p><strong>Minimum GPA:</strong> {admissionReqs.minimum_gpa}</p>
                              )}
                              {admissionReqs.additional_requirements && (
                                <div>
                                  <strong>Additional Requirements:</strong>
                                  <ul>
                                    {admissionReqs.additional_requirements.map((req: string, index: number) => (
                                      <li key={index}>{req}</li>
                                    ))}
                                  </ul>
                                </div>
                              )}
                            </div>
                          );
                        }
                        return <p>{requirement.admissionRequirements}</p>;
                      })()}
                    </div>
                  </div>
                )}

                {/* Course Requirements */}
                {requirement.courseRequirements && (
                  <div className="requirement-section">
                    <h4>Course Requirements</h4>
                    <div className="requirement-content">
                      {(() => {
                        const courseReqs = parseJsonField(requirement.courseRequirements);
                        if (courseReqs) {
                          return (
                            <div>
                              {courseReqs.core_courses && (
                                <div>
                                  <strong>Core Courses:</strong>
                                  <div className="course-list">
                                    {courseReqs.core_courses.map((course: string, index: number) => (
                                      <span key={index} className="course-tag">{course}</span>
                                    ))}
                                  </div>
                                </div>
                              )}
                              {courseReqs.elective_courses && (
                                <div>
                                  <strong>Elective Courses:</strong>
                                  <p>{courseReqs.elective_courses.credits_required} credits required</p>
                                  {courseReqs.elective_courses.min_300_level && (
                                    <p>Minimum {courseReqs.elective_courses.min_300_level} credits at 300+ level</p>
                                  )}
                                </div>
                              )}
                            </div>
                          );
                        }
                        return <p>{requirement.courseRequirements}</p>;
                      })()}
                    </div>
                  </div>
                )}

                {/* SBC Requirements */}
                {requirement.sbcRequirements && (
                  <div className="requirement-section">
                    <h4>SBC Requirements</h4>
                    <div className="requirement-content">
                      {(() => {
                        const sbcReqs = parseJsonField(requirement.sbcRequirements);
                        if (sbcReqs) {
                          return (
                            <div className="sbc-grid">
                              {sbcReqs.map((sbc: any, index: number) => (
                                <div key={index} className="sbc-item">
                                  <span className="sbc-code">{sbc.category}</span>
                                  <span className="sbc-credits">{sbc.credits_required} credits</span>
                                </div>
                              ))}
                            </div>
                          );
                        }
                        return <p>{requirement.sbcRequirements}</p>;
                      })()}
                    </div>
                  </div>
                )}

                <div className="requirement-footer">
                  <span className="created-by">Created by: {requirement.createdBy}</span>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

export default DegreeRequirements;
