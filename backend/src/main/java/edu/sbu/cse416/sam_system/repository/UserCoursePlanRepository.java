package edu.sbu.cse416.sam_system.repository;

import edu.sbu.cse416.sam_system.entity.UserCoursePlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * TP: Used cursor to implement repository for UserCoursePlan entity.
 * Repository for UserCoursePlan entity.
 * Handles database operations for user's course plan data.
 */
@Repository
public interface UserCoursePlanRepository extends JpaRepository<UserCoursePlan, Long> {
    
    /**
     * Find all course plans for a specific user
     */
    @Query("SELECT ucp FROM UserCoursePlan ucp WHERE ucp.user.userId = :userId")
    List<UserCoursePlan> findByUserId(@Param("userId") Long userId);
    
    /**
     * Find course plans for a specific user and semester/year
     */
    @Query("SELECT ucp FROM UserCoursePlan ucp WHERE ucp.user.userId = :userId AND ucp.semester = :semester AND ucp.year = :year")
    List<UserCoursePlan> findByUserIdAndSemesterAndYear(@Param("userId") Long userId, 
                                                         @Param("semester") String semester, 
                                                         @Param("year") Integer year);
    
    /**
     * Find course plans for a specific user and department
     */
    @Query("SELECT ucp FROM UserCoursePlan ucp WHERE ucp.user.userId = :userId AND ucp.department = :department")
    List<UserCoursePlan> findByUserIdAndDepartment(@Param("userId") Long userId, 
                                                   @Param("department") String department);
    
    /**
     * Find future planned courses for a user (semester/year in the future)
     */
    @Query("SELECT ucp FROM UserCoursePlan ucp WHERE ucp.user.userId = :userId AND (ucp.year > :currentYear OR (ucp.year = :currentYear AND ucp.semester IN ('Fall', 'Spring', 'Summer')))")
    List<UserCoursePlan> findFuturePlannedCoursesByUserId(@Param("userId") Long userId, 
                                                          @Param("currentYear") Integer currentYear);
    
    /**
     * Delete all course plans for a specific user
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM UserCoursePlan ucp WHERE ucp.user.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
    
    /**
     * Check if a user has a specific course in their plan
     */
    @Query("SELECT COUNT(ucp) > 0 FROM UserCoursePlan ucp WHERE ucp.user.userId = :userId AND ucp.department = :department AND ucp.courseNum = :courseNum")
    boolean existsByUserIdAndDepartmentAndCourseNum(@Param("userId") Long userId, 
                                                   @Param("department") String department, 
                                                   @Param("courseNum") Integer courseNum);
}
