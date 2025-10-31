package edu.sbu.cse416.sam_system.repository;

import edu.sbu.cse416.sam_system.entity.ClassRoster;
import edu.sbu.cse416.sam_system.entity.ClassEntity;
import edu.sbu.cse416.sam_system.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for ClassRoster entity.
 * Provides methods to query class enrollment data.
 * 
 * TP: Used Cursor to implement JPA repository interface.
 */
@Repository
public interface ClassRosterRepository extends JpaRepository<ClassRoster, Long> {
    
    /**
     * Find all students enrolled in a specific class
     * @param classEntity The class to find students for
     * @return List of ClassRoster entries for the class
     */
    List<ClassRoster> findByClassEntity(ClassEntity classEntity);
    
    /**
     * Find all classes a student is enrolled in
     * @param user The student to find classes for
     * @return List of ClassRoster entries for the student
     */
    List<ClassRoster> findByUser(User user);
    
    /**
     * Find enrollment record for a specific student and class
     * @param classEntity The class
     * @param user The student
     * @return Optional ClassRoster entry
     */
    Optional<ClassRoster> findByClassEntityAndUser(ClassEntity classEntity, User user);
    
    /**
     * Count active enrollments for a specific class
     * @param classEntity The class to count enrollments for
     * @return Number of active enrollments
     */
    @Query("SELECT COUNT(cr) FROM ClassRoster cr WHERE cr.classEntity = :classEntity AND cr.status = 'ENROLLED'")
    Long countActiveEnrollmentsByClass(@Param("classEntity") ClassEntity classEntity);
    
    /**
     * Find all active enrollments for a specific class
     * @param classEntity The class to find active enrollments for
     * @return List of active ClassRoster entries
     */
    @Query("SELECT cr FROM ClassRoster cr WHERE cr.classEntity = :classEntity AND cr.status = 'ENROLLED'")
    List<ClassRoster> findActiveEnrollmentsByClass(@Param("classEntity") ClassEntity classEntity);
    
    /**
     * Find all active enrollments for a specific student
     * @param user The student to find active enrollments for
     * @return List of active ClassRoster entries
     */
    @Query("SELECT cr FROM ClassRoster cr WHERE cr.user = :user AND cr.status = 'ENROLLED'")
    List<ClassRoster> findActiveEnrollmentsByUser(@Param("user") User user);
    
    /**
     * Check if a student is enrolled in a specific class
     * @param classEntity The class
     * @param user The student
     * @return true if student is actively enrolled
     */
    @Query("SELECT COUNT(cr) > 0 FROM ClassRoster cr WHERE cr.classEntity = :classEntity AND cr.user = :user AND cr.status = 'ENROLLED'")
    boolean isStudentEnrolled(@Param("classEntity") ClassEntity classEntity, @Param("user") User user);
    
    /**
     * Delete enrollment record for a specific student and class
     * @param classEntity The class
     * @param user The student
     */
    void deleteByClassEntityAndUser(ClassEntity classEntity, User user);
    
    /**
     * Delete all roster entries for classes in a specific semester/year
     * @param semester The semester
     * @param year The year
     */
    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("DELETE FROM ClassRoster cr WHERE cr.classEntity.semester = :semester AND cr.classEntity.year = :year")
    void deleteBySemesterAndYear(@Param("semester") String semester, @Param("year") Integer year);
}
