import { useEffect, useMemo, useState } from "react";

type StudentProfile = {
  id: number;
  netId: string;
  name: string;
  major: string;
  email: string;
  status: string;
  entryYear: string;
};

type AcademicEvent = {
  date: string;
  title: string;
  type: string;
};

const BASE = "http://localhost:8080";
const USE_MOCK_WHEN_FAIL = true; 

// ---- mock data ----
const MOCK_PROFILE: StudentProfile = {
  id: 1,
  netId: "jsyoon",
  name: "Jae Seong Yoon mock",
  major: "Computer Science",
  email: "jsyoon@stonybrook.edu",
  status: "Active",
  entryYear: "2023",
};

const MOCK_EVENTS: AcademicEvent[] = [
  { date: "2025-01-27", title: "Spring Classes Begin", type: "TERM" },
  { date: "2025-02-05", title: "Add/Drop Deadline", type: "DEADLINE" },
];

export default function ProfilePage() {
  const [profile, setProfile] = useState<StudentProfile | null>(null);
  const [events, setEvents] = useState<AcademicEvent[]>([]);
  const [msg, setMsg] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [sysDate, setSysDate] = useState<string>("");
  const [overridden, setOverridden] = useState(false);

  // Generic safe fetch wrapper 
  const safeFetch = useMemo(
    () =>
      async (input: RequestInfo, init?: RequestInit) => {
        try {
          const res = await fetch(input, {
            credentials: "include", 
            ...init,
          });
          const text = await res.text();
          const data = text ? JSON.parse(text) : null;
          if (!res.ok) throw new Error(data?.message || `HTTP ${res.status}`);
          return data;
        } catch (e: any) {
          throw new Error(e?.message || "Failed to fetch");
        }
      },
    []
  );

  // Load data on mount
  useEffect(() => {
    (async () => {
      setMsg(null);
      try {
        const [p, ev, now] = await Promise.all([
          safeFetch(`${BASE}/api/profile/me`),
          safeFetch(`${BASE}/api/admin/academic-calendar`).catch(() => []),
          safeFetch(`${BASE}/api/admin/system-date`).catch(() => null),
        ]);
        setProfile(p);
        setEvents(Array.isArray(ev) ? ev : []);
        if (now) {
          setSysDate(now.currentDate ?? "");
          setOverridden(!!now.overridden);
        }
      } catch (e: any) {
        if (USE_MOCK_WHEN_FAIL) {
          setProfile(MOCK_PROFILE);
          setEvents(MOCK_EVENTS);
          setMsg("Backend unreachable — showing mock data.");
        } else {
          setMsg(e.message);
        }
      }
    })();
  }, [safeFetch]);

  // Upload YAML
  const onUploadYaml = async (file: File | null) => {
    if (!file) return;
    setBusy(true);
    setMsg(null);
    try {
      const fd = new FormData();
      fd.append("file", file);
      const result = await safeFetch(
        `${BASE}/api/admin/academic-calendar/import`,
        { method: "POST", body: fd }
      );
      setMsg(`Imported: ${result.imported} (skipped ${result.skipped})`);
      const ev = await safeFetch(`${BASE}/api/admin/academic-calendar`);
      setEvents(Array.isArray(ev) ? ev : []);
    } catch (e: any) {
      setMsg(`Upload failed: ${e.message}`);
    } finally {
      setBusy(false);
    }
  };

  // System date set/clear
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
      const data = await safeFetch(`${BASE}/api/admin/system-date`, {
        method: "DELETE",
      });
      setMsg("Override cleared");
      setSysDate(data.currentDate ?? "");
      setOverridden(false);
    } catch (e: any) {
      setMsg(`Failed to clear override: ${e.message}`);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div style={{ maxWidth: 980, margin: "24px auto", padding: "0 16px" }}>
      <h1 style={{ marginBottom: 8 }}>Student Profile & Support Features</h1>
      <p style={{ color: "#666", marginTop: 0 }}>
        View your basic profile, upload the academic calendar, test the system date,
        and check error handling.
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

      {/* Profile */}
      <section
        style={{
          background: "#fafafa",
          border: "1px solid #eee",
          borderRadius: 12,
          padding: 16,
          marginTop: 16,
        }}
      >
        <h2 style={{ marginTop: 0 }}>Basic Student Profile</h2>
        {!profile ? (
          <div>Loading...</div>
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

      {/* Academic Calendar */}
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
          <input
            type="file"
            accept=".yaml,.yml"
            onChange={(e) => onUploadYaml(e.target.files?.[0] || null)}
            disabled={busy}
          />
          <span style={{ fontSize: 12, color: "#888" }}>
            Fields: date (yyyy-MM-dd), title, type
          </span>
        </div>

        <div style={{ marginTop: 12 }}>
          <strong>Current Events ({events.length})</strong>
          <ul style={{ marginTop: 8 }}>
            {events.map((ev, i) => (
              <li key={`${ev.date}-${ev.title}-${i}`}>
                [{ev.type}] {ev.date} — {ev.title}
              </li>
            ))}
            {!events.length && <li>No events imported</li>}
          </ul>
        </div>
      </section>

      {/* System Date */}
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
          <input
            type="date"
            value={sysDate ?? ""}
            onChange={(e) => setSysDate(e.target.value)}
            disabled={busy}
          />
          <button onClick={applySystemDate} disabled={busy || !sysDate}>
            Apply Date
          </button>
          <button onClick={clearSystemDate} disabled={busy || !overridden}>
            Clear Override
          </button>
          <span
            style={{
              fontSize: 12,
              color: overridden ? "#fa541c" : "#52c41a",
              marginLeft: 8,
            }}
          >
            {overridden ? "Overridden" : "System Time"}
          </span>
        </div>
      </section>

      {/* Help */}
      <section
        style={{
          background: "#f6ffed",
          border: "1px solid #b7eb8f",
          borderRadius: 12,
          padding: 16,
          marginTop: 16,
        }}
      >
        <h3 style={{ marginTop: 0 }}>Help & Notes</h3>
        <ul style={{ margin: 0, paddingLeft: 18 }}>
          <li>
            If upload fails, valid records are still imported and skipped items will be shown in the message above.
          </li>
          <li>
            If the backend is down or CORS is blocked, you’ll see an error message at the top.
          </li>
          <li>
            System date override resets when the backend restarts (temporary memory storage).
          </li>
        </ul>
      </section>
    </div>
  );
}