package edu.sbu.cse416.sam_system.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import edu.sbu.cse416.sam_system.entity.User;
import edu.sbu.cse416.sam_system.entity.UserRole;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DTO for user API responses with properly structured data
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserResponseDto {
    
    // Basic user info
    private Long userId;
    private String sbuId;
    private String email;
    private String firstName;
    private String lastName;
    private UserRole role;
    private Boolean isActive;
    
    // Student-specific fields (parsed from JSON)
    private List<String> majors;
    private List<String> degrees;
    private List<String> minors;
    private List<String> plannedMajors;
    private List<String> plannedMinors;
    private Map<String, Object> universityEntry;
    private Map<String, Object> graduation;
    private List<Map<String, Object>> classes;
    private List<Map<String, Object>> transferCourses;
    private List<Map<String, Object>> coursePlan;
    private String aoi;
    private String college;
    private String directAdmit;
    
    // Advisor-specific fields
    private String advisorLevel;
    private String academicUnit;
    
    // Instructor-specific fields
    private String department;
    private String officeLocation;
    private String officeHours;
    
    // Constructors
    public UserResponseDto() {}
    
    public UserResponseDto(User user) {
        this.userId = user.getUserId();
        this.sbuId = user.getSbuId();
        this.email = user.getEmail();
        this.firstName = user.getFirstName();
        this.lastName = user.getLastName();
        this.role = user.getRole();
        this.isActive = user.getIsActive();
        
        // Parse JSON fields for students
        if (user.getRole() == UserRole.STUDENT) {
            this.majors = parseJsonArray(user.getMajors());
            this.degrees = parseJsonArray(user.getDegrees());
            this.minors = parseJsonArray(user.getMinors());
            this.plannedMajors = parseJsonArray(user.getPlannedMajors());
            this.plannedMinors = parseJsonArray(user.getPlannedMinors());
            this.universityEntry = parseJsonObject(user.getUniversityEntry());
            this.graduation = parseJsonObject(user.getGraduation());
            // Note: classes, transferCourses, and coursePlan are now stored in separate tables
            // These fields will be populated separately via API calls to:
            // - /api/users/{userId}/classes
            // - /api/users/{userId}/transfer-courses  
            // - /api/users/{userId}/course-plan
            this.classes = new ArrayList<>();
            this.transferCourses = new ArrayList<>();
            this.coursePlan = new ArrayList<>();
            this.aoi = user.getAoi();
            this.college = user.getCollege();
            this.directAdmit = user.getDirectAdmit();
        }
        
        // Parse fields for advisors
        if (user.getRole() == UserRole.ADVISOR) {
            this.advisorLevel = user.getLevel();
            this.academicUnit = user.getAcademicUnit();
        }
        
        // Parse fields for instructors
        if (user.getRole() == UserRole.INSTRUCTOR) {
            this.department = user.getDepartment();
            this.officeLocation = user.getOfficeLocation();
            this.officeHours = user.getOfficeHours();
        }
    }
    
    // Helper methods for JSON parsing
    @SuppressWarnings("unchecked")
    private List<String> parseJsonArray(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(jsonString, List.class);
        } catch (Exception e) {
            return null;
        }
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonObject(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(jsonString, Map.class);
        } catch (Exception e) {
            return null;
        }
    }
    
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseJsonArrayOfObjects(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(jsonString, List.class);
        } catch (Exception e) {
            return null;
        }
    }
    
    // Getters and Setters
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    
    public String getSbuId() { return sbuId; }
    public void setSbuId(String sbuId) { this.sbuId = sbuId; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    
    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }
    
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    
    public List<String> getMajors() { return majors; }
    public void setMajors(List<String> majors) { this.majors = majors; }
    
    public List<String> getDegrees() { return degrees; }
    public void setDegrees(List<String> degrees) { this.degrees = degrees; }
    
    public List<String> getMinors() { return minors; }
    public void setMinors(List<String> minors) { this.minors = minors; }
    
    public List<String> getPlannedMajors() { return plannedMajors; }
    public void setPlannedMajors(List<String> plannedMajors) { this.plannedMajors = plannedMajors; }
    
    public List<String> getPlannedMinors() { return plannedMinors; }
    public void setPlannedMinors(List<String> plannedMinors) { this.plannedMinors = plannedMinors; }
    
    public Map<String, Object> getUniversityEntry() { return universityEntry; }
    public void setUniversityEntry(Map<String, Object> universityEntry) { this.universityEntry = universityEntry; }
    
    public Map<String, Object> getGraduation() { return graduation; }
    public void setGraduation(Map<String, Object> graduation) { this.graduation = graduation; }
    
    public List<Map<String, Object>> getClasses() { return classes; }
    public void setClasses(List<Map<String, Object>> classes) { this.classes = classes; }
    
    public List<Map<String, Object>> getTransferCourses() { return transferCourses; }
    public void setTransferCourses(List<Map<String, Object>> transferCourses) { this.transferCourses = transferCourses; }
    
    public List<Map<String, Object>> getCoursePlan() { return coursePlan; }
    public void setCoursePlan(List<Map<String, Object>> coursePlan) { this.coursePlan = coursePlan; }
    
    public String getAoi() { return aoi; }
    public void setAoi(String aoi) { this.aoi = aoi; }
    
    public String getCollege() { return college; }
    public void setCollege(String college) { this.college = college; }
    
    public String getDirectAdmit() { return directAdmit; }
    public void setDirectAdmit(String directAdmit) { this.directAdmit = directAdmit; }
    
    public String getAdvisorLevel() { return advisorLevel; }
    public void setAdvisorLevel(String advisorLevel) { this.advisorLevel = advisorLevel; }
    
    public String getAcademicUnit() { return academicUnit; }
    public void setAcademicUnit(String academicUnit) { this.academicUnit = academicUnit; }
    
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    
    public String getOfficeLocation() { return officeLocation; }
    public void setOfficeLocation(String officeLocation) { this.officeLocation = officeLocation; }
    
    public String getOfficeHours() { return officeHours; }
    public void setOfficeHours(String officeHours) { this.officeHours = officeHours; }
}
