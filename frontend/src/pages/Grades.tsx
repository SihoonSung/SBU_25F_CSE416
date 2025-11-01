import React, { useState, useEffect } from 'react';
import './Grades.css';
import { getCurrentUser } from '../utils/auth';

/**
 * Grades Component
 * Allows professors to enter and manage grades for their classes.
 */

interface Class {
  classId: number;
  department: string;
  courseNum: number;
  section: string;
  title: string;
  instructor: string;
  schedule: string;
  location: string;
  semester: string;
  year: number;
}

interface RosterStudent {
  rosterId: number;
  userId: number;
  firstName: string;
  lastName: string;
  email: string;
  enrollmentDate: string;
  status: string;
  grade: string | null;
}

const Grades: React.FC = () => {
  const [classes, setClasses] = useState<Class[]>([]);
  const [selectedClass, setSelectedClass] = useState<Class | null>(null);
  const [roster, setRoster] = useState<RosterStudent[]>([]);
  const [loading, setLoading] = useState(true);
  const [rosterLoading, setRosterLoading] = useState(false);
  const [currentUser, setCurrentUser] = useState<any>(null);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [editingGrades, setEditingGrades] = useState<{[key: number]: string}>({});

  const backendBase = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8080';

  useEffect(() => {
    fetchCurrentUser();
  }, []);

  useEffect(() => {
    if (currentUser) {
      fetchClasses();
    }
  }, [currentUser]);

  useEffect(() => {
    if (selectedClass) {
      fetchRoster(selectedClass.classId);
    }
  }, [selectedClass]);

  const fetchCurrentUser = async () => {
    try {
      const user = await getCurrentUser();
      setCurrentUser(user.user);
    } catch (error) {
      console.error('Error fetching current user:', error);
      setError('Failed to load user information');
    }
  };

  const fetchClasses = async () => {
    try {
      setLoading(true);
      if (!currentUser) return;

      const instructorName = `${currentUser.firstName} ${currentUser.lastName}`.trim();
      
      const response = await fetch(`${backendBase}/api/classes/instructor/${encodeURIComponent(instructorName)}`, {
        credentials: 'include'
      });

      if (response.ok) {
        const data = await response.json();
        setClasses(data);
        if (data.length > 0 && !selectedClass) {
          setSelectedClass(data[0]);
        }
      } else {
        setError('Failed to load classes');
      }
    } catch (error) {
      console.error('Error fetching classes:', error);
      setError('Error loading classes');
    } finally {
      setLoading(false);
    }
  };

  const fetchRoster = async (classId: number) => {
    try {
      setRosterLoading(true);
      const response = await fetch(`${backendBase}/api/roster/class/${classId}`, {
        credentials: 'include'
      });

      if (response.ok) {
        const data = await response.json();
        setRoster(data || []);
        
        // Initialize editing grades with current grades
        const grades: {[key: number]: string} = {};
        data.forEach((student: RosterStudent) => {
          grades[student.userId] = student.grade || '';
        });
        setEditingGrades(grades);
      } else {
        setError('Failed to load roster');
        setRoster([]);
      }
    } catch (error) {
      console.error('Error fetching roster:', error);
      setError('Error loading roster');
      setRoster([]);
    } finally {
      setRosterLoading(false);
    }
  };

  const handleGradeChange = (userId: number, grade: string) => {
    setEditingGrades({...editingGrades, [userId]: grade});
  };

  const handleSaveGrade = async (classId: number, studentId: number, grade: string) => {
    if (!currentUser || !currentUser.userId) {
      setError('User information not available');
      return;
    }

    try {
      const response = await fetch(`${backendBase}/api/roster/class/${classId}/student/${studentId}/grade`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        credentials: 'include',
        body: JSON.stringify({
          instructorId: currentUser.userId,
          grade: grade.trim()
        })
      });

      const data = await response.json();

      if (response.ok && data.success) {
        setSuccess(`Grade saved successfully for ${data.studentName || 'student'}`);
        setError(null);
        // Refresh roster to get updated grades
        await fetchRoster(classId);
      } else {
        setError(data.message || 'Failed to save grade');
        setSuccess(null);
      }
    } catch (error) {
      console.error('Error saving grade:', error);
      setError('Error saving grade');
      setSuccess(null);
    }
  };

  const formatDate = (dateString: string) => {
    try {
      return new Date(dateString).toLocaleDateString();
    } catch {
      return dateString;
    }
  };

  if (loading) {
    return (
      <div className="grades-page">
        <div className="loading">Loading classes...</div>
      </div>
    );
  }

  return (
    <div className="grades-page">
      <div className="grades-header">
        <h1>Grade Management</h1>
        <p>Enter and manage grades for your classes</p>
      </div>

      {error && (
        <div className="error-message">
          {error}
        </div>
      )}

      {success && (
        <div className="success-message">
          {success}
        </div>
      )}

      <div className="grades-content">
        <div className="classes-section">
          <h2>Your Classes</h2>
          {classes.length === 0 ? (
            <div className="no-classes">
              <p>No classes found. You may not be assigned as an instructor for any classes.</p>
            </div>
          ) : (
            <div className="classes-list">
              {classes.map((cls) => (
                <div
                  key={cls.classId}
                  className={`class-card ${selectedClass?.classId === cls.classId ? 'selected' : ''}`}
                  onClick={() => setSelectedClass(cls)}
                >
                  <div className="class-code">
                    {cls.department} {cls.courseNum}.{cls.section}
                  </div>
                  <div className="class-title">{cls.title}</div>
                  <div className="class-term">
                    {cls.semester} {cls.year}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="grades-section">
          {selectedClass ? (
            <>
              <div className="grades-header-info">
                <h2>
                  {selectedClass.department} {selectedClass.courseNum}.{selectedClass.section} - {selectedClass.title}
                </h2>
                <p className="class-info">
                  {selectedClass.semester} {selectedClass.year} • {selectedClass.schedule} • {selectedClass.location}
                </p>
              </div>

              {rosterLoading ? (
                <div className="loading">Loading roster...</div>
              ) : roster.length === 0 ? (
                <div className="no-roster">
                  <p>No students enrolled in this class.</p>
                </div>
              ) : (
                <div className="grades-table-container">
                  <table className="grades-table">
                    <thead>
                      <tr>
                        <th>Name</th>
                        <th>Email</th>
                        <th>Student ID</th>
                        <th>Status</th>
                        <th>Grade</th>
                        <th>Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {roster.map((student) => (
                        <tr key={student.rosterId}>
                          <td>{student.firstName} {student.lastName}</td>
                          <td>{student.email}</td>
                          <td>{student.userId}</td>
                          <td>
                            <span className={`status-badge ${student.status.toLowerCase()}`}>
                              {student.status}
                            </span>
                          </td>
                          <td>
                            <input
                              type="text"
                              className="grade-input"
                              value={editingGrades[student.userId] || ''}
                              onChange={(e) => handleGradeChange(student.userId, e.target.value)}
                              placeholder="A, B+, C, etc."
                              maxLength={3}
                            />
                          </td>
                          <td>
                            <button
                              className="save-grade-btn"
                              onClick={() => handleSaveGrade(selectedClass.classId, student.userId, editingGrades[student.userId] || '')}
                              disabled={!editingGrades[student.userId] || editingGrades[student.userId].trim() === ''}
                            >
                              Save
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  <div className="grades-summary">
                    <p>Total Students: {roster.length}</p>
                  </div>
                </div>
              )}
            </>
          ) : (
            <div className="no-class-selected">
              <p>Select a class to manage grades</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default Grades;

