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

/**
 * Service for user management operations.
 * Handles Google OAuth2 authentication, user creation, and YAML import functionality.
 * 
 * Note: Google OAuth2 integration and user management features were implemented 
 * with assistance from Cursor AI and ChatGPT for proper authentication flow 
 * and data handling.
 */
@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    /** Find or create user from Google OAuth2 login */
    @Transactional
    public User findOrCreateUserFromOAuth2(OAuth2User principal) {
        // Try OIDC standard key first, fallback to alternative
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

        // (Optional) Uncomment to restrict to SBU email addresses only
        // if (email == null || !email.endsWith("@stonybrook.edu")) {
        //     throw new RuntimeException("Only SBU accounts are allowed");
        // }

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

    // ===== Query Methods =====
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

    // ===== Update Methods =====
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

    // ===== YAML Import (supports users array + SBU format) =====
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

    /** Normalize email and check for duplicates before saving */
    private User processUserForImport(User user) {
        if (!isBlank(user.getEmail())) user.setEmail(safeLower(user.getEmail()));
        Optional<User> existing = userRepository.findByEmail(user.getEmail());
        if (existing.isEmpty()) return userRepository.save(user);
        return null;
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

    private User createUserFromSBUYamlData(Map<String, Object> userData, UserRole role) {
        try {
            User user = new User();
            user.setEmail(safeLower((String) userData.get("email")));
            user.setFirstName((String) userData.get("first_name"));
            user.setLastName((String) userData.get("last_name"));
            user.setRole(role);

            if (userData.containsKey("SBU_ID")) {
                String sbuId = String.valueOf(userData.get("SBU_ID"));
                if (role == UserRole.STUDENT) user.setStudentId(sbuId);
            }

            if (role == UserRole.STUDENT) {
                // Handle student-specific fields
                if (userData.containsKey("majors")) {
                    List<?> majors = (List<?>) userData.get("majors");
                    if (majors != null && !majors.isEmpty()) {
                        user.setMajor(String.valueOf(majors.get(0))); // Keep first major for backward compatibility
                        user.setMajors(convertListToJson(majors)); // Store all majors as JSON
                    }
                }
                
                if (userData.containsKey("degrees")) {
                    user.setDegrees(convertListToJson((List<?>) userData.get("degrees")));
                }
                
                if (userData.containsKey("minors")) {
                    user.setMinors(convertListToJson((List<?>) userData.get("minors")));
                }
                
                if (userData.containsKey("planned_majors")) {
                    user.setPlannedMajors(convertListToJson((List<?>) userData.get("planned_majors")));
                }
                
                if (userData.containsKey("planned_minors")) {
                    user.setPlannedMinors(convertListToJson((List<?>) userData.get("planned_minors")));
                }
                
                if (userData.containsKey("university_entry")) {
                    user.setUniversityEntry(convertObjectToJson(userData.get("university_entry")));
                }
                
                if (userData.containsKey("direct_admit")) {
                    user.setDirectAdmit((String) userData.get("direct_admit"));
                }
                
                if (userData.containsKey("AOI")) {
                    user.setAoi((String) userData.get("AOI"));
                }
                
                if (userData.containsKey("college")) {
                    user.setCollege((String) userData.get("college"));
                }
                
                if (userData.containsKey("transfer_courses")) {
                    user.setTransferCourses(convertListToJson((List<?>) userData.get("transfer_courses")));
                }
                
                if (userData.containsKey("classes")) {
                    user.setClasses(convertListToJson((List<?>) userData.get("classes")));
                }
                
                if (userData.containsKey("course_plan")) {
                    user.setCoursePlan(convertListToJson((List<?>) userData.get("course_plan")));
                }
                
                if (userData.containsKey("graduation")) {
                    user.setGraduation(convertObjectToJson(userData.get("graduation")));
                }
                
            } else if (role == UserRole.ADVISOR) {
                if (userData.containsKey("level")) {
                    user.setLevel((String) userData.get("level"));
                }
                if (userData.containsKey("academic_unit")) {
                    user.setAcademicUnit((String) userData.get("academic_unit"));
                }
            } else if (role == UserRole.INSTRUCTOR) {
                if (userData.containsKey("academic_unit")) {
                    user.setDepartment((String) userData.get("academic_unit"));
                }
            }

            user.setIsActive(true);
            return user;
        } catch (Exception e) {
            System.err.println("Error creating user from SBU YAML data: " + e.getMessage());
            return null;
        }
    }
    
    // Helper methods for JSON conversion
    private String convertListToJson(List<?> list) {
        if (list == null || list.isEmpty()) return null;
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(list);
        } catch (Exception e) {
            System.err.println("Error converting list to JSON: " + e.getMessage());
            return null;
        }
    }
    
    private String convertObjectToJson(Object obj) {
        if (obj == null) return null;
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            System.err.println("Error converting object to JSON: " + e.getMessage());
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

    // ===== helpers =====
    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
    private static String nvl(String a, String b) { return !isBlank(a) ? a : b; }
    private static String safeLower(String s) { return s == null ? null : s.toLowerCase(Locale.ROOT).trim(); }
    private static String strAttr(OAuth2User p, String key) {
        Object v = p.getAttribute(key);
        return v == null ? null : String.valueOf(v);
    }
}