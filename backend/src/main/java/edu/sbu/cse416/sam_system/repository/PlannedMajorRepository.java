package edu.sbu.cse416.sam_system.repository;

import edu.sbu.cse416.sam_system.entity.PlannedMajor;
import edu.sbu.cse416.sam_system.entity.User;
import edu.sbu.cse416.sam_system.entity.DegreeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * TP: Used cursor to implement repository for PlannedMajor entity.
 * Repository for PlannedMajor entity.
 * Handles database operations for planned majors.
 */
@Repository
public interface PlannedMajorRepository extends JpaRepository<PlannedMajor, Long> {
    
    /**
     * Find all active planned majors for a student
     */
    @Query("SELECT pm FROM PlannedMajor pm WHERE pm.student = :student AND pm.isActive = true")
    List<PlannedMajor> findActiveByStudent(@Param("student") User student);
    
    /**
     * Find all active planned majors for a student by student ID
     */
    @Query("SELECT pm FROM PlannedMajor pm WHERE pm.student.userId = :studentId AND pm.isActive = true")
    List<PlannedMajor> findActiveByStudentId(@Param("studentId") Long studentId);
    
    /**
     * Find a specific planned major for a student
     */
    @Query("SELECT pm FROM PlannedMajor pm WHERE pm.student = :student AND pm.subjectCode = :subjectCode AND pm.degreeType = :degreeType AND pm.isActive = true")
    Optional<PlannedMajor> findByStudentAndSubjectAndDegreeType(
            @Param("student") User student, 
            @Param("subjectCode") String subjectCode, 
            @Param("degreeType") DegreeType degreeType);
    
    /**
     * Find a specific planned major for a student by student ID
     */
    @Query("SELECT pm FROM PlannedMajor pm WHERE pm.student.userId = :studentId AND pm.subjectCode = :subjectCode AND pm.degreeType = :degreeType AND pm.isActive = true")
    Optional<PlannedMajor> findByStudentIdAndSubjectAndDegreeType(
            @Param("studentId") Long studentId, 
            @Param("subjectCode") String subjectCode, 
            @Param("degreeType") DegreeType degreeType);
    
    /**
     * Count active planned majors for a student
     */
    @Query("SELECT COUNT(pm) FROM PlannedMajor pm WHERE pm.student.userId = :studentId AND pm.isActive = true")
    long countActiveByStudentId(@Param("studentId") Long studentId);
    
    /**
     * Find all planned majors (including inactive) for a student
     */
    @Query("SELECT pm FROM PlannedMajor pm WHERE pm.student.userId = :studentId ORDER BY pm.plannedAt DESC")
    List<PlannedMajor> findAllByStudentId(@Param("studentId") Long studentId);
}
