import React, { useState, useEffect } from 'react'
import './CourseSearch.css'

/*
 * Course Search Page Component
 * Displays course catalog with filtering options.
 * 
 * TP: Used Cursor to implement backend integration and filtering logic.
 */

interface Course {
  code: string
  title: string
  credits: number
  prerequisites: string
  instructor: string
  time: string
  seatsOpen: number
  classId?: number
  department?: string
  courseNum?: number
  section?: string
  schedule?: string
  location?: string
}

function CourseSearch() {
  const [filters, setFilters] = useState({
    subject: '',
    courseNumber: '',
    sbc: 'Any',
    instructor: '',
    daysTime: 'Any'
  })

  const [courses, setCourses] = useState<Course[]>([])
  const [filteredCourses, setFilteredCourses] = useState<Course[]>([])

  /**
   * Fetch all classes from backend on component mount
   */
  useEffect(() => {
    fetchAllClasses()
  }, [])

  /**
   * Fetch all classes from the backend API
   */
  const fetchAllClasses = async () => {
    try {
      const response = await fetch('http://localhost:8080/api/classes')
      const data = await response.json()
      
      // Transform backend data to frontend format
      const transformedData = data.map((cls: any) => ({
        code: `${cls.department}${cls.courseNum}.${cls.section}`,
        title: cls.title,
        credits: cls.credits,
        prerequisites: cls.prerequisites || 'None',
        instructor: cls.instructor,
        time: cls.schedule || 'View details',
        seatsOpen: Math.floor(Math.random() * 50) + 1, // Temporary random seats
        classId: cls.classId,
        department: cls.department,
        courseNum: cls.courseNum,
        section: cls.section,
        schedule: cls.schedule,
        location: cls.location
      }))
      
      setCourses(transformedData)
      setFilteredCourses(transformedData)
    } catch (error) {
      console.error('Error fetching classes:', error)
      // Fallback to sample data if backend is not available
      const sampleCourses: Course[] = [
        {
          code: 'CSE101.01',
          title: 'Computer Science Principles',
          credits: 4,
          prerequisites: 'Level 2+ or higher on the mathematics placement examination',
          instructor: 'Jalaa Hoblos',
          time: 'View details',
          seatsOpen: 47
        },
        {
          code: 'CSE114.01',
          title: 'Introduction to Object-Oriented Programming',
          credits: 4,
          prerequisites: 'CSE 101 or CSE 110 or ISE 108',
          instructor: 'Anita Wasilewska',
          time: 'View details',
          seatsOpen: 32
        }
      ]
      setCourses(sampleCourses)
      setFilteredCourses(sampleCourses)
    }
  }

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

  /**
   * Apply filters to courses whenever filters or courses change
   */
  useEffect(() => {
    let filtered = courses.filter(course => {
      // Subject/Department filter (case insensitive)
      if (filters.subject && filters.subject.trim() !== '' && course.department) {
        if (!course.department.toLowerCase().includes(filters.subject.toLowerCase())) {
          return false
        }
      }
      
      // Course number filter
      if (filters.courseNumber && course.courseNum) {
        if (!course.courseNum.toString().includes(filters.courseNumber)) {
          return false
        }
      }
      
      // Instructor filter (case insensitive)
      if (filters.instructor && filters.instructor.trim() !== '') {
        if (!course.instructor.toLowerCase().includes(filters.instructor.toLowerCase())) {
          return false
        }
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
