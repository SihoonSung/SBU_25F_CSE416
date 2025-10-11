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
  const [selectedCourse, setSelectedCourse] = useState(null)
  const [searchResults, setSearchResults] = useState([])
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
   * Fetch all classes from backend on component mount
   */
  useEffect(() => {
    fetchClasses()
  }, [])

  /**
   * Fetch all classes from the backend API
   * Transforms data to frontend format (e.g., CSE101.01)
   */
  const fetchClasses = async () => {
    try {
      const response = await fetch('http://localhost:8080/api/classes')
      const data = await response.json()
      
      // Transform backend data to frontend format
      const transformedData = data.map((cls: any) => ({
        id: cls.classId,
        code: `${cls.department}${cls.courseNum}.${cls.section}`,
        courseId: cls.classId.toString(),
        department: cls.department,
        courseNum: cls.courseNum,
        section: cls.section,
        title: cls.title,
        schedule: cls.schedule,
        instructor: cls.instructor,
        location: cls.location,
        prerequisites: cls.prerequisites,
        credits: cls.credits,
        semester: cls.semester,
        year: cls.year
      }))
      
      setSearchResults(transformedData)
    } catch (error) {
      console.error('Error fetching classes:', error)
    }
  }

  /**
   * Handle search functionality
   * If search term is empty, fetch all classes
   * Otherwise, search using the backend API
   */
  const handleSearch = async (term: string) => {
    if (!term.trim()) {
      fetchClasses()
      return
    }
    
    try {
      const response = await fetch(`http://localhost:8080/api/classes/search?q=${encodeURIComponent(term)}`)
      const data = await response.json()
      
      // Transform backend data to frontend format
      const transformedData = data.map((cls: any) => ({
        id: cls.classId,
        code: `${cls.department}${cls.courseNum}.${cls.section}`,
        courseId: cls.classId.toString(),
        department: cls.department,
        courseNum: cls.courseNum,
        section: cls.section,
        title: cls.title,
        schedule: cls.schedule,
        instructor: cls.instructor,
        location: cls.location,
        prerequisites: cls.prerequisites,
        credits: cls.credits,
        semester: cls.semester,
        year: cls.year
      }))
      
      setSearchResults(transformedData)
    } catch (error) {
      console.error('Error searching classes:', error)
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
        <div className="search-container">
          {/* Search Bar */}
          <div className="search-bar">
            <div className="search-icon">🔍</div>
            <input
              type="text"
              placeholder="Search by course code, title, or instructor..."
              value={searchTerm}
              onChange={(e) => {
                setSearchTerm(e.target.value)
                handleSearch(e.target.value)
              }}
            />
          </div>
          {/* Search Results */}
          <div className="search-results">
            {searchResults.map((course) => (
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
                        <span className="detail-label">Schedule:</span>
                        <span className="detail-value">{course.schedule}</span>
                      </div>
                      <div className="detail-row">
                        <span className="detail-label">Instructor:</span>
                        <span className="detail-value">{course.instructor}</span>
                      </div>
                      <div className="detail-row">
                        <span className="detail-label">Location:</span>
                        <span className="detail-value">{course.location}</span>
                      </div>
                      <div className="detail-row">
                        <span className="detail-label">Prerequisites:</span>
                        <span className="detail-value">{course.prerequisites}</span>
                      </div>
                      <div className="detail-row">
                        <span className="detail-link">Link</span>
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
            ))}
          </div>
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
