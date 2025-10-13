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
  capacity?: number
  sbc?: string
  classId?: number
  department?: string
  courseNum?: number
  section?: string
  schedule?: string
  location?: string
}

function CourseSearch() {
  const [filters, setFilters] = useState({
    classId: '',
    subject: '',
    courseNumber: '',
    sbc: 'Any',
    instructor: '',
    daysTime: 'Any'
  })

  const [courses, setCourses] = useState<Course[]>([])
  const [filteredCourses, setFilteredCourses] = useState<Course[]>([])
  const [currentPage, setCurrentPage] = useState(1)
  const [itemsPerPage] = useState(10)
  const [availableTerms, setAvailableTerms] = useState([])
  const [selectedSemester, setSelectedSemester] = useState('')
  const [selectedYear, setSelectedYear] = useState('')

  /**
   * Fetch available terms on component mount
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
   */
  const fetchClassesByTerm = async (semester: string, year: number) => {
    try {
      const response = await fetch(`http://localhost:8080/api/classes/by-term?semester=${semester}&year=${year}`)
      const data = await response.json()
      
      // Transform backend data to frontend format
      const transformedData = data.map((cls: any) => ({
        code: `${cls.department}${cls.courseNum}.${cls.section}`,
        title: cls.title,
        credits: cls.minCredits === cls.maxCredits ? cls.minCredits : `${cls.minCredits}-${cls.maxCredits}`,
        prerequisites: cls.prerequisites || 'None',
        instructor: cls.instructor,
        time: cls.schedule || 'View details',
        seatsOpen: cls.capacity || 0,
        capacity: cls.capacity,
        sbc: cls.sbc,
        classId: cls.classId,
        department: cls.department,
        courseNum: cls.courseNum,
        section: cls.section,
        schedule: cls.schedule,
        location: cls.location
      }))
      
      setCourses(transformedData)
    } catch (error) {
      console.error('Error fetching classes:', error)
    }
  }
  
  /**
   * Fetch all classes from the backend API (deprecated)
   */
  const fetchAllClasses = async () => {
    try {
      const response = await fetch('http://localhost:8080/api/classes')
      const data = await response.json()
      
      // Transform backend data to frontend format
      const transformedData = data.map((cls: any) => ({
        code: `${cls.department}${cls.courseNum}.${cls.section}`,
        title: cls.title,
        credits: cls.minCredits === cls.maxCredits ? cls.minCredits : `${cls.minCredits}-${cls.maxCredits}`,
        prerequisites: cls.prerequisites || 'None',
        instructor: cls.instructor,
        time: cls.schedule || 'View details',
        seatsOpen: cls.capacity || 0, // Use capacity from backend
        capacity: cls.capacity,
        sbc: cls.sbc,
        classId: cls.classId,
        department: cls.department,
        courseNum: cls.courseNum,
        section: cls.section,
        schedule: cls.schedule,
        location: cls.location
      }))
      
      setCourses(transformedData)
    } catch (error) {
      console.error('Error fetching classes:', error)
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
   * Calculate pagination for search results
   * Shows 10 courses per page
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

  /**
   * Apply filters to courses whenever filters or courses change
   * Also sorts results by Department → Course Number → Section
   */
  useEffect(() => {
    let filtered = courses.filter(course => {
      // Class ID filter
      if (filters.classId && filters.classId.trim() !== '' && course.classId) {
        if (!course.classId.toString().includes(filters.classId)) {
          return false
        }
      }
      
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
    
    // Sort by Department → Course Number → Section
    filtered.sort((a, b) => {
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
    
    setFilteredCourses(filtered)
    setCurrentPage(1) // Reset to first page when filters change
  }, [filters, courses])

  return (
    <div className="course-search">
      {/* Term Selection */}
      <div className="term-selection-bar">
        <label htmlFor="semester-select">Semester:</label>
        <select
          id="semester-select"
          value={selectedSemester}
          onChange={(e) => setSelectedSemester(e.target.value)}
        >
          {availableTerms.map((term: any) => (
            <option key={`${term.semester}-${term.year}`} value={term.semester}>
              {term.semester}
            </option>
          ))}
        </select>
        
        <label htmlFor="year-select">Year:</label>
        <select
          id="year-select"
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
      
      <div className="course-search-content">
        {/* Filters Sidebar */}
        <div className="filters-sidebar">
          <h2>Filters</h2>
          <div className="filter-group">
            <label htmlFor="classId">Class ID:</label>
            <input
              type="text"
              id="classId"
              value={filters.classId}
              onChange={(e) => handleFilterChange('classId', e.target.value)}
              placeholder="e.g. 85429"
            />
          </div>
          
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
              <div className="header-cell">SBC</div>
              <div className="header-cell">PREREQUISITES</div>
              <div className="header-cell">INSTRUCTOR</div>
              <div className="header-cell">SEATS OPEN</div>
              <div className="header-cell">ACTION</div>
            </div>
            
            {currentItems.map((course, index) => (
              <div key={course.classId || course.code} className="table-row">
                <div className="table-cell course-code">{course.code}</div>
                <div className="table-cell course-title">{course.title}</div>
                <div className="table-cell course-credits">{course.credits}</div>
                <div className="table-cell course-sbc">{course.sbc || 'N/A'}</div>
                <div className="table-cell course-prerequisites">{course.prerequisites}</div>
                <div className="table-cell course-instructor">{course.instructor}</div>
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
                  Page {currentPage} of {totalPages} ({filteredCourses.length} results)
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
      </div>
    </div>
  )
}

export default CourseSearch
