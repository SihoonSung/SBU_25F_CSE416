package edu.sbu.cse416.sam_system.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity representing a student's planned major.
 * This is different from MajorMinorDeclaration - it's just for tracking
 * admission and degree requirements status without formal declaration.
 */
@Entity
@Table(name = "planned_majors")
public class PlannedMajor {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "planned_major_id")
    private Long plannedMajorId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;
    
    @Column(name = "subject_code", nullable = false, length = 10)
    private String subjectCode;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "degree_type", nullable = false)
    private DegreeType degreeType;
    
    @Column(name = "planned_at", nullable = false)
    private LocalDateTime plannedAt;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    // Constructors
    public PlannedMajor() {}
    
    public PlannedMajor(User student, String subjectCode, DegreeType degreeType) {
        this.student = student;
        this.subjectCode = subjectCode;
        this.degreeType = degreeType;
        this.plannedAt = LocalDateTime.now();
        this.isActive = true;
    }
    
    // Getters and Setters
    public Long getPlannedMajorId() {
        return plannedMajorId;
    }
    
    public void setPlannedMajorId(Long plannedMajorId) {
        this.plannedMajorId = plannedMajorId;
    }
    
    public User getStudent() {
        return student;
    }
    
    public void setStudent(User student) {
        this.student = student;
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
    
    public LocalDateTime getPlannedAt() {
        return plannedAt;
    }
    
    public void setPlannedAt(LocalDateTime plannedAt) {
        this.plannedAt = plannedAt;
    }
    
    public Boolean getIsActive() {
        return isActive;
    }
    
    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
    
    @Override
    public String toString() {
        return "PlannedMajor{" +
                "plannedMajorId=" + plannedMajorId +
                ", student=" + (student != null ? student.getUserId() : null) +
                ", subjectCode='" + subjectCode + '\'' +
                ", degreeType=" + degreeType +
                ", plannedAt=" + plannedAt +
                ", isActive=" + isActive +
                '}';
    }
}
