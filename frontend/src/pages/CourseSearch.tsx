/*
 * TP: Used Cursor to implement course search page with filtering and pagination
 */

import { useState, useEffect } from 'react'
import './CourseSearch.css'

/**
 * Course Search Page Component
 * Displays course catalog from courses table (scraped data).
 * Shows detailed course information including descriptions, prerequisites, SBCs, etc.
 */

interface Course {
  id: number
  subject: string
  courseNumber: string
  courseName: string
  description: string
  credits: number | string
  prerequisites: string
  corequisites: string
  antirequisites: string
  advisoryPrerequisites: string
  sbcsSatisfied: string
  warning: boolean
}

function CourseSearch() {
  const backendBase = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8080'
  const [selectedCourse, setSelectedCourse] = useState<Course | null>(null)
  const [courses, setCourses] = useState<Course[]>([])
  const [filteredCourses, setFilteredCourses] = useState<Course[]>([])
  const [searchTerm, setSearchTerm] = useState('')
  const [currentPage, setCurrentPage] = useState(1)
  const [itemsPerPage] = useState(10)
  const [filters, setFilters] = useState({
    courseNumber: '',
    sbc: 'Any',
    instructor: '',
    dayTime: 'Any'
  })
  const [availableTerms, setAvailableTerms] = useState<any[]>([])
  const [selectedTerm, setSelectedTerm] = useState<string>('')
  const [warningMessage, setWarningMessage] = useState<string>('')
  const supportedSubjects = ['BIO', 'PSY', 'CSE', 'ECO', 'AMS', 'POL']

  /**
   * Fetch available terms on component mount
   */
  useEffect(() => {
    fetchTerms()
  }, [])

  /**
   * Fetch courses when term changes
   */
  useEffect(() => {
    if (selectedTerm) {
      fetchCourses()
    }
  }, [selectedTerm])

  /**
   * Fetch available terms from backend
   */
  const fetchTerms = async () => {
    try {
      const response = await fetch(`${backendBase}/api/scraping/terms`, {
        credentials: 'include'
      })
      
      if (!response.ok) {
        console.error('Failed to fetch terms:', response.status)
        setAvailableTerms([])
        return
      }
      
      const data = await response.json()
      
      // Ensure data is an array
      if (!Array.isArray(data)) {
        console.error('Terms data is not an array:', data)
        setAvailableTerms([])
        return
      }
      
      setAvailableTerms(data)
      
      // Auto-select the first term if available
      if (data.length > 0) {
        setSelectedTerm(data[0].name)
      }
      
      console.log('Fetched terms:', data.length)
    } catch (error) {
      console.error('Error fetching terms:', error)
      setAvailableTerms([])
    }
  }

  /**
   * Fetch courses for selected term (all subjects in database)
   */
  const fetchCourses = async () => {
    if (!selectedTerm) {
      setCourses([])
      return
    }

    try {
      // Fetch ALL courses for the selected term (not limited to specific subjects)
      const response = await fetch(
        `${backendBase}/api/scraping/courses/byTerm?term=${encodeURIComponent(selectedTerm)}`,
        {
          credentials: 'include'
        }
      )
      
      if (!response.ok) {
        console.error('Failed to fetch courses:', response.status)
        setCourses([])
        return
      }
      
      const allCourses = await response.json()
      
      console.log('Total courses fetched:', allCourses.length)
      
      if (allCourses.length === 0) {
        console.warn('No courses found for selected term.')
        setCourses([])
        return
      }
      
      // Transform backend data to frontend format
      const transformedData = allCourses
        .map((course: any) => ({
          id: course.id,
          subject: course.subject,
          courseNumber: course.courseNumber,
          courseName: course.courseName,
          description: course.description || 'No description available',
          credits: course.credits === course.maxCredits || course.maxCredits === null 
            ? course.credits 
            : `${course.credits}-${course.maxCredits}`,
          prerequisites: course.prerequisites || 'None',
          corequisites: course.corequisites || 'None',
          antirequisites: course.antirequisites || 'None',
          advisoryPrerequisites: course.advisoryPrerequisites || 'None',
          sbcsSatisfied: course.sbcsSatisfied || 'None',
          warning: course.warning || false
        }))
        .sort((a: Course, b: Course) => {
          // Sort by Subject → Course Number
          if (a.subject !== b.subject) {
            return a.subject.localeCompare(b.subject)
          }
          return parseInt(a.courseNumber) - parseInt(b.courseNumber)
        })
      
      console.log('Transformed courses:', transformedData.length)
      setCourses(transformedData)
    } catch (error) {
      console.error('Error fetching courses:', error)
    }
  }

  /**
   * Handle subject search (letters only - CSE, BIO, MAT, etc.)
   */
  const handleSearch = (term: string) => {
    setSearchTerm(term)
    setCurrentPage(1)
    
    // Check if subject is unsupported and show warning
    if (term.trim() !== '') {
      const upperValue = term.trim().toUpperCase()
      const isSupported = supportedSubjects.some(s => s === upperValue || upperValue.includes(s))
      
      if (!isSupported && upperValue.length >= 2) {
        setWarningMessage(
          `Warning: "${term}" may not be a fully supported subject. ` +
          `Fully supported subjects are: BIO, PSY, CSE, ECO, AMS, POL. ` +
          `Other subjects will have limited information (description, credits, SBCs only).`
        )
      } else {
        setWarningMessage('')
      }
    } else {
      setWarningMessage('')
    }
  }

  /**
   * Handle filter change
   */
  const handleFilterChange = (field: string, value: string) => {
    setFilters(prev => ({
      ...prev,
      [field]: value
    }))
    setCurrentPage(1)
  }

  /**
   * Handle course selection to show details
   */
  const handleCourseSelect = (course: Course) => {
    setSelectedCourse(course)
  }

  /**
   * Go back to search results from course details
   */
  const handleBackToSearch = () => {
    setSelectedCourse(null)
  }


  /**
   * Apply filters and search to courses
   */
  useEffect(() => {
    let filtered = courses.filter(course => {
      // Subject filter (Course Code - letters only, e.g., CSE, BIO)
      if (searchTerm.trim() !== '') {
        const search = searchTerm.toUpperCase()
        if (!course.subject.toUpperCase().includes(search)) {
          return false
        }
      }
      
      // Course number filter (numbers only, e.g., 416, 202)
      if (filters.courseNumber && filters.courseNumber.trim() !== '') {
        if (!course.courseNumber.includes(filters.courseNumber)) {
          return false
        }
      }
      
      // SBC filter
      if (filters.sbc && filters.sbc !== 'Any') {
        if (!course.sbcsSatisfied || !course.sbcsSatisfied.includes(filters.sbc)) {
        return false
        }
      }
      
      return true
    })
    
    setFilteredCourses(filtered)
  }, [filters, courses, searchTerm])

  /**
   * Calculate pagination for search results
   */
  const indexOfLastItem = currentPage * itemsPerPage
  const indexOfFirstItem = indexOfLastItem - itemsPerPage
  const currentItems = filteredCourses.slice(indexOfFirstItem, indexOfLastItem)
  const totalPages = Math.ceil(filteredCourses.length / itemsPerPage)

  /**
   * Navigate to next page
   */
  const handleNextPage = () => {
    if (currentPage < totalPages) {
      setCurrentPage(currentPage + 1)
    }
  }

  /**
   * Navigate to previous page
   */
  const handlePrevPage = () => {
    if (currentPage > 1) {
      setCurrentPage(currentPage - 1)
    }
  }

  return (
    <div className="course-search">
      {/* Warning Message */}
      {warningMessage && (
        <div className="warning-banner">
          {warningMessage}
        </div>
      )}

      <div className="course-search-content">
        {/* Filters Sidebar */}
        <div className="filters-sidebar">
          <h2>Filters</h2>
          
          {/* Term Selection */}
          <div className="filter-group">
            <label htmlFor="term-select">Term:</label>
            <select
              id="term-select"
              value={selectedTerm}
              onChange={(e) => setSelectedTerm(e.target.value)}
            >
              <option value="">Select Term...</option>
              {availableTerms.map((term: any) => (
                <option key={term.id} value={term.name}>
                  {term.name}
                </option>
              ))}
            </select>
          </div>

          {/* Info Text */}
          <div className="filter-info">
            <p>Showing all courses from the database for the selected term</p>
          </div>
          
          {/* Subject Search (Letters only) */}
          <div className="filter-group">
            <label htmlFor="search">Subject (Course Code):</label>
            <input
              type="text"
              id="search"
              placeholder="e.g., CSE, BIO, MAT..."
              value={searchTerm}
              onChange={(e) => handleSearch(e.target.value.toUpperCase())}
              maxLength={10}
            />
          </div>
          
          {/* Course Number Filter (Numbers only) */}
          <div className="filter-group">
            <label htmlFor="courseNumber">Course Number:</label>
            <input
              type="text"
              id="courseNumber"
              value={filters.courseNumber}
              onChange={(e) => handleFilterChange('courseNumber', e.target.value.replace(/\D/g, ''))}
              placeholder="e.g., 416, 202, 125..."
              maxLength={5}
            />
          </div>
          {/* Instructor Filter (text) */}
          <div className="filter-group">
            <label htmlFor="instructor">Instructor:</label>
            <input
              type="text"
              id="instructor"
              value={filters.instructor}
              onChange={(e) => handleFilterChange('instructor', e.target.value)}
              placeholder="e.g., alex"
              maxLength={40}
            />
          </div>
          
          <div className="filter-group">
            <label htmlFor="sbc">SBC:</label>
            <select
              id="sbc"
              value={filters.sbc}
              onChange={(e) => handleFilterChange('sbc', e.target.value)}
            >
              <option value="Any">Any</option>
              <option value="ARTS">ARTS</option>
              <option value="CER">CER</option>
              <option value="ESI">ESI</option>
              <option value="EXP+">EXP+</option>
              <option value="GLO">GLO</option>
              <option value="HFA">HFA</option>
              <option value="HUM">HUM</option>
              <option value="LANG">LANG</option>
              <option value="QPS">QPS</option>
              <option value="SBS">SBS</option>
              <option value="SNW">SNW</option>
              <option value="STAS">STAS</option>
              <option value="TECH">TECH</option>
              <option value="USA">USA</option>
              <option value="WRT">WRT</option>
            </select>
          </div>
          {/* Day/Time Filter (preset) */}
          <div className="filter-group">
            <label htmlFor="dayTime">Days/Time:</label>
            <select
              id="dayTime"
              value={filters.dayTime}
              onChange={(e) => handleFilterChange('dayTime', e.target.value)}
            >
              <option value="Any">Any</option>
              <option value="Morning">Morning</option>
              <option value="Afternoon">Afternoon</option>
              <option value="Evening">Evening</option>
            </select>
          </div>
          
          <div className="filter-stats">
            <div className="stat-item">
              <span className="stat-label">Total Courses:</span>
              <span className="stat-value">{courses.length}</span>
            </div>
            <div className="stat-item">
              <span className="stat-label">Filtered Results:</span>
              <span className="stat-value">{filteredCourses.length}</span>
            </div>
          </div>
          
          {/* Clear Filters Button */}
          <button 
            className="clear-filters-btn"
            onClick={() => {
              setFilters({ courseNumber: '', sbc: 'Any', instructor: '', dayTime: 'Any' })
              setSearchTerm('')
              setCurrentPage(1)
            }}
          >
            Clear Filters
          </button>
        </div>

        {/* Results */}
        <div className="results-main">
          <h1>Courses</h1>
          <div className="catalog-results-cards">
            {courses.length === 0 ? (
              <div className="catalog-no-data">
                <div className="catalog-no-data-icon"></div>
                <h3>No Course Data Available</h3>
                <p>The course catalog is empty. Please run web scraping to import course information.</p>
                <p className="catalog-hint">Go to Course Catalog Management page to scrape courses</p>
              </div>
            ) : currentItems.length === 0 ? (
              <div className="catalog-no-results">
                <div className="catalog-no-results-icon"></div>
                <h3>No Results Found</h3>
                <p>Try adjusting your filters or search term</p>
              </div>
            ) : (
              currentItems.map((course) => (
                <div key={course.id} className="catalog-course-item">
                  {selectedCourse && selectedCourse.id === course.id ? (
                    <div 
                      className="catalog-detail-card catalog-clickable"
                      onClick={handleBackToSearch}
                    >
                      <div className="catalog-detail-header">
                        <div className="catalog-detail-info">
                          <div className="catalog-code-large">{course.subject} {course.courseNumber}</div>
                          {course.warning && (
                            <div className="catalog-warning-text">Simplified placement exam info</div>
                          )}
            </div>
                      </div>
                      <div className="catalog-detail-content">
                        {!supportedSubjects.includes(course.subject) && (
                          <div className="catalog-unsupported-warning">
                            Warning: This subject ({course.subject}) is not fully supported. Some information may be marked as "unknown".
                          </div>
                        )}
                        
                        <div className="catalog-detail-row">
                          <span className="catalog-detail-label">Title:</span>
                          <span className="catalog-detail-value">{course.courseName}</span>
                        </div>
                        
                        <div className="catalog-detail-row">
                          <span className="catalog-detail-label">Credits:</span>
                          <span className="catalog-detail-value">
                            {typeof course.credits === 'string' && course.credits.includes('-') 
                              ? `${course.credits} credits` 
                              : `${course.credits} credit${course.credits !== 1 ? 's' : ''}`
                            }
                          </span>
                        </div>
                        
                        {course.sbcsSatisfied && course.sbcsSatisfied !== 'None' && (
                          <div className="catalog-detail-row">
                            <span className="catalog-detail-label">SBC:</span>
                            <span className={`catalog-detail-value ${course.sbcsSatisfied === 'unknown' ? 'catalog-unknown-info' : 'catalog-sbc-badges'}`}>
                              {course.sbcsSatisfied}
                            </span>
                          </div>
                        )}
                        
                        {course.description && course.description !== 'No description available' && (
                          <div className="catalog-detail-row">
                            <span className="catalog-detail-label">Description:</span>
                            <span className="catalog-detail-value catalog-description-text">{course.description}</span>
                          </div>
                        )}
                        
                        {/* Requisites Section */}
                        <div className="catalog-requisites-section">
                          {course.prerequisites && course.prerequisites !== 'None' && (
                            <div className="catalog-detail-row">
                              <span className="catalog-detail-label">Prerequisites:</span>
                              <span className={`catalog-detail-value ${course.prerequisites === 'unknown' ? 'catalog-unknown-info' : ''}`}>
                                {course.prerequisites}
                              </span>
                            </div>
                          )}
                          {course.corequisites && course.corequisites !== 'None' && (
                            <div className="catalog-detail-row">
                              <span className="catalog-detail-label">Corequisites:</span>
                              <span className={`catalog-detail-value ${course.corequisites === 'unknown' ? 'catalog-unknown-info' : ''}`}>
                                {course.corequisites}
                              </span>
                            </div>
                          )}
                          {course.antirequisites && course.antirequisites !== 'None' && (
                            <div className="catalog-detail-row">
                              <span className="catalog-detail-label">Antirequisites:</span>
                              <span className={`catalog-detail-value ${course.antirequisites === 'unknown' ? 'catalog-unknown-info' : ''}`}>
                                {course.antirequisites}
                              </span>
                            </div>
                          )}
                          {course.advisoryPrerequisites && course.advisoryPrerequisites !== 'None' && (
                            <div className="catalog-detail-row">
                              <span className="catalog-detail-label">Advisory Prerequisites:</span>
                              <span className={`catalog-detail-value ${course.advisoryPrerequisites === 'unknown' ? 'catalog-unknown-info' : ''}`}>
                                {course.advisoryPrerequisites}
                              </span>
                            </div>
                          )}
                        </div>
                        
                        {/* External link to Classie-Evals */}
                        <div className="catalog-detail-row">
                          <a 
                            href={`https://classie-evals.stonybrook.edu/?SearchKeyword=${course.subject}${course.courseNumber}&SearchTerm=ALL`}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="catalog-detail-link"
                            onClick={(e) => e.stopPropagation()}
                          >
                            View Course Evaluations
                          </a>
                        </div>
                      </div>
                    </div>
                  ) : (
                    <div 
                      className="catalog-result-card catalog-clickable"
                      onClick={() => handleCourseSelect(course)}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px', minWidth: '120px' }}>
                        <div className="catalog-course-code">
                          {course.subject} {course.courseNumber}
                        </div>
                        {(course.warning || !supportedSubjects.includes(course.subject)) && (
                          <span style={{ fontSize: '16px', color: '#f59e0b' }} title={course.warning ? 'Simplified placement exam info' : 'Limited support'}>⚠️</span>
                        )}
                      </div>
                      <div className="catalog-course-title">{course.courseName}</div>
                      <div className="catalog-course-schedule">
                        {typeof course.credits === 'string' && course.credits.includes('-') 
                          ? `${course.credits} credits` 
                          : `${course.credits} credit${course.credits !== 1 ? 's' : ''}`
                        }
                      </div>
                      <div className="catalog-course-instructor">
                        {course.sbcsSatisfied && course.sbcsSatisfied !== 'None' && course.sbcsSatisfied !== 'unknown' ? (
                          <>SBC: {course.sbcsSatisfied}</>
                        ) : course.sbcsSatisfied === 'unknown' ? (
                          <span className="catalog-unknown-info">SBC: Unknown</span>
                        ) : null}
                      </div>
                    </div>
                  )}
                </div>
              ))
            )}
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="catalog-pagination">
              <button 
                className="catalog-pagination-btn"
                onClick={handlePrevPage}
                disabled={currentPage === 1}
              >
                ← Previous
              </button>
              <span className="catalog-pagination-info">
                Page {currentPage} of {totalPages} ({filteredCourses.length} results)
              </span>
              <button 
                className="catalog-pagination-btn"
                onClick={handleNextPage}
                disabled={currentPage === totalPages}
              >
                Next →
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

export default CourseSearch
