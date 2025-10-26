package edu.sbu.cse416.sam_system.repository;

import edu.sbu.cse416.sam_system.entity.MajorMinorDeclaration;
import edu.sbu.cse416.sam_system.entity.User;
import edu.sbu.cse416.sam_system.entity.DeclarationStatus;
import edu.sbu.cse416.sam_system.entity.DeclarationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MajorMinorDeclarationRepository extends JpaRepository<MajorMinorDeclaration, Long> {
    
    List<MajorMinorDeclaration> findByStudent(User student);
    
    List<MajorMinorDeclaration> findByStatus(DeclarationStatus status);
    
    List<MajorMinorDeclaration> findByStudentAndStatus(User student, DeclarationStatus status);
    
    List<MajorMinorDeclaration> findBySubjectCode(String subjectCode);
    
    List<MajorMinorDeclaration> findBySubjectCodeAndStatus(String subjectCode, DeclarationStatus status);
    
    // Methods required by MajorMinorDeclarationService
    @Query("SELECT COUNT(d) > 0 FROM MajorMinorDeclaration d WHERE d.student = :student AND d.subjectCode = :subjectCode AND d.declarationType = :declarationType AND d.status = 'PENDING' AND d.isActive = true")
    boolean existsPendingDeclaration(@Param("student") User student, @Param("subjectCode") String subjectCode, @Param("declarationType") DeclarationType declarationType);
    
    @Query("SELECT COUNT(d) > 0 FROM MajorMinorDeclaration d WHERE d.student = :student AND d.subjectCode = :subjectCode AND d.declarationType = :declarationType AND d.status = 'APPROVED' AND d.isActive = true")
    boolean existsApprovedDeclaration(@Param("student") User student, @Param("subjectCode") String subjectCode, @Param("declarationType") DeclarationType declarationType);
    
    List<MajorMinorDeclaration> findByStudentOrderByCreatedAtDesc(User student);
    
    @Query("SELECT d FROM MajorMinorDeclaration d WHERE d.advisor = :advisor OR d.ceasAdvisor = :advisor ORDER BY d.createdAt DESC")
    List<MajorMinorDeclaration> findByAdvisorOrCeasAdvisorOrderByCreatedAtDesc(@Param("advisor") User advisor);
    
    @Query("SELECT COUNT(d) FROM MajorMinorDeclaration d WHERE d.student = :student AND d.declarationType = 'MAJOR' AND d.status = 'APPROVED' AND d.isActive = true")
    long countActiveMajorsByStudent(@Param("student") User student);
    
    @Query("SELECT COUNT(d) FROM MajorMinorDeclaration d WHERE d.student = :student AND d.declarationType = 'MINOR' AND d.status = 'APPROVED' AND d.isActive = true")
    long countActiveMinorsByStudent(@Param("student") User student);
}