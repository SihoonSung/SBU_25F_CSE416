package edu.sbu.cse416.sam_system.repository;

import edu.sbu.cse416.sam_system.entity.PrerequisiteWaiver;
import edu.sbu.cse416.sam_system.entity.User;
import edu.sbu.cse416.sam_system.entity.ClassEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for PrerequisiteWaiver entity.
 * 
 * TP: Used Cursor to implement prerequisite waiver repository
 */
@Repository
public interface PrerequisiteWaiverRepository extends JpaRepository<PrerequisiteWaiver, Long> {
    
    /**
     * Find all waivers for a student
     */
    List<PrerequisiteWaiver> findByStudent(User student);
    
    /**
     * Find all waivers for a student by student ID
     */
    @Query("SELECT pw FROM PrerequisiteWaiver pw WHERE pw.student.userId = :studentId")
    List<PrerequisiteWaiver> findByStudentId(@Param("studentId") Long studentId);
    
    /**
     * Find approved waivers for a student and class
     */
    @Query("SELECT pw FROM PrerequisiteWaiver pw WHERE pw.student = :student AND pw.classEntity = :classEntity AND pw.status = 'APPROVED'")
    List<PrerequisiteWaiver> findApprovedWaiversByStudentAndClass(@Param("student") User student, @Param("classEntity") ClassEntity classEntity);
    
    /**
     * Check if there's an approved waiver for a student and class with specific prerequisite
     */
    @Query("SELECT pw FROM PrerequisiteWaiver pw WHERE pw.student = :student AND pw.classEntity = :classEntity AND pw.prerequisite = :prerequisite AND pw.status = 'APPROVED'")
    Optional<PrerequisiteWaiver> findApprovedWaiver(@Param("student") User student, @Param("classEntity") ClassEntity classEntity, @Param("prerequisite") String prerequisite);
    
    /**
     * Find pending waivers
     */
    @Query("SELECT pw FROM PrerequisiteWaiver pw WHERE pw.status = 'PENDING' ORDER BY pw.createdAt DESC")
    List<PrerequisiteWaiver> findPendingWaivers();
    
    /**
     * Find waivers for an instructor (for classes they teach)
     */
    @Query("SELECT pw FROM PrerequisiteWaiver pw WHERE pw.classEntity.instructor LIKE %:instructorName% AND pw.status = 'PENDING'")
    List<PrerequisiteWaiver> findWaiversForInstructor(@Param("instructorName") String instructorName);
}

