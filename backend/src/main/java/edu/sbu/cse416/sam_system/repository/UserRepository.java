package edu.sbu.cse416.sam_system.repository;

import edu.sbu.cse416.sam_system.entity.User;
import edu.sbu.cse416.sam_system.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByGoogleId(String googleId);

    Optional<User> findByStudentId(String studentId);

    List<User> findByRole(UserRole role);

    List<User> findByIsActiveTrue();

    List<User> findByRoleAndIsActiveTrue(UserRole role);

    @Query("SELECT u FROM User u WHERE " +
            "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(CONCAT(u.firstName, ' ', u.lastName)) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<User> findByNameContaining(@Param("searchTerm") String searchTerm);

    @Query("SELECT u FROM User u WHERE " +
            "u.role = :role AND " +
            "(LOWER(u.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(CONCAT(u.firstName, ' ', u.lastName)) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    List<User> findByNameContainingAndRole(@Param("searchTerm") String searchTerm, @Param("role") UserRole role);

    @Query("SELECT u FROM User u WHERE u.email LIKE CONCAT('%', :domain)")
    List<User> findByEmailDomain(@Param("domain") String domain);

    @Query("SELECT u FROM User u WHERE u.role = 'STUDENT' AND LOWER(u.major) LIKE LOWER(CONCAT('%', :major, '%'))")
    List<User> findByMajorContaining(@Param("major") String major);

    @Query("SELECT u FROM User u WHERE u.role = 'INSTRUCTOR' AND LOWER(u.department) LIKE LOWER(CONCAT('%', :department, '%'))")
    List<User> findByDepartmentContaining(@Param("department") String department);

    boolean existsByEmail(String email);

    boolean existsByGoogleId(String googleId);

    boolean existsByStudentId(String studentId);

    long countByRole(UserRole role);

    long countByIsActiveTrue();
}