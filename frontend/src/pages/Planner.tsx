/*
 * TP: Updated Planner.tsx
 * - Fix: Manual Add now supports "Force Add" by specifying a semester.
 * - Usage: "CODE|CREDITS|SEMESTER YEAR" (e.g., "CSE 373|3|Spring 2026")
 * - Retains previous validations and UI updates.
 */

import { useMemo, useState, useEffect } from 'react'
import './Planner.css'

// --- Types ---

type Course = { 
  id?: number;
  department: string;
  courseNum: number;
  code: string; 
  title: string; 
  credits: number;
  tags?: string[]; 
  isNonRequired?: boolean;    
  prereqWarnings?: string[];  
}

type PlannerCourseDto = {
  id: number | null;
  department: string;
  courseNum: number;
  title: string;
  semester: string;
  year: number;
  credits: number;
  sbcs: string[];
  unsatisfiedPrerequisites: string[];
}

type AutoPlannerResponse = {
  proposedPlan: PlannerCourseDto[];
  warnings: string[];
  totalCredits: number;
  nonRequiredCourses: string[]; 
  degreeRequirementForecast: any; 
}

type ValidationReport = {
  unsatisfiedPrerequisites: Array<{
    course: string;
    courseName: string;
    semester: string;
    year: number;
    status: string;
    isPlanned?: boolean;
    missingPrerequisites?: string[];
    missingCorequisites?: string[];
    violatedAntirequisites?: string[];
  }>;
  degreeRequirementStatus: any[]; 
  nonRequiredClasses: Array<{
    course: string;
    courseName?: string;
    semester?: string;
    year?: number;
    credits?: number;
    reason: string;
    status?: string;
  }>;
}

// Helper for manual parsing
type ManualCourseInput = {
    code: string;
    credits: number;
    term?: string; // Optional forced term
}

const BASE = import.meta.env.VITE_BACKEND_URL || "http://localhost:8080";

export default function Planner() {
  // --- State ---
  const [userId, setUserId] = useState<number | null>(null);
  const [currentCredits, setCurrentCredits] = useState<number>(0); 
  const [busy, setBusy] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  const [targetGraduation, setTargetGraduation] = useState({
    semester: 'Spring', 
    year: '2029',       
  })

  const TOTAL_CREDITS_REQUIRED = 120; 
  const AVG_CREDITS_PER_SEM = 15;     

  const [options, setOptions] = useState({
    preferredCourses: '',
    avoidCourses: '',
    sbcPrefs: [
      { area: 'ARTS', score: 3 },
      { area: 'GLO', score: 3 },
      { area: 'LANG', score: 3 },
      { area: 'SBS', score: 3 },
      { area: 'TECH', score: 3 },
      { area: 'USA', score: 3 },
      { area: 'WRT', score: 3 },
    ] as Array<{area:string; score:number}>,
    daysToPrefer: 'None',
    modality: 'Any',
    preferredTimes: [] as Array<{day:string; time:string}>,
    preferDays: [] as string[],
    preferMorning: false,
    onlineOnly: false,
  })

  const [manualCourses, setManualCourses] = useState<string>('')
  const [plan, setPlan] = useState<{ [term: string]: Course[] }>({})
  const [warnings, setWarnings] = useState<string[]>([]);
  
  const [degreeStatus, setDegreeStatus] = useState<any>(null); 
  const [validationResult, setValidationResult] = useState<ValidationReport | null>(null);
  
  // Workload limits for each semester
  const [workloadLimits, setWorkloadLimits] = useState<{ [term: string]: number }>({});

  // --- Initialization ---
  useEffect(() => {
    const init = async () => {
      try {
        const profileRes = await fetch(`${BASE}/api/profile/me`, { credentials: 'include' });
        if (!profileRes.ok) throw new Error("Please log in first.");
        const profile = await profileRes.json();
        
        const uid = profile.id; 
        const sid = profile.studentId; 
        setUserId(uid);

        const gpaRes = await fetch(`${BASE}/api/profile/student-id/${sid}/gpa`, { credentials: 'include' });
        if(gpaRes.ok) {
            const gpaData = await gpaRes.json();
            const credits = gpaData.cumulative?.credits || 0;
            setCurrentCredits(credits); 
            calculateAndSetTarget(credits);
        } else {
            calculateAndSetTarget(0);
        }

        await fetchCurrentPlan(uid);

      } catch (e) {
        console.error("Initialization failed:", e);
      }
    };

    init();
  }, []);

  const fetchCurrentPlan = async (uid: number) => {
    try {
        const res = await fetch(`${BASE}/api/planner/${uid}`, { credentials: 'include' });
        if (!res.ok) throw new Error("Failed to fetch current plan.");

        const data = await res.json();

        const newPlan: { [term: string]: Course[] } = {};
        
        if (data.plannedCourses && data.plannedCourses.length > 0) {
            data.plannedCourses.forEach((d: PlannerCourseDto) => {
                const termKey = `${d.semester} ${d.year}`;
                if (!newPlan[termKey]) newPlan[termKey] = [];
                
                const courseCode = `${d.department} ${d.courseNum}`;
                newPlan[termKey].push({
                    id: d.id || undefined,
                    department: d.department,
                    courseNum: d.courseNum,
                    code: courseCode,
                    title: d.title,
                    credits: d.credits,
                    tags: d.sbcs,
                    isNonRequired: false, 
                    prereqWarnings: d.unsatisfiedPrerequisites,
                });
            });
        }
        setPlan(newPlan);
        
        if (data.degreeRequirementForecast) {
            setDegreeStatus(data.degreeRequirementForecast);
        }
        
    } catch (e) {
        console.error("Failed to load plan:", e);
    }
  }

  const calculateAndSetTarget = (credits: number) => {
    const creditsNeeded = Math.max(0, TOTAL_CREDITS_REQUIRED - credits);
    const semestersNeeded = Math.ceil(creditsNeeded / AVG_CREDITS_PER_SEM);

    const now = new Date();
    const currentYear = now.getFullYear();
    const currentMonth = now.getMonth(); 

    let nextSem = currentMonth < 8 ? "Fall" : "Spring";
    let nextYear = currentMonth < 8 ? currentYear : currentYear + 1;

    let finalSem = nextSem;
    let finalYear = nextYear;
    
    for (let i = 1; i < semestersNeeded; i++) {
        if (finalSem === "Spring") {
            finalSem = "Fall";
        } else {
            finalSem = "Spring";
            finalYear += 1;
        }
    }
    if (semestersNeeded <= 0) finalYear += 1; 

    setTargetGraduation({ semester: finalSem, year: finalYear.toString() });
  }

  // Generate list of all semesters from now until target graduation
  const generateSemesterList = (targetSem: string, targetYear: number): string[] => {
    const semesters: string[] = [];
    const now = new Date();
    const currentMonth = now.getMonth();
    let y = now.getFullYear();
    
    if (currentMonth >= 7) { 
        y += 1; 
    }

    while (y <= targetYear) {
      const semOrder = ['Spring', 'Fall'];
      for (const s of semOrder) {
        if (y < now.getFullYear()) continue;
        if (y === now.getFullYear() && currentMonth >= 7 && s === 'Spring') continue;
        if (y > targetYear) break;
        
        if (y === targetYear) {
            if (s === 'Spring' && targetSem === 'Spring') {
                semesters.push(`${s} ${y}`);
                break;
            } else if (s === 'Fall' && targetSem === 'Fall') {
                semesters.push(`${s} ${y}`);
                break;
            } else if (s === 'Spring' && targetSem === 'Fall') {
                semesters.push(`${s} ${y}`);
            }
        } else {
            semesters.push(`${s} ${y}`);
        }
      }
      y++;
    }
    return semesters;
  }

  // Initialize workload limits when target graduation changes
  useEffect(() => {
    const targetYear = parseInt(targetGraduation.year);
    const semesterList = generateSemesterList(targetGraduation.semester, targetYear);
    
    setWorkloadLimits(prev => {
      const newLimits: { [term: string]: number } = {};
      semesterList.forEach(term => {
        // Keep existing value if it exists, otherwise default to 17
        newLimits[term] = prev[term] || 17;
      });
      return newLimits;
    });
  }, [targetGraduation.semester, targetGraduation.year]);

  const totalByTerm = useMemo(() =>
      Object.fromEntries(
        Object.entries(plan).map(([t, arr]) => [t, arr.reduce((s, c) => s + c.credits, 0)])
      ), [plan]
  )

  // ✅ Updated: Handles "Code|Credits|Term" format
  const parseManual = (text: string): ManualCourseInput[] => {
    return text.split(',').map(s => s.trim()).filter(Boolean).map(line => {
        const parts = line.split('|').map(x => x?.trim())
        const code = (parts[0] || '').replace(/\s+/g, ' ')
        const credits = Number(parts[1] || 3) || 3
        const term = parts[2] || undefined; // e.g. "Spring 2026"
        
        return { code, credits, term }
      })
  }

  const generateWorkloadLimits = (targetSem: string, targetYear: number) => {
    const limits: { [key: string]: number } = {};
    const now = new Date();
    const currentMonth = now.getMonth();
    let y = now.getFullYear();
    
    if (currentMonth >= 7) { 
        y += 1; 
    }

    const safetyLimitYear = targetYear + 5; 

    while (y <= safetyLimitYear) {
      const semesters = ['Spring', 'Fall'];
      for (const s of semesters) {
        if (y < now.getFullYear()) continue;
        if (y === now.getFullYear() && currentMonth >= 7 && s === 'Spring') continue;
        if (y > targetYear) return limits;

        if (y === targetYear) {
            if (s === 'Spring') {
                limits[`${s} ${y}`] = 17;
                if (targetSem === 'Spring') return limits; 
            }
            else if (s === 'Fall') {
                if (targetSem === 'Fall') {
                    limits[`${s} ${y}`] = 17;
                    return limits; 
                }
            }
        } else {
            limits[`${s} ${y}`] = 17;
        }
      }
      y++;
    }
    return limits;
  }

  // --- Auto Generate Logic ---
  const handleGenerate = async () => {
    if (!userId) { alert('Please log in first.'); return; }

    setBusy(true);
    setErrorMsg(null);
    setSuccessMsg(null);
    setWarnings([]);
    setDegreeStatus(null);
    setValidationResult(null);

    try {
      const avoidList = options.avoidCourses.split(',').map(s => s.trim()).filter(Boolean);
      const prefList = options.preferredCourses.split(',').map(s => s.trim()).filter(Boolean);
      
      // Parse manual courses
      const manualInputList = parseManual(manualCourses);
      // Only send code to backend as preference
      const manualCodes = manualInputList.map(m => m.code); 
      const combinedPref = [...new Set([...prefList, ...manualCodes])];

      // Use user-defined workload limits if available, otherwise generate defaults
      const workloads = Object.keys(workloadLimits).length > 0 
        ? workloadLimits 
        : generateWorkloadLimits(targetGraduation.semester, parseInt(targetGraduation.year));

      const payload = {
        graduationSemester: targetGraduation.semester,
        graduationYear: parseInt(targetGraduation.year),
        workloadLimits: workloads,
        avoidCourses: avoidList,
        preferredCourses: combinedPref,
      };

      const res = await fetch(`${BASE}/api/planner/${userId}/auto-generate`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify(payload),
      });

      if (!res.ok) {
        const errText = await res.text();
        throw new Error('Failed to generate plan: ' + errText);
      }

      const data: AutoPlannerResponse = await res.json();
      const newPlan: { [term: string]: Course[] } = {};
      const nonRequiredSet = new Set(data.nonRequiredCourses || []);

      // 1. Process Backend Plan
      if (data.proposedPlan && data.proposedPlan.length > 0) {
        data.proposedPlan.forEach(d => {
          const termKey = `${d.semester} ${d.year}`;
          if (!newPlan[termKey]) newPlan[termKey] = [];
          
          const courseCode = `${d.department} ${d.courseNum}`;
          newPlan[termKey].push({
            id: d.id || undefined,
            department: d.department,
            courseNum: d.courseNum,
            code: courseCode,
            title: d.title,
            credits: d.credits,
            tags: d.sbcs,
            isNonRequired: nonRequiredSet.has(courseCode),
            prereqWarnings: d.unsatisfiedPrerequisites,
          });
        });
      }

      // 2. ✅ Force Add Manual Courses (if specified with term)
      manualInputList.forEach(m => {
        if (m.term) {
            const termKey = m.term; // e.g., "Spring 2026"
            if (!newPlan[termKey]) newPlan[termKey] = [];
            
            // Check duplicates
            const exists = newPlan[termKey].some(c => c.code === m.code);
            if (!exists) {
                const parts = m.code.split(' ');
                newPlan[termKey].push({
                    department: parts[0] || 'MANUAL',
                    courseNum: parseInt(parts[1]) || 0,
                    code: m.code,
                    title: m.code,
                    credits: m.credits,
                    tags: ['Force-Added'],
                    isNonRequired: true, // Assume non-required until validated
                    prereqWarnings: []
                });
            }
        }
      });

      if (Object.keys(newPlan).length === 0 && data.totalCredits === 0) {
          setWarnings(prev => [...prev, 'No courses were planned.']);
      }

      setPlan(newPlan);

      if (data.degreeRequirementForecast) {
        setDegreeStatus(data.degreeRequirementForecast);
      }

      if (data.warnings && data.warnings.length > 0) {
        setWarnings(prev => [...prev, ...data.warnings]);
      }
      setSuccessMsg('Plan generated successfully!');

    } catch (e: any) {
      console.error('Generate Error:', e);
      setErrorMsg(e.message);
    } finally {
      setBusy(false);
    }
  }

  // --- Save Plan ---
  const handleConfirm = async () => {
    if (!userId) return;
    if (Object.keys(plan).length === 0) { alert('No plan to save.'); return; }

    setBusy(true);
    setSuccessMsg(null);
    setErrorMsg(null);

    try {
      const allCourses: PlannerCourseDto[] = [];
      Object.entries(plan).forEach(([termKey, courses]) => {
        const parts = termKey.split(' ');
        if (parts.length < 2) return;
        const semester = parts[0];
        const year = parseInt(parts[1]);

        courses.forEach(c => {
          allCourses.push({
            id: c.id || null,
            department: c.department,
            courseNum: c.courseNum,
            title: c.title,
            semester: semester,
            year: year,
            credits: c.credits,
            sbcs: c.tags || [],
            unsatisfiedPrerequisites: [], 
          });
        });
      });

      const res = await fetch(`${BASE}/api/planner/${userId}/confirm`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify(allCourses),
      });

      if (!res.ok) {
        const errText = await res.text();
        throw new Error(`Failed to save plan: ${errText}`);
      }

      await fetchCurrentPlan(userId); 
      setSuccessMsg('Plan saved successfully!');
      setWarnings([]);
      setValidationResult(null);

    } catch (e: any) {
      setErrorMsg(e.message);
    } finally {
      setBusy(false);
    }
  }

  // --- Validate Plan ---
  const handleValidate = async () => {
    if (!userId) return;

    setBusy(true);
    setValidationResult(null);
    setErrorMsg(null);
    setWarnings([]);

    try {
      const res = await fetch(`${BASE}/api/planner/${userId}/validate`, {
        method: 'GET',
        credentials: 'include',
      });

      if (!res.ok) {
        const errText = await res.text();
        throw new Error(`Validation failed: ${res.status} ${errText}`);
      }

      const data: { success: boolean, validation: ValidationReport } = await res.json();

      if (data.success && data.validation) {
        setValidationResult(data.validation);

        if (data.validation.degreeRequirementStatus) {
          setDegreeStatus({ declaredMajors: data.validation.degreeRequirementStatus }); 
        }

        const nonRequiredSet = new Set(data.validation.nonRequiredClasses.map(i => i.course));
        
        const warningMap = new Map<string, string[]>();
        data.validation.unsatisfiedPrerequisites.forEach(issue => {
            const key = `${issue.course}|${issue.semester}|${issue.year}`; 
            const msgs = [];
            if(issue.missingPrerequisites) msgs.push(...issue.missingPrerequisites);
            if(issue.missingCorequisites) msgs.push(...issue.missingCorequisites);
            if(issue.violatedAntirequisites) msgs.push(...issue.violatedAntirequisites);
            warningMap.set(key, msgs);
        });

        setPlan(prevPlan => {
            const nextPlan = { ...prevPlan };
            Object.keys(nextPlan).forEach(termKey => {
                const [sem, yrStr] = termKey.split(' ');
                const yr = parseInt(yrStr);
                
                nextPlan[termKey] = nextPlan[termKey].map(c => {
                    const uniqueKey = `${c.code}|${sem}|${yr}`;
                    const warnings = warningMap.get(uniqueKey);
                    
                    return {
                        ...c,
                        isNonRequired: nonRequiredSet.has(c.code),
                        prereqWarnings: warnings
                    };
                });
            });
            return nextPlan;
        });

        setSuccessMsg('Plan validation complete!');

      } else {
        throw new Error('Unknown validation response');
      }
    } catch (e: any) {
      console.error('Validate Error:', e);
      setErrorMsg('Validation Error: ' + e.message);
    } finally {
      setBusy(false);
    }
  }

  const removeCourse = (term: string, courseCode: string) => {
    setPlan(prev => {
      const newPlan = { ...prev };
      if (newPlan[term]) {
        newPlan[term] = newPlan[term].filter(c => c.code !== courseCode);
      }
      return newPlan;
    });
  }

  // --- Render Helpers ---
  const renderDegreeStatusDetails = (statusData: any) => {
    const majors = Array.isArray(statusData) ? statusData : statusData.declaredMajors || [];

    if (!majors || majors.length === 0) {
      return <div className="validation-empty">No detailed degree status available.</div>;
    }

    return (
      <div className="degree-status-container">
        {majors.map((major: any, idx: number) => (
          <div key={idx} className="major-status-card">
            <div className="major-header">
              <h4 className="major-title">
                {major.requirementName || `${major.subjectCode} ${major.degreeType}`}
              </h4>
              <span className={`status-badge ${major.overallStatus === 'SATISFIED' ? 'status-satisfied' : 'status-pending'}`}>
                {major.overallStatus || 'PENDING'}
              </span>
            </div>
            
            <div className="major-summary">
              <div className="summary-item">
                <span className="summary-label">Credits:</span>
                <span className="summary-value">
                  {major.totalCreditsEarned} / {major.totalCreditsRequired}
                </span>
              </div>
              {major.gpaRequirement && (
                <div className="summary-item">
                  <span className="summary-label">GPA:</span>
                  <span className="summary-value">
                    {major.currentGPA?.toFixed(2)} / {major.gpaRequirement}
                  </span>
                </div>
              )}
            </div>

            {major.courseRequirements?.groups && (
              <div className="course-requirements-section">
                <h5 className="section-title">Course Requirements</h5>
                <div className="req-group-list">
                  {major.courseRequirements.groups.map((group: any) => (
                    <div key={group.groupId} className={`req-item ${group.status.toLowerCase().includes('completed') ? 'req-satisfied' : 'req-unsatisfied'}`}>
                      <div className="req-header">
                        <span className="req-type">{group.groupType}</span>
                        <span className="req-id">({group.groupId})</span>
                        <span className={`req-status ${group.status.toLowerCase().includes('completed') ? 'status-completed' : 'status-pending'}`}>
                          {group.status}
                        </span>
                      </div>
                      {group.missingCourses && group.missingCourses.length > 0 && (
                        <div className="missing-list">
                          <span className="missing-label">Missing:</span>
                          <span className="missing-courses">{group.missingCourses.join(', ')}</span>
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              </div>
            )}
            
            {major.sbcRequirements?.sbcCategories && (
              <div className="sbc-section">
                <h5 className="section-title">Related SBCs</h5>
                <div className="sbc-grid-display">
                  {major.sbcRequirements.sbcCategories.map((sbc: any) => (
                    <div key={sbc.category} className={`sbc-item ${sbc.met ? 'sbc-met' : 'sbc-unmet'}`}>
                      <span className="sbc-name">{sbc.category}</span>
                      <span className="sbc-icon">{sbc.met ? '✅' : '❌'}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        ))}
      </div>
    );
  }

  const renderValidationDetails = (validation: ValidationReport) => {
    return (
      <div className="validation-details-container">
        {validation.nonRequiredClasses.length > 0 && (
          <div className="validation-section non-required-section">
            <div className="section-header">
              <span className="section-icon">⚠️</span>
              <h4 className="section-title">Non-Required Courses</h4>
              <span className="section-count">{validation.nonRequiredClasses.length}</span>
            </div>
            <p className="section-description">These courses do not contribute to satisfying prerequisites or degree requirements (only count toward minimum graduation credits)</p>
            <div className="item-list">
              {validation.nonRequiredClasses.map((nr, i) => (
                <div key={i} className="validation-item">
                  <span className="item-course">{nr.course}</span>
                  {nr.semester && nr.year && (
                    <span className="item-term">{nr.semester} {nr.year}</span>
                  )}
                  <span className="item-reason">{nr.reason}</span>
                </div>
              ))}
            </div>
          </div>
        )}

        {validation.unsatisfiedPrerequisites.length > 0 && (
          <div className="validation-section prerequisites-section">
            <div className="section-header">
              <span className="section-icon">❌</span>
              <h4 className="section-title">Unsatisfied Prerequisites</h4>
              <span className="section-count">{validation.unsatisfiedPrerequisites.length}</span>
            </div>
            <div className="item-list">
              {validation.unsatisfiedPrerequisites.map((up, i) => (
                <div key={i} className="validation-item prereq-item">
                  <div className="prereq-header">
                    <span className="item-course">{up.course}</span>
                    <span className="item-term">{up.semester} {up.year}</span>
                  </div>
                  {up.missingPrerequisites && up.missingPrerequisites.length > 0 && (
                    <div className="prereq-details">
                      <span className="prereq-label">Missing Prerequisites:</span>
                      <span className="prereq-courses">{up.missingPrerequisites.join(', ')}</span>
                    </div>
                  )}
                  {up.missingCorequisites && up.missingCorequisites.length > 0 && (
                    <div className="prereq-details">
                      <span className="prereq-label">Missing Corequisites:</span>
                      <span className="prereq-courses">{up.missingCorequisites.join(', ')}</span>
                    </div>
                  )}
                  {up.violatedAntirequisites && up.violatedAntirequisites.length > 0 && (
                    <div className="prereq-details">
                      <span className="prereq-label">Violated Antirequisites:</span>
                      <span className="prereq-courses">{up.violatedAntirequisites.join(', ')}</span>
                    </div>
                  )}
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    );
  }

  // --- UI Components ---
  const Pill = ({ term, c }: { term: string; c: Course }) => (
    <div
      key={c.code + term}
      className={`pill ${c.isNonRequired ? 'non-required' : ''} ${c.prereqWarnings?.length ? 'has-warning' : ''}`}
      title={c.isNonRequired ? 'Elective / Non-Required' : ''}
    >
      {c.prereqWarnings && c.prereqWarnings.length > 0 && (
        <div className="pill-warning-icon" title={`Issues:\n${c.prereqWarnings.join('\n')}`}>
          ⚠️
        </div>
      )}
      <div className="pill-code">{c.code}</div>
      <div className="pill-credits">{c.credits} cr</div>
      <button onClick={() => removeCourse(term, c.code)} className="pill-remove">×</button>
    </div>
  )

  const col = (term: string) => (
    <div className="term-col" key={term}>
      <div className="term-header">{term}</div>
      <div className="pill-list">
        {plan[term]?.map(c => <Pill key={c.code} term={term} c={c} />)}
      </div>
      <div className="term-total">Total: {totalByTerm[term] || 0}</div>
    </div>
  )

  const updateSbcScore = (i: number, v: number) => {
    setOptions(o => ({
      ...o,
      sbcPrefs: o.sbcPrefs.map((p, idx) =>
        idx === i ? { ...p, score: v } : p
      ),
    }))
  }

  // --- Main Render ---
  return (
    <div className="planner-page">
      <div className="generator-panel">
        <h2>Plan Generator</h2>
        
        {successMsg && <div className="msg success">{successMsg}</div>}
        {errorMsg && <div className="msg error">{errorMsg}</div>}
        {warnings.length > 0 && (
            <div className="msg warning">
                <strong>Warnings:</strong>
                <ul>{warnings.map((w,i) => <li key={i}>{w}</li>)}</ul>
            </div>
        )}

        <div className="card">
          <div className="card-title">Target Graduation</div>
          <div className="grid-2">
            <label>
              <span>Semester</span>
              <select value={targetGraduation.semester} onChange={e => setTargetGraduation({...targetGraduation, semester: e.target.value})}>
                <option>Spring</option><option>Fall</option>
              </select>
            </label>
            <label>
              <span>Year</span>
              <input value={targetGraduation.year} onChange={e => setTargetGraduation({...targetGraduation, year: e.target.value})} />
            </label>
          </div>
        </div>

        <div className="card">
          <div className="card-title">Semester Max Credits</div>
          <p style={{fontSize:'0.85em', color:'#666', marginBottom:'10px'}}>
            Set maximum credits for each semester until graduation
          </p>
          <div style={{maxHeight: '300px', overflowY: 'auto', border: '1px solid #ddd', borderRadius: '4px', padding: '10px'}}>
            <table style={{width: '100%', borderCollapse: 'collapse'}}>
              <thead>
                <tr style={{borderBottom: '2px solid #ddd'}}>
                  <th style={{textAlign: 'left', padding: '8px', fontWeight: 'bold'}}>Semester</th>
                  <th style={{textAlign: 'left', padding: '8px', fontWeight: 'bold'}}>Max Credits</th>
                </tr>
              </thead>
              <tbody>
                {generateSemesterList(targetGraduation.semester, parseInt(targetGraduation.year)).map(term => (
                  <tr key={term} style={{borderBottom: '1px solid #eee'}}>
                    <td style={{padding: '8px'}}>{term}</td>
                    <td style={{padding: '8px'}}>
                      <input
                        type="number"
                        min="1"
                        max="24"
                        value={workloadLimits[term] || 17}
                        onChange={e => {
                          const value = parseInt(e.target.value) || 17;
                          setWorkloadLimits(prev => ({
                            ...prev,
                            [term]: value
                          }));
                        }}
                        style={{
                          width: '80px',
                          padding: '4px 8px',
                          border: '1px solid #ccc',
                          borderRadius: '4px'
                        }}
                      />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        <div className="card">
            <div className="card-title">Preferences</div>
            <label>
                <span>Preferred Courses</span>
                <input placeholder="CSE 320, AMS 301" value={options.preferredCourses} onChange={e => setOptions({...options, preferredCourses: e.target.value})} />
            </label>
            <label>
                <span>Avoid Courses</span>
                <input placeholder="CSE 353" value={options.avoidCourses} onChange={e => setOptions({...options, avoidCourses: e.target.value})} />
            </label>
            <div className="subsection">
                <div className="sub-title">SBC Priorities (1-5)</div>
                <div className="sbc-grid">
                    {options.sbcPrefs.map((p, i) => (
                        <div key={p.area} className="sbc-row">
                            <span>{p.area}</span>
                            <select value={p.score} onChange={e => updateSbcScore(i, Number(e.target.value))}>
                                {[1,2,3,4,5].map(n => <option key={n} value={n}>{n}</option>)}
                            </select>
                        </div>
                    ))}
                </div>
            </div>
        </div>

        <div className="card">
            <div className="card-title">Manual Add</div>
            <p style={{fontSize:'0.8em', color:'#666', marginBottom:'5px'}}>
                Format: <code>CODE|CREDITS</code> (auto-place) or <code>CODE|CREDITS|TERM YEAR</code> (force-add)
            </p>
            <textarea placeholder="e.g., CSE 373|3|Spring 2026, CSE 327|3" value={manualCourses} onChange={e => setManualCourses(e.target.value)} />
            <button className="apply-btn" onClick={handleGenerate} disabled={busy || !userId}>
                {busy ? 'Generating...' : 'Generate Plan'}
            </button>
        </div>
      </div>

      <div className="plan-panel">
        <div className="panel-header">
            <h2>Generated Plan</h2>
            <div className="action-buttons">
                {Object.keys(plan).length > 0 && (
                    <>
                        <button className="btn-validate" onClick={handleValidate} disabled={busy}>Validate</button>
                        <button className="btn-save" onClick={handleConfirm} disabled={busy}>Save</button>
                    </>
                )}
            </div>
        </div>

        {(degreeStatus || validationResult) && (
            <div className="report-panel">
                {degreeStatus && (
                    <div className="validation-section degree-status-section">
                        <h3 className="panel-title">Degree Requirement Status</h3>
                        {renderDegreeStatusDetails(degreeStatus)}
                    </div>
                )}
                
                {validationResult && (
                    <div className="validation-section validation-details-section">
                        <h3 className="panel-title">Validation Details</h3>
                        {renderValidationDetails(validationResult)}
                    </div>
                )}
            </div>
        )}

        <div className="term-grid">
            {Object.keys(plan).sort((a,b) => {
                const [sA, yA] = a.split(' '); const [sB, yB] = b.split(' ');
                if (yA !== yB) return parseInt(yA) - parseInt(yB);
                const order: any = {Spring: 1, Summer: 2, Fall: 3};
                return (order[sA] || 9) - (order[sB] || 9);
            }).map(col)}
        </div>
      </div>
    </div>
  )
}