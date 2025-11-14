package edu.sbu.cse416.sam_system.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity representing a waived course for degree requirements.
 * 
 * The registrar waives a degree requirement indirectly, by waiving individual courses
 * as if the student had taken the courses and received an adequate grade.
 * Such courses contribute to satisfying major-specific degree requirements
 * (e.g., "at least 12 credits of upper-division CSE classes").
 * They do NOT contribute to:
 * - University-level minimum credits needed for graduation
 * - Student's GPA
 * 
 * TP: Used Cursor to implement degree requirement waiver system
 */
@Entity
@Table(name = "degree_requirement_waivers")
public class DegreeRequirementWaiver {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "waiver_id")
    private Long waiverId;
    
    // Student for whom course is being waived
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;
    
    // Major or minor for which this course waiver applies
    @Column(name = "subject_code", nullable = false)
    private String subjectCode; // e.g., "CSE"
    
    // Course being waived (e.g., "CSE 300")
    @Column(name = "department", nullable = false)
    private String department; // e.g., "CSE"
    
    @Column(name = "course_num", nullable = false)
    private Integer courseNum; // e.g., 300
    
    // Credits for this waived course
    @Column(name = "credits", nullable = false)
    private Integer credits;
    
    // Grade assigned to this waived course (e.g., "A", "B+", "P")
    // This grade does NOT affect GPA
    @Column(name = "grade")
    private String grade;
    
    // Approved by (registrar or advisor)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by", nullable = false)
    private User approvedBy;
    
    @Column(name = "approved_at", nullable = false)
    private LocalDateTime approvedAt;
    
    // Optional note from approver
    @Column(name = "note", columnDefinition = "TEXT")
    private String note;
    
    // Timestamps
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // Constructors
    public DegreeRequirementWaiver() {
        this.createdAt = LocalDateTime.now();
        this.approvedAt = LocalDateTime.now();
    }
    
    public DegreeRequirementWaiver(User student, String subjectCode, String department, 
                                   Integer courseNum, Integer credits, String grade, User approvedBy) {
        this();
        this.student = student;
        this.subjectCode = subjectCode;
        this.department = department;
        this.courseNum = courseNum;
        this.credits = credits;
        this.grade = grade;
        this.approvedBy = approvedBy;
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
    
    public String getSubjectCode() {
        return subjectCode;
    }
    
    public void setSubjectCode(String subjectCode) {
        this.subjectCode = subjectCode;
    }
    
    public String getDepartment() {
        return department;
    }
    
    public void setDepartment(String department) {
        this.department = department;
    }
    
    public Integer getCourseNum() {
        return courseNum;
    }
    
    public void setCourseNum(Integer courseNum) {
        this.courseNum = courseNum;
    }
    
    public Integer getCredits() {
        return credits;
    }
    
    public void setCredits(Integer credits) {
        this.credits = credits;
    }
    
    public String getGrade() {
        return grade;
    }
    
    public void setGrade(String grade) {
        this.grade = grade;
    }
    
    /**
     * Get course code (e.g., "CSE 300")
     */
    public String getCourseCode() {
        return department + " " + courseNum;
    }
    
    public User getApprovedBy() {
        return approvedBy;
    }
    
    public void setApprovedBy(User approvedBy) {
        this.approvedBy = approvedBy;
    }
    
    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }
    
    public void setApprovedAt(LocalDateTime approvedAt) {
        this.approvedAt = approvedAt;
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
}

