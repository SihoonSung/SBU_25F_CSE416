import React, { useState } from 'react';
import './DataImport.css';

/**
 * Data Import Component
 * Unified interface for importing different types of data.
 */

type TabType = 'classes' | 'users' | 'requirements';

const DataImport: React.FC = () => {
  const [activeTab, setActiveTab] = useState<TabType>('classes');
  const backendBase = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8080';

  return (
    <div className="data-import-page">
      <div className="page-header">
        <h1>Data Import</h1>
        <p>Import classes, users, and degree requirements into the system</p>
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
      </div>

      {/* Tab Content */}
      <div className="tab-content">
        {activeTab === 'classes' && <ClassesTab backendBase={backendBase} />}
        {activeTab === 'users' && <UsersTab backendBase={backendBase} />}
        {activeTab === 'requirements' && <RequirementsTab backendBase={backendBase} />}
      </div>
    </div>
  );
};

// Classes Tab Component (PDF Upload + Scraping)
const ClassesTab: React.FC<{ backendBase: string }> = ({ backendBase }) => {
  const [subTab, setSubTab] = useState<'pdf' | 'scraping'>('pdf');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [semester, setSemester] = useState('Spring');
  const [year, setYear] = useState(2025);
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
  const supportedSubjects = ['BIO', 'PSY', 'CSE', 'ECO', 'AMS', 'POL'];

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

      if (!response.ok) {
        const errorData = await response.json().catch(() => ({ message: 'Failed to upload file' }));
        setUploadMessage(`Error: ${errorData.message || 'Upload failed'}`);
        return;
      }

      const data = await response.json();
      if (data.success) {
        setUploadMessage(`Success: ${data.message || 'Upload successful'}`);
        setSelectedFile(null);
        setFormatWarning('');
        // Reset file input
        const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
        if (fileInput) fileInput.value = '';
      } else {
        setUploadMessage(`Error: ${data.message || 'Upload failed'}`);
      }
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      setUploadMessage(`Error uploading file: ${errorMessage}`);
    } finally {
      setUploading(false);
    }
  };

  const handleSubjectChange = (subject: string) => {
    if (selectedSubjects.includes(subject)) {
      setSelectedSubjects(selectedSubjects.filter(s => s !== subject));
    } else {
      setSelectedSubjects([...selectedSubjects, subject]);
    }
  };

  const handleAddCustomSubject = () => {
    if (customSubject.trim() && !selectedSubjects.includes(customSubject.toUpperCase())) {
      setSelectedSubjects([...selectedSubjects, customSubject.toUpperCase()]);
      setCustomSubject('');
    }
  };

  const handleRemoveSubject = (subject: string) => {
    setSelectedSubjects(selectedSubjects.filter(s => s !== subject));
  };

  const handleScraping = async () => {
    if (!selectedTerm || selectedSubjects.length === 0) {
      setScrapingMessage({ type: 'warning', text: 'Please enter a term and select at least one subject.' });
      return;
    }

    setIsLoading(true);
    setScrapingMessage(null);

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
        setScrapingMessage({ type: 'error', text: errorData.message || 'Scraping failed' });
        return;
      }

      const result = await response.json();
      if (result.success) {
        let messageType: 'success' | 'warning' = 'success';
        if (result.skippedSubjects && result.skippedSubjects.length > 0) {
          messageType = 'warning';
        }
        setScrapingMessage({ type: messageType, text: result.message || 'Scraping successful' });
      } else {
        setScrapingMessage({ type: 'error', text: result.message || 'Scraping failed' });
      }
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
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
            <input
              type="text"
              value={selectedTerm}
              onChange={(e) => setSelectedTerm(e.target.value)}
              placeholder="e.g., Fall 2025, Spring 2026"
            />
          </div>

          <div className="form-group">
            <label>Select Subjects:</label>
            <div className="subjects-grid">
              {supportedSubjects.map(subject => (
                <label key={subject} className="subject-checkbox">
                  <input
                    type="checkbox"
                    checked={selectedSubjects.includes(subject)}
                    onChange={() => handleSubjectChange(subject)}
                  />
                  {subject}
                </label>
              ))}
            </div>
          </div>

          <div className="form-group">
            <label>Add Custom Subject:</label>
            <div className="custom-subject-input">
              <input
                type="text"
                value={customSubject}
                onChange={(e) => setCustomSubject(e.target.value.toUpperCase())}
                onKeyPress={(e) => e.key === 'Enter' && handleAddCustomSubject()}
                placeholder="e.g., MAT, PHY"
                maxLength={10}
              />
              <button onClick={handleAddCustomSubject} disabled={!customSubject.trim()}>+ Add</button>
            </div>
          </div>

          {selectedSubjects.length > 0 && (
            <div className="selected-subjects">
              <strong>Selected ({selectedSubjects.length}):</strong>
              {selectedSubjects.map(subject => (
                <span key={subject} className="subject-tag">
                  {subject}
                  <button onClick={() => handleRemoveSubject(subject)}>×</button>
                </span>
              ))}
            </div>
          )}

          {scrapingMessage && (
            <div className={`alert alert-${scrapingMessage.type}`}>{scrapingMessage.text}</div>
          )}

          <button onClick={handleScraping} disabled={!selectedTerm || selectedSubjects.length === 0 || isLoading} className="scrape-button">
            {isLoading ? 'Scraping...' : 'Start Scraping'}
          </button>
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
      setResult(data);

      if (data.success) {
        setSelectedFile(null);
        const fileInput = document.getElementById('requirements-file-input') as HTMLInputElement;
        if (fileInput) fileInput.value = '';
      }
    } catch (error) {
      setResult({
        success: false,
        error: 'Error uploading file. Please try again.'
      });
    } finally {
      setUploading(false);
    }
  };

  return (
    <div className="requirements-tab">
      <h3>Import Degree Requirements</h3>
      <p>Upload a YAML file to import degree requirements for majors and minors</p>

      <div className="form-group">
        <label>Select YAML File:</label>
        <input
          id="requirements-file-input"
          type="file"
          accept=".yaml,.yml"
          onChange={handleFileChange}
        />
        {selectedFile && <p className="file-name">Selected: {selectedFile.name}</p>}
      </div>

      {result && (
        <div className={`alert ${result.success ? 'alert-success' : 'alert-error'}`}>
          {result.success ? (
            <>
              <strong>Success:</strong> {result.message || 'Successfully imported degree requirements'}
              {result.requirements && (
                <p>Imported {result.requirements.length} requirement(s)</p>
              )}
            </>
          ) : (
            <>
              <strong>Error:</strong> {result.error || result.message || 'Failed to import'}
            </>
          )}
        </div>
      )}

      {uploadMessage && (
        <div className={`alert ${uploadMessage.includes('Success') ? 'alert-success' : 'alert-error'}`}>
          {uploadMessage}
        </div>
      )}

      <button onClick={handleUpload} disabled={!selectedFile || uploading} className="upload-button">
        {uploading ? 'Importing...' : 'Import Requirements'}
      </button>
    </div>
  );
};

export default DataImport;

