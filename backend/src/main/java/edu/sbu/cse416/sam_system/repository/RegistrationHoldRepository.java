package edu.sbu.cse416.sam_system.repository;

import edu.sbu.cse416.sam_system.entity.RegistrationHold;
import edu.sbu.cse416.sam_system.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for RegistrationHold entity.
 * 
 * TP: Used Cursor to implement JPA repository interface.
 */
@Repository
public interface RegistrationHoldRepository extends JpaRepository<RegistrationHold, Long> {
    
    /**
     * Find all active holds for a specific student
     * @param student The student
     * @return List of active registration holds
     */
    @Query("SELECT h FROM RegistrationHold h WHERE h.student = :student AND h.isActive = true")
    List<RegistrationHold> findActiveHoldsByStudent(@Param("student") User student);
    
    /**
     * Find all holds (active and inactive) for a specific student
     * @param student The student
     * @return List of all registration holds
     */
    @Query("SELECT h FROM RegistrationHold h WHERE h.student = :student ORDER BY h.placedAt DESC")
    List<RegistrationHold> findAllHoldsByStudent(@Param("student") User student);
    
    /**
     * Check if student has any active holds
     * @param studentId Student ID
     * @return true if student has active holds, false otherwise
     */
    @Query("SELECT COUNT(h) > 0 FROM RegistrationHold h WHERE h.student.userId = :studentId AND h.isActive = true")
    boolean hasActiveHolds(@Param("studentId") Long studentId);
    
    /**
     * Find all active holds by type
     * @param holdType Hold type
     * @return List of active registration holds
     */
    @Query("SELECT h FROM RegistrationHold h WHERE h.holdType = :holdType AND h.isActive = true")
    List<RegistrationHold> findActiveHoldsByType(@Param("holdType") RegistrationHold.HoldType holdType);
}

