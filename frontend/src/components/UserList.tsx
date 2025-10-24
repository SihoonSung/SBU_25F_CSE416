import React, { useState, useEffect } from 'react';
import { User } from '../types/User';
import { UserApiService } from '../services/userApi';

interface UserListProps {
  onUserSelect?: (user: User) => void;
}

const UserList: React.FC<UserListProps> = ({ onUserSelect }) => {
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  
  // Search filters
  const [filters, setFilters] = useState({
    name: '',
    role: '',
    major: '',
    minor: '',
    graduationTerm: '',
    advisorLevel: '',
    academicUnit: ''
  });
  
  useEffect(() => {
    fetchUsers();
  }, []);
  
  const fetchUsers = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await UserApiService.getAllUsers();
      setUsers(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unknown error');
    } finally {
      setLoading(false);
    }
  };
  
  const searchUsers = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await UserApiService.searchUsers(filters);
      setUsers(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unknown error');
    } finally {
      setLoading(false);
    }
  };
  
  const handleUserClick = (user: User) => {
    if (onUserSelect) {
      onUserSelect(user);
    }
  };
  
  const renderUserInfo = (user: User) => {
    switch (user.role) {
      case 'STUDENT':
        return (
          <div className="user-info">
            <p><strong>SBU ID:</strong> {user.sbuId}</p>
            <p><strong>Email:</strong> {user.email}</p>
            {user.majors && user.majors.length > 0 && (
              <p><strong>Majors:</strong> {user.majors.join(', ')}</p>
            )}
            {user.minors && user.minors.length > 0 && (
              <p><strong>Minors:</strong> {user.minors.join(', ')}</p>
            )}
            {user.graduation && (
              <p><strong>Graduation:</strong> {user.graduation.semester} {user.graduation.year}</p>
            )}
            {user.classes && (
              <p><strong>Classes:</strong> {user.classes.length} classes</p>
            )}
          </div>
        );
        
      case 'ADVISOR':
        return (
          <div className="user-info">
            <p><strong>SBU ID:</strong> {user.sbuId}</p>
            <p><strong>Email:</strong> {user.email}</p>
            <p><strong>Level:</strong> {user.advisorLevel}</p>
            <p><strong>Academic Unit:</strong> {user.academicUnit}</p>
          </div>
        );
        
      case 'INSTRUCTOR':
        return (
          <div className="user-info">
            <p><strong>SBU ID:</strong> {user.sbuId}</p>
            <p><strong>Email:</strong> {user.email}</p>
            <p><strong>Department:</strong> {user.department}</p>
            {user.officeLocation && (
              <p><strong>Office:</strong> {user.officeLocation}</p>
            )}
          </div>
        );
        
      case 'REGISTRAR':
        return (
          <div className="user-info">
            <p><strong>SBU ID:</strong> {user.sbuId}</p>
            <p><strong>Email:</strong> {user.email}</p>
          </div>
        );
        
      default:
        return null;
    }
  };
  
  if (loading) {
    return <div className="loading">Loading users...</div>;
  }
  
  if (error) {
    return <div className="error">Error: {error}</div>;
  }
  
  return (
    <div className="user-list">
      <h2>User Management</h2>
      
      {/* Search Filters */}
      <div className="search-filters">
        <div className="filter-row">
          <input
            type="text"
            placeholder="Name"
            value={filters.name}
            onChange={(e) => setFilters({...filters, name: e.target.value})}
          />
          <select
            value={filters.role}
            onChange={(e) => setFilters({...filters, role: e.target.value})}
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
            value={filters.major}
            onChange={(e) => setFilters({...filters, major: e.target.value})}
          />
          <input
            type="text"
            placeholder="Minor"
            value={filters.minor}
            onChange={(e) => setFilters({...filters, minor: e.target.value})}
          />
        </div>
        <div className="filter-row">
          <input
            type="text"
            placeholder="Graduation Term (e.g., Fall 2025)"
            value={filters.graduationTerm}
            onChange={(e) => setFilters({...filters, graduationTerm: e.target.value})}
          />
          <select
            value={filters.advisorLevel}
            onChange={(e) => setFilters({...filters, advisorLevel: e.target.value})}
          >
            <option value="">All Advisor Levels</option>
            <option value="university">University</option>
            <option value="college">College</option>
            <option value="department">Department</option>
          </select>
          <input
            type="text"
            placeholder="Academic Unit"
            value={filters.academicUnit}
            onChange={(e) => setFilters({...filters, academicUnit: e.target.value})}
          />
          <button onClick={searchUsers}>Search</button>
          <button onClick={fetchUsers}>Clear</button>
        </div>
      </div>
      
      {/* User List */}
      <div className="users-grid">
        {users.map((user) => (
          <div
            key={user.userId}
            className="user-card"
            onClick={() => handleUserClick(user)}
          >
            <h3>{user.firstName} {user.lastName}</h3>
            <p className="role">{user.role}</p>
            {renderUserInfo(user)}
          </div>
        ))}
      </div>
      
      {users.length === 0 && (
        <div className="no-users">No users found</div>
      )}
    </div>
  );
};

export default UserList;
