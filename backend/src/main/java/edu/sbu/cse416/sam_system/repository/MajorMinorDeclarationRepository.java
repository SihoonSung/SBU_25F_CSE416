package edu.sbu.cse416.sam_system.repository;

import edu.sbu.cse416.sam_system.entity.DeclarationStatus;
import edu.sbu.cse416.sam_system.entity.DeclarationType;
import edu.sbu.cse416.sam_system.entity.MajorMinorDeclaration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for MajorMinorDeclaration entity.
 * 
 * TP: Used Cursor to implement major/minor declaration system
 */
@Repository
public interface MajorMinorDeclarationRepository extends JpaRepository<MajorMinorDeclaration, Long> {
    
    /**
     * Find all declarations for a specific student
     */
    @Query("SELECT d FROM MajorMinorDeclaration d WHERE d.studentNetId = :studentNetId " +
           "AND d.isActive = true ORDER BY d.submittedAt DESC")
    List<MajorMinorDeclaration> findByStudentNetIdAndActive(@Param("studentNetId") String studentNetId);
    
    /**
     * Find pending declarations for a specific advisor
     */
    @Query("SELECT d FROM MajorMinorDeclaration d WHERE d.advisorNetId = :advisorNetId " +
           "AND d.status = 'PENDING' AND d.isActive = true ORDER BY d.submittedAt ASC")
    List<MajorMinorDeclaration> findPendingByAdvisorNetId(@Param("advisorNetId") String advisorNetId);
    
    /**
     * Find declarations by status
     */
    @Query("SELECT d FROM MajorMinorDeclaration d WHERE d.status = :status " +
           "AND d.isActive = true ORDER BY d.submittedAt DESC")
    List<MajorMinorDeclaration> findByStatusAndActive(@Param("status") DeclarationStatus status);
    
    /**
     * Find active declarations for a specific student and declaration type
     */
    @Query("SELECT d FROM MajorMinorDeclaration d WHERE d.studentNetId = :studentNetId " +
           "AND d.declarationType = :declarationType AND d.status = 'APPROVED' " +
           "AND d.isActive = true ORDER BY d.submittedAt DESC")
    List<MajorMinorDeclaration> findActiveByStudentAndType(
            @Param("studentNetId") String studentNetId,
            @Param("declarationType") DeclarationType declarationType);
    
    /**
     * Count active declarations for a student by type
     */
    @Query("SELECT COUNT(d) FROM MajorMinorDeclaration d WHERE d.studentNetId = :studentNetId " +
           "AND d.declarationType = :declarationType AND d.status = 'APPROVED' " +
           "AND d.isActive = true")
    long countActiveByStudentAndType(@Param("studentNetId") String studentNetId,
                                   @Param("declarationType") DeclarationType declarationType);
    
    /**
     * Find declarations by subject code and effective term
     */
    @Query("SELECT d FROM MajorMinorDeclaration d WHERE d.subjectCode = :subjectCode " +
           "AND d.effectiveTerm = :effectiveTerm AND d.isActive = true " +
           "ORDER BY d.submittedAt DESC")
    List<MajorMinorDeclaration> findBySubjectCodeAndEffectiveTerm(
            @Param("subjectCode") String subjectCode,
            @Param("effectiveTerm") String effectiveTerm);
    
    /**
     * Find all active declarations
     */
    @Query("SELECT d FROM MajorMinorDeclaration d WHERE d.isActive = true " +
           "ORDER BY d.submittedAt DESC")
    List<MajorMinorDeclaration> findAllActive();
    
    /**
     * Check if student has an active declaration for a specific subject and type
     */
    @Query("SELECT COUNT(d) > 0 FROM MajorMinorDeclaration d WHERE d.studentNetId = :studentNetId " +
           "AND d.subjectCode = :subjectCode AND d.declarationType = :declarationType " +
           "AND d.status = 'APPROVED' AND d.isActive = true")
    boolean existsActiveByStudentAndSubjectAndType(
            @Param("studentNetId") String studentNetId,
            @Param("subjectCode") String subjectCode,
            @Param("declarationType") DeclarationType declarationType);
    
    /**
     * Find declarations by advisor and status
     */
    @Query("SELECT d FROM MajorMinorDeclaration d WHERE d.advisorNetId = :advisorNetId " +
           "AND d.status = :status AND d.isActive = true ORDER BY d.submittedAt DESC")
    List<MajorMinorDeclaration> findByAdvisorAndStatus(
            @Param("advisorNetId") String advisorNetId,
            @Param("status") DeclarationStatus status);
}
