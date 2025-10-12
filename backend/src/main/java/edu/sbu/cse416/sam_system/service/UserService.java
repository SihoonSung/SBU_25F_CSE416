package edu.sbu.cse416.sam_system.service;

import edu.sbu.cse416.sam_system.entity.User;
import edu.sbu.cse416.sam_system.entity.UserRole;
import edu.sbu.cse416.sam_system.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Service class for user management operations.
 * Handles user authentication, registration, and management.
 * 
 * TP: Used Cursor to implement user service with Google OAuth2 integration
 */
@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    /**
     * Find or create user from Google OAuth2 authentication
     */
    public User findOrCreateUserFromOAuth2(OAuth2User oauth2User) {
        String googleId = oauth2User.getName();
        String email = oauth2User.getAttribute("email");
        String firstName = oauth2User.getAttribute("given_name");
        String lastName = oauth2User.getAttribute("family_name");
        String profilePictureUrl = oauth2User.getAttribute("picture");

        // Check if user exists by Google ID
        Optional<User> existingUser = userRepository.findByGoogleId(googleId);
        if (existingUser.isPresent()) {
            User user = existingUser.get();
            user.updateLastLogin();
            user.setProfilePictureUrl(profilePictureUrl);
            return userRepository.save(user);
        }

        // Check if user exists by email
        existingUser = userRepository.findByEmail(email);
        if (existingUser.isPresent()) {
            User user = existingUser.get();
            user.setGoogleId(googleId);
            user.setProfilePictureUrl(profilePictureUrl);
            user.updateLastLogin();
            return userRepository.save(user);
        }

        // Create new user
        User newUser = new User();
        newUser.setGoogleId(googleId);
        newUser.setEmail(email);
        newUser.setFirstName(firstName);
        newUser.setLastName(lastName);
        newUser.setProfilePictureUrl(profilePictureUrl);
        newUser.setRole(UserRole.STUDENT); // Default role
        newUser.setIsActive(true);
        newUser.updateLastLogin();

        return userRepository.save(newUser);
    }

    /**
     * Get user by ID
     */
    public Optional<User> getUserById(Long userId) {
        return userRepository.findById(userId);
    }

    /**
     * Get user by email
     */
    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * Get user by Google ID
     */
    public Optional<User> getUserByGoogleId(String googleId) {
        return userRepository.findByGoogleId(googleId);
    }

    /**
     * Get all users
     */
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    /**
     * Get users by role
     */
    public List<User> getUsersByRole(UserRole role) {
        return userRepository.findByRole(role);
    }

    /**
     * Search users by name
     */
    public List<User> searchUsersByName(String searchTerm) {
        return userRepository.findByNameContaining(searchTerm);
    }

    /**
     * Search users by name and role
     */
    public List<User> searchUsersByNameAndRole(String searchTerm, UserRole role) {
        return userRepository.findByNameContainingAndRole(searchTerm, role);
    }

    /**
     * Search users by email domain
     */
    public List<User> searchUsersByEmailDomain(String domain) {
        return userRepository.findByEmailDomain(domain);
    }

    /**
     * Search students by major
     */
    public List<User> searchStudentsByMajor(String major) {
        return userRepository.findByMajorContaining(major);
    }

    /**
     * Search instructors by department
     */
    public List<User> searchInstructorsByDepartment(String department) {
        return userRepository.findByDepartmentContaining(department);
    }

    /**
     * Update user information
     */
    public User updateUser(User user) {
        return userRepository.save(user);
    }

    /**
     * Deactivate user
     */
    public User deactivateUser(Long userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setIsActive(false);
            return userRepository.save(user);
        }
        throw new RuntimeException("User not found with ID: " + userId);
    }

    /**
     * Activate user
     */
    public User activateUser(Long userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setIsActive(true);
            return userRepository.save(user);
        }
        throw new RuntimeException("User not found with ID: " + userId);
    }

    /**
     * Import users from YAML file
     */
    public List<User> importUsersFromYaml(MultipartFile file) throws IOException {
        List<User> importedUsers = new ArrayList<>();
        Yaml yaml = new Yaml();

        try (InputStream inputStream = file.getInputStream()) {
            Map<String, Object> data = yaml.load(inputStream);
            
            if (data.containsKey("users")) {
                List<Map<String, Object>> usersData = (List<Map<String, Object>>) data.get("users");
                
                for (Map<String, Object> userData : usersData) {
                    User user = createUserFromYamlData(userData);
                    if (user != null) {
                        // Check if user already exists
                        Optional<User> existingUser = userRepository.findByEmail(user.getEmail());
                        if (existingUser.isEmpty()) {
                            importedUsers.add(userRepository.save(user));
                        }
                    }
                }
            }
        }

        return importedUsers;
    }

    /**
     * Create user from YAML data
     */
    private User createUserFromYamlData(Map<String, Object> userData) {
        try {
            User user = new User();
            
            user.setEmail((String) userData.get("email"));
            user.setFirstName((String) userData.get("firstName"));
            user.setLastName((String) userData.get("lastName"));
            
            // Parse role
            String roleStr = (String) userData.get("role");
            if (roleStr != null) {
                user.setRole(UserRole.valueOf(roleStr.toUpperCase()));
            } else {
                user.setRole(UserRole.STUDENT); // Default role
            }
            
            // Student-specific fields
            if (userData.containsKey("studentId")) {
                user.setStudentId((String) userData.get("studentId"));
            }
            if (userData.containsKey("major")) {
                user.setMajor((String) userData.get("major"));
            }
            if (userData.containsKey("gpa")) {
                Object gpaObj = userData.get("gpa");
                if (gpaObj instanceof Number) {
                    user.setGpa(((Number) gpaObj).doubleValue());
                }
            }
            if (userData.containsKey("graduationYear")) {
                Object yearObj = userData.get("graduationYear");
                if (yearObj instanceof Number) {
                    user.setGraduationYear(((Number) yearObj).intValue());
                }
            }
            
            // Instructor-specific fields
            if (userData.containsKey("department")) {
                user.setDepartment((String) userData.get("department"));
            }
            if (userData.containsKey("officeLocation")) {
                user.setOfficeLocation((String) userData.get("officeLocation"));
            }
            if (userData.containsKey("officeHours")) {
                user.setOfficeHours((String) userData.get("officeHours"));
            }
            
            user.setIsActive(true);
            
            return user;
        } catch (Exception e) {
            System.err.println("Error creating user from YAML data: " + e.getMessage());
            return null;
        }
    }

    /**
     * Get user statistics
     */
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
}
