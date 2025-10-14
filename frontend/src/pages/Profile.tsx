import { useEffect, useMemo, useState } from "react";

/** ===== Types (DTOs expected from backend) ===== */
type Role = "STUDENT" | "ADVISOR" | "REGISTRAR" | "ADMIN";

type CurrentUserInfo = { netId: string; roles: Role[] };

type StudentProfile = {
  id: number;
  netId: string;
  name: string;
  major: string;
  email: string;
  status: string;
  entryYear: string;
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
  usingClasses?: Array<{
    subject: string;
    courseNum: number;
    term?: string;
    grade?: string | null;
  }>;
  note?: string | null;
};

type Hold = { code: string; title: string; placedAt: string; placedBy?: string };

type TimeConflictWaiver = {
  classA: string;
  classB: string;
  term: string;
  waivedBy: string;
  waivedAt: string;
};

type AuditLogEntry = {
  id: number;
  timestamp: string;
  actorRole: "student" | "advisor" | "registrar" | "system";
  actorDisplay: string;
  action: string;
  details?: string;
};

/** ===== Config ===== */
const BASE = import.meta.env.VITE_BACKEND_URL || "http://localhost:8080";

/** ===== Component ===== */
export default function ProfilePage() {
  // who am I
  const [me, setMe] = useState<CurrentUserInfo | null>(null);

  // selected target student (null => me)
  const [targetNetId, setTargetNetId] = useState<string | null>(null);

  // basic profile of the target
  const [profile, setProfile] = useState<StudentProfile | null>(null);

  // per spec sections
  const [summary, setSummary] = useState<GpaSummary | null>(null);
  const [schedule, setSchedule] = useState<ScheduleClass[]>([]);
  const [term, setTerm] = useState<string>("2025-Fall");
  const [plans, setPlans] = useState<PlannedProgram[]>([]);
  const [admissionReqs, setAdmissionReqs] = useState<RequirementStatus[]>([]);
  const [degreeReqs, setDegreeReqs] = useState<RequirementStatus[]>([]);
  const [holds, setHolds] = useState<Hold[]>([]);
  const [waivedConflicts, setWaivedConflicts] = useState<TimeConflictWaiver[]>([]);
  const [audit, setAudit] = useState<AuditLogEntry[]>([]);

  // admin panels (calendar/system date)
  const [events, setEvents] = useState<AcademicEvent[]>([]);
  const [sysDate, setSysDate] = useState<string>("");
  const [overridden, setOverridden] = useState(false);

  // UI state
  const [msg, setMsg] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // search ui
  const [search, setSearch] = useState("");
  const [searching, setSearching] = useState(false);
  const [searchResult, setSearchResult] = useState<StudentProfile | null>(null);

  /** common fetch helper */
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

  /** decide api prefix for the current target student */
  const buildPrefix = (netId: string | null) =>
    netId ? `${BASE}/api/profile/student/${encodeURIComponent(netId)}` : `${BASE}/api/profile/me`;

  /** initial load: who am I, load my default profile if any, admin utils */
  useEffect(() => {
    (async () => {
      setMsg(null);
      try {
        const who = await safeFetch(`${BASE}/api/auth/me`).catch(() => null);
        if (who) setMe(who as CurrentUserInfo);

        // default target is me — only if /me works
        const p = await safeFetch(`${BASE}/api/profile/me`).catch(() => null);
        if (p) {
          setTargetNetId(null);
          setProfile(p as StudentProfile);
          await loadAllSections(null, term);
        }

        // admin utils (best-effort)
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

  /** when term changes, reload only schedule for current target */
  useEffect(() => {
    (async () => {
      if (!profile) return;
      try {
        const sched = await safeFetch(
          `${buildPrefix(targetNetId)}/schedule?term=${encodeURIComponent(term)}`
        );
        setSchedule(Array.isArray(sched) ? (sched as ScheduleClass[]) : []);
      } catch (e: any) {
        setMsg(e.message);
      }
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [term, targetNetId, profile]);

  /** load all sections for a netId (null => me) */
  const loadAllSections = async (netId: string | null, t: string) => {
    const prefix = buildPrefix(netId);
    const [p, gpa, sched, planList, adm, deg, h, w, log] = await Promise.all([
      safeFetch(`${prefix}`).catch(() => null),
      safeFetch(`${prefix}/gpa`).catch(() => null),
      safeFetch(`${prefix}/schedule?term=${encodeURIComponent(t)}`).catch(() => []),
      safeFetch(`${prefix}/plans`).catch(() => []),
      safeFetch(`${prefix}/requirements/admission`).catch(() => []),
      safeFetch(`${prefix}/requirements/degree`).catch(() => []),
      safeFetch(`${prefix}/holds`).catch(() => []),
      safeFetch(`${prefix}/waivers/time-conflicts`).catch(() => []),
      safeFetch(`${prefix}/audit-log`).catch(() => []),
    ]);

    if (p) setProfile(p as StudentProfile);
    if (gpa) setSummary(gpa as GpaSummary);
    setSchedule(Array.isArray(sched) ? (sched as ScheduleClass[]) : []);
    setPlans(Array.isArray(planList) ? (planList as PlannedProgram[]) : []);
    setAdmissionReqs(Array.isArray(adm) ? (adm as RequirementStatus[]) : []);
    setDegreeReqs(Array.isArray(deg) ? (deg as RequirementStatus[]) : []);
    setHolds(Array.isArray(h) ? (h as Hold[]) : []);
    setWaivedConflicts(Array.isArray(w) ? (w as TimeConflictWaiver[]) : []);
    setAudit(Array.isArray(log) ? (log as AuditLogEntry[]) : []);
  };

  /** search student (netId or email) */
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
        data = await safeFetch(
          `${BASE}/api/profile/student-by-email?email=${encodeURIComponent(q)}`
        );
      }
      setSearchResult(data);
      if (!data) setMsg("No student found.");
    } catch (e: any) {
      setMsg(e.message);
    } finally {
      setSearching(false);
    }
  };

  /** switch target to searched student and load all sections */
  const useSearchedAsTarget = async () => {
    if (!searchResult) return;
    setTargetNetId(searchResult.netId);
    await loadAllSections(searchResult.netId, term);
  };

  /** system date apply/clear */
  const applySystemDate = async () => {
    if (!sysDate) {
      setMsg("Please select a date (yyyy-MM-dd).");
      return;
    }
    setBusy(true);
    setMsg(null);
    try {
      const data = await safeFetch(
        `${BASE}/api/admin/system-date?date=${encodeURIComponent(sysDate)}`,
        { method: "PUT" }
      );
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

  /** UI helpers */
  const fmt = {
    num(n?: number | null) {
      return typeof n === "number" && !Number.isNaN(n) ? n.toFixed(2) : "-";
    },
    date(d?: string) {
      if (!d) return "-";
      const tt = new Date(d);
      return isNaN(tt.getTime()) ? d : tt.toLocaleString();
    },
  };

  /** Render */
  return (
    <div style={{ maxWidth: 980, margin: "24px auto", padding: "0 16px" }}>
      <h1 style={{ marginBottom: 8 }}>Student Profile & Support Features</h1>
      <p style={{ color: "#666", marginTop: 0 }}>
        View profile basics, audit history, holds and waivers, requirements, GPA summary, and schedule.
      </p>

      {msg && (
        <div
          style={{
            background: "#fff7e6",
            border: "1px solid #ffd591",
            padding: "10px 12px",
            borderRadius: 8,
            margin: "12px 0",
            whiteSpace: "pre-wrap",
          }}
        >
          {msg}
        </div>
      )}

      {/* Student search (always visible for experimentation) */}
      <section
        style={{
          background: "#f0f5ff",
          border: "1px solid #adc6ff",
          borderRadius: 12,
          padding: 16,
          marginTop: 16,
        }}
      >
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
          <div
            style={{
              border: "1px solid #e6f4ff",
              background: "#fff",
              borderRadius: 8,
              padding: 12,
            }}
          >
            <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
              <div style={{ flex: 1 }}>
                <div style={{ fontWeight: 600, marginBottom: 4 }}>
                  {searchResult.name} ({searchResult.netId})
                </div>
                <div style={{ fontSize: 13, color: "#666" }}>
                  {searchResult.email} • {searchResult.major} • {searchResult.status} • Entry {searchResult.entryYear}
                </div>
              </div>
              <button onClick={useSearchedAsTarget}>View full profile</button>
            </div>
          </div>
        )}
      </section>

      {/* Basic profile of the target */}
      <section
        style={{
          background: "#fafafa",
          border: "1px solid #eee",
          borderRadius: 12,
          padding: 16,
          marginTop: 16,
        }}
      >
        <h2 style={{ marginTop: 0 }}>
          Student Profile {targetNetId ? `(target: ${targetNetId})` : "(me)"}
        </h2>
        {!profile ? (
          <div>No student profile for the current target.</div>
        ) : (
          <div
            style={{
              display: "grid",
              gridTemplateColumns: "150px 1fr",
              rowGap: 8,
              columnGap: 12,
            }}
          >
            <div style={{ color: "#666" }}>Name</div>
            <div>{profile.name}</div>
            <div style={{ color: "#666" }}>NetID</div>
            <div>{profile.netId}</div>
            <div style={{ color: "#666" }}>Major</div>
            <div>{profile.major}</div>
            <div style={{ color: "#666" }}>Email</div>
            <div>{profile.email}</div>
            <div style={{ color: "#666" }}>Status</div>
            <div>{profile.status}</div>
            <div style={{ color: "#666" }}>Entry Year</div>
            <div>{profile.entryYear}</div>
          </div>
        )}
      </section>

      {/* Programs (declared & planned) */}
      {profile && (
        <section
          style={{ background: "#fff", border: "1px solid #eee", borderRadius: 12, padding: 16, marginTop: 16 }}
        >
          <h2 style={{ marginTop: 0 }}>Programs (Declared & Planned)</h2>
          {plans.length === 0 ? (
            <div>No programs found.</div>
          ) : (
            <div style={{ border: "1px solid #eee", borderRadius: 8, overflow: "hidden" }}>
              <div
                style={{
                  display: "grid",
                  gridTemplateColumns: "120px 130px 1fr 120px 120px",
                  background: "#fafafa",
                  fontWeight: 600,
                  padding: "8px 12px",
                }}
              >
                <div>Kind</div>
                <div>Code</div>
                <div>Name</div>
                <div>Declared</div>
                <div>Version</div>
              </div>
              {plans.map((p, i) => (
                <div
                  key={`${p.kind}-${p.code}-${i}`}
                  style={{
                    display: "grid",
                    gridTemplateColumns: "120px 130px 1fr 120px 120px",
                    padding: "8px 12px",
                    borderTop: "1px solid #f0f0f0",
                  }}
                >
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
      )}

      {/* Admission Requirements */}
      {profile && (
        <section
          style={{ background: "#fff", border: "1px solid #eee", borderRadius: 12, padding: 16, marginTop: 16 }}
        >
          <h2 style={{ marginTop: 0 }}>Restricted-program Admission Requirements</h2>
          <ReqTable data={admissionReqs} />
        </section>
      )}

      {/* Degree Requirements */}
      {profile && (
        <section
          style={{ background: "#fff", border: "1px solid #eee", borderRadius: 12, padding: 16, marginTop: 16 }}
        >
          <h2 style={{ marginTop: 0 }}>Degree Requirements</h2>
          <ReqTable data={degreeReqs} />
        </section>
      )}

      {/* Academic Summary */}
      {profile && (
        <section
          style={{ background: "#f6ffed", border: "1px solid #b7eb8f", borderRadius: 12, padding: 16, marginTop: 16 }}
        >
          <h2 style={{ marginTop: 0 }}>Academic Summary</h2>
          {!summary ? (
            <div>Loading...</div>
          ) : (
            <>
              <div style={{ display: "flex", gap: 16, flexWrap: "wrap", marginBottom: 8 }}>
                <div>
                  <strong>Standing:</strong> {summary.standing}
                </div>
                <div>
                  <strong>Cumulative GPA:</strong> {fmt.num(summary.cumulative.gpa ?? null)}
                </div>
                <div>
                  <strong>Cumulative Credits:</strong> {summary.cumulative.credits}
                </div>
              </div>

              <div style={{ border: "1px solid #eee", borderRadius: 8, overflow: "hidden" }}>
                <div
                  style={{
                    display: "grid",
                    gridTemplateColumns: "160px 1fr 1fr",
                    background: "#fafafa",
                    fontWeight: 600,
                    padding: "8px 12px",
                  }}
                >
                  <div>Term</div>
                  <div>Term GPA</div>
                  <div>Term Credits</div>
                </div>
                {summary.terms.map((t) => (
                  <div
                    key={t.term}
                    style={{
                      display: "grid",
                      gridTemplateColumns: "160px 1fr 1fr",
                      padding: "8px 12px",
                      borderTop: "1px solid #f0f0f0",
                    }}
                  >
                    <div>{t.term}</div>
                    <div>{fmt.num(t.gpa ?? null)}</div>
                    <div>{t.credits}</div>
                  </div>
                ))}
              </div>
            </>
          )}
        </section>
      )}

      {/* Term Schedule */}
      {profile && (
        <section
          style={{ background: "#fff", border: "1px solid #eee", borderRadius: 12, padding: 16, marginTop: 16 }}
        >
          <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 8 }}>
            <h2 style={{ margin: 0 }}>Term Schedule</h2>
            <select
              value={term}
              onChange={(e) => setTerm(e.target.value)}
              style={{ padding: "6px 8px", borderRadius: 6, border: "1px solid #ddd" }}
              disabled={busy}
            >
              <option value="2025-Fall">2025-Fall</option>
              <option value="2025-Spring">2025-Spring</option>
              <option value="2024-Fall">2024-Fall</option>
            </select>
          </div>

          <div style={{ border: "1px solid #eee", borderRadius: 8, overflow: "hidden" }}>
            <div
              style={{
                display: "grid",
                gridTemplateColumns: "160px 1fr 1fr 1fr 1fr",
                background: "#fafafa",
                fontWeight: 600,
                padding: "8px 12px",
              }}
            >
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
                <div
                  key={`${c.classId}`}
                  style={{
                    display: "grid",
                    gridTemplateColumns: "160px 1fr 1fr 1fr 1fr",
                    padding: "8px 12px",
                    borderTop: "1px solid #f0f0f0",
                  }}
                >
                  <div>
                    {c.subject}
                    {c.courseNum}.{c.section}
                  </div>
                  <div>{c.title}</div>
                  <div>{c.schedule}</div>
                  <div>{c.location}</div>
                  <div>{c.instructor || "-"}</div>
                </div>
              ))
            )}
          </div>
        </section>
      )}

      {/* Holds */}
      {profile && (
        <section
          style={{ background: "#fff", border: "1px solid #eee", borderRadius: 12, padding: 16, marginTop: 16 }}
        >
          <h2 style={{ marginTop: 0 }}>Registration Holds</h2>
          {holds.length === 0 ? (
            <div>No holds.</div>
          ) : (
            <div style={{ border: "1px solid #eee", borderRadius: 8, overflow: "hidden" }}>
              <div
                style={{
                  display: "grid",
                  gridTemplateColumns: "160px 1fr 200px",
                  background: "#fafafa",
                  fontWeight: 600,
                  padding: "8px 12px",
                }}
              >
                <div>Code</div>
                <div>Title</div>
                <div>Placed</div>
              </div>
              {holds.map((h, i) => (
                <div
                  key={`${h.code}-${i}`}
                  style={{
                    display: "grid",
                    gridTemplateColumns: "160px 1fr 200px",
                    padding: "8px 12px",
                    borderTop: "1px solid #f0f0f0",
                  }}
                >
                  <div>{h.code}</div>
                  <div>{h.title}</div>
                  <div>{fmt.date(h.placedAt)}</div>
                </div>
              ))}
            </div>
          )}
        </section>
      )}

      {/* Waived Time Conflicts */}
      {profile && (
        <section
          style={{ background: "#fff", border: "1px solid #eee", borderRadius: 12, padding: 16, marginTop: 16 }}
        >
          <h2 style={{ marginTop: 0 }}>Waived Time Conflicts</h2>
          {waivedConflicts.length === 0 ? (
            <div>No waived time conflicts.</div>
          ) : (
            <div style={{ border: "1px solid #eee", borderRadius: 8, overflow: "hidden" }}>
              <div
                style={{
                  display: "grid",
                  gridTemplateColumns: "160px 160px 140px 1fr 200px",
                  background: "#fafafa",
                  fontWeight: 600,
                  padding: "8px 12px",
                }}
              >
                <div>Class A</div>
                <div>Class B</div>
                <div>Term</div>
                <div>Waived By</div>
                <div>Timestamp</div>
              </div>
              {waivedConflicts.map((w, i) => (
                <div
                  key={`${w.classA}-${w.classB}-${i}`}
                  style={{
                    display: "grid",
                    gridTemplateColumns: "160px 160px 140px 1fr 200px",
                    padding: "8px 12px",
                    borderTop: "1px solid #f0f0f0",
                  }}
                >
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
      )}

      {/* Audit Log */}
      {profile && (
        <section
          style={{ background: "#fff", border: "1px solid #eee", borderRadius: 12, padding: 16, marginTop: 16 }}
        >
          <h2 style={{ marginTop: 0 }}>Audit Log</h2>
          {audit.length === 0 ? (
            <div>No log entries.</div>
          ) : (
            <div style={{ border: "1px solid #eee", borderRadius: 8, overflow: "hidden" }}>
              <div
                style={{
                  display: "grid",
                  gridTemplateColumns: "200px 140px 180px 1fr",
                  background: "#fafafa",
                  fontWeight: 600,
                  padding: "8px 12px",
                }}
              >
                <div>Timestamp</div>
                <div>Actor</div>
                <div>Action</div>
                <div>Details</div>
              </div>
              {audit.map((a) => (
                <div
                  key={a.id}
                  style={{
                    display: "grid",
                    gridTemplateColumns: "200px 140px 180px 1fr",
                    padding: "8px 12px",
                    borderTop: "1px solid #f0f0f0",
                  }}
                >
                  <div>{fmt.date(a.timestamp)}</div>
                  <div>{a.actorDisplay}</div>
                  <div>{a.action}</div>
                  <div style={{ whiteSpace: "pre-wrap" }}>{a.details || "-"}</div>
                </div>
              ))}
            </div>
          )}
        </section>
      )}

      {/* Academic calendar import (optional admin utility) */}
      <section
        style={{
          background: "#fafafa",
          border: "1px solid #eee",
          borderRadius: 12,
          padding: 16,
          marginTop: 16,
        }}
      >
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

      {/* System date test */}
      <section
        style={{
          background: "#fafafa",
          border: "1px solid #eee",
          borderRadius: 12,
          padding: 16,
          marginTop: 16,
        }}
      >
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

  /** file upload handler */
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

/** ===== Child components ===== */
function ReqTable({ data }: { data: RequirementStatus[] }) {
  if (!data || data.length === 0) return <div>No requirements.</div>;
  return (
    <div style={{ border: "1px solid #eee", borderRadius: 8, overflow: "hidden" }}>
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "140px 140px 1fr 120px 1fr",
          background: "#fafafa",
          fontWeight: 600,
          padding: "8px 12px",
        }}
      >
        <div>Program</div>
        <div>Req Code</div>
        <div>Title</div>
        <div>Status</div>
        <div>Using Classes</div>
      </div>
      {data.map((r, i) => (
        <div
          key={`${r.programCode}-${r.code}-${i}`}
          style={{
            display: "grid",
            gridTemplateColumns: "140px 140px 1fr 120px 1fr",
            padding: "8px 12px",
            borderTop: "1px solid #f0f0f0",
          }}
        >
          <div>{r.programCode}</div>
          <div>{r.code}</div>
          <div>{r.title}</div>
          <div style={{ textTransform: "capitalize" }}>{r.status}</div>
          <div style={{ fontSize: 13, color: "#666" }}>
            {r.usingClasses && r.usingClasses.length
              ? r.usingClasses
                  .map((c) => `${c.subject} ${c.courseNum}${c.term ? ` (${c.term}${c.grade ? ` ${c.grade}` : ""})` : ""}`)
                  .join(", ")
              : "-"}
          </div>
        </div>
      ))}
    </div>
  );
}