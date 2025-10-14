import React, { useState, useEffect } from 'react';
import './CourseCatalogManagement.css';


//TP : Used cursor to display course catalog data in temrs of design


interface ScrapedCourse {
  id: number;
  subject: string;
  courseNumber: string;
  courseName: string;
  description: string;
  credits: number;
  maxCredits?: number; // Maximum credits for credit ranges
  prerequisites: string;
  corequisites: string;
  antirequisites: string;
  advisoryPrerequisites: string;
  sbcsSatisfied: string;
  warning?: boolean; // True if placement exam simplification was applied
  term?: string; // Optional term name for display
}

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
  const [availableSubjects] = useState<string[]>(['BIO', 'PSY', 'CSE', 'ECO', 'AMS', 'POL']);
  const [supportedSubjects] = useState<string[]>(['BIO', 'PSY', 'CSE', 'ECO', 'AMS', 'POL']);
  const [scrapedCourses, setScrapedCourses] = useState<ScrapedCourse[]>([]);
  const [terms, setTerms] = useState<Term[]>([]);
  const [currentPage, setCurrentPage] = useState<number>(1);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [message, setMessage] = useState<{ type: 'success' | 'error' | 'warning', text: string } | null>(null);
  const [totalCourses, setTotalCourses] = useState<number>(0);

  const coursesPerPage = 10;

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


  const checkForUnsupportedSubjects = (subjects: string[]) => {
    const unsupportedSelected = subjects.filter(s => !supportedSubjects.includes(s));
    if (unsupportedSelected.length > 0) {
      setMessage({
        type: 'warning',
        text: `Warning: Subject(s) ${unsupportedSelected.join(', ')} are not fully supported. Course information will be marked as "unknown".`
      });
    } else {
      // Clear warning if all selected subjects are supported
      if (message?.type === 'warning' && message.text.includes('are not fully supported')) {
        setMessage(null);
      }
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
      const response = await fetch('http://localhost:8080/api/scraping/scrape', {
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
        await fetchScrapedCourses();
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

  const fetchScrapedCourses = async () => {
    if (!selectedTerm || selectedSubjects.length === 0) return;

    try {
      // Fetch courses for each subject individually and combine results
      const allCourses: ScrapedCourse[] = [];
      
      for (const subject of selectedSubjects) {
        const response = await fetch(
          `http://localhost:8080/api/scraping/courses?term=${encodeURIComponent(selectedTerm)}&subject=${subject}`
        );
        
        if (response.ok) {
          const courses = await response.json();
          allCourses.push(...courses);
        }
      }
      
      setScrapedCourses(allCourses);
      setTotalCourses(allCourses.length);
      setCurrentPage(1);
    } catch (error) {
      console.error('Error fetching courses:', error);
    }
  };

  const handleViewCourses = async () => {
    if (!selectedTerm || selectedSubjects.length === 0) {
      setMessage({ type: 'warning', text: 'Please select both term and at least one subject to view courses.' });
      return;
    }
    await fetchScrapedCourses();
  };

  // Reset Term functionality
  /*
  const handleResetTerm = async () => {
    if (!selectedTerm) {
      setMessage({ type: 'warning', text: 'Please select a term to reset.' });
      return;
    }

    if (!confirm(`Are you sure you want to reset "${selectedTerm}"? This will allow re-scraping but won't delete existing data.`)) {
      return;
    }

    try {
      const response = await fetch(
        `http://localhost:8080/api/scraping/reset-term?termName=${encodeURIComponent(selectedTerm)}`,
        { method: 'POST' }
      );

      const result = await response.json();
      
      if (result.success) {
        setMessage({ type: 'success', text: result.message });
        await fetchTerms(); // Refresh terms
      } else {
        setMessage({ type: 'error', text: result.message });
      }
    } catch (error) {
      setMessage({ type: 'error', text: 'Failed to reset term. Please try again.' });
      console.error('Reset error:', error);
    }
  };
  */

  const getTermStatusDisplay = (termName: string) => {
    if (!isValidTermFormat(termName)) {
      return <span className="status-badge invalid">⚠ Invalid Format</span>;
    }

    const status = checkTermStatus(termName);
    switch (status) {
      case 'scraped':
        return <span className="status-badge scraped">✓ Already Scraped</span>;
      case 'not_scraped':
        return <span className="status-badge not-scraped">○ Ready to Scrape</span>;
      case 'not_found':
        return <span className="status-badge not-found">✓ Valid Term</span>;
      default:
        return <span className="status-badge unknown">? Unknown</span>;
    }
  };

  const getPaginatedCourses = () => {
    const startIndex = (currentPage - 1) * coursesPerPage;
    const endIndex = startIndex + coursesPerPage;
    return scrapedCourses.slice(startIndex, endIndex);
  };

  const totalPages = Math.ceil(totalCourses / coursesPerPage);

  return (
    <div className="course-catalog-management">
      <div className="page-header">
        <h1>Course Catalog Management</h1>
        <p>Scrape and manage course catalog data from SBU's online catalog</p>
      </div>

      {message && (
        <div className={`message ${message.type}`}>
          <span className="message-icon">
            {message.type === 'success' && '✓'}
            {message.type === 'error' && '✗'}
            {message.type === 'warning' && '⚠'}
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
         
          {/* Supported Subjects Only */}
          <div className="subjects-section">
            <h4>Supported Subjects</h4>
            <div className="subjects-grid">
              {availableSubjects.map(subject => (
                <label key={subject} className="subject-checkbox supported">
                  <input
                    type="checkbox"
                    checked={selectedSubjects.includes(subject)}
                    onChange={() => handleSubjectChange(subject)}
                  />
                  <span className="subject-label">{subject}</span>
                  <div className="subject-status">Supported</div>
                </label>
              ))}
            </div>
            <div className="subject-help">
              <span style={{ fontSize: '1rem' }}>
                <strong>Supported Subjects :</strong> BIO, PSY, CSE, ECO, AMS, POL - Only those subjects are be available. Please check the green box
              </span>
          </div>
          
          </div>


          {/* Selected Subjects Display */}
          {selectedSubjects.length > 0 && (
            <div className="selected-subjects">
              <h4>Selected Subjects:</h4>
              <div className="selected-subjects-list">
                {selectedSubjects.map(subject => (
                  <span key={subject} className={`selected-subject ${supportedSubjects.includes(subject) ? 'supported' : 'unsupported'}`}>
                    {subject}
                    {!supportedSubjects.includes(subject) && <span className="warning-icon">⚠</span>}
                    <button
                      type="button"
                      onClick={() => handleSubjectChange(subject)}
                      className="remove-subject"
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

          <button
            onClick={handleViewCourses}
            disabled={!selectedTerm || selectedSubjects.length === 0}
            className="view-button"
          >
            View Scraped Courses
          </button>

          {/* Reset Term button - commented out as it may not be needed
          <button
            onClick={handleResetTerm}
            disabled={!selectedTerm || !isValidTermFormat(selectedTerm) || checkTermStatus(selectedTerm) !== 'scraped'}
            className="reset-button"
          >
            Reset Term
          </button>
          */}
        </div>
      </div>

      {scrapedCourses.length > 0 && (
        <div className="results-section">
          <div className="results-header">
            <h3>Scraped Courses ({totalCourses} total)</h3>
            <div className="pagination-info">
              Showing {((currentPage - 1) * coursesPerPage) + 1}-{Math.min(currentPage * coursesPerPage, totalCourses)} of {totalCourses}
            </div>
          </div>

          <div className="courses-table-container">
            <table className="courses-table">
              <thead>
                <tr>
                  <th>Subject</th>
                  <th>Number</th>
                  <th>Course Name</th>
                  <th>Credits</th>
                  <th>Max Credits</th>
                  <th>Prerequisites</th>
                  <th>SBCs</th>
                  <th>Warning</th>
                </tr>
              </thead>
              <tbody>
                {getPaginatedCourses().map(course => (
                  <tr key={course.id}>
                    <td className="subject-cell">{course.subject}</td>
                    <td className="number-cell">{course.courseNumber}</td>
                    <td className="name-cell">{course.courseName}</td>
                    <td className="credits-cell">{course.credits !== null ? course.credits : 'N/A'}</td>
                    <td className="credits-cell">{course.maxCredits !== null ? course.maxCredits : 'N/A'}</td>
                    <td className="prereq-cell">
                      {course.prerequisites ? (
                        <span title={course.prerequisites}>
                          {course.prerequisites.length > 50 
                            ? course.prerequisites.substring(0, 50) + '...'
                            : course.prerequisites
                          }
                        </span>
                      ) : 'None'}
                    </td>
                    <td className="sbc-cell">{course.sbcsSatisfied || 'N/A'}</td>
                    <td className="warning-cell">
                      {course.warning ? (
                        <span className="warning-icon" title="Placement exam simplification applied">⚠️</span>
                      ) : (
                        <span className="no-warning">✓</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {totalPages > 1 && (
            <div className="pagination">
              <button
                onClick={() => setCurrentPage(prev => Math.max(1, prev - 1))}
                disabled={currentPage === 1}
                className="page-button"
              >
                Previous
              </button>
              
              <span className="page-info">
                Page {currentPage} of {totalPages}
              </span>
              
              <button
                onClick={() => setCurrentPage(prev => Math.min(totalPages, prev + 1))}
                disabled={currentPage === totalPages}
                className="page-button"
              >
                Next
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default CourseCatalogManagement;
