package edu.sbu.cse416.sam_system.repository;

import edu.sbu.cse416.sam_system.entity.RequisiteWaiver;
import edu.sbu.cse416.sam_system.entity.RequisiteWaiver.RequisiteType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for RequisiteWaiver entity.
 * 
 * TP: Used Cursor to implement JPA repository interface.
 */
@Repository
public interface RequisiteWaiverRepository extends JpaRepository<RequisiteWaiver, Long> {
    
    /**
     * Find all waivers for a specific student
     */
    @Query("SELECT w FROM RequisiteWaiver w WHERE w.student.userId = :studentId ORDER BY w.createdAt DESC")
    List<RequisiteWaiver> findByStudentId(@Param("studentId") Long studentId);
    
    /**
     * Find all pending waivers
     */
    @Query("SELECT w FROM RequisiteWaiver w WHERE w.status = 'PENDING' ORDER BY w.createdAt DESC")
    List<RequisiteWaiver> findPendingWaivers();
    
    /**
     * Find waivers that need approval from an advisor (for a specific department)
     */
    @Query("SELECT w FROM RequisiteWaiver w WHERE w.status = 'PENDING' " +
           "AND w.classEntity.department = :department")
    List<RequisiteWaiver> findWaiversForAdvisor(@Param("department") String department);
    
    /**
     * Find all pending waivers for registrar approval
     */
    @Query("SELECT w FROM RequisiteWaiver w WHERE w.status = 'PENDING' ORDER BY w.createdAt DESC")
    List<RequisiteWaiver> findWaiversForRegistrar();
    
    /**
     * Check if there's an approved waiver for a specific student, class, and requisite type
     */
    @Query("SELECT w FROM RequisiteWaiver w WHERE w.student.userId = :studentId " +
           "AND w.classEntity.classId = :classId " +
           "AND w.requisiteType = :requisiteType " +
           "AND w.status = 'APPROVED'")
    Optional<RequisiteWaiver> findApprovedWaiver(@Param("studentId") Long studentId, 
                                                 @Param("classId") Long classId, 
                                                 @Param("requisiteType") RequisiteType requisiteType);
    
    /**
     * Find waiver by student, class, and requisite type
     */
    @Query("SELECT w FROM RequisiteWaiver w WHERE w.student.userId = :studentId " +
           "AND w.classEntity.classId = :classId " +
           "AND w.requisiteType = :requisiteType")
    Optional<RequisiteWaiver> findByStudentAndClassAndType(@Param("studentId") Long studentId,
                                                           @Param("classId") Long classId,
                                                           @Param("requisiteType") RequisiteType requisiteType);
}

