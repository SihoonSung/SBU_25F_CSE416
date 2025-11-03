import { useState, useEffect } from 'react'
import './PdfUpload.css'

/**
 * Class Management Page Component
 * Allows registrars to manage class capacities.
 */

interface Class {
  classId: number
  department: string
  courseNum: number
  section: string
  title: string
  instructor: string
  schedule: string
  location: string
  semester: string
  year: number
  credits: number
  capacity: number | null
}

function PdfUpload() {
  const backendBase = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8080'
  const [classes, setClasses] = useState<Class[]>([])
  const [editingCapacity, setEditingCapacity] = useState<{[key: number]: number}>({})
  const [currentPage, setCurrentPage] = useState(1)
  const [itemsPerPage] = useState(10)
  const [searchTerm, setSearchTerm] = useState('')
  const [loadingCapacity, setLoadingCapacity] = useState(false)
  const [capacityMessage, setCapacityMessage] = useState('')

  /**
   * Fetch all classes on component mount
   */
  useEffect(() => {
    fetchClasses()
  }, [])

  /**
   * Fetch all classes from the backend
   */
  const fetchClasses = async () => {
    try {
      const response = await fetch(`${backendBase}/api/registrar/classes`, {
        credentials: 'include'
      })
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`)
      }
      const data = await response.json()
      
      // Map backend ClassEntity fields to frontend Class interface
      // Backend uses: maxCapacity, currentCapacity
      // Frontend expects: capacity (which should be maxCapacity)
      const mappedClasses = Array.isArray(data) ? data.map((cls: any) => ({
        classId: cls.classId,
        department: cls.department,
        courseNum: cls.courseNum,
        section: cls.section || '',
        title: cls.title || '',
        instructor: cls.instructor || '',
        schedule: cls.schedule || '',
        location: cls.location || '',
        semester: cls.semester || '',
        year: cls.year || cls.academicYear || 2025,
        credits: cls.minCredits || cls.maxCredits || 0,
        capacity: cls.maxCapacity !== null && cls.maxCapacity !== undefined ? cls.maxCapacity : null
      })) : []
      
      setClasses(mappedClasses)
      console.log(`[PdfUpload] Fetched ${mappedClasses.length} classes`)
    } catch (error) {
      console.error('[PdfUpload] Error fetching classes:', error)
      alert('Failed to fetch classes: ' + (error instanceof Error ? error.message : String(error)))
    }
  }

  /**
   * Handle capacity edit
   */
  const handleCapacityChange = (classId: number, value: string) => {
    const numValue = parseInt(value)
    if (!isNaN(numValue) && numValue >= 0) {
      setEditingCapacity({...editingCapacity, [classId]: numValue})
    }
  }

  /**
   * Load room capacities from server and calculate all class capacities
   */
  const handleLoadCapacity = async () => {
    setLoadingCapacity(true)
    setCapacityMessage('')

    try {
      const response = await fetch(`${backendBase}/api/rooms/load-and-calculate`, {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json'
        }
      })

      if (!response.ok) {
        const errorText = await response.text()
        throw new Error(`HTTP ${response.status}: ${errorText || response.statusText}`)
      }

      const data = await response.json()
      
      if (data.success) {
        setCapacityMessage(`Success: ${data.message || 'Capacity loaded successfully'} (${data.roomsLoaded || 0} rooms, ${data.classesUpdated || 0} classes updated)`)
        // Refresh class list to show updated capacities
        await fetchClasses()
      } else {
        setCapacityMessage('Error: ' + (data.message || 'Failed to load capacities'))
      }
    } catch (error) {
      console.error('[PdfUpload] Error loading capacities:', error)
      setCapacityMessage('Error loading capacities: ' + (error instanceof Error ? error.message : String(error)))
    } finally {
      setLoadingCapacity(false)
    }
  }

  /**
   * Save capacity change
   */
  const handleSaveCapacity = async (classId: number) => {
    const newCapacity = editingCapacity[classId]
    if (newCapacity === undefined) return

    try {
      const response = await fetch(`${backendBase}/api/registrar/classes/${classId}/capacity`, {
        credentials: 'include',
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ capacity: newCapacity })
      })

      const data = await response.json()
      
      if (data.success) {
        // Update local state
        setClasses(classes.map(cls => 
          cls.classId === classId ? {...cls, capacity: newCapacity} : cls
        ))
        // Clear editing state
        const newEditing = {...editingCapacity}
        delete newEditing[classId]
        setEditingCapacity(newEditing)
      } else {
        alert('Failed to update capacity: ' + data.message)
      }
    } catch (error) {
      alert('Error updating capacity: ' + error)
    }
  }

  /**
   * Filter and sort classes based on search term
   * Sorts by Department → Course Number → Section
   */
  const filteredClasses = classes
    .filter(cls => {
      if (!searchTerm.trim()) return true
      
      const search = searchTerm.toLowerCase()
      const code = `${cls.department}${cls.courseNum}.${cls.section}`.toLowerCase()
      const title = cls.title.toLowerCase()
      const instructor = cls.instructor.toLowerCase()
      
      return code.includes(search) || title.includes(search) || instructor.includes(search)
    })
    .sort((a, b) => {
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

  /**
   * Calculate pagination for class management table
   * Shows 10 classes per page
   */
  const indexOfLastItem = currentPage * itemsPerPage
  const indexOfFirstItem = indexOfLastItem - itemsPerPage
  const currentItems = filteredClasses.slice(indexOfFirstItem, indexOfLastItem)
  const totalPages = Math.ceil(filteredClasses.length / itemsPerPage)

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
    <div className="registrar-page">
      <h1>Class Capacity Management</h1>

      {/* Room Capacity Section */}
      <div className="capacity-section">
        <h2>Load Room Capacities</h2>
        <p className="section-description">
          Load room capacities from server and automatically calculate class capacities based on room locations.
        </p>
        
        <button 
          className="load-capacity-btn"
          onClick={handleLoadCapacity}
          disabled={loadingCapacity}
        >
          {loadingCapacity ? 'Loading...' : 'Load Capacity from Server'}
        </button>

        {capacityMessage && (
          <div className={`capacity-message ${capacityMessage.includes('Success') ? 'success' : 'error'}`}>
            {capacityMessage}
          </div>
        )}
      </div>

      {/* Class Management Section */}
      <div className="management-section">
        <h2>Manage Class Capacities</h2>
        <p className="section-description">
          View and edit the capacity for each class.
        </p>

        {/* Search Bar */}
        <div className="class-search-bar">
          <div className="search-icon"></div>
          <input
            type="text"
            placeholder="Search by course code, title, or instructor..."
            value={searchTerm}
            onChange={(e) => {
              setSearchTerm(e.target.value)
              setCurrentPage(1) // Reset to first page on search
            }}
          />
        </div>

        <div className="classes-table">
          <div className="table-header">
            <div className="th">Code</div>
            <div className="th">Title</div>
            <div className="th">Instructor</div>
            <div className="th">Schedule</div>
            <div className="th">Semester</div>
            <div className="th">Capacity</div>
            <div className="th">Actions</div>
          </div>
          
          {classes.length === 0 ? (
            <div className="no-classes">No classes found. Upload a schedule to get started.</div>
          ) : (
            currentItems.map((cls) => (
              <div key={cls.classId} className="table-row">
                <div className="td">{cls.department}{cls.courseNum}.{cls.section}</div>
                <div className="td">{cls.title}</div>
                <div className="td">{cls.instructor}</div>
                <div className="td">{cls.schedule}</div>
                <div className="td">{cls.semester} {cls.year}</div>
                <div className="td">
                  {editingCapacity[cls.classId] !== undefined ? (
                    <input 
                      type="number"
                      className="capacity-input"
                      value={editingCapacity[cls.classId]}
                      onChange={(e) => handleCapacityChange(cls.classId, e.target.value)}
                      min="0"
                    />
                  ) : (
                    <span>{cls.capacity || 'Not set'}</span>
                  )}
                </div>
                <div className="td">
                  {editingCapacity[cls.classId] !== undefined ? (
                    <>
                      <button 
                        className="save-btn"
                        onClick={() => handleSaveCapacity(cls.classId)}
                      >
                        Save
                      </button>
                      <button 
                        className="cancel-btn"
                        onClick={() => {
                          const newEditing = {...editingCapacity}
                          delete newEditing[cls.classId]
                          setEditingCapacity(newEditing)
                        }}
                      >
                        Cancel
                      </button>
                    </>
                  ) : (
                    <button 
                      className="edit-btn"
                      onClick={() => setEditingCapacity({...editingCapacity, [cls.classId]: cls.capacity || 0})}
                    >
                      Edit
                    </button>
                  )}
                </div>
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
              Page {currentPage} of {totalPages} ({filteredClasses.length} classes)
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
  )
}

export default PdfUpload

