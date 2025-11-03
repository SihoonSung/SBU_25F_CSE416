import { useEffect, useState } from 'react'
import './DegreeAudit.css'
import { getCurrentUser } from '../utils/auth'

const backendBase = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8080'

type GpaSummary = {
  terms: Array<{ term: string; gpa: number | null; credits: number }>
  cumulative: { gpa: number | null; credits: number }
  standing: string
}

type RequirementStatus = {
  area: "ADMISSION" | "DEGREE"
  programCode: string
  programName: string
  code: string
  title: string
  status: "satisfied" | "pending" | "unsatisfied"
  usingClasses?: Array<{ subject: string; courseNum: number; term?: string; grade?: string | null }>
  note?: string | null
}

export default function DegreeAudit() {
  const [studentName, setStudentName] = useState<string>('')
  const [studentId, setStudentId] = useState<string | null>(null)
  const [gpa, setGpa] = useState<number | null>(null)
  const [totalCredits, setTotalCredits] = useState<number>(0)
  const [catalogTerm, setCatalogTerm] = useState<string>('Spring 2025')
  const [progressPct, setProgressPct] = useState<number>(0)
  const [loading, setLoading] = useState<boolean>(true)
  const [sbcRequirements, setSbcRequirements] = useState<RequirementStatus[]>([])
  const [majorRequirements, setMajorRequirements] = useState<RequirementStatus[]>([])

  useEffect(() => {
    (async () => {
      try {
        setLoading(true)
        const me = await getCurrentUser()
        const u = me?.user
        
        setStudentName(u?.fullName || '')
        const sid = u?.studentId || u?.sbuId || null
        setStudentId(sid)

        // Get userId for fetching classes
        const userId = u?.userId || null

        if (sid && userId) {
          // Calculate GPA directly from completed classes
          try {
            const completedClassesResponse = await fetch(`${backendBase}/api/users/${userId}/classes/completed`, {
              credentials: 'include'
            })
            
            if (completedClassesResponse.ok) {
              const completedClasses: Array<{ credits: number; grade: string | null }> = await completedClassesResponse.json()
              
              // Grade to GPA points mapping
              const gradePoints: Record<string, number> = {
                'A+': 4.0, 'A': 4.0, 'A-': 3.7,
                'B+': 3.3, 'B': 3.0, 'B-': 2.7,
                'C+': 2.3, 'C': 2.0, 'C-': 1.7,
                'D+': 1.3, 'D': 1.0, 'D-': 0.7,
                'F': 0.0, 'W': 0.0, 'I': 0.0,
                'P': 0.0, 'NP': 0.0, 'S': 0.0, 'U': 0.0
              }
              
              // Filter classes with valid grades (exclude pass/fail and incomplete)
              const gradedClasses = completedClasses.filter(cls => {
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
              
              // Calculate GPA: (sum of credits × grade points) / total credits
              let totalPoints = 0
              let totalCredits = 0
              
              gradedClasses.forEach(cls => {
                const grade = cls.grade?.trim().toUpperCase() || ''
                const points = gradePoints[grade] || 0
                const credits = cls.credits || 0
                
                totalPoints += points * credits
                totalCredits += credits
              })
              
              // Calculate GPA
              const calculatedGpa = totalCredits > 0 ? totalPoints / totalCredits : null
              setGpa(calculatedGpa)
              
              // Set total credits (include all completed classes)
              const allCredits = completedClasses.reduce((sum, cls) => sum + (cls.credits || 0), 0)
              setTotalCredits(allCredits)
              
              console.log(`[DegreeAudit] Calculated GPA: ${calculatedGpa?.toFixed(2)}, Total Credits: ${allCredits}`)
            }
          } catch (error) {
            console.error('Error calculating GPA:', error)
          }

          // Fetch requirement status using DegreeRequirementStatusService (dynamic calculation)
          try {
            const requirementStatusResponse = await fetch(`${backendBase}/api/degree-requirements-status/student/${userId}`, {
              credentials: 'include'
            })
            
            console.log(`[DegreeAudit] Requirement status API response status: ${requirementStatusResponse.status}`)
            
            if (requirementStatusResponse.ok) {
              const statusData: any = await requirementStatusResponse.json()
              console.log(`[DegreeAudit] Received requirement status data:`, statusData)
              
              // Transform the response to RequirementStatus format
              const transformedReqs: RequirementStatus[] = []
              const sbcReqs: RequirementStatus[] = []
              
              // Process declared majors - each group is a requirement group
              if (statusData.declaredMajors && Array.isArray(statusData.declaredMajors)) {
                for (const major of statusData.declaredMajors) {
                  const subjectCode = major.subjectCode || ''
                  const degreeType = major.degreeType || ''
                  const programName = `${subjectCode} ${degreeType}`.trim()
                  
                  // Fetch SBC requirements from the major's DegreeRequirement
                  try {
                    const majorReqResponse = await fetch(`${backendBase}/api/degree-requirements/${encodeURIComponent(subjectCode)}/${encodeURIComponent(degreeType)}`, {
                      credentials: 'include'
                    })
                    if (majorReqResponse.ok) {
                      const majorReqData: any = await majorReqResponse.json()
                      if (majorReqData.success && majorReqData.requirement && majorReqData.requirement.sbcRequirements) {
                        try {
                          const sbcRequirementsJson = typeof majorReqData.requirement.sbcRequirements === 'string' 
                            ? JSON.parse(majorReqData.requirement.sbcRequirements)
                            : majorReqData.requirement.sbcRequirements
                          
                          // Process SBC requirements - they can be an array or object with categories
                          // SBC structure from YAML: { sbc: "ARTS" } or { sbc: "SNW", required_major_courses: [...] }
                          if (Array.isArray(sbcRequirementsJson)) {
                            for (const sbcItem of sbcRequirementsJson) {
                              // SBC code is stored in the 'sbc' field
                              const sbcCode = sbcItem.sbc || ''
                              if (sbcCode) {
                                // Map SBC codes to full names for better display
                                const sbcNames: Record<string, string> = {
                                  'ARTS': 'Arts',
                                  'GLO': 'Global Issues',
                                  'HUM': 'Humanities',
                                  'LANG': 'Language',
                                  'USA': 'United States History',
                                  'WRT': 'Writing & Communication',
                                  'HFA+': 'Humanities & Fine Arts Plus',
                                  'CER': 'Community Engagement & Reflection',
                                  'DIV': 'Diversity',
                                  'QPS': 'Quantitative & Problem Solving',
                                  'SNW': 'Natural Sciences & Technology',
                                  'SBS': 'Social & Behavioral Sciences',
                                  'SBS+': 'Social & Behavioral Sciences Plus',
                                  'STAS': 'Statistics & Data Analysis',
                                  'TECH': 'Technology & Innovation',
                                  'EXP+': 'Experience Plus',
                                  'STEM+': 'Science, Technology, Engineering & Math Plus',
                                  'ESI': 'Environmental Sustainability',
                                  'SPK': 'Speaking',
                                  'WRTD': 'Writing in the Discipline'
                                }
                                
                                sbcReqs.push({
                                  area: 'ADMISSION',
                                  programCode: 'SBC',
                                  programName: 'SBC Requirements',
                                  code: sbcCode,
                                  title: sbcNames[sbcCode] || sbcCode,
                                  status: 'pending', // Will be calculated based on student's courses
                                  usingClasses: [],
                                  note: sbcItem.description || (sbcItem.required_major_courses || sbcItem.optional_major_courses ? 'May be satisfied by major courses' : null)
                                })
                              }
                            }
                          } else if (sbcRequirementsJson && typeof sbcRequirementsJson === 'object') {
                            // Handle object format with categories
                            if (sbcRequirementsJson.categories && Array.isArray(sbcRequirementsJson.categories)) {
                              for (const sbcItem of sbcRequirementsJson.categories) {
                                const sbcCode = sbcItem.sbc || ''
                                if (sbcCode) {
                                  const sbcNames: Record<string, string> = {
                                    'ARTS': 'Arts',
                                    'GLO': 'Global Issues',
                                    'HUM': 'Humanities',
                                    'LANG': 'Language',
                                    'USA': 'United States History',
                                    'WRT': 'Writing & Communication',
                                    'HFA+': 'Humanities & Fine Arts Plus',
                                    'CER': 'Community Engagement & Reflection',
                                    'DIV': 'Diversity',
                                    'QPS': 'Quantitative & Problem Solving',
                                    'SNW': 'Natural Sciences & Technology',
                                    'SBS': 'Social & Behavioral Sciences',
                                    'SBS+': 'Social & Behavioral Sciences Plus',
                                    'STAS': 'Statistics & Data Analysis',
                                    'TECH': 'Technology & Innovation',
                                    'EXP+': 'Experience Plus',
                                    'STEM+': 'Science, Technology, Engineering & Math Plus',
                                    'ESI': 'Environmental Sustainability',
                                    'SPK': 'Speaking',
                                    'WRTD': 'Writing in the Discipline'
                                  }
                                  
                                  sbcReqs.push({
                                    area: 'ADMISSION',
                                    programCode: 'SBC',
                                    programName: 'SBC Requirements',
                                    code: sbcCode,
                                    title: sbcNames[sbcCode] || sbcCode,
                                    status: 'pending',
                                    usingClasses: [],
                                    note: sbcItem.description || (sbcItem.required_major_courses || sbcItem.optional_major_courses ? 'May be satisfied by major courses' : null)
                                  })
                                }
                              }
                            }
                          }
                          console.log(`[DegreeAudit] Found ${sbcReqs.length} SBC requirements from ${subjectCode} ${degreeType}`)
                        } catch (e) {
                          console.error('[DegreeAudit] Error parsing SBC requirements:', e)
                        }
                      }
                    }
                  } catch (e) {
                    console.error(`[DegreeAudit] Error fetching SBC requirements for ${subjectCode} ${degreeType}:`, e)
                  }
                  
                  if (major.groups && Array.isArray(major.groups)) {
                    for (const group of major.groups) {
                      const groupId = group.groupId || group.id || ''
                      const groupType = group.groupType || group.type || ''
                      const status = group.status === 'SATISFIED' ? 'satisfied' :
                                   group.status === 'NOT_SATISFIED' ? 'unsatisfied' : 'pending'
                      
                      // Get satisfied courses from the group
                      const satisfiedCourses = group.satisfiedCourses || []
                      const usingClasses = satisfiedCourses.map((course: any) => ({
                        subject: course.subject || course.department || '',
                        courseNum: course.courseNum || course.course_num || 0,
                        term: course.term || course.termCode || null,
                        grade: course.grade || null
                      }))
                      
                      // Create a requirement entry for this group
                      transformedReqs.push({
                        area: 'DEGREE',
                        programCode: subjectCode,
                        programName: programName,
                        code: groupId ? `${subjectCode}-${groupId}` : `${subjectCode}-${groupType}`,
                        title: groupType ? `${groupType.charAt(0).toUpperCase() + groupType.slice(1)} Group (${programName})` : `Requirement Group (${programName})`,
                        status: status,
                        usingClasses: usingClasses,
                        note: group.message || (group.creditsRequired ? `Credits: ${group.creditsSatisfied || 0}/${group.creditsRequired}` : null)
                      })
                    }
                  }
                  
                  // Also check if there are individual requirements in the major
                  if (major.overallStatus) {
                    transformedReqs.push({
                      area: 'DEGREE',
                      programCode: subjectCode,
                      programName: programName,
                      code: `${subjectCode}-OVERALL`,
                      title: `Overall ${programName} Requirements`,
                      status: major.overallStatus === 'SATISFIED' ? 'satisfied' :
                             major.overallStatus === 'IN_PROGRESS' ? 'pending' : 'unsatisfied',
                      usingClasses: [],
                      note: `Progress: ${major.satisfiedGroups || 0}/${major.totalGroups || 0} groups satisfied`
                    })
                  }
                }
              }
              
              // Process declared minors similarly
              if (statusData.declaredMinors && Array.isArray(statusData.declaredMinors)) {
                for (const minor of statusData.declaredMinors) {
                  const subjectCode = minor.subjectCode || ''
                  const programName = `${subjectCode} MINOR`.trim()
                  
                  if (minor.groups && Array.isArray(minor.groups)) {
                    for (const group of minor.groups) {
                      const groupId = group.groupId || group.id || ''
                      const groupType = group.groupType || group.type || ''
                      const status = group.status === 'SATISFIED' ? 'satisfied' :
                                   group.status === 'NOT_SATISFIED' ? 'unsatisfied' : 'pending'
                      
                      const satisfiedCourses = group.satisfiedCourses || []
                      const usingClasses = satisfiedCourses.map((course: any) => ({
                        subject: course.subject || course.department || '',
                        courseNum: course.courseNum || course.course_num || 0,
                        term: course.term || course.termCode || null,
                        grade: course.grade || null
                      }))
                      
                      transformedReqs.push({
                        area: 'DEGREE',
                        programCode: subjectCode,
                        programName: programName,
                        code: `${subjectCode}-MINOR-${groupId}`,
                        title: `${groupType} Group (${programName})`,
                        status: status,
                        usingClasses: usingClasses,
                        note: group.message || null
                      })
                    }
                  }
                }
              }
              
              setSbcRequirements(sbcReqs)
              setMajorRequirements(transformedReqs)
              
              console.log(`[DegreeAudit] Transformed to ${transformedReqs.length} total requirements (${sbcReqs.length} SBC, ${transformedReqs.length} Major)`)
              
              // Also try to get from old API as fallback
              if (transformedReqs.length === 0) {
                console.log(`[DegreeAudit] No requirements from status API, trying fallback APIs...`)
                
                // Fallback: Try old admission requirements API
                try {
                  const sbcResponse = await fetch(`${backendBase}/api/profile/student-id/${encodeURIComponent(sid)}/requirements/admission`, {
                    credentials: 'include'
                  })
                  if (sbcResponse.ok) {
                    const sbcData: RequirementStatus[] = await sbcResponse.json()
                    console.log(`[DegreeAudit] Fallback - Received ${sbcData.length} admission requirements`)
                    setSbcRequirements(sbcData)
                  }
                } catch (e) {
                  console.error('[DegreeAudit] Fallback admission API error:', e)
                }
                
                // Fallback: Try old degree requirements API
                try {
                  const majorResponse = await fetch(`${backendBase}/api/profile/student-id/${encodeURIComponent(sid)}/requirements/degree`, {
                    credentials: 'include'
                  })
                  if (majorResponse.ok) {
                    const majorData: RequirementStatus[] = await majorResponse.json()
                    console.log(`[DegreeAudit] Fallback - Received ${majorData.length} degree requirements`)
                    setMajorRequirements(majorData)
                  }
                } catch (e) {
                  console.error('[DegreeAudit] Fallback degree API error:', e)
                }
              }
            } else {
              console.error(`[DegreeAudit] Failed to fetch requirement status. Status: ${requirementStatusResponse.status}`)
              
              // Fallback to old APIs
              try {
                const sbcResponse = await fetch(`${backendBase}/api/profile/student-id/${encodeURIComponent(sid)}/requirements/admission`, {
                  credentials: 'include'
                })
                if (sbcResponse.ok) {
                  const sbcData: RequirementStatus[] = await sbcResponse.json()
                  setSbcRequirements(sbcData)
                }
              } catch (e) {
                console.error('[DegreeAudit] Error fetching admission requirements:', e)
              }
              
              try {
                const majorResponse = await fetch(`${backendBase}/api/profile/student-id/${encodeURIComponent(sid)}/requirements/degree`, {
                  credentials: 'include'
                })
                if (majorResponse.ok) {
                  const majorData: RequirementStatus[] = await majorResponse.json()
                  setMajorRequirements(majorData)
                }
              } catch (e) {
                console.error('[DegreeAudit] Error fetching degree requirements:', e)
              }
            }
          } catch (error) {
            console.error('[DegreeAudit] Error fetching requirement status:', error)
            
            // Fallback to old APIs on error
            try {
              const sbcResponse = await fetch(`${backendBase}/api/profile/student-id/${encodeURIComponent(sid)}/requirements/admission`, {
                credentials: 'include'
              })
              if (sbcResponse.ok) {
                const sbcData: RequirementStatus[] = await sbcResponse.json()
                setSbcRequirements(sbcData)
              }
            } catch (e) {
              console.error('[DegreeAudit] Error fetching admission requirements:', e)
            }
            
            try {
              const majorResponse = await fetch(`${backendBase}/api/profile/student-id/${encodeURIComponent(sid)}/requirements/degree`, {
                credentials: 'include'
              })
              if (majorResponse.ok) {
                const majorData: RequirementStatus[] = await majorResponse.json()
                setMajorRequirements(majorData)
              }
            } catch (e) {
              console.error('[DegreeAudit] Error fetching degree requirements:', e)
            }
          }

          // Calculate overall progress from current state
          const allCurrentReqs = [...sbcRequirements, ...majorRequirements]
          if (allCurrentReqs.length > 0) {
            const satisfied = allCurrentReqs.filter(req => req.status === 'satisfied').length
            const progress = Math.round((satisfied / allCurrentReqs.length) * 100)
            setProgressPct(progress)
            console.log(`[DegreeAudit] Progress: ${satisfied}/${allCurrentReqs.length} satisfied = ${progress}%`)
          } else {
            // If no requirements found, check if we can get progress from requirement status API
            try {
              const statusResponse = await fetch(`${backendBase}/api/degree-requirements-status/student/${userId}`, {
                credentials: 'include'
              })
              if (statusResponse.ok) {
                const statusData: any = await statusResponse.json()
                // Try to calculate progress from declared majors
                if (statusData.declaredMajors && Array.isArray(statusData.declaredMajors)) {
                  let totalGroups = 0
                  let satisfiedGroups = 0
                  for (const major of statusData.declaredMajors) {
                    if (major.groups && Array.isArray(major.groups)) {
                      totalGroups += major.groups.length
                      satisfiedGroups += major.groups.filter((g: any) => g.status === 'SATISFIED').length
                    }
                  }
                  if (totalGroups > 0) {
                    const progress = Math.round((satisfiedGroups / totalGroups) * 100)
                    setProgressPct(progress)
                    console.log(`[DegreeAudit] Progress from status API: ${satisfiedGroups}/${totalGroups} groups = ${progress}%`)
                  }
                }
              }
            } catch (error) {
              console.error('[DegreeAudit] Error calculating progress from status API:', error)
            }
          }
        }

        // Get current term for catalog term
        try {
          const termsResponse = await fetch(`${backendBase}/api/classes/terms`, {
            credentials: 'include'
          })
          if (termsResponse.ok) {
            const terms = await termsResponse.json()
            if (Array.isArray(terms) && terms.length > 0) {
              // Get the most recent term
              const latest = terms[0]
              const semester = latest.semester ? 
                (latest.semester.charAt(0).toUpperCase() + latest.semester.slice(1).toLowerCase()) : 
                'Spring'
              const year = latest.year || 2025
              setCatalogTerm(`${semester} ${year}`)
            }
          }
        } catch (error) {
          console.error('Error fetching terms:', error)
        }
      } catch (error) {
        console.error('Error loading degree audit data:', error)
      } finally {
        setLoading(false)
      }
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
          <div className="stat-value">{gpa !== null ? gpa.toFixed(2) : '—'}</div>
        </div>
        <div className="stat-card">
          <div className="stat-label">Cumulative Credits</div>
          <div className="stat-value">{totalCredits || '—'}</div>
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
        {loading ? (
          <div className="placeholder">Loading...</div>
        ) : sbcRequirements.length === 0 ? (
          <div className="placeholder">No SBC requirements found.</div>
        ) : (
          <div className="requirements-list">
            {sbcRequirements.map((req, idx) => (
              <div key={`sbc-${idx}`} className={`requirement-item requirement-${req.status}`}>
                <div className="requirement-header">
                  <span className="requirement-code">{req.code}</span>
                  <span className="requirement-title">{req.title}</span>
                  <span className={`status-badge status-${req.status}`}>
                    {req.status === 'satisfied' ? '✓ Satisfied' : 
                     req.status === 'pending' ? '⏳ Pending' : 
                     '✗ Unsatisfied'}
                  </span>
                </div>
                {req.usingClasses && req.usingClasses.length > 0 && (
                  <div className="requirement-classes">
                    Using: {req.usingClasses.map(c => `${c.subject}${c.courseNum}`).join(', ')}
                  </div>
                )}
                {req.note && <div className="requirement-note">{req.note}</div>}
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="section-card">
        <div className="section-title">Major Requirements</div>
        {loading ? (
          <div className="placeholder">Loading...</div>
        ) : majorRequirements.length === 0 ? (
          <div className="placeholder">No major requirements found.</div>
        ) : (
          <div className="requirements-list">
            {majorRequirements.map((req, idx) => (
              <div key={`major-${idx}`} className={`requirement-item requirement-${req.status}`}>
                <div className="requirement-header">
                  <span className="requirement-code">{req.code}</span>
                  <span className="requirement-title">{req.title}</span>
                  <span className={`status-badge status-${req.status}`}>
                    {req.status === 'satisfied' ? '✓ Satisfied' : 
                     req.status === 'pending' ? '⏳ Pending' : 
                     '✗ Unsatisfied'}
                  </span>
                </div>
                {req.usingClasses && req.usingClasses.length > 0 && (
                  <div className="requirement-classes">
                    Using: {req.usingClasses.map(c => `${c.subject}${c.courseNum}`).join(', ')}
                  </div>
                )}
                {req.note && <div className="requirement-note">{req.note}</div>}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}



