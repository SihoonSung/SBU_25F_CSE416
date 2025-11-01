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
 * 
 * Note: Google OAuth2 integration and authentication flow were implemented 
 * with assistance from Cursor AI and ChatGPT for proper OAuth handling 
 * and secure authentication patterns.
 */
function Dashboard() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const [isLoading, setIsLoading] = useState(false)
  const [me, setMe] = useState<Me>(null)

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

  return (
    <div className="dashboard">
      {/* Header */}
      <header className="dashboard-header">
        <h1>Welcome back{me ? `, ${me.fullName}` : ''}</h1>
      </header>

      {/* Main Content */}
      <div className="dashboard-content">
        {!isRegistrar ? (
        <>
        {/* Left Column */}
        <div className="left-column">
          {/* Alert Message */}
          <div className="alert-box">
            <div className="alert-icon">ℹ</div>
            <div className="alert-text">
              <span className="alert-title">Time conflict:</span> CSE101 and CSE114 overlap. Please resolve before submitting.
            </div>
          </div>

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
            {/* System Alerts */}
            <div className="alert-box">
              <div className="alert-icon">ℹ</div>
              <div className="alert-text">
                <span className="alert-title">System notice:</span> 3 pending overrides require review.
              </div>
            </div>

            {/* Quick Stats */}
            <div className="stats-container">
              <div className="stat-card">
                <div className="stat-label">Total Users</div>
                <div className="stat-value">1,245</div>
              </div>
              <div className="stat-card">
                <div className="stat-label">Classes (Active)</div>
                <div className="stat-value">832</div>
              </div>
              <div className="stat-card">
                <div className="stat-label">Pending Overrides</div>
                <div className="stat-value">3</div>
              </div>
            </div>

            {/* Imports / Health */}
            <div className="progress-section">
              <div className="progress-header">
                <div className="progress-label">Data Health</div>
                <div className="progress-text">OK</div>
              </div>
              <div className="progress-bar">
                <div className="progress-fill" style={{ width: '92%' }}></div>
              </div>
            </div>

            <div className="reminder-card">
              <div className="reminder-title">Quick Links</div>
              <div className="reminder-content">
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
                  <a className="course-btn" href="/course-registration">Registration Mgmt</a>
                  <a className="course-btn" href="/user-management">User Mgmt</a>
                  <a className="course-btn" href="/course-catalog-management">Course Catalog</a>
                  <a className="course-btn" href="/degree-requirements-import">Req Import</a>
                  <a className="course-btn" href="/degree-requirements">Degree Reqs</a>
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