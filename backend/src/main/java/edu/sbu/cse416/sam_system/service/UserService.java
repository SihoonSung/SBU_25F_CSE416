package edu.sbu.cse416.sam_system.service;

import edu.sbu.cse416.sam_system.entity.User;
import edu.sbu.cse416.sam_system.entity.UserRole;
import edu.sbu.cse416.sam_system.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Service class for user management operations.
 * Handles user authentication, registration, and management.
 */
@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    /**
     * Find or create user from Google OIDC authentication
     */
    public User findOrCreateUserFromOAuth2(OidcUser principal) {
        String googleId = principal.getSubject();
        String email = safeLower(principal.getEmail());
        String firstName = nvl(principal.getGivenName(), principal.getAttribute("given_name"));
        String lastName  = nvl(principal.getFamilyName(), principal.getAttribute("family_name"));
        String fullName  = nvl(principal.getFullName(), principal.getAttribute("name"));
        String picture   = nvl(principal.getPicture(), principal.getAttribute("picture"));

        // Handle null/empty values
        if (isBlank(firstName) && !isBlank(fullName)) {
            String[] parts = fullName.trim().split("\\s+", 2);
            firstName = parts[0];
            if (isBlank(lastName) && parts.length > 1) lastName = parts[1];
        }
        if (isBlank(firstName)) firstName = "Unknown";
        if (isBlank(lastName))  lastName  = "User";

        // Check existing user by Google ID
        Optional<User> existingByGoogleId = userRepository.findByGoogleId(googleId);
        if (existingByGoogleId.isPresent()) {
            User u = existingByGoogleId.get();
            if (!isBlank(picture)) u.setProfilePictureUrl(picture);
            if (!isBlank(email) && isBlank(u.getEmail())) u.setEmail(email);
            u.updateLastLogin();
            return userRepository.save(u);
        }

        // Check existing user by email
        if (!isBlank(email)) {
            Optional<User> existingByEmail = userRepository.findByEmail(email);
            if (existingByEmail.isPresent()) {
                User u = existingByEmail.get();
                u.setGoogleId(googleId);
                if (!isBlank(picture)) u.setProfilePictureUrl(picture);
                u.updateLastLogin();
                if (isBlank(u.getFirstName())) u.setFirstName(firstName);
                if (isBlank(u.getLastName()))  u.setLastName(lastName);
                return userRepository.save(u);
            }
        }

        // Create new user
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

    /** Get user by ID */
    public Optional<User> getUserById(Long userId) {
        return userRepository.findById(userId);
    }

    /** Get user by email */
    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmail(safeLower(email));
    }

    /** Get user by Google ID (OIDC sub) */
    public Optional<User> getUserByGoogleId(String googleId) {
        return userRepository.findByGoogleId(googleId);
    }

    /** Get all users */
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    /** Get users by role */
    public List<User> getUsersByRole(UserRole role) {
        return userRepository.findByRole(role);
    }

    /** Search users by name */
    public List<User> searchUsersByName(String searchTerm) {
        return userRepository.findByNameContaining(searchTerm);
    }

    /** Search users by name and role */
    public List<User> searchUsersByNameAndRole(String searchTerm, UserRole role) {
        return userRepository.findByNameContainingAndRole(searchTerm, role);
    }

    /** Search users by email domain */
    public List<User> searchUsersByEmailDomain(String domain) {
        return userRepository.findByEmailDomain(domain);
    }

    /** Search students by major */
    public List<User> searchStudentsByMajor(String major) {
        return userRepository.findByMajorContaining(major);
    }

    /** Search instructors by department */
    public List<User> searchInstructorsByDepartment(String department) {
        return userRepository.findByDepartmentContaining(department);
    }

    /** Update user information */
    public User updateUser(User user) {
        // Normalize email
        if (!isBlank(user.getEmail())) {
            user.setEmail(safeLower(user.getEmail()));
        }
        return userRepository.save(user);
    }

    /** Deactivate user */
    public User deactivateUser(Long userId) {
        return userRepository.findById(userId)
                .map(u -> { u.setIsActive(false); return userRepository.save(u); })
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));
    }

    /** Activate user */
    public User activateUser(Long userId) {
        return userRepository.findById(userId)
                .map(u -> { u.setIsActive(true); return userRepository.save(u); })
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));
    }

    /** Import users from YAML file */
    public List<User> importUsersFromYaml(MultipartFile file) throws IOException {
        List<User> importedUsers = new ArrayList<>();
        Yaml yaml = new Yaml();

        try (InputStream inputStream = file.getInputStream()) {
            Map<String, Object> data = yaml.load(inputStream);
            if (data != null) {
                // Handle different YAML structures
                if (data.containsKey("users")) {
                    // Standard format: users array
                    List<Map<String, Object>> usersData = (List<Map<String, Object>>) data.get("users");
                    for (Map<String, Object> userData : usersData) {
                        User user = createUserFromYamlData(userData);
                        if (user != null) {
                            importedUsers.add(processUserForImport(user));
                        }
                    }
                } else {
                    // Handle SBU format: separate arrays for each role
                    importUsersByRole(data, "registrars", UserRole.REGISTRAR, importedUsers);
                    importUsersByRole(data, "academic_advisors", UserRole.ADVISOR, importedUsers);
                    importUsersByRole(data, "instructors", UserRole.INSTRUCTOR, importedUsers);
                    importUsersByRole(data, "students", UserRole.STUDENT, importedUsers);
                }
            }
        }

        return importedUsers;
    }

    /** Import users by role from SBU YAML format */
    private void importUsersByRole(Map<String, Object> data, String roleKey, UserRole role, List<User> importedUsers) {
        if (data.containsKey(roleKey)) {
            List<Map<String, Object>> usersData = (List<Map<String, Object>>) data.get(roleKey);
            for (Map<String, Object> userData : usersData) {
                User user = createUserFromSBUYamlData(userData, role);
                if (user != null) {
                    importedUsers.add(processUserForImport(user));
                }
            }
        }
    }

    /** Process user for import (normalize email and check duplicates) */
    private User processUserForImport(User user) {
        if (!isBlank(user.getEmail())) {
            user.setEmail(safeLower(user.getEmail()));
        }
        // Check for duplicates
        Optional<User> existing = userRepository.findByEmail(user.getEmail());
        if (existing.isEmpty()) {
            return userRepository.save(user);
        }
        return null;
    }

    /** Create user from YAML data */
    private User createUserFromYamlData(Map<String, Object> userData) {
        try {
            User user = new User();

            user.setEmail(safeLower((String) userData.get("email")));
            user.setFirstName((String) userData.get("firstName"));
            user.setLastName((String) userData.get("lastName"));

            // Parse role
            String roleStr = (String) userData.get("role");
            user.setRole(roleStr != null ? UserRole.valueOf(roleStr.toUpperCase()) : UserRole.STUDENT);

            // Student-specific
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

            // Instructor-specific
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

    /** Create user from SBU YAML data format */
    private User createUserFromSBUYamlData(Map<String, Object> userData, UserRole role) {
        try {
            User user = new User();

            // SBU format uses different field names
            user.setEmail(safeLower((String) userData.get("email")));
            user.setFirstName((String) userData.get("first_name"));
            user.setLastName((String) userData.get("last_name"));
            user.setRole(role);

            // SBU_ID can be used as studentId for students
            if (userData.containsKey("SBU_ID")) {
                String sbuId = String.valueOf(userData.get("SBU_ID"));
                if (role == UserRole.STUDENT) {
                    user.setStudentId(sbuId);
                }
            }

            // Handle role-specific fields
            if (role == UserRole.STUDENT) {
                // Students might have majors array - take first one
                if (userData.containsKey("majors")) {
                    List<?> majors = (List<?>) userData.get("majors");
                    if (majors != null && !majors.isEmpty()) {
                        user.setMajor(String.valueOf(majors.get(0)));
                    }
                }
            } else if (role == UserRole.INSTRUCTOR || role == UserRole.ADVISOR) {
                // For instructors/advisors, we could set department based on academic_unit
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

    /** Get user statistics */
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

    // -------- helpers --------
    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
    private static String nvl(String a, String b) {
        return !isBlank(a) ? a : b;
    }
    private static String safeLower(String s) {
        return s == null ? null : s.toLowerCase(Locale.ROOT).trim();
    }
}