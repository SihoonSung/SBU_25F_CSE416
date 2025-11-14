package edu.sbu.cse416.sam_system.repository;

import edu.sbu.cse416.sam_system.entity.AuditLog;
import edu.sbu.cse416.sam_system.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for AuditLog entity.
 * 
 * TP: Used Cursor to implement audit log repository
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    
    /**
     * Find all audit log entries for a student, ordered by timestamp descending
     * @param student Student user
     * @return List of audit log entries
     */
    @Query("SELECT a FROM AuditLog a WHERE a.student = :student ORDER BY a.timestamp DESC, a.id DESC")
    List<AuditLog> findByStudentOrderByTimestampDesc(@Param("student") User student);
    
    /**
     * Find all audit log entries for a student by student ID, ordered by timestamp descending
     * @param studentId Student user ID
     * @return List of audit log entries
     */
    @Query("SELECT a FROM AuditLog a WHERE a.student.userId = :studentId ORDER BY a.timestamp DESC, a.id DESC")
    List<AuditLog> findByStudentIdOrderByTimestampDesc(@Param("studentId") Long studentId);
}

