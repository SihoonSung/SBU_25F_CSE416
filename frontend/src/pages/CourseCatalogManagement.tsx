/*
 * TP: Used Cursor to implement course catalog management page with scraping functionality
 */

import React, { useState, useEffect } from 'react';
import './CourseCatalogManagement.css';

/**
 * Course Catalog Management Component
 * Provides interface for managing course catalog scraping and data display.
 */


interface ScrapingResponse {
  success: boolean;
  message: string;
  term?: string;
  subjects?: string[];
  totalScraped?: number;
  scrapedSubjects?: string[];
  skippedSubjects?: string[];
  failedSubjects?: string[];
}

interface Term {
  id: number;
  name: string;
  isScraped: boolean;
  isActive: boolean;
  startDate: string;
  endDate: string;
}

const CourseCatalogManagement: React.FC = () => {
  const [selectedTerm, setSelectedTerm] = useState<string>('');
  const [selectedSubjects, setSelectedSubjects] = useState<string[]>([]);
  const supportedSubjects = ['BIO', 'PSY', 'CSE', 'ECO', 'AMS', 'POL'];
  const [customSubject, setCustomSubject] = useState<string>('');
  const [terms, setTerms] = useState<Term[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [message, setMessage] = useState<{ type: 'success' | 'error' | 'warning', text: string } | null>(null);

  useEffect(() => {
    fetchTerms();
  }, []);

  const fetchTerms = async () => {
    try {
      const response = await fetch('http://localhost:8080/api/scraping/terms');
      if (response.ok) {
        const termsData = await response.json();
        setTerms(termsData);
      }
    } catch (error) {
      console.error('Error fetching terms:', error);
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
      setMessage({ type: 'warning', text: `Subject "${trimmed}" is already selected.` });
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
    // All subjects are now fully supported - no warning needed
    // Clear any previous warnings about unsupported subjects
    if (message?.type === 'warning' && message.text.includes('are not fully supported')) {
      setMessage(null);
    }
  };

  const checkTermStatus = (termName: string): 'not_scraped' | 'scraped' | 'not_found' => {
    const term = terms.find(t => t.name === termName);
    if (!term) return 'not_found';
    return term.isScraped ? 'scraped' : 'not_scraped';
  };

  const isValidTermFormat = (term: string): boolean => {
    const pattern = /^(Fall|Spring|Summer)\s+\d{4}$/;
    return pattern.test(term);
  };

  const handleScraping = async () => {
    if (!selectedTerm || selectedSubjects.length === 0) {
      setMessage({ type: 'warning', text: 'Please enter a term and select at least one subject.' });
      return;
    }

    if (!isValidTermFormat(selectedTerm)) {
      setMessage({ type: 'warning', text: 'Please enter a valid term format (e.g., Fall 2025, Spring 2026).' });
      return;
    }

    setIsLoading(true);
    setMessage(null);

    try {
      const response = await fetch(`${backendBase}/api/scraping/scrape`, {
        credentials: 'include',
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          term: selectedTerm,
          subjects: selectedSubjects
        })
      });

      const result: ScrapingResponse = await response.json();

      if (result.success) {
        // Determine message type based on results
        let messageType: 'success' | 'warning' = 'success';
        
        if (result.skippedSubjects && result.skippedSubjects.length > 0) {
          messageType = 'warning'; // Show warning if some subjects were skipped
        }
        
        setMessage({ type: messageType, text: result.message });
        await fetchTerms(); // Refresh terms to update isScraped status
      } else {
        setMessage({ type: 'error', text: result.message });
      }
    } catch (error) {
      setMessage({ type: 'error', text: 'Failed to scrape courses. Please try again.' });
      console.error('Scraping error:', error);
    } finally {
      setIsLoading(false);
    }
  };


  const getTermStatusDisplay = (termName: string) => {
    if (!isValidTermFormat(termName)) {
      return <span className="status-badge invalid">Invalid Format</span>;
    }

    const status = checkTermStatus(termName);
    switch (status) {
      case 'scraped':
        return <span className="status-badge scraped">Already Scraped</span>;
      case 'not_scraped':
        return <span className="status-badge not-scraped">Ready to Scrape</span>;
      case 'not_found':
        return <span className="status-badge not-found">Valid Term</span>;
      default:
        return <span className="status-badge unknown">? Unknown</span>;
    }
  };


  return (
    <div className="course-catalog-management">
      <div className="page-header">
        <h1>Course Catalog Management</h1>
        <p>Scrape and manage course catalog data from SBU's online catalog</p>
      </div>

      {message && (
        <div className={`message ${message.type}`}>
          <span className="message-icon">
            {message.type === 'success' && 'Success'}
            {message.type === 'error' && 'Error'}
            {message.type === 'warning' && 'Warning'}
          </span>
          {message.text}
        </div>
      )}

      <div className="scraping-section">
        <div className="form-group">
          <label htmlFor="term-input">Enter Term:</label>
          <div className="term-input-container">
            <input
              id="term-input"
              type="text"
              value={selectedTerm}
              onChange={(e) => setSelectedTerm(e.target.value)}
              placeholder="e.g., Fall 2025, Spring 2024"
              className="term-input"
              pattern="^(Fall|Spring|Summer)\s+\d{4}$"
              title="Format: Fall 2025, Spring 2024, Summer 2025"
            />
            {selectedTerm && getTermStatusDisplay(selectedTerm)}
          </div>
          <div className="term-help">
            <small>Format: Spring 2024, Fall 2024, Spring 2025</small>
          </div>
        </div>

        <div className="form-group">
          <label>Select Subjects:</label>
         
          {/* Supported Subjects (Quick Select) */}
          <div className="subjects-section">
            <h4>Quick Select Subjects</h4>
            <div className="subjects-grid">
              {supportedSubjects.map(subject => (
                <label key={subject} className="subject-checkbox supported">
                  <input
                    type="checkbox"
                    checked={selectedSubjects.includes(subject)}
                    onChange={() => handleSubjectChange(subject)}
                  />
                  <span className="subject-label">{subject}</span>
                </label>
              ))}
            </div>
          </div>

          {/* Add Custom Subject */}
          <div className="custom-subject-section">
            <h4>Add Other Subjects</h4>
            <div className="custom-subject-input">
              <input
                type="text"
                value={customSubject}
                onChange={(e) => setCustomSubject(e.target.value.toUpperCase())}
                onKeyPress={(e) => {
                  if (e.key === 'Enter') {
                    handleAddCustomSubject();
                  }
                }}
                placeholder="e.g., MAT, PHY, CHE..."
                className="custom-subject-textbox"
                maxLength={10}
              />
              <button 
                type="button"
                onClick={handleAddCustomSubject}
                className="add-custom-btn"
                disabled={!customSubject.trim()}
              >
                + Add Subject
              </button>
            </div>
            <div className="custom-subject-help">
              <strong>All subjects are fully supported:</strong> All course details (description, credits, SBCs, prerequisites, corequisites, antirequisites, advisory prerequisites) will be extracted for any subject.
              <br/><br/>
              <strong>Instructions:</strong> Enter <strong>ONE subject at a time</strong> (e.g., MAT, PHY, CHE). 
              Do <strong>NOT</strong> use commas or multiple subjects. Add one subject, click "Add Subject", 
              then repeat for additional subjects.
            </div>
          </div>

          {/* Selected Subjects Display */}
          {selectedSubjects.length > 0 && (
            <div className="selected-subjects">
              <h4>Selected Subjects ({selectedSubjects.length}):</h4>
              <div className="selected-subjects-list">
                {selectedSubjects.map(subject => (
                  <span key={subject} className="selected-subject supported">
                    {subject}
                    <button
                      type="button"
                      onClick={() => handleRemoveSubject(subject)}
                      className="remove-subject"
                      title="Remove subject"
                    >
                      ×
                    </button>
                  </span>
                ))}
              </div>
            </div>
          )}

        
        </div>

        <div className="action-buttons">
          <button
            onClick={handleScraping}
            disabled={isLoading || !selectedTerm || selectedSubjects.length === 0}
            className="scrape-button"
          >
            {isLoading ? 'Scraping...' : 'Scrape Course Catalog'}
          </button>
        </div>
      </div>
    </div>
  );
};

export default CourseCatalogManagement;
