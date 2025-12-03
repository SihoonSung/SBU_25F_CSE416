/*
 * TP: Used Cursor to implement unified data import interface for classes, users, and requirements
 */

import React, { useState, useEffect } from 'react';
import './DataImport.css';
import LoadingModal from '../components/LoadingModal';
import { getBackendBase, getDefaultSemester, getDefaultYear } from '../constants';

/**
 * Data Import Component
 * Unified interface for importing different types of data.
 */

type TabType = 'classes' | 'users' | 'requirements' | 'calendar';

const DataImport: React.FC = () => {
  const [activeTab, setActiveTab] = useState<TabType>('classes');
  const [userRoles, setUserRoles] = useState<string[]>([]);
  const backendBase = getBackendBase();

  useEffect(() => {
    // Fetch user roles to determine access
    const fetchUserRoles = async () => {
      try {
        const response = await fetch(`${backendBase}/api/auth/me`, { credentials: 'include' });
        if (response.ok) {
          const data = await response.json();
          const roles = Array.isArray(data.roles) ? data.roles : (data.user?.role ? [String(data.user.role).toUpperCase()] : []);
          setUserRoles(roles);
        }
      } catch (error) {
        console.error('Error fetching user roles:', error);
      }
    };
    fetchUserRoles();
  }, [backendBase]);

  const isRegistrar = userRoles.includes('REGISTRAR');

  return (
    <div className="data-import-page">
      <div className="page-header">
        <h1>Data Import</h1>
        <p>Import classes, users, degree requirements, and academic calendar events</p>
      </div>

      {/* Tabs */}
      <div className="tabs-container">
        <button
          type="button"
          className={`tab ${activeTab === 'classes' ? 'active' : ''}`}
          onClick={() => setActiveTab('classes')}
        >
          Classes
        </button>
        <button
          type="button"
          className={`tab ${activeTab === 'users' ? 'active' : ''}`}
          onClick={() => setActiveTab('users')}
        >
          Users
        </button>
        <button
          type="button"
          className={`tab ${activeTab === 'requirements' ? 'active' : ''}`}
          onClick={() => setActiveTab('requirements')}
        >
          Degree Requirements
        </button>
        {isRegistrar && (
          <button
            type="button"
            className={`tab ${activeTab === 'calendar' ? 'active' : ''}`}
            onClick={() => setActiveTab('calendar')}
          >
            Academic Calendar
          </button>
        )}
      </div>

      {/* Tab Content */}
      <div className="tab-content">
        {activeTab === 'classes' && <ClassesTab backendBase={backendBase} />}
        {activeTab === 'users' && <UsersTab backendBase={backendBase} />}
        {activeTab === 'requirements' && <RequirementsTab backendBase={backendBase} />}
        {activeTab === 'calendar' && isRegistrar && <AcademicCalendarTab backendBase={backendBase} />}
      </div>
    </div>
  );
};

// Classes Tab Component (PDF Upload + Scraping)
const ClassesTab: React.FC<{ backendBase: string }> = ({ backendBase }) => {
  const [subTab, setSubTab] = useState<'pdf' | 'scraping'>('pdf');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [semester, setSemester] = useState(getDefaultSemester());
  const [year, setYear] = useState(getDefaultYear());
  const [subjects, setSubjects] = useState('');
  const [uploading, setUploading] = useState(false);
  const [uploadMessage, setUploadMessage] = useState('');
  const [formatWarning, setFormatWarning] = useState('');

  // Scraping states
  const [selectedTerm, setSelectedTerm] = useState<string>('');
  const [selectedSubjects, setSelectedSubjects] = useState<string[]>([]);
  const [customSubject, setCustomSubject] = useState<string>('');
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [scrapingMessage, setScrapingMessage] = useState<{ type: 'success' | 'error' | 'warning', text: string } | null>(null);
  const [terms, setTerms] = useState<any[]>([]);
  const [showProgressModal, setShowProgressModal] = useState<boolean>(false);
  const [subjectStatuses, setSubjectStatuses] = useState<Map<string, 'processing' | 'success' | 'skipped' | 'failed'>>(new Map());
  const supportedSubjects = ['BIO', 'PSY', 'CSE', 'ECO', 'AMS', 'POL'];

  useEffect(() => {
    if (subTab === 'scraping') {
      fetchTerms();
    }
  }, [subTab]);

  const fetchTerms = async () => {
    try {
      const response = await fetch(`${backendBase}/api/scraping/terms`, {
        credentials: 'include'
      });
      if (response.ok) {
        const termsData = await response.json();
        setTerms(termsData);
      }
    } catch (error) {
      console.error('Error fetching terms:', error);
    }
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      const file = e.target.files[0];
      if (file.type !== 'application/pdf') {
        setFormatWarning('Warning: Selected file is not a PDF.');
        setSelectedFile(null);
        return;
      }
      if (file.size > 10 * 1024 * 1024) {
        setFormatWarning('Warning: File size is too large (max 10MB).');
        setSelectedFile(null);
        return;
      }
      setSelectedFile(file);
      setUploadMessage('');
      setFormatWarning('');
    }
  };

  const handlePDFUpload = async () => {
    if (!selectedFile) {
      setUploadMessage('Please select a PDF file');
      return;
    }

    setUploading(true);
    setUploadMessage('');

    const formData = new FormData();
    formData.append('file', selectedFile);
    formData.append('semester', semester);
    formData.append('year', year.toString());
    if (subjects.trim()) {
      formData.append('subjects', subjects.trim());
    }

    try {
      const response = await fetch(`${backendBase}/api/registrar/upload-schedule`, {
        method: 'POST',
        body: formData,
        credentials: 'include'
      });

      const data = await response.json();

      if (data.success) {
        setUploadMessage(`Success: ${data.message || 'Upload successful'}`);
        setSelectedFile(null);
        setFormatWarning('');
        // Reset file input
        const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
        if (fileInput) fileInput.value = '';
      } else {
        // Check for format-related errors (same as original PdfUpload page)
        if (data.message && (
          data.message.toLowerCase().includes('format') ||
          data.message.toLowerCase().includes('invalid') ||
          data.message.toLowerCase().includes('parse') ||
          data.message.toLowerCase().includes('structure') ||
          data.message.toLowerCase().includes('unable to') ||
          data.message.toLowerCase().includes('corrupted') ||
          data.message.toLowerCase().includes('course schedule') ||
          data.message.toLowerCase().includes('sbu') ||
          data.message.toLowerCase().includes('not a valid') ||
          data.message.toLowerCase().includes('does not appear')
        )) {
          setFormatWarning(`PDF Format Issue: ${data.message}`);
          setUploadMessage('Error: Upload failed - Please upload the correct SBU Course Schedule PDF');
        } else {
          setUploadMessage(`Error: ${data.message || 'Upload failed'}`);
        }
      }
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      setUploadMessage(`Error uploading file: ${errorMessage}`);
    } finally {
      setUploading(false);
    }
  };

  const handleSubjectChange = (subject: string) => {
    const newSelectedSubjects = selectedSubjects.includes(subject)
      ? selectedSubjects.filter(s => s !== subject)
      : [...selectedSubjects, subject];
    setSelectedSubjects(newSelectedSubjects);
    checkForUnsupportedSubjects(newSelectedSubjects);
  };

  const handleAddCustomSubject = () => {
    const trimmed = customSubject.trim().toUpperCase();
    if (!trimmed) {
      return;
    }

    if (selectedSubjects.includes(trimmed)) {
      setScrapingMessage({ type: 'warning', text: `Subject "${trimmed}" is already selected.` });
      return;
    }

    const newSelectedSubjects = [...selectedSubjects, trimmed];
    setSelectedSubjects(newSelectedSubjects);
    setCustomSubject('');
    checkForUnsupportedSubjects(newSelectedSubjects);
  };

  const handleRemoveSubject = (subject: string) => {
    const newSelectedSubjects = selectedSubjects.filter(s => s !== subject);
    setSelectedSubjects(newSelectedSubjects);
    checkForUnsupportedSubjects(newSelectedSubjects);
  };

  const checkForUnsupportedSubjects = (subjects: string[]) => {
    const unsupportedSelected = subjects.filter(s => !supportedSubjects.includes(s));
    if (unsupportedSelected.length > 0) {
      setScrapingMessage({
        type: 'warning',
        text: `Warning: Subject(s) ${unsupportedSelected.join(', ')} are not fully supported. Course information will be marked as "unknown".`
      });
    } else {
      // Clear warning if all selected subjects are supported
      if (scrapingMessage?.type === 'warning' && scrapingMessage.text.includes('are not fully supported')) {
        setScrapingMessage(null);
      }
    }
  };

  const isValidTermFormat = (term: string): boolean => {
    const pattern = /^(Fall|Spring|Summer)\s+\d{4}$/;
    return pattern.test(term);
  };

  const checkTermStatus = (termName: string): 'not_scraped' | 'scraped' | 'not_found' => {
    const term = terms.find((t: any) => t.name === termName);
    if (!term) return 'not_found';
    return term.isScraped ? 'scraped' : 'not_scraped';
  };

  const getTermStatusDisplay = (termName: string) => {
    if (!isValidTermFormat(termName)) {
      return <span className="status-badge invalid" style={{ marginLeft: '10px', padding: '4px 8px', borderRadius: '4px', backgroundColor: '#fee2e2', color: '#991b1b' }}>Invalid Format</span>;
    }

    const status = checkTermStatus(termName);
    switch (status) {
      case 'scraped':
        return <span className="status-badge scraped" style={{ marginLeft: '10px', padding: '4px 8px', borderRadius: '4px', backgroundColor: '#dbeafe', color: '#1e40af' }}>Already Scraped</span>;
      case 'not_scraped':
        return <span className="status-badge not-scraped" style={{ marginLeft: '10px', padding: '4px 8px', borderRadius: '4px', backgroundColor: '#dcfce7', color: '#166534' }}>Ready to Scrape</span>;
      case 'not_found':
        return <span className="status-badge not-found" style={{ marginLeft: '10px', padding: '4px 8px', borderRadius: '4px', backgroundColor: '#f3f4f6', color: '#374151' }}>Valid Term</span>;
      default:
        return <span className="status-badge unknown" style={{ marginLeft: '10px', padding: '4px 8px', borderRadius: '4px', backgroundColor: '#fef3c7', color: '#92400e' }}>Unknown</span>;
    }
  };

  const handleScraping = async () => {
    if (!selectedTerm || selectedSubjects.length === 0) {
      setScrapingMessage({ type: 'warning', text: 'Please enter a term and select at least one subject.' });
      return;
    }

    if (!isValidTermFormat(selectedTerm)) {
      setScrapingMessage({ type: 'warning', text: 'Please enter a valid term format (e.g., Fall 2025, Spring 2026).' });
      return;
    }

    setIsLoading(true);
    setScrapingMessage(null);

    // Initialize subject statuses and show progress modal
    const initialStatuses = new Map<string, 'processing' | 'success' | 'skipped' | 'failed'>();
    selectedSubjects.forEach(subject => {
      initialStatuses.set(subject, 'processing');
    });
    setSubjectStatuses(initialStatuses);
    setShowProgressModal(true);

    try {
      const response = await fetch(`${backendBase}/api/scraping/scrape`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({
          term: selectedTerm,
          subjects: selectedSubjects
        })
      });

      if (!response.ok) {
        const errorData = await response.json().catch(() => ({ message: 'Failed to scrape courses' }));
        // Mark all as failed
        const failedStatuses = new Map<string, 'processing' | 'success' | 'skipped' | 'failed'>();
        selectedSubjects.forEach(subject => {
          failedStatuses.set(subject, 'failed');
        });
        setSubjectStatuses(failedStatuses);
        setScrapingMessage({ type: 'error', text: errorData.message || 'Scraping failed' });
        return;
      }

      const result = await response.json();
      if (result.success) {
        // Update subject statuses based on results
        const updatedStatuses = new Map<string, 'processing' | 'success' | 'skipped' | 'failed'>();
        selectedSubjects.forEach(subject => {
          if (result.scrapedSubjects?.includes(subject)) {
            updatedStatuses.set(subject, 'success');
          } else if (result.skippedSubjects?.includes(subject)) {
            updatedStatuses.set(subject, 'skipped');
          } else if (result.failedSubjects?.includes(subject)) {
            updatedStatuses.set(subject, 'failed');
          } else {
            updatedStatuses.set(subject, 'success'); // Default to success if not in any list
          }
        });
        setSubjectStatuses(updatedStatuses);

        let messageType: 'success' | 'warning' = 'success';
        if (result.skippedSubjects && result.skippedSubjects.length > 0) {
          messageType = 'warning';
        }
        setScrapingMessage({ type: messageType, text: result.message || 'Scraping successful' });
        await fetchTerms(); // Refresh terms to update isScraped status

        // Close modal after a short delay to show final status
        setTimeout(() => {
          setShowProgressModal(false);
        }, 2000);
      } else {
        // Mark all as failed
        const failedStatuses = new Map<string, 'processing' | 'success' | 'skipped' | 'failed'>();
        selectedSubjects.forEach(subject => {
          failedStatuses.set(subject, 'failed');
        });
        setSubjectStatuses(failedStatuses);
        setScrapingMessage({ type: 'error', text: result.message || 'Scraping failed' });
      }
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      // Mark all as failed
      const failedStatuses = new Map<string, 'processing' | 'success' | 'skipped' | 'failed'>();
      selectedSubjects.forEach(subject => {
        failedStatuses.set(subject, 'failed');
      });
      setSubjectStatuses(failedStatuses);
      setScrapingMessage({ type: 'error', text: `Failed to scrape courses: ${errorMessage}` });
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="classes-tab">
      <div className="sub-tabs">
        <button className={`sub-tab ${subTab === 'pdf' ? 'active' : ''}`} onClick={() => setSubTab('pdf')}>
          PDF Upload
        </button>
        <button className={`sub-tab ${subTab === 'scraping' ? 'active' : ''}`} onClick={() => setSubTab('scraping')}>
          Web Scraping
        </button>
      </div>

      {subTab === 'pdf' ? (
        <div className="upload-section">
          <h3>Upload Class Schedule PDF</h3>
          <p>Upload a PDF file containing class schedule information</p>

          <div className="form-group">
            <label>Select PDF File:</label>
            <input type="file" accept=".pdf" onChange={handleFileChange} />
            {selectedFile && <p className="file-name">Selected: {selectedFile.name}</p>}
          </div>

          <div className="form-group">
            <label>Semester:</label>
            <select value={semester} onChange={(e) => setSemester(e.target.value)}>
              <option value="Spring">Spring</option>
              <option value="Fall">Fall</option>
              <option value="Summer">Summer</option>
            </select>
          </div>

          <div className="form-group">
            <label>Year:</label>
            <input type="number" value={year} onChange={(e) => setYear(parseInt(e.target.value))} />
          </div>

          <div className="form-group">
            <label>Subjects (optional, comma-separated):</label>
            <input type="text" value={subjects} onChange={(e) => setSubjects(e.target.value)} placeholder="e.g., CSE, AMS, BIO" />
          </div>

          {formatWarning && <div className="alert alert-warning">{formatWarning}</div>}
          {uploadMessage && <div className={`alert ${uploadMessage.includes('Success') ? 'alert-success' : 'alert-error'}`}>{uploadMessage}</div>}

          <button onClick={handlePDFUpload} disabled={!selectedFile || uploading} className="upload-button">
            {uploading ? 'Uploading...' : 'Upload PDF'}
          </button>
        </div>
      ) : (
        <div className="scraping-section">
          <h3>Scrape Course Catalog</h3>
          <p>Scrape course data directly from SBU's online catalog</p>

          <div className="form-group">
            <label>Enter Term:</label>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <input
                type="text"
                value={selectedTerm}
                onChange={(e) => setSelectedTerm(e.target.value)}
                placeholder="e.g., Fall 2025, Spring 2026"
                style={{ flex: 1 }}
              />
              {selectedTerm && getTermStatusDisplay(selectedTerm)}
            </div>
            <small style={{ display: 'block', marginTop: '5px', color: '#6b7280' }}>
              Format: Fall 2025, Spring 2026, Summer 2025
            </small>
          </div>

          <div className="form-group">
            <label>Select Subjects:</label>
            <div style={{ marginBottom: '20px' }}>
              <h4 style={{ marginBottom: '10px', fontSize: '14px', fontWeight: 600 }}>Fully Supported Subjects (All Info Extracted)</h4>
              <div className="subjects-grid">
                {supportedSubjects.map(subject => (
                  <label key={subject} className="subject-checkbox" style={{ position: 'relative' }}>
                    <input
                      type="checkbox"
                      checked={selectedSubjects.includes(subject)}
                      onChange={() => handleSubjectChange(subject)}
                    />
                    <span>{subject}</span>
                    <small style={{ display: 'block', fontSize: '11px', color: '#059669', marginTop: '2px' }}>Full Support</small>
                  </label>
                ))}
              </div>
              <small style={{ display: 'block', marginTop: '8px', color: '#6b7280' }}>
                <strong>Full Support:</strong> BIO, PSY, CSE, ECO, AMS, POL - All course details will be extracted
              </small>
            </div>
          </div>

          <div className="form-group">
            <label>Add Other Subjects (Limited Info):</label>
            <div className="custom-subject-input">
              <input
                type="text"
                value={customSubject}
                onChange={(e) => setCustomSubject(e.target.value.toUpperCase())}
                onKeyPress={(e) => e.key === 'Enter' && handleAddCustomSubject()}
                placeholder="e.g., MAT, PHY, CHE"
                maxLength={10}
              />
              <button onClick={handleAddCustomSubject} disabled={!customSubject.trim()}>+ Add Subject</button>
            </div>
            <small style={{ display: 'block', marginTop: '8px', color: '#dc2626' }}>
              <strong>Warning:</strong> Other subjects will extract <strong>Description, Credits, and SBCs</strong> only.
              Requisite fields (prerequisites, corequisites, antirequisites, advisory prerequisites) will be marked as "unknown".
              <br />
              <strong>Instructions:</strong> Enter <strong>ONE subject at a time</strong> (e.g., MAT, PHY, CHE).
              Do <strong>NOT</strong> use commas or multiple subjects.
            </small>
          </div>

          {selectedSubjects.length > 0 && (
            <div className="selected-subjects" style={{ marginTop: '20px', marginBottom: '20px' }}>
              <h4 style={{ marginBottom: '10px', fontSize: '14px', fontWeight: 600 }}>
                Selected Subjects ({selectedSubjects.length}):
              </h4>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
                {selectedSubjects.map(subject => (
                  <span
                    key={subject}
                    style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: '6px',
                      padding: '6px 12px',
                      borderRadius: '6px',
                      backgroundColor: supportedSubjects.includes(subject) ? '#dcfce7' : '#fee2e2',
                      color: supportedSubjects.includes(subject) ? '#166534' : '#991b1b',
                      border: `1px solid ${supportedSubjects.includes(subject) ? '#86efac' : '#fecaca'}`,
                    }}
                  >
                    {subject}
                    {!supportedSubjects.includes(subject) && (
                      <span title="Limited support" style={{ fontSize: '12px' }}>⚠️</span>
                    )}
                    <button
                      onClick={() => handleRemoveSubject(subject)}
                      style={{
                        background: 'none',
                        border: 'none',
                        cursor: 'pointer',
                        fontSize: '18px',
                        lineHeight: 1,
                        padding: 0,
                        marginLeft: '4px',
                        color: 'inherit',
                      }}
                      title="Remove subject"
                    >
                      ×
                    </button>
                  </span>
                ))}
              </div>
            </div>
          )}

          {scrapingMessage && (
            <div className={`alert alert-${scrapingMessage.type}`}>{scrapingMessage.text}</div>
          )}

          <button onClick={handleScraping} disabled={!selectedTerm || selectedSubjects.length === 0 || isLoading} className="scrape-button">
            {isLoading ? 'Scraping...' : 'Start Scraping'}
          </button>

          {/* Progress Modal */}
          {showProgressModal && (
            <div
              style={{
                position: 'fixed',
                top: 0,
                left: 0,
                right: 0,
                bottom: 0,
                backgroundColor: 'rgba(0, 0, 0, 0.5)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                zIndex: 1000,
              }}
              onClick={(e) => {
                // Only close if clicking the backdrop, not the modal content
                if (e.target === e.currentTarget && !isLoading) {
                  setShowProgressModal(false);
                }
              }}
            >
              <div
                style={{
                  backgroundColor: 'white',
                  borderRadius: '12px',
                  padding: '24px',
                  maxWidth: '500px',
                  width: '90%',
                  maxHeight: '80vh',
                  overflow: 'auto',
                  boxShadow: '0 10px 30px rgba(0, 0, 0, 0.3)',
                }}
                onClick={(e) => e.stopPropagation()}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
                  <h3 style={{ margin: 0, fontSize: '20px', fontWeight: 600 }}>Scraping Progress</h3>
                  {!isLoading && (
                    <button
                      onClick={() => setShowProgressModal(false)}
                      style={{
                        background: 'none',
                        border: 'none',
                        fontSize: '24px',
                        cursor: 'pointer',
                        padding: '0',
                        lineHeight: 1,
                        color: '#6b7280',
                      }}
                    >
                      ×
                    </button>
                  )}
                </div>

                <div style={{ marginBottom: '16px', color: '#6b7280' }}>
                  <strong>Term:</strong> {selectedTerm}
                </div>

                <div style={{ marginBottom: '20px' }}>
                  <strong style={{ display: 'block', marginBottom: '12px' }}>
                    Processing {selectedSubjects.length} subject(s):
                  </strong>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                    {selectedSubjects.map(subject => {
                      const status = subjectStatuses.get(subject) || 'processing';
                      return (
                        <div
                          key={subject}
                          style={{
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'space-between',
                            padding: '10px 12px',
                            borderRadius: '6px',
                            backgroundColor: status === 'processing' ? '#f3f4f6' :
                              status === 'success' ? '#dcfce7' :
                                status === 'skipped' ? '#fef3c7' : '#fee2e2',
                            border: `1px solid ${status === 'processing' ? '#e5e7eb' :
                              status === 'success' ? '#86efac' :
                                status === 'skipped' ? '#fde047' : '#fecaca'
                              }`,
                          }}
                        >
                          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                            {status === 'processing' && (
                              <div
                                style={{
                                  width: '16px',
                                  height: '16px',
                                  border: '2px solid #3b82f6',
                                  borderTop: '2px solid transparent',
                                  borderRadius: '50%',
                                  animation: 'spin 1s linear infinite',
                                }}
                              />
                            )}
                            {status === 'success' && <span style={{ fontSize: '18px' }}>✓</span>}
                            {status === 'skipped' && <span style={{ fontSize: '18px' }}>⚠</span>}
                            {status === 'failed' && <span style={{ fontSize: '18px' }}>✗</span>}
                            <span style={{ fontWeight: 500 }}>{subject}</span>
                            {!supportedSubjects.includes(subject) && (
                              <span style={{ fontSize: '12px', color: '#dc2626' }}>(Limited Info)</span>
                            )}
                          </div>
                          <span
                            style={{
                              fontSize: '12px',
                              color: status === 'processing' ? '#3b82f6' :
                                status === 'success' ? '#166534' :
                                  status === 'skipped' ? '#92400e' : '#991b1b',
                              fontWeight: 500,
                            }}
                          >
                            {status === 'processing' && 'Processing...'}
                            {status === 'success' && 'Completed'}
                            {status === 'skipped' && 'Already Scraped'}
                            {status === 'failed' && 'Failed'}
                          </span>
                        </div>
                      );
                    })}
                  </div>
                </div>

                {isLoading && (
                  <div style={{ textAlign: 'center', color: '#6b7280', fontSize: '14px', marginTop: '16px' }}>
                    Please wait while we scrape course data...
                  </div>
                )}

                {!isLoading && (
                  <div style={{ textAlign: 'center', marginTop: '16px' }}>
                    <button
                      onClick={() => setShowProgressModal(false)}
                      style={{
                        padding: '8px 20px',
                        backgroundColor: '#B91C1C',
                        color: 'white',
                        border: 'none',
                        borderRadius: '6px',
                        cursor: 'pointer',
                        fontWeight: 500,
                      }}
                    >
                      Close
                    </button>
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

// Users Tab Component
const UsersTab: React.FC<{ backendBase: string }> = ({ backendBase }) => {
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [uploadMessage, setUploadMessage] = useState('');

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      const file = e.target.files[0];
      if (!file.name.toLowerCase().endsWith('.yaml') && !file.name.toLowerCase().endsWith('.yml')) {
        setUploadMessage('Error: Please select a YAML file (.yaml or .yml)');
        setSelectedFile(null);
        return;
      }
      if (file.size > 10 * 1024 * 1024) {
        setUploadMessage('Error: File size must be less than 10MB');
        setSelectedFile(null);
        return;
      }
      setSelectedFile(file);
      setUploadMessage('');
    }
  };

  const handleUpload = async () => {
    if (!selectedFile) {
      setUploadMessage('Please select a YAML file');
      return;
    }

    setUploading(true);
    setUploadMessage('');

    const formData = new FormData();
    formData.append('file', selectedFile);

    try {
      const response = await fetch(`${backendBase}/api/users/import`, {
        method: 'POST',
        body: formData,
        credentials: 'include'
      });

      const data = await response.json();

      if (response.ok) {
        setUploadMessage(`Success: Successfully imported ${data.importedCount} users`);
        setSelectedFile(null);
      } else {
        setUploadMessage(`Error: ${data.error || 'Failed to import users'}`);
      }
    } catch (error) {
      setUploadMessage('Error uploading file. Please try again.');
    } finally {
      setUploading(false);
    }
  };

  return (
    <div className="users-tab">
      <h3>Import Users</h3>
      <p>Upload a YAML file to import users into the system</p>

      <div className="form-group">
        <label>Select YAML File:</label>
        <input type="file" accept=".yaml,.yml" onChange={handleFileChange} />
        {selectedFile && <p className="file-name">Selected: {selectedFile.name}</p>}
      </div>

      {uploadMessage && (
        <div className={`alert ${uploadMessage.includes('Success') ? 'alert-success' : 'alert-error'}`}>
          {uploadMessage}
        </div>
      )}

      <button onClick={handleUpload} disabled={!selectedFile || uploading} className="upload-button">
        {uploading ? 'Importing...' : 'Import Users'}
      </button>

      <LoadingModal isOpen={uploading} message="Importing users..." />
    </div>
  );
};

// Degree Requirements Tab Component
const RequirementsTab: React.FC<{ backendBase: string }> = ({ backendBase }) => {
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [uploadMessage, setUploadMessage] = useState('');
  const [result, setResult] = useState<any>(null);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      const file = e.target.files[0];
      if (!file.name.toLowerCase().endsWith('.yaml') && !file.name.toLowerCase().endsWith('.yml')) {
        setResult({ success: false, error: 'Please select a YAML file (.yaml or .yml)' });
        setSelectedFile(null);
        return;
      }
      if (file.size > 10 * 1024 * 1024) {
        setResult({ success: false, error: 'File size must be less than 10MB' });
        setSelectedFile(null);
        return;
      }
      setSelectedFile(file);
      setResult(null);
      setUploadMessage('');
    }
  };

  const handleUpload = async () => {
    if (!selectedFile) {
      setResult({ success: false, error: 'Please select a file to upload' });
      return;
    }

    setUploading(true);
    setResult(null);
    setUploadMessage('');

    const formData = new FormData();
    formData.append('file', selectedFile);

    try {
      const response = await fetch(`${backendBase}/api/degree-requirements/import`, {
        method: 'POST',
        body: formData,
        credentials: 'include'
      });

      const data = await response.json();

      if (response.ok) {
        setResult({
          success: true,
          message: `Successfully imported ${data.importedCount} requirements.`,
          details: data
        });
        setSelectedFile(null);
      } else {
        setResult({
          success: false,
          error: data.error || 'Failed to import requirements',
          details: data
        });
      }
    } catch (error) {
      setResult({
        success: false,
        error: 'Network error occurred while uploading file.'
      });
    } finally {
      setUploading(false);
    }
  };

  return (
    <div className="requirements-tab">
      <h3>Import Degree Requirements</h3>
      <p>Upload a YAML file to import degree requirements (majors, minors, courses, etc.)</p>

      <div className="form-group">
        <label>Select YAML File:</label>
        <input type="file" accept=".yaml,.yml" onChange={handleFileChange} />
        {selectedFile && <p className="file-name">Selected: {selectedFile.name}</p>}
      </div>

      {result && (
        <div className={`alert ${result.success ? 'alert-success' : 'alert-error'}`}>
          {result.success ? result.message : result.error}
          {result.details && result.details.errors && result.details.errors.length > 0 && (
            <ul style={{ marginTop: '10px', paddingLeft: '20px' }}>
              {result.details.errors.map((err: string, i: number) => (
                <li key={i}>{err}</li>
              ))}
            </ul>
          )}
        </div>
      )}

      <button onClick={handleUpload} disabled={!selectedFile || uploading} className="upload-button">
        {uploading ? 'Importing...' : 'Import Requirements'}
      </button>

      <LoadingModal isOpen={uploading} message="Importing degree requirements..." />
    </div>
  );
};

// Academic Calendar Tab Component
const AcademicCalendarTab: React.FC<{ backendBase: string }> = ({ backendBase }) => {
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [uploadMessage, setUploadMessage] = useState('');
  const [events, setEvents] = useState<any[]>([]);

  useEffect(() => {
    // Load existing events
    const fetchEvents = async () => {
      try {
        const response = await fetch(`${backendBase}/api/admin/academic-calendar`, { credentials: 'include' });
        if (response.ok) {
          const data = await response.json();
          setEvents(Array.isArray(data) ? data : []);
        }
      } catch (error) {
        console.error('Error fetching events:', error);
      }
    };
    fetchEvents();
  }, [backendBase]);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      const file = e.target.files[0];
      if (!file.name.toLowerCase().endsWith('.yaml') && !file.name.toLowerCase().endsWith('.yml')) {
        setUploadMessage('Error: Please select a YAML file (.yaml or .yml)');
        setSelectedFile(null);
        return;
      }
      setSelectedFile(file);
      setUploadMessage('');
    }
  };

  const handleUpload = async () => {
    if (!selectedFile) {
      setUploadMessage('Please select a YAML file');
      return;
    }

    setUploading(true);
    setUploadMessage('');

    const formData = new FormData();
    formData.append('file', selectedFile);

    try {
      const response = await fetch(`${backendBase}/api/admin/academic-calendar/import`, {
        method: 'POST',
        body: formData,
        credentials: 'include'
      });

      const result = await response.json();

      if (response.ok || result.imported !== undefined) {
        setUploadMessage(
          `Imported: ${result.imported} (skipped ${result.skipped})${result.errors?.length ? "\nErrors:\n- " + result.errors.join("\n- ") : ""
          }`
        );
        setSelectedFile(null);
        // Refresh events
        const evRes = await fetch(`${backendBase}/api/admin/academic-calendar`, { credentials: 'include' });
        if (evRes.ok) {
          const evData = await evRes.json();
          setEvents(Array.isArray(evData) ? evData : []);
        }
      } else {
        setUploadMessage(`Upload failed: ${result.message || 'Unknown error'}`);
      }
    } catch (e: any) {
      setUploadMessage(`Upload failed: ${e.message}`);
    } finally {
      setUploading(false);
    }
  };

  return (
    <div className="calendar-tab">
      <h3>Import Academic Calendar</h3>
      <p>Upload a YAML file to import academic calendar events (semesters, holidays, etc.)</p>

      <div className="form-group">
        <label>Select YAML File:</label>
        <input type="file" accept=".yaml,.yml" onChange={handleFileChange} />
        {selectedFile && <p className="file-name">Selected: {selectedFile.name}</p>}
        <span style={{ fontSize: 12, color: "#888", display: 'block', marginTop: 4 }}>Fields: date (yyyy-MM-dd), title, type</span>
      </div>

      {uploadMessage && (
        <div className={`alert ${uploadMessage.includes('Imported') ? 'alert-success' : 'alert-error'}`} style={{ whiteSpace: 'pre-wrap' }}>
          {uploadMessage}
        </div>
      )}

      <button onClick={handleUpload} disabled={!selectedFile || uploading} className="upload-button">
        {uploading ? 'Importing...' : 'Import Calendar'}
      </button>

      <div style={{ marginTop: 24 }}>
        <strong>Current Events ({events.length})</strong>
        <ul style={{ marginTop: 8, maxHeight: 300, overflowY: 'auto', border: '1px solid #eee', padding: 12, borderRadius: 8 }}>
          {events.map((ev, i) => (
            <li key={`${ev.date}-${ev.title}-${i}`} style={{ marginBottom: 4 }}>
              <span style={{ fontWeight: 600, marginRight: 8 }}>[{ev.type}]</span>
              <span style={{ marginRight: 8 }}>{ev.date}</span>
              <span>{ev.title}</span>
            </li>
          ))}
          {!events.length && <li>No events imported</li>}
        </ul>
      </div>

      <LoadingModal isOpen={uploading} message="Importing academic calendar..." />
    </div>
  );
};

export default DataImport;
