package edu.sbu.cse416.sam_system.repository;

import edu.sbu.cse416.sam_system.entity.ClassEntity;
import edu.sbu.cse416.sam_system.entity.User;
import edu.sbu.cse416.sam_system.entity.WaitlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * TP: Used Cursor to implement JPA repository for waitlist entries.
 * Repository interface for WaitlistEntry entity.
 * Provides database operations for waitlist functionality.
 * 
 * Features:
 * - Find waitlist entries by class
 * - Check queue position
 * - Delete entries by semester/year
 */
@Repository
public interface WaitlistRepository extends JpaRepository<WaitlistEntry, Long> {

    List<WaitlistEntry> findByClassEntity_ClassIdOrderByCreatedAtAsc(Long classId);

    Optional<WaitlistEntry> findFirstByClassEntityOrderByCreatedAtAsc(ClassEntity classEntity);

    boolean existsByClassEntityAndUser(ClassEntity classEntity, User user);

    long countByClassEntity(ClassEntity classEntity);
    
    /**
     * Count waitlist entries with WAITING status for a class
     * @param classEntity The class entity
     * @return count of active waitlist entries
     */
    @Query("SELECT COUNT(w) FROM WaitlistEntry w WHERE w.classEntity = :classEntity AND w.status = 'WAITING'")
    long countActiveWaitlistByClassEntity(@Param("classEntity") ClassEntity classEntity);

    @Modifying
    @Transactional
    void deleteByClassEntityAndUser(ClassEntity classEntity, User user);

    @Query("SELECT COALESCE(MIN(w.createdAt), CURRENT_TIMESTAMP) FROM WaitlistEntry w WHERE w.classEntity.classId = :classId")
    Optional<java.time.LocalDateTime> firstCreatedAt(@Param("classId") Long classId);
    
    /**
     * Delete all waitlist entries for classes in a specific semester/year
     * @param semester The semester
     * @param year The year
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM WaitlistEntry w WHERE w.classEntity.semester = :semester AND w.classEntity.year = :year")
    void deleteBySemesterAndYear(@Param("semester") String semester, @Param("year") Integer year);
}


