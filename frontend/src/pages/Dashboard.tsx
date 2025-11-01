import React, { useEffect, useState } from 'react'
import { useSearchParams, useNavigate } from 'react-router-dom'
import './Dashboard.css'

type Me = {
  userId: number
  email: string
  fullName: string
  firstName?: string
  lastName?: string
  role?: string
  profilePictureUrl?: string
  isActive?: boolean
} | null

/**
 * Dashboard component with Google OAuth2 integration.
 * Handles OAuth callback processing and displays user dashboard.
 */
function Dashboard() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const [isLoading, setIsLoading] = useState(false)
  const [me, setMe] = useState<Me>(null)
  const [registrationEligibility, setRegistrationEligibility] = useState<any>(null)
  const [currentSemester, setCurrentSemester] = useState<string>('Fall')
  const [currentYear, setCurrentYear] = useState<number>(2025)
  const [timeConflicts, setTimeConflicts] = useState<string[]>([])
  const [pendingOverrides, setPendingOverrides] = useState<number>(0)
  const [userStats, setUserStats] = useState<any>(null)
  const [dbStats, setDbStats] = useState<any>(null)
  const [scrapingStats, setScrapingStats] = useState<any>(null)
  const [teachingClasses, setTeachingClasses] = useState<any[]>([])
  const [classStats, setClassStats] = useState<any[]>([])
  const [pendingGrades, setPendingGrades] = useState<number>(0)
  const [pendingWaivers, setPendingWaivers] = useState<number>(0)

  const backendBase = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8080'

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
          setMe(data.user ?? null)
          
          // Check registration eligibility if user is a student
          if (data.user?.role === 'STUDENT' && data.user?.userId) {
            await checkRegistrationEligibility(data.user.userId)
            await checkTimeConflicts(data.user.userId)
          }
          
          // Fetch professor data if user is an instructor
          if (data.user?.role === 'INSTRUCTOR' && data.user?.firstName && data.user?.lastName) {
            await fetchProfessorData(data.user)
          }
          
          // Always try to fetch stats if user is logged in (same as UserManagement page)
          // This ensures stats are loaded regardless of role
          if (data.user) {
            await fetchAdminStats()
          }
          
          // Check pending overrides if user is admin/registrar
          if ((data.user?.role === 'REGISTRAR' || data.user?.role === 'ADMIN') && data.user?.userId) {
            console.log('User role for admin dashboard:', data.user?.role, 'User ID:', data.user?.userId)
            await checkPendingOverrides()
          } else {
            console.log('User is not registrar/admin. Role:', data.user?.role, 'User ID:', data.user?.userId)
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
      // Silently ignore - registration can proceed without schedule
      console.log('Registration schedule check failed:', error)
    }
  }

  // TP: Used cursor to combine LEC and REC schedules
  const combineSchedules = (course: any) => {
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
    if (single) return { start: (single[1]+single[2]).replace(/\s+/g, ''), end: null as any }
    return { start: s, end: null as any }
  }

  // TP: Used cursor to check if two courses have overlapping schedules
  const isOverlap = (a: any, b: any) => {
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
  const findConflictPairs = (list: any[]) => {
    const pairs: Array<[any, any]> = []
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
      setTimeConflicts([])
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
      
      if (waiversResponse.ok) {
        const waivers = await waiversResponse.json()
        pendingCount += Array.isArray(waivers) ? waivers.length : 0
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
    } catch (error) {
      setPendingOverrides(0)
    }
  }

  /**
   * Fetch professor data (teaching classes, stats, pending items)
   */
  const fetchProfessorData = async (user: any) => {
    try {
      const instructorName = `${user.firstName} ${user.lastName}`.trim()
      
      // Fetch teaching classes
      const classesResponse = await fetch(`${backendBase}/api/classes/instructor/${encodeURIComponent(instructorName)}`, {
        method: 'GET',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' }
      })
      
      if (classesResponse.ok) {
        const classes = await classesResponse.json()
        setTeachingClasses(classes || [])
        
        // Fetch enrollment stats for each class
        const statsPromises = classes.map(async (cls: any) => {
          try {
            const statsResponse = await fetch(`${backendBase}/api/roster/stats/${cls.classId}`, {
              method: 'GET',
              credentials: 'include',
              headers: { 'Content-Type': 'application/json' }
            })
            
            if (statsResponse.ok) {
              return await statsResponse.json()
            }
            return null
          } catch (error) {
            console.error(`Error fetching stats for class ${cls.classId}:`, error)
            return null
          }
        })
        
        const stats = await Promise.all(statsPromises)
        setClassStats(stats.filter((s: any) => s !== null))
        
        // Count students without grades
        let pendingCount = 0
        for (const cls of classes) {
          try {
            const rosterResponse = await fetch(`${backendBase}/api/roster/class/${cls.classId}`, {
              method: 'GET',
              credentials: 'include',
              headers: { 'Content-Type': 'application/json' }
            })
            
            if (rosterResponse.ok) {
              const roster = await rosterResponse.json()
              pendingCount += (roster || []).filter((student: any) => !student.grade && student.status === 'ENROLLED').length
            }
          } catch (error) {
            console.error(`Error fetching roster for class ${cls.classId}:`, error)
          }
        }
        setPendingGrades(pendingCount)
      }
      
      // Fetch pending waivers for instructor
      if (user.userId) {
        try {
          const waiversResponse = await fetch(`${backendBase}/api/waivers/instructor/${user.userId}`, {
            method: 'GET',
            credentials: 'include',
            headers: { 'Content-Type': 'application/json' }
          })
          
          if (waiversResponse.ok) {
            const waivers = await waiversResponse.json()
            setPendingWaivers((waivers || []).length)
          }
        } catch (error) {
          console.error('Error fetching pending waivers:', error)
        }
      }
    } catch (error) {
      console.error('Error fetching professor data:', error)
    }
  }

  /**
   * Fetch admin statistics (for admin/registrar)
   */
  const fetchAdminStats = async () => {
    try {
      // Fetch user statistics (same as UserManagement)
      const userStatsResponse = await fetch(`${backendBase}/api/users/statistics`, {
        method: 'GET',
        credentials: 'include'
      })
      
      if (userStatsResponse.ok) {
        const stats = await userStatsResponse.json()
        console.log('User statistics received:', stats)
        setUserStats(stats)
      } else {
        const errorText = await userStatsResponse.text().catch(() => 'Unknown error')
        console.error('Failed to fetch user statistics:', userStatsResponse.status, errorText)
        
        // If 403 Forbidden, the user might not be recognized as REGISTRAR by backend
        if (userStatsResponse.status === 403) {
          console.warn('Access denied: User may not have REGISTRAR role. Current user:', me)
        }
        
        setUserStats({ 
          totalUsers: 0, 
          activeUsers: 0,
          students: 0,
          instructors: 0,
          advisors: 0,
          registrars: 0
        })
      }

      // Fetch database status
      const dbStatusResponse = await fetch(`${backendBase}/api/scraping/db-status`, {
        method: 'GET',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' }
      })
      
      if (dbStatusResponse.ok) {
        const dbStatus = await dbStatusResponse.json()
        setDbStats(dbStatus)
      }

      const scrapingStatsResponse = await fetch(`${backendBase}/api/scraping/stats`, {
        method: 'GET',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' }
      })
      
      if (scrapingStatsResponse.ok) {
        const stats = await scrapingStatsResponse.json()
        setScrapingStats(stats)
      }
    } catch (error) {
      setUserStats({ 
        totalUsers: 0, 
        activeUsers: 0,
        students: 0,
        instructors: 0,
        advisors: 0,
        registrars: 0
      })
    }
  }

  if (isLoading) {
    return (
      <div className="dashboard">
        <div className="loading-container">
          <div className="loading-spinner"></div>
          <p>Logging in...</p>
        </div>
      </div>
    )
  }

  const role = String(me?.role || '').toUpperCase()
  const isRegistrar = role === 'REGISTRAR' || role === 'ADMIN'
  const isInstructor = role === 'INSTRUCTOR'

  return (
    <div className="dashboard">
      {/* Header */}
      <header className="dashboard-header">
        <h1>Welcome back{me ? `, ${me.fullName}` : ''}</h1>
      </header>

      {/* Main Content */}
      <div className="dashboard-content">
        {isInstructor ? (
        <>
          {/* Professor Dashboard */}
          <div className="left-column">
            {/* Pending Items Alerts */}
            {pendingGrades > 0 && (
              <div className="alert-box alert-warning">
                <div className="alert-icon"></div>
                <div className="alert-text">
                  <span className="alert-title">Grades Pending:</span> You have {pendingGrades} student{pendingGrades > 1 ? 's' : ''} awaiting grade input.
                </div>
              </div>
            )}

            {pendingWaivers > 0 && (
              <div className="alert-box alert-warning">
                <div className="alert-icon"></div>
                <div className="alert-text">
                  <span className="alert-title">Override Requests:</span> {pendingWaivers} pending override request{pendingWaivers > 1 ? 's' : ''} require{pendingWaivers === 1 ? 's' : ''} your review.
                </div>
              </div>
            )}

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
                <div className="stat-value">{pendingGrades}</div>
              </div>
              <div className="stat-card">
                <div className="stat-label">Pending Overrides</div>
                <div className="stat-value">{pendingWaivers}</div>
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
                    {teachingClasses.slice(0, 5).map((cls: any) => {
                      const stats = classStats.find((s: any) => s.classId === cls.classId)
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
                            {cls.semester} {cls.year} • {stats ? `${stats.activeEnrollments || 0}/${stats.maxCapacity || 'N/A'} students` : 'No stats'}
                          </div>
                        </div>
                      )
                    })}
                    {teachingClasses.length > 5 && (
                      <button 
                        className="course-btn" 
                        onClick={() => navigate('/roster')}
                        style={{ width: '100%', textAlign: 'center', padding: '0.75rem 1rem' }}
                      >
                        View All Classes ({teachingClasses.length})
                      </button>
                    )}
                  </div>
                )}
              </div>
            </div>

            {/* Quick Actions */}
            <div className="reminder-card">
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
            </div>
          </div>

          {/* Right Column */}
          <div className="right-column">
            {/* Class Enrollment Overview */}
            <div className="schedule-section">
              <div className="section-header">
                <h2>Class Enrollment Overview</h2>
                <h3>{currentSemester} {currentYear}</h3>
              </div>
              {teachingClasses.length === 0 ? (
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
                    {teachingClasses.map((cls: any) => {
                      const stats = classStats.find((s: any) => s.classId === cls.classId)
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
              )}
            </div>
          </div>
        </>
        ) : !isRegistrar ? (
        <>
        {/* Left Column */}
        <div className="left-column">
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

          {/* Statistics Cards */}
          <div className="stats-container">
            <div className="stat-card">
              <div className="stat-label">GPA</div>
              <div className="stat-value">3.52</div>
            </div>
            <div className="stat-card">
              <div className="stat-label">Term</div>
              <div className="stat-value">3.31</div>
            </div>
            <div className="stat-card">
              <div className="stat-label">Cumulative Credits</div>
              <div className="stat-value">118</div>
            </div>
          </div>

          {/* Overall Progress */}
          <div className="progress-section">
            <div className="progress-header">
              <div className="progress-label">Overall Progress</div>
              <div className="progress-text">85%</div>
            </div>
            <div className="progress-bar">
              <div className="progress-fill" style={{ width: '85%' }}></div>
            </div>
          </div>

          {/* Reminder Card */}
          <div className="reminder-card">
            <div className="reminder-title">Reminder</div>
            <div className="reminder-content">
              <div>3 days left to apply for GPNC.</div>
              <div>72 more days and you're done with this semester!</div>
            </div>
          </div>
        </div>

        {/* Right Column */}
        <div className="right-column">
          {/* Course Schedule - Temporary hardcoded */}
          <div className="schedule-section">
            <div className="section-header">
              <h2>Course Schedule</h2>
              <h3>Spring 2025</h3>
            </div>

            <table className="schedule-table">
              <thead>
                <tr>
                  <th className="time-header">Time</th>
                  <th className="day-header">Mon</th>
                  <th className="day-header">Tue</th>
                  <th className="day-header">Wed</th>
                  <th className="day-header">Thu</th>
                  <th className="day-header">Fri</th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td className="time-cell">9</td>
                  <td className="course-cell"></td>
                  <td className="course-cell cse373" rowSpan={2}>CSE373<br/>FREY HALL 317</td>
                  <td className="course-cell cse312" rowSpan={2}>CSE312<br/>COMPUTER SCI 312B</td>
                  <td className="course-cell cse300" rowSpan={2}>CSE300<br/>FREY HALL 328</td>
                  <td className="course-cell cse355" rowSpan={2}>CSE355<br/>ONLINE</td>
                </tr>
                <tr>
                  <td className="time-cell">10</td>
                  <td className="course-cell"></td>
                </tr>

                <tr>
                  <td className="time-cell">11</td>
                  <td className="course-cell"></td>
                  <td className="course-cell"></td>
                  <td className="course-cell ams310">AMS310<br/>HUMANITIES 1006</td>
                  <td className="course-cell"></td>
                  <td className="course-cell"></td>
                </tr>

                <tr>
                  <td className="time-cell">12</td>
                  <td className="course-cell"></td>
                  <td className="course-cell"></td>
                  <td className="course-cell"></td>
                  <td className="course-cell"></td>
                  <td className="course-cell"></td>
                </tr>

                <tr>
                  <td className="time-cell">1</td>
                  <td className="course-cell"></td>
                  <td className="course-cell"></td>
                  <td className="course-cell"></td>
                  <td className="course-cell"></td>
                  <td className="course-cell"></td>
                </tr>

                <tr>
                  <td className="time-cell">2</td>
                  <td className="course-cell"></td>
                  <td className="course-cell cse416">CSE416<br/>FREY HALL 317</td>
                  <td className="course-cell"></td>
                  <td className="course-cell"></td>
                  <td className="course-cell"></td>
                </tr>
              </tbody>
            </table>
          </div>

          {/* Planner */}
          <div className="planner-section">
            <div className="section-header">
              <h2>Planner</h2>
            </div>
            <div className="planner-container">
              <div className="planner-columns">
                <div className="planner-column">
                  <div className="planner-semester">Spring 2025</div>
                  <div className="course-buttons">
                    <button className="course-btn">AMS 326</button>
                    <button className="course-btn">AMS 373</button>
                    <button className="course-btn">CSE 316</button>
                    <button className="course-btn">CSE 327</button>
                    <button className="course-btn">CSE 478</button>
                    <button className="course-btn">WRT 102</button>
                  </div>
                </div>
                <div className="planner-column">
                  <div className="planner-semester">Fall 2025</div>
                  <div className="course-buttons">
                    <button className="course-btn">AMS 310</button>
                    <button className="course-btn">CSE 300</button>
                    <button className="course-btn">CSE 312</button>
                    <button className="course-btn">CSE 355</button>
                    <button className="course-btn">CSE 373</button>
                    <button className="course-btn">CSE 416</button>
                  </div>
                </div>
                <div className="planner-column">
                  <div className="planner-semester">Spring 2026</div>
                  <div className="course-buttons">
                    <button className="course-btn">CSE 350</button>
                    <button className="course-btn">CSE 390</button>
                    <button className="course-btn">CSE 487</button>
                    <button className="course-btn">KOR 220</button>
                    <button className="course-btn">POL 102</button>
                  </div>
                </div>
              </div>
              <div className="planner-nav">
                <span className="nav-arrow">←</span>
                <span className="nav-arrow">→</span>
              </div>
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
                  <tr>
                    <td className="time-cell">SBU123456</td>
                    <td className="course-cell">Prerequisite</td>
                    <td className="course-cell">CSE 300</td>
                    <td className="course-cell">Pending</td>
                    <td className="course-cell">2025-02-14</td>
                  </tr>
                  <tr>
                    <td className="time-cell">SBU987654</td>
                    <td className="course-cell">Capacity</td>
                    <td className="course-cell">CSE 312</td>
                    <td className="course-cell">Approved</td>
                    <td className="course-cell">2025-02-13</td>
                  </tr>
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
                      <button className="course-btn">Fall 2025 • 520 rows</button>
                      <button className="course-btn">Spring 2025 • 498 rows</button>
                    </div>
                  </div>
                  <div className="planner-column">
                    <div className="planner-semester">Degree Reqs</div>
                    <div className="course-buttons">
                      <button className="course-btn">CSE • v2025</button>
                      <button className="course-btn">AMS • v2025</button>
                    </div>
                  </div>
                  <div className="planner-column">
                    <div className="planner-semester">SBC</div>
                    <div className="course-buttons">
                      <button className="course-btn">Catalog v2025</button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </>
        )}
      </div>
    </div>
  )
}

export default Dashboard