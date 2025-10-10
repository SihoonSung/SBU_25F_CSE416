import { useState } from 'react'
import './CourseRegistration.css'

/*
 * TP: Used Cursor to make it consistent with the codebase.
 */

function CourseRegistration() {
  const [searchTerm, setSearchTerm] = useState('')
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

  const [searchResults] = useState([
    {
      id: 'CSE355',
      code: 'CSE355',
      title: 'Computational Geometry',
      schedule: 'Mon/Wed 11:00 AM',
      instructor: 'Joe Mitchell'
    }
  ])

  const handleAddToCart = (course: any) => {
    setCart([...cart, { ...course, status: 'Added' }])
  }

  const handleDropCourse = (courseId: string) => {
    setCart(cart.filter(course => course.id !== courseId))
  }

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
              onChange={(e) => setSearchTerm(e.target.value)}
            />
          </div>
          {/* Search Results */}
          <div className="search-results">
            {searchResults.map((course) => (
              <div key={course.id} className="search-result-card">
                <div className="course-info">
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
