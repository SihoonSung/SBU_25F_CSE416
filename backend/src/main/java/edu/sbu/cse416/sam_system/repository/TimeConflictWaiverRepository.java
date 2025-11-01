package edu.sbu.cse416.sam_system.repository;

import edu.sbu.cse416.sam_system.entity.TimeConflictWaiver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for TimeConflictWaiver entity.
 * 
 * TP: Used Cursor to implement JPA repository interface.
 */
@Repository
public interface TimeConflictWaiverRepository extends JpaRepository<TimeConflictWaiver, Long> {
    
    /**
     * Find all waivers for a specific student
     */
    @Query("SELECT w FROM TimeConflictWaiver w WHERE w.student.userId = :studentId ORDER BY w.createdAt DESC")
    List<TimeConflictWaiver> findByStudentId(@Param("studentId") Long studentId);
    
    /**
     * Find all pending waivers
     */
    @Query("SELECT w FROM TimeConflictWaiver w WHERE w.status = 'PENDING' ORDER BY w.createdAt DESC")
    List<TimeConflictWaiver> findPendingWaivers();
    
    /**
     * Find waivers that need instructor approval (for a specific instructor's classes)
     */
    @Query("SELECT w FROM TimeConflictWaiver w WHERE w.status = 'PENDING' " +
           "AND (w.enrolledClass.instructor LIKE %:instructorName% OR w.conflictingClass.instructor LIKE %:instructorName%)")
    List<TimeConflictWaiver> findWaiversForInstructor(@Param("instructorName") String instructorName);
    
    /**
     * Find waivers that need advisor approval (for a specific department)
     */
    @Query("SELECT w FROM TimeConflictWaiver w WHERE w.status = 'PENDING' " +
           "AND (w.enrolledClass.department = :department OR w.conflictingClass.department = :department)")
    List<TimeConflictWaiver> findWaiversForDepartment(@Param("department") String department);
    
    /**
     * Check if there's an approved waiver for a specific student and two classes
     */
    @Query("SELECT w FROM TimeConflictWaiver w WHERE w.student.userId = :studentId " +
           "AND ((w.enrolledClass.classId = :classId1 AND w.conflictingClass.classId = :classId2) " +
           "OR (w.enrolledClass.classId = :classId2 AND w.conflictingClass.classId = :classId1)) " +
           "AND w.status = 'APPROVED'")
    Optional<TimeConflictWaiver> findApprovedWaiver(@Param("studentId") Long studentId, 
                                                     @Param("classId1") Long classId1, 
                                                     @Param("classId2") Long classId2);
    
    /**
     * Find waiver by student and both classes
     */
    @Query("SELECT w FROM TimeConflictWaiver w WHERE w.student.userId = :studentId " +
           "AND w.enrolledClass.classId = :enrolledClassId " +
           "AND w.conflictingClass.classId = :conflictingClassId")
    Optional<TimeConflictWaiver> findByStudentAndClasses(@Param("studentId") Long studentId,
                                                         @Param("enrolledClassId") Long enrolledClassId,
                                                         @Param("conflictingClassId") Long conflictingClassId);
}

