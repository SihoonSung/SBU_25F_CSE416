package edu.sbu.cse416.sam_system.entity;

/*
 * TP: Used Cursor to implement Course entity for storing scraped course information
 * Features:
 * - Stores course details: subject, number, name, description, credits
 * - Handles prerequisites, corequisites, antirequisites, advisory prerequisites
 * - Tracks SBCs satisfied and Classie evaluation link
 * - Links to Term entity for catalog version tracking
 */

import jakarta.persistence.*;

@Entity
@Table(name = "courses")
public class Course {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "subject", nullable = false)
    private String subject; // e.g., "CSE"
    
    @Column(name = "course_number", nullable = false)
    private String courseNumber; // e.g., "416"
    
    @Column(name = "course_name", nullable = false)
    private String courseName;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "prerequisites", columnDefinition = "TEXT")
    private String prerequisites;
    
    @Column(name = "corequisites", columnDefinition = "TEXT")
    private String corequisites;
    
    @Column(name = "antirequisites", columnDefinition = "TEXT")
    private String antirequisites;
    
    @Column(name = "advisory_prerequisites", columnDefinition = "TEXT")
    private String advisoryPrerequisites;
    
    @Column(name = "credits")
    private Integer credits;
    
    @Column(name = "sbcs_satisfied", columnDefinition = "TEXT")
    private String sbcsSatisfied; // Stony Brook Curriculum requirements
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "term_id", nullable = false)
    private Term term;
    
    @Column(name = "classie_link")
    private String classieLink; // Link to Classie evaluations
    
    // Constructors
    public Course() {}
    
    public Course(String subject, String courseNumber, String courseName, Term term) {
        this.subject = subject;
        this.courseNumber = courseNumber;
        this.courseName = courseName;
        this.term = term;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getSubject() {
        return subject;
    }
    
    public void setSubject(String subject) {
        this.subject = subject;
    }
    
    public String getCourseNumber() {
        return courseNumber;
    }
    
    public void setCourseNumber(String courseNumber) {
        this.courseNumber = courseNumber;
    }
    
    public String getCourseName() {
        return courseName;
    }
    
    public void setCourseName(String courseName) {
        this.courseName = courseName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getPrerequisites() {
        return prerequisites;
    }
    
    public void setPrerequisites(String prerequisites) {
        this.prerequisites = prerequisites;
    }
    
    public String getCorequisites() {
        return corequisites;
    }
    
    public void setCorequisites(String corequisites) {
        this.corequisites = corequisites;
    }
    
    public String getAntirequisites() {
        return antirequisites;
    }
    
    public void setAntirequisites(String antirequisites) {
        this.antirequisites = antirequisites;
    }
    
    public String getAdvisoryPrerequisites() {
        return advisoryPrerequisites;
    }
    
    public void setAdvisoryPrerequisites(String advisoryPrerequisites) {
        this.advisoryPrerequisites = advisoryPrerequisites;
    }
    
    public Integer getCredits() {
        return credits;
    }
    
    public void setCredits(Integer credits) {
        this.credits = credits;
    }
    
    public String getSbcsSatisfied() {
        return sbcsSatisfied;
    }
    
    public void setSbcsSatisfied(String sbcsSatisfied) {
        this.sbcsSatisfied = sbcsSatisfied;
    }
    
    public Term getTerm() {
        return term;
    }
    
    public void setTerm(Term term) {
        this.term = term;
    }
    
    public String getClassieLink() {
        return classieLink;
    }
    
    public void setClassieLink(String classieLink) {
        this.classieLink = classieLink;
    }
    
    // Helper methods
    public String getFullCourseCode() {
        return subject + " " + courseNumber;
    }
    
    public String getClassieUrl() {
        if (classieLink != null && !classieLink.isEmpty()) {
            return classieLink;
        }
        // Default Classie URL format - this may need to be updated based on actual Classie structure
        return "https://classie.evals.stonybrook.edu/sb/" + subject + "/" + courseNumber;
    }
    
    @Override
    public String toString() {
        return "Course{" +
                "id=" + id +
                ", subject='" + subject + '\'' +
                ", courseNumber='" + courseNumber + '\'' +
                ", courseName='" + courseName + '\'' +
                ", credits=" + credits +
                ", term=" + (term != null ? term.getName() : "null") +
                '}';
    }
}
