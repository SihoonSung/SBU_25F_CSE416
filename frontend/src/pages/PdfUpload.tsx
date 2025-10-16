import { useState, useEffect } from 'react'
import './PdfUpload.css'

/**
 * PDF Upload Page Component
 * Allows registrars to upload PDF schedules and manage class capacities.
 * 
 * Note: File upload and class management functionality were implemented 
 * with assistance from Cursor AI and ChatGPT for proper file handling 
 * and data management capabilities.
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
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [semester, setSemester] = useState('Spring')
  const [year, setYear] = useState(2025)
  const [subjects, setSubjects] = useState('')
  const [uploading, setUploading] = useState(false)
  const [uploadMessage, setUploadMessage] = useState('')
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
      const response = await fetch('http://localhost:8080/api/registrar/classes')
      const data = await response.json()
      setClasses(data)
    } catch (error) {
      console.error('Error fetching classes:', error)
    }
  }

  /**
   * Handle file selection
   */
  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      setSelectedFile(e.target.files[0])
      setUploadMessage('')
    }
  }

  /**
   * Handle PDF upload and import
   */
  const handleUpload = async () => {
    if (!selectedFile) {
      setUploadMessage('Please select a PDF file')
      return
    }

    setUploading(true)
    setUploadMessage('')

    const formData = new FormData()
    formData.append('file', selectedFile)
    formData.append('semester', semester)
    formData.append('year', year.toString())
    if (subjects.trim()) {
      formData.append('subjects', subjects.trim())
    }

    try {
      const response = await fetch('http://localhost:8080/api/registrar/upload-schedule', {
        method: 'POST',
        body: formData
      })

      const data = await response.json()
      
      if (data.success) {
        setUploadMessage(`✅ ${data.message}`)
        setSelectedFile(null)
        // Refresh class list
        fetchClasses()
      } else {
        setUploadMessage(`❌ ${data.message}`)
      }
    } catch (error) {
      setUploadMessage('❌ Error uploading file: ' + error)
    } finally {
      setUploading(false)
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
      const response = await fetch('http://localhost:8080/api/rooms/load-and-calculate', {
        method: 'POST'
      })

      const data = await response.json()
      
      if (data.success) {
        setCapacityMessage('✅ ' + data.message)
        // Refresh class list to show updated capacities
        fetchClasses()
      } else {
        setCapacityMessage('❌ ' + data.message)
      }
    } catch (error) {
      setCapacityMessage('❌ Error loading capacities: ' + error)
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
      const response = await fetch(`http://localhost:8080/api/registrar/classes/${classId}/capacity`, {
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
      <h1>📄 PDF Upload & Class Management</h1>

      {/* PDF Upload Section */}
      <div className="upload-section">
        <h2>📤 Upload Class Schedule PDF</h2>
        <p className="section-description">
          Upload a PDF from SBU's Course Offerings by Term page to import class schedules.
        </p>
        
        <div className="upload-form">
          <div className="form-row">
            <div className="form-group">
              <label>Semester:</label>
              <select value={semester} onChange={(e) => setSemester(e.target.value)}>
                <option value="Spring">Spring</option>
                <option value="Fall">Fall</option>
                <option value="Summer">Summer</option>
                <option value="Winter">Winter</option>
              </select>
            </div>
            
            <div className="form-group">
              <label>Year:</label>
              <input 
                type="number" 
                value={year} 
                onChange={(e) => setYear(parseInt(e.target.value))}
                min="2020"
                max="2030"
              />
            </div>
          </div>

          <div className="form-group">
            <label>Subjects (optional):</label>
            <input 
              type="text" 
              placeholder="e.g., CSE,AMS,MAT (leave empty for all subjects)"
              value={subjects}
              onChange={(e) => setSubjects(e.target.value)}
            />
            <small>Comma-separated list of subject codes to import</small>
          </div>

          <div className="form-group">
            <label>PDF File:</label>
            <input 
              type="file" 
              accept=".pdf"
              onChange={handleFileChange}
            />
            {selectedFile && <span className="file-name">📎 {selectedFile.name}</span>}
          </div>

          <button 
            className="upload-btn"
            onClick={handleUpload}
            disabled={uploading || !selectedFile}
          >
            {uploading ? '⏳ Uploading...' : '📤 Upload & Import'}
          </button>

          {uploadMessage && (
            <div className={`upload-message ${uploadMessage.includes('✅') ? 'success' : 'error'}`}>
              {uploadMessage}
            </div>
          )}
        </div>
      </div>

      {/* Room Capacity Section */}
      <div className="capacity-section">
        <h2>🏢 Load Room Capacities</h2>
        <p className="section-description">
          Load room capacities from server and automatically calculate class capacities based on room locations.
        </p>
        
        <button 
          className="load-capacity-btn"
          onClick={handleLoadCapacity}
          disabled={loadingCapacity}
        >
          {loadingCapacity ? '⏳ Loading...' : '🏢 Load Capacity from Server'}
        </button>

        {capacityMessage && (
          <div className={`capacity-message ${capacityMessage.includes('✅') ? 'success' : 'error'}`}>
            {capacityMessage}
          </div>
        )}
      </div>

      {/* Class Management Section */}
      <div className="management-section">
        <h2>📊 Manage Class Capacities</h2>
        <p className="section-description">
          View and edit the capacity for each class.
        </p>

        {/* Search Bar */}
        <div className="class-search-bar">
          <div className="search-icon">🔍</div>
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
                        ✓ Save
                      </button>
                      <button 
                        className="cancel-btn"
                        onClick={() => {
                          const newEditing = {...editingCapacity}
                          delete newEditing[cls.classId]
                          setEditingCapacity(newEditing)
                        }}
                      >
                        ✕ Cancel
                      </button>
                    </>
                  ) : (
                    <button 
                      className="edit-btn"
                      onClick={() => setEditingCapacity({...editingCapacity, [cls.classId]: cls.capacity || 0})}
                    >
                      ✏️ Edit
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

