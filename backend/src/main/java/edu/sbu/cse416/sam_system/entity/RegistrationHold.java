package edu.sbu.cse416.sam_system.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity class representing a registration hold for a student.
 * Supports two types: ACADEMIC_ADVISING and FINANCIAL
 * 
 * TP: Used Cursor to implement registration hold system
 */
@Entity
@Table(name = "registration_holds")
public class RegistrationHold {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "hold_id")
    private Long holdId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "hold_type", nullable = false)
    private HoldType holdType;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "placed_by_user_id", nullable = false)
    private User placedBy;
    
    @Column(name = "placed_at", nullable = false)
    private LocalDateTime placedAt;
    
    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;
    
    public enum HoldType {
        ACADEMIC_ADVISING,  // Placed by advisors
        FINANCIAL           // Placed by registrars
    }
    
    // Constructors
    public RegistrationHold() {
        this.placedAt = LocalDateTime.now();
    }
    
    public RegistrationHold(User student, HoldType holdType, User placedBy, String reason) {
        this();
        this.student = student;
        this.holdType = holdType;
        this.placedBy = placedBy;
        this.reason = reason;
    }
    
    // Getters and Setters
    public Long getHoldId() {
        return holdId;
    }
    
    public void setHoldId(Long holdId) {
        this.holdId = holdId;
    }
    
    public User getStudent() {
        return student;
    }
    
    public void setStudent(User student) {
        this.student = student;
    }
    
    public HoldType getHoldType() {
        return holdType;
    }
    
    public void setHoldType(HoldType holdType) {
        this.holdType = holdType;
    }
    
    public User getPlacedBy() {
        return placedBy;
    }
    
    public void setPlacedBy(User placedBy) {
        this.placedBy = placedBy;
    }
    
    public LocalDateTime getPlacedAt() {
        return placedAt;
    }
    
    public void setPlacedAt(LocalDateTime placedAt) {
        this.placedAt = placedAt;
    }
    
    public String getReason() {
        return reason;
    }
    
    public void setReason(String reason) {
        this.reason = reason;
    }
    
}

