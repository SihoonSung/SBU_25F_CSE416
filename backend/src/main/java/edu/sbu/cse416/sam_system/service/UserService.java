package edu.sbu.cse416.sam_system.service;

import edu.sbu.cse416.sam_system.entity.User;
import edu.sbu.cse416.sam_system.entity.UserRole;
import edu.sbu.cse416.sam_system.repository.UserRepository;
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

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

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
        User nu = new User();
        nu.setGoogleId(googleId);
        nu.setEmail(email);
        nu.setFirstName(firstName);
        nu.setLastName(lastName);
        nu.setProfilePictureUrl(picture);
        nu.setRole(UserRole.STUDENT);
        nu.setIsActive(true);
        nu.updateLastLogin();
        return userRepository.save(nu);
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
        Yaml yaml = new Yaml();

        try (InputStream inputStream = file.getInputStream()) {
            Map<String, Object> data = yaml.load(inputStream);
            if (data == null) return importedUsers;

            if (data.containsKey("users")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> usersData = (List<Map<String, Object>>) data.get("users");
                for (Map<String, Object> userData : usersData) {
                    User user = createUserFromYamlData(userData);
                    if (user != null) {
                        User saved = processUserForImport(user);
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

    private void importUsersByRole(Map<String, Object> data, String roleKey, UserRole role, List<User> importedUsers) {
        if (data.containsKey(roleKey)) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> usersData = (List<Map<String, Object>>) data.get(roleKey);
            for (Map<String, Object> userData : usersData) {
                User user = createUserFromSBUYamlData(userData, role);
                if (user != null) {
                    User saved = processUserForImport(user);
                    if (saved != null) importedUsers.add(saved);
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
    private User processUserForImport(User user) {
        if (!isBlank(user.getEmail())) user.setEmail(safeLower(user.getEmail()));
        
        // Check for existing user by SBU ID (primary identifier)
        if (user.getSbuId() != null) {
            Optional<User> existingBySbuId = userRepository.findBySbuId(user.getSbuId());
            if (existingBySbuId.isPresent()) {
                // User exists - delete existing and replace with new data
                System.out.println("Warning: User with SBU ID " + user.getSbuId() + " already exists. Replacing existing data.");
                userRepository.delete(existingBySbuId.get());
            }
        }
        
        // Also check by email as fallback
        Optional<User> existingByEmail = userRepository.findByEmail(user.getEmail());
        if (existingByEmail.isPresent() && (user.getSbuId() == null || !existingByEmail.get().getSbuId().equals(user.getSbuId()))) {
            System.out.println("Warning: User with email " + user.getEmail() + " already exists. Skipping import.");
            return null;
        }
        
        // Validate SBU ID before saving
        if (user.getSbuId() == null || user.getSbuId().trim().isEmpty()) {
            System.err.println("ERROR: Cannot save user without SBU ID. User: " + user.getFirstName() + " " + user.getLastName());
            return null;
        }
        
        return userRepository.save(user);
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
        
        if (userData.containsKey("transfer_courses")) {
            user.setTransferCourses(convertToJson(userData.get("transfer_courses")));
        }
        
        if (userData.containsKey("classes")) {
            user.setClasses(convertToJson(userData.get("classes")));
        }
        
        if (userData.containsKey("course_plan")) {
            user.setCoursePlan(convertToJson(userData.get("course_plan")));
        }
        
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
            user.setAcademicUnit((String) userData.get("academic_unit"));
        }
    }
    
    private void processInstructorData(User user, Map<String, Object> userData) {
        if (userData.containsKey("academic_unit")) {
            user.setDepartment((String) userData.get("academic_unit"));
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
        if (user.getTransferCourses() != null) {
            studentData.put("transfer_courses", parseJson(user.getTransferCourses()));
        }
        if (user.getClasses() != null) {
            studentData.put("classes", parseJson(user.getClasses()));
        }
        if (user.getCoursePlan() != null) {
            studentData.put("course_plan", parseJson(user.getCoursePlan()));
        }
        
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