package edu.sbu.cse416.sam_system.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Entity class representing a user in the SAM system.
 * Supports four roles: STUDENT, INSTRUCTOR, ADVISOR, REGISTRAR
 * * TP: Used Cursor to implement user management and authentication system
 */
@Entity
@Table(name = "users")
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;
    
    @Column(name = "sbu_id", unique = true, nullable = false)
    private String sbuId;
    
    @Column(name = "email", unique = true)
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
    
    // Extended student fields from YAML
    @Column(name = "university_entry")
    private String universityEntry; // JSON: {semester: Fall, year: 2020}
    
    @Column(name = "direct_admit")
    private String directAdmit; // major to which student was directly admitted
    
    @Column(name = "aoi")
    private String aoi; // Area of Interest major
    
    @Column(name = "college")
    private String college; // college acronym (e.g., CEAS)
    
    @Column(name = "majors", columnDefinition = "TEXT")
    private String majors; // JSON array: [CSE,AMS]
    
    @Column(name = "degrees", columnDefinition = "TEXT")
    private String degrees; // JSON array: [BS,BS]
    
    @Column(name = "major_requirement_versions", columnDefinition = "TEXT")
    private String majorRequirementVersions; // JSON array
    
    @Column(name = "minors", columnDefinition = "TEXT")
    private String minors; // JSON array
    
    @Column(name = "minor_requirement_versions", columnDefinition = "TEXT")
    private String minorRequirementVersions; // JSON array
    
    @Column(name = "planned_majors", columnDefinition = "TEXT")
    private String plannedMajors; // JSON array
    
    @Column(name = "planned_major_requirement_versions", columnDefinition = "TEXT")
    private String plannedMajorRequirementVersions; // JSON array
    
    @Column(name = "planned_degrees", columnDefinition = "TEXT")
    private String plannedDegrees; // JSON array
    
    @Column(name = "planned_minors", columnDefinition = "TEXT")
    private String plannedMinors; // JSON array
    
    @Column(name = "planned_minor_requirement_versions", columnDefinition = "TEXT")
    private String plannedMinorRequirementVersions; // JSON array
    
    @Column(name = "graduation", columnDefinition = "TEXT")
    private String graduation; // JSON: {semester: Fall, year: 2024}
    
    @Column(name = "transfer_courses", columnDefinition = "LONGTEXT")
    private String transferCourses; // JSON array of transfer course objects
    
    @Column(name = "classes", columnDefinition = "LONGTEXT")
    private String classes; // JSON array of class objects
    
    @Column(name = "course_plan", columnDefinition = "LONGTEXT")
    private String coursePlan; // JSON array of planned courses
    
    // Instructor-specific fields
    @Column(name = "department")
    private String department;
    
    @Column(name = "office_location")
    private String officeLocation;
    
    @Column(name = "office_hours")
    private String officeHours;
    
    // Advisor-specific fields
    @Column(name = "level")
    private String level; // university, college, department
    
    @Column(name = "academic_unit")
    private String academicUnit; // SBU, CSE, etc.
    
    // Student data stored as JSON
    @Column(name = "student_data", columnDefinition = "LONGTEXT")
    private String studentData; // JSON containing all student-specific fields
    
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
    
    public String getSbuId() {
        return sbuId;
    }
    
    public void setSbuId(String sbuId) {
        this.sbuId = sbuId;
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
    
    // Extended student field getters and setters
    public String getUniversityEntry() {
        return universityEntry;
    }
    
    public void setUniversityEntry(String universityEntry) {
        this.universityEntry = universityEntry;
    }
    
    public String getDirectAdmit() {
        return directAdmit;
    }
    
    public void setDirectAdmit(String directAdmit) {
        this.directAdmit = directAdmit;
    }
    
    public String getAoi() {
        return aoi;
    }
    
    public void setAoi(String aoi) {
        this.aoi = aoi;
    }
    
    public String getCollege() {
        return college;
    }
    
    public void setCollege(String college) {
        this.college = college;
    }
    
    public String getMajors() {
        return majors;
    }
    
    public void setMajors(String majors) {
        this.majors = majors;
    }
    
    public String getDegrees() {
        return degrees;
    }
    
    public void setDegrees(String degrees) {
        this.degrees = degrees;
    }
    
    public String getMinors() {
        return minors;
    }
    
    public void setMinors(String minors) {
        this.minors = minors;
    }
    
    public String getPlannedMajors() {
        return plannedMajors;
    }
    
    public void setPlannedMajors(String plannedMajors) {
        this.plannedMajors = plannedMajors;
    }
    
    public String getPlannedMinors() {
        return plannedMinors;
    }
    
    public void setPlannedMinors(String plannedMinors) {
        this.plannedMinors = plannedMinors;
    }
    
    public String getTransferCourses() {
        return transferCourses;
    }
    
    public void setTransferCourses(String transferCourses) {
        this.transferCourses = transferCourses;
    }
    
    public String getClasses() {
        return classes;
    }
    
    public void setClasses(String classes) {
        this.classes = classes;
    }
    
    public String getCoursePlan() {
        return coursePlan;
    }
    
    public void setCoursePlan(String coursePlan) {
        this.coursePlan = coursePlan;
    }
    
    // Advisor field getters and setters
    public String getLevel() {
        return level;
    }
    
    public void setLevel(String level) {
        this.level = level;
    }
    
    public String getAcademicUnit() {
        return academicUnit;
    }
    
    public void setAcademicUnit(String academicUnit) {
        this.academicUnit = academicUnit;
    }
    
    public String getStudentData() {
        return studentData;
    }
    
    public void setStudentData(String studentData) {
        this.studentData = studentData;
    }
    
    // Additional getters and setters for missing fields
    public String getMajorRequirementVersions() {
        return majorRequirementVersions;
    }
    
    public void setMajorRequirementVersions(String majorRequirementVersions) {
        this.majorRequirementVersions = majorRequirementVersions;
    }
    
    public String getMinorRequirementVersions() {
        return minorRequirementVersions;
    }
    
    public void setMinorRequirementVersions(String minorRequirementVersions) {
        this.minorRequirementVersions = minorRequirementVersions;
    }
    
    public String getPlannedMajorRequirementVersions() {
        return plannedMajorRequirementVersions;
    }
    
    public void setPlannedMajorRequirementVersions(String plannedMajorRequirementVersions) {
        this.plannedMajorRequirementVersions = plannedMajorRequirementVersions;
    }
    
    public String getPlannedDegrees() {
        return plannedDegrees;
    }
    
    public void setPlannedDegrees(String plannedDegrees) {
        this.plannedDegrees = plannedDegrees;
    }
    
    public String getPlannedMinorRequirementVersions() {
        return plannedMinorRequirementVersions;
    }
    
    public void setPlannedMinorRequirementVersions(String plannedMinorRequirementVersions) {
        this.plannedMinorRequirementVersions = plannedMinorRequirementVersions;
    }
    
    public String getGraduation() {
        return graduation;
    }
    
    public void setGraduation(String graduation) {
        this.graduation = graduation;
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