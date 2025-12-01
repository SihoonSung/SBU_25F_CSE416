/*
 * TP: Used Cursor to implement course planner page with semester planning functionality
 */

import { useMemo, useState } from 'react'
import './Planner.css'
import { getDefaultSemester, getDefaultYear, PLANNER_DEFAULTS } from '../constants'

type Course = { code: string; title: string; credits: number }

export default function Planner() {
  const [required, setRequired] = useState({
    semester: getDefaultSemester(),
    year: String(getDefaultYear()),
    minCredits: PLANNER_DEFAULTS.MIN_CREDITS,
    maxCredits: PLANNER_DEFAULTS.MAX_CREDITS,
    dayOff: 'None',
  })
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
    daysToAvoid: 'None',
    daysToPrefer: 'None',
    modality: 'Any',
    preferredTimes: [] as Array<{day:string; time:string}>,
    preferDays: [] as string[],
    preferMorning: false,
    onlineOnly: false,
  })
  const [manualCourses, setManualCourses] = useState<string>('')
  const currentYear = getDefaultYear();
  const nextYear = currentYear + 1;
  const defaultSemester = getDefaultSemester();
  const [plan, setPlan] = useState<{ [term: string]: Course[] }>({
    [`Spring ${currentYear}`]: [],
    [`Fall ${currentYear}`]: [],
    [`Spring ${nextYear}`]: [],
  })

  const totalByTerm = useMemo(() =>
    Object.fromEntries(
      Object.entries(plan).map(([t, arr]) => [t, arr.reduce((s, c) => s + c.credits, 0)])
    ), [plan])

  const parseManual = (text: string): Course[] => {
    // 입력 형식: CSE 373|3, CSE 478|3
    return text
      .split(',')
      .map(s => s.trim())
      .filter(Boolean)
      .map(line => {
        const [codeRaw, creditsRaw] = line.split('|').map(x => x?.trim())
        const code = (codeRaw || '').replace(/\s+/g, ' ')
        const credits = Number(creditsRaw || 3) || 3
        return { code, title: code, credits }
      })
  }

  const handleGenerate = () => {
    const seeds: Course[] = parseManual(manualCourses)
    const buckets = Object.keys(plan)
    const next: { [k: string]: Course[] } = Object.fromEntries(buckets.map(b => [b, []]))
    let idx = 0
    for (const c of seeds) {
      const bucket = buckets[idx % buckets.length]
      next[bucket] = [...next[bucket], c]
      idx++
    }
    setPlan(next)
  }

  const pill = (c: Course) => (
    <div key={c.code} className="pill">
      <div className="pill-code">{c.code}</div>
      <div className="pill-credits">{c.credits} credits</div>
    </div>
  )

  const col = (term: string) => (
    <div className="term-col" key={term}>
      <div className="term-header">{term}</div>
      <div className="pill-list">{plan[term].map(pill)}</div>
      <div className="term-total">Total Credits: {totalByTerm[term] || 0}</div>
    </div>
  )

  const toggleDay = (d: string) => {
    setOptions(o => ({
      ...o,
      preferDays: o.preferDays.includes(d) ? o.preferDays.filter(x => x !== d) : [...o.preferDays, d]
    }))
  }

  const updateSbcScore = (i: number, v: number) => {
    setOptions(o => ({
      ...o,
      sbcPrefs: o.sbcPrefs.map((p, idx) => idx === i ? { ...p, score: v } : p)
    }))
  }

  const addPreferredTime = () => {
    setOptions(o => ({
      ...o,
      preferredTimes: [...o.preferredTimes, { day: 'Mon', time: '' }]
    }))
  }

  const updatePreferredTime = (idx: number, key: 'day'|'time', v: string) => {
    setOptions(o => ({
      ...o,
      preferredTimes: o.preferredTimes.map((t, i) => i === idx ? { ...t, [key]: v } : t)
    }))
  }

  const removePreferredTime = (idx: number) => {
    setOptions(o => ({
      ...o,
      preferredTimes: o.preferredTimes.filter((_, i) => i !== idx)
    }))
  }

  return (
    <div className="planner-page">
      <div className="generator-panel">
        <h2>Plan Generator</h2>

        <div className="card">
          <div className="card-title">Required</div>
          <div className="grid-2">
            <label>
              <span>Semester</span>
              <select value={required.semester} onChange={e => setRequired({ ...required, semester: e.target.value })}>
                <option>Spring</option>
                <option>Fall</option>
              </select>
            </label>
            <label>
              <span>Year</span>
              <input value={required.year} onChange={e => setRequired({ ...required, year: e.target.value })} />
            </label>
            <label>
              <span>Min Credits</span>
              <input type="number" value={required.minCredits}
                     onChange={e => setRequired({ ...required, minCredits: Number(e.target.value) })} />
            </label>
            <label>
              <span>Max Credits</span>
              <input type="number" value={required.maxCredits}
                     onChange={e => setRequired({ ...required, maxCredits: Number(e.target.value) })} />
            </label>
            <label>
              <span>Day Off</span>
              <select value={required.dayOff} onChange={e => setRequired({ ...required, dayOff: e.target.value })}>
                {['None','Mon','Tue','Wed','Thu','Fri'].map(d => <option key={d}>{d}</option>)}
              </select>
            </label>
          </div>
        </div>

        <div className="card">
          <div className="card-title">Options</div>
          <label>
            <span>Preferred Courses</span>
            <input placeholder="e.g., CSE 355, AMS 311, ..." value={options.preferredCourses}
                   onChange={e => setOptions({ ...options, preferredCourses: e.target.value })} />
          </label>
          <label>
            <span>Avoid Courses</span>
            <input placeholder="e.g., CSE 353, AMS 310, ..." value={options.avoidCourses}
                   onChange={e => setOptions({ ...options, avoidCourses: e.target.value })} />
          </label>

          <div className="subsection">
            <div className="sub-title">SBC Areas <span className="hint">Enter your preference rating for SBC (1-5 points)</span></div>
            <div className="sbc-grid">
              {options.sbcPrefs.map((p, i) => (
                <div key={p.area} className="sbc-row">
                  <div className="sbc-area">{p.area}</div>
                  <select value={p.score} onChange={e => updateSbcScore(i, Number(e.target.value))}>
                    {[1,2,3,4,5].map(n => <option key={n} value={n}>{n}</option>)}
                  </select>
                </div>
              ))}
            </div>
          </div>
          <div className="chips">
            {['M','T','W','R','F'].map(d => (
              <button key={d}
                      className={`chip ${options.preferDays.includes(d) ? 'active' : ''}`}
                      onClick={() => toggleDay(d)}>{d}</button>
            ))}
          </div>
          <div className="grid-2">
            <label className="switch-line">
              <input type="checkbox" checked={options.preferMorning}
                     onChange={e => setOptions({ ...options, preferMorning: e.target.checked })} />
              <span>Morning preferred</span>
            </label>
            <label className="switch-line">
              <input type="checkbox" checked={options.onlineOnly}
                     onChange={e => setOptions({ ...options, onlineOnly: e.target.checked })} />
              <span>Online only</span>
            </label>
          </div>

          <div className="grid-3">
            <label>
              <span>Days to Avoid</span>
              <select value={options.daysToAvoid} onChange={e => setOptions({ ...options, daysToAvoid: e.target.value })}>
                {['None','Mon','Tue','Wed','Thu','Fri'].map(d => <option key={d}>{d}</option>)}
              </select>
            </label>
            <label>
              <span>Days to Prefer</span>
              <select value={options.daysToPrefer} onChange={e => setOptions({ ...options, daysToPrefer: e.target.value })}>
                {['None','Mon','Tue','Wed','Thu','Fri'].map(d => <option key={d}>{d}</option>)}
              </select>
            </label>
            <label>
              <span>Modality</span>
              <select value={options.modality} onChange={e => setOptions({ ...options, modality: e.target.value })}>
                {['Any','Online','In-person','Hybrid'].map(m => <option key={m}>{m}</option>)}
              </select>
            </label>
          </div>

          <div className="subsection">
            <div className="sub-title">Preferred Time</div>
            <div className="time-list">
              {options.preferredTimes.map((t, i) => (
                <div key={i} className="time-row">
                  <select value={t.day} onChange={e => updatePreferredTime(i, 'day', e.target.value)}>
                    {['Mon','Tue','Wed','Thu','Fri'].map(d => <option key={d}>{d}</option>)}
                  </select>
                  <input placeholder="e.g., 09:00-15:00" value={t.time} onChange={e => updatePreferredTime(i, 'time', e.target.value)} />
                  <button type="button" className="icon-btn" onClick={() => removePreferredTime(i)}>×</button>
                </div>
              ))}
              <button type="button" className="add-time" onClick={addPreferredTime}>+ Add</button>
            </div>
          </div>
        </div>

        <div className="card">
          <div className="card-title">Add Courses</div>
          <textarea
            placeholder="e.g., CSE 373|3, CSE 327|3, WRT 102|3"
            value={manualCourses}
            onChange={e => setManualCourses(e.target.value)}
          />
          <button className="apply-btn" onClick={handleGenerate}>Apply</button>
        </div>
      </div>

      <div className="plan-panel">
        <h2>Generated Plan</h2>
        <div className="term-grid">
          {Object.keys(plan).map(col)}
        </div>
      </div>
    </div>
  )
}


