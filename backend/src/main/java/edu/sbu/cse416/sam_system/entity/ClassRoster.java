package edu.sbu.cse416.sam_system.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity class representing a student's enrollment in a class.
 * Maps to the 'class_roster' table in the database.
 * Contains information about which students are enrolled in which classes.
 * 
 * This entity is used for:
 * - Tracking class enrollment
 * - Managing class capacity
 * - Preparing for grade management
 * 
 * TP: Used Cursor to implement JPA entity and database mapping.
 */
@Entity
@Table(name = "class_roster",
       uniqueConstraints = @UniqueConstraint(columnNames = {"class_id", "user_id"}))
public class ClassRoster {
    
    // Primary key - auto-generated ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "roster_id")
    private Long rosterId;
    
    // Foreign key to ClassEntity
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id", nullable = false)
    private ClassEntity classEntity;
    
    // Foreign key to User
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    // Enrollment date
    @Column(name = "enrollment_date")
    private LocalDateTime enrollmentDate;
    
    // Enrollment status (e.g., "ENROLLED", "DROPPED", "WITHDRAWN")
    @Column(name = "status")
    private String status;
    
    // Constructors
    public ClassRoster() {
    }
    
    public ClassRoster(ClassEntity classEntity, User user) {
        this.classEntity = classEntity;
        this.user = user;
        this.enrollmentDate = LocalDateTime.now();
        this.status = "ENROLLED";
    }
    
    // Getters and Setters
    public Long getRosterId() {
        return rosterId;
    }
    
    public void setRosterId(Long rosterId) {
        this.rosterId = rosterId;
    }
    
    public ClassEntity getClassEntity() {
        return classEntity;
    }
    
    public void setClassEntity(ClassEntity classEntity) {
        this.classEntity = classEntity;
    }
    
    public User getUser() {
        return user;
    }
    
    public void setUser(User user) {
        this.user = user;
    }
    
    public LocalDateTime getEnrollmentDate() {
        return enrollmentDate;
    }
    
    public void setEnrollmentDate(LocalDateTime enrollmentDate) {
        this.enrollmentDate = enrollmentDate;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    /**
     * Check if the enrollment is active
     * @return true if status is "ENROLLED"
     */
    public boolean isActive() {
        return "ENROLLED".equals(status);
    }
    
    /**
     * Drop the enrollment
     */
    public void drop() {
        this.status = "DROPPED";
    }
    
    /**
     * Withdraw from the enrollment
     */
    public void withdraw() {
        this.status = "WITHDRAWN";
    }
}
