import { useEffect, useMemo, useState } from "react";

/** ===== Types ===== */
type Role = "STUDENT" | "ADVISOR" | "REGISTRAR" | "ADMIN";

type CurrentUserInfo = { netId: string; roles: Role[] };

/** Generic person (non-student compatible) */
type PersonBasic = {
  sbuId?: string | null;
  firstName?: string | null;
  lastName?: string | null;
  email?: string | null;
  role?: "student" | "advisor" | "registrar" | "instructor" | "unknown";
  unitLevel?: "university" | "college" | "department" | null; // for advisors
  academicUnit?: string | null; // e.g., CSE, CAS, SBU
};

/** Student basic + details (as before) */
type StudentProfile = {
  id: number;
  netId: string;
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

type Hold = { code: string; title: string; placedAt: string; placedBy?: string };
type TimeConflictWaiver = { classA: string; classB: string; term: string; waivedBy: string; waivedAt: string };
type AuditLogEntry = { id: number; timestamp: string; actorRole: "student" | "advisor" | "registrar" | "system"; actorDisplay: string; action: string; details?: string };

type TransferCourse = { originalClass: string; originalUniversity: string; credits: number; subject: string | null; courseNum: number | null; grade: string | null };
type HistoricClass = { classId: number; subject: string; courseNum: number; section: number; semester: TermRef["semester"]; year: number; credits: number; gpnc: boolean; grade: string | null };
type PlannedCourse = { subject: string; courseNum: number; semester: TermRef["semester"]; year: number; credits: number };

/** ===== Config ===== */
const BASE = import.meta.env.VITE_BACKEND_URL || "http://localhost:8080";

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
  const [myPerson, setMyPerson] = useState<PersonBasic | null>(null); // non-student compatible info (from YAML)
  const [myIsStudent, setMyIsStudent] = useState<boolean>(false);

  /** ===== My student bundle (only if STUDENT) ===== */
  const [myTerm, setMyTerm] = useState<string>("2025-Fall");
  const [myProfile, setMyProfile] = useState<StudentProfile | null>(null);
  const [myDetails, setMyDetails] = useState<FullStudentDetails | null>(null);
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
  const [targetNetId, setTargetNetId] = useState<string | null>(null);
  const [term, setTerm] = useState<string>("2025-Fall"); // target term
  const [profile, setProfile] = useState<StudentProfile | null>(null);
  const [details, setDetails] = useState<FullStudentDetails | null>(null);
  const [summary, setSummary] = useState<GpaSummary | null>(null);
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
  const buildPrefix = (netId: string | null) =>
    netId ? `${BASE}/api/profile/student/${encodeURIComponent(netId)}` : `${BASE}/api/profile/me`;

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

  /** ===== Initial load ===== */
  useEffect(() => {
    (async () => {
      setMsg(null);
      try {
        // who am I (roles)
        const who = await safeFetch(`${BASE}/api/auth/me`).catch(() => null);
        if (who) setMe(who as CurrentUserInfo);

        // generic person info from YAML (works for any role)
        // Expected: /api/profile/person/me (basic), /api/profile/person/me/details (YAML-shaped)
        const person = await safeFetch(`${BASE}/api/profile/person/me`).catch(() => null);
        setMyPerson(person as PersonBasic | null);

        // am I a student?
        const isStudent = !!(who && Array.isArray(who.roles) && who.roles.includes("STUDENT"));
        setMyIsStudent(isStudent);

        // if student, load my student bundle
        if (isStudent) {
          await loadStudentBundle(null, myTerm, {
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

        // admin utilities (calendar & system date)
        const [ev, now] = await Promise.all([
          safeFetch(`${BASE}/api/admin/academic-calendar`).catch(() => []),
          safeFetch(`${BASE}/api/admin/system-date`).catch(() => null),
        ]);
        setEvents(Array.isArray(ev) ? ev : []);
        if (now) {
          setSysDate(now.currentDate ?? "");
          setOverridden(!!now.overridden);
        }
      } catch (e: any) {
        setMsg(e.message || "Failed to load.");
      }
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [safeFetch]);

  /** ===== My schedule reload on term change ===== */
  useEffect(() => {
    (async () => {
      if (!myIsStudent || !myProfile) return;
      try {
        const sched = await safeFetch(`${buildPrefix(null)}/schedule?term=${encodeURIComponent(myTerm)}`);
        setMySchedule(Array.isArray(sched) ? (sched as ScheduleClass[]) : []);
      } catch (e: any) {
        setMsg(e.message);
      }
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [myTerm, myIsStudent, myProfile]);

  /** ===== Target schedule reload on term change ===== */
  useEffect(() => {
    (async () => {
      if (!profile) return;
      try {
        const sched = await safeFetch(`${buildPrefix(targetNetId)}/schedule?term=${encodeURIComponent(term)}`);
        setSchedule(Array.isArray(sched) ? (sched as ScheduleClass[]) : []);
      } catch (e: any) {
        setMsg(e.message);
      }
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [term, targetNetId, profile]);

  /** ===== Load student bundle (me or target) ===== */
  async function loadStudentBundle(
    netId: string | null,
    t: string,
    sinks: {
      setProfile: (v: StudentProfile | null) => void;
      setDetails: (v: FullStudentDetails | null) => void;
      setSummary: (v: GpaSummary | null) => void;
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
    const prefix = buildPrefix(netId);
    const [p, d, gpa, sched, planList, adm, deg, h, w, log, tr, ch, cp] = await Promise.all([
      safeFetch(`${prefix}`).catch(() => null),
      safeFetch(`${prefix}/details`).catch(() => null),
      safeFetch(`${prefix}/gpa`).catch(() => null),
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

    sinks.setProfile(p as StudentProfile | null);
    sinks.setDetails(d as FullStudentDetails | null);
    sinks.setSummary(gpa as GpaSummary | null);
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

  /** ===== Student search ===== */
  const doSearchStudent = async () => {
    if (!search.trim()) return;
    setSearching(true);
    setSearchResult(null);
    setMsg(null);
    try {
      const q = search.trim();
      let data: StudentProfile | null = null;
      if (!q.includes("@")) {
        data = await safeFetch(`${BASE}/api/profile/student?netId=${encodeURIComponent(q)}`);
      } else {
        data = await safeFetch(`${BASE}/api/profile/student-by-email?email=${encodeURIComponent(q)}`);
      }
      setSearchResult(data);
      if (!data) setMsg("No student found.");
    } catch (e: any) {
      setMsg(e.message);
    } finally {
      setSearching(false);
    }
  };

  const useSearchedAsTarget = async () => {
    if (!searchResult) return;
    setTargetNetId(searchResult.netId);
    await loadStudentBundle(searchResult.netId, term, {
      setProfile,
      setDetails,
      setSummary,
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

  /** ===== Render ===== */
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

      {/* ===== Panel 1: My Account (works for any role; YAML-backed) ===== */}
      <section style={{ background: "#fafafa", border: "1px solid #eee", borderRadius: 12, padding: 16, marginTop: 16 }}>
        <h2 style={{ marginTop: 0 }}>My Account</h2>
        {!me && !myPerson ? (
          <div>No account info available.</div>
        ) : (
          <>
            <div style={{ display: "grid", gridTemplateColumns: "160px 1fr 160px 1fr", rowGap: 8, columnGap: 12 }}>
              <div style={{ color: "#666" }}>NetID</div>
              <div>{me?.netId || "-"}</div>
              <div style={{ color: "#666" }}>Roles</div>
              <div>{me?.roles?.length ? me.roles.join(", ") : "-"}</div>
              <div style={{ color: "#666" }}>Name</div>
              <div>{fmt.fullName(myPerson || undefined)}</div>
              <div style={{ color: "#666" }}>Email</div>
              <div>{myPerson?.email || "-"}</div>
              <div style={{ color: "#666" }}>SBU ID</div>
              <div>{myPerson?.sbuId || "-"}</div>
              <div style={{ color: "#666" }}>Role Type</div>
              <div style={{ textTransform: "capitalize" }}>{myPerson?.role || "-"}</div>
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

      {/* ===== Panel 2: My Student Profile (only if STUDENT) ===== */}
      {myIsStudent && (
        <StudentBundleView
          title="My Student Profile"
          term={myTerm}
          setTerm={setMyTerm}
          profile={myProfile}
          details={myDetails}
          plans={myPlans}
          summary={mySummary}
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
        />
      )}

      {/* ===== Panel 3: Student Search & Target Profile (separate from me) ===== */}
      <section style={{ background: "#f0f5ff", border: "1px solid #adc6ff", borderRadius: 12, padding: 16, marginTop: 16 }}>
        <h2 style={{ marginTop: 0 }}>Find a Student</h2>
        <div style={{ display: "flex", gap: 8, alignItems: "center", marginBottom: 8 }}>
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Enter netId or email"
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
                  {searchResult.name} ({searchResult.netId})
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

      <StudentBundleView
        title={`Target Student Profile${targetNetId ? ` (${targetNetId})` : ""}`}
        term={term}
        setTerm={setTerm}
        profile={profile}
        details={details}
        plans={plans}
        summary={summary}
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
      />

      {/* ===== Admin: Academic calendar + system date (optional) ===== */}
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
  profile: StudentProfile | null;
  details: FullStudentDetails | null;
  plans: PlannedProgram[];
  summary: GpaSummary | null;
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
}) {
  const {
    title,
    term,
    setTerm,
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
  } = props;

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
            <div style={{ color: "#666" }}>NetID</div>
            <div>{profile.netId}</div>
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
          </section>

          {/* Academic Summary */}
          <section style={{ background: "#f6ffed", border: "1px solid #b7eb8f", borderRadius: 12, padding: 16, marginTop: 16 }}>
            <h3 style={{ marginTop: 0 }}>Academic Summary</h3>
            {!summary ? (
              <div>Loading...</div>
            ) : (
              <>
                <div style={{ display: "flex", gap: 16, flexWrap: "wrap", marginBottom: 8 }}>
                  <div><strong>Standing:</strong> {summary.standing}</div>
                  <div><strong>Cumulative GPA:</strong> {fmt.num(summary.cumulative.gpa ?? null)}</div>
                  <div><strong>Cumulative Credits:</strong> {summary.cumulative.credits}</div>
                </div>
                <div style={{ border: "1px solid #eee", borderRadius: 8, overflow: "hidden" }}>
                  <div style={{ display: "grid", gridTemplateColumns: "160px 1fr 1fr", background: "#fafafa", fontWeight: 600, padding: "8px 12px" }}>
                    <div>Term</div>
                    <div>Term GPA</div>
                    <div>Term Credits</div>
                  </div>
                  {summary.terms.map((t) => (
                    <div key={t.term} style={{ display: "grid", gridTemplateColumns: "160px 1fr 1fr", padding: "8px 12px", borderTop: "1px solid #f0f0f0" }}>
                      <div>{t.term}</div>
                      <div>{fmt.num(t.gpa ?? null)}</div>
                      <div>{t.credits}</div>
                    </div>
                  ))}
                </div>
              </>
            )}
          </section>

          {/* Term Schedule */}
          <section style={{ border: "1px solid #eee", borderRadius: 12, padding: 16, marginTop: 16 }}>
            <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 8 }}>
              <h3 style={{ margin: 0 }}>Term Schedule</h3>
              <select value={term} onChange={(e) => setTerm(e.target.value)} style={{ padding: "6px 8px", borderRadius: 6, border: "1px solid #ddd" }} disabled={busy}>
                <option value="2025-Fall">2025-Fall</option>
                <option value="2025-Spring">2025-Spring</option>
                <option value="2024-Fall">2024-Fall</option>
              </select>
            </div>
            <div style={{ border: "1px solid #eee", borderRadius: 8, overflow: "hidden" }}>
              <div style={{ display: "grid", gridTemplateColumns: "160px 1fr 1fr 1fr 1fr", background: "#fafafa", fontWeight: 600, padding: "8px 12px" }}>
                <div>Course</div>
                <div>Title</div>
                <div>Schedule</div>
                <div>Location</div>
                <div>Instructor</div>
              </div>
              {schedule.length === 0 ? (
                <div style={{ padding: "12px" }}>No classes found</div>
              ) : (
                schedule.map((c) => (
                  <div key={`${c.classId}`} style={{ display: "grid", gridTemplateColumns: "160px 1fr 1fr 1fr 1fr", padding: "8px 12px", borderTop: "1px solid #f0f0f0" }}>
                    <div>{c.subject}{c.courseNum}.{c.section}</div>
                    <div>{c.title}</div>
                    <div>{c.schedule}</div>
                    <div>{c.location}</div>
                    <div>{c.instructor || "-"}</div>
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
            <h3 style={{ marginTop: 0 }}>Registration Holds</h3>
            {holds.length === 0 ? (
              <div>No holds.</div>
            ) : (
              <div style={{ border: "1px solid #eee", borderRadius: 8, overflow: "hidden" }}>
                <div style={{ display: "grid", gridTemplateColumns: "160px 1fr 200px", background: "#fafafa", fontWeight: 600, padding: "8px 12px" }}>
                  <div>Code</div>
                  <div>Title</div>
                  <div>Placed</div>
                </div>
                {holds.map((h, i) => (
                  <div key={`${h.code}-${i}`} style={{ display: "grid", gridTemplateColumns: "160px 1fr 200px", padding: "8px 12px", borderTop: "1px solid #f0f0f0" }}>
                    <div>{h.code}</div>
                    <div>{h.title}</div>
                    <div>{fmt.date(h.placedAt)}</div>
                  </div>
                ))}
              </div>
            )}
          </section>

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