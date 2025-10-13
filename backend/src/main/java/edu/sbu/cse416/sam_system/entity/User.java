package edu.sbu.cse416.sam_system.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity class representing a user in the SAM system.
 * Supports four roles: STUDENT, INSTRUCTOR, ADVISOR, REGISTRAR
 * 
 * TP: Used Cursor to implement user management and authentication system
 */
@Entity
@Table(name = "users")
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;
    
    @Column(name = "email", nullable = false, unique = true)
    private String email;
    
    @Column(name = "first_name", nullable = false)
    private String firstName;
    
    @Column(name = "last_name")
    private String lastName;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private UserRole role;
    
    @Column(name = "google_id", unique = true)
    private String googleId;
    
    @Column(name = "profile_picture_url")
    private String profilePictureUrl;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "last_login")
    private LocalDateTime lastLogin;
    
    // Student-specific fields
    @Column(name = "student_id")
    private String studentId;
    
    @Column(name = "major")
    private String major;
    
    @Column(name = "gpa")
    private Double gpa;
    
    @Column(name = "graduation_year")
    private Integer graduationYear;
    
    // Instructor-specific fields
    @Column(name = "department")
    private String department;
    
    @Column(name = "office_location")
    private String officeLocation;
    
    @Column(name = "office_hours")
    private String officeHours;
    
    // Constructors
    public User() {
        this.createdAt = LocalDateTime.now();
    }
    
    public User(String email, String firstName, String lastName, UserRole role) {
        this();
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.role = role;
    }
    
    // Getters and Setters
    public Long getUserId() {
        return userId;
    }
    
    public void setUserId(Long userId) {
        this.userId = userId;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getFirstName() {
        return firstName;
    }
    
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }
    
    public String getLastName() {
        return lastName;
    }
    
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }
    
    public UserRole getRole() {
        return role;
    }
    
    public void setRole(UserRole role) {
        this.role = role;
    }
    
    public String getGoogleId() {
        return googleId;
    }
    
    public void setGoogleId(String googleId) {
        this.googleId = googleId;
    }
    
    public String getProfilePictureUrl() {
        return profilePictureUrl;
    }
    
    public void setProfilePictureUrl(String profilePictureUrl) {
        this.profilePictureUrl = profilePictureUrl;
    }
    
    public Boolean getIsActive() {
        return isActive;
    }
    
    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getLastLogin() {
        return lastLogin;
    }
    
    public void setLastLogin(LocalDateTime lastLogin) {
        this.lastLogin = lastLogin;
    }
    
    public String getStudentId() {
        return studentId;
    }
    
    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }
    
    public String getMajor() {
        return major;
    }
    
    public void setMajor(String major) {
        this.major = major;
    }
    
    public Double getGpa() {
        return gpa;
    }
    
    public void setGpa(Double gpa) {
        this.gpa = gpa;
    }
    
    public Integer getGraduationYear() {
        return graduationYear;
    }
    
    public void setGraduationYear(Integer graduationYear) {
        this.graduationYear = graduationYear;
    }
    
    public String getDepartment() {
        return department;
    }
    
    public void setDepartment(String department) {
        this.department = department;
    }
    
    public String getOfficeLocation() {
        return officeLocation;
    }
    
    public void setOfficeLocation(String officeLocation) {
        this.officeLocation = officeLocation;
    }
    
    public String getOfficeHours() {
        return officeHours;
    }
    
    public void setOfficeHours(String officeHours) {
        this.officeHours = officeHours;
    }
    
    // Helper methods
    public String getFullName() {
        if (lastName == null || lastName.trim().isEmpty()) {
            return firstName;
        }
        return firstName + " " + lastName;
    }
    
    public boolean isStudent() {
        return role == UserRole.STUDENT;
    }
    
    public boolean isInstructor() {
        return role == UserRole.INSTRUCTOR;
    }
    
    public boolean isAdvisor() {
        return role == UserRole.ADVISOR;
    }
    
    public boolean isRegistrar() {
        return role == UserRole.REGISTRAR;
    }
    
    public void updateLastLogin() {
        this.lastLogin = LocalDateTime.now();
    }
    
    @Override
    public String toString() {
        return "User{" +
                "userId=" + userId +
                ", email='" + email + '\'' +
                ", fullName='" + getFullName() + '\'' +
                ", role=" + role +
                ", isActive=" + isActive +
                '}';
    }
}
