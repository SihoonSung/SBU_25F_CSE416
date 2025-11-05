/*
 * TP: Used Cursor to implement user management page with search, filter, and import functionality
 */

import React, { useState, useEffect } from 'react';
import './UserManagement.css';

/**
 * User Management Component
 * Provides interface for managing users, importing user data, and user administration.
 */

interface User {
  userId: number;
  email: string;
  firstName: string;
  lastName: string;
  fullName: string;
  role: string;
  isActive: boolean;
  studentId?: string;
  major?: string;
  gpa?: number;
  graduationYear?: number;
  department?: string;
  officeLocation?: string;
  officeHours?: string;
  createdAt: string;
  lastLogin?: string;
}

const UserManagement: React.FC = () => {
  const [users, setUsers] = useState<User[]>([]);
  const [filteredUsers, setFilteredUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [roleFilter, setRoleFilter] = useState('');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [importMessage, setImportMessage] = useState('');
  const [statistics, setStatistics] = useState<any>(null);
  const [useBackendSearch, setUseBackendSearch] = useState(false);
  const [searchFilters, setSearchFilters] = useState({
    name: '',
    role: '',
    major: '',
    minor: '',
    graduationTerm: '',
    advisorLevel: '',
    academicUnit: ''
  });

  const backendBase = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8080';

  useEffect(() => {
    fetchUsers();
    fetchStatistics();
  }, []);

  useEffect(() => {
    filterUsers();
  }, [users, searchTerm, roleFilter, useBackendSearch, searchFilters]);

  const fetchUsers = async () => {
    try {
      const response = await fetch(`${backendBase}/api/users`, {
        credentials: 'include'
      });
      
      if (response.ok) {
        const data = await response.json();
        setUsers(data);
      } else {
        console.error('Failed to fetch users');
      }
    } catch (error) {
      console.error('Error fetching users:', error);
    } finally {
      setLoading(false);
    }
  };

  const fetchStatistics = async () => {
    try {
      const response = await fetch(`${backendBase}/api/users/statistics`, {
        credentials: 'include'
      });
      
      if (response.ok) {
        const data = await response.json();
        setStatistics(data);
      }
    } catch (error) {
      console.error('Error fetching statistics:', error);
    }
  };

  const filterUsers = async () => {
    if (useBackendSearch && (searchFilters.name || searchFilters.role || searchFilters.major || 
        searchFilters.minor || searchFilters.graduationTerm || searchFilters.advisorLevel || 
        searchFilters.academicUnit)) {
      // Use backend search API
      try {
        const params = new URLSearchParams();
        if (searchFilters.name) params.append('name', searchFilters.name);
        if (searchFilters.role) params.append('role', searchFilters.role);
        if (searchFilters.major) params.append('major', searchFilters.major);
        if (searchFilters.minor) params.append('minor', searchFilters.minor);
        if (searchFilters.graduationTerm) params.append('graduationTerm', searchFilters.graduationTerm);
        if (searchFilters.advisorLevel) params.append('advisorLevel', searchFilters.advisorLevel);
        if (searchFilters.academicUnit) params.append('academicUnit', searchFilters.academicUnit);
        
        const response = await fetch(`${backendBase}/api/users/search?${params.toString()}`, {
          credentials: 'include'
        });
        
        if (response.ok) {
          const data = await response.json();
          // Transform backend response to match frontend User interface
          const transformedUsers = data.map((user: any) => ({
            userId: user.userId,
            email: user.email,
            firstName: user.firstName,
            lastName: user.lastName,
            fullName: `${user.firstName} ${user.lastName}`,
            role: user.role,
            isActive: user.isActive,
            studentId: user.studentId,
            major: user.major,
            department: user.department,
            createdAt: user.createdAt
          }));
          setFilteredUsers(transformedUsers);
        }
      } catch (error) {
        console.error('Error searching users:', error);
        // Fallback to frontend filtering
        filterUsersFrontend();
      }
    } else {
      // Use frontend filtering
      filterUsersFrontend();
    }
  };

  const filterUsersFrontend = () => {
    let filtered = users;

    if (searchTerm) {
      filtered = filtered.filter(user =>
        user.fullName.toLowerCase().includes(searchTerm.toLowerCase()) ||
        user.email.toLowerCase().includes(searchTerm.toLowerCase()) ||
        (user.studentId && user.studentId.toLowerCase().includes(searchTerm.toLowerCase()))
      );
    }

    if (roleFilter) {
      filtered = filtered.filter(user => user.role === roleFilter);
    }

    setFilteredUsers(filtered);
  };

  const handleFileUpload = async () => {
    if (!selectedFile) {
      setImportMessage('Please select a file');
      return;
    }

    const formData = new FormData();
    formData.append('file', selectedFile);

    try {
      const response = await fetch(`${backendBase}/api/users/import`, {
        method: 'POST',
        body: formData,
        credentials: 'include'
      });

      const contentType = response.headers.get('content-type');
      let data;
      
      if (contentType && contentType.includes('application/json')) {
        try {
          data = await response.json();
          console.log('Import response:', data);
        } catch (jsonError) {
          setImportMessage(`Error: Failed to parse response`);
          console.error('Error parsing JSON response:', jsonError);
          return;
        }
      } else {
        // Response is not JSON
        const text = await response.text();
        console.error('Non-JSON response:', response.status, text);
        setImportMessage(`Error: ${response.status === 500 ? 'Internal Server Error' : text || 'Unknown error'}`);
        return;
      }

      if (response.ok) {
        setImportMessage(`Successfully imported ${data.importedCount} users`);
        fetchUsers();
        fetchStatistics();
        setSelectedFile(null);
      } else {
        // Show detailed error information
        const errorMsg = data.error || data.message || data.message || 'Unknown error';
        const errorDetails = data.exception ? ` (${data.exception})` : '';
        const fullError = `${errorMsg}${errorDetails}`;
        
        console.error('Import error details:', {
          status: response.status,
          statusText: response.statusText,
          data: data,
          responseHeaders: Object.fromEntries(response.headers.entries())
        });
        
        setImportMessage(`Error: ${fullError}. Please check the browser console and backend logs for more details.`);
      }
    } catch (error) {
      setImportMessage('Error uploading file: Network error or server unavailable');
      console.error('Error uploading file:', error);
    }
  };

  const toggleUserStatus = async (userId: number, isActive: boolean) => {
    try {
      const endpoint = isActive ? 'deactivate' : 'activate';
      const response = await fetch(`${backendBase}/api/users/${userId}/${endpoint}`, {
        method: 'PUT',
        credentials: 'include'
      });

      if (response.ok) {
        fetchUsers();
        fetchStatistics();
      } else {
        console.error('Failed to update user status');
      }
    } catch (error) {
      console.error('Error updating user status:', error);
    }
  };

  const handleExportUser = async (userId: number) => {
    try {
      const response = await fetch(`${backendBase}/api/users/${userId}/export`, {
        credentials: 'include'
      });
      
      if (response.ok) {
        const data = await response.json();
        // Create downloadable JSON file
        const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `user_${userId}_export.json`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
      } else {
        alert('Failed to export user data');
      }
    } catch (error) {
      console.error('Error exporting user:', error);
      alert('Error exporting user data');
    }
  };

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleDateString();
  };

  if (loading) {
    return <div className="loading">Loading users...</div>;
  }

  return (
    <div className="user-management">
      <div className="user-management-header">
        <h1>User Management</h1>
        <p>Manage system users and their roles</p>
      </div>

      {statistics && (
        <div className="statistics-grid">
          <div className="stat-card">
            <h3>Total Users</h3>
            <p className="stat-number">{statistics.totalUsers}</p>
          </div>
          <div className="stat-card">
            <h3>Active Users</h3>
            <p className="stat-number">{statistics.activeUsers}</p>
          </div>
          <div className="stat-card">
            <h3>Students</h3>
            <p className="stat-number">{statistics.students}</p>
          </div>
          <div className="stat-card">
            <h3>Instructors</h3>
            <p className="stat-number">{statistics.instructors}</p>
          </div>
          <div className="stat-card">
            <h3>Advisors</h3>
            <p className="stat-number">{statistics.advisors}</p>
          </div>
          <div className="stat-card">
            <h3>Registrars</h3>
            <p className="stat-number">{statistics.registrars}</p>
          </div>
        </div>
      )}

      <div className="user-management-controls">
        <div className="search-mode-toggle">
          <label>
            <input
              type="checkbox"
              checked={useBackendSearch}
              onChange={(e) => setUseBackendSearch(e.target.checked)}
            />
            Use Advanced Search
          </label>
        </div>

        {useBackendSearch ? (
          <div className="advanced-search-filters">
            <input
              type="text"
              placeholder="Name"
              value={searchFilters.name}
              onChange={(e) => setSearchFilters({...searchFilters, name: e.target.value})}
              className="search-input"
            />
            <select
              value={searchFilters.role}
              onChange={(e) => setSearchFilters({...searchFilters, role: e.target.value})}
              className="role-filter"
            >
              <option value="">All Roles</option>
              <option value="STUDENT">Student</option>
              <option value="INSTRUCTOR">Instructor</option>
              <option value="ADVISOR">Advisor</option>
              <option value="REGISTRAR">Registrar</option>
            </select>
            <input
              type="text"
              placeholder="Major"
              value={searchFilters.major}
              onChange={(e) => setSearchFilters({...searchFilters, major: e.target.value})}
              className="search-input"
            />
            <input
              type="text"
              placeholder="Minor"
              value={searchFilters.minor}
              onChange={(e) => setSearchFilters({...searchFilters, minor: e.target.value})}
              className="search-input"
            />
            <input
              type="text"
              placeholder="Graduation Term (e.g., Fall 2025)"
              value={searchFilters.graduationTerm}
              onChange={(e) => setSearchFilters({...searchFilters, graduationTerm: e.target.value})}
              className="search-input"
            />
            <input
              type="text"
              placeholder="Advisor Level"
              value={searchFilters.advisorLevel}
              onChange={(e) => setSearchFilters({...searchFilters, advisorLevel: e.target.value})}
              className="search-input"
            />
            <input
              type="text"
              placeholder="Academic Unit"
              value={searchFilters.academicUnit}
              onChange={(e) => setSearchFilters({...searchFilters, academicUnit: e.target.value})}
              className="search-input"
            />
            <button
              onClick={() => setSearchFilters({
                name: '', role: '', major: '', minor: '', graduationTerm: '',
                advisorLevel: '', academicUnit: ''
              })}
              className="clear-filters-btn"
            >
              Clear
            </button>
          </div>
        ) : (
          <div className="search-filters">
            <input
              type="text"
              placeholder="Search users by name, email, or student ID..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="search-input"
            />
            <select
              value={roleFilter}
              onChange={(e) => setRoleFilter(e.target.value)}
              className="role-filter"
            >
              <option value="">All Roles</option>
              <option value="STUDENT">Student</option>
              <option value="INSTRUCTOR">Instructor</option>
              <option value="ADVISOR">Advisor</option>
              <option value="REGISTRAR">Registrar</option>
            </select>
          </div>
        )}

        <div className="import-section">
          <div className="file-upload">
            <input
              type="file"
              accept=".yaml,.yml"
              onChange={(e) => setSelectedFile(e.target.files?.[0] || null)}
              className="file-input"
              id="file-upload"
            />
            <label htmlFor="file-upload" className="file-label">
              {selectedFile ? selectedFile.name : 'Choose YAML File'}
            </label>
            <button
              onClick={handleFileUpload}
              disabled={!selectedFile}
              className="import-button"
            >
              Import Users
            </button>
          </div>
          {importMessage && (
            <div className={`import-message ${importMessage.includes('Error') ? 'error' : 'success'}`}>
              {importMessage}
            </div>
          )}
        </div>
      </div>

      <div className="users-table-container">
        <table className="users-table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Email</th>
              <th>Role</th>
              <th>Student ID</th>
              <th>Major/Department</th>
              <th>Status</th>
              <th>Created</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {filteredUsers.map((user) => (
              <tr key={user.userId}>
                <td>
                  <div className="user-info">
                    <strong>{user.fullName}</strong>
                  </div>
                </td>
                <td>{user.email}</td>
                <td>
                  <span className={`role-badge role-${user.role.toLowerCase()}`}>
                    {user.role}
                  </span>
                </td>
                <td>{user.studentId || '-'}</td>
                <td>{user.major || user.department || '-'}</td>
                <td>
                  <span className={`status-badge ${user.isActive ? 'active' : 'inactive'}`}>
                    {user.isActive ? 'Active' : 'Inactive'}
                  </span>
                </td>
                <td>{formatDate(user.createdAt)}</td>
                <td>
                  <div className="action-buttons">
                    <button
                      onClick={() => toggleUserStatus(user.userId, user.isActive)}
                      className={`status-button ${user.isActive ? 'deactivate' : 'activate'}`}
                    >
                      {user.isActive ? 'Deactivate' : 'Activate'}
                    </button>
                    <button
                      onClick={() => handleExportUser(user.userId)}
                      className="export-button"
                      title="Export user data"
                    >
                      Export
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {filteredUsers.length === 0 && (
        <div className="no-users">
          <p>No users found matching your criteria.</p>
        </div>
      )}
    </div>
  );
};

export default UserManagement;
