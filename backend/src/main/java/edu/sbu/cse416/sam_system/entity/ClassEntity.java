package edu.sbu.cse416.sam_system.entity;

import jakarta.persistence.*;

/**
 * Entity class representing a course/class in the system.
 * Maps to the 'classes' table in the database.
 * Contains information about courses available for registration.
 * 
 * TP: Used Cursor to implement JPA entity and database mapping.
 */
@Entity
@Table(name = "classes")
public class ClassEntity {
    
    // Primary key - auto-generated class ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "class_id")
    private Long classId;
    
    // Course department (e.g., CSE, AMS)
    @Column(name = "department", nullable = false)
    private String department;
    
    // Course number (e.g., 101, 214)
    @Column(name = "course_num", nullable = false)
    private Integer courseNum;
    
    // Section number (e.g., "01", "02")
    @Column(name = "section")
    private String section;
    
    // Semester (e.g., "Spring", "Fall")
    @Column(name = "semester")
    private String semester;
    
    // Year (e.g., 2025)
    @Column(name = "year")
    private Integer year;
    
    // Number of credits
    @Column(name = "credits")
    private Integer credits;
    
    // Course title
    @Column(name = "title")
    private String title;
    
    // Instructor name
    @Column(name = "instructor")
    private String instructor;
    
    // Schedule (e.g., "Mon/Wed 11:00 AM - 12:20 PM")
    @Column(name = "schedule")
    private String schedule;
    
    // Location (e.g., "Frey Hall 317")
    @Column(name = "location")
    private String location;
    
    // Prerequisites (e.g., "CSE 114")
    @Column(name = "prerequisites")
    private String prerequisites;
    
    // Constructors
    public ClassEntity() {
    }
    
    public ClassEntity(String department, Integer courseNum, String section, String semester, 
                  Integer year, Integer credits, String title,
                  String instructor, String schedule, String location, String prerequisites) {
        this.department = department;
        this.courseNum = courseNum;
        this.section = section;
        this.semester = semester;
        this.year = year;
        this.credits = credits;
        this.title = title;
        this.instructor = instructor;
        this.schedule = schedule;
        this.location = location;
        this.prerequisites = prerequisites;
    }
    
    /**
     * Helper method to get full course code with section
     * @return Course code in format "CSE101.01"
     */
    public String getCourseCode() {
        return department + courseNum + "." + section;
    }
    
    // Getters and Setters
    public Long getClassId() {
        return classId;
    }
    
    public void setClassId(Long classId) {
        this.classId = classId;
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
    
    public String getSection() {
        return section;
    }
    
    public void setSection(String section) {
        this.section = section;
    }
    
    public String getSemester() {
        return semester;
    }
    
    public void setSemester(String semester) {
        this.semester = semester;
    }
    
    public Integer getYear() {
        return year;
    }
    
    public void setYear(Integer year) {
        this.year = year;
    }
    
    public Integer getCredits() {
        return credits;
    }
    
    public void setCredits(Integer credits) {
        this.credits = credits;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getInstructor() {
        return instructor;
    }
    
    public void setInstructor(String instructor) {
        this.instructor = instructor;
    }
    
    public String getSchedule() {
        return schedule;
    }
    
    public void setSchedule(String schedule) {
        this.schedule = schedule;
    }
    
    public String getLocation() {
        return location;
    }
    
    public void setLocation(String location) {
        this.location = location;
    }
    
    public String getPrerequisites() {
        return prerequisites;
    }
    
    public void setPrerequisites(String prerequisites) {
        this.prerequisites = prerequisites;
    }
}

