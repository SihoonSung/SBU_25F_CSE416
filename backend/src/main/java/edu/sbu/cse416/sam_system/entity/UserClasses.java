package edu.sbu.cse416.sam_system.entity;

import jakarta.persistence.*;

/**
 * Entity representing a user's classes (taken, taking, or registered for).
 * Normalized from User entity's JSON classes field.
 */
@Entity
@Table(name = "user_classes")
public class UserClasses {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(name = "class_id")
    private Long classId;
    
    @Column(name = "department")
    private String department;
    
    @Column(name = "course_num")
    private Integer courseNum;
    
    @Column(name = "section")
    private String section;
    
    @Column(name = "semester")
    private String semester;
    
    @Column(name = "year")
    private Integer year;
    
    @Column(name = "credits")
    private Integer credits;
    
    @Column(name = "gpnc")
    private String gpnc; // String as requested
    
    @Column(name = "grade")
    private String grade;
    
    // Constructors
    public UserClasses() {}
    
    public UserClasses(User user, Long classId, String department, Integer courseNum, 
                      String section, String semester, Integer year, Integer credits, 
                      String gpnc, String grade) {
        this.user = user;
        this.classId = classId;
        this.department = department;
        this.courseNum = courseNum;
        this.section = section;
        this.semester = semester;
        this.year = year;
        this.credits = credits;
        this.gpnc = gpnc;
        this.grade = grade;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public User getUser() {
        return user;
    }
    
    public void setUser(User user) {
        this.user = user;
    }
    
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
    
    public String getGpnc() {
        return gpnc;
    }
    
    public void setGpnc(String gpnc) {
        this.gpnc = gpnc;
    }
    
    public String getGrade() {
        return grade;
    }
    
    public void setGrade(String grade) {
        this.grade = grade;
    }
}
