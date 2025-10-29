package edu.sbu.cse416.sam_system.entity;

import jakarta.persistence.*;

/**
 * TP: Used cursor to implement entity representing a user's transfer courses and transfer credits.
 * Entity representing a user's transfer courses and transfer credits.
 * Normalized from User entity's JSON transfer_courses field.
 */
@Entity
@Table(name = "user_transfer_courses")
public class UserTransferCourses {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(name = "class_name")
    private String className;
    
    @Column(name = "university")
    private String university;
    
    @Column(name = "credits")
    private Integer credits;
    
    @Column(name = "department")
    private String department;
    
    @Column(name = "course_num")
    private Integer courseNum;
    
    @Column(name = "grade")
    private String grade;
    
    // Constructors
    public UserTransferCourses() {}
    
    public UserTransferCourses(User user, String className, String university, 
                              Integer credits, String department, Integer courseNum, String grade) {
        this.user = user;
        this.className = className;
        this.university = university;
        this.credits = credits;
        this.department = department;
        this.courseNum = courseNum;
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
    
    public String getClassName() {
        return className;
    }
    
    public void setClassName(String className) {
        this.className = className;
    }
    
    public String getUniversity() {
        return university;
    }
    
    public void setUniversity(String university) {
        this.university = university;
    }
    
    public Integer getCredits() {
        return credits;
    }
    
    public void setCredits(Integer credits) {
        this.credits = credits;
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
    
    public String getGrade() {
        return grade;
    }
    
    public void setGrade(String grade) {
        this.grade = grade;
    }
}
