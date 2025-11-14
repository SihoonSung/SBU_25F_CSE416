package edu.sbu.cse416.sam_system.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity class representing an audit log entry for student profile changes.
 * Tracks all changes to student data including majors, minors, grades, holds, etc.
 * 
 * TP: Used Cursor to implement audit log system
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;
    
    @Column(name = "ts", nullable = false)
    private LocalDateTime timestamp;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performing_user_id", nullable = false)
    private User performingUser;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "actor_role", nullable = false)
    private UserRole actorRole;
    
    @Column(name = "actor_display", nullable = false)
    private String actorDisplay; // Display name for the performing user
    
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private ActionType action;
    
    @Column(name = "details", columnDefinition = "TEXT")
    private String details; // JSON or text description of the change
    
    @Column(name = "note", columnDefinition = "TEXT")
    private String note; // Optional note from registrar or advisor
    
    public enum ActionType {
        // Major/Minor changes
        MAJOR_DECLARED,
        MAJOR_CHANGED,
        MAJOR_REMOVED,
        MINOR_DECLARED,
        MINOR_CHANGED,
        MINOR_REMOVED,
        
        // Major/Minor declaration requests (student-initiated)
        MAJOR_DECLARATION_REQUESTED,
        MINOR_DECLARATION_REQUESTED,
        
        // Requirement versions
        MAJOR_REQUIREMENT_VERSION_CHANGED,
        MINOR_REQUIREMENT_VERSION_CHANGED,
        
        // Waived requirements
        DEGREE_REQUIREMENT_WAIVED,
        PREREQUISITE_WAIVED,
        COREQUISITE_WAIVED,
        ANTIREQUISITE_WAIVED,
        TIME_CONFLICT_WAIVED,
        
        // Waiver requests (student-initiated)
        PREREQUISITE_WAIVER_REQUESTED,
        COREQUISITE_WAIVER_REQUESTED,
        ANTIREQUISITE_WAIVER_REQUESTED,
        TIME_CONFLICT_WAIVER_REQUESTED,
        
        // Class schedule
        CLASS_ENROLLED,
        CLASS_DROPPED,
        CLASS_ADDED,
        
        // Registration holds
        HOLD_PLACED,
        HOLD_REMOVED,
        
        // Grades
        GRADE_ASSIGNED,
        GRADE_CHANGED
    }
    
    // Constructors
    public AuditLog() {
        this.timestamp = LocalDateTime.now();
    }
    
    public AuditLog(User student, User performingUser, ActionType action, String details) {
        this();
        this.student = student;
        this.performingUser = performingUser;
        this.actorRole = performingUser.getRole();
        this.actorDisplay = performingUser.getFullName();
        this.action = action;
        this.details = details;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public User getStudent() {
        return student;
    }
    
    public void setStudent(User student) {
        this.student = student;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    public User getPerformingUser() {
        return performingUser;
    }
    
    public void setPerformingUser(User performingUser) {
        this.performingUser = performingUser;
        if (performingUser != null) {
            this.actorRole = performingUser.getRole();
            this.actorDisplay = performingUser.getFullName();
        }
    }
    
    public UserRole getActorRole() {
        return actorRole;
    }
    
    public void setActorRole(UserRole actorRole) {
        this.actorRole = actorRole;
    }
    
    public String getActorDisplay() {
        return actorDisplay;
    }
    
    public void setActorDisplay(String actorDisplay) {
        this.actorDisplay = actorDisplay;
    }
    
    public ActionType getAction() {
        return action;
    }
    
    public void setAction(ActionType action) {
        this.action = action;
    }
    
    public String getDetails() {
        return details;
    }
    
    public void setDetails(String details) {
        this.details = details;
    }
    
    public String getNote() {
        return note;
    }
    
    public void setNote(String note) {
        this.note = note;
    }
}

