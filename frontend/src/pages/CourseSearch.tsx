import React, { useState, useEffect } from 'react'
import './CourseSearch.css'

interface Course {
  code: string
  title: string
  credits: number
  prerequisites: string
  instructor: string
  time: string
  seatsOpen: number
}

function CourseSearch() {
  const [filters, setFilters] = useState({
    subject: 'CSE',
    courseNumber: '',
    sbc: 'Any',
    instructor: '',
    daysTime: 'Any'
  })

  const [courses, setCourses] = useState<Course[]>([
    {
      code: 'CSE101',
      title: 'Computer Science Principles',
      credits: 4,
      prerequisites: 'Level 2+ or higher on the mathematics placement examination;',
      instructor: 'Jalaa Hoblos',
      time: 'View details',
      seatsOpen: 47
    },
    {
      code: 'CSE103',
      title: 'Data Science Principles',
      credits: 4,
      prerequisites: 'Level 3 or higher on the mathematics placement examination;',
      instructor: 'Kevin McDonnell',
      time: 'View details',
      seatsOpen: 24
    },
    {
      code: 'CSE114',
      title: 'Introduction to Object-Oriented Programming',
      credits: 4,
      prerequisites: 'CSE 101 or CSE 110 or ISE 108;',
      instructor: 'Anita Wasilewska',
      time: 'View details',
      seatsOpen: 32
    },
    {
      code: 'CSE214',
      title: 'Data Structures',
      credits: 4,
      prerequisites: 'CSE 114 or ISE 218;',
      instructor: 'Paul Fodor',
      time: 'View details',
      seatsOpen: 28
    },
    {
      code: 'CSE300',
      title: 'Technical Communications',
      credits: 3,
      prerequisites: 'WRT 102;',
      instructor: 'Richard McKenna',
      time: 'View details',
      seatsOpen: 15
    }
  ])

  const [filteredCourses, setFilteredCourses] = useState<Course[]>(courses)

  const handleFilterChange = (field: string, value: string) => {
    setFilters(prev => ({
      ...prev,
      [field]: value
    }))
  }

  const handleAddToCart = (courseCode: string) => {
    console.log(`Added ${courseCode} to cart`)
    // TODO: Implement add to cart functionality
  }

  const handleViewDetails = (courseCode: string) => {
    console.log(`View details for ${courseCode}`)
    // TODO: Implement view details functionality
  }

  useEffect(() => {
    let filtered = courses.filter(course => {
      // Subject filter
      if (filters.subject && !course.code.startsWith(filters.subject)) {
        return false
      }
      
      // Course number filter
      if (filters.courseNumber) {
        const courseNumber = course.code.replace(/[A-Z]/g, '')
        if (!courseNumber.includes(filters.courseNumber)) {
          return false
        }
      }
      
      // Instructor filter
      if (filters.instructor && !course.instructor.toLowerCase().includes(filters.instructor.toLowerCase())) {
        return false
      }
      
      return true
    })
    
    setFilteredCourses(filtered)
  }, [filters, courses])

  return (
    <div className="course-search">
      <div className="course-search-content">
        {/* Filters Sidebar */}
        <div className="filters-sidebar">
          <h2>Filters</h2>
          <div className="filter-group">
            <label htmlFor="subject">Subject:</label>
            <input
              type="text"
              id="subject"
              value={filters.subject}
              onChange={(e) => handleFilterChange('subject', e.target.value)}
              placeholder="CSE"
            />
          </div>
          
          <div className="filter-group">
            <label htmlFor="courseNumber">Course Number:</label>
            <input
              type="text"
              id="courseNumber"
              value={filters.courseNumber}
              onChange={(e) => handleFilterChange('courseNumber', e.target.value)}
              placeholder="e.g. 101"
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
              <option value="GLO">GLO</option>
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
          
          <div className="filter-group">
            <label htmlFor="instructor">Instructor:</label>
            <input
              type="text"
              id="instructor"
              value={filters.instructor}
              onChange={(e) => handleFilterChange('instructor', e.target.value)}
              placeholder="e.g. alex"
            />
          </div>
          
          <div className="filter-group">
            <label htmlFor="daysTime">Days/Time:</label>
            <select
              id="daysTime"
              value={filters.daysTime}
              onChange={(e) => handleFilterChange('daysTime', e.target.value)}
            >
              <option value="Any">Any</option>
              <option value="MWF">MWF</option>
              <option value="TTH">TTH</option>
              <option value="M-F">M-F</option>
              <option value="Weekend">Weekend</option>
            </select>
          </div>
        </div>

        {/* Results Table */}
        <div className="results-main">
          <h1>Course Search Results</h1>
          <div className="results-container">
            <div className="results-table">
            <div className="table-header">
              <div className="header-cell">COURSE CODE</div>
              <div className="header-cell">TITLE</div>
              <div className="header-cell">CREDITS</div>
              <div className="header-cell">PREREQUISITES</div>
              <div className="header-cell">INSTRUCTOR</div>
              <div className="header-cell">TIME</div>
              <div className="header-cell">SEATS OPEN</div>
              <div className="header-cell">ACTION</div>
            </div>
            
            {filteredCourses.map((course, index) => (
              <div key={course.code} className="table-row">
                <div className="table-cell course-code">{course.code}</div>
                <div className="table-cell course-title">{course.title}</div>
                <div className="table-cell course-credits">{course.credits}</div>
                <div className="table-cell course-prerequisites">{course.prerequisites}</div>
                <div className="table-cell course-instructor">{course.instructor}</div>
                <div className="table-cell course-time">
                  <button 
                    className="view-details-btn"
                    onClick={() => handleViewDetails(course.code)}
                  >
                    View details
                  </button>
                </div>
                <div className="table-cell course-seats">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <circle cx="9" cy="21" r="1"></circle>
                    <circle cx="20" cy="21" r="1"></circle>
                    <path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6"></path>
                  </svg>
                  {course.seatsOpen}
                </div>
                <div className="table-cell course-action">
                  <button 
                    className="add-to-cart-btn"
                    onClick={() => handleAddToCart(course.code)}
                  >
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <circle cx="9" cy="21" r="1"></circle>
                      <circle cx="20" cy="21" r="1"></circle>
                      <path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6"></path>
                    </svg>
                    Add to Cart
                  </button>
                </div>
              </div>
            ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

export default CourseSearch
