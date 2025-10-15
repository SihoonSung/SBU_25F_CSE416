package edu.sbu.cse416.sam_system.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity class representing a student's declaration of major or minor.
 * 
 * TP: Used Cursor to implement major/minor declaration system
 */
@Entity
@Table(name = "major_minor_declarations")
public class MajorMinorDeclaration {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "declaration_id")
    private Long declarationId;
    
    @Column(name = "student_netid", nullable = false)
    private String studentNetId;
    
    @Column(name = "subject_code", nullable = false)
    private String subjectCode;
    
    @Column(name = "degree_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private DegreeType degreeType;
    
    @Column(name = "declaration_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private DeclarationType declarationType;
    
    @Column(name = "effective_term", nullable = false)
    private String effectiveTerm;
    
    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private DeclarationStatus status = DeclarationStatus.PENDING;
    
    @Column(name = "advisor_netid")
    private String advisorNetId;
    
    @Column(name = "advisor_approval_date")
    private LocalDateTime advisorApprovalDate;
    
    @Column(name = "advisor_comments", columnDefinition = "TEXT")
    private String advisorComments;
    
    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    // Constructors
    public MajorMinorDeclaration() {
        this.createdAt = LocalDateTime.now();
        this.submittedAt = LocalDateTime.now();
    }
    
    public MajorMinorDeclaration(String studentNetId, String subjectCode, 
                               DegreeType degreeType, DeclarationType declarationType,
                               String effectiveTerm) {
        this();
        this.studentNetId = studentNetId;
        this.subjectCode = subjectCode;
        this.degreeType = degreeType;
        this.declarationType = declarationType;
        this.effectiveTerm = effectiveTerm;
    }
    
    // Getters and Setters
    public Long getDeclarationId() {
        return declarationId;
    }
    
    public void setDeclarationId(Long declarationId) {
        this.declarationId = declarationId;
    }
    
    public String getStudentNetId() {
        return studentNetId;
    }
    
    public void setStudentNetId(String studentNetId) {
        this.studentNetId = studentNetId;
    }
    
    public String getSubjectCode() {
        return subjectCode;
    }
    
    public void setSubjectCode(String subjectCode) {
        this.subjectCode = subjectCode;
    }
    
    public DegreeType getDegreeType() {
        return degreeType;
    }
    
    public void setDegreeType(DegreeType degreeType) {
        this.degreeType = degreeType;
    }
    
    public DeclarationType getDeclarationType() {
        return declarationType;
    }
    
    public void setDeclarationType(DeclarationType declarationType) {
        this.declarationType = declarationType;
    }
    
    public String getEffectiveTerm() {
        return effectiveTerm;
    }
    
    public void setEffectiveTerm(String effectiveTerm) {
        this.effectiveTerm = effectiveTerm;
    }
    
    public DeclarationStatus getStatus() {
        return status;
    }
    
    public void setStatus(DeclarationStatus status) {
        this.status = status;
    }
    
    public String getAdvisorNetId() {
        return advisorNetId;
    }
    
    public void setAdvisorNetId(String advisorNetId) {
        this.advisorNetId = advisorNetId;
    }
    
    public LocalDateTime getAdvisorApprovalDate() {
        return advisorApprovalDate;
    }
    
    public void setAdvisorApprovalDate(LocalDateTime advisorApprovalDate) {
        this.advisorApprovalDate = advisorApprovalDate;
    }
    
    public String getAdvisorComments() {
        return advisorComments;
    }
    
    public void setAdvisorComments(String advisorComments) {
        this.advisorComments = advisorComments;
    }
    
    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }
    
    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public Boolean getIsActive() {
        return isActive;
    }
    
    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
    
    @Override
    public String toString() {
        return "MajorMinorDeclaration{" +
                "declarationId=" + declarationId +
                ", studentNetId='" + studentNetId + '\'' +
                ", subjectCode='" + subjectCode + '\'' +
                ", degreeType=" + degreeType +
                ", declarationType=" + declarationType +
                ", effectiveTerm='" + effectiveTerm + '\'' +
                ", status=" + status +
                ", advisorNetId='" + advisorNetId + '\'' +
                ", submittedAt=" + submittedAt +
                ", isActive=" + isActive +
                '}';
    }
}
