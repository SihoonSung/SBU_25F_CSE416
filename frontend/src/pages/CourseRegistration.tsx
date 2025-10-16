import { useState, useEffect } from 'react'
import './CourseRegistration.css'

/*
 * Course Registration Page Component
 * Allows students to search for classes and add them to their cart.
 * Includes expandable course details and real-time search.
 * 
 * TP: Used Cursor to implement frontend-backend integration and UI components.
 */

function CourseRegistration() {
  const [searchTerm, setSearchTerm] = useState('')
  const [selectedCourse, setSelectedCourse] = useState<any>(null)
  const [searchResults, setSearchResults] = useState<any[]>([])
  const [currentPage, setCurrentPage] = useState(1)
  const [itemsPerPage] = useState(10)
  const [availableTerms, setAvailableTerms] = useState<any[]>([])
  const [selectedSemester, setSelectedSemester] = useState('')
  const [selectedYear, setSelectedYear] = useState<number | string>('')
  const [cart, setCart] = useState([
    {
      id: 'CSE101',
      code: 'CSE101',
      title: 'Computer Science Principles',
      schedule: 'Mon/Wed 11:00 AM',
      instructor: 'Jalaa Hoblos',
      status: 'Enrolled'
    },
    {
      id: 'WRT101',
      code: 'WRT101',
      title: 'Introductory Writing Workshop',
      schedule: 'Mon/Wed 9:30 AM',
      instructor: 'Soni Adhikari',
      status: 'Waitlisted'
    },
    {
      id: 'CSE114',
      code: 'CSE114',
      title: 'Intro to Object-Oriented Prog',
      schedule: 'Mon/Wed 11:00 AM',
      instructor: 'Paul Fodor',
      status: 'Conflict'
    }
  ])

  /**
   * Fetch available terms and classes on component mount
   */
  useEffect(() => {
    fetchAvailableTerms()
  }, [])
  
  /**
   * Fetch classes when term selection changes
   */
  useEffect(() => {
    if (selectedSemester && selectedYear) {
      fetchClassesByTerm(selectedSemester, selectedYear)
    }
  }, [selectedSemester, selectedYear])

  /**
   * Transform backend class data to frontend format
   */
  const transformClassData = (cls: any) => ({
    id: cls.classId,
    code: `${cls.department}${cls.courseNum}.${cls.section}`,
    courseId: cls.classId.toString(),
    department: cls.department,
    courseNum: cls.courseNum,
    section: cls.section,
    sectionType: cls.sectionType, // LEC, LAB, REC
    title: cls.title,
    schedule: cls.schedule,
    instructor: cls.instructor,
    location: cls.location,
    lecSchedule: cls.lecSchedule, // LEC schedule
    lecLocation: cls.lecLocation, // LEC location
    prerequisites: cls.prerequisites,
    corequisites: cls.corequisites,
    antirequisites: cls.antirequisites,
    sbc: cls.sbc, // SBC codes
    minCredits: cls.minCredits,
    maxCredits: cls.maxCredits,
    credits: cls.minCredits === cls.maxCredits ? cls.minCredits : `${cls.minCredits}-${cls.maxCredits}`,
    semester: cls.semester,
    year: cls.year
  })

  /**
   * Sort classes by Department → Course Number → Section
   */
  const sortClasses = (classes: any[]) => {
    return classes.sort((a: any, b: any) => {
      // Sort by Department first
      if (a.department !== b.department) {
        return a.department.localeCompare(b.department)
      }
      // Then by Course Number
      if (a.courseNum !== b.courseNum) {
        return a.courseNum - b.courseNum
      }
      // Finally by Section
      return a.section.localeCompare(b.section)
    })
  }

  /**
   * Fetch available semester/year combinations from backend
   */
  const fetchAvailableTerms = async () => {
    try {
      const response = await fetch('http://localhost:8080/api/scraping/terms')
      const data = await response.json()
      
      // Transform scraping terms data to course registration format
      const transformedTerms = data.map((term: any) => ({
        semester: term.name.split(' ')[0], // "Fall" from "Fall 2025"
        year: parseInt(term.name.split(' ')[1]) // 2025 from "Fall 2025"
      }))
      
      setAvailableTerms(transformedTerms)
      
      // Auto-select the first term if available
      if (transformedTerms.length > 0) {
        setSelectedSemester(transformedTerms[0].semester)
        setSelectedYear(transformedTerms[0].year)
      }
    } catch (error) {
      console.error('Error fetching terms:', error)
    }
  }
  
  /**
   * Fetch classes by semester and year
   * Transforms data to frontend format (e.g., CSE101.01)
   */
  const fetchClassesByTerm = async (semester: string, year: number | string) => {
    try {
      const termName = `${semester} ${year}` // "Fall 2025"
      const response = await fetch(`http://localhost:8080/api/scraping/courses/byTerm?term=${encodeURIComponent(termName)}`)
      const data = await response.json()
      
      // Transform course data to class format for registration
      const transformedData = data.map((course: any) => ({
        id: course.id,
        code: `${course.subject}${course.courseNumber}`,
        courseId: course.id.toString(),
        department: course.subject,
        courseNum: course.courseNumber,
        section: '01', // Default section
        sectionType: 'LEC',
        title: course.courseName,
        schedule: 'TBA', // Default schedule
        instructor: 'TBA', // Default instructor
        location: 'TBA', // Default location
        lecSchedule: 'TBA',
        lecLocation: 'TBA',
        prerequisites: course.prerequisites || 'None',
        corequisites: course.corequisites || 'None',
        antirequisites: course.antirequisites || 'None',
        sbc: course.sbcsSatisfied || 'None',
        minCredits: course.credits,
        maxCredits: course.credits,
        credits: course.credits,
        semester: semester,
        year: year
      }))
      
      setSearchResults(transformedData)
    } catch (error) {
      console.error('Error fetching classes:', error)
    }
  }
  

  /**
   * Handle search functionality
   * If search term is empty, fetch classes for selected term
   * Otherwise, filter the current results by search term
   */
  const handleSearch = async (term: string) => {
    if (!term.trim()) {
      if (selectedSemester && selectedYear) {
        fetchClassesByTerm(selectedSemester, selectedYear)
      }
      return
    }
    
    // Filter current search results by search term
    const filteredResults = searchResults.filter((course: any) => 
      course.code.toLowerCase().includes(term.toLowerCase()) ||
      course.title.toLowerCase().includes(term.toLowerCase()) ||
      course.department.toLowerCase().includes(term.toLowerCase())
    )
    
    setSearchResults(filteredResults)
    setCurrentPage(1) // Reset to first page on new search
  }

  /**
   * Calculate pagination for search results
   * Shows 10 classes per page
   */
  const indexOfLastItem = currentPage * itemsPerPage
  const indexOfFirstItem = indexOfLastItem - itemsPerPage
  const currentItems = searchResults.slice(indexOfFirstItem, indexOfLastItem)
  const totalPages = Math.ceil(searchResults.length / itemsPerPage)

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

  /**
   * Handle course selection to show details
   */
  const handleCourseSelect = (course: any) => {
    setSelectedCourse(course)
  }

  /**
   * Go back to search results from course details
   */
  const handleBackToSearch = () => {
    setSelectedCourse(null)
  }

  /**
   * Add a course to the cart
   */
  const handleAddToCart = (course: any) => {
    setCart([...cart, { ...course, status: 'Added' }])
  }

  /**
   * Remove a course from the cart
   */
  const handleDropCourse = (courseId: string) => {
    setCart(cart.filter(course => course.id !== courseId))
  }

  // Check if there are any time conflicts in the cart
  const hasConflict = cart.some(course => course.status === 'Conflict')

  return (
    <div className="course-registration-page">
      {/* Search Section */}
      <div className="search-section">
        <h2>Course Search</h2>
        
        {/* Term Selection */}
        <div className="term-selection">
          <label htmlFor="semester">Semester:</label>
          <select
            id="semester"
            value={selectedSemester}
            onChange={(e) => setSelectedSemester(e.target.value)}
          >
            {availableTerms.map((term: any) => (
              <option key={`${term.semester}-${term.year}`} value={term.semester}>
                {term.semester}
              </option>
            ))}
          </select>
          
          <label htmlFor="year">Year:</label>
          <select
            id="year"
            value={selectedYear}
            onChange={(e) => setSelectedYear(Number(e.target.value))}
          >
            {availableTerms.map((term: any) => (
              <option key={`${term.semester}-${term.year}`} value={term.year}>
                {term.year}
              </option>
            ))}
          </select>
        </div>
        
        <div className="search-container">
          {/* Search Bar */}
          <div className="search-bar">
            <div className="search-icon">🔍</div>
            <input
              type="text"
              placeholder="Search by class ID, course code, title, or instructor..."
              value={searchTerm}
              onChange={(e) => {
                setSearchTerm(e.target.value)
                handleSearch(e.target.value)
              }}
            />
          </div>
          {/* Search Results */}
          <div className="search-results">
            {searchResults.length === 0 ? (
              <div className="no-data-message">
                <div className="no-data-icon">📚</div>
                <h3>No Class Data Available</h3>
                <p>There are no classes in the database for the selected term.</p>
                <p className="hint-text">💡 Go to PDF Upload & Class Management page to import class schedules</p>
              </div>
            ) : (
              currentItems.map((course) => (
              <div key={course.id} className="course-item-container">
                {selectedCourse && selectedCourse.id === course.id ? (
                  <div 
                    className="course-detail-card clickable"
                    onClick={handleBackToSearch}
                  >
                    <div className="course-detail-header">
                      <div className="course-detail-info">
                        <div className="course-code-large">{course.code}</div>
                        <div className="course-id">{course.courseId}</div>
                      </div>
                      <button 
                        className="add-to-cart-btn"
                        onClick={(e) => {
                          e.stopPropagation();
                          handleAddToCart(course);
                        }}
                      >
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                          <circle cx="9" cy="21" r="1"></circle>
                          <circle cx="20" cy="21" r="1"></circle>
                          <path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6"></path>
                        </svg>
                        <span className="btn-text">Add to Cart</span>
                      </button>
                    </div>
                    <div className="course-detail-content">
                      <div className="detail-row">
                        <span className="detail-label">Title:</span>
                        <span className="detail-value">{course.title}</span>
                      </div>
                      
                      <div className="detail-row">
                        <span className="detail-label">Credits:</span>
                        <span className="detail-value">{course.credits}</span>
                      </div>
                      
                      {course.sbc && course.sbc !== 'None' && (
                        <div className="detail-row">
                          <span className="detail-label">SBC:</span>
                          <span className="detail-value sbc-badges">{course.sbc}</span>
                        </div>
                      )}
                      
                      {/* Show LEC info first if available (priority) */}
                      {course.lecSchedule && (
                        <div className="detail-section lec-section">
                          <div className="section-title">📚 Lecture Section (Required)</div>
                          <div className="detail-row">
                            <span className="detail-label">Schedule:</span>
                            <span className="detail-value">{course.lecSchedule}</span>
                          </div>
                          <div className="detail-row">
                            <span className="detail-label">Location:</span>
                            <span className="detail-value">{course.lecLocation}</span>
                          </div>
                        </div>
                      )}
                      
                      {/* Show current section (LAB/REC/etc) */}
                      <div className="detail-section">
                        <div className="section-title">
                          {course.sectionType === 'LAB' && '🔬 Lab Section'}
                          {course.sectionType === 'REC' && '📝 Recitation Section'}
                          {course.sectionType === 'LEC' && '📚 Lecture Section'}
                          {!course.sectionType && 'Section'}
                        </div>
                        <div className="detail-row">
                          <span className="detail-label">Schedule:</span>
                          <span className="detail-value">{course.schedule}</span>
                        </div>
                        <div className="detail-row">
                          <span className="detail-label">Location:</span>
                          <span className="detail-value">{course.location}</span>
                        </div>
                        <div className="detail-row">
                          <span className="detail-label">Instructor:</span>
                          <span className="detail-value">{course.instructor}</span>
                        </div>
                      </div>
                      
                      {/* Requisites Section */}
                      <div className="requisites-section">
                        {course.prerequisites && course.prerequisites !== 'None' && (
                          <div className="detail-row">
                            <span className="detail-label">Prerequisites:</span>
                            <span className="detail-value">{course.prerequisites}</span>
                          </div>
                        )}
                        {course.corequisites && course.corequisites !== 'None' && (
                          <div className="detail-row">
                            <span className="detail-label">Corequisites:</span>
                            <span className="detail-value">{course.corequisites}</span>
                          </div>
                        )}
                        {course.antirequisites && course.antirequisites !== 'None' && (
                          <div className="detail-row">
                            <span className="detail-label">Antirequisites:</span>
                            <span className="detail-value">{course.antirequisites}</span>
                          </div>
                        )}
                      </div>
                      
                      {/* External link to Classie-Evals for course evaluations */}
                      <div className="detail-row">
                        <a 
                          href={`https://classie-evals.stonybrook.edu/?SearchKeyword=${course.department}${course.courseNum}&SearchTerm=ALL`}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="detail-link"
                          onClick={(e) => e.stopPropagation()}
                        >
                          📊 View Course Evaluations
                        </a>
                      </div>
                    </div>
                  </div>
                ) : (
                  <div 
                    className="search-result-card"
                  >
                    <div 
                      className="course-info clickable"
                      onClick={() => handleCourseSelect(course)}
                    >
                      <div className="course-code">{course.code}</div>
                      <div className="course-title">{course.title}</div>
                      <div className="course-schedule">{course.schedule}</div>
                      <div className="course-instructor">{course.instructor}</div>
                    </div>
                    <button 
                      className="add-to-cart-btn"
                      onClick={() => handleAddToCart(course)}
                    >
                      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                        <circle cx="9" cy="21" r="1"></circle>
                        <circle cx="20" cy="21" r="1"></circle>
                        <path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6"></path>
                      </svg>
                      <span className="btn-text">Add to Cart</span>
                    </button>
                  </div>
                )}
              </div>
              ))
            )}
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="pagination">
              <button 
                className="pagination-btn"
                onClick={handlePrevPage}
                disabled={currentPage === 1}
              >
                ← Previous
              </button>
              <span className="pagination-info">
                Page {currentPage} of {totalPages} ({searchResults.length} results)
              </span>
              <button 
                className="pagination-btn"
                onClick={handleNextPage}
                disabled={currentPage === totalPages}
              >
                Next →
              </button>
            </div>
          )}
        </div>
      </div>

      {/* Cart Section */}
      <div className="cart-section">
        <h2>My Course Cart</h2>
        <div className="cart-container">
          {/* Conflict Warning */}
          {hasConflict && (
            <div className="conflict-warning">
              <div className="warning-icon">ℹ</div>
              <span>Time conflict: CSE101 and CSE114 overlap. Please resolve before submitting.</span>
            </div>
          )}
          
          {/* Cart Courses */}
          <div className="cart-courses">
            {cart.map((course) => (
              <div key={course.id} className="cart-course-card">
                <div className="course-info">
                  <div className="course-code">{course.code}</div>
                  <div className="course-title">{course.title}</div>
                  <div className="course-schedule">{course.schedule}</div>
                  <div className="course-instructor">{course.instructor}</div>
                </div>
                <span className={`status-tag ${course.status.toLowerCase()}`}>
                  {course.status}
                </span>
                <button 
                  className="drop-btn"
                  onClick={() => handleDropCourse(course.id)}
                >
                  Drop
                </button>
              </div>
            ))}
          </div>

          {/* Submit Registration Button */}
          <button className="submit-registration-btn">
            Submit Registration
          </button>
        </div>
      </div>
    </div>
  )
}

export default CourseRegistration
