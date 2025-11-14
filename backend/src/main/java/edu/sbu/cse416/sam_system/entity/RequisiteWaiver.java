package edu.sbu.cse416.sam_system.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity representing a requisite waiver request.
 * Students can request waivers to enroll in classes without meeting prerequisites, 
 * antirequisites, or corequisites.
 * Requires approval from advisor or registrar.
 * 
 * TP: Used Cursor to implement requisite waiver system.
 */
@Entity
@Table(name = "requisite_waivers")
public class RequisiteWaiver {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "waiver_id")
    private Long waiverId;
    
    // Student requesting the waiver
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;
    
    // Class for which the requisite is being waived
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id", nullable = false)
    private ClassEntity classEntity;
    
    // Type of requisite being waived
    @Enumerated(EnumType.STRING)
    @Column(name = "requisite_type", nullable = false)
    private RequisiteType requisiteType;
    
    // Approval from advisor
    @Column(name = "advisor_approved")
    private Boolean advisorApproved = null; // null = pending, true = approved, false = rejected
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "advisor_approved_by")
    private User advisorApprovedBy;
    
    @Column(name = "advisor_approved_at")
    private LocalDateTime advisorApprovedAt;
    
    // Approval from registrar
    @Column(name = "registrar_approved")
    private Boolean registrarApproved = null; // null = pending, true = approved, false = rejected
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registrar_approved_by")
    private User registrarApprovedBy;
    
    @Column(name = "registrar_approved_at")
    private LocalDateTime registrarApprovedAt;
    
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
    
    public enum RequisiteType {
        PREREQUISITE,
        ANTIREQUISITE,
        COREQUISITE
    }
    
    public enum WaiverStatus {
        PENDING,    // Waiting for approvals
        APPROVED,   // Approved by advisor or registrar
        REJECTED    // Rejected by advisor or registrar
    }
    
    // Constructors
    public RequisiteWaiver() {
        this.createdAt = LocalDateTime.now();
    }
    
    public RequisiteWaiver(User student, ClassEntity classEntity, RequisiteType requisiteType) {
        this();
        this.student = student;
        this.classEntity = classEntity;
        this.requisiteType = requisiteType;
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
    
    public RequisiteType getRequisiteType() {
        return requisiteType;
    }
    
    public void setRequisiteType(RequisiteType requisiteType) {
        this.requisiteType = requisiteType;
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
    
    public Boolean getRegistrarApproved() {
        return registrarApproved;
    }
    
    public void setRegistrarApproved(Boolean registrarApproved) {
        this.registrarApproved = registrarApproved;
    }
    
    public User getRegistrarApprovedBy() {
        return registrarApprovedBy;
    }
    
    public void setRegistrarApprovedBy(User registrarApprovedBy) {
        this.registrarApprovedBy = registrarApprovedBy;
    }
    
    public LocalDateTime getRegistrarApprovedAt() {
        return registrarApprovedAt;
    }
    
    public void setRegistrarApprovedAt(LocalDateTime registrarApprovedAt) {
        this.registrarApprovedAt = registrarApprovedAt;
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
     * Check if waiver is approved (by advisor or registrar)
     */
    public boolean isApproved() {
        return (advisorApproved != null && advisorApproved) || 
               (registrarApproved != null && registrarApproved);
    }
    
    /**
     * Update status based on approvals
     */
    public void updateStatus() {
        if (isApproved()) {
            this.status = WaiverStatus.APPROVED;
        } else if ((advisorApproved != null && !advisorApproved) || 
                   (registrarApproved != null && !registrarApproved)) {
            this.status = WaiverStatus.REJECTED;
        } else {
            this.status = WaiverStatus.PENDING;
        }
        this.updatedAt = LocalDateTime.now();
    }
}

