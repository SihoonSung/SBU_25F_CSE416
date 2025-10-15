package edu.sbu.cse416.sam_system.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Entity class representing degree requirements for majors and minors.
 * 
 * TP: Used Cursor to implement degree requirements management system
 */
@Entity
@Table(name = "degree_requirements")
public class DegreeRequirement {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "requirement_id")
    private Long requirementId;
    
    @Column(name = "subject_code", nullable = false)
    private String subjectCode;
    
    @Column(name = "degree_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private DegreeType degreeType;
    
    @Column(name = "requirements_version", nullable = false)
    private String requirementsVersion;
    
    @Column(name = "effective_term", nullable = false)
    private String effectiveTerm;
    
    @Column(name = "total_credits_required", nullable = false)
    private Integer totalCreditsRequired;
    
    @Column(name = "is_restricted", nullable = false)
    private Boolean isRestricted = false;
    
    @Column(name = "admission_requirements", columnDefinition = "TEXT")
    private String admissionRequirements;
    
    @Column(name = "course_requirements", columnDefinition = "TEXT")
    private String courseRequirements;
    
    @Column(name = "gpa_requirement")
    private Double gpaRequirement;
    
    @Column(name = "prerequisite_courses", columnDefinition = "TEXT")
    private String prerequisiteCourses;
    
    @Column(name = "sbc_requirements", columnDefinition = "TEXT")
    private String sbcRequirements;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "created_by", nullable = false)
    private String createdBy;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    // Constructors
    public DegreeRequirement() {
        this.createdAt = LocalDateTime.now();
    }
    
    public DegreeRequirement(String subjectCode, DegreeType degreeType, 
                           String requirementsVersion, String effectiveTerm,
                           Integer totalCreditsRequired, String createdBy) {
        this();
        this.subjectCode = subjectCode;
        this.degreeType = degreeType;
        this.requirementsVersion = requirementsVersion;
        this.effectiveTerm = effectiveTerm;
        this.totalCreditsRequired = totalCreditsRequired;
        this.createdBy = createdBy;
    }
    
    // Getters and Setters
    public Long getRequirementId() {
        return requirementId;
    }
    
    public void setRequirementId(Long requirementId) {
        this.requirementId = requirementId;
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
    
    public String getRequirementsVersion() {
        return requirementsVersion;
    }
    
    public void setRequirementsVersion(String requirementsVersion) {
        this.requirementsVersion = requirementsVersion;
    }
    
    public String getEffectiveTerm() {
        return effectiveTerm;
    }
    
    public void setEffectiveTerm(String effectiveTerm) {
        this.effectiveTerm = effectiveTerm;
    }
    
    public Integer getTotalCreditsRequired() {
        return totalCreditsRequired;
    }
    
    public void setTotalCreditsRequired(Integer totalCreditsRequired) {
        this.totalCreditsRequired = totalCreditsRequired;
    }
    
    public Boolean getIsRestricted() {
        return isRestricted;
    }
    
    public void setIsRestricted(Boolean isRestricted) {
        this.isRestricted = isRestricted;
    }
    
    public String getAdmissionRequirements() {
        return admissionRequirements;
    }
    
    public void setAdmissionRequirements(String admissionRequirements) {
        this.admissionRequirements = admissionRequirements;
    }
    
    public String getCourseRequirements() {
        return courseRequirements;
    }
    
    public void setCourseRequirements(String courseRequirements) {
        this.courseRequirements = courseRequirements;
    }
    
    public Double getGpaRequirement() {
        return gpaRequirement;
    }
    
    public void setGpaRequirement(Double gpaRequirement) {
        this.gpaRequirement = gpaRequirement;
    }
    
    public String getPrerequisiteCourses() {
        return prerequisiteCourses;
    }
    
    public void setPrerequisiteCourses(String prerequisiteCourses) {
        this.prerequisiteCourses = prerequisiteCourses;
    }
    
    public String getSbcRequirements() {
        return sbcRequirements;
    }
    
    public void setSbcRequirements(String sbcRequirements) {
        this.sbcRequirements = sbcRequirements;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public String getCreatedBy() {
        return createdBy;
    }
    
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
    
    public Boolean getIsActive() {
        return isActive;
    }
    
    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
    
    @Override
    public String toString() {
        return "DegreeRequirement{" +
                "requirementId=" + requirementId +
                ", subjectCode='" + subjectCode + '\'' +
                ", degreeType=" + degreeType +
                ", requirementsVersion='" + requirementsVersion + '\'' +
                ", effectiveTerm='" + effectiveTerm + '\'' +
                ", totalCreditsRequired=" + totalCreditsRequired +
                ", isRestricted=" + isRestricted +
                ", createdBy='" + createdBy + '\'' +
                ", isActive=" + isActive +
                '}';
    }
}
