import React, { useState } from 'react';
import './DegreeRequirementsImport.css';

/**
 * Degree Requirements Import Component
 * Provides interface for importing degree requirements from YAML files.
 * 
 * Note: YAML file import functionality and user interface were implemented 
 * with assistance from Cursor AI and ChatGPT for proper file handling 
 * and data validation capabilities.
 */

interface ImportResult {
  success: boolean;
  message: string;
  requirements?: any[];
  error?: string;
}

const DegreeRequirementsImport: React.FC = () => {
  const [file, setFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [result, setResult] = useState<ImportResult | null>(null);
  const [dragActive, setDragActive] = useState(false);

  const handleFileSelect = (selectedFile: File) => {
    // Validate file type
    if (!selectedFile.name.toLowerCase().endsWith('.yaml') && 
        !selectedFile.name.toLowerCase().endsWith('.yml')) {
      setResult({
        success: false,
        error: 'Please select a YAML file (.yaml or .yml)'
      });
      return;
    }

    // Validate file size (max 10MB)
    if (selectedFile.size > 10 * 1024 * 1024) {
      setResult({
        success: false,
        error: 'File size must be less than 10MB'
      });
      return;
    }

    setFile(selectedFile);
    setResult(null);
  };

  const handleFileInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const selectedFile = e.target.files?.[0];
    if (selectedFile) {
      handleFileSelect(selectedFile);
    }
  };

  const handleDrag = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (e.type === "dragenter" || e.type === "dragover") {
      setDragActive(true);
    } else if (e.type === "dragleave") {
      setDragActive(false);
    }
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setDragActive(false);
    
    const droppedFile = e.dataTransfer.files?.[0];
    if (droppedFile) {
      handleFileSelect(droppedFile);
    }
  };

  const handleUpload = async () => {
    if (!file) {
      setResult({
        success: false,
        error: 'Please select a file to upload'
      });
      return;
    }

    setUploading(true);
    setResult(null);

    try {
      const formData = new FormData();
      formData.append('file', file);

      const backendBase = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8080';
      const response = await fetch(`${backendBase}/api/degree-requirements/import`, {
        method: 'POST',
        body: formData,
        credentials: 'include'
      });

      const data = await response.json();
      setResult(data);

      if (data.success) {
        setFile(null);
        // Reset file input
        const fileInput = document.getElementById('fileInput') as HTMLInputElement;
        if (fileInput) {
          fileInput.value = '';
        }
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

  const handleClear = () => {
    setFile(null);
    setResult(null);
    const fileInput = document.getElementById('fileInput') as HTMLInputElement;
    if (fileInput) {
      fileInput.value = '';
    }
  };

  const downloadSampleFile = () => {
    // Create a sample YAML content
    const sampleContent = `# Sample Degree Requirements YAML File
requirements_version: "Fall 2025"
effective_term: "Fall 2025"
imported_by: "registrar_netid"
imported_at: "2025-01-14T10:00:00Z"

degree_requirements:
  - subject_code: "CSE"
    degree_type: "BS"
    total_credits_required: 120
    is_restricted: true
    gpa_requirement: 3.0
    admission_requirements:
      required_courses:
        - course_code: "CSE 114"
          min_grade: "C"
        - course_code: "CSE 214"
          min_grade: "C"
      minimum_gpa: 3.0
    course_requirements:
      core_courses:
        - "CSE 114"
        - "CSE 214"
        - "CSE 215"
      elective_courses:
        category: "CSE Electives"
        credits_required: 12
    sbc_requirements:
      - category: "ARTS"
        credits_required: 3
      - category: "GLO"
        credits_required: 3

minor_requirements:
  - subject_code: "CSE"
    degree_type: "MINOR"
    total_credits_required: 18
    is_restricted: false
    gpa_requirement: 2.0
    course_requirements:
      core_courses:
        - "CSE 114"
        - "CSE 214"
        - "CSE 215"
`;

    // Create and download the file
    const blob = new Blob([sampleContent], { type: 'text/yaml' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'sample_degree_requirements.yaml';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    window.URL.revokeObjectURL(url);
  };

  return (
    <div className="degree-requirements-import">
      <div className="page-header">
        <h1>Import Degree Requirements</h1>
        <p>Upload YAML files to import degree requirements for majors and minors</p>
      </div>

      {/* Instructions */}
      <div className="instructions-section">
        <h2>Instructions</h2>
        <div className="instructions-content">
          <div className="instruction-item">
            <h3>📋 File Format</h3>
            <p>Upload YAML files (.yaml or .yml) containing degree requirements. The file should include:</p>
            <ul>
              <li><strong>requirements_version</strong>: Version identifier (e.g., "Fall 2025")</li>
              <li><strong>effective_term</strong>: When requirements take effect</li>
              <li><strong>degree_requirements</strong>: List of major degree requirements</li>
              <li><strong>minor_requirements</strong>: List of minor degree requirements</li>
            </ul>
          </div>
          
          <div className="instruction-item">
            <h3>✅ Required Fields</h3>
            <p>Each requirement must include:</p>
            <ul>
              <li><strong>subject_code</strong>: Department code (e.g., "CSE", "BIO")</li>
              <li><strong>degree_type</strong>: "BA", "BS", or "MINOR"</li>
              <li><strong>total_credits_required</strong>: Total credits needed</li>
              <li><strong>is_restricted</strong>: Whether program requires approval</li>
            </ul>
          </div>
          
          <div className="instruction-item">
            <h3>📝 Optional Fields</h3>
            <p>Additional information you can include:</p>
            <ul>
              <li><strong>gpa_requirement</strong>: Minimum GPA needed</li>
              <li><strong>admission_requirements</strong>: Prerequisites and requirements</li>
              <li><strong>course_requirements</strong>: Required and elective courses</li>
              <li><strong>sbc_requirements</strong>: Stony Brook Curriculum requirements</li>
            </ul>
          </div>
        </div>
        
        <div className="sample-file-section">
          <button className="download-sample-button" onClick={downloadSampleFile}>
            📥 Download Sample YAML File
          </button>
        </div>
      </div>

      {/* File Upload */}
      <div className="upload-section">
        <h2>Upload YAML File</h2>
        
        <div 
          className={`file-drop-zone ${dragActive ? 'drag-active' : ''} ${file ? 'file-selected' : ''}`}
          onDragEnter={handleDrag}
          onDragLeave={handleDrag}
          onDragOver={handleDrag}
          onDrop={handleDrop}
        >
          <div className="drop-zone-content">
            {file ? (
              <div className="file-selected-content">
                <div className="file-icon">📄</div>
                <div className="file-info">
                  <div className="file-name">{file.name}</div>
                  <div className="file-size">{(file.size / 1024).toFixed(1)} KB</div>
                </div>
                <button className="remove-file-button" onClick={handleClear}>
                  ✕
                </button>
              </div>
            ) : (
              <div className="drop-zone-empty">
                <div className="upload-icon">📁</div>
                <div className="upload-text">
                  <h3>Drop your YAML file here</h3>
                  <p>or click to browse</p>
                </div>
                <input
                  id="fileInput"
                  type="file"
                  accept=".yaml,.yml"
                  onChange={handleFileInputChange}
                  className="file-input"
                />
              </div>
            )}
          </div>
        </div>

        <div className="upload-actions">
          <button 
            className="upload-button"
            onClick={handleUpload}
            disabled={!file || uploading}
          >
            {uploading ? 'Uploading...' : 'Upload File'}
          </button>
          
          {file && (
            <button 
              className="clear-button"
              onClick={handleClear}
              disabled={uploading}
            >
              Clear
            </button>
          )}
        </div>
      </div>

      {/* Results */}
      {result && (
        <div className="results-section">
          <h2>Import Results</h2>
          
          {result.success ? (
            <div className="result-success">
              <div className="result-header">
                <span className="result-icon">✅</span>
                <h3>Import Successful!</h3>
              </div>
              <p>{result.message}</p>
              
              {result.requirements && result.requirements.length > 0 && (
                <div className="imported-requirements">
                  <h4>Imported Requirements:</h4>
                  <div className="requirements-list">
                    {result.requirements.map((req, index) => (
                      <div key={index} className="requirement-item">
                        <span className="requirement-subject">{req.subjectCode}</span>
                        <span className="requirement-type">{req.degreeType}</span>
                        <span className="requirement-credits">{req.totalCreditsRequired} credits</span>
                        {req.isRestricted && <span className="restricted-badge">Restricted</span>}
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          ) : (
            <div className="result-error">
              <div className="result-header">
                <span className="result-icon">❌</span>
                <h3>Import Failed</h3>
              </div>
              <p>{result.error}</p>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default DegreeRequirementsImport;
