package edu.sbu.cse416.sam_system.repository;

import edu.sbu.cse416.sam_system.entity.UserClasses;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * TP: Used cursor to implement repository for UserClasses entity.
 * Repository for UserClasses entity.
 * Handles database operations for user's classes data.
 */
@Repository
public interface UserClassesRepository extends JpaRepository<UserClasses, Long> {
    
    /**
     * Find all classes for a specific user
     */
    @Query("SELECT uc FROM UserClasses uc WHERE uc.user.userId = :userId")
    List<UserClasses> findByUserId(@Param("userId") Long userId);
    
    /**
     * Find classes for a specific user and semester/year
     */
    @Query("SELECT uc FROM UserClasses uc WHERE uc.user.userId = :userId AND uc.semester = :semester AND uc.year = :year")
    List<UserClasses> findByUserIdAndSemesterAndYear(@Param("userId") Long userId, 
                                                    @Param("semester") String semester, 
                                                    @Param("year") Integer year);
    
    /**
     * Find classes for a specific user and department
     */
    @Query("SELECT uc FROM UserClasses uc WHERE uc.user.userId = :userId AND uc.department = :department")
    List<UserClasses> findByUserIdAndDepartment(@Param("userId") Long userId, 
                                               @Param("department") String department);
    
    /**
     * Find completed classes (with grades) for a user
     */
    @Query("SELECT uc FROM UserClasses uc WHERE uc.user.userId = :userId AND uc.grade IS NOT NULL")
    List<UserClasses> findCompletedClassesByUserId(@Param("userId") Long userId);
    
    /**
     * Find future classes (without grades) for a user
     */
    @Query("SELECT uc FROM UserClasses uc WHERE uc.user.userId = :userId AND uc.grade IS NULL")
    List<UserClasses> findFutureClassesByUserId(@Param("userId") Long userId);
    
    /**
     * Delete all classes for a specific user
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM UserClasses uc WHERE uc.user.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
    
    /**
     * Delete a specific class for a user
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM UserClasses uc WHERE uc.user.userId = :userId AND uc.classId = :classId")
    void deleteByUserIdAndClassId(@Param("userId") Long userId, @Param("classId") Long classId);
    
    /**
     * Check if a user has a specific class
     */
    @Query("SELECT COUNT(uc) > 0 FROM UserClasses uc WHERE uc.user.userId = :userId AND uc.classId = :classId")
    boolean existsByUserIdAndClassId(@Param("userId") Long userId, @Param("classId") Long classId);
}
