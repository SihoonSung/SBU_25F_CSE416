package edu.sbu.cse416.sam_system.entity;

import jakarta.persistence.*;

/**
 * Entity class representing a course/class in the system.
 * Maps to the 'classes' table in the database.
 * Contains information about courses available for registration.
 * 
 * Composite index on semester and year for fast filtering and deletion.
 * 
 * TP: Used Cursor to implement JPA entity and database mapping.
 */
@Entity
@Table(name = "classes", 
       indexes = @Index(name = "idx_semester_year", columnList = "semester, academic_year"))
public class ClassEntity {
    
    // Primary key - class ID from course catalog (5-digit number)
    @Id
    @Column(name = "class_id")
    private Long classId;
    
    // Course department (e.g., CSE, AMS)
    @Column(name = "department", nullable = false)
    private String department;
    
    // Course number (e.g., 101, 214)
    @Column(name = "course_num", nullable = false)
    private Integer courseNum;
    
    // Section number (e.g., "01", "02", "L01")
    @Column(name = "section")
    private String section;
    
    // Section type (e.g., "LEC", "LAB", "REC")
    @Column(name = "section_type")
    private String sectionType;
    
    // Semester (e.g., "Spring", "Fall")
    @Column(name = "semester")
    private String semester;
    
    // Year (e.g., 2025)
    @Column(name = "academic_year")
    private Integer year;
    
    // Minimum number of credits (for variable credit courses)
    @Column(name = "min_credits")
    private Integer minCredits;
    
    // Maximum number of credits (for variable credit courses, null if fixed)
    @Column(name = "max_credits")
    private Integer maxCredits;
    
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
    
    // Lecture schedule (for courses with LAB sections)
    @Column(name = "lec_schedule")
    private String lecSchedule;
    
    // Lecture location (for courses with LAB sections)
    @Column(name = "lec_location")
    private String lecLocation;
    
    // Lecture instructor (for courses with LAB sections)
    @Column(name = "lec_instructor")
    private String lecInstructor;
    
    // Prerequisites (e.g., "CSE 114")
    // Using TEXT type to support long prerequisite descriptions
    @Column(name = "prerequisites", columnDefinition = "TEXT")
    private String prerequisites;
    
    // Corequisites (courses that must be taken concurrently)
    // Using TEXT type to support long corequisite descriptions
    @Column(name = "corequisites", columnDefinition = "TEXT")
    private String corequisites;
    
    // Antirequisites (courses that cannot be taken together)
    // Using TEXT type to support long antirequisite descriptions
    @Column(name = "antirequisites", columnDefinition = "TEXT")
    private String antirequisites;
    
    // SBC (Stony Brook Curriculum) codes (e.g., "ARTS", "GLO", "HUM")
    @Column(name = "sbc")
    private String sbc;
    
    // Class capacity (maximum number of students)
    @Column(name = "capacity")
    private Integer capacity;
    
    // Maximum capacity (fixed, cannot be changed)
    @Column(name = "max_capacity")
    private Integer maxCapacity;
    
    // Current capacity (number of students currently enrolled)
    @Column(name = "current_capacity")
    private Integer currentCapacity;
    
    // Constructors
    public ClassEntity() {
    }
    
    public ClassEntity(String department, Integer courseNum, String section, String semester, 
                  Integer year, Integer minCredits, Integer maxCredits, String title,
                  String instructor, String schedule, String location, String prerequisites) {
        this.department = department;
        this.courseNum = courseNum;
        this.section = section;
        this.semester = semester;
        this.year = year;
        this.minCredits = minCredits;
        this.maxCredits = maxCredits;
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
    
    public String getSectionType() {
        return sectionType;
    }
    
    public void setSectionType(String sectionType) {
        this.sectionType = sectionType;
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
    
    public Integer getMinCredits() {
        return minCredits;
    }
    
    public void setMinCredits(Integer minCredits) {
        this.minCredits = minCredits;
    }
    
    public Integer getMaxCredits() {
        return maxCredits;
    }
    
    public void setMaxCredits(Integer maxCredits) {
        this.maxCredits = maxCredits;
    }
    
    /**
     * Get credits as a string (e.g., "3" or "1-6")
     * @return Credits string
     */
    public String getCreditsDisplay() {
        if (maxCredits != null && !maxCredits.equals(minCredits)) {
            return minCredits + "-" + maxCredits;
        }
        return String.valueOf(minCredits);
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
    
    public String getLecSchedule() {
        return lecSchedule;
    }
    
    public void setLecSchedule(String lecSchedule) {
        this.lecSchedule = lecSchedule;
    }
    
    public String getLecLocation() {
        return lecLocation;
    }
    
    public void setLecLocation(String lecLocation) {
        this.lecLocation = lecLocation;
    }
    
    public String getLecInstructor() {
        return lecInstructor;
    }
    
    public void setLecInstructor(String lecInstructor) {
        this.lecInstructor = lecInstructor;
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
    
    public String getSbc() {
        return sbc;
    }
    
    public void setSbc(String sbc) {
        this.sbc = sbc;
    }
    
    public Integer getCapacity() {
        return capacity;
    }
    
    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }
    
    public Integer getMaxCapacity() {
        return maxCapacity;
    }
    
    public void setMaxCapacity(Integer maxCapacity) {
        this.maxCapacity = maxCapacity;
    }
    
    public Integer getCurrentCapacity() {
        return currentCapacity;
    }
    
    public void setCurrentCapacity(Integer currentCapacity) {
        this.currentCapacity = currentCapacity;
    }
    
    /**
     * Check if the class is full
     * @return true if current capacity >= max capacity
     */
    public boolean isFull() {
        if (maxCapacity == null || currentCapacity == null) {
            return false;
        }
        return currentCapacity >= maxCapacity;
    }
    
    /**
     * Get available spots in the class
     * @return number of available spots
     */
    public Integer getAvailableSpots() {
        if (maxCapacity == null || currentCapacity == null) {
            return null;
        }
        return Math.max(0, maxCapacity - currentCapacity);
    }
}

