import { useEffect, useMemo, useState } from "react";

/**
 * Profile Page Component
 * Comprehensive student profile management with academic information display.
 */

/** ===== Types ===== */
type Role = "STUDENT" | "ADVISOR" | "REGISTRAR" | "ADMIN";

type CurrentUserInfo = { netId: string; roles: Role[] };

/** Generic person (non-student compatible) - modified */
type PersonBasic = {
  sbuId?: string | null;
  firstName?: string | null;
  lastName?: string | null;
  email?: string | null;
  role?: "student" | "advisor" | "registrar" | "instructor" | "unknown";
  
  // Advisor-specific fields
  unitLevel?: "university" | "college" | "department" | null; 
  academicUnit?: string | null; 
  
  // Additional fields from users table (Instructor/General fields)
  department?: string | null;
  officeHours?: string | null;
  officeLocation?: string | null;

  // Student/General fields
  major?: string | null; 
  gpa?: number | null; 
  graduationYear?: number | null; 
  profilePictureUrl?: string | null;
  
  // Additional fields for status display
  classStanding?: string | null; // Class Standing (e.g., U1, U2...)
};

/** Student basic + details (as before) */
type StudentProfile = {
  id: number;
  studentId: string;
  name: string;
  major: string;
  email: string;
  status: string;
  entryYear: string;
};

type TermRef = { semester: "Fall" | "Spring" | "SummerI" | "SummerII"; year: number };

type FullStudentDetails = {
  sbuId: string;
  firstName: string;
  lastName: string;
  email: string;
  universityEntry: TermRef | null;
  directAdmit: string | null;
  aoi: string | null;
  college: string | null;
  majors: string[];
  degrees: string[];
  majorRequirementVersions: TermRef[];
  minors: string[];
  minorRequirementVersions: TermRef[];
  plannedMajors: string[];
  plannedMajorRequirementVersions: TermRef[];
  plannedDegrees: string[];
  plannedMinors: string[];
  plannedMinorRequirementVersions: TermRef[];
  graduation: TermRef | null;
};

type AcademicEvent = { date: string; title: string; type: string };

type GpaTerm = { term: string; gpa: number | null; credits: number };
type GpaSummary = {
  terms: GpaTerm[];
  cumulative: { gpa: number | null; credits: number };
  standing: string;
};

type ScheduleClass = {
  classId: number;
  subject: string;
  courseNum: number;
  section: string;
  title: string;
  schedule: string;
  location: string;
  instructor?: string;
  credits?: number | null;
};

type PlannedProgram = {
  kind: "MAJOR" | "MINOR";
  code: string;
  name: string;
  declared: boolean;
  version?: string;
};

type RequirementStatus = {
  area: "ADMISSION" | "DEGREE";
  programCode: string;
  programName: string;
  code: string;
  title: string;
  status: "satisfied" | "pending" | "unsatisfied";
  usingClasses?: Array<{ subject: string; courseNum: number; term?: string; grade?: string | null }>;
  note?: string | null;
};

type Hold = { 
  holdId?: number;
  code?: string; 
  title?: string; 
  holdType?: string;
  placedAt: string; 
  placedBy?: string | { userId: number; name: string };
  reason?: string;
};
type TimeConflictWaiver = { classA: string; classB: string; term: string; waivedBy: string; waivedAt: string };
type AuditLogEntry = { id: number; timestamp: string; actorRole: "student" | "advisor" | "registrar" | "system"; actorDisplay: string; action: string; details?: string };

type TransferCourse = { originalClass: string; originalUniversity: string; credits: number; subject: string | null; courseNum: number | null; grade: string | null };
type HistoricClass = { classId: number; subject: string; courseNum: number; section: number | string; semester: TermRef["semester"]; year: number; credits: number; gpnc: boolean; grade: string | null };
type PlannedCourse = { subject: string; courseNum: number; semester: TermRef["semester"]; year: number; credits: number };

/** ===== Config ===== */
const BASE = import.meta.env.VITE_BACKEND_URL || "http://localhost:8080";

/** ===== GPA Scale (front-end) ===== */
const GRADE_TO_POINTS: Record<string, number> = {
  "A": 4.00, "A-": 3.67,
  "B+": 3.33, "B": 3.00, "B-": 2.67,
  "C+": 2.33, "C": 2.00, "C-": 1.67,
  "D+": 1.33, "D": 1.00,
  "F": 0.00,
  "Q": 0.00 // Withdraw/Drop – excluded from GPA calc below anyway
};
const EXCLUDE_GRADES = new Set(["P", "NP", "S", "U", "W", "INC", "I", "AU", "R", "NR", "NA", "Q"]);

/** ===== Helpers for GPA from classHistory ===== */
function termKey(year: number, semester: TermRef["semester"]) {
  return `${year}-${semester}`;
}
function semesterOrder(sem: string) {
  // Sort within the same year
  const order: Record<string, number> = { "Spring": 1, "SummerI": 2, "SummerII": 3, "Fall": 4 };
  return order[sem] ?? 0;
}
function computeGpaFromHistory(history: HistoricClass[]): GpaSummary {
  // Aggregate by term
  const perTerm: Record<string, { qpts: number; cr: number }> = {};
  let cumQpts = 0;
  let cumCr = 0;

  for (const c of history || []) {
    const grade = (c.grade || "").trim().toUpperCase();
    const credits = Number(c.credits) || 0;

    // Include only courses with credits > 0, with letter grades we map, not GPNC, and not excluded codes.
    if (credits <= 0) continue;
    if (c.gpnc) continue;
    if (!grade || EXCLUDE_GRADES.has(grade)) continue;

    // Normalize odd forms like "A " or "B+ "
    const mapped = GRADE_TO_POINTS[grade] ?? null;
    if (mapped == null) continue;

    const tk = termKey(c.year, c.semester);
    if (!perTerm[tk]) perTerm[tk] = { qpts: 0, cr: 0 };
    perTerm[tk].qpts += mapped * credits;
    perTerm[tk].cr += credits;

    cumQpts += mapped * credits;
    cumCr += credits;
  }

  // Build terms array, sorted by recency (year desc, semester order desc)
  const terms: GpaTerm[] = Object.entries(perTerm)
    .map(([k, v]) => {
      const [y, s] = k.split("-");
      const gpa = v.cr > 0 ? v.qpts / v.cr : null;
      return { term: `${s} ${y}`, gpa, credits: v.cr };
    })
    .sort((a, b) => {
      // parse "Fall 2025"
      const [sa, ya] = a.term.split(" ");
      const [sb, yb] = b.term.split(" ");
      const yaN = parseInt(ya, 10);
      const ybN = parseInt(yb, 10);
      if (yaN !== ybN) return ybN - yaN;
      return semesterOrder(sb) - semesterOrder(sa);
    });

  const cumulative = {
    gpa: cumCr > 0 ? cumQpts / cumCr : null,
    credits: cumCr
  };

  // Simple standing rule (customize if you want)
  const standing =
    cumulative.gpa == null ? "-" :
    cumulative.gpa >= 2.0 ? "Good Standing" : "Probation";

  return { terms, cumulative, standing };
}

/** ===== Component ===== */
export default function ProfilePage() {
  /** ===== Common fetch ===== */
  const safeFetch = useMemo(
    () => async (input: RequestInfo, init?: RequestInit) => {
      const res = await fetch(input, { credentials: "include", ...init });
      const text = await res.text();
      const data = text ? JSON.parse(text) : null;
      if (!res.ok) {
        const e = (data && (data.message || data.error)) || `HTTP ${res.status}`;
        throw new Error(e);
      }
      return data;
    },
    []
  );

  /** ===== Who am I ===== */
  const [me, setMe] = useState<CurrentUserInfo | null>(null);
  const [myPerson, setMyPerson] = useState<PersonBasic | null>(null); // non-student compatible info (from YAML/DB)
  const [myIsStudent, setMyIsStudent] = useState<boolean>(false);
  const [currentUserId, setCurrentUserId] = useState<number | null>(null);

  /** ===== My student bundle (only if STUDENT) ===== */
  const [myTerm, setMyTerm] = useState<string>("2025-Fall");
  const [availableTerms, setAvailableTerms] = useState<string[]>([]);
  const [myProfile, setMyProfile] = useState<StudentProfile | null>(null);
  const [myDetails, setMyDetails] = useState<FullStudentDetails | null>(null);
  // NOTE: summary will now be computed from classHistory; we keep state but won't fetch it.
  const [mySummary, setMySummary] = useState<GpaSummary | null>(null);
  const [mySchedule, setMySchedule] = useState<ScheduleClass[]>([]);
  const [myPlans, setMyPlans] = useState<PlannedProgram[]>([]);
  const [myAdmissionReqs, setMyAdmissionReqs] = useState<RequirementStatus[]>([]);
  const [myDegreeReqs, setMyDegreeReqs] = useState<RequirementStatus[]>([]);
  const [myHolds, setMyHolds] = useState<Hold[]>([]);
  const [myWaived, setMyWaived] = useState<TimeConflictWaiver[]>([]);
  const [myAudit, setMyAudit] = useState<AuditLogEntry[]>([]);
  const [myTransfers, setMyTransfers] = useState<TransferCourse[]>([]);
  const [myClassHistory, setMyClassHistory] = useState<HistoricClass[]>([]);
  const [myCoursePlan, setMyCoursePlan] = useState<PlannedCourse[]>([]);

  /** ===== Target student bundle (via search) ===== */
  const [targetSid, setTargetSid] = useState<string | null>(null);
  const [term, setTerm] = useState<string>("2025-Fall"); // target term
  const [profile, setProfile] = useState<StudentProfile | null>(null);
  const [details, setDetails] = useState<FullStudentDetails | null>(null);
  const [summary, setSummary] = useState<GpaSummary | null>(null); // computed from classHistory
  const [schedule, setSchedule] = useState<ScheduleClass[]>([]);
  const [plans, setPlans] = useState<PlannedProgram[]>([]);
  const [admissionReqs, setAdmissionReqs] = useState<RequirementStatus[]>([]);
  const [degreeReqs, setDegreeReqs] = useState<RequirementStatus[]>([]);
  const [holds, setHolds] = useState<Hold[]>([]);
  const [waivedConflicts, setWaivedConflicts] = useState<TimeConflictWaiver[]>([]);
  const [audit, setAudit] = useState<AuditLogEntry[]>([]);
  const [transfers, setTransfers] = useState<TransferCourse[]>([]);
  const [classHistory, setClassHistory] = useState<HistoricClass[]>([]);
  const [coursePlan, setCoursePlan] = useState<PlannedCourse[]>([]);

  /** ===== Admin utils ===== */
  const [events, setEvents] = useState<AcademicEvent[]>([]);
  const [sysDate, setSysDate] = useState<string>("");
  const [overridden, setOverridden] = useState(false);

  /** ===== UI ===== */
  const [msg, setMsg] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  /** ===== Search UI ===== */
  const [search, setSearch] = useState("");
  const [searching, setSearching] = useState(false);
  const [searchResult, setSearchResult] = useState<StudentProfile | null>(null);

  /** ===== Helpers ===== */
  const buildSidPrefix = (sid: string) =>
    `${BASE}/api/profile/student-id/${encodeURIComponent(sid)}`;

  const fmt = {
    num(n?: number | null) {
      return typeof n === "number" && !Number.isNaN(n) ? n.toFixed(2) : "-";
    },
    date(d?: string) {
      if (!d) return "-";
      const tt = new Date(d);
      return isNaN(tt.getTime()) ? d : tt.toLocaleString();
    },
    termRef(t?: TermRef | null) {
      if (!t) return "-";
      return `${t.semester} ${t.year}`;
    },
    join(arr?: string[] | null) {
      return arr && arr.length ? arr.join(", ") : "-";
    },
    fullName(p?: { firstName?: string | null; lastName?: string | null }) {
      const fn = (p?.firstName || "").trim();
      const ln = (p?.lastName || "").trim();
      return (fn || ln) ? `${fn} ${ln}`.trim() : "-";
    },
  };

  /** ===== Initial load - Modified: Get myPerson from database ===== */
  useEffect(() => {
    (async () => {
      setMsg(null);
      try {
        // 1) Login/authorization
        const whoRaw = await safeFetch(`${BASE}/api/auth/me`, {
          headers: { 'Cache-Control': 'no-cache', 'Pragma': 'no-cache' }
        });

        const authRoles =
          Array.isArray(whoRaw?.roles) && whoRaw.roles.length
            ? whoRaw.roles
            : (whoRaw?.user?.role
                ? [String(whoRaw.user.role).toUpperCase()]
                : []);

        setMe({
          netId: whoRaw?.netId ?? "",
          roles: authRoles as Role[],
        });

        const userId = whoRaw?.user?.userId || whoRaw?.userId || null;
        setCurrentUserId(userId);

        const isStudent = authRoles.includes("STUDENT" as Role);
        setMyIsStudent(isStudent);

        // 2) Person for My Account display (from DB)
        const personRaw = await safeFetch(`${BASE}/api/profile/person/me`).catch(() => null);
        const person = personRaw
          ? { ...personRaw, role: personRaw.role ? String(personRaw.role).toLowerCase() : "unknown" }
          : null;
        setMyPerson(person as PersonBasic | null);

        // 3) Student bundle
        if (isStudent && person && (person.sbuId || (person as any).studentId)) {
          const sid = (person as any).studentId ?? person.sbuId!;
          await loadStudentBundle(sid, myTerm, {
            setProfile: setMyProfile,
            setDetails: setMyDetails,
            setSummary: setMySummary, // will be computed after history arrives
            setSchedule: setMySchedule,
            setPlans: setMyPlans,
            setAdmissionReqs: setMyAdmissionReqs,
            setDegreeReqs: setMyDegreeReqs,
            setHolds: setMyHolds,
            setWaived: setMyWaived,
            setAudit: setMyAudit,
            setTransfers: setMyTransfers,
            setClassHistory: setMyClassHistory,
            setCoursePlan: setMyCoursePlan,
          });
        }

        // 4) Admin utilities and available terms
        const [ev, now, termsData] = await Promise.all([
          safeFetch(`${BASE}/api/admin/academic-calendar`).catch(() => []),
          safeFetch(`${BASE}/api/admin/system-date`).catch(() => null),
          safeFetch(`${BASE}/api/classes/terms`).catch(() => []),
        ]);
        setEvents(Array.isArray(ev) ? ev : []);
        if (now) {
          setSysDate(now.currentDate ?? "");
          setOverridden(!!now.overridden);
        }
        
        const defaultTerms = ['2026-Spring', '2025-Fall', '2025-Spring', '2024-Fall'];
        let termStrings: string[] = [];
        if (Array.isArray(termsData) && termsData.length > 0) {
          termStrings = termsData.map((t: any) => {
            const semester = t.semester || '';
            const year = t.year || '';
            if (semester && year) {
              const semesterName = semester.charAt(0).toUpperCase() + semester.slice(1).toLowerCase();
              return `${year}-${semesterName}`;
            }
            return null;
          }).filter((t: string | null) => t !== null) as string[];
        }
        const allTerms = [...new Set([...termStrings, ...defaultTerms])];
        allTerms.sort((a, b) => {
          const [yearA, semA] = a.split('-');
          const [yearB, semB] = b.split('-');
          if (yearA !== yearB) return parseInt(yearB) - parseInt(yearA);
          const semOrder: Record<string, number> = { 'Fall': 3, 'Summer': 2, 'Spring': 1 };
          return (semOrder[semB] || 0) - (semOrder[semA] || 0);
        });
        setAvailableTerms(allTerms);
      } catch (e: any) {
        setMsg(e.message || "Failed to load.");
        setMe(null);
        setMyPerson(null);
        setMyIsStudent(false);
      }
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [safeFetch]);

  /** ===== My schedule reload on term change ===== */
  useEffect(() => {
    (async () => {
      if (!myIsStudent || !myProfile?.studentId) return;
      try {
        const termParts = myTerm.split('-');
        const termYear = termParts.length === 2 ? parseInt(termParts[0]) : null;
        const termSemester = termParts.length === 2 ? termParts[1] : null;
        
        // Try original schedule API first
        const sched = await safeFetch(
          `${buildSidPrefix(myProfile.studentId)}/schedule?term=${encodeURIComponent(myTerm)}`
        ).catch(() => []);
        
        let scheduleData: ScheduleClass[] = Array.isArray(sched) ? (sched as ScheduleClass[]) : [];
        
        // If empty, try roster API
        if (scheduleData.length === 0 && currentUserId && termYear && termSemester) {
          try {
            const rosterData = await safeFetch(`${BASE}/api/roster/student/${currentUserId}`).catch(() => []);
            if (Array.isArray(rosterData) && rosterData.length > 0) {
              const normalizedSemester = termSemester.charAt(0).toUpperCase() + termSemester.slice(1).toLowerCase();
              const filteredRoster = rosterData.filter((item: any) => {
                const itemYear = typeof item.year === 'number' ? item.year : parseInt(item.year);
                const itemSemester = item.semester ? 
                  (item.semester.charAt(0).toUpperCase() + item.semester.slice(1).toLowerCase()) : null;
                return itemYear === termYear && itemSemester === normalizedSemester;
              });
              if (filteredRoster.length > 0) {
                scheduleData = filteredRoster.map((item: any) => ({
                  classId: item.classId,
                  subject: item.department || item.subject,
                  courseNum: item.courseNum,
                  section: item.section || '',
                  title: item.title || '',
                  schedule: item.schedule || '',
                  location: item.location || '',
                  instructor: item.instructor || '',
                  credits: item.credits || item.minCredits || 0
                }));
              }
            }
          } catch (e) {
            console.error('Error fetching roster data for my schedule:', e);
          }
        }
        setMySchedule(scheduleData);
      } catch (e: any) {
        setMsg(e.message);
      }
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [myTerm, myIsStudent, myProfile?.studentId, currentUserId]);

  /** ===== Target schedule reload on term change ===== */
  useEffect(() => {
    (async () => {
      if (!targetSid) return;
      try {
        const sched = await safeFetch(
          `${buildSidPrefix(targetSid)}/schedule?term=${encodeURIComponent(term)}`
        );
        setSchedule(Array.isArray(sched) ? (sched as ScheduleClass[]) : []);
      } catch (e: any) {
        setMsg(e.message);
      }
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [term, targetSid]);

  async function loadStudentBundle(
    sid: string | null,
    t: string,
    sinks: {
      setProfile: (v: StudentProfile | null) => void;
      setDetails: (v: FullStudentDetails | null) => void;
      setSummary: (v: GpaSummary | null) => void; // will be set from classHistory
      setSchedule: (v: ScheduleClass[]) => void;
      setPlans: (v: PlannedProgram[]) => void;
      setAdmissionReqs: (v: RequirementStatus[]) => void;
      setDegreeReqs: (v: RequirementStatus[]) => void;
      setHolds: (v: Hold[]) => void;
      setWaived: (v: TimeConflictWaiver[]) => void;
      setAudit: (v: AuditLogEntry[]) => void;
      setTransfers: (v: TransferCourse[]) => void;
      setClassHistory: (v: HistoricClass[]) => void;
      setCoursePlan: (v: PlannedCourse[]) => void;
    }
  ) {
    let theSid = sid;
    if (!theSid) {
      const meResp = await safeFetch(`${BASE}/api/profile/me`).catch(() => null);
      if (!meResp) {
        sinks.setProfile(null);
        sinks.setDetails(null);
        sinks.setSummary(null as any);
        sinks.setSchedule([]);
        sinks.setPlans([]);
        sinks.setAdmissionReqs([]);
        sinks.setDegreeReqs([]);
        sinks.setHolds([]);
        sinks.setWaived([]);
        sinks.setAudit([]);
        sinks.setTransfers([]);
        sinks.setClassHistory([]);
        sinks.setCoursePlan([]);
        return;
      }
      const meProfile = meResp as StudentProfile;
      sinks.setProfile(meProfile);
      theSid = meProfile.studentId;
    }

    const prefix = buildSidPrefix(theSid!);

    // Parse term
    const termParts = t.split('-');
    const termYear = termParts.length === 2 ? parseInt(termParts[0]) : null;
    const termSemester = termParts.length === 2 ? termParts[1] : null;

    // NOTE: removed backend /gpa call; GPA is computed from classes below
    const [p, d, schedOld, planList, adm, deg, hOld, w, log, trOld, chOld, cpOld] = await Promise.all([
      sid ? safeFetch(`${prefix}`).catch(() => null) : Promise.resolve(null),
      safeFetch(`${prefix}/details`).catch(() => null),
      safeFetch(`${prefix}/schedule?term=${encodeURIComponent(t)}`).catch(() => []),
      safeFetch(`${prefix}/plans`).catch(() => []),
      safeFetch(`${prefix}/requirements/admission`).catch(() => []),
      safeFetch(`${prefix}/requirements/degree`).catch(() => []),
      safeFetch(`${prefix}/holds`).catch(() => []),
      safeFetch(`${prefix}/waivers/time-conflicts`).catch(() => []),
      safeFetch(`${prefix}/audit-log`).catch(() => []),
      safeFetch(`${prefix}/transfers`).catch(() => []),
      safeFetch(`${prefix}/classes`).catch(() => []),
      safeFetch(`${prefix}/course-plan`).catch(() => []),
    ]);

    // Get userId for additional API calls
    let userId: number | null = null;
    if (p && (p as any).id) {
      userId = (p as any).id;
    }

    // Holds with RegistrationHoldController (more reliable)
    let h: Hold[] = [];
    if (userId) {
      try {
        const holdsResponse = await safeFetch(`${BASE}/api/holds/student/${userId}`).catch(() => null);
        if (holdsResponse && holdsResponse.success && Array.isArray(holdsResponse.holds)) {
          h = holdsResponse.holds.map((hold: any) => ({
            holdId: hold.holdId,
            holdType: hold.holdType,
            reason: hold.reason || '',
            placedAt: hold.placedAt || '',
            placedBy: hold.placedBy ? {
              userId: hold.placedBy.userId || 0,
              name: hold.placedBy.name || ''
            } : undefined
          }));
        } else if (Array.isArray(hOld) && hOld.length > 0) {
          h = hOld as Hold[];
        }
      } catch (error) {
        console.error('Error fetching holds from /api/holds/student:', error);
        if (Array.isArray(hOld) && hOld.length > 0) {
          h = hOld as Hold[];
        }
      }
    } else if (Array.isArray(hOld) && hOld.length > 0) {
      h = hOld as Hold[];
    }

    // Transfers / Course plan / Class history (fallback via users APIs if needed)
    let tr = trOld;
    let ch = chOld;
    let cp = cpOld;

    if (userId && (!tr || (Array.isArray(tr) && tr.length === 0))) {
      try {
        const transferData = await safeFetch(`${BASE}/api/users/${userId}/transfer-courses`).catch(() => []);
        if (Array.isArray(transferData) && transferData.length > 0) {
          tr = transferData.map((item: any) => ({
            originalClass: item.originalClass || item.courseName || '',
            originalUniversity: item.originalUniversity || item.university || '',
            credits: item.credits || 0,
            subject: item.subject || item.equivalentSubject || null,
            courseNum: item.courseNum || item.equivalentCourseNum || null,
            grade: item.grade || null
          }));
        }
      } catch (e) {
        console.error('Error fetching transfer courses:', e);
      }
    }

    if (userId && (!cp || (Array.isArray(cp) && cp.length === 0))) {
      try {
        const planData = await safeFetch(`${BASE}/api/users/${userId}/course-plan`).catch(() => []);
        if (Array.isArray(planData) && planData.length > 0) {
          cp = planData.map((item: any) => ({
            subject: item.subject || item.department || '',
            courseNum: item.courseNum || 0,
            semester: item.semester || null,
            year: item.year || 0,
            credits: item.credits || 0
          }));
        }
      } catch (e) {
        console.error('Error fetching course plan:', e);
      }
    }

    if (userId && (!ch || (Array.isArray(ch) && ch.length === 0))) {
      try {
        const [completedData, futureData] = await Promise.all([
          safeFetch(`${BASE}/api/users/${userId}/classes/completed`).catch(() => []),
          safeFetch(`${BASE}/api/users/${userId}/classes/future`).catch(() => [])
        ]);

        if (Array.isArray(completedData) && completedData.length > 0) {
          ch = completedData.map((item: any) => ({
            classId: item.classId,
            subject: item.department || '',
            courseNum: item.courseNum || 0,
            section: item.section || '',
            title: item.title || '',
            semester: item.semester || '',
            year: item.year || 0,
            credits: item.credits || 0,
            grade: item.grade || null,
            gpnc: !!item.gpnc
          }));
        } else if (Array.isArray(futureData) && futureData.length > 0) {
          ch = futureData.map((item: any) => ({
            classId: item.classId,
            subject: item.department || '',
            courseNum: item.courseNum || 0,
            section: item.section || '',
            title: item.title || '',
            semester: item.semester || '',
            year: item.year || 0,
            credits: item.credits || 0,
            grade: null,
            gpnc: false
          }));
        }
      } catch (e) {
        console.error('Error fetching class history:', e);
      }
    } else {
      // Ensure gpnc is boolean if coming from older API
      if (Array.isArray(ch)) {
        ch = ch.map((item: any) => ({
          ...item,
          gpnc: !!item.gpnc
        }));
      }
    }

    // Process schedule with roster fallback
    let sched: ScheduleClass[] = Array.isArray(schedOld) ? (schedOld as ScheduleClass[]) : [];
    if (sched.length === 0 && termYear && termSemester) {
      try {
        let uId: number | null = null;
        if (p && (p as any).id) uId = (p as any).id;
        if (uId) {
          const rosterData = await safeFetch(`${BASE}/api/roster/student/${uId}`).catch(() => []);
          if (Array.isArray(rosterData) && rosterData.length > 0) {
            const normalizedSemester = termSemester.charAt(0).toUpperCase() + termSemester.slice(1).toLowerCase();
            const filteredRoster = rosterData.filter((item: any) => {
              const itemYear = typeof item.year === 'number' ? item.year : (item.year ? parseInt(String(item.year)) : null);
              const itemSemester = item.semester ? (String(item.semester).charAt(0).toUpperCase() + String(item.semester).slice(1).toLowerCase()) : null;
              return itemYear === termYear && itemSemester === normalizedSemester;
            });
            if (filteredRoster.length > 0) {
              sched = filteredRoster.map((item: any) => ({
                classId: item.classId,
                subject: item.department || item.subject,
                courseNum: item.courseNum,
                section: item.section || '',
                title: item.title || '',
                schedule: item.schedule || '',
                location: item.location || '',
                instructor: item.instructor || '',
                credits: item.credits || item.minCredits || 0
              }));
            }
          }
        }
      } catch (e) {
        console.error('[Profile] Error fetching roster data:', e);
      }
    }

    if (sid) sinks.setProfile(p as StudentProfile | null);
    sinks.setDetails(d as FullStudentDetails | null);
    // sinks.setSummary(...) — DO NOT set from backend; will compute from class history
    sinks.setSchedule(Array.isArray(sched) ? (sched as ScheduleClass[]) : []);
    sinks.setPlans(Array.isArray(planList) ? (planList as PlannedProgram[]) : []);
    sinks.setAdmissionReqs(Array.isArray(adm) ? (adm as RequirementStatus[]) : []);
    sinks.setDegreeReqs(Array.isArray(deg) ? (deg as RequirementStatus[]) : []);
    sinks.setHolds(Array.isArray(h) ? (h as Hold[]) : []);
    sinks.setWaived(Array.isArray(w) ? (w as TimeConflictWaiver[]) : []);
    sinks.setAudit(Array.isArray(log) ? (log as AuditLogEntry[]) : []);
    sinks.setTransfers(Array.isArray(tr) ? (tr as TransferCourse[]) : []);
    sinks.setClassHistory(Array.isArray(ch) ? (ch as HistoricClass[]) : []);
    sinks.setCoursePlan(Array.isArray(cp) ? (cp as PlannedCourse[]) : []);
  }

  /** === Recompute my GPA when myClassHistory changes === */
  useEffect(() => {
    if (myClassHistory && myClassHistory.length >= 0) {
      setMySummary(computeGpaFromHistory(myClassHistory));
    }
  }, [myClassHistory]);

  /** === Recompute target GPA when target classHistory changes === */
  useEffect(() => {
    if (classHistory && classHistory.length >= 0) {
      setSummary(computeGpaFromHistory(classHistory));
    }
  }, [classHistory]);

  /** ===== Student search ===== */
  const doSearchStudent = async () => {
    if (!search.trim()) return;
    setSearching(true);
    setSearchResult(null);
    setMsg(null);
    try {
      const q = search.trim();
      const r = q.includes("@")
        ? await safeFetch(`${BASE}/api/profile/student-by-email?email=${encodeURIComponent(q)}`)
        : await safeFetch(`${BASE}/api/profile/student-id/${encodeURIComponent(q)}`);

      const mapped: StudentProfile = {
        id: r.id,
        studentId: r.studentId ?? "-",
        name: r.name ?? "-",
        major: r.major ?? "-",
        email: r.email ?? "-",
        status: r.status ?? "-",
        entryYear: r.entryYear ?? "-",
      };

      setSearchResult(mapped);
    } catch (e: any) {
      setMsg(e.message || "No student found.");
    } finally {
      setSearching(false);
    }
  };

  const useSearchedAsTarget = async () => {
    if (!searchResult) return;
    setTargetSid(searchResult.studentId);
    await loadStudentBundle(searchResult.studentId, term, {
      setProfile,
      setDetails,
      setSummary, // computed later
      setSchedule,
      setPlans,
      setAdmissionReqs,
      setDegreeReqs,
      setHolds,
      setWaived: setWaivedConflicts,
      setAudit,
      setTransfers,
      setClassHistory,
      setCoursePlan,
    });
  };

  /** ===== Admin: system date ===== */
  const applySystemDate = async () => {
    if (!sysDate) {
      setMsg("Please select a date (yyyy-MM-dd).");
      return;
    }
    setBusy(true);
    setMsg(null);
    try {
      const data = await safeFetch(`${BASE}/api/admin/system-date?date=${encodeURIComponent(sysDate)}`, { method: "PUT" });
      setMsg(`System date set to ${data.currentDate}`);
      setOverridden(true);
    } catch (e: any) {
      setMsg(`Failed to set date: ${e.message}`);
    } finally {
      setBusy(false);
    }
  };

  const clearSystemDate = async () => {
    setBusy(true);
    setMsg(null);
    try {
      const data = await safeFetch(`${BASE}/api/admin/system-date`, { method: "DELETE" });
      setMsg("Override cleared");
      setSysDate(data.currentDate ?? "");
      setOverridden(false);
    } catch (e: any) {
      setMsg(`Failed to clear override: ${e.message}`);
    } finally {
      setBusy(false);
    }
  };

  /** ===== Render - Modified: Added My Account UI fields and removed NetID ===== */
  const isAdvisorOrRegistrar = (me?.roles || []).some(r => r === 'ADVISOR' || r === 'REGISTRAR');
  return (
    <div style={{ maxWidth: 1200, margin: "24px auto", padding: "0 16px" }}>
      <h1 style={{ marginBottom: 8 }}>Account & Student Profiles</h1>
      <p style={{ color: "#666", marginTop: 0 }}>
        View your account info (role-aware) and student profiles (yours and any searched student).
      </p>

      {msg && (
        <div style={{ background: "#fff7e6", border: "1px solid #ffd591", padding: "10px 12px", borderRadius: 8, margin: "12px 0", whiteSpace: "pre-wrap" }}>
          {msg}
        </div>
      )}

      {/* ===== Panel 1: My Account (works for any role; DB-backed) ===== */}
      <section style={{ background: "#fafafa", border: "1px solid #eee", borderRadius: 12, padding: 16, marginTop: 16 }}>
        <h2 style={{ marginTop: 0 }}>My Account</h2>
        {!me && !myPerson ? (
          <div>No account info available.</div>
        ) : (
          <>
            <div style={{ display: "grid", gridTemplateColumns: "160px 1fr 160px 1fr", rowGap: 8, columnGap: 12 }}>
              <div style={{ color: "#666" }}>Name</div>
              <div>{fmt.fullName(myPerson || undefined)}</div>
              <div style={{ color: "#666" }}>Roles</div> 
              <div>{me?.roles?.length ? me.roles.join(", ") : "-"}</div> 
              <div style={{ color: "#666" }}>SBU ID</div>
              <div>{myPerson?.sbuId || "-"}</div>
              <div style={{ color: "#666" }}>Email</div>
              <div>{myPerson?.email || "-"}</div>

              {myPerson?.major && (
                <>
                  <div style={{ color: "#666" }}>Major (Primary)</div>
                  <div>{myPerson.major}</div>
                </>
              )}
              {typeof myPerson?.gpa === 'number' && (
                <>
                  <div style={{ color: "#666" }}>GPA (Cumulative)</div>
                  <div>{fmt.num(myPerson.gpa)}</div>
                </>
              )}
              {myPerson?.graduationYear && (
                <>
                  <div style={{ color: "#666" }}>Graduation Year</div>
                  <div>{myPerson.graduationYear}</div>
                </>
              )}

              {myPerson?.classStanding && (
                  <>
                    <div style={{ color: "#666" }}>Class Standing</div>
                    <div>{myPerson.classStanding}</div>
                  </>
              )}

              {myPerson?.department && (
                <>
                  <div style={{ color: "#666" }}>Department</div>
                  <div>{myPerson.department}</div>
                </>
              )}
              {myPerson?.officeLocation && (
                <>
                  <div style={{ color: "#666" }}>Office Location</div>
                  <div>{myPerson.officeLocation}</div>
                </>
              )}
              {myPerson?.officeHours && (
                <>
                  <div style={{ color: "#666" }}>Office Hours</div>
                  <div>{myPerson.officeHours}</div>
                </>
              )}

              {myPerson?.role === "advisor" && (
                <>
                  <div style={{ color: "#666" }}>Advisor Level</div>
                  <div>{myPerson?.unitLevel || "-"}</div>
                  <div style={{ color: "#666" }}>Academic Unit</div>
                  <div>{myPerson?.academicUnit || "-"}</div>
                </>
              )}
            </div>
          </>
        )}
      </section>

      {/* ===== Registration Status Summary (Holds & Waivers) ===== */}
      {myIsStudent && (
        <section style={{ border: "1px solid #e0f0ff", background: "#f0f5ff", borderRadius: 12, padding: 16, marginTop: 16 }}>
          <h3 style={{ marginTop: 0 }}>Registration Status Summary</h3>
          <div style={{ marginBottom: 8 }}>
            <div style={{ color: "#666", display: "inline-block", width: 150 }}>Registration Holds</div>
            <span style={{ fontWeight: 600, color: myHolds.length > 0 ? '#fa541c' : '#52c41a' }}>
              {myHolds.length > 0 ? `${myHolds.length} HOLD(S) placed` : 'None'}
            </span>
          </div>
          <div>
            <div style={{ color: "#666", display: "inline-block", width: 150 }}>Time Conflicts Waived</div>
            <span style={{ fontWeight: 600, color: myWaived.length > 0 ? '#faad14' : '#666' }}>
              {myWaived.length > 0 ? `${myWaived.length} Conflict(s)` : 'None'}
            </span>
          </div>
          <p style={{ fontSize: 12, color: '#888', marginTop: 8 }}>
            * For detailed information, check the respective tables in the 'My Student Profile' section below.
          </p>
        </section>
      )}

      {/* ===== Panel 2: My Student Profile (only if STUDENT) ===== */}
      {myIsStudent && (
        <StudentBundleView
          title="My Student Profile"
          term={myTerm}
          setTerm={setMyTerm}
          availableTerms={availableTerms}
          profile={myProfile}
          details={myDetails}
          plans={myPlans}
          summary={mySummary}             // computed from history
          schedule={mySchedule}
          admissionReqs={myAdmissionReqs}
          degreeReqs={myDegreeReqs}
          holds={myHolds}
          waivedConflicts={myWaived}
          audit={myAudit}
          transfers={myTransfers}
          classHistory={myClassHistory}
          coursePlan={myCoursePlan}
          fmt={fmt}
          busy={busy}
          isAdvisorOrRegistrar={isAdvisorOrRegistrar}
          studentId={myProfile?.studentId}
          currentUserId={currentUserId}
          onHoldsUpdate={async () => {
            if (myProfile?.studentId) {
              await loadStudentBundle(myProfile.studentId, myTerm, {
                setProfile: setMyProfile,
                setDetails: setMyDetails,
                setSummary: setMySummary,
                setSchedule: setMySchedule,
                setPlans: setMyPlans,
                setAdmissionReqs: setMyAdmissionReqs,
                setDegreeReqs: setMyDegreeReqs,
                setHolds: setMyHolds,
                setWaived: setMyWaived,
                setAudit: setMyAudit,
                setTransfers: setMyTransfers,
                setClassHistory: setMyClassHistory,
                setCoursePlan: setMyCoursePlan,
              });
            }
          }}
          onScheduleUpdate={async () => {
            if (myProfile?.studentId) {
              await loadStudentBundle(myProfile.studentId, myTerm, {
                setProfile: setMyProfile,
                setDetails: setMyDetails,
                setSummary: setMySummary,
                setSchedule: setMySchedule,
                setPlans: setMyPlans,
                setAdmissionReqs: setMyAdmissionReqs,
                setDegreeReqs: setMyDegreeReqs,
                setHolds: setMyHolds,
                setWaived: setMyWaived,
                setAudit: setMyAudit,
                setTransfers: setMyTransfers,
                setClassHistory: setMyClassHistory,
                setCoursePlan: setMyCoursePlan,
              });
            }
          }}
        />
      )}

      {/* ===== Panel 3: Student Search & Target Profile (advisor/registrar) ===== */}
      {isAdvisorOrRegistrar && (
      <section style={{ background: "#f0f5ff", border: "1px solid #adc6ff", borderRadius: 12, padding: 16, marginTop: 16 }}>
        <h2 style={{ marginTop: 0 }}>Find a Student</h2>
        <div style={{ display: "flex", gap: 8, alignItems: "center", marginBottom: 8 }}>
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Enter studentId or email"
            style={{ flex: 1, padding: "8px 10px", border: "1px solid #ddd", borderRadius: 6 }}
            disabled={searching || busy}
          />
          <button onClick={doSearchStudent} disabled={searching || busy || !search.trim()}>
            {searching ? "Searching..." : "Search"}
          </button>
        </div>

        {searchResult && (
          <div style={{ border: "1px solid #e6f4ff", background: "#fff", borderRadius: 8, padding: 12 }}>
            <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
              <div style={{ flex: 1 }}>
                <div style={{ fontWeight: 600, marginBottom: 4 }}>
                  {searchResult.name} ({searchResult.studentId})
                </div>
                <div style={{ fontSize: 13, color: "#666" }}>
                  {searchResult.email} • {searchResult.major} • {searchResult.status} • Entry {searchResult.entryYear}
                </div>
              </div>
              <button onClick={useSearchedAsTarget}>View target profile</button>
            </div>
          </div>
        )}
      </section>
      )}

      {isAdvisorOrRegistrar && (
      <StudentBundleView
        title={`Target Student Profile${targetSid ? ` (${targetSid})` : ""}`}
        term={term}
        setTerm={setTerm}
        availableTerms={availableTerms}
        profile={profile}
        details={details}
        plans={plans}
        summary={summary}               // computed from history
        schedule={schedule}
        admissionReqs={admissionReqs}
        degreeReqs={degreeReqs}
        holds={holds}
        waivedConflicts={waivedConflicts}
        audit={audit}
        transfers={transfers}
        classHistory={classHistory}
        coursePlan={coursePlan}
        fmt={fmt}
        busy={busy}
        isAdvisorOrRegistrar={isAdvisorOrRegistrar}
        studentId={targetSid}
        currentUserId={currentUserId}
        onHoldsUpdate={async () => {
          if (targetSid) {
            await loadStudentBundle(targetSid, term, {
              setProfile: setProfile,
              setDetails: setDetails,
              setSummary: setSummary,
              setSchedule: setSchedule,
              setPlans: setPlans,
              setAdmissionReqs: setAdmissionReqs,
              setDegreeReqs: setDegreeReqs,
              setHolds: setHolds,
              setWaived: setWaivedConflicts,
              setAudit: setAudit,
              setTransfers: setTransfers,
              setClassHistory: setClassHistory,
              setCoursePlan: setCoursePlan,
            });
          }
        }}
        onScheduleUpdate={async () => {
          if (targetSid) {
            await loadStudentBundle(targetSid, term, {
              setProfile: setProfile,
              setDetails: setDetails,
              setSummary: setSummary,
              setSchedule: setSchedule,
              setPlans: setPlans,
              setAdmissionReqs: setAdmissionReqs,
              setDegreeReqs: setDegreeReqs,
              setHolds: setHolds,
              setWaived: setWaivedConflicts,
              setAudit: setAudit,
              setTransfers: setTransfers,
              setClassHistory: setClassHistory,
              setCoursePlan: setCoursePlan,
            });
          }
        }}
      />)}

      {/* ===== Admin: Academic calendar + system date (optional) ===== */}
      {isAdvisorOrRegistrar && (
      <section style={{ background: "#fafafa", border: "1px solid #eee", borderRadius: 12, padding: 16, marginTop: 16 }}>
        <h2 style={{ marginTop: 0 }}>Academic Calendar Import (YAML)</h2>
        <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
          <input type="file" accept=".yaml,.yml" onChange={(e) => onUploadYaml(e.target.files?.[0] || null)} disabled={busy} />
          <span style={{ fontSize: 12, color: "#888" }}>Fields: date (yyyy-MM-dd), title, type</span>
        </div>

        <div style={{ marginTop: 12 }}>
          <strong>Current Events ({events.length})</strong>
          <ul style={{ marginTop: 8 }}>
            {events.map((ev, i) => (
              <li key={`${ev.date}-${ev.title}-${i}`}>[{ev.type}] {ev.date} — {ev.title}</li>
            ))}
            {!events.length && <li>No events imported</li>}
          </ul>
        </div>
      </section>
      )}

      {isAdvisorOrRegistrar && (
      <section style={{ background: "#fafafa", border: "1px solid #eee", borderRadius: 12, padding: 16, marginTop: 16 }}>
        <h2 style={{ marginTop: 0 }}>Test System Date</h2>
        <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
          <input type="date" value={sysDate ?? ""} onChange={(e) => setSysDate(e.target.value)} disabled={busy} />
          <button onClick={applySystemDate} disabled={busy || !sysDate}>Apply Date</button>
          <button onClick={clearSystemDate} disabled={busy || !overridden}>Clear Override</button>
          <span style={{ fontSize: 12, color: overridden ? "#fa541c" : "#52c41a", marginLeft: 8 }}>
            {overridden ? "Overridden" : "System Time"}
          </span>
        </div>
      </section>
      )}
    </div>
  );

  /** ===== Upload YAML ===== */
  async function onUploadYaml(file: File | null) {
    if (!file) return;
    setBusy(true);
    setMsg(null);
    try {
      const fd = new FormData();
      fd.append("file", file);
      const result = await safeFetch(`${BASE}/api/admin/academic-calendar/import`, { method: "POST", body: fd });
      setMsg(
        `Imported: ${result.imported} (skipped ${result.skipped})${
          result.errors?.length ? "\nErrors:\n- " + result.errors.join("\n- ") : ""
        }`
      );
      const ev = await safeFetch(`${BASE}/api/admin/academic-calendar`);
      setEvents(Array.isArray(ev) ? ev : []);
    } catch (e: any) {
      setMsg(`Upload failed: ${e.message}`);
    } finally {
      setBusy(false);
    }
  }
}

/** ===== Reusable bundle viewer ===== */
function StudentBundleView(props: {
  title: string;
  term: string;
  setTerm: (v: string) => void;
  availableTerms?: string[];
  profile: StudentProfile | null;
  details: FullStudentDetails | null;
  plans: PlannedProgram[];
  summary: GpaSummary | null;              // now computed on client
  schedule: ScheduleClass[];
  admissionReqs: RequirementStatus[];
  degreeReqs: RequirementStatus[];
  holds: Hold[];
  waivedConflicts: TimeConflictWaiver[];
  audit: AuditLogEntry[];
  transfers: TransferCourse[];
  classHistory: HistoricClass[];
  coursePlan: PlannedCourse[];
  fmt: {
    num: (n?: number | null) => string;
    date: (d?: string) => string;
    termRef: (t?: TermRef | null) => string;
    join: (arr?: string[] | null) => string;
  };
  busy: boolean;
  isAdvisorOrRegistrar?: boolean;
  studentId?: string | null;
  currentUserId?: number | null;
  onHoldsUpdate?: () => void;
  onScheduleUpdate?: () => void | Promise<void>;
}) {
  const {
    title,
    term,
    setTerm,
    availableTerms = [],
    profile,
    details,
    plans,
    summary,
    schedule,
    admissionReqs,
    degreeReqs,
    holds,
    waivedConflicts,
    audit,
    transfers,
    classHistory,
    coursePlan,
    fmt,
    busy,
    isAdvisorOrRegistrar = false,
    studentId,
    currentUserId,
    onHoldsUpdate,
    onScheduleUpdate,
  } = props;

  const backendBase = BASE;

  // Compute local GPA summary from classHistory (defensive: prefer prop if provided)
  const localSummary: GpaSummary = summary ?? computeGpaFromHistory(classHistory || []);

  // Hold management state
  const [showHoldModal, setShowHoldModal] = useState(false);
  const [holdType, setHoldType] = useState<'ACADEMIC_ADVISING' | 'FINANCIAL'>('ACADEMIC_ADVISING');
  const [holdReason, setHoldReason] = useState('');
  const [holdLoading, setHoldLoading] = useState(false);
  
  // Drop class state
  const [droppingClassId, setDroppingClassId] = useState<number | null>(null);

  // Create hold
  const handleCreateHold = async () => {
    if (!studentId || !currentUserId) {
      alert('Student ID or current user ID is missing');
      return;
    }

    // Convert studentId to userId
    let studentUserId: number | null = null;
    try {
      const studentRes = await fetch(`${backendBase}/api/profile/student-id/${studentId}`, { credentials: 'include' });
      if (studentRes.ok) {
        const studentData = await studentRes.json();
        studentUserId = studentData.userId || studentData.id || null;
      }
    } catch (e) {
      console.error('Error fetching student:', e);
    }

    if (!studentUserId) {
      alert('Could not find student user ID');
      return;
    }

    setHoldLoading(true);
    try {
      const response = await fetch(`${backendBase}/api/holds`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({
          studentId: studentUserId,
          holdType,
          placedById: currentUserId,
          reason: holdReason.trim() || null
        })
      });

      const data = await response.json();
      if (response.ok && data.success) {
        alert('Hold created successfully');
        setShowHoldModal(false);
        setHoldReason('');
        if (onHoldsUpdate) onHoldsUpdate();
      } else {
        alert(data.message || 'Failed to create hold');
      }
    } catch (error) {
      alert('Error creating hold: ' + (error instanceof Error ? error.message : String(error)));
    } finally {
      setHoldLoading(false);
    }
  };

  // Drop class
  const handleDropClass = async (classId: number) => {
    if (!currentUserId) {
      alert('User ID not available. Please refresh the page.');
      return;
    }
    if (!window.confirm(`Are you sure you want to drop this class? This action cannot be undone.`)) {
      return;
    }
    setDroppingClassId(classId);
    try {
      const response = await fetch(`${backendBase}/api/users/${currentUserId}/classes/${classId}/drop`, {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' }
      });
      const data = await response.json();
      if (data.success) {
        if (onScheduleUpdate) {
          try { await onScheduleUpdate(); } catch (updateError) { console.error('Error updating schedule after drop:', updateError); }
        }
        alert(`Class dropped successfully: ${data.department || ''} ${data.courseNum || ''}`);
      } else {
        alert(`Failed to drop class: ${data.message}`);
      }
    } catch (error) {
      console.error('Error dropping class:', error);
      alert('Error dropping class. Please try again.');
    } finally {
      setDroppingClassId(null);
    }
  };
  
  // Remove hold
  const handleRemoveHold = async (holdId: number) => {
    if (!currentUserId) {
      alert('Current user ID is missing');
      return;
    }
    if (!confirm('Are you sure you want to remove this hold?')) {
      return;
    }
    try {
      const response = await fetch(`${backendBase}/api/holds/${holdId}?removedById=${currentUserId}`, {
        method: 'DELETE',
        credentials: 'include'
      });
      const data = await response.json();
      if (response.ok && data.success) {
        alert('Hold removed successfully');
        if (onHoldsUpdate) onHoldsUpdate();
      } else {
        alert(data.message || 'Failed to remove hold');
      }
    } catch (error) {
      alert('Error removing hold: ' + (error instanceof Error ? error.message : String(error)));
    }
  };

  return (
    <section style={{ background: "#fff", border: "1px solid #eee", borderRadius: 12, padding: 16, marginTop: 16 }}>
      <h2 style={{ marginTop: 0 }}>{title}</h2>
      {!profile ? (
        <div>No student profile loaded.</div>
      ) : (
        <>
          {/* Basic profile */}
          <div style={{ display: "grid", gridTemplateColumns: "150px 1fr", rowGap: 8, columnGap: 12, marginTop: 8 }}>
            <div style={{ color: "#666" }}>Name</div>
            <div>{profile.name}</div>
            <div style={{ color: "#666" }}>Student ID</div>
            <div>{profile.studentId}</div>
            <div style={{ color: "#666" }}>Email</div>
            <div>{profile.email}</div>
            <div style={{ color: "#666" }}>Status</div>
            <div>{profile.status}</div>
            <div style={{ color: "#666" }}>Entry Year</div>
            <div>{profile.entryYear}</div>
          </div>

          {/* Details */}
          {details && (
            <section style={{ border: "1px solid #eee", borderRadius: 12, padding: 16, marginTop: 16 }}>
              <h3 style={{ marginTop: 0 }}>User Data Details</h3>
              <div style={{ display: "grid", gridTemplateColumns: "220px 1fr 220px 1fr", rowGap: 8, columnGap: 12 }}>
                <div style={{ color: "#666" }}>SBU ID</div>
                <div>{details.sbuId}</div>
                <div style={{ color: "#666" }}>First Name</div>
                <div>{details.firstName}</div>
                <div style={{ color: "#666" }}>Last Name</div>
                <div>{details.lastName}</div>
                <div style={{ color: "#666" }}>University Entry</div>
                <div>{fmt.termRef(details.universityEntry)}</div>
                <div style={{ color: "#666" }}>Direct Admit</div>
                <div>{details.directAdmit || "-"}</div>
                <div style={{ color: "#666" }}>AOI</div>
                <div>{details.aoi || "-"}</div>
                <div style={{ color: "#666" }}>College</div>
                <div>{details.college || "-"}</div>
                <div style={{ color: "#666" }}>Graduation</div>
                <div>{fmt.termRef(details.graduation)}</div>
              </div>

              {/* Declared majors/minors */}
              <div style={{ marginTop: 12 }}>
                <strong>Declared Majors / Degrees / Versions</strong>
                <div style={{ border: "1px solid #eee", borderRadius: 8, overflow: "hidden", marginTop: 8 }}>
                  <div style={{ display: "grid", gridTemplateColumns: "160px 160px 160px", background: "#fafafa", fontWeight: 600, padding: "8px 12px" }}>
                    <div>Major</div>
                    <div>Degree</div>
                    <div>Req Version</div>
                  </div>
                  {(details.majors || []).map((m, i) => (
                    <div key={`${m}-${i}`} style={{ display: "grid", gridTemplateColumns: "160px 160px 160px", padding: "8px 12px", borderTop: "1px solid #f0f0f0" }}>
                      <div>{m}</div>
                      <div>{details.degrees?.[i] || "-"}</div>
                      <div>{fmt.termRef(details.majorRequirementVersions?.[i] || null)}</div>
                    </div>
                  ))}
                  {!details.majors?.length && <div style={{ padding: 12 }}>None</div>}
                </div>
              </div>

              <div style={{ marginTop: 12 }}>
                <strong>Declared Minors / Versions</strong>
                <div style={{ border: "1px solid #eee", borderRadius: 8, overflow: "hidden", marginTop: 8 }}>
                  <div style={{ display: "grid", gridTemplateColumns: "160px 160px", background: "#fafafa", fontWeight: 600, padding: "8px 12px" }}>
                    <div>Minor</div>
                    <div>Req Version</div>
                  </div>
                  {(details.minors || []).map((m, i) => (
                    <div key={`${m}-${i}`} style={{ display: "grid", gridTemplateColumns: "160px 160px", padding: "8px 12px", borderTop: "1px solid #f0f0f0" }}>
                      <div>{m}</div>
                      <div>{fmt.termRef(details.minorRequirementVersions?.[i] || null)}</div>
                    </div>
                  ))}
                  {!details.minors?.length && <div style={{ padding: 12 }}>None</div>}
                </div>
              </div>

              {/* Planned programs */}
              <div style={{ marginTop: 12 }}>
                <strong>Planned Majors / Degrees / Versions</strong>
                <div style={{ border: "1px solid #eee", borderRadius: 8, overflow: "hidden", marginTop: 8 }}>
                  <div style={{ display: "grid", gridTemplateColumns: "160px 160px 160px", background: "#fafafa", fontWeight: 600, padding: "8px 12px" }}>
                    <div>Major</div>
                    <div>Degree</div>
                    <div>Req Version</div>
                  </div>
                  {(details.plannedMajors || []).map((m, i) => (
                    <div key={`${m}-${i}`} style={{ display: "grid", gridTemplateColumns: "160px 160px 160px", padding: "8px 12px", borderTop: "1px solid #f0f0f0" }}>
                      <div>{m}</div>
                      <div>{details.plannedDegrees?.[i] || "-"}</div>
                      <div>{fmt.termRef(details.plannedMajorRequirementVersions?.[i] || null)}</div>
                    </div>
                  ))}
                  {!details.plannedMajors?.length && <div style={{ padding: 12 }}>None</div>}
                </div>
              </div>

              <div style={{ marginTop: 12 }}>
                <strong>Planned Minors / Versions</strong>
                <div style={{ border: "1px solid #eee", borderRadius: 8, overflow: "hidden", marginTop: 8 }}>
                  <div style={{ display: "grid", gridTemplateColumns: "160px 160px", background: "#fafafa", fontWeight: 600, padding: "8px 12px" }}>
                    <div>Minor</div>
                    <div>Req Version</div>
                  </div>
                  {(details.plannedMinors || []).map((m, i) => (
                    <div key={`${m}-${i}`} style={{ display: "grid", gridTemplateColumns: "160px 160px", padding: "8px 12px", borderTop: "1px solid #f0f0f0" }}>
                      <div>{m}</div>
                      <div>{fmt.termRef(details.plannedMinorRequirementVersions?.[i] || null)}</div>
                    </div>
                  ))}
                  {!details.plannedMinors?.length && <div style={{ padding: 12 }}>None</div>}
                </div>
              </div>
            </section>
          )}

          {/* Programs */}
          <section style={{ border: "1px solid #eee", borderRadius: 12, padding: 16, marginTop: 16 }}>
            <h3 style={{ marginTop: 0 }}>Programs (Declared & Planned)</h3>
            {plans.length === 0 ? (
              <div>No programs found.</div>
            ) : (
              <div style={{ border: "1px solid #eee", borderRadius: 8, overflow: "hidden" }}>
                <div style={{ display: "grid", gridTemplateColumns: "120px 130px 1fr 120px 120px", background: "#fafafa", fontWeight: 600, padding: "8px 12px" }}>
                  <div>Kind</div>
                  <div>Code</div>
                  <div>Name</div>
                  <div>Declared</div>
                  <div>Version</div>
                </div>
                {plans.map((p, i) => (
                  <div key={`${p.kind}-${p.code}-${i}`} style={{ display: "grid", gridTemplateColumns: "120px 130px 1fr 120px 120px", padding: "8px 12px", borderTop: "1px solid #f0f0f0" }}>
                    <div>{p.kind}</div>
                    <div>{p.code}</div>
                    <div>{p.name}</div>
                    <div>{p.declared ? "Yes" : "No"}</div>
                    <div>{p.version || "-"}</div>
                  </div>
                ))}
              </div>
            )}

            {/* Admission Requirements */}
            <section style={{ border: "1px solid #eee", borderRadius: 12, padding: 16, marginTop: 16 }}>
              <h3 style={{ marginTop: 0 }}>Admission Requirements</h3>
              <ReqTable data={admissionReqs} />
            </section>

            {/* Degree Requirements */}
            <section style={{ border: "1px solid #eee", borderRadius: 12, padding: 16, marginTop: 16 }}>
              <h3 style={{ marginTop: 0 }}>Degree Requirements</h3>
              <ReqTable data={degreeReqs} />
            </section>
          </section>

          {/* Academic Summary — GPA computed from classHistory */}
          <section style={{ background: "#f6ffed", border: "1px solid #b7eb8f", borderRadius: 12, padding: 16, marginTop: 16 }}>
            <h3 style={{ marginTop: 0 }}>Academic Summary</h3>
            {!localSummary ? (
              <div>Loading...</div>
            ) : (
              <>
                <div style={{ display: "flex", gap: 16, flexWrap: "wrap", marginBottom: 8 }}>
                  <div><strong>Standing:</strong> {localSummary.standing}</div>
                  <div><strong>Cumulative GPA:</strong> {fmt.num(localSummary.cumulative.gpa ?? null)}</div>
                  <div><strong>Cumulative Credits:</strong> {localSummary.cumulative.credits}</div>
                </div>
                <div style={{ border: "1px solid #eee", borderRadius: 8, overflow: "hidden" }}>
                  <div style={{ display: "grid", gridTemplateColumns: "160px 1fr 1fr", background: "#fafafa", fontWeight: 600, padding: "8px 12px" }}>
                    <div>Term</div>
                    <div>Term GPA</div>
                    <div>Term Credits</div>
                  </div>
                  {localSummary.terms.map((t) => (
                    <div key={t.term} style={{ display: "grid", gridTemplateColumns: "160px 1fr 1fr", padding: "8px 12px", borderTop: "1px solid #f0f0f0" }}>
                      <div>{t.term}</div>
                      <div>{fmt.num(t.gpa ?? null)}</div>
                      <div>{t.credits}</div>
                    </div>
                  ))}
                  {localSummary.terms.length === 0 && (
                    <div style={{ padding: "12px" }}>No graded credit-bearing classes found.</div>
                  )}
                </div>
              </>
            )}
          </section>

          {/* Term Schedule */}
          <section style={{ border: "1px solid #eee", borderRadius: 12, padding: 16, marginTop: 16 }}>
            <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 8 }}>
              <h3 style={{ margin: 0 }}>Term Schedule</h3>
              <select value={term} onChange={(e) => setTerm(e.target.value)} style={{ padding: "6px 8px", borderRadius: 6, border: "1px solid #ddd" }} disabled={busy}>
                {availableTerms.length > 0 ? (
                  availableTerms.map((t) => (
                    <option key={t} value={t}>{t}</option>
                  ))
                ) : (
                  <>
                    <option value="2026-Spring">2026-Spring</option>
                    <option value="2025-Fall">2025-Fall</option>
                    <option value="2025-Spring">2025-Spring</option>
                    <option value="2024-Fall">2024-Fall</option>
                  </>
                )}
              </select>
            </div>
            <div style={{ border: "1px solid #eee", borderRadius: 8, overflow: "hidden" }}>
              <div style={{ display: "grid", gridTemplateColumns: "160px 1fr 1fr 1fr 1fr 100px", background: "#fafafa", fontWeight: 600, padding: "8px 12px" }}>
                <div>Course</div>
                <div>Title</div>
                <div>Schedule</div>
                <div>Location</div>
                <div>Instructor</div>
                <div>Action</div>
              </div>
              {schedule.length === 0 ? (
                <div style={{ padding: "12px" }}>No classes found</div>
              ) : (
                schedule.map((c) => (
                  <div key={`${c.classId}`} style={{ display: "grid", gridTemplateColumns: "160px 1fr 1fr 1fr 1fr 100px", padding: "8px 12px", borderTop: "1px solid #f0f0f0", alignItems: "center" }}>
                    <div>{c.subject}{c.courseNum}.{c.section}</div>
                    <div>{c.title}</div>
                    <div>{c.schedule}</div>
                    <div>{c.location}</div>
                    <div>{c.instructor || "-"}</div>
                    <div>
                      <button
                        onClick={() => handleDropClass(c.classId)}
                        disabled={busy || droppingClassId === c.classId}
                        style={{
                          padding: '4px 8px',
                          backgroundColor: '#ff4d4f',
                          color: 'white',
                          border: 'none',
                          borderRadius: 4,
                          cursor: (busy || droppingClassId === c.classId) ? 'not-allowed' : 'pointer',
                          fontSize: 12,
                          opacity: (busy || droppingClassId === c.classId) ? 0.6 : 1
                        }}
                      >
                        {droppingClassId === c.classId ? 'Dropping...' : 'Drop'}
                      </button>
                    </div>
                  </div>
                ))
              )}
            </div>
          </section>

          {/* Transfers */}
          <section style={{ border: "1px solid #eee", borderRadius: 12, padding: 16, marginTop: 16 }}>
            <h3 style={{ marginTop: 0 }}>Transfer Courses</h3>
            {transfers.length === 0 ? (
              <div>No transfer courses.</div>
            ) : (
              <div style={{ border: "1px solid #eee", borderRadius: 8, overflow: "hidden" }}>
                <div style={{ display: "grid", gridTemplateColumns: "160px 140px 100px 140px 140px 100px", background: "#fafafa", fontWeight: 600, padding: "8px 12px" }}>
                  <div>Original Class</div>
                  <div>University</div>
                  <div>Credits</div>
                  <div>Equivalent Subject</div>
                  <div>Equivalent Course</div>
                  <div>Grade</div>
                </div>
                {transfers.map((t, i) => (
                  <div key={`${t.originalClass}-${i}`} style={{ display: "grid", gridTemplateColumns: "160px 140px 100px 140px 140px 100px", padding: "8px 12px", borderTop: "1px solid #f0f0f0" }}>
                    <div>{t.originalClass}</div>
                    <div>{t.originalUniversity}</div>
                    <div>{t.credits}</div>
                    <div>{t.subject || "-"}</div>
                    <div>{t.courseNum ?? "-"}</div>
                    <div>{t.grade || "-"}</div>
                  </div>
                ))}
              </div>
            )}
          </section>

          {/* All Classes */}
          <section style={{ border: "1px solid #eee", borderRadius: 12, padding: 16, marginTop: 16 }}>
            <h3 style={{ marginTop: 0 }}>All Classes (History)</h3>
            {classHistory.length === 0 ? (
              <div>No classes.</div>
            ) : (
              <div style={{ border: "1px solid #eee", borderRadius: 8, overflow: "hidden" }}>
                <div style={{ display: "grid", gridTemplateColumns: "120px 120px 100px 80px 100px 80px 80px", background: "#fafafa", fontWeight: 600, padding: "8px 12px" }}>
                  <div>Class ID</div>
                  <div>Course</div>
                  <div>Term</div>
                  <div>Credits</div>
                  <div>GPNC</div>
                  <div>Grade</div>
                  <div>Section</div>
                </div>
                {classHistory.map((c) => (
                  <div key={`${c.classId}-${c.year}-${c.semester}`} style={{ display: "grid", gridTemplateColumns: "120px 120px 100px 80px 100px 80px 80px", padding: "8px 12px", borderTop: "1px solid #f0f0f0" }}>
                    <div>{c.classId}</div>
                    <div>{c.subject} {c.courseNum}</div>
                    <div>{c.semester} {c.year}</div>
                    <div>{c.credits}</div>
                    <div>{c.gpnc ? "Yes" : "No"}</div>
                    <div>{c.grade || "-"}</div>
                    <div>{c.section}</div>
                  </div>
                ))}
              </div>
            )}
          </section>

          {/* Planned Courses */}
          <section style={{ border: "1px solid #eee", borderRadius: 12, padding: 16, marginTop: 16 }}>
            <h3 style={{ marginTop: 0 }}>Planned Courses (Future)</h3>
            {coursePlan.length === 0 ? (
              <div>No planned courses.</div>
            ) : (
              <div style={{ border: "1px solid #eee", borderRadius: 8, overflow: "hidden" }}>
                <div style={{ display: "grid", gridTemplateColumns: "160px 140px 140px 100px", background: "#fafafa", fontWeight: 600, padding: "8px 12px" }}>
                  <div>Course</div>
                  <div>Term</div>
                  <div>Credits</div>
                  <div>Notes</div>
                </div>
                {coursePlan.map((c, i) => (
                  <div key={`${c.subject}-${c.courseNum}-${c.year}-${c.semester}-${i}`} style={{ display: "grid", gridTemplateColumns: "160px 140px 140px 100px", padding: "8px 12px", borderTop: "1px solid #f0f0f0" }}>
                    <div>{c.subject} {c.courseNum}</div>
                    <div>{c.semester} {c.year}</div>
                    <div>{c.credits}</div>
                    <div>-</div>
                  </div>
                ))}
              </div>
            )}
          </section>

          {/* Holds */}
          <section style={{ border: "1px solid #eee", borderRadius: 12, padding: 16, marginTop: 16 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
              <h3 style={{ marginTop: 0 }}>Registration Holds</h3>
              {isAdvisorOrRegistrar && studentId && currentUserId && (
                <button 
                  onClick={() => setShowHoldModal(true)}
                  disabled={busy || holdLoading}
                  style={{
                    padding: '6px 12px',
                    backgroundColor: '#1890ff',
                    color: 'white',
                    border: 'none',
                    borderRadius: 4,
                    cursor: 'pointer',
                    fontSize: 14
                  }}
                >
                  + Add Hold
                </button>
              )}
            </div>
            {holds.length === 0 ? (
              <div>No holds.</div>
            ) : (
              <div style={{ border: "1px solid #eee", borderRadius: 8, overflow: "hidden" }}>
                <div style={{ display: "grid", gridTemplateColumns: isAdvisorOrRegistrar ? "120px 1fr 150px 100px 80px" : "120px 1fr 150px 100px", background: "#fafafa", fontWeight: 600, padding: "8px 12px" }}>
                  <div>Type</div>
                  <div>Reason</div>
                  <div>Placed</div>
                  <div>By</div>
                  {isAdvisorOrRegistrar && <div>Action</div>}
                </div>
                {holds.map((h, i) => (
                  <div key={`${h.holdId || i}-${h.placedAt}`} style={{ display: "grid", gridTemplateColumns: isAdvisorOrRegistrar ? "120px 1fr 150px 100px 80px" : "120px 1fr 150px 100px", padding: "8px 12px", borderTop: "1px solid #f0f0f0" }}>
                    <div>{h.holdType || h.code || 'N/A'}</div>
                    <div>{h.reason || h.title || '-'}</div>
                    <div>{fmt.date(h.placedAt)}</div>
                    <div>
                      {typeof h.placedBy === 'object' ? h.placedBy.name : (h.placedBy || '-')}
                    </div>
                    {isAdvisorOrRegistrar && h.holdId && (
                      <div>
                        <button
                          onClick={() => handleRemoveHold(h.holdId!)}
                          disabled={busy || holdLoading}
                          style={{
                            padding: '4px 8px',
                            backgroundColor: '#ff4d4f',
                            color: 'white',
                            border: 'none',
                            borderRadius: 4,
                            cursor: 'pointer',
                            fontSize: 12
                          }}
                        >
                          Remove
                        </button>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
          </section>

          {/* Hold Creation Modal */}
          {showHoldModal && (
            <div style={{
              position: 'fixed',
              top: 0, left: 0, right: 0, bottom: 0,
              backgroundColor: 'rgba(0,0,0,0.5)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              zIndex: 1000
            }}>
              <div style={{
                backgroundColor: 'white',
                borderRadius: 8,
                padding: 24,
                maxWidth: 500,
                width: '90%',
                maxHeight: '90vh',
                overflow: 'auto'
              }}>
                <h3 style={{ marginTop: 0 }}>Create Registration Hold</h3>
                <div style={{ marginBottom: 16 }}>
                  <label style={{ display: 'block', marginBottom: 8, fontWeight: 600 }}>Hold Type</label>
                  <select
                    value={holdType}
                    onChange={(e) => setHoldType(e.target.value as 'ACADEMIC_ADVISING' | 'FINANCIAL')}
                    style={{ width: '100%', padding: '8px', border: '1px solid #ddd', borderRadius: 4 }}
                  >
                    <option value="ACADEMIC_ADVISING">Academic Advising</option>
                    <option value="FINANCIAL">Financial</option>
                  </select>
                </div>
                <div style={{ marginBottom: 16 }}>
                  <label style={{ display: 'block', marginBottom: 8, fontWeight: 600 }}>Reason (Optional)</label>
                  <textarea
                    value={holdReason}
                    onChange={(e) => setHoldReason(e.target.value)}
                    placeholder="Enter reason for the hold"
                    style={{ width: '100%', padding: '8px', border: '1px solid #ddd', borderRadius: 4, minHeight: 100 }}
                  />
                </div>
                <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
                  <button
                    onClick={() => { setShowHoldModal(false); setHoldReason(''); }}
                    disabled={holdLoading}
                    style={{ padding: '8px 16px', backgroundColor: '#f0f0f0', border: 'none', borderRadius: 4, cursor: 'pointer' }}
                  >
                    Cancel
                  </button>
                  <button
                    onClick={handleCreateHold}
                    disabled={holdLoading}
                    style={{ padding: '8px 16px', backgroundColor: '#1890ff', color: 'white', border: 'none', borderRadius: 4, cursor: 'pointer' }}
                  >
                    {holdLoading ? 'Creating...' : 'Create Hold'}
                  </button>
                </div>
              </div>
            </div>
          )}

          {/* Waived Time Conflicts */}
          <section style={{ border: "1px solid #eee", borderRadius: 12, padding: 16, marginTop: 16 }}>
            <h3 style={{ marginTop: 0 }}>Waived Time Conflicts</h3>
            {waivedConflicts.length === 0 ? (
              <div>No waived time conflicts.</div>
            ) : (
              <div style={{ border: "1px solid #eee", borderRadius: 8, overflow: "hidden" }}>
                <div style={{ display: "grid", gridTemplateColumns: "160px 160px 140px 1fr 200px", background: "#fafafa", fontWeight: 600, padding: "8px 12px" }}>
                  <div>Class A</div>
                  <div>Class B</div>
                  <div>Term</div>
                  <div>Waived By</div>
                  <div>Timestamp</div>
                </div>
                {waivedConflicts.map((w, i) => (
                  <div key={`${w.classA}-${w.classB}-${i}`} style={{ display: "grid", gridTemplateColumns: "160px 160px 140px 1fr 200px", padding: "8px 12px", borderTop: "1px solid #f0f0f0" }}>
                    <div>{w.classA}</div>
                    <div>{w.classB}</div>
                    <div>{w.term}</div>
                    <div>{w.waivedBy}</div>
                    <div>{fmt.date(w.waivedAt)}</div>
                  </div>
                ))}
              </div>
            )}
          </section>

          {/* Audit Log */}
          {/* NOTE: the JSX comment must be wrapped like this */}
          <section style={{ border: "1px solid #eee", borderRadius: 12, padding: 16, marginTop: 16 }}>
            <h3 style={{ marginTop: 0 }}>Audit Log</h3>
            {audit.length === 0 ? (
              <div>No log entries.</div>
            ) : (
              <div style={{ border: "1px solid #eee", borderRadius: 8, overflow: "hidden" }}>
                <div style={{ display: "grid", gridTemplateColumns: "200px 140px 180px 1fr", background: "#fafafa", fontWeight: 600, padding: "8px 12px" }}>
                  <div>Timestamp</div>
                  <div>Actor</div>
                  <div>Action</div>
                  <div>Details</div>
                </div>
                {audit.map((a) => (
                  <div key={a.id} style={{ display: "grid", gridTemplateColumns: "200px 140px 180px 1fr", padding: "8px 12px", borderTop: "1px solid #f0f0f0" }}>
                    <div>{fmt.date(a.timestamp)}</div>
                    <div>{a.actorDisplay}</div>
                    <div>{a.action}</div>
                    <div style={{ whiteSpace: "pre-wrap" }}>{a.details || "-"}</div>
                  </div>
                ))}
              </div>
            )}
          </section>
        </>
      )}
    </section>
  );
}

/** ===== Requirement table (reused) ===== */
function ReqTable({ data }: { data: RequirementStatus[] }) {
  if (!data || data.length === 0) return <div>No requirements.</div>;
  return (
    <div style={{ border: "1px solid #eee", borderRadius: 8, overflow: "hidden" }}>
      <div style={{ display: "grid", gridTemplateColumns: "140px 140px 1fr 120px 1fr", background: "#fafafa", fontWeight: 600, padding: "8px 12px" }}>
        <div>Program</div>
        <div>Req Code</div>
        <div>Title</div>
        <div>Status</div>
        <div>Using Classes</div>
      </div>
      {data.map((r, i) => (
        <div key={`${r.programCode}-${r.code}-${i}`} style={{ display: "grid", gridTemplateColumns: "140px 140px 1fr 120px 1fr", padding: "8px 12px", borderTop: "1px solid #f0f0f0" }}>
          <div>{r.programCode}</div>
          <div>{r.code}</div>
          <div>{r.title}</div>
          <div style={{ textTransform: "capitalize" }}>{r.status}</div>
          <div style={{ fontSize: 13, color: "#666" }}>
            {r.usingClasses && r.usingClasses.length
              ? r.usingClasses.map((c) => `${c.subject} ${c.courseNum}${c.term ? ` (${c.term}${c.grade ? ` ${c.grade}` : ""})` : ""}`).join(", ")
              : "-"}
          </div>
        </div>
      ))}
    </div>
  );
}
