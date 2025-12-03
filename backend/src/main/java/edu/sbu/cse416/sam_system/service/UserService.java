package edu.sbu.cse416.sam_system.service;

import edu.sbu.cse416.sam_system.entity.User;
import edu.sbu.cse416.sam_system.entity.UserRole;
import edu.sbu.cse416.sam_system.entity.UserClasses;
import edu.sbu.cse416.sam_system.entity.UserTransferCourses;
import edu.sbu.cse416.sam_system.entity.UserCoursePlan;
import edu.sbu.cse416.sam_system.repository.UserRepository;
import edu.sbu.cse416.sam_system.repository.UserClassesRepository;
import edu.sbu.cse416.sam_system.repository.UserTransferCoursesRepository;
import edu.sbu.cse416.sam_system.repository.UserCoursePlanRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.Locale;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.Optional;
import java.util.HashMap;
import java.util.Map;

/**
 * TP: Used cursor to implement service for user management.
 */
@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private UserClassesRepository userClassesRepository;
    
    @Autowired
    private UserTransferCoursesRepository userTransferCoursesRepository;
    
    @Autowired
    private UserCoursePlanRepository userCoursePlanRepository;

    /** Find or create user after Google OAuth2 login */
    @Transactional
    public User findOrCreateUserFromOAuth2(OAuth2User principal) {
        // Try OIDC standard key first, fallback if not available
        String googleId = strAttr(principal, "sub");
        if (isBlank(googleId)) googleId = principal.getName(); // fallback

        String email     = safeLower(strAttr(principal, "email"));
        String firstName = nvl(strAttr(principal, "given_name"),  strAttr(principal, "firstName"));
        String lastName  = nvl(strAttr(principal, "family_name"), strAttr(principal, "lastName"));
        String fullName  = strAttr(principal, "name");
        String picture   = strAttr(principal, "picture");

        // Name correction
        if (isBlank(firstName) && !isBlank(fullName)) {
            String[] parts = fullName.trim().split("\\s+", 2);
            firstName = parts[0];
            if (isBlank(lastName) && parts.length > 1) lastName = parts[1];
        }
        if (isBlank(firstName)) firstName = "Unknown";
        if (isBlank(lastName))  lastName  = "User";

        // 1) Search by googleId first
        if (!isBlank(googleId)) {
            Optional<User> byGoogle = userRepository.findByGoogleId(googleId);
            if (byGoogle.isPresent()) {
                User u = byGoogle.get();
                if (!isBlank(picture)) u.setProfilePictureUrl(picture);
                if (!isBlank(email) && isBlank(u.getEmail())) u.setEmail(email);
                u.updateLastLogin();
                return userRepository.save(u);
            }
        }

        // 2) Email matching -> link googleId to existing account
        if (!isBlank(email)) {
            Optional<User> byEmail = userRepository.findByEmail(email);
            if (byEmail.isPresent()) {
                User u = byEmail.get();
                if (!isBlank(googleId)) u.setGoogleId(googleId);
                if (!isBlank(picture))  u.setProfilePictureUrl(picture);
                if (isBlank(u.getFirstName())) u.setFirstName(firstName);
                if (isBlank(u.getLastName()))  u.setLastName(lastName);
                u.updateLastLogin();
                return userRepository.save(u);
            }
        }

        // 3) Create new user (default STUDENT, active)
        // Note: This should rarely happen as users should be imported first
        // For now, we'll throw an exception to prevent unauthorized account creation
        throw new IllegalStateException("User not found in system. Please contact administrator to import user data first.");
    }

    // ===== Query methods =====
    public Optional<User> getUserById(Long userId) { return userRepository.findById(userId); }

    public Optional<User> getUserByEmail(String email) { return userRepository.findByEmail(safeLower(email)); }

    public Optional<User> getUserByEmailIgnoreCase(String email) {
        if (email == null) return Optional.empty();
        return userRepository.findByEmailIgnoreCase(email);
    }

    public Optional<User> getUserByGoogleId(String googleId) { return userRepository.findByGoogleId(googleId); }

    public Optional<User> getUserByStudentId(String studentId) {
        if (studentId == null) return Optional.empty();
        return userRepository.findByStudentId(studentId);
    }
    
    public Optional<User> getUserBySbuId(String sbuId) {
        if (sbuId == null) return Optional.empty();
        return userRepository.findBySbuId(sbuId);
    }

    public List<User> getAllUsers() { return userRepository.findAll(); }

    public List<User> getUsersByRole(UserRole role) { return userRepository.findByRole(role); }

    public List<User> searchUsersByName(String searchTerm) { return userRepository.findByNameContaining(searchTerm); }

    public List<User> searchUsersByNameAndRole(String searchTerm, UserRole role) {
        return userRepository.findByNameContainingAndRole(searchTerm, role);
    }

    public List<User> searchUsersByEmailDomain(String domain) { return userRepository.findByEmailDomain(domain); }

    public List<User> searchStudentsByMajor(String major) { return userRepository.findByMajorContaining(major); }

    public List<User> searchInstructorsByDepartment(String department) {
        return userRepository.findByDepartmentContaining(department);
    }
    
    // Advanced search methods for students
    public List<User> searchStudentsByMinor(String minor) {
        return userRepository.findByMinorContaining(minor);
    }
    
    public List<User> searchStudentsByGraduationYear(Integer year) {
        return userRepository.findByGraduationYear(year);
    }
    
    public List<User> searchStudentsByGraduationTerm(String semester, Integer year) {
        return userRepository.findByGraduationTerm(semester, year);
    }
    
    // Academic advisor search methods
    public List<User> searchAdvisorsByLevel(String level) {
        return userRepository.findByAdvisorLevel(level);
    }
    
    public List<User> searchAdvisorsByAcademicUnit(String academicUnit) {
        return userRepository.findByAcademicUnit(academicUnit);
    }
    
    public List<User> searchAdvisorsByLevelAndAcademicUnit(String level, String academicUnit) {
        return userRepository.findByAdvisorLevelAndAcademicUnit(level, academicUnit);
    }
    
    // Comprehensive search method
    public List<User> searchUsers(String name, String role, String major, String minor, 
                                  String graduationTerm, String advisorLevel, String academicUnit) {
        List<User> results = new ArrayList<>();
        
        // Start with all users if no filters
        if (name == null && role == null && major == null && minor == null && 
            graduationTerm == null && advisorLevel == null && academicUnit == null) {
            return getAllUsers();
        }
        
        // Start with all users
        results = getAllUsers();
        
        // Filter by role first
        if (role != null && !role.trim().isEmpty()) {
            try {
                UserRole userRole = UserRole.valueOf(role.toUpperCase());
                results = results.stream()
                        .filter(user -> user.getRole() == userRole)
                        .collect(Collectors.toList());
            } catch (IllegalArgumentException e) {
                // Invalid role, return empty list
                return new ArrayList<>();
            }
        }
        
        // Filter by name (prefix matching)
        if (name != null && !name.trim().isEmpty()) {
            String searchName = name.trim().toLowerCase();
            results = results.stream()
                    .filter(user -> {
                        String fullName = user.getFullName().toLowerCase();
                        return fullName.startsWith(searchName) || 
                               user.getFirstName().toLowerCase().startsWith(searchName) ||
                               user.getLastName().toLowerCase().startsWith(searchName);
                    })
                    .collect(Collectors.toList());
        }
        
        // Filter by major (for students)
        if (major != null && !major.trim().isEmpty()) {
            String searchMajor = major.trim().toLowerCase();
            results = results.stream()
                    .filter(user -> user.getRole() == UserRole.STUDENT && 
                            user.getMajor() != null && 
                            user.getMajor().toLowerCase().contains(searchMajor))
                    .collect(Collectors.toList());
        }
        
        // Filter by minor (for students)
        if (minor != null && !minor.trim().isEmpty()) {
            List<User> minorResults = searchStudentsByMinor(minor);
            results = results.stream()
                    .filter(minorResults::contains)
                    .collect(Collectors.toList());
        }
        
        // Filter by graduation term
        if (graduationTerm != null && !graduationTerm.trim().isEmpty()) {
            // Parse graduation term (e.g., "Fall 2025")
            String[] parts = graduationTerm.trim().split(" ");
            if (parts.length == 2) {
                String semester = parts[0];
                try {
                    Integer year = Integer.parseInt(parts[1]);
                    List<User> termResults = searchStudentsByGraduationTerm(semester, year);
                    results = results.stream()
                            .filter(termResults::contains)
                            .collect(Collectors.toList());
                } catch (NumberFormatException e) {
                    // Invalid year format, skip this filter
                }
            }
        }
        
        // Filter by advisor level (for advisors only)
        if (advisorLevel != null && !advisorLevel.trim().isEmpty()) {
            String searchLevel = advisorLevel.trim();
            results = results.stream()
                    .filter(user -> user.getRole() == UserRole.ADVISOR && 
                            user.getLevel() != null && 
                            user.getLevel().equals(searchLevel))
                    .collect(Collectors.toList());
        }
        
        // Filter by academic unit (for advisors only)
        if (academicUnit != null && !academicUnit.trim().isEmpty()) {
            String searchUnit = academicUnit.trim();
            results = results.stream()
                    .filter(user -> user.getRole() == UserRole.ADVISOR && 
                            user.getAcademicUnit() != null && 
                            user.getAcademicUnit().equals(searchUnit))
                    .collect(Collectors.toList());
        }
        
        return results;
    }

    // ===== Modification methods =====
    @Transactional
    public User updateUser(User user) {
        if (!isBlank(user.getEmail())) user.setEmail(safeLower(user.getEmail()));
        return userRepository.save(user);
    }

    @Transactional
    public User deactivateUser(Long userId) {
        return userRepository.findById(userId)
                .map(u -> { u.setIsActive(false); return userRepository.save(u); })
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));
    }

    @Transactional
    public User activateUser(Long userId) {
        return userRepository.findById(userId)
                .map(u -> { u.setIsActive(true); return userRepository.save(u); })
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));
    }

    // ===== YAML Import (users array + SBU format support) =====
    public List<User> importUsersFromYaml(MultipartFile file) throws IOException {
        List<User> importedUsers = new ArrayList<>();
        
        // Configure YAML parser to be more tolerant of formatting issues
        Yaml yaml = new Yaml();
        
        try (InputStream inputStream = file.getInputStream()) {
            // Pre-process the YAML content to normalize whitespace
            String content = new String(inputStream.readAllBytes());
            content = normalizeYamlContent(content);
            
            Map<String, Object> data = yaml.load(content);
            if (data == null) return importedUsers;

            if (data.containsKey("users")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> usersData = (List<Map<String, Object>>) data.get("users");
                for (Map<String, Object> userData : usersData) {
                    User user = createUserFromYamlData(userData);
                    if (user != null) {
                        User saved = processUserForImport(user, userData);
                        if (saved != null) importedUsers.add(saved);
                    }
                }
            } else {
                importUsersByRole(data, "registrars",        UserRole.REGISTRAR,  importedUsers);
                importUsersByRole(data, "academic_advisors", UserRole.ADVISOR,    importedUsers);
                importUsersByRole(data, "instructors",       UserRole.INSTRUCTOR, importedUsers);
                importUsersByRole(data, "students",          UserRole.STUDENT,    importedUsers);
            }
        }
        return importedUsers;
    }
    
    /**
     * Normalize YAML content to handle multiple blank lines and formatting issues
     */
    private String normalizeYamlContent(String content) {
        // Replace multiple consecutive newlines (2 or more) with double newline
        content = content.replaceAll("\\n\\s*\\n\\s*\\n+", "\n\n");
        
        // Handle cases where there are multiple blank lines between list items
        content = content.replaceAll("(\\n\\s*-\\s+[^\\n]+\\n)(\\s*\\n)+(\\s*-\\s+)", "$1\n$3");
        
        // Ensure proper indentation for list items (fix any misaligned dashes)
        content = content.replaceAll("\\n(\\s*)-\\s", "\n$1- ");
        
        // Remove trailing whitespace from lines
        content = content.replaceAll("\\s+\\n", "\n");
        
        // Fix any remaining multiple blank lines between sections
        content = content.replaceAll("(\\n\\s*\\n\\s*\\n)+", "\n\n");
        
        return content;
    }

    private void importUsersByRole(Map<String, Object> data, String roleKey, UserRole role, List<User> importedUsers) {
        if (data.containsKey(roleKey)) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> usersData = (List<Map<String, Object>>) data.get(roleKey);
            for (Map<String, Object> userData : usersData) {
                User user = createUserFromSBUYamlData(userData, role);
                if (user != null) {
                    User saved = processUserForImport(user, userData);
                    if (saved != null) {
                        importedUsers.add(saved);
                    }
                }
            }
        }
    }
    
    private User createUserFromYamlData(Map<String, Object> userData) {
        try {
            User user = new User();
            user.setEmail(safeLower((String) userData.get("email")));
            user.setFirstName((String) userData.get("firstName"));
            user.setLastName((String) userData.get("lastName"));

            String roleStr = (String) userData.get("role");
            user.setRole(roleStr != null ? UserRole.valueOf(roleStr.toUpperCase()) : UserRole.STUDENT);

            if (userData.containsKey("studentId"))   user.setStudentId((String) userData.get("studentId"));
            if (userData.containsKey("major"))       user.setMajor((String) userData.get("major"));
            if (userData.containsKey("gpa")) {
                Object gpaObj = userData.get("gpa");
                if (gpaObj instanceof Number) user.setGpa(((Number) gpaObj).doubleValue());
            }
            if (userData.containsKey("graduationYear")) {
                Object yearObj = userData.get("graduationYear");
                if (yearObj instanceof Number) user.setGraduationYear(((Number) yearObj).intValue());
            }
            if (userData.containsKey("department"))     user.setDepartment((String) userData.get("department"));
            if (userData.containsKey("officeLocation")) user.setOfficeLocation((String) userData.get("officeLocation"));
            if (userData.containsKey("officeHours"))    user.setOfficeHours((String) userData.get("officeHours"));

            user.setIsActive(true);
            return user;
        } catch (Exception e) {
            System.err.println("Error creating user from YAML data: " + e.getMessage());
            return null;
        }
    }

    /** Normalize email + check for duplicates before saving */
    private User processUserForImport(User user, Map<String, Object> userData) {
        if (!isBlank(user.getEmail())) user.setEmail(safeLower(user.getEmail()));
        
        // Check for existing user by SBU ID (primary identifier)
        if (user.getSbuId() != null) {
            Optional<User> existingBySbuId = userRepository.findBySbuId(user.getSbuId());
            if (existingBySbuId.isPresent()) {
                // User exists - delete existing and replace with new data
                User existingUser = existingBySbuId.get();
                Long existingUserId = existingUser.getUserId();
                
                // Delete related data first (to avoid foreign key constraint issues and orphan records)
                userCoursePlanRepository.deleteByUserId(existingUserId);
                userClassesRepository.deleteByUserId(existingUserId);
                userTransferCoursesRepository.deleteByUserId(existingUserId);
                
                // Now delete the user
                userRepository.delete(existingUser);
            }
        }
        
        // Also check by email as fallback (only if SBU ID is different)
        if (user.getSbuId() != null) {
            Optional<User> existingByEmail = userRepository.findByEmail(user.getEmail());
            if (existingByEmail.isPresent() && !existingByEmail.get().getSbuId().equals(user.getSbuId())) {
                return null;
            }
        }
        
        // Validate SBU ID before saving
        if (user.getSbuId() == null || user.getSbuId().trim().isEmpty()) {
            System.err.println("ERROR: Cannot save user without SBU ID. User: " + user.getFirstName() + " " + user.getLastName());
            return null;
        }
        
        User savedUser = userRepository.save(user);
        
        // Process related data after user is saved
        processUserRelatedData(savedUser, userData);
        
        return savedUser;
    }

    /** Process related data (classes, transfer_courses, course_plan) into separate tables */
    private void processUserRelatedData(User user, Map<String, Object> userData) {
        try {
            // Process classes
            if (userData.containsKey("classes")) {
                Object classesObj = userData.get("classes");
                if (classesObj != null && classesObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> classes = (List<Map<String, Object>>) classesObj;
                    
                    for (Map<String, Object> classData : classes) {
                        // Skip null entries
                        if (classData == null) {
                            continue;
                        }
                        
                        UserClasses userClass = new UserClasses();
                        userClass.setUser(user);
                        userClass.setClassId(getLongValue(classData.get("class_id")));
                        userClass.setDepartment(getStringValue(classData.get("department")));
                        userClass.setCourseNum(getIntegerValue(classData.get("course_num")));
                        userClass.setSection(getStringValue(classData.get("section")));
                        userClass.setSemester(getStringValue(classData.get("semester")));
                        userClass.setYear(getIntegerValue(classData.get("year")));
                        userClass.setCredits(getIntegerValue(classData.get("credits")));
                        userClass.setGpnc(getStringValue(classData.get("GPNC")));
                        userClass.setGrade(getStringValue(classData.get("grade")));
                        
                        userClassesRepository.save(userClass);
                    }
                }
            }
            
            // Process transfer_courses
            if (userData.containsKey("transfer_courses")) {
                // Delete existing transfer courses first to avoid duplicates
                userTransferCoursesRepository.deleteByUserId(user.getUserId());
                
                Object transferCoursesObj = userData.get("transfer_courses");
                if (transferCoursesObj != null && transferCoursesObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> transferCourses = (List<Map<String, Object>>) transferCoursesObj;
                    
                    for (Map<String, Object> transferData : transferCourses) {
                        // Skip null entries
                        if (transferData == null) {
                            continue;
                        }
                        
                        UserTransferCourses userTransfer = new UserTransferCourses();
                        userTransfer.setUser(user);
                        userTransfer.setClassName(getStringValue(transferData.get("class")));
                        userTransfer.setUniversity(getStringValue(transferData.get("university")));
                        userTransfer.setCredits(getIntegerValue(transferData.get("credits")));
                        userTransfer.setDepartment(getStringValue(transferData.get("department")));
                        userTransfer.setCourseNum(getIntegerValue(transferData.get("course_num")));
                        userTransfer.setGrade(getStringValue(transferData.get("grade")));
                        
                        userTransferCoursesRepository.save(userTransfer);
                    }
                }
            }
            
            // Process course_plan
            if (userData.containsKey("course_plan")) {
                Object coursePlanObj = userData.get("course_plan");
                
                if (coursePlanObj != null && coursePlanObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> coursePlan = (List<Map<String, Object>>) coursePlanObj;
                    
                    if (!coursePlan.isEmpty()) {
                        for (Map<String, Object> planData : coursePlan) {
                            try {
                                // Skip null entries
                                if (planData == null) {
                                    continue;
                                }
                                
                                UserCoursePlan userPlan = new UserCoursePlan();
                                userPlan.setUser(user);
                                userPlan.setDepartment(getStringValue(planData.get("department")));
                                userPlan.setCourseNum(getIntegerValue(planData.get("course_num")));
                                userPlan.setSemester(getStringValue(planData.get("semester")));
                                userPlan.setYear(getIntegerValue(planData.get("year")));
                                userPlan.setCredits(getIntegerValue(planData.get("credits")));
                                
                                UserCoursePlan saved = userCoursePlanRepository.save(userPlan);
                                
                                // Verify the saved record
                                if (saved.getUser().getUserId() != user.getUserId()) {
                                    System.err.println("ERROR: Saved UserCoursePlan has wrong userId! Expected: " + user.getUserId() + ", Got: " + saved.getUser().getUserId());
                                }
                            } catch (Exception e) {
                                System.err.println("ERROR: Failed to save course plan item: " + e.getMessage());
                                e.printStackTrace();
                            }
                        }
                    }
                } else if (coursePlanObj != null) {
                    System.err.println("ERROR: course_plan is not a List! Type: " + coursePlanObj.getClass().getName());
                }
            }
        } catch (Exception e) {
            System.err.println("ERROR: Error processing related data for user " + user.getSbuId() + " (userId: " + user.getUserId() + "): " + e.getMessage());
            e.printStackTrace();
            // Re-throw exception to ensure transaction rollback if needed
            throw new RuntimeException("Failed to process related data for user " + user.getSbuId() + ": " + e.getMessage(), e);
        }
    }
    
    /** Helper method to safely convert Object to Long */
    private Long getLongValue(Object value) {
        if (value == null) return null;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Integer) return ((Integer) value).longValue();
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
    
    /** Helper method to safely convert Object to Integer */
    private Integer getIntegerValue(Object value) {
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Long) return ((Long) value).intValue();
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
    
    /** Helper method to safely convert Object to String */
    private String getStringValue(Object value) {
        if (value == null) return null;
        String s = value.toString();
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty()) return null;
        if ("null".equalsIgnoreCase(s)) return null;
        return s;
    }
    
    private User createUserFromSBUYamlData(Map<String, Object> userData, UserRole role) {
        try {
            User user = new User();
            user.setEmail(safeLower((String) userData.get("email")));
            user.setFirstName((String) userData.get("first_name"));
            user.setLastName((String) userData.get("last_name"));
            user.setRole(role);

            // Set SBU ID as primary identifier
            if (userData.containsKey("SBU_ID")) {
                String sbuId = String.valueOf(userData.get("SBU_ID"));
                user.setSbuId(sbuId);
                if (role == UserRole.STUDENT) user.setStudentId(sbuId);
            } else {
                System.err.println("ERROR: SBU_ID not found in user data for: " + user.getFirstName() + " " + user.getLastName());
                System.err.println("Available keys: " + userData.keySet());
            }

            // Role-specific data handling
            if (role == UserRole.STUDENT) {
                processStudentData(user, userData);
            } else if (role == UserRole.ADVISOR) {
                processAdvisorData(user, userData);
            } else if (role == UserRole.INSTRUCTOR) {
                processInstructorData(user, userData);
            } else if (role == UserRole.REGISTRAR) {
                // Registrars have minimal additional data
            }

            user.setIsActive(true);
            return user;
        } catch (Exception e) {
            System.err.println("Error creating user from SBU YAML data: " + e.getMessage());
            return null;
        }
    }
    
    private void processStudentData(User user, Map<String, Object> userData) {
        // Basic student fields
        if (userData.containsKey("majors")) {
            List<?> majors = (List<?>) userData.get("majors");
            if (majors != null && !majors.isEmpty()) {
                user.setMajor(String.valueOf(majors.get(0)));
                // Store full majors array as JSON
                user.setMajors(convertToJson(majors));
            }
        }
        
        if (userData.containsKey("degrees")) {
            user.setDegrees(convertToJson(userData.get("degrees")));
        }
        
        if (userData.containsKey("minors")) {
            user.setMinors(convertToJson(userData.get("minors")));
        }
        
        if (userData.containsKey("planned_majors")) {
            user.setPlannedMajors(convertToJson(userData.get("planned_majors")));
        }
        
        if (userData.containsKey("planned_minors")) {
            user.setPlannedMinors(convertToJson(userData.get("planned_minors")));
        }
        
        // Add missing requirement versions and planned degrees
        if (userData.containsKey("major_requirement_versions")) {
            user.setMajorRequirementVersions(convertToJson(userData.get("major_requirement_versions")));
        }
        
        if (userData.containsKey("minor_requirement_versions")) {
            user.setMinorRequirementVersions(convertToJson(userData.get("minor_requirement_versions")));
        }
        
        if (userData.containsKey("planned_major_requirement_versions")) {
            user.setPlannedMajorRequirementVersions(convertToJson(userData.get("planned_major_requirement_versions")));
        }
        
        if (userData.containsKey("planned_degrees")) {
            user.setPlannedDegrees(convertToJson(userData.get("planned_degrees")));
        }
        
        if (userData.containsKey("planned_minor_requirement_versions")) {
            user.setPlannedMinorRequirementVersions(convertToJson(userData.get("planned_minor_requirement_versions")));
        }
        
        if (userData.containsKey("university_entry")) {
            user.setUniversityEntry(convertToJson(userData.get("university_entry")));
        }
        
        if (userData.containsKey("graduation")) {
            user.setGraduation(convertToJson(userData.get("graduation")));
        }
        
        // Note: transfer_courses, classes, and course_plan are now processed 
        // in processUserRelatedData() method after user is saved
        
        if (userData.containsKey("AOI")) {
            user.setAoi((String) userData.get("AOI"));
        }
        
        if (userData.containsKey("college")) {
            user.setCollege((String) userData.get("college"));
        }
        
        if (userData.containsKey("direct_admit")) {
            user.setDirectAdmit((String) userData.get("direct_admit"));
        }
        
        // Store all student data as JSON for easy access
        user.setStudentData(convertToJson(userData));
    }
    
    private void processAdvisorData(User user, Map<String, Object> userData) {
        if (userData.containsKey("level")) {
            user.setLevel((String) userData.get("level"));
        }
        
        if (userData.containsKey("academic_unit")) {
            Object academicUnitObj = userData.get("academic_unit");
            if (academicUnitObj instanceof List) {
                // Handle array format like [CSE,ISE]
                @SuppressWarnings("unchecked")
                List<String> academicUnits = (List<String>) academicUnitObj;
                user.setAcademicUnit(String.join(",", academicUnits));
            } else if (academicUnitObj instanceof String) {
                // Handle string format
                user.setAcademicUnit((String) academicUnitObj);
            }
        }
    }
    
    private void processInstructorData(User user, Map<String, Object> userData) {
        if (userData.containsKey("academic_unit")) {
            Object academicUnitObj = userData.get("academic_unit");
            if (academicUnitObj instanceof List) {
                // Handle array format like [CSE,ISE]
                @SuppressWarnings("unchecked")
                List<String> academicUnits = (List<String>) academicUnitObj;
                user.setDepartment(String.join(",", academicUnits));
            } else if (academicUnitObj instanceof String) {
                // Handle string format
                user.setDepartment((String) academicUnitObj);
            }
        }
    }
    
    private String convertToJson(Object obj) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            System.err.println("Error converting to JSON: " + e.getMessage());
            return null;
        }
    }

    public Map<String, Object> getUserStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", userRepository.count());
        stats.put("activeUsers", userRepository.countByIsActiveTrue());
        stats.put("students", userRepository.countByRole(UserRole.STUDENT));
        stats.put("instructors", userRepository.countByRole(UserRole.INSTRUCTOR));
        stats.put("advisors", userRepository.countByRole(UserRole.ADVISOR));
        stats.put("registrars", userRepository.countByRole(UserRole.REGISTRAR));
        return stats;
    }

    // ===== Export functionality =====
    public Map<String, Object> exportUserToYamlFormat(Long userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return null;
        }
        
        User user = userOpt.get();
        Map<String, Object> yamlData = new HashMap<>();
        
        // Convert user to YAML format based on role
        if (user.getRole() == UserRole.STUDENT) {
            yamlData = convertStudentToYamlFormat(user);
        } else if (user.getRole() == UserRole.ADVISOR) {
            yamlData = convertAdvisorToYamlFormat(user);
        } else if (user.getRole() == UserRole.INSTRUCTOR) {
            yamlData = convertInstructorToYamlFormat(user);
        } else if (user.getRole() == UserRole.REGISTRAR) {
            yamlData = convertRegistrarToYamlFormat(user);
        }
        
        return yamlData;
    }
    
    private Map<String, Object> convertStudentToYamlFormat(User user) {
        Map<String, Object> studentData = new HashMap<>();
        studentData.put("SBU_ID", user.getSbuId());
        studentData.put("first_name", user.getFirstName());
        studentData.put("last_name", user.getLastName());
        studentData.put("email", user.getEmail());
        
        // Add all student-specific fields from JSON columns
        if (user.getUniversityEntry() != null) {
            studentData.put("university_entry", parseJson(user.getUniversityEntry()));
        }
        if (user.getDirectAdmit() != null) {
            studentData.put("direct_admit", user.getDirectAdmit());
        }
        if (user.getAoi() != null) {
            studentData.put("AOI", user.getAoi());
        }
        if (user.getCollege() != null) {
            studentData.put("college", user.getCollege());
        }
        if (user.getMajors() != null) {
            studentData.put("majors", parseJson(user.getMajors()));
        }
        if (user.getDegrees() != null) {
            studentData.put("degrees", parseJson(user.getDegrees()));
        }
        if (user.getMajorRequirementVersions() != null) {
            studentData.put("major_requirement_versions", parseJson(user.getMajorRequirementVersions()));
        }
        if (user.getMinors() != null) {
            studentData.put("minors", parseJson(user.getMinors()));
        }
        if (user.getMinorRequirementVersions() != null) {
            studentData.put("minor_requirement_versions", parseJson(user.getMinorRequirementVersions()));
        }
        if (user.getPlannedMajors() != null) {
            studentData.put("planned_majors", parseJson(user.getPlannedMajors()));
        }
        if (user.getPlannedMajorRequirementVersions() != null) {
            studentData.put("planned_major_requirement_versions", parseJson(user.getPlannedMajorRequirementVersions()));
        }
        if (user.getPlannedDegrees() != null) {
            studentData.put("planned_degrees", parseJson(user.getPlannedDegrees()));
        }
        if (user.getPlannedMinors() != null) {
            studentData.put("planned_minors", parseJson(user.getPlannedMinors()));
        }
        if (user.getPlannedMinorRequirementVersions() != null) {
            studentData.put("planned_minor_requirement_versions", parseJson(user.getPlannedMinorRequirementVersions()));
        }
        if (user.getGraduation() != null) {
            studentData.put("graduation", parseJson(user.getGraduation()));
        }
        // Note: transfer_courses, classes, and course_plan are now stored in separate tables
        // Use the new API endpoints to retrieve this data:
        // - /api/users/{userId}/transfer-courses
        // - /api/users/{userId}/classes  
        // - /api/users/{userId}/course-plan
        
        return studentData;
    }
    
    private Map<String, Object> convertAdvisorToYamlFormat(User user) {
        Map<String, Object> advisorData = new HashMap<>();
        advisorData.put("SBU_ID", user.getSbuId());
        advisorData.put("first_name", user.getFirstName());
        advisorData.put("last_name", user.getLastName());
        advisorData.put("email", user.getEmail());
        advisorData.put("level", user.getLevel());
        advisorData.put("academic_unit", user.getAcademicUnit());
        return advisorData;
    }
    
    private Map<String, Object> convertInstructorToYamlFormat(User user) {
        Map<String, Object> instructorData = new HashMap<>();
        instructorData.put("SBU_ID", user.getSbuId());
        instructorData.put("first_name", user.getFirstName());
        instructorData.put("last_name", user.getLastName());
        instructorData.put("email", user.getEmail());
        return instructorData;
    }
    
    private Map<String, Object> convertRegistrarToYamlFormat(User user) {
        Map<String, Object> registrarData = new HashMap<>();
        registrarData.put("SBU_ID", user.getSbuId());
        registrarData.put("first_name", user.getFirstName());
        registrarData.put("last_name", user.getLastName());
        registrarData.put("email", user.getEmail());
        return registrarData;
    }
    
    private Object parseJson(String jsonString) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(jsonString, Object.class);
        } catch (Exception e) {
            System.err.println("Error parsing JSON: " + e.getMessage());
            return jsonString; // Return as string if parsing fails
        }
    }
    

    // helpers 
    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
    private static String nvl(String a, String b) { return !isBlank(a) ? a : b; }
    private static String safeLower(String s) { return s == null ? null : s.toLowerCase(Locale.ROOT).trim(); }
    private static String strAttr(OAuth2User p, String key) {
        Object v = p.getAttribute(key);
        return v == null ? null : String.valueOf(v);
    }
}