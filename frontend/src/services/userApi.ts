import { User, UserSearchResponse, UserExportResponse } from '../types/User';

const API_BASE_URL = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8080';

export class UserApiService {
  
  /**
   * Get all users with proper typing
   */
  static async getAllUsers(): Promise<User[]> {
    const response = await fetch(`${API_BASE_URL}/api/users`, {
      credentials: 'include'
    });
    
    if (!response.ok) {
      throw new Error(`Failed to fetch users: ${response.statusText}`);
    }
    
    return response.json();
  }
  
  /**
   * Search users with filters
   */
  static async searchUsers(filters: {
    name?: string;
    role?: string;
    major?: string;
    minor?: string;
    graduationTerm?: string;
    advisorLevel?: string;
    academicUnit?: string;
  }): Promise<User[]> {
    const params = new URLSearchParams();
    
    Object.entries(filters).forEach(([key, value]) => {
      if (value) {
        params.append(key, value);
      }
    });
    
    const response = await fetch(`${API_BASE_URL}/api/users/search?${params}`, {
      credentials: 'include'
    });
    
    if (!response.ok) {
      throw new Error(`Failed to search users: ${response.statusText}`);
    }
    
    return response.json();
  }
  
  /**
   * Get user by SBU ID
   */
  static async getUserBySbuId(sbuId: string): Promise<User> {
    const response = await fetch(`${API_BASE_URL}/api/users/sbu-id/${sbuId}`, {
      credentials: 'include'
    });
    
    if (!response.ok) {
      throw new Error(`Failed to fetch user: ${response.statusText}`);
    }
    
    return response.json();
  }
  
  /**
   * Export user data as YAML
   */
  static async exportUser(sbuId: string): Promise<UserExportResponse> {
    const response = await fetch(`${API_BASE_URL}/api/users/export/${sbuId}`, {
      credentials: 'include'
    });
    
    if (!response.ok) {
      throw new Error(`Failed to export user: ${response.statusText}`);
    }
    
    return response.json();
  }
  
  /**
   * Export all users as YAML
   */
  static async exportAllUsers(): Promise<UserExportResponse> {
    const response = await fetch(`${API_BASE_URL}/api/users/export-all`, {
      credentials: 'include'
    });
    
    if (!response.ok) {
      throw new Error(`Failed to export all users: ${response.statusText}`);
    }
    
    return response.json();
  }
  
  /**
   * Import users from YAML file
   */
  static async importUsers(file: File): Promise<{ success: boolean; message: string; users?: User[] }> {
    const formData = new FormData();
    formData.append('file', file);
    
    const response = await fetch(`${API_BASE_URL}/api/users/import`, {
      method: 'POST',
      body: formData,
      credentials: 'include'
    });
    
    if (!response.ok) {
      throw new Error(`Failed to import users: ${response.statusText}`);
    }
    
    return response.json();
  }
  
  /**
   * Get user statistics
   */
  static async getUserStatistics(): Promise<{
    totalUsers: number;
    activeUsers: number;
    students: number;
    instructors: number;
    advisors: number;
    registrars: number;
  }> {
    const response = await fetch(`${API_BASE_URL}/api/users/statistics`, {
      credentials: 'include'
    });
    
    if (!response.ok) {
      throw new Error(`Failed to fetch statistics: ${response.statusText}`);
    }
    
    return response.json();
  }
}

// React hook for user data
export const useUsers = () => {
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  
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
  
  const searchUsers = async (filters: Parameters<typeof UserApiService.searchUsers>[0]) => {
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
  
  return {
    users,
    loading,
    error,
    fetchUsers,
    searchUsers
  };
};
