package edu.sbu.cse416.sam_system.entity;

import jakarta.persistence.*;

/**
 * Entity representing a user's course plan (planned courses for future semesters).
 * Normalized from User entity's JSON course_plan field.
 */
@Entity
@Table(name = "user_course_plan")
public class UserCoursePlan {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(name = "department")
    private String department;
    
    @Column(name = "course_num")
    private Integer courseNum;
    
    @Column(name = "semester")
    private String semester;
    
    @Column(name = "year")
    private Integer year;
    
    @Column(name = "credits")
    private Integer credits;
    
    // Constructors
    public UserCoursePlan() {}
    
    public UserCoursePlan(User user, String department, Integer courseNum, 
                         String semester, Integer year, Integer credits) {
        this.user = user;
        this.department = department;
        this.courseNum = courseNum;
        this.semester = semester;
        this.year = year;
        this.credits = credits;
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
}
