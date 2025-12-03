/*
 * TP: Used Cursor to implement dashboard page with Google OAuth2 integration and user dashboard
 */

import { useEffect, useState } from 'react'
import { useSearchParams, useNavigate } from 'react-router-dom'
import './Dashboard.css'
import { getBackendBase, getDefaultSemester, getDefaultYear, getCurrentSemester, getCurrentYear } from '../constants'
import LoadingModal from '../components/LoadingModal'

type Me = {
  userId: number
  email: string
  fullName: string
  firstName?: string
  lastName?: string
  role?: string
  profilePictureUrl?: string
  isActive?: boolean
  studentId?: string | null
} | null

interface RegistrationEligibility {
  canRegister: boolean
  message?: string
}

interface Hold {
  id: number
  type: string
  description: string
  holdType?: string
  reason?: string
}

interface UserStats {
  gpa: number
  credits: number
  totalUsers?: number
  activeUsers?: number
  students?: number
  instructors?: number
  advisors?: number
  registrars?: number
}

interface DbStats {
  users: number
  classes: number
  status?: string
  totalTerms?: number
  totalCourses?: number
}

interface ScrapingStats {
  lastScraped: string
  totalCourses: number
  scrapedTerms?: number
  totalTerms?: number
}

interface TeachingClass {
  id: number
  code: string
  title: string
  classId?: number
  department?: string
  courseNum?: number
  section?: string
  semester?: string
  year?: number
  maxCapacity?: number
}

interface ClassStat {
  id: number
  code: string
  enrollment: number
  activeEnrollments?: number
  maxCapacity?: number
  isFull?: boolean
}



interface StudentScheduleItem {
  classId: number
  subject: string
  courseNum: number
  section: string
  title: string
  schedule: string
  location: string
  instructor: string
  credits: number
  department?: string
  code?: string
  lecSchedule?: string
}

interface CoursePlanItem {
  term: string
  year: number
  courses: Array<{
    subject: string
    courseNum: number
    credits: number
  }>
}

interface Declaration {
  id: number
  status: string
  approved?: boolean | null
}

interface RosterItem {
  year: number | string
  semester: string
  classId: number
  department?: string
  subject?: string
  courseNum: number
  section?: string
  title?: string
  schedule?: string
  location?: string
  instructor?: string
  credits?: number
  minCredits?: number
}

interface Waiver {
  id: number
  studentName?: string
  class?: string
  reason?: string
  date?: string
  status: string
  createdAt?: string
  waiverId?: number
  student?: { studentId: string }
  studentId?: string
  enrolledClass?: { department: string; courseNum: string }
  conflictingClass?: { department: string; courseNum: string }
  advisorApproved?: boolean | null
  instructorApproved?: boolean | null
  registrarApproved?: boolean | null
}

interface AcademicEvent {
  id: number
  title: string
  date: string
  type: string
  description?: string
  eventName?: string
}

/**
 * Dashboard component with Google OAuth2 integration.
 * Handles OAuth callback processing and displays user dashboard.
 */
function Dashboard() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const [isLoading, setIsLoading] = useState(false)
  const [me, setMe] = useState<Me>(null)
  const [registrationEligibility, setRegistrationEligibility] = useState<RegistrationEligibility | null>(null)
  const [currentSemester, setCurrentSemester] = useState<string>(getDefaultSemester())
  const [currentYear, setCurrentYear] = useState<number>(getDefaultYear())
  const [timeConflicts, setTimeConflicts] = useState<string[]>([])
  const [activeHolds, setActiveHolds] = useState<Hold[]>([])
  const [pendingOverrides, setPendingOverrides] = useState<number>(0)
  const [userStats, setUserStats] = useState<UserStats | null>(null)
  const [dbStats, setDbStats] = useState<DbStats | null>(null)
  const [scrapingStats, setScrapingStats] = useState<ScrapingStats | null>(null)
  const [teachingClasses, setTeachingClasses] = useState<TeachingClass[]>([])
  const [classStats, setClassStats] = useState<ClassStat[]>([])

  const [recentOverrides, setRecentOverrides] = useState<Waiver[]>([])
  const [pendingDeclarations, setPendingDeclarations] = useState<number>(0)
  const [advisorPendingWaivers, setAdvisorPendingWaivers] = useState<number>(0)
  const [studentPendingWaivers, setStudentPendingWaivers] = useState<number>(0)
  const [studentPendingDeclarations, setStudentPendingDeclarations] = useState<number>(0)
  const [calculatedGpa, setCalculatedGpa] = useState<number | null>(null)
  const [cumulativeCredits, setCumulativeCredits] = useState<number>(0)
  const [termGpa, setTermGpa] = useState<number | null>(null)
  const [studentSchedule, setStudentSchedule] = useState<StudentScheduleItem[]>([])
  const [studentCoursePlan, setStudentCoursePlan] = useState<CoursePlanItem[]>([])
  const [academicEvents, setAcademicEvents] = useState<AcademicEvent[]>([])
  const [overallProgress, setOverallProgress] = useState<number>(0)
  const [recentImports, setRecentImports] = useState<{
    classes: Array<{ term: string; courseCount: number }>
    degreeReqs: Array<{ subject: string; version: string }>
  }>({ classes: [], degreeReqs: [] })

  const backendBase = getBackendBase()

  // 1) Handle OAuth success callback + 2) Load current user from session
  useEffect(() => {
    const run = async () => {
      const success = searchParams.get('success')
      try {
        setIsLoading(true)

        // If returned from OAuth success, request backend to process success (user creation/update)
        if (success === 'true') {
          const ok = await fetch(`${backendBase}/api/auth/success`, {
            method: 'GET',
            credentials: 'include', // Include server session cookie
            headers: { 'Content-Type': 'application/json' },
            mode: 'cors',
          })
          if (!ok.ok) {
            console.error('auth/success call failed')
          }
          // Remove success parameter from URL
          navigate('/home', { replace: true })
        }

        // Query current logged-in user with session cookie
        const meRes = await fetch(`${backendBase}/api/auth/me`, {
          method: 'GET',
          credentials: 'include', // Important
          headers: {
            'Content-Type': 'application/json',
            'Cache-Control': 'no-cache',
            'Pragma': 'no-cache'
          },
          mode: 'cors',
          cache: 'no-cache'
        })

        if (meRes.ok) {
          const data = await meRes.json()
          const user = data.user ?? null
          setMe(user)

          // Normalize role (handle case-insensitive and array formats)
          const userRole = user?.role ? String(user.role).toUpperCase() : null

          // Set current term based on current date (not API's latest term)
          const currentDateSemester = getCurrentSemester()
          const currentDateYear = getCurrentYear()
          setCurrentSemester(currentDateSemester)
          setCurrentYear(currentDateYear)

          // Check registration eligibility if user is a student
          if (userRole === 'STUDENT' && user?.userId) {
            await checkRegistrationEligibility(user.userId)
            await checkTimeConflicts(user.userId)
            await calculateGpa(user.userId)
            await checkActiveHolds(user.userId)
            await checkStudentPendingItems(user.userId)
            await fetchStudentSchedule(user.userId)
            await fetchStudentCoursePlan(user.userId)
            await fetchAcademicEvents()
          }

          // Fetch professor data if user is an instructor
          if (userRole === 'INSTRUCTOR' && user?.firstName && user?.lastName) {
            await fetchProfessorData(user)
          }

          // Fetch advisor data if user is an advisor
          if (userRole === 'ADVISOR' && user?.userId) {
            await fetchAdvisorData(user.userId)
          }

          // Fetch admin stats only for REGISTRAR/ADMIN roles
          if ((userRole === 'REGISTRAR' || userRole === 'ADMIN') && user) {
            await fetchAdminStats()
          }

          // Check pending overrides if user is admin/registrar
          if ((userRole === 'REGISTRAR' || userRole === 'ADMIN') && user?.userId) {
            await checkPendingOverrides()
            await fetchRecentImports()
          }
        } else {
          setMe(null)
        }
      } catch (e) {
        console.error(e)
        setMe(null)
      } finally {
        setIsLoading(false)
      }
    }

    run()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [backendBase]) // success parameter is removed after processing and replaced with /home, so excluded from dependencies

  /**
   * Check for active registration holds
   */
  const checkActiveHolds = async (userId: number) => {
    try {
      const response = await fetch(`${backendBase}/api/holds/check/${userId}`, {
        credentials: 'include'
      })

      if (response.ok) {
        const data = await response.json()
        if (data.hasActiveHolds && data.activeHolds && Array.isArray(data.activeHolds)) {
          setActiveHolds(data.activeHolds)
        } else {
          setActiveHolds([])
        }
      } else {
        setActiveHolds([])
      }
    } catch {
      console.error('Error checking holds:', error)
      setActiveHolds([])
    }
  }

  /**
   * Check for student's pending items (waivers and declarations)
   */
  const checkStudentPendingItems = async (userId: number) => {
    try {
      // Check pending waivers for student
      const waiversResponse = await fetch(`${backendBase}/api/waivers/student/${userId}`, {
        credentials: 'include'
      })

      if (waiversResponse.ok) {
        const waivers = await waiversResponse.json()
        if (Array.isArray(waivers)) {
          // Filter for pending waivers
          // Pending if: status is PENDING, or any approval field is null (not yet approved/rejected)
          const pending = waivers.filter((w: Waiver) => {
            const status = (w.status || '').toUpperCase()
            const isPendingStatus = status === 'PENDING' || status === 'SUBMITTED'
            // Check if waiting for any approval (null means pending, true/false means decided)
            const waitingForAdvisor = w.advisorApproved === null || w.advisorApproved === undefined
            const waitingForInstructor = w.instructorApproved === null || w.instructorApproved === undefined
            const waitingForRegistrar = w.registrarApproved === null || w.registrarApproved === undefined

            const isPending = isPendingStatus || waitingForAdvisor || waitingForInstructor || waitingForRegistrar
            return isPending
          })
          setStudentPendingWaivers(pending.length)
        } else {
          setStudentPendingWaivers(0)
        }
      } else {
        setStudentPendingWaivers(0)
      }

      // Check pending declarations for student - use my-declarations endpoint
      const declarationsResponse = await fetch(`${backendBase}/api/major-minor-declarations/my-declarations`, {
        credentials: 'include'
      })

      if (declarationsResponse.ok) {
        const data = await declarationsResponse.json()
        if (data.success && Array.isArray(data.declarations)) {
          // Filter for pending declarations
          // Pending if: status is PENDING, or approved is null/false
          const pending = data.declarations.filter((d: Declaration) => {
            const status = (d.status || '').toUpperCase()
            const isPendingStatus = status === 'PENDING' || status === 'SUBMITTED'
            const isNotApproved = d.approved === null || d.approved === undefined || d.approved === false
            const isPending = isPendingStatus || isNotApproved
            return isPending
          })
          setStudentPendingDeclarations(pending.length)
        } else {
          setStudentPendingDeclarations(0)
        }
      } else {
        setStudentPendingDeclarations(0)
      }
    } catch (error) {
      console.error('Error checking student pending items:', error)
      setStudentPendingWaivers(0)
      setStudentPendingDeclarations(0)
    }
  }

  /**
   * Check registration eligibility for a student
   */
  const checkRegistrationEligibility = async (userId: number) => {
    try {
      const response = await fetch(
        `${backendBase}/api/admin/registration-schedules/check/${userId}?semester=${currentSemester}&year=${currentYear}`,
        {
          method: 'GET',
          credentials: 'include',
          headers: { 'Content-Type': 'application/json' }
        }
      )

      if (response.ok) {
        const data = await response.json()
        // Only show alert if registration is explicitly available
        if (data.canRegister) {
          setRegistrationEligibility(data)
        }
      }
    } catch (error) {
      console.error('Error checking registration eligibility:', error)
      // Silently ignore - registration can proceed without schedule
    }
  }

  // TP: Used cursor to combine LEC and REC schedules
  const combineSchedules = (course: StudentScheduleItem) => {
    const schedules = []

    if (course.lecSchedule) schedules.push(course.lecSchedule)
    if (course.schedule) schedules.push(course.schedule)
    return schedules.join(', ')
  }

  // TP: Used cursor to normalize day names
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

  // TP: Used cursor to extract start and end times from schedule string
  const extractStartEnd = (s: string) => {
    // Supports formats: "9:30 AM", "09:00 AM - 10:20 AM", "09:00-10:20PM"
    const range = s.match(/(\d{1,2}:\d{2}\s*[AP]M)\s*[-–]\s*(\d{1,2}:\d{2}\s*[AP]M)/i)
    const single = s.match(/(\d{1,2}:\d{2})\s*([AP]M)/i)
    if (range) return { start: range[1].replace(/\s+/g, ''), end: range[2].replace(/\s+/g, '') }

    if (single) return { start: (single[1] + single[2]).replace(/\s+/g, ''), end: null }

    return { start: s, end: null }
  }

  // TP: Used cursor to check if two courses have overlapping schedules
  const isOverlap = (a: StudentScheduleItem, b: StudentScheduleItem) => {
    const scheduleA = combineSchedules(a)
    const scheduleB = combineSchedules(b)
    const da = normalizeDays(scheduleA || '')
    const db = normalizeDays(scheduleB || '')
    if (![...da].some(d => db.includes(d))) return false
    const ta = extractStartEnd(scheduleA || '')
    const tb = extractStartEnd(scheduleB || '')
    // simplest: if start time text matches, treat as overlap; or full range equality
    if (ta.start && tb.start && ta.start.toUpperCase() === tb.start.toUpperCase()) return true
    if (ta.end && tb.end && ta.start.toUpperCase() === tb.start.toUpperCase() && ta.end.toUpperCase() === tb.end.toUpperCase()) return true
    // fallback: identical schedule string
    return (scheduleA || '').trim().toUpperCase() === (scheduleB || '').trim().toUpperCase()
  }

  // TP: Used cursor to find conflict pairs by checking actual schedule overlap
  const findConflictPairs = (list: StudentScheduleItem[]) => {
    const pairs: Array<[StudentScheduleItem, StudentScheduleItem]> = []
    for (let i = 0; i < list.length; i++) {
      for (let j = i + 1; j < list.length; j++) {
        if (isOverlap(list[i], list[j])) pairs.push([list[i], list[j]])
      }
    }
    return pairs
  }

  // TP: Used cursor to check for actual time conflicts in cart or registered classes
  const checkTimeConflicts = async (userId: number) => {
    try {
      // Get cart items
      const cartResponse = await fetch(`${backendBase}/api/cart/items?userId=${userId}`, {
        method: 'GET',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' }
      })

      if (cartResponse.ok) {
        const cartData = await cartResponse.json()
        if (cartData.success && cartData.items) {
          const cartItems = cartData.items
          const conflictPairs = findConflictPairs(cartItems)

          if (conflictPairs.length > 0) {
            const conflictMessages = conflictPairs.map(([a, b]) =>
              `${a.code || `${a.department}${a.courseNum}.${a.section}`} and ${b.code || `${b.department}${b.courseNum}.${b.section}`}`
            )
            setTimeConflicts(conflictMessages)
          } else {
            setTimeConflicts([])
          }
        } else {
          setTimeConflicts([])
        }
      } else {
        setTimeConflicts([])
      }
    } catch (error) {
      console.error('Error checking time conflicts:', error)
      setTimeConflicts([])
    }
  }

  /**
   * Calculate GPA directly from completed classes
   */
  const calculateGpa = async (userId: number) => {
    try {
      const completedClassesResponse = await fetch(`${backendBase}/api/users/${userId}/classes/completed`, {
        credentials: 'include'
      })

      if (completedClassesResponse.ok) {
        const completedClasses: Array<{ credits: number; grade: string | null; semester?: string; year?: number }> = await completedClassesResponse.json()

        // Grade to GPA points mapping
        const gradePoints: Record<string, number> = {
          'A+': 4.0, 'A': 4.0, 'A-': 3.7,
          'B+': 3.3, 'B': 3.0, 'B-': 2.7,
          'C+': 2.3, 'C': 2.0, 'C-': 1.7,
          'D+': 1.3, 'D': 1.0, 'D-': 0.7,
          'F': 0.0, 'W': 0.0, 'I': 0.0,
          'P': 0.0, 'NP': 0.0, 'S': 0.0, 'U': 0.0
        }
        // Filter classes with valid grades (exclude pass/fail, incomplete, transfer credits, and placement exams)
        const gradedClasses = completedClasses.filter(cls => {
          const grade = cls.grade?.trim().toUpperCase()
          if (!grade) return false

          // Exclude transfer credit ('T') - does not affect GPA
          if (grade === 'T') return false

          // Exclude placement exam grades (numeric 1-9) - does not affect GPA
          const numericGrade = parseFloat(grade)
          if (!isNaN(numericGrade) && numericGrade >= 1 && numericGrade <= 9 && grade.length === 1) {
            return false
          }

          // Exclude pass/fail and incomplete grades
          return grade !== 'P' &&
            grade !== 'NP' &&
            grade !== 'S' &&
            grade !== 'U' &&
            grade !== 'W' &&
            grade !== 'I' &&
            gradePoints[grade] !== undefined
        })

        // Calculate cumulative GPA: (sum of credits × grade points) / total credits
        let totalPoints = 0
        let totalCredits = 0

        gradedClasses.forEach(cls => {
          const grade = cls.grade?.trim().toUpperCase() || ''
          const points = gradePoints[grade] || 0
          const credits = cls.credits || 0

          totalPoints += points * credits
          totalCredits += credits
        })

        // Calculate cumulative GPA
        const calculatedGpaValue = totalCredits > 0 ? totalPoints / totalCredits : null
        setCalculatedGpa(calculatedGpaValue)

        // Calculate most recent term GPA (find the term with most recent year and semester)
        if (completedClasses.length > 0) {
          // Find the most recent term from completed classes
          const termMap = new Map<string, Array<{ credits: number; grade: string | null }>>()

          completedClasses.forEach(cls => {
            if (cls.semester && cls.year) {
              const normalizedSemester = cls.semester.charAt(0).toUpperCase() + cls.semester.slice(1).toLowerCase()
              const termKey = `${cls.year}-${normalizedSemester}`
              if (!termMap.has(termKey)) {
                termMap.set(termKey, [])
              }
              termMap.get(termKey)!.push(cls)
            }
          })

          // Find the most recent term (highest year, then Fall > Summer > Spring)
          let latestTermKey: string | null = null
          let latestYear = 0
          let latestSemesterOrder = 0

          termMap.forEach((_, termKey) => {
            const [yearStr, semester] = termKey.split('-')
            const year = parseInt(yearStr)
            const semesterOrder = semester === 'Fall' ? 3 : semester === 'Summer' ? 2 : 1

            if (year > latestYear || (year === latestYear && semesterOrder > latestSemesterOrder)) {
              latestYear = year
              latestSemesterOrder = semesterOrder
              latestTermKey = termKey
            }
          })

          // Calculate GPA for the most recent term
          if (latestTermKey) {
            const latestTermClasses = termMap.get(latestTermKey)!
            const latestTermGraded = latestTermClasses.filter(cls => {
              const grade = cls.grade?.trim().toUpperCase()
              return grade &&
                grade !== 'P' &&
                grade !== 'NP' &&
                grade !== 'S' &&
                grade !== 'U' &&
                grade !== 'W' &&
                grade !== 'I' &&
                gradePoints[grade] !== undefined
            })

            let termPoints = 0
            let termCredits = 0

            latestTermGraded.forEach(cls => {
              const grade = cls.grade?.trim().toUpperCase() || ''
              const points = gradePoints[grade] || 0
              const credits = cls.credits || 0

              termPoints += points * credits
              termCredits += credits
            })

            const termGpaValue = termCredits > 0 ? termPoints / termCredits : null
            setTermGpa(termGpaValue)
          } else {
            setTermGpa(null)
          }
        } else {
          setTermGpa(null)
        }

        // Set total credits (include all completed classes)
        const allCredits = completedClasses.reduce((sum, cls) => sum + (cls.credits || 0), 0)
        setCumulativeCredits(allCredits)
      } else {
        setCalculatedGpa(null)
        setTermGpa(null)
        setCumulativeCredits(0)
      }
    } catch (error) {
      console.error('Error calculating GPA:', error)
      setCalculatedGpa(null)
      setTermGpa(null)
      setCumulativeCredits(0)
    }
  }

  /**
   * Fetch student schedule for current term (following Profile.tsx logic)
   */
  const fetchStudentSchedule = async (userId: number) => {
    try {
      const termToUse = `${currentYear}-${currentSemester}`
      const termYearNum = currentYear
      const normalizedSemester = currentSemester ?
        (currentSemester.charAt(0).toUpperCase() + currentSemester.slice(1).toLowerCase()) :
        null

      console.log('[Dashboard] Fetching schedule for term:', termToUse, 'userId:', userId)

      // First, try to get studentId from profile
      let studentId: string | null = null
      if (me?.studentId) {
        studentId = me.studentId
        console.log('[Dashboard] Using studentId from me:', studentId)
      } else {
        // Try to get studentId from profile API
        try {
          const profileResponse = await fetch(`${backendBase}/api/profile/me`, {
            credentials: 'include'
          })
          if (profileResponse.ok) {
            const profileData = await profileResponse.json()
            studentId = profileData.studentId || profileData.sbuId || null
            console.log('[Dashboard] Got studentId from profile API:', studentId)
          }
        } catch (error) {
          console.error('[Dashboard] Error fetching profile:', error)
          // Continue with roster API
        }
      }

      // Try profile schedule API first (like Profile.tsx)
      if (studentId && termYearNum && normalizedSemester) {
        try {
          const profileScheduleUrl = `${backendBase}/api/profile/student-id/${encodeURIComponent(studentId)}/schedule?term=${encodeURIComponent(termToUse)}`
          console.log('[Dashboard] Trying profile schedule API:', profileScheduleUrl)
          const scheduleResponse = await fetch(profileScheduleUrl, {
            credentials: 'include'
          })

          if (scheduleResponse.ok) {
            const scheduleData = await scheduleResponse.json()
            console.log('[Dashboard] Profile schedule API response:', scheduleData)
            if (Array.isArray(scheduleData) && scheduleData.length > 0) {
              console.log('[Dashboard] Setting schedule from profile API:', scheduleData.length, 'classes')
              setStudentSchedule(scheduleData)
              return
            }
          } else {
            console.log('[Dashboard] Profile schedule API failed:', scheduleResponse.status)
          }
        } catch (error) {
          console.error('[Dashboard] Error fetching profile schedule:', error)
          // Fall through to roster API
        }
      }

      // Fallback to roster API (like Profile.tsx)
      if (termYearNum && normalizedSemester) {
        try {
          const rosterUrl = `${backendBase}/api/roster/student/${userId}`
          console.log('[Dashboard] Trying roster API:', rosterUrl, 'filtering for:', termYearNum, normalizedSemester)
          const rosterResponse = await fetch(rosterUrl, {
            credentials: 'include'
          })

          if (rosterResponse.ok) {
            const rosterData = await rosterResponse.json()
            console.log('[Dashboard] Roster API response:', rosterData?.length || 0, 'items')

            if (Array.isArray(rosterData) && rosterData.length > 0) {
              const filtered = rosterData.filter((item: RosterItem) => {
                const itemYear = typeof item.year === 'number' ? item.year : parseInt(String(item.year))
                const itemSemester = item.semester ?
                  (item.semester.charAt(0).toUpperCase() + item.semester.slice(1).toLowerCase()) :
                  null
                return itemYear === termYearNum && itemSemester === normalizedSemester
              })

              console.log('[Dashboard] Filtered roster data:', filtered.length, 'items for term')

              if (filtered.length > 0) {
                // Map to schedule format (like Profile.tsx)
                const scheduleData = filtered.map((item: RosterItem) => ({
                  classId: item.classId,
                  subject: item.department || item.subject || '',
                  courseNum: item.courseNum,
                  section: item.section || '',
                  title: item.title || '',
                  schedule: item.schedule || '',
                  location: item.location || '',
                  instructor: item.instructor || '',
                  credits: item.credits || item.minCredits || 0
                }))
                console.log('[Dashboard] Setting schedule from roster API:', scheduleData.length, 'classes')
                setStudentSchedule(scheduleData)
                return
              }
            }
          } else {
            console.log('[Dashboard] Roster API failed:', rosterResponse.status)
          }
        } catch { // Added error variable
          console.error('[Dashboard] Error fetching roster data for schedule:', error)
        }
      }

      // If nothing found, set empty array
      console.log('[Dashboard] No schedule data found, setting empty array')
      setStudentSchedule([])
    } catch {
      console.error('Error fetching student schedule:', error)
      setStudentSchedule([])
    }
  }

  /**
   * Fetch student course plan
   */
  const fetchStudentCoursePlan = async (userId: number) => {
    try {
      const response = await fetch(`${backendBase}/api/planner/${userId}`, {
        credentials: 'include'
      })

      if (response.ok) {
        const data = await response.json()
        if (data.plannedCourses && Array.isArray(data.plannedCourses)) {
          setStudentCoursePlan(data.plannedCourses)
        } else {
          setStudentCoursePlan([])
        }
      } else {
        // Fallback to user course plan API
        const fallbackResponse = await fetch(`${backendBase}/api/users/${userId}/course-plan`, {
          credentials: 'include'
        })

        if (fallbackResponse.ok) {
          const planData = await fallbackResponse.json()
          setStudentCoursePlan(Array.isArray(planData) ? planData : [])
        } else {
          setStudentCoursePlan([])
        }
      }
    } catch {
      setStudentCoursePlan([])
    }
  }

  /**
   * Fetch academic events for reminders
   */
  const fetchAcademicEvents = async () => {
    try {
      const response = await fetch(`${backendBase}/api/admin/academic-calendar`, {
        credentials: 'include'
      })

      if (response.ok) {
        const events = await response.json()
        setAcademicEvents(Array.isArray(events) ? events : [])
      } else {
        setAcademicEvents([])
      }
    } catch {
      setAcademicEvents([])
    }
  }

  /**
   * Calculate overall progress based on cumulative credits
   */
  useEffect(() => {
    if (cumulativeCredits > 0) {
      // Typical degree requirement is 120 credits
      const totalRequired = 120
      const progress = Math.min(100, Math.round((cumulativeCredits / totalRequired) * 100))
      setOverallProgress(progress)
    } else {
      setOverallProgress(0)
    }
  }, [cumulativeCredits])

  /**
   * Refetch schedule when semester/year changes
   */
  useEffect(() => {
    if (me?.userId && me?.role === 'STUDENT' && currentSemester && currentYear) {
      fetchStudentSchedule(me.userId)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentSemester, currentYear])

  /**
   * Fetch advisor data (pending declarations and waivers)
   */
  const fetchAdvisorData = async (advisorId: number) => {
    try {
      // Fetch pending declarations
      const declarationsResponse = await fetch(`${backendBase}/api/major-minor-declarations/pending`, {
        credentials: 'include'
      })

      if (declarationsResponse.ok) {
        const data = await declarationsResponse.json()
        if (data.success && Array.isArray(data.declarations)) {
          setPendingDeclarations(data.declarations.length)
        }
      }

      // Fetch pending waivers for advisor
      const waiversResponse = await fetch(`${backendBase}/api/waivers/advisor/${advisorId}`, {
        credentials: 'include'
      })

      if (waiversResponse.ok) {
        const waivers = await waiversResponse.json()
        if (Array.isArray(waivers)) {
          const pending = waivers.filter((w: Waiver) => w.status === 'PENDING' && w.advisorApproved === null)
          setAdvisorPendingWaivers(pending.length)
        }
      }
    } catch {
      setPendingDeclarations(0)
      setAdvisorPendingWaivers(0)
    }
  }

  /**
   * Check for pending overrides (for admin/registrar)
   */
  const checkPendingOverrides = async () => {
    try {
      // Check pending waivers
      const waiversResponse = await fetch(`${backendBase}/api/waivers/pending`, {
        method: 'GET',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' }
      })

      let pendingCount = 0
      let recentWaivers: Waiver[] = [] // Changed type to Waiver[]

      if (waiversResponse.ok) {
        const waivers = await waiversResponse.json()
        if (Array.isArray(waivers)) {

          pendingCount += waivers.length
          // Get recent waivers (last 7 days)
          const sevenDaysAgo = new Date()
          sevenDaysAgo.setDate(sevenDaysAgo.getDate() - 7)
          recentWaivers = waivers
            .filter((w: Waiver) => {
              if (!w.createdAt) return false
              const createdDate = new Date(w.createdAt || '')
              return createdDate >= sevenDaysAgo
            })
            .slice(0, 10) // Limit to 10 most recent
        }
      }

      // Check pending declarations
      const declarationsResponse = await fetch(`${backendBase}/api/declarations/pending`, {
        method: 'GET',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' }
      })

      if (declarationsResponse.ok) {
        const declarationsData = await declarationsResponse.json()
        if (declarationsData.success && declarationsData.declarations) {
          pendingCount += Array.isArray(declarationsData.declarations) ? declarationsData.declarations.length : 0
        }
      }

      setPendingOverrides(pendingCount)
      setRecentOverrides(recentWaivers)
    } catch {
      setPendingOverrides(0)
      setRecentOverrides([])
    }
  }

  /**
   * Fetch professor data (teaching classes, stats, pending items)
   */
  const fetchProfessorData = async (user: Me) => {
    if (!user?.fullName && !user?.firstName || !user?.lastName) {
      setTeachingClasses([]);
      setClassStats([]);
      return;
    }

    try {
      // Build instructor name - try fullName first, then firstName + lastName
      const instructorName = user.fullName || `${user.firstName} ${user.lastName}`.trim();
      
      // Fetch classes taught by this instructor
      const classesResponse = await fetch(`${backendBase}/api/classes/instructor/${encodeURIComponent(instructorName)}`, {
        credentials: 'include'
      });

      let classes: TeachingClass[] = [];
      if (classesResponse.ok) {
        const classData = await classesResponse.json();
        if (Array.isArray(classData)) {
          // Filter for current semester/year
          const currentSem = getCurrentSemester();
          const currentYr = getCurrentYear();
          
          classes = classData
            .filter((cls: any) => cls.semester === currentSem && cls.year === currentYr)
            .map((cls: any) => ({
              id: cls.classId || cls.id,
              classId: cls.classId || cls.id,
              code: `${cls.department || ''} ${cls.courseNum || ''}`.trim(),
              title: cls.title || '',
              department: cls.department || '',
              courseNum: cls.courseNum || 0,
              section: cls.section || '',
              semester: cls.semester || currentSem,
              year: cls.year || currentYr,
              maxCapacity: cls.maxCapacity || 0
            }));
        }
      }

      setTeachingClasses(classes);

      // Fetch statistics for each class
      if (classes.length > 0) {
        const statsPromises = classes.map(async (cls: TeachingClass) => {
          try {
            const statsResponse = await fetch(`${backendBase}/api/roster/stats/${cls.classId}`, {
              credentials: 'include'
            });
            if (statsResponse.ok) {
              const statsData = await statsResponse.json();
              return {
                id: cls.id,
                code: cls.code,
                enrollment: statsData.activeEnrollments || 0,
                activeEnrollments: statsData.activeEnrollments || 0,
                maxCapacity: statsData.maxCapacity || cls.maxCapacity || 0,
                isFull: statsData.isFull || false
              };
            }
            return {
              id: cls.id,
              code: cls.code,
              enrollment: 0,
              activeEnrollments: 0,
              maxCapacity: cls.maxCapacity || 0,
              isFull: false
            };
          } catch {
            return {
              id: cls.id,
              code: cls.code,
              enrollment: 0,
              activeEnrollments: 0,
              maxCapacity: cls.maxCapacity || 0,
              isFull: false
            };
          }
        });
        const stats = await Promise.all(statsPromises);
        setClassStats(stats);
      } else {
        setClassStats([]);
      }
    } catch (error) {
      console.error('Error fetching professor data:', error);
      setTeachingClasses([]);
      setClassStats([]);
    }
  };

  /**
   * Fetch admin statistics (for admin/registrar)
   */
  const fetchAdminStats = async () => {
    try {
      // Fetch user statistics
      const userStatsResponse = await fetch(`${backendBase}/api/users/statistics`, {
        credentials: 'include'
      });
      
      if (userStatsResponse.ok) {
        const userData = await userStatsResponse.json();
        setUserStats({
          totalUsers: userData.totalUsers || 0,
          activeUsers: userData.activeUsers || 0,
          students: userData.students || 0,
          instructors: userData.instructors || 0,
          advisors: userData.advisors || 0,
          registrars: userData.registrars || 0
        });
      } else {
        setUserStats({
          totalUsers: 0,
          activeUsers: 0,
          students: 0,
          instructors: 0,
          advisors: 0,
          registrars: 0
        });
      }

      // Fetch database status
      const dbStatusResponse = await fetch(`${backendBase}/api/scraping/db-status`, {
        credentials: 'include'
      });
      
      if (dbStatusResponse.ok) {
        const dbData = await dbStatusResponse.json();
        setDbStats({
          status: dbData.status || 'ERROR',
          totalTerms: dbData.totalTerms || 0,
          totalCourses: dbData.totalCourses || 0
        });
      } else {
        setDbStats({
          status: 'ERROR',
          totalTerms: 0,
          totalCourses: 0
        });
      }

      // Fetch scraping statistics
      const scrapingStatsResponse = await fetch(`${backendBase}/api/scraping/stats`, {
        credentials: 'include'
      });
      
      if (scrapingStatsResponse.ok) {
        const scrapingData = await scrapingStatsResponse.json();
        setScrapingStats({
          totalCourses: scrapingData.totalCourses || 0,
          totalTerms: scrapingData.totalTerms || 0,
          scrapedTerms: scrapingData.scrapedTerms || 0
        });
      } else {
        setScrapingStats({
          totalCourses: 0,
          totalTerms: 0,
          scrapedTerms: 0
        });
      }
    } catch (error) {
      console.error('Error fetching admin stats:', error);
      // Set defaults on error
      setUserStats({
        totalUsers: 0,
        activeUsers: 0,
        students: 0,
        instructors: 0,
        advisors: 0,
        registrars: 0
      });
      setDbStats({
        status: 'ERROR',
        totalTerms: 0,
        totalCourses: 0
      });
      setScrapingStats({
        totalCourses: 0,
        totalTerms: 0,
        scrapedTerms: 0
      });
    }
  };

  /**
   * Fetch recent imports (scraped terms and degree requirements)
   */
  const fetchRecentImports = async () => {
    try {
      // Fetch scraped terms (recent class imports)
      const termsResponse = await fetch(`${backendBase}/api/scraping/terms`, {
        credentials: 'include'
      })

      let classes: Array<{ term: string; courseCount: number }> = []
      if (termsResponse.ok) {
        const terms = await termsResponse.json()
        if (Array.isArray(terms)) {
          // Get course count for each term
          const termPromises = terms
            .filter((t: { isScraped: boolean; name: string }) => t.isScraped)
            .slice(0, 5) // Get 5 most recent
            .map(async (term: { name: string }) => {
              try {
                const coursesResponse = await fetch(`${backendBase}/api/scraping/courses/byTerm?term=${encodeURIComponent(term.name)}`, {
                  credentials: 'include'
                })
                if (coursesResponse.ok) {
                  const courses = await coursesResponse.json()
                  return {
                    term: term.name,
                    courseCount: Array.isArray(courses) ? courses.length : 0
                  }
                }
                return { term: term.name, courseCount: 0 }
              } catch {
                return { term: term.name, courseCount: 0 }
              }
            })
          classes = await Promise.all(termPromises)
        }
      }

      // Fetch degree requirements (recent degree req imports)
      const degreeReqsResponse = await fetch(`${backendBase}/api/degree-requirements`, {
        credentials: 'include'
      })

      let degreeReqs: Array<{ subject: string; version: string }> = []
      if (degreeReqsResponse.ok) {
        const data = await degreeReqsResponse.json()
        if (data.success && Array.isArray(data.requirements)) {
          // Group by subject and get effective term as version
          const reqsBySubject = new Map<string, string>()
          data.requirements.forEach((req: { subjectCode: string; effectiveTerm: string }) => {
            if (req.subjectCode && req.effectiveTerm) {
              const existing = reqsBySubject.get(req.subjectCode)
              if (!existing || req.effectiveTerm > existing) {
                reqsBySubject.set(req.subjectCode, req.effectiveTerm)
              }
            }
          })
          degreeReqs = Array.from(reqsBySubject.entries())
            .slice(0, 5)
            .map(([subject, version]) => ({ subject, version }))
        }
      }

      setRecentImports({ classes, degreeReqs })
    } catch {
      setRecentImports({ classes: [], degreeReqs: [] })
    }
  }

  if (isLoading) {
    return <LoadingModal isOpen={true} message="Loading dashboard..." />
  }

  const role = String(me?.role || '').toUpperCase()
  const isRegistrar = role === 'REGISTRAR' || role === 'ADMIN'
  const isInstructor = role === 'INSTRUCTOR'
  const isAdvisor = role === 'ADVISOR'

  return (
    <div className="dashboard">
      {/* Header */}
      <header className="dashboard-header">
        <h1>Welcome back{me ? `, ${me.fullName} ` : ''}</h1>
      </header>

      {/* Main Content */}
      <div className="dashboard-content">
        {isInstructor ? (
          <>
            {/* Professor Dashboard */}
            <div className="left-column">
              {/* Quick Stats */}
              <div className="stats-container">
                <div className="stat-card">
                  <div className="stat-label">Teaching Classes</div>
                  <div className="stat-value">{teachingClasses.length}</div>
                </div>
                <div className="stat-card">
                  <div className="stat-label">Total Students</div>
                  <div className="stat-value">
                    {classStats.reduce((sum, stat) => sum + (stat.activeEnrollments || 0), 0)}
                  </div>
                </div>
                <div className="stat-card">
                  <div className="stat-label">Grades Pending</div>

                </div>
                <div className="stat-card">
                  <div className="stat-label">Pending Overrides</div>

                </div>
              </div>

              {/* Teaching Classes */}
              <div className="reminder-card">
                <div className="reminder-title">My Classes</div>
                <div className="reminder-content">
                  {teachingClasses.length === 0 ? (
                    <div style={{ padding: '1rem', textAlign: 'center', color: 'var(--text-secondary)' }}>
                      No classes assigned.
                    </div>
                  ) : (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                      {teachingClasses.slice(0, 5).map((cls: TeachingClass) => {
                        const stats = classStats.find((s: ClassStat) => s.id === cls.id)
                        return (
                          <div
                            key={cls.classId}
                            className="course-btn"
                            style={{
                              width: '100%',
                              textAlign: 'left',
                              padding: '0.75rem 1rem',
                              cursor: 'pointer'
                            }}
                            onClick={() => navigate(`/roster?classId=${cls.classId}`)}
                          >
                            <div style={{ fontWeight: 600 }}>
                              {cls.department} {cls.courseNum}.{cls.section} - {cls.title}
                            </div>
                            <div style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginTop: '0.25rem' }}>
                              {cls.semester} {cls.year} • {stats ? `${stats.activeEnrollments || 0} /${stats.maxCapacity || 'N/A'} students` : 'No stats'}
                            </div >
                          </div >
                        )
                      })}
                      {
                        teachingClasses.length > 5 && (
                          <button
                            className="course-btn"
                            onClick={() => navigate('/roster')}
                            style={{ width: '100%', textAlign: 'center', padding: '0.75rem 1rem' }}
                          >
                            View All Classes ({teachingClasses.length})
                          </button>
                        )
                      }
                    </div >
                  )}
                </div >
              </div >

              {/* Quick Actions */}
              < div className="reminder-card" >
                <div className="reminder-title">Quick Actions</div>
                <div className="reminder-content">
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                    <button
                      className="course-btn"
                      onClick={() => navigate('/roster')}
                      style={{ width: '100%', textAlign: 'left', padding: '0.75rem 1rem' }}
                    >
                      View Class Rosters
                    </button>
                    <button
                      className="course-btn"
                      onClick={() => navigate('/grades')}
                      style={{ width: '100%', textAlign: 'left', padding: '0.75rem 1rem' }}
                    >
                      Enter Grades
                    </button>
                    <button
                      className="course-btn"
                      onClick={() => navigate('/advisor-approval')}
                      style={{ width: '100%', textAlign: 'left', padding: '0.75rem 1rem' }}
                    >
                      Registration Overrides
                    </button>
                  </div>
                </div>
              </div >
            </div >

            {/* Right Column */}
            < div className="right-column" >
              {/* Class Enrollment Overview */}
              < div className="schedule-section" >
                <div className="section-header">
                  <h2>Class Enrollment Overview</h2>
                  <h3>{currentSemester} {currentYear}</h3>
                </div>
                {
                  teachingClasses.length === 0 ? (
                    <div style={{ padding: '2rem', textAlign: 'center', color: 'var(--text-secondary)' }}>
                      No classes assigned for this semester.
                    </div>
                  ) : (
                    <table className="schedule-table">
                      <thead>
                        <tr>
                          <th className="time-header">Class</th>
                          <th className="day-header">Students</th>
                          <th className="day-header">Capacity</th>
                          <th className="day-header">Status</th>
                        </tr>
                      </thead>
                      <tbody>
                        {teachingClasses.map((cls: TeachingClass) => {
                          const stats = classStats.find((s: ClassStat) => s.id === cls.id)
                          const enrollment = stats?.activeEnrollments || 0
                          const capacity = stats?.maxCapacity || cls.maxCapacity || 'N/A'
                          const isFull = stats?.isFull || false

                          return (
                            <tr key={cls.classId}>
                              <td className="time-cell">
                                <div style={{ fontWeight: 600 }}>{cls.department} {cls.courseNum}.{cls.section}</div>
                                <div style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>{cls.title}</div>
                              </td>
                              <td className="course-cell">{enrollment}</td>
                              <td className="course-cell">{capacity}</td>
                              <td className="course-cell">
                                <span style={{
                                  padding: '0.25rem 0.5rem',
                                  borderRadius: '4px',
                                  fontSize: '0.8rem',
                                  backgroundColor: isFull ? '#fee' : '#dfd',
                                  color: isFull ? '#c33' : '#363'
                                }}>
                                  {isFull ? 'Full' : 'Open'}
                                </span>
                              </td>
                            </tr>
                          )
                        })}
                      </tbody>
                    </table>
                  )
                }
              </div >
            </div >
          </>
        ) : isAdvisor ? (
          <>
            {/* Advisor Dashboard */}
            <div className="left-column">
              {/* Pending Items Alerts */}
              {pendingDeclarations > 0 && (
                <div className="alert-box alert-warning">
                  <div className="alert-icon"></div>
                  <div className="alert-text">
                    <span className="alert-title">Declarations Pending:</span> You have {pendingDeclarations} declaration{pendingDeclarations > 1 ? 's' : ''} awaiting your review.
                  </div>
                </div>
              )}

              {advisorPendingWaivers > 0 && (
                <div className="alert-box alert-warning">
                  <div className="alert-icon"></div>
                  <div className="alert-text">
                    <span className="alert-title">Override Requests:</span> You have {advisorPendingWaivers} override request{advisorPendingWaivers > 1 ? 's' : ''} awaiting your review.
                  </div>
                </div>
              )}

              {/* Quick Stats */}
              <div className="stats-container">
                <div className="stat-card">
                  <div className="stat-label">Pending Declarations</div>
                  <div className="stat-value">{pendingDeclarations}</div>
                </div>
                <div className="stat-card">
                  <div className="stat-label">Pending Overrides</div>
                  <div className="stat-value">{advisorPendingWaivers}</div>
                </div>
              </div>

              {/* Quick Actions */}
              <div className="reminder-card">
                <div className="reminder-title">Quick Actions</div>
                <div className="reminder-content">
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                    <button
                      className="course-btn"
                      onClick={() => navigate('/advisor-approval')}
                      style={{ width: '100%', textAlign: 'left', padding: '0.75rem 1rem' }}
                    >
                      Review Declarations & Overrides
                    </button>
                    <button
                      className="course-btn"
                      onClick={() => navigate('/degree-audit')}
                      style={{ width: '100%', textAlign: 'left', padding: '0.75rem 1rem' }}
                    >
                      View Student Degree Audit
                    </button>
                  </div>
                </div>
              </div>
            </div>

            {/* Right Column */}
            <div className="right-column">
              <div className="schedule-section">
                <div className="section-header">
                  <h2>Recent Activity</h2>
                  <h3>Last 7 days</h3>
                </div>
                <div style={{ padding: '2rem', textAlign: 'center', color: 'var(--text-secondary)' }}>
                  No recent activity to display.
                </div>
              </div>
            </div>
          </>
        ) : !isRegistrar ? (
          <>
            {/* Left Column */}
            <div className="left-column">
              {/* Registration Hold Alert - Show if there are active holds */}
              {activeHolds.length > 0 && (
                <div className="alert-box alert-error">
                  <div className="alert-icon"></div>
                  <div className="alert-text">
                    <span className="alert-title">Registration Blocked:</span> You have {activeHolds.length} active hold{activeHolds.length > 1 ? 's' : ''} that prevent registration:
                    <ul style={{ marginTop: '0.5rem', marginBottom: 0, paddingLeft: '1.5rem' }}>
                      {activeHolds.map((hold: Hold, index: number) => (
                        <li key={index} style={{ marginTop: '0.25rem' }}>
                          <strong>{hold.holdType}:</strong> {hold.reason || 'No reason provided'}
                        </li>
                      ))}
                    </ul>
                    <div style={{ marginTop: '0.5rem', fontSize: '0.9rem' }}>
                      Please resolve these holds before registering for classes.
                    </div>
                  </div>
                </div>
              )}

              {/* Registration Eligibility Alert - Only show if registration is open */}
              {registrationEligibility && registrationEligibility.canRegister && (
                <div className="alert-box alert-success">
                  <div className="alert-icon"></div>
                  <div className="alert-text">
                    <span className="alert-title">Registration Open:</span> You can register for {currentSemester} {currentYear}.
                    {registrationEligibility.schedule && registrationEligibility.schedule.endDate && (
                      <span> Registration ends {new Date(registrationEligibility.schedule.endDate).toLocaleDateString()}.</span>
                    )}
                  </div>
                </div>
              )}

              {/* Time Conflict Alert - Only show if there are actual conflicts */}
              {timeConflicts.length > 0 && (
                <div className="alert-box alert-warning">
                  <div className="alert-icon"></div>
                  <div className="alert-text">
                    <span className="alert-title">Time Conflicts Detected:</span> {`Time conflicts detected: ${timeConflicts.join(', ')}. Please resolve before submitting.`}
                  </div>
                </div>
              )}

              {/* Student Pending Waivers Alert */}
              {studentPendingWaivers > 0 && (
                <div className="alert-box alert-warning">
                  <div className="alert-icon"></div>
                  <div className="alert-text">
                    <span className="alert-title">Pending Override Requests:</span> You have {studentPendingWaivers} override request{studentPendingWaivers > 1 ? 's' : ''} awaiting review.
                  </div>
                </div>
              )}

              {/* Student Pending Declarations Alert */}
              {studentPendingDeclarations > 0 && (
                <div className="alert-box alert-warning">
                  <div className="alert-icon"></div>
                  <div className="alert-text">
                    <span className="alert-title">Pending Declarations:</span> You have {studentPendingDeclarations} declaration{studentPendingDeclarations > 1 ? 's' : ''} awaiting review.
                  </div>
                </div>
              )}

              {/* Statistics Cards */}
              <div className="stats-container">
                <div className="stat-card">
                  <div className="stat-label">GPA</div>
                  <div className="stat-value">{calculatedGpa !== null ? calculatedGpa.toFixed(2) : '—'}</div>
                </div>
                <div className="stat-card">
                  <div className="stat-label">Term</div>
                  <div className="stat-value">{termGpa !== null ? termGpa.toFixed(2) : '—'}</div>
                </div>
                <div className="stat-card">
                  <div className="stat-label">Cumulative Credits</div>
                  <div className="stat-value">{cumulativeCredits > 0 ? cumulativeCredits : '—'}</div>
                </div>
              </div>

              {/* Overall Progress */}
              <div className="progress-section">
                <div className="progress-header">
                  <div className="progress-label">Overall Progress</div>
                  <div className="progress-text">{overallProgress}%</div>
                </div>
                <div className="progress-bar">
                  <div className="progress-fill" style={{ width: `${overallProgress}%` }}></div>
                </div>
              </div>

              {/* Reminder Card */}
              <div className="reminder-card">
                <div className="reminder-title">Reminder</div>
                <div className="reminder-content">
                  {academicEvents.length > 0 ? (
                    academicEvents
                      .filter((event: AcademicEvent) => {
                        if (!event.date) return false
                        const eventDate = new Date(event.date)
                        const today = new Date()
                        today.setHours(0, 0, 0, 0)
                        const daysDiff = Math.ceil((eventDate.getTime() - today.getTime()) / (1000 * 60 * 60 * 24))
                        return daysDiff >= 0 && daysDiff <= 30 // Show events in next 30 days
                      })
                      .sort((a: AcademicEvent, b: AcademicEvent) => {
                        const dateA = new Date(a.date).getTime()
                        const dateB = new Date(b.date).getTime()
                        return dateA - dateB
                      })
                      .slice(0, 2)
                      .map((event: AcademicEvent, idx: number) => {
                        const eventDate = new Date(event.date)
                        const today = new Date()
                        today.setHours(0, 0, 0, 0)
                        const daysDiff = Math.ceil((eventDate.getTime() - today.getTime()) / (1000 * 60 * 60 * 24))
                        return (
                          <div key={idx}>
                            {daysDiff === 0 ? 'Today: ' : daysDiff === 1 ? 'Tomorrow: ' : `${daysDiff} days left: `}
                            {event.description || event.eventName || 'Academic event'}
                          </div>
                        )
                      })
                  ) : (
                    <div>No upcoming events.</div>
                  )}
                </div>
              </div>
            </div>

            {/* Right Column */}
            <div className="right-column">
              {/* Course Schedule */}
              <div className="schedule-section">
                <div className="section-header">
                  <h2>Current Classes</h2>
                  <h3>{currentSemester} {currentYear}</h3>
                </div>

                {studentSchedule.length > 0 ? (
                  <div>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
                      {studentSchedule.map((course: StudentScheduleItem, idx: number) => {
                        const courseCode = `${course.subject || course.department || ''}${course.courseNum || ''}`
                        const scheduleStr = combineSchedules(course)
                        const location = course.location || ''
                        const instructor = course.instructor || ''
                        const title = course.title || ''

                        return (
                          <div
                            key={idx}
                            style={{
                              padding: '1rem',
                              border: '1px solid #ddd',
                              borderRadius: '8px',
                              backgroundColor: '#fff'
                            }}
                          >
                            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '0.5rem' }}>
                              <div>
                                <div style={{ fontSize: '1.1rem', fontWeight: 'bold', marginBottom: '0.25rem' }}>
                                  {courseCode}
                                  {course.section && <span style={{ fontSize: '0.9rem', fontWeight: 'normal', color: '#666' }}> - Section {course.section}</span>}
                                </div>
                                {title && (
                                  <div style={{ fontSize: '0.9rem', color: '#666', marginBottom: '0.25rem' }}>
                                    {title}
                                  </div>
                                )}
                              </div>
                            </div>
                            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem', fontSize: '0.85rem', color: '#555' }}>
                              {scheduleStr && (
                                <div>
                                  <strong>Schedule:</strong> {scheduleStr}
                                </div>
                              )}
                              {location && (
                                <div>
                                  <strong>Location:</strong> {location}
                                </div>
                              )}
                              {instructor && (
                                <div>
                                  <strong>Instructor:</strong> {instructor}
                                </div>
                              )}
                              {course.credits && (
                                <div>
                                  <strong>Credits:</strong> {course.credits}
                                </div>
                              )}
                            </div>
                          </div>
                        )
                      })}
                    </div>
                  </div>
                ) : (
                  <div style={{ padding: '2rem', textAlign: 'center', color: 'var(--text-secondary)' }}>
                    No classes scheduled for {currentSemester} {currentYear}
                  </div>
                )}
              </div>

              {/* Planner */}
              <div className="planner-section">
                <div className="section-header">
                  <h2>Planner</h2>
                </div>
                <div className="planner-container">
                  {studentCoursePlan.length > 0 ? (
                    (() => {
                      // Group courses by semester and year
                      const planByTerm: { [key: string]: Array<CoursePlanItem['courses'][number] & { term: string; year: number }> } = {}
                      studentCoursePlan.forEach((planItem: CoursePlanItem) => {
                        if (planItem.courses && Array.isArray(planItem.courses)) {
                          planItem.courses.forEach((course) => {
                            const termKey = `${planItem.term} ${planItem.year}`
                            if (!planByTerm[termKey]) planByTerm[termKey] = []
                            planByTerm[termKey].push({ ...course, term: planItem.term, year: planItem.year })
                          })
                        }
                      })

                      // Sort terms chronologically
                      const sortedTerms = Object.keys(planByTerm).sort((a, b) => {
                        const [semA, yearA] = a.split(' ')
                        const [semB, yearB] = b.split(' ')
                        const yearNumA = parseInt(yearA)
                        const yearNumB = parseInt(yearB)
                        if (yearNumA !== yearNumB) return yearNumA - yearNumB
                        const semOrder: { [key: string]: number } = { 'Spring': 0, 'Summer': 1, 'Fall': 2 }
                        return (semOrder[semA] || 0) - (semOrder[semB] || 0)
                      })

                      return (
                        <div className="planner-columns">
                          {sortedTerms.slice(0, 3).map((term, idx) => (
                            <div key={idx} className="planner-column">
                              <div className="planner-semester">{term}</div>
                              <div className="course-buttons">
                                {planByTerm[term].map((course, courseIdx: number) => {
                                  const courseCode = `${course.department || course.subject || ''} ${course.courseNum || course.courseNumber || ''}`.trim()
                                  return (
                                    <button key={courseIdx} className="course-btn">
                                      {courseCode}
                                    </button>
                                  )
                                })}
                              </div>
                            </div>
                          ))}
                          {sortedTerms.length === 0 && (
                            <div style={{ padding: '2rem', textAlign: 'center', color: 'var(--text-secondary)' }}>
                              No courses planned
                            </div>
                          )}
                        </div>
                      )
                    })()
                  ) : (
                    <div style={{ padding: '2rem', textAlign: 'center', color: 'var(--text-secondary)' }}>
                      No courses planned
                    </div>
                  )}
                </div>
              </div>
            </div>
          </>
        ) : (
          <>
            {/* Admin/Registrar Dashboard */}
            <div className="left-column">
              {/* System Alerts - Only show if there are actual pending overrides */}
              {pendingOverrides > 0 && (
                <div className="alert-box">
                  <div className="alert-icon"></div>
                  <div className="alert-text">
                    <span className="alert-title">System notice:</span> {pendingOverrides} pending override{pendingOverrides > 1 ? 's' : ''} require{pendingOverrides === 1 ? 's' : ''} review.
                  </div>
                </div>
              )}

              {/* Quick Stats - Real Data */}
              <div className="stats-container">
                <div className="stat-card">
                  <div className="stat-label">Total Users</div>
                  <div className="stat-value">{userStats?.totalUsers ?? 0}</div>
                </div>
                <div className="stat-card">
                  <div className="stat-label">Active Users</div>
                  <div className="stat-value">{userStats?.activeUsers ?? 0}</div>
                </div>
                <div className="stat-card">
                  <div className="stat-label">Students</div>
                  <div className="stat-value">{userStats?.students ?? 0}</div>
                </div>
                <div className="stat-card">
                  <div className="stat-label">Instructors</div>
                  <div className="stat-value">{userStats?.instructors ?? 0}</div>
                </div>
                <div className="stat-card">
                  <div className="stat-label">Advisors</div>
                  <div className="stat-value">{userStats?.advisors ?? 0}</div>
                </div>
                <div className="stat-card">
                  <div className="stat-label">Registrars</div>
                  <div className="stat-value">{userStats?.registrars ?? 0}</div>
                </div>
                <div className="stat-card">
                  <div className="stat-label">Pending Overrides</div>
                  <div className="stat-value">{pendingOverrides > 0 ? pendingOverrides : 0}</div>
                </div>
              </div>

              {/* Database Status */}
              {dbStats && (
                <div className="progress-section">
                  <div className="progress-header">
                    <div className="progress-label">Database Status</div>
                    <div className="progress-text">{dbStats.status === 'OK' ? 'OK' : 'ERROR'}</div>
                  </div>
                  <div className="progress-bar">
                    <div className="progress-fill" style={{ width: dbStats.status === 'OK' ? '100%' : '50%' }}></div>
                  </div>
                  <div style={{ marginTop: '0.5rem', fontSize: '0.9rem', color: 'var(--text-secondary)' }}>
                    {dbStats.totalTerms ?? 0} terms • {dbStats.totalCourses ?? 0} courses
                  </div>
                </div>
              )}

              {/* Course Statistics */}
              {scrapingStats && (
                <div className="progress-section">
                  <div className="progress-header">
                    <div className="progress-label">Course Data</div>
                    <div className="progress-text">{scrapingStats.totalCourses ?? 0}</div>
                  </div>
                  <div className="progress-bar">
                    <div className="progress-fill" style={{ width: `${((scrapingStats.scrapedTerms ?? 0) / Math.max(scrapingStats.totalTerms ?? 1, 1)) * 100}%` }}></div>
                  </div>
                  <div style={{ marginTop: '0.5rem', fontSize: '0.9rem', color: 'var(--text-secondary)' }}>
                    {scrapingStats.scrapedTerms ?? 0} / {scrapingStats.totalTerms ?? 0} terms scraped
                  </div>
                </div>
              )}

              {/* Quick Actions - Replaced Quick Links */}
              <div className="reminder-card">
                <div className="reminder-title">Quick Actions</div>
                <div className="reminder-content">
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                    <button
                      className="course-btn"
                      onClick={() => navigate('/user-management')}
                      style={{ width: '100%', textAlign: 'left', padding: '0.75rem 1rem' }}
                    >
                      Manage Users
                    </button>
                    <button
                      className="course-btn"
                      onClick={() => navigate('/data-import')}
                      style={{ width: '100%', textAlign: 'left', padding: '0.75rem 1rem' }}
                    >
                      Import Data
                    </button>
                    <button
                      className="course-btn"
                      onClick={() => navigate('/degree-requirements')}
                      style={{ width: '100%', textAlign: 'left', padding: '0.75rem 1rem' }}
                    >
                      View Degree Requirements
                    </button>
                    <button
                      className="course-btn"
                      onClick={() => navigate('/pdf-upload')}
                      style={{ width: '100%', textAlign: 'left', padding: '0.75rem 1rem' }}
                    >
                      Manage Classes & Capacities
                    </button>
                  </div>
                </div>
              </div>
            </div>

            {/* Right column: Recent items */}
            <div className="right-column">
              <div className="schedule-section">
                <div className="section-header">
                  <h2>Recent Overrides</h2>
                  <h3>Last 7 days</h3>
                </div>
                <table className="schedule-table">
                  <thead>
                    <tr>
                      <th className="time-header">Student</th>
                      <th className="day-header">Type</th>
                      <th className="day-header">Course</th>
                      <th className="day-header">Status</th>
                      <th className="day-header">Date</th>
                    </tr>
                  </thead>
                  <tbody>
                    {recentOverrides.length === 0 ? (
                      <tr>
                        <td colSpan={5} className="course-cell" style={{ textAlign: 'center', color: '#666' }}>
                          No recent override requests
                        </td>
                      </tr>
                    ) : (
                      recentOverrides.map((waiver: Waiver) => (
                        <tr key={waiver.waiverId}>
                          <td className="time-cell">{waiver.student?.studentId || waiver.studentId || 'N/A'}</td>
                          <td className="course-cell">Time Conflict</td>
                          <td className="course-cell">
                            {waiver.enrolledClass?.department}{waiver.enrolledClass?.courseNum} / {waiver.conflictingClass?.department}{waiver.conflictingClass?.courseNum}
                          </td>
                          <td className="course-cell">
                            <span style={{
                              padding: '4px 8px',
                              borderRadius: '4px',
                              fontSize: '0.875rem',
                              fontWeight: '500',
                              backgroundColor: waiver.status === 'APPROVED' ? '#d1fae5' :
                                waiver.status === 'REJECTED' ? '#fee2e2' : '#fef3c7',
                              color: waiver.status === 'APPROVED' ? '#065f46' :
                                waiver.status === 'REJECTED' ? '#991b1b' : '#92400e'
                            }}>
                              {waiver.status || 'PENDING'}
                            </span>
                          </td>
                          <td className="course-cell">
                            {waiver.createdAt ? new Date(waiver.createdAt).toLocaleDateString() : 'N/A'}
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>

              <div className="planner-section">
                <div className="section-header">
                  <h2>Recent Imports</h2>
                </div>
                <div className="planner-container">
                  <div className="planner-columns">
                    <div className="planner-column">
                      <div className="planner-semester">Classes</div>
                      <div className="course-buttons">
                        {recentImports.classes.length > 0 ? (
                          recentImports.classes.map((item, idx) => (
                            <button key={idx} className="course-btn">
                              {item.term} • {item.courseCount} courses
                            </button>
                          ))
                        ) : (
                          <div style={{ padding: '1rem', textAlign: 'center', color: 'var(--text-secondary)' }}>
                            No class imports
                          </div>
                        )}
                      </div>
                    </div>
                    <div className="planner-column">
                      <div className="planner-semester">Degree Reqs</div>
                      <div className="course-buttons">
                        {recentImports.degreeReqs.length > 0 ? (
                          recentImports.degreeReqs.map((item, idx) => (
                            <button key={idx} className="course-btn">
                              {item.subject} • v{item.version}
                            </button>
                          ))
                        ) : (
                          <div style={{ padding: '1rem', textAlign: 'center', color: 'var(--text-secondary)' }}>
                            No degree requirements
                          </div>
                        )}
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </>
        )}
      </div >
    </div >
  )
}


export default Dashboard