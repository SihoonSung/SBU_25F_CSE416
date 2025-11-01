package edu.sbu.cse416.sam_system.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity representing a time conflict waiver request.
 * Students can request waivers to enroll in classes with time conflicts.
 * Requires approval from both instructors and a department-level advisor.
 * 
 * TP: Used Cursor to implement time conflict waiver system.
 */
@Entity
@Table(name = "time_conflict_waivers")
public class TimeConflictWaiver {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "waiver_id")
    private Long waiverId;
    
    // Student requesting the waiver
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;
    
    // Currently enrolled class
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enrolled_class_id", nullable = false)
    private ClassEntity enrolledClass;
    
    // Conflicting class to add
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conflicting_class_id", nullable = false)
    private ClassEntity conflictingClass;
    
    // Approval from enrolled class instructor
    @Column(name = "enrolled_instructor_approved")
    private Boolean enrolledInstructorApproved = null; // null = pending, true = approved, false = rejected
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enrolled_instructor_approved_by")
    private User enrolledInstructorApprovedBy;
    
    @Column(name = "enrolled_instructor_approved_at")
    private LocalDateTime enrolledInstructorApprovedAt;
    
    // Approval from conflicting class instructor
    @Column(name = "conflicting_instructor_approved")
    private Boolean conflictingInstructorApproved = null; // null = pending, true = approved, false = rejected
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conflicting_instructor_approved_by")
    private User conflictingInstructorApprovedBy;
    
    @Column(name = "conflicting_instructor_approved_at")
    private LocalDateTime conflictingInstructorApprovedAt;
    
    // Approval from department-level advisor
    @Column(name = "advisor_approved")
    private Boolean advisorApproved = null; // null = pending, true = approved, false = rejected
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "advisor_approved_by")
    private User advisorApprovedBy;
    
    @Column(name = "advisor_approved_at")
    private LocalDateTime advisorApprovedAt;
    
    // Waiver status
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private WaiverStatus status = WaiverStatus.PENDING;
    
    // Optional reason from student
    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;
    
    // Timestamps
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    public enum WaiverStatus {
        PENDING,    // Waiting for approvals
        APPROVED,   // All approvals received
        REJECTED    // Rejected by someone
    }
    
    // Constructors
    public TimeConflictWaiver() {
        this.createdAt = LocalDateTime.now();
    }
    
    public TimeConflictWaiver(User student, ClassEntity enrolledClass, ClassEntity conflictingClass) {
        this();
        this.student = student;
        this.enrolledClass = enrolledClass;
        this.conflictingClass = conflictingClass;
    }
    
    // Getters and Setters
    public Long getWaiverId() {
        return waiverId;
    }
    
    public void setWaiverId(Long waiverId) {
        this.waiverId = waiverId;
    }
    
    public User getStudent() {
        return student;
    }
    
    public void setStudent(User student) {
        this.student = student;
    }
    
    public ClassEntity getEnrolledClass() {
        return enrolledClass;
    }
    
    public void setEnrolledClass(ClassEntity enrolledClass) {
        this.enrolledClass = enrolledClass;
    }
    
    public ClassEntity getConflictingClass() {
        return conflictingClass;
    }
    
    public void setConflictingClass(ClassEntity conflictingClass) {
        this.conflictingClass = conflictingClass;
    }
    
    public Boolean getEnrolledInstructorApproved() {
        return enrolledInstructorApproved;
    }
    
    public void setEnrolledInstructorApproved(Boolean enrolledInstructorApproved) {
        this.enrolledInstructorApproved = enrolledInstructorApproved;
    }
    
    public User getEnrolledInstructorApprovedBy() {
        return enrolledInstructorApprovedBy;
    }
    
    public void setEnrolledInstructorApprovedBy(User enrolledInstructorApprovedBy) {
        this.enrolledInstructorApprovedBy = enrolledInstructorApprovedBy;
    }
    
    public LocalDateTime getEnrolledInstructorApprovedAt() {
        return enrolledInstructorApprovedAt;
    }
    
    public void setEnrolledInstructorApprovedAt(LocalDateTime enrolledInstructorApprovedAt) {
        this.enrolledInstructorApprovedAt = enrolledInstructorApprovedAt;
    }
    
    public Boolean getConflictingInstructorApproved() {
        return conflictingInstructorApproved;
    }
    
    public void setConflictingInstructorApproved(Boolean conflictingInstructorApproved) {
        this.conflictingInstructorApproved = conflictingInstructorApproved;
    }
    
    public User getConflictingInstructorApprovedBy() {
        return conflictingInstructorApprovedBy;
    }
    
    public void setConflictingInstructorApprovedBy(User conflictingInstructorApprovedBy) {
        this.conflictingInstructorApprovedBy = conflictingInstructorApprovedBy;
    }
    
    public LocalDateTime getConflictingInstructorApprovedAt() {
        return conflictingInstructorApprovedAt;
    }
    
    public void setConflictingInstructorApprovedAt(LocalDateTime conflictingInstructorApprovedAt) {
        this.conflictingInstructorApprovedAt = conflictingInstructorApprovedAt;
    }
    
    public Boolean getAdvisorApproved() {
        return advisorApproved;
    }
    
    public void setAdvisorApproved(Boolean advisorApproved) {
        this.advisorApproved = advisorApproved;
    }
    
    public User getAdvisorApprovedBy() {
        return advisorApprovedBy;
    }
    
    public void setAdvisorApprovedBy(User advisorApprovedBy) {
        this.advisorApprovedBy = advisorApprovedBy;
    }
    
    public LocalDateTime getAdvisorApprovedAt() {
        return advisorApprovedAt;
    }
    
    public void setAdvisorApprovedAt(LocalDateTime advisorApprovedAt) {
        this.advisorApprovedAt = advisorApprovedAt;
    }
    
    public WaiverStatus getStatus() {
        return status;
    }
    
    public void setStatus(WaiverStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }
    
    public String getReason() {
        return reason;
    }
    
    public void setReason(String reason) {
        this.reason = reason;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    /**
     * Check if all approvals are received
     */
    public boolean isFullyApproved() {
        return enrolledInstructorApproved != null && enrolledInstructorApproved
            && conflictingInstructorApproved != null && conflictingInstructorApproved
            && advisorApproved != null && advisorApproved;
    }
    
    /**
     * Update status based on approvals
     */
    public void updateStatus() {
        if (isFullyApproved()) {
            this.status = WaiverStatus.APPROVED;
        } else if (enrolledInstructorApproved == Boolean.FALSE 
                || conflictingInstructorApproved == Boolean.FALSE 
                || advisorApproved == Boolean.FALSE) {
            this.status = WaiverStatus.REJECTED;
        } else {
            this.status = WaiverStatus.PENDING;
        }
        this.updatedAt = LocalDateTime.now();
    }
}

