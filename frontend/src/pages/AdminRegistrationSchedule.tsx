import React, { useEffect, useMemo, useState } from "react";
import "./AdminRegistrationSchedule.css";

type ScheduleRow = {
  scheduleId: number;
  semester: string;
  year: number;
  classStanding: string | null;
  minCredits: number | null;
  maxCredits: number | null;
  startDate: string | null;
  endDate: string | null;
  description: string | null;
  createdBy?: number;
  createdByName?: string;
  createdAt?: string;
};

const STANDINGS = ["U1", "U2", "U3", "U4"];
const SEMESTERS = ["Spring", "Summer", "Fall", "Winter"];

const STANDING_TO_U: Record<string, string> = {
  U1: "U1",
  U2: "U2",
  U3: "U3",
  U4: "U4",
  FRESHMAN: "U1",
  SOPHOMORE: "U2",
  JUNIOR: "U3",
  SENIOR: "U4",
};

function toU(value: string | null | undefined): string {
  if (!value) return "";
  const key = value.toUpperCase();
  return STANDING_TO_U[key] ?? value;
}

function sortStanding(a: ScheduleRow, b: ScheduleRow): number {
  const order = { U4: 1, U3: 2, U2: 3, U1: 4 };
  const sa = toU(a.classStanding);
  const sb = toU(b.classStanding);
  return (order[sa as keyof typeof order] ?? 99) - (order[sb as keyof typeof order] ?? 99);
}

export default function AdminRegistrationSchedule() {
  const backendBase = import.meta.env.VITE_BACKEND_URL || "http://localhost:8080";

  // Term filter
  const [semester, setSemester] = useState<string>("Fall");
  const [year, setYear] = useState<number>(2025);

  // Table
  const [rows, setRows] = useState<ScheduleRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string>("");

  // Form
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState<ScheduleRow>({
    scheduleId: 0,
    semester: "Fall",
    year: 2025,
    classStanding: "U4",
    minCredits: null,
    maxCredits: null,
    startDate: "",
    endDate: "",
    description: "",
  });

  // Current user (resolved from existing backend endpoints without backend change)
  const [currentUserId, setCurrentUserId] = useState<number | null>(null);
  const [currentUserName, setCurrentUserName] = useState<string>("");

  // Resolve current user id:
  // 1) GET /api/profile/auth-info -> in this implementation, the "netId" field contains the user's email
  // 2) GET /api/profile/student-by-email?email=... -> returns dto with "id" (users.user_id), usable for createdById
  const fetchCurrentUserId = async () => {
    try {
      const authRes = await fetch(`${backendBase}/api/profile/auth-info`, {
        credentials: "include",
      });
      if (!authRes.ok) {
        const txt = await authRes.text().catch(() => "");
        throw new Error(`Failed to load auth-info (${authRes.status}) ${txt}`.trim());
      }
      const authJson = await authRes.json();
      const email: string | undefined = authJson?.netId; // "netId" carries email in the current backend

      if (!email) {
        throw new Error("Invalid current user payload: email missing");
      }

      const byEmailRes = await fetch(
        `${backendBase}/api/profile/student-by-email?email=${encodeURIComponent(email)}`,
        { credentials: "include" }
      );
      if (!byEmailRes.ok) {
        const txt = await byEmailRes.text().catch(() => "");
        throw new Error(`Failed to resolve user by email (${byEmailRes.status}) ${txt}`.trim());
      }
      const dto = await byEmailRes.json(); // StudentProfileDto: includes "id" and "name"
      if (typeof dto?.id !== "number") {
        throw new Error("Invalid user dto: id missing");
      }
      setCurrentUserId(dto.id);
      setCurrentUserName(dto?.name || "");
    } catch (err: any) {
      console.error(err);
      setErrorMsg(err?.message || "Failed to load current user");
      setCurrentUserId(null);
    }
  };

  // Keep form term synced with filter when creating a new record
  useEffect(() => {
    if (editingId === null) {
      setForm((f) => ({ ...f, semester, year }));
    }
  }, [semester, year, editingId]);

  const termLabel = useMemo(() => `${semester} ${year}`, [semester, year]);

  const toast = (msg: string) => window.alert(msg);

  // Fetch schedules for the selected term
  const fetchSchedules = async () => {
    setLoading(true);
    setErrorMsg("");
    try {
      const res = await fetch(
        `${backendBase}/api/admin/registration-schedules?semester=${encodeURIComponent(
          semester
        )}&year=${year}`,
        { credentials: "include" }
      );
      if (!res.ok) {
        const txt = await res.text().catch(() => "");
        throw new Error(`Failed to load schedules (${res.status}) ${txt || ""}`.trim());
      }

      const data: ScheduleRow[] = await res.json();
      const fixed = (Array.isArray(data) ? data : []).map((r) => ({
        ...r,
        classStanding: toU(r.classStanding),
      }));

      fixed.sort(sortStanding);
      setRows(fixed);
    } catch (e: any) {
      setErrorMsg(e?.message || "Failed to load schedules");
      setRows([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    // Resolve current user id first (even if it fails, still load the table)
    fetchCurrentUserId().finally(() => {
      fetchSchedules();
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const resetForm = () => {
    setEditingId(null);
    setForm({
      scheduleId: 0,
      semester,
      year,
      classStanding: "U4",
      minCredits: null,
      maxCredits: null,
      startDate: "",
      endDate: "",
      description: "",
    });
  };

  const validateForm = (): string | null => {
    if (!form.semester) return "Semester is required.";
    if (!form.year) return "Year is required.";
    if (!form.classStanding) return "Class standing is required.";
    if (form.startDate && form.endDate && form.startDate > form.endDate)
      return "Start date must be on or before end date.";
    if (currentUserId == null)
      return "Cannot determine current user. Please sign in again.";
    return null;
  };

  const orNull = (v: string | null | undefined) => (v && v.trim() !== "" ? v : null);

  // Create / update
  const submitForm = async () => {
    const v = validateForm();
    if (v) {
      toast(v);
      return;
    }

    const payload: Record<string, any> = {
      semester: form.semester,
      year: form.year,
      classStanding: toU(form.classStanding),
      minCredits: null,
      maxCredits: null,
      startDate: orNull(form.startDate),
      endDate: orNull(form.endDate),
      createdById: currentUserId, // resolved from current session
      description: orNull(form.description),
    };

    try {
      const url =
        editingId === null
          ? `${backendBase}/api/admin/registration-schedules`
          : `${backendBase}/api/admin/registration-schedules/${editingId}`;
      const method = editingId === null ? "POST" : "PUT";

      const res = await fetch(url, {
        method,
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });

      const data = await res.json().catch(() => ({}));
      if (!res.ok || data?.success === false) {
        throw new Error(data?.message || "Save failed");
      }

      toast(editingId === null ? "Created!" : "Updated!");
      await fetchSchedules();
      resetForm();
    } catch (e: any) {
      toast(e?.message || "Save failed");
    }
  };

  const onEdit = (row: ScheduleRow) => {
    setEditingId(row.scheduleId);
    setForm({
      ...row,
      classStanding: toU(row.classStanding),
      minCredits: row.minCredits ?? null,
      maxCredits: row.maxCredits ?? null,
      description: row.description ?? "",
    });
  };

  const onDelete = async (row: ScheduleRow) => {
    if (!window.confirm("Delete this schedule?")) return;
    try {
      const res = await fetch(
        `${backendBase}/api/admin/registration-schedules/${row.scheduleId}`,
        { method: "DELETE", credentials: "include" }
      );
      const data = await res.json().catch(() => ({}));
      if (!res.ok || data?.success === false) {
        throw new Error(data?.message || "Delete failed");
      }
      toast("Deleted.");
      await fetchSchedules();
    } catch (e: any) {
      toast(e?.message || "Delete failed");
    }
  };

  return (
    <div className="rs-page">
      <header className="rs-header">
        <h1>Registration Schedule</h1>
        <div className="rs-term">
          <label>
            Semester
            <select
              value={semester}
              onChange={(e) => setSemester(e.target.value)}
            >
              {SEMESTERS.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
          </label>
          <label>
            Year
            <input
              type="number"
              value={year}
              onChange={(e) => setYear(Number(e.target.value))}
              min={2000}
              max={2100}
            />
          </label>
          <button className="rs-btn" onClick={fetchSchedules}>
            Load {termLabel}
          </button>
        </div>
        {currentUserId != null ? (
          <div className="rs-hint">
            Signed in as: {currentUserName || "Current User"} (id: {currentUserId})
          </div>
        ) : (
          <div className="rs-error">{errorMsg || "Current user not resolved"}</div>
        )}
      </header>

      {/* Form */}
      <section className="rs-card">
        <div className="rs-card-header">
          <h2>{editingId === null ? "Create" : "Edit"} Schedule</h2>
          {editingId !== null && (
            <button className="rs-btn ghost" onClick={resetForm}>
              + New
            </button>
          )}
        </div>

        <div className="rs-grid">
          <label>
            Semester
            <select
              value={form.semester}
              onChange={(e) =>
                setForm((f) => ({ ...f, semester: e.target.value }))
              }
            >
              {SEMESTERS.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
          </label>

          <label>
            Year
            <input
              type="number"
              value={form.year}
              onChange={(e) =>
                setForm((f) => ({ ...f, year: Number(e.target.value) }))
              }
              min={2000}
              max={2100}
            />
          </label>

          <label>
            Class Standing
            <select
              value={form.classStanding ?? ""}
              onChange={(e) =>
                setForm((f) => ({ ...f, classStanding: e.target.value }))
              }
            >
              <option value="">-- select --</option>
              {STANDINGS.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
          </label>

          <label>
            Start Date (optional)
            <input
              type="date"
              value={form.startDate ?? ""}
              onChange={(e) =>
                setForm((f) => ({ ...f, startDate: e.target.value }))
              }
            />
          </label>

          <label>
            End Date (optional)
            <input
              type="date"
              value={form.endDate ?? ""}
              onChange={(e) =>
                setForm((f) => ({ ...f, endDate: e.target.value }))
              }
            />
          </label>

          <label className="rs-col-span">
            Description (optional)
            <input
              type="text"
              placeholder="Senior registration period"
              value={form.description ?? ""}
              onChange={(e) =>
                setForm((f) => ({ ...f, description: e.target.value }))
              }
            />
          </label>
        </div>

        <div className="rs-actions">
          <button className="rs-btn primary" onClick={submitForm}>
            {editingId === null ? "Create" : "Update"}
          </button>
          <button className="rs-btn" onClick={resetForm}>
            Reset
          </button>
        </div>
      </section>

      {/* Table */}
      <section className="rs-card">
        <div className="rs-card-header">
          <h2>Registration Schedules for {termLabel}</h2>
          {loading && <span className="rs-hint">Loading…</span>}
          {errorMsg && <span className="rs-error">{errorMsg}</span>}
        </div>

        {rows.length === 0 ? (
          <div className="rs-empty">No schedules for this term.</div>
        ) : (
          <div className="rs-table-wrap">
            <table className="rs-table">
              <thead>
                <tr>
                  <th>Standing</th>
                  <th>Credits</th>
                  <th>Start</th>
                  <th>End</th>
                  <th>Description</th>
                  <th>Created By</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr key={r.scheduleId}>
                    <td>{toU(r.classStanding) || "-"}</td>
                    <td>
                      {(r.minCredits ?? "-")} – {(r.maxCredits ?? "-")}
                    </td>
                    <td>{r.startDate ?? "-"}</td>
                    <td>{r.endDate ?? "-"}</td>
                    <td className="rs-ellipsis" title={r.description ?? ""}>
                      {r.description ?? "-"}
                    </td>
                    <td>{r.createdByName ?? "-"}</td>
                    <td>
                      <button className="rs-link" onClick={() => onEdit(r)}>
                        Edit
                      </button>
                      <span className="rs-sep">|</span>
                      <button
                        className="rs-link danger"
                        onClick={() => onDelete(r)}
                      >
                        Delete
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}