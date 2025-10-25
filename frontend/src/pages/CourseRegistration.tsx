import { useState, useEffect } from 'react'
import './CourseRegistration.css'
import { checkTimeConflict } from '../utils/timeConflict'

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
  const [cart, setCart] = useState<any[]>([])
  const [userId] = useState(1) // 임시 사용자 ID
  

  /**
   * Fetch available terms and classes on component mount
   */
  useEffect(() => {
    fetchAvailableTerms()
    loadCartFromDB()
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
    lecInstructor: cls.lecInstructor, // LEC instructor
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
   * Load cart items from database
   */
  const loadCartFromDB = async () => {
    try {
      const response = await fetch(`http://localhost:8080/api/cart/items?userId=${userId}`)
      const data = await response.json()
      
      if (data.success) {
        setCart(data.items)
      } else {
        console.error('Error loading cart:', data.message)
        setCart([])
      }
    } catch (error) {
      console.error('Error loading cart from DB:', error)
      setCart([])
    }
  }


  /**
   * Fetch available semester/year combinations from backend
   */
  const fetchAvailableTerms = async () => {
    try {
      const response = await fetch('http://localhost:8080/api/classes/terms')
      const data = await response.json()

      setAvailableTerms(data)
      
      // Auto-select the first term if available
      if (data.length > 0) {
        setSelectedSemester(data[0].semester)
        setSelectedYear(data[0].year)
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
      const response = await fetch(`http://localhost:8080/api/classes/by-term?semester=${semester}&year=${year}`)
      const data = await response.json()
      
      // Transform and sort data
      const transformedData = data.map(transformClassData)
      setSearchResults(sortClasses(transformedData))

    } catch (error) {
      console.error('Error fetching classes:', error)
    }
  }
  

  /**
   * Handle search functionality
   * If search term is empty, fetch classes for selected term
   * Otherwise, search using the backend API (filtered by selected term)
   */
  const handleSearch = async (term: string) => {
    if (!term.trim()) {
      if (selectedSemester && selectedYear) {
        fetchClassesByTerm(selectedSemester, selectedYear)
      }
      return
    }
    
    try {
      const response = await fetch(`http://localhost:8080/api/classes/search?q=${encodeURIComponent(term)}`)
      const data = await response.json()
      
      // Transform, filter, and sort data
      const transformedData = data
        .map(transformClassData)
        .filter((cls: any) => cls.semester === selectedSemester && cls.year === selectedYear)

        setSearchResults(sortClasses(transformedData))

      } catch (error) {
        console.error('Error searching classes:', error)
      }
  
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
   * Combine LEC and REC schedules into a single string
   */
  const combineSchedules = (course: any) => {
    const schedules = []
    if (course.lecSchedule) schedules.push(course.lecSchedule)
    if (course.schedule) schedules.push(course.schedule)
    return schedules.join(', ')
  }


  /**
   * Add a course to the cart with time conflict checking
   */
  const handleAddToCart = async (course: any) => {
    // Check for time conflicts with existing cart items BEFORE adding
    const courseSchedule = combineSchedules(course)
    let hasConflict = false
    
    if (courseSchedule && cart.length > 0) {
      let conflictMessage = ''

      for (const cartItem of cart) {
        const cartSchedule = combineSchedules(cartItem)
        
        if (cartSchedule) {
          try {
            const result = await checkTimeConflict(courseSchedule, cartSchedule)
            if (result.hasConflict) {
              hasConflict = true
              conflictMessage += `⚠️ Time conflict with ${cartItem.code} (${cartItem.title})\n`
            }
          } catch (error) {
            console.error('Error checking time conflict:', error)
          }
        }
      }

      if (hasConflict) {
        alert(`Time Conflict Detected!\n\n${conflictMessage}\nYou can still add this class, but please resolve conflicts before registration.`)
      }
    }

    // Add to cart regardless of conflicts
    try {
      const response = await fetch('http://localhost:8080/api/cart/add', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          userId: userId,
          classId: course.id
        })
      })
      
      const data = await response.json()
      
      if (data.success) {
        // Reload cart from database
        await loadCartFromDB()
        if (!hasConflict) {
          alert('Class added to cart successfully!')
        }
      } else {
        if (data.message.includes('already exists')) {
          alert('This class is already in your cart!')
        } else {
          alert('Error adding class to cart: ' + data.message)
        }
      }
    } catch (error) {
      console.error('Error adding to cart:', error)
      alert('Error adding class to cart. Please try again.')
    }
  }

  /**
   * Remove a course from the cart
   */
  const handleDropCourse = async (courseId: string) => {
    try {
      const response = await fetch(`http://localhost:8080/api/cart/remove?userId=${userId}&classId=${courseId}`, {
        method: 'DELETE'
      })
      
      const data = await response.json()
      
      if (data.success) {
        // Reload cart from database
        await loadCartFromDB()
        alert('Class removed from cart successfully!')
      } else {
        alert('❌ Error removing class from cart: ' + data.message)
      }
    } catch (error) {
      console.error('Error removing from cart:', error)
      alert('❌ Error removing class from cart. Please try again.')
    }
  }

  /**
   * Handle submitting registration for all cart items
   */
  const handleSubmitRegistration = async () => {
    if (cart.length === 0) {
      alert('Your cart is empty. Add classes to register.')
      return
    }

    // Check if there are any items with conflicts
    const hasConflicts = cart.some(item => item.status === 'Conflict')
    if (hasConflicts) {
      alert('Cannot register classes with time conflicts. Please resolve conflicts first.')
      return
    }

    // Confirm registration
    const confirmed = window.confirm(`Are you sure you want to register for ${cart.length} class(es)?`)
    if (!confirmed) return

    try {
      const response = await fetch(`http://localhost:8080/api/cart/submit-registration?userId=${userId}`, {
        method: 'POST'
      })
      
      const data = await response.json()
      
      if (data.success) {
        // Reload cart from database to show updated statuses
        await loadCartFromDB()
        alert(`Registration successful! Registered for ${data.registeredCount} class(es).`)
      } else {
        if (data.conflicts && data.conflicts.length > 0) {
          alert(`Registration failed due to time conflicts:\n${data.conflicts.join('\n')}\n\nPlease resolve conflicts before registering.`)
        } else if (data.duplicateClasses && data.duplicateClasses.length > 0) {
          alert(`Registration failed - these classes are already registered:\n${data.duplicateClasses.join('\n')}`)
        } else {
          alert('Registration failed: ' + data.message)
        }
      }
    } catch (error) {
      console.error('Error submitting registration:', error)
      alert('Error submitting registration. Please try again.')
    }
  }


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
                          <div className="detail-row">
                            <span className="detail-label">Instructor:</span>
                            <span className="detail-value">{course.lecInstructor || 'TBA'}</span>
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
          {cart.some(item => item.status === 'Conflict') && (
            <div className="conflict-warning">
              <div className="conflict-icon">⚠️</div>
              <div className="conflict-details">
                <div className="conflict-title">Time Conflicts Detected</div>
                <div className="conflict-message">
                  Some classes in your cart have scheduling conflicts. Please resolve these before submitting registration.
                </div>
              </div>
            </div>
          )}
          
          {/* Cart Courses */}
          <div className="cart-courses">
            {cart.length === 0 ? (
              <div className="empty-cart-message">
                <div className="empty-cart-icon">🛒</div>
                <h3>Your cart is empty</h3>
                <p>Add classes to your cart to get started!</p>
              </div>
            ) : (
              cart.map((course) => (
                <div key={course.id} className="cart-course-card">
                  <div className="course-info">
                    <div className="course-code">{course.code}</div>
                    <div className="course-title">{course.title}</div>
                    <div className="course-schedule">{combineSchedules(course)}</div>
                    <div className="course-instructor">{course.instructor}</div>
                  </div>
                  <span className={`status-tag ${course.status.toLowerCase()}`}>
                    {course.status}
                  </span>
                  <button 
                    className="drop-btn"
                    onClick={() => handleDropCourse(course.classId)}
                  >
                    Drop
                  </button>
                </div>
              ))
            )}
          </div>

          {/* Submit Registration Button */}
          <button 
            className="submit-registration-btn"
            onClick={handleSubmitRegistration}
            disabled={cart.length === 0}
          >
            Submit Registration
          </button>
        </div>
      </div>

    </div>
  )
}

export default CourseRegistration
