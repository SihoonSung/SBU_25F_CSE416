package edu.sbu.cse416.sam_system.repository;

import edu.sbu.cse416.sam_system.entity.User;
import edu.sbu.cse416.sam_system.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for User entity operations.
 * Provides methods for user authentication and management.
 * 
 * TP: Used Cursor to implement user repository with search functionality
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    /**
     * Find user by email address
     */
    Optional<User> findByEmail(String email);
    
    /**
     * Find user by Google ID
     */
    Optional<User> findByGoogleId(String googleId);
    
    /**
     * Find user by student ID
     */
    Optional<User> findByStudentId(String studentId);
    
    /**
     * Find all users by role
     */
    List<User> findByRole(UserRole role);
    
    /**
     * Find all active users
     */
    List<User> findByIsActiveTrue();
    
    /**
     * Find all users by role and active status
     */
    List<User> findByRoleAndIsActiveTrue(UserRole role);
    
    /**
     * Search users by name (first name or last name contains search term)
     */
    @Query("SELECT u FROM User u WHERE " +
           "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(CONCAT(u.firstName, ' ', u.lastName)) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<User> findByNameContaining(@Param("searchTerm") String searchTerm);
    
    /**
     * Search users by name and role
     */
    @Query("SELECT u FROM User u WHERE " +
           "u.role = :role AND " +
           "(LOWER(u.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(CONCAT(u.firstName, ' ', u.lastName)) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    List<User> findByNameContainingAndRole(@Param("searchTerm") String searchTerm, @Param("role") UserRole role);
    
    /**
     * Search users by email domain
     */
    @Query("SELECT u FROM User u WHERE u.email LIKE CONCAT('%', :domain)")
    List<User> findByEmailDomain(@Param("domain") String domain);
    
    /**
     * Find users by major (for students)
     */
    @Query("SELECT u FROM User u WHERE u.role = 'STUDENT' AND LOWER(u.major) LIKE LOWER(CONCAT('%', :major, '%'))")
    List<User> findByMajorContaining(@Param("major") String major);
    
    /**
     * Find users by department (for instructors)
     */
    @Query("SELECT u FROM User u WHERE u.role = 'INSTRUCTOR' AND LOWER(u.department) LIKE LOWER(CONCAT('%', :department, '%'))")
    List<User> findByDepartmentContaining(@Param("department") String department);
    
    /**
     * Check if email exists
     */
    boolean existsByEmail(String email);
    
    /**
     * Check if Google ID exists
     */
    boolean existsByGoogleId(String googleId);
    
    /**
     * Check if student ID exists
     */
    boolean existsByStudentId(String studentId);
    
    /**
     * Count users by role
     */
    long countByRole(UserRole role);
    
    /**
     * Count active users
     */
    long countByIsActiveTrue();
}
