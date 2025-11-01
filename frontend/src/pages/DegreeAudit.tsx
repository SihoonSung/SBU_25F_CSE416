import { useEffect, useState } from 'react'
import './DegreeAudit.css'
import { getCurrentUser } from '../utils/auth'

export default function DegreeAudit() {
  const [studentName, setStudentName] = useState<string>('')
  const [gpa, setGpa] = useState<number>(0)
  const [totalCredits, setTotalCredits] = useState<number>(0)
  const [catalogTerm, setCatalogTerm] = useState<string>('Spring 2025')
  const [progressPct, setProgressPct] = useState<number>(85)

  useEffect(() => {
    (async () => {
      try {
        const me = await getCurrentUser()
        const u = me?.user
        setStudentName(u?.fullName || '')
        if (u?.gpa) setGpa(u.gpa)
        // TODO: 나중에 백엔드 연결 시 실제 총이수학점/카탈로그 텀 연동
      } catch {}
    })()
  }, [])

  return (
    <div className="degree-audit-page">
      <h2>Degree Audit</h2>

      <div className="stats-row">
        <div className="stat-card">
          <div className="stat-label">Student Name</div>
          <div className="stat-value">{studentName || '—'}</div>
        </div>
        <div className="stat-card">
          <div className="stat-label">GPA</div>
          <div className="stat-value">{gpa?.toFixed(2)}</div>
        </div>
        <div className="stat-card">
          <div className="stat-label">Cumulative Credits</div>
          <div className="stat-value">{totalCredits || 118}</div>
        </div>
        <div className="stat-card">
          <div className="stat-label">Catalog Term</div>
          <div className="stat-value">{catalogTerm}</div>
        </div>
      </div>

      <div className="progress-card">
        <div className="progress-label">Overall Progress</div>
        <div className="progress-bar"><div className="fill" style={{ width: `${progressPct}%` }} /></div>
        <div className="progress-pct">{progressPct}%</div>
      </div>

      <div className="section-card">
        <div className="section-title">SBC Requirements</div>
        <div className="placeholder">SBC 영역 충족 현황이 여기에 표시됩니다. (추후 연결)</div>
      </div>

      <div className="section-card">
        <div className="section-title">Major Requirements: Computer Science</div>
        <div className="placeholder">전공 요건 충족 현황이 여기에 표시됩니다. (추후 연결)</div>
      </div>
    </div>
  )
}



