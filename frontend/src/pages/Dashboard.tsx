import React, { useEffect, useState } from 'react'
import { useSearchParams, useNavigate } from 'react-router-dom'
import './Dashboard.css'
import { signIn } from '../utils/auth'

/*
 * TP: Used Cursor to make it consistent with the codebase.
 */

function Dashboard() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const [isLoading, setIsLoading] = useState(false)
  const backendBase = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8080'

  // OAuth 성공 콜백 처리
  useEffect(() => {
    const handleOAuthSuccess = async () => {
      const success = searchParams.get('success')
      
      if (success === 'true') {
        setIsLoading(true)
        try {
          // OAuth 인증 완료 후 사용자 정보 가져오기
          const response = await fetch(`${backendBase}/api/auth/success`, {
            method: 'GET',
            credentials: 'include',
            headers: {
              'Content-Type': 'application/json',
            },
            mode: 'cors',
          })
          
          if (response.ok) {
            const data = await response.json()
            if (data.success && data.user) {
              // 사용자 정보를 localStorage에 저장
              signIn('oauth-token', data.user.email)
              
              // URL에서 success 파라미터 제거
              navigate('/home', { replace: true })
            }
          } else {
            console.error('Failed to get user info')
          }
        } catch (error) {
          console.error('Error during OAuth callback:', error)
        } finally {
          setIsLoading(false)
        }
      }
    }

    handleOAuthSuccess()
  }, [searchParams, navigate, backendBase])
  if (isLoading) {
    return (
      <div className="dashboard">
        <div className="loading-container">
          <div className="loading-spinner"></div>
          <p>로그인 중...</p>
        </div>
      </div>
    )
  }

  return (
    <div className="dashboard">
      {/* Header */}
      <header className="dashboard-header">
        <h1>Welcome back, Sihoon</h1>
      </header>

      {/* Main Content */}
      <div className="dashboard-content">
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
              <div className="progress-fill" style={{width: '85%'}}></div>
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
            <div className="schedule-table">
              <div className="schedule-header">
                <div className="time-header">Time</div>
                <div className="day-header">Mon</div>
                <div className="day-header">Tue</div>
                <div className="day-header">Wed</div>
                <div className="day-header">Thu</div>
                <div className="day-header">Fri</div>
              </div>
              <div className="schedule-body">
                <div className="schedule-row">
                  <div className="time-cell">9</div>
                  <div className="course-cell"></div>
                  <div className="course-cell cse373" rowSpan="2">CSE373<br/>FREY HALL 317</div>
                  <div className="course-cell cse312" rowSpan="2">CSE312<br/>COMPUTER SCI 312B</div>
                  <div className="course-cell cse300" rowSpan="2">CSE300<br/>FREY HALL 328</div>
                  <div className="course-cell cse355" rowSpan="2">CSE355<br/>ONLINE</div>
                </div>
                <div className="schedule-row">
                  <div className="time-cell">10</div>
                  <div className="course-cell"></div>
                  <div className="course-cell"></div>
                  <div className="course-cell"></div>
                  <div className="course-cell"></div>
                </div>
                <div className="schedule-row">
                  <div className="time-cell">11</div>
                  <div className="course-cell"></div>
                  <div className="course-cell"></div>
                  <div className="course-cell ams310">AMS310<br/>HUMANITIES 1006</div>
                  <div className="course-cell"></div>
                </div>
                <div className="schedule-row">
                  <div className="time-cell">12</div>
                  <div className="course-cell"></div>
                  <div className="course-cell"></div>
                  <div className="course-cell"></div>
                  <div className="course-cell"></div>
                </div>
                <div className="schedule-row">
                  <div className="time-cell">1</div>
                  <div className="course-cell"></div>
                  <div className="course-cell"></div>
                  <div className="course-cell"></div>
                  <div className="course-cell"></div>
                </div>
                <div className="schedule-row">
                  <div className="time-cell">2</div>
                  <div className="course-cell"></div>
                  <div className="course-cell cse416">CSE416<br/>FREY HALL 317</div>
                  <div className="course-cell"></div>
                  <div className="course-cell"></div>
                </div>
              </div>
            </div>
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
      </div>
    </div>
  )
}

export default Dashboard