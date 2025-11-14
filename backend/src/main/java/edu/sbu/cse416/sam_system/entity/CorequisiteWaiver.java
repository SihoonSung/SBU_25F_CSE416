package edu.sbu.cse416.sam_system.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity representing a corequisite waiver request.
 * Students can request waivers to enroll in classes without meeting corequisites.
 * Requires approval from instructor or advisor.
 * 
 * TP: Used Cursor to implement corequisite waiver system
 */
@Entity
@Table(name = "corequisite_waivers")
public class CorequisiteWaiver {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "waiver_id")
    private Long waiverId;
    
    // Student requesting the waiver
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;
    
    // Class for which corequisite is being waived
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id", nullable = false)
    private ClassEntity classEntity;
    
    // Corequisite being waived (e.g., "CSE 114")
    @Column(name = "corequisite", columnDefinition = "TEXT", nullable = false)
    private String corequisite;
    
    // Approval from instructor
    @Column(name = "instructor_approved")
    private Boolean instructorApproved = null; // null = pending, true = approved, false = rejected
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instructor_approved_by")
    private User instructorApprovedBy;
    
    @Column(name = "instructor_approved_at")
    private LocalDateTime instructorApprovedAt;
    
    // Approval from advisor (department-level or higher)
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
    
    // Optional note from approver
    @Column(name = "note", columnDefinition = "TEXT")
    private String note;
    
    // Timestamps
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    public enum WaiverStatus {
        PENDING,    // Waiting for approvals
        APPROVED,   // Approved
        REJECTED    // Rejected
    }
    
    // Constructors
    public CorequisiteWaiver() {
        this.createdAt = LocalDateTime.now();
    }
    
    public CorequisiteWaiver(User student, ClassEntity classEntity, String corequisite) {
        this();
        this.student = student;
        this.classEntity = classEntity;
        this.corequisite = corequisite;
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
    
    public ClassEntity getClassEntity() {
        return classEntity;
    }
    
    public void setClassEntity(ClassEntity classEntity) {
        this.classEntity = classEntity;
    }
    
    public String getCorequisite() {
        return corequisite;
    }
    
    public void setCorequisite(String corequisite) {
        this.corequisite = corequisite;
    }
    
    public Boolean getInstructorApproved() {
        return instructorApproved;
    }
    
    public void setInstructorApproved(Boolean instructorApproved) {
        this.instructorApproved = instructorApproved;
    }
    
    public User getInstructorApprovedBy() {
        return instructorApprovedBy;
    }
    
    public void setInstructorApprovedBy(User instructorApprovedBy) {
        this.instructorApprovedBy = instructorApprovedBy;
    }
    
    public LocalDateTime getInstructorApprovedAt() {
        return instructorApprovedAt;
    }
    
    public void setInstructorApprovedAt(LocalDateTime instructorApprovedAt) {
        this.instructorApprovedAt = instructorApprovedAt;
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
    
    public String getNote() {
        return note;
    }
    
    public void setNote(String note) {
        this.note = note;
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
     * Check if waiver is approved (either instructor or advisor approved)
     */
    public boolean isApproved() {
        return (instructorApproved != null && instructorApproved) || 
               (advisorApproved != null && advisorApproved);
    }
    
    /**
     * Update status based on approvals
     */
    public void updateStatus() {
        if (isApproved()) {
            this.status = WaiverStatus.APPROVED;
        } else if ((instructorApproved != null && !instructorApproved) || 
                   (advisorApproved != null && !advisorApproved)) {
            this.status = WaiverStatus.REJECTED;
        } else {
            this.status = WaiverStatus.PENDING;
        }
        this.updatedAt = LocalDateTime.now();
    }
}

