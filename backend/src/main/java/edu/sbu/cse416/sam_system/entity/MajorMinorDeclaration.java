package edu.sbu.cse416.sam_system.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;

/**
 * TP: Used cursor to implement entity representing a student's request to declare a major or minor.
 * Entity representing a student's request to declare a major or minor.
 * Supports up to 2 majors and 3 minors per student.
 */
@Entity
@Table(name = "major_minor_declarations")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class MajorMinorDeclaration {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "declaration_id")
    private Long declarationId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;
    
    @Column(name = "student_netid", nullable = false)
    private String studentNetId;
    
    @Column(name = "subject_code", nullable = false)
    private String subjectCode;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "declaration_type", nullable = false)
    private DeclarationType declarationType; // MAJOR or MINOR
    
    @Column(name = "effective_term", nullable = false)
    private String effectiveTerm; // e.g., "Fall 2025"
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DeclarationStatus status; // PENDING, APPROVED, REJECTED
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "advisor_id")
    private User advisor; // Department-level advisor who approved/rejected
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ceas_advisor_id")
    private User ceasAdvisor; // CEAS advisor for CEAS transfers
    
    @Column(name = "advisor_notes")
    private String advisorNotes;
    
    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;
    
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    // Constructors
    public MajorMinorDeclaration() {
        this.createdAt = LocalDateTime.now();
        this.status = DeclarationStatus.PENDING;
        this.isActive = true;
    }
    
    // Getters and Setters
    public Long getDeclarationId() { return declarationId; }
    public void setDeclarationId(Long declarationId) { this.declarationId = declarationId; }
    
    public User getStudent() { return student; }
    public void setStudent(User student) { this.student = student; }
    
    public String getStudentNetId() { return studentNetId; }
    public void setStudentNetId(String studentNetId) { this.studentNetId = studentNetId; }
    
    public String getSubjectCode() { return subjectCode; }
    public void setSubjectCode(String subjectCode) { this.subjectCode = subjectCode; }
    
    public DeclarationType getDeclarationType() { return declarationType; }
    public void setDeclarationType(DeclarationType declarationType) { this.declarationType = declarationType; }
    
    public String getEffectiveTerm() { return effectiveTerm; }
    public void setEffectiveTerm(String effectiveTerm) { this.effectiveTerm = effectiveTerm; }
    
    public DeclarationStatus getStatus() { return status; }
    public void setStatus(DeclarationStatus status) { this.status = status; }
    
    public User getAdvisor() { return advisor; }
    public void setAdvisor(User advisor) { this.advisor = advisor; }
    
    public User getCeasAdvisor() { return ceasAdvisor; }
    public void setCeasAdvisor(User ceasAdvisor) { this.ceasAdvisor = ceasAdvisor; }
    
    public String getAdvisorNotes() { return advisorNotes; }
    public void setAdvisorNotes(String advisorNotes) { this.advisorNotes = advisorNotes; }
    
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    
    // Business methods
    public void approve(User advisor, String notes) {
        this.status = DeclarationStatus.APPROVED;
        // Check if this is a CEAS advisor (if ceasAdvisor was set during creation)
        if (this.ceasAdvisor != null && this.ceasAdvisor.equals(advisor)) {
            this.ceasAdvisor = advisor;
        } else {
            this.advisor = advisor;
        }
        this.advisorNotes = notes;
        this.reviewedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.isActive = true;
    }
    
    public void reject(User advisor, String notes) {
        this.status = DeclarationStatus.REJECTED;
        // Check if this is a CEAS advisor (if ceasAdvisor was set during creation)
        if (this.ceasAdvisor != null && this.ceasAdvisor.equals(advisor)) {
            this.ceasAdvisor = advisor;
        } else {
            this.advisor = advisor;
        }
        this.advisorNotes = notes;
        this.reviewedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.isActive = false;
    }
    
    public boolean isPending() {
        return this.status == DeclarationStatus.PENDING;
    }
    
    public boolean isApproved() {
        return this.status == DeclarationStatus.APPROVED;
    }
    
    public boolean isRejected() {
        return this.status == DeclarationStatus.REJECTED;
    }
    
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}