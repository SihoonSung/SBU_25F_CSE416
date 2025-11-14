package edu.sbu.cse416.sam_system.repository;

import edu.sbu.cse416.sam_system.entity.CorequisiteWaiver;
import edu.sbu.cse416.sam_system.entity.User;
import edu.sbu.cse416.sam_system.entity.ClassEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for CorequisiteWaiver entity.
 * 
 * TP: Used Cursor to implement corequisite waiver repository
 */
@Repository
public interface CorequisiteWaiverRepository extends JpaRepository<CorequisiteWaiver, Long> {
    
    /**
     * Find all waivers for a student
     */
    List<CorequisiteWaiver> findByStudent(User student);
    
    /**
     * Find all waivers for a student by student ID
     */
    @Query("SELECT cw FROM CorequisiteWaiver cw WHERE cw.student.userId = :studentId")
    List<CorequisiteWaiver> findByStudentId(@Param("studentId") Long studentId);
    
    /**
     * Find approved waivers for a student and class
     */
    @Query("SELECT cw FROM CorequisiteWaiver cw WHERE cw.student = :student AND cw.classEntity = :classEntity AND cw.status = 'APPROVED'")
    List<CorequisiteWaiver> findApprovedWaiversByStudentAndClass(@Param("student") User student, @Param("classEntity") ClassEntity classEntity);
    
    /**
     * Check if there's an approved waiver for a student and class with specific corequisite
     */
    @Query("SELECT cw FROM CorequisiteWaiver cw WHERE cw.student = :student AND cw.classEntity = :classEntity AND cw.corequisite = :corequisite AND cw.status = 'APPROVED'")
    Optional<CorequisiteWaiver> findApprovedWaiver(@Param("student") User student, @Param("classEntity") ClassEntity classEntity, @Param("corequisite") String corequisite);
    
    /**
     * Find pending waivers
     */
    @Query("SELECT cw FROM CorequisiteWaiver cw WHERE cw.status = 'PENDING' ORDER BY cw.createdAt DESC")
    List<CorequisiteWaiver> findPendingWaivers();
    
    /**
     * Find waivers for an instructor (for classes they teach)
     */
    @Query("SELECT cw FROM CorequisiteWaiver cw WHERE cw.classEntity.instructor LIKE %:instructorName% AND cw.status = 'PENDING'")
    List<CorequisiteWaiver> findWaiversForInstructor(@Param("instructorName") String instructorName);
}

