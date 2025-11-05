/*
 * TP: Used Cursor to implement roster viewing page for instructors
 */

import React, { useState, useEffect } from 'react';
import './Roster.css';
import { getCurrentUser } from '../utils/auth';

/**
 * Roster Component
 * Allows professors to view class rosters for their classes.
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

const Roster: React.FC = () => {
  const [classes, setClasses] = useState<Class[]>([]);
  const [selectedClass, setSelectedClass] = useState<Class | null>(null);
  const [roster, setRoster] = useState<RosterStudent[]>([]);
  const [loading, setLoading] = useState(true);
  const [rosterLoading, setRosterLoading] = useState(false);
  const [currentUser, setCurrentUser] = useState<any>(null);
  const [error, setError] = useState<string | null>(null);
  const [classStats, setClassStats] = useState<any>(null);

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
      fetchClassStats(selectedClass.classId);
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

      // Get instructor name from current user
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

  const fetchClassStats = async (classId: number) => {
    try {
      const response = await fetch(`${backendBase}/api/roster/stats/${classId}`, {
        credentials: 'include'
      });

      if (response.ok) {
        const data = await response.json();
        setClassStats(data);
      }
    } catch (error) {
      console.error('Error fetching class stats:', error);
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
      <div className="roster-page">
        <div className="loading">Loading classes...</div>
      </div>
    );
  }

  return (
    <div className="roster-page">
      <div className="roster-header">
        <h1>Class Roster</h1>
        <p>View student rosters for your classes</p>
      </div>

      {error && (
        <div className="error-message">
          {error}
        </div>
      )}

      <div className="roster-content">
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

        <div className="roster-section">
          {selectedClass ? (
            <>
              <div className="roster-header-info">
                <h2>
                  {selectedClass.department} {selectedClass.courseNum}.{selectedClass.section} - {selectedClass.title}
                </h2>
                <p className="class-info">
                  {selectedClass.semester} {selectedClass.year} • {selectedClass.schedule} • {selectedClass.location}
                </p>
                {classStats && (
                  <div className="class-stats">
                    <div className="stat-item">
                      <strong>Capacity:</strong> {classStats.currentCapacity || 0} / {classStats.maxCapacity || 0}
                    </div>
                    <div className="stat-item">
                      <strong>Active Enrollments:</strong> {classStats.activeEnrollments || 0}
                    </div>
                    <div className="stat-item">
                      <strong>Available Spots:</strong> {classStats.availableSpots || 0}
                    </div>
                    <div className="stat-item">
                      <strong>Status:</strong> {classStats.isFull ? 'Full' : 'Available'}
                    </div>
                  </div>
                )}
              </div>

              {rosterLoading ? (
                <div className="loading">Loading roster...</div>
              ) : roster.length === 0 ? (
                <div className="no-roster">
                  <p>No students enrolled in this class.</p>
                </div>
              ) : (
                <div className="roster-table-container">
                  <table className="roster-table">
                    <thead>
                      <tr>
                        <th>Name</th>
                        <th>Email</th>
                        <th>Student ID</th>
                        <th>Enrollment Date</th>
                        <th>Status</th>
                        <th>Grade</th>
                      </tr>
                    </thead>
                    <tbody>
                      {roster.map((student) => (
                        <tr key={student.rosterId}>
                          <td>{student.firstName} {student.lastName}</td>
                          <td>{student.email}</td>
                          <td>{student.userId}</td>
                          <td>{formatDate(student.enrollmentDate)}</td>
                          <td>
                            <span className={`status-badge ${student.status.toLowerCase()}`}>
                              {student.status}
                            </span>
                          </td>
                          <td>
                            {student.grade || '-'}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  <div className="roster-summary">
                    <p>Total Students: {roster.length}</p>
                  </div>
                </div>
              )}
            </>
          ) : (
            <div className="no-class-selected">
              <p>Select a class to view its roster</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default Roster;

