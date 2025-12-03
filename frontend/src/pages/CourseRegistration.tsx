import { useState, useEffect } from 'react'
import './CourseRegistration.css'
import { checkTimeConflict } from '../utils/timeConflict'
import { getCurrentUser } from '../utils/auth'
import { getBackendBase, EXTERNAL_URLS, DEFAULT_ITEMS_PER_PAGE } from '../constants'

import NotificationToast, { type ToastType } from '../components/NotificationToast'
import ConfirmationModal from '../components/ConfirmationModal'
import LoadingModal from '../components/LoadingModal'

// Allow references to Node's process object in browser build (Jest compatibility)
declare const process: any

/*
 * Course Registration Page Component
 * Allows students to search for classes and add them to their cart.
 * Includes expandable course details and real-time search.
 * 
 * TP: Used Cursor to implement frontend-backend integration and UI components.
 */

function CourseRegistration() {
  const backendBase = getBackendBase()
  const [searchTerm, setSearchTerm] = useState('')
  const [selectedCourse, setSelectedCourse] = useState<any>(null)
  const [searchResults, setSearchResults] = useState<any[]>([])
  const [currentPage, setCurrentPage] = useState(1)
  const [itemsPerPage] = useState(DEFAULT_ITEMS_PER_PAGE)
  const [availableTerms, setAvailableTerms] = useState<any[]>([])
  const [selectedSemester, setSelectedSemester] = useState('')
  const [selectedYear, setSelectedYear] = useState<number | string>('')
  const [cart, setCart] = useState<any[]>([])
  const [registeredClasses, setRegisteredClasses] = useState<any[]>([])
  const [waitlistClasses, setWaitlistClasses] = useState<any[]>([])
  const [courseCatalogMap, setCourseCatalogMap] = useState<Record<string, any>>({})
  const [userId, setUserId] = useState<number | null>(null)
  const [registrationEligibility, setRegistrationEligibility] = useState<any>(null)
  const [showRegistrationAlert, setShowRegistrationAlert] = useState(false)
  const [droppingRegisteredClassId, setDroppingRegisteredClassId] = useState<number | null>(null)
  const [cancelingWaitlistId, setCancelingWaitlistId] = useState<number | null>(null)
  const [isProcessing, setIsProcessing] = useState(false)
  const [processingMessage, setProcessingMessage] = useState('Loading...')

  // Notification State
  const [toast, setToast] = useState<{ message: string; type: ToastType } | null>(null)
  const [modalConfig, setModalConfig] = useState<{
    isOpen: boolean;
    title: string;
    message: string;
    onConfirm: () => void;
    onCancel?: () => void;
    type?: 'danger' | 'primary';
    confirmText?: string;
    cancelText?: string;
  }>({
    isOpen: false,
    title: '',
    message: '',
    onConfirm: () => { },
    type: 'primary',
    confirmText: 'Confirm',
    cancelText: 'Cancel'
  })

  const showToast = (message: string, type: ToastType = 'info') => {
    setToast({ message, type })
  }

  const showModal = (
    title: string,
    message: string,
    onConfirm: () => void,
    type: 'danger' | 'primary' = 'primary',
    confirmText: string = 'Confirm',
    cancelText: string = 'Cancel',
    onCancel?: () => void
  ) => {
    setModalConfig({
      isOpen: true,
      title,
      message,
      onConfirm,
      onCancel,
      type,
      confirmText,
      cancelText
    })
  }

  const closeModal = () => {
    setModalConfig(prev => ({ ...prev, isOpen: false }))
  }

  /**
   * Fetch available terms and classes on component mount
   */
  useEffect(() => {
    const initializeUser = async () => {
      try {
        const userData = await getCurrentUser()
        if (userData.user?.userId) {
          setUserId(userData.user.userId)
        }
      } catch (error) {
        console.error('Failed to get current user:', error)
      }
    }

    initializeUser()
    fetchAvailableTerms()
  }, [])

  /**
   * Check registration eligibility when term is selected
   */
  useEffect(() => {
    if (userId && selectedSemester && selectedYear) {
      checkRegistrationEligibility()
    }
  }, [userId, selectedSemester, selectedYear])

  /**
   * Load cart when userId is available
   * Initial load: fetch cart and update conflicts once
   * Conflicts will be updated automatically by backend when adding/removing items
   */
  useEffect(() => {
    if (userId) {
      // Load cart first
      loadCartFromDB().then(() => {
        // Update conflicts once after initial load (non-recursive)
        if (userId) {
          fetch(`${backendBase}/api/cart/update-conflicts?userId=${userId}`, {
            method: 'POST',
            credentials: 'include'
          })
            .then(res => res.json())
            .then(data => {
              if (data.success) {
                // Reload cart once to get updated conflict statuses
                loadCartFromDB()
              }
            })
            .catch(err => console.error('Error updating conflicts on initial load:', err))
        }
      })
    }
  }, [userId])

  /**
   * Load registered classes when userId and term are available
   */
  useEffect(() => {
    if (userId && selectedSemester && selectedYear) {
      loadRegisteredClasses()
    } else {
      setRegisteredClasses([])
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [userId, selectedSemester, selectedYear])

  /**
   * Reload cart when registered classes change to check for conflicts
   */
  useEffect(() => {
    if (userId && registeredClasses.length >= 0) {
      loadCartFromDB()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [registeredClasses])


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
  const buildCourseKey = (department: string, courseNum: number | string) =>
    `${department}-${String(courseNum)}`

  const createCourseCatalogMap = (courses: any[]) => {
    const map: Record<string, any> = {}
    if (!Array.isArray(courses)) {
      return map
    }
    courses.forEach((course: any) => {
      if (!course || !course.subject || !course.courseNumber) return
      const key = buildCourseKey(course.subject, course.courseNumber)
      map[key] = course
    })
    return map
  }

  const formatTermName = (semester: string, year: number | string) => {
    if (!semester || semester.trim() === '' || year === undefined || year === null || String(year).trim() === '') {
      return ''
    }
    const normalizedSemester =
      semester.charAt(0).toUpperCase() + semester.slice(1).toLowerCase()
    return `${normalizedSemester} ${year}`
  }

  const transformClassData = (cls: any, catalogMap: Record<string, any>) => {
    const courseKey = buildCourseKey(cls.department, cls.courseNum)
    const catalogCourse = catalogMap[courseKey] || {}

    return {
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
      prerequisites: catalogCourse.prerequisites ?? null,
      corequisites: catalogCourse.corequisites ?? null,
      antirequisites: catalogCourse.antirequisites ?? null,
      advisoryPrereqs: catalogCourse.advisoryPrerequisites ?? null,
      sbc: cls.sbc, // SBC codes
      minCredits: cls.minCredits,
      maxCredits: cls.maxCredits,
      credits: cls.minCredits === cls.maxCredits ? cls.minCredits : `${cls.minCredits}-${cls.maxCredits}`,
      semester: cls.semester,
      year: cls.year
    }
  }

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
   * Load registered classes for selected term
   */
  const loadRegisteredClasses = async () => {
    if (!userId || !selectedSemester || !selectedYear) {
      setRegisteredClasses([])
      return
    }

    try {
      // Get all registered classes for the user
      const response = await fetch(`${backendBase}/api/roster/student/${userId}`, {
        credentials: 'include'
      })

      if (response.ok) {
        const data = await response.json()

        if (!Array.isArray(data)) {
          console.error('Invalid response format from roster API:', data)
          setRegisteredClasses([])
          return
        }

        // Filter by selected semester and year
        const normalizedSelectedSemester = selectedSemester.charAt(0).toUpperCase() + selectedSemester.slice(1).toLowerCase()
        const selectedYearNum = typeof selectedYear === 'number' ? selectedYear : parseInt(String(selectedYear))

        const filtered = data.filter((item: any) => {
          const itemYear = typeof item.year === 'number' ? item.year : parseInt(String(item.year))
          const itemSemester = item.semester ?
            (String(item.semester).charAt(0).toUpperCase() + String(item.semester).slice(1).toLowerCase()) : null

          return itemYear === selectedYearNum && itemSemester === normalizedSelectedSemester
        })

        // Transform to match cart format
        const transformed = filtered.map((item: any) => ({
          id: item.classId,
          code: `${item.department}${item.courseNum}.${item.section}`,
          classId: item.classId,
          department: item.department,
          courseNum: item.courseNum,
          section: item.section,
          title: item.title || '',
          schedule: item.schedule || '',
          instructor: item.instructor || '',
          location: item.location || '',
          semester: item.semester,
          year: item.year,
          status: 'Enrolled'
        }))

        // Check for time conflicts between registered classes
        const classesWithConflicts = await Promise.all(transformed.map(async (cls: any, index: number) => {
          const conflictingClasses: string[] = []
          for (let i = 0; i < transformed.length; i++) {
            if (i !== index && cls.schedule && transformed[i].schedule) {
              try {
                const result = await checkTimeConflict(cls.schedule, transformed[i].schedule)
                if (result.hasConflict) {
                  conflictingClasses.push(`${transformed[i].code} (${transformed[i].title})`)
                }
              } catch (error) {
                // Fallback to simple string comparison
                if (cls.schedule === transformed[i].schedule) {
                  conflictingClasses.push(`${transformed[i].code} (${transformed[i].title})`)
                }
              }
            }
          }
          return {
            ...cls,
            hasTimeConflict: conflictingClasses.length > 0,
            conflictingClasses: conflictingClasses
          }
        }))

        setRegisteredClasses(classesWithConflicts)
      } else {
        console.error('Failed to load registered classes:', response.status)
        setRegisteredClasses([])
      }
    } catch (error) {
      console.error('Error loading registered classes:', error)
      setRegisteredClasses([])
    }
  }

  /**
   * Load cart items from database
   * Also checks for conflicts with registered classes
   */
  const loadCartFromDB = async () => {
    if (!userId) {
      return
    }

    try {
      const response = await fetch(`${backendBase}/api/cart/items?userId=${userId}`, {
        credentials: 'include'
      })
      const data = await response.json()

      if (data.success) {
        let cartItems = data.items

        // Check for conflicts with registered classes
        if (registeredClasses.length > 0) {
          cartItems = await Promise.all(cartItems.map(async (cartItem: any) => {
            const cartSchedule = cartItem.schedule || combineSchedules(cartItem)
            if (!cartSchedule) return cartItem

            let hasConflictWithRegistered = false
            const conflictingRegisteredClasses: string[] = []

            for (const registeredClass of registeredClasses) {
              const registeredSchedule = registeredClass.schedule
              if (!registeredSchedule) continue

              try {
                const result = await checkTimeConflict(cartSchedule, registeredSchedule)
                if (result.hasConflict) {
                  hasConflictWithRegistered = true
                  conflictingRegisteredClasses.push(`${registeredClass.code} (${registeredClass.title})`)
                }
              } catch (error) {
                // Fallback to simple string comparison
                if (cartSchedule === registeredSchedule) {
                  hasConflictWithRegistered = true
                  conflictingRegisteredClasses.push(`${registeredClass.code} (${registeredClass.title})`)
                }
              }
            }

            if (hasConflictWithRegistered) {
              return {
                ...cartItem,
                status: 'Conflict with enrolled',
                hasConflictWithRegistered: true,
                conflictingRegisteredClasses: conflictingRegisteredClasses
              }
            }

            return cartItem
          }))
        }

        setCart(cartItems)
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
   * Check registration eligibility for the selected term
   */
  const checkRegistrationEligibility = async () => {
    /* istanbul ignore next */
    if (!userId || !selectedSemester || !selectedYear) return

    try {
      const response = await fetch(
        `${backendBase}/api/admin/registration-schedules/check/${userId}?semester=${selectedSemester}&year=${selectedYear}`,
        {
          method: 'GET',
          credentials: 'include',
          headers: { 'Content-Type': 'application/json' }
        }
      )

      if (response.ok) {
        const data = await response.json()
        // Only show alert if registration is explicitly available
        // If schedule doesn't exist, silently allow registration (for testing)
        if (data.canRegister) {
          setRegistrationEligibility(data)
          setShowRegistrationAlert(true)
        }
      }
    } catch (error) {
      // Silently ignore errors - allow registration to proceed
    }
  }


  /**
   * Fetch available semester/year combinations from backend
   */
  const fetchAvailableTerms = async () => {
    try {
      const response = await fetch(`${backendBase}/api/classes/terms`, {
        credentials: 'include'
      })
      const data = await response.json()

      // Ensure data is always an array
      setAvailableTerms(Array.isArray(data) ? data : [])

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
      const response = await fetch(`${backendBase}/api/classes/by-term?semester=${semester}&year=${year}`, {
        credentials: 'include'
      })
      const data = await response.json()

      const termName = formatTermName(semester, year)
      let catalogMap: Record<string, any> = {}

      if (termName) {
        try {
          const catalogResponse = await fetch(
            `${backendBase}/api/scraping/courses/byTerm?term=${encodeURIComponent(termName)}`,
            { credentials: 'include' }
          )
          if (catalogResponse.ok) {
            const catalogData = await catalogResponse.json()
            catalogMap = createCourseCatalogMap(catalogData)
          } else {
            catalogMap = {}
          }
        } catch (catalogError) {
          console.error('Error fetching course catalog:', catalogError)
          catalogMap = {}
        }
      }

      setCourseCatalogMap(catalogMap)

      // Transform and sort data using catalog requisites
      const classArray = Array.isArray(data) ? data : []
      const transformedData = classArray.map((cls: any) => transformClassData(cls, catalogMap))
      setSearchResults(sortClasses(transformedData))

    } catch (error) {
      console.error('Error fetching classes:', error)
      setCourseCatalogMap({})
      setSearchResults([])
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
      const response = await fetch(`${backendBase}/api/classes/search?q=${encodeURIComponent(term)}`, {
        credentials: 'include'
      })
      const data = await response.json()

      // Transform, filter, and sort data
      const dataArray = Array.isArray(data) ? data : []
      const transformedData = dataArray
        .map((cls: any) => transformClassData(cls, courseCatalogMap))
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
    if (!userId) {
      showToast('Please wait for user authentication to complete.', 'warning')
      return
    }

    // Check if course is already in cart first - if yes, show message and return immediately
    if (cart.some(c => c.id === course.id)) {
      showToast('This class is already in your cart!', 'warning')
      return
    }

    const executeAddToCart = async () => {
      setIsProcessing(true)
      setProcessingMessage('Adding to cart...')
      // Add to cart regardless of conflicts
      try {
        const response = await fetch(`${backendBase}/api/cart/add`, {
          method: 'POST',
          credentials: 'include',
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
          await loadCartFromDB()
          showToast('Class added to cart successfully', 'success')
        } else {
          if (data.message.includes('already exists')) {
            showToast('This class is already in your cart!', 'warning')
          } else {
            showToast('Error adding class to cart: ' + data.message, 'error')
          }
        }
      } catch (error) {
        console.error('Error adding to cart:', error)
        showToast('Error adding class to cart. Please try again.', 'error')
      } finally {
        setIsProcessing(false)
      }
    }

    // Check for time conflicts with existing cart items BEFORE adding (only for different courses)
    const courseSchedule = combineSchedules(course)
    let hasConflict = false
    let conflictMessage = ''

    if (courseSchedule && cart.length > 0) {
      for (const cartItem of cart) {
        // Skip checking conflict with the same course (shouldn't happen here, but safety check)
        if (cartItem.id === course.id) {
          continue
        }

        const cartSchedule = combineSchedules(cartItem)

        if (cartSchedule) {
          try {
            const result = await checkTimeConflict(courseSchedule, cartSchedule)
            if (result.hasConflict) {
              hasConflict = true
              conflictMessage += `Time conflict with ${cartItem.code} (${cartItem.title})\n`
            }
          } catch (error) {
            console.error('Error checking time conflict:', error)
          }
        }
      }

      if (hasConflict) {
        showModal(
          'Time Conflict Detected',
          `${conflictMessage}\nWould you like to request a time conflict waiver?`,
          () => {
            // Find the conflicting class
            const conflictingClass = cart.find(item => conflictMessage.includes(item.code));
            if (conflictingClass) {
              handleRequestWaiver(course.id, conflictingClass.id);
            }
          },
          'primary',
          'Request Waiver',
          'Add to Cart Anyway',
          () => executeAddToCart()
        );
        return;
      }
    }

    await executeAddToCart();
  }

  /**
   * Request time conflict waiver
   */
  const handleRequestWaiver = async (enrolledClassId: number, conflictingClassId: number) => {
    if (!userId) {
      showToast('Please wait for user authentication to complete.', 'warning')
      return
    }

    const reason = prompt('Please provide a reason for the time conflict waiver request:')
    if (!reason || !reason.trim()) {
      return
    }

    try {
      const response = await fetch(`${backendBase}/api/waivers`, {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          studentId: userId,
          enrolledClassId: enrolledClassId,
          conflictingClassId: conflictingClassId,
          reason: reason.trim()
        })
      })

      const data = await response.json()

      if (data.success || response.ok) {
        showToast('Waiver request created successfully! You will be notified when it is approved.', 'success')
      } else {
        if (data.message && data.message.includes('already exists')) {
          showToast('A waiver request for these classes already exists.', 'warning')
        } else {
          showToast('Failed to create waiver request: ' + (data.message || 'Unknown error'), 'error')
        }
      }
    } catch (error) {
      console.error('Error creating waiver request:', error)
      showToast('Error creating waiver request. Please try again.', 'error')
    }
  }

  /**
   * Drop a registered class
   */
  const handleDropRegisteredClass = async (classId: number) => {
    if (!userId) {
      showToast('Please wait for user authentication to complete.', 'warning')
      return
    }

    const executeDrop = async () => {
      setDroppingRegisteredClassId(classId)
      setIsProcessing(true)
      setProcessingMessage('Dropping class...')

      try {
        const response = await fetch(`${backendBase}/api/users/${userId}/classes/${classId}/drop`, {
          method: 'POST',
          credentials: 'include',
          headers: {
            'Content-Type': 'application/json'
          }
        })

        const data = await response.json()

        if (data.success) {
          // Reload registered classes after drop
          await loadRegisteredClasses()
          // Also reload cart in case it affects conflicts
          await loadCartFromDB()
          showToast(`Class dropped successfully: ${data.department || ''} ${data.courseNum || ''}`, 'success')
        } else {
          showToast(`Failed to drop class: ${data.message}`, 'error')
        }
      } catch (error) {
        console.error('Error dropping class:', error)
        showToast('Error dropping class. Please try again.', 'error')
      } finally {
        setDroppingRegisteredClassId(null)
        setIsProcessing(false)
      }
    }

    showModal(
      'Drop Class',
      'Are you sure you want to drop this class? This action cannot be undone.',
      executeDrop,
      'danger',
      'Drop Class'
    )
  }

  /**
   * Handle canceling waitlist for a class
   */
  const handleCancelWaitlist = async (classId: number) => {
    if (!userId) {
      showToast('Please wait for user authentication to complete.', 'warning')
      return
    }

    const executeCancel = async () => {
      setCancelingWaitlistId(classId)
      setIsProcessing(true)
      setProcessingMessage('Canceling waitlist...')
      try {
        const response = await fetch(`${backendBase}/api/users/${userId}/classes/${classId}/waitlist/cancel`, {
          method: 'POST',
          credentials: 'include',
          headers: {
            'Content-Type': 'application/json'
          }
        })

        const data = await response.json()

        if (data.success || response.ok) {
          // Remove from waitlist state
          setWaitlistClasses(prev => prev.filter(w => w.classId !== classId))
          showToast('Waitlist entry removed successfully', 'success')
        } else {
          showToast('Failed to cancel waitlist: ' + (data.message || 'Unknown error'), 'error')
        }
      } catch (error) {
        console.error('Error canceling waitlist:', error)
        showToast('Error canceling waitlist. Please try again.', 'error')
      } finally {
        setCancelingWaitlistId(null)
        setIsProcessing(false)
      }
    }

    showModal(
      'Cancel Waitlist',
      'Are you sure you want to cancel your waitlist entry for this class?',
      executeCancel,
      'danger',
      'Cancel Waitlist'
    )
  }

  /**
   * Remove a course from the cart
   */
  const handleDropCourse = async (classId: number | string) => {
    if (!userId) {
      showToast('Please wait for user authentication to complete.', 'warning')
      return
    }

    try {
      setIsProcessing(true)
      setProcessingMessage('Removing from cart...')
      const response = await fetch(`${backendBase}/api/cart/remove?userId=${userId}&classId=${classId}`, {
        credentials: 'include',
        method: 'DELETE'
      })

      const data = await response.json()

      if (data.success) {
        // Reload cart after removal (conflicts will be updated automatically by backend)
        await loadCartFromDB()
        // Also reload registered classes in case dropping from cart affects registered status
        if (selectedSemester && selectedYear) {
          await loadRegisteredClasses()
        }
        showToast('Class removed from cart', 'success')
      } else {
        showToast('Error removing class from cart: ' + data.message, 'error')
      }
    } catch (error) {
      console.error('Error removing from cart:', error)
      showToast('Error removing class from cart. Please try again.', 'error')
    } finally {
      setIsProcessing(false)
    }
  }

  /**
   * Handle submitting registration for all cart items
   * Blocks submission if cart has conflicts with enrolled classes
   */
  /**
   * Handle submitting registration for all cart items
   * Blocks submission if cart has conflicts with enrolled classes
   */
  const handleSubmitRegistration = async () => {
    if (!userId) {
      showToast('Please wait for user authentication to complete.', 'warning')
      return
    }

    if (cart.length === 0) {
      showToast('Your cart is empty. Add classes to register.', 'warning')
      return
    }

    // Check for conflicts with enrolled classes before submitting
    const cartItemsWithRegisteredConflicts = cart.filter((item: any) =>
      item.hasConflictWithRegistered || item.status === 'Conflict with enrolled'
    )

    if (cartItemsWithRegisteredConflicts.length > 0) {
      const conflictMessages = cartItemsWithRegisteredConflicts.map((item: any) => {
        const conflicts = item.conflictingRegisteredClasses || []
        return `${item.code} conflicts with: ${conflicts.join(', ')}`
      }).join('\n')

      showModal(
        'Registration Blocked',
        `Cannot register classes due to time conflicts with enrolled classes:\n\n${conflictMessages}\n\nPlease remove conflicting classes from cart or drop the enrolled classes first.`,
        () => { }, // No action on confirm, just close
        'danger',
        'OK',
        '', // No cancel text
        undefined // No cancel action
      )
      return
    }

    // Check for active holds
    try {
      const holdsResponse = await fetch(`${backendBase}/api/holds/check/${userId}`, {
        credentials: 'include'
      })

      if (holdsResponse.ok) {
        const holdsData = await holdsResponse.json()
        if (holdsData.hasActiveHolds && holdsData.activeHolds && holdsData.activeHolds.length > 0) {
          const holdMessages = holdsData.activeHolds.map((h: any) =>
            `- ${h.holdType}: ${h.reason || 'No reason provided'}`
          ).join('\n')

          showModal(
            'Registration Blocked',
            `Registration is blocked due to active holds:\n${holdMessages}\n\nPlease resolve these holds before registering.`,
            () => { },
            'danger',
            'OK'
          )
          return
        }
      }
    } catch (error) {
      console.error('Error checking holds:', error)
      // Continue with registration even if hold check fails
    }

    // Separate items with and without conflicts
    const itemsWithoutConflicts = cart.filter(item => item.status !== 'Conflict')
    const itemsWithConflicts = cart.filter(item => item.status === 'Conflict')

    if (itemsWithoutConflicts.length === 0) {
      showToast('Cannot register classes with time conflicts. Please resolve conflicts first.', 'warning')
      return
    }

    const executeRegistration = async () => {
      setIsProcessing(true)
      setProcessingMessage('Processing registration...')
      // Temporarily remove conflict items from cart by removing them from backend
      const conflictClassIds: (number | string)[] = []
      for (const conflictItem of itemsWithConflicts) {
        try {
          await fetch(`${backendBase}/api/cart/remove?userId=${userId}&classId=${conflictItem.classId}`, {
            credentials: 'include',
            method: 'DELETE'
          })
          conflictClassIds.push(conflictItem.classId)
        } catch (error) {
          console.error('Error removing conflict item from cart:', error)
        }
      }

      try {
        const response = await fetch(`${backendBase}/api/cart/submit-registration?userId=${userId}`, {
          credentials: 'include',
          method: 'POST'
        })

        const data = await response.json()

        if (data.success) {
          // Reload cart from database to show updated statuses
          await loadCartFromDB()

          // Reload registered classes to show newly registered classes
          if (selectedSemester && selectedYear) {
            await loadRegisteredClasses()
          }

          // Handle waitlist classes from response
          if (data.waitlistedClasses && Array.isArray(data.waitlistedClasses) && data.waitlistedClasses.length > 0) {
            // Transform waitlist data to match our format
            const waitlistData = data.waitlistedClasses.map((wl: any) => ({
              id: wl.classId,
              code: `${wl.department}${wl.courseNum}`,
              classId: wl.classId,
              department: wl.department,
              courseNum: wl.courseNum,
              title: wl.title || '',
              position: wl.position || 0,
              semester: selectedSemester,
              year: selectedYear
            }))

            // Filter by current selected semester/year and merge with existing waitlist (avoid duplicates)
            setWaitlistClasses(prev => {
              // Remove old waitlist entries for this semester/year
              const filtered = prev.filter(w => !(w.semester === selectedSemester && w.year === selectedYear))
              const existingIds = new Set(filtered.map(w => w.classId))
              const newItems = waitlistData.filter((w: any) => !existingIds.has(w.classId))
              return [...filtered, ...newItems]
            })
          }

          // Build success message
          let successMessage = data.message || `Registration successful! Registered for ${data.registeredCount} class(es).`

          // Re-add conflict items to cart
          if (itemsWithConflicts.length > 0) {
            for (const conflictItem of itemsWithConflicts) {
              try {
                await fetch(`${backendBase}/api/cart/add?userId=${userId}&classId=${conflictItem.classId}`, {
                  credentials: 'include',
                  method: 'POST'
                })
              } catch (error) {
                console.error('Error re-adding conflict item to cart:', error)
              }
            }

            // Reload cart again to show re-added conflict items
            await loadCartFromDB()

            successMessage += `\n\n${itemsWithConflicts.length} class(es) with conflicts remain in your cart:`
            successMessage += '\n' + itemsWithConflicts.map(item => `- ${item.code}: ${item.title}`).join('\n')
          }

          showToast(successMessage, 'success')
        } else {
          // Re-add conflict items to cart even if registration failed
          if (itemsWithConflicts.length > 0) {
            for (const conflictItem of itemsWithConflicts) {
              try {
                await fetch(`${backendBase}/api/cart/add?userId=${userId}&classId=${conflictItem.classId}`, {
                  credentials: 'include',
                  method: 'POST'
                })
              } catch (error) {
                console.error('Error re-adding conflict item to cart:', error)
              }
            }
            await loadCartFromDB()
          }

          if (data.conflicts && data.conflicts.length > 0) {
            showModal('Registration Failed', `Registration failed due to time conflicts:\n${data.conflicts.join('\n')}\n\nPlease resolve conflicts before registering.`, () => { }, 'danger', 'OK')
          } else if (data.duplicateClasses && data.duplicateClasses.length > 0) {
            showModal('Registration Failed', `Registration failed - these classes are already registered:\n${data.duplicateClasses.join('\n')}`, () => { }, 'danger', 'OK')
          } else if (data.prerequisiteErrors && data.prerequisiteErrors.length > 0) {
            showModal('Registration Failed', `Registration failed - prerequisites not met:\n${data.prerequisiteErrors.join('\n')}`, () => { }, 'danger', 'OK')
          } else if (data.antirequisiteErrors && data.antirequisiteErrors.length > 0) {
            showModal('Registration Failed', `Registration failed - antirequisites conflict:\n${data.antirequisiteErrors.join('\n')}`, () => { }, 'danger', 'OK')
          } else if (data.corequisiteErrors && data.corequisiteErrors.length > 0) {
            showModal('Registration Failed', `Registration failed - corequisites not met:\n${data.corequisiteErrors.join('\n')}`, () => { }, 'danger', 'OK')
          } else if (data.error) {
            showToast('Registration failed: ' + data.error, 'error')
          } else {
            showToast('Registration failed: ' + (data.message || 'Unknown error'), 'error')
          }
        }
      } catch (error) {
        // Re-add conflict items to cart on error
        if (itemsWithConflicts.length > 0) {
          for (const conflictItem of itemsWithConflicts) {
            try {
              await fetch(`${backendBase}/api/cart/add?userId=${userId}&classId=${conflictItem.classId}`, {
                credentials: 'include',
                method: 'POST'
              })
            } catch (addError) {
              console.error('Error re-adding conflict item to cart:', addError)
            }
          }
          await loadCartFromDB()
        }

        console.error('Error submitting registration:', error)
        showToast('Error submitting registration. Please try again.', 'error')
      } finally {
        setIsProcessing(false)
      }
    }

    // Confirm registration
    let confirmMessage = `Register for ${itemsWithoutConflicts.length} class(es) without conflicts?`
    if (itemsWithConflicts.length > 0) {
      confirmMessage += `\n\n${itemsWithConflicts.length} class(es) with conflicts will remain in your cart.`
    }

    showModal(
      'Confirm Registration',
      confirmMessage,
      executeRegistration,
      'primary',
      'Register'
    )
  }


  // Show loading state if userId is not available yet
  if (!userId) {
    return (
      <div className="course-registration-page">
        <div style={{ textAlign: 'center', padding: '50px' }}>
          <h2>Loading...</h2>
          <p>Please wait while we authenticate your session.</p>
        </div>
      </div>
    )
  }

  // --- Helpers: schedule overlap detection ---
  const normalizeDays = (s: string) => {
    const map: Record<string, string> = { Mon: 'M', Tue: 'T', Wed: 'W', Thu: 'R', Fri: 'F' }
    let days = ''
    Object.keys(map).forEach(k => { if (s.includes(k)) days += map[k] })
    if (!days) {
      // fallback for compact forms like MW, TR
      if (/M(?!on)/.test(s)) days += 'M'
      if (/T(?!ue)/.test(s)) days += 'T'
      if (/W/.test(s)) days += 'W'
      if (/R|Thu/.test(s)) days += 'R'
      if (/F/.test(s)) days += 'F'
    }
    return days || s
  }

  const extractStartEnd = (s: string) => {
    // Supports formats: "9:30 AM", "09:00 AM - 10:20 AM", "09:00-10:20PM"
    const range = s.match(/(\d{1,2}:\d{2}\s*[AP]M)\s*[-–]\s*(\d{1,2}:\d{2}\s*[AP]M)/i)
    const single = s.match(/(\d{1,2}:\d{2})\s*([AP]M)/i)
    if (range) return { start: range[1].replace(/\s+/g, ''), end: range[2].replace(/\s+/g, '') }
    if (single) return { start: (single[1] + single[2]).replace(/\s+/g, ''), end: null as any }
    return { start: s, end: null as any }
  }

  const isOverlap = (a: any, b: any) => {
    const da = normalizeDays(a.schedule || '')
    const db = normalizeDays(b.schedule || '')
    if (![...da].some(d => db.includes(d))) return false
    const ta = extractStartEnd(a.schedule || '')
    const tb = extractStartEnd(b.schedule || '')
    // simplest: if start time text matches, treat as overlap; or full range equality
    if (ta.start && tb.start && ta.start.toUpperCase() === tb.start.toUpperCase()) return true
    if (ta.end && tb.end && ta.start.toUpperCase() === tb.start.toUpperCase() && ta.end.toUpperCase() === tb.end.toUpperCase()) return true
    // fallback: identical schedule string
    return (a.schedule || '').trim().toUpperCase() === (b.schedule || '').trim().toUpperCase()
  }

  return (
    <div className="course-registration-page">
      {/* Registration Eligibility Alert */}
      {showRegistrationAlert && registrationEligibility && (
        <div className={`registration-alert ${registrationEligibility.canRegister ? 'alert-success' : 'alert-warning'}`}>
          <div className="alert-icon"></div>
          <div className="alert-content">
            {registrationEligibility.canRegister ? (
              <>
                <strong>Registration Open:</strong> You can register for {selectedSemester} {selectedYear}.
                {registrationEligibility.schedule && registrationEligibility.schedule.endDate && (
                  <span> Registration ends {new Date(registrationEligibility.schedule.endDate).toLocaleDateString()}.</span>
                )}
              </>
            ) : (
              <>
                <strong>Registration Not Available:</strong> {registrationEligibility.reason || 'Registration is not currently open for your class standing.'}
              </>
            )}
          </div>
          <button className="alert-close" onClick={() => setShowRegistrationAlert(false)}>×</button>
        </div>
      )}

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
            {Array.isArray(availableTerms) && availableTerms.map((term: any) => (
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
            {Array.isArray(availableTerms) && availableTerms.map((term: any) => (
              <option key={`${term.semester}-${term.year}`} value={term.year}>
                {term.year}
              </option>
            ))}
          </select>
        </div>

        <div className="search-container">
          {/* Search Bar */}
          <div className="search-bar">
            <div className="search-icon"></div>
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
                <div className="no-data-icon"></div>
                <h3>No Class Data Available</h3>
                <p>There are no classes in the database for the selected term.</p>
                <p className="hint-text">Go to PDF Upload & Class Management page to import class schedules</p>
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
                          disabled={cart.some(c => c.id === course.id)}
                        >
                          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                            <circle cx="9" cy="21" r="1"></circle>
                            <circle cx="20" cy="21" r="1"></circle>
                            <path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6"></path>
                          </svg>
                          <span className="btn-text">{cart.some(c => c.id === course.id) ? 'Added' : 'Add to Cart'}</span>
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
                            <div className="section-title">Lecture Section (Required)</div>
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
                            {course.sectionType === 'LAB' && 'Lab Section'}
                            {course.sectionType === 'REC' && 'Recitation Section'}
                            {course.sectionType === 'LEC' && 'Lecture Section'}
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
                            href={`${EXTERNAL_URLS.CLASSIE_EVALS}/?SearchKeyword=${course.department}${course.courseNum}&SearchTerm=ALL`}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="detail-link"
                            onClick={(e) => e.stopPropagation()}
                          >
                            View Course Evaluations
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
                        disabled={cart.some(c => c.id === course.id)}
                      >
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                          <circle cx="9" cy="21" r="1"></circle>
                          <circle cx="20" cy="21" r="1"></circle>
                          <path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6"></path>
                        </svg>
                        <span className="btn-text">{cart.some(c => c.id === course.id) ? 'Added' : 'Add to Cart'}</span>
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
          {/* Registered Classes inside Cart - only show if there are registered classes */}
          {registeredClasses.length > 0 && (
            <div className="registered-inside-cart">
              <h3>Registered Classes ({selectedSemester} {selectedYear})</h3>
              <div className="registered-courses">
                {registeredClasses.map((course) => (
                  <div key={course.id} className={`registered-course-card ${course.hasTimeConflict ? 'has-conflict' : ''}`}>
                    <div className="course-info">
                      <div className="course-code">{course.code}</div>
                      <div className="course-title">{course.title}</div>
                      <div className="course-schedule">{course.schedule}</div>
                      <div className="course-instructor">{course.instructor}</div>
                      <div className="course-location">{course.location}</div>
                      {course.hasTimeConflict && course.conflictingClasses && course.conflictingClasses.length > 0 && (
                        <div className="time-conflict-warning" style={{
                          marginTop: '0.5rem',
                          padding: '0.5rem',
                          background: '#fef2f2',
                          border: '1px solid #fecaca',
                          borderRadius: 'var(--radius-sm)',
                          fontSize: '0.875rem',
                          color: '#991b1b'
                        }}>
                          <strong>⚠️ Time Conflict:</strong> This class conflicts with {course.conflictingClasses.join(', ')}.
                          {course.hasWaiver ? ' Override approved.' : ' Please resolve this conflict or request an override.'}
                        </div>
                      )}
                    </div>
                    <span className="status-tag enrolled">Enrolled</span>
                    <button
                      className="drop-btn"
                      onClick={() => handleDropRegisteredClass(course.classId)}
                      disabled={droppingRegisteredClassId === course.classId}
                    >
                      {droppingRegisteredClassId === course.classId ? 'Dropping...' : 'Drop'}
                    </button>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Waitlist Classes - only show if there are waitlist classes for selected semester/year */}
          {waitlistClasses.filter(w => w.semester === selectedSemester && w.year === selectedYear).length > 0 && (
            <div className="registered-inside-cart">
              <h3>Waitlist ({selectedSemester} {selectedYear})</h3>
              <div className="registered-courses">
                {waitlistClasses
                  .filter(w => w.semester === selectedSemester && w.year === selectedYear)
                  .map((course) => (
                    <div key={course.id} className="registered-course-card">
                      <div className="course-info">
                        <div className="course-code">{course.code}</div>
                        <div className="course-title">{course.title}</div>
                        {course.position && course.position > 0 && (
                          <div className="course-schedule">Position in queue: #{course.position}</div>
                        )}
                      </div>
                      <span className="status-tag waitlisted">Waitlist</span>
                      <button
                        className="drop-btn"
                        onClick={() => handleCancelWaitlist(course.classId)}
                        disabled={cancelingWaitlistId === course.classId}
                      >
                        {cancelingWaitlistId === course.classId ? 'Canceling...' : 'Cancel'}
                      </button>
                    </div>
                  ))}
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
              cart.map((course) => {
                // Find courses that conflict with this course (in cart)
                const conflictingCourses = cart.filter((otherCourse) =>
                  otherCourse.id !== course.id &&
                  course.status === 'Conflict' &&
                  otherCourse.status === 'Conflict' &&
                  isOverlap(course, otherCourse)
                )

                return (
                  <div key={course.id} className={`cart-course-card ${course.hasConflictWithRegistered ? 'has-conflict-with-registered' : ''}`}>
                    <div className="course-info">
                      <div className="course-code">{course.code}</div>
                      <div className="course-title">{course.title}</div>
                      <div className="course-schedule">{combineSchedules(course)}</div>
                      <div className="course-instructor">{course.instructor}</div>
                      {conflictingCourses.length > 0 && (
                        <div className="conflict-info">
                          <span className="conflict-label">Time conflict with:</span>
                          <span className="conflict-courses">
                            {conflictingCourses.map(c => c.code).join(', ')}
                          </span>
                        </div>
                      )}
                      {course.hasConflictWithRegistered && course.conflictingRegisteredClasses && course.conflictingRegisteredClasses.length > 0 && (
                        <div className="conflict-info" style={{
                          marginTop: '0.5rem',
                          padding: '0.5rem',
                          background: '#fef2f2',
                          border: '1px solid #fecaca',
                          borderRadius: 'var(--radius-sm)',
                          fontSize: '0.875rem',
                          color: '#991b1b'
                        }}>
                          <strong>⚠️ Cannot Register:</strong> This class conflicts with enrolled classes: {course.conflictingRegisteredClasses.join(', ')}. Please drop the enrolled classes first or remove this from cart.
                        </div>
                      )}
                    </div>
                    <span className={`status-tag ${(course.status === 'Conflict with enrolled' || course.hasConflictWithRegistered) ? 'conflict-with-enrolled' : course.status.toLowerCase()}`}>
                      {course.status === 'Conflict with enrolled' ? 'Conflict with Enrolled' : course.status}
                    </span>
                    <button
                      className="drop-btn"
                      onClick={() => handleDropCourse(course.classId)}
                    >
                      Drop
                    </button>
                  </div>
                )
              })
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

      {/* Toast Notification */}
      {toast && (
        <NotificationToast
          message={toast.message}
          type={toast.type}
          onClose={() => setToast(null)}
        />
      )}

      {/* Confirmation Modal */}
      <ConfirmationModal
        isOpen={modalConfig.isOpen}
        title={modalConfig.title}
        message={modalConfig.message}
        onConfirm={() => {
          modalConfig.onConfirm()
          closeModal()
        }}
        onCancel={closeModal}
        type={modalConfig.type}
        confirmText={modalConfig.confirmText}
      />
      <ConfirmationModal
        isOpen={modalConfig.isOpen}
        title={modalConfig.title}
        message={modalConfig.message}
        onConfirm={() => {
          modalConfig.onConfirm()
          closeModal()
        }}
        onCancel={closeModal}
        type={modalConfig.type}
        confirmText={modalConfig.confirmText}
      />

      <LoadingModal isOpen={isProcessing} message={processingMessage} />
    </div>
  )
}

export default CourseRegistration
